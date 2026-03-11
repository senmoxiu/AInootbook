package org.jeecg.modules.airag.teaching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 选课编辑 DTO
 */
@Data
@Schema(description = "选课编辑请求")
public class AinoteCourseSelectionEditDTO {

    @NotBlank(message = "选课ID不能为空")
    @Schema(description = "选课ID（必填）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "班级ID")
    private String classId;

    @Schema(description = "组织ID")
    private String departId;

    @Schema(description = "状态：0-已退课，1-正常")
    private Integer status;
}
