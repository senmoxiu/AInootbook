-- 修复学校组织树编码、父子关系与叶子节点，并清理遗留企业组织数据

-- 1. 软删除遗留企业根节点及其整棵子树
UPDATE sys_depart child
JOIN (
    SELECT DISTINCT org_code
    FROM sys_depart
    WHERE depart_name IN ('北京国炬', '卓尔互动', '控股集团')
      AND (parent_id IS NULL OR parent_id = '')
      AND org_code IS NOT NULL
      AND org_code <> ''
) root ON child.org_code LIKE CONCAT(root.org_code, '%')
SET child.del_flag = '1',
    child.update_by = 'admin',
    child.update_time = NOW();

-- 2. 创建或修复学校根节点（固定根编码 A01）
-- 若 org_code=A01 已存在（id 可能不同），直接 UPDATE 修正；否则 INSERT
UPDATE sys_depart
SET id             = 'ainote_school_root',
    parent_id      = NULL,
    depart_name    = 'AInootbook学校',
    depart_name_en = 'AInootbook School',
    depart_name_abbr = 'AIN',
    depart_order   = 1,
    description    = 'AInootbook学校根节点',
    org_category   = '1',
    org_type       = '1',
    status         = '1',
    del_flag       = '0',
    iz_leaf        = 0,
    update_by      = 'admin',
    update_time    = NOW()
WHERE org_code = 'A01'
  AND (parent_id IS NULL OR parent_id = '');

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_school_root', NULL, 'AInootbook学校', 'AInootbook School', 'AIN',
    1, 'AInootbook学校根节点', '1', '1', 'A01',
    NULL, NULL, NULL, NULL, '1', '0',
    0, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE org_code = 'A01' AND (parent_id IS NULL OR parent_id = '')
);

-- 3. 补齐 V3.9.2_19 的教育组织节点（若历史迁移未成功执行）
INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_dept_cs', 'ainote_school_root', '计算机学院', 'School of Computer Science', 'CS',
    1, '计算机科学与技术学院', '5', '2', 'A01A01',
    NULL, NULL, NULL, NULL, '1', '0',
    0, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_dept_cs'
);

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_dept_ee', 'ainote_school_root', '电子信息学院', 'School of Electronics', 'EE',
    2, '电子信息工程学院', '5', '2', 'A01A02',
    NULL, NULL, NULL, NULL, '1', '0',
    1, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_dept_ee'
);

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_major_se', 'ainote_dept_cs', '软件工程', 'Software Engineering', 'SE',
    1, '软件工程专业', '6', '3', 'A01A01A01',
    NULL, NULL, NULL, NULL, '1', '0',
    0, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_major_se'
);

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_major_cs', 'ainote_dept_cs', '计算机科学', 'Computer Science', 'CS-MAJOR',
    2, '计算机科学与技术专业', '6', '3', 'A01A01A02',
    NULL, NULL, NULL, NULL, '1', '0',
    1, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_major_cs'
);

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_class_se2401', 'ainote_major_se', '软工2401班', 'SE Class 2401', 'SE2401',
    1, '软件工程2024级01班', '7', '4', 'A01A01A01A01',
    NULL, NULL, NULL, NULL, '1', '0',
    1, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_class_se2401'
);

INSERT INTO sys_depart (
    id, parent_id, depart_name, depart_name_en, depart_name_abbr,
    depart_order, description, org_category, org_type, org_code,
    mobile, fax, address, memo, status, del_flag,
    iz_leaf, create_by, create_time, update_by, update_time
)
SELECT
    'ainote_class_se2402', 'ainote_major_se', '软工2402班', 'SE Class 2402', 'SE2402',
    2, '软件工程2024级02班', '7', '4', 'A01A01A01A02',
    NULL, NULL, NULL, NULL, '1', '0',
    1, 'admin', NOW(), 'admin', NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_depart WHERE id = 'ainote_class_se2402'
);

-- 4. 修复教育组织的编码、父级关系、层级和叶子状态
UPDATE sys_depart
SET parent_id = 'ainote_school_root',
    depart_order = 1,
    org_category = '5',
    org_type = '2',
    org_code = 'A01A01',
    status = '1',
    del_flag = '0',
    iz_leaf = 0,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_dept_cs';

UPDATE sys_depart
SET parent_id = 'ainote_school_root',
    depart_order = 2,
    org_category = '5',
    org_type = '2',
    org_code = 'A01A02',
    status = '1',
    del_flag = '0',
    iz_leaf = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_dept_ee';

UPDATE sys_depart
SET parent_id = 'ainote_dept_cs',
    depart_order = 1,
    org_category = '6',
    org_type = '3',
    org_code = 'A01A01A01',
    status = '1',
    del_flag = '0',
    iz_leaf = 0,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_major_se';

UPDATE sys_depart
SET parent_id = 'ainote_dept_cs',
    depart_order = 2,
    org_category = '6',
    org_type = '3',
    org_code = 'A01A01A02',
    status = '1',
    del_flag = '0',
    iz_leaf = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_major_cs';

UPDATE sys_depart
SET parent_id = 'ainote_major_se',
    depart_order = 1,
    org_category = '7',
    org_type = '4',
    org_code = 'A01A01A01A01',
    status = '1',
    del_flag = '0',
    iz_leaf = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_class_se2401';

UPDATE sys_depart
SET parent_id = 'ainote_major_se',
    depart_order = 2,
    org_category = '7',
    org_type = '4',
    org_code = 'A01A01A01A02',
    status = '1',
    del_flag = '0',
    iz_leaf = 1,
    update_by = 'admin',
    update_time = NOW()
WHERE id = 'ainote_class_se2402';
