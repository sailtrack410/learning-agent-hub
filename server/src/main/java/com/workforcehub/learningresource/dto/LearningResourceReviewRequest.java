package com.workforcehub.learningresource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LearningResourceReviewRequest {
    @NotBlank(message = "审核结论不能为空")
    @Pattern(regexp = "APPROVE|REJECT", message = "审核结论必须为APPROVE或REJECT")
    private String decision;

    @Size(max = 1000, message = "审核意见不能超过1000个字符")
    private String note;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
