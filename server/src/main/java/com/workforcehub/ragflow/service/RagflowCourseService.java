package com.workforcehub.ragflow.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.file.repository.AttachmentRow;
import com.workforcehub.ragflow.dto.RagflowDocumentResponse;
import com.workforcehub.ragflow.repository.RagflowDocumentRow;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

@Service
public class RagflowCourseService {
    private final RagflowRepository ragflowRepository;
    private final AttachmentRefRepository attachmentRefRepository;
    private final RagflowIngestService ragflowIngestService;
    private final RagflowVectorRepository vectorRepository;
    private final Executor kbIngestExecutor;

    public RagflowCourseService(RagflowRepository ragflowRepository,
                                AttachmentRefRepository attachmentRefRepository,
                                RagflowIngestService ragflowIngestService,
                                RagflowVectorRepository vectorRepository,
                                @Qualifier("kbIngestExecutor") Executor kbIngestExecutor) {
        this.ragflowRepository = ragflowRepository;
        this.attachmentRefRepository = attachmentRefRepository;
        this.ragflowIngestService = ragflowIngestService;
        this.vectorRepository = vectorRepository;
        this.kbIngestExecutor = kbIngestExecutor;
    }

    public Long createSpace(Long courseId, String courseName, Long operatorId) {
        RagflowGlobalSettingRow setting = ragflowRepository.getGlobalSetting();
        Long spaceId = IdGenerator.nextId();
        ragflowRepository.insertSpace(
            spaceId,
            courseName + "课程知识库",
            "COURSE_" + courseId,
            "课程资料解析与检索空间",
            "PRIVATE",
            "ACTIVE",
            null,
            null,
            setting.embeddingModelId(),
            setting.embeddingDimension(),
            false,
            null,
            null,
            null,
            setting.chunkSize(),
            setting.chunkOverlap(),
            setting.retrievalTopK(),
            setting.scoreThreshold(),
            operatorId
        );
        return spaceId;
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowDocumentResponse addDocument(Long courseId, Long spaceId, Long attachmentRefId, Long operatorId) {
        ragflowRepository.getSpaceOrThrow(spaceId);
        AttachmentRow attachment = attachmentRefRepository.getAttachmentOrThrow(attachmentRefId);
        if (!"COURSE_RESOURCE".equals(attachment.ownerType()) || !courseId.equals(attachment.ownerId())) {
            throw new BizException(400, "课程资源与当前课程不匹配");
        }
        if (attachment.deletedAt() != null || attachment.isDeleted() != 0) {
            throw new BizException(409, "课程资源已删除");
        }
        if (ragflowRepository.existsDocumentByAttachment(spaceId, attachmentRefId)) {
            return listDocuments(spaceId).stream()
                .filter(item -> attachmentRefId.equals(item.getAttachmentRefId()))
                .findFirst()
                .orElseThrow(() -> new BizException(409, "课程资料已入库"));
        }
        Long documentId = IdGenerator.nextId();
        Long jobId = IdGenerator.nextId();
        String parserType = parserType(attachment.mimeType(), attachment.displayName());
        ragflowRepository.insertDocument(documentId, spaceId, attachment.id(), attachment.fileObjectId(),
            attachment.displayName(), attachment.mimeType(), attachment.fileSize(), attachment.contentSha256(),
            parserType, operatorId);
        ragflowRepository.insertIngestJob(jobId, documentId, spaceId, attachment.fileObjectId(), operatorId);
        ragflowRepository.bindLatestIngestJob(documentId, jobId, operatorId);
        scheduleIngest(spaceId, documentId, jobId, attachment.fileObjectId(), attachment.displayName(),
            attachment.mimeType(), parserType);
        ragflowRepository.refreshSpaceCounts(spaceId);
        return findDocument(spaceId, documentId);
    }

    public List<RagflowDocumentResponse> listDocuments(Long spaceId) {
        ragflowRepository.getSpaceOrThrow(spaceId);
        return ragflowRepository.listDocuments(spaceId).stream().map(this::toResponse).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public RagflowDocumentResponse reingestDocument(Long spaceId, Long documentId, Long operatorId) {
        RagflowDocumentRow document = ragflowRepository.getDocumentOrThrow(spaceId, documentId);
        if ("PENDING".equals(document.ingestStatus()) || "RUNNING".equals(document.ingestStatus())) {
            return toResponse(document);
        }
        Long jobId = IdGenerator.nextId();
        ragflowRepository.insertIngestJob(jobId, documentId, spaceId, document.sourceFileObjectId(), operatorId);
        ragflowRepository.bindLatestIngestJob(documentId, jobId, operatorId);
        scheduleIngest(spaceId, documentId, jobId, document.sourceFileObjectId(), document.title(),
            document.mimeType(), document.parserType());
        ragflowRepository.refreshSpaceCounts(spaceId);
        return findDocument(spaceId, documentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSpace(Long spaceId, Long operatorId) {
        ragflowRepository.getSpaceOrThrow(spaceId);
        ragflowRepository.softDeleteDocumentsBySpace(spaceId, operatorId);
        ragflowRepository.deactivateSpaceChunks(spaceId);
        ragflowRepository.softDeleteSpace(spaceId, operatorId);
        Runnable cleanup = () -> vectorRepository.deactivateSpaceVectors(spaceId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }
        cleanup.run();
    }

    private RagflowDocumentResponse findDocument(Long spaceId, Long documentId) {
        return listDocuments(spaceId).stream()
            .filter(item -> documentId.equals(item.getDocumentId()))
            .findFirst()
            .orElseThrow(() -> new BizException(500, "课程资料入库任务创建失败"));
    }

    private void scheduleIngest(Long spaceId, Long documentId, Long jobId, Long fileObjectId,
                                String title, String mimeType, String parserType) {
        Runnable execution = () -> kbIngestExecutor.execute(() -> ragflowIngestService.ingest(
            spaceId, documentId, jobId, fileObjectId, title, mimeType, parserType));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    execution.run();
                }
            });
            return;
        }
        execution.run();
    }

