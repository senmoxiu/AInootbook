package org.jeecg.modules.ainote.handler;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiConfig;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.task.AinoteAiTaskWorker;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.handler.AIChatHandler;
import org.jeecg.modules.airag.llm.mapper.AiragModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OCR 任务处理器：图片文字识别
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTaskHandler implements AinoteAiTaskWorker.AinoteAiTaskHandler {

    private static final String TASK_TYPE = "ocr";
    private static final int DEFAULT_TENANT_ID = 0;

    private static final String OCR_PROMPT = "请识别图片中的所有文字内容。要求：\n"
            + "1. 按照图片中文字的空间位置，从上到下、从左到右的顺序输出\n"
            + "2. 保留原文的段落结构和换行\n"
            + "3. 仅输出识别到的文字，不添加任何解释或标注\n"
            + "4. 如果图片中没有文字，请回复\"无文字内容\"";

    private static final String[] OCR_MODEL_ENV_KEYS = {
            "ainote.ai.ocr-model-id",
            "ainote.task.ocr-model-id",
            "jeecg.airag.ocr-model-id"
    };

    private final IAinoteMaterialService materialService;
    private final AIChatHandler aiChatHandler;
    private final AiragModelMapper airagModelMapper;
    private final IAinoteAiConfigService configService;
    private final Environment environment;

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(AinoteAiTask task) throws Exception {
        if (task == null) {
            throw new JeecgBootException("任务不能为空");
        }

        String taskId = requireNotBlank(task.getId(), "任务ID不能为空");
        String materialId = requireNotBlank(task.getMaterialId(), "素材ID不能为空");

        AinoteMaterial material = materialService.getById(materialId);
        if (material == null) {
            throw new JeecgBootException("素材不存在: materialId=" + materialId);
        }

        String imageUrl = requireNotBlank(materialService.generatePresignedUrl(materialId),
                "生成素材预签名URL失败: materialId=" + materialId);

        AiragModel model = resolveVisionModel(task.getTenantId());

        UserMessage userMessage = aiChatHandler.buildUserMessage(OCR_PROMPT, List.of(imageUrl));
        String response = aiChatHandler.completions(model.getId(), List.of((ChatMessage) userMessage));

        String text = normalizeOcrText(response);
        if (oConvertUtils.isEmpty(text)) {
            throw new JeecgBootException("OCR识别结果为空");
        }

        JSONObject result = new JSONObject();
        result.put("text", text);
        log.info("OCR任务完成: taskId={}, materialId={}, modelId={}, textLength={}",
                taskId, materialId, model.getId(), text.length());
        return result.toJSONString();
    }

    // ─── 模型解析：3 级 fallback ──────────────────────────────

    private AiragModel resolveVisionModel(Integer tenantId) {
        AiragModel model = resolveModelFromConfig(tenantId);
        if (model != null) {
            return model;
        }
        model = resolveModelFromEnvironment();
        if (model != null) {
            return model;
        }
        throw new JeecgBootException("未配置可用的OCR Vision模型，请在[AI配置]中设置OCR模型或配置环境变量");
    }

    private AiragModel resolveModelFromConfig(Integer tenantId) {
        try {
            int tid = tenantId != null ? tenantId : DEFAULT_TENANT_ID;
            AinoteAiConfig config = configService.getConfig(tid);
            if (config == null) {
                return null;
            }
            return loadActiveModel(config.getOcrModelId());
        } catch (Exception e) {
            log.warn("读取OCR模型配置失败，尝试环境变量兜底: {}", e.getMessage());
            return null;
        }
    }

    private AiragModel resolveModelFromEnvironment() {
        if (environment == null) {
            return null;
        }
        for (String key : OCR_MODEL_ENV_KEYS) {
            AiragModel model = loadActiveModel(environment.getProperty(key));
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    private AiragModel loadActiveModel(String modelId) {
        String resolvedId = trimToNull(modelId);
        if (resolvedId == null) {
            return null;
        }
        AiragModel model = airagModelMapper.getByIdIgnoreTenant(resolvedId);
        if (model == null) {
            log.warn("OCR模型不存在: modelId={}", resolvedId);
            return null;
        }
        if (model.getActivateFlag() == null || model.getActivateFlag() != 1) {
            log.warn("OCR模型未激活: modelId={}", resolvedId);
            return null;
        }
        // W6: OCR需要多模态LLM，校验模型类型
        if (!"llm".equalsIgnoreCase(model.getModelType())) {
            log.warn("OCR模型类型不是llm: modelId={}, modelType={}", resolvedId, model.getModelType());
            return null;
        }
        return model;
    }

    // ─── 响应解析 ─────────────────────────────────────────────

    private String normalizeOcrText(String response) {
        String trimmed = trimToNull(response);
        if (trimmed == null) {
            return null;
        }
        // 尝试从 JSON 中提取 text 字段
        String text = extractTextFromJson(trimmed);
        if (text == null) {
            // LLM 可能返回 markdown code fence 包裹的纯文本
            text = stripCodeFence(trimmed);
        }
        return trimToNull(text == null ? null : text.replace("\r\n", "\n").replace("\r", "\n"));
    }

    private String extractTextFromJson(String content) {
        try {
            JSONObject obj = JSONObject.parseObject(content);
            return trimToNull(obj == null ? null : obj.getString("text"));
        } catch (Exception ignore) {
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    JSONObject obj = JSONObject.parseObject(content.substring(start, end + 1));
                    return trimToNull(obj == null ? null : obj.getString("text"));
                } catch (Exception ignore2) {
                    // not JSON
                }
            }
            return null;
        }
    }

    private String stripCodeFence(String content) {
        String normalized = content;
        if (normalized.startsWith("```")) {
            int firstLineBreak = normalized.indexOf('\n');
            if (firstLineBreak >= 0 && firstLineBreak < normalized.length() - 1) {
                normalized = normalized.substring(firstLineBreak + 1);
            }
            if (normalized.endsWith("```")) {
                normalized = normalized.substring(0, normalized.length() - 3);
            }
        }
        return normalized.trim();
    }

    // ─── 工具方法 ─────────────────────────────────────────────

    private String requireNotBlank(String value, String message) {
        String resolved = trimToNull(value);
        if (resolved == null) {
            throw new JeecgBootException(message);
        }
        return resolved;
    }

    private String trimToNull(String value) {
        if (oConvertUtils.isEmpty(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
