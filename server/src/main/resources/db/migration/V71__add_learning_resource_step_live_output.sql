ALTER TABLE `edu_resource_generation_step`
    ADD COLUMN `live_output` TEXT DEFAULT NULL COMMENT '生成中的实时输出，最长12000字符' AFTER `output_summary`;
