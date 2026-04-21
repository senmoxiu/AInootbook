package org.jeecg.modules.airag.teaching.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.vo.AinoteTeachingVO;

import java.util.List;

/**
 * 教学关系表 Service 接口
 */
public interface IAinoteTeachingService extends IService<AinoteTeaching> {

    /**
     * 应用数据权限过滤到查询条件
     * @param wrapper 查询条件
     */
    void applyDataPermission(QueryWrapper<AinoteTeaching> wrapper);

    /**
     * 校验 depart_id 合法性
     * @param departId 组织ID
     */
    void validateDepartId(String departId);

    /**
     * 校验 semester 格式
     * @param semester 学期
     */
    void validateSemester(String semester);

    /**
     * 联表分页查询教学任务视图
     * @param page    分页参数
     * @param wrapper 动态查询条件（含数据权限）
     * @return 分页视图结果
     */
    IPage<AinoteTeachingVO> queryTeachingVoPage(
            Page<AinoteTeachingVO> page,
            QueryWrapper<AinoteTeaching> wrapper);

    /**
     * 批量新增/更新教学关系
     * @param dto 批量请求参数
     * @return 批量操作结果
     */
    BatchResult batchUpsert(BatchUpsertDTO dto);

    /**
     * 批量删除教学关系（带权限校验）
     * @param ids ID列表
     * @return 是否删除成功
     */
    boolean batchDeleteWithPermission(List<String> ids);

    /**
     * 查询指定部门及其所有子孙部门的 ID 列表
     * @param departId 根部门ID
     * @return 包含自身及所有子孙的 ID 列表
     */
    List<String> getDepartAndSubIds(String departId);
}
