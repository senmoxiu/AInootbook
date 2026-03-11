package org.jeecg.modules.ainote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.ainote.entity.AinoteAiTask;

import java.util.List;

/**
 * AI任务表 Mapper 接口
 */
public interface AinoteAiTaskMapper extends BaseMapper<AinoteAiTask> {

    /**
     * 查询待处理任务（租户隔离，包含重试时间过滤）
     */
    @Select("SELECT * FROM ainote_ai_task WHERE task_status = 0 AND tenant_id = #{tenantId} " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY create_time ASC LIMIT #{limit}")
    List<AinoteAiTask> selectPendingTasks(@Param("tenantId") Integer tenantId, @Param("limit") Integer limit);

    /**
     * 条件更新任务状态（乐观锁，包含重试时间检查）
     */
    @Update("UPDATE ainote_ai_task SET task_status = #{newStatus}, worker_id = #{workerId} " +
            "WHERE id = #{id} AND task_status = #{oldStatus} AND tenant_id = #{tenantId} " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW())")
    int updateTaskStatusWithLock(@Param("id") String id,
                                  @Param("tenantId") Integer tenantId,
                                  @Param("oldStatus") Integer oldStatus,
                                  @Param("newStatus") Integer newStatus,
                                  @Param("workerId") String workerId);

    /**
     * 查询笔记的所有任务（租户隔离）
     */
    @Select("SELECT * FROM ainote_ai_task WHERE note_id = #{noteId} AND tenant_id = #{tenantId} ORDER BY create_time ASC")
    List<AinoteAiTask> selectByNoteId(@Param("noteId") String noteId, @Param("tenantId") Integer tenantId);

    /**
     * 查询素材的所有任务（租户隔离）
     */
    @Select("SELECT * FROM ainote_ai_task WHERE material_id = #{materialId} AND tenant_id = #{tenantId} ORDER BY create_time ASC")
    List<AinoteAiTask> selectByMaterialId(@Param("materialId") String materialId, @Param("tenantId") Integer tenantId);

    /**
     * 统计任务状态数量（租户隔离）
     */
    @Select("SELECT COUNT(1) FROM ainote_ai_task WHERE note_id = #{noteId} AND task_status = #{taskStatus} AND tenant_id = #{tenantId}")
    Long countByStatus(@Param("noteId") String noteId,
                       @Param("taskStatus") Integer taskStatus,
                       @Param("tenantId") Integer tenantId);
}
