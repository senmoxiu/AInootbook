-- 教学关系表改造 - 添加约束和索引
-- 版本: V3.9.2_6
-- 说明: 添加 NOT NULL 约束、唯一约束、索引
-- 前置条件: V3.9.2_5 回填完成且校验通过（无空值）

-- 1. 修改 ainote_teaching.depart_id 为 NOT NULL
ALTER TABLE ainote_teaching
    MODIFY COLUMN depart_id VARCHAR(32) NOT NULL COMMENT '组织ID（关联sys_depart，院系/专业/班级）';

-- 2. 添加唯一约束：防止同一租户下同一教师在同一学期对同一课程和组织重复配置
ALTER TABLE ainote_teaching
    ADD CONSTRAINT uk_teaching UNIQUE (tenant_id, teacher_id, course_id, depart_id, semester);

-- 3. 添加 depart_id 索引：优化按组织查询
CREATE INDEX idx_teaching_depart ON ainote_teaching (depart_id);

-- 4. 修改 ainote_course_selection.depart_id 为 NOT NULL
ALTER TABLE ainote_course_selection
    MODIFY COLUMN depart_id VARCHAR(32) NOT NULL COMMENT '组织ID（关联sys_depart）';

-- 5. 添加 depart_id 索引
CREATE INDEX idx_course_selection_depart ON ainote_course_selection (depart_id);
