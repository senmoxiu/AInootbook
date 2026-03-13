-- AI笔记模块 - Pipeline 配置 DML
-- 版本: V3.9.2_22
-- 说明: tenant_id=0 配置去重并回填默认模型，同时更新 note_summary 种子提示词
-- 注意: 参考 design.md Migration Plan 阶段 1，脚本必须可重跑

-- ============================================================
-- 1. tenant_id=0 配置去重：按 update_time/create_time 保留最新一条
-- ============================================================
DROP TEMPORARY TABLE IF EXISTS tmp_ainote_ai_config_tenant0_latest;

CREATE TEMPORARY TABLE tmp_ainote_ai_config_tenant0_latest AS
SELECT c.*
FROM ainote_ai_config c
INNER JOIN (
    SELECT id
    FROM ainote_ai_config
    WHERE tenant_id = 0
    ORDER BY COALESCE(update_time, create_time, '1970-01-01 00:00:00') DESC,
             COALESCE(create_time, '1970-01-01 00:00:00') DESC,
             id DESC
    LIMIT 1
) latest ON latest.id = c.id;

DELETE FROM ainote_ai_config
WHERE tenant_id = 0
  AND id NOT IN (
      SELECT id
      FROM tmp_ainote_ai_config_tenant0_latest
  );

INSERT IGNORE INTO ainote_ai_config
SELECT *
FROM tmp_ainote_ai_config_tenant0_latest;

DROP TEMPORARY TABLE IF EXISTS tmp_ainote_ai_config_tenant0_latest;

-- ============================================================
-- 2. 回填默认模型（仅 tenant_id=0）
-- ============================================================
UPDATE ainote_ai_config
SET keywords_model_id = COALESCE(keywords_model_id, summary_model_id),
    integrate_model_id = COALESCE(integrate_model_id, summary_model_id)
WHERE tenant_id = 0
  AND summary_model_id IS NOT NULL
  AND (keywords_model_id IS NULL OR integrate_model_id IS NULL);

-- ============================================================
-- 3. 更新 note_summary 种子提示词（移除 keywords 要求）
-- ============================================================
UPDATE airag_prompts
SET content = '请为以下内容生成摘要:\n\n{{content}}'
WHERE prompt_key = 'note_summary'
  AND status = 1;
