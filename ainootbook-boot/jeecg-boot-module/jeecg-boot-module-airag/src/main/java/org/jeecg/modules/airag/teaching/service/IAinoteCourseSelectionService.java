package org.jeecg.modules.airag.teaching.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.teaching.dto.AinoteCourseSelectionAddDTO;
import org.jeecg.modules.airag.teaching.entity.AinoteCourseSelection;
import org.jeecg.modules.airag.teaching.vo.AvailableTeachingVO;
import org.jeecg.modules.airag.teaching.vo.AinoteCourseSelectionVO;
import org.jeecg.modules.airag.teaching.vo.SelectionGroupVO;

import java.util.List;

/**
 * 选课表 Service 接口
 */
public interface IAinoteCourseSelectionService extends IService<AinoteCourseSelection> {

    /**
     * 根据当前登录用户角色动态追加数据权限条件：
     * - admin：仅租户隔离
     * - teacher：租户 + teaching_id IN (当前教师的教学任务)
     * - student：租户 + student_id = 当前用户
     */
    void applyDataPermission(QueryWrapper<AinoteCourseSelection> wrapper);

    /**
     * 选课逻辑：
     * 1. 校验 student_id + teaching_id 唯一约束（已退课记录则恢复）
     * 2. 从 teaching 记录获取 course_id、semester、academic_year、depart_id、class_id
     * 3. 双轨写入 class_id 和 depart_id
     */
    void addSelection(AinoteCourseSelectionAddDTO dto);

    /**
     * 软删除（退课）：将 status 置为 0，不物理删除
     */
    void softDeleteById(String id);

    /**
     * 批量软删除（退课）
     */
    void softDeleteByIds(List<String> ids);

    /**
     * 联表分页查询选课视图
     */
    IPage<AinoteCourseSelectionVO> querySelectionVoPage(
            Page<AinoteCourseSelectionVO> page,
            QueryWrapper<AinoteCourseSelection> wrapper);

    /**
     * 分页查询当前学生可选教学任务
     */
    IPage<AvailableTeachingVO> queryAvailableTeachings(
            Page<AvailableTeachingVO> page,
            String courseName);

    /**
     * 按教学任务聚合查询选课人数（管理员视图）
     */
    IPage<SelectionGroupVO> queryGroupedByTeaching(
            Page<SelectionGroupVO> page,
            String courseName,
            String semester);

    /**
     * 清空某教学任务下的所有选课记录（软删除）
     */
    void clearByTeachingId(String teachingId);

    /**
     * 查询某教学任务下的所有选课学生明细
     */
    List<AinoteCourseSelectionVO> queryStudentsByTeachingId(String teachingId);

    /**
     * 查询当前用户已选课程列表（学生：已选课程；admin：全部课程）
     */
    IPage<org.jeecg.modules.airag.teaching.entity.AinoteCourse> queryMySelectedCourses(
            Page<org.jeecg.modules.airag.teaching.entity.AinoteCourse> page);
}
