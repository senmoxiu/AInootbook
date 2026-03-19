package org.jeecg.modules.airag.llm.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 智谱 GLM-OCR 处理器
 * 调用 layout_parsing API 进行文档/图片 OCR 识别，归一化返回纯文本。
 */
@Slf4j
@Component
public class GlmOcrHandler {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final String OCR_PATH = "/api/paas/v4/layout_parsing";
    private static final int MAX_ERROR_BODY_LENGTH = 2000;

    /** 测试连通性用的公开图片（智谱官方示例） */
    private static final String DEFAULT_TEST_FILE_URL =
            "https://cdn.bigmodel.cn/static/logo/introduction.png";

    /**
     * 每次调用时新建 HttpClient 实例，避免 Spring 单例持有的实例在特定环境下 TLS/连接池异常导致超时。
     */
    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * 调用 GLM-OCR layout_parsing API
     *
     * @param model   模型配置（需包含 baseUrl、modelName、credential）
     * @param fileUrl 图片/PDF 的可访问 URL
     * @return 归一化后的纯文本
     */
    public String parse(AiragModel model, String fileUrl) {
        validateModel(model);
        if (oConvertUtils.isEmpty(fileUrl)) {
            throw new JeecgBootException("fileUrl不能为空");
        }

        String apiKey = resolveApiKey(model);
        URI endpoint = buildEndpoint(model.getBaseUrl());
        String requestBody = buildRequestBody(model.getModelName(), fileUrl);

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = buildHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long costMs = System.currentTimeMillis() - start;
            log.info("GLM-OCR调用完成: modelId={}, statusCode={}, costMs={}, fileUrlLength={}",
                    model.getId(), response.statusCode(), costMs, fileUrl.length());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new JeecgBootException("调用GLM-OCR失败: statusCode=" + response.statusCode()
                        + ", body=" + truncate(response.body()));
            }

