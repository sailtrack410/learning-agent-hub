CREATE TABLE `edu_course` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_code` VARCHAR(64) NOT NULL COMMENT '课程编码',
    `course_name` VARCHAR(128) NOT NULL COMMENT '课程名称',
    `summary` VARCHAR(1000) DEFAULT NULL COMMENT '课程简介',
    `learning_objectives` TEXT COMMENT '学习目标',
    `prerequisites` VARCHAR(1000) DEFAULT NULL COMMENT '先修要求',
    `teacher_name` VARCHAR(64) DEFAULT NULL COMMENT '授课教师',
    `total_hours` INT NOT NULL DEFAULT 0 COMMENT '总学时',
    `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` BIGINT DEFAULT NULL,
    `updated_by` BIGINT DEFAULT NULL,
    `is_deleted` TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_code` (`course_code`),
    KEY `idx_edu_course_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程';

CREATE TABLE `edu_course_chapter` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `chapter_no` INT NOT NULL COMMENT '章节序号',
    `chapter_name` VARCHAR(128) NOT NULL COMMENT '章节名称',
    `summary` VARCHAR(1000) DEFAULT NULL COMMENT '章节简介',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_chapter_no` (`course_id`, `chapter_no`),
    KEY `idx_edu_chapter_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节';

CREATE TABLE `edu_knowledge_point` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `chapter_id` BIGINT NOT NULL COMMENT '章节ID',
    `point_code` VARCHAR(64) NOT NULL COMMENT '知识点编码',
    `point_name` VARCHAR(128) NOT NULL COMMENT '知识点名称',
    `summary` VARCHAR(1000) DEFAULT NULL COMMENT '知识点简介',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_point_code` (`course_id`, `point_code`),
    KEY `idx_edu_point_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识点';

CREATE TABLE `edu_knowledge_dependency` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `prerequisite_point_id` BIGINT NOT NULL COMMENT '前置知识点ID',
    `dependent_point_id` BIGINT NOT NULL COMMENT '后续知识点ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_point_dependency` (`prerequisite_point_id`, `dependent_point_id`),
    KEY `idx_edu_dependency_course` (`course_id`),
    KEY `idx_edu_dependency_target` (`dependent_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点依赖';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
) VALUES
    (7011, 'ADMIN_COURSE_MANAGE', '课程管理', 'COURSE', 'MANAGE', 'ACTIVE', NULL, NULL, 0)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    module_code = VALUES(module_code),
    action_code = VALUES(action_code),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted);

INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
SELECT 6111, role.id, permission.id, NULL
FROM admin_role role
JOIN admin_permission permission ON permission.perm_code = 'ADMIN_COURSE_MANAGE'
WHERE role.role_code = 'SUPER_ADMIN'
  AND role.is_deleted = 0
ON DUPLICATE KEY UPDATE created_by = VALUES(created_by);

INSERT INTO `edu_course` (
    id, course_code, course_name, summary, learning_objectives, prerequisites,
    teacher_name, total_hours, status, created_by, updated_by
) VALUES (
    8100001, 'JAVA_WEB', 'Java Web 应用开发',
    '从 Web 基础到 Spring Boot 工程交付，完成可运行、可测试、可部署的 Java Web 应用。',
    '掌握 Servlet 与 HTTP 基础；使用 Spring Boot 构建 REST API；完成 MyBatis 数据访问、认证授权、自动化测试与部署。',
    '具备 Java 语法、面向对象和关系型数据库基础。',
    '课程组', 48, 'PUBLISHED', NULL, NULL
) ON DUPLICATE KEY UPDATE
    course_name = VALUES(course_name),
    summary = VALUES(summary),
    learning_objectives = VALUES(learning_objectives),
    prerequisites = VALUES(prerequisites),
    teacher_name = VALUES(teacher_name),
    total_hours = VALUES(total_hours),
    status = VALUES(status),
    is_deleted = 0;

INSERT INTO `edu_course_chapter` (id, course_id, chapter_no, chapter_name, summary) VALUES
    (8110001, 8100001, 1, 'Servlet 与 Web 基础', '理解 HTTP 请求响应与 Servlet 生命周期。'),
    (8110002, 8100001, 2, 'Spring Boot 快速开发', '建立分层清晰的 Spring Boot 项目。'),
    (8110003, 8100001, 3, 'REST API 设计', '设计一致、可验证的 Web 接口。'),
    (8110004, 8100001, 4, 'MyBatis 数据访问', '完成数据库映射与事务处理。'),
    (8110005, 8100001, 5, '认证与权限', '实现登录、会话和接口授权。'),
    (8110006, 8100001, 6, '自动化测试', '使用单元测试和集成测试验证业务。'),
    (8110007, 8100001, 7, '构建与部署', '完成应用构建、配置和部署。')
ON DUPLICATE KEY UPDATE chapter_name = VALUES(chapter_name), summary = VALUES(summary);

INSERT INTO `edu_knowledge_point` (id, course_id, chapter_id, point_code, point_name, summary, sort_order) VALUES
    (8120001, 8100001, 8110001, 'HTTP', 'HTTP 请求与响应', '方法、状态码、请求头和响应体。', 1),
    (8120002, 8100001, 8110001, 'SERVLET', 'Servlet 生命周期', '容器、映射和请求处理。', 2),
    (8120003, 8100001, 8110002, 'SPRING_IOC', 'Spring IoC', '依赖注入与组件管理。', 1),
    (8120004, 8100001, 8110002, 'SPRING_BOOT', 'Spring Boot 自动配置', '启动器、配置与应用结构。', 2),
    (8120005, 8100001, 8110003, 'REST', 'REST 资源设计', '资源路径、方法语义与状态码。', 1),
    (8120006, 8100001, 8110003, 'VALIDATION', '参数校验与异常处理', '输入约束与统一错误响应。', 2),
    (8120007, 8100001, 8110004, 'MYBATIS', 'MyBatis 映射', 'Mapper、SQL 与结果映射。', 1),
    (8120008, 8100001, 8110004, 'TRANSACTION', '事务边界', '事务一致性与回滚。', 2),
    (8120009, 8100001, 8110005, 'AUTHN', '身份认证', '登录、密码与会话。', 1),
    (8120010, 8100001, 8110005, 'AUTHZ', '权限控制', '角色、权限点与接口授权。', 2),
    (8120011, 8100001, 8110006, 'UNIT_TEST', '单元测试', '隔离验证业务逻辑。', 1),
    (8120012, 8100001, 8110006, 'INTEGRATION_TEST', '集成测试', '验证接口、数据库与安全边界。', 2),
    (8120013, 8100001, 8110007, 'BUILD', 'Maven 构建', '依赖、生命周期与产物。', 1),
    (8120014, 8100001, 8110007, 'DEPLOY', '应用部署', '环境配置、健康检查与发布。', 2)
ON DUPLICATE KEY UPDATE point_name = VALUES(point_name), summary = VALUES(summary), sort_order = VALUES(sort_order);

INSERT INTO `edu_knowledge_dependency` (id, course_id, prerequisite_point_id, dependent_point_id) VALUES
    (8130001, 8100001, 8120001, 8120002),
    (8130002, 8100001, 8120002, 8120003),
    (8130003, 8100001, 8120003, 8120004),
    (8130004, 8100001, 8120004, 8120005),
    (8130005, 8100001, 8120005, 8120006),
    (8130006, 8100001, 8120004, 8120007),
    (8130007, 8100001, 8120007, 8120008),
    (8130008, 8100001, 8120006, 8120009),
    (8130009, 8100001, 8120009, 8120010),
    (8130010, 8100001, 8120008, 8120011),
    (8130011, 8100001, 8120011, 8120012),
    (8130012, 8100001, 8120012, 8120013),
    (8130013, 8100001, 8120013, 8120014)
ON DUPLICATE KEY UPDATE course_id = VALUES(course_id);
