package org.jeecg.modules.airag.teaching.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.airag.teaching.entity.AinoteTeaching;
import org.jeecg.modules.airag.teaching.vo.AinoteTeachingVO;

import java.util.List;

/**
 * 教学关系表 Mapper 接口
 */
public interface AinoteTeachingMapper extends BaseMapper<AinoteTeaching> {

    /**
     * 根据课程ID、组织ID、学期查询教学关系
     */
    List<AinoteTeaching> selectByCourseAndDepartAndSemester(
            @Param("courseId") String courseId,
            @Param("departId") String departId,
            @Param("semester") String semester);

    /**
     * 批量查询已存在的教学关系（用于差分计算）
     */
    List<AinoteTeaching> selectExistingByKeys(
            @Param("courseId") String courseId,
            @Param("departIds") List<String> departIds,
            @Param("semester") String semester);

    /**
     * 联表分页查询教学任务视图
     */
    IPage<AinoteTeachingVO> queryTeachingVoPage(
            Page<AinoteTeachingVO> page,
            @Param("ew") Wrapper<AinoteTeaching> ew);
}
