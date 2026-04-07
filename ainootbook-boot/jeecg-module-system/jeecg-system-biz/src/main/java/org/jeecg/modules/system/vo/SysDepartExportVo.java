package org.jeecg.modules.system.vo;

import lombok.Data;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecgframework.poi.excel.annotation.Excel;

@Data
public class SysDepartExportVo {
    /**组织路径*/
    @Excel(name="组织路径",width=50)
    private String departNameUrl;
    /**机构/部门名称*/
    @Excel(name="组织名称",width=50)
    private String departName;
    /**id*/
    private String id;
    /**父级id*/
    private String parentId;
    /**英文名*/
    @Excel(name="英文名",width=15)
    private String departNameEn;
    /**排序*/
    @Excel(name="排序",width=15)
    private Integer departOrder;
    /**描述*/
    @Excel(name="描述",width=15)
    private String description;
    /**组织类别 1=学校，2=组织单元，3=岗位，5=学院，6=专业，7=班级*/
    @Excel(name="组织类别",width=15,dicCode="org_category")
    private String orgCategory;
    /** 职级id */
    @Excel(name="职级",width=15,dictTable = "sys_position", dicCode = "id", dicText = "name")
    private String positionId;
    /**组织编码*/
    @Excel(name="组织编码",width=15)
    private String orgCode;
    /**手机号*/
    @Excel(name="手机号",width=15)
    private String mobile;
    /**传真*/
    @Excel(name="传真",width=15)
    private String fax;
    /**地址*/
    @Excel(name="地址",width=15)
    private String address;
    /**备注*/
    @Excel(name="备注",width=15)
    private String memo;
}
