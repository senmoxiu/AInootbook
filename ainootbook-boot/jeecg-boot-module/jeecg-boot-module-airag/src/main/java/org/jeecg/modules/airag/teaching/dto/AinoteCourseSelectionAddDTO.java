package org.jeecg.modules.airag.teaching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 选课新增 DTO
 */
@Data
@Schema(description = "选课新增请求")
public class AinoteCourseSelectionAddDTO {

    @NotBlank(message = "教学任务ID不能为空")
    @Schema(description = "教学任务ID（必填）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String teachingId;

    @Schema(description = "学生ID（管理员可指定，普通学生由后端从登录用户获取）")
    private String studentId;

    @Schema(description = "班级ID（双轨并存，优先从 teaching 记录获取）")
    private String classId;

    @Schema(description = "组织ID（双轨并存，优先从 teaching 记录获取）")
    private String departId;
}
