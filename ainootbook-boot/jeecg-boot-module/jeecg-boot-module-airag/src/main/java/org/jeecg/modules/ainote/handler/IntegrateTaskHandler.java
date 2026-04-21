package org.jeecg.modules.ainote.handler;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.assembler.AinoteNoteAssembler;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.task.AinoteAiTaskWorker;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记整合任务处理器：聚合源任务文本 → LLM 整合 → 写入 noteContent → 创建 summary 任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrateTaskHandler implements AinoteAiTaskWorker.AinoteAiTaskHandler {

    private static final String TASK_TYPE = "integrate";
    private static final String TASK_TYPE_SUMMARY = "summary";
    private static final int STATUS_COMPLETED = 2;

    private final IAinoteNoteService noteService;
    private final IAinoteAiTaskService aiTaskService;
    private final AinoteNoteAssembler noteAssembler;

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(AinoteAiTask task) throws Exception {
        if (task == null) throw new JeecgBootException("任务不能为空");
        String noteId = task.getNoteId();
        if (oConvertUtils.isEmpty(noteId)) throw new JeecgBootException("noteId不能为空");

        AinoteNote note = noteService.getById(noteId);
        if (note == null) throw new JeecgBootException("笔记不存在: noteId=" + noteId);

        List<AinoteAiTask> tasks = aiTaskService.getTasksByNoteId(noteId);

        // 执行整合（LLM 调用 + 写入 noteContent）
        String markdown = noteAssembler.integrateFromTasks(note, tasks);

        // 整合完成后创建 summary 任务（异步线程，用 createTaskBySystem 跳过 Shiro）
        boolean summaryExists = tasks.stream().anyMatch(t ->
                t != null && TASK_TYPE_SUMMARY.equals(normalizeType(t.getTaskType())));
        if (!summaryExists) {
            aiTaskService.createTaskBySystem(noteId, null, TASK_TYPE_SUMMARY,
                    note.getTenantId(), note.getCreateBy());
            log.info("Summary task created after integrate: noteId={}", noteId);
        }

        JSONObject result = new JSONObject();
        result.put("markdownLength", markdown != null ? markdown.length() : 0);
        return result.toJSONString();
    }

    private String normalizeType(String t) {
        return t == null ? "" : t.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
