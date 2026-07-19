package com.workforcehub.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StudentProfileAnswerRequest {
    @NotBlank(message = "回答不能为空")
    @Size(min = 2, max = 1000, message = "回答长度必须在2到1000个字符之间")
    private String message;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
