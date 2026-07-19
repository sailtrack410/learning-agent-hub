package com.workforcehub.profile.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class StudentProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public StudentProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureProfile(Long profileId, Long studentUserId) {
        jdbcTemplate.update("""
            INSERT INTO edu_student_profile (id, student_user_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE student_user_id = VALUES(student_user_id)
            """, profileId, studentUserId);
    }

    public ProfileRow lockProfile(Long studentUserId) {
        return jdbcTemplate.query("""
            SELECT id, student_user_id, current_version
            FROM edu_student_profile
            WHERE student_user_id = ? AND status = 'ACTIVE'
            FOR UPDATE
            """, rs -> rs.next() ? new ProfileRow(
            rs.getLong("id"), rs.getLong("student_user_id"), rs.getInt("current_version")) : null,
            studentUserId);
    }

    public List<DimensionRow> listDimensions(Long profileId) {
        return jdbcTemplate.query("""
            SELECT dimension_code, conclusion, confidence, evidence_summary, updated_at
            FROM edu_student_profile_dimension
            WHERE profile_id = ?
            ORDER BY created_at, id
            """, (rs, rowNum) -> new DimensionRow(
            rs.getString("dimension_code"), rs.getString("conclusion"), rs.getBigDecimal("confidence"),
            rs.getString("evidence_summary"), rs.getTimestamp("updated_at").toLocalDateTime()), profileId);
    }

    public List<MessageRow> listMessages(Long profileId) {
        return jdbcTemplate.query("""
            SELECT id, message_role, dimension_code, content, created_at
            FROM edu_student_profile_message
            WHERE profile_id = ?
            ORDER BY created_at, id
            """, (rs, rowNum) -> new MessageRow(
            rs.getLong("id"), rs.getString("message_role"), rs.getString("dimension_code"),
            rs.getString("content"), rs.getTimestamp("created_at").toLocalDateTime()), profileId);
    }

    public void insertMessage(Long id, Long profileId, String role, String dimensionCode, String content) {
        jdbcTemplate.update("""
            INSERT INTO edu_student_profile_message (id, profile_id, message_role, dimension_code, content)
            VALUES (?, ?, ?, ?, ?)
            """, id, profileId, role, dimensionCode, content);
    }

    public void upsertDimension(Long id, Long profileId, String dimensionCode, String conclusion,
                                BigDecimal confidence, String evidenceSummary) {
        jdbcTemplate.update("""
            INSERT INTO edu_student_profile_dimension
                (id, profile_id, dimension_code, conclusion, confidence, evidence_summary)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                conclusion = VALUES(conclusion),
                confidence = VALUES(confidence),
                evidence_summary = VALUES(evidence_summary),
                updated_at = NOW()
            """, id, profileId, dimensionCode, conclusion, confidence, evidenceSummary);
    }

    public void insertVersion(Long id, Long profileId, int versionNo, String triggerType, String changeSummary) {
        jdbcTemplate.update("""
            INSERT INTO edu_student_profile_version (id, profile_id, version_no, trigger_type, change_summary)
            VALUES (?, ?, ?, ?, ?)
            """, id, profileId, versionNo, triggerType, changeSummary);
    }

    public void insertEvidence(Long id, Long profileId, Long versionId, String dimensionCode,
                               String evidenceType, String evidenceText) {
        jdbcTemplate.update("""
            INSERT INTO edu_student_profile_evidence
                (id, profile_id, version_id, dimension_code, evidence_type, evidence_text)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, profileId, versionId, dimensionCode, evidenceType, evidenceText);
    }

    public void updateCurrentVersion(Long profileId, int versionNo) {
        jdbcTemplate.update("""
            UPDATE edu_student_profile SET current_version = ? WHERE id = ?
            """, versionNo, profileId);
    }

    public record ProfileRow(Long id, Long studentUserId, int currentVersion) {
    }

    public record DimensionRow(String dimensionCode, String conclusion, BigDecimal confidence,
                               String evidenceSummary, LocalDateTime updatedAt) {
    }

    public record MessageRow(Long id, String role, String dimensionCode, String content,
                             LocalDateTime createdAt) {
    }
}
