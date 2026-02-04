package org.jeecg.modules.airag.teaching.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 课程视图对象
 */
@Data
@Schema(description = "课程视图对象")
public class AinoteCourseVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "课程ID")
    private String id;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "课程代码")
    private String courseCode;

    @Schema(description = "学分")
    private BigDecimal credits;

    @Schema(description = "课时")
    private Integer courseHours;

    @Schema(description = "课程描述")
    private String courseDesc;

    @Schema(description = "课程类型：1-必修，2-选修")
    private Integer courseType;

    @Schema(description = "课程类型文本")
    private String courseTypeText;

    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;

    @Schema(description = "被引用次数（教学任务数）")
    private Long referenceCount;
}
