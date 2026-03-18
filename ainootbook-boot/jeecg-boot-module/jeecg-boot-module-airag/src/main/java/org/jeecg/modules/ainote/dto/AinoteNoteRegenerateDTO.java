package org.jeecg.modules.ainote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 重新生成笔记请求对象
 */
@Data
@Schema(description = "重新生成笔记请求")
public class AinoteNoteRegenerateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "noteId不能为空")
    @Schema(description = "笔记ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String noteId;

    @NotNull(message = "baseVersion不能为空")
    @Min(value = 1, message = "baseVersion必须大于0")
    @Schema(description = "基准版本号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer baseVersion;

    @Size(max = 20000, message = "补充内容长度不能超过20000")
    @Schema(description = "补充内容")
    private String additionalContent;
}
