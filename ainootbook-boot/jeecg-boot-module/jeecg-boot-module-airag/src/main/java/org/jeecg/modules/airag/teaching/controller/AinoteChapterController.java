package org.jeecg.modules.airag.teaching.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.teaching.entity.AinoteChapter;
import org.jeecg.modules.airag.teaching.service.IAinoteChapterService;
import org.jeecg.modules.airag.teaching.vo.AinoteChapterTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 章节管理 Controller
 */
@Tag(name = "章节管理")
@RestController
@RequestMapping("/teaching/chapter")
@Slf4j
public class AinoteChapterController extends JeecgController<AinoteChapter, IAinoteChapterService> {

    @Autowired
    private IAinoteChapterService ainoteChapterService;

    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    @RequiresPermissions("teaching:chapter:list")
    public Result<IPage<AinoteChapter>> queryPageList(
            AinoteChapter ainoteChapter,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<AinoteChapter> queryWrapper = QueryGenerator.initQueryWrapper(ainoteChapter, req.getParameterMap());
        // 租户隔离
        Integer tenantId = getTenantId(req);
        if (tenantId != null) {
            queryWrapper.eq("tenant_id", tenantId);
        }
        Page<AinoteChapter> page = new Page<>(pageNo, pageSize);
        IPage<AinoteChapter> pageList = ainoteChapterService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "查询课程章节树")
    @GetMapping(value = "/tree")
    @RequiresPermissions("teaching:chapter:list")
    public Result<List<AinoteChapterTreeVO>> queryTree(
            @RequestParam(name = "courseId") String courseId,
            HttpServletRequest request) {
        Integer tenantId = getTenantId(request);
        List<AinoteChapterTreeVO> tree = ainoteChapterService.queryTreeByCourse(courseId, tenantId);
        return Result.OK(tree);
    }

    @Operation(summary = "添加章节")
    @PostMapping(value = "/add")
    @RequiresPermissions("teaching:chapter:add")
    public Result<String> add(@RequestBody AinoteChapter ainoteChapter, HttpServletRequest request) {
        // 后端强制覆盖租户ID
        Integer tenantId = getTenantId(request);
        ainoteChapter.setTenantId(tenantId);
        ainoteChapterService.addChapter(ainoteChapter);
        return Result.OK("添加成功！");
    }

    @Operation(summary = "编辑章节")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("teaching:chapter:edit")
    public Result<String> edit(@RequestBody AinoteChapter ainoteChapter, HttpServletRequest request) {
        // 校验租户归属
        Integer tenantId = getTenantId(request);
        AinoteChapter existing = ainoteChapterService.getById(ainoteChapter.getId());
        if (existing == null || (tenantId != null && !tenantId.equals(existing.getTenantId()))) {
            return Result.error("数据不存在或无权限编辑");
        }
        ainoteChapterService.editChapter(ainoteChapter);
        return Result.OK("编辑成功！");
    }

    @Operation(summary = "通过id删除")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("teaching:chapter:delete")
    public Result<String> delete(@RequestParam(name = "id") String id, HttpServletRequest request) {
        // 校验租户归属
        Integer tenantId = getTenantId(request);
        AinoteChapter existing = ainoteChapterService.getById(id);
        if (existing == null || (tenantId != null && !tenantId.equals(existing.getTenantId()))) {
            return Result.error("数据不存在或无权限删除");
        }
        ainoteChapterService.deleteWithCheck(id);
        return Result.OK("删除成功！");
    }

    @Operation(summary = "批量删除")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("teaching:chapter:delete")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids, HttpServletRequest request) {
        Integer tenantId = getTenantId(request);
        List<String> idList = Arrays.stream(ids.split(",")).collect(Collectors.toList());
        // 批量校验租户归属
        if (tenantId != null) {
            long ownedCount = ainoteChapterService.count(
                    new QueryWrapper<AinoteChapter>().in("id", idList).eq("tenant_id", tenantId));
            if (ownedCount != idList.size()) {
                return Result.error("部分数据无权限删除");
            }
        }
        ainoteChapterService.batchDeleteWithCheck(idList);
        return Result.OK("批量删除成功！");
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    @RequiresPermissions("teaching:chapter:list")
    public Result<AinoteChapter> queryById(@RequestParam(name = "id") String id, HttpServletRequest request) {
        Integer tenantId = getTenantId(request);
        QueryWrapper<AinoteChapter> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        AinoteChapter ainoteChapter = ainoteChapterService.getOne(wrapper);
        if (ainoteChapter == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(ainoteChapter);
    }

    @Operation(summary = "导出excel")
    @RequestMapping(value = "/exportXls")
    @RequiresPermissions("teaching:chapter:export")
    public org.springframework.web.servlet.ModelAndView exportXls(HttpServletRequest request, AinoteChapter ainoteChapter) {
        return super.exportXls(request, ainoteChapter, AinoteChapter.class, "章节表");
    }

    @Operation(summary = "通过excel导入数据")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    @RequiresPermissions("teaching:chapter:import")
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, AinoteChapter.class);
    }

    private Integer getTenantId(HttpServletRequest request) {
        String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isNotEmpty(tenantIdStr)) {
            return Integer.parseInt(tenantIdStr);
        }
        return null;
    }
}
