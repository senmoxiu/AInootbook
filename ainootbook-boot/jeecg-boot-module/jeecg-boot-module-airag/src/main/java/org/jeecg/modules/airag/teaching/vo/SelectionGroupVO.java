package org.jeecg.modules.airag.teaching.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 选课聚合视图：按教学任务聚合，展示选课人数
 */
@Data
@Schema(description = "选课聚合视图（管理员用）")
public class SelectionGroupVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "教学任务ID")
    private String teachingId;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "课程代码")
    private String courseCode;

    @Schema(description = "教师姓名")
    private String teacherName;

    @Schema(description = "组织名称")
    private String departName;

    @Schema(description = "学期")
    private String semester;

    @Schema(description = "选课人数（status=1）")
    private Integer studentCount;
}
