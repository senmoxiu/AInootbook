-- AI笔记模块 - Pipeline 配置 DDL
-- 版本: V3.9.2_21
-- 说明: 为 ainote_ai_config 补充 pipeline 分阶段模型配置、失败策略，并增加 tenant_id 唯一约束
-- 注意: MySQL DDL 无事务，脚本必须可重跑

-- ============================================================
-- 1. 新增模型字段（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'asr_model_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN asr_model_id VARCHAR(32) DEFAULT NULL COMMENT ''ASR 模型 ID''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'video_model_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN video_model_id VARCHAR(32) DEFAULT NULL COMMENT ''Video 模型 ID''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'keywords_model_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN keywords_model_id VARCHAR(32) DEFAULT NULL COMMENT ''Keywords 模型 ID''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'integrate_model_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN integrate_model_id VARCHAR(32) DEFAULT NULL COMMENT ''Integrate 模型 ID''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 新增失败策略字段（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'asr_failure_mode');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN asr_failure_mode VARCHAR(16) DEFAULT ''retry'' COMMENT ''ASR 失败模式: skip/retry/fail_all''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'asr_retry_limit');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN asr_retry_limit INT DEFAULT 3 COMMENT ''ASR 重试次数上限''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'ocr_failure_mode');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN ocr_failure_mode VARCHAR(16) DEFAULT ''retry''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'ocr_retry_limit');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN ocr_retry_limit INT DEFAULT 3',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'video_failure_mode');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN video_failure_mode VARCHAR(16) DEFAULT ''retry''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'video_retry_limit');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN video_retry_limit INT DEFAULT 3',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'summary_failure_mode');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN summary_failure_mode VARCHAR(16) DEFAULT ''retry''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'summary_retry_limit');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN summary_retry_limit INT DEFAULT 3',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'integrate_failure_mode');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN integrate_failure_mode VARCHAR(16) DEFAULT ''skip''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND column_name = 'integrate_retry_limit');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_config ADD COLUMN integrate_retry_limit INT DEFAULT 0',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 新增 tenant 唯一约束（幂等）
-- ============================================================
SET @idx_exists = (SELECT COUNT(*)
                   FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_config'
                     AND index_name = 'uk_ainote_ai_config_tenant');
SET @sql = IF(@idx_exists = 0,
              'ALTER TABLE ainote_ai_config ADD UNIQUE KEY uk_ainote_ai_config_tenant (tenant_id)',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
