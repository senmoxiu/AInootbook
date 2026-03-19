-- =====================================================
-- V3.9.2_26: 新增 OCR 模型类型字典项
-- 功能：为 model_type 字典新增 OCR 类型，支持独立 OCR 模型
-- =====================================================

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status)
SELECT REPLACE(UUID(), '-', ''),
       sd.id,
       'OCR',
       'OCR',
       'OCR 文字识别模型',
       5,
       1
FROM sys_dict sd
WHERE sd.dict_code = 'model_type'
LIMIT 1;
