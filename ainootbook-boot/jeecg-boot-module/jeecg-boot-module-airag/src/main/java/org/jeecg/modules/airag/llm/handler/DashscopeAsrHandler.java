package org.jeecg.modules.airag.llm.handler;

import com.alibaba.dashscope.audio.asr.transcription.Transcription;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionQueryParam;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionResult;
import com.alibaba.dashscope.audio.asr.transcription.TranscriptionTaskResult;
import com.alibaba.dashscope.common.TaskStatus;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.AssertUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * DashScope ASR（语音转写）处理器
 */
@Slf4j
@Component
public class DashscopeAsrHandler {

    private static final int DEFAULT_POLL_INTERVAL_MS = 1000;
    private static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 60;
    private static final int DEFAULT_TEST_TIMEOUT_SECONDS = 15;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 20000;

    private static final Set<String> LOCAL_ONLY_PARAM_KEYS = new HashSet<>(Arrays.asList(
            "testFileUrl", "testTimeoutSeconds", "testParams",
            "timeoutSeconds", "timeout", "pollIntervalMs"
    ));

    /**
     * 测试 ASR 模型连接
     */
    public void testConnection(AiragModel model) {
        Map<String, Object> modelParams = parseJsonToMap(model.getModelParams());
        String testFileUrl = oConvertUtils.getString(modelParams.get("testFileUrl"), null);
        AssertUtils.assertNotEmpty("ASR测试需要在模型参数中配置 testFileUrl(短音频URL)", testFileUrl);

        int testTimeoutSeconds = oConvertUtils.getInt(modelParams.get("testTimeoutSeconds"), DEFAULT_TEST_TIMEOUT_SECONDS);
        Map<String, Object> testParams = toMap(modelParams.get("testParams"));

        AsrTask submit = submitTask(model, testFileUrl, testParams);
        AsrTask done = waitAndGetResult(model, submit.getTaskId(), Duration.ofSeconds(testTimeoutSeconds));
        if (!TaskStatus.SUCCEEDED.getValue().equalsIgnoreCase(done.getStatus())) {
            throw new JeecgBootException("ASR测试连接失败，任务状态=" + done.getStatus() +
                (oConvertUtils.isEmpty(done.getMessage()) ? "" : (", message=" + done.getMessage())));
        }
    }

    /**
     * 提交转写任务
     */
    public AsrTask submitTask(AiragModel model, String fileUrl, Map<String, Object> params) {
        AssertUtils.assertNotEmpty("fileUrl不能为空", fileUrl);
        if (!LLMConsts.WEB_PATTERN.matcher(fileUrl).matches()) {
            throw new JeecgBootException("fileUrl必须是http/https地址");
        }

        String apiKey = getApiKey(model);
        AssertUtils.assertNotEmpty("基础模型不能为空", model.getModelName());

        Map<String, Object> mergedParams = new HashMap<>();
        mergedParams.putAll(parseJsonToMap(model.getModelParams()));
        if (params != null) {
            mergedParams.putAll(params);
        }

        try {
            Transcription transcription = new Transcription();
            TranscriptionParam.TranscriptionParamBuilder<?, ?> builder = TranscriptionParam.builder()
                    .apiKey(apiKey)
                    .model(model.getModelName())
                    .fileUrls(Collections.singletonList(fileUrl));

            applyKnownOptions(builder, mergedParams);
            applyGenericParameters(builder, mergedParams);

            TranscriptionResult result = transcription.asyncCall(builder.build());
            return toAsrTask(result);
        } catch (ApiException e) {
            log.error("DashScope ASR submitTask 失败: {}", e.getMessage(), e);
            throw new JeecgBootException("提交ASR转写任务失败：" + safeMsg(e.getMessage()));
        } catch (Exception e) {
            log.error("DashScope ASR submitTask 异常", e);
            throw new JeecgBootException("提交ASR转写任务失败，详情请查看后台日志。");
        }
    }

