-- V3.9.2_25: 清理 demo 相关数据（幂等）
-- 1. 软删除 sys_permission 中 demo/examples/demo 相关菜单
UPDATE sys_permission
SET del_flag = 1
WHERE del_flag = 0
  AND (
    component LIKE '%demo%'
    OR component LIKE '%examples/demo%'
    OR url LIKE '%/demo/%'
  );

-- 2. 删除 sys_role_permission 中关联已软删除 demo 权限的记录
DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE del_flag = 1
      AND (
        component LIKE '%demo%'
        OR component LIKE '%examples/demo%'
        OR url LIKE '%/demo/%'
      )
);

-- 3. 删除 airag_mcp 中 tool_list 包含 /demo/shop/ 的记录
DELETE FROM airag_mcp
WHERE tool_list LIKE '%/demo/shop/%';

-- 4. 删除 airag_flow 中 flow_data 包含 /test/jeecgDemo/ 的记录
DELETE FROM airag_flow
WHERE flow_data LIKE '%/test/jeecgDemo/%';
