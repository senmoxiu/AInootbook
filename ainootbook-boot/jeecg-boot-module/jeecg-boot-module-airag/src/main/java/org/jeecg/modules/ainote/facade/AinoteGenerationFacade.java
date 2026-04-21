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

import java.util.ArrayList;
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

    public String regenerateNoteContent(AinoteNote note, String additionalContent) {
        if (note == null || oConvertUtils.isEmpty(note.getId())) {
            throw new JeecgBootException("note is required");
        }
        return noteAssembler.regenerateNoteContent(note, additionalContent);
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

        AinoteProgressVO vo = new AinoteProgressVO();

        List<AinoteAiTask> tasks = aiTaskService.getTasksByNoteId(note.getId());
        if (tasks == null || tasks.isEmpty()) {
            vo.setProgress(0);
            vo.setStatus("idle");
            vo.setSteps(buildSteps(List.of(), Map.of(), Map.of()));
            return vo;
        }

        // 推进任务链：检查各阶段是否需要创建下一阶段任务（自愈 + 首次显示）
        boolean advanced = advanceTaskChain(note.getId(), tasks);
        if (advanced) {
            // 重新加载，确保新创建的任务出现在 steps 里
            tasks = aiTaskService.getTasksByNoteId(note.getId());
        }

        // 按 taskType 分组，取优先级最高的状态
        Map<String, String> typeStatus = new LinkedHashMap<>();
        Map<String, String> typeError = new LinkedHashMap<>();
        for (AinoteAiTask task : tasks) {
            String type = task.getTaskType();
            if (type == null) continue;
            String cur = typeStatus.getOrDefault(type, "pending");
            String next = toStepStatus(task.getTaskStatus());
            typeStatus.put(type, mergeStatus(cur, next));
            if ("failed".equals(next) && task.getErrorMessage() != null) {
                typeError.put(type, task.getErrorMessage());
            }
        }

        vo.setSteps(buildSteps(tasks, typeStatus, typeError));

        long total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getTaskStatus() != null && t.getTaskStatus() == STATUS_COMPLETED).count();
        int percent = (int) Math.min(100, Math.max(0, (completed * 100L) / total));
        vo.setProgress(percent);

        boolean hasProcessing = typeStatus.containsValue("processing");
        boolean hasPending = typeStatus.containsValue("pending");
        boolean hasFailed = typeStatus.containsValue("failed");
        boolean allDone = !typeStatus.isEmpty() && typeStatus.values().stream().allMatch("completed"::equals);

        if (allDone) {
            vo.setStatus("completed");
            vo.setProgress(100);
        } else if (hasProcessing || hasPending) {
            vo.setStatus("processing");
        } else if (hasFailed) {
            vo.setStatus("failed");
            vo.setErrorMsg(typeError.values().stream().findFirst().orElse(null));
        } else {
            vo.setStatus("idle");
        }

        return vo;
    }

    /**
     * 推进任务链：
     * - 源任务(asr/tika/ocr/video)全部终止 → 确保 integrate 任务存在
     * - integrate 完成 → 确保 summary 任务存在
     * - summary 完成 → 确保 keywords 任务存在
     * 返回 true 表示有新任务被创建（调用方需重新加载任务列表）
     */
    private boolean advanceTaskChain(String noteId, List<AinoteAiTask> tasks) {
        boolean created = false;

        List<AinoteAiTask> sourceTasks = tasks.stream()
                .filter(t -> t != null && isSourceType(t.getTaskType()))
                .toList();

        if (!sourceTasks.isEmpty() && sourceTasks.stream().allMatch(t ->
                t.getTaskStatus() != null
                && t.getTaskStatus() != STATUS_PENDING
                && t.getTaskStatus() != STATUS_PROCESSING)) {

            // 源任务全部终止，确保 integrate 存在
            boolean integrateExists = tasks.stream().anyMatch(t -> "integrate".equals(normalizeType(t.getTaskType())));
            if (!integrateExists) {
                aiTaskService.createTask(noteId, null, "integrate");
                log.info("[advanceTaskChain] integrate task created: noteId={}", noteId);
                created = true;
            }
        }

        // integrate 完成 → 确保 summary 存在
        boolean integrateCompleted = tasks.stream().anyMatch(t ->
                "integrate".equals(normalizeType(t.getTaskType()))
                && t.getTaskStatus() != null && t.getTaskStatus() == STATUS_COMPLETED);
        if (integrateCompleted) {
            boolean summaryExists = tasks.stream().anyMatch(t -> "summary".equals(normalizeType(t.getTaskType())));
            if (!summaryExists) {
                aiTaskService.createTask(noteId, null, "summary");
                log.info("[advanceTaskChain] summary task created: noteId={}", noteId);
                created = true;
            }
        }

        // summary 完成 → 确保 keywords 存在
        boolean summaryCompleted = tasks.stream().anyMatch(t ->
                "summary".equals(normalizeType(t.getTaskType()))
                && t.getTaskStatus() != null && t.getTaskStatus() == STATUS_COMPLETED);
        if (summaryCompleted) {
            boolean keywordsExists = tasks.stream().anyMatch(t -> "keywords".equals(normalizeType(t.getTaskType())));
            if (!keywordsExists) {
                aiTaskService.createTask(noteId, null, "keywords");
                log.info("[advanceTaskChain] keywords task created: noteId={}", noteId);
                created = true;
            }
        }

        return created;
    }

    private boolean isSourceType(String type) {
        if (type == null) return false;
        String t = type.trim().toLowerCase(Locale.ROOT);
        return "asr".equals(t) || "tika".equals(t) || "ocr".equals(t) || "video".equals(t);
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
    }

    private static final Map<String, String> STEP_LABELS = Map.of(
        "asr", "语音转写",
        "tika", "文档解析",
        "ocr", "图片识别",
        "video", "视频处理",
        "integrate", "笔记整合",
        "summary", "摘要生成",
        "keywords", "关键词提取"
    );

    /** 按固定顺序构建步骤列表（无任务时全部 skipped）*/
    private List<AinoteProgressVO.StepVO> buildSteps(List<AinoteAiTask> tasks) {
        return buildSteps(tasks, Map.of(), Map.of());
    }

    private List<AinoteProgressVO.StepVO> buildSteps(
            List<AinoteAiTask> tasks,
            Map<String, String> typeStatus,
            Map<String, String> typeError) {

        List<String> order = List.of("asr", "tika", "ocr", "video", "integrate", "summary", "keywords");
        List<AinoteProgressVO.StepVO> steps = new ArrayList<>();
        for (String key : order) {
            if (!typeStatus.containsKey(key)) continue;
            String st = typeStatus.get(key);
            AinoteProgressVO.StepVO step = new AinoteProgressVO.StepVO();
            step.setKey(key);
            step.setLabel(STEP_LABELS.getOrDefault(key, key));
            step.setStatus(st);
            step.setErrorMsg(typeError.get(key));
            step.setProgress(switch (st) {
                case "completed" -> 100;
                case "processing" -> 50;
                case "failed" -> 100;   // 进度条满，但 status=failed 显示红色
                default -> 0;           // pending / skipped
            });
            steps.add(step);
        }
        return steps;
    }

    private String toStepStatus(Integer taskStatus) {
        if (taskStatus == null) return "pending";
        return switch (taskStatus) {
            case STATUS_PENDING -> "pending";
            case STATUS_PROCESSING -> "processing";
            case STATUS_COMPLETED -> "completed";
            case STATUS_FAILED -> "failed";
            default -> "pending";
        };
    }

    /** 合并同类型多个任务的状态，取优先级最高的 */
    private String mergeStatus(String a, String b) {
        int pa = statusPriority(a), pb = statusPriority(b);
        return pa >= pb ? a : b;
    }

    private int statusPriority(String s) {
        // completed 优先级最高：同类型只要有一个完成，该步骤就算完成
        // failed 次之：有失败就标失败
        // processing > pending > unknown
        return switch (s) {
            case "completed" -> 5;
            case "failed" -> 4;
            case "processing" -> 3;
            case "pending" -> 2;
            default -> 0;
        };
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

