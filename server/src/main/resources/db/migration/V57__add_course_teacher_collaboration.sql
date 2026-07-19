CREATE TABLE `edu_course_teacher` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `user_id` BIGINT NOT NULL COMMENT '教师用户ID',
    `member_role` VARCHAR(32) NOT NULL COMMENT '成员角色: OWNER/COLLABORATOR',
    `invited_by` BIGINT DEFAULT NULL COMMENT '邀请人用户ID',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_teacher` (`course_id`, `user_id`),
    KEY `idx_edu_course_teacher_user` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程管理教师';

CREATE TABLE `edu_course_invitation` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `token_hash` CHAR(64) NOT NULL COMMENT '邀请令牌SHA-256摘要',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ACCEPTED',
    `invited_by` BIGINT NOT NULL COMMENT '邀请人用户ID',
    `expires_at` DATETIME NOT NULL COMMENT '过期时间',
    `accepted_by` BIGINT DEFAULT NULL COMMENT '接受人用户ID',
    `accepted_at` DATETIME DEFAULT NULL COMMENT '接受时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_invitation_token` (`token_hash`),
    KEY `idx_edu_course_invitation_course` (`course_id`, `status`),
    KEY `idx_edu_course_invitation_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程教师邀请';

UPDATE `edu_course`
SET `created_by` = 8220001, `updated_by` = COALESCE(`updated_by`, 8220001)
WHERE `id` = 8100001 AND `created_by` IS NULL;

INSERT INTO `edu_course_teacher` (`id`, `course_id`, `user_id`, `member_role`, `invited_by`)
SELECT `id`, `id`, `created_by`, 'OWNER', NULL
FROM `edu_course`
WHERE `created_by` IS NOT NULL AND `is_deleted` = 0
ON DUPLICATE KEY UPDATE `member_role` = 'OWNER';
