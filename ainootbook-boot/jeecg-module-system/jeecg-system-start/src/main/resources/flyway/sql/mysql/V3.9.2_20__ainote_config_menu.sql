-- AI笔记模块 - AI配置管理菜单
-- 版本: V3.9.2_20
-- 说明: 新增 AI配置 菜单（挂在 ainote_main 下），仅 admin 角色可见

-- ============================================================
-- 1. AI配置菜单页（menu_type=1，一级子菜单）
-- ============================================================
INSERT IGNORE INTO sys_permission
    (id, parent_id, name, url, component, is_route, component_name, redirect,
     menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf,
     keep_alive, hidden, hide_tab, description, create_by, create_time,
     update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
    ('ainote_ai_config', 'ainote_main', 'AI智能配置', '/ainote/config', 'ainote/config/index', 1,
     'AinoteAiConfig', NULL, 1, NULL, '1', 9, 0, 'setting', 1, 1, 0, 0,
     'AI笔记智能配置管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 2. 按钮权限（menu_type=2，挂在 ainote_ai_config 下）
-- ============================================================
INSERT IGNORE INTO sys_permission
    (id, parent_id, name, url, component, is_route, component_name, redirect,
     menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf,
     keep_alive, hidden, hide_tab, description, create_by, create_time,
     update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
    ('ainote_ai_config_view', 'ainote_ai_config', '查看', NULL, NULL, 0, NULL, NULL,
     2, 'ainote:aiConfig:view', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
    ('ainote_ai_config_edit', 'ainote_ai_config', '编辑', NULL, NULL, 0, NULL, NULL,
     2, 'ainote:aiConfig:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 3. 授权 admin 角色
-- ============================================================
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.id IN (
    'ainote_ai_config',
    'ainote_ai_config_view',
    'ainote_ai_config_edit'
)
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );
