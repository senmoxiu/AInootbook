-- AI课堂笔记智能整理系统 - 数据字典配置
-- 版本: V3.9.2_3
-- 说明: 使用 INSERT IGNORE 确保幂等性，避免重复数据导致迁移失败

-- 1. 用户角色类型字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_user_role', '笔记系统用户角色', 'ainote_user_role', 'AI笔记系统用户角色类型', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_user_role_1', 'ainote_user_role', '学生', '1', '学生用户', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_user_role_2', 'ainote_user_role', '教师', '2', '教师用户', 2, 1, 'admin', NOW(), NULL, NULL),
('ainote_user_role_3', 'ainote_user_role', '管理员', '3', '系统管理员', 3, 1, 'admin', NOW(), NULL, NULL);

-- 2. 笔记状态字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_note_status', '笔记状态', 'ainote_note_status', '笔记的状态类型', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_note_status_1', 'ainote_note_status', '草稿', '1', '草稿状态', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_note_status_2', 'ainote_note_status', '已完成', '2', '已完成状态', 2, 1, 'admin', NOW(), NULL, NULL),
('ainote_note_status_3', 'ainote_note_status', '已删除', '3', '已删除状态', 3, 1, 'admin', NOW(), NULL, NULL);

-- 3. 文件类型字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_file_type', '素材文件类型', 'ainote_file_type', '笔记素材的文件类型', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_file_type_1', 'ainote_file_type', '音频', '1', '音频文件', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_file_type_2', 'ainote_file_type', '图片', '2', '图片文件', 2, 1, 'admin', NOW(), NULL, NULL),
('ainote_file_type_3', 'ainote_file_type', '视频', '3', '视频文件', 3, 1, 'admin', NOW(), NULL, NULL),
('ainote_file_type_4', 'ainote_file_type', '文档', '4', '文档文件', 4, 1, 'admin', NOW(), NULL, NULL);

-- 4. 处理状态字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_process_status', '处理状态', 'ainote_process_status', 'AI任务处理状态', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_process_status_0', 'ainote_process_status', '待处理', '0', '等待处理', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_process_status_1', 'ainote_process_status', '处理中', '1', '正在处理', 2, 1, 'admin', NOW(), NULL, NULL),
('ainote_process_status_2', 'ainote_process_status', '已完成', '2', '处理完成', 3, 1, 'admin', NOW(), NULL, NULL),
('ainote_process_status_3', 'ainote_process_status', '失败', '3', '处理失败', 4, 1, 'admin', NOW(), NULL, NULL);

-- 5. AI任务类型字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_task_type', 'AI任务类型', 'ainote_task_type', 'AI处理任务的类型', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_task_type_1', 'ainote_task_type', 'ASR语音转写', '1', '语音识别转文字', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_task_type_2', 'ainote_task_type', 'OCR文字识别', '2', '图片文字识别', 2, 1, 'admin', NOW(), NULL, NULL),
('ainote_task_type_3', 'ainote_task_type', 'NLP文本分析', '3', '自然语言处理分析', 3, 1, 'admin', NOW(), NULL, NULL),
('ainote_task_type_4', 'ainote_task_type', '摘要生成', '4', 'AI生成摘要', 4, 1, 'admin', NOW(), NULL, NULL);

-- 6. 课程类型字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_course_type', '课程类型', 'ainote_course_type', '课程的类型分类', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_course_type_1', 'ainote_course_type', '必修课', '1', '必修课程', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_course_type_2', 'ainote_course_type', '选修课', '2', '选修课程', 2, 1, 'admin', NOW(), NULL, NULL);

-- 7. 分享类型字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_share_type', '分享类型', 'ainote_share_type', '笔记分享的类型', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_share_type_1', 'ainote_share_type', '链接分享', '1', '通过链接分享', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_share_type_2', 'ainote_share_type', '二维码分享', '2', '通过二维码分享', 2, 1, 'admin', NOW(), NULL, NULL);

-- 8. 是否公开字典
INSERT IGNORE INTO sys_dict (id, dict_name, dict_code, description, del_flag, create_by, create_time, update_by, update_time, type)
VALUES ('ainote_is_public', '是否公开', 'ainote_is_public', '笔记是否公开', 0, 'admin', NOW(), NULL, NULL, 0);

INSERT IGNORE INTO sys_dict_item (id, dict_id, item_text, item_value, description, sort_order, status, create_by, create_time, update_by, update_time)
VALUES
('ainote_is_public_0', 'ainote_is_public', '私有', '0', '仅自己可见', 1, 1, 'admin', NOW(), NULL, NULL),
('ainote_is_public_1', 'ainote_is_public', '公开', '1', '所有人可见', 2, 1, 'admin', NOW(), NULL, NULL);
