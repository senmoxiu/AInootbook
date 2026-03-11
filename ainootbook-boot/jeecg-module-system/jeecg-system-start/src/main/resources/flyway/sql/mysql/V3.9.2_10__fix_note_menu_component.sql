-- AI笔记模块 - 菜单路由修复
-- 版本: V3.9.2_10
-- 修复: 笔记管理菜单 component 指向不存在的组件文件，导致前端路由无法加载
-- 问题: ainote_note_list 与父菜单 ainote_note_mgr URL 冲突，
--       ainote_note_public/ainote_note_edit 的 component 指向不存在的 Vue 文件

-- 1. 修复 ainote_note_list: URL 与父菜单冲突，改为 /ainote/note/list
UPDATE sys_permission SET url = '/ainote/note/list'
WHERE id = 'ainote_note_list' AND url = '/ainote/note';

-- 2. 修复 ainote_note_public: PublicNoteList 组件不存在，暂指向 index
UPDATE sys_permission SET component = 'ainote/note/index'
WHERE id = 'ainote_note_public' AND component = 'ainote/note/PublicNoteList';

-- 3. 修复 ainote_note_edit: NoteEdit 组件不存在，指向 detail
UPDATE sys_permission SET component = 'ainote/note/detail'
WHERE id = 'ainote_note_edit' AND component = 'ainote/note/NoteEdit';

-- 4. 父目录添加 redirect 到第一个子菜单
UPDATE sys_permission SET redirect = '/ainote/note/list'
WHERE id = 'ainote_note_mgr' AND redirect IS NULL;
