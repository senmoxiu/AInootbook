-- 补充前端引用但数据库中缺失的字典项
-- 版本: V3.9.2_14
-- 说明: 新增 ainote_course_status / ainote_selection_status / ainote_teaching_status 三个字典

-- 1. 课程状态字典（启用/禁用）—— 与后端 @Dict(dicCode = "course_status") 保持一致
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('course_status', '课程状态', 'course_status', '课程启用/禁用状态', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('course_status_1', 'course_status', '启用', '1', '课程启用', 1, 1, 'admin', NOW(), NULL, NULL),
('course_status_0', 'course_status', '禁用', '0', '课程禁用', 2, 1, 'admin', NOW(), NULL, NULL);

-- 2. 选课状态字典（正常/已退课）
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_selection_status', '选课状态', 'ainote_selection_status', '选课正常/已退课状态', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_selection_status_1', 'ainote_selection_status', '正常', '1', '选课正常', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_selection_status_0', 'ainote_selection_status', '已退课', '0', '已退课', 2, 1, 'admin', NOW(), NULL, NULL);

-- 3. 教学任务状态字典（启用/禁用）
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_teaching_status', '教学任务状态', 'ainote_teaching_status', '教学任务启用/禁用状态', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_teaching_status_1', 'ainote_teaching_status', '启用', '1', '教学任务启用', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_teaching_status_0', 'ainote_teaching_status', '禁用', '0', '教学任务禁用', 2, 1, 'admin', NOW(), NULL, NULL);

-- 4. 课程类型字典（与后端 @Dict(dicCode = "course_type") 保持一致）
-- 注意：V3.9.2_3 定义了 ainote_course_type，此处补充 course_type 以匹配后端注解
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('course_type', '课程类型', 'course_type', '课程的类型分类', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('course_type_1', 'course_type', '必修课', '1', '必修课程', 1, 1, 'admin', NOW(), NULL, NULL),
('course_type_2', 'course_type', '选修课', '2', '选修课程', 2, 1, 'admin', NOW(), NULL, NULL);

-- 5. 组织分类扩展字典项（院系/专业/班级），与 DepartTreeSelect 过滤条件对齐
INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'org_category_5', id, '院系', '5', '院系组织', 5, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict WHERE dict_code = 'org_category' LIMIT 1;

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'org_category_6', id, '专业', '6', '专业组织', 6, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict WHERE dict_code = 'org_category' LIMIT 1;

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
SELECT 'org_category_7', id, '班级', '7', '班级组织', 7, 1, 'admin', NOW(), NULL, NULL
FROM sys_dict WHERE dict_code = 'org_category' LIMIT 1;
