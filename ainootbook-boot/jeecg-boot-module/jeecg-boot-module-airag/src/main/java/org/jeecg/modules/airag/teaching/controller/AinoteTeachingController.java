package org.jeecg.modules.airag.teaching.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.service.IAinoteTeachingService;
import org.jeecg.modules.airag.teaching.vo.AinoteTeachingVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 教学关系表 Controller
 */
@Tag(name = "教学任务管理")
@RestController
@RequestMapping("/teaching/assignment")
@Slf4j
public class AinoteTeachingController extends JeecgController<AinoteTeaching, IAinoteTeachingService> {

    @Autowired
    private IAinoteTeachingService ainoteTeachingService;

    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<AinoteTeachingVO>> queryPageList(
            AinoteTeaching ainoteTeaching,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        // 提取 departId 后从参数 map 中移除，防止 QueryGenerator 生成精确等值条件
        String departId = req.getParameter("departId");
        Map<String, String[]> paramMap = new HashMap<>(req.getParameterMap());
        paramMap.remove("departId");
        // 实体上也清空，双重保险
        ainoteTeaching.setDepartId(null);
        QueryWrapper<AinoteTeaching> queryWrapper = QueryGenerator.initQueryWrapper(ainoteTeaching, paramMap);
        // 若传了 departId，改为 IN 查询（包含自身及所有子孙节点）
        if (oConvertUtils.isNotEmpty(departId)) {
            queryWrapper.in("depart_id", ainoteTeachingService.getDepartAndSubIds(departId));
        }
        // 应用数据权限过滤
        ainoteTeachingService.applyDataPermission(queryWrapper);
        Page<AinoteTeachingVO> page = new Page<>(pageNo, pageSize);
        IPage<AinoteTeachingVO> pageList = ainoteTeachingService.queryTeachingVoPage(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    public Result<AinoteTeaching> queryById(@RequestParam(name = "id") String id) {
        QueryWrapper<AinoteTeaching> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        ainoteTeachingService.applyDataPermission(queryWrapper);
        AinoteTeaching ainoteTeaching = ainoteTeachingService.getOne(queryWrapper);
        if (ainoteTeaching == null) {
            return Result.error("未找到对应数据或无权限访问");
        }
        return Result.OK(ainoteTeaching);
    }

    @Operation(summary = "添加教学任务")
    @PostMapping(value = "/add")
    @RequiresPermissions("teaching:assignment:add")
    public Result<String> add(@RequestBody AinoteTeaching ainoteTeaching, HttpServletRequest request) {
        ainoteTeachingService.validateDepartId(ainoteTeaching.getDepartId());
        ainoteTeachingService.validateSemester(ainoteTeaching.getSemester());
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        ainoteTeaching.setTeacherId(user.getId());
        ainoteTeaching.setTenantId(getTenantId(request));
        ainoteTeachingService.save(ainoteTeaching);
        return Result.OK("添加成功！");
    }

    @Operation(summary = "编辑教学任务")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("teaching:assignment:edit")
    public Result<String> edit(@RequestBody AinoteTeaching ainoteTeaching, HttpServletRequest request) {
        QueryWrapper<AinoteTeaching> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", ainoteTeaching.getId());
        ainoteTeachingService.applyDataPermission(queryWrapper);
        AinoteTeaching existing = ainoteTeachingService.getOne(queryWrapper);
        if (existing == null) {
            return Result.error("数据不存在或无权限编辑");
        }
        if (ainoteTeaching.getDepartId() != null) {
            ainoteTeachingService.validateDepartId(ainoteTeaching.getDepartId());
        }
        if (ainoteTeaching.getSemester() != null) {
            ainoteTeachingService.validateSemester(ainoteTeaching.getSemester());
        }
        ainoteTeaching.setTenantId(null);
        ainoteTeachingService.updateById(ainoteTeaching);
        return Result.OK("编辑成功！");
    }

    @Operation(summary = "通过id删除")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("teaching:assignment:delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        boolean success = ainoteTeachingService.batchDeleteWithPermission(Arrays.asList(id));
        if (!success) {
            return Result.error("删除失败，无权限或数据不存在！");
        }
        return Result.OK("删除成功！");
    }

    @Operation(summary = "批量删除")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("teaching:assignment:delete")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        boolean success = ainoteTeachingService.batchDeleteWithPermission(Arrays.asList(ids.split(",")));
        if (!success) {
            return Result.error("删除失败，无权限或数据不存在！");
        }
        return Result.OK("批量删除成功！");
    }

    @Operation(summary = "批量配置教学任务")
    @PostMapping(value = "/batchUpsert")
    @RequiresPermissions("teaching:assignment:add")
    public Result<BatchResult> batchUpsert(@RequestBody @Validated BatchUpsertDTO dto) {
        BatchResult result = ainoteTeachingService.batchUpsert(dto);
        return Result.OK(result);
    }

    private Integer getTenantId(HttpServletRequest request) {
        String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isNotEmpty(tenantIdStr)) {
            return Integer.parseInt(tenantIdStr);
        }
        return null;
    }
}
