-- 清理非教育场景菜单与关联数据
-- 版本: V3.9.2_30
-- 说明:
-- 1. 软删除低代码/报表/Word 模板/OCR/MCP/OpenAPI/AI 写作/AI 海报/监控/大屏等非教育菜单
-- 2. 用递归 CTE 一次性收集所有层级子节点（MySQL 8.0+）
-- 3. 清理角色、部门、部门角色、租户套餐权限残留
-- 4. 清理对应业务表中的非教育数据

-- ============================================================
-- 1. 用递归 CTE 收集所有命中菜单及其全部子孙节点
-- ============================================================
UPDATE sys_permission
SET del_flag = 1
WHERE del_flag = 0
  AND id IN (
    WITH RECURSIVE menu_tree AS (
        -- 锚点：直接命中的非教育菜单
        SELECT id
        FROM sys_permission
        WHERE del_flag = 0
          AND (
              id = '1922109301837606914'
              OR id = '1912753560201089025'
              OR url LIKE '/online/%'
              OR component LIKE 'online/%'
              OR url LIKE '/jmreport%'
              OR url LIKE '/jimubi%'
              OR component LIKE 'jmreport/%'
              OR component LIKE '%wordtpl%'
              OR name LIKE '%Word模板%'
              OR url = '/ai/ocr'
              OR component LIKE '%/ocr/%'
              OR component LIKE '%/aimcp/%'
              OR name LIKE '%MCP%'
              OR url LIKE '/openapi%'
              OR url = '/openapi'
              OR component LIKE '%aiwriter%'
              OR component LIKE '%aiposter%'
              OR url LIKE '/monitor%'
              OR component LIKE '%drag%'
              OR name LIKE '%大屏%'
          )
        UNION ALL
        -- 递归：收集所有子孙节点
        SELECT child.id
        FROM sys_permission child
        INNER JOIN menu_tree parent ON child.parent_id = parent.id
        WHERE child.del_flag = 0
    )
    SELECT id FROM menu_tree
  );

-- ============================================================
-- 2. 清理权限关联（基于已软删除的菜单 id）
-- ============================================================
DELETE FROM sys_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE del_flag = 1
);

DELETE FROM sys_depart_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE del_flag = 1
);

DELETE FROM sys_depart_role_permission
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE del_flag = 1
);

DELETE FROM sys_tenant_pack_perms
WHERE permission_id IN (
    SELECT id FROM sys_permission WHERE del_flag = 1
);

-- ============================================================
-- 3. 清理业务数据（DELETE，不使用 TRUNCATE）
-- ============================================================
DELETE FROM onl_cgform_head;

DELETE FROM jimu_report;

DELETE FROM onl_drag_page;

DELETE FROM aigc_word_template;

DELETE FROM open_api_permission;

DELETE FROM open_api_log;

DELETE FROM open_api_auth;

DELETE FROM open_api;

-- ============================================================
-- 4. 人工校验 SQL（按需手工执行）
-- ============================================================
-- SELECT count(*)
-- FROM sys_permission
-- WHERE del_flag = 0
--   AND parent_id IN (SELECT id FROM sys_permission WHERE del_flag = 1);
