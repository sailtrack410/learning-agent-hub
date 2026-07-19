package com.workforcehub.course.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CourseLearningAnalyticsRepository {
    private final JdbcTemplate jdbcTemplate;

    public CourseLearningAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CourseRow getTeacherCourse(Long teacherUserId, Long courseId) {
        return jdbcTemplate.query("""
            SELECT course.id, course.course_name
            FROM edu_course course
            JOIN edu_course_teacher teacher ON teacher.course_id = course.id AND teacher.user_id = ?
            WHERE course.id = ? AND course.is_deleted = 0
            """, rs -> rs.next() ? new CourseRow(rs.getLong("id"), rs.getString("course_name")) : null,
            teacherUserId, courseId);
    }

    public List<StudentBaseRow> listStudents(Long courseId) {
        return jdbcTemplate.query("""
            SELECT user.id, user.employee_no, user.real_name, user.primary_dept_id,
                   department.dept_name
            FROM edu_course_enrollment enrollment
            JOIN edu_user_role role ON role.user_id = enrollment.student_user_id
                AND role.education_role = 'STUDENT'
            JOIN org_user user ON user.id = enrollment.student_user_id
                AND user.status = 'ACTIVE' AND user.is_deleted = 0
            LEFT JOIN org_department department ON department.id = user.primary_dept_id
                AND department.status = 'ACTIVE' AND department.is_deleted = 0
            WHERE enrollment.course_id = ? AND enrollment.status = 'ENROLLED'
            ORDER BY department.dept_name, user.employee_no, user.id
            """, (rs, rowNum) -> new StudentBaseRow(
            rs.getLong("id"), rs.getString("employee_no"), rs.getString("real_name"),
            rs.getObject("primary_dept_id", Long.class), rs.getString("dept_name")), courseId);
    }

    public boolean existsEnrolledStudent(Long courseId, Long studentUserId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM edu_course_enrollment enrollment
            JOIN edu_user_role role ON role.user_id = enrollment.student_user_id
                AND role.education_role = 'STUDENT'
            WHERE enrollment.course_id = ? AND enrollment.student_user_id = ?
              AND enrollment.status = 'ENROLLED'
            """, Long.class, courseId, studentUserId);
        return count != null && count > 0;
    }

    public List<Long> listEnrolledStudentIdsByClass(Long courseId, Long classId) {
        return jdbcTemplate.queryForList("""
            SELECT enrollment.student_user_id
            FROM edu_course_enrollment enrollment
            JOIN edu_user_role role ON role.user_id = enrollment.student_user_id
                AND role.education_role = 'STUDENT'
            JOIN org_user user ON user.id = enrollment.student_user_id
                AND user.status = 'ACTIVE' AND user.is_deleted = 0
            WHERE enrollment.course_id = ? AND enrollment.status = 'ENROLLED'
              AND user.primary_dept_id = ?
            ORDER BY user.id
            """, Long.class, courseId, classId);
    }

    public String getActiveClassName(Long classId) {
        return jdbcTemplate.query("""
            SELECT dept_name
            FROM org_department
            WHERE id = ? AND status = 'ACTIVE' AND is_deleted = 0
            """, rs -> rs.next() ? rs.getString("dept_name") : null, classId);
    }

    public List<Long> listCourseTeacherIds(Long courseId) {
        return jdbcTemplate.queryForList("""
            SELECT user_id FROM edu_course_teacher WHERE course_id = ? ORDER BY id
            """, Long.class, courseId);
    }

    public List<ProfileRow> listProfiles(Long courseId) {
        return jdbcTemplate.query("""
            SELECT profile.student_user_id, profile.current_version,
                   COUNT(dimension.id) dimension_count,
                   GROUP_CONCAT(dimension.conclusion ORDER BY dimension.updated_at DESC SEPARATOR '；') profile_summary,
                   MAX(dimension.updated_at) last_profile_at
            FROM edu_student_profile profile
            JOIN edu_course_enrollment enrollment ON enrollment.student_user_id = profile.student_user_id
                AND enrollment.course_id = ? AND enrollment.status = 'ENROLLED'
            LEFT JOIN edu_student_profile_dimension dimension ON dimension.profile_id = profile.id
            WHERE profile.status = 'ACTIVE'
            GROUP BY profile.student_user_id, profile.current_version
            """, (rs, rowNum) -> new ProfileRow(
            rs.getLong("student_user_id"), rs.getInt("current_version"), rs.getInt("dimension_count"),
            rs.getString("profile_summary"), timestamp(rs.getTimestamp("last_profile_at"))), courseId);
    }

    public List<ProgressRow> listProgress(Long courseId) {
        return jdbcTemplate.query("""
            SELECT student_user_id, AVG(progress_percent) progress_percent,
                   MAX(updated_at) last_progress_at
            FROM edu_learning_progress
            WHERE course_id = ?
            GROUP BY student_user_id
            """, (rs, rowNum) -> new ProgressRow(
            rs.getLong("student_user_id"), rs.getBigDecimal("progress_percent"),
            timestamp(rs.getTimestamp("last_progress_at"))), courseId);
    }

    public List<MasteryRow> listMastery(Long courseId) {
        return jdbcTemplate.query("""
            SELECT student_user_id, AVG(mastery_score) average_mastery,
                   MAX(last_assessed_at) last_assessed_at
            FROM edu_student_mastery
            WHERE course_id = ?
            GROUP BY student_user_id
            """, (rs, rowNum) -> new MasteryRow(
            rs.getLong("student_user_id"), rs.getBigDecimal("average_mastery"),
            timestamp(rs.getTimestamp("last_assessed_at"))), courseId);
    }

    public List<AttemptRow> listAttempts(Long courseId) {
        return jdbcTemplate.query("""
            SELECT attempt.student_user_id, COUNT(*) attempt_count, MAX(attempt.submitted_at) last_attempt_at
            FROM edu_assessment_attempt attempt
            JOIN edu_assessment assessment ON assessment.id = attempt.assessment_id
            WHERE assessment.course_id = ? AND attempt.status = 'SUBMITTED'
            GROUP BY attempt.student_user_id
            """, (rs, rowNum) -> new AttemptRow(
            rs.getLong("student_user_id"), rs.getInt("attempt_count"),
            timestamp(rs.getTimestamp("last_attempt_at"))), courseId);
    }

    public List<ResourceLearningRow> listResourceLearning(Long courseId) {
        return jdbcTemplate.query("""
            SELECT record.student_user_id, SUM(record.view_count) view_count,
                   MAX(record.last_viewed_at) last_viewed_at
            FROM edu_resource_learning_record record
            JOIN edu_generated_resource resource ON resource.id = record.resource_id
            WHERE resource.course_id = ?
            GROUP BY record.student_user_id
            """, (rs, rowNum) -> new ResourceLearningRow(
            rs.getLong("student_user_id"), rs.getInt("view_count"),
            timestamp(rs.getTimestamp("last_viewed_at"))), courseId);
    }

    public List<WeakPointRow> listWeakKnowledgePoints(Long courseId) {
        return jdbcTemplate.query("""
            SELECT point.id, point.point_name, AVG(mastery.mastery_score) average_mastery,
                   COUNT(DISTINCT mastery.student_user_id) assessed_student_count,
                   COUNT(DISTINCT CASE WHEN mastery.mastery_score < 60 THEN mastery.student_user_id END) weak_student_count
            FROM edu_knowledge_point point
            LEFT JOIN edu_student_mastery mastery
                ON mastery.course_id = point.course_id AND mastery.knowledge_point_id = point.id
            WHERE point.course_id = ?
            GROUP BY point.id, point.point_name
            ORDER BY CASE WHEN AVG(mastery.mastery_score) IS NULL THEN 1 ELSE 0 END,
                     AVG(mastery.mastery_score), point.sort_order, point.id
            """, (rs, rowNum) -> new WeakPointRow(
            rs.getLong("id"), rs.getString("point_name"), rs.getBigDecimal("average_mastery"),
            rs.getInt("assessed_student_count"), rs.getInt("weak_student_count")), courseId);
    }

    public ResourceStatsRow getResourceStats(Long courseId) {
        return jdbcTemplate.query("""
            SELECT COUNT(DISTINCT resource.id) published_resource_count,
                   COUNT(DISTINCT record.student_user_id) resource_viewer_count,
                   COALESCE(SUM(record.view_count), 0) resource_view_count
            FROM edu_generated_resource resource
            LEFT JOIN edu_resource_learning_record record ON record.resource_id = resource.id
            WHERE resource.course_id = ? AND resource.publication_status = 'PUBLISHED'
            """, rs -> {
            rs.next();
            return new ResourceStatsRow(rs.getInt("published_resource_count"),
                rs.getInt("resource_viewer_count"), rs.getInt("resource_view_count"));
        }, courseId);
    }

    private LocalDateTime timestamp(java.sql.Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record CourseRow(Long courseId, String courseName) {}
    public record StudentBaseRow(Long studentUserId, String studentNo, String studentName,
                                 Long classId, String className) {}
    public record ProfileRow(Long studentUserId, int versionNo, int dimensionCount,
                             String summary, LocalDateTime updatedAt) {}
    public record ProgressRow(Long studentUserId, BigDecimal progressPercent, LocalDateTime updatedAt) {}
    public record MasteryRow(Long studentUserId, BigDecimal averageMastery, LocalDateTime updatedAt) {}
    public record AttemptRow(Long studentUserId, int attemptCount, LocalDateTime submittedAt) {}
    public record ResourceLearningRow(Long studentUserId, int viewCount, LocalDateTime viewedAt) {}
    public record WeakPointRow(Long knowledgePointId, String knowledgePointName, BigDecimal averageMastery,
                               int assessedStudentCount, int weakStudentCount) {}
    public record ResourceStatsRow(int publishedResourceCount, int resourceViewerCount, int resourceViewCount) {}
}
