package com.workforcehub.course.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CourseKnowledgeDocumentRequest {
    @NotNull
    private Long attachmentRefId;
    private Long chapterId;
    private List<Long> knowledgePointIds = new ArrayList<>();
}
