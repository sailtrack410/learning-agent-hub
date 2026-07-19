package com.workforcehub.course.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EducationRoleRepository {
    private final JdbcTemplate jdbcTemplate;

    public EducationRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String findRoleByUserId(Long userId) {
        return jdbcTemplate.query("""
            SELECT education_role FROM edu_user_role WHERE user_id = ?
            """, rs -> rs.next() ? rs.getString("education_role") : null, userId);
    }
}
