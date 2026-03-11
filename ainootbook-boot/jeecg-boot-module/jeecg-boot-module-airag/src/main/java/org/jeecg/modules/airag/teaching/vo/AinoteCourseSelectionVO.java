package org.jeecg.modules.airag.teaching.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 选课视图对象
 */
@Data
@Schema(description = "选课视图对象")
public class AinoteCourseSelectionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "选课ID")
    private String id;

    @Schema(description = "学生ID")
    private String studentId;

    @Schema(description = "学生姓名")
    private String studentName;

    @Schema(description = "教学任务ID")
    private String teachingId;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "课程名称")
    private String courseName;

    @Schema(description = "课程代码")
    private String courseCode;

    @Schema(description = "教师姓名")
    private String teacherName;

    @Schema(description = "组织ID")
    private String departId;

    @Schema(description = "组织名称")
    private String departName;

    @Schema(description = "学期")
    private String semester;

    @Schema(description = "学年")
    private String academicYear;

    @Schema(description = "状态：0-已退课，1-正常")
    private Integer status;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "选课时间")
    private Date selectedAt;
}
