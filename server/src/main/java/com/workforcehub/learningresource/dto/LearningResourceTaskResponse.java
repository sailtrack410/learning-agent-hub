package com.workforcehub.learningresource.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LearningResourceTaskResponse(
    Long taskId,
    Long studentUserId,
    Long courseId,
    String courseName,
    Long chapterId,
    String chapterName,
    Long knowledgePointId,
    String knowledgePointName,
    int profileVersionNo,
    String requestMessage,
    String status,
    String statusName,
    int progressPercent,
    String currentRoleCode,
    String currentRoleName,
    int attemptNo,
    String modelName,
    String failureType,
    String failureMessage,
    Long totalDurationMs,
    LocalDateTime startedAt,
    LocalDateTime finishedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<Step> steps,
    List<GeneratedLearningResourceResponse> resources
) {
    public record Step(
        Long stepId,
        String roleCode,
        String roleName,
        int stepOrder,
        String status,
        String statusName,
        String inputSummary,
        String outputSummary,
        String liveOutput,
        String modelName,
        Long durationMs,
        String failureMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
    ) {
    }
}
