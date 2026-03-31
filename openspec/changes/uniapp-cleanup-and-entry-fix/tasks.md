# Tasks: UniApp 清理与入口修复

## 1. TabBar 学习入口配置

- [x] 1.1 修改 `ainootbook-uniapp/src/pages.config.ts`，TabBar 配置新增"学习"Tab，替换"消息"Tab
- [x] 1.2 配置"学习"Tab 的 `pagePath` 为 `pages-study/course/list`
- [x] 1.3 准备"学习"Tab 的图标资源（`iconPath` 和 `selectedIconPath`，81px × 81px）
- [x] 1.4 验证 TabBar 配置：启动应用，确认显示 3 个 Tab（首页、学习、我的）

## 2. 路由名去重修复

- [x] 2.1 修改 `ainootbook-uniapp/src/router/index.ts` 的 `setRouteName()` 函数
- [x] 2.2 实现完整路径规范名生成：`path.replace(/^pages-?/, '').replace(/\//g, '-')`
- [x] 2.3 验证路由名生成：`pages-study/course/list` → `study-course-list`
- [x] 2.4 验证路由名生成：`pages-study/note/list` → `study-note-list`
- [x] 2.5 验证路由名生成：`pages-study/course/detail` → `study-course-detail`
- [x] 2.6 验证路由名生成：`pages-study/note/detail` → `study-note-detail`
- [ ] 2.7 添加构建时路由名冲突检测（可选）

## 3. 前后端契约修复

- [x] 3.1 修改 `ainootbook-uniapp/src/api/note.ts`，更新 API 类型定义
- [x] 3.2 修复 `noteApi.addNote` 返回类型：`Promise<string>`（noteId）
- [x] 3.3 修复 `noteApi.regenerateNote` 返回类型：`Promise<{ version: number, noteContent: string }>`
- [x] 3.4 新增 `noteApi.getProgress(generationId)` 方法（封装 `/progress` 接口）
- [x] 3.5 新增 `noteApi.cancelGeneration(generationId)` 方法（封装 `/cancelGeneration` 接口）
- [x] 3.6 修改 `ainootbook-uniapp/src/pages-study/note/components/NoteCard.vue`，字段名对齐：`note.title` → `note.noteTitle`，`note.summary` → `note.aiSummary`
- [x] 3.7 修改 `ainootbook-uniapp/src/pages-study/note/detail.vue`，编辑保存补充 `baseVersion` 参数
- [x] 3.8 修改 `ainootbook-uniapp/src/types/note.ts`，更新 `Note` 和 `NoteVersion` 类型定义

## 4. AI 进度轮询实现（混合双车道）

- [x] 4.1 修改 `ainootbook-uniapp/src/components/AiProgressTracker.vue`，改用 `noteApi.getProgress()` 封装
- [x] 4.2 实现自适应间隔轮询：[2s, 4s, 8s, 10s]
- [x] 4.3 实现轮询超时保护：最大 5 分钟
- [x] 4.4 实现轮询失败重试：最多 3 次
- [x] 4.5 实现生命周期管理：`onShow` 启动、`onHide` 暂停、`onUnload` 停止
- [x] 4.6 实现单例模式：同一 `noteId` 只允许一个轮询实例
- [x] 4.7 实现取消功能：调用 `noteApi.cancelGeneration()`
- [x] 4.8 修改 `ainootbook-uniapp/src/pages-study/note/wizard.vue`，素材处理使用异步轮询
- [x] 4.9 修改 `ainootbook-uniapp/src/pages-study/note/detail.vue`，regenerate 使用同步 loading（不使用 AiProgressTracker）
- [x] 4.10 修改 `ainootbook-uniapp/src/store/aiProgress.ts`，实现进度状态管理

## 5. Demo 代码清理

- [ ] 5.1 删除 `ainootbook-uniapp/src/pages/about/` 目录
- [ ] 5.2 删除 `ainootbook-uniapp/src/service/app/pet.ts` 文件
- [ ] 5.3 删除 `ainootbook-uniapp/src/layouts/demo.vue` 文件
- [ ] 5.4 删除 `ainootbook-uniapp/src/service/index/foo.ts` 文件（如果存在）
- [ ] 5.5 修改 `ainootbook-uniapp/src/common/work.ts`，删除 `routeIndex='demo'` 的"组件示例"入口
- [ ] 5.6 修改 `ainootbook-uniapp/src/pages/index/index.vue`，删除 `fallback` 到 `router.replace({ name: 'demo' })` 的逻辑
- [ ] 5.7 修改 `ainootbook-uniapp/src/pages.json`，删除 demo 相关页面配置（如果存在）

## 6. 可选模块清理

