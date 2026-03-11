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
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * AI任务表实体类
 */
@Data
@TableName("ainote_ai_task")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "AI任务表")
public class AinoteAiTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "关联笔记ID")
    private String noteId;

    @Schema(description = "关联素材ID")
    private String materialId;

    @Dict(dicCode = "ainote_task_type")
    @Schema(description = "任务类型: asr/tika/summary")
    private String taskType;

    @Dict(dicCode = "ainote_task_status")
    @Schema(description = "任务状态: 0=待处理/1=处理中/2=已完成/3=失败")
    private Integer taskStatus;

    @Schema(description = "处理结果JSON")
    private String processResult;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "下次重试时间")
    private Date nextRetryAt;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "开始时间")
    private Date startedAt;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "完成时间")
    private Date completedAt;

    @Schema(description = "执行时长(毫秒)")
    private Long duration;

    @Schema(description = "执行节点ID")
    private String workerId;

    @Schema(description = "第三方任务ID(用于ASR轮询)")
    private String vendorTaskId;

    @Schema(description = "租户ID")
    private Integer tenantId;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;
}
