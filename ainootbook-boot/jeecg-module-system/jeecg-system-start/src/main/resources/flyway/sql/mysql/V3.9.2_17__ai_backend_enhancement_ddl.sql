-- AI backend enhancement DDL
-- 版本: V3.9.2_17
-- 说明: 补充 OCR 模型配置、Flow 摘要配置及任务类型字典项

ALTER TABLE ainote_ai_config
    ADD COLUMN ocr_model_id VARCHAR(36) DEFAULT NULL COMMENT 'OCR Vision模型ID';

ALTER TABLE ainote_ai_config
    ADD COLUMN summary_flow_id VARCHAR(36) DEFAULT NULL COMMENT '摘要Flow流程ID';

ALTER TABLE ainote_ai_config
    ADD COLUMN summary_flow_enabled TINYINT DEFAULT 0 COMMENT '是否启用Flow摘要';

INSERT IGNORE INTO sys_dict_item
    (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time)
SELECT REPLACE(UUID(),'-',''), id, 'OCR文字识别', 'ocr', '图片OCR文字识别任务', 5, 1, 'admin', NOW()
FROM sys_dict
WHERE dict_code = 'ainote_task_type'
LIMIT 1;

INSERT IGNORE INTO sys_dict_item
    (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time)
SELECT REPLACE(UUID(),'-',''), id, '视频转写', 'video', '视频素材处理任务', 6, 1, 'admin', NOW()
FROM sys_dict
WHERE dict_code = 'ainote_task_type'
LIMIT 1;
