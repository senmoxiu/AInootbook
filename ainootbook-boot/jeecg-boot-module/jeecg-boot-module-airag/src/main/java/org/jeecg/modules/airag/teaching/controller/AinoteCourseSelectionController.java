package org.jeecg.modules.airag.teaching.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
import org.jeecg.modules.airag.teaching.dto.AinoteCourseSelectionAddDTO;
import org.jeecg.modules.airag.teaching.dto.AinoteCourseSelectionEditDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteCourseSelection;
import org.jeecg.modules.airag.teaching.vo.AvailableTeachingVO;
import org.jeecg.modules.airag.teaching.service.IAinoteCourseSelectionService;
import org.jeecg.modules.airag.teaching.vo.AinoteCourseSelectionVO;
import org.jeecg.modules.airag.teaching.vo.SelectionGroupVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 选课管理 Controller
 */
@Tag(name = "选课管理")
@RestController
@RequestMapping("/teaching/selection")
@Slf4j
public class AinoteCourseSelectionController
        extends JeecgController<AinoteCourseSelection, IAinoteCourseSelectionService> {

    @Autowired
    private IAinoteCourseSelectionService selectionService;

    @Operation(summary = "清空某教学任务下的所有选课（软删除）")
    @DeleteMapping("/clearByTeaching")
    @RequiresPermissions("teaching:selection:delete")
    public Result<String> clearByTeaching(@RequestParam String teachingId) {
        selectionService.clearByTeachingId(teachingId);
        return Result.OK("已清空该课程所有选课记录");
    }

    @Operation(summary = "按课程聚合查询选课人数（管理员视图）")
    @GetMapping("/listGrouped")
    @RequiresPermissions("teaching:selection:list")
    public Result<IPage<SelectionGroupVO>> listGrouped(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String semester) {
        Page<SelectionGroupVO> page = new Page<>(pageNo, pageSize);
        return Result.OK(selectionService.queryGroupedByTeaching(page, courseName, semester));
    }

    @Operation(summary = "查询某教学任务下的选课学生明细")
    @GetMapping("/studentsByTeaching")
    @RequiresPermissions("teaching:selection:list")
    public Result<List<AinoteCourseSelectionVO>> studentsByTeaching(
            @RequestParam String teachingId) {
        return Result.OK(selectionService.queryStudentsByTeachingId(teachingId));
    }

    @Operation(summary = "查询我的已选课程（学生：已选课程；admin：全部课程）")
    @GetMapping("/myCourses")
    public Result<IPage<org.jeecg.modules.airag.teaching.entity.AinoteCourse>> myCourses(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "100") Integer pageSize) {
        Page<org.jeecg.modules.airag.teaching.entity.AinoteCourse> page = new Page<>(pageNo, pageSize);
        return Result.OK(selectionService.queryMySelectedCourses(page));
    }

    @Operation(summary = "分页列表查询（联表 VO）")
    @GetMapping(value = "/list")
    @RequiresPermissions("teaching:selection:list")
    public Result<IPage<AinoteCourseSelectionVO>> queryPageList(
            AinoteCourseSelection selection,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<AinoteCourseSelection> queryWrapper =
                QueryGenerator.initQueryWrapper(selection, req.getParameterMap());
        // 追加数据权限条件
        selectionService.applyDataPermission(queryWrapper);
        Page<AinoteCourseSelectionVO> page = new Page<>(pageNo, pageSize);
        IPage<AinoteCourseSelectionVO> pageList = selectionService.querySelectionVoPage(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "查询可选教学任务（学生选课用）")
    @GetMapping("/available")
    @RequiresPermissions("teaching:selection:list")
    public Result<IPage<AvailableTeachingVO>> available(
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String courseName) {
        Page<AvailableTeachingVO> page = new Page<>(pageNo, pageSize);
        IPage<AvailableTeachingVO> result = selectionService.queryAvailableTeachings(page, courseName);
        return Result.OK(result);
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    @RequiresPermissions("teaching:selection:list")
    public Result<AinoteCourseSelection> queryById(@RequestParam(name = "id") String id) {
        QueryWrapper<AinoteCourseSelection> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        selectionService.applyDataPermission(queryWrapper);
        AinoteCourseSelection selection = selectionService.getOne(queryWrapper);
        if (selection == null) {
            return Result.error("未找到对应数据或无权限访问");
        }
        return Result.OK(selection);
    }

    @Operation(summary = "选课（新增）")
    @PostMapping(value = "/add")
    @RequiresPermissions("teaching:selection:add")
    public Result<String> add(@RequestBody @Validated AinoteCourseSelectionAddDTO dto) {
        selectionService.addSelection(dto);
        return Result.OK("选课成功！");
    }

    @Operation(summary = "编辑选课记录")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("teaching:selection:edit")
    public Result<String> edit(@RequestBody @Validated AinoteCourseSelectionEditDTO dto) {
        // 校验数据权限
        QueryWrapper<AinoteCourseSelection> checkWrapper = new QueryWrapper<>();
        checkWrapper.eq("id", dto.getId());
        selectionService.applyDataPermission(checkWrapper);
        AinoteCourseSelection existing = selectionService.getOne(checkWrapper);
        if (existing == null) {
            return Result.error("数据不存在或无权限编辑");
        }
        // 仅允许更新 classId、departId、status（双轨字段同步）
        AinoteCourseSelection update = new AinoteCourseSelection();
        update.setId(dto.getId());
        // 双轨同步：classId 和 departId 保持一致
        String departId = dto.getDepartId() != null ? dto.getDepartId() : existing.getDepartId();
        String classId = dto.getClassId() != null ? dto.getClassId() : existing.getClassId();
        update.setClassId(classId != null ? classId : departId);
        update.setDepartId(departId != null ? departId : classId);
        update.setStatus(dto.getStatus());
        selectionService.updateById(update);
        return Result.OK("编辑成功！");
    }

    @Operation(summary = "退课（软删除）")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("teaching:selection:delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        selectionService.softDeleteById(id);
        return Result.OK("退课成功！");
    }

    @Operation(summary = "批量退课（批量软删除）")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("teaching:selection:delete")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        selectionService.softDeleteByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量退课成功！");
    }

    @Operation(summary = "学生退课（等同于软删除）")
    @PostMapping(value = "/dropCourse")
    @RequiresPermissions("teaching:selection:delete")
    public Result<String> dropCourse(@RequestParam(name = "id") String id) {
        selectionService.softDeleteById(id);
        return Result.OK("退课成功！");
    }

    /**
     * 获取当前租户ID
     */
    private Integer getTenantId(HttpServletRequest request) {
        String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isNotEmpty(tenantIdStr)) {
            return Integer.parseInt(tenantIdStr);
        }
        return null;
    }
}
