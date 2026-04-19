-- V3.9.2_39: 补授学生角色教学管理父级菜单权限
-- 原因: V3.9.2_38 授权了 teaching_selection 子菜单，但缺少父级 teaching_main，导致菜单不可见

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'teaching_main'
FROM (SELECT id FROM sys_role WHERE role_code = 'student' LIMIT 1) r
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = 'teaching_main'
);
