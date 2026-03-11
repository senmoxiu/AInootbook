package org.jeecg.modules.ainote.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.mapper.AinoteAiTaskMapper;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * AI任务执行器：在任务被 claim 之后执行具体业务逻辑，并回写任务结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteAiTaskWorker {

    /** 单任务超时时间（分钟），WR-01 */
    private static final long TASK_TIMEOUT_MINUTES = 10;

    private final IAinoteAiTaskService aiTaskService;
    private final AinoteAiTaskMapper aiTaskMapper;
    private final List<AinoteAiTaskHandler> handlers;

    public void execute(String taskId) {
        if (oConvertUtils.isEmpty(taskId) || taskId.isBlank()) {
            log.warn("执行AI任务失败: taskId为空");
            return;
        }

        AinoteAiTask task;
        try {
            task = aiTaskMapper.selectById(taskId);
        } catch (Exception e) {
            log.error("加载AI任务失败: taskId={}", taskId, e);
            return;
        }

        if (task == null) {
            log.warn("AI任务不存在: taskId={}", taskId);
            return;
        }

        Integer tenantId = task.getTenantId();
        if (tenantId == null) {
            tenantId = 0;
        }

        String previousTenant = TenantContext.getTenant();
        try {
            TenantContext.setTenant(String.valueOf(tenantId));
            String taskType = normalizeTaskType(task.getTaskType());

            AinoteAiTaskHandler handler = findHandler(taskType);
            if (handler == null) {
                failSafely(taskId, tenantId, "未找到任务处理器: taskType=" + taskType);
                return;
            }

            String result;
            // future 声明在 try 块外，以便在 catch(TimeoutException) 中调用 cancel
            CompletableFuture<String> future = null;
            try {
                // WR-01: 单任务超时控制
                // 必须捕获 future 引用以便超时时 cancel，避免"外层超时、内层继续跑"的竞争状态
                // 同时在 lambda 内重新设置 TenantContext，因为 CompletableFuture 使用不同线程（ThreadLocal 不继承）
                final int taskTenantId = tenantId;
                future = CompletableFuture.supplyAsync(() -> {
                    TenantContext.setTenant(String.valueOf(taskTenantId));
                    try {
                        return handler.handle(task);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        TenantContext.clear();
                    }
                });
                result = future.get(TASK_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException timeoutEx) {
                // 超时后 cancel(true) 尝试中断异步线程，防止后台继续跑导致 Semaphore 占用/状态回写错乱
                if (future != null) {
                    future.cancel(true);
                }
                log.error("AI任务超时: taskId={}, tenantId={}, taskType={}, timeout={}min", taskId, tenantId, taskType, TASK_TIMEOUT_MINUTES);
                failSafely(taskId, tenantId, "任务执行超时（" + TASK_TIMEOUT_MINUTES + "分钟）");
                return;
            } catch (Exception handlerEx) {
                Throwable cause = handlerEx.getCause() != null ? handlerEx.getCause() : handlerEx;
                log.error("AI任务处理异常: taskId={}, tenantId={}, taskType={}", taskId, tenantId, taskType, cause);
                failSafely(taskId, tenantId, "任务处理异常: " + safeMessage(cause));
                return;
            }

            try {
                boolean ok = aiTaskService.completeTask(taskId, result);
                if (!ok) {
                    log.warn("完成AI任务未命中(可能状态不匹配): taskId={}, tenantId={}, taskType={}", taskId, tenantId, taskType);
                }
            } catch (Exception completeEx) {
                log.error("回写completeTask异常: taskId={}, tenantId={}, taskType={}", taskId, tenantId, taskType, completeEx);
                failSafely(taskId, tenantId, "完成任务回写异常: " + safeMessage(completeEx));
            }
        } catch (Exception e) {
            // 兜底：所有异常必须捕获，避免线程池线程退出
            log.error("执行AI任务未预期异常: taskId={}, tenantId={}", taskId, tenantId, e);
            try {
                TenantContext.setTenant(String.valueOf(tenantId));
                failSafely(taskId, tenantId, "未预期异常: " + safeMessage(e));
            } catch (Exception ignore) {
                // ignore
            }
        } finally {
            restoreTenant(previousTenant);
        }
    }

    private AinoteAiTaskHandler findHandler(String taskType) {
        if (handlers == null || handlers.isEmpty()) {
            return null;
        }
        for (AinoteAiTaskHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            String supportedType = normalizeTaskType(handler.getTaskType());
            if (Objects.equals(supportedType, taskType)) {
                return handler;
            }
        }
        return null;
    }

    private String normalizeTaskType(String taskType) {
        if (oConvertUtils.isEmpty(taskType)) {
            return "unknown";
        }
        return taskType.trim().toLowerCase();
    }

    private void failSafely(String taskId, Integer tenantId, String errorMessage) {
        String message = errorMessage;
        if (oConvertUtils.isEmpty(message) || message.isBlank()) {
            message = "任务处理失败";
        }

        try {
            boolean ok = aiTaskService.failTask(taskId, message);
            if (!ok) {
                log.warn("回写failTask未命中(可能状态不匹配): taskId={}, tenantId={}, errorMessage={}", taskId, tenantId, message);
            }
        } catch (Exception e) {
            log.error("回写failTask异常: taskId={}, tenantId={}, errorMessage={}", taskId, tenantId, message, e);
        }
    }

    private void restoreTenant(String previousTenant) {
        try {
            if (oConvertUtils.isEmpty(previousTenant) || "undefined".equalsIgnoreCase(previousTenant)) {
                TenantContext.clear();
                return;
            }
            TenantContext.setTenant(previousTenant);
        } catch (Exception e) {
            TenantContext.clear();
        }
    }

    private String safeMessage(Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String msg = t.getMessage();
        if (oConvertUtils.isEmpty(msg) || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return msg;
    }

    /**
     * 任务处理器（预留接口）
     */
    public interface AinoteAiTaskHandler {

        /**
         * 任务类型: asr/tika/summary
         */
        String getTaskType();

        /**
         * 执行业务逻辑并返回 process_result(JSON 字符串)
         */
        String handle(AinoteAiTask task) throws Exception;
    }
}
