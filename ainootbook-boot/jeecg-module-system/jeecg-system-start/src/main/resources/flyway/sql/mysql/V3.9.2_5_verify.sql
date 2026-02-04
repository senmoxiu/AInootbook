-- 教学关系表改造 - 回填校验脚本
-- 版本: V3.9.2_5_verify
-- 说明: 在执行 V3.9.2_6 约束脚本前，必须确认以下查询结果均为 0

-- 1. 检查 ainote_teaching 表中 depart_id 为空的记录
SELECT 'ainote_teaching 未回填记录数' AS check_item, COUNT(*) AS count
FROM ainote_teaching
WHERE depart_id IS NULL;

-- 2. 检查 ainote_course_selection 表中 depart_id 为空的记录
SELECT 'ainote_course_selection 未回填记录数' AS check_item, COUNT(*) AS count
FROM ainote_course_selection
WHERE depart_id IS NULL;

-- 3. 检查 depart_id 是否都指向有效的 sys_depart 记录
SELECT 'ainote_teaching 无效 depart_id 数' AS check_item, COUNT(*) AS count
FROM ainote_teaching t
WHERE t.depart_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_depart d WHERE d.id = t.depart_id);

SELECT 'ainote_course_selection 无效 depart_id 数' AS check_item, COUNT(*) AS count
FROM ainote_course_selection cs
WHERE cs.depart_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_depart d WHERE d.id = cs.depart_id);

-- 4. 检查 depart_id 对应的 org_category 是否在允许范围内（5=院系, 6=专业, 7=班级）
SELECT 'ainote_teaching org_category 不合规数' AS check_item, COUNT(*) AS count
FROM ainote_teaching t
    INNER JOIN sys_depart d ON t.depart_id = d.id
WHERE d.org_category NOT IN ('5', '6', '7');

SELECT 'ainote_course_selection org_category 不合规数' AS check_item, COUNT(*) AS count
FROM ainote_course_selection cs
    INNER JOIN sys_depart d ON cs.depart_id = d.id
WHERE d.org_category NOT IN ('5', '6', '7');

-- 5. 【关键】检查唯一约束冲突：同一组合是否有重复记录
-- 如果 count > 0，说明回填后会触发 uk_teaching 唯一约束冲突，需先去重
SELECT 'ainote_teaching 唯一约束冲突数' AS check_item, COUNT(*) AS count
FROM (
    SELECT tenant_id, teacher_id, course_id, depart_id, semester, COUNT(*) AS cnt
    FROM ainote_teaching
    WHERE depart_id IS NOT NULL
    GROUP BY tenant_id, teacher_id, course_id, depart_id, semester
    HAVING cnt > 1
) dup;

-- 6. 列出唯一约束冲突的具体记录（如有冲突需人工处理）
-- SELECT tenant_id, teacher_id, course_id, depart_id, semester, COUNT(*) AS cnt
-- FROM ainote_teaching
-- WHERE depart_id IS NOT NULL
-- GROUP BY tenant_id, teacher_id, course_id, depart_id, semester
-- HAVING cnt > 1;

-- 7. 列出 depart_id 为空的记录（需人工补齐）
-- SELECT t.id, t.teacher_id, t.course_id, t.class_id, t.semester
-- FROM ainote_teaching t
-- WHERE t.depart_id IS NULL;
