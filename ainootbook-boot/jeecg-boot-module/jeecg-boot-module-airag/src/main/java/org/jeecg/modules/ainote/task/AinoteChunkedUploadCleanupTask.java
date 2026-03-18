package org.jeecg.modules.ainote.task;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 分片上传清理任务：回收过期未完成上传的临时分片。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AinoteChunkedUploadCleanupTask {

    private static final String SESSION_KEY_PREFIX = "ainote:chunked-upload:";
    private static final String CHUNK_LOCK_KEY_PREFIX = SESSION_KEY_PREFIX + "lock:";
    private static final String FINALIZE_LOCK_KEY_PREFIX = SESSION_KEY_PREFIX + "finalize:lock:";
    private static final String CLEANUP_INDEX_KEY = SESSION_KEY_PREFIX + "cleanup:index";
    private static final String CLEANUP_META_KEY_PREFIX = SESSION_KEY_PREFIX + "cleanup:";
    private static final String LOCK_KEY = SESSION_KEY_PREFIX + "cleanup:task:lock";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int BATCH_SIZE = 500;

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredUploads() {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_KEY, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            Set<String> uploadIds = stringRedisTemplate.opsForZSet()
                    .rangeByScore(CLEANUP_INDEX_KEY, 0, now, 0, BATCH_SIZE);
            if (uploadIds == null || uploadIds.isEmpty()) {
                return;
            }
            for (String uploadId : uploadIds) {
                cleanupUpload(uploadId, now);
            }
        } catch (Exception e) {
            log.error("清理过期分片上传会话失败", e);
        } finally {
            try {
                stringRedisTemplate.delete(LOCK_KEY);
            } catch (Exception e) {
                log.warn("释放分片上传清理任务锁失败", e);
            }
        }
    }

    private void cleanupUpload(String uploadId, long now) {
        if (oConvertUtils.isEmpty(uploadId) || uploadId.isBlank()) {
            return;
        }

        String cleanupMetaKey = buildCleanupMetaKey(uploadId);
        try {
            Map<Object, Object> cleanupMeta = stringRedisTemplate.opsForHash().entries(cleanupMetaKey);
            if (cleanupMeta == null || cleanupMeta.isEmpty()) {
                removeCleanupMetadata(uploadId);
                return;
            }

            long expireAt = parseLong(cleanupMeta.get("expireAt"));
            if (expireAt > now) {
                return;
            }

            String status = String.valueOf(cleanupMeta.getOrDefault("status", ""));
            if (STATUS_COMPLETED.equals(status)) {
                removeCleanupMetadata(uploadId);
                return;
            }

            int totalChunks = parseInt(cleanupMeta.get("totalChunks"));
            for (int i = 0; i < totalChunks; i++) {
                deleteObjectQuietly(buildChunkObjectKey(uploadId, i));
                stringRedisTemplate.delete(buildChunkLockKey(uploadId, i));
            }

            stringRedisTemplate.delete(buildFinalizeLockKey(uploadId));
            stringRedisTemplate.delete(buildSessionKey(uploadId));
            removeCleanupMetadata(uploadId);
            log.info("清理过期分片上传会话成功: uploadId={}, totalChunks={}", uploadId, totalChunks);
        } catch (Exception e) {
            log.error("清理过期分片上传会话失败: uploadId={}", uploadId, e);
        }
    }

    private void removeCleanupMetadata(String uploadId) {
        stringRedisTemplate.opsForZSet().remove(CLEANUP_INDEX_KEY, uploadId);
        stringRedisTemplate.delete(buildCleanupMetaKey(uploadId));
    }

    private int parseInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseLong(Object value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
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
            log.warn("删除临时分片失败: objectKey={}, error={}", objectKey, e.getMessage());
        }
    }

    private String buildSessionKey(String uploadId) {
        return SESSION_KEY_PREFIX + uploadId;
    }

    private String buildCleanupMetaKey(String uploadId) {
        return CLEANUP_META_KEY_PREFIX + uploadId;
    }

    private String buildChunkLockKey(String uploadId, int chunkIndex) {
        return CHUNK_LOCK_KEY_PREFIX + uploadId + ":" + chunkIndex;
    }

    private String buildFinalizeLockKey(String uploadId) {
        return FINALIZE_LOCK_KEY_PREFIX + uploadId;
    }

    private String buildChunkObjectKey(String uploadId, int chunkIndex) {
        return "ainote/chunks/" + uploadId + "/" + chunkIndex;
    }
}
