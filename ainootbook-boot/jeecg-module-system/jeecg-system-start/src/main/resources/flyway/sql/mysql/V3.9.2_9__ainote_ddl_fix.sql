-- AI笔记模块 - DDL修复迁移脚本（幂等版）
-- 版本: V3.9.2_9
-- 修复: CR-01 缺失字段 / CR-02 类型不一致 / CR-03 material_id可空 / CR-05 PROCESSING唯一性
-- 注意: MySQL DDL 无事务，首次执行部分成功后重跑必须幂等

-- ============================================================
-- CR-02: 修复 ainote_material.file_type 类型 (TINYINT → VARCHAR)
-- MODIFY 天然幂等，重复执行无副作用
-- ============================================================
ALTER TABLE ainote_material
    MODIFY COLUMN file_type VARCHAR(20) NOT NULL COMMENT '文件类型: audio/document/image';

-- ============================================================
-- CR-01: 补齐 ainote_material.del_flag 字段（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ainote_material' AND column_name = 'del_flag');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE ainote_material ADD COLUMN del_flag INT DEFAULT 0 COMMENT ''删除标记: 0=正常/1=已删除''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- CR-02: 修复 ainote_ai_task.task_type 类型 (TINYINT → VARCHAR)
-- ============================================================
ALTER TABLE ainote_ai_task
    MODIFY COLUMN task_type VARCHAR(20) NOT NULL COMMENT '任务类型: asr/tika/summary';

-- ============================================================
-- CR-01: 补齐 ainote_ai_task.next_retry_at（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND column_name = 'next_retry_at');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE ainote_ai_task ADD COLUMN next_retry_at DATETIME NULL COMMENT ''下次重试时间''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- CR-01: 补齐 ainote_ai_task.worker_id（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND column_name = 'worker_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE ainote_ai_task ADD COLUMN worker_id VARCHAR(100) NULL COMMENT ''执行节点ID''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- CR-01: 补齐 ainote_ai_task.vendor_task_id（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND column_name = 'vendor_task_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE ainote_ai_task ADD COLUMN vendor_task_id VARCHAR(100) NULL COMMENT ''第三方任务ID(ASR轮询用)''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- CR-01: 修复 duration 类型 (INT → BIGINT)
-- ============================================================
ALTER TABLE ainote_ai_task
    MODIFY COLUMN duration BIGINT NULL COMMENT '执行时长(毫秒)';

-- ============================================================
-- CR-03: 修复 material_id 可空约束（幂等处理外键）
-- 先查外键是否存在再 DROP，避免外键名不匹配导致失败
-- ============================================================
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task'
    AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'ainote_ai_task_ibfk_1');
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE ainote_ai_task DROP FOREIGN KEY ainote_ai_task_ibfk_1',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 也检查新名称的外键，防止重复添加
SET @fk_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task'
    AND constraint_type = 'FOREIGN KEY' AND constraint_name = 'fk_ai_task_material');
SET @sql = IF(@fk_exists > 0,
    'ALTER TABLE ainote_ai_task DROP FOREIGN KEY fk_ai_task_material',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE ainote_ai_task
    MODIFY COLUMN material_id VARCHAR(32) NULL COMMENT '素材ID(笔记级任务可空)';

ALTER TABLE ainote_ai_task
    ADD CONSTRAINT fk_ai_task_material
    FOREIGN KEY (material_id) REFERENCES ainote_material(id) ON DELETE CASCADE;

-- ============================================================
-- CR-05: 添加索引（幂等，先检查再加）
-- ============================================================
SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND index_name = 'idx_material_type_status');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE ainote_ai_task ADD INDEX idx_material_type_status (material_id, task_type, task_status)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND index_name = 'idx_next_retry');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE ainote_ai_task ADD INDEX idx_next_retry (next_retry_at)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'ainote_ai_task' AND index_name = 'idx_worker');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE ainote_ai_task ADD INDEX idx_worker (worker_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
