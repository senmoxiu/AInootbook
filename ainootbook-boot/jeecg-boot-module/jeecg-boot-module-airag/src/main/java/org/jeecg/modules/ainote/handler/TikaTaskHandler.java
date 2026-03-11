package org.jeecg.modules.ainote.handler;

import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.document.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.task.AinoteAiTaskWorker;
import org.jeecg.modules.airag.llm.document.TikaDocumentParser;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * 文档解析处理器：下载素材文件 -> Tika解析 -> 文本清洗
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TikaTaskHandler implements AinoteAiTaskWorker.AinoteAiTaskHandler {

    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private static final String TASK_TYPE = "tika";

    private final IAinoteMaterialService materialService;
    private final TikaDocumentParser tikaDocumentParser = new TikaDocumentParser();

    @Override
    public String getTaskType() {
        return TASK_TYPE;
    }

    @Override
    public String handle(AinoteAiTask task) throws Exception {
        if (task == null) {
            throw new JeecgBootException("任务不能为空");
        }

        String taskId = task.getId();
        String materialId = task.getMaterialId();
        if (oConvertUtils.isEmpty(materialId)) {
            throw new JeecgBootException("materialId不能为空");
        }

        AinoteMaterial material = materialService.getById(materialId);
        if (material == null) {
            throw new JeecgBootException("素材不存在: materialId=" + materialId);
        }

        String presignedUrl = materialService.generatePresignedUrl(materialId);
        if (oConvertUtils.isEmpty(presignedUrl)) {
            throw new JeecgBootException("生成预签名URL失败: materialId=" + materialId);
        }

        Path tempDir = null;
        Path tempFile = null;
        try {
            tempDir = Files.createTempDirectory("ainote-tika-");
            String ext = normalizeExt(material.getFileExt(), material.getFileName());
            if (oConvertUtils.isEmpty(ext)) {
                throw new JeecgBootException("素材扩展名为空，无法解析: materialId=" + materialId);
            }
            tempFile = Files.createTempFile(tempDir, "material-", "." + ext);

            log.info("Tika任务下载文件: taskId={}, materialId={}, tmp={}", taskId, materialId, tempFile);
            downloadToFile(presignedUrl, tempFile);

            Document document = tikaDocumentParser.parse(tempFile.toFile());
            String text = document == null ? "" : document.text();
            text = cleanText(text);
            if (oConvertUtils.isEmpty(text)) {
                throw new JeecgBootException("文档解析结果为空: materialId=" + materialId);
            }

            JSONObject result = new JSONObject();
            result.put("text", text);
            log.info("Tika任务完成: taskId={}, materialId={}, textLength={}", taskId, materialId, text.length());
            return result.toJSONString();
        } finally {
            deleteQuietly(tempFile);
            deleteQuietly(tempDir);
        }
    }

    private void downloadToFile(String url, Path targetFile) throws Exception {
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new JeecgBootException("仅支持http/https下载: scheme=" + scheme);
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new JeecgBootException("下载失败: status=" + status);
        }

        try (InputStream body = response.body()) {
            Files.copy(body, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String cleanText(String text) {
        if (oConvertUtils.isEmpty(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

        StringBuilder sb = new StringBuilder();
        for (String line : normalized.split("\n")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            trimmed = trimmed.replaceAll("\\s{2,}", " ");
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(trimmed);
        }
        return sb.toString();
    }

    private String normalizeExt(String fileExt, String fileName) {
        String ext = fileExt;
        if (oConvertUtils.isEmpty(ext) && oConvertUtils.isNotEmpty(fileName)) {
            int idx = fileName.lastIndexOf('.');
            if (idx >= 0 && idx < fileName.length() - 1) {
                ext = fileName.substring(idx + 1);
            }
        }
        if (oConvertUtils.isEmpty(ext)) {
            return "";
        }
        ext = ext.trim();
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignore) {
        }
    }
}
