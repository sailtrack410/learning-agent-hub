package com.workforcehub.learningresource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.util.IdGenerator;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleContentSafetyAdminApiIntegrationTest extends LocalIntegrationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Cookie superAdminSession;
    private Cookie limitedAdminSession;

    @BeforeEach
    void createAdminSessions() throws Exception {
        String superPassword = "A1!" + UUID.randomUUID();
        String superUsername = "content.safety.admin." + UUID.randomUUID();
        long superAdminId = IdGenerator.nextId();
        Long superAdminRoleId = jdbcTemplate.queryForObject(
            "SELECT id FROM admin_role WHERE role_code = 'SUPER_ADMIN'", Long.class);
        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, status, is_deleted)
            VALUES (?, ?, ?, '内容安全管理员', 'ACTIVE', 0)
            """, superAdminId, superUsername, passwordEncoder.encode(superPassword));
        jdbcTemplate.update("""
            INSERT INTO admin_user_role (id, admin_user_id, role_id, created_by)
            VALUES (?, ?, ?, NULL)
            """, IdGenerator.nextId(), superAdminId, superAdminRoleId);
        superAdminSession = login(superUsername, superPassword);

        String limitedPassword = "B2!" + UUID.randomUUID();
        String limitedUsername = "limited.safety." + UUID.randomUUID();
        long limitedId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        jdbcTemplate.update("""
            INSERT INTO admin_user (id, username, password_hash, display_name, status, is_deleted)
            VALUES (?, ?, ?, '受限管理员', 'ACTIVE', 0)
            """, limitedId, limitedUsername, passwordEncoder.encode(limitedPassword));
        limitedAdminSession = login(limitedUsername, limitedPassword);
    }

    @Test
    void shouldAuthenticateAuthorizeUpdateAndAuditContentSafetyPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/content-safety"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/content-safety").cookie(limitedAdminSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        mockMvc.perform(get("/api/admin/content-safety").cookie(superAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.promptInjectionDetectionEnabled").value(true))
            .andExpect(jsonPath("$.data.reviewConfidenceThreshold").value(0.75))
            .andExpect(jsonPath("$.data.versionNo").value(1));

        String update = objectMapper.writeValueAsString(Map.of(
            "promptInjectionDetectionEnabled", true,
            "reviewConfidenceThreshold", 0.90,
            "blockedMarkers", java.util.List.of(" Custom-Danger ", "custom-danger", "越权读取"),
            "expectedVersion", 1
        ));
        mockMvc.perform(put("/api/admin/content-safety").cookie(superAdminSession)
                .contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.reviewConfidenceThreshold").value(0.9))
            .andExpect(jsonPath("$.data.blockedMarkers.length()").value(2))
            .andExpect(jsonPath("$.data.versionNo").value(2));

        mockMvc.perform(put("/api/admin/content-safety").cookie(superAdminSession)
                .contentType(MediaType.APPLICATION_JSON).content(update))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409));
        mockMvc.perform(put("/api/admin/content-safety").cookie(superAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "promptInjectionDetectionEnabled": true,
                      "reviewConfidenceThreshold": 0.75,
                      "blockedMarkers": [],
                      "expectedVersion": 2
                    }
                    """))
            .andExpect(status().isBadRequest());

        org.junit.jupiter.api.Assertions.assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_audit_log
            WHERE action_type = 'CONTENT_SAFETY_POLICY_UPDATED' AND result = 'SUCCESS'
            """, Integer.class));
    }

    private Cookie login(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("loginName", loginName, "password", password))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session);
        return session;
    }
}
