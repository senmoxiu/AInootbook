package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.jeecg.common.config.TenantContext;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteAiTask;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteAiTaskMapper;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * AI任务 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteAiTaskServiceImpl extends ServiceImpl<AinoteAiTaskMapper, AinoteAiTask>
        implements IAinoteAiTaskService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PROCESSING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private static final Set<String> ALLOWED_TASK_TYPES = Set.of("asr", "tika", "ocr", "video", "summary");

    /** WR-02: 最大重试次数（可后续迁移到配置文件） */
    private static final int MAX_RETRY_COUNT = 3;
    /** WR-02: 退避时间（分钟），按重试次数递增 */
    private static final int[] BACKOFF_MINUTES = {1, 2, 4};

    private final AinoteAiTaskMapper aiTaskMapper;

    @Lazy
    @jakarta.annotation.Resource
    private IAinoteNoteService noteService;

    @Lazy
    @jakarta.annotation.Resource
    private IAinoteMaterialService materialService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AinoteAiTask createTask(String noteId, String materialId, String taskType) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        if (oConvertUtils.isEmpty(taskType) || taskType.isBlank()) {
            throw new JeecgBootException("任务类型不能为空");
        }
        // 校验任务类型白名单
        if (!ALLOWED_TASK_TYPES.contains(taskType)) {
            throw new JeecgBootException("任务类型不合法，仅支持: " + String.join(", ", ALLOWED_TASK_TYPES));
        }

        Integer tenantId = getRequiredTenantId();
        LoginUser user = getCurrentUser();

        // CR-04: 校验笔记存在且未删除
        AinoteNote note = noteService.getById(noteId);
        if (note == null) {
            throw new JeecgBootException("笔记不存在: " + noteId);
        }
        if (note.getNoteStatus() != null && note.getNoteStatus() == 3) {
            throw new JeecgBootException("笔记已删除，不允许创建任务");
        }
        if (!tenantId.equals(note.getTenantId())) {
            throw new JeecgBootException("笔记不属于当前租户");
        }

        // CR-04: 如果 materialId 不为空，校验素材存在且属于该笔记
        if (oConvertUtils.isNotEmpty(materialId) && !materialId.isBlank()) {
            AinoteMaterial material = materialService.getById(materialId);
            if (material == null) {
                throw new JeecgBootException("素材不存在: " + materialId);
            }
            if (!noteId.equals(material.getNoteId())) {
                throw new JeecgBootException("素材不属于该笔记");
            }
        }

        // CR-05: CAS 去重 — 防止重复创建 PENDING/PROCESSING 任务
        QueryWrapper<AinoteAiTask> dupCheck = new QueryWrapper<>();
        dupCheck.eq("note_id", noteId);
        dupCheck.eq("task_type", taskType);
        dupCheck.eq("tenant_id", tenantId);
        dupCheck.in("task_status", STATUS_PENDING, STATUS_PROCESSING);
        if (oConvertUtils.isNotEmpty(materialId) && !materialId.isBlank()) {
            dupCheck.eq("material_id", materialId);
        } else {
            dupCheck.isNull("material_id");
        }
        Long existingCount = aiTaskMapper.selectCount(dupCheck);
        if (existingCount != null && existingCount > 0) {
            throw new JeecgBootException("已有相同类型的任务在处理中，请勿重复创建");
        }

        AinoteAiTask task = new AinoteAiTask();
        task.setNoteId(noteId);
        task.setMaterialId(materialId);
        task.setTaskType(taskType);
        task.setTaskStatus(STATUS_PENDING);
        task.setRetryCount(0);
        task.setTenantId(tenantId);
        task.setCreateBy(user.getId());
        task.setCreateTime(new Date());

        try {
            int inserted = aiTaskMapper.insert(task);
            if (inserted <= 0) {
                throw new JeecgBootException("创建任务失败");
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建AI任务失败: noteId={}, materialId={}, taskType={}, tenantId={}",
                    noteId, materialId, taskType, tenantId, e);
            throw new JeecgBootException("创建任务失败");
        }

        log.info("创建AI任务成功: taskId={}, noteId={}, materialId={}, taskType={}, tenantId={}",
                task.getId(), noteId, materialId, taskType, tenantId);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean claimTask(String taskId) {
        if (oConvertUtils.isEmpty(taskId) || taskId.isBlank()) {
            throw new JeecgBootException("任务ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();
        String workerId = resolveWorkerId();

        try {
            int updated = aiTaskMapper.updateTaskStatusWithLock(taskId, tenantId,
                    STATUS_PENDING, STATUS_PROCESSING, workerId);
            if (updated <= 0) {
                log.info("抢占任务失败(可能已被抢占或不存在): taskId={}, tenantId={}", taskId, tenantId);
                return false;
            }

            UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", taskId);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("task_status", STATUS_PROCESSING);
            wrapper.set("started_at", new Date());
            int startedUpdated = aiTaskMapper.update(null, wrapper);
            if (startedUpdated <= 0) {
                log.warn("写入任务开始时间失败: taskId={}, tenantId={}, workerId={}", taskId, tenantId, workerId);
                throw new JeecgBootException("抢占任务失败");
            }

            log.info("抢占任务成功: taskId={}, tenantId={}, workerId={}", taskId, tenantId, workerId);
            return true;
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("抢占任务异常: taskId={}, tenantId={}, workerId={}", taskId, tenantId, workerId, e);
            throw new JeecgBootException("抢占任务失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeTask(String taskId, String processResult) {
        if (oConvertUtils.isEmpty(taskId) || taskId.isBlank()) {
            throw new JeecgBootException("任务ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();

        try {
            QueryWrapper<AinoteAiTask> query = new QueryWrapper<>();
            query.eq("id", taskId);
            query.eq("tenant_id", tenantId);
            query.eq("task_status", STATUS_PROCESSING);
            AinoteAiTask task = aiTaskMapper.selectOne(query);
            if (task == null) {
                log.info("完成任务失败(任务不存在或状态不匹配): taskId={}, tenantId={}", taskId, tenantId);
                return false;
            }

            Date completedAt = new Date();
            long duration = 0L;
            if (task.getStartedAt() != null) {
                duration = completedAt.getTime() - task.getStartedAt().getTime();
                if (duration < 0) {
                    duration = 0L;
                }
            } else {
                log.warn("任务开始时间为空，将duration置为0: taskId={}, tenantId={}", taskId, tenantId);
            }

            UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", taskId);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("task_status", STATUS_PROCESSING);
            wrapper.set("task_status", STATUS_COMPLETED);
            wrapper.set("completed_at", completedAt);
            wrapper.set("duration", duration);
            wrapper.set("process_result", processResult);

            int updated = aiTaskMapper.update(null, wrapper);
            if (updated <= 0) {
                log.info("完成任务失败(更新未命中): taskId={}, tenantId={}", taskId, tenantId);
                return false;
            }

            log.info("完成任务成功: taskId={}, tenantId={}, durationMs={}", taskId, tenantId, duration);
            return true;
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("完成任务异常: taskId={}, tenantId={}", taskId, tenantId, e);
            throw new JeecgBootException("完成任务失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean failTask(String taskId, String errorMessage) {
        if (oConvertUtils.isEmpty(taskId) || taskId.isBlank()) {
            throw new JeecgBootException("任务ID不能为空");
        }
        if (oConvertUtils.isEmpty(errorMessage) || errorMessage.isBlank()) {
            throw new JeecgBootException("错误信息不能为空");
        }
        Integer tenantId = getRequiredTenantId();

        try {
            QueryWrapper<AinoteAiTask> query = new QueryWrapper<>();
            query.eq("id", taskId);
            query.eq("tenant_id", tenantId);
            query.eq("task_status", STATUS_PROCESSING);
            AinoteAiTask task = aiTaskMapper.selectOne(query);
            if (task == null) {
                log.info("失败任务更新未命中(任务不存在或状态不匹配): taskId={}, tenantId={}", taskId, tenantId);
                return false;
            }

            int currentRetry = task.getRetryCount() == null ? 0 : task.getRetryCount();
            int nextRetry = currentRetry + 1;

            // 检查是否超过最大重试次数
            if (nextRetry > MAX_RETRY_COUNT) {
                // 超过最大重试次数，标记为永久失败
                UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", taskId);
                wrapper.eq("tenant_id", tenantId);
                wrapper.eq("task_status", STATUS_PROCESSING);
                wrapper.set("task_status", STATUS_FAILED);
                wrapper.set("error_message", errorMessage + " (已达最大重试次数)");
                wrapper.set("retry_count", nextRetry);
                wrapper.set("next_retry_at", null);

                int updated = aiTaskMapper.update(null, wrapper);
                if (updated <= 0) {
                    log.info("失败任务更新失败(更新未命中): taskId={}, tenantId={}", taskId, tenantId);
                    return false;
                }

                log.error("任务永久失败(已达最大重试次数): taskId={}, tenantId={}, retryCount={}",
                        taskId, tenantId, nextRetry);
                return true;
            }

            // 未超过最大重试次数，设置重试时间并回到 PENDING 状态
            int backoffMinutes = calcBackoffMinutes(nextRetry);
            Date nextRetryAt = new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(backoffMinutes));

            UpdateWrapper<AinoteAiTask> wrapper = new UpdateWrapper<>();
            wrapper.eq("id", taskId);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("task_status", STATUS_PROCESSING);
            wrapper.set("task_status", STATUS_PENDING);
            wrapper.set("error_message", errorMessage);
            wrapper.set("retry_count", nextRetry);
            wrapper.set("next_retry_at", nextRetryAt);

            int updated = aiTaskMapper.update(null, wrapper);
            if (updated <= 0) {
                log.info("失败任务更新失败(更新未命中): taskId={}, tenantId={}", taskId, tenantId);
                return false;
            }

            log.warn("任务处理失败(将重试): taskId={}, tenantId={}, retryCount={}, nextRetryAt={}",
                    taskId, tenantId, nextRetry, nextRetryAt);
            return true;
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("失败任务处理异常: taskId={}, tenantId={}", taskId, tenantId, e);
            throw new JeecgBootException("失败任务处理失败");
        }
    }

    @Override
    public List<AinoteAiTask> getTasksByNoteId(String noteId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();
        return aiTaskMapper.selectByNoteId(noteId, tenantId);
    }

    @Override
    public List<AinoteAiTask> getTasksByMaterialId(String materialId) {
        if (oConvertUtils.isEmpty(materialId) || materialId.isBlank()) {
            throw new JeecgBootException("素材ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();
        return aiTaskMapper.selectByMaterialId(materialId, tenantId);
    }

    @Override
    public long countCompletedTasks(String noteId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();
        Long count = aiTaskMapper.countByStatus(noteId, STATUS_COMPLETED, tenantId);
        return count == null ? 0L : count;
    }

    @Override
    public long countTotalTasks(String noteId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();

        QueryWrapper<AinoteAiTask> wrapper = new QueryWrapper<>();
        wrapper.eq("note_id", noteId);
        wrapper.eq("tenant_id", tenantId);

        Long count = aiTaskMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    private String resolveWorkerId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (oConvertUtils.isEmpty(host) || host.isBlank()) {
                return "unknown";
            }
            return host;
        } catch (Exception e) {
            log.warn("获取workerId失败，降级为unknown", e);
            return "unknown";
        }
    }

    private int calcBackoffMinutes(int retryCount) {
        int index = Math.max(0, retryCount - 1);
        if (index >= BACKOFF_MINUTES.length) {
            return BACKOFF_MINUTES[BACKOFF_MINUTES.length - 1];
        }
        return BACKOFF_MINUTES[index];
    }

    private LoginUser getCurrentUser() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        return user;
    }

    private Integer getRequiredTenantId() {
        Integer tenantId = getTenantIdFromSecurityUtils();
        if (tenantId != null) {
            return tenantId;
        }
        String tenantIdStr = TenantContext.getTenant();
        if (oConvertUtils.isEmpty(tenantIdStr) || "undefined".equalsIgnoreCase(tenantIdStr)) {
            return 0;
        }
        try {
            return Integer.parseInt(tenantIdStr);
        } catch (NumberFormatException e) {
            log.warn("租户ID格式错误: {}", tenantIdStr);
            return 0;
        }
    }

    private Integer getTenantIdFromSecurityUtils() {
        try {
            Session session = SecurityUtils.getSubject() == null ? null : SecurityUtils.getSubject().getSession(false);
            if (session == null) {
                return null;
            }
            Object value = session.getAttribute("tenantId");
            if (value == null) {
                return null;
            }
            String tenantIdStr = String.valueOf(value);
            if (oConvertUtils.isEmpty(tenantIdStr) || "undefined".equalsIgnoreCase(tenantIdStr)) {
                return null;
            }
            return Integer.parseInt(tenantIdStr);
        } catch (Exception e) {
            log.debug("从SecurityUtils获取租户ID失败", e);
            return null;
        }
    }
}
