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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        Set<String> existingMaterialTaskKeys = buildMaterialTaskKeys(existingTasks);

        int createdCount = 0;
        for (AinoteMaterial material : materials) {
            if (material == null || oConvertUtils.isEmpty(material.getId())) {
                continue;
            }

            String taskType = routeTaskType(material.getFileType());
            if (oConvertUtils.isEmpty(taskType)) {
                continue;
            }

            String taskKey = buildTaskKey(material.getId(), taskType);
            if (existingMaterialTaskKeys.contains(taskKey)) {
                continue;
            }

            aiTaskService.createTask(note.getId(), material.getId(), taskType);
            existingMaterialTaskKeys.add(taskKey);
            createdCount++;
        }

        log.info("Trigger generation finished: noteId={}, createdTaskCount={}", note.getId(), createdCount);
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

    private Set<String> buildMaterialTaskKeys(List<AinoteAiTask> tasks) {
        Set<String> keys = new HashSet<>();
        if (tasks == null || tasks.isEmpty()) {
            return keys;
        }
        for (AinoteAiTask task : tasks) {
            if (task == null || oConvertUtils.isEmpty(task.getMaterialId()) || oConvertUtils.isEmpty(task.getTaskType())) {
                continue;
            }
            keys.add(buildTaskKey(task.getMaterialId(), task.getTaskType()));
        }
        return keys;
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

