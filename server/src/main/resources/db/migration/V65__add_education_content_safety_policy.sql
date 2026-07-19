CREATE TABLE `edu_content_safety_policy` (
    `id` BIGINT NOT NULL COMMENT '固定策略ID',
    `prompt_injection_detection_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用提示词注入拦截',
    `review_confidence_threshold` DECIMAL(5,4) NOT NULL DEFAULT 0.7500 COMMENT '低于该置信度时转教师复核',
    `blocked_markers_json` JSON NOT NULL COMMENT '提示词注入和越权指令标记',
    `version_no` INT NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    `updated_by` BIGINT DEFAULT NULL COMMENT '最后更新管理员ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教育内容安全策略';

INSERT INTO `edu_content_safety_policy` (
    id, prompt_injection_detection_enabled, review_confidence_threshold,
    blocked_markers_json, version_no, updated_by
) VALUES (
    1, 1, 0.7500,
    JSON_ARRAY(
        'ignore previous', 'ignore all previous', 'system prompt', 'developer message', 'tool call',
        '忽略之前', '忽略以上', '系统提示词', '开发者消息', '调用工具', '越权调用',
        'drop table', 'truncate table', '删除数据库'
    ),
    1, NULL
) ON DUPLICATE KEY UPDATE id = VALUES(id);

INSERT INTO `admin_permission` (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
) VALUES (
    7012, 'ADMIN_CONTENT_SAFETY_MANAGE', '内容安全策略管理', 'CONTENT_SAFETY', 'MANAGE',
    'ACTIVE', NULL, NULL, 0
) ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_code = VALUES(module_code),
    action_code = VALUES(action_code),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO `admin_role_permission` (id, role_id, permission_id, created_by)
SELECT 6112, role.id, permission.id, NULL
FROM admin_role role
JOIN admin_permission permission ON permission.perm_code = 'ADMIN_CONTENT_SAFETY_MANAGE'
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.is_deleted = 0
  AND permission.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM admin_role_permission existing
      WHERE existing.role_id = role.id
        AND existing.permission_id = permission.id
  );
