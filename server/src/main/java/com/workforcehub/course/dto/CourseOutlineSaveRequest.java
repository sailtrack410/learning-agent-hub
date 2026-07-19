package com.workforcehub.course.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CourseOutlineSaveRequest {
    @Valid
    @NotEmpty
    private List<Chapter> chapters = new ArrayList<>();

    @Valid
    private List<Dependency> dependencies = new ArrayList<>();

    @Data
    public static class Chapter {
        private Long chapterId;

        @NotBlank
        @Size(max = 128)
        private String chapterName;

        @Size(max = 1000)
        private String summary;

        @Valid
        private List<KnowledgePoint> knowledgePoints = new ArrayList<>();
    }

    @Data
    public static class KnowledgePoint {
        @NotBlank
        @Size(max = 64)
        private String pointCode;

        @NotBlank
        @Size(max = 128)
        private String pointName;

        @Size(max = 1000)
        private String summary;
    }

    @Data
    public static class Dependency {
        @NotBlank
        private String prerequisitePointCode;

        @NotBlank
        private String dependentPointCode;
    }
}