    /**
     * 查询任务状态
     */
    public AsrTask queryTask(AiragModel model, String taskId) {
        AssertUtils.assertNotEmpty("taskId不能为空", taskId);
        String apiKey = getApiKey(model);

        try {
            Transcription transcription = new Transcription();
            TranscriptionQueryParam queryParam = TranscriptionQueryParam.builder()
                    .apiKey(apiKey)
                    .taskId(taskId)
                    .build();
            TranscriptionResult result = transcription.fetch(queryParam);
            return toAsrTask(result);
        } catch (ApiException e) {
            log.error("DashScope ASR queryTask 失败: {}", e.getMessage(), e);
            throw new JeecgBootException("查询ASR任务失败：" + safeMsg(e.getMessage()));
        } catch (Exception e) {
            log.error("DashScope ASR queryTask 异常", e);
            throw new JeecgBootException("查询ASR任务失败，详情请查看后台日志。");
        }
    }

    /**
     * 等待并获取结果
     */
    public AsrTask waitAndGetResult(AiragModel model, String taskId) {
        int timeoutSeconds = resolveWaitTimeoutSeconds(model);
        return waitAndGetResult(model, taskId, Duration.ofSeconds(timeoutSeconds));
    }

    private AsrTask waitAndGetResult(AiragModel model, String taskId, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        int pollIntervalMs = resolvePollIntervalMs(model);

        AsrTask latest = null;
        while (true) {
            latest = queryTask(model, taskId);
            String status = oConvertUtils.getString(latest.getStatus(), TaskStatus.UNKNOWN.getValue());

            if (TaskStatus.SUCCEEDED.getValue().equalsIgnoreCase(status)) {
                String url = latest.getTranscriptionUrl();
                if (oConvertUtils.isNotEmpty(url)) {
                    latest.setResult(downloadTranscriptionJson(url));
                }
                return latest;
            }
            if (TaskStatus.FAILED.getValue().equalsIgnoreCase(status)
                    || TaskStatus.CANCELED.getValue().equalsIgnoreCase(status)) {
                return latest;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new JeecgBootException("等待ASR任务结果超时，请稍后查询任务状态/结果");
            }
            sleepQuietly(pollIntervalMs);
        }
    }

    private String getApiKey(AiragModel model) {
        AssertUtils.assertNotEmpty("凭证信息不能为空", model.getCredential());
        JSONObject credential = JSONObject.parseObject(model.getCredential());
        String apiKey = credential.getString("apiKey");
        AssertUtils.assertNotEmpty("apiKey不能为空", apiKey);
        return apiKey;
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (oConvertUtils.isEmpty(json)) {
            return Collections.emptyMap();
        }
        try {
            JSONObject obj = JSONObject.parseObject(json);
            return obj == null ? Collections.emptyMap() : obj;
        } catch (Exception ignore) {
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj == null) return Collections.emptyMap();
        if (obj instanceof Map) return (Map<String, Object>) obj;
        if (obj instanceof String) return parseJsonToMap((String) obj);
        return Collections.emptyMap();
    }

    private void applyKnownOptions(TranscriptionParam.TranscriptionParamBuilder<?, ?> builder, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;

        Object phraseId = params.get("phraseId");
        if (phraseId != null) builder.phraseId(phraseId.toString());

        Object channelId = params.get("channelId");
        if (channelId != null) {
            List<Integer> channelIds = parseIntegerList(channelId);
            if (!channelIds.isEmpty()) builder.channelId(channelIds);
        }

        Object diarizationEnabled = params.get("diarizationEnabled");
        if (diarizationEnabled != null) {
            builder.diarizationEnabled(Boolean.parseBoolean(diarizationEnabled.toString()));
        }

        Object speakerCount = params.get("speakerCount");
        if (speakerCount != null) {
            try {
                Integer v = Integer.valueOf(speakerCount.toString());
                builder.speakerCount(v);
            } catch (NumberFormatException ignored) {
            }
        }

        Object disfluencyRemovalEnabled = params.get("disfluencyRemovalEnabled");
        if (disfluencyRemovalEnabled != null) {
            builder.disfluencyRemovalEnabled(Boolean.parseBoolean(disfluencyRemovalEnabled.toString()));
        }

        Object timestampAlignmentEnabled = params.get("timestampAlignmentEnabled");
        if (timestampAlignmentEnabled != null) {
            builder.timestampAlignmentEnabled(Boolean.parseBoolean(timestampAlignmentEnabled.toString()));
        }

        Object specialWordFilter = params.get("specialWordFilter");
        if (specialWordFilter != null) builder.specialWordFilter(specialWordFilter.toString());

        Object audioEventDetectionEnabled = params.get("audioEventDetectionEnabled");
        if (audioEventDetectionEnabled != null) {
            builder.audioEventDetectionEnabled(Boolean.parseBoolean(audioEventDetectionEnabled.toString()));
        }
    }

