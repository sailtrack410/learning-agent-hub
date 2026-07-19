package com.workforcehub.file.repository;

import com.workforcehub.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public class AttachmentPreviewRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AttachmentRefRepository attachmentRefRepository;

    public AttachmentPreviewRepository(JdbcTemplate jdbcTemplate, AttachmentRefRepository attachmentRefRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentRefRepository = attachmentRefRepository;
    }

    public PreviewRow find(Long sourceAttachmentRefId) {
        List<PreviewRow> rows = jdbcTemplate.query("""
            SELECT source_attachment_ref_id, preview_attachment_ref_id, preview_status, error_message, updated_at
            FROM sys_attachment_preview
            WHERE source_attachment_ref_id = ?
            """, (rs, rowNum) -> new PreviewRow(
            rs.getLong("source_attachment_ref_id"),
            rs.getObject("preview_attachment_ref_id", Long.class),
            rs.getString("preview_status"),
            rs.getString("error_message"),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ), sourceAttachmentRefId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void markConverting(Long sourceAttachmentRefId) {
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_preview (source_attachment_ref_id, preview_status)
            VALUES (?, 'CONVERTING')
            ON DUPLICATE KEY UPDATE preview_status = 'CONVERTING', error_message = NULL
            """, sourceAttachmentRefId);
    }

    public void markFailed(Long sourceAttachmentRefId, String errorMessage) {
        jdbcTemplate.update("""
            UPDATE sys_attachment_preview
            SET preview_status = 'FAILED', error_message = ?
            WHERE source_attachment_ref_id = ?
            """, abbreviate(errorMessage), sourceAttachmentRefId);
    }

    @Transactional
    public Long saveReady(Long sourceAttachmentRefId, Long courseId, Long uploaderId,
                          String fileName, String storageProvider, String bucketName,
                          String objectKey, long fileSize, String sha256, Long reusableFileObjectId) {
        Long fileObjectId = reusableFileObjectId == null ? IdGenerator.nextId() : reusableFileObjectId;
        Long attachmentRefId = IdGenerator.nextId();
        if (reusableFileObjectId == null) {
            jdbcTemplate.update("""
                INSERT INTO sys_file_object
                    (id, storage_provider, bucket_name, object_key, file_name, file_ext, mime_type,
                     file_size, file_hash, content_sha256, ref_count, upload_status, scan_status, created_by)
                VALUES (?, ?, ?, ?, ?, 'pdf', 'application/pdf', ?, ?, ?, 0, 'UPLOADED', 'READY', ?)
                """, fileObjectId, storageProvider, bucketName, objectKey, fileName, fileSize, sha256, sha256, uploaderId);
        }
        attachmentRefRepository.create(attachmentRefId, "COURSE_RESOURCE", courseId, fileObjectId, null,
            fileName, fileName, "USER", uploaderId, null);
        jdbcTemplate.update("UPDATE sys_file_object SET ref_count = ref_count + 1 WHERE id = ?", fileObjectId);
        jdbcTemplate.update("""
            UPDATE sys_attachment_preview
            SET preview_attachment_ref_id = ?, preview_status = 'READY', error_message = NULL
            WHERE source_attachment_ref_id = ?
            """, attachmentRefId, sourceAttachmentRefId);
        if (reusableFileObjectId == null) {
            jdbcTemplate.update("""
                INSERT INTO sys_storage_usage
                    (id, scope_type, scope_id, used_bytes, cache_used_bytes, file_count)
                VALUES (?, 'GLOBAL', NULL, ?, 0, 1)
                ON DUPLICATE KEY UPDATE used_bytes = used_bytes + VALUES(used_bytes), file_count = file_count + 1
                """, IdGenerator.nextId(), fileSize);
        }
        return attachmentRefId;
    }

    private String abbreviate(String value) {
        if (value == null) return "文件预览转换失败";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    public record PreviewRow(Long sourceAttachmentRefId, Long previewAttachmentRefId,
                             String previewStatus, String errorMessage, LocalDateTime updatedAt) {}
}
