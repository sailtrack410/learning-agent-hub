package com.workforcehub.learningresource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class LearningResourcePushRequest {
    @NotBlank(message = "推送目标类型不能为空")
    private String targetType;

    @NotNull(message = "推送目标不能为空")
    private Long targetId;

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }
}
