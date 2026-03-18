package org.jeecg.modules.ainote.handler;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.modules.ainote.config.AinoteProperties;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.enums.AinoteProcessingType;
import org.jeecg.modules.ainote.service.AinoteAiRuntimeConfigResolver;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.service.MarkdownPrecompileService;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.handler.AIChatHandler;
import org.jeecg.modules.airag.llm.service.IAiragModelService;
import org.jeecg.modules.airag.prompts.service.IAiragPromptsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SummaryTaskHandler 单元测试")
class SummaryTaskHandlerTest {

    @Mock
    private AIChatHandler aiChatHandler;
    @Mock
    private IAinoteNoteService noteService;
    @Mock
    private IAinoteAiTaskService aiTaskService;
    @Spy
    private AinoteProperties ainoteProperties = createProperties();
    @Mock
    private IAiragPromptsService promptsService;
    @Mock
    private IAinoteAiConfigService configService;
    @Mock
    private IAiragModelService airagModelService;
    @Mock
    private AinoteAiRuntimeConfigResolver runtimeConfigResolver;
    @Mock
    private MarkdownPrecompileService markdownPrecompileService;

    @InjectMocks
    private SummaryTaskHandler handler;

    @Test
    @DisplayName("handle: noteContent 优先时应独立调用摘要和关键词 LLM")
    void handle_shouldInvokeSummaryAndKeywordsSeparately_whenNoteContentAvailable() throws Exception {
        AinoteAiTask task = new AinoteAiTask().setNoteId("note-1").setTenantId(1);
        AinoteNote note = new AinoteNote().setId("note-1").setNoteContent("原始笔记正文");

        when(noteService.getById("note-1")).thenReturn(note);
        when(configService.getConfig(1)).thenReturn(baseConfig());
        when(promptsService.getOne(any())).thenReturn(null);
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.SUMMARY, "1")).thenReturn("summary-model");
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.KEYWORDS, "1")).thenReturn("keywords-model");
        when(airagModelService.getById("summary-model")).thenReturn(activeModel("summary-model"));
        when(airagModelService.getById("keywords-model")).thenReturn(activeModel("keywords-model"));
        when(markdownPrecompileService.precompile("原始笔记正文")).thenReturn("<p>预编译内容</p>");
        when(aiChatHandler.completions(eq("summary-model"), anyList())).thenReturn("{\"summary\":\"摘要结果\"}");
        when(aiChatHandler.completions(eq("keywords-model"), anyList()))
                .thenReturn("{\"keywords\":[\"关键字A\",\"关键字B\"]}");
        when(noteService.updateById(any(AinoteNote.class))).thenReturn(true);

        String result = handler.handle(task);

        verify(aiChatHandler, times(1)).completions(eq("summary-model"), anyList());
        verify(aiChatHandler, times(1)).completions(eq("keywords-model"), anyList());
        verify(aiTaskService, never()).getTasksByNoteId("note-1");

        ArgumentCaptor<AinoteNote> noteCaptor = ArgumentCaptor.forClass(AinoteNote.class);
        verify(noteService).updateById(noteCaptor.capture());
        AinoteNote updated = noteCaptor.getValue();
        assertThat(updated.getRenderedContent()).isEqualTo("<p>预编译内容</p>");
        assertThat(updated.getAiSummary()).isEqualTo("摘要结果");
        assertThat(updated.getKeywords()).isEqualTo("关键字A,关键字B");

        JSONObject resultJson = JSONObject.parseObject(result);
        assertThat(resultJson.getString("summary")).isEqualTo("摘要结果");
        assertThat(resultJson.getJSONArray("keywords")).hasSize(2);
        assertThat(resultJson.getJSONObject("resultMeta").getString("sourceType")).isEqualTo("noteContent");
    }

    @Test
    @DisplayName("handle: noteContent 为空时应回退到 aggregateCompletedText")
    void handle_shouldFallbackToAggregatedCompletedText_whenNoteContentBlank() throws Exception {
        AinoteAiTask task = new AinoteAiTask().setNoteId("note-2").setTenantId(1);
        AinoteNote note = new AinoteNote().setId("note-2").setNoteContent("   ");

        when(noteService.getById("note-2")).thenReturn(note);
        when(aiTaskService.getTasksByNoteId("note-2"))
                .thenReturn(List.of(completedTask("asr", "转写文本内容")));
        when(configService.getConfig(1)).thenReturn(baseConfig());
        when(promptsService.getOne(any())).thenReturn(null);
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.SUMMARY, "1")).thenReturn("summary-model");
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.KEYWORDS, "1")).thenReturn("keywords-model");
        when(airagModelService.getById("summary-model")).thenReturn(activeModel("summary-model"));
        when(airagModelService.getById("keywords-model")).thenReturn(activeModel("keywords-model"));
        when(markdownPrecompileService.precompile("   ")).thenReturn("");
        when(aiChatHandler.completions(eq("summary-model"), anyList())).thenReturn("{\"summary\":\"聚合摘要\"}");
        when(aiChatHandler.completions(eq("keywords-model"), anyList()))
                .thenReturn("{\"keywords\":[\"聚合关键词\"]}");
        when(noteService.updateById(any(AinoteNote.class))).thenReturn(true);

        String result = handler.handle(task);

        verify(aiTaskService).getTasksByNoteId("note-2");
        verify(aiChatHandler, times(1)).completions(eq("summary-model"), anyList());
        verify(aiChatHandler, times(1)).completions(eq("keywords-model"), anyList());
        assertThat(JSONObject.parseObject(result).getJSONObject("resultMeta").getString("sourceType"))
                .isEqualTo("aggregateCompletedText");
    }

    @Test
    @DisplayName("handle: 关键词阶段失败时应按 skip 策略保留摘要")
    void handle_shouldSkipKeywordsStageAndPersistSummary_whenKeywordsInvocationFails() throws Exception {
        AinoteAiTask task = new AinoteAiTask().setNoteId("note-3").setTenantId(1);
        AinoteNote note = new AinoteNote().setId("note-3").setNoteContent("摘要源文本");

        when(noteService.getById("note-3")).thenReturn(note);
        when(configService.getConfig(1)).thenReturn(baseConfig());
        when(promptsService.getOne(any())).thenReturn(null);
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.SUMMARY, "1")).thenReturn("summary-model");
        when(runtimeConfigResolver.resolveModelId(AinoteProcessingType.KEYWORDS, "1")).thenReturn("keywords-model");
        when(airagModelService.getById("summary-model")).thenReturn(activeModel("summary-model"));
        when(airagModelService.getById("keywords-model")).thenReturn(activeModel("keywords-model"));
        when(markdownPrecompileService.precompile("摘要源文本")).thenReturn("<p>摘要源文本</p>");
        when(aiChatHandler.completions(eq("summary-model"), anyList())).thenReturn("{\"summary\":\"仅摘要\"}");
        when(aiChatHandler.completions(eq("keywords-model"), anyList()))
                .thenThrow(new RuntimeException("关键词调用失败"));
        when(noteService.updateById(any(AinoteNote.class))).thenReturn(true);

        String result = handler.handle(task);

        ArgumentCaptor<AinoteNote> noteCaptor = ArgumentCaptor.forClass(AinoteNote.class);
        verify(noteService).updateById(noteCaptor.capture());
        AinoteNote updated = noteCaptor.getValue();
        assertThat(updated.getRenderedContent()).isEqualTo("<p>摘要源文本</p>");
        assertThat(updated.getAiSummary()).isEqualTo("仅摘要");
        assertThat(updated.getKeywords()).isEmpty();

        JSONObject resultJson = JSONObject.parseObject(result);
        JSONObject meta = resultJson.getJSONObject("resultMeta");
        assertThat(meta.getBoolean("skipped")).isTrue();
        assertThat(meta.getString("skipStage")).isEqualTo("keywords");
        assertThat(meta.getString("skipStrategy")).isEqualTo("skip");
        assertThat(meta.getString("skipReason")).contains("关键词调用失败");
    }

    private AinoteAiConfig baseConfig() {
        return AinoteAiConfig.defaults()
                .setSummaryPromptKey("note_summary")
                .setKeywordsPromptKey("note_keywords")
                .setMaxSummaryLength(120)
                .setMaxKeywordsCount(5);
    }

    private AinoteAiTask completedTask(String taskType, String text) {
        JSONObject processResult = new JSONObject();
        processResult.put("text", text);
        return new AinoteAiTask()
                .setTaskType(taskType)
                .setTaskStatus(2)
                .setProcessResult(processResult.toJSONString());
    }

    private AiragModel activeModel(String modelId) {
        return new AiragModel().setId(modelId).setActivateFlag(1);
    }

    private static AinoteProperties createProperties() {
        AinoteProperties properties = new AinoteProperties();
        properties.getAi().getSummary().setMaxLength(150);
        properties.getAi().getKeywords().setMaxCount(5);
        return properties;
    }
}
