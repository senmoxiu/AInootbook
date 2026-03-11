package org.jeecg.modules.ainote.assembler;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.service.impl.AinoteEmbeddingService;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Assemble note content and trigger downstream summary/vectorization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteNoteAssembler {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private static final int NOTE_STATUS_COMPLETED = 2;

    private static final String TASK_TYPE_ASR = "asr";
    private static final String TASK_TYPE_TIKA = "tika";
    private static final String TASK_TYPE_OCR = "ocr";
    private static final String TASK_TYPE_VIDEO = "video";
    private static final String TASK_TYPE_SUMMARY = "summary";

    private static final String EMBED_LOCK_PREFIX = "ainote:embed:";
    private static final Duration EMBED_LOCK_TTL = Duration.ofSeconds(120);

    private final IAinoteNoteService noteService;
    private final IAinoteAiTaskService aiTaskService;
    private final IAinoteAiConfigService configService;
    private final AinoteEmbeddingService embeddingService;
    private final StringRedisTemplate stringRedisTemplate;
    private final Environment environment;

    public boolean assembleIfReady(String noteId) {
        return assembleIfReady(noteId, null);
    }

    public boolean assembleIfReady(String noteId, String knowledgeId) {
        AinoteNote note = requireNoteWithPermission(noteId);
        List<AinoteAiTask> tasks = aiTaskService.getTasksByNoteId(note.getId());
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }

        List<AinoteAiTask> sourceTasks = extractSourceTasks(tasks);
        if (sourceTasks.isEmpty()) {
            return false;
        }

        if (!allSourceTasksTerminal(sourceTasks)) {
            return false;
        }

        String aggregatedText = aggregateText(sourceTasks);
        if (oConvertUtils.isEmpty(aggregatedText)) {
            log.warn("Skip assembling: no completed asr/tika/ocr text, noteId={}", note.getId());
            return false;
        }

        String markdown = buildMarkdown(note.getNoteTitle(), aggregatedText);
        upsertNoteContent(note, markdown);

        AinoteAiTask summaryTask = findLatestSummaryTask(tasks);
        if (summaryTask == null) {
            aiTaskService.createTask(note.getId(), null, TASK_TYPE_SUMMARY);
            log.info("Summary task created: noteId={}", note.getId());
            return true;
        }

        Integer summaryStatus = summaryTask.getTaskStatus();
        if (summaryStatus == null || summaryStatus == STATUS_PENDING || summaryStatus == STATUS_PROCESSING) {
            return true;
        }
        if (summaryStatus == STATUS_FAILED) {
            log.warn("Summary task failed: noteId={}, taskId={}, error={}",
                    note.getId(), summaryTask.getId(), summaryTask.getErrorMessage());
            return false;
        }

        if (summaryStatus == STATUS_COMPLETED) {
            String resolvedKnowledgeId = resolveKnowledgeId(knowledgeId);
            if (oConvertUtils.isEmpty(resolvedKnowledgeId)) {
                throw new JeecgBootException("knowledgeId is required for vectorization");
            }
            if (note.getNoteStatus() != null && note.getNoteStatus() == NOTE_STATUS_COMPLETED) {
                return true;
            }
            vectorizeAndFinalize(note.getId(), resolvedKnowledgeId);
            return true;
        }
        return false;
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

    private List<AinoteAiTask> extractSourceTasks(List<AinoteAiTask> tasks) {
        List<AinoteAiTask> sourceTasks = new ArrayList<>();
        for (AinoteAiTask task : tasks) {
            if (task == null) {
                continue;
            }
            String taskType = normalizeTaskType(task.getTaskType());
            if (TASK_TYPE_ASR.equals(taskType) || TASK_TYPE_TIKA.equals(taskType) || TASK_TYPE_OCR.equals(taskType) || TASK_TYPE_VIDEO.equals(taskType)) {
                sourceTasks.add(task);
            }
        }
        return sourceTasks;
    }

    private boolean allSourceTasksTerminal(List<AinoteAiTask> sourceTasks) {
        for (AinoteAiTask task : sourceTasks) {
            Integer status = task.getTaskStatus();
            if (status == null || status == STATUS_PENDING || status == STATUS_PROCESSING) {
                return false;
            }
        }
        return true;
    }

    private String aggregateText(List<AinoteAiTask> sourceTasks) {
        StringBuilder sb = new StringBuilder();
        for (AinoteAiTask task : sourceTasks) {
            if (task.getTaskStatus() == null || task.getTaskStatus() != STATUS_COMPLETED) {
                continue;
            }
            String text = extractTextFromProcessResult(task.getProcessResult());
            text = normalizeText(text);
            if (oConvertUtils.isEmpty(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    private String extractTextFromProcessResult(String processResult) {
        if (oConvertUtils.isEmpty(processResult)) {
            return "";
        }
        String trimmed = processResult.trim();
        try {
            JSONObject obj = JSONObject.parseObject(trimmed);
            if (obj == null) {
                return "";
            }

            String directText = obj.getString("text");
            if (oConvertUtils.isNotEmpty(directText)) {
                return directText;
            }

            JSONArray segments = obj.getJSONArray("segments");
            if (segments == null) {
                segments = obj.getJSONArray("sentences");
            }
            if (segments == null || segments.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < segments.size(); i++) {
                Object segment = segments.get(i);
                String segmentText = "";
                if (segment instanceof JSONObject) {
                    segmentText = ((JSONObject) segment).getString("text");
                } else if (segment instanceof String) {
                    segmentText = (String) segment;
                }
                if (oConvertUtils.isEmpty(segmentText)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(segmentText);
            }
            return sb.toString();
        } catch (Exception ignore) {
            return trimmed;
        }
    }

    private String buildMarkdown(String noteTitle, String aggregatedText) {
        String safeTitle = oConvertUtils.isEmpty(noteTitle) ? "AI Note" : noteTitle.trim();
        String normalizedText = normalizeText(aggregatedText);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(safeTitle).append("\n\n");
        markdown.append("## Content\n\n");
        markdown.append(normalizedText);
        return markdown.toString().trim();
    }

    private void upsertNoteContent(AinoteNote note, String markdown) {
        String currentContent = oConvertUtils.getString(note.getNoteContent(), "");
        if (currentContent.equals(markdown)) {
            return;
        }

        AinoteNote update = new AinoteNote();
        update.setId(note.getId());
        update.setNoteContent(markdown);
        if (!noteService.updateById(update)) {
            throw new JeecgBootException("Update note content failed: noteId=" + note.getId());
        }
    }

    private AinoteAiTask findLatestSummaryTask(List<AinoteAiTask> tasks) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            AinoteAiTask task = tasks.get(i);
            if (task == null) {
                continue;
            }
            if (TASK_TYPE_SUMMARY.equals(normalizeTaskType(task.getTaskType()))) {
                return task;
            }
        }
        return null;
    }

    private void vectorizeAndFinalize(String noteId, String knowledgeId) {
        String lockKey = EMBED_LOCK_PREFIX + noteId;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, EMBED_LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("向量化锁被占用，跳过: noteId={}", noteId);
            return;
        }
        try {
            AinoteNote latestNote = noteService.getByIdWithPermission(noteId);
            if (latestNote == null) {
                throw new JeecgBootException("Note does not exist or permission denied");
            }
            embeddingService.deleteNoteEmbedding(noteId, knowledgeId);
            embeddingService.embedNote(latestNote, knowledgeId);

            AinoteNote update = new AinoteNote();
            update.setId(noteId);
            update.setNoteStatus(NOTE_STATUS_COMPLETED);
            if (!noteService.updateById(update)) {
                throw new JeecgBootException("Update note status failed: noteId=" + noteId);
            }
            log.info("Vectorization completed: noteId={}, knowledgeId={}", noteId, knowledgeId);
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private void releaseLock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    private String resolveKnowledgeId(String providedKnowledgeId) {
        try {
            AinoteAiConfig config = configService.getConfig(0);
            String configKnowledgeId = trimToNull(config == null ? null : config.getKnowledgeId());
            if (configKnowledgeId != null) {
                return configKnowledgeId;
            }
        } catch (Exception e) {
            log.warn("读取AI配置知识库ID失败，回退到环境变量: {}", e.getMessage());
        }
        if (oConvertUtils.isNotEmpty(providedKnowledgeId)) {
            return providedKnowledgeId.trim();
        }
        String[] keys = {
                "ainote.ai.knowledge-id",
                "ainote.ai.embedding.knowledge-id",
                "ainote.embedding.knowledge-id"
        };
        for (String key : keys) {
            String value = trimToNull(environment.getProperty(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeTaskType(String taskType) {
        if (oConvertUtils.isEmpty(taskType)) {
            return "";
        }
        return taskType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String text) {
        if (oConvertUtils.isEmpty(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");

        StringBuilder sb = new StringBuilder();
        for (String line : normalized.split("\n")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim().replaceAll("\\s{2,}", " ");
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private String trimToNull(String value) {
        if (oConvertUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
