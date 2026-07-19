package com.workforcehub.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseInvitationAcceptRequest {
    @NotBlank
    @Size(max = 128)
    private String token;
}
