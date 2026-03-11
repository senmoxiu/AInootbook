package org.jeecg.modules.ainote.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.util.ShiroThreadPoolExecutor;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.mapper.AinoteAiTaskMapper;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AI任务调度器：定时扫描待处理任务并投递到线程池执行。
 * <p>
 * 约定：
 * - 使用 Mapper.selectPendingTasks() 获取待处理任务
 * - 通过 IAinoteAiTaskService.claimTask() 抢占（乐观锁）
 * - 抢占成功后，投递到 ainoteTaskExecutor 线程池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteAiTaskScheduler {

    private static final int STATUS_PENDING = 0;
    private static final long FIXED_DELAY_MS = 5000L;
    private static final int DEFAULT_BATCH_SIZE = 20;
    /** WR-09: 分布式锁 key 和超时 */
    private static final String LOCK_KEY = "ainote:task:scheduler:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final AinoteAiTaskMapper aiTaskMapper;
    private final IAinoteAiTaskService aiTaskService;
    private final AinoteAiTaskWorker aiTaskWorker;
    private final StringRedisTemplate stringRedisTemplate;

    @Resource(name = "ainoteTaskExecutor")
    private ShiroThreadPoolExecutor ainoteTaskExecutor;

    @Scheduled(fixedDelay = FIXED_DELAY_MS)
    public void scanAndDispatch() {
        // WR-09: Redis 分布式锁，防止多实例重复调度
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }
        try {
            List<Integer> tenantIds = resolveTenantIdsWithPendingTasks();
            for (Integer tenantId : tenantIds) {
                if (tenantId == null) {
                    continue;
                }
                dispatchTenant(tenantId);
            }
        } catch (Exception e) {
            log.error("AI任务调度扫描异常", e);
        } finally {
            try {
                stringRedisTemplate.delete(LOCK_KEY);
            } catch (Exception ex) {
                log.warn("释放调度锁失败", ex);
            }
        }
    }

    private List<Integer> resolveTenantIdsWithPendingTasks() {
        try {
            QueryWrapper<AinoteAiTask> wrapper = new QueryWrapper<>();
            wrapper.select("tenant_id")
                    .eq("task_status", STATUS_PENDING)
                    .and(w -> w.isNull("next_retry_at").or().le("next_retry_at", new Date()))
                    .groupBy("tenant_id");
            List<AinoteAiTask> rows = aiTaskMapper.selectList(wrapper);
            if (rows == null || rows.isEmpty()) {
                return List.of(0);
            }
            return rows.stream()
                    .map(AinoteAiTask::getTenantId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("解析待处理任务的tenantId列表失败，降级为tenantId=0", e);
            return List.of(0);
        }
    }

    private void dispatchTenant(Integer tenantId) {
        List<AinoteAiTask> tasks;
        try {
            tasks = aiTaskMapper.selectPendingTasks(tenantId, DEFAULT_BATCH_SIZE);
        } catch (Exception e) {
            log.error("查询待处理AI任务失败: tenantId={}", tenantId, e);
            return;
        }

        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        for (AinoteAiTask task : tasks) {
            if (task == null || oConvertUtils.isEmpty(task.getId())) {
                continue;
            }

            String taskId = task.getId();
            String previousTenant = TenantContext.getTenant();

            try {
                TenantContext.setTenant(String.valueOf(tenantId));

                boolean claimed = aiTaskService.claimTask(taskId);
                if (!claimed) {
                    continue;
                }

                try {
                    ainoteTaskExecutor.execute(() -> aiTaskWorker.execute(taskId));
                } catch (Exception submitEx) {
                    log.error("投递AI任务到线程池失败: taskId={}, tenantId={}", taskId, tenantId, submitEx);
                    try {
                        aiTaskService.failTask(taskId, "任务投递失败: " + safeMessage(submitEx));
                    } catch (Exception failEx) {
                        log.error("任务投递失败后回写failTask异常: taskId={}, tenantId={}", taskId, tenantId, failEx);
                    }
                }
            } catch (Exception e) {
                log.error("抢占并投递AI任务异常: taskId={}, tenantId={}", taskId, tenantId, e);
            } finally {
                restoreTenant(previousTenant);
            }
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
}
