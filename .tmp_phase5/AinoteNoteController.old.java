package org.jeecg.modules.ainote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.modules.ainote.facade.AinoteGenerationFacade;
import org.jeecg.modules.ainote.service.impl.AinoteEmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 笔记表 Controller
 */
@Tag(name = "笔记管理")
@RestController
@RequestMapping("/ainote/note")
@Slf4j
public class AinoteNoteController extends JeecgController<AinoteNote, IAinoteNoteService> {

    @Autowired
    private IAinoteNoteService ainoteNoteService;

    @Autowired
    private JeecgBaseConfig jeecgBaseConfig;

    @Autowired
    private AinoteEmbeddingService ainoteEmbeddingService;

    @Autowired
    private AinoteGenerationFacade generationFacade;

    /**
     * 分页列表查询
     * TODO: 优化建议 - 列表接口返回大字段（noteContent LONGTEXT）可能影响性能
     * 建议后续创建 AinoteNoteListVO，仅返回必要字段（标题、摘要、状态、时间、计数等）
     */
    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<AinoteNote>> queryPageList(
            AinoteNote ainoteNote,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<AinoteNote> queryWrapper =
                QueryGenerator.initQueryWrapper(ainoteNote, req.getParameterMap());
        // 应用数据权限过滤（租户隔离 + owner/public + 教师规则）
        ainoteNoteService.applyDataPermission(queryWrapper);
        // 列表查询排除大字段以提升性能
        queryWrapper.select(AinoteNote.class, info ->
                !info.getColumn().equals("note_content"));
        Page<AinoteNote> page = new Page<>(pageNo, pageSize);
        IPage<AinoteNote> pageList = ainoteNoteService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    public Result<AinoteNote> queryById(@RequestParam(name = "id") String id) {
        AinoteNote note = ainoteNoteService.getByIdWithPermission(id);
        if (note == null) {
            return Result.error("未找到对应数据或无权限访问");
        }
        return Result.OK(note);
    }

    @Operation(summary = "添加笔记")
    @PostMapping(value = "/add")
    @RequiresPermissions("ainote:note:add")
    public Result<String> add(@RequestBody @Validated AinoteNoteCreateDTO dto) {
        String id = ainoteNoteService.createNote(dto);
        return Result.OK("添加成功！", id);
    }

    @Operation(summary = "编辑笔记")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    @RequiresPermissions("ainote:note:edit")
    public Result<String> edit(@RequestBody @Validated AinoteNoteUpdateDTO dto) {
        ainoteNoteService.updateNote(dto);
        return Result.OK("编辑成功！");
    }

    @Operation(summary = "通过id删除（逻辑删除）")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("ainote:note:delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        boolean success = ainoteNoteService.deleteLogicalById(id);
        if (!success) {
            return Result.error("删除失败，无权限或数据不存在！");
        }
        return Result.OK("删除成功！");
    }

    @Operation(summary = "批量删除（逻辑删除）")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("ainote:note:delete")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        // 修复：trim + 过滤空串 + 去重
        List<String> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (idList.isEmpty()) {
            return Result.error("请选择要删除的数据！");
        }
        boolean success = ainoteNoteService.deleteLogicalByIds(idList);
        if (!success) {
            return Result.error("删除失败，无权限或数据不存在！");
        }
        return Result.OK("批量删除成功！");
    }

    @Operation(summary = "创建分享（16位分享码）")
    @PostMapping(value = "/share")
    @RequiresPermissions("ainote:note:share")
    public Result<AinoteNoteShareVO> createShare(
            @RequestBody @Validated AinoteNoteShareCreateDTO dto) {
        AinoteNoteShareVO vo = ainoteNoteService.createShare(dto);
        return Result.OK(vo);
    }

    @Operation(summary = "通过分享码查询分享内容")
    @GetMapping(value = "/share/{shareCode}")
    public Result<AinoteNoteShareDetailVO> queryByShareCode(
            @PathVariable(name = "shareCode") String shareCode) {
        AinoteNoteShareDetailVO vo = ainoteNoteService.queryByShareCode(shareCode);
        if (vo == null) {
            return Result.error("分享链接无效或已过期");
        }
        return Result.OK(vo);
    }