    private RagflowDocumentResponse toResponse(RagflowDocumentRow row) {
        RagflowDocumentResponse response = new RagflowDocumentResponse();
        response.setDocumentId(row.id());
        response.setSpaceId(row.spaceId());
        response.setAttachmentRefId(row.attachmentRefId());
        response.setTitle(row.title());
        response.setMimeType(row.mimeType());
        response.setFileSize(row.fileSize());
        response.setParserType(row.parserType());
        response.setParserTypeName(parserTypeName(row.parserType()));
        response.setIngestStatus(row.ingestStatus());
        response.setIngestStatusName(statusName(row.ingestStatus()));
        response.setIngestProgressPercent(row.ingestProgressPercent());
        response.setIngestStage(row.ingestStage());
        response.setIngestStageName(stageName(row.ingestStage()));
        response.setVectorStatus(row.vectorStatus());
        response.setVectorStatusName(statusName(row.vectorStatus()));
        response.setChunkCount(row.chunkCount());
        response.setErrorMessage(row.errorMessage());
        response.setCreatedAt(row.createdAt());
        response.setUpdatedAt(row.updatedAt());
        return response;
    }

    private String statusName(String status) {
        if ("PENDING".equals(status)) return "等待处理";
        if ("RUNNING".equals(status)) return "正在解析和向量化";
        if ("SUCCESS".equals(status)) return "已入库";
        if ("FAILED".equals(status)) return "入库失败";
        return status;
    }

    private String stageName(String stage) {
        if ("QUEUED".equals(stage)) return "等待处理";
        if ("READ_FILE".equals(stage)) return "读取文件";
        if ("PARSE_CONTENT".equals(stage)) return "解析内容";
        if ("SPLIT_CHUNKS".equals(stage)) return "切分文本";
        if ("VECTORIZE".equals(stage)) return "生成向量";
        if ("WRITE_VECTOR".equals(stage)) return "写入向量库";
        if ("COMMIT_INDEX".equals(stage)) return "提交索引";
        if ("COMPLETED".equals(stage)) return "入库完成";
        if ("FAILED".equals(stage)) return "处理失败";
        return stage;
    }

    private String parserTypeName(String parserType) {
        if ("PDF".equals(parserType)) return "PDF 解析";
        if ("WORD".equals(parserType)) return "Word 解析";
        if ("PPT".equals(parserType)) return "演示文稿解析";
        if ("OCR".equals(parserType)) return "图片文字识别";
        if ("TABLE".equals(parserType)) return "表格解析";
        return "文本解析";
    }

    private String parserType(String mimeType, String fileName) {
        String text = ((mimeType == null ? "" : mimeType) + " " + (fileName == null ? "" : fileName))
            .toLowerCase(Locale.ROOT);
        if (text.contains("pdf")) return "PDF";
        if (text.contains("word") || text.endsWith(".doc") || text.endsWith(".docx")) return "WORD";
        if (text.contains("presentation") || text.endsWith(".ppt") || text.endsWith(".pptx")) return "PPT";
        if (text.contains("image") || text.endsWith(".png") || text.endsWith(".jpg")
            || text.endsWith(".jpeg") || text.endsWith(".webp")) return "OCR";
        if (text.contains("spreadsheet") || text.contains("excel") || text.endsWith(".xls")
            || text.endsWith(".xlsx") || text.endsWith(".csv")) return "TABLE";
        return "TEXT";
    }
}
