-- AI课堂笔记智能整理系统 - 数据库初始化脚本
-- 版本: V3.9.2_1
-- 说明: 创建笔记系统核心业务表

-- 1. 专业表 (ainote_major)
CREATE TABLE IF NOT EXISTS ainote_major (
    id VARCHAR(32) PRIMARY KEY COMMENT '专业ID',
    major_name VARCHAR(100) NOT NULL COMMENT '专业名称',
    major_code VARCHAR(20) NOT NULL UNIQUE COMMENT '专业代码',
    dept_id VARCHAR(32) NOT NULL COMMENT '所属院系ID（关联sys_depart）',
    major_desc TEXT COMMENT '专业描述',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    INDEX idx_dept (dept_id),
    INDEX idx_code (major_code),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业表';

-- 2. 班级表 (ainote_class)
CREATE TABLE IF NOT EXISTS ainote_class (
    id VARCHAR(32) PRIMARY KEY COMMENT '班级ID',
    class_name VARCHAR(100) NOT NULL COMMENT '班级名称',
    class_code VARCHAR(20) NOT NULL UNIQUE COMMENT '班级代码',
    major_id VARCHAR(32) NOT NULL COMMENT '所属专业ID',
    grade YEAR NOT NULL COMMENT '年级',
    student_count INT DEFAULT 0 COMMENT '学生人数',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (major_id) REFERENCES ainote_major(id) ON DELETE CASCADE,
    INDEX idx_major (major_id),
    INDEX idx_grade (grade),
    INDEX idx_code (class_code),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级表';

-- 3. 课程表 (ainote_course)
CREATE TABLE IF NOT EXISTS ainote_course (
    id VARCHAR(32) PRIMARY KEY COMMENT '课程ID',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    course_code VARCHAR(20) NOT NULL UNIQUE COMMENT '课程代码',
    credits DECIMAL(3,1) COMMENT '学分',
    course_hours INT COMMENT '课时',
    course_desc TEXT COMMENT '课程描述',
    course_type TINYINT DEFAULT 1 COMMENT '课程类型：1-必修，2-选修',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    INDEX idx_code (course_code),
    INDEX idx_type (course_type),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 4. 教学关系表 (ainote_teaching)
CREATE TABLE IF NOT EXISTS ainote_teaching (
    id VARCHAR(32) PRIMARY KEY COMMENT '教学ID',
    teacher_id VARCHAR(32) NOT NULL COMMENT '教师ID（关联sys_user）',
    course_id VARCHAR(32) NOT NULL COMMENT '课程ID',
    class_id VARCHAR(32) COMMENT '班级ID（可选，为空表示面向所有班级）',
    semester VARCHAR(20) NOT NULL COMMENT '学期（如：2024-2025-1）',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年（如：2024-2025）',
    status TINYINT DEFAULT 1 COMMENT '状态：0-已结束，1-进行中',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (teacher_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES ainote_course(id) ON DELETE CASCADE,
    FOREIGN KEY (class_id) REFERENCES ainote_class(id) ON DELETE SET NULL,
    INDEX idx_teacher (teacher_id),
    INDEX idx_course (course_id),
    INDEX idx_class (class_id),
    INDEX idx_semester (semester),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学关系表';

-- 5. 选课表 (ainote_course_selection)
CREATE TABLE IF NOT EXISTS ainote_course_selection (
    id VARCHAR(32) PRIMARY KEY COMMENT '选课ID',
    student_id VARCHAR(32) NOT NULL COMMENT '学生ID（关联sys_user）',
    teaching_id VARCHAR(32) NOT NULL COMMENT '教学ID',
    course_id VARCHAR(32) NOT NULL COMMENT '课程ID',
    class_id VARCHAR(32) NOT NULL COMMENT '班级ID',
    semester VARCHAR(20) NOT NULL COMMENT '学期',
    academic_year VARCHAR(20) NOT NULL COMMENT '学年',
    status TINYINT DEFAULT 1 COMMENT '状态：0-已退课，1-正常',
    selected_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '选课时间',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (student_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (teaching_id) REFERENCES ainote_teaching(id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES ainote_course(id) ON DELETE CASCADE,
    FOREIGN KEY (class_id) REFERENCES ainote_class(id) ON DELETE CASCADE,
    UNIQUE KEY uk_student_teaching (student_id, teaching_id),
    INDEX idx_student (student_id),
    INDEX idx_teaching (teaching_id),
    INDEX idx_course (course_id),
    INDEX idx_class (class_id),
    INDEX idx_semester (semester),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课表';

-- 6. 章节表 (ainote_chapter)
CREATE TABLE IF NOT EXISTS ainote_chapter (
    id VARCHAR(32) PRIMARY KEY COMMENT '章节ID',
    course_id VARCHAR(32) NOT NULL COMMENT '课程ID',
    chapter_name VARCHAR(200) NOT NULL COMMENT '章节名称',
    chapter_order INT NOT NULL COMMENT '章节序号',
    parent_id VARCHAR(32) COMMENT '父章节ID（支持多级章节）',
    chapter_desc TEXT COMMENT '章节描述',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (course_id) REFERENCES ainote_course(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES ainote_chapter(id) ON DELETE CASCADE,
    INDEX idx_course (course_id),
    INDEX idx_parent (parent_id),
    INDEX idx_order (chapter_order),
    INDEX idx_status (status),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节表';

-- 7. 笔记表 (ainote_note) - 核心表
CREATE TABLE IF NOT EXISTS ainote_note (
    id VARCHAR(32) PRIMARY KEY COMMENT '笔记ID',
    student_id VARCHAR(32) NOT NULL COMMENT '学生ID（关联sys_user）',
    course_id VARCHAR(32) NOT NULL COMMENT '课程ID',
    chapter_id VARCHAR(32) COMMENT '章节ID',
    teaching_id VARCHAR(32) COMMENT '教学ID',
    note_title VARCHAR(200) NOT NULL COMMENT '笔记标题',
    note_content LONGTEXT COMMENT '笔记内容（Markdown格式）',
    ai_summary TEXT COMMENT 'AI生成的摘要',
    keywords VARCHAR(500) COMMENT '关键词（逗号分隔）',
    note_status TINYINT DEFAULT 1 COMMENT '笔记状态：1-草稿，2-已完成，3-已删除',
    is_public TINYINT DEFAULT 0 COMMENT '是否公开：0-私有，1-公开',
    view_count INT DEFAULT 0 COMMENT '浏览次数',
    like_count INT DEFAULT 0 COMMENT '点赞次数',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (student_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (course_id) REFERENCES ainote_course(id) ON DELETE CASCADE,
    FOREIGN KEY (chapter_id) REFERENCES ainote_chapter(id) ON DELETE SET NULL,
    FOREIGN KEY (teaching_id) REFERENCES ainote_teaching(id) ON DELETE SET NULL,
    INDEX idx_student (student_id),
    INDEX idx_course (course_id),
    INDEX idx_chapter (chapter_id),
    INDEX idx_teaching (teaching_id),
    INDEX idx_status (note_status),
    INDEX idx_public (is_public),
    INDEX idx_created (create_time),
    INDEX idx_tenant (tenant_id),
    FULLTEXT INDEX ft_content (note_title, note_content, keywords)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- 8. 素材表 (ainote_material)
CREATE TABLE IF NOT EXISTS ainote_material (
    id VARCHAR(32) PRIMARY KEY COMMENT '素材ID',
    note_id VARCHAR(32) NOT NULL COMMENT '笔记ID',
    file_type TINYINT NOT NULL COMMENT '文件类型：1-音频，2-图片，3-视频，4-文档',
    file_path VARCHAR(500) NOT NULL COMMENT '文件路径（OSS路径）',
    file_url VARCHAR(500) COMMENT '文件访问URL',
    file_size BIGINT COMMENT '文件大小（字节）',
    file_name VARCHAR(255) NOT NULL COMMENT '文件名',
    file_ext VARCHAR(20) COMMENT '文件扩展名',
    duration INT COMMENT '时长（秒，仅音视频）',
    process_status TINYINT DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-已完成，3-失败',
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (note_id) REFERENCES ainote_note(id) ON DELETE CASCADE,
    INDEX idx_note (note_id),
    INDEX idx_type (file_type),
    INDEX idx_status (process_status),
    INDEX idx_uploaded (uploaded_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='素材表';

-- 9. AI处理任务表 (ainote_ai_task)
CREATE TABLE IF NOT EXISTS ainote_ai_task (
    id VARCHAR(32) PRIMARY KEY COMMENT '任务ID',
    material_id VARCHAR(32) NOT NULL COMMENT '素材ID',
    note_id VARCHAR(32) NOT NULL COMMENT '笔记ID',
    task_type TINYINT NOT NULL COMMENT '任务类型：1-ASR语音转写，2-OCR文字识别，3-NLP文本分析，4-摘要生成',
    task_status TINYINT DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-已完成，3-失败',
    model_id VARCHAR(36) COMMENT 'AI模型ID（关联airag_model）',
    process_result LONGTEXT COMMENT '处理结果（JSON格式）',
    error_message TEXT COMMENT '错误信息',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    started_at DATETIME COMMENT '开始时间',
    completed_at DATETIME COMMENT '完成时间',
    duration INT COMMENT '处理耗时（秒）',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (material_id) REFERENCES ainote_material(id) ON DELETE CASCADE,
    FOREIGN KEY (note_id) REFERENCES ainote_note(id) ON DELETE CASCADE,
    INDEX idx_material (material_id),
    INDEX idx_note (note_id),
    INDEX idx_type (task_type),
    INDEX idx_status (task_status),
    INDEX idx_model (model_id),
    INDEX idx_created (create_time),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI处理任务表';

-- 10. 笔记分享表 (ainote_share)
CREATE TABLE IF NOT EXISTS ainote_share (
    id VARCHAR(32) PRIMARY KEY COMMENT '分享ID',
    note_id VARCHAR(32) NOT NULL COMMENT '笔记ID',
    share_code VARCHAR(32) NOT NULL UNIQUE COMMENT '分享码',
    share_type TINYINT DEFAULT 1 COMMENT '分享类型：1-链接分享，2-二维码分享',
    expire_time DATETIME COMMENT '过期时间（NULL表示永久）',
    view_count INT DEFAULT 0 COMMENT '查看次数',
    status TINYINT DEFAULT 1 COMMENT '状态：0-已失效，1-有效',
    create_by VARCHAR(32) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(32) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id INT DEFAULT 0 COMMENT '租户ID',
    FOREIGN KEY (note_id) REFERENCES ainote_note(id) ON DELETE CASCADE,
    INDEX idx_note (note_id),
    INDEX idx_code (share_code),
    INDEX idx_status (status),
    INDEX idx_expire (expire_time),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记分享表';

-- 插入初始数据（示例）
-- 注意：实际使用时需要根据具体情况调整
