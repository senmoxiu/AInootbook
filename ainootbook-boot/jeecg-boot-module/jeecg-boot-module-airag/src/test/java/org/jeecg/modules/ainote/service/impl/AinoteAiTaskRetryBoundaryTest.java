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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AinoteAiTaskServiceImpl 重试边界单元测试")
class AinoteAiTaskRetryBoundaryTest {

    @Mock
    private AinoteAiTaskMapper aiTaskMapper;
    @Mock
    private IAinoteAiConfigService aiConfigService;

    @InjectMocks
    private AinoteAiTaskServiceImpl service;

    @Test
    @DisplayName("failTask: currentRetry 已到上限时应将 retry_count 钳制到 retryLimit")
    void failTask_shouldClampRetryCountToRetryLimit_whenCurrentRetryAlreadyAtLimit() {
        AinoteAiTask processingTask = processingTask("task-boundary-max", 5);

        when(aiTaskMapper.selectOne(any())).thenReturn(processingTask);
        when(aiConfigService.getConfig(1))
                .thenReturn(AinoteAiConfig.defaults().setSummaryFailureMode("retry").setSummaryRetryLimit(5));
        when(aiTaskMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockTenantSubject(1)) {
            boolean handled = service.failTask("task-boundary-max", "边界失败");

            assertThat(handled).isTrue();

            ArgumentCaptor<UpdateWrapper<AinoteAiTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
            verify(aiTaskMapper).update(isNull(), updateCaptor.capture());
            assertThat(updateCaptor.getValue().getParamNameValuePairs().values()).contains(3, 5);
            assertThat(updateCaptor.getValue().getSqlSet()).contains("retry_count").contains("next_retry_at");
        }
    }

    @Test
    @DisplayName("failTask: currentRetry 比上限少 1 时应使用 Math.min 计算 nextRetry 并保持待重试")
    void failTask_shouldSchedulePendingRetryWithBoundaryValue_whenCurrentRetryIsOneBelowLimit() {
        AinoteAiTask processingTask = processingTask("task-boundary-pending", 4);

        when(aiTaskMapper.selectOne(any())).thenReturn(processingTask);
        when(aiConfigService.getConfig(1))
                .thenReturn(AinoteAiConfig.defaults().setSummaryFailureMode("retry").setSummaryRetryLimit(5));
        when(aiTaskMapper.update(isNull(), any(UpdateWrapper.class))).thenReturn(1);

        try (MockedStatic<SecurityUtils> securityUtils = mockTenantSubject(1)) {
            boolean handled = service.failTask("task-boundary-pending", "等待重试");

            assertThat(handled).isTrue();

            ArgumentCaptor<UpdateWrapper<AinoteAiTask>> updateCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
            verify(aiTaskMapper).update(isNull(), updateCaptor.capture());
            UpdateWrapper<AinoteAiTask> wrapper = updateCaptor.getValue();
            assertThat(wrapper.getParamNameValuePairs().values()).contains(0, 5);
            assertThat(wrapper.getParamNameValuePairs().values()).anySatisfy(value -> assertThat(value).isInstanceOf(Date.class));
            assertThat(wrapper.getSqlSet()).contains("retry_count").contains("next_retry_at");
        }
    }

    private AinoteAiTask processingTask(String taskId, int retryCount) {
        return new AinoteAiTask()
                .setId(taskId)
                .setTaskType("summary")
                .setTaskStatus(1)
                .setRetryCount(retryCount)
                .setTenantId(1);
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
