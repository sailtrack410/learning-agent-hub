package com.workforcehub.course.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseSaveRequest {
    @NotBlank
    @Size(max = 64)
    private String courseCode;

    @NotBlank
    @Size(max = 128)
    private String courseName;

    @Size(max = 1000)
    private String summary;

    private String learningObjectives;

    @Size(max = 1000)
    private String prerequisites;

    @Size(max = 64)
    private String teacherName;

    private Long coverAttachmentRefId;

    @Min(0)
    @Max(10000)
    private Integer totalHours;

    private String status;
}
