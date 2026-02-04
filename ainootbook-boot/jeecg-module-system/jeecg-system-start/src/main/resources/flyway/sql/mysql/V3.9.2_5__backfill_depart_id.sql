-- 教学关系表改造 - 回填 depart_id
-- 版本: V3.9.2_5
-- 说明: 分批回填 depart_id，从 class_id 关联链获取组织 ID

-- 回填策略：
-- 1. ainote_teaching.class_id → ainote_class.major_id → ainote_major.dept_id
-- 2. ainote_course_selection.class_id → ainote_class.major_id → ainote_major.dept_id
-- 注意：无法映射的记录需要人工处理

-- 1. 回填 ainote_teaching 表的 depart_id
UPDATE ainote_teaching t
    INNER JOIN ainote_class c ON t.class_id = c.id
    INNER JOIN ainote_major m ON c.major_id = m.id
SET t.depart_id = m.dept_id
WHERE t.depart_id IS NULL
  AND t.class_id IS NOT NULL;

-- 2. 回填 ainote_course_selection 表的 depart_id
UPDATE ainote_course_selection cs
    INNER JOIN ainote_class c ON cs.class_id = c.id
    INNER JOIN ainote_major m ON c.major_id = m.id
SET cs.depart_id = m.dept_id
WHERE cs.depart_id IS NULL
  AND cs.class_id IS NOT NULL;

-- 3. 校验查询（不执行，仅供参考）
-- 查看未回填的记录数量：
-- SELECT COUNT(*) FROM ainote_teaching WHERE depart_id IS NULL;
-- SELECT COUNT(*) FROM ainote_course_selection WHERE depart_id IS NULL;
