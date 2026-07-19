package com.workforcehub.course.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CourseResponse {
    private Long courseId;
    private String courseCode;
    private String courseName;
    private String summary;
    private String learningObjectives;
    private String prerequisites;
    private String teacherName;
    private Long coverAttachmentRefId;
    private Integer totalHours;
    private String status;
    private String statusName;
    private String teacherRole;
    private Boolean enrolled;
    private List<Teacher> teachers = new ArrayList<>();
    private Integer chapterCount;
    private Integer knowledgePointCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Chapter> chapters = new ArrayList<>();
    private List<Dependency> dependencies = new ArrayList<>();

    @Data
    public static class Teacher {
        private Long userId;
        private String teacherName;
        private String memberRole;
    }

    @Data
    public static class Chapter {
        private Long chapterId;
        private Integer chapterNo;
        private String chapterName;
        private String summary;
        private String contentType;
        private String contentJson;
        private String status;
        private String statusName;
        private List<KnowledgePoint> knowledgePoints = new ArrayList<>();
    }

    @Data
    public static class KnowledgePoint {
        private Long knowledgePointId;
        private String pointCode;
        private String pointName;
        private String summary;
        private Integer sortOrder;
    }

    @Data
    public static class Dependency {
        private String prerequisitePointCode;
        private String dependentPointCode;
    }
}
