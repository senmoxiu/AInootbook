package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.ainote.entity.AinoteAiTask;

import java.util.List;

/**
 * AI任务 Service 接口
 */
public interface IAinoteAiTaskService extends IService<AinoteAiTask> {

    /**
     * 创建任务（自动填充 tenantId）
     */
    AinoteAiTask createTask(String noteId, String materialId, String taskType);

    /**
     * 抢占任务（PENDING -> PROCESSING）
     */
    boolean claimTask(String taskId);

    /**
     * 完成任务（更新状态、结果、时长）
     */
    boolean completeTask(String taskId, String processResult);

    /**
     * 失败任务（更新状态、错误信息、计算重试时间）
     */
    boolean failTask(String taskId, String errorMessage);

    /**
     * 查询笔记的所有任务（租户隔离）
     */
    List<AinoteAiTask> getTasksByNoteId(String noteId);

    /**
     * 查询素材的所有任务（租户隔离）
     */
    List<AinoteAiTask> getTasksByMaterialId(String materialId);

    /**
     * 统计已完成任务数（task_status=2）
     */
    long countCompletedTasks(String noteId);

    /**
     * 统计总任务数
     */
    long countTotalTasks(String noteId);
}
