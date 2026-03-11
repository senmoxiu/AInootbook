package org.jeecg.modules.ainote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 分享详情（包含笔记内容）
 */
@Data
@Schema(description = "笔记分享详情")
public class AinoteNoteShareDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "分享ID")
    private String shareId;

    @Schema(description = "笔记ID")
    private String noteId;

    @Schema(description = "分享码")
    private String shareCode;

    @Schema(description = "分享类型：1-链接分享，2-二维码分享")
    private Integer shareType;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间（NULL表示永久）")
    private Date expireTime;

    @Schema(description = "分享查看次数")
    private Integer shareViewCount;

    @Schema(description = "分享状态：0-已失效，1-有效")
    private Integer shareStatus;

    @Schema(description = "学生ID")
    private String studentId;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "章节ID")
    private String chapterId;

    @Schema(description = "教学ID")
    private String teachingId;

    @Schema(description = "笔记标题")
    private String noteTitle;

    @Schema(description = "笔记内容（Markdown格式）")
    private String noteContent;

    @Schema(description = "AI生成的摘要")
    private String aiSummary;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "笔记状态：1-草稿，2-已完成，3-已删除")
    private Integer noteStatus;

    @Schema(description = "是否公开：0-私有，1-公开")
    private Integer isPublic;

    @Schema(description = "笔记浏览次数")
    private Integer noteViewCount;

    @Schema(description = "点赞次数")
    private Integer likeCount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
