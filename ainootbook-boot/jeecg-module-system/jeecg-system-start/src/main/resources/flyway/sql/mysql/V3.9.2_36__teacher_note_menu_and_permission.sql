-- 新增教师端学生笔记查看菜单与权限
-- 版本: V3.9.2_36
-- 说明: 在笔记管理下新增教师端学生笔记查看菜单及查询权限，并授权给 teacher 角色

-- 1. 补充学生笔记查看菜单及查询按钮权限
INSERT IGNORE INTO sys_permission
    (id, parent_id, name, url, component, is_route, component_name, redirect,
     menu_type, perms, perms_type, sort_no, always_show, icon,
     is_leaf, keep_alive, hidden, hide_tab, description,
     create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_note_teacher', 'ainote_note_mgr', '学生笔记查看', '/ainote/note/teacher', 'ainote/note/teacher-view/index', 1, 'AinoteNoteTeacherView', NULL, 1, NULL, '1', 3, 0, NULL, 1, 1, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_teacher_list', 'ainote_note_teacher', '查询', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:teacherList', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 2. 授权 teacher 角色（菜单 + 查询按钮）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'teacher' LIMIT 1) r
JOIN (
    SELECT id FROM sys_permission
    WHERE id IN ('ainote_note_teacher', 'ainote_note_teacher_list')
      AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);
