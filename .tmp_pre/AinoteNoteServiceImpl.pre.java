package org.jeecg.modules.ainote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.TokenUtils;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.dto.AinoteNoteCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteShareCreateDTO;
import org.jeecg.modules.ainote.dto.AinoteNoteUpdateDTO;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.IAinoteAiConfigService;
import org.jeecg.modules.ainote.service.IAinoteNoteService;
import org.jeecg.modules.ainote.vo.AinoteNoteShareDetailVO;
import org.jeecg.modules.ainote.vo.AinoteNoteShareVO;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.security.SecureRandom;
import java.util.*;

/**
 * 笔记表 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
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

    private final AinoteEmbeddingService ainoteEmbeddingService;
    private final IAinoteAiConfigService ainoteAiConfigService;

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
        boolean isPublicChanged = dto.getIsPublic() != null
                && !Objects.equals(dto.getIsPublic(), existing.getIsPublic());
        updateById(note);

        if (isPublicChanged) {
            tryReEmbedNote(dto.getId(), tenantId);
        }
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
            tryDeleteEmbedding(id, tenantId);
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

    private void tryReEmbedNote(String noteId, Integer tenantId) {
        try {
            String knowledgeId = resolveKnowledgeId(tenantId);
            if (oConvertUtils.isEmpty(knowledgeId)) {
                log.debug("未配置知识库ID，跳过重建向量: noteId={}", noteId);
                return;
            }
            AinoteNote latest = getById(noteId);
            if (latest == null || Objects.equals(latest.getNoteStatus(), NOTE_STATUS_DELETED)) {
                return;
            }
            ainoteEmbeddingService.deleteNoteEmbedding(noteId, knowledgeId);
            ainoteEmbeddingService.embedNote(latest, knowledgeId);
        } catch (Exception e) {
            log.warn("重建笔记向量失败，已忽略: noteId={}, err={}", noteId, e.getMessage());
        }
    }

    private void tryDeleteEmbedding(String noteId, Integer tenantId) {
        try {
            String knowledgeId = resolveKnowledgeId(tenantId);
            if (oConvertUtils.isEmpty(knowledgeId)) {
                log.debug("未配置知识库ID，跳过删除向量: noteId={}", noteId);
                return;
            }
            ainoteEmbeddingService.deleteNoteEmbedding(noteId, knowledgeId);
        } catch (Exception e) {
            log.warn("删除笔记向量失败，已忽略: noteId={}, err={}", noteId, e.getMessage());
        }
    }

    private String resolveKnowledgeId(Integer tenantId) {
        Integer safeTenantId = tenantId == null ? 0 : tenantId;
        return ainoteAiConfigService.getConfig(safeTenantId).getKnowledgeId();
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
