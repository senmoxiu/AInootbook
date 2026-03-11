-- 选课管理与章节管理模块 - 索引增强 + 菜单路由 + 按钮权限
-- 版本: V3.9.2_11
-- 说明: 为 ainote_course_selection 和 ainote_chapter 添加复合索引，
--       新增选课管理菜单路由，补充选课/章节按钮权限，并为管理员角色授权

-- ============================================================
-- 1. ainote_course_selection 复合索引增强（Flyway 保证单次执行，直接 CREATE）
-- ============================================================

-- 租户+学生+状态 复合索引（高频查询：某租户下某学生的有效选课）
CREATE INDEX idx_cs_tenant_student_status ON ainote_course_selection (tenant_id, student_id, status);

-- 租户+教学+状态 复合索引（高频查询：某租户下某教学任务的选课情况）
CREATE INDEX idx_cs_tenant_teaching_status ON ainote_course_selection (tenant_id, teaching_id, status);

-- ============================================================
-- 2. ainote_chapter 复合索引增强（Flyway 保证单次执行，直接 CREATE）
-- ============================================================

-- 租户+课程+父节点+排序 复合索引（高频查询：某课程下的章节树形结构）
CREATE INDEX idx_ch_tenant_course_parent_order ON ainote_chapter (tenant_id, course_id, parent_id, chapter_order);

-- ============================================================
-- 3. 选课管理菜单（挂在 teaching_main 下，与 course/assignment 同级）
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('teaching_selection', 'teaching_main', '选课管理', '/teaching/selection', 'ainote/teaching/selection/index', 1, 'TeachingSelection', NULL, 1, NULL, '1', 3, 0, 'ant-design:user-switch-outlined', 1, 1, 0, 0, '学生选课管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 4. 按钮权限：选课管理（挂在 teaching_selection 下）
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('teaching_selection_list',   'teaching_selection', '查询', NULL, NULL, 0, NULL, NULL, 2, 'teaching:selection:list',   '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_selection_add',    'teaching_selection', '添加', NULL, NULL, 0, NULL, NULL, 2, 'teaching:selection:add',    '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_selection_edit',   'teaching_selection', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'teaching:selection:edit',   '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_selection_delete', 'teaching_selection', '删除', NULL, NULL, 0, NULL, NULL, 2, 'teaching:selection:delete', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 5. 按钮权限：章节管理（挂在 teaching_course 下，章节从课程页面抽屉进入）
-- ============================================================
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('teaching_chapter_list',   'teaching_course', '章节查询', NULL, NULL, 0, NULL, NULL, 2, 'teaching:chapter:list',   '1', 10, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_chapter_add',    'teaching_course', '章节添加', NULL, NULL, 0, NULL, NULL, 2, 'teaching:chapter:add',    '1', 11, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_chapter_edit',   'teaching_course', '章节编辑', NULL, NULL, 0, NULL, NULL, 2, 'teaching:chapter:edit',   '1', 12, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('teaching_chapter_delete', 'teaching_course', '章节删除', NULL, NULL, 0, NULL, NULL, 2, 'teaching:chapter:delete', '1', 13, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- ============================================================
-- 6. 管理员角色授权（动态子查询，NOT EXISTS 防重复）
-- ============================================================
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.id IN (
    'teaching_selection',
    'teaching_selection_list',
    'teaching_selection_add',
    'teaching_selection_edit',
    'teaching_selection_delete',
    'teaching_chapter_list',
    'teaching_chapter_add',
    'teaching_chapter_edit',
    'teaching_chapter_delete'
)
WHERE r.role_code = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );
