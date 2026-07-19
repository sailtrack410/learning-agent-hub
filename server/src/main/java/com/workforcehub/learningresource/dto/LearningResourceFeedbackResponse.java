package com.workforcehub.learningresource.dto;

import java.time.LocalDateTime;

public record LearningResourceFeedbackResponse(
    Long feedbackId,
    Long resourceId,
    String feedbackType,
    String feedbackTypeName,
    String comment,
    Integer profileVersionNo,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