    private void applyGenericParameters(TranscriptionParam.TranscriptionParamBuilder<?, ?> builder, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;

        Set<String> knownKeys = new HashSet<>(Arrays.asList(
            "phraseId", "channelId", "diarizationEnabled", "speakerCount",
            "disfluencyRemovalEnabled", "timestampAlignmentEnabled",
            "specialWordFilter", "audioEventDetectionEnabled"
        ));

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (oConvertUtils.isEmpty(key) || LOCAL_ONLY_PARAM_KEYS.contains(key) || knownKeys.contains(key)) {
                continue;
            }
            builder.parameter(key, entry.getValue());
        }
    }

    private List<Integer> parseIntegerList(Object val) {
        if (val == null) return Collections.emptyList();
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            List<Integer> out = new ArrayList<>();
            for (Object o : list) {
                try {
                    Integer i = Integer.valueOf(o.toString());
                    out.add(i);
                } catch (NumberFormatException ignored) {
                }
            }
            return out;
        }
        String s = val.toString();
        if (oConvertUtils.isEmpty(s)) return Collections.emptyList();
        String[] parts = s.split(",");
        List<Integer> out = new ArrayList<>();
        for (String p : parts) {
            try {
                Integer i = Integer.valueOf(p.trim());
                out.add(i);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private AsrTask toAsrTask(TranscriptionResult result) {
        AsrTask task = new AsrTask();
        if (result == null) {
            task.setStatus(TaskStatus.UNKNOWN.getValue());
            return task;
        }
        task.setTaskId(result.getTaskId());
        task.setRequestId(result.getRequestId());
        TaskStatus status = result.getTaskStatus();
        task.setStatus(status == null ? TaskStatus.UNKNOWN.getValue() : status.getValue());

        List<TranscriptionTaskResult> results = result.getResults();
        if (results != null && !results.isEmpty()) {
            TranscriptionTaskResult first = results.get(0);
            task.setFileUrl(first.getFileUrl());
            task.setTranscriptionUrl(first.getTranscriptionUrl());
            task.setMessage(first.getMessage());
        }
        return task;
    }

    private String downloadTranscriptionJson(String transcriptionUrl) {
        if (!LLMConsts.WEB_PATTERN.matcher(transcriptionUrl).matches()) {
            throw new JeecgBootException("transcriptionUrl非法");
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL(transcriptionUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (stream == null) {
                throw new JeecgBootException("拉取转写结果失败，HTTP=" + code);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("下载转写结果失败: {}", transcriptionUrl, e);
            throw new JeecgBootException("拉取转写结果失败，详情请查看后台日志。");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private int resolveWaitTimeoutSeconds(AiragModel model) {
        Map<String, Object> modelParams = parseJsonToMap(model.getModelParams());
        Object t1Obj = modelParams.get("timeoutSeconds");
        Object t2Obj = modelParams.get("timeout");
        Integer t1 = t1Obj != null ? parseInteger(t1Obj) : null;
        Integer t2 = t2Obj != null ? parseInteger(t2Obj) : null;
        Integer t = (t1 != null ? t1 : t2);
        return (t == null || t <= 0) ? DEFAULT_WAIT_TIMEOUT_SECONDS : t;
    }

    private int resolvePollIntervalMs(AiragModel model) {
        Map<String, Object> modelParams = parseJsonToMap(model.getModelParams());
        Object vObj = modelParams.get("pollIntervalMs");
        Integer v = vObj != null ? parseInteger(vObj) : null;
        return (v == null || v <= 0) ? DEFAULT_POLL_INTERVAL_MS : v;
    }

    private Integer parseInteger(Object obj) {
        if (obj == null) return null;
        try {
            return Integer.valueOf(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sleepQuietly(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeMsg(String msg) {
        if (oConvertUtils.isEmpty(msg)) return "";
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
    }

    @Data
    public static class AsrTask {
        private String taskId;
        private String requestId;
        private String status;
        private String fileUrl;
        private String transcriptionUrl;
        private String message;
        private String result;
    }
}
