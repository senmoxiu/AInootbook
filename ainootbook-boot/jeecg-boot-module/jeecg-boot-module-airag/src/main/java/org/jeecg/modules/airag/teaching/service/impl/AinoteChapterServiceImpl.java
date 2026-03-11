package org.jeecg.modules.airag.teaching.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.airag.teaching.entity.AinoteChapter;
import org.jeecg.modules.airag.teaching.mapper.AinoteChapterMapper;
import org.jeecg.modules.airag.teaching.service.IAinoteChapterService;
import org.jeecg.modules.airag.teaching.vo.AinoteChapterTreeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节表 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteChapterServiceImpl extends ServiceImpl<AinoteChapterMapper, AinoteChapter>
        implements IAinoteChapterService {

    @Override
    public List<AinoteChapterTreeVO> queryTreeByCourse(String courseId, Integer tenantId) {
        // 单次查询全部章节，Java 内存构树（兼容 MySQL 5.7，不依赖递归 CTE）
        List<AinoteChapter> all = baseMapper.selectByCourseForTree(courseId, tenantId);
        return buildTree(all);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addChapter(AinoteChapter chapter) {
        if (StringUtils.hasText(chapter.getParentId())) {
            validateNoCycle(null, chapter.getParentId(), chapter.getCourseId());
        }
        save(chapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editChapter(AinoteChapter chapter) {
        // 编辑时回填 courseId（防止前端未传导致 NPE）
        if (chapter.getCourseId() == null) {
            AinoteChapter existing = getById(chapter.getId());
            if (existing != null) {
                chapter.setCourseId(existing.getCourseId());
            }
        }
        if (StringUtils.hasText(chapter.getParentId())) {
            validateNoCycle(chapter.getId(), chapter.getParentId(), chapter.getCourseId());
        }
        updateById(chapter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWithCheck(String id) {
        // 收集所有子章节（含多级），一并检查和删除
        List<String> toDelete = collectSubtreeIds(id);
        toDelete.add(id);
        // 检查整棵子树的笔记引用
        for (String chapterId : toDelete) {
            long noteCount = baseMapper.countNotesByChapterId(chapterId);
            if (noteCount > 0) {
                AinoteChapter chapter = getById(chapterId);
                String name = chapter != null ? chapter.getChapterName() : chapterId;
                throw new JeecgBootException("章节「" + name + "」已被笔记引用，无法删除");
            }
        }
        removeByIds(toDelete);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteWithCheck(List<String> ids) {
        // 批量收集所有待删除 ID（含子树）
        Set<String> allToDelete = new LinkedHashSet<>();
        for (String id : ids) {
            allToDelete.addAll(collectSubtreeIds(id));
            allToDelete.add(id);
        }
        // 批量检查笔记引用
        for (String chapterId : allToDelete) {
            long noteCount = baseMapper.countNotesByChapterId(chapterId);
            if (noteCount > 0) {
                AinoteChapter chapter = getById(chapterId);
                String name = chapter != null ? chapter.getChapterName() : chapterId;
                throw new JeecgBootException("章节「" + name + "」已被笔记引用，无法删除");
            }
        }
        removeByIds(new ArrayList<>(allToDelete));
    }

    /**
     * 防环校验：O(N) 迭代检测
     * - 不允许 parentId 指向自身
     * - 不允许 parentId 指向自己的后代
     * - 不允许跨课程挂载
     */
    private void validateNoCycle(String chapterId, String newParentId, String courseId) {
        if (courseId == null) {
            throw new JeecgBootException("课程ID不能为空");
        }
        if (chapterId != null && chapterId.equals(newParentId)) {
            throw new JeecgBootException("章节不能将自身设为父节点");
        }
        // 验证父节点存在且属于同一课程
        if (StringUtils.hasText(newParentId)) {
            AinoteChapter parent = getById(newParentId);
            if (parent == null) {
                throw new JeecgBootException("父章节不存在");
            }
            if (!courseId.equals(parent.getCourseId())) {
                throw new JeecgBootException("不允许跨课程挂载章节");
            }
        }
        // 若是编辑操作，检查 newParentId 是否为当前节点的后代
        if (chapterId != null) {
            Set<String> descendants = collectSubtreeIds(chapterId).stream().collect(Collectors.toSet());
            if (descendants.contains(newParentId)) {
                throw new JeecgBootException("不允许将父节点设置为自身的后代节点，会形成环");
            }
        }
    }

    /**
     * 收集指定节点的所有后代 ID（不含自身）
     */
    private List<String> collectSubtreeIds(String rootId) {
        // 查询同课程所有章节，内存过滤后代
        AinoteChapter root = getById(rootId);
        if (root == null) {
            return new ArrayList<>();
        }
        List<AinoteChapter> all = list(new QueryWrapper<AinoteChapter>().eq("course_id", root.getCourseId()));
        // 构建 parentId -> children 映射
        Map<String, List<String>> childMap = new HashMap<>();
        for (AinoteChapter c : all) {
            if (StringUtils.hasText(c.getParentId())) {
                childMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        // BFS 收集后代
        List<String> result = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            List<String> children = childMap.getOrDefault(cur, Collections.emptyList());
            result.addAll(children);
            queue.addAll(children);
        }
        return result;
    }

    /**
     * 将平铺列表构建为树形结构
     */
    private List<AinoteChapterTreeVO> buildTree(List<AinoteChapter> all) {
        // id -> VO 映射
        Map<String, AinoteChapterTreeVO> voMap = new LinkedHashMap<>();
        for (AinoteChapter c : all) {
            AinoteChapterTreeVO vo = toVO(c);
            voMap.put(c.getId(), vo);
        }
        List<AinoteChapterTreeVO> roots = new ArrayList<>();
        for (AinoteChapter c : all) {
            AinoteChapterTreeVO vo = voMap.get(c.getId());
            if (!StringUtils.hasText(c.getParentId()) || !voMap.containsKey(c.getParentId())) {
                // 根节点：parentId 为空，或父节点不在本课程内
                roots.add(vo);
            } else {
                voMap.get(c.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    private AinoteChapterTreeVO toVO(AinoteChapter c) {
        AinoteChapterTreeVO vo = new AinoteChapterTreeVO();
        vo.setId(c.getId());
        vo.setCourseId(c.getCourseId());
        vo.setChapterName(c.getChapterName());
        vo.setChapterOrder(c.getChapterOrder());
        vo.setParentId(c.getParentId());
        vo.setChapterDesc(c.getChapterDesc());
        vo.setStatus(c.getStatus());
        return vo;
    }
}
