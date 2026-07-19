CREATE TABLE `edu_student_profile` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `current_version` INT NOT NULL DEFAULT 0 COMMENT '当前画像版本',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_student_profile_user` (`student_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像';

CREATE TABLE `edu_student_profile_version` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `profile_id` BIGINT NOT NULL COMMENT '画像ID',
    `version_no` INT NOT NULL COMMENT '版本号',
    `trigger_type` VARCHAR(32) NOT NULL COMMENT '触发类型: DIALOGUE/CORRECTION/ASSESSMENT',
    `change_summary` VARCHAR(1000) NOT NULL COMMENT '变更摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_profile_version` (`profile_id`, `version_no`),
    KEY `idx_edu_profile_version_profile` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像版本';

CREATE TABLE `edu_student_profile_dimension` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `profile_id` BIGINT NOT NULL COMMENT '画像ID',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
    `conclusion` VARCHAR(1000) NOT NULL COMMENT '维度结论',
    `confidence` DECIMAL(5,4) NOT NULL COMMENT '置信度',
    `evidence_summary` VARCHAR(1000) NOT NULL COMMENT '依据摘要',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_profile_dimension` (`profile_id`, `dimension_code`),
    KEY `idx_edu_profile_dimension_profile` (`profile_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像维度';

CREATE TABLE `edu_student_profile_evidence` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `profile_id` BIGINT NOT NULL COMMENT '画像ID',
    `version_id` BIGINT NOT NULL COMMENT '画像版本ID',
    `dimension_code` VARCHAR(64) NOT NULL COMMENT '维度编码',
    `evidence_type` VARCHAR(32) NOT NULL COMMENT '依据类型',
    `evidence_text` VARCHAR(1000) NOT NULL COMMENT '依据内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_edu_profile_evidence_profile` (`profile_id`),
    KEY `idx_edu_profile_evidence_version` (`version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像证据';

CREATE TABLE `edu_student_profile_message` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `profile_id` BIGINT NOT NULL COMMENT '画像ID',
    `message_role` VARCHAR(16) NOT NULL COMMENT '消息角色: USER/ASSISTANT',
    `dimension_code` VARCHAR(64) DEFAULT NULL COMMENT '关联画像维度',
    `content` VARCHAR(1000) NOT NULL COMMENT '消息内容',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_edu_profile_message_profile` (`profile_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像对话消息';
