-- 清理失效菜单 + 修复路由
-- 版本: V3.9.2_13
-- 说明: V3.9.2_2 创建的原始 ainote_main 菜单树中，部分子菜单指向不存在的前端组件。
--       这些功能已在后续版本中迁移到 teaching_main 或嵌入笔记详情页，原菜单成为死链。
--       本脚本通过 del_flag=1 软删除这些失效条目，并删除其关联的角色权限。

-- ============================================================
-- 1. 软删除「基础管理」整个子树
--    原因: 专业/班级已复用 sys_depart 部门树；课程/章节已迁移到 teaching_main
-- ============================================================
UPDATE sys_permission SET del_flag = 1
WHERE id IN (
    'ainote_basic',
    'ainote_major', 'ainote_major_add', 'ainote_major_edit', 'ainote_major_delete', 'ainote_major_export',
    'ainote_class', 'ainote_class_add', 'ainote_class_edit', 'ainote_class_delete', 'ainote_class_export',
    'ainote_course', 'ainote_course_add', 'ainote_course_edit', 'ainote_course_delete', 'ainote_course_export',
    'ainote_chapter'
) AND del_flag = 0;

-- ============================================================
-- 2. 软删除「教学管理」旧子树（ainote_teaching_mgr）
--    原因: 教学安排已迁移到 teaching_main > teaching_assignment
--          选课管理已迁移到 teaching_main > teaching_selection
-- ============================================================
UPDATE sys_permission SET del_flag = 1
WHERE id IN (
    'ainote_teaching_mgr',
    'ainote_teaching',
    'ainote_selection'
) AND del_flag = 0;

-- ============================================================
-- 3. 软删除「素材管理」和「AI任务监控」独立菜单
--    原因: 素材管理已嵌入笔记详情页 (detail.vue 素材卡片)
--          AI任务监控已嵌入 AiProcessProgress 组件
-- ============================================================
UPDATE sys_permission SET del_flag = 1
WHERE id IN (
    'ainote_material',
    'ainote_ai_task'
) AND del_flag = 0;

-- ============================================================
-- 4. 清理已删除菜单的角色授权记录
-- ============================================================
DELETE FROM sys_role_permission
WHERE permission_id IN (
    'ainote_basic',
    'ainote_major', 'ainote_major_add', 'ainote_major_edit', 'ainote_major_delete', 'ainote_major_export',
    'ainote_class', 'ainote_class_add', 'ainote_class_edit', 'ainote_class_delete', 'ainote_class_export',
    'ainote_course', 'ainote_course_add', 'ainote_course_edit', 'ainote_course_delete', 'ainote_course_export',
    'ainote_chapter',
    'ainote_teaching_mgr', 'ainote_teaching', 'ainote_selection',
    'ainote_material', 'ainote_ai_task'
);
