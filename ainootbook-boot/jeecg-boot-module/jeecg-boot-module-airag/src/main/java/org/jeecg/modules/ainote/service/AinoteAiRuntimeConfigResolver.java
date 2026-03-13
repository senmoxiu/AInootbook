package org.jeecg.modules.ainote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.enums.AinoteProcessingType;
import org.jeecg.modules.ainote.service.impl.AinoteAiConfigServiceImpl;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Ainote AI 运行时配置解析器
 * 优先级：租户配置 -> 环境变量 -> null
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteAiRuntimeConfigResolver {

    private final AinoteAiConfigServiceImpl aiConfigService;
    private final Environment environment;

    /**
     * 统一解析运行时模型ID。
     * 返回 null 时由调用方自行决定后续兜底策略。
     */
    public String resolveModelId(AinoteProcessingType type, String tenantId) {
        if (type == null) {
            return null;
        }

        String modelId = resolveFromTenantConfig(type, tenantId);
        if (modelId != null) {
            return modelId;
        }
        return resolveFromEnvironment(type);
    }

    /**
     * 解析知识库ID（仅租户配置）。
     */
    public String resolveKnowledgeIdFromConfig(String tenantId) {
        try {
            AinoteAiConfig config = aiConfigService.getConfig(parseTenantId(tenantId));
            return trimToNull(config == null ? null : config.getKnowledgeId());
        } catch (Exception e) {
            log.warn("读取知识库运行时配置失败: tenantId={}, error={}", tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * 解析知识库ID（环境兜底）。
     */
    public String resolveKnowledgeIdFromEnvironment() {
        String[] keys = {"ainote.ai.knowledge-id", "ainote.ai.embedding.knowledge-id", "ainote.embedding.knowledge-id"};
        if (environment == null) {
            return null;
        }
        for (String key : keys) {
            String value = trimToNull(environment.getProperty(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String resolveFromTenantConfig(AinoteProcessingType type, String tenantId) {
        try {
            AinoteAiConfig config = aiConfigService.getConfig(parseTenantId(tenantId));
            return extractModelId(type, config);
        } catch (Exception e) {
            log.warn("读取AI运行时配置失败，回退到环境变量: type={}, tenantId={}, error={}",
                    type.name(), tenantId, e.getMessage());
            return null;
        }
    }

    private String resolveFromEnvironment(AinoteProcessingType type) {
        String envKey = resolveEnvironmentKey(type);
        if (envKey == null) {
            return null;
        }

        String modelId = trimToNull(System.getenv(envKey));
        if (modelId != null) {
            return modelId;
        }
        if (environment == null) {
            return null;
        }
        return trimToNull(environment.getProperty(envKey));
    }

    private Integer parseTenantId(String tenantId) {
        String resolved = trimToNull(tenantId);
        if (resolved == null) {
            return null;
        }
        try {
            return Integer.valueOf(resolved);
        } catch (NumberFormatException e) {
            log.warn("tenantId不是有效整数，使用默认租户配置: tenantId={}", tenantId);
            return null;
        }
    }

    private String extractModelId(AinoteProcessingType type, AinoteAiConfig config) {
        if (config == null) {
            return null;
        }

        switch (type) {
            case ASR:
                return trimToNull(config.getAsrModelId());
            case OCR:
                return trimToNull(config.getOcrModelId());
            case VIDEO:
                return trimToNull(config.getVideoModelId());
            case SUMMARY:
                return trimToNull(config.getSummaryModelId());
            case KEYWORDS:
                return trimToNull(config.getKeywordsModelId());
            case INTEGRATE:
                return trimToNull(config.getIntegrateModelId());
            default:
                return null;
        }
    }

    private String resolveEnvironmentKey(AinoteProcessingType type) {
        switch (type) {
            case ASR:
                return "AINOTE_ASR_MODEL_ID";
            case OCR:
                return "AINOTE_OCR_MODEL_ID";
            case VIDEO:
                return "AINOTE_VIDEO_MODEL_ID";
            case SUMMARY:
                return "AINOTE_SUMMARY_MODEL_ID";
            case KEYWORDS:
                return "AINOTE_KEYWORDS_MODEL_ID";
            case INTEGRATE:
                return "AINOTE_INTEGRATE_MODEL_ID";
            default:
                return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
