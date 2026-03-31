-- V3.9.2_29: 清理 UniApp 已删除分包对应的数据库数据（幂等）
-- 说明: 已删除以下 UniApp 分包：
--       - pages-work（低代码在线表单 + 拖拽仪表盘）
--       - pages-sub（在线表单列表视图）
--       - pages-message（聊天、联系人、租户切换）
--       这些分包均为 JeecgBoot 框架原始功能，本次正式从学生端 App 移除。
--
-- 注意：以上分包对应的是 UniApp 客户端路由，不对应 Web 后台 sys_permission 菜单。
--       本脚本主要清理 airag_mcp / airag_flow 中可能残留的 demo 数据，
--       以及补充清理 V3.9.2_25 未覆盖的 demo 相关菜单记录。

-- ============================================================
-- 1. 补充软删除 sys_permission 中可能残留的 demo 相关菜单
--    （V3.9.2_25 已处理 component LIKE '%demo%' 的记录，
--     本步骤补充处理 url 路径含 workHome/online 但明确属于 demo 展示的菜单）
-- ============================================================
UPDATE sys_permission
SET del_flag = 1
WHERE del_flag = 0
  AND (
    component LIKE '%workHome%'
    OR url LIKE '%/workHome%'
  );

-- ============================================================
-- 2. 清理已软删除菜单的角色权限关联记录
-- ============================================================
DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission
    WHERE del_flag = 1
      AND (
        component LIKE '%workHome%'
        OR url LIKE '%/workHome%'
      )
);

-- ============================================================
-- 3. 清理 airag_mcp 中 demo 相关记录（补充 V3.9.2_25 未覆盖的 name 模式）
--    先 SELECT 审计（可手动执行）：
--    SELECT id, name, tools FROM airag_mcp WHERE name LIKE '%demo%' OR name LIKE '%测试%';
-- ============================================================
DELETE FROM airag_mcp
WHERE name LIKE '%demo%'
   OR name LIKE '%Demo%'
   OR tools LIKE '%/demo/%';

-- ============================================================
-- 4. 清理 airag_flow 中 demo 相关记录（补充 V3.9.2_25 未覆盖的 name 模式）
--    先 SELECT 审计（可手动执行）：
--    SELECT id, name FROM airag_flow WHERE name LIKE '%demo%' OR name LIKE '%测试%';
-- ============================================================
DELETE FROM airag_flow
WHERE name LIKE '%demo%'
   OR name LIKE '%Demo%'
   OR design LIKE '%/demo/%';
