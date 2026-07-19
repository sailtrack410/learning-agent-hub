package com.workforcehub.learningresource.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LearningResourceRepository {
    private final JdbcTemplate jdbcTemplate;

    public LearningResourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CoursePointRow getPublishedCoursePoint(Long courseId, Long knowledgePointId) {
        List<CoursePointRow> rows = jdbcTemplate.query("""
            SELECT course.id course_id, course.course_name, chapter.id chapter_id, chapter.chapter_name,
                   point.id knowledge_point_id, point.point_name, point.summary point_summary
            FROM edu_course course
            JOIN edu_knowledge_point point ON point.course_id = course.id AND point.id = ?
            JOIN edu_course_chapter chapter ON chapter.id = point.chapter_id AND chapter.course_id = course.id
            WHERE course.id = ? AND course.status = 'PUBLISHED' AND course.is_deleted = 0
              AND chapter.status = 'PUBLISHED'
            """, (rs, rowNum) -> new CoursePointRow(
            rs.getLong("course_id"), rs.getString("course_name"),
            rs.getLong("chapter_id"), rs.getString("chapter_name"),
            rs.getLong("knowledge_point_id"), rs.getString("point_name"), rs.getString("point_summary")
        ), knowledgePointId, courseId);
        if (rows.isEmpty()) throw new BizException(404, "课程或知识点不存在");
        return rows.get(0);
    }

    public void insertTask(Long taskId, Long studentUserId, CoursePointRow context, int profileVersionNo,
                           String profileSnapshotJson, String idempotencyKey, String requestMessage) {
        jdbcTemplate.update("""
            INSERT INTO edu_resource_generation_task
                (id, student_user_id, course_id, chapter_id, knowledge_point_id,
                 profile_version_no, profile_snapshot_json, idempotency_key, request_message)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, taskId, studentUserId, context.courseId(), context.chapterId(), context.knowledgePointId(),
            profileVersionNo, profileSnapshotJson, idempotencyKey, requestMessage);
    }

    public TaskRow findTaskByIdempotency(Long studentUserId, String idempotencyKey) {
        List<TaskRow> rows = jdbcTemplate.query(taskSelect() + """
            WHERE task.student_user_id = ? AND task.idempotency_key = ?
            """, this::mapTask, studentUserId, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public TaskRow getTask(Long taskId) {
        List<TaskRow> rows = jdbcTemplate.query(taskSelect() + " WHERE task.id = ?", this::mapTask, taskId);
        if (rows.isEmpty()) throw new BizException(404, "资源生成任务不存在");
        return rows.get(0);
    }

    public TaskRow lockTask(Long taskId) {
        List<TaskRow> rows = jdbcTemplate.query(taskSelect() + " WHERE task.id = ? FOR UPDATE", this::mapTask, taskId);
        if (rows.isEmpty()) throw new BizException(404, "资源生成任务不存在");
        return rows.get(0);
    }

    public List<TaskRow> listStudentTasks(Long studentUserId, Long courseId) {
        if (courseId == null) {
            return jdbcTemplate.query(taskSelect() + """
                WHERE task.student_user_id = ? ORDER BY task.created_at DESC, task.id DESC
                """, this::mapTask, studentUserId);
        }
        return jdbcTemplate.query(taskSelect() + """
            WHERE task.student_user_id = ? AND task.course_id = ?
            ORDER BY task.created_at DESC, task.id DESC
            """, this::mapTask, studentUserId, courseId);
    }

    public List<TaskRow> listTeacherTasks(Long teacherUserId, Long courseId, String status) {
        StringBuilder sql = new StringBuilder(taskSelect()).append("""
            JOIN edu_course_teacher teacher ON teacher.course_id = task.course_id AND teacher.user_id = ?
            WHERE 1 = 1
            """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(teacherUserId);
        if (courseId != null) {
            sql.append(" AND task.course_id = ?");
            args.add(courseId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND task.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY task.created_at DESC, task.id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapTask, args.toArray());
    }

    public int markRunning(Long taskId, int attemptNo, String modelName) {
        return jdbcTemplate.update("""
            UPDATE edu_resource_generation_task
            SET status = 'RUNNING', progress_percent = 5, current_role_code = 'PROFILE_ANALYST',
                model_name = ?, failure_type = NULL, failure_message = NULL,
                total_duration_ms = NULL, started_at = NOW(), finished_at = NULL
            WHERE id = ? AND attempt_no = ? AND status = 'PENDING'
            """, modelName, taskId, attemptNo);
    }

    public void updateProgress(Long taskId, int progress, String roleCode) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_task
            SET progress_percent = ?, current_role_code = ?
            WHERE id = ? AND status = 'RUNNING'
            """, progress, roleCode, taskId);
    }

    public void markSucceeded(Long taskId, long durationMs) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_task
            SET status = 'SUCCEEDED', progress_percent = 100, current_role_code = NULL,
                total_duration_ms = ?, finished_at = NOW()
            WHERE id = ? AND status = 'RUNNING'
            """, durationMs, taskId);
    }

    public void markFailed(Long taskId, String failureType, String failureMessage, long durationMs) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_task
            SET status = 'FAILED', current_role_code = NULL, failure_type = ?, failure_message = ?,
                total_duration_ms = ?, finished_at = NOW()
            WHERE id = ? AND status = 'RUNNING'
            """, failureType, failureMessage, durationMs, taskId);
    }

    public int restartFailedTask(Long taskId) {
        return jdbcTemplate.update("""
            UPDATE edu_resource_generation_task
            SET status = 'PENDING', progress_percent = 0, current_role_code = NULL,
                attempt_no = attempt_no + 1, failure_type = NULL, failure_message = NULL,
                total_duration_ms = NULL, started_at = NULL, finished_at = NULL
            WHERE id = ? AND status = 'FAILED'
            """, taskId);
    }

    public void insertRunningStep(Long stepId, Long taskId, int attemptNo, String roleCode,
                                  String roleName, int stepOrder, String inputSummary, String modelName) {
        jdbcTemplate.update("""
            INSERT INTO edu_resource_generation_step
                (id, task_id, attempt_no, role_code, role_name, step_order, status,
                 input_summary, model_name, started_at)
            VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, NOW())
            """, stepId, taskId, attemptNo, roleCode, roleName, stepOrder, inputSummary, modelName);
    }

    public void finishStep(Long stepId, String outputSummary, long durationMs) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_step
            SET status = 'SUCCEEDED', output_summary = ?, live_output = NULL,
                duration_ms = ?, finished_at = NOW()
            WHERE id = ? AND status = 'RUNNING'
            """, outputSummary, durationMs, stepId);
    }

    public void updateStepLiveOutput(Long stepId, String liveOutput) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_step
            SET live_output = ?
            WHERE id = ? AND status = 'RUNNING'
            """, liveOutput, stepId);
    }

    public void failStep(Long stepId, String failureMessage, long durationMs) {
        jdbcTemplate.update("""
            UPDATE edu_resource_generation_step
            SET status = 'FAILED', failure_message = ?, duration_ms = ?, finished_at = NOW()
            WHERE id = ? AND status = 'RUNNING'
            """, failureMessage, durationMs, stepId);
    }

    public List<StepRow> listSteps(Long taskId, int attemptNo) {
        return jdbcTemplate.query("""
            SELECT id, task_id, attempt_no, role_code, role_name, step_order, status,
                   input_summary, output_summary, live_output, model_name, duration_ms, failure_message,
                   started_at, finished_at
            FROM edu_resource_generation_step
            WHERE task_id = ? AND attempt_no = ?
            ORDER BY step_order
            """, this::mapStep, taskId, attemptNo);
    }

    public void insertResource(Long resourceId, Long taskId, Long studentUserId, CoursePointRow context,
                               String resourceType, String title, String summary, String content,
                               String contentFormat, String validationStatus, String validationReason,
                               BigDecimal confidence) {
        jdbcTemplate.update("""
            INSERT INTO edu_generated_resource
                (id, task_id, student_user_id, course_id, chapter_id, knowledge_point_id,
                 resource_type, title, summary, content, content_format, validation_status,
                 validation_reason, confidence, publication_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT')
            """, resourceId, taskId, studentUserId, context.courseId(), context.chapterId(),
            context.knowledgePointId(), resourceType, title, summary, content, contentFormat,
            validationStatus, validationReason, confidence);
    }

    public void insertCitation(Long citationId, Long resourceId, Long documentId, Long chunkId,
                               String documentTitle, String excerpt, Integer pageNo, BigDecimal score) {
        jdbcTemplate.update("""
            INSERT INTO edu_generated_resource_citation
                (id, resource_id, kb_document_id, kb_chunk_id, document_title, excerpt, page_no, relevance_score)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, citationId, resourceId, documentId, chunkId, documentTitle, excerpt, pageNo, score);
    }

    public List<ResourceRow> listTaskResources(Long taskId) {
        return jdbcTemplate.query(resourceSelect() + " WHERE resource.task_id = ? ORDER BY resource.created_at, resource.id",
            this::mapResource, taskId);
    }

    public ResourceRow getResource(Long resourceId) {
        List<ResourceRow> rows = jdbcTemplate.query(resourceSelect() + " WHERE resource.id = ?", this::mapResource, resourceId);
        if (rows.isEmpty()) throw new BizException(404, "学习资源不存在");
        return rows.get(0);
    }

    public ResourceRow lockResource(Long resourceId) {
        List<ResourceRow> rows = jdbcTemplate.query(
            resourceSelect() + " WHERE resource.id = ? FOR UPDATE", this::mapResource, resourceId);
        if (rows.isEmpty()) throw new BizException(404, "学习资源不存在");
        return rows.get(0);
    }

    public List<ResourceRow> listStudentResources(Long studentUserId, Long courseId, String resourceType) {
        StringBuilder sql = new StringBuilder(resourceSelect()).append("""
            WHERE resource.publication_status = 'PUBLISHED'
              AND (
                resource.student_user_id = ?
                OR EXISTS (
                    SELECT 1
                    FROM edu_generated_resource_push push_record
                    WHERE push_record.resource_id = resource.id AND push_record.status = 'ACTIVE'
                      AND EXISTS (
                          SELECT 1 FROM edu_course_enrollment enrollment
                          WHERE enrollment.course_id = resource.course_id
                            AND enrollment.student_user_id = ? AND enrollment.status = 'ENROLLED'
                      )
                      AND (
                        (push_record.target_type = 'STUDENT' AND push_record.target_id = ?)
                        OR (push_record.target_type = 'CLASS' AND push_record.target_id = (
                            SELECT user.primary_dept_id FROM org_user user
                            WHERE user.id = ? AND user.status = 'ACTIVE' AND user.is_deleted = 0
                        ))
                      )
                )
              )
            """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(studentUserId);
        args.add(studentUserId);
        args.add(studentUserId);
        args.add(studentUserId);
        if (courseId != null) {
            sql.append(" AND resource.course_id = ?");
            args.add(courseId);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource.resource_type = ?");
            args.add(resourceType);
        }
        sql.append(" ORDER BY resource.published_at DESC, resource.created_at DESC");
        return jdbcTemplate.query(sql.toString(), this::mapResource, args.toArray());
    }

    public boolean canStudentAccessResource(Long studentUserId, Long resourceId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM edu_generated_resource resource
            WHERE resource.id = ? AND resource.publication_status = 'PUBLISHED'
              AND (
                resource.student_user_id = ?
                OR EXISTS (
                    SELECT 1
                    FROM edu_generated_resource_push push_record
                    WHERE push_record.resource_id = resource.id AND push_record.status = 'ACTIVE'
                      AND EXISTS (
                          SELECT 1 FROM edu_course_enrollment enrollment
                          WHERE enrollment.course_id = resource.course_id
                            AND enrollment.student_user_id = ? AND enrollment.status = 'ENROLLED'
                      )
                      AND (
                        (push_record.target_type = 'STUDENT' AND push_record.target_id = ?)
                        OR (push_record.target_type = 'CLASS' AND push_record.target_id = (
                            SELECT user.primary_dept_id FROM org_user user
                            WHERE user.id = ? AND user.status = 'ACTIVE' AND user.is_deleted = 0
                        ))
                      )
                )
              )
            """, Integer.class, resourceId, studentUserId, studentUserId, studentUserId, studentUserId);
        return count != null && count > 0;
    }

    public PushTargetRow getPushTarget(Long courseId, String targetType, Long targetId) {
        if ("STUDENT".equals(targetType)) {
            return jdbcTemplate.query("""
                SELECT user.id, user.real_name
                FROM org_user user
                JOIN edu_user_role role ON role.user_id = user.id AND role.education_role = 'STUDENT'
                JOIN edu_course_enrollment enrollment ON enrollment.student_user_id = user.id
                    AND enrollment.course_id = ? AND enrollment.status = 'ENROLLED'
                WHERE user.id = ? AND user.status = 'ACTIVE' AND user.is_deleted = 0
                """, rs -> rs.next() ? new PushTargetRow(rs.getLong("id"), rs.getString("real_name")) : null,
                courseId, targetId);
        }
        if ("CLASS".equals(targetType)) {
            return jdbcTemplate.query("""
                SELECT department.id, department.dept_name
                FROM org_department department
                WHERE department.id = ? AND department.status = 'ACTIVE' AND department.is_deleted = 0
                  AND EXISTS (
                      SELECT 1 FROM org_user user
                      JOIN edu_user_role role ON role.user_id = user.id AND role.education_role = 'STUDENT'
                      JOIN edu_course_enrollment enrollment ON enrollment.student_user_id = user.id
                          AND enrollment.course_id = ? AND enrollment.status = 'ENROLLED'
                      WHERE user.primary_dept_id = department.id AND user.status = 'ACTIVE' AND user.is_deleted = 0
                  )
                """, rs -> rs.next() ? new PushTargetRow(rs.getLong("id"), rs.getString("dept_name")) : null,
                targetId, courseId);
        }
        return null;
    }

    public void upsertResourcePush(Long pushId, Long resourceId, Long courseId, String targetType,
                                   Long targetId, Long teacherUserId) {
        jdbcTemplate.update("""
            INSERT INTO edu_generated_resource_push
                (id, resource_id, course_id, target_type, target_id, pushed_by, status)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
            ON DUPLICATE KEY UPDATE
                pushed_by = VALUES(pushed_by), status = 'ACTIVE', updated_at = NOW()
            """, pushId, resourceId, courseId, targetType, targetId, teacherUserId);
    }

    public ResourcePushRow getResourcePush(Long resourceId, String targetType, Long targetId) {
        return jdbcTemplate.query("""
            SELECT id, resource_id, course_id, target_type, target_id, status, created_at
            FROM edu_generated_resource_push
            WHERE resource_id = ? AND target_type = ? AND target_id = ?
            """, rs -> rs.next() ? new ResourcePushRow(
            rs.getLong("id"), rs.getLong("resource_id"), rs.getLong("course_id"),
            rs.getString("target_type"), rs.getLong("target_id"), rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime()) : null, resourceId, targetType, targetId);
    }

    public void recordResourceView(Long recordId, Long resourceId, Long studentUserId) {
        jdbcTemplate.update("""
            INSERT INTO edu_resource_learning_record
                (id, resource_id, student_user_id, view_count, first_viewed_at, last_viewed_at)
            VALUES (?, ?, ?, 1, NOW(), NOW())
            ON DUPLICATE KEY UPDATE view_count = view_count + 1, last_viewed_at = NOW()
            """, recordId, resourceId, studentUserId);
    }

    public int upsertResourceFeedback(Long feedbackId, Long resourceId, Long studentUserId,
                                      String feedbackType, String comment) {
        return jdbcTemplate.update("""
            INSERT INTO edu_resource_feedback
                (id, resource_id, student_user_id, feedback_type, feedback_comment)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                feedback_type = VALUES(feedback_type),
                feedback_comment = VALUES(feedback_comment)
            """, feedbackId, resourceId, studentUserId, feedbackType, comment);
    }

    public void updateFeedbackProfileVersion(Long resourceId, Long studentUserId, int profileVersionNo) {
        jdbcTemplate.update("""
            UPDATE edu_resource_feedback
            SET profile_version_no = ?
            WHERE resource_id = ? AND student_user_id = ?
            """, profileVersionNo, resourceId, studentUserId);
    }

    public ResourceFeedbackRow getResourceFeedback(Long resourceId, Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT id, resource_id, student_user_id, feedback_type, feedback_comment,
                   profile_version_no, created_at, updated_at
            FROM edu_resource_feedback
            WHERE resource_id = ? AND student_user_id = ?
            """, rs -> rs.next() ? new ResourceFeedbackRow(
            rs.getLong("id"), rs.getLong("resource_id"), rs.getLong("student_user_id"),
            rs.getString("feedback_type"), rs.getString("feedback_comment"),
            rs.getObject("profile_version_no", Integer.class),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()) : null, resourceId, studentUserId);
    }

    public List<ResourceRow> listTeacherResources(Long teacherUserId, Long courseId, Long knowledgePointId,
                                                  String resourceType, String publicationStatus) {
        StringBuilder sql = new StringBuilder(resourceSelect()).append("""
            JOIN edu_course_teacher teacher ON teacher.course_id = resource.course_id AND teacher.user_id = ?
            WHERE 1 = 1
            """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(teacherUserId);
        if (courseId != null) {
            sql.append(" AND resource.course_id = ?");
            args.add(courseId);
        }
        if (knowledgePointId != null) {
            sql.append(" AND resource.knowledge_point_id = ?");
            args.add(knowledgePointId);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource.resource_type = ?");
            args.add(resourceType);
        }
        if (publicationStatus != null && !publicationStatus.isBlank()) {
            sql.append(" AND resource.publication_status = ?");
            args.add(publicationStatus);
        }
        sql.append(" ORDER BY resource.created_at DESC, resource.id DESC");
        return jdbcTemplate.query(sql.toString(), this::mapResource, args.toArray());
    }

    public List<CitationRow> listCitations(Long resourceId) {
        return jdbcTemplate.query("""
            SELECT kb_document_id, kb_chunk_id, document_title, excerpt, page_no, relevance_score
            FROM edu_generated_resource_citation
            WHERE resource_id = ? ORDER BY relevance_score DESC, id
            """, (rs, rowNum) -> new CitationRow(
            rs.getLong("kb_document_id"), rs.getLong("kb_chunk_id"), rs.getString("document_title"),
            rs.getString("excerpt"), rs.getObject("page_no", Integer.class), rs.getBigDecimal("relevance_score")
        ), resourceId);
    }

    public int reviewResource(Long resourceId, Long teacherUserId, String decision, String note) {
        String validationStatus = "APPROVE".equals(decision) ? "PASSED" : "REJECTED";
        String publicationStatus = "APPROVE".equals(decision) ? "DRAFT" : "REJECTED";
        return jdbcTemplate.update("""
            UPDATE edu_generated_resource resource
            JOIN edu_course_teacher teacher ON teacher.course_id = resource.course_id AND teacher.user_id = ?
            SET resource.validation_status = ?, resource.publication_status = ?,
                resource.review_note = ?, resource.reviewed_by = ?, resource.reviewed_at = NOW()
            WHERE resource.id = ? AND resource.publication_status IN ('DRAFT', 'REJECTED')
            """, teacherUserId, validationStatus, publicationStatus, note, teacherUserId, resourceId);
    }

    public int updateResource(Long resourceId, Long teacherUserId, String title, String summary, String content) {
        return jdbcTemplate.update("""
            UPDATE edu_generated_resource resource
            JOIN edu_course_teacher teacher ON teacher.course_id = resource.course_id AND teacher.user_id = ?
            SET resource.title = ?, resource.summary = ?, resource.content = ?,
                resource.validation_status = 'NEEDS_REVIEW',
                resource.validation_reason = '教师修改后需重新确认内容与引用一致。',
                resource.publication_status = 'DRAFT', resource.review_note = NULL,
                resource.reviewed_by = NULL, resource.reviewed_at = NULL, resource.published_at = NULL
            WHERE resource.id = ? AND resource.publication_status <> 'PUBLISHED'
            """, teacherUserId, title, summary, content, resourceId);
    }

    public int publishResource(Long resourceId, Long teacherUserId) {
        return jdbcTemplate.update("""
            UPDATE edu_generated_resource resource
            JOIN edu_course_teacher teacher ON teacher.course_id = resource.course_id AND teacher.user_id = ?
            SET resource.publication_status = 'PUBLISHED', resource.published_at = NOW()
            WHERE resource.id = ? AND resource.publication_status IN ('DRAFT', 'WITHDRAWN')
              AND resource.validation_status = 'PASSED' AND resource.reviewed_at IS NOT NULL
            """, teacherUserId, resourceId);
    }

    public int withdrawResource(Long resourceId, Long teacherUserId) {
        return jdbcTemplate.update("""
            UPDATE edu_generated_resource resource
            JOIN edu_course_teacher teacher ON teacher.course_id = resource.course_id AND teacher.user_id = ?
            SET resource.publication_status = 'WITHDRAWN'
            WHERE resource.id = ? AND resource.publication_status = 'PUBLISHED'
            """, teacherUserId, resourceId);
    }

    private String taskSelect() {
        return """
            SELECT task.id, task.student_user_id, task.course_id, course.course_name,
                   task.chapter_id, chapter.chapter_name, task.knowledge_point_id, point.point_name,
                   task.profile_version_no, task.profile_snapshot_json, task.request_message, task.status, task.progress_percent,
                   task.current_role_code, task.attempt_no, task.model_name, task.failure_type,
                   task.failure_message, task.total_duration_ms, task.started_at, task.finished_at,
                   task.created_at, task.updated_at
            FROM edu_resource_generation_task task
            JOIN edu_course course ON course.id = task.course_id
            JOIN edu_course_chapter chapter ON chapter.id = task.chapter_id
            JOIN edu_knowledge_point point ON point.id = task.knowledge_point_id
            """;
    }

    private String resourceSelect() {
        return """
            SELECT resource.id, resource.task_id, resource.student_user_id, resource.course_id,
                   course.course_name, resource.chapter_id, chapter.chapter_name,
                   resource.knowledge_point_id, point.point_name, resource.resource_type,
                   resource.title, resource.summary, resource.content, resource.content_format,
                   resource.validation_status, resource.validation_reason, resource.confidence,
                   resource.publication_status, resource.review_note, resource.reviewed_at,
                   resource.published_at, resource.created_at, resource.updated_at
            FROM edu_generated_resource resource
            JOIN edu_course course ON course.id = resource.course_id
            JOIN edu_course_chapter chapter ON chapter.id = resource.chapter_id
            JOIN edu_knowledge_point point ON point.id = resource.knowledge_point_id
            """;
    }

    private TaskRow mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRow(
            rs.getLong("id"), rs.getLong("student_user_id"), rs.getLong("course_id"), rs.getString("course_name"),
            rs.getLong("chapter_id"), rs.getString("chapter_name"), rs.getLong("knowledge_point_id"),
            rs.getString("point_name"), rs.getInt("profile_version_no"), rs.getString("profile_snapshot_json"),
            rs.getString("request_message"),
            rs.getString("status"), rs.getInt("progress_percent"), rs.getString("current_role_code"),
            rs.getInt("attempt_no"), rs.getString("model_name"), rs.getString("failure_type"),
            rs.getString("failure_message"), rs.getObject("total_duration_ms", Long.class),
            timestamp(rs, "started_at"), timestamp(rs, "finished_at"),
            rs.getTimestamp("created_at").toLocalDateTime(), rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private StepRow mapStep(ResultSet rs, int rowNum) throws SQLException {
        return new StepRow(
            rs.getLong("id"), rs.getLong("task_id"), rs.getInt("attempt_no"), rs.getString("role_code"),
            rs.getString("role_name"), rs.getInt("step_order"), rs.getString("status"),
            rs.getString("input_summary"), rs.getString("output_summary"), rs.getString("live_output"),
            rs.getString("model_name"),
            rs.getObject("duration_ms", Long.class), rs.getString("failure_message"),
            timestamp(rs, "started_at"), timestamp(rs, "finished_at")
        );
    }

    private ResourceRow mapResource(ResultSet rs, int rowNum) throws SQLException {
        return new ResourceRow(
            rs.getLong("id"), rs.getLong("task_id"), rs.getLong("student_user_id"),
            rs.getLong("course_id"), rs.getString("course_name"), rs.getLong("chapter_id"),
            rs.getString("chapter_name"), rs.getLong("knowledge_point_id"), rs.getString("point_name"),
            rs.getString("resource_type"), rs.getString("title"), rs.getString("summary"),
            rs.getString("content"), rs.getString("content_format"), rs.getString("validation_status"),
            rs.getString("validation_reason"), rs.getBigDecimal("confidence"),
            rs.getString("publication_status"), rs.getString("review_note"), timestamp(rs, "reviewed_at"),
            timestamp(rs, "published_at"), rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

    private LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column) == null ? null : rs.getTimestamp(column).toLocalDateTime();
    }

    public record CoursePointRow(Long courseId, String courseName, Long chapterId, String chapterName,
                                 Long knowledgePointId, String knowledgePointName, String knowledgePointSummary) {
    }

    public record TaskRow(Long id, Long studentUserId, Long courseId, String courseName, Long chapterId,
                          String chapterName, Long knowledgePointId, String knowledgePointName,
                          int profileVersionNo, String profileSnapshotJson, String requestMessage,
                          String status, int progressPercent,
                          String currentRoleCode, int attemptNo, String modelName, String failureType,
                          String failureMessage, Long totalDurationMs, LocalDateTime startedAt,
                          LocalDateTime finishedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record StepRow(Long id, Long taskId, int attemptNo, String roleCode, String roleName,
                          int stepOrder, String status, String inputSummary, String outputSummary,
                          String liveOutput, String modelName, Long durationMs, String failureMessage,
                          LocalDateTime startedAt, LocalDateTime finishedAt) {
    }

    public record ResourceRow(Long id, Long taskId, Long studentUserId, Long courseId, String courseName,
                              Long chapterId, String chapterName, Long knowledgePointId,
                              String knowledgePointName, String resourceType, String title, String summary,
                              String content, String contentFormat, String validationStatus,
                              String validationReason, BigDecimal confidence, String publicationStatus,
                              String reviewNote, LocalDateTime reviewedAt, LocalDateTime publishedAt,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record CitationRow(Long documentId, Long chunkId, String documentTitle, String excerpt,
                              Integer pageNo, BigDecimal relevanceScore) {
    }

    public record PushTargetRow(Long targetId, String targetName) {}

    public record ResourcePushRow(Long pushId, Long resourceId, Long courseId, String targetType,
                                  Long targetId, String status, LocalDateTime createdAt) {}

    public record ResourceFeedbackRow(Long feedbackId, Long resourceId, Long studentUserId,
                                      String feedbackType, String comment, Integer profileVersionNo,
                                      LocalDateTime createdAt, LocalDateTime updatedAt) {}
}
