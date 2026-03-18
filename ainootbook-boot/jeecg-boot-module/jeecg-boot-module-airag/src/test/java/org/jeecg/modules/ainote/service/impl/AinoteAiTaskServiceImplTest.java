package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.mapper.AinoteAiTaskMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AinoteAiTaskServiceImpl 失败策略单元测试")
class AinoteAiTaskServiceImplTest {

    @Mock
    private AinoteAiTaskMapper aiTaskMapper;
    @Mock
    private IAinoteAiConfigService aiConfigService;

    @InjectMocks
    private AinoteAiTaskServiceImpl service;

    @Test
    @DisplayName("failTask: skip 策略应写入 COMPLETED 且 process_result.skipped=true")
    void failTask_shouldCompleteTaskWithSkippedResult_whenFailureModeIsSkip() {
        AinoteAiTask processingTask = processingTask("task-skip", "summary", 1);

        when(aiTaskMapper.selectOne(any())).thenReturn(processingTask, processingTask);
        when(aiConfigService.getConfig(1)).thenReturn(AinoteAiConfig.defaults().setSummaryFailureMode("skip"));
        when(aiTaskMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockTenantSubject(1)) {
            boolean handled = service.failTask("task-skip", "关键词阶段失败");

            assertThat(handled).isTrue();
            verify(aiTaskMapper, times(2)).selectOne(any());

            ArgumentCaptor<UpdateWrapper<AinoteAiTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
            verify(aiTaskMapper).update(isNull(), updateCaptor.capture());
            UpdateWrapper<AinoteAiTask> wrapper = updateCaptor.getValue();
            assertThat(wrapper.getSqlSet()).contains("task_status").contains("process_result")
                    .contains("error_message").contains("next_retry_at");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(2);
            assertThat(stringParams(wrapper))
                    .contains("\"skipped\":true")
                    .contains("\"skipStrategy\":\"skip\"")
                    .contains("\"taskType\":\"summary\"");
        }
    }

    @Test
    @DisplayName("failTask: retry 策略应使用配置 retryLimit 而不是固定 3 次")
    void failTask_shouldUseConfiguredRetryLimitInsteadOfHardcodedMaxRetryCount() {
        AinoteAiTask processingTask = processingTask("task-retry", "summary", 4);

        when(aiTaskMapper.selectOne(any())).thenReturn(processingTask);
        when(aiConfigService.getConfig(1))
                .thenReturn(AinoteAiConfig.defaults().setSummaryFailureMode("retry").setSummaryRetryLimit(5));
        when(aiTaskMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockTenantSubject(1)) {
            boolean handled = service.failTask("task-retry", "暂时失败");

            assertThat(handled).isTrue();

            ArgumentCaptor<UpdateWrapper<AinoteAiTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
            verify(aiTaskMapper).update(isNull(), updateCaptor.capture());
            UpdateWrapper<AinoteAiTask> wrapper = updateCaptor.getValue();
            assertThat(wrapper.getSqlSet()).contains("task_status").contains("retry_count").contains("next_retry_at");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(0, 5);
            assertThat(wrapper.getParamNameValuePairs().values()).anySatisfy(value -> assertThat(value).isInstanceOf(Date.class));
            assertThat(stringParams(wrapper)).contains("暂时失败").doesNotContain("已达最大重试次数");
        }
    }

    @Test
    @DisplayName("failTask: fail_all 策略应直接写入 FAILED 且不安排重试")
    void failTask_shouldFailImmediatelyWithoutRetry_whenFailureModeIsFailAll() {
        AinoteAiTask processingTask = processingTask("task-fail-all", "summary", 2);

        when(aiTaskMapper.selectOne(any())).thenReturn(processingTask);
        when(aiConfigService.getConfig(1)).thenReturn(AinoteAiConfig.defaults().setSummaryFailureMode("fail_all"));
        when(aiTaskMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockTenantSubject(1)) {
            boolean handled = service.failTask("task-fail-all", "致命失败");

            assertThat(handled).isTrue();

            ArgumentCaptor<UpdateWrapper<AinoteAiTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
            verify(aiTaskMapper).update(isNull(), updateCaptor.capture());
            UpdateWrapper<AinoteAiTask> wrapper = updateCaptor.getValue();
            assertThat(wrapper.getSqlSet()).contains("task_status").contains("error_message")
                    .contains("retry_count").contains("next_retry_at");
            assertThat(wrapper.getParamNameValuePairs().values()).contains(2, 3);
            assertThat(stringParams(wrapper)).contains("致命失败");
        }
    }

    private AinoteAiTask processingTask(String taskId, String taskType, int retryCount) {
        return new AinoteAiTask()
                .setId(taskId)
                .setTaskType(taskType)
                .setTaskStatus(1)
                .setRetryCount(retryCount)
                .setTenantId(1)
                .setStartedAt(new Date(System.currentTimeMillis() - 1000L));
    }

    private String stringParams(UpdateWrapper<AinoteAiTask> wrapper) {
        return wrapper.getParamNameValuePairs().values().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining("\n"));
    }

    private MockedStatic<SecurityUtils> mockTenantSubject(int tenantId) {
        Subject subject = mock(Subject.class);
        Session session = mock(Session.class);
        when(subject.getSession(false)).thenReturn(session);
        when(session.getAttribute("tenantId")).thenReturn(String.valueOf(tenantId));

        MockedStatic<SecurityUtils> securityUtils = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtils.when(SecurityUtils::getSubject).thenReturn(subject);
        return securityUtils;
    }
}
