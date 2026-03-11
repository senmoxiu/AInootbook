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
import org.jeecg.modules.ainote.config.AinoteProperties;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteMaterialMapper;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.util.MinioUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AinoteMaterialServiceImpl extends ServiceImpl<AinoteMaterialMapper, AinoteMaterial>
        implements IAinoteMaterialService {

    private static final long DEFAULT_AUDIO_MAX_SIZE = 150L * 1024 * 1024;
    private static final long DEFAULT_DOCUMENT_MAX_SIZE = 50L * 1024 * 1024;
    private static final long DEFAULT_IMAGE_MAX_SIZE = 10L * 1024 * 1024;
    private static final long DEFAULT_VIDEO_MAX_SIZE = 500L * 1024 * 1024;
    private static final long DEFAULT_PRESIGNED_URL_TTL_SECONDS = 900L;

    private static final Set<String> DEFAULT_AUDIO_EXTS = Set.of("mp3", "wav", "m4a");
    private static final Set<String> DEFAULT_DOCUMENT_EXTS = Set.of("pdf", "docx", "pptx", "xlsx", "md", "txt");
    private static final Set<String> DEFAULT_IMAGE_EXTS = Set.of("jpg", "jpeg", "png");
    private static final Set<String> DEFAULT_VIDEO_EXTS = Set.of("mp4", "avi", "mov", "mkv", "webm", "m4v");

    /** 注意：注入 NoteMapper 而非 NoteService，避免循环依赖 */
    private final AinoteNoteMapper noteMapper;
    private final AinoteMaterialMapper materialMapper;
    private final AinoteProperties ainoteProperties;
    private final MinioUtil minioUtil;

    /** 笔记已删除状态（note_status=3） */
    private static final int NOTE_STATUS_DELETED = 3;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AinoteMaterial uploadFile(MultipartFile file, String noteId) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("文件不能为空");
        }
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }

        LoginUser user = getCurrentUser();
        Integer tenantId = getRequiredTenantId();

        // C1-写权限：当前用户必须是该笔记的所有者
        assertNoteWriteAccess(noteId, user.getId(), tenantId);

        String originalFilename = file.getOriginalFilename();
        if (oConvertUtils.isEmpty(originalFilename)) {
            originalFilename = file.getName();
        }
        if (oConvertUtils.isEmpty(originalFilename)) {
            throw new JeecgBootException("文件名不能为空");
        }

        String ext = extractExt(originalFilename);
        String fileType = resolveFileType(ext);
        validateFileSize(file.getSize(), fileType);

        String objectKey = buildObjectKey(tenantId, noteId, ext);
        try {
            minioUtil.upload(file, objectKey);
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("上传MinIO失败: noteId={}, objectKey={}", noteId, objectKey, e);
            throw new JeecgBootException("文件上传失败");
        }

        AinoteMaterial material = new AinoteMaterial();
        material.setNoteId(noteId);
        material.setFileType(fileType);
        material.setFilePath(objectKey);
        material.setFileName(originalFilename);
        material.setFileExt(ext);
        material.setFileSize(file.getSize());
        material.setProcessStatus(0);
        material.setTenantId(tenantId);
        material.setCreateBy(user.getId());
        material.setUpdateBy(user.getId());
        material.setDelFlag(0);

        int inserted = materialMapper.insert(material);
        if (inserted <= 0) {
            throw new JeecgBootException("素材保存失败");
        }
        return material;
    }

    @Override
    public String generatePresignedUrl(String materialId) {
        if (oConvertUtils.isEmpty(materialId) || materialId.isBlank()) {
            throw new JeecgBootException("素材ID不能为空");
        }

        Integer tenantId = getRequiredTenantId();
        QueryWrapper<AinoteMaterial> wrapper = new QueryWrapper<>();
        wrapper.eq("id", materialId);
        wrapper.eq("del_flag", 0);
        wrapper.eq("tenant_id", tenantId);

        AinoteMaterial material = materialMapper.selectOne(wrapper);
        if (material == null) {
            throw new JeecgBootException("素材不存在");
        }

        // C1-读权限：如有登录用户，验证对素材所属笔记的读权限
        // 系统内部任务调用时 SecurityUtils 无 Principal，跳过用户级校验（租户已由 wrapper 保证）
        LoginUser requestUser = getOptionalCurrentUser();
        if (requestUser != null) {
            assertNoteReadAccess(material.getNoteId(), requestUser.getId(), tenantId);
        }

        long ttl = ainoteProperties.getMinio().getPresignedUrlTtl();
        if (ttl <= 0) {
            ttl = DEFAULT_PRESIGNED_URL_TTL_SECONDS;
        }
        if (ttl > Integer.MAX_VALUE) {
            ttl = Integer.MAX_VALUE;
        }

        try {
            return minioUtil.getPresignedUrl(material.getFilePath(), (int) ttl);
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取预签名URL失败: materialId={}", materialId, e);
            throw new JeecgBootException("获取预签名URL失败");
        }
    }

    @Override
    public List<AinoteMaterial> listByNoteId(String noteId, Integer tenantId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        if (tenantId == null) {
            tenantId = getRequiredTenantId();
        }
        return materialMapper.selectByNoteId(noteId, tenantId);
    }

    @Override
    public List<AinoteMaterial> listByNoteIdAndType(String noteId, String fileType, Integer tenantId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        if (oConvertUtils.isEmpty(fileType) || fileType.isBlank()) {
            throw new JeecgBootException("文件类型不能为空");
        }
        if (tenantId == null) {
            tenantId = getRequiredTenantId();
        }
        return materialMapper.selectByNoteIdAndType(noteId, fileType, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProcessStatus(String id, Integer status, Integer tenantId) {
        if (oConvertUtils.isEmpty(id) || id.isBlank()) {
            throw new JeecgBootException("素材ID不能为空");
        }
        if (status == null) {
            throw new JeecgBootException("处理状态不能为空");
        }
        if (status < 0 || status > 3) {
            throw new JeecgBootException("处理状态无效");
        }
        if (tenantId == null) {
            tenantId = getRequiredTenantId();
        }
        return materialMapper.updateProcessStatus(id, status, tenantId) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByNoteId(String noteId) {
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }
        Integer tenantId = getRequiredTenantId();
        LoginUser user = getCurrentUser();

        UpdateWrapper<AinoteMaterial> wrapper = new UpdateWrapper<>();
        wrapper.eq("note_id", noteId);
        wrapper.eq("del_flag", 0);
        wrapper.eq("tenant_id", tenantId);
        wrapper.set("del_flag", 1);
        wrapper.set("update_by", user.getId());
        wrapper.setSql("update_time = CURRENT_TIMESTAMP");
        return materialMapper.update(null, wrapper) > 0;
    }

    private String buildObjectKey(Integer tenantId, String noteId, String ext) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        int tid = tenantId == null ? 0 : tenantId;
        return "ainote/material/" + tid + "/" + noteId + "/" + uuid + "." + ext;
    }

    private String extractExt(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx == filename.length() - 1) {
            throw new JeecgBootException("文件扩展名不能为空");
        }
        return filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveFileType(String ext) {
        String normalized = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        if (getAllowedAudioExts().contains(normalized)) {
            return "audio";
        }
        if (getAllowedDocumentExts().contains(normalized)) {
            return "document";
        }
        if (getAllowedImageExts().contains(normalized)) {
            return "image";
        }
        if (getAllowedVideoExts().contains(normalized)) {
            return "video";
        }
        throw new JeecgBootException("不支持的文件类型：" + normalized);
    }

    private void validateFileSize(long size, String fileType) {
        long maxSize = getMaxSizeBytes(fileType);
        if (maxSize > 0 && size > maxSize) {
            long maxMb = maxSize / 1024 / 1024;
            throw new JeecgBootException("文件大小超过限制，最大允许" + maxMb + "MB");
        }
    }

    private long getMaxSizeBytes(String fileType) {
        AinoteProperties.Material.MaxSize cfg = ainoteProperties.getMaterial().getMaxSize();
        long configured;
        switch (fileType) {
            case "audio":
                configured = cfg.getAudio();
                return configured > 0 ? configured : DEFAULT_AUDIO_MAX_SIZE;
            case "document":
                configured = cfg.getDocument();
                return configured > 0 ? configured : DEFAULT_DOCUMENT_MAX_SIZE;
            case "image":
                configured = cfg.getImage();
                return configured > 0 ? configured : DEFAULT_IMAGE_MAX_SIZE;
            case "video":
                configured = cfg.getVideo();
                return configured > 0 ? configured : DEFAULT_VIDEO_MAX_SIZE;
            default:
                return 0L;
        }
    }

    private Set<String> getAllowedAudioExts() {
        Set<String> exts = normalizeExtSet(ainoteProperties.getMaterial().getAllowedTypes().getAudio());
        return exts.isEmpty() ? DEFAULT_AUDIO_EXTS : exts;
    }

    private Set<String> getAllowedDocumentExts() {
        Set<String> exts = normalizeExtSet(ainoteProperties.getMaterial().getAllowedTypes().getDocument());
        return exts.isEmpty() ? DEFAULT_DOCUMENT_EXTS : exts;
    }

    private Set<String> getAllowedImageExts() {
        Set<String> exts = normalizeExtSet(ainoteProperties.getMaterial().getAllowedTypes().getImage());
        return exts.isEmpty() ? DEFAULT_IMAGE_EXTS : exts;
    }

    private Set<String> getAllowedVideoExts() {
        Set<String> exts = normalizeExtSet(ainoteProperties.getMaterial().getAllowedTypes().getVideo());
        return exts.isEmpty() ? DEFAULT_VIDEO_EXTS : exts;
    }

    private Set<String> normalizeExtSet(List<String> exts) {
        if (exts == null || exts.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String ext : exts) {
            if (oConvertUtils.isEmpty(ext)) {
                continue;
            }
            String e = ext.trim().toLowerCase(Locale.ROOT);
            if (e.startsWith(".")) {
                e = e.substring(1);
            }
            if (!e.isEmpty()) {
                normalized.add(e);
            }
        }
        return normalized;
    }

    private LoginUser getCurrentUser() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        return user;
    }

    /** 可能返回 null（系统任务调用场景，无 Shiro 会话） */
    private LoginUser getOptionalCurrentUser() {
        try {
            return (LoginUser) SecurityUtils.getSubject().getPrincipal();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * C1-写权限：笔记必须存在且属于当前用户（student_id 或 create_by）
     */
    private void assertNoteWriteAccess(String noteId, String userId, Integer tenantId) {
        AinoteNote note = noteMapper.selectById(noteId);
        if (note == null || NOTE_STATUS_DELETED == note.getNoteStatus()) {
            throw new JeecgBootException("笔记不存在或已删除");
        }
        if (!tenantId.equals(note.getTenantId())) {
            throw new JeecgBootException("无权向该笔记上传素材");
        }
        boolean isOwner = userId.equals(note.getStudentId()) || userId.equals(note.getCreateBy());
        if (!isOwner) {
            throw new JeecgBootException("无权向该笔记上传素材");
        }
    }

    /**
     * C1-读权限：笔记存在，且为本人（student_id/create_by）或公开笔记
     */
    private void assertNoteReadAccess(String noteId, String userId, Integer tenantId) {
        if (oConvertUtils.isEmpty(noteId)) {
            return;
        }
        AinoteNote note = noteMapper.selectById(noteId);
        if (note == null || NOTE_STATUS_DELETED == note.getNoteStatus()) {
            throw new JeecgBootException("素材所属笔记不存在");
        }
        if (!tenantId.equals(note.getTenantId())) {
            throw new JeecgBootException("无权访问该素材");
        }
        boolean isOwner = userId.equals(note.getStudentId()) || userId.equals(note.getCreateBy());
        boolean isPublic = Integer.valueOf(1).equals(note.getIsPublic());
        if (!isOwner && !isPublic) {
            throw new JeecgBootException("无权访问该素材");
        }
    }

    @Override
    public Integer getRequiredTenantId() {
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
