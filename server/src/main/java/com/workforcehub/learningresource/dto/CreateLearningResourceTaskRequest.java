package com.workforcehub.learningresource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateLearningResourceTaskRequest {
    @NotNull(message = "课程不能为空")
    private Long courseId;

    @NotNull(message = "知识点不能为空")
    private Long knowledgePointId;

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过64个字符")
    private String idempotencyKey;

    @Size(max = 500, message = "补充学习诉求不能超过500个字符")
    private String requestMessage;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getKnowledgePointId() {
        return knowledgePointId;
    }

    public void setKnowledgePointId(Long knowledgePointId) {
        this.knowledgePointId = knowledgePointId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getRequestMessage() {
        return requestMessage;
    }

    public void setRequestMessage(String requestMessage) {
        this.requestMessage = requestMessage;
    }
}
