package org.jeecg.modules.ainote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 笔记表实体类
 */
@Data
@TableName("ainote_note")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "笔记表")
public class AinoteNote implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "笔记ID")
    private String id;

    @Schema(description = "学生ID")
    private String studentId;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "章节ID")
    private String chapterId;

    @Schema(description = "教学ID")
    private String teachingId;

    @Excel(name = "笔记标题", width = 30)
    @Schema(description = "笔记标题")
    private String noteTitle;

    @Schema(description = "笔记内容（Markdown格式）")
    private String noteContent;

    @Schema(description = "AI生成的摘要")
    private String aiSummary;

    @Excel(name = "关键词", width = 20)
    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @Excel(name = "笔记状态", width = 10, dicCode = "ainote_note_status")
    @Dict(dicCode = "ainote_note_status")
    @Schema(description = "笔记状态：1-草稿，2-已完成，3-已删除")
    private Integer noteStatus;

    @Excel(name = "是否公开", width = 10, dicCode = "yn")
    @Dict(dicCode = "yn")
    @Schema(description = "是否公开：0-私有，1-公开")
    private Integer isPublic;

    @Schema(description = "浏览次数")
    private Integer viewCount;

    @Schema(description = "点赞次数")
    private Integer likeCount;

    @Excel(name = "创建人", width = 15)
    @Dict(dicCode = "sys_user,realname,username")
    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "所属部门编码")
    private String sysOrgCode;

    @Schema(description = "租户ID")
    private Integer tenantId;
}
