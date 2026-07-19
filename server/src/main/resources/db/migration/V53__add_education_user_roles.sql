CREATE TABLE `edu_user_role` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `education_role` VARCHAR(32) NOT NULL COMMENT '教育角色: STUDENT/TEACHER',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_user_role_user` (`user_id`),
    KEY `idx_edu_user_role_role` (`education_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教育用户角色';

INSERT INTO `edu_user_role` (id, user_id, education_role) VALUES
    (8140001, 2002, 'TEACHER'),
    (8140002, 2003, 'STUDENT'),
    (8140003, 2004, 'STUDENT')
ON DUPLICATE KEY UPDATE education_role = VALUES(education_role);
