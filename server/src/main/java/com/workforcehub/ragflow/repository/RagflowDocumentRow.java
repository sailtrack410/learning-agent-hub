package com.workforcehub.ragflow.repository;

import java.time.LocalDateTime;

public record RagflowDocumentRow(
    Long id,
    Long spaceId,
    Long attachmentRefId,
    Long sourceFileObjectId,
    String title,
    String mimeType,
    Long fileSize,
    String contentHash,
    String parserType,
    String ingestStatus,
    Long latestIngestJobId,
    String ingestJobStatus,
    Integer ingestProgressPercent,
    String ingestStage,
    String vectorStatus,
    Integer chunkCount,
    String errorMessage,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
