-- 同步学校组织字典并补充教师、学生角色

-- 1. 修正 org_category 字典文案
UPDATE sys_dict_item item
JOIN sys_dict dict ON dict.id = item.dict_id AND dict.dict_code = 'org_category'
SET item.item_text = '学校',
    item.description = '学校组织',
    item.sort_order = 1,
    item.status = 1,
    item.update_by = 'admin',
    item.update_time = NOW()
WHERE item.item_value = '1';

UPDATE sys_dict_item item
JOIN sys_dict dict ON dict.id = item.dict_id AND dict.dict_code = 'org_category'
SET item.item_text = '组织单元',
    item.description = '组织单元',
    item.sort_order = 2,
    item.status = 1,
    item.update_by = 'admin',
    item.update_time = NOW()
WHERE item.item_value = '2';

INSERT IGNORE INTO sys_dict_item (
    id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time
)
SELECT 'org_category_5', id, '学院', '5', '学院组织', 5, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict
WHERE dict_code = 'org_category'
LIMIT 1;

INSERT IGNORE INTO sys_dict_item (
    id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time
)
SELECT 'org_category_6', id, '专业', '6', '专业组织', 6, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict
WHERE dict_code = 'org_category'
LIMIT 1;

INSERT IGNORE INTO sys_dict_item (
    id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time
)
SELECT 'org_category_7', id, '班级', '7', '班级组织', 7, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict
WHERE dict_code = 'org_category'
LIMIT 1;

UPDATE sys_dict_item item
JOIN sys_dict dict ON dict.id = item.dict_id AND dict.dict_code = 'org_category'
SET item.item_text = '学院',
    item.description = '学院组织',
    item.sort_order = 5,
    item.status = 1,
    item.update_by = 'admin',
    item.update_time = NOW()
WHERE item.item_value = '5';

UPDATE sys_dict_item item
JOIN sys_dict dict ON dict.id = item.dict_id AND dict.dict_code = 'org_category'
SET item.item_text = '专业',
    item.description = '专业组织',
    item.sort_order = 6,
    item.status = 1,
    item.update_by = 'admin',
    item.update_time = NOW()
WHERE item.item_value = '6';

UPDATE sys_dict_item item
JOIN sys_dict dict ON dict.id = item.dict_id AND dict.dict_code = 'org_category'
SET item.item_text = '班级',
    item.description = '班级组织',
    item.sort_order = 7,
    item.status = 1,
    item.update_by = 'admin',
    item.update_time = NOW()
WHERE item.item_value = '7';

-- 2. 补充教师、学生角色
UPDATE sys_role
SET role_name = '教师',
    description = CASE
        WHEN description IS NULL OR description = '' THEN '教师角色'
        ELSE description
    END,
    update_by = 'admin',
    update_time = NOW()
WHERE role_code = 'teacher';

INSERT INTO sys_role (
    id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id
)
SELECT
    REPLACE(UUID(), '-', ''), '教师', 'teacher', '教师角色', 'admin', NOW(), 'admin', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'teacher'
);

UPDATE sys_role
SET role_name = '学生',
    description = CASE
        WHEN description IS NULL OR description = '' THEN '学生角色'
        ELSE description
    END,
    update_by = 'admin',
    update_time = NOW()
WHERE role_code = 'student';

INSERT INTO sys_role (
    id, role_name, role_code, description, create_by, create_time, update_by, update_time, tenant_id
)
SELECT
    REPLACE(UUID(), '-', ''), '学生', 'student', '学生角色', 'admin', NOW(), 'admin', NOW(), 0
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'student'
);
