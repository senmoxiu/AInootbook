package org.jeecg.modules.airag.teaching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

/**
 * 批量配置教学任务请求对象
 */
@Data
@Schema(description = "批量配置教学任务请求")
public class BatchUpsertDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "课程ID不能为空")
    @Schema(description = "课程ID", required = true)
    private String courseId;

    @NotEmpty(message = "组织ID列表不能为空")
    @Size(max = 100, message = "单次批量操作最多100个组织")
    @Schema(description = "组织ID列表（院系/专业/班级）", required = true)
    private List<@NotBlank(message = "组织ID不能为空") String> departIds;

    @NotBlank(message = "学期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{4}-(0[1-9]|1[0-2]|[1-9])$", message = "学期格式错误，应为YYYY-YYYY-N或YYYY-YYYY-NN")
    @Schema(description = "学期（格式：YYYY-YYYY-NN）", required = true, example = "2024-2025-01")
    private String semester;

    @NotBlank(message = "学年不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "学年格式错误，应为YYYY-YYYY")
    @Schema(description = "学年（格式：YYYY-YYYY）", required = true, example = "2024-2025")
    private String academicYear;

    @Schema(description = "教师ID（管理员可指定，教师角色自动填充当前用户）")
    private String teacherId;
}
