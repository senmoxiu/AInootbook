-- 教师/学生/审计测试角色权限授权
-- 版本: V3.9.2_33
-- 说明: 按现有菜单路径动态授权，全部使用 NOT EXISTS 保证幂等

-- 1. admin：系统管理 + AI笔记 + 教学 + AI相关菜单/按钮
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'admin' LIMIT 1) r
JOIN (
    SELECT DISTINCT sp.id
    FROM sys_permission sp
    WHERE sp.del_flag = 0
      AND (
          sp.url = '/system' OR sp.url LIKE '/system/%' OR sp.component LIKE 'system/%'
          OR sp.url = '/ainote' OR sp.url LIKE '/ainote/%' OR sp.component LIKE 'ainote/%'
          OR sp.url = '/teaching' OR sp.url LIKE '/teaching/%'
          OR sp.url = '/airag' OR sp.url LIKE '/airag/%' OR sp.url LIKE '/super/airag/%' OR sp.component LIKE 'super/airag/%'
      )
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND (
          parent.url = '/system' OR parent.url LIKE '/system/%' OR parent.component LIKE 'system/%'
          OR parent.url = '/ainote' OR parent.url LIKE '/ainote/%' OR parent.component LIKE 'ainote/%'
          OR parent.url = '/teaching' OR parent.url LIKE '/teaching/%'
          OR parent.url = '/airag' OR parent.url LIKE '/airag/%' OR parent.url LIKE '/super/airag/%' OR parent.component LIKE 'super/airag/%'
      )
) p
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission srp
    WHERE srp.role_id = r.id
      AND srp.permission_id = p.id
);

-- 2. teacher：教学任务/选课/章节可维护，课程与笔记/AI配置只读
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'teacher' LIMIT 1) r
JOIN (
    SELECT DISTINCT sp.id
    FROM sys_permission sp
    WHERE sp.del_flag = 0
      AND (
          sp.url IN (
              '/ainote',
              '/teaching',
              '/teaching/course',
              '/teaching/assignment',
              '/teaching/selection',
              '/ainote/note',
              '/ainote/note/list',
              '/ainote/note/public',
              '/ainote/config'
          )
          OR sp.url LIKE '/ainote/note/view/%'
          OR sp.component IN (
              'ainote/teaching/course/index',
              'ainote/teaching/assignment/index',
              'ainote/teaching/selection/index',
              'ainote/config/index'
          )
      )
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND (
          parent.url IN ('/teaching/assignment', '/teaching/selection')
          OR parent.component IN ('ainote/teaching/assignment/index', 'ainote/teaching/selection/index')
      )
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND (parent.url = '/teaching/course' OR parent.component = 'ainote/teaching/course/index')
      AND child.perms LIKE 'teaching:chapter:%'
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND parent.url = '/ainote/note/list'
      AND child.perms = 'ainote:note:list'
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND (parent.url = '/ainote/config' OR parent.component = 'ainote/config/index')
      AND child.menu_type = 2
      AND child.perms = 'ainote:aiConfig:view'
) p
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission srp
    WHERE srp.role_id = r.id
      AND srp.permission_id = p.id
);

-- 3. student：笔记 CRUD + 素材能力 + 任务进度入口（存在则授权）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), r.id, p.id
FROM (SELECT id FROM sys_role WHERE role_code = 'student' LIMIT 1) r
JOIN (
    SELECT DISTINCT sp.id
    FROM sys_permission sp
    WHERE sp.del_flag = 0
      AND (
          sp.url IN (
              '/ainote',
              '/ainote/note',
              '/ainote/note/list',
              '/ainote/aitask'
          )
          OR sp.url LIKE '/ainote/note/edit/%'
          OR sp.url LIKE '/ainote/note/view/%'
          OR sp.url LIKE '/ainote/task/%'
          OR sp.component IN ('ainote/note/index', 'ainote/note/detail')
          OR sp.component LIKE 'ainote/aitask/%'
      )
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND parent.url = '/ainote/note/list'
      AND child.perms IN ('ainote:note:list', 'ainote:note:add', 'ainote:note:edit', 'ainote:note:delete')
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND parent.url = '/ainote/note/list'
      AND child.perms LIKE 'ainote:material:%'
    UNION
    SELECT DISTINCT child.id
    FROM sys_permission child
    JOIN sys_permission parent ON parent.id = child.parent_id
    WHERE child.del_flag = 0
      AND parent.del_flag = 0
      AND (
          parent.url = '/ainote/aitask'
          OR parent.url LIKE '/ainote/task/%'
          OR parent.component LIKE 'ainote/aitask/%'
      )
) p
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_role_permission srp
    WHERE srp.role_id = r.id
      AND srp.permission_id = p.id
);

-- 4. 审计测试角色：移除教育菜单相关权限
DELETE srp
FROM sys_role_permission srp
JOIN sys_role r ON r.id = srp.role_id
JOIN sys_permission p ON p.id = srp.permission_id
WHERE r.role_code = 'test'
  AND p.del_flag = 0
  AND (
      p.url = '/ainote' OR p.url LIKE '/ainote/%'
      OR p.url = '/teaching' OR p.url LIKE '/teaching/%'
      OR p.url = '/airag' OR p.url LIKE '/airag/%' OR p.url LIKE '/super/airag/%'
      OR p.component LIKE 'ainote/%'
      OR p.component LIKE 'super/airag/%'
  );
