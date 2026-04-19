package org.jeecg.modules.ainote.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresRoles;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.service.IAinoteStatisticsService;
import org.jeecg.modules.ainote.vo.AinoteCourseStatVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记统计 Controller
 * 仅教师/管理员可访问，汇总展示各章节笔记上传数量、AI处理完成数量及高频关键词分布。
 * 不暴露学生笔记的具体内容，所有返回数据均为聚合统计结果。
 */
@Slf4j
@Tag(name = "AI笔记 - 统计分析")
@RestController
@RequestMapping("/ainote/statistics")
@RequiredArgsConstructor
public class AinoteStatisticsController {

    private final IAinoteStatisticsService statisticsService;

    /**
     * 查询课程笔记统计概览
     * 返回指定课程的笔记总数、AI完成数、素材数、参与学生数及各章节明细。
     *
     * @param courseId  课程ID（必填）
     * @param semester  学期（可选，格式：2025-1）
     * @param chapterId 章节ID（可选，指定后只返回该章节数据）
     */
    @GetMapping("/course")
    @Operation(summary = "课程笔记统计概览", description = "教师查看指定课程各章节笔记上传、AI处理完成及高频关键词统计")
    @RequiresRoles(value = {"teacher", "admin"}, logical = org.apache.shiro.authz.annotation.Logical.OR)
    public Result<AinoteCourseStatVO> getCourseStatistics(
            @Parameter(description = "课程ID", required = true)
            @RequestParam String courseId,
            @Parameter(description = "学期，格式：2025-1")
            @RequestParam(required = false) String semester,
            @Parameter(description = "章节ID，不传则返回全部章节")
            @RequestParam(required = false) String chapterId,
            HttpServletRequest request) {
        String tenantId = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isEmpty(tenantId)) {
            tenantId = "0";
        }
        return Result.ok(statisticsService.getCourseStatistics(courseId, tenantId, semester, chapterId));
    }

    /**
     * 查询全课程高频关键词排行
     * 对指定课程所有笔记的 keywords 字段进行词频统计，返回 TOP N 关键词。
     *
     * @param courseId 课程ID（必填）
     * @param topN     返回数量（默认20，最大50）
     */
    @GetMapping("/keywords")
    @Operation(summary = "高频关键词排行", description = "统计指定课程所有笔记的高频关键词，按频次降序返回")
    @RequiresRoles(value = {"teacher", "admin"}, logical = org.apache.shiro.authz.annotation.Logical.OR)
    public Result<?> getTopKeywords(
            @Parameter(description = "课程ID", required = true)
            @RequestParam String courseId,
            @Parameter(description = "返回关键词数量，默认20")
            @RequestParam(defaultValue = "20") Integer topN,
            HttpServletRequest request) {
        String tenantId = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isEmpty(tenantId)) {
            tenantId = "0";
        }
        return Result.ok(statisticsService.getTopKeywords(courseId, tenantId, topN));
    }

    /**
     * 查询素材类型分布
     * 统计指定课程各类型素材（音频/视频/图片/文档）的数量分布。
     *
     * @param courseId 课程ID（必填）
     */
    @GetMapping("/materials")
    @Operation(summary = "素材类型分布统计", description = "统计指定课程各类型素材数量")
    @RequiresRoles(value = {"teacher", "admin"}, logical = org.apache.shiro.authz.annotation.Logical.OR)
    public Result<?> getMaterialTypeStats(
            @Parameter(description = "课程ID", required = true)
            @RequestParam String courseId,
            HttpServletRequest request) {
        String tenantId = TokenUtils.getTenantIdByRequest(request);
        if (oConvertUtils.isEmpty(tenantId)) {
            tenantId = "0";
        }
        return Result.ok(statisticsService.getMaterialTypeStats(courseId, tenantId));
    }
}
