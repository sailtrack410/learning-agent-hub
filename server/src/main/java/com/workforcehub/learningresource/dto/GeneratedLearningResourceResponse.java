package com.workforcehub.learningresource.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record GeneratedLearningResourceResponse(
    Long resourceId,
    Long taskId,
    Long courseId,
    String courseName,
    Long chapterId,
    String chapterName,
    Long knowledgePointId,
    String knowledgePointName,
    String resourceType,
    String resourceTypeName,
    String title,
    String summary,
    String content,
    String contentFormat,
    String validationStatus,
    String validationStatusName,
    String validationReason,
    BigDecimal confidence,
    String publicationStatus,
    String publicationStatusName,
    String reviewNote,
    LocalDateTime reviewedAt,
    LocalDateTime publishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<Citation> citations
) {
    public record Citation(
        Long documentId,
        Long chunkId,
        String documentTitle,
        String excerpt,
        Integer pageNo,
        BigDecimal relevanceScore
    ) {
    }
}
