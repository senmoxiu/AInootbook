package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 生成进度 VO（WR-03: 前后端契约对齐）
 */
@Data
@Schema(description = "AI生成进度")
public class AinoteProgressVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "整体进度百分比 0-100")
    private int progress;

    @Schema(description = "整体状态: idle/processing/completed/failed")
    private String status;

    @Schema(description = "错误信息（仅 failed 时有值）")
    private String errorMsg;

    @Schema(description = "分步进度列表")
    private List<StepVO> steps;

    @Data
    @Schema(description = "单步进度")
    public static class StepVO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "步骤标识: asr/tika/summary")
        private String key;

        @Schema(description = "步骤名称")
        private String label;

        @Schema(description = "步骤状态: pending/processing/completed/failed/skipped")
        private String status;

        @Schema(description = "步骤进度 0-100")
        private int progress;

        @Schema(description = "错误信息（仅 failed 时有值）")
        private String errorMsg;
    }
}
