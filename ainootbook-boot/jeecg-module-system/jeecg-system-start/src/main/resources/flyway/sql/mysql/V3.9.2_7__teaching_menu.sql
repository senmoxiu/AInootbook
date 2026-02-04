-- 教学关系改造 - 菜单权限配置
-- 版本: V3.9.2_7
-- 说明: 添加课程库管理和教学任务配置的菜单权限

-- 1. 一级菜单：教学管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('teaching_main', NULL, '教学管理', '/teaching', 'layouts/RouteView', 1, NULL, '/teaching/course', 0, NULL, '1', 50, 0, 'ant-design:book-outlined', 0, 0, 0, 0, '教学管理模块', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 2. 二级菜单：课程库管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('teaching_course', 'teaching_main', '课程库管理', '/teaching/course', 'ainote/teaching/course/index', 1, 'TeachingCourse', NULL, 1, NULL, '1', 1, 0, 'ant-design:book-outlined', 1, 1, 0, 0, '课程库管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 3. 二级菜单：教学任务配置
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('teaching_assignment', 'teaching_main', '教学任务配置', '/teaching/assignment', 'ainote/teaching/assignment/index', 1, 'TeachingAssignment', NULL, 1, NULL, '1', 2, 0, 'ant-design:setting-outlined', 1, 1, 0, 0, '教学任务配置', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 4. 按钮权限：课程库管理
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('teaching_course_add', 'teaching_course', '添加', NULL, NULL, 0, NULL, NULL, 2, 'teaching:course:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_course_edit', 'teaching_course', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'teaching:course:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_course_delete', 'teaching_course', '删除', NULL, NULL, 0, NULL, NULL, 2, 'teaching:course:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_course_export', 'teaching_course', '导出', NULL, NULL, 0, NULL, NULL, 2, 'teaching:course:export', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_course_import', 'teaching_course', '导入', NULL, NULL, 0, NULL, NULL, 2, 'teaching:course:import', '1', 5, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 5. 按钮权限：教学任务配置
INSERT INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('teaching_assignment_add', 'teaching_assignment', '添加', NULL, NULL, 0, NULL, NULL, 2, 'teaching:assignment:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_assignment_edit', 'teaching_assignment', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'teaching:assignment:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_assignment_delete', 'teaching_assignment', '删除', NULL, NULL, 0, NULL, NULL, 2, 'teaching:assignment:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 6. 为管理员角色授权（假设 admin 角色 ID 为 'f6817f48af4fb3af11b9e8bf182f618b'）
-- 注意：实际部署时需要根据实际角色 ID 调整
-- INSERT INTO sys_role_permission (id, role_id, permission_id)
-- SELECT UUID(), 'f6817f48af4fb3af11b9e8bf182f618b', id FROM sys_permission WHERE id LIKE 'teaching_%';
