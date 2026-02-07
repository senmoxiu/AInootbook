package org.jeecg.modules.airag.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.AssertUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.handler.DashscopeAsrHandler;
import org.jeecg.modules.airag.llm.service.IAiragModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * DashScope ASR 语音识别接口
 */
@Tag(name = "ASR语音识别")
@RestController("airagAsrController")
@RequestMapping("/airag/asr")
@Slf4j
public class AiragAsrController {

    private static final String AIRAG_ASR_TASK_REDIS_KEY = "airag:asr:task";
    private static final int TASK_CACHE_TTL_SECONDS = 24 * 60 * 60;

    @Autowired
    private IAiragModelService airagModelService;

    @Autowired
    private DashscopeAsrHandler dashscopeAsrHandler;

    @Autowired
    private RedisUtil redisUtil;

    @Operation(summary = "ASR-提交转写任务")
    @PostMapping("/transcribe")
    public Result<?> transcribe(@RequestBody TranscribeReq req, HttpServletRequest request) {
        AssertUtils.assertNotEmpty("modelId不能为空", req.getModelId());
        AssertUtils.assertNotEmpty("fileUrl不能为空", req.getFileUrl());

        AiragModel model = airagModelService.getById(req.getModelId());
        AssertUtils.assertNotEmpty("模型不存在", model);
        if (!LLMConsts.MODEL_TYPE_ASR.equalsIgnoreCase(model.getModelType())) {
            return Result.error("请选择ASR模型");
        }
        AssertUtils.assertSame("模型未激活,请先在[AI模型配置]中[测试激活]模型", model.getActivateFlag(), 1);

        DashscopeAsrHandler.AsrTask task = dashscopeAsrHandler.submitTask(model, req.getFileUrl(), req.getParams());
        cacheTaskModelId(request, task.getTaskId(), model.getId());
        return Result.OK(task);
    }

    @Operation(summary = "ASR-查询任务状态")
    @GetMapping("/task/{taskId}")
    public Result<?> queryTask(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        AssertUtils.assertNotEmpty("taskId不能为空", taskId);
        String modelId = getTaskModelId(request, taskId);
        if (oConvertUtils.isEmpty(modelId)) {
            return Result.error("任务不存在或已过期");
        }
        AiragModel model = airagModelService.getById(modelId);
        if (model == null) {
            return Result.error("模型不存在");
        }
        return Result.OK(dashscopeAsrHandler.queryTask(model, taskId));
    }

    @Operation(summary = "ASR-获取转写结果")
    @GetMapping("/task/{taskId}/result")
    public Result<?> getResult(@PathVariable("taskId") String taskId, HttpServletRequest request) {
        AssertUtils.assertNotEmpty("taskId不能为空", taskId);
        String modelId = getTaskModelId(request, taskId);
        if (oConvertUtils.isEmpty(modelId)) {
            return Result.error("任务不存在或已过期");
        }
        AiragModel model = airagModelService.getById(modelId);
        if (model == null) {
            return Result.error("模型不存在");
        }
        return Result.OK(dashscopeAsrHandler.waitAndGetResult(model, taskId));
    }

    private void cacheTaskModelId(HttpServletRequest request, String taskId, String modelId) {
        String key = buildTaskKey(request, taskId);
        redisUtil.set(key, modelId, TASK_CACHE_TTL_SECONDS);
    }

    private String getTaskModelId(HttpServletRequest request, String taskId) {
        String key = buildTaskKey(request, taskId);
        Object val = redisUtil.get(key);
        return val == null ? null : val.toString();
    }

    private String buildTaskKey(HttpServletRequest request, String taskId) {
        String tenantId = TokenUtils.getTenantIdByRequest(request);
        tenantId = oConvertUtils.getString(tenantId, "0");
        return AIRAG_ASR_TASK_REDIS_KEY + ":" + tenantId + ":" + taskId;
    }

    @Data
    public static class TranscribeReq {
        private String modelId;
        private String fileUrl;
        private Map<String, Object> params;
    }
}
