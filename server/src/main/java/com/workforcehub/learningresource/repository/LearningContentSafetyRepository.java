package com.workforcehub.learningresource.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class LearningContentSafetyRepository {
    private static final long POLICY_ID = 1L;
    private final JdbcTemplate jdbcTemplate;

    public LearningContentSafetyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PolicyRow getPolicy() {
        List<PolicyRow> rows = jdbcTemplate.query("""
            SELECT prompt_injection_detection_enabled, review_confidence_threshold,
                   blocked_markers_json, version_no, updated_at
            FROM edu_content_safety_policy
            WHERE id = ?
            """, (rs, rowNum) -> new PolicyRow(
            rs.getBoolean("prompt_injection_detection_enabled"),
            rs.getBigDecimal("review_confidence_threshold"),
            rs.getString("blocked_markers_json"),
            rs.getInt("version_no"),
            rs.getTimestamp("updated_at").toLocalDateTime()
        ), POLICY_ID);
        if (rows.isEmpty()) throw new IllegalStateException("教育内容安全策略未初始化");
        return rows.get(0);
    }

    public int updatePolicy(boolean detectionEnabled, BigDecimal reviewThreshold, String markersJson,
                            int expectedVersion, Long updatedBy) {
        return jdbcTemplate.update("""
            UPDATE edu_content_safety_policy
            SET prompt_injection_detection_enabled = ?,
                review_confidence_threshold = ?,
                blocked_markers_json = CAST(? AS JSON),
                version_no = version_no + 1,
                updated_by = ?
            WHERE id = ? AND version_no = ?
            """, detectionEnabled, reviewThreshold, markersJson, updatedBy, POLICY_ID, expectedVersion);
    }

    public record PolicyRow(boolean detectionEnabled, BigDecimal reviewThreshold, String markersJson,
                            int versionNo, LocalDateTime updatedAt) {}
}
