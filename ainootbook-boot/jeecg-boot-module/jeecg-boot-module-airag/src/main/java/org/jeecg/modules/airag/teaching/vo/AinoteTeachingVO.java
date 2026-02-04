package org.jeecg.modules.airag.teaching.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 教学关系视图对象
 */
@Data
@Schema(description = "教学关系视图对象")
public class AinoteTeachingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "教学ID")
    private String id;

    @Schema(description = "教师ID")
    private String teacherId;

    @Schema(description = "教师姓名")
    private String teacherName;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "课程代码")
    private String courseCode;

    @Schema(description = "组织ID")
    private String departId;

    @Schema(description = "组织名称")
    private String departName;

    @Schema(description = "组织类型（5=院系，6=专业，7=班级）")
    private String orgCategory;

    @Schema(description = "学期")
    private String semester;

    @Schema(description = "学年")
    private String academicYear;

    @Schema(description = "状态：0-已结束，1-进行中")
    private Integer status;

    @Schema(description = "状态文本")
    private String statusText;
}
