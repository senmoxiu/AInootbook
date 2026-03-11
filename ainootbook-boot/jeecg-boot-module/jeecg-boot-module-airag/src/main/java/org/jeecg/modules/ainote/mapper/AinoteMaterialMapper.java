package org.jeecg.modules.ainote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.ainote.entity.AinoteMaterial;

import java.util.List;

@Mapper
public interface AinoteMaterialMapper extends BaseMapper<AinoteMaterial> {

    @Select("SELECT * FROM ainote_material WHERE note_id = #{noteId} AND tenant_id = #{tenantId} AND del_flag = 0 ORDER BY create_time ASC")
    List<AinoteMaterial> selectByNoteId(@Param("noteId") String noteId, @Param("tenantId") Integer tenantId);

    @Select("SELECT * FROM ainote_material WHERE note_id = #{noteId} AND file_type = #{fileType} AND tenant_id = #{tenantId} AND del_flag = 0 ORDER BY create_time ASC")
    List<AinoteMaterial> selectByNoteIdAndType(@Param("noteId") String noteId, @Param("fileType") String fileType, @Param("tenantId") Integer tenantId);

    @Update("UPDATE ainote_material SET process_status = #{status}, update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND tenant_id = #{tenantId} AND del_flag = 0")
    int updateProcessStatus(@Param("id") String id, @Param("status") Integer status, @Param("tenantId") Integer tenantId);
}
