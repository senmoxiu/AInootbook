package org.jeecg.modules.ainote.service;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.dto.AiragFlowDTO;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteSummaryFlowService {

    private final ISysBaseAPI sysBaseAPI;

    public String callAiragFlow(String flowId, Map<String, Object> inputParams, long timeoutMs) {
        AiragFlowDTO dto = AiragFlowDTO.builder()
                .flowId(flowId)
                .inputParams(inputParams)
                .isStream(false)
                .build();
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> sysBaseAPI.runAiragFlow(dto));
        try {
            Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return normalizeFlowOutput(result);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Flow执行超时: flowId={}, timeoutMs={}", flowId, timeoutMs);
            throw new JeecgBootException("Flow执行超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JeecgBootException("Flow执行被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof JeecgBootException) {
                throw (JeecgBootException) cause;
            }
            String msg = cause != null ? cause.getMessage() : "Flow执行失败";
            log.warn("Flow执行失败: flowId={}, error={}", flowId, msg, cause);
            throw new JeecgBootException(msg != null ? msg : "Flow执行失败");
        }
    }

    private String normalizeFlowOutput(Object rawOutput) {
        if (rawOutput == null) {
            throw new JeecgBootException("Flow返回为空");
        }
        if (rawOutput instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty()) {
                throw new JeecgBootException("Flow返回为空");
            }
            try {
                JSONObject parsed = JSONObject.parseObject(trimmed);
                if (parsed != null && parsed.containsKey("summary")) {
                    return trimmed;
                }
            } catch (Exception ignored) {
            }
            return wrapAsResult(trimmed, Collections.emptyList());
        }
        if (rawOutput instanceof Map<?, ?> outputMap) {
            Object summary = firstNonNull(outputMap, "outputText", "result", "summary");
            Object keywords = outputMap.get("keywords");
            // 若提取到的值本身是含 summary 键的完整 JSON 字符串，直接透传，避免二次包装
            if (summary != null) {
                String summaryStr = String.valueOf(summary).trim();
                try {
                    JSONObject parsed = JSONObject.parseObject(summaryStr);
                    if (parsed != null && parsed.containsKey("summary")) {
                        return summaryStr;
                    }
                } catch (Exception ignored) {
                }
            }
            return wrapAsResult(
                    summary != null ? String.valueOf(summary) : String.valueOf(rawOutput),
                    keywords != null ? keywords : Collections.emptyList()
            );
        }
        return wrapAsResult(String.valueOf(rawOutput), Collections.emptyList());
    }

    private Object firstNonNull(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private String wrapAsResult(Object summary, Object keywords) {
        JSONObject result = new JSONObject();
        result.put("summary", summary);
        result.put("keywords", keywords);
        return result.toJSONString();
    }
}
