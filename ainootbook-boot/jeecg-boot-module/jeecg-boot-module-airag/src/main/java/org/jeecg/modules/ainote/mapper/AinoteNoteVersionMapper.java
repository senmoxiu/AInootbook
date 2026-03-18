package org.jeecg.modules.ainote.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;

/**
 * 笔记版本表 Mapper 接口
 */
@Mapper
public interface AinoteNoteVersionMapper extends BaseMapper<AinoteNoteVersion> {

    /**
     * 分页查询笔记版本历史（不返回内容大字段）
     */
    @Select({
            "<script>",
            "SELECT",
            "  version_number AS version,",
            "  ai_summary AS summary,",
            "  keywords,",
            "  created_at AS createdAt,",
            "  created_by AS createdBy",
            "FROM ainote_note_version",
            "WHERE note_id = #{noteId}",
            "<if test='tenantId != null'>",
            "  AND tenant_id = #{tenantId}",
            "</if>",
            "ORDER BY version_number DESC",
            "</script>"
    })
    IPage<AinoteNoteVersionVO> selectVersionPage(Page<AinoteNoteVersionVO> page,
                                                 @Param("noteId") String noteId,
                                                 @Param("tenantId") Integer tenantId);
}
