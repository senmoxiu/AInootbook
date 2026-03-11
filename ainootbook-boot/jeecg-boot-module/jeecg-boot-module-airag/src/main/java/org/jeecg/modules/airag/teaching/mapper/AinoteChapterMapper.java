package org.jeecg.modules.airag.teaching.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.airag.teaching.entity.AinoteChapter;

import java.util.List;

/**
 * 章节表 Mapper 接口
 */
public interface AinoteChapterMapper extends BaseMapper<AinoteChapter> {

    /**
     * 按课程查询所有章节（用于内存构树）
     */
    List<AinoteChapter> selectByCourseForTree(@Param("courseId") String courseId,
                                              @Param("tenantId") Integer tenantId);

    /**
     * 统计引用该章节的笔记数量（删除前检查）
     */
    @Select("SELECT COUNT(*) FROM ainote_note WHERE chapter_id = #{chapterId}")
    long countNotesByChapterId(@Param("chapterId") String chapterId);
}
