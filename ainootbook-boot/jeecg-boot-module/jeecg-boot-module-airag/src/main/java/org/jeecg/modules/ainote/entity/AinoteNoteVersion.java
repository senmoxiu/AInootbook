package org.jeecg.modules.ainote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 笔记版本表实体类
 */
@Data
@TableName("ainote_note_version")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "笔记版本表")
public class AinoteNoteVersion implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "版本ID")
    private String id;

    @Schema(description = "笔记ID")
    private String noteId;

    @Schema(description = "版本号")
    private Integer versionNumber;

    @Schema(description = "笔记内容")
    private String noteContent;

    @Schema(description = "AI摘要")
    private String aiSummary;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Schema(description = "生成任务ID")
    private String generationId;

    @Schema(description = "创建人")
    private String createdBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "租户ID")
    private Integer tenantId;
}
