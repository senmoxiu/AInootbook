package org.jeecg.modules.ainote.service;

import org.jeecg.modules.ainote.vo.AinoteChapterStatVO;
import org.jeecg.modules.ainote.vo.AinoteCourseStatVO;

import java.util.List;

/**
 * 笔记统计 Service 接口
 * 仅供教师/管理员调用，所有查询均强制附加 tenantId 隔离，不返回学生笔记具体内容。
 *
 * <p>当前为接口定义，实现类待补全。</p>
 */
public interface IAinoteStatisticsService {

    /**
     * 获取课程笔记统计概览
     * 包含：笔记总数、AI完成数、素材数、参与学生数、各章节明细、全课程高频关键词。
     *
     * @param courseId  课程ID
     * @param tenantId  租户ID（从JWT提取，防越租户查询）
     * @param semester  学期筛选（可为null）
     * @param chapterId 章节筛选（可为null，null时返回全部章节）
     * @return AinoteCourseStatVO
     */
    AinoteCourseStatVO getCourseStatistics(String courseId, String tenantId, String semester, String chapterId);

    /**
     * 获取课程高频关键词排行
     *
     * @param courseId 课程ID
     * @param tenantId 租户ID
     * @param topN     返回数量（最大50）
     * @param semester 学期筛选（可为null）
     */
    List<AinoteChapterStatVO.KeywordFreqVO> getTopKeywords(String courseId, String tenantId, int topN, String semester);

    /**
     * 获取素材类型分布统计
     *
     * @param courseId 课程ID
     * @param tenantId 租户ID
     * @param semester 学期筛选（可为null）
     */
    List<AinoteCourseStatVO.MaterialTypeStatVO> getMaterialTypeStats(String courseId, String tenantId, String semester);
}
