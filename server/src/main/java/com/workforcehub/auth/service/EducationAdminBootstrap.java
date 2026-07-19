package com.workforcehub.auth.service;

import com.workforcehub.common.util.IdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 仅在显式启用时创建首个教育运营管理员；已有账号不会被重置密码。
 */
@Component
public class EducationAdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String username;
    private final String password;
    private final String displayName;

    public EducationAdminBootstrap(JdbcTemplate jdbcTemplate,
                                   PasswordEncoder passwordEncoder,
                                   @Value("${workforce.auth.admin-bootstrap.enabled:false}") boolean enabled,
                                   @Value("${workforce.auth.admin-bootstrap.username:}") String username,
                                   @Value("${workforce.auth.admin-bootstrap.password:}") String password,
                                   @Value("${workforce.auth.admin-bootstrap.display-name:教育运营管理员}") String displayName) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enabled) initialize(username, password, displayName);
    }

    @Transactional
    public Long initialize(String requestedUsername, String requestedPassword, String requestedDisplayName) {
        String normalizedUsername = required(requestedUsername, "管理员登录名不能为空");
        if (!StringUtils.hasText(requestedPassword)) throw new IllegalStateException("管理员初始密码不能为空");
        String normalizedPassword = requestedPassword;
        if (normalizedUsername.length() > 128) throw new IllegalStateException("管理员登录名不能超过128个字符");
        if (normalizedPassword.length() < 8 || normalizedPassword.length() > 64) {
            throw new IllegalStateException("管理员初始密码长度必须为8到64个字符");
        }
        String normalizedDisplayName = StringUtils.hasText(requestedDisplayName)
            ? requestedDisplayName.trim() : "教育运营管理员";
        if (normalizedDisplayName.length() > 64) throw new IllegalStateException("管理员显示名称不能超过64个字符");

        Long superAdminRoleId = findSuperAdminRoleId();
        List<AdminAccountState> accounts = jdbcTemplate.query("""
            SELECT id, username, status, is_deleted
            FROM admin_user
            ORDER BY id
            FOR UPDATE
            """, (rs, rowNum) -> new AdminAccountState(
            rs.getLong("id"), rs.getString("username"), rs.getString("status"),
            rs.getInt("is_deleted") != 0));

        if (accounts.isEmpty()) {
            Long adminUserId = IdGenerator.nextId();
            jdbcTemplate.update("""
                INSERT INTO admin_user
                    (id, username, password_hash, display_name, status, created_by, updated_by, is_deleted)
                VALUES (?, ?, ?, ?, 'ACTIVE', NULL, NULL, 0)
                """, adminUserId, normalizedUsername, passwordEncoder.encode(normalizedPassword), normalizedDisplayName);
            jdbcTemplate.update("""
                INSERT INTO admin_user_role (id, admin_user_id, role_id, created_by)
                VALUES (?, ?, ?, NULL)
                """, IdGenerator.nextId(), adminUserId, superAdminRoleId);
            return adminUserId;
        }

        Optional<AdminAccountState> requestedAccount = accounts.stream()
            .filter(account -> account.username().equals(normalizedUsername))
            .findFirst();
        if (requestedAccount.isEmpty()) {
            throw new IllegalStateException("系统已存在管理员账号，不能再次执行首个管理员初始化");
        }

        AdminAccountState account = requestedAccount.get();
        if (account.deleted()) throw new IllegalStateException("同名管理员账号已被删除，不能自动恢复");
        if (!"ACTIVE".equals(account.status())) throw new IllegalStateException("同名管理员账号不是启用状态");
        if (!hasRole(account.id(), superAdminRoleId)) {
            throw new IllegalStateException("已有管理员账号不能通过初始化提升为超级管理员");
        }
        return account.id();
    }

    private Long findSuperAdminRoleId() {
        List<Long> roleIds = jdbcTemplate.query("""
            SELECT id
            FROM admin_role
            WHERE role_code = 'SUPER_ADMIN'
              AND status = 'ACTIVE'
              AND is_deleted = 0
            LIMIT 1
            """, (rs, rowNum) -> rs.getLong("id"));
        if (roleIds.isEmpty()) throw new IllegalStateException("未找到有效的超级管理员角色");
        return roleIds.get(0);
    }

    private boolean hasRole(Long adminUserId, Long roleId) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM admin_user_role
            WHERE admin_user_id = ? AND role_id = ?
            """, Integer.class, adminUserId, roleId);
        return count != null && count > 0;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalStateException(message);
        return value.trim();
    }

    private record AdminAccountState(Long id, String username, String status, boolean deleted) {}
}
