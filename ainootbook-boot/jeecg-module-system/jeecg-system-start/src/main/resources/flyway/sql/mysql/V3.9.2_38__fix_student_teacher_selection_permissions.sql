-- V3.9.2_38: 选课权限调整
-- 1. 移除教师角色的选课管理菜单/按钮权限（精确匹配 permission_id）
-- 2. 给学生角色补授选课相关权限（菜单 + 查询 + 新增 + 删除/退课）

-- Step 1: 移除教师角色的选课权限
DELETE rp FROM sys_role_permission rp
INNER JOIN sys_role r ON rp.role_id = r.id
WHERE r.role_code = 'teacher'
  AND rp.permission_id IN (
      'teaching_selection',
      'teaching_selection_list',
      'teaching_selection_add',
      'teaching_selection_edit',
      'teaching_selection_delete'
  );

-- Step 2: 给学生角色补授选课权限（幂等，NOT EXISTS 防重复）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'student' LIMIT 1) r
CROSS JOIN (
    SELECT id FROM sys_permission WHERE id IN (
        'teaching_selection',
        'teaching_selection_list',
        'teaching_selection_add',
        'teaching_selection_delete'
    ) AND del_flag = 0
) p
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
);
