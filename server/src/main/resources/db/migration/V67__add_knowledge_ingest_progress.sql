ALTER TABLE `kb_ingest_job`
    ADD COLUMN `progress_percent` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '入库进度百分比' AFTER `status`,
    ADD COLUMN `current_stage` VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '当前处理阶段' AFTER `progress_percent`;

UPDATE `kb_ingest_job`
SET `progress_percent` = CASE
        WHEN `status` = 'SUCCESS' THEN 100
        WHEN `status` = 'RUNNING' THEN 5
        ELSE 0
    END,
    `current_stage` = CASE
        WHEN `status` = 'SUCCESS' THEN 'COMPLETED'
        WHEN `status` = 'FAILED' THEN 'FAILED'
        WHEN `status` = 'RUNNING' THEN 'READ_FILE'
        ELSE 'QUEUED'
    END;
