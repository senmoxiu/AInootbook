-- V3.9.2_19: 补充教育类组织数据（院系/专业/班级）
-- orgCategory: 5=院系, 6=专业, 7=班级
-- 依赖：sys_depart 表中必须有 org_category 字段

-- 查找根部门ID（公司），作为院系的父节点
-- 注意：此处使用固定ID，避免依赖查询结果

-- 院系（orgCategory=5）
INSERT IGNORE INTO `sys_depart`
    (`id`, `parent_id`, `depart_name`, `depart_name_en`, `depart_name_abbr`,
     `depart_order`, `description`, `org_category`, `org_type`, `org_code`,
     `mobile`, `fax`, `address`, `memo`, `status`, `del_flag`,
     `iz_leaf`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('ainote_dept_cs', NULL, '计算机学院', 'School of Computer Science', 'CS',
     1, '计算机科学与技术学院', '5', '1', 'CS',
     NULL, NULL, NULL, NULL, '1', 0,
     '0', 'admin', NOW(), 'admin', NOW()),
    ('ainote_dept_ee', NULL, '电子信息学院', 'School of Electronics', 'EE',
     2, '电子信息工程学院', '5', '1', 'EE',
     NULL, NULL, NULL, NULL, '1', 0,
     '0', 'admin', NOW(), 'admin', NOW());

-- 专业（orgCategory=6，挂载在计算机学院下）
INSERT IGNORE INTO `sys_depart`
    (`id`, `parent_id`, `depart_name`, `depart_name_en`, `depart_name_abbr`,
     `depart_order`, `description`, `org_category`, `org_type`, `org_code`,
     `mobile`, `fax`, `address`, `memo`, `status`, `del_flag`,
     `iz_leaf`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('ainote_major_se', 'ainote_dept_cs', '软件工程', 'Software Engineering', 'SE',
     1, '软件工程专业', '6', '1', 'CS-SE',
     NULL, NULL, NULL, NULL, '1', 0,
     '0', 'admin', NOW(), 'admin', NOW()),
    ('ainote_major_cs', 'ainote_dept_cs', '计算机科学', 'Computer Science', 'CS-MAJOR',
     2, '计算机科学与技术专业', '6', '1', 'CS-CS',
     NULL, NULL, NULL, NULL, '1', 0,
     '0', 'admin', NOW(), 'admin', NOW());

-- 班级（orgCategory=7，挂载在软件工程专业下）
INSERT IGNORE INTO `sys_depart`
    (`id`, `parent_id`, `depart_name`, `depart_name_en`, `depart_name_abbr`,
     `depart_order`, `description`, `org_category`, `org_type`, `org_code`,
     `mobile`, `fax`, `address`, `memo`, `status`, `del_flag`,
     `iz_leaf`, `create_by`, `create_time`, `update_by`, `update_time`)
VALUES
    ('ainote_class_se2401', 'ainote_major_se', '软工2401班', 'SE Class 2401', 'SE2401',
     1, '软件工程2024级01班', '7', '1', 'CS-SE-2401',
     NULL, NULL, NULL, NULL, '1', 0,
     '1', 'admin', NOW(), 'admin', NOW()),
    ('ainote_class_se2402', 'ainote_major_se', '软工2402班', 'SE Class 2402', 'SE2402',
     2, '软件工程2024级02班', '7', '1', 'CS-SE-2402',
     NULL, NULL, NULL, NULL, '1', 0,
     '1', 'admin', NOW(), 'admin', NOW());
