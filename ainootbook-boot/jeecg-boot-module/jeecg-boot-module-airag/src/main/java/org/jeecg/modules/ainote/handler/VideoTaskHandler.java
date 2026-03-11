package org.jeecg.modules.ainote.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.config.AinoteProperties;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.task.AinoteAiTaskWorker;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.handler.DashscopeAsrHandler;
import org.jeecg.modules.airag.llm.mapper.AiragModelMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import ws.schild.jave.Encoder;
import ws.schild.jave.MultimediaObject;
import ws.schild.jave.encode.AudioAttributes;
import ws.schild.jave.encode.EncodingAttributes;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 视频任务处理器：下载视频 -> JAVE2 提取音频 -> 上传临时音频 -> DashScope ASR 转写
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTaskHandler implements AinoteAiTaskWorker.AinoteAiTaskHandler {

    private static final String TASK_TYPE = "video";
    private static final Semaphore VIDEO_SEMAPHORE = new Semaphore(2);
    /**
     * 等待信号量的最大时间。
     * 超时后抛出异常，任务进入重试队列，避免无限阻塞 worker 线程、饿死 OCR/Summary 等快任务。
     */
    private static final long SEMAPHORE_WAIT_SECONDS = 30L;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final long MAX_WAIT_MS = TimeUnit.MINUTES.toMillis(10);

    private final IAinoteMaterialService materialService;
    private final DashscopeAsrHandler dashscopeAsrHandler;
    private final AiragModelMapper airagModelMapper;
    private final IAinoteAiTaskService aiTaskService;
    private final AinoteProperties ainoteProperties;
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
        Integer tenantId = task.getTenantId();

        AinoteMaterial material = materialService.getById(materialId);
        if (material == null) {
            throw new JeecgBootException("素材不存在: materialId=" + materialId);
        }

        String presignedUrl = requireNotBlank(materialService.generatePresignedUrl(materialId),
                "生成素材预签名URL失败: materialId=" + materialId);

        // C3-并发安全：使用 tryAcquire 替代 acquire，避免长时间阻塞 worker 线程
        // 超时后抛异常，由调度器走重试逻辑，而非无限占用线程
        boolean acquired = VIDEO_SEMAPHORE.tryAcquire(SEMAPHORE_WAIT_SECONDS, TimeUnit.SECONDS);
        if (!acquired) {
            throw new JeecgBootException("视频处理资源繁忙，稍后自动重试（最大并发=2）");
        }
        Path tempDir = null;
        String tempAudioKey = null;
        try {
            tempDir = Files.createTempDirectory("ainote-video-" + taskId);
            String ext = normalizeExt(material.getFileExt(), material.getFileName());
            if (oConvertUtils.isEmpty(ext)) {
                ext = "mp4";
            }
            Path tempVideoFile = tempDir.resolve("source." + ext);

            log.info("视频任务下载文件: taskId={}, materialId={}, tmp={}", taskId, materialId, tempVideoFile);
            downloadToFile(presignedUrl, tempVideoFile);

            Path tempAudioFile = extractAudio(tempVideoFile);
            long audioSize = Files.size(tempAudioFile);
            if (audioSize <= 0) {
                throw new JeecgBootException("音频提取结果为空: materialId=" + materialId);
            }
            log.info("音频提取完成: taskId={}, audioSize={}", taskId, audioSize);

            tempAudioKey = uploadTempAudio(tempAudioFile, taskId, tenantId);

            String audioUrl = generatePresignedUrlForTempAudio(tempAudioKey);

            AiragModel asrModel = resolveAsrModel();

            DashscopeAsrHandler.AsrTask submit = dashscopeAsrHandler.submitTask(asrModel, audioUrl, Collections.emptyMap());
            String vendorTaskId = requireNotBlank(submit == null ? null : submit.getTaskId(),
                    "提交ASR任务失败: 返回taskId为空");

            persistVendorTaskId(taskId, tenantId, vendorTaskId);

            int timeoutSeconds = resolveWaitTimeoutSeconds();
            AiragModel waitModel = buildWaitModel(asrModel, timeoutSeconds);
            DashscopeAsrHandler.AsrTask done = dashscopeAsrHandler.waitAndGetResult(waitModel, vendorTaskId);

            String status = done == null ? null : trimToNull(done.getStatus());
            if (status == null) {
                throw new JeecgBootException("ASR任务状态为空: vendorTaskId=" + vendorTaskId);
            }
            if (!"SUCCEEDED".equalsIgnoreCase(status)) {
                String msg = done.getMessage();
                throw new JeecgBootException("ASR任务未成功: status=" + status +
                        (oConvertUtils.isNotEmpty(msg) ? ", message=" + msg : "") +
                        ", vendorTaskId=" + vendorTaskId);
            }

            String resultJson = requireNotBlank(done.getResult(),
                    "ASR转写成功但结果为空: vendorTaskId=" + vendorTaskId);
            log.info("视频任务完成: taskId={}, materialId={}, vendorTaskId={}", taskId, materialId, vendorTaskId);
            return resultJson;
        } finally {
            VIDEO_SEMAPHORE.release();
            cleanupTempDir(tempDir);
            cleanupMinioTempAudio(tempAudioKey);
        }
    }

    private Path extractAudio(Path videoFile) throws Exception {
        MultimediaObject source = new MultimediaObject(videoFile.toFile());
        AudioAttributes audio = new AudioAttributes()
                .setCodec("pcm_s16le")
                .setSamplingRate(16000)
                .setChannels(1);
        EncodingAttributes attrs = new EncodingAttributes()
                .setOutputFormat("wav")
                .setAudioAttributes(audio);
        Path audioFile = videoFile.resolveSibling("audio.wav");
        Encoder encoder = new Encoder();
        encoder.encode(source, audioFile.toFile(), attrs);
        return audioFile;
    }

    private String uploadTempAudio(Path audioFile, String taskId, Integer tenantId) throws Exception {
        int tid = tenantId == null ? 0 : tenantId;
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String tempAudioKey = "ainote/tmp-audio/" + tid + "/" + taskId + "/" + uuid + ".wav";

        try (InputStream is = Files.newInputStream(audioFile)) {
            org.jeecg.common.util.MinioUtil.upload(is, tempAudioKey);
        }
        log.info("临时音频上传完成: taskId={}, key={}", taskId, tempAudioKey);
        return tempAudioKey;
    }

    private String generatePresignedUrlForTempAudio(String tempAudioKey) {
        String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
        if (oConvertUtils.isEmpty(bucketName)) {
            throw new JeecgBootException("MinIO bucket 未配置");
        }
        String url = org.jeecg.common.util.MinioUtil.getObjectUrl(bucketName, tempAudioKey, 900);
        if (oConvertUtils.isEmpty(url)) {
            throw new JeecgBootException("获取临时音频预签名URL失败");
        }
        return url;
    }

    private void cleanupMinioTempAudio(String tempAudioKey) {
        if (oConvertUtils.isEmpty(tempAudioKey)) {
            return;
        }
        try {
            String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
            if (oConvertUtils.isNotEmpty(bucketName)) {
                org.jeecg.common.util.MinioUtil.removeObject(bucketName, tempAudioKey);
                log.debug("已清理MinIO临时音频: key={}", tempAudioKey);
            }
        } catch (Exception e) {
            log.warn("清理MinIO临时音频失败: key={}, error={}", tempAudioKey, e.getMessage());
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

    private void persistVendorTaskId(String taskId, Integer tenantId, String vendorTaskId) {
        UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", taskId);
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        wrapper.set("vendor_task_id", vendorTaskId);
        if (!aiTaskService.update(null, wrapper)) {
            throw new JeecgBootException("保存vendorTaskId失败: taskId=" + taskId);
        }
    }

    private AiragModel resolveAsrModel() {
        String configuredModelId = resolveConfiguredAsrModelId();
        AiragModel model;
        if (configuredModelId != null) {
            model = airagModelMapper.getByIdIgnoreTenant(configuredModelId);
            if (model == null) {
                throw new JeecgBootException("ASR模型不存在: modelId=" + configuredModelId);
            }
        } else {
            LambdaQueryWrapper<AiragModel> qw = new LambdaQueryWrapper<>();
            qw.eq(AiragModel::getModelType, LLMConsts.MODEL_TYPE_ASR);
            qw.eq(AiragModel::getActivateFlag, 1);
            qw.orderByDesc(AiragModel::getUpdateTime);
            qw.last("LIMIT 1");
            model = airagModelMapper.selectOne(qw);
            if (model == null) {
                throw new JeecgBootException("未配置可用的ASR模型，请在[AI模型配置]中新增并[测试激活]ASR模型");
            }
        }

        if (!LLMConsts.MODEL_TYPE_ASR.equalsIgnoreCase(model.getModelType())) {
            throw new JeecgBootException("模型类型不是ASR: modelId=" + model.getId());
        }
        if (model.getActivateFlag() == null || model.getActivateFlag() != 1) {
            throw new JeecgBootException("ASR模型未激活: modelId=" + model.getId());
        }
        return model;
    }

    private String resolveConfiguredAsrModelId() {
        if (environment == null) {
            return null;
        }
        String[] keys = {"ainote.ai.asr-model-id", "ainote.task.asr-model-id", "jeecg.airag.asr-model-id"};
        for (String key : keys) {
            String val = trimToNull(environment.getProperty(key));
            if (val != null) {
                return val;
            }
        }
        return null;
    }

    private int resolveWaitTimeoutSeconds() {
        long configuredMs = 0L;
        try {
            configuredMs = ainoteProperties.getTask().getTimeout().getAsrPoll();
        } catch (Exception ignored) {
        }
        if (configuredMs <= 0L) {
            configuredMs = MAX_WAIT_MS;
        }
        long seconds = TimeUnit.MILLISECONDS.toSeconds(Math.min(configuredMs, MAX_WAIT_MS));
        return (int) Math.max(1, Math.min(seconds, Integer.MAX_VALUE));
    }

    private AiragModel buildWaitModel(AiragModel model, int timeoutSeconds) {
        AiragModel copy = new AiragModel();
        copy.setId(model.getId());
        copy.setName(model.getName());
        copy.setProvider(model.getProvider());
        copy.setModelType(model.getModelType());
        copy.setModelName(model.getModelName());
        copy.setBaseUrl(model.getBaseUrl());
        copy.setCredential(model.getCredential());
        copy.setActivateFlag(model.getActivateFlag());
        copy.setTenantId(model.getTenantId());
        copy.setModelParams(mergeModelParams(model.getModelParams(), timeoutSeconds));
        return copy;
    }

    private String mergeModelParams(String modelParams, int timeoutSeconds) {
        JSONObject obj;
        try {
            obj = oConvertUtils.isEmpty(modelParams) ? new JSONObject() : JSONObject.parseObject(modelParams);
            if (obj == null) {
                obj = new JSONObject();
            }
        } catch (Exception e) {
            obj = new JSONObject();
        }
        obj.put("timeoutSeconds", timeoutSeconds);
        obj.put("timeout", timeoutSeconds);
        return obj.toJSONString();
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

    private void cleanupTempDir(Path tempDir) {
        if (tempDir == null) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
            for (Path entry : stream) {
                deleteQuietly(entry);
            }
        } catch (Exception ignored) {
        }
        deleteQuietly(tempDir);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    private String requireNotBlank(String value, String message) {
        String v = trimToNull(value);
        if (v == null) {
            throw new JeecgBootException(message);
        }
        return v;
    }

    private String trimToNull(String value) {
        if (oConvertUtils.isEmpty(value)) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