- [ ] 6.1 删除 `ainootbook-uniapp/src/pages-work/` 目录（低代码在线表单）
- [ ] 6.2 删除 `ainootbook-uniapp/src/pages-sub/` 目录（表格/卡片数据展示）
- [ ] 6.3 删除 `ainootbook-uniapp/src/pages-message/` 目录（聊天、租户切换）
- [ ] 6.4 修改 `ainootbook-uniapp/src/pages.json`，删除可选模块的页面配置
- [ ] 6.5 全局搜索残留引用：`rg "pages-work|pages-sub|pages-message" ainootbook-uniapp/src`
- [ ] 6.6 清理残留引用（如果存在）

## 7. Flyway 数据库清理脚本

- [ ] 7.1 创建 Flyway 脚本：`ainootbook-boot/jeecg-module-system/jeecg-system-start/src/main/resources/flyway/sql/mysql/V3.9.2_26__cleanup_uniapp_demo.sql`
- [ ] 7.2 脚本内容：按明确 `component` 路径软删除 demo 菜单（`UPDATE sys_permission SET del_flag=1 WHERE component IN (...)`）
- [ ] 7.3 脚本内容：删除 demo 相关权限（`DELETE FROM sys_permission_data_rule WHERE permission_id IN (...)`）
- [ ] 7.4 脚本内容：删除 demo 相关 MCP 配置（`DELETE FROM airag_mcp WHERE name LIKE '%demo%'`，先 SELECT 审计）
- [ ] 7.5 脚本内容：删除 demo 相关 Flow 配置（`DELETE FROM airag_flow WHERE name LIKE '%demo%'`，先 SELECT 审计）
- [ ] 7.6 验证脚本幂等性：执行两次，第二次应该 0 rows affected

## 8. 类型检查修复

- [ ] 8.1 修改 `ainootbook-uniapp/package.json`，升级 `vue-tsc` 到 `^2.0.0`
- [ ] 8.2 修改 `ainootbook-uniapp/package.json`，补充 `@vue/test-utils` 依赖 `^2.4.0`
- [ ] 8.3 运行 `pnpm install` 安装新依赖
- [ ] 8.4 运行 `pnpm type-check`，记录当前错误数
- [ ] 8.5 逐一修复类型错误（根据 `pnpm type-check` 输出）
- [ ] 8.6 验证类型检查通过：`pnpm type-check` 零错误

## 9. 单元测试修复

- [ ] 9.1 修改 `ainootbook-uniapp/src/components/__tests__/AiProgressTracker.spec.ts`，补充 `uni` 全局对象 mock
- [ ] 9.2 Mock `uni.request` 方法
- [ ] 9.3 Mock `uni.showToast` 方法
- [ ] 9.4 补充测试用例：轮询间隔递增
- [ ] 9.5 补充测试用例：轮询超时保护
- [ ] 9.6 补充测试用例：轮询失败重试
- [ ] 9.7 补充测试用例：生命周期管理（onShow/onHide/onUnload）
- [ ] 9.8 运行 `pnpm test`，确保所有测试通过

## 10. 集成测试验证

- [ ] 10.1 启动后端服务：`cd ainootbook-boot && mvn spring-boot:run`
- [ ] 10.2 启动 UniApp H5：`cd ainootbook-uniapp && pnpm dev:h5`
- [ ] 10.3 验证 TabBar "学习"Tab 可点击，导航到课程列表
- [ ] 10.4 验证课程列表页面正常显示
- [ ] 10.5 验证课程详情页面正常显示
- [ ] 10.6 验证笔记列表页面正常显示
- [ ] 10.7 验证笔记详情页面正常显示
- [ ] 10.8 验证笔记创建向导：上传素材 → AI 进度轮询 → 生成成功
- [ ] 10.9 验证笔记编辑保存：修改内容 → 保存 → 成功（baseVersion 乐观锁）
- [ ] 10.10 验证笔记版本历史查看
- [ ] 10.11 验证 AI 进度取消功能

## 11. 多端测试

- [ ] 11.1 H5 平台测试：`pnpm dev:h5`，验证所有功能正常
- [ ] 11.2 微信小程序测试：`pnpm dev:mp-weixin`，导入微信开发者工具，验证所有功能正常
- [ ] 11.3 APP 平台测试（可选）：`pnpm dev:app`，验证所有功能正常

## 12. 性能测试

- [ ] 12.1 构建生产版本：`pnpm build:h5`
- [ ] 12.2 记录清理前 bundle 体积
- [ ] 12.3 记录清理后 bundle 体积
- [ ] 12.4 对比 bundle 体积优化百分比
- [ ] 12.5 验证首屏加载时间是否改善

## 13. 文档更新

- [ ] 13.1 更新 `ainootbook-uniapp/CLAUDE.md`，补充路由名生成规则说明
- [ ] 13.2 更新 `ainootbook-uniapp/CLAUDE.md`，补充 AI 进度轮询架构说明
- [ ] 13.3 更新根目录 `CLAUDE.md` 的 Changelog，记录本次变更
- [ ] 13.4 更新用户手册（如果存在），补充学生端入口使用说明
