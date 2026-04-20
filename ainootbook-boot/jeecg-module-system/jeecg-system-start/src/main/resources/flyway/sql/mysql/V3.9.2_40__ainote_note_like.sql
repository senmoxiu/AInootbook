-- 新增笔记点赞关系表与点赞权限
-- 版本: V3.9.2_40

CREATE TABLE IF NOT EXISTS ainote_note_like (
    id          VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '点赞ID',
    note_id     VARCHAR(32)  NOT NULL COMMENT '笔记ID',
    user_id     VARCHAR(32)  NOT NULL COMMENT '点赞用户ID',
    create_by   VARCHAR(32)  COMMENT '创建人',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by   VARCHAR(32)  COMMENT '更新人',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    sys_org_code VARCHAR(64) COMMENT '所属部门编码',
    tenant_id   INT          DEFAULT 0 COMMENT '租户ID',
    UNIQUE KEY uk_note_user_tenant (note_id, user_id, tenant_id),
    INDEX idx_note_id (note_id),
    INDEX idx_user_id (user_id),
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记点赞关系表';

-- 新增 ainote:note:like 按钮权限
INSERT IGNORE INTO sys_permission
    (id, parent_id, name, url, component, is_route, component_name, redirect,
     menu_type, perms, perms_type, sort_no, always_show, icon,
     is_leaf, keep_alive, hidden, hide_tab, description,
     create_by, create_time, update_by, update_time, del_flag, rule_flag, status, internal_or_external)
VALUES
('ainote_note_like', 'ainote_note_list', '点赞', NULL, NULL, 0, NULL, NULL,
 2, 'ainote:note:like', '1', 8, 0, NULL,
 1, 0, 0, 0, '笔记点赞/取消点赞',
 'admin', NOW(), NULL, NULL, 0, 0, '1', 0);

-- 授权给 admin / teacher / student
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM sys_role r
JOIN (SELECT id FROM sys_permission WHERE id = 'ainote_note_like' AND del_flag = 0) p
WHERE r.role_code IN ('admin', 'teacher', 'student')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission srp
    WHERE srp.role_id = r.id AND srp.permission_id = p.id
  );
