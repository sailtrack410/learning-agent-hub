package com.workforcehub.course.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CourseKnowledgeDocumentResponse {
    private Long documentId;
    private Long attachmentRefId;
    private String title;
    private String mimeType;
    private Long fileSize;
    private String parserTypeName;
    private String ingestStatus;
    private String ingestStatusName;
    private Integer ingestProgressPercent;
    private String ingestStage;
    private String ingestStageName;
    private String vectorStatus;
    private String vectorStatusName;
    private Integer chunkCount;
    private String errorMessage;
    private Long chapterId;
    private String chapterName;
    private List<KnowledgePoint> knowledgePoints = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class KnowledgePoint {
        private Long knowledgePointId;
        private String pointName;
    }
}
