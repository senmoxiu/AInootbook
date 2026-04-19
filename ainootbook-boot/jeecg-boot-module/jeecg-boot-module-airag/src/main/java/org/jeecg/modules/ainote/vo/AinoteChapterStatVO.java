package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 章节笔记统计 VO
 * 用于教师端查看各章节笔记上传数量、AI处理完成数量及高频关键词分布
 */
@Data
@Schema(description = "章节笔记统计视图对象")
public class AinoteChapterStatVO {

    @Schema(description = "章节ID")
    private String chapterId;

    @Schema(description = "章节名称")
    private String chapterName;

    @Schema(description = "章节序号")
    private Integer chapterOrder;

    @Schema(description = "笔记上传总数")
    private Integer uploadCount;

    @Schema(description = "AI处理完成数量（noteStatus=2）")
    private Integer completedCount;

    @Schema(description = "待处理数量（noteStatus=1）")
    private Integer pendingCount;

    @Schema(description = "处理失败数量（noteStatus=4）")
    private Integer failedCount;

    @Schema(description = "AI处理完成率（百分比，保留一位小数）")
    private Double completionRate;

    @Schema(description = "高频关键词列表（按出现频次降序，最多返回10个）")
    private List<KeywordFreqVO> topKeywords;

    /**
     * 关键词频次内嵌对象
     */
    @Data
    @Schema(description = "关键词及其出现频次")
    public static class KeywordFreqVO {

        @Schema(description = "关键词文本")
        private String keyword;

        @Schema(description = "在本章节所有笔记中出现的频次")
        private Integer frequency;
    }
}
