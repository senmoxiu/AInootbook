package org.jeecg.modules.ainote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.ShiroThreadPoolExecutor;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteRegenerateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.enums.NoteSearchScope;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.util.RoleUtils;
import org.jeecg.modules.ainote.vo.AinoteNoteRegenerateVO;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;
import org.jeecg.modules.ainote.vo.AinoteProgressVO;
import org.jeecg.modules.ainote.vo.AinoteTeacherNoteVO;
import org.jeecg.modules.ainote.vo.SemanticSearchResultDTO;
import org.jeecg.modules.ainote.vo.AinoteNoteDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteListVO;
import org.jeecgframework.poi.excel.def.NormalExcelConstants;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;
import org.jeecgframework.poi.excel.view.JeecgEntityExcelView;
import org.jeecg.modules.ainote.facade.AinoteGenerationFacade;
import org.jeecg.modules.ainote.service.impl.AinoteEmbeddingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 笔记表 Controller
 */
@Tag(name = "笔记管理")
@RestController
@RequestMapping("/ainote/note")
@Slf4j
@RequiredArgsConstructor
public class AinoteNoteController extends JeecgController<AinoteNote, IAinoteNoteService> {

    private final IAinoteNoteService ainoteNoteService;

    private final JeecgBaseConfig jeecgBaseConfig;

    private final AinoteEmbeddingService ainoteEmbeddingService;

    private final AinoteGenerationFacade generationFacade;

    private final IAinoteAiConfigService ainoteAiConfigService;

    @Resource(name = "viewCountExecutor")
    private ShiroThreadPoolExecutor viewCountExecutor;

