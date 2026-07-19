package com.workforcehub.file.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.course.service.CourseResourceAccessService;
import com.workforcehub.file.dto.CourseResourcePreviewResponse;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.repository.AttachmentPreviewRepository;
import com.workforcehub.file.repository.AttachmentPreviewRepository.PreviewRow;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.DownloadAttachmentRow;
import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.repository.FileObjectRow;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

@Service
public class CourseResourcePreviewService {
    private static final Logger LOG = LoggerFactory.getLogger(CourseResourcePreviewService.class);
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("doc", "docx", "ppt", "pptx");

    private final AttachmentRefRepository attachmentRefRepository;
    private final AttachmentPreviewRepository attachmentPreviewRepository;
    private final StorageProviderRegistry storageProviderRegistry;
    private final CourseResourceAccessService courseResourceAccessService;
    private final OfficePdfConverter officePdfConverter;
    private final FileQuotaPolicyService fileQuotaPolicyService;
    private final FileObjectRepository fileObjectRepository;
    private final Executor bizExecutor;
    private final long maxSourceBytes;

    public CourseResourcePreviewService(AttachmentRefRepository attachmentRefRepository,
                                        AttachmentPreviewRepository attachmentPreviewRepository,
                                        StorageProviderRegistry storageProviderRegistry,
                                        CourseResourceAccessService courseResourceAccessService,
                                        OfficePdfConverter officePdfConverter,
                                        FileQuotaPolicyService fileQuotaPolicyService,
                                        FileObjectRepository fileObjectRepository,
                                        @Qualifier("bizExecutor") Executor bizExecutor,
                                        @Value("${workforce.file.preview.max-source-bytes:104857600}") long maxSourceBytes) {
        this.attachmentRefRepository = attachmentRefRepository;
        this.attachmentPreviewRepository = attachmentPreviewRepository;
        this.storageProviderRegistry = storageProviderRegistry;
        this.courseResourceAccessService = courseResourceAccessService;
        this.officePdfConverter = officePdfConverter;
        this.fileQuotaPolicyService = fileQuotaPolicyService;
        this.fileObjectRepository = fileObjectRepository;
        this.bizExecutor = bizExecutor;
        this.maxSourceBytes = maxSourceBytes;
    }

