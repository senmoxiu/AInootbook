package org.jeecg.modules.ainote.facade;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.assembler.AinoteNoteAssembler;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.vo.AinoteProgressVO;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generation orchestration facade.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteGenerationFacade {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private static final String FILE_TYPE_AUDIO = "audio";
    private static final String FILE_TYPE_DOCUMENT = "document";
    private static final String FILE_TYPE_IMAGE = "image";
    private static final String FILE_TYPE_VIDEO = "video";

    private static final String TASK_TYPE_ASR = "asr";
    private static final String TASK_TYPE_TIKA = "tika";
    private static final String TASK_TYPE_OCR = "ocr";
    private static final String TASK_TYPE_VIDEO = "video";
    private static final String TASK_TYPE_SUMMARY = "summary";

    private final IAinoteNoteService noteService;
    private final IAinoteMaterialService materialService;
    private final IAinoteAiTaskService aiTaskService;
    private final AinoteNoteAssembler noteAssembler;

    public void triggerGeneration(String noteId) {
        triggerGeneration(noteId, null);
    }

    public void triggerGeneration(String noteId, String knowledgeId) {
        AinoteNote note = requireNoteWithPermission(noteId);
        Integer tenantId = materialService.getRequiredTenantId();

        List<AinoteMaterial> materials = materialService.listByNoteId(note.getId(), tenantId);
        if (materials == null || materials.isEmpty()) {
            throw new JeecgBootException("Current note has no processable materials");
        }

        List<AinoteAiTask> existingTasks = aiTaskService.getTasksByNoteId(note.getId());
        Map<String, AinoteAiTask> latestMaterialTasks = buildLatestMaterialTasks(existingTasks);
        AinoteAiTask latestSummaryTask = findLatestSummaryTask(existingTasks);

        int createdCount = 0;
        int restartedCount = 0;
        for (AinoteMaterial material : materials) {
            if (material == null || oConvertUtils.isEmpty(material.getId())) {
                continue;
            }

            String taskType = routeTaskType(material.getFileType());
            if (oConvertUtils.isEmpty(taskType)) {
                continue;
            }

            String taskKey = buildTaskKey(material.getId(), taskType);
            AinoteAiTask latestTask = latestMaterialTasks.get(taskKey);
            if (latestTask != null) {
                Integer taskStatus = latestTask.getTaskStatus();
                if (taskStatus != null && (taskStatus == STATUS_PENDING || taskStatus == STATUS_PROCESSING)) {
                    continue;
                }
                if (shouldRetrySourceTask(latestTask) && resetTaskForRetry(latestTask.getId(), tenantId)) {
                    restartedCount++;
                    continue;
                }
                if (taskStatus != null && taskStatus == STATUS_COMPLETED) {
                    continue;
                }
            }

            try {
                aiTaskService.createTask(note.getId(), material.getId(), taskType);
                createdCount++;
            } catch (JeecgBootException ex) {
                if (isDuplicateActiveTaskError(ex)) {
                    log.info("Skip duplicate active source task: noteId={}, materialId={}, taskType={}",
                            note.getId(), material.getId(), taskType);
                    continue;
                }
                throw ex;
            }
        }

        if (createdCount == 0 && restartedCount == 0
                && latestSummaryTask != null
                && latestSummaryTask.getTaskStatus() != null
                && latestSummaryTask.getTaskStatus() == STATUS_FAILED
                && hasCompletedSourceTask(existingTasks)
                && resetTaskForRetry(latestSummaryTask.getId(), tenantId)) {
            restartedCount++;
        }

        log.info("Trigger generation finished: noteId={}, createdTaskCount={}, restartedTaskCount={}",
                note.getId(), createdCount, restartedCount);
        noteAssembler.assembleIfReady(note.getId(), knowledgeId);
    }

    public void cancelGeneration(String noteId) {
        AinoteNote note = requireNoteWithPermission(noteId);
        Integer tenantId = materialService.getRequiredTenantId();

        UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("note_id", note.getId());
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("task_status", STATUS_PENDING);
        wrapper.set("task_status", STATUS_FAILED);
        wrapper.set("error_message", "canceled");

        boolean canceled = aiTaskService.update(null, wrapper);
        log.info("Cancel generation finished: noteId={}, hasPendingCanceled={}", note.getId(), canceled);
    }

    public AinoteProgressVO getProgress(String noteId) {
        return getProgress(noteId, null);
    }

    /**
     * WR-03: 返回结构化进度信息，与前端契约对齐
     */
    public AinoteProgressVO getProgress(String noteId, String knowledgeId) {
        AinoteNote note = requireNoteWithPermission(noteId);
        noteAssembler.assembleIfReady(note.getId(), knowledgeId);

        AinoteProgressVO vo = new AinoteProgressVO();

        long total = aiTaskService.countTotalTasks(note.getId());
        if (total <= 0) {
            vo.setProgress(0);
            vo.setStatus("idle");
            return vo;
        }

        long completed = aiTaskService.countCompletedTasks(note.getId());
        int percent = (int) Math.min(100, Math.max(0, (completed * 100L) / total));
        vo.setProgress(percent);

        // 判断整体状态
        List<AinoteAiTask> tasks = aiTaskService.getTasksByNoteId(note.getId());
        boolean hasProcessing = false;
        boolean hasPending = false;
        String lastError = null;
        boolean allCompleted = true;

        for (AinoteAiTask task : tasks) {
            Integer st = task.getTaskStatus();
            if (st == null) {
                allCompleted = false;
                continue;
            }
            if (st == STATUS_PENDING) {
                hasPending = true;
                allCompleted = false;
            } else if (st == STATUS_PROCESSING) {
                hasProcessing = true;
                allCompleted = false;
            } else if (st == STATUS_FAILED) {
                lastError = task.getErrorMessage();
                allCompleted = false;
            }
            // STATUS_COMPLETED 不影响 allCompleted
        }

        if (allCompleted && !tasks.isEmpty()) {
            vo.setStatus("completed");
            vo.setProgress(100);
        } else if (lastError != null && !hasProcessing && !hasPending) {
            vo.setStatus("failed");
            vo.setErrorMsg(lastError);
        } else if (hasProcessing || hasPending) {
            vo.setStatus("processing");
        } else {
            vo.setStatus("idle");
        }

        return vo;
    }

    private AinoteNote requireNoteWithPermission(String noteId) {
        if (oConvertUtils.isEmpty(noteId)) {
            throw new JeecgBootException("noteId is required");
        }
        AinoteNote note = noteService.getByIdWithPermission(noteId);
        if (note == null) {
            throw new JeecgBootException("Note does not exist or permission denied");
        }
        return note;
    }

    private Map<String, AinoteAiTask> buildLatestMaterialTasks(List<AinoteAiTask> tasks) {
        Map<String, AinoteAiTask> taskMap = new LinkedHashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return taskMap;
        }
        for (AinoteAiTask task : tasks) {
            if (task == null || oConvertUtils.isEmpty(task.getMaterialId()) || oConvertUtils.isEmpty(task.getTaskType())) {
                continue;
            }
            taskMap.put(buildTaskKey(task.getMaterialId(), task.getTaskType()), task);
        }
        return taskMap;
    }

    private AinoteAiTask findLatestSummaryTask(List<AinoteAiTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }
        AinoteAiTask latestSummaryTask = null;
        for (AinoteAiTask task : tasks) {
            if (task == null || !TASK_TYPE_SUMMARY.equals(normalizeTaskType(task.getTaskType()))) {
                continue;
            }
            latestSummaryTask = task;
        }
        return latestSummaryTask;
    }

    private boolean shouldRetrySourceTask(AinoteAiTask task) {
        if (task == null || !isSourceTask(task.getTaskType())) {
            return false;
        }
        Integer status = task.getTaskStatus();
        if (status == null) {
            return true;
        }
        if (status == STATUS_FAILED) {
            return true;
        }
        return status == STATUS_COMPLETED && !hasProcessResult(task);
    }

    private boolean hasCompletedSourceTask(List<AinoteAiTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        for (AinoteAiTask task : tasks) {
            if (task == null || !isSourceTask(task.getTaskType())) {
                continue;
            }
            Integer status = task.getTaskStatus();
            if (status != null && status == STATUS_COMPLETED && hasProcessResult(task)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSourceTask(String taskType) {
        String normalized = normalizeTaskType(taskType);
        return TASK_TYPE_ASR.equals(normalized)
                || TASK_TYPE_TIKA.equals(normalized)
                || TASK_TYPE_OCR.equals(normalized)
                || TASK_TYPE_VIDEO.equals(normalized);
    }

    private boolean hasProcessResult(AinoteAiTask task) {
        return task != null && oConvertUtils.isNotEmpty(task.getProcessResult()) && !task.getProcessResult().isBlank();
    }

    private boolean resetTaskForRetry(String taskId, Integer tenantId) {
        if (oConvertUtils.isEmpty(taskId)) {
            return false;
        }
        UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId);
        wrapper.eq("tenant_id", tenantId);
        wrapper.eq("task_status", STATUS_FAILED);
        wrapper.set("task_status", STATUS_PENDING);
        wrapper.set("error_message", null);
        wrapper.set("process_result", null);
        wrapper.set("retry_count", 0);
        wrapper.set("started_at", null);
        wrapper.set("completed_at", null);
        wrapper.set("duration", null);
        wrapper.set("next_retry_at", null);
        wrapper.set("worker_id", null);
        wrapper.set("vendor_task_id", null);
        wrapper.set("update_time", new Date());
        boolean reset = aiTaskService.update(null, wrapper);
        if (reset) {
            log.info("Reset failed AI task for retry: taskId={}, tenantId={}", taskId, tenantId);
        }
        return reset;
    }

    private boolean isDuplicateActiveTaskError(JeecgBootException ex) {
        if (ex == null || oConvertUtils.isEmpty(ex.getMessage())) {
            return false;
        }
        return ex.getMessage().contains("已有相同类型的任务在处理中");
    }

    private String normalizeTaskType(String taskType) {
        if (oConvertUtils.isEmpty(taskType)) {
            return "";
        }
        return taskType.trim().toLowerCase(Locale.ROOT);
    }

    private String routeTaskType(String fileType) {
        if (oConvertUtils.isEmpty(fileType)) {
            throw new JeecgBootException("fileType is required");
        }
        String normalized = fileType.trim().toLowerCase(Locale.ROOT);
        if (FILE_TYPE_AUDIO.equals(normalized)) {
            return TASK_TYPE_ASR;
        }
        if (FILE_TYPE_DOCUMENT.equals(normalized)) {
            return TASK_TYPE_TIKA;
        }
        if (FILE_TYPE_IMAGE.equals(normalized)) {
            return TASK_TYPE_OCR;
        }
        if (FILE_TYPE_VIDEO.equals(normalized)) {
            return TASK_TYPE_VIDEO;
        }
        throw new JeecgBootException("Unsupported fileType: " + fileType);
    }

    private String buildTaskKey(String materialId, String taskType) {
        return materialId + "#" + taskType.toLowerCase(Locale.ROOT);
    }
}

