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
import org.jeecgframework.poi.excel.annotation.Excel;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 课程表实体类
 */
@Data
@TableName("ainote_course")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@Schema(description = "课程表")
public class AinoteCourse implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "课程ID")
    private String id;

    @Excel(name = "课程名称", width = 20)
    @Schema(description = "课程名称")
    private String courseName;

    @Excel(name = "课程代码", width = 15)
    @Schema(description = "课程代码")
    private String courseCode;

    @Excel(name = "学分", width = 10)
    @Schema(description = "学分")
    private BigDecimal credits;

    @Excel(name = "课时", width = 10)
    @Schema(description = "课时")
    private Integer courseHours;

    @Excel(name = "课程描述", width = 30)
    @Schema(description = "课程描述")
    private String courseDesc;

    @Excel(name = "课程类型", width = 10, dicCode = "course_type")
    @Dict(dicCode = "course_type")
    @Schema(description = "课程类型：1-必修，2-选修")
    private Integer courseType;

    @Excel(name = "状态", width = 10, dicCode = "course_status")
    @Dict(dicCode = "course_status")
    @Schema(description = "状态：0-禁用，1-启用")
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
