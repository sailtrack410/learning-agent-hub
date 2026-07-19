package com.workforcehub.learningpath.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class LearningPathRepository {
    private final JdbcTemplate jdbcTemplate;

    public LearningPathRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PathRow getActivePath(Long studentUserId, Long courseId) {
        return jdbcTemplate.query("""
            SELECT id, student_user_id, course_id, profile_version_no, source_assessment_attempt_id,
                   version_no, generation_reason, created_at
            FROM edu_learning_path
            WHERE student_user_id = ? AND course_id = ? AND active_flag = 1 AND status = 'ACTIVE'
            """, rs -> rs.next() ? new PathRow(
            rs.getLong("id"), rs.getLong("student_user_id"), rs.getLong("course_id"),
            rs.getInt("profile_version_no"), rs.getObject("source_assessment_attempt_id", Long.class),
            rs.getInt("version_no"), rs.getString("generation_reason"),
            rs.getTimestamp("created_at").toLocalDateTime()) : null, studentUserId, courseId);
    }

    public int nextVersionNo(Long studentUserId, Long courseId) {
        Integer version = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM edu_learning_path WHERE student_user_id = ? AND course_id = ?
            """, Integer.class, studentUserId, courseId);
        return version == null ? 1 : version;
    }

    public void supersedeActive(Long studentUserId, Long courseId) {
        jdbcTemplate.update("""
            UPDATE edu_learning_path
            SET status = 'SUPERSEDED', active_flag = NULL
            WHERE student_user_id = ? AND course_id = ? AND active_flag = 1
            """, studentUserId, courseId);
    }

    public void insertPath(Long pathId, Long studentUserId, Long courseId, int profileVersionNo,
                           Long sourceAssessmentAttemptId, int versionNo, String generationReason) {
        jdbcTemplate.update("""
            INSERT INTO edu_learning_path
                (id, student_user_id, course_id, profile_version_no, source_assessment_attempt_id,
                 version_no, status, active_flag, generation_reason)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 1, ?)
            """, pathId, studentUserId, courseId, profileVersionNo, sourceAssessmentAttemptId,
            versionNo, generationReason);
    }

    public void insertPathIfAbsent(Long pathId, Long studentUserId, Long courseId, int profileVersionNo,
                                   int versionNo, String generationReason) {
        jdbcTemplate.update("""
            INSERT INTO edu_learning_path
                (id, student_user_id, course_id, profile_version_no, version_no, status, active_flag, generation_reason)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 1, ?)
            ON DUPLICATE KEY UPDATE id = id
            """, pathId, studentUserId, courseId, profileVersionNo, versionNo, generationReason);
    }

    public void insertNode(Long nodeId, Long pathId, Long knowledgePointId, Long chapterId, int sequenceNo,
                           String learningObjective, String resourceType, Long resourceRefId,
                           String resourceTitle, int estimatedMinutes, String completionCondition,
                           String nodeStatus, BigDecimal masteryScore, String recommendationReason) {
        jdbcTemplate.update("""
            INSERT INTO edu_learning_path_node
                (id, path_id, knowledge_point_id, chapter_id, sequence_no, learning_objective,
                 resource_type, resource_ref_id, resource_title, estimated_minutes, completion_condition,
                 node_status, mastery_score_snapshot, recommendation_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, nodeId, pathId, knowledgePointId, chapterId, sequenceNo, learningObjective,
            resourceType, resourceRefId, resourceTitle, estimatedMinutes, completionCondition,
            nodeStatus, masteryScore, recommendationReason);
    }

    public void insertRecommendation(Long recommendationId, Long pathId, Long nodeId, Long studentUserId,
                                     Long courseId, Long knowledgePointId, String resourceType,
                                     Long resourceRefId, String resourceTitle, int priorityNo,
                                     String reason, String status) {
        jdbcTemplate.update("""
            INSERT INTO edu_learning_recommendation
                (id, path_id, node_id, student_user_id, course_id, knowledge_point_id, resource_type,
                 resource_ref_id, resource_title, priority_no, reason, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, recommendationId, pathId, nodeId, studentUserId, courseId, knowledgePointId,
            resourceType, resourceRefId, resourceTitle, priorityNo, reason, status);
    }

    public Map<Long, ProgressRow> listProgress(Long studentUserId, Long courseId) {
        Map<Long, ProgressRow> progress = new LinkedHashMap<>();
        jdbcTemplate.query("""
            SELECT knowledge_point_id, latest_path_node_id, progress_status, progress_percent,
                   completion_source, completed_at
            FROM edu_learning_progress
            WHERE student_user_id = ? AND course_id = ?
            """, rs -> {
            progress.put(rs.getLong("knowledge_point_id"), new ProgressRow(
                rs.getLong("knowledge_point_id"), rs.getObject("latest_path_node_id", Long.class),
                rs.getString("progress_status"), rs.getInt("progress_percent"),
                rs.getString("completion_source"),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime()));
        },
            studentUserId, courseId);
        return progress;
    }

    public void upsertProgress(Long progressId, Long studentUserId, Long courseId, Long knowledgePointId,
                               Long nodeId, String status, int progressPercent, String completionSource) {
        jdbcTemplate.update("""
            INSERT INTO edu_learning_progress
                (id, student_user_id, course_id, knowledge_point_id, latest_path_node_id,
                 progress_status, progress_percent, completion_source, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'COMPLETED' THEN NOW() ELSE NULL END)
            ON DUPLICATE KEY UPDATE
                latest_path_node_id = VALUES(latest_path_node_id),
                progress_status = VALUES(progress_status),
                progress_percent = VALUES(progress_percent),
                completion_source = VALUES(completion_source),
                completed_at = CASE
                    WHEN VALUES(progress_status) = 'COMPLETED' THEN COALESCE(completed_at, NOW())
                    ELSE NULL
                END
            """, progressId, studentUserId, courseId, knowledgePointId, nodeId, status,
            progressPercent, completionSource, status);
    }

    public List<NodeRow> listNodes(Long pathId) {
        return jdbcTemplate.query("""
            SELECT n.id, n.path_id, n.knowledge_point_id, p.point_name, n.chapter_id, c.chapter_name,
                   n.sequence_no, n.learning_objective, n.resource_type, n.resource_ref_id,
                   n.resource_title, n.estimated_minutes, n.completion_condition, n.node_status,
                   n.mastery_score_snapshot, n.recommendation_reason
            FROM edu_learning_path_node n
            JOIN edu_knowledge_point p ON p.id = n.knowledge_point_id
            JOIN edu_course_chapter c ON c.id = n.chapter_id
            WHERE n.path_id = ?
            ORDER BY n.sequence_no
            """, (rs, rowNum) -> new NodeRow(
            rs.getLong("id"), rs.getLong("path_id"), rs.getLong("knowledge_point_id"),
            rs.getString("point_name"), rs.getLong("chapter_id"), rs.getString("chapter_name"),
            rs.getInt("sequence_no"), rs.getString("learning_objective"), rs.getString("resource_type"),
            rs.getLong("resource_ref_id"), rs.getString("resource_title"),
            rs.getInt("estimated_minutes"), rs.getString("completion_condition"),
            rs.getString("node_status"), rs.getBigDecimal("mastery_score_snapshot"),
            rs.getString("recommendation_reason")), pathId);
    }

    public List<RecommendationRow> listRecommendations(Long pathId) {
        return jdbcTemplate.query("""
            SELECT r.id, r.node_id, r.knowledge_point_id, p.point_name, r.resource_type,
                   r.resource_ref_id, r.resource_title, r.priority_no, r.reason, r.status
            FROM edu_learning_recommendation r
            JOIN edu_knowledge_point p ON p.id = r.knowledge_point_id
            WHERE r.path_id = ?
            ORDER BY CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END, r.priority_no, r.id
            """, (rs, rowNum) -> new RecommendationRow(
            rs.getLong("id"), rs.getLong("node_id"), rs.getLong("knowledge_point_id"),
            rs.getString("point_name"), rs.getString("resource_type"), rs.getLong("resource_ref_id"),
            rs.getString("resource_title"), rs.getInt("priority_no"), rs.getString("reason"),
            rs.getString("status")), pathId);
    }

    public NodeAccessRow lockActiveNode(Long studentUserId, Long courseId, Long nodeId) {
        return jdbcTemplate.query("""
            SELECT n.id, n.path_id, n.knowledge_point_id, n.node_status
            FROM edu_learning_path_node n
            JOIN edu_learning_path path ON path.id = n.path_id
            WHERE n.id = ? AND path.student_user_id = ? AND path.course_id = ?
              AND path.active_flag = 1 AND path.status = 'ACTIVE'
            FOR UPDATE
            """, rs -> rs.next() ? new NodeAccessRow(
            rs.getLong("id"), rs.getLong("path_id"), rs.getLong("knowledge_point_id"),
            rs.getString("node_status")) : null, nodeId, studentUserId, courseId);
    }

    public void completeNode(Long pathId, Long nodeId) {
        jdbcTemplate.update("""
            UPDATE edu_learning_path_node SET node_status = 'COMPLETED'
            WHERE id = ? AND path_id = ? AND node_status = 'IN_PROGRESS'
            """, nodeId, pathId);
        jdbcTemplate.update("""
            UPDATE edu_learning_recommendation SET status = 'COMPLETED'
            WHERE node_id = ? AND path_id = ?
            """, nodeId, pathId);
    }

    public NodeAccessRow activateNextNode(Long pathId) {
        NodeAccessRow next = jdbcTemplate.query("""
            SELECT id, path_id, knowledge_point_id, node_status
            FROM edu_learning_path_node
            WHERE path_id = ? AND node_status IN ('PENDING', 'LOCKED')
            ORDER BY sequence_no LIMIT 1
            """, rs -> rs.next() ? new NodeAccessRow(
            rs.getLong("id"), rs.getLong("path_id"), rs.getLong("knowledge_point_id"),
            rs.getString("node_status")) : null, pathId);
        if (next != null) {
            jdbcTemplate.update("""
                UPDATE edu_learning_path_node SET node_status = 'IN_PROGRESS' WHERE id = ? AND path_id = ?
                """, next.nodeId(), pathId);
        }
        return next;
    }

    public void deleteCoursePaths(Long courseId) {
        jdbcTemplate.update("""
            DELETE recommendation FROM edu_learning_recommendation recommendation
            JOIN edu_learning_path path ON path.id = recommendation.path_id
            WHERE path.course_id = ?
            """, courseId);
        jdbcTemplate.update("""
            DELETE node FROM edu_learning_path_node node
            JOIN edu_learning_path path ON path.id = node.path_id
            WHERE path.course_id = ?
            """, courseId);
        jdbcTemplate.update("DELETE FROM edu_learning_path WHERE course_id = ?", courseId);
        jdbcTemplate.update("DELETE FROM edu_learning_progress WHERE course_id = ?", courseId);
    }

    public record PathRow(Long pathId, Long studentUserId, Long courseId, int profileVersionNo,
                          Long sourceAssessmentAttemptId, int versionNo, String generationReason,
                          LocalDateTime createdAt) {
    }

    public record NodeRow(Long nodeId, Long pathId, Long knowledgePointId, String knowledgePointName,
                          Long chapterId, String chapterName, int sequenceNo, String learningObjective,
                          String resourceType, Long resourceRefId, String resourceTitle,
                          int estimatedMinutes, String completionCondition, String nodeStatus,
                          BigDecimal masteryScore, String recommendationReason) {
    }

    public record RecommendationRow(Long recommendationId, Long nodeId, Long knowledgePointId,
                                    String knowledgePointName, String resourceType, Long resourceRefId,
                                    String resourceTitle, int priorityNo, String reason, String status) {
    }

    public record ProgressRow(Long knowledgePointId, Long latestPathNodeId, String status,
                              int progressPercent, String completionSource, LocalDateTime completedAt) {
    }

    public record NodeAccessRow(Long nodeId, Long pathId, Long knowledgePointId, String status) {
    }
}
