package org.jeecg.modules.ainote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 笔记列表视图（包含关联表字段）
 */
@Data
@Schema(description = "笔记列表信息")
public class AinoteNoteListVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "笔记ID")
    private String id;

    @Schema(description = "学生ID")
    private String studentId;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "章节ID")
    private String chapterId;

    @Schema(description = "笔记标题")
    private String noteTitle;

    @Schema(description = "AI生成的摘要")
    private String aiSummary;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "当前版本号")
    private Integer currentVersion;

    @Schema(description = "笔记状态：1-草稿，2-已完成，3-已删除")
    private Integer noteStatus;

    @Schema(description = "笔记状态文本")
    private String noteStatusText;

    @Schema(description = "是否公开：0-私有，1-公开")
    private Integer isPublic;

    @Schema(description = "浏览次数")
    private Integer viewCount;

    @Schema(description = "点赞次数")
    private Integer likeCount;

    @Schema(description = "创建人username")
    private String createBy;

    @Schema(description = "创建人姓名")
    private String createByName;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
