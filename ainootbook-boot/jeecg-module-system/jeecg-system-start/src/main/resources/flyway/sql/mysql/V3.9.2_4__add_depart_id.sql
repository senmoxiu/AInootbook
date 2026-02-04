-- 教学关系表改造 - 添加 depart_id 字段
-- 版本: V3.9.2_4
-- 说明: 为 ainote_teaching 和 ainote_course_selection 添加 depart_id 字段（允许 NULL，后续回填）

-- 1. 为 ainote_teaching 添加 depart_id 字段
ALTER TABLE ainote_teaching
    ADD COLUMN depart_id VARCHAR(32) COMMENT '组织ID（关联sys_depart，院系/专业/班级）' AFTER class_id;

-- 2. 为 ainote_course_selection 添加 depart_id 字段
ALTER TABLE ainote_course_selection
    ADD COLUMN depart_id VARCHAR(32) COMMENT '组织ID（关联sys_depart）' AFTER class_id;
