package org.jeecg.modules.airag.teaching.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.airag.teaching.entity.AinoteChapter;
import org.jeecg.modules.airag.teaching.vo.AinoteChapterTreeVO;

import java.util.List;

/**
 * 章节表 Service 接口
 */
public interface IAinoteChapterService extends IService<AinoteChapter> {

    /**
     * 查询课程下的章节树（Java 内存构树，兼容 MySQL 5.7）
     *
     * @param courseId 课程ID
     * @param tenantId 租户ID
     * @return 树形结构列表
     */
    List<AinoteChapterTreeVO> queryTreeByCourse(String courseId, Integer tenantId);

    /**
     * 新增章节（含防环校验）
     *
     * @param chapter 章节实体
     */
    void addChapter(AinoteChapter chapter);

    /**
     * 编辑章节（含防环校验）
     *
     * @param chapter 章节实体
     */
    void editChapter(AinoteChapter chapter);

    /**
     * 删除章节（检查笔记引用，级联删除子章节）
     *
     * @param id 章节ID
     */
    void deleteWithCheck(String id);

    /**
     * 批量删除章节（检查整棵子树笔记引用）
     *
     * @param ids 章节ID列表
     */
    void batchDeleteWithCheck(List<String> ids);
}
