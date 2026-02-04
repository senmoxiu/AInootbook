-- Flyway 修复脚本
-- 执行此脚本后重新启动应用
-- 说明: 当 Flyway 迁移脚本修改后（如改为 INSERT IGNORE），需要清理历史记录让 Flyway 重新执行

-- 1. 删除失败的迁移记录
DELETE FROM flyway_schema_history WHERE version = '3.9.2.2' AND success = 0;
DELETE FROM flyway_schema_history WHERE version = '3.9.2.3' AND success = 0;

-- 2. 删除记录让 Flyway 重新执行（脚本已改为 INSERT IGNORE，可安全重复执行）
DELETE FROM flyway_schema_history WHERE version = '3.9.2.2';
DELETE FROM flyway_schema_history WHERE version = '3.9.2.3';

-- 3. 验证当前状态
SELECT * FROM flyway_schema_history WHERE version LIKE '3.9.2%' ORDER BY installed_rank;
