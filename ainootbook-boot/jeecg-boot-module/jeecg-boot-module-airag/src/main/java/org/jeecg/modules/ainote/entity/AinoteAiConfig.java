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
 * AI配置实体类
 */
@Data
@TableName("ainote_ai_config")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "AI配置")
public class AinoteAiConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "摘要模型ID")
    private String summaryModelId;

    @Schema(description = "OCR Vision模型ID")
    private String ocrModelId;

    @Schema(description = "ASR模型ID")
    private String asrModelId;

    @Schema(description = "视频处理模型ID")
    private String videoModelId;

    @Schema(description = "关键词模型ID")
    private String keywordsModelId;

    @Schema(description = "整合模型ID")
    private String integrateModelId;

    @Schema(description = "知识库ID")
    private String knowledgeId;

    @Schema(description = "摘要提示词Key")
    private String summaryPromptKey;

    @Schema(description = "关键词提示词Key")
    private String keywordsPromptKey;

    @Schema(description = "整合提示词Key")
    private String integratePromptKey;

    @Schema(description = "摘要最大长度")
    private Integer maxSummaryLength;

    @Schema(description = "关键词最大数量")
    private Integer maxKeywordsCount;

    @Schema(description = "摘要Flow流程ID")
    private String summaryFlowId;

    @Schema(description = "是否启用Flow摘要")
    private Integer summaryFlowEnabled;

    @Schema(description = "ASR失败模式: skip/retry/fail_all")
    private String asrFailureMode;

    @Schema(description = "ASR重试次数上限(0-10)")
    private Integer asrRetryLimit;

    @Schema(description = "OCR失败模式: skip/retry/fail_all")
    private String ocrFailureMode;

    @Schema(description = "OCR重试次数上限(0-10)")
    private Integer ocrRetryLimit;

    @Schema(description = "视频失败模式: skip/retry/fail_all")
    private String videoFailureMode;

    @Schema(description = "视频重试次数上限(0-10)")
    private Integer videoRetryLimit;

    @Schema(description = "摘要失败模式: skip/retry/fail_all")
    private String summaryFailureMode;

    @Schema(description = "摘要重试次数上限(0-10)")
    private Integer summaryRetryLimit;

    @Schema(description = "整合失败模式: skip/retry/fail_all")
    private String integrateFailureMode;

    @Schema(description = "整合重试次数上限(0-10)")
    private Integer integrateRetryLimit;

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

    /**
     * 构建默认配置
     */
    public static AinoteAiConfig defaults() {
        return new AinoteAiConfig()
                .setSummaryPromptKey("note_summary")
                .setKeywordsPromptKey("note_keywords")
                .setIntegratePromptKey("note_integrate")
                .setMaxSummaryLength(200)
                .setMaxKeywordsCount(5)
                .setAsrFailureMode("retry")
                .setAsrRetryLimit(3)
                .setOcrFailureMode("retry")
                .setOcrRetryLimit(3)
                .setVideoFailureMode("retry")
                .setVideoRetryLimit(3)
                .setSummaryFailureMode("retry")
                .setSummaryRetryLimit(3)
                .setIntegrateFailureMode("skip")
                .setIntegrateRetryLimit(0)
                .setSummaryFlowEnabled(0);
    }
}
