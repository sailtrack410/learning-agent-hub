CREATE TABLE `edu_course_knowledge_space` (
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `kb_space_id` BIGINT NOT NULL COMMENT '知识库空间ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`course_id`),
    UNIQUE KEY `uk_edu_course_kb_space` (`kb_space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识库空间';

CREATE TABLE `edu_course_knowledge_document` (
    `id` BIGINT NOT NULL COMMENT '主键',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `kb_document_id` BIGINT NOT NULL COMMENT '知识库文档ID',
    `attachment_ref_id` BIGINT NOT NULL COMMENT '课程原始附件引用ID',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '关联章节ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by` BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_edu_course_kb_document` (`kb_document_id`),
    UNIQUE KEY `uk_edu_course_attachment` (`course_id`, `attachment_ref_id`),
    KEY `idx_edu_course_knowledge_chapter` (`course_id`, `chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识库文档关联';

CREATE TABLE `edu_course_knowledge_document_point` (
    `document_binding_id` BIGINT NOT NULL COMMENT '课程知识库文档关联ID',
    `knowledge_point_id` BIGINT NOT NULL COMMENT '知识点ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`document_binding_id`, `knowledge_point_id`),
    KEY `idx_edu_course_document_point` (`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识库文档知识点关联';
