package org.jeecg.modules.ainote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 编辑笔记请求对象
 */
@Data
@Schema(description = "编辑笔记请求")
public class AinoteNoteUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "笔记ID不能为空")
    @Schema(description = "笔记ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "章节ID")
    private String chapterId;

    @Schema(description = "教学ID")
    private String teachingId;

    @Size(max = 200, message = "笔记标题长度不能超过200")
    @Schema(description = "笔记标题")
    private String noteTitle;

    @Schema(description = "笔记内容（Markdown格式）")
    private String noteContent;

    @Schema(description = "AI生成的摘要")
    private String aiSummary;

    @Size(max = 500, message = "关键词长度不能超过500")
    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "笔记状态：1-草稿，2-已完成")
    private Integer noteStatus;

    @Schema(description = "是否公开：0-私有，1-公开")
    private Integer isPublic;
}
