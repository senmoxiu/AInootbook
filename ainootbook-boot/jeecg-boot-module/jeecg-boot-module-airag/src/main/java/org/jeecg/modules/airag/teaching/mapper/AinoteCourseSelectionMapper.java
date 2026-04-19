package org.jeecg.modules.airag.teaching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.airag.teaching.entity.AinoteCourseSelection;
import org.jeecg.modules.airag.teaching.vo.AvailableTeachingVO;
import org.jeecg.modules.airag.teaching.vo.AinoteCourseSelectionVO;

/**
 * 选课表 Mapper 接口
 */
public interface AinoteCourseSelectionMapper extends BaseMapper<AinoteCourseSelection> {

    /**
     * 联表分页查询选课视图（含学生姓名、课程名称、教师姓名、组织名称）
     *
     * @param page  分页参数
     * @param ew    动态查询条件（使用子查询包裹避免列歧义）
     * @return 分页结果
     */
    IPage<AinoteCourseSelectionVO> querySelectionVoPage(
            Page<AinoteCourseSelectionVO> page,
            @Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<AinoteCourseSelection> ew);

    /**
     * 分页查询学生可选教学任务
     */
    IPage<AvailableTeachingVO> queryAvailableTeachings(
            Page<AvailableTeachingVO> page,
            @Param("studentId") String studentId,
            @Param("tenantId") Integer tenantId,
            @Param("courseName") String courseName);
}
