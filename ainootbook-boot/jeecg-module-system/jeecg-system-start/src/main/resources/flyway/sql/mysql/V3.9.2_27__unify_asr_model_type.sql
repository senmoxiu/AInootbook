-- -----------------------------------------------------------
-- V3.9.2_27: 统一 ASR 模型类型标识 AUDIO → ASR
-- 背景: 前端模型管理页面历史使用 'AUDIO' 作为语音识别模型类型，
--       但后端常量 LLMConsts.MODEL_TYPE_ASR = 'ASR'，导致
--       AsrTaskHandler 校验 model_type 时必然失败。
-- 修复: 将数据库中所有 model_type='AUDIO' 的记录统一为 'ASR'
-- 注意: ainote_ai_config 与 airag_model 表 collation 不同，
--       JOIN 时需显式 COLLATE utf8mb4_general_ci 避免隐式转换报错
-- -----------------------------------------------------------

-- 1. 修正 airag_model 表中已有的 AUDIO 类型模型
UPDATE `airag_model`
SET `model_type` = 'ASR',
    `update_time` = NOW()
WHERE `model_type` = 'AUDIO';

-- 2. 修正字典表中可能存在的 AUDIO 字典项（dict_id 为 model_type 字典）
UPDATE `sys_dict_item`
SET `item_value` = 'ASR',
    `item_text`  = '语音识别',
    `update_time` = NOW()
WHERE `dict_id` = '1891456510739890177'
  AND `item_value` = 'AUDIO';

-- 3. 清理 ainote_ai_config 中指向非 ASR 类型模型的 asr_model_id
--    （将指向错误类型模型的字段置空，迫使运行时走 fallback 逻辑）
UPDATE `ainote_ai_config` c
INNER JOIN `airag_model` m ON c.`asr_model_id` COLLATE utf8mb4_general_ci = m.`id` COLLATE utf8mb4_general_ci
SET c.`asr_model_id` = NULL,
    c.`update_time`  = NOW()
WHERE m.`model_type` COLLATE utf8mb4_general_ci != 'ASR';

-- 4. 同理清理 video_model_id 中指向非 ASR 类型模型的记录
--    （视频处理复用 ASR 模型做音频提取转写）
UPDATE `ainote_ai_config` c
INNER JOIN `airag_model` m ON c.`video_model_id` COLLATE utf8mb4_general_ci = m.`id` COLLATE utf8mb4_general_ci
SET c.`video_model_id` = NULL,
    c.`update_time`    = NOW()
WHERE m.`model_type` COLLATE utf8mb4_general_ci != 'ASR';
