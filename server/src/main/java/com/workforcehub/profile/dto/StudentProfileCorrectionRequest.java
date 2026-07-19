package com.workforcehub.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StudentProfileCorrectionRequest {
    @NotBlank(message = "画像结论不能为空")
    @Size(max = 1000, message = "画像结论不能超过1000个字符")
    private String conclusion;

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }
}
