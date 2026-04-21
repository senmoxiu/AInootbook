package org.jeecg.modules.airag.teaching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.airag.teaching.entity.AinoteCourseSelection;
import org.jeecg.modules.airag.teaching.vo.AvailableTeachingVO;
import org.jeecg.modules.airag.teaching.vo.AinoteCourseSelectionVO;
import org.jeecg.modules.airag.teaching.vo.SelectionGroupVO;

import java.util.List;

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
    /**
     * 按教学任务聚合查询选课人数（管理员视图）
     */
    IPage<SelectionGroupVO> queryGroupedByTeaching(
            Page<SelectionGroupVO> page,
            @Param("tenantId") Integer tenantId,
            @Param("courseName") String courseName,
            @Param("semester") String semester,
            @Param("teacherFilter") String teacherFilter);

    /**
     * 查询某教学任务下的所有选课学生明细
     */
    List<AinoteCourseSelectionVO> queryStudentsByTeachingId(
            @Param("teachingId") String teachingId,
            @Param("tenantId") Integer tenantId);

    /**
     * 查询当前用户已选课程列表（学生：已选课程；admin：全部课程）
     */
    IPage<org.jeecg.modules.airag.teaching.entity.AinoteCourse> queryMySelectedCourses(
            Page<org.jeecg.modules.airag.teaching.entity.AinoteCourse> page,
            @Param("studentId") String studentId,
            @Param("tenantId") Integer tenantId,
            @Param("isAdmin") boolean isAdmin);
}
