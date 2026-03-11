package org.jeecg.modules.airag.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 选课表实体类
 */
@Data
@TableName("ainote_course_selection")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "选课表")
public class AinoteCourseSelection implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "选课ID")
    private String id;

    @Dict(dictTable = "sys_user", dicCode = "id", dicText = "realname")
    @Schema(description = "学生ID")
    private String studentId;

    @Dict(dictTable = "ainote_teaching", dicCode = "id", dicText = "id")
    @Schema(description = "教学任务ID")
    private String teachingId;

    @Dict(dictTable = "ainote_course", dicCode = "id", dicText = "course_name")
    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "班级ID（双轨并存，与 depart_id 同步写入）")
    private String classId;

    @Dict(dictTable = "sys_depart", dicCode = "id", dicText = "depart_name")
    @Schema(description = "组织ID（关联 sys_depart）")
    private String departId;

    @Schema(description = "学期（格式：YYYY-YYYY-NN）")
    private String semester;

    @Schema(description = "学年（格式：YYYY-YYYY）")
    private String academicYear;

    @Dict(dicCode = "selection_status")
    @Schema(description = "状态：0-已退课，1-正常")
    private Integer status;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "选课时间")
    private Date selectedAt;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "所属部门编码")
    private String sysOrgCode;

    @Schema(description = "租户ID")
    private Integer tenantId;
}
