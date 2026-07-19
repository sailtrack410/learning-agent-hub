package com.workforcehub.assessment.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AssessmentRepository {
    private final JdbcTemplate jdbcTemplate;

    public AssessmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AssessmentRow> listPublished(Long courseId) {
        return jdbcTemplate.query("""
            SELECT a.id, a.course_id, a.title, a.description, a.total_score, a.duration_minutes,
                   COUNT(q.id) question_count
            FROM edu_assessment a
            JOIN edu_course c ON c.id = a.course_id AND c.status = 'PUBLISHED' AND c.is_deleted = 0
            LEFT JOIN edu_assessment_question q ON q.assessment_id = a.id
            WHERE a.course_id = ? AND a.status = 'PUBLISHED'
            GROUP BY a.id, a.course_id, a.title, a.description, a.total_score, a.duration_minutes
            ORDER BY a.created_at, a.id
            """, (rs, rowNum) -> new AssessmentRow(
            rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), rs.getString("description"),
            rs.getInt("total_score"), rs.getInt("duration_minutes"), rs.getInt("question_count")), courseId);
    }

    public AssessmentRow lockPublished(Long courseId, Long assessmentId) {
        return jdbcTemplate.query("""
            SELECT a.id, a.course_id, a.title, a.description, a.total_score, a.duration_minutes,
                   (SELECT COUNT(*) FROM edu_assessment_question q WHERE q.assessment_id = a.id) question_count
            FROM edu_assessment a
            JOIN edu_course c ON c.id = a.course_id AND c.status = 'PUBLISHED' AND c.is_deleted = 0
            WHERE a.id = ? AND a.course_id = ? AND a.status = 'PUBLISHED'
            FOR UPDATE
            """, rs -> rs.next() ? new AssessmentRow(
            rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), rs.getString("description"),
            rs.getInt("total_score"), rs.getInt("duration_minutes"), rs.getInt("question_count")) : null,
            assessmentId, courseId);
    }

    public AssessmentRow getPublished(Long courseId, Long assessmentId) {
        return jdbcTemplate.query("""
            SELECT a.id, a.course_id, a.title, a.description, a.total_score, a.duration_minutes,
                   (SELECT COUNT(*) FROM edu_assessment_question q WHERE q.assessment_id = a.id) question_count
            FROM edu_assessment a
            JOIN edu_course c ON c.id = a.course_id AND c.status = 'PUBLISHED' AND c.is_deleted = 0
            WHERE a.id = ? AND a.course_id = ? AND a.status = 'PUBLISHED'
            """, rs -> rs.next() ? new AssessmentRow(
            rs.getLong("id"), rs.getLong("course_id"), rs.getString("title"), rs.getString("description"),
            rs.getInt("total_score"), rs.getInt("duration_minutes"), rs.getInt("question_count")) : null,
            assessmentId, courseId);
    }

    public List<QuestionRow> listQuestions(Long assessmentId) {
        return jdbcTemplate.query("""
            SELECT q.id, q.assessment_id, q.knowledge_point_id, p.point_name, q.question_type,
                   q.prompt, q.options_json, q.correct_answer_json, q.analysis, q.score, q.sort_order
            FROM edu_assessment_question q
            JOIN edu_knowledge_point p ON p.id = q.knowledge_point_id
            WHERE q.assessment_id = ?
            ORDER BY q.sort_order, q.id
            """, (rs, rowNum) -> new QuestionRow(
            rs.getLong("id"), rs.getLong("assessment_id"), rs.getLong("knowledge_point_id"),
            rs.getString("point_name"), rs.getString("question_type"), rs.getString("prompt"),
            rs.getString("options_json"), rs.getString("correct_answer_json"), rs.getString("analysis"),
            rs.getInt("score"), rs.getInt("sort_order")), assessmentId);
    }

    public AttemptKeyRow findAttemptByIdempotencyKey(Long studentUserId, String idempotencyKey) {
        return jdbcTemplate.query("""
            SELECT id, assessment_id FROM edu_assessment_attempt
            WHERE student_user_id = ? AND idempotency_key = ?
            """, rs -> rs.next() ? new AttemptKeyRow(
            rs.getLong("id"), rs.getLong("assessment_id")) : null, studentUserId, idempotencyKey);
    }

    public int nextAttemptNo(Long studentUserId, Long assessmentId) {
        Integer value = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(attempt_no), 0) + 1
            FROM edu_assessment_attempt
            WHERE student_user_id = ? AND assessment_id = ?
            """, Integer.class, studentUserId, assessmentId);
        return value == null ? 1 : value;
    }

    public void insertAttempt(Long id, Long assessmentId, Long studentUserId, String idempotencyKey,
                              int attemptNo, int totalScore, int earnedScore, int durationSeconds) {
        jdbcTemplate.update("""
            INSERT INTO edu_assessment_attempt
                (id, assessment_id, student_user_id, idempotency_key, attempt_no, total_score,
                 earned_score, duration_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, assessmentId, studentUserId, idempotencyKey, attemptNo, totalScore,
            earnedScore, durationSeconds);
    }

    public void insertAnswer(Long id, Long attemptId, Long questionId, String answerJson, boolean correct,
                             int earnedScore, String errorType, int durationSeconds) {
        jdbcTemplate.update("""
            INSERT INTO edu_assessment_answer
                (id, attempt_id, question_id, answer_json, is_correct, earned_score, error_type, duration_seconds)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, attemptId, questionId, answerJson, correct, earnedScore, errorType, durationSeconds);
    }

    public void updateMastery(Long id, Long studentUserId, Long courseId, Long knowledgePointId, boolean correct) {
        int correctIncrement = correct ? 1 : 0;
        jdbcTemplate.update("""
            INSERT INTO edu_student_mastery
                (id, student_user_id, course_id, knowledge_point_id, mastery_score, attempt_count, correct_count)
            VALUES (?, ?, ?, ?, ?, 1, ?)
            ON DUPLICATE KEY UPDATE
                mastery_score = ROUND((correct_count + ?) * 100.0 / (attempt_count + 1), 2),
                attempt_count = attempt_count + 1,
                correct_count = correct_count + ?,
                last_assessed_at = NOW()
            """, id, studentUserId, courseId, knowledgePointId,
            correct ? new BigDecimal("100.00") : BigDecimal.ZERO, correctIncrement,
            correctIncrement, correctIncrement);
    }

    public AttemptRow getAttempt(Long attemptId, Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT id, assessment_id, attempt_no, total_score, earned_score, duration_seconds, submitted_at
            FROM edu_assessment_attempt
            WHERE id = ? AND student_user_id = ?
            """, rs -> rs.next() ? new AttemptRow(
            rs.getLong("id"), rs.getLong("assessment_id"), rs.getInt("attempt_no"),
            rs.getInt("total_score"), rs.getInt("earned_score"), rs.getInt("duration_seconds"),
            rs.getTimestamp("submitted_at").toLocalDateTime()) : null, attemptId, studentUserId);
    }

    public List<AnswerRow> listAnswers(Long attemptId) {
        return jdbcTemplate.query("""
            SELECT question_id, answer_json, is_correct, earned_score, error_type, duration_seconds
            FROM edu_assessment_answer WHERE attempt_id = ? ORDER BY id
            """, (rs, rowNum) -> new AnswerRow(
            rs.getLong("question_id"), rs.getString("answer_json"), rs.getBoolean("is_correct"),
            rs.getInt("earned_score"), rs.getString("error_type"), rs.getInt("duration_seconds")), attemptId);
    }

    public List<MasteryRow> listMastery(Long studentUserId, Long courseId, List<Long> knowledgePointIds) {
        if (knowledgePointIds.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(knowledgePointIds.size(), "?"));
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(studentUserId);
        parameters.add(courseId);
        parameters.addAll(knowledgePointIds);
        return jdbcTemplate.query("""
            SELECT m.knowledge_point_id, p.point_name, m.mastery_score, m.attempt_count, m.correct_count
            FROM edu_student_mastery m
            JOIN edu_knowledge_point p ON p.id = m.knowledge_point_id
            WHERE m.student_user_id = ? AND m.course_id = ?
              AND m.knowledge_point_id IN (%s)
            ORDER BY p.sort_order, p.id
            """.formatted(placeholders), (rs, rowNum) -> new MasteryRow(
            rs.getLong("knowledge_point_id"), rs.getString("point_name"), rs.getBigDecimal("mastery_score"),
            rs.getInt("attempt_count"), rs.getInt("correct_count")), parameters.toArray());
    }

    public List<MasteryRow> listCourseMastery(Long studentUserId, Long courseId) {
        return jdbcTemplate.query("""
            SELECT m.knowledge_point_id, p.point_name, m.mastery_score, m.attempt_count, m.correct_count
            FROM edu_student_mastery m
            JOIN edu_knowledge_point p ON p.id = m.knowledge_point_id
            WHERE m.student_user_id = ? AND m.course_id = ?
            ORDER BY p.sort_order, p.id
            """, (rs, rowNum) -> new MasteryRow(
            rs.getLong("knowledge_point_id"), rs.getString("point_name"), rs.getBigDecimal("mastery_score"),
            rs.getInt("attempt_count"), rs.getInt("correct_count")), studentUserId, courseId);
    }

    public record AssessmentRow(Long id, Long courseId, String title, String description,
                                int totalScore, int durationMinutes, int questionCount) {
    }

    public record QuestionRow(Long id, Long assessmentId, Long knowledgePointId, String knowledgePointName,
                              String questionType, String prompt, String optionsJson, String correctAnswerJson,
                              String analysis, int score, int sortOrder) {
    }

    public record AttemptRow(Long id, Long assessmentId, int attemptNo, int totalScore, int earnedScore,
                             int durationSeconds, LocalDateTime submittedAt) {
    }

    public record AttemptKeyRow(Long attemptId, Long assessmentId) {
    }

    public record AnswerRow(Long questionId, String answerJson, boolean correct, int earnedScore,
                            String errorType, int durationSeconds) {
    }

    public record MasteryRow(Long knowledgePointId, String knowledgePointName, BigDecimal masteryScore,
                             int attemptCount, int correctCount) {
    }
}
