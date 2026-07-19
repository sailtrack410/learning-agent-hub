package com.workforcehub.profile.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StudentProfileResponse(
    Long profileId,
    int versionNo,
    int completedDimensions,
    int totalDimensions,
    boolean complete,
    String nextDimensionCode,
    String nextQuestion,
    List<Dimension> dimensions,
    List<Message> messages
) {
    public record Dimension(
        String dimensionCode,
        String dimensionName,
        String conclusion,
        BigDecimal confidence,
        String evidenceSummary,
        LocalDateTime updatedAt
    ) {
    }

    public record Message(
        Long messageId,
        String role,
        String dimensionCode,
        String content,
        LocalDateTime createdAt
    ) {
    }
}
