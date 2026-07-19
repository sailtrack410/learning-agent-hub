UPDATE `edu_course_chapter`
SET `content_json` = CASE `content_type`
        WHEN 'VIDEO' THEN JSON_OBJECT(
            'type', 'doc',
            'content', JSON_ARRAY(JSON_OBJECT(
                'type', 'video',
                'attrs', JSON_OBJECT(
                    'attachmentRefId', `resource_attachment_ref_id`,
                    'name', COALESCE(`resource_name`, '教学视频'),
                    'mimeType', COALESCE(`resource_mime_type`, 'video/mp4')
                )
            )))
        WHEN 'FILE' THEN JSON_OBJECT(
            'type', 'doc',
            'content', JSON_ARRAY(JSON_OBJECT(
                'type', 'attachment',
                'attrs', JSON_OBJECT(
                    'attachmentRefId', `resource_attachment_ref_id`,
                    'name', COALESCE(`resource_name`, '课程资料'),
                    'mimeType', COALESCE(`resource_mime_type`, 'application/octet-stream')
                )
            )))
        ELSE `content_json`
    END,
    `content_type` = 'ARTICLE';

ALTER TABLE `edu_course_chapter`
    MODIFY COLUMN `content_type` VARCHAR(32) NOT NULL DEFAULT 'ARTICLE' COMMENT '内容类型: ARTICLE';
