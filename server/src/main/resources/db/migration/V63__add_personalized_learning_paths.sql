CREATE TABLE `edu_learning_path` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `profile_version_no` INT NOT NULL DEFAULT 0 COMMENT '生成时画像版本',
    `source_assessment_attempt_id` BIGINT DEFAULT NULL COMMENT '触发重排的作答ID',
    `version_no` INT NOT NULL COMMENT '路径版本',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/SUPERSEDED',
    `active_flag` TINYINT DEFAULT 1 COMMENT '当前版本标识，历史版本为NULL',
    `generation_reason` VARCHAR(1000) NOT NULL COMMENT '生成或调整原因',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_path_version` (`student_user_id`, `course_id`, `version_no`),
    UNIQUE KEY `uk_edu_path_active` (`student_user_id`, `course_id`, `active_flag`),
    KEY `idx_edu_path_course` (`course_id`, `student_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个性化学习路径';

CREATE TABLE `edu_learning_path_node` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `path_id` BIGINT NOT NULL COMMENT '学习路径ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `chapter_id` BIGINT NOT NULL COMMENT '课程章节ID',
    `sequence_no` INT NOT NULL COMMENT '路径顺序',
    `learning_objective` VARCHAR(1000) NOT NULL COMMENT '学习目标',
    `resource_type` VARCHAR(32) NOT NULL COMMENT '推荐资源类型',
    `resource_ref_id` BIGINT NOT NULL COMMENT '推荐资源关联ID',
    `resource_title` VARCHAR(256) NOT NULL COMMENT '推荐资源标题',
    `estimated_minutes` INT NOT NULL COMMENT '预计时长分钟',
    `completion_condition` VARCHAR(500) NOT NULL COMMENT '完成条件',
    `node_status` VARCHAR(32) NOT NULL COMMENT '状态: LOCKED/PENDING/IN_PROGRESS/COMPLETED',
    `mastery_score_snapshot` DECIMAL(5,2) DEFAULT NULL COMMENT '生成时掌握度',
    `recommendation_reason` VARCHAR(1000) NOT NULL COMMENT '推荐理由',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_path_node_sequence` (`path_id`, `sequence_no`),
    UNIQUE KEY `uk_edu_path_node_point` (`path_id`, `knowledge_point_id`),
    KEY `idx_edu_path_node_status` (`path_id`, `node_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习路径节点';

CREATE TABLE `edu_learning_recommendation` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `path_id` BIGINT NOT NULL COMMENT '学习路径ID',
    `node_id` BIGINT NOT NULL COMMENT '路径节点ID',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `resource_type` VARCHAR(32) NOT NULL COMMENT '资源类型',
    `resource_ref_id` BIGINT NOT NULL COMMENT '资源关联ID',
    `resource_title` VARCHAR(256) NOT NULL COMMENT '资源标题',
    `priority_no` INT NOT NULL COMMENT '推荐优先级',
    `reason` VARCHAR(1000) NOT NULL COMMENT '推荐理由',
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/COMPLETED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_recommendation_node` (`path_id`, `node_id`),
    KEY `idx_edu_recommendation_student` (`student_user_id`, `course_id`, `status`, `priority_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个性化资源推荐记录';

CREATE TABLE `edu_learning_progress` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `latest_path_node_id` BIGINT DEFAULT NULL COMMENT '最新路径节点ID',
    `progress_status` VARCHAR(32) NOT NULL COMMENT '状态: LOCKED/PENDING/IN_PROGRESS/COMPLETED',
    `progress_percent` INT NOT NULL DEFAULT 0 COMMENT '完成百分比',
    `completion_source` VARCHAR(32) DEFAULT NULL COMMENT '完成来源: MANUAL/ASSESSMENT',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_learning_progress` (`student_user_id`, `course_id`, `knowledge_point_id`),
    KEY `idx_edu_learning_progress_course` (`course_id`, `student_user_id`, `progress_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生知识点学习进度';