    public synchronized CourseResourcePreviewResponse prepare(Long courseId, Long attachmentRefId) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireCourseResource(userId, courseId, attachmentRefId);
        DownloadAttachmentRow source = activeAttachment(attachmentRefId);
        String extension = extension(source.displayName());
        if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(source.mimeType())) {
            return response(source, "READY", resourceUrl(attachmentRefId), null);
        }
        if (!OFFICE_EXTENSIONS.contains(extension)) {
            throw new BizException(422, "当前文件类型不支持转换预览");
        }
        if (source.fileSize() > maxSourceBytes) {
            throw new BizException(422, "Office文件超过预览转换大小限制");
        }
        PreviewRow existing = attachmentPreviewRepository.find(attachmentRefId);
        if (existing != null && "READY".equals(existing.previewStatus()) && existing.previewAttachmentRefId() != null) {
            activeAttachment(existing.previewAttachmentRefId());
            return response(source, "READY", previewUrl(attachmentRefId), null);
        }
        if (existing != null && "CONVERTING".equals(existing.previewStatus())
            && existing.updatedAt().isAfter(LocalDateTime.now().minusMinutes(5))) {
            return response(source, "CONVERTING", null, null);
        }
        attachmentPreviewRepository.markConverting(attachmentRefId);
        bizExecutor.execute(() -> convert(courseId, userId, source, extension));
        return response(source, "CONVERTING", null, null);
    }

    public CourseResourcePreviewResponse status(Long courseId, Long attachmentRefId) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireCourseResource(userId, courseId, attachmentRefId);
        DownloadAttachmentRow source = activeAttachment(attachmentRefId);
        String extension = extension(source.displayName());
        if ("pdf".equals(extension) || "application/pdf".equalsIgnoreCase(source.mimeType())) {
            return response(source, "READY", resourceUrl(attachmentRefId), null);
        }
        PreviewRow preview = attachmentPreviewRepository.find(attachmentRefId);
        if (preview == null) return response(source, "PENDING", null, null);
        if ("READY".equals(preview.previewStatus()) && preview.previewAttachmentRefId() != null) {
            activeAttachment(preview.previewAttachmentRefId());
            return response(source, "READY", previewUrl(attachmentRefId), null);
        }
        return response(source, preview.previewStatus(), null, preview.errorMessage());
    }

    private void convert(Long courseId, Long userId, DownloadAttachmentRow source, String extension) {
        Long attachmentRefId = source.attachmentRefId();
        Path workDirectory = null;
        String previewObjectKey = null;
        StorageProvider storageProvider = null;
        try {
            storageProvider = storageProviderRegistry.require(source.storageProvider());
            workDirectory = Files.createTempDirectory("course-preview-");
            Path sourceFile = workDirectory.resolve("source." + extension);
            StorageObjectResource sourceResource = storageProvider.loadObject(source.bucketName(), source.objectKey());
            try (InputStream inputStream = sourceResource.resource().getInputStream()) {
                Files.copy(inputStream, sourceFile);
            }
            Path pdf = officePdfConverter.convert(sourceFile, workDirectory, workDirectory.resolve("profile"));
            long pdfSize = Files.size(pdf);
            String sha256 = sha256(pdf);
            FileObjectRow reusable = fileObjectRepository.findActiveBySha256AndSize(sha256, pdfSize);
            if (reusable == null) {
                fileQuotaPolicyService.ensureGlobalQuota(pdfSize);
                previewObjectKey = "course-previews/%s/%s/%s.pdf".formatted(courseId, attachmentRefId, sha256);
                try (InputStream inputStream = Files.newInputStream(pdf)) {
                    storageProvider.storeObject(source.bucketName(), previewObjectKey, "application/pdf", inputStream, pdfSize);
                }
            }
            String previewName = baseName(source.displayName()) + "-预览.pdf";
            attachmentPreviewRepository.saveReady(attachmentRefId, courseId, userId, previewName,
                source.storageProvider(), source.bucketName(), previewObjectKey, pdfSize, sha256,
                reusable == null ? null : reusable.id());
            previewObjectKey = null;
        } catch (Exception exception) {
            if (previewObjectKey != null && storageProvider != null) {
                try {
                    storageProvider.deleteObject(source.bucketName(), previewObjectKey);
                } catch (Exception cleanupException) {
                    LOG.warn("Failed to clean course preview object after conversion error");
                }
            }
            String message = exception instanceof BizException ? exception.getMessage() : "文件预览转换失败";
            attachmentPreviewRepository.markFailed(attachmentRefId, message);
            LOG.warn("Course preview conversion failed attachmentRefId={}", attachmentRefId);
        } finally {
            deleteDirectory(workDirectory);
        }
    }

    public DownloadFileResult readPreview(Long sourceAttachmentRefId) {
        Long userId = CurrentUserHolder.requireUserId();
        courseResourceAccessService.requireReadableResource(userId, sourceAttachmentRefId);
        DownloadAttachmentRow source = activeAttachment(sourceAttachmentRefId);
        if ("pdf".equals(extension(source.displayName())) || "application/pdf".equalsIgnoreCase(source.mimeType())) {
            return load(source);
        }
        PreviewRow preview = attachmentPreviewRepository.find(sourceAttachmentRefId);
        if (preview == null || !"READY".equals(preview.previewStatus()) || preview.previewAttachmentRefId() == null) {
            throw new BizException(409, "文件预览尚未生成");
        }
        return load(activeAttachment(preview.previewAttachmentRefId()));
    }

    private DownloadAttachmentRow activeAttachment(Long attachmentRefId) {
        DownloadAttachmentRow attachment = attachmentRefRepository.getDownloadAttachmentOrThrow(attachmentRefId);
        if (attachment.deletedAt() != null || attachment.attachmentDeleted() != 0 || attachment.fileDeleted() != 0) {
            throw new BizException(409, "课程资源已删除");
        }
        return attachment;
    }

    private DownloadFileResult load(DownloadAttachmentRow attachment) {
        StorageObjectResource resource = storageProviderRegistry.require(attachment.storageProvider())
            .loadObject(attachment.bucketName(), attachment.objectKey());
        return new DownloadFileResult(resource.resource(), attachment.displayName(), attachment.mimeType(), resource.size());
    }

    private CourseResourcePreviewResponse response(DownloadAttachmentRow source, String status, String previewUrl,
                                                     String errorMessage) {
        return new CourseResourcePreviewResponse(source.attachmentRefId(), source.displayName(), status,
            previewUrl, resourceUrl(source.attachmentRefId()), errorMessage);
    }

    private String resourceUrl(Long attachmentRefId) {
        return "/api/employee/courses/resources/" + attachmentRefId;
    }

    private String previewUrl(Long attachmentRefId) {
        return resourceUrl(attachmentRefId) + "/preview";
    }

    private String extension(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String baseName(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : "课程附件";
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    LOG.warn("Failed to clean course preview temporary file");
                }
            });
        } catch (Exception exception) {
            LOG.warn("Failed to clean course preview temporary directory");
        }
    }
}
