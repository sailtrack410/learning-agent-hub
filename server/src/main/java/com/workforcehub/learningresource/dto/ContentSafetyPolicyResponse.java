package com.workforcehub.learningresource.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ContentSafetyPolicyResponse(
    boolean promptInjectionDetectionEnabled,
    BigDecimal reviewConfidenceThreshold,
    List<String> blockedMarkers,
    int versionNo,
    LocalDateTime updatedAt
) {}
