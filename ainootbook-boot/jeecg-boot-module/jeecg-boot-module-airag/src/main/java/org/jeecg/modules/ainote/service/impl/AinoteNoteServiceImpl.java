package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteRegenerateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.entity.AinoteNoteVersion;
import org.jeecg.modules.ainote.facade.AinoteGenerationFacade;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteAiTaskService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.service.IAinoteNoteVersionService;
import org.jeecg.modules.ainote.service.MarkdownPrecompileService;
import org.jeecg.modules.ainote.vo.AinoteNoteRegenerateVO;
import org.jeecg.modules.ainote.vo.AinoteNoteVersionVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.security.SecureRandom;
import java.util.*;

/**
 * 笔记表 Service 实现
 */
@Slf4j
@Service
public class AinoteNoteServiceImpl extends ServiceImpl<AinoteNoteMapper, AinoteNote>
        implements IAinoteNoteService {

    private static final int NOTE_STATUS_DRAFT = 1;
    private static final int NOTE_STATUS_DONE = 2;
    private static final int NOTE_STATUS_DELETED = 3;

    private static final int SHARE_CODE_LENGTH = 16;
    private static final String SHARE_CODE_CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SHARE_CODE_MAX_RETRY = 10;
    /** WR-05: 关键词最大数量 */
    private static final int MAX_KEYWORD_COUNT = 5;
    private static final String EMBED_LOCK_PREFIX = "ainote:embed:";
    private static final java.time.Duration EMBED_LOCK_TTL = java.time.Duration.ofSeconds(120);

    @Autowired
    private AinoteEmbeddingService ainoteEmbeddingService;

    @Autowired
    private IAinoteAiConfigService ainoteAiConfigService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IAinoteNoteVersionService ainoteNoteVersionService;

    @Lazy
    @Autowired
    private AinoteGenerationFacade generationFacade;

    @Autowired
    private IAinoteAiTaskService ainoteAiTaskService;

    @Autowired
    private MarkdownPrecompileService markdownPrecompileService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void applyDataPermission(QueryWrapper<AinoteNote> wrapper) {
        if (wrapper == null) {
            return;
        }
        LoginUser user = getCurrentUser();
        Integer tenantId = getRequiredTenantId();

        // 租户隔离（强制）
        wrapper.eq("tenant_id", tenantId);
        // 默认不展示已删除
        wrapper.ne("note_status", NOTE_STATUS_DELETED);

        // 管理员可查看当前租户全部
        if (isAdmin(user)) {
            return;
        }

        // 检查是否为教师（通过 teaching 表关联）
        boolean isTeacher = isTeacher(user.getId(), tenantId);

        // 数据权限过滤：
        // 1. 本人笔记（student_id 或 create_by）
        // 2. 公开笔记
        // 3. 教师可查看其所授课程学生的笔记（通过 teaching_id 关联）
        if (isTeacher) {
            // WR-06: 使用参数化查询防止 SQL 注入
            wrapper.and(w -> w.eq("student_id", user.getId())
                    .or().eq("create_by", user.getId())
                    .or().eq("is_public", 1)
                    .or().apply("teaching_id IN (SELECT id FROM ainote_teaching WHERE teacher_id = {0} AND tenant_id = {1})",
                            user.getId(), tenantId));
        } else {
            // 普通用户：本人 + 公开
            wrapper.and(w -> w.eq("student_id", user.getId())
                    .or().eq("create_by", user.getId())
                    .or().eq("is_public", 1));
        }
    }

    /**
     * WR-07 + WR-06: 检查用户是否为教师，使用参数化查询
     */
    private boolean isTeacher(String userId, Integer tenantId) {
        try {
            QueryWrapper<AinoteNote> checkWrapper = new QueryWrapper<>();
            // 使用 apply + 参数化占位符防止 SQL 注入
            checkWrapper.apply(
                    "EXISTS (SELECT 1 FROM ainote_teaching t WHERE t.teacher_id = {0} AND t.tenant_id = {1} LIMIT 1)",
                    userId, tenantId);
            return baseMapper.selectCount(checkWrapper) > 0;
        } catch (Exception e) {
            log.debug("检查教师身份失败，可能 teaching 表不存在: {}", e.getMessage());
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNote(AinoteNoteCreateDTO dto) {
        LoginUser user = getCurrentUser();

        AinoteNote note = new AinoteNote();
        note.setStudentId(user.getId());
        note.setCourseId(dto.getCourseId());
        note.setChapterId(dto.getChapterId());
        note.setTeachingId(dto.getTeachingId());
        note.setNoteTitle(dto.getNoteTitle());
        note.setNoteContent(dto.getNoteContent());
        note.setAiSummary(dto.getAiSummary());
        // WR-05: 关键词数量校验
        validateKeywordCount(dto.getKeywords());
        note.setKeywords(dto.getKeywords());
        note.setCurrentVersion(1);

        Integer noteStatus = dto.getNoteStatus() == null ? NOTE_STATUS_DRAFT : dto.getNoteStatus();
        if (!Set.of(NOTE_STATUS_DRAFT, NOTE_STATUS_DONE).contains(noteStatus)) {
            throw new JeecgBootException("笔记状态不合法，仅支持 1-草稿、2-已完成");
        }
        note.setNoteStatus(noteStatus);

        Integer isPublic = dto.getIsPublic() == null ? 0 : dto.getIsPublic();
        if (!Set.of(0, 1).contains(isPublic)) {
            throw new JeecgBootException("是否公开不合法，仅支持 0-私有、1-公开");
        }
        note.setIsPublic(isPublic);

        Integer tenantId = getCurrentTenantId();
        if (tenantId != null) {
            note.setTenantId(tenantId);
        }
        note.setCreateBy(user.getId());
        note.setUpdateBy(user.getId());
        note.setSysOrgCode(user.getOrgCode());

        if (oConvertUtils.isNotEmpty(note.getNoteContent())) {
            note.setRenderedContent(markdownPrecompileService.precompile(note.getNoteContent()));
        }

        save(note);
        return note.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNote(AinoteNoteUpdateDTO dto) {
        LoginUser user = getCurrentUser();

        // 仅 owner 或 admin 可编辑（owner = student_id）
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.eq("id", dto.getId());
        Integer tenantId = getRequiredTenantId();
        wrapper.eq("tenant_id", tenantId);
        wrapper.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            wrapper.eq("student_id", user.getId());
        }
        AinoteNote existing = getOne(wrapper);
        if (existing == null) {
            throw new JeecgBootException("数据不存在或无权限编辑");
        }

        AinoteNote note = new AinoteNote();
        note.setId(dto.getId());
        if (oConvertUtils.isNotEmpty(dto.getCourseId())) {
            note.setCourseId(dto.getCourseId());
        }
        if (dto.getChapterId() != null) {
            note.setChapterId(dto.getChapterId());
        }
        if (dto.getTeachingId() != null) {
            note.setTeachingId(dto.getTeachingId());
        }
        if (dto.getNoteTitle() != null) {
            note.setNoteTitle(dto.getNoteTitle());
        }
        if (dto.getNoteContent() != null) {
            note.setNoteContent(dto.getNoteContent());
            if (oConvertUtils.isNotEmpty(dto.getNoteContent())) {
                note.setRenderedContent(markdownPrecompileService.precompile(dto.getNoteContent()));
            } else {
                note.setRenderedContent("");
            }
        }
        if (dto.getAiSummary() != null) {
            note.setAiSummary(dto.getAiSummary());
        }
        if (dto.getKeywords() != null) {
            validateKeywordCount(dto.getKeywords());
            note.setKeywords(dto.getKeywords());
        }
        if (dto.getNoteStatus() != null) {
            if (!Set.of(NOTE_STATUS_DRAFT, NOTE_STATUS_DONE).contains(dto.getNoteStatus())) {
                throw new JeecgBootException("笔记状态不合法，仅支持 1-草稿、2-已完成");
            }
            note.setNoteStatus(dto.getNoteStatus());
        }
        if (dto.getIsPublic() != null) {
            if (!Set.of(0, 1).contains(dto.getIsPublic())) {
                throw new JeecgBootException("是否公开不合法，仅支持 0-私有、1-公开");
            }
            note.setIsPublic(dto.getIsPublic());
        }

        note.setUpdateBy(user.getId());

        // 版本管理：内容实际变更时推进版本号
        Integer currentVersion = existing.getCurrentVersion() != null ? existing.getCurrentVersion() : 1;
        boolean contentActuallyChanged = dto.getNoteContent() != null
                && !Objects.equals(oConvertUtils.getString(existing.getNoteContent(), ""), dto.getNoteContent());
        Integer newVersion = null;
        if (contentActuallyChanged) {
            ensureVersionSnapshot(existing, currentVersion, tenantId);
            newVersion = resolveNextVersionNumber(existing.getId(), tenantId, currentVersion);
            note.setCurrentVersion(newVersion);
        }

        boolean isPublicChanged = dto.getIsPublic() != null
                && !Objects.equals(dto.getIsPublic(), existing.getIsPublic());
        boolean isCourseIdChanged = oConvertUtils.isNotEmpty(dto.getCourseId())
                && !Objects.equals(dto.getCourseId(), existing.getCourseId());
        updateById(note);

        // 内容变更后保存新版本快照
        if (contentActuallyChanged && newVersion != null) {
            saveVersionSnapshot(existing.getId(), newVersion, dto.getNoteContent(),
                    dto.getAiSummary() != null ? dto.getAiSummary() : existing.getAiSummary(),
                    dto.getKeywords() != null ? dto.getKeywords() : existing.getKeywords(),
                    user.getId(), new Date(), tenantId);
        }

        boolean needsReEmbed = isPublicChanged || isCourseIdChanged;
        if (needsReEmbed) {
            try {
                String knowledgeId = ainoteAiConfigService.getConfig(0).getKnowledgeId();
                if (oConvertUtils.isNotEmpty(knowledgeId)) {
                    String lockKey = EMBED_LOCK_PREFIX + dto.getId();
                    String lockValue = UUID.randomUUID().toString();
                    Boolean acquired = stringRedisTemplate.opsForValue()
                            .setIfAbsent(lockKey, lockValue, EMBED_LOCK_TTL);
                    if (Boolean.TRUE.equals(acquired)) {
                        try {
                            AinoteNote latest = getById(dto.getId());
                            if (latest != null && !Objects.equals(latest.getNoteStatus(), NOTE_STATUS_DELETED)) {
                                ainoteEmbeddingService.deleteNoteEmbedding(dto.getId(), knowledgeId);
                                ainoteEmbeddingService.embedNote(latest, knowledgeId);
                                log.info("笔记元数据变更，已重建向量: noteId={}, knowledgeId={}",
                                        dto.getId(), knowledgeId);
                            }
                        } finally {
                            releaseLock(lockKey, lockValue);
                        }
                    } else {
                        log.info("向量化锁被占用，跳过: noteId={}", dto.getId());
                    }
                }
            } catch (Exception e) {
                log.warn("笔记元数据变更后重建向量失败: noteId={}, err={}",
                        dto.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AinoteNote rollbackToVersion(String noteId, Integer targetVersion) {
        LoginUser user = getCurrentUser();
        Integer tenantId = getRequiredTenantId();

        QueryWrapper<AinoteNote> noteWrapper = new QueryWrapper<>();
        noteWrapper.eq("id", noteId);
        noteWrapper.eq("tenant_id", tenantId);
        noteWrapper.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            noteWrapper.eq("student_id", user.getId());
        }
        AinoteNote existing = getOne(noteWrapper);
        if (existing == null) {
            throw new JeecgBootException("笔记不存在或无权限回滚");
        }

        QueryWrapper<AinoteNoteVersion> versionWrapper = new QueryWrapper<>();
        versionWrapper.eq("note_id", noteId);
        versionWrapper.eq("version_number", targetVersion);
        versionWrapper.eq("tenant_id", tenantId);
        AinoteNoteVersion target = ainoteNoteVersionService.getOne(versionWrapper);
        if (target == null) {
            throw new JeecgBootException("目标版本不存在");
        }

        // 保存回滚前状态快照
        Integer currentVersion = existing.getCurrentVersion() != null ? existing.getCurrentVersion() : 1;
        ensureVersionSnapshot(existing, currentVersion, tenantId);

        // 生成新版本号（append-only，不回退版本号）
        Integer newVersion = resolveNextVersionNumber(noteId, tenantId, currentVersion);
        Date rollbackTime = new Date();

        // 使用 UpdateWrapper 带全部条件防止 lost update
        UpdateWrapper<AinoteNote> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", noteId);
        updateWrapper.eq("tenant_id", tenantId);
        updateWrapper.ne("note_status", NOTE_STATUS_DELETED);
        updateWrapper.eq("current_version", currentVersion);
        if (!isAdmin(user)) {
            updateWrapper.eq("student_id", user.getId());
        }
        updateWrapper.set("note_content", target.getNoteContent());
        updateWrapper.set("rendered_content", markdownPrecompileService.precompile(target.getNoteContent()));
        updateWrapper.set("ai_summary", target.getAiSummary());
        updateWrapper.set("keywords", target.getKeywords());
        updateWrapper.set("current_version", newVersion);
        updateWrapper.set("update_by", user.getId());
        updateWrapper.set("update_time", rollbackTime);

        boolean success = update(null, updateWrapper);
        if (!success) {
            throw new JeecgBootException("回滚失败，笔记版本已变更，请刷新后重试", HttpStatus.CONFLICT.value());
        }

        // 保存回滚结果的版本快照
        saveVersionSnapshot(noteId, newVersion, target.getNoteContent(),
                target.getAiSummary(), target.getKeywords(),
                user.getId(), rollbackTime, tenantId);

        return getById(noteId);
    }

    @Override
    public AinoteNoteRegenerateVO regenerateNote(AinoteNoteRegenerateDTO dto) {
        LoginUser user = getCurrentUser();
        Integer tenantId = getRequiredTenantId();

        // Phase 1: 权限校验 + 版本校验（无事务）
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.eq("id", dto.getNoteId());
        wrapper.eq("tenant_id", tenantId);
        wrapper.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            wrapper.eq("student_id", user.getId());
        }
        AinoteNote existing = getOne(wrapper);
        if (existing == null) {
            throw new JeecgBootException("笔记不存在或无权限编辑");
        }

        Integer baseVersion = dto.getBaseVersion();
        if (!Objects.equals(existing.getCurrentVersion(), baseVersion)) {
            throw new JeecgBootException("笔记版本已变更，请刷新后重试", HttpStatus.CONFLICT.value());
        }

        // Phase 1: LLM 调用（无事务，避免长时间占用数据库连接）
        String regeneratedContent = generationFacade.regenerateNoteContent(existing, dto.getAdditionalContent());
        if (oConvertUtils.isEmpty(regeneratedContent)) {
            throw new JeecgBootException("重新生成失败，未返回有效内容");
        }

        boolean contentChanged = !Objects.equals(oConvertUtils.getString(existing.getNoteContent(), ""), regeneratedContent);
        String renderedContent = markdownPrecompileService.precompile(regeneratedContent);
        Date regenerateTime = new Date();

        // Phase 2: 编程式事务，仅包含 DB 操作
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        Integer newVersion = txTemplate.execute(status -> {
            Integer resolvedVersion = resolveNextVersionNumber(existing.getId(), tenantId, baseVersion);

            UpdateWrapper<AinoteNote> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", existing.getId());
            updateWrapper.eq("tenant_id", tenantId);
            updateWrapper.ne("note_status", NOTE_STATUS_DELETED);
            updateWrapper.eq("current_version", baseVersion);
            if (!isAdmin(user)) {
                updateWrapper.eq("student_id", user.getId());
            }
            updateWrapper.set("note_content", regeneratedContent);
            updateWrapper.set("rendered_content", renderedContent);
            updateWrapper.set("current_version", resolvedVersion);
            updateWrapper.set("update_by", user.getId());
            updateWrapper.set("update_time", regenerateTime);
            if (contentChanged) {
                updateWrapper.set("ai_summary", null);
                updateWrapper.set("keywords", null);
            }

            boolean updated = update(null, updateWrapper);
            if (!updated) {
                throw new JeecgBootException("笔记版本已变更，请刷新后重试", HttpStatus.CONFLICT.value());
            }

            ensureVersionSnapshot(existing, baseVersion, tenantId);
            saveVersionSnapshot(existing.getId(), resolvedVersion, regeneratedContent,
                    contentChanged ? null : existing.getAiSummary(),
                    contentChanged ? null : existing.getKeywords(),
                    user.getId(), regenerateTime, tenantId);

            if (contentChanged) {
                triggerSummaryRefresh(existing.getId());
            }
            return resolvedVersion;
        });

        if (newVersion == null) {
            throw new JeecgBootException("重新生成失败，版本写入异常");
        }

        // 向量重建在事务外执行（网络调用）
        if (contentChanged) {
            rebuildNoteEmbedding(existing.getId(), tenantId);
        }

        AinoteNoteRegenerateVO vo = new AinoteNoteRegenerateVO();
        vo.setVersion(newVersion);
        vo.setNoteContent(regeneratedContent);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteLogicalById(String id) {
        LoginUser user = getCurrentUser();

        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        Integer tenantId = getRequiredTenantId();
        wrapper.eq("tenant_id", tenantId);
        wrapper.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            wrapper.eq("student_id", user.getId());
        }
        AinoteNote existing = getOne(wrapper);
        if (existing == null) {
            throw new JeecgBootException("数据不存在或无权限删除");
        }

        AinoteNote update = new AinoteNote();
        update.setId(id);
        update.setNoteStatus(NOTE_STATUS_DELETED);
        update.setUpdateBy(user.getId());
        boolean success = updateById(update);
        if (success) {
            try {
                String knowledgeId = ainoteAiConfigService.getConfig(0).getKnowledgeId();
                if (oConvertUtils.isNotEmpty(knowledgeId)) {
                    ainoteEmbeddingService.deleteNoteEmbedding(id, knowledgeId);
                }
            } catch (Exception e) {
                log.warn("删除笔记向量失败，已忽略: noteId={}, err={}", id, e.getMessage());
            }
        }
        return success;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteLogicalByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return true;
        }
        LoginUser user = getCurrentUser();
        Integer tenantId = getRequiredTenantId();

        // 去重后的 ID 列表
        List<String> uniqueIds = ids.stream().distinct().toList();

        QueryWrapper<AinoteNote> query = new QueryWrapper<>();
        query.in("id", uniqueIds);
        query.eq("tenant_id", tenantId);
        query.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            query.eq("student_id", user.getId());
        }

        List<AinoteNote> records = list(query);
        if (records.size() != uniqueIds.size()) {
            throw new JeecgBootException("部分记录无权限删除或不存在");
        }

        UpdateWrapper<AinoteNote> updateWrapper = new UpdateWrapper<>();
        updateWrapper.in("id", uniqueIds);
        updateWrapper.eq("tenant_id", tenantId);
        if (!isAdmin(user)) {
            updateWrapper.eq("student_id", user.getId());
        }
        updateWrapper.ne("note_status", NOTE_STATUS_DELETED);

        AinoteNote update = new AinoteNote();
        update.setNoteStatus(NOTE_STATUS_DELETED);
        update.setUpdateBy(user.getId());
        return update(update, updateWrapper);
    }

    @Override
    public AinoteNote getByIdWithPermission(String id) {
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id);
        applyDataPermission(wrapper);
        return getOne(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AinoteNoteShareVO createShare(AinoteNoteShareCreateDTO dto) {
        LoginUser user = getCurrentUser();

        // 仅 owner/admin 可分享
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();
        wrapper.eq("id", dto.getNoteId());
        Integer tenantId = getCurrentTenantId();
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }
        wrapper.ne("note_status", NOTE_STATUS_DELETED);
        if (!isAdmin(user)) {
            wrapper.eq("student_id", user.getId());
        }
        AinoteNote note = getOne(wrapper);
        if (note == null) {
            throw new JeecgBootException("笔记不存在或无权限分享");
        }

        Integer shareType = dto.getShareType() == null ? 1 : dto.getShareType();
        if (!Set.of(1, 2).contains(shareType)) {
            throw new JeecgBootException("分享类型不合法，仅支持 1-链接分享、2-二维码分享");
        }

        Date expireTime = dto.getExpireTime();
        if (expireTime != null && expireTime.before(new Date())) {
            throw new JeecgBootException("过期时间不能早于当前时间");
        }

        // 修复：分享租户ID必须与笔记租户ID一致，且不能为空
        Integer shareTenantId = note.getTenantId();
        if (shareTenantId == null) {
            // 笔记租户ID为空视为数据异常，使用当前租户ID
            shareTenantId = getRequiredTenantId();
            log.warn("笔记 {} 的租户ID为空，使用当前租户ID: {}", note.getId(), shareTenantId);
        }

        String shareId = IdWorker.getIdStr();
        for (int i = 0; i < SHARE_CODE_MAX_RETRY; i++) {
            String shareCode = generateShareCode(SHARE_CODE_LENGTH);
            try {
                baseMapper.insertShare(
                        shareId,
                        note.getId(),
                        shareCode,
                        shareType,
                        expireTime,
                        user.getId(),
                        note.getSysOrgCode(),
                        shareTenantId
                );
                AinoteNoteShareVO vo = new AinoteNoteShareVO();
                vo.setShareId(shareId);
                vo.setNoteId(note.getId());
                vo.setShareCode(shareCode);
                vo.setShareType(shareType);
                vo.setExpireTime(expireTime);
                return vo;
            } catch (DuplicateKeyException e) {
                // share_code 唯一约束冲突，重试
                log.warn("生成分享码冲突，重试: attempt={}", i + 1);
                shareId = IdWorker.getIdStr();
            }
        }
        throw new JeecgBootException("生成分享码失败，请稍后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AinoteNoteShareDetailVO queryByShareCode(String shareCode) {
        if (shareCode == null || shareCode.isBlank()) {
            throw new JeecgBootException("分享码不能为空");
        }
        // 租户隔离：传入当前租户ID进行过滤
        Integer tenantId = getCurrentTenantId();
        AinoteNoteShareDetailVO vo = baseMapper.selectShareDetailByCode(shareCode, tenantId);
        if (vo == null) {
            return null;
        }
        try {
            baseMapper.incShareViewCount(vo.getShareId());
            baseMapper.incNoteViewCount(vo.getNoteId());
        } catch (Exception e) {
            // 查看次数统计失败不影响主流程
            log.warn("更新分享/笔记查看次数失败: shareId={}, noteId={}",
                    vo.getShareId(), vo.getNoteId(), e);
        }
        return vo;
    }

    private String generateShareCode(int length) {
        int n = SHARE_CODE_CHARS.length();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SHARE_CODE_CHARS.charAt(secureRandom.nextInt(n)));
        }
        return sb.toString();
    }

    @Override
    public IPage<AinoteNote> queryPublicNotes(Integer pageNo, Integer pageSize, String keyword) {
        QueryWrapper<AinoteNote> wrapper = new QueryWrapper<>();

        // 租户隔离
        Integer tenantId = getCurrentTenantId();
        if (tenantId != null) {
            wrapper.eq("tenant_id", tenantId);
        }

        // 仅公开笔记
        wrapper.eq("is_public", 1);
        // 排除已删除
        wrapper.ne("note_status", NOTE_STATUS_DELETED);

        // 关键词搜索（标题或摘要）
        if (oConvertUtils.isNotEmpty(keyword)) {
            wrapper.and(w -> w.like("note_title", keyword)
                    .or().like("ai_summary", keyword)
                    .or().like("keywords", keyword));
        }

        // 按更新时间倒序
        wrapper.orderByDesc("update_time");

        Page<AinoteNote> page = new Page<>(pageNo, pageSize);
        return page(page, wrapper);
    }

    @Override
    public IPage<AinoteNoteVersionVO> queryVersionPage(String noteId, Integer pageNo, Integer pageSize) {
        AinoteNote note = getByIdWithPermission(noteId);
        if (note == null) {
            throw new JeecgBootException("笔记不存在或无权限访问");
        }
        return ainoteNoteVersionService.queryVersionPage(
                noteId, getRequiredTenantId(), pageNo, pageSize);
    }

    private void ensureVersionSnapshot(AinoteNote note, Integer versionNumber, Integer tenantId) {
        QueryWrapper<AinoteNoteVersion> versionWrapper = new QueryWrapper<>();
        versionWrapper.eq("note_id", note.getId());
        versionWrapper.eq("version_number", versionNumber);
        versionWrapper.eq("tenant_id", tenantId);
        versionWrapper.select("id");
        AinoteNoteVersion existingVersion = ainoteNoteVersionService.getOne(versionWrapper, false);
        if (existingVersion != null) {
            return;
        }
        saveVersionSnapshot(note.getId(), versionNumber, note.getNoteContent(), note.getAiSummary(),
                note.getKeywords(), resolveVersionCreatedBy(note), resolveVersionCreatedAt(note), tenantId);
    }

    private void saveVersionSnapshot(String noteId, Integer versionNumber, String noteContent, String aiSummary,
                                     String keywords, String createdBy, Date createdAt, Integer tenantId) {
        AinoteNoteVersion version = new AinoteNoteVersion();
        version.setNoteId(noteId);
        version.setVersionNumber(versionNumber);
        version.setNoteContent(noteContent);
        version.setAiSummary(aiSummary);
        version.setKeywords(keywords);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(createdAt != null ? createdAt : new Date());
        version.setTenantId(tenantId);
        try {
            if (!ainoteNoteVersionService.save(version)) {
                throw new JeecgBootException("保存笔记版本失败");
            }
        } catch (DuplicateKeyException e) {
            log.warn("笔记版本快照已存在，跳过保存: noteId={}, versionNumber={}", noteId, versionNumber);
        }
    }

    private String resolveVersionCreatedBy(AinoteNote note) {
        if (note == null) {
            return null;
        }
        if (oConvertUtils.isNotEmpty(note.getUpdateBy())) {
            return note.getUpdateBy();
        }
        return note.getCreateBy();
    }

    private Date resolveVersionCreatedAt(AinoteNote note) {
        if (note == null) {
            return new Date();
        }
        if (note.getUpdateTime() != null) {
            return note.getUpdateTime();
        }
        if (note.getCreateTime() != null) {
            return note.getCreateTime();
        }
        return new Date();
    }

    private Integer resolveNextVersionNumber(String noteId, Integer tenantId, Integer currentVersion) {
        QueryWrapper<AinoteNoteVersion> versionWrapper = new QueryWrapper<>();
        versionWrapper.eq("note_id", noteId);
        versionWrapper.eq("tenant_id", tenantId);
        versionWrapper.select("version_number");
        versionWrapper.orderByDesc("version_number");
        versionWrapper.last("LIMIT 1");
        AinoteNoteVersion latestVersion = ainoteNoteVersionService.getOne(versionWrapper, false);
        int maxPersistedVersion = latestVersion != null && latestVersion.getVersionNumber() != null
                ? latestVersion.getVersionNumber() : 0;
        int current = currentVersion != null ? currentVersion : 0;
        return Math.max(current, maxPersistedVersion) + 1;
    }

    private void triggerSummaryRefresh(String noteId) {
        try {
            ainoteAiTaskService.createTask(noteId, null, "summary");
        } catch (JeecgBootException e) {
            if (isDuplicateActiveTaskError(e)) {
                log.info("摘要任务已在处理中，跳过重复创建: noteId={}", noteId);
                return;
            }
            log.warn("创建摘要任务失败，已忽略: noteId={}, err={}", noteId, e.getMessage());
        } catch (Exception e) {
            log.warn("创建摘要任务异常，已忽略: noteId={}, err={}", noteId, e.getMessage());
        }
    }

    private void rebuildNoteEmbedding(String noteId, Integer tenantId) {
        try {
            String knowledgeId = ainoteAiConfigService.getConfig(tenantId).getKnowledgeId();
            if (oConvertUtils.isEmpty(knowledgeId)) {
                return;
            }
            AinoteNote latest = getById(noteId);
            if (latest == null || Objects.equals(latest.getNoteStatus(), NOTE_STATUS_DELETED)) {
                return;
            }
            ainoteEmbeddingService.deleteNoteEmbedding(noteId, knowledgeId);
            ainoteEmbeddingService.embedNote(latest, knowledgeId);
        } catch (Exception e) {
            log.warn("笔记重生成后重建向量失败: noteId={}, err={}", noteId, e.getMessage());
        }
    }

    private boolean isDuplicateActiveTaskError(JeecgBootException ex) {
        return ex != null
                && oConvertUtils.isNotEmpty(ex.getMessage())
                && ex.getMessage().contains("已有相同类型的任务在处理中");
    }

    private LoginUser getCurrentUser() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        return user;
    }

    private boolean isAdmin(LoginUser user) {
        String roleCode = user.getRoleCode();
        if (roleCode == null) {
            return false;
        }
        Set<String> roles = new HashSet<>(Arrays.asList(roleCode.split(",")));
        return roles.contains("admin");
    }

    private Integer getCurrentTenantId() {
        try {
            HttpServletRequest request = SpringContextUtils.getHttpServletRequest();
            String tenantIdStr = TokenUtils.getTenantIdByRequest(request);
            if (oConvertUtils.isNotEmpty(tenantIdStr)) {
                return Integer.parseInt(tenantIdStr);
            }
        } catch (Exception e) {
            log.warn("获取租户ID失败", e);
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<?> importExcelWithSecurity(HttpServletRequest request, HttpServletResponse response, LoginUser user) {
        try {
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
            if (fileMap.isEmpty()) {
                return Result.error("请选择要导入的文件");
            }

            MultipartFile file = fileMap.values().iterator().next();
            ImportParams params = new ImportParams();
            params.setTitleRows(2);
            params.setHeadRows(1);
            params.setNeedSave(false);

            List<AinoteNote> list = ExcelImportUtil.importExcel(file.getInputStream(), AinoteNote.class, params);
            if (list == null || list.isEmpty()) {
                return Result.error("导入数据为空");
            }

            // 获取当前租户ID
            Integer tenantId = getCurrentTenantId();

            // 安全加固：强制覆盖敏感字段
            for (AinoteNote note : list) {
                // 强制设置 studentId 为导入用户
                note.setStudentId(user.getId());
                // 强制设置租户ID
                if (tenantId != null) {
                    note.setTenantId(tenantId);
                }
                // 强制设置创建人/更新人
                note.setCreateBy(user.getId());
                note.setUpdateBy(user.getId());
                // 强制设置组织机构
                note.setSysOrgCode(user.getOrgCode());

                // 校验笔记状态
                if (note.getNoteStatus() == null) {
                    note.setNoteStatus(NOTE_STATUS_DRAFT);
                } else if (!Set.of(NOTE_STATUS_DRAFT, NOTE_STATUS_DONE).contains(note.getNoteStatus())) {
                    note.setNoteStatus(NOTE_STATUS_DRAFT);
                }

                // 校验是否公开
                if (note.getIsPublic() == null || !Set.of(0, 1).contains(note.getIsPublic())) {
                    note.setIsPublic(0);
                }
            }

            // 批量保存
            saveBatch(list);
            log.info("管理员 {} 导入笔记 {} 条", user.getUsername(), list.size());

            return Result.ok("导入成功，共导入 " + list.size() + " 条数据");
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    /**
     * WR-05: 校验关键词数量不超过 MAX_KEYWORD_COUNT
     */
    private void validateKeywordCount(String keywords) {
        if (oConvertUtils.isEmpty(keywords)) {
            return;
        }
        String[] parts = keywords.split(",");
        long count = 0;
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                count++;
            }
        }
        if (count > MAX_KEYWORD_COUNT) {
            throw new JeecgBootException("关键词数量不能超过" + MAX_KEYWORD_COUNT + "个");
        }
    }

    private static final String RELEASE_LOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private void releaseLock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LOCK_SCRIPT, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    /**
     * 获取必需的租户ID（强约束：租户ID为空时抛出异常或降级到默认租户0）
     */
    private Integer getRequiredTenantId() {
        Integer tenantId = getCurrentTenantId();
        if (tenantId == null) {
            // 降级策略：使用默认租户0（单租户模式）
            log.warn("租户ID为空，降级到默认租户0");
            return 0;
        }
        return tenantId;
    }
}
