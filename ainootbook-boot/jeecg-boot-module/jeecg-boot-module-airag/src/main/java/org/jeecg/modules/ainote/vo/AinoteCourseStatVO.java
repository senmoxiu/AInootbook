package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 课程笔记统计概览 VO
 * 汇总整门课程的笔记统计数据，供教师端概览卡片使用
 */
@Data
@Schema(description = "课程笔记统计概览视图对象")
public class AinoteCourseStatVO {

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "笔记总数")
    private Integer totalNotes;

    @Schema(description = "AI处理完成总数")
    private Integer completedNotes;

    @Schema(description = "素材上传总数")
    private Integer totalMaterials;

    @Schema(description = "参与学生数（有笔记记录的不重复学生数）")
    private Integer studentCount;

    @Schema(description = "素材类型分布（key=类型名称, value=数量）")
    private List<MaterialTypeStatVO> materialTypeStats;

    @Schema(description = "全课程高频关键词 TOP N（默认20个）")
    private List<AinoteChapterStatVO.KeywordFreqVO> topKeywords;

    @Schema(description = "各章节统计明细列表")
    private List<AinoteChapterStatVO> chapterStats;

    /**
     * 素材类型统计内嵌对象
     */
    @Data
    @Schema(description = "素材类型及数量")
    public static class MaterialTypeStatVO {

        @Schema(description = "素材类型（audio/video/image/document）")
        private String materialType;

        @Schema(description = "该类型素材数量")
        private Integer count;
    }
}
