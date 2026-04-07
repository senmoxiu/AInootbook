-- 修复 ainote:task:* 权限缺失
-- 版本: V3.9.2_35
-- 问题: AinoteAiTaskController 使用 @RequiresPermissions("ainote:task:*")，
--       但 sys_permission 中从未插入对应的按钮权限记录，导致所有角色（含 admin）均报 403。

-- 1. 补充 AI任务监控 按钮权限（挂在 ainote_ai_task 菜单下）
INSERT IGNORE INTO sys_permission
    (id, parent_id, name, url, component, is_route, component_name, redirect,
     menu_type, perms, perms_type, sort_no, always_show, icon,
     is_leaf, keep_alive, hidden, hide_tab, description,
     create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_task_list',   'ainote_ai_task', '查询', NULL, NULL, 0, NULL, NULL, 2, 'ainote:task:list',   '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_task_query',  'ainote_ai_task', '详情', NULL, NULL, 0, NULL, NULL, 2, 'ainote:task:query',  '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_task_delete', 'ainote_ai_task', '删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:task:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_task_all',    'ainote_ai_task', '全部', NULL, NULL, 0, NULL, NULL, 2, 'ainote:task:*',      '1', 0, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 2. 授权 admin 角色（含 ainote_ai_task 菜单本身 + 所有按钮）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'admin' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id IN ('ainote_ai_task', 'ainote_task_list', 'ainote_task_query', 'ainote_task_delete', 'ainote_task_all')
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);

-- 3. 授权 student 角色（只需查询 + 进度，不需要删除）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'student' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id IN ('ainote_ai_task', 'ainote_task_list', 'ainote_task_query', 'ainote_task_all')
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);

-- 4. 授权 teacher 角色（只读）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'teacher' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id IN ('ainote_ai_task', 'ainote_task_list', 'ainote_task_query', 'ainote_task_all')
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);
