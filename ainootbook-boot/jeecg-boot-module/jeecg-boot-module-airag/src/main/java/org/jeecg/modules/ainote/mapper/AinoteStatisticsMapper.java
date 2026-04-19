package org.jeecg.modules.ainote.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.ainote.vo.AinoteChapterStatVO;
import org.jeecg.modules.ainote.vo.AinoteCourseStatVO;

import java.util.List;

/**
 * 笔记统计 Mapper
 */
@Mapper
public interface AinoteStatisticsMapper {

    /** 按章节分组统计笔记数量 */
    List<AinoteChapterStatVO> selectChapterStats(
            @Param("courseId") String courseId,
            @Param("tenantId") String tenantId,
            @Param("semester") String semester,
            @Param("chapterId") String chapterId);

    /** 统计课程笔记总数 */
    int countNotes(@Param("courseId") String courseId, @Param("tenantId") String tenantId);

    /** 统计AI处理完成笔记数 */
    int countCompletedNotes(@Param("courseId") String courseId, @Param("tenantId") String tenantId);

    /** 统计素材总数 */
    int countMaterials(@Param("courseId") String courseId, @Param("tenantId") String tenantId);

    /** 统计参与学生数 */
    int countStudents(@Param("courseId") String courseId, @Param("tenantId") String tenantId);

    /** 按素材类型分组统计 */
    List<AinoteCourseStatVO.MaterialTypeStatVO> selectMaterialTypeStats(
            @Param("courseId") String courseId, @Param("tenantId") String tenantId);

    /** 查询课程下所有笔记的关键词原始字段 */
    List<String> selectAllKeywords(
            @Param("courseId") String courseId, @Param("tenantId") String tenantId);
}
