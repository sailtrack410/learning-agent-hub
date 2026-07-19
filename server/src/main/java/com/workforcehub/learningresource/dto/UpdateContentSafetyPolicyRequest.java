package com.workforcehub.learningresource.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class UpdateContentSafetyPolicyRequest {
    @NotNull(message = "必须设置提示词注入拦截状态")
    private Boolean promptInjectionDetectionEnabled;

    @NotNull(message = "必须设置教师复核阈值")
    @DecimalMin(value = "0.0000", message = "教师复核阈值不能小于0")
    @DecimalMax(value = "1.0000", message = "教师复核阈值不能大于1")
    private BigDecimal reviewConfidenceThreshold;

    @NotNull(message = "必须提供危险指令标记")
    @Size(max = 50, message = "危险指令标记不能超过50条")
    private List<@Valid @NotBlank(message = "危险指令标记不能为空") @Size(max = 100, message = "单条危险指令标记不能超过100个字符") String> blockedMarkers;

    @Positive(message = "策略版本必须大于0")
    private int expectedVersion;

    public Boolean getPromptInjectionDetectionEnabled() {
        return promptInjectionDetectionEnabled;
    }

    public void setPromptInjectionDetectionEnabled(Boolean promptInjectionDetectionEnabled) {
        this.promptInjectionDetectionEnabled = promptInjectionDetectionEnabled;
    }

    public BigDecimal getReviewConfidenceThreshold() {
        return reviewConfidenceThreshold;
    }

    public void setReviewConfidenceThreshold(BigDecimal reviewConfidenceThreshold) {
        this.reviewConfidenceThreshold = reviewConfidenceThreshold;
    }

    public List<String> getBlockedMarkers() {
        return blockedMarkers;
    }

    public void setBlockedMarkers(List<String> blockedMarkers) {
        this.blockedMarkers = blockedMarkers;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(int expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
