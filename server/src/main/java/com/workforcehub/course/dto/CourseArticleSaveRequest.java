package com.workforcehub.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseArticleSaveRequest {
    @NotBlank
    @Size(max = 128)
    private String title;

    @Size(max = 1000)
    private String summary;

    @NotBlank
    @Size(max = 200000)
    private String contentJson;

    private String status;
}
