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
                .setSummaryFlowEnabled(0);
    }
}
