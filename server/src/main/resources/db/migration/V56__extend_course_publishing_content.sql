ALTER TABLE `edu_course`
    ADD COLUMN `cover_attachment_ref_id` BIGINT DEFAULT NULL COMMENT '课程封面附件引用ID' AFTER `teacher_name`;

ALTER TABLE `edu_course_chapter`
    ADD COLUMN `content_type` VARCHAR(32) NOT NULL DEFAULT 'RICH_TEXT' COMMENT '内容类型: RICH_TEXT/VIDEO/FILE' AFTER `summary`,
    ADD COLUMN `content_json` LONGTEXT DEFAULT NULL COMMENT 'Tiptap JSON内容' AFTER `content_type`,
    ADD COLUMN `resource_attachment_ref_id` BIGINT DEFAULT NULL COMMENT '视频或文件附件引用ID' AFTER `content_json`,
    ADD COLUMN `resource_name` VARCHAR(256) DEFAULT NULL COMMENT '资源文件名' AFTER `resource_attachment_ref_id`,
    ADD COLUMN `resource_mime_type` VARCHAR(128) DEFAULT NULL COMMENT '资源MIME类型' AFTER `resource_name`,
    ADD COLUMN `video_duration_seconds` INT DEFAULT NULL COMMENT '视频时长秒数' AFTER `resource_mime_type`,
    ADD COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED' AFTER `video_duration_seconds`,
    ADD KEY `idx_edu_chapter_status` (`course_id`, `status`);

UPDATE `edu_course_chapter` chapter
JOIN `edu_course` course ON course.id = chapter.course_id
SET chapter.status = CASE WHEN course.status = 'PUBLISHED' THEN 'PUBLISHED' ELSE 'DRAFT' END,
    chapter.content_json = JSON_OBJECT(
        'type', 'doc',
        'content', JSON_ARRAY(JSON_OBJECT(
            'type', 'paragraph',
            'content', JSON_ARRAY(JSON_OBJECT('type', 'text', 'text', COALESCE(chapter.summary, chapter.chapter_name)))
        ))
    );

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5007,
        'COURSE_RESOURCE_DEFAULT_2G',
        '课程资源上传策略',
        'COURSE_RESOURCE',
        2147483648,
        '["pdf","doc","docx","ppt","pptx","txt","md","png","jpg","jpeg","gif","webp","mp4","webm","mov"]',
        '["application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","text/plain","text/markdown","image/png","image/jpeg","image/gif","image/webp","video/mp4","video/webm","video/quicktime"]',
        'ENABLED',
        NULL
    );
