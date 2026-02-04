-- AI课堂笔记智能整理系统 - 菜单权限配置
-- 版本: V3.9.2_2
-- 说明: 使用 INSERT IGNORE 确保幂等性

-- 1. 一级菜单：AI笔记系统
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES ('ainote_main', NULL, 'AI笔记系统', '/ainote', 'layouts/RouteView', 1, NULL, NULL, 0, NULL, '1', 10, 0, 'book', 0, 0, 0, 0, 'AI课堂笔记智能整理系统', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 2. 二级菜单：基础管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_basic', 'ainote_main', '基础管理', '/ainote/basic', 'layouts/RouteView', 1, NULL, NULL, 0, NULL, '1', 1, 0, 'setting', 0, 0, 0, 0, '基础数据管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_major', 'ainote_basic', '专业管理', '/ainote/major', 'ainote/major/MajorList', 1, 'MajorList', NULL, 1, NULL, '1', 1, 0, 'profile', 1, 1, 0, 0, '专业信息管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_class', 'ainote_basic', '班级管理', '/ainote/class', 'ainote/class/ClassList', 1, 'ClassList', NULL, 1, NULL, '1', 2, 0, 'team', 1, 1, 0, 0, '班级信息管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_course', 'ainote_basic', '课程管理', '/ainote/course', 'ainote/course/CourseList', 1, 'CourseList', NULL, 1, NULL, '1', 3, 0, 'read', 1, 1, 0, 0, '课程信息管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_chapter', 'ainote_basic', '章节管理', '/ainote/chapter', 'ainote/chapter/ChapterList', 1, 'ChapterList', NULL, 1, NULL, '1', 4, 0, 'ordered-list', 1, 1, 0, 0, '课程章节管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 3. 二级菜单：教学管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_teaching_mgr', 'ainote_main', '教学管理', '/ainote/teaching', 'layouts/RouteView', 1, NULL, NULL, 0, NULL, '1', 2, 0, 'solution', 0, 0, 0, 0, '教学相关管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_teaching', 'ainote_teaching_mgr', '教学安排', '/ainote/teaching/list', 'ainote/teaching/TeachingList', 1, 'TeachingList', NULL, 1, NULL, '1', 1, 0, 'schedule', 1, 1, 0, 0, '教学关系管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_selection', 'ainote_teaching_mgr', '选课管理', '/ainote/selection', 'ainote/selection/SelectionList', 1, 'SelectionList', NULL, 1, NULL, '1', 2, 0, 'select', 1, 1, 0, 0, '学生选课管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 4. 二级菜单：笔记管理（核心功能）
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_note_mgr', 'ainote_main', '笔记管理', '/ainote/note', 'layouts/RouteView', 1, NULL, NULL, 0, NULL, '1', 3, 0, 'edit', 0, 0, 0, 0, '笔记相关管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_list', 'ainote_note_mgr', '我的笔记', '/ainote/note/list', 'ainote/note/NoteList', 1, 'NoteList', NULL, 1, NULL, '1', 1, 0, 'file-text', 1, 1, 0, 0, '笔记列表', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_edit', 'ainote_note_mgr', '笔记编辑', '/ainote/note/edit/:id', 'ainote/note/NoteEdit', 1, 'NoteEdit', NULL, 1, NULL, '1', 2, 0, NULL, 1, 1, 1, 0, '笔记编辑页面', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_view', 'ainote_note_mgr', '笔记查看', '/ainote/note/view/:id', 'ainote/note/NoteView', 1, 'NoteView', NULL, 1, NULL, '1', 3, 0, NULL, 1, 1, 1, 0, '笔记查看页面', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_public', 'ainote_note_mgr', '公开笔记', '/ainote/note/public', 'ainote/note/PublicNoteList', 1, 'PublicNoteList', NULL, 1, NULL, '1', 4, 0, 'share-alt', 1, 1, 0, 0, '公开笔记广场', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 5. 二级菜单：素材管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_material', 'ainote_main', '素材管理', '/ainote/material', 'ainote/material/MaterialList', 1, 'MaterialList', NULL, 1, NULL, '1', 4, 0, 'file-image', 1, 1, 0, 0, '笔记素材管理', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 6. 二级菜单：AI任务监控
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_ai_task', 'ainote_main', 'AI任务监控', '/ainote/aitask', 'ainote/aitask/AiTaskList', 1, 'AiTaskList', NULL, 1, NULL, '1', 5, 0, 'robot', 1, 1, 0, 0, 'AI处理任务监控', 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 7. 按钮权限：专业管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_major_add', 'ainote_major', '添加', NULL, NULL, 0, NULL, NULL, 2, 'ainote:major:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_major_edit', 'ainote_major', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'ainote:major:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_major_delete', 'ainote_major', '删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:major:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_major_export', 'ainote_major', '导出', NULL, NULL, 0, NULL, NULL, 2, 'ainote:major:export', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 8. 按钮权限：班级管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_class_add', 'ainote_class', '添加', NULL, NULL, 0, NULL, NULL, 2, 'ainote:class:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_class_edit', 'ainote_class', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'ainote:class:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_class_delete', 'ainote_class', '删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:class:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_class_export', 'ainote_class', '导出', NULL, NULL, 0, NULL, NULL, 2, 'ainote:class:export', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 9. 按钮权限：课程管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_course_add', 'ainote_course', '添加', NULL, NULL, 0, NULL, NULL, 2, 'ainote:course:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_course_edit', 'ainote_course', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'ainote:course:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_course_delete', 'ainote_course', '删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:course:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_course_export', 'ainote_course', '导出', NULL, NULL, 0, NULL, NULL, 2, 'ainote:course:export', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 10. 按钮权限：笔记管理
INSERT IGNORE INTO sys_permission (id, parent_id, name, url, component, is_route, component_name, redirect, menu_type, perms, perms_type, sort_no, always_show, icon, is_leaf, keep_alive, hidden, hide_tab, description, create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_note_add', 'ainote_note_list', '新建笔记', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_edit_btn', 'ainote_note_list', '编辑', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:edit', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_delete', 'ainote_note_list', '删除', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:delete', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_share', 'ainote_note_list', '分享', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:share', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_export', 'ainote_note_list', '导出', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:export', '1', 5, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0),
('ainote_note_upload', 'ainote_note_list', '上传素材', NULL, NULL, 0, NULL, NULL, 2, 'ainote:note:upload', '1', 6, 0, NULL, 1, 0, 0, 0, NULL, 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);
