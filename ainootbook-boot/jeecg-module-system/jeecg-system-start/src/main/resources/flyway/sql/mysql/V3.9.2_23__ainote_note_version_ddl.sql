-- AI笔记模块 - 笔记版本快照表 DDL
-- 版本: V3.9.2_23
-- 说明: 新增 ainote_note_version 表，用于保存笔记内容、摘要和关键词的历史版本快照

-- ============================================================
-- 1. 创建笔记版本快照表（幂等）
-- ============================================================
CREATE TABLE IF NOT EXISTS ainote_note_version (
    id VARCHAR(32) PRIMARY KEY COMMENT '版本ID',
    note_id VARCHAR(32) NOT NULL COMMENT '笔记ID',
    version_number INT NOT NULL COMMENT '版本号，从1开始递增',
    note_content LONGTEXT COMMENT '版本快照内容（Markdown格式）',
    ai_summary TEXT COMMENT '版本快照摘要',
    keywords VARCHAR(500) COMMENT '版本快照关键词（逗号分隔）',
    generation_id VARCHAR(36) COMMENT '生成批次UUID，关联AI任务批次',
    created_by VARCHAR(32) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    UNIQUE KEY uk_note_version (note_id, version_number),
    FOREIGN KEY (note_id) REFERENCES ainote_note(id) ON DELETE CASCADE,
    INDEX idx_note (note_id),
    INDEX idx_version (note_id, version_number),
    INDEX idx_generation (generation_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记版本快照表';

-- ============================================================
-- 2. 为 ainote_note 增加 current_version 字段（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_note'
                     AND column_name = 'current_version');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_note ADD COLUMN current_version INT DEFAULT 1 COMMENT ''当前版本号''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 为 ainote_ai_task 增加 generation_id 字段（幂等）
-- ============================================================
SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_ai_task'
                     AND column_name = 'generation_id');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_ai_task ADD COLUMN generation_id VARCHAR(36) COMMENT ''生成批次UUID''',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
