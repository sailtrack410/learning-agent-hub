package com.workforcehub.course.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.course.dto.CourseLearningOverviewResponse;
import com.workforcehub.course.repository.CourseLearningAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CourseLearningAnalyticsService {
    private final EducationRoleService educationRoleService;
    private final CourseLearningAnalyticsRepository repository;

    public CourseLearningAnalyticsService(EducationRoleService educationRoleService,
                                          CourseLearningAnalyticsRepository repository) {
        this.educationRoleService = educationRoleService;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CourseLearningOverviewResponse overview(Long courseId) {
        Long teacherUserId = educationRoleService.requireCurrentRole("TEACHER");
        CourseLearningAnalyticsRepository.CourseRow course = repository.getTeacherCourse(teacherUserId, courseId);
        if (course == null) throw new BizException(404, "课程不存在或无权查看学习数据");

        Map<Long, CourseLearningAnalyticsRepository.ProfileRow> profiles = byStudent(
            repository.listProfiles(courseId), CourseLearningAnalyticsRepository.ProfileRow::studentUserId);
        Map<Long, CourseLearningAnalyticsRepository.ProgressRow> progress = byStudent(
            repository.listProgress(courseId), CourseLearningAnalyticsRepository.ProgressRow::studentUserId);
        Map<Long, CourseLearningAnalyticsRepository.MasteryRow> mastery = byStudent(
            repository.listMastery(courseId), CourseLearningAnalyticsRepository.MasteryRow::studentUserId);
        Map<Long, CourseLearningAnalyticsRepository.AttemptRow> attempts = byStudent(
            repository.listAttempts(courseId), CourseLearningAnalyticsRepository.AttemptRow::studentUserId);
        Map<Long, CourseLearningAnalyticsRepository.ResourceLearningRow> resourceLearning =
            byStudent(repository.listResourceLearning(courseId),
                CourseLearningAnalyticsRepository.ResourceLearningRow::studentUserId);

        List<CourseLearningOverviewResponse.StudentSummary> students = repository.listStudents(courseId).stream()
            .map(student -> studentSummary(student, profiles.get(student.studentUserId()),
                progress.get(student.studentUserId()), mastery.get(student.studentUserId()),
                attempts.get(student.studentUserId()), resourceLearning.get(student.studentUserId())))
            .sorted(Comparator.comparingInt(this::riskOrder)
                .thenComparing(CourseLearningOverviewResponse.StudentSummary::className,
                    Comparator.nullsLast(String::compareTo))
                .thenComparing(CourseLearningOverviewResponse.StudentSummary::studentNo))
            .toList();

        Map<Long, List<CourseLearningOverviewResponse.StudentSummary>> byClass = new LinkedHashMap<>();
        for (CourseLearningOverviewResponse.StudentSummary student : students) {
            byClass.computeIfAbsent(student.classId(), ignored -> new ArrayList<>()).add(student);
        }
        List<CourseLearningOverviewResponse.ClassSummary> classes = byClass.entrySet().stream().map(entry -> {
            List<CourseLearningOverviewResponse.StudentSummary> members = entry.getValue();
            String className = members.stream().map(CourseLearningOverviewResponse.StudentSummary::className)
                .filter(java.util.Objects::nonNull).findFirst().orElse("未分班");
            return new CourseLearningOverviewResponse.ClassSummary(entry.getKey(), className, members.size(),
                (int) members.stream().filter(item -> !"LOW".equals(item.riskLevel())).count(),
                average(members.stream().map(CourseLearningOverviewResponse.StudentSummary::progressPercent).toList()),
                averageNullable(members.stream().map(CourseLearningOverviewResponse.StudentSummary::averageMastery).toList()));
        }).toList();

        List<CourseLearningOverviewResponse.WeakKnowledgePoint> weakPoints = repository
            .listWeakKnowledgePoints(courseId).stream().map(row -> new CourseLearningOverviewResponse.WeakKnowledgePoint(
                row.knowledgePointId(), row.knowledgePointName(), scale(row.averageMastery()),
                row.assessedStudentCount(), row.weakStudentCount())).toList();
        CourseLearningAnalyticsRepository.ResourceStatsRow resourceStats = repository.getResourceStats(courseId);

        return new CourseLearningOverviewResponse(course.courseId(), course.courseName(), students.size(),
            (int) students.stream().filter(item -> item.lastLearningAt() != null).count(),
            (int) students.stream().filter(item -> !"LOW".equals(item.riskLevel())).count(),
            average(students.stream().map(CourseLearningOverviewResponse.StudentSummary::progressPercent).toList()),
            averageNullable(students.stream().map(CourseLearningOverviewResponse.StudentSummary::averageMastery).toList()),
            resourceStats.publishedResourceCount(), resourceStats.resourceViewerCount(), resourceStats.resourceViewCount(),
            classes, students, weakPoints);
    }

    private CourseLearningOverviewResponse.StudentSummary studentSummary(
        CourseLearningAnalyticsRepository.StudentBaseRow student,
        CourseLearningAnalyticsRepository.ProfileRow profile,
        CourseLearningAnalyticsRepository.ProgressRow progress,
        CourseLearningAnalyticsRepository.MasteryRow mastery,
        CourseLearningAnalyticsRepository.AttemptRow attempt,
        CourseLearningAnalyticsRepository.ResourceLearningRow resourceLearning) {
        BigDecimal progressPercent = scale(progress == null ? BigDecimal.ZERO : progress.progressPercent());
        BigDecimal averageMastery = scale(mastery == null ? null : mastery.averageMastery());
        int profileVersion = profile == null ? 0 : profile.versionNo();
        int dimensionCount = profile == null ? 0 : profile.dimensionCount();
        List<String> riskReasons = riskReasons(dimensionCount, progress, averageMastery, attempt);
        String riskLevel = riskReasons.stream().anyMatch(reason -> reason.startsWith("高风险：")) ? "HIGH"
            : riskReasons.isEmpty() ? "LOW" : "MEDIUM";
        List<String> displayReasons = riskReasons.stream().map(reason -> reason.replaceFirst("^(高风险：|中风险：)", ""))
            .toList();
        return new CourseLearningOverviewResponse.StudentSummary(student.studentUserId(), student.studentNo(),
            student.studentName(), student.classId(), student.className(), profileVersion, dimensionCount,
            profile == null ? null : truncate(profile.summary(), 500), progressPercent, averageMastery,
            attempt == null ? 0 : attempt.attemptCount(), latest(
                progress == null ? null : progress.updatedAt(), mastery == null ? null : mastery.updatedAt(),
                attempt == null ? null : attempt.submittedAt(),
                resourceLearning == null ? null : resourceLearning.viewedAt()), riskLevel, displayReasons);
    }

    private List<String> riskReasons(int dimensionCount,
                                     CourseLearningAnalyticsRepository.ProgressRow progress,
                                     BigDecimal mastery,
                                     CourseLearningAnalyticsRepository.AttemptRow attempt) {
        List<String> reasons = new ArrayList<>();
        if (dimensionCount < 6) reasons.add("高风险：学习画像尚未完成");
        if (progress == null) reasons.add("高风险：尚未生成学习路径");
        else if (progress.progressPercent().compareTo(new BigDecimal("30")) < 0) reasons.add("高风险：课程进度低于30%");
        else if (progress.progressPercent().compareTo(new BigDecimal("60")) < 0) reasons.add("中风险：课程进度低于60%");
        if (mastery != null && mastery.compareTo(new BigDecimal("60")) < 0) reasons.add("高风险：平均掌握度低于60%");
        else if (mastery != null && mastery.compareTo(new BigDecimal("75")) < 0) reasons.add("中风险：平均掌握度低于75%");
        if (attempt == null) reasons.add("中风险：尚未完成课程测验");
        return reasons;
    }

    private int riskOrder(CourseLearningOverviewResponse.StudentSummary student) {
        if ("HIGH".equals(student.riskLevel())) return 0;
        if ("MEDIUM".equals(student.riskLevel())) return 1;
        return 2;
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO.setScale(2);
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageNullable(List<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(java.util.Objects::nonNull).toList();
        return present.isEmpty() ? null : average(present);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime latest(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) latest = value;
        }
        return latest;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    private <T> Map<Long, T> byStudent(List<T> rows, java.util.function.Function<T, Long> studentId) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T row : rows) result.put(studentId.apply(row), row);
        return result;
    }
}
