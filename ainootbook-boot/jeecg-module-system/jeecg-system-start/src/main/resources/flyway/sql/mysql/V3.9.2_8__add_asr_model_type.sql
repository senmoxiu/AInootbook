-- ----------------------------------------------
-- ASR 模型类型字典项（model_type）
-- ----------------------------------------------

INSERT INTO `sys_dict_item` (
  `id`, `dict_id`, `item_text`, `item_value`, `description`,
  `sort_order`, `status`, `create_by`, `create_time`, `update_by`, `update_time`
)
SELECT
  '2026020700000000001', '1891456510739890177', '语音识别', 'ASR', NULL,
  4, 1, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_dict_item`
  WHERE `dict_id` = '1891456510739890177' AND `item_value` = 'ASR'
);
