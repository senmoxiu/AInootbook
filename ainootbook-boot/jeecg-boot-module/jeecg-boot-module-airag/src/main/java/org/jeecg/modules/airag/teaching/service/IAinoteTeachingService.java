package org.jeecg.modules.airag.teaching.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.teaching.dto.BatchResult;
import org.jeecg.modules.airag.teaching.dto.BatchUpsertDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;

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
}
