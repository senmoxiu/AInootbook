-- AI笔记模块 - 菜单路径修复与权限码补充
-- 版本: V3.9.2_12
-- 说明: 修复 V3.9.2_10 遗漏的菜单 component 路径，补充笔记/素材按钮权限码

-- ============================================================
-- 1. 菜单路径修复
-- ============================================================

-- 1.1 ainote_note_list: NoteList 组件不存在，指向 index（同步 component_name 保证 keep-alive）
UPDATE sys_permission SET component = 'ainote/note/index', component_name = 'AinoteNoteIndex'
WHERE id = 'ainote_note_list' AND component = 'ainote/note/NoteList';

-- 1.2 ainote_note_view: NoteView 组件不存在，指向 detail
UPDATE sys_permission SET component = 'ainote/note/detail', component_name = 'AinoteNoteDetail'
WHERE id = 'ainote_note_view' AND component = 'ainote/note/NoteView';

-- 1.3 ainote_note_public: 从临时 index 指向独立的公开广场页面
UPDATE sys_permission SET component = 'ainote/note/public/index', component_name = 'AinoteNotePublic'
WHERE id = 'ainote_note_public' AND component = 'ainote/note/index';

-- ============================================================
-- 2. 笔记按钮权限补充（挂在 ainote_note_list 下）
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_note_list_btn', 'ainote_note_list', '查询', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:list', '1', 0, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_import',   'ainote_note_list', '导入', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:import', '1', 7, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 3. 素材按钮权限补充（挂在 ainote_note_list 下，素材从笔记详情页操作）
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_material_upload',      'ainote_note_list', '素材上传',   NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:upload',       '1', 10, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_material_list',        'ainote_note_list', '素材列表',   NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:list',         '1', 11, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_material_query',       'ainote_note_list', '素材查询',   NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:query',        '1', 12, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_material_url',         'ainote_note_list', '签名URL',    NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:presignedUrl', '1', 13, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_material_delete',      'ainote_note_list', '素材删除',   NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:delete',       '1', 14, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_material_deleteBatch', 'ainote_note_list', '素材批量删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:material:deleteBatch', '1', 15, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 4. 管理员角色授权
-- ============================================================
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.id IN (
    'ainote_note_list_btn',
    'ainote_note_import',
    'ainote_material_upload',
    'ainote_material_list',
    'ainote_material_query',
    'ainote_material_url',
    'ainote_material_delete',
    'ainote_material_deleteBatch'
)
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );
