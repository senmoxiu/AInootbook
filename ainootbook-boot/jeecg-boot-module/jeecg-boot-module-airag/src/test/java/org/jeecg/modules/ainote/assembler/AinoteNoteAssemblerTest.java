package org.jeecg.modules.ainote.assembler;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.enums.AinoteProcessingType;
import org.jeecg.modules.ainote.service.AinoteAiRuntimeConfigResolver;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.service.impl.AinoteEmbeddingService;
import org.jeecg.modules.airag.llm.handler.AIChatHandler;
import org.jeecg.modules.airag.prompts.service.IAiragPromptsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AinoteNoteAssembler 单元测试")
class AinoteNoteAssemblerTest {

    @Mock
    private IAinoteNoteService noteService;
    @Mock
    private IAinoteAiTaskService aiTaskService;
    @Mock
    private IAinoteAiConfigService configService;
    @Mock
    private AinoteEmbeddingService embeddingService;
    @Mock
    private AinoteAiRuntimeConfigResolver runtimeConfigResolver;
    @Mock
    private AIChatHandler aiChatHandler;
    @Mock
    private IAiragPromptsService promptsService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private AinoteNoteAssembler assembler;

    @Test
    @DisplayName("assembleIfReady: 整合成功时应覆盖 noteContent 并创建摘要任务")
    void assembleIfReady_shouldOverwriteNoteContentWithIntegratedMarkdown_whenIntegrateSucceeds() {
        AinoteNote note = new AinoteNote()
                .setId("note-1")
                .setTenantId(1)
                .setNoteTitle("课程笔记")
                .setNoteContent("旧内容");

        when(noteService.getByIdWithPermission("note-1")).thenReturn(note);
        when(aiTaskService.getTasksByNoteId("note-1")).thenReturn(List.of(completedSourceTask("asr", "原始文本", 1)));
        when(configService.getConfig(1)).thenReturn(AinoteAiConfig.defaults().setIntegrateFailureMode("skip"));
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.INTEGRATE, "1")).thenReturn("integrate-model");
        when(promptsService.getOne(any())).thenReturn(null);
        when(aiChatHandler.completions(eq("integrate-model"), anyList())).thenReturn("# 课程笔记\n\nLLM 整合结果");
        when(noteService.updateById(any(AinoteNote.class))).thenReturn(true);

        boolean assembled = assembler.assembleIfReady("note-1");

        assertThat(assembled).isTrue();
        ArgumentCaptor<AinoteNote> noteCaptor = ArgumentCaptor.forClass(AinoteNote.class);
        verify(noteService).updateById(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getNoteContent()).isEqualTo("# 课程笔记\n\nLLM 整合结果");
        verify(aiTaskService).createTask("note-1", null, "summary");
    }

    @Test
    @DisplayName("assembleIfReady: 整合失败且 skip 时应回退到 buildMarkdown")
    void assembleIfReady_shouldFallbackToBuildMarkdown_whenIntegrateFailsAndSkipStrategyIsUsed() {
        AinoteNote note = new AinoteNote()
                .setId("note-2")
                .setTenantId(1)
                .setNoteTitle("课堂记录")
                .setNoteContent("旧内容");

        when(noteService.getByIdWithPermission("note-2")).thenReturn(note);
        when(aiTaskService.getTasksByNoteId("note-2")).thenReturn(List.of(completedSourceTask("tika", "原始文本", 1)));
        when(configService.getConfig(1)).thenReturn(AinoteAiConfig.defaults().setIntegrateFailureMode("skip"));
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.INTEGRATE, "1")).thenReturn("integrate-model");
        when(promptsService.getOne(any())).thenReturn(null);
        when(aiChatHandler.completions(eq("integrate-model"), anyList()))
                .thenThrow(new RuntimeException("LLM 整合失败"));
        when(noteService.updateById(any(AinoteNote.class))).thenReturn(true);

        boolean assembled = assembler.assembleIfReady("note-2");

        assertThat(assembled).isTrue();
        ArgumentCaptor<AinoteNote> noteCaptor = ArgumentCaptor.forClass(AinoteNote.class);
        verify(noteService).updateById(noteCaptor.capture());
        assertThat(noteCaptor.getValue().getNoteContent())
                .isEqualTo("# 课堂记录\n\n## Content\n\n原始文本");
        verify(aiTaskService).createTask("note-2", null, "summary");
    }

    @Test
    @DisplayName("assembleIfReady: 整合失败且 fail_all 时应阻断摘要创建")
    void assembleIfReady_shouldBlockSummaryCreation_whenIntegrateFailsAndFailureModeIsFailAll() {
        AinoteNote note = new AinoteNote()
                .setId("note-3")
                .setTenantId(1)
                .setNoteTitle("失败笔记");

        when(noteService.getByIdWithPermission("note-3")).thenReturn(note);
        when(aiTaskService.getTasksByNoteId("note-3")).thenReturn(List.of(completedSourceTask("ocr", "原始文本", 1)));
        when(configService.getConfig(1)).thenReturn(AinoteAiConfig.defaults().setIntegrateFailureMode("fail_all"));
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.INTEGRATE, "1")).thenReturn("integrate-model");
        when(promptsService.getOne(any())).thenReturn(null);
        when(aiChatHandler.completions(eq("integrate-model"), anyList()))
                .thenThrow(new RuntimeException("LLM 整合失败"));

        boolean assembled = assembler.assembleIfReady("note-3");

        assertThat(assembled).isFalse();
        verify(noteService, never()).updateById(any(AinoteNote.class));
        verify(aiTaskService, never()).createTask(any(), any(), any());
    }

    private AinoteAiTask completedSourceTask(String taskType, String text, Integer tenantId) {
        JSONObject processResult = new JSONObject();
        processResult.put("text", text);
        return new AinoteAiTask()
                .setTaskType(taskType)
                .setTaskStatus(2)
                .setTenantId(tenantId)
                .setProcessResult(processResult.toJSONString());
    }
}
