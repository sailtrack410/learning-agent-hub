package com.workforcehub.course.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CourseInvitationResponse {
    private String token;
    private LocalDateTime expiresAt;
}
