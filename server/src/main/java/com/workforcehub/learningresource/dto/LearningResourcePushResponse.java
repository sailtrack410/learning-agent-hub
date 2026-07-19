package com.workforcehub.learningresource.dto;

import java.time.LocalDateTime;

public record LearningResourcePushResponse(
    Long pushId,
    Long resourceId,
    Long courseId,
    String targetType,
    Long targetId,
    String targetName,
    String status,
    LocalDateTime createdAt
) {}