            String text = extractText(response.body());
            if (oConvertUtils.isEmpty(text)) {
                throw new JeecgBootException("GLM-OCR识别结果为空");
            }
            return text;
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("GLM-OCR调用异常: modelId={}, costMs={}, fileUrlLength={}",
                    model.getId(), costMs, fileUrl.length(), e);
            throw new JeecgBootException("调用GLM-OCR失败: " + e.getMessage());
        }
    }

    /**
     * 调用 GLM-OCR layout_parsing API（base64 模式）
     * 直接传文件字节，避免智谱服务端回源拉取内网 MinIO URL 导致超时。
     *
     * @param model     模型配置
     * @param fileBytes 文件字节（图片/PDF）
     * @param fileExt   文件扩展名（jpg/jpeg/png/pdf）
     * @return 归一化后的纯文本
     */
    public String parseBase64(AiragModel model, byte[] fileBytes, String fileExt) {
        validateModel(model);
        if (fileBytes == null || fileBytes.length == 0) {
            throw new JeecgBootException("文件内容不能为空");
        }

        String apiKey = resolveApiKey(model);
        URI endpoint = buildEndpoint(model.getBaseUrl());

        String mimeType = resolveImageMimeType(fileExt);
        String base64Content = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);
        JSONObject body = new JSONObject();
        body.put("model", model.getModelName());
        body.put("file", base64Content);
        String requestBody = body.toJSONString();

        log.info("GLM-OCR base64调用: modelId={}, endpoint={}, fileBytesLength={}, base64Length={}",
                model.getId(), endpoint, fileBytes.length, base64Content.length());

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = buildHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long costMs = System.currentTimeMillis() - start;
            log.info("GLM-OCR base64调用完成: modelId={}, statusCode={}, costMs={}, fileBytesLength={}",
                    model.getId(), response.statusCode(), costMs, fileBytes.length);

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new JeecgBootException("调用GLM-OCR失败: statusCode=" + response.statusCode()
                        + ", body=" + truncate(response.body()));
            }

            String text = extractText(response.body());
            if (oConvertUtils.isEmpty(text)) {
                throw new JeecgBootException("GLM-OCR识别结果为空");
            }
            return text;
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("GLM-OCR base64调用异常: modelId={}, costMs={}, fileBytesLength={}",
                    model.getId(), costMs, fileBytes.length, e);
            throw new JeecgBootException("调用GLM-OCR失败: " + e.getMessage());
        }
    }

    /**
     * 测试模型连通性（用智谱官方示例图片）
     */
    public void testConnection(AiragModel model) {
        // 优先从 modelParams 读取自定义测试图片
        String testUrl = resolveTestFileUrl(model);
        String text = parse(model, testUrl);
        if (oConvertUtils.isEmpty(text)) {
            throw new JeecgBootException("GLM-OCR测试连接失败：返回结果为空");
        }
        log.info("GLM-OCR测试连接成功: modelId={}, resultLength={}", model.getId(), text.length());
    }

    // ─── 内部方法 ─────────────────────────────────────────────

    private String resolveImageMimeType(String fileExt) {
        if (oConvertUtils.isEmpty(fileExt)) {
            return "image/jpeg";
        }
        String ext = fileExt.trim().toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "pdf":
                return "application/pdf";
            default:
                return "image/jpeg";
        }
    }

    private void validateModel(AiragModel model) {
        if (model == null) {
            throw new JeecgBootException("模型配置不能为空");
        }
        if (oConvertUtils.isEmpty(model.getModelName())) {
            throw new JeecgBootException("基础模型名称不能为空");
        }
        if (oConvertUtils.isEmpty(model.getBaseUrl())) {
            throw new JeecgBootException("API域名不能为空");
        }
    }

    private String resolveApiKey(AiragModel model) {
        if (oConvertUtils.isEmpty(model.getCredential())) {
            throw new JeecgBootException("凭证信息不能为空");
        }
        try {
            JSONObject credential = JSONObject.parseObject(model.getCredential());
            String apiKey = credential == null ? null : credential.getString("apiKey");
            if (oConvertUtils.isEmpty(apiKey)) {
                throw new JeecgBootException("apiKey不能为空");
            }
            return apiKey.trim();
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("凭证信息格式不正确，无法解析apiKey");
        }
    }

    private String resolveTestFileUrl(AiragModel model) {
        if (oConvertUtils.isNotEmpty(model.getModelParams())) {
            try {
                JSONObject params = JSONObject.parseObject(model.getModelParams());
                String testUrl = params == null ? null : params.getString("testFileUrl");
                if (oConvertUtils.isNotEmpty(testUrl)) {
                    return testUrl.trim();
                }
            } catch (Exception e) {
                log.warn("解析modelParams失败，使用默认测试图片: {}", e.getMessage());
            }
        }
        return DEFAULT_TEST_FILE_URL;
    }

    private URI buildEndpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return URI.create(normalized + OCR_PATH);
    }

    private String buildRequestBody(String modelName, String fileUrl) {
        JSONObject body = new JSONObject();
        body.put("model", modelName);
        body.put("file", fileUrl);
        return body.toJSONString();
    }

    /**
     * 从 GLM-OCR 响应中提取文本。
     * 响应结构：顶层或 data 节点下的 md_results（字符串/数组）或 layout_details。
     */
    private String extractText(String responseBody) {
        if (oConvertUtils.isEmpty(responseBody)) {
            return null;
        }
        JSONObject root;
        try {
            root = JSON.parseObject(responseBody);
        } catch (Exception e) {
            throw new JeecgBootException("GLM-OCR返回结果不是合法JSON");
        }
        if (root == null) {
            return null;
        }

        // 优先从顶层取
        String text = extractMdResults(root);
        if (oConvertUtils.isNotEmpty(text)) {
            return normalize(text);
        }

        // 尝试从 data 节点取
        JSONObject data = root.getJSONObject("data");
        if (data != null) {
            text = extractMdResults(data);
            if (oConvertUtils.isNotEmpty(text)) {
                return normalize(text);
            }
        }

        // fallback: layout_details
        text = extractFromLayoutDetails(root);
        if (oConvertUtils.isNotEmpty(text)) {
            return normalize(text);
        }
        if (data != null) {
            text = extractFromLayoutDetails(data);
            if (oConvertUtils.isNotEmpty(text)) {
                return normalize(text);
            }
        }

        return null;
    }

    /**
     * 从 md_results 字段提取文本（可能是 String 或 JSONArray）
     */
    private String extractMdResults(JSONObject obj) {
        Object mdResults = obj.get("md_results");
        if (mdResults == null) {
            return null;
        }
        if (mdResults instanceof String) {
            return (String) mdResults;
        }
        if (mdResults instanceof JSONArray) {
            JSONArray arr = (JSONArray) mdResults;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                String fragment = null;
                if (item instanceof String) {
                    fragment = (String) item;
                } else if (item instanceof JSONObject) {
                    JSONObject itemObj = (JSONObject) item;
                    fragment = itemObj.getString("md");
                    if (fragment == null) fragment = itemObj.getString("content");
                    if (fragment == null) fragment = itemObj.getString("text");
                }
                if (oConvertUtils.isNotEmpty(fragment)) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(fragment);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        return null;
    }

    /**
     * 从 layout_details 聚合文本（fallback）
     */
    private String extractFromLayoutDetails(JSONObject obj) {
        Object details = obj.get("layout_details");
        if (!(details instanceof JSONArray)) {
            return null;
        }
        JSONArray arr = (JSONArray) details;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            Object item = arr.get(i);
            if (item instanceof JSONObject) {
                JSONObject block = (JSONObject) item;
                String text = block.getString("text");
                if (text == null) text = block.getString("content");
                if (oConvertUtils.isNotEmpty(text)) {
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(text);
                }
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String normalize(String text) {
        if (oConvertUtils.isEmpty(text)) {
            return null;
        }
        String result = text.replace("\r\n", "\n").replace("\r", "\n").trim();
        return result.isEmpty() ? null : result;
    }

    private String truncate(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        return trimmed.length() <= MAX_ERROR_BODY_LENGTH
                ? trimmed
                : trimmed.substring(0, MAX_ERROR_BODY_LENGTH) + "...";
    }
}
