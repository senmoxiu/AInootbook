package org.jeecg.modules.ainote.handler;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.config.AinoteProperties;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.enums.AinoteProcessingType;
import org.jeecg.modules.ainote.service.AinoteAiRuntimeConfigResolver;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.task.AinoteAiTaskWorker;
import org.jeecg.modules.airag.llm.consts.LLMConsts;
import org.jeecg.modules.airag.llm.entity.AiragModel;
import org.jeecg.modules.airag.llm.handler.DashscopeAsrHandler;
import org.jeecg.modules.airag.llm.mapper.AiragModelMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ASR 任务处理器：音频转写
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrTaskHandler implements AinoteAiTaskWorker.AinoteAiTaskHandler {

    private static final String TASK_TYPE = "asr";
    private static final long MAX_WAIT_MS = TimeUnit.MINUTES.toMillis(10);

    private final DashscopeAsrHandler dashscopeAsrHandler;
    private final IAinoteMaterialService materialService;
    private final AiragModelMapper airagModelMapper;
    private final IAinoteAiTaskService aiTaskService;
    private final AinoteProperties ainoteProperties;
    private final AinoteAiRuntimeConfigResolver runtimeConfigResolver;

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

        // 1) 获取素材
        AinoteMaterial material = materialService.getById(materialId);
        if (material == null) {
            throw new JeecgBootException("素材不存在: materialId=" + materialId);
        }

        // 2) 生成签名URL
        String fileUrl = requireNotBlank(materialService.generatePresignedUrl(materialId),
                "生成素材预签名URL失败: materialId=" + materialId);

        // 3) 获取 ASR 模型配置
        AiragModel asrModel = resolveAsrModel();

        // 4) 提交任务
        DashscopeAsrHandler.AsrTask submit = dashscopeAsrHandler.submitTask(asrModel, fileUrl, Collections.emptyMap());
        String vendorTaskId = requireNotBlank(submit == null ? null : submit.getTaskId(), "提交ASR任务失败: 返回taskId为空");

        // 5) 持久化 vendor_task_id
        persistVendorTaskId(taskId, tenantId, vendorTaskId);

        // 6) 轮询等待结果
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
                    (oConvertUtils.isNotEmpty(msg) ? ", message=" + msg : "") + ", vendorTaskId=" + vendorTaskId);
        }

        // 7) 返回转写结果
        String resultJson = requireNotBlank(done.getResult(), "ASR转写成功但结果为空: vendorTaskId=" + vendorTaskId);
        log.info("ASR任务完成: taskId={}, materialId={}, vendorTaskId={}", taskId, materialId, vendorTaskId);
        return resultJson;
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
        String tenantId = TenantContext.getTenant();
        String configuredModelId = runtimeConfigResolver.resolveModelId(
                AinoteProcessingType.ASR,
                String.valueOf(tenantId));
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
