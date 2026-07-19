CREATE TABLE `edu_assessment` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID',
    `title` VARCHAR(128) NOT NULL COMMENT '测验标题',
    `description` VARCHAR(1000) DEFAULT NULL COMMENT '说明',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED',
    `total_score` INT NOT NULL DEFAULT 0 COMMENT '总分',
    `duration_minutes` INT NOT NULL DEFAULT 20 COMMENT '建议时长',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_edu_assessment_course` (`course_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程测验';

CREATE TABLE `edu_assessment_question` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `assessment_id` BIGINT NOT NULL COMMENT '测验ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `question_type` VARCHAR(32) NOT NULL COMMENT '题型: SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE',
    `prompt` VARCHAR(1000) NOT NULL COMMENT '题干',
    `options_json` TEXT DEFAULT NULL COMMENT '选项JSON',
    `correct_answer_json` VARCHAR(1000) NOT NULL COMMENT '正确答案JSON',
    `analysis` VARCHAR(2000) NOT NULL COMMENT '答案解析',
    `score` INT NOT NULL COMMENT '分值',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    KEY `idx_edu_question_assessment` (`assessment_id`, `sort_order`),
    KEY `idx_edu_question_point` (`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测验题目';

CREATE TABLE `edu_assessment_attempt` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `assessment_id` BIGINT NOT NULL COMMENT '测验ID',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `idempotency_key` VARCHAR(64) NOT NULL COMMENT '幂等键',
    `attempt_no` INT NOT NULL COMMENT '尝试次数',
    `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态',
    `total_score` INT NOT NULL COMMENT '总分',
    `earned_score` INT NOT NULL COMMENT '得分',
    `duration_seconds` INT NOT NULL COMMENT '总用时秒数',
    `submitted_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_attempt_idempotency` (`student_user_id`, `idempotency_key`),
    UNIQUE KEY `uk_edu_attempt_no` (`student_user_id`, `assessment_id`, `attempt_no`),
    KEY `idx_edu_attempt_assessment` (`assessment_id`, `student_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生测验作答';

CREATE TABLE `edu_assessment_answer` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `attempt_id` BIGINT NOT NULL COMMENT '作答ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `answer_json` VARCHAR(1000) NOT NULL COMMENT '学生答案JSON',
    `is_correct` TINYINT NOT NULL COMMENT '是否正确',
    `earned_score` INT NOT NULL COMMENT '本题得分',
    `error_type` VARCHAR(32) DEFAULT NULL COMMENT '错误类型',
    `duration_seconds` INT NOT NULL DEFAULT 0 COMMENT '本题用时秒数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_answer_question` (`attempt_id`, `question_id`),
    KEY `idx_edu_answer_attempt` (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='测验答案';

CREATE TABLE `edu_student_mastery` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `student_user_id` BIGINT NOT NULL COMMENT '学生用户ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `mastery_score` DECIMAL(5,2) NOT NULL COMMENT '掌握度百分比',
    `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '相关题目作答次数',
    `correct_count` INT NOT NULL DEFAULT 0 COMMENT '正确次数',
    `last_assessed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_student_mastery` (`student_user_id`, `course_id`, `knowledge_point_id`),
    KEY `idx_edu_mastery_course` (`course_id`, `student_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生知识点掌握度';

INSERT INTO `edu_assessment`
    (id, course_id, chapter_id, title, description, status, total_score, duration_minutes)
VALUES
    (8500001, 8100001, NULL, 'Java Web 基础诊断', '覆盖 HTTP、Servlet、Spring Boot、REST、MyBatis 和权限基础。', 'PUBLISHED', 100, 20)
ON DUPLICATE KEY UPDATE
    title = VALUES(title), description = VALUES(description), status = VALUES(status),
    total_score = VALUES(total_score), duration_minutes = VALUES(duration_minutes);

INSERT INTO `edu_assessment_question`
    (id, assessment_id, knowledge_point_id, question_type, prompt, options_json,
     correct_answer_json, analysis, score, sort_order)
VALUES
    (8510001, 8500001, 8120001, 'SINGLE_CHOICE', '创建资源成功后最合适的 HTTP 状态码是？',
     '[{"value":"A","label":"200"},{"value":"B","label":"201"},{"value":"C","label":"400"},{"value":"D","label":"500"}]',
     '["B"]', '201 Created 表示服务器已成功创建资源。', 15, 1),
    (8510002, 8500001, 8120002, 'TRUE_FALSE', 'Servlet 实例字段适合保存每个请求的当前用户信息。',
     '[{"value":"TRUE","label":"正确"},{"value":"FALSE","label":"错误"}]',
     '["FALSE"]', 'Servlet 通常被并发请求共享，请求数据应保存在方法局部变量或请求作用域。', 15, 2),
    (8510003, 8500001, 8120004, 'MULTIPLE_CHOICE', '哪些条件可能影响 Spring Boot 自动配置是否生效？',
     '[{"value":"A","label":"类路径依赖"},{"value":"B","label":"配置属性"},{"value":"C","label":"已有 Bean"},{"value":"D","label":"源文件行数"}]',
     '["A","B","C"]', '自动配置通常由类路径、属性和 Bean 条件共同决定。', 20, 3),
    (8510004, 8500001, 8120005, 'SINGLE_CHOICE', '更新课程 8100001 的资源式接口更合适的是？',
     '[{"value":"A","label":"POST /updateCourse"},{"value":"B","label":"PUT /api/courses/8100001"},{"value":"C","label":"GET /api/courses/update"},{"value":"D","label":"DELETE /api/courses"}]',
     '["B"]', '资源 URI 使用名词，PUT 表达幂等更新。', 15, 4),
    (8510005, 8500001, 8120008, 'MULTIPLE_CHOICE', '哪些操作通常需要明确事务边界？',
     '[{"value":"A","label":"创建订单与订单明细"},{"value":"B","label":"读取静态配置"},{"value":"C","label":"提交答案并更新掌握度"},{"value":"D","label":"渲染前端图标"}]',
     '["A","C"]', '需要保持多表写入一致性的业务操作应位于同一事务中。', 20, 5),
    (8510006, 8500001, 8120010, 'TRUE_FALSE', '前端隐藏无权限按钮可以替代后端接口鉴权。',
     '[{"value":"TRUE","label":"正确"},{"value":"FALSE","label":"错误"}]',
     '["FALSE"]', '前端限制可被绕过，后端必须完成角色、操作和数据权限校验。', 15, 6)
ON DUPLICATE KEY UPDATE
    question_type = VALUES(question_type), prompt = VALUES(prompt), options_json = VALUES(options_json),
    correct_answer_json = VALUES(correct_answer_json), analysis = VALUES(analysis), score = VALUES(score),
    sort_order = VALUES(sort_order);
