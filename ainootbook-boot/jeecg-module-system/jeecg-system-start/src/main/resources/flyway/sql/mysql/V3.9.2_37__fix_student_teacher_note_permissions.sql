-- 修复学生笔记查看权限 + 教师菜单显示问题
-- 版本: V3.9.2_37
-- 说明:
--   1. 授权学生 ainote_note_view 菜单（虽然 hidden=1，但路由守卫需要）
--   2. 授权教师父菜单 ainote_note_mgr 和 ainote_main（确保子菜单能显示）

-- 1. 授权学生 ainote_note_view 菜单（解决 403 问题）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'student' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id = 'ainote_note_view'
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);

-- 2. 授权教师父菜单 ainote_note_mgr（确保 ainote_note_teacher 子菜单能显示）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'teacher' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id IN ('ainote_note_mgr', 'ainote_main')
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);
