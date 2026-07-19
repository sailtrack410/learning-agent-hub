CREATE TABLE `sys_attachment_preview` (
    `source_attachment_ref_id` BIGINT       NOT NULL COMMENT '原附件引用ID',
    `preview_attachment_ref_id` BIGINT      DEFAULT NULL COMMENT 'PDF预览附件引用ID',
    `preview_status`           VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONVERTING/READY/FAILED',
    `error_message`            VARCHAR(500) DEFAULT NULL COMMENT '转换失败原因',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`source_attachment_ref_id`),
    UNIQUE KEY `uk_preview_attachment_ref` (`preview_attachment_ref_id`),
    KEY `idx_preview_status` (`preview_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件PDF预览关系';
