-- V3.9.2_40: 扩展 ainote_teaching.teacher_id 字段长度，支持多教师逗号分隔存储
-- 单个 UUID 32位，最多支持 10 个教师：32*10 + 9个逗号 = 329，取 500 留余量
ALTER TABLE ainote_teaching MODIFY COLUMN teacher_id VARCHAR(500) COMMENT '教师ID，多个用逗号分隔';
