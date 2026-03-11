-- AI 笔记配置表
-- 版本: V3.9.2_15
-- 说明: 集中管理 AI 笔记模块的模型选择、知识库绑定、提示词标识和参数上限

CREATE TABLE IF NOT EXISTS ainote_ai_config (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY              COMMENT '主键ID',
    summary_model_id   VARCHAR(36)  DEFAULT NULL                   COMMENT '摘要LLM模型ID（关联 airag_model.id）',
    knowledge_id       VARCHAR(36)  DEFAULT NULL                   COMMENT '笔记知识库ID（关联 airag_knowledge.id）',
    summary_prompt_key VARCHAR(50)  DEFAULT 'note_summary'         COMMENT '摘要提示词key',
    keywords_prompt_key VARCHAR(50) DEFAULT 'note_keywords'        COMMENT '关键词提示词key',
    integrate_prompt_key VARCHAR(50) DEFAULT 'note_integrate'      COMMENT '整合提示词key',
    max_summary_length INT          DEFAULT 200                    COMMENT '摘要最大长度（字符）',
    max_keywords_count INT          DEFAULT 5                      COMMENT '关键词最大数量',
    tenant_id       INT             DEFAULT NULL                   COMMENT '租户ID',
    create_by       VARCHAR(50)     DEFAULT NULL                   COMMENT '创建人',
    create_time     DATETIME        DEFAULT NULL                   COMMENT '创建时间',
    update_by       VARCHAR(50)     DEFAULT NULL                   COMMENT '更新人',
    update_time     DATETIME        DEFAULT NULL                   COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI笔记配置';
