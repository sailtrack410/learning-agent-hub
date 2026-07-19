package com.workforcehub.auth;

import com.workforcehub.auth.service.EducationAdminBootstrap;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("api")
class EducationAdminBootstrapIntegrationTest extends LocalIntegrationTestSupport {
    @Autowired
    private EducationAdminBootstrap bootstrap;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldCreateOneSuperAdminWithoutResettingExistingPassword() {
        jdbcTemplate.update("DELETE FROM admin_user_role");
        jdbcTemplate.update("DELETE FROM admin_user");
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "education.admin." + suffix;
        String originalPassword = "A1!" + UUID.randomUUID();
        String replacementPassword = "B2!" + UUID.randomUUID();

        Long firstId = bootstrap.initialize(username, originalPassword, "教育运营管理员");
        Long secondId = bootstrap.initialize(username, replacementPassword, "另一个名称");

        assertEquals(firstId, secondId);
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_user WHERE username = ? AND status = 'ACTIVE' AND is_deleted = 0",
            Integer.class, username));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_user_role relation
            JOIN admin_role role ON role.id = relation.role_id
            WHERE relation.admin_user_id = ? AND role.role_code = 'SUPER_ADMIN'
            """, Integer.class, firstId));
        String passwordHash = jdbcTemplate.queryForObject(
            "SELECT password_hash FROM admin_user WHERE id = ?", String.class, firstId);
        assertTrue(passwordEncoder.matches(originalPassword, passwordHash));
        assertFalse(passwordEncoder.matches(replacementPassword, passwordHash));
    }

    @Test
    void shouldRejectCreatingAnotherAdminWhenTheSystemAlreadyHasOne() {
        String username = "another.education.admin." + UUID.randomUUID();

        assertThrows(IllegalStateException.class,
            () -> bootstrap.initialize(username, "A1!" + UUID.randomUUID(), "另一个管理员"));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_user WHERE username = ?", Integer.class, username));
    }

    @Test
    void shouldNotElevateAnExistingLimitedAdmin() {
        String username = "limited.education.admin." + UUID.randomUUID();
        long adminUserId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, status, is_deleted)
            VALUES (?, ?, ?, '受限管理员', 'ACTIVE', 0)
            """, adminUserId, username, passwordEncoder.encode("A1!" + UUID.randomUUID()));

        assertThrows(IllegalStateException.class,
            () -> bootstrap.initialize(username, "B2!" + UUID.randomUUID(), "受限管理员"));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_user_role WHERE admin_user_id = ?", Integer.class, adminUserId));
    }

    @Test
    void shouldRejectMissingBootstrapCredentials() {
        assertThrows(IllegalStateException.class, () -> bootstrap.initialize("", "", "教育运营管理员"));
    }
}
