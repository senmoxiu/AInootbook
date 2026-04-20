package org.jeecg.modules.ainote.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.mapper.AinoteStatisticsMapper;
import org.jeecg.modules.ainote.service.IAinoteStatisticsService;
import org.jeecg.modules.ainote.vo.AinoteChapterStatVO;
import org.jeecg.modules.ainote.vo.AinoteCourseStatVO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 笔记统计 Service 实现
 * 聚合查询课程笔记数据，关键词词频统计在 Java 层完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteStatisticsServiceImpl implements IAinoteStatisticsService {

    private static final int MAX_TOP_N = 50;

    private final AinoteStatisticsMapper statisticsMapper;

    @Override
    public AinoteCourseStatVO getCourseStatistics(String courseId, String tenantId,
                                                   String semester, String chapterId) {
        if (oConvertUtils.isEmpty(courseId)) {
            throw new JeecgBootException("课程ID不能为空");
        }
        if (oConvertUtils.isEmpty(tenantId)) {
            throw new JeecgBootException("租户ID不能为空");
        }

        AinoteCourseStatVO vo = new AinoteCourseStatVO();
        vo.setCourseId(courseId);

        // 概览指标：四次独立聚合查询（统一 semester 过滤口径）
        vo.setTotalNotes(statisticsMapper.countNotes(courseId, tenantId, semester));
        vo.setCompletedNotes(statisticsMapper.countCompletedNotes(courseId, tenantId, semester));
        vo.setTotalMaterials(statisticsMapper.countMaterials(courseId, tenantId, semester));
        vo.setStudentCount(statisticsMapper.countStudents(courseId, tenantId, semester));

        // 章节明细：按章节分组统计，计算完成率
        List<AinoteChapterStatVO> chapterStats =
                statisticsMapper.selectChapterStats(courseId, tenantId, semester, chapterId);
        for (AinoteChapterStatVO cs : chapterStats) {
            int total = cs.getUploadCount() != null ? cs.getUploadCount() : 0;
            int completed = cs.getCompletedCount() != null ? cs.getCompletedCount() : 0;
            cs.setCompletionRate(total > 0
                    ? Math.round(completed * 1000.0 / total) / 10.0 : 0.0);
        }
        vo.setChapterStats(chapterStats);

        // 素材类型分布（统一 semester 过滤口径）
        vo.setMaterialTypeStats(statisticsMapper.selectMaterialTypeStats(courseId, tenantId, semester));

        // 全课程高频关键词 TOP 20（统一 semester 过滤口径）
        vo.setTopKeywords(getTopKeywords(courseId, tenantId, 20, semester));

        return vo;
    }

    @Override
    public List<AinoteChapterStatVO.KeywordFreqVO> getTopKeywords(String courseId,
                                                                    String tenantId, int topN, String semester) {
        if (oConvertUtils.isEmpty(courseId)) {
            throw new JeecgBootException("课程ID不能为空");
        }
        int limit = Math.min(Math.max(topN, 1), MAX_TOP_N);

        // 从数据库取出所有关键词原始字段，Java 层拆分并累加词频
        List<String> rawKeywordsList = statisticsMapper.selectAllKeywords(courseId, tenantId, semester);
        Map<String, Integer> freqMap = new HashMap<>();
        for (String raw : rawKeywordsList) {
            if (oConvertUtils.isEmpty(raw)) {
                continue;
            }
            for (String kw : raw.split(",")) {
                String trimmed = kw.trim();
                if (!trimmed.isEmpty()) {
                    freqMap.merge(trimmed, 1, Integer::sum);
                }
            }
        }

        // 按频次降序排序，取 TOP N
        return freqMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> {
                    AinoteChapterStatVO.KeywordFreqVO kf = new AinoteChapterStatVO.KeywordFreqVO();
                    kf.setKeyword(entry.getKey());
                    kf.setFrequency(entry.getValue());
                    return kf;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<AinoteCourseStatVO.MaterialTypeStatVO> getMaterialTypeStats(String courseId,
                                                                             String tenantId, String semester) {
        if (oConvertUtils.isEmpty(courseId)) {
            throw new JeecgBootException("课程ID不能为空");
        }
        return statisticsMapper.selectMaterialTypeStats(courseId, tenantId, semester);
    }
}
