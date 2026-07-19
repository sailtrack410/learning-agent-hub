package com.workforcehub.learningdashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record StudentLearningDashboardResponse(
    int courseCount,
    int todayTaskCount,
    BigDecimal overallProgress,
    BigDecimal averageMastery,
    int pendingAssessmentCount,
    int recommendationCount,
    List<CourseSummary> courses,
    List<TodayTask> todayTasks,
    List<Recommendation> recommendations
) {
    public record CourseSummary(
        Long courseId,
        String courseName,
        Long coverAttachmentRefId,
        String teacherName,
        int totalHours,
        int progressPercent,
        BigDecimal averageMastery,
        int todayTaskCount,
        int pendingAssessmentCount,
        int recommendationCount,
        String currentNodeTitle
    ) {}

    public record TodayTask(
        Long courseId,
        String courseName,
        Long nodeId,
        Long chapterId,
        String chapterName,
        String resourceTitle,
        int estimatedMinutes,
        String status,
        String recommendationReason
    ) {}

    public record Recommendation(
        Long courseId,
        String courseName,
        Long nodeId,
        Long resourceRefId,
        String resourceTitle,
        int priorityNo,
        String reason
    ) {}
}
