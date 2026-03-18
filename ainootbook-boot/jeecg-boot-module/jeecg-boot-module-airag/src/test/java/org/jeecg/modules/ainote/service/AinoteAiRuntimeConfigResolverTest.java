package org.jeecg.modules.ainote.service;

import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.enums.AinoteProcessingType;
import org.jeecg.modules.ainote.service.impl.AinoteAiConfigServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AinoteAiRuntimeConfigResolver 单元测试")
class AinoteAiRuntimeConfigResolverTest {

    @Mock
    private AinoteAiConfigServiceImpl aiConfigService;

    private MockEnvironment environment;
    private AinoteAiRuntimeConfigResolver resolver;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        resolver = new AinoteAiRuntimeConfigResolver(aiConfigService, environment);
    }

    @Test
    @DisplayName("resolveModelId: 应优先使用租户配置覆盖环境变量")
    void resolveModelId_shouldPreferTenantConfigOverEnvironment_forIndependentTypes() {
        environment.setProperty("AINOTE_ASR_MODEL_ID", "env-asr");
        environment.setProperty("AINOTE_OCR_MODEL_ID", "env-ocr");
        environment.setProperty("AINOTE_VIDEO_MODEL_ID", "env-video");
        environment.setProperty("AINOTE_SUMMARY_MODEL_ID", "env-summary");
        environment.setProperty("AINOTE_KEYWORDS_MODEL_ID", "env-keywords");

        AinoteAiConfig tenantConfig = new AinoteAiConfig()
                .setAsrModelId("tenant-asr")
                .setOcrModelId("tenant-ocr")
                .setVideoModelId("tenant-video")
                .setSummaryModelId("tenant-summary")
                .setKeywordsModelId("tenant-keywords");
        when(aiConfigService.getConfig(100)).thenReturn(tenantConfig);

        assertThat(resolver.resolveModelId(AinoteProcessingType.ASR, "100")).isEqualTo("tenant-asr");
        assertThat(resolver.resolveModelId(AinoteProcessingType.OCR, "100")).isEqualTo("tenant-ocr");
        assertThat(resolver.resolveModelId(AinoteProcessingType.VIDEO, "100")).isEqualTo("tenant-video");
        assertThat(resolver.resolveModelId(AinoteProcessingType.SUMMARY, "100")).isEqualTo("tenant-summary");
        assertThat(resolver.resolveModelId(AinoteProcessingType.KEYWORDS, "100")).isEqualTo("tenant-keywords");
    }

    @Test
    @DisplayName("resolveModelId: 缺少租户配置时应回退到环境变量")
    void resolveModelId_shouldFallbackToEnvironment_whenTenantConfigMissing() {
        environment.setProperty("AINOTE_ASR_MODEL_ID", "env-asr");
        environment.setProperty("AINOTE_OCR_MODEL_ID", "env-ocr");
        environment.setProperty("AINOTE_VIDEO_MODEL_ID", "env-video");
        environment.setProperty("AINOTE_SUMMARY_MODEL_ID", "env-summary");
        environment.setProperty("AINOTE_KEYWORDS_MODEL_ID", "env-keywords");
        when(aiConfigService.getConfig(200)).thenReturn(new AinoteAiConfig());

        assertThat(resolver.resolveModelId(AinoteProcessingType.ASR, "200")).isEqualTo("env-asr");
        assertThat(resolver.resolveModelId(AinoteProcessingType.OCR, "200")).isEqualTo("env-ocr");
        assertThat(resolver.resolveModelId(AinoteProcessingType.VIDEO, "200")).isEqualTo("env-video");
        assertThat(resolver.resolveModelId(AinoteProcessingType.SUMMARY, "200")).isEqualTo("env-summary");
        assertThat(resolver.resolveModelId(AinoteProcessingType.KEYWORDS, "200")).isEqualTo("env-keywords");
    }

    @Test
    @DisplayName("resolveModelId: 租户配置和环境变量均缺失时应返回 null 交由调用方兜底")
    void resolveModelId_shouldReturnNullForCallerFallback_whenTenantAndEnvironmentAreMissing() {
        when(aiConfigService.getConfig(300)).thenReturn(new AinoteAiConfig());

        assertThat(resolver.resolveModelId(AinoteProcessingType.ASR, "300")).isNull();
        assertThat(resolver.resolveModelId(AinoteProcessingType.OCR, "300")).isNull();
        assertThat(resolver.resolveModelId(AinoteProcessingType.VIDEO, "300")).isNull();
        assertThat(resolver.resolveModelId(AinoteProcessingType.SUMMARY, "300")).isNull();
        assertThat(resolver.resolveModelId(AinoteProcessingType.KEYWORDS, "300")).isNull();
    }
}
