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
import org.jeecg.modules.airag.teaching.entity.AinoteCourse;
import org.jeecg.modules.airag.teaching.service.IAinoteCourseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;

/**
 * 课程表 Controller
 */
@Tag(name = "课程管理")
@RestController
@RequestMapping("/teaching/course")
@Slf4j
public class AinoteCourseController extends JeecgController<AinoteCourse, IAinoteCourseService> {

    @Autowired
    private IAinoteCourseService ainoteCourseService;

    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<AinoteCourse>> queryPageList(
            AinoteCourse ainoteCourse,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<AinoteCourse> queryWrapper = QueryGenerator.initQueryWrapper(ainoteCourse, req.getParameterMap());
        Page<AinoteCourse> page = new Page<>(pageNo, pageSize);
        IPage<AinoteCourse> pageList = ainoteCourseService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "添加课程")
    @PostMapping(value = "/add")
    @RequiresPermissions("teaching:course:add")
    public Result<String> add(@RequestBody AinoteCourse ainoteCourse) {
        ainoteCourseService.save(ainoteCourse);
        return Result.OK("添加成功！");
    }

    @Operation(summary = "编辑课程")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("teaching:course:edit")
    public Result<String> edit(@RequestBody AinoteCourse ainoteCourse) {
        ainoteCourseService.updateById(ainoteCourse);
        return Result.OK("编辑成功！");
    }

    @Operation(summary = "通过id删除")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("teaching:course:delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        boolean success = ainoteCourseService.removeWithProtection(id);
        if (!success) {
            return Result.error("删除失败，该课程已被教学任务引用！");
        }
        return Result.OK("删除成功！");
    }

    @Operation(summary = "批量删除")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("teaching:course:delete")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        boolean success = ainoteCourseService.removeBatchWithProtection(Arrays.asList(ids.split(",")));
        if (!success) {
            return Result.error("删除失败，部分课程已被教学任务引用！");
        }
        return Result.OK("批量删除成功！");
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    public Result<AinoteCourse> queryById(@RequestParam(name = "id") String id) {
        AinoteCourse ainoteCourse = ainoteCourseService.getById(id);
        if (ainoteCourse == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(ainoteCourse);
    }

    @Operation(summary = "导出excel")
    @RequestMapping(value = "/exportXls")
    @RequiresPermissions("teaching:course:export")
    public ModelAndView exportXls(HttpServletRequest request, AinoteCourse ainoteCourse) {
        return super.exportXls(request, ainoteCourse, AinoteCourse.class, "课程表");
    }

    @Operation(summary = "通过excel导入数据")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    @RequiresPermissions("teaching:course:import")
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        return super.importExcel(request, response, AinoteCourse.class);
    }
}