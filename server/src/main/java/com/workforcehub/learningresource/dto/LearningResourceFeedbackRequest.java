package com.workforcehub.learningresource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class LearningResourceFeedbackRequest {
    @NotBlank(message = "反馈类型不能为空")
    private String feedbackType;

    @Size(max = 500, message = "反馈说明最多500个字符")
    private String comment;

    public String getFeedbackType() {
        return feedbackType;
    }

    public void setFeedbackType(String feedbackType) {
        this.feedbackType = feedbackType;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
