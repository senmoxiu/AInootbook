package org.jeecg.modules.ainote.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 素材表实体类
 */
@Data
@TableName("ainote_material")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "素材表")
public class AinoteMaterial implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键")
    private String id;

    @Schema(description = "关联笔记ID")
    private String noteId;

    @Dict(dicCode = "ainote_file_type")
    @Schema(description = "文件类型: audio/document/image")
    private String fileType;

    @Schema(description = "MinIO对象路径")
    private String filePath;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件扩展名")
    private String fileExt;

    @Schema(description = "文件大小(字节)")
    private Long fileSize;

    @Dict(dicCode = "ainote_process_status")
    @Schema(description = "处理状态: 0=待处理/1=处理中/2=已完成/3=失败")
    private Integer processStatus;

    @Schema(description = "租户ID")
    private Integer tenantId;

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

    @TableLogic
    @Schema(description = "删除标记: 0=正常/1=已删除")
    private Integer delFlag;
}
