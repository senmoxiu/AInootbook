-- 补充首页仪表盘菜单（dashboard/analysis）
-- 版本: V3.9.2_34
-- 说明:
-- 1. 插入 dashboard 父菜单 + analysis 子菜单（幂等）
-- 2. 授权给 admin 角色（幂等）

-- ============================================================
-- 1. 插入 dashboard 父菜单（若不存在）
-- ============================================================
INSERT INTO sys_permission (
    id, parent_id, name, url, component,
    is_route, component_name, redirect, menu_type, perms, perms_type,
    sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab,
    description, create_by, create_time, update_by, update_time,
    del_flag, rule_flag, status, internal_or_external
)
SELECT
    'ainote_menu_dashboard', NULL, '首页', '/dashboard', 'layouts/default/index',
    1, '', '/dashboard/analysis', 0, NULL, '0',
    -1.00, 0, 'ant-design:home-outlined', 0, 0, 0, 0,
    'AI笔记本首页', 'admin', NOW(), 'admin', NOW(),
    0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE id = 'ainote_menu_dashboard'
);

-- ============================================================
-- 2. 插入 dashboard/analysis 子菜单（若不存在）
-- ============================================================
INSERT INTO sys_permission (
    id, parent_id, name, url, component,
    is_route, component_name, redirect, menu_type, perms, perms_type,
    sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab,
    description, create_by, create_time, update_by, update_time,
    del_flag, rule_flag, status, internal_or_external
)
SELECT
    'ainote_menu_dashboard_analysis', 'ainote_menu_dashboard', '工作台', '/dashboard/analysis', 'dashboard/Analysis/index',
    1, 'Analysis', NULL, 1, NULL, '0',
    1.00, 0, 'ant-design:dashboard-outlined', 1, 0, 0, 0,
    'AI笔记本工作台首页', 'admin', NOW(), 'admin', NOW(),
    0, 0, '1', 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE id = 'ainote_menu_dashboard_analysis'
);

-- ============================================================
-- 3. 授权 admin 角色（幂等）
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard'
FROM sys_role r
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard'
  )
LIMIT 1;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard_analysis'
FROM sys_role r
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard_analysis'
  )
LIMIT 1;

-- ============================================================
-- 4. 授权 teacher 角色（幂等）
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard'
FROM sys_role r
WHERE r.role_code = 'teacher'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard'
  )
LIMIT 1;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard_analysis'
FROM sys_role r
WHERE r.role_code = 'teacher'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard_analysis'
  )
LIMIT 1;

-- ============================================================
-- 5. 授权 student 角色（幂等）
-- ============================================================
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard'
FROM sys_role r
WHERE r.role_code = 'student'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard'
  )
LIMIT 1;

INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, 'ainote_menu_dashboard_analysis'
FROM sys_role r
WHERE r.role_code = 'student'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = 'ainote_menu_dashboard_analysis'
  )
LIMIT 1;
