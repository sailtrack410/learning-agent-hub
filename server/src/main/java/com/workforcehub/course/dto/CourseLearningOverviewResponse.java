package com.workforcehub.course.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CourseLearningOverviewResponse(
    Long courseId,
    String courseName,
    int studentCount,
    int activeStudentCount,
    int riskStudentCount,
    BigDecimal completionRate,
    BigDecimal averageMastery,
    int publishedResourceCount,
    int resourceViewerCount,
    int resourceViewCount,
    List<ClassSummary> classes,
    List<StudentSummary> students,
    List<WeakKnowledgePoint> weakKnowledgePoints
) {
    public record ClassSummary(
        Long classId,
        String className,
        int studentCount,
        int riskStudentCount,
        BigDecimal completionRate,
        BigDecimal averageMastery
    ) {}

    public record StudentSummary(
        Long studentUserId,
        String studentNo,
        String studentName,
        Long classId,
        String className,
        int profileVersionNo,
        int profileDimensionCount,
        String profileSummary,
        BigDecimal progressPercent,
        BigDecimal averageMastery,
        int assessmentAttemptCount,
        LocalDateTime lastLearningAt,
        String riskLevel,
        List<String> riskReasons
    ) {}

    public record WeakKnowledgePoint(
        Long knowledgePointId,
        String knowledgePointName,
        BigDecimal averageMastery,
        int assessedStudentCount,
        int weakStudentCount
    ) {}
}
