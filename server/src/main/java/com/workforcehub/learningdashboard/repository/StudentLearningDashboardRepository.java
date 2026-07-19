package com.workforcehub.learningdashboard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public class StudentLearningDashboardRepository {
    private final JdbcTemplate jdbcTemplate;

    public StudentLearningDashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CourseRow> listCourses(Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT course.id course_id, course.course_name, course.cover_attachment_ref_id,
                   course.teacher_name, course.total_hours,
                   COALESCE(path_stats.total_nodes, 0) total_nodes,
                   COALESCE(path_stats.completed_nodes, 0) completed_nodes,
                   COALESCE(path_stats.today_task_count, 0) today_task_count,
                   path_stats.current_node_title,
                   mastery.average_mastery,
                   COALESCE(assessment.pending_assessment_count, 0) pending_assessment_count,
                   COALESCE(recommendation.recommendation_count, 0) recommendation_count
            FROM edu_course course
            LEFT JOIN (
                SELECT path.course_id, COUNT(node.id) total_nodes,
                       SUM(CASE WHEN node.node_status = 'COMPLETED' THEN 1 ELSE 0 END) completed_nodes,
                       LEAST(3, SUM(CASE WHEN node.node_status IN ('IN_PROGRESS', 'PENDING') THEN 1 ELSE 0 END)) today_task_count,
                       MAX(CASE WHEN node.node_status = 'IN_PROGRESS' THEN node.resource_title END) current_node_title
                FROM edu_learning_path path
                JOIN edu_learning_path_node node ON node.path_id = path.id
                WHERE path.student_user_id = ? AND path.active_flag = 1 AND path.status = 'ACTIVE'
                GROUP BY path.course_id
            ) path_stats ON path_stats.course_id = course.id
            LEFT JOIN (
                SELECT course_id, AVG(mastery_score) average_mastery
                FROM edu_student_mastery
                WHERE student_user_id = ?
                GROUP BY course_id
            ) mastery ON mastery.course_id = course.id
            LEFT JOIN (
                SELECT item.course_id, COUNT(*) pending_assessment_count
                FROM edu_assessment item
                WHERE item.status = 'PUBLISHED'
                  AND NOT EXISTS (
                      SELECT 1 FROM edu_assessment_attempt attempt
                      WHERE attempt.assessment_id = item.id AND attempt.student_user_id = ?
                        AND attempt.status = 'SUBMITTED'
                  )
                GROUP BY item.course_id
            ) assessment ON assessment.course_id = course.id
            LEFT JOIN (
                SELECT path.course_id, COUNT(recommendation.id) recommendation_count
                FROM edu_learning_path path
                JOIN edu_learning_recommendation recommendation ON recommendation.path_id = path.id
                    AND recommendation.status = 'ACTIVE'
                WHERE path.student_user_id = ? AND path.active_flag = 1 AND path.status = 'ACTIVE'
                GROUP BY path.course_id
            ) recommendation ON recommendation.course_id = course.id
            WHERE course.status = 'PUBLISHED' AND course.is_deleted = 0
            ORDER BY CASE WHEN path_stats.current_node_title IS NULL THEN 1 ELSE 0 END,
                     course.updated_at DESC, course.id DESC
            """, (rs, rowNum) -> new CourseRow(
            rs.getLong("course_id"), rs.getString("course_name"),
            rs.getObject("cover_attachment_ref_id", Long.class), rs.getString("teacher_name"),
            rs.getInt("total_hours"), rs.getInt("total_nodes"), rs.getInt("completed_nodes"),
            rs.getInt("today_task_count"), rs.getString("current_node_title"),
            rs.getBigDecimal("average_mastery"), rs.getInt("pending_assessment_count"),
            rs.getInt("recommendation_count")), studentUserId, studentUserId, studentUserId, studentUserId);
    }

    public List<TodayTaskRow> listTodayTasks(Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT ranked.course_id, ranked.course_name, ranked.node_id, ranked.chapter_id,
                   ranked.chapter_name, ranked.resource_title, ranked.estimated_minutes,
                   ranked.node_status, ranked.recommendation_reason
            FROM (
                SELECT path.course_id, course.course_name, node.id node_id, node.chapter_id,
                       chapter.chapter_name, node.resource_title, node.estimated_minutes,
                       node.node_status, node.recommendation_reason,
                       ROW_NUMBER() OVER (PARTITION BY path.course_id ORDER BY node.sequence_no, node.id) row_no
                FROM edu_learning_path path
                JOIN edu_course course ON course.id = path.course_id
                    AND course.status = 'PUBLISHED' AND course.is_deleted = 0
                JOIN edu_learning_path_node node ON node.path_id = path.id
                    AND node.node_status IN ('IN_PROGRESS', 'PENDING')
                JOIN edu_course_chapter chapter ON chapter.id = node.chapter_id
                WHERE path.student_user_id = ? AND path.active_flag = 1 AND path.status = 'ACTIVE'
            ) ranked
            WHERE ranked.row_no <= 3
            ORDER BY CASE WHEN ranked.node_status = 'IN_PROGRESS' THEN 0 ELSE 1 END,
                     ranked.course_name, ranked.row_no
            LIMIT 8
            """, (rs, rowNum) -> new TodayTaskRow(
            rs.getLong("course_id"), rs.getString("course_name"), rs.getLong("node_id"),
            rs.getLong("chapter_id"), rs.getString("chapter_name"), rs.getString("resource_title"),
            rs.getInt("estimated_minutes"), rs.getString("node_status"),
            rs.getString("recommendation_reason")), studentUserId);
    }

    public List<RecommendationRow> listRecommendations(Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT recommendation.course_id, course.course_name, recommendation.node_id,
                   recommendation.resource_ref_id, recommendation.resource_title,
                   recommendation.priority_no, recommendation.reason
            FROM edu_learning_recommendation recommendation
            JOIN edu_learning_path path ON path.id = recommendation.path_id
                AND path.active_flag = 1 AND path.status = 'ACTIVE'
            JOIN edu_course course ON course.id = recommendation.course_id
                AND course.status = 'PUBLISHED' AND course.is_deleted = 0
            WHERE recommendation.student_user_id = ? AND recommendation.status = 'ACTIVE'
            ORDER BY recommendation.priority_no, recommendation.created_at, recommendation.id
            LIMIT 8
            """, (rs, rowNum) -> new RecommendationRow(
            rs.getLong("course_id"), rs.getString("course_name"), rs.getLong("node_id"),
            rs.getLong("resource_ref_id"), rs.getString("resource_title"),
            rs.getInt("priority_no"), rs.getString("reason")), studentUserId);
    }

    public BigDecimal getAverageMastery(Long studentUserId) {
        return jdbcTemplate.queryForObject("""
            SELECT AVG(mastery.mastery_score)
            FROM edu_student_mastery mastery
            JOIN edu_course course ON course.id = mastery.course_id
                AND course.status = 'PUBLISHED' AND course.is_deleted = 0
            WHERE mastery.student_user_id = ?
            """, BigDecimal.class, studentUserId);
    }

    public record CourseRow(Long courseId, String courseName, Long coverAttachmentRefId,
                            String teacherName, int totalHours, int totalNodes, int completedNodes,
                            int todayTaskCount, String currentNodeTitle, BigDecimal averageMastery,
                            int pendingAssessmentCount, int recommendationCount) {}
    public record TodayTaskRow(Long courseId, String courseName, Long nodeId, Long chapterId,
                               String chapterName, String resourceTitle, int estimatedMinutes,
                               String status, String recommendationReason) {}
    public record RecommendationRow(Long courseId, String courseName, Long nodeId, Long resourceRefId,
                                    String resourceTitle, int priorityNo, String reason) {}
}
