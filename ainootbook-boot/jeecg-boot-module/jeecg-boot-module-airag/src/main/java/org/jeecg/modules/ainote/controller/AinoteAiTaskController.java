package org.jeecg.modules.ainote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.session.Session;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.util.RoleUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI任务表 Controller
 */
@Tag(name = "AI任务管理")
@RestController
@RequestMapping("/ainote/task")
@Slf4j
public class AinoteAiTaskController extends JeecgController<AinoteAiTask, IAinoteAiTaskService> {

    @Autowired
    private IAinoteAiTaskService ainoteAiTaskService;

    @Autowired
    private IAinoteNoteService ainoteNoteService;

    @Autowired
    private IAinoteMaterialService ainoteMaterialService;

    /**
     * 分页列表查询（带数据权限）
     */
    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    @RequiresPermissions("ainote:task:*")
    public Result<IPage<AinoteAiTask>> queryPageList(
            AinoteAiTask ainoteAiTask,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        try {
            QueryWrapper<AinoteAiTask> queryWrapper =
                    QueryGenerator.initQueryWrapper(ainoteAiTask, req.getParameterMap());

            LoginUser user = getCurrentUser();
            Integer tenantId = getRequiredTenantId();
            applyTaskDataPermission(queryWrapper, user, tenantId);

            Page<AinoteAiTask> page = new Page<>(pageNo, pageSize);
            IPage<AinoteAiTask> pageList = ainoteAiTaskService.page(page, queryWrapper);
            return Result.OK(pageList);
        } catch (JeecgBootException e) {
            log.warn("分页查询AI任务失败: msg={}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("分页查询AI任务失败", e);
            return Result.error("查询失败！");
        }
    }

    /**
     * 通过ID查询
     */
    @Operation(summary = "通过ID查询")
    @GetMapping(value = "/queryById")
    @RequiresPermissions("ainote:task:*")
    public Result<AinoteAiTask> queryById(@RequestParam(name = "id") String id) {
        try {
            if (oConvertUtils.isEmpty(id)) {
                return Result.error("参数id不能为空");
            }
            LoginUser user = getCurrentUser();
            Integer tenantId = getRequiredTenantId();

            QueryWrapper<AinoteAiTask> wrapper = new QueryWrapper<>();
            wrapper.eq("id", id);
            applyTaskDataPermission(wrapper, user, tenantId);

            AinoteAiTask task = ainoteAiTaskService.getOne(wrapper);
            if (task == null) {
                return Result.error("未找到对应数据或无权限访问");
            }
            return Result.OK(task);
        } catch (JeecgBootException e) {
            log.warn("查询AI任务失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询AI任务失败: id={}", id, e);
            return Result.error("查询失败！");
        }
    }

    /**
     * 查询笔记的所有任务
     */
    @Operation(summary = "查询笔记的所有任务")
    @GetMapping(value = "/queryByNoteId")
    @RequiresPermissions("ainote:task:*")
    public Result<List<AinoteAiTask>> queryByNoteId(@RequestParam(name = "noteId") String noteId) {
        try {
            if (oConvertUtils.isEmpty(noteId)) {
                return Result.error("参数noteId不能为空");
            }

            AinoteNote note = ainoteNoteService.getByIdWithPermission(noteId);
            if (note == null) {
                return Result.error("未找到对应笔记或无权限访问");
            }

            List<AinoteAiTask> list = ainoteAiTaskService.getTasksByNoteId(noteId);
            return Result.OK(list);
        } catch (JeecgBootException e) {
            log.warn("查询笔记任务失败: noteId={}, msg={}", noteId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询笔记任务失败: noteId={}", noteId, e);
            return Result.error("查询失败！");
        }
    }

    /**
     * 查询素材的所有任务
     */
    @Operation(summary = "查询素材的所有任务")
    @GetMapping(value = "/queryByMaterialId")
    @RequiresPermissions("ainote:task:*")
    public Result<List<AinoteAiTask>> queryByMaterialId(@RequestParam(name = "materialId") String materialId) {
        try {
            if (oConvertUtils.isEmpty(materialId)) {
                return Result.error("参数materialId不能为空");
            }
            Integer tenantId = getRequiredTenantId();

            QueryWrapper<AinoteMaterial> wrapper = new QueryWrapper<>();
            wrapper.eq("id", materialId);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("del_flag", 0);
            AinoteMaterial material = ainoteMaterialService.getOne(wrapper);
            if (material == null) {
                return Result.error("未找到对应素材或无权限访问");
            }
            if (oConvertUtils.isEmpty(material.getNoteId())) {
                return Result.error("素材未关联笔记，无法查询任务");
            }

            AinoteNote note = ainoteNoteService.getByIdWithPermission(material.getNoteId());
            if (note == null) {
                return Result.error("未找到对应笔记或无权限访问");
            }

            List<AinoteAiTask> list = ainoteAiTaskService.getTasksByMaterialId(materialId);
            return Result.OK(list);
        } catch (JeecgBootException e) {
            log.warn("查询素材任务失败: materialId={}, msg={}", materialId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询素材任务失败: materialId={}", materialId, e);
            return Result.error("查询失败！");
        }
    }

    /**
     * 查询笔记的任务进度
     */
    @Operation(summary = "查询笔记的任务进度")
    @GetMapping(value = "/progress")
    @RequiresPermissions("ainote:task:*")
    public Result<Map<String, Long>> progress(@RequestParam(name = "noteId") String noteId) {
        try {
            if (oConvertUtils.isEmpty(noteId)) {
                return Result.error("参数noteId不能为空");
            }

            AinoteNote note = ainoteNoteService.getByIdWithPermission(noteId);
            if (note == null) {
                return Result.error("未找到对应笔记或无权限访问");
            }

            long completed = ainoteAiTaskService.countCompletedTasks(noteId);
            long total = ainoteAiTaskService.countTotalTasks(noteId);
            return Result.OK(Map.of("completed", completed, "total", total));
        } catch (JeecgBootException e) {
            log.warn("查询任务进度失败: noteId={}, msg={}", noteId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询任务进度失败: noteId={}", noteId, e);
            return Result.error("查询失败！");
        }
    }

    /**
     * 删除任务
     */
    @Operation(summary = "删除任务")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("ainote:task:*")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        try {
            if (oConvertUtils.isEmpty(id)) {
                return Result.error("请选择要删除的数据！");
            }
            LoginUser user = getCurrentUser();
            Integer tenantId = getRequiredTenantId();

            QueryWrapper<AinoteAiTask> wrapper = new QueryWrapper<>();
            wrapper.eq("id", id);
            applyTaskDataPermission(wrapper, user, tenantId);
            boolean success = ainoteAiTaskService.remove(wrapper);
            if (!success) {
                return Result.error("删除失败，数据不存在或无权限！");
            }
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            log.warn("删除AI任务失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除AI任务失败: id={}", id, e);
            return Result.error("删除失败！");
        }
    }

    /**
     * 批量删除任务
     */
    @Operation(summary = "批量删除任务")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("ainote:task:*")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        try {
            if (oConvertUtils.isEmpty(ids)) {
                return Result.error("请选择要删除的数据！");
            }
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            if (idList.isEmpty()) {
                return Result.error("请选择要删除的数据！");
            }
            LoginUser user = getCurrentUser();
            Integer tenantId = getRequiredTenantId();

            QueryWrapper<AinoteAiTask> wrapper = new QueryWrapper<>();
            wrapper.in("id", idList);
            applyTaskDataPermission(wrapper, user, tenantId);
            boolean success = ainoteAiTaskService.remove(wrapper);
            if (!success) {
                return Result.error("批量删除失败，数据不存在或无权限！");
            }
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            log.warn("批量删除AI任务失败: ids={}, msg={}", ids, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除AI任务失败: ids={}", ids, e);
            return Result.error("批量删除失败！");
        }
    }

    private void applyTaskDataPermission(QueryWrapper<AinoteAiTask> wrapper, LoginUser user, Integer tenantId) {
        if (wrapper == null) {
            return;
        }
        wrapper.eq("tenant_id", tenantId);

        if (isAdmin(user)) {
            return;
        }

        String userId = escapeSqlValue(user.getId());
        wrapper.inSql("note_id", buildNotePermissionSubquery(userId, tenantId));
    }

    private String buildNotePermissionSubquery(String userIdEscaped, Integer tenantId) {
        return "SELECT id FROM ainote_note WHERE tenant_id = " + tenantId
                + " AND note_status <> 3 AND ("
                + "student_id = '" + userIdEscaped + "'"
                + " OR create_by = '" + userIdEscaped + "'"
                + " OR is_public = 1"
                + " OR teaching_id IN (SELECT id FROM ainote_teaching WHERE teacher_id = '" + userIdEscaped
                + "' AND tenant_id = " + tenantId + ")"
                + ")";
    }

    private LoginUser getCurrentUser() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        return user;
    }

    private boolean isAdmin(LoginUser user) {
        return RoleUtils.hasRole(user.getRoleCode(), "admin");
    }

    private Integer getRequiredTenantId() {
        Integer tenantId = getTenantIdFromSecurityUtils();
        if (tenantId != null) {
            return tenantId;
        }
        String tenantIdStr = TenantContext.getTenant();
        if (oConvertUtils.isEmpty(tenantIdStr) || "undefined".equalsIgnoreCase(tenantIdStr)) {
            return 0;
        }
        try {
            return Integer.parseInt(tenantIdStr);
        } catch (NumberFormatException e) {
            log.warn("租户ID格式错误: {}", tenantIdStr);
            return 0;
        }
    }

    private Integer getTenantIdFromSecurityUtils() {
        try {
            Session session = SecurityUtils.getSubject() == null ? null : SecurityUtils.getSubject().getSession(false);
            if (session == null) {
                return null;
            }
            Object value = session.getAttribute("tenantId");
            if (value == null) {
                return null;
            }
            String tenantIdStr = String.valueOf(value);
            if (oConvertUtils.isEmpty(tenantIdStr) || "undefined".equalsIgnoreCase(tenantIdStr)) {
                return null;
            }
            return Integer.parseInt(tenantIdStr);
        } catch (Exception e) {
            log.debug("从SecurityUtils获取租户ID失败", e);
            return null;
        }
    }

    private String escapeSqlValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "''");
    }
}
