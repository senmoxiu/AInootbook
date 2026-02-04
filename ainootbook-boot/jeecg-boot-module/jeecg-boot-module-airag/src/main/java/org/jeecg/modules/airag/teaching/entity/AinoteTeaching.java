package org.jeecg.modules.airag.teaching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;

/**
 * 教学关系表实体类
 */
@Data
@TableName("ainote_teaching")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "教学关系表")
public class AinoteTeaching implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "教学ID")
    private String id;

    @Excel(name = "教师", width = 15, dictTable = "sys_user", dicCode = "id", dicText = "realname")
    @Dict(dictTable = "sys_user", dicCode = "id", dicText = "realname")
    @Schema(description = "教师ID")
    private String teacherId;

    @Excel(name = "课程", width = 15, dictTable = "ainote_course", dicCode = "id", dicText = "course_name")
    @Dict(dictTable = "ainote_course", dicCode = "id", dicText = "course_name")
    @Schema(description = "课程ID")
    private String courseId;

    @JsonIgnore
    @Schema(description = "班级ID（旧字段，已废弃）")
    @Deprecated
    private String classId;

    @Excel(name = "组织", width = 20, dictTable = "sys_depart", dicCode = "id", dicText = "depart_name")
    @Dict(dictTable = "sys_depart", dicCode = "id", dicText = "depart_name")
    @Schema(description = "组织ID（关联sys_depart，院系/专业/班级）")
    private String departId;

    @Excel(name = "学期", width = 15)
    @Schema(description = "学期（格式：YYYY-YYYY-NN）")
    private String semester;

    @Excel(name = "学年", width = 15)
    @Schema(description = "学年（格式：YYYY-YYYY）")
    private String academicYear;

    @Excel(name = "状态", width = 10, dicCode = "teaching_status")
    @Dict(dicCode = "teaching_status")
    @Schema(description = "状态：0-已结束，1-进行中")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private java.util.Date createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private java.util.Date updateTime;

    @Schema(description = "所属部门编码")
    private String sysOrgCode;

    @Schema(description = "租户ID")
    private Integer tenantId;
}
