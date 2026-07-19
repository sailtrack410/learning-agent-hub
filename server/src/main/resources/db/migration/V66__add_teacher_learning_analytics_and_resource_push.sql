CREATE TABLE `edu_generated_resource_push` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `resource_id` BIGINT NOT NULL COMMENT '已发布生成资源ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型: STUDENT/CLASS',
    `target_id` BIGINT NOT NULL COMMENT '学生用户ID或班级部门ID',
    `pushed_by` BIGINT NOT NULL COMMENT '推送教师用户ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/REVOKED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_resource_push_target` (`resource_id`, `target_type`, `target_id`),
    KEY `idx_edu_resource_push_course` (`course_id`, `status`, `created_at`),
    KEY `idx_edu_resource_push_target` (`target_type`, `target_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生成学习资源定向推送';

CREATE TABLE `edu_resource_learning_record` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `resource_id` BIGINT NOT NULL COMMENT '生成资源ID',
    `student_user_id` BIGINT NOT NULL COMMENT '查看资源的学生用户ID',
    `view_count` INT NOT NULL DEFAULT 1 COMMENT '查看次数',
    `first_viewed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_viewed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_resource_learning_student` (`resource_id`, `student_user_id`),
    KEY `idx_edu_resource_learning_student` (`student_user_id`, `last_viewed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生生成资源学习记录';
