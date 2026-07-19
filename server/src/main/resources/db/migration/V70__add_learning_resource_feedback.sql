CREATE TABLE `edu_resource_feedback` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `resource_id` BIGINT NOT NULL COMMENT '生成资源ID',
    `student_user_id` BIGINT NOT NULL COMMENT '反馈学生用户ID',
    `feedback_type` VARCHAR(32) NOT NULL COMMENT '反馈类型: HELPFUL/TOO_DIFFICULT/TOO_EASY/NOT_RELEVANT',
    `feedback_comment` VARCHAR(500) DEFAULT NULL COMMENT '脱敏后的补充说明',
    `profile_version_no` INT DEFAULT NULL COMMENT '反馈触发的画像版本',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_resource_feedback_student` (`resource_id`, `student_user_id`),
    KEY `idx_edu_resource_feedback_student` (`student_user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生学习资源反馈';
