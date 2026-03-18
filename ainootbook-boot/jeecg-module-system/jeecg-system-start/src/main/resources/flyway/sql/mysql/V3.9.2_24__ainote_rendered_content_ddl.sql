-- AI笔记模块 - 渲染内容字段 DDL
-- 版本: V3.9.2_24
-- 说明: 为 ainote_note 增加 rendered_content 字段，保存 Markdown 预编译后的 HTML

SET @col_exists = (SELECT COUNT(*)
                   FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'ainote_note'
                     AND column_name = 'rendered_content');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE ainote_note ADD COLUMN rendered_content LONGTEXT COMMENT ''笔记内容预编译后的HTML'' AFTER note_content',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
