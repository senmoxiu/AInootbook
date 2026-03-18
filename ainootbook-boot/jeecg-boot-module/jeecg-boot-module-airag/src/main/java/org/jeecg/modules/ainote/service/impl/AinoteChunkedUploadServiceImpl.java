package org.jeecg.modules.ainote.service.impl;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.IAinoteChunkedUploadService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.jeecg.modules.ainote.util.MinioUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 视频分片上传 Service 实现。
 */
@Slf4j
@Service
public class AinoteChunkedUploadServiceImpl implements IAinoteChunkedUploadService {

    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024;
    private static final int NOTE_STATUS_DELETED = 3;
    private static final String SESSION_KEY_PREFIX = "ainote:chunked-upload:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);
    private static final long CHUNK_SIZE = 5L * 1024 * 1024;
    private static final String CHUNK_FIELD_PREFIX = "chunk:";
    private static final String CHUNK_LOCK_KEY_PREFIX = SESSION_KEY_PREFIX + "lock:";
    private static final Duration CHUNK_LOCK_TTL = Duration.ofMinutes(5);
    private static final long CHUNK_WAIT_INTERVAL_MILLIS = 200L;
    private static final int CHUNK_WAIT_RETRY_TIMES = 50;
    private static final String FINALIZE_LOCK_KEY_PREFIX = SESSION_KEY_PREFIX + "finalize:lock:";
    private static final Duration FINALIZE_LOCK_TTL = Duration.ofMinutes(30);
    private static final long FINALIZE_WAIT_INTERVAL_MILLIS = 500L;
    private static final int FINALIZE_WAIT_RETRY_TIMES = 60;
    private static final String CLEANUP_INDEX_KEY = SESSION_KEY_PREFIX + "cleanup:index";
    private static final String CLEANUP_META_KEY_PREFIX = SESSION_KEY_PREFIX + "cleanup:";
    private static final String INIT_INDEX_KEY_PREFIX = SESSION_KEY_PREFIX + "init:index:";
    private static final String STATUS_INIT = "INIT";
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_UPLOADED = "UPLOADED";
    private static final String STATUS_FINALIZING = "FINALIZING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String FILE_TYPE_VIDEO = "video";

    @Autowired
    private IAinoteMaterialService ainoteMaterialService;

    @Autowired
    private AinoteNoteMapper ainoteNoteMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MinioUtil minioUtil;

