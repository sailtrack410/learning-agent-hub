package com.workforcehub.learningdashboard.service;

import com.workforcehub.course.service.EducationRoleService;
import com.workforcehub.learningdashboard.dto.StudentLearningDashboardResponse;
import com.workforcehub.learningdashboard.repository.StudentLearningDashboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class StudentLearningDashboardService {
    private final EducationRoleService educationRoleService;
    private final StudentLearningDashboardRepository repository;

    public StudentLearningDashboardService(EducationRoleService educationRoleService,
                                           StudentLearningDashboardRepository repository) {
        this.educationRoleService = educationRoleService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public StudentLearningDashboardResponse current() {
        Long studentUserId = educationRoleService.requireCurrentRole("STUDENT");
        List<StudentLearningDashboardRepository.CourseRow> rows = repository.listCourses(studentUserId);
        int totalNodes = rows.stream().mapToInt(StudentLearningDashboardRepository.CourseRow::totalNodes).sum();
        int completedNodes = rows.stream().mapToInt(StudentLearningDashboardRepository.CourseRow::completedNodes).sum();
        List<StudentLearningDashboardResponse.CourseSummary> courses = rows.stream().map(row ->
            new StudentLearningDashboardResponse.CourseSummary(
                row.courseId(), row.courseName(), row.coverAttachmentRefId(), row.teacherName(), row.totalHours(),
                progress(row.completedNodes(), row.totalNodes()), scale(row.averageMastery()), row.todayTaskCount(),
                row.pendingAssessmentCount(), row.recommendationCount(), row.currentNodeTitle()
            )).toList();
        List<StudentLearningDashboardResponse.TodayTask> todayTasks = repository.listTodayTasks(studentUserId)
            .stream().map(row -> new StudentLearningDashboardResponse.TodayTask(
                row.courseId(), row.courseName(), row.nodeId(), row.chapterId(), row.chapterName(),
                row.resourceTitle(), row.estimatedMinutes(), row.status(), row.recommendationReason()
            )).toList();
        List<StudentLearningDashboardResponse.Recommendation> recommendations = repository
            .listRecommendations(studentUserId).stream().map(row ->
                new StudentLearningDashboardResponse.Recommendation(
                    row.courseId(), row.courseName(), row.nodeId(), row.resourceRefId(), row.resourceTitle(),
                    row.priorityNo(), row.reason()
                )).toList();
        return new StudentLearningDashboardResponse(rows.size(), rows.stream().mapToInt(
            StudentLearningDashboardRepository.CourseRow::todayTaskCount).sum(),
            BigDecimal.valueOf(progress(completedNodes, totalNodes)).setScale(2),
            scale(repository.getAverageMastery(studentUserId)),
            rows.stream().mapToInt(StudentLearningDashboardRepository.CourseRow::pendingAssessmentCount).sum(),
            rows.stream().mapToInt(StudentLearningDashboardRepository.CourseRow::recommendationCount).sum(),
            courses, todayTasks, recommendations);
    }

    private int progress(int completed, int total) {
        return total == 0 ? 0 : Math.round(completed * 100F / total);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