    @Operation(summary = "公开笔记广场（仅展示公开笔记）")
    @GetMapping(value = "/public")
    public Result<IPage<AinoteNote>> queryPublicNotes(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "keyword", required = false) String keyword,
            HttpServletRequest req) {
        IPage<AinoteNote> pageList = ainoteNoteService.queryPublicNotes(pageNo, pageSize, keyword);
        return Result.OK(pageList);
    }

    @Operation(summary = "导出excel（带数据权限）")
    @RequestMapping(value = "/exportXls")
    @RequiresPermissions("ainote:note:export")
    public ModelAndView exportXls(HttpServletRequest request, AinoteNote ainoteNote) {
        // 组装查询条件
        QueryWrapper<AinoteNote> queryWrapper =
                QueryGenerator.initQueryWrapper(ainoteNote, request.getParameterMap());
        ainoteNoteService.applyDataPermission(queryWrapper);

        // 过滤选中数据
        String selections = request.getParameter("selections");
        if (oConvertUtils.isNotEmpty(selections)) {
            List<String> selectionList = Arrays.asList(selections.split(","));
            queryWrapper.in("id", selectionList);
        }

        // 获取导出数据
        List<AinoteNote> exportList = ainoteNoteService.list(queryWrapper);
        LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        String realname = (sysUser != null) ? sysUser.getRealname() : "";

        // AutoPoi 导出Excel
        ModelAndView mv = new ModelAndView(new JeecgEntityExcelView());
        mv.addObject(NormalExcelConstants.FILE_NAME, "笔记表");
        mv.addObject(NormalExcelConstants.CLASS, AinoteNote.class);
        ExportParams exportParams = new ExportParams(
                "笔记表报表", "导出人:" + realname, "笔记表", ExcelType.XSSF);
        exportParams.setImageBasePath(jeecgBaseConfig.getPath().getUpload());
        mv.addObject(NormalExcelConstants.PARAMS, exportParams);
        mv.addObject(NormalExcelConstants.DATA_LIST, exportList);

        // 指定导出列（可选）
        String exportFields = request.getParameter(NormalExcelConstants.EXPORT_FIELDS);
        if (oConvertUtils.isNotEmpty(exportFields)) {
            mv.addObject(NormalExcelConstants.EXPORT_FIELDS, exportFields);
        }
        return mv;
    }

    @Operation(summary = "通过excel导入数据（仅管理员，强制覆盖敏感字段）")
    @RequestMapping(value = "/importExcel", method = RequestMethod.POST)
    @RequiresPermissions("ainote:note:import")
    public Result<?> importExcel(HttpServletRequest request, HttpServletResponse response) {
        // 安全加固：导入功能仅允许管理员使用，并强制覆盖敏感字段
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            return Result.error("用户未登录");
        }

        // 检查是否为管理员
        String roleCode = user.getRoleCode();
        boolean isAdmin = roleCode != null && Arrays.asList(roleCode.split(",")).contains("admin");
        if (!isAdmin) {
            return Result.error("导入功能仅限管理员使用");
        }

        // 调用安全导入方法
        return ainoteNoteService.importExcelWithSecurity(request, response, user);
    }

    @Operation(summary = "语义检索笔记（向量相似度搜索）")
    @GetMapping(value = "/search")
    @RequiresPermissions("ainote:note:list")
    public Result<List<AinoteNote>> semanticSearch(
            @RequestParam(name = "knowledgeId") String knowledgeId,
            @RequestParam(name = "q") String queryText,
            @RequestParam(name = "topN", defaultValue = "10") Integer topN) {
        List<String> noteIds = ainoteEmbeddingService.searchNotes(knowledgeId, queryText, topN);
        if (noteIds.isEmpty()) {
            return Result.OK(List.of());
        }
        // 回表查询，应用数据权限（权限过滤后结果数可能少于 topN）
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.in("id", noteIds);
        ainoteNoteService.applyDataPermission(wrapper);
        wrapper.select(AinoteNote.class, info ->
                !info.getColumn().equals("note_content"));
        List<AinoteNote> notes = ainoteNoteService.list(wrapper);
        // 按向量相似度原顺序重排（noteIds 已按 score 降序）
        Map<String, AinoteNote> noteMap = new HashMap<>();
        notes.forEach(n -> noteMap.put(n.getId(), n));
        List<AinoteNote> sorted = noteIds.stream()
                .filter(noteMap::containsKey)
                .map(noteMap::get)
                .collect(Collectors.toList());
        return Result.OK(sorted);
    }

    @Operation(summary = "触发AI生成（为笔记素材创建AI任务）")
    @PostMapping(value = "/triggerGeneration")
    @RequiresPermissions("ainote:note:edit")
    public Result<String> triggerGeneration(
            @RequestParam(name = "noteId") String noteId,
            @RequestParam(name = "knowledgeId", required = false) String knowledgeId) {
        generationFacade.triggerGeneration(noteId, knowledgeId);
        return Result.OK("生成任务已触发");
    }

    @Operation(summary = "取消AI生成（取消待处理任务）")
    @PostMapping(value = "/cancelGeneration")
    @RequiresPermissions("ainote:note:edit")
    public Result<String> cancelGeneration(@RequestParam(name = "noteId") String noteId) {
        generationFacade.cancelGeneration(noteId);
        return Result.OK("已取消待处理任务");
    }

    @Operation(summary = "查询AI生成进度（百分比）")
    @GetMapping(value = "/progress")
    public Result<Integer> getProgress(
            @RequestParam(name = "noteId") String noteId,
            @RequestParam(name = "knowledgeId", required = false) String knowledgeId) {
        int progress = generationFacade.getProgress(noteId, knowledgeId);
        return Result.OK(progress);
    }
}