    @Override
    public String initChunkedUpload(Long fileSize, String fileMd5, String ext, String noteId) {
        if (fileSize == null || fileSize <= 0) {
            throw new JeecgBootException("文件大小不能为空");
        }
        if (fileSize > MAX_VIDEO_SIZE) {
            throw new JeecgBootException("视频大小超过限制，最大允许500MB");
        }
        if (oConvertUtils.isEmpty(fileMd5) || fileMd5.isBlank()) {
            throw new JeecgBootException("文件MD5不能为空");
        }
        if (oConvertUtils.isEmpty(ext) || ext.isBlank()) {
            throw new JeecgBootException("文件扩展名不能为空");
        }
        if (oConvertUtils.isEmpty(noteId) || noteId.isBlank()) {
            throw new JeecgBootException("笔记ID不能为空");
        }

        LoginUser user = getCurrentUser();
        Integer tenantId = ainoteMaterialService.getRequiredTenantId();
        assertNoteWriteAccess(noteId, user.getId(), tenantId);

        int totalChunks = calculateTotalChunks(fileSize);
        String normalizedFileMd5 = normalizeMd5(fileMd5);
        String normalizedExt = normalizeExt(ext);
        String initIndexKey = buildInitIndexKey(tenantId, user.getId(), noteId, fileSize, normalizedFileMd5, normalizedExt);
        String existingUploadId = stringRedisTemplate.opsForValue().get(initIndexKey);
        if (oConvertUtils.isNotEmpty(existingUploadId) && !existingUploadId.isBlank()) {
            Map<Object, Object> existingSession = tryLoadSession(buildSessionKey(existingUploadId));
            if (existingSession != null && !existingSession.isEmpty()) {
                return existingUploadId;
            }
            stringRedisTemplate.delete(initIndexKey);
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> session = new HashMap<>();
        session.put("uploadId", uploadId);
        session.put("noteId", noteId);
        session.put("tenantId", String.valueOf(tenantId));
        session.put("userId", user.getId());
        session.put("fileSize", String.valueOf(fileSize));
        session.put("fileMd5", normalizedFileMd5);
        session.put("ext", normalizedExt);
        session.put("chunkSize", String.valueOf(CHUNK_SIZE));
        session.put("totalChunks", String.valueOf(totalChunks));
        session.put("uploadedSize", "0");
        session.put("uploadedCount", "0");
        session.put("status", STATUS_INIT);

        String sessionKey = buildSessionKey(uploadId);
        stringRedisTemplate.opsForHash().putAll(sessionKey, session);
        stringRedisTemplate.opsForValue().set(initIndexKey, uploadId, SESSION_TTL);
        stringRedisTemplate.expire(sessionKey, SESSION_TTL);
        refreshCleanupMetadata(uploadId, totalChunks, STATUS_INIT);
        log.info("初始化分片上传成功: uploadId={}, noteId={}, tenantId={}, totalChunks={}",
                uploadId, noteId, tenantId, totalChunks);
        return uploadId;
    }

    @Override
    public JSONObject uploadChunk(String uploadId, Integer chunkIndex, String chunkMd5, MultipartFile file) {
        if (oConvertUtils.isEmpty(uploadId) || uploadId.isBlank()) {
            throw new JeecgBootException("uploadId不能为空");
        }
        if (chunkIndex == null) {
            throw new JeecgBootException("chunkIndex不能为空");
        }
        if (oConvertUtils.isEmpty(chunkMd5) || chunkMd5.isBlank()) {
            throw new JeecgBootException("chunkMD5不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("分片文件不能为空");
        }
        if (file.getSize() > CHUNK_SIZE) {
            throw new JeecgBootException("分片大小超过限制，单片最大允许5MB");
        }

        String normalizedUploadId = uploadId.trim();
        String sessionKey = buildSessionKey(normalizedUploadId);
        Map<Object, Object> session = loadSession(sessionKey);
        LoginUser user = getCurrentUser();
        Integer tenantId = ainoteMaterialService.getRequiredTenantId();
        assertUploadSessionAccess(session, user.getId(), tenantId);

        String sessionStatus = getRequiredSessionValue(session, "status");
        if (STATUS_COMPLETED.equals(sessionStatus)) {
            throw new JeecgBootException("上传已完成，无需继续上传分片");
        }
        if (STATUS_FINALIZING.equals(sessionStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件正在合并，请稍后重试");
        }

        int totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new JeecgBootException("chunkIndex超出范围");
        }

        String normalizedChunkMd5 = normalizeMd5(chunkMd5);
        String chunkField = buildChunkField(chunkIndex);
        JSONObject existingChunk = getChunkState(sessionKey, chunkField);
        if (existingChunk != null) {
            return buildIdempotentChunkResult(sessionKey, normalizedUploadId, chunkIndex, totalChunks,
                    normalizedChunkMd5, existingChunk);
        }

        String lockKey = buildChunkLockKey(normalizedUploadId, chunkIndex);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, normalizedChunkMd5, CHUNK_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            JSONObject waitedChunk = waitForChunkState(sessionKey, chunkField);
            if (waitedChunk != null) {
                return buildIdempotentChunkResult(sessionKey, normalizedUploadId, chunkIndex, totalChunks,
                        normalizedChunkMd5, waitedChunk);
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "分片正在上传，请稍后重试");
        }

        String objectKey = buildChunkObjectKey(normalizedUploadId, chunkIndex);
        try {
            existingChunk = getChunkState(sessionKey, chunkField);
            if (existingChunk != null) {
                return buildIdempotentChunkResult(sessionKey, normalizedUploadId, chunkIndex, totalChunks,
                        normalizedChunkMd5, existingChunk);
            }

            String actualChunkMd5 = calculateMd5(file);
            if (!normalizedChunkMd5.equals(actualChunkMd5)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片MD5校验失败");
            }

            minioUtil.upload(file, objectKey);

            JSONObject chunkState = new JSONObject();
            chunkState.put("chunkIndex", chunkIndex);
            chunkState.put("chunkMd5", normalizedChunkMd5);
            chunkState.put("fileSize", file.getSize());
            chunkState.put("objectKey", objectKey);
            chunkState.put("uploadedAt", System.currentTimeMillis());

            try {
                stringRedisTemplate.opsForHash().put(sessionKey, chunkField, chunkState.toJSONString());
                stringRedisTemplate.opsForHash().increment(sessionKey, "uploadedSize", file.getSize());
                Long uploadedCount = stringRedisTemplate.opsForHash().increment(sessionKey, "uploadedCount", 1L);
                String status = uploadedCount != null && uploadedCount >= totalChunks ? STATUS_UPLOADED : STATUS_UPLOADING;
                stringRedisTemplate.opsForHash().put(sessionKey, "status", status);
                stringRedisTemplate.expire(sessionKey, SESSION_TTL);
                refreshCleanupMetadata(normalizedUploadId, totalChunks, status);
                log.info("上传分片成功: uploadId={}, chunkIndex={}, totalChunks={}",
                        normalizedUploadId, chunkIndex, totalChunks);
                return buildChunkResult(normalizedUploadId, chunkIndex, totalChunks, getUploadedCount(sessionKey), false);
            } catch (Exception e) {
                deleteTempChunkQuietly(objectKey);
                log.error("保存分片状态失败: uploadId={}, chunkIndex={}", normalizedUploadId, chunkIndex, e);
                throw new JeecgBootException("保存分片状态失败");
            }
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject finalizeUpload(String uploadId) {
        if (oConvertUtils.isEmpty(uploadId) || uploadId.isBlank()) {
            throw new JeecgBootException("uploadId不能为空");
        }

        String normalizedUploadId = uploadId.trim();
        String sessionKey = buildSessionKey(normalizedUploadId);
        Map<Object, Object> session = loadSession(sessionKey);
        LoginUser user = getCurrentUser();
        Integer tenantId = ainoteMaterialService.getRequiredTenantId();
        assertUploadSessionAccess(session, user.getId(), tenantId);

        String noteId = getRequiredSessionValue(session, "noteId");
        int totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
        session = repairCompletedSessionIfNecessary(sessionKey, normalizedUploadId, session, noteId, tenantId, totalChunks);
        if (isCompletedSession(session)) {
            return buildCompletedResult(resolveCompletedMaterialId(normalizedUploadId, session, noteId, tenantId));
        }

        String finalizeLockKey = buildFinalizeLockKey(normalizedUploadId);
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(finalizeLockKey, "1", FINALIZE_LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            JSONObject waitedResult = waitForCompletedSession(normalizedUploadId, sessionKey, noteId, tenantId);
            if (waitedResult != null) {
                return waitedResult;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件正在合并，请稍后重试");
        }

        Path mergedFile = null;
        String finalObjectKey = null;
        boolean materialSaved = false;
        try {
            session = loadSession(sessionKey);
            assertUploadSessionAccess(session, user.getId(), tenantId);
            noteId = getRequiredSessionValue(session, "noteId");
            totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
            session = repairCompletedSessionIfNecessary(sessionKey, normalizedUploadId, session, noteId, tenantId, totalChunks);
            if (isCompletedSession(session)) {
                return buildCompletedResult(resolveCompletedMaterialId(normalizedUploadId, session, noteId, tenantId));
            }

            long uploadedCount = parseLong(session.get("uploadedCount"), "上传会话缺少uploadedCount");
            if (uploadedCount < totalChunks) {
                throw new JeecgBootException("仍有分片未上传完成");
            }

            String ext = normalizeExt(getRequiredSessionValue(session, "ext"));
            long expectedFileSize = parseLong(session.get("fileSize"), "上传会话缺少fileSize");
            String expectedFileMd5 = normalizeMd5(getRequiredSessionValue(session, "fileMd5"));
            String materialId = buildMaterialId(normalizedUploadId);

            AinoteMaterial existingMaterial = findExistingMaterial(materialId, noteId, tenantId);
            if (existingMaterial != null) {
                markSessionCompleted(sessionKey, normalizedUploadId, totalChunks, materialId);
                log.info("分片上传已完成，直接返回已有素材: uploadId={}, materialId={}", normalizedUploadId, materialId);
                return buildCompletedResult(materialId);
            }

            updateSessionStatus(sessionKey, normalizedUploadId, totalChunks, STATUS_FINALIZING);
            mergedFile = Files.createTempFile("ainote-merge-" + normalizedUploadId + "-", "." + ext);
            MessageDigest md5Digest = newMd5Digest();
            long mergedSize = mergeChunksToLocalFile(sessionKey, normalizedUploadId, totalChunks, mergedFile, md5Digest);
            if (mergedSize != expectedFileSize) {
                throw new JeecgBootException("合并后的文件大小校验失败");
            }

            String actualFileMd5 = toHex(md5Digest.digest());
            if (!expectedFileMd5.equals(actualFileMd5)) {
                throw new JeecgBootException("文件MD5校验失败");
            }

            finalObjectKey = buildMaterialObjectKey(tenantId, noteId, ext);
            uploadMergedFile(mergedFile, finalObjectKey);

            AinoteMaterial material = buildMaterial(materialId, noteId, tenantId, user.getId(),
                    normalizedUploadId, ext, expectedFileSize, finalObjectKey);
            materialSaved = ainoteMaterialService.save(material);
            if (!materialSaved) {
                deleteObjectQuietly(finalObjectKey);
                throw new JeecgBootException("素材保存失败");
            }

            markSessionCompleted(sessionKey, normalizedUploadId, totalChunks, materialId);
            try {
                deleteTempChunks(normalizedUploadId, totalChunks);
            } catch (Exception cleanupEx) {
                log.error("清理临时分片失败: uploadId={}", normalizedUploadId, cleanupEx);
            }

            log.info("完成分片上传成功: uploadId={}, materialId={}, noteId={}",
                    normalizedUploadId, materialId, noteId);
            return buildCompletedResult(materialId);
        } catch (ResponseStatusException e) {
            restoreSessionStatusAfterFinalizeFailure(sessionKey, normalizedUploadId);
            log.error("完成分片上传失败: uploadId={}, msg={}", normalizedUploadId, e.getReason(), e);
            throw e;
        } catch (JeecgBootException e) {
            restoreSessionStatusAfterFinalizeFailure(sessionKey, normalizedUploadId);
            if (!materialSaved && oConvertUtils.isNotEmpty(finalObjectKey)) {
                deleteObjectQuietly(finalObjectKey);
            }
            log.error("完成分片上传失败: uploadId={}, msg={}", normalizedUploadId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            restoreSessionStatusAfterFinalizeFailure(sessionKey, normalizedUploadId);
            if (!materialSaved && oConvertUtils.isNotEmpty(finalObjectKey)) {
                deleteObjectQuietly(finalObjectKey);
            }
            log.error("完成分片上传失败: uploadId={}", normalizedUploadId, e);
            throw new JeecgBootException("完成分片上传失败");
        } finally {
            stringRedisTemplate.delete(finalizeLockKey);
            deleteLocalFileQuietly(mergedFile);
        }
    }

    @Override
    public JSONObject queryUploadProgress(String uploadId) {
        if (oConvertUtils.isEmpty(uploadId) || uploadId.isBlank()) {
            throw new JeecgBootException("uploadId不能为空");
        }

        String normalizedUploadId = uploadId.trim();
        String sessionKey = buildSessionKey(normalizedUploadId);
        Map<Object, Object> session;
        try {
            session = loadSession(sessionKey);
        } catch (ResponseStatusException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                return buildEmptyProgressResult(normalizedUploadId);
            }
            throw e;
        }
        LoginUser user = getCurrentUser();
        Integer tenantId = ainoteMaterialService.getRequiredTenantId();
        assertUploadSessionAccess(session, user.getId(), tenantId);

        String noteId = getRequiredSessionValue(session, "noteId");
        int totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
        session = repairCompletedSessionIfNecessary(sessionKey, normalizedUploadId, session, noteId, tenantId, totalChunks);

        long uploadedCount = parseLong(session.get("uploadedCount"), "上传会话缺少uploadedCount");
        long uploadedSize = parseLong(session.get("uploadedSize"), "上传会话缺少uploadedSize");
        String status = getRequiredSessionValue(session, "status");

        JSONObject result = new JSONObject();
        result.put("uploadId", normalizedUploadId);
        result.put("uploadedCount", uploadedCount);
        result.put("totalChunks", totalChunks);
        result.put("uploadedSize", uploadedSize);
        result.put("status", status);
        result.put("uploadedChunkIndices", getUploadedChunkIndices(session));
        result.put("completed", STATUS_COMPLETED.equals(status));
        if (STATUS_COMPLETED.equals(status)) {
            result.put("materialId", resolveCompletedMaterialId(normalizedUploadId, session, noteId, tenantId));
        }
        return result;
    }

    private JSONObject buildEmptyProgressResult(String uploadId) {
        JSONObject result = new JSONObject();
        result.put("uploadId", uploadId);
        result.put("uploadedCount", 0L);
        result.put("totalChunks", 0);
        result.put("uploadedSize", 0L);
        result.put("status", "NOT_FOUND");
        result.put("uploadedChunkIndices", new ArrayList<>());
        result.put("completed", false);
        return result;
    }

    private Map<Object, Object> loadSession(String sessionKey) {
        try {
            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(sessionKey);
            if (session != null && !session.isEmpty()) {
                return session;
            }
        } catch (Exception e) {
            log.debug("读取上传会话Hash失败，尝试兼容旧版字符串会话: sessionKey={}", sessionKey, e);
        }

        String legacySession = stringRedisTemplate.opsForValue().get(sessionKey);
        if (oConvertUtils.isEmpty(legacySession) || legacySession.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "uploadId不存在或已过期");
        }

        JSONObject legacy = JSONObject.parseObject(legacySession);
        if (legacy == null || legacy.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "uploadId不存在或已过期");
        }

        Map<String, String> migrated = new HashMap<>();
        legacy.forEach((key, value) -> {
            if (value != null) {
                migrated.put(key, String.valueOf(value));
            }
        });

        String fileSizeText = migrated.get("fileSize");
        if (oConvertUtils.isEmpty(fileSizeText) || fileSizeText.isBlank()) {
            throw new JeecgBootException("上传会话缺少fileSize");
        }

        migrated.putIfAbsent("chunkSize", String.valueOf(CHUNK_SIZE));
        migrated.putIfAbsent("totalChunks", String.valueOf(calculateTotalChunks(Long.parseLong(fileSizeText))));
        migrated.putIfAbsent("uploadedSize", "0");
        migrated.putIfAbsent("uploadedCount", "0");
        migrated.putIfAbsent("status", STATUS_INIT);

        stringRedisTemplate.delete(sessionKey);
        stringRedisTemplate.opsForHash().putAll(sessionKey, migrated);
        stringRedisTemplate.expire(sessionKey, SESSION_TTL);

        String migratedUploadId = migrated.get("uploadId");
        if (oConvertUtils.isNotEmpty(migratedUploadId) && !migratedUploadId.isBlank()) {
            refreshCleanupMetadata(migratedUploadId,
                    parseInt(migrated.get("totalChunks"), "上传会话缺少totalChunks"),
                    migrated.get("status"));
        }

        Map<Object, Object> migratedSession = new HashMap<>();
        migratedSession.putAll(migrated);
        return migratedSession;
    }

    private Map<Object, Object> tryLoadSession(String sessionKey) {
        try {
            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(sessionKey);
            if (session != null && !session.isEmpty()) {
                return session;
            }
        } catch (Exception e) {
            log.debug("尝试读取上传会话失败: sessionKey={}", sessionKey, e);
        }
        return null;
    }

    private JSONObject waitForCompletedSession(String uploadId, String sessionKey, String noteId, Integer tenantId) {
        for (int i = 0; i < FINALIZE_WAIT_RETRY_TIMES; i++) {
            Map<Object, Object> session = tryLoadSession(sessionKey);
            if (session != null && !session.isEmpty()) {
                int totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
                session = repairCompletedSessionIfNecessary(sessionKey, uploadId, session, noteId, tenantId, totalChunks);
                if (isCompletedSession(session)) {
                    return buildCompletedResult(resolveCompletedMaterialId(uploadId, session, noteId, tenantId));
                }
            }
            try {
                Thread.sleep(FINALIZE_WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private Map<Object, Object> repairCompletedSessionIfNecessary(String sessionKey, String uploadId,
                                                                  Map<Object, Object> session, String noteId,
                                                                  Integer tenantId, int totalChunks) {
        if (session == null || session.isEmpty() || isCompletedSession(session)) {
            return session;
        }
        AinoteMaterial existingMaterial = findExistingMaterial(buildMaterialId(uploadId), noteId, tenantId);
        if (existingMaterial == null) {
            return session;
        }
        markSessionCompleted(sessionKey, uploadId, totalChunks, existingMaterial.getId());
        Map<Object, Object> repaired = tryLoadSession(sessionKey);
        return repaired == null || repaired.isEmpty() ? session : repaired;
    }

    private String resolveCompletedMaterialId(String uploadId, Map<Object, Object> session, String noteId, Integer tenantId) {
        String materialId = getOptionalSessionValue(session, "materialId");
        if (oConvertUtils.isEmpty(materialId) || materialId.isBlank()) {
            materialId = buildMaterialId(uploadId);
        }
        AinoteMaterial existingMaterial = findExistingMaterial(materialId, noteId, tenantId);
        if (existingMaterial == null) {
            throw new JeecgBootException("上传已完成但素材记录不存在");
        }
        return existingMaterial.getId();
    }

    private void markSessionCompleted(String sessionKey, String uploadId, int totalChunks, String materialId) {
        stringRedisTemplate.opsForHash().put(sessionKey, "materialId", materialId);
        stringRedisTemplate.opsForHash().put(sessionKey, "status", STATUS_COMPLETED);
        stringRedisTemplate.opsForHash().put(sessionKey, "completedAt", String.valueOf(System.currentTimeMillis()));
        stringRedisTemplate.expire(sessionKey, SESSION_TTL);
        removeCleanupMetadata(uploadId);
        log.info("更新上传会话为已完成: uploadId={}, materialId={}, totalChunks={}", uploadId, materialId, totalChunks);
    }

    private void updateSessionStatus(String sessionKey, String uploadId, int totalChunks, String status) {
        stringRedisTemplate.opsForHash().put(sessionKey, "status", status);
        stringRedisTemplate.expire(sessionKey, SESSION_TTL);
        refreshCleanupMetadata(uploadId, totalChunks, status);
    }

    private void restoreSessionStatusAfterFinalizeFailure(String sessionKey, String uploadId) {
        try {
            Map<Object, Object> session = tryLoadSession(sessionKey);
            if (session == null || session.isEmpty() || isCompletedSession(session)) {
                return;
            }
            int totalChunks = parseInt(session.get("totalChunks"), "上传会话缺少totalChunks");
            long uploadedCount = parseLong(session.get("uploadedCount"), "上传会话缺少uploadedCount");
            String status = uploadedCount >= totalChunks ? STATUS_UPLOADED : STATUS_UPLOADING;
            updateSessionStatus(sessionKey, uploadId, totalChunks, status);
        } catch (Exception e) {
            log.error("恢复上传会话状态失败: uploadId={}", uploadId, e);
        }
    }

    private void refreshCleanupMetadata(String uploadId, int totalChunks, String status) {
        long expireAt = System.currentTimeMillis() + SESSION_TTL.toMillis();
        Map<String, String> cleanupMeta = new HashMap<>();
        cleanupMeta.put("uploadId", uploadId);
        cleanupMeta.put("totalChunks", String.valueOf(totalChunks));
        cleanupMeta.put("status", status);
        cleanupMeta.put("expireAt", String.valueOf(expireAt));

        String cleanupMetaKey = buildCleanupMetaKey(uploadId);
        stringRedisTemplate.opsForHash().putAll(cleanupMetaKey, cleanupMeta);
        stringRedisTemplate.opsForZSet().add(CLEANUP_INDEX_KEY, uploadId, expireAt);
    }

    private void removeCleanupMetadata(String uploadId) {
        stringRedisTemplate.opsForZSet().remove(CLEANUP_INDEX_KEY, uploadId);
        stringRedisTemplate.delete(buildCleanupMetaKey(uploadId));
    }

    private long mergeChunksToLocalFile(String sessionKey, String uploadId, int totalChunks,
                                        Path mergedFile, MessageDigest md5Digest) {
        String bucketName = getRequiredBucketName();
        long mergedSize = 0L;
        byte[] buffer = new byte[8192];
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(mergedFile))) {
            for (int i = 0; i < totalChunks; i++) {
                JSONObject chunkState = getChunkState(sessionKey, buildChunkField(i));
                if (chunkState == null) {
                    throw new JeecgBootException("仍有分片未上传完成");
                }
                String objectKey = chunkState.getString("objectKey");
                if (oConvertUtils.isEmpty(objectKey) || objectKey.isBlank()) {
                    objectKey = buildChunkObjectKey(uploadId, i);
                }
                try (InputStream inputStream = org.jeecg.common.util.MinioUtil.getMinioFile(bucketName, objectKey)) {
                    if (inputStream == null) {
                        throw new JeecgBootException("读取分片失败，chunkIndex=" + i);
                    }
                    int len;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                        md5Digest.update(buffer, 0, len);
                        mergedSize += len;
                    }
                }
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("合并分片失败");
        }
        return mergedSize;
    }

    private void uploadMergedFile(Path mergedFile, String objectKey) {
        try (InputStream inputStream = Files.newInputStream(mergedFile)) {
            String uploadResult = org.jeecg.common.util.MinioUtil.upload(inputStream, objectKey);
            if (oConvertUtils.isEmpty(uploadResult) || uploadResult.isBlank()) {
                throw new JeecgBootException("上传合并文件失败");
            }
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            throw new JeecgBootException("上传合并文件失败");
        }
    }

    private AinoteMaterial buildMaterial(String materialId, String noteId, Integer tenantId, String userId,
                                         String uploadId, String ext, long fileSize, String objectKey) {
        AinoteMaterial material = new AinoteMaterial();
        material.setId(materialId);
        material.setNoteId(noteId);
        material.setFileType(FILE_TYPE_VIDEO);
        material.setFilePath(objectKey);
        material.setFileName(uploadId + "." + ext);
        material.setFileExt(ext);
        material.setFileSize(fileSize);
        material.setProcessStatus(0);
        material.setTenantId(tenantId);
        material.setCreateBy(userId);
        material.setUpdateBy(userId);
        material.setDelFlag(0);
        return material;
    }

    private AinoteMaterial findExistingMaterial(String materialId, String noteId, Integer tenantId) {
        if (oConvertUtils.isEmpty(materialId) || materialId.isBlank()) {
            return null;
        }
        AinoteMaterial material = ainoteMaterialService.getById(materialId);
        if (material == null) {
            return null;
        }
        if (!tenantId.equals(material.getTenantId())) {
            return null;
        }
        if (!noteId.equals(material.getNoteId())) {
            return null;
        }
        if (Integer.valueOf(1).equals(material.getDelFlag())) {
            return null;
        }
        return material;
    }

    private JSONObject buildCompletedResult(String materialId) {
        JSONObject result = new JSONObject();
        result.put("materialId", materialId);
        result.put("fileUrl", ainoteMaterialService.generatePresignedUrl(materialId));
        result.put("status", STATUS_COMPLETED);
        return result;
    }

    private List<Integer> getUploadedChunkIndices(Map<Object, Object> session) {
        List<Integer> chunkIndices = new ArrayList<>();
        if (session == null || session.isEmpty()) {
            return chunkIndices;
        }
        for (Map.Entry<Object, Object> entry : session.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String field = String.valueOf(entry.getKey());
            if (!field.startsWith(CHUNK_FIELD_PREFIX)) {
                continue;
            }
            try {
                chunkIndices.add(Integer.parseInt(field.substring(CHUNK_FIELD_PREFIX.length())));
            } catch (NumberFormatException e) {
                throw new JeecgBootException("分片状态损坏");
            }
        }
        chunkIndices.sort(Integer::compareTo);
        return chunkIndices;
    }

    private String buildChunkField(int chunkIndex) {
        return CHUNK_FIELD_PREFIX + chunkIndex;
    }

    private String buildChunkLockKey(String uploadId, int chunkIndex) {
        return CHUNK_LOCK_KEY_PREFIX + uploadId + ":" + chunkIndex;
    }

    private String buildFinalizeLockKey(String uploadId) {
        return FINALIZE_LOCK_KEY_PREFIX + uploadId;
    }

    private String buildCleanupMetaKey(String uploadId) {
        return CLEANUP_META_KEY_PREFIX + uploadId;
    }

    private String buildInitIndexKey(Integer tenantId, String userId, String noteId,
                                     long fileSize, String fileMd5, String ext) {
        int tid = tenantId == null ? 0 : tenantId;
        return INIT_INDEX_KEY_PREFIX + tid + ":" + userId + ":" + noteId + ":" + fileSize + ":" + fileMd5 + ":" + ext;
    }

    private String buildChunkObjectKey(String uploadId, int chunkIndex) {
        return "ainote/chunks/" + uploadId + "/" + chunkIndex;
    }

    private String buildMaterialObjectKey(Integer tenantId, String noteId, String ext) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        int tid = tenantId == null ? 0 : tenantId;
        return "ainote/material/" + tid + "/" + noteId + "/" + uuid + "." + ext;
    }

    private String buildMaterialId(String uploadId) {
        return uploadId;
    }

    private JSONObject getChunkState(String sessionKey, String chunkField) {
        Object raw = stringRedisTemplate.opsForHash().get(sessionKey, chunkField);
        if (raw == null) {
            return null;
        }
        try {
            return JSONObject.parseObject(String.valueOf(raw));
        } catch (Exception e) {
            throw new JeecgBootException("分片状态损坏");
        }
    }

    private JSONObject waitForChunkState(String sessionKey, String chunkField) {
        for (int i = 0; i < CHUNK_WAIT_RETRY_TIMES; i++) {
            JSONObject chunkState = getChunkState(sessionKey, chunkField);
            if (chunkState != null) {
                return chunkState;
            }
            try {
                Thread.sleep(CHUNK_WAIT_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    private JSONObject buildIdempotentChunkResult(String sessionKey, String uploadId, int chunkIndex, int totalChunks,
                                                  String chunkMd5, JSONObject chunkState) {
        String storedChunkMd5 = normalizeMd5(chunkState.getString("chunkMd5"));
        if (!storedChunkMd5.equals(chunkMd5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分片已上传且MD5不一致");
        }
        return buildChunkResult(uploadId, chunkIndex, totalChunks, getUploadedCount(sessionKey), true);
    }

    private JSONObject buildChunkResult(String uploadId, int chunkIndex, int totalChunks, long uploadedCount,
                                        boolean alreadyUploaded) {
        JSONObject result = new JSONObject();
        result.put("uploadId", uploadId);
        result.put("chunkIndex", chunkIndex);
        result.put("totalChunks", totalChunks);
        result.put("uploadedCount", uploadedCount);
        result.put("uploaded", true);
        result.put("alreadyUploaded", alreadyUploaded);
        result.put("completed", uploadedCount >= totalChunks);
        result.put("status", uploadedCount >= totalChunks ? STATUS_UPLOADED : STATUS_UPLOADING);
        return result;
    }

    private long getUploadedCount(String sessionKey) {
        Object uploadedCount = stringRedisTemplate.opsForHash().get(sessionKey, "uploadedCount");
        if (uploadedCount == null) {
            return 0L;
        }
        return parseLong(uploadedCount, "上传会话uploadedCount损坏");
    }

    private int parseInt(Object value, String message) {
        if (value == null) {
            throw new JeecgBootException(message);
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new JeecgBootException(message);
        }
    }

    private long parseLong(Object value, String message) {
        if (value == null) {
            throw new JeecgBootException(message);
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new JeecgBootException(message);
        }
    }

    private String getRequiredSessionValue(Map<Object, Object> session, String field) {
        Object value = session.get(field);
        if (value == null) {
            throw new JeecgBootException("上传会话缺少" + field);
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            throw new JeecgBootException("上传会话缺少" + field);
        }
        return text;
    }

    private String getOptionalSessionValue(Map<Object, Object> session, String field) {
        if (session == null || session.isEmpty()) {
            return null;
        }
        Object value = session.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isCompletedSession(Map<Object, Object> session) {
        return STATUS_COMPLETED.equals(getOptionalSessionValue(session, "status"));
    }

    private void assertUploadSessionAccess(Map<Object, Object> session, String userId, Integer tenantId) {
        String sessionUserId = getRequiredSessionValue(session, "userId");
        String sessionTenantId = getRequiredSessionValue(session, "tenantId");
        if (!userId.equals(sessionUserId) || !String.valueOf(tenantId).equals(sessionTenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问上传会话");
        }
    }

    private int calculateTotalChunks(long fileSize) {
        long totalChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;
        if (totalChunks <= 0 || totalChunks > Integer.MAX_VALUE) {
            throw new JeecgBootException("分片数量无效");
        }
        return (int) totalChunks;
    }

    private String calculateMd5(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return DigestUtils.md5DigestAsHex(inputStream).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            throw new JeecgBootException("计算分片MD5失败");
        }
    }

    private MessageDigest newMd5Digest() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (Exception e) {
            throw new JeecgBootException("初始化MD5失败");
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private void deleteTempChunks(String uploadId, int totalChunks) {
        for (int i = 0; i < totalChunks; i++) {
            deleteTempChunkQuietly(buildChunkObjectKey(uploadId, i));
        }
    }

    private void deleteTempChunkQuietly(String objectKey) {
        deleteObjectQuietly(objectKey);
    }

    private void deleteObjectQuietly(String objectKey) {
        if (oConvertUtils.isEmpty(objectKey) || objectKey.isBlank()) {
            return;
        }
        try {
            String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
            if (oConvertUtils.isNotEmpty(bucketName)) {
                org.jeecg.common.util.MinioUtil.removeObject(bucketName, objectKey);
            }
        } catch (Exception e) {
            log.warn("清理MinIO对象失败: objectKey={}, error={}", objectKey, e.getMessage());
        }
    }

    private void deleteLocalFileQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.warn("删除本地临时文件失败: file={}, error={}", file, e.getMessage());
        }
    }

    private String getRequiredBucketName() {
        String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
        if (oConvertUtils.isEmpty(bucketName) || bucketName.isBlank()) {
            throw new JeecgBootException("MinIO bucket 未配置");
        }
        return bucketName;
    }

    private String normalizeMd5(String md5) {
        if (oConvertUtils.isEmpty(md5) || md5.isBlank()) {
            throw new JeecgBootException("MD5不能为空");
        }
        return md5.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeExt(String ext) {
        String normalized = ext.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isEmpty()) {
            throw new JeecgBootException("文件扩展名不能为空");
        }
        return normalized;
    }

    private String buildSessionKey(String uploadId) {
        return SESSION_KEY_PREFIX + uploadId;
    }

    private LoginUser getCurrentUser() {
        LoginUser user = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (user == null) {
            throw new JeecgBootException("用户未登录");
        }
        return user;
    }

    private void assertNoteWriteAccess(String noteId, String userId, Integer tenantId) {
        AinoteNote note = ainoteNoteMapper.selectById(noteId);
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
}
