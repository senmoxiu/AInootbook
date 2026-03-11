package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 生成进度 VO（WR-03: 前后端契约对齐）
 */
@Data
@Schema(description = "AI生成进度")
public class AinoteProgressVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "进度百分比 0-100")
    private int progress;

    @Schema(description = "状态: idle/processing/completed/failed")
    private String status;

    @Schema(description = "错误信息（仅 failed 时有值）")
    private String errorMsg;
}