    /**
     * 分页列表查询
     */
    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<AinoteNoteListVO>> queryPageList(
            AinoteNote ainoteNote,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {

        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        String tenantIdStr = TokenUtils.getTenantIdByRequest(req);
        Integer tenantId = oConvertUtils.isNotEmpty(tenantIdStr) ? Integer.parseInt(tenantIdStr) : 0;

        // 手动构建带表别名的查询条件（因为 SQL 有 JOIN）
        QueryWrapper<AinoteNote> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("n.tenant_id", tenantId);
        queryWrapper.ne("n.note_status", 3);
        queryWrapper.eq("n.student_id", user.getId());

        // 前端搜索条件
        if (oConvertUtils.isNotEmpty(ainoteNote.getNoteTitle())) {
            queryWrapper.like("n.note_title", ainoteNote.getNoteTitle());
        }
        if (ainoteNote.getNoteStatus() != null) {
            queryWrapper.eq("n.note_status", ainoteNote.getNoteStatus());
        }
        if (ainoteNote.getIsPublic() != null) {
            queryWrapper.eq("n.is_public", ainoteNote.getIsPublic());
        }

        queryWrapper.orderByDesc("n.create_time");

        Page<AinoteNoteListVO> page = new Page<>(pageNo, pageSize);
        IPage<AinoteNoteListVO> pageList = ((AinoteNoteMapper) ainoteNoteService.getBaseMapper()).queryNoteListPage(page, queryWrapper);
        return Result.OK(pageList);
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    public Result<AinoteNoteDetailVO> queryById(@RequestParam(name = "id") String id) {
        // 先检查权限
        AinoteNote note = ainoteNoteService.getByIdWithPermission(id);
        if (note == null) {
            return Result.error("未找到对应数据或无权限访问");
        }
        // 返回详情 VO（包含关联表字段）
        AinoteNoteDetailVO detailVO = ((AinoteNoteMapper) ainoteNoteService.getBaseMapper()).queryNoteDetailById(id);
        try {
            viewCountExecutor.execute(() -> {
                try {
                    ((AinoteNoteMapper) ainoteNoteService.getBaseMapper()).incNoteViewCount(id);
                } catch (Exception e) {
                    log.warn("异步更新笔记浏览量失败: noteId={}", id, e);
                }
            });
        } catch (Exception e) {
            log.warn("提交笔记浏览量更新任务失败: noteId={}", id, e);
        }
        return Result.OK(detailVO);
    }

    @Operation(summary = "点赞笔记")
    @PostMapping(value = "/like")
    @RequiresPermissions("ainote:note:like")
    public Result<String> like(@RequestParam(name = "noteId") String noteId) {
        ainoteNoteService.likeNote(noteId);
        return Result.OK("点赞成功！");
    }

    @Operation(summary = "取消点赞")
    @PostMapping(value = "/unlike")
    @RequiresPermissions("ainote:note:like")
    public Result<String> unlike(@RequestParam(name = "noteId") String noteId) {
        ainoteNoteService.unlikeNote(noteId);
        return Result.OK("取消点赞成功！");
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
    public ResponseEntity<Result<String>> edit(@RequestBody @Validated AinoteNoteUpdateDTO dto) {
        try {
            ainoteNoteService.updateNote(dto);
            return ResponseEntity.ok(Result.OK("编辑成功！"));
        } catch (JeecgBootException e) {
            if (e.getErrCode() == HttpStatus.CONFLICT.value()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(HttpStatus.CONFLICT.value(), e.getMessage()));
            }
            if (e.getErrCode() == HttpStatus.BAD_REQUEST.value()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Result.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
            }
            throw e;
        }
    }

    @Operation(summary = "回滚笔记到指定版本")
    @PostMapping(value = "/rollback")
    @RequiresPermissions("ainote:note:edit")
    public Result<AinoteNote> rollback(
            @RequestParam(name = "noteId") String noteId,
            @RequestParam(name = "targetVersion") Integer targetVersion) {
        if (oConvertUtils.isEmpty(noteId)) {
            return Result.error("noteId不能为空");
        }
        if (targetVersion == null || targetVersion <= 0) {
            return Result.error("targetVersion不合法");
        }
        AinoteNote note = ainoteNoteService.rollbackToVersion(noteId, targetVersion);
        return Result.OK(note);
    }

    @Operation(summary = "基于baseVersion乐观锁重新生成笔记")
    @PostMapping(value = "/regenerate")
    @RequiresPermissions("ainote:note:edit")
    public ResponseEntity<Result<AinoteNoteRegenerateVO>> regenerate(
            @RequestBody @Validated AinoteNoteRegenerateDTO dto) {
        try {
            AinoteNoteRegenerateVO vo = ainoteNoteService.regenerateNote(dto);
            return ResponseEntity.ok(Result.OK(vo));
        } catch (JeecgBootException e) {
            if (e.getErrCode() == HttpStatus.CONFLICT.value()) {
                log.warn("笔记重新生成发生版本冲突: noteId={}, baseVersion={}, msg={}",
                        dto.getNoteId(), dto.getBaseVersion(), e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Result.error(HttpStatus.CONFLICT.value(), e.getMessage()));
            }
            log.warn("笔记重新生成失败: noteId={}, baseVersion={}, msg={}",
                    dto.getNoteId(), dto.getBaseVersion(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        } catch (Exception e) {
            log.error("笔记重新生成异常: noteId={}, baseVersion={}",
                    dto.getNoteId(), dto.getBaseVersion(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "重新生成失败！"));
        }
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

    @Operation(summary = "教师笔记列表（仅教师可用）")
    @GetMapping(value = "/teacherList")
    @RequiresPermissions("ainote:note:teacherList")
    public Result<IPage<AinoteTeacherNoteVO>> teacherList(
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(name = "courseId", required = false) String courseId,
            @RequestParam(name = "studentName", required = false) String studentName,
            HttpServletRequest req) {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            return Result.error("用户未登录");
        }
        String tenantIdStr = TokenUtils.getTenantIdByRequest(req);
        Integer tenantId = oConvertUtils.isNotEmpty(tenantIdStr) ? Integer.parseInt(tenantIdStr) : 0;
        String roleCode = oConvertUtils.getString(user.getRoleCode(), "");
        if (!RoleUtils.hasRole(roleCode, "teacher") && !RoleUtils.hasRole(roleCode, "admin")) {
            return Result.error(403, "无权限");
        }
        IPage<AinoteTeacherNoteVO> pageList = ainoteNoteService.queryTeacherNotePage(
                new Page<>(pageNo, pageSize), user.getId(), tenantId, courseId, studentName);
        return Result.OK(pageList);
    }

    @Operation(summary = "查询笔记版本历史")
    @GetMapping(value = "/versions")
    @RequiresPermissions("ainote:note:list")
    public Result<IPage<AinoteNoteVersionVO>> queryVersions(
            @RequestParam(name = "noteId") String noteId,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") Integer pageSize) {
        if (oConvertUtils.isEmpty(noteId)) {
            return Result.error("noteId不能为空");
        }
        int safePageSize = Math.min(pageSize != null ? pageSize : 20, 100);
        IPage<AinoteNoteVersionVO> pageList =
                ainoteNoteService.queryVersionPage(noteId, pageNo, safePageSize);
        return Result.OK(pageList);
    }

    @Operation(summary = "查询版本详情（含完整内容）")
    @GetMapping(value = "/version/{versionId}")
    @RequiresPermissions("ainote:note:list")
    public Result<AinoteNoteVersion> queryVersionDetail(
            @PathVariable(name = "versionId") String versionId) {
        if (oConvertUtils.isEmpty(versionId)) {
            return Result.error("versionId不能为空");
        }
        AinoteNoteVersion version = ainoteNoteService.queryVersionDetail(versionId);
        if (version == null) {
            return Result.error("版本不存在或无权限访问");
        }
        AinoteNoteVersion sanitized = new AinoteNoteVersion()
                .setId(version.getId())
                .setNoteId(version.getNoteId())
                .setVersionNumber(version.getVersionNumber())
                .setNoteContent(version.getNoteContent())
                .setAiSummary(version.getAiSummary())
                .setKeywords(version.getKeywords())
                .setCreatedBy(version.getCreatedBy())
                .setCreatedAt(version.getCreatedAt());
        return Result.OK(sanitized);
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
        boolean isAdmin = RoleUtils.hasRole(roleCode, "admin");
        if (!isAdmin) {
            return Result.error("导入功能仅限管理员使用");
        }

        // 调用安全导入方法
        return ainoteNoteService.importExcelWithSecurity(request, response, user);
    }

    @Operation(summary = "Semantic search with scope filtering")
    @GetMapping("/semanticSearch")
    @RequiresPermissions("ainote:note:list")
    public Result<List<SemanticSearchResultDTO>> semanticSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "personal") String scope,
            @RequestParam(defaultValue = "20") Integer topN,
            HttpServletRequest request) {
        if (oConvertUtils.isEmpty(q)) {
            return Result.error("查询内容不能为空");
        }

        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            return Result.error("用户未登录");
        }

        NoteSearchScope searchScope = NoteSearchScope.PERSONAL;
        if (oConvertUtils.isNotEmpty(scope)) {
            try {
                searchScope = NoteSearchScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignore) {
                searchScope = NoteSearchScope.PERSONAL;
            }
        }

        String roleCode = oConvertUtils.getString(user.getRoleCode(), "");
        if (searchScope == NoteSearchScope.ALL && !RoleUtils.hasRole(roleCode, "admin")) {
            return Result.error("ALL 范围仅管理员可用");
        }
        if (searchScope == NoteSearchScope.COURSE) {
            String tenantIdStr2 = TokenUtils.getTenantIdByRequest(request);
            Integer tenantId2 = oConvertUtils.isNotEmpty(tenantIdStr2) ? Integer.parseInt(tenantIdStr2) : 0;
            boolean isTeacherLike = RoleUtils.hasRole(roleCode, "teacher")
                    || RoleUtils.hasRole(roleCode, "admin")
                    || !ainoteNoteService.queryTeacherNotePage(
                            new Page<>(1, 1), user.getId(), tenantId2, null, null)
                    .getRecords().isEmpty();
            if (!isTeacherLike) {
                return Result.error("COURSE 范围仅教师可用");
            }
        }

        int safeTopN = (topN == null || topN <= 0) ? 20 : Math.min(topN, 50);
        String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
        Integer tenantId = oConvertUtils.isNotEmpty(tenantIdStr) ? Integer.parseInt(tenantIdStr) : 0;

        String knowledgeId = null;
        try {
            knowledgeId = ainoteAiConfigService.getConfig(tenantId).getKnowledgeId();
        } catch (Exception e) {
            log.warn("获取知识库ID失败，tenantId={}: {}", tenantId, e.getMessage());
        }

        if (oConvertUtils.isEmpty(knowledgeId)) {
            return Result.OK(List.of());
        }

        List<SemanticSearchResultDTO> result = ainoteEmbeddingService.searchNotesByScope(
                knowledgeId, q, searchScope, user.getId(), safeTopN);
        return Result.OK(result);
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

    @Operation(summary = "查询AI生成进度")
    @GetMapping(value = "/progress")
    public Result<AinoteProgressVO> getProgress(
            @RequestParam(name = "noteId") String noteId,
            @RequestParam(name = "knowledgeId", required = false) String knowledgeId) {
        AinoteProgressVO progress = generationFacade.getProgress(noteId, knowledgeId);
        return Result.OK(progress);
    }

}
