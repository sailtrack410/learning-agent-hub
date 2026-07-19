package com.workforcehub.learningpath.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record LearningPathResponse(
    Long pathId,
    Long courseId,
    String courseName,
    int versionNo,
    int profileVersionNo,
    Long sourceAssessmentAttemptId,
    String generationReason,
    int progressPercent,
    Long currentNodeId,
    LocalDateTime createdAt,
    List<Node> todayNodes,
    List<Node> nodes,
    List<Recommendation> recommendations
) {
    public record Node(
        Long nodeId,
        int sequenceNo,
        Long knowledgePointId,
        String knowledgePointName,
        Long chapterId,
        String chapterName,
        String learningObjective,
        String resourceType,
        Long resourceRefId,
        String resourceTitle,
        int estimatedMinutes,
        String completionCondition,
        String status,
        BigDecimal masteryScore,
        String recommendationReason
    ) {
    }

    public record Recommendation(
        Long recommendationId,
        Long nodeId,
        Long knowledgePointId,
        String knowledgePointName,
        String resourceType,
        Long resourceRefId,
        String resourceTitle,
        int priorityNo,
        String reason,
        String status
    ) {
    }
}
