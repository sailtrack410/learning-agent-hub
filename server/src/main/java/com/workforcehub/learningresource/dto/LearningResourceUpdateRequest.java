package com.workforcehub.learningresource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LearningResourceUpdateRequest {
    @NotBlank(message = "资源标题不能为空")
    @Size(max = 256, message = "资源标题不能超过256个字符")
    private String title;

    @NotBlank(message = "资源摘要不能为空")
    @Size(max = 1000, message = "资源摘要不能超过1000个字符")
    private String summary;

    @NotBlank(message = "资源正文不能为空")
    @Size(max = 100000, message = "资源正文不能超过100000个字符")
    private String content;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
