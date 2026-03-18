package org.jeecg.modules.ainote.service;

import com.alibaba.fastjson.JSONObject;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.entity.AinoteNote;
import org.jeecg.modules.ainote.mapper.AinoteNoteMapper;
import org.jeecg.modules.ainote.service.impl.AinoteChunkedUploadServiceImpl;
import org.jeecg.modules.ainote.util.MinioUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Transactional
@DisplayName("AinoteChunkedUploadService 单元测试")
class AinoteChunkedUploadServiceTest {

    private static final String NOTE_ID = "note-1";
    private static final String USER_ID = "user-1";
    private static final int TENANT_ID = 7;
    private static final long MAX_VIDEO_SIZE = 500L * 1024 * 1024;
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private static final String BUCKET_NAME = "test-bucket";

    @Mock
    private IAinoteMaterialService ainoteMaterialService;
    @Mock
    private AinoteNoteMapper ainoteNoteMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    @Mock
    private MinioUtil minioUtil;

    @InjectMocks
    private AinoteChunkedUploadServiceImpl service;

    private final Map<String, Map<Object, Object>> hashStore = new ConcurrentHashMap<>();
    private final Map<String, String> valueStore = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Double>> zSetStore = new ConcurrentHashMap<>();
    private final Map<String, byte[]> objectStore = new ConcurrentHashMap<>();
    private final Map<String, AinoteMaterial> materialStore = new ConcurrentHashMap<>();

    private MockedStatic<SecurityUtils> securityUtilsMock;
    private MockedStatic<org.jeecg.common.util.MinioUtil> commonMinioUtilMock;

    @BeforeEach
    void setUp() throws IOException {
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(ainoteMaterialService.getRequiredTenantId()).thenReturn(TENANT_ID);
        when(ainoteMaterialService.getById(anyString()))
                .thenAnswer(invocation -> materialStore.get(invocation.getArgument(0)));
        when(ainoteMaterialService.generatePresignedUrl(anyString()))
                .thenAnswer(invocation -> "https://cdn.example/material/" + invocation.getArgument(0));
        when(ainoteMaterialService.save(any(AinoteMaterial.class)))
                .thenAnswer(invocation -> {
                    AinoteMaterial material = invocation.getArgument(0);
                    materialStore.put(material.getId(), material);
                    return true;
                });

        wireRedisTemplate();
        wireObjectStorage();
        mockLoginUser();
    }

    @AfterEach
    void tearDown() {
        if (securityUtilsMock != null) {
            securityUtilsMock.close();
        }
        if (commonMinioUtilMock != null) {
            commonMinioUtilMock.close();
        }
        hashStore.clear();
        valueStore.clear();
        zSetStore.clear();
        objectStore.clear();
        materialStore.clear();
    }

    @Test
    void should_return_uploadId_when_initChunkedUpload_given_valid_request() {
        mockWritableNote();

        byte[] content = "video-body".getBytes();
        String uploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);

        assertThat(uploadId).isNotBlank();
        Map<Object, Object> session = hashStore.get(sessionKey(uploadId));
        assertThat(session).isNotNull();
        assertThat(session.get("uploadId")).isEqualTo(uploadId);
        assertThat(session.get("noteId")).isEqualTo(NOTE_ID);
        assertThat(session.get("uploadedCount")).isEqualTo("0");
        assertThat(session.get("status")).isEqualTo("INIT");
    }

    @Test
    void should_throw_exception_when_initChunkedUpload_given_file_size_exceeds_500mb() {
        assertThatThrownBy(() -> service.initChunkedUpload(MAX_VIDEO_SIZE + 1, "abc123", "mp4", NOTE_ID))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("500MB");
    }

    @Test
    void should_return_same_uploadId_when_initChunkedUpload_given_duplicate_request() {
        mockWritableNote();

        byte[] content = "same-video".getBytes();
        String firstUploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);
        String secondUploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);

        assertThat(secondUploadId).isEqualTo(firstUploadId);
        assertThat(hashStore.keySet()).contains(sessionKey(firstUploadId), cleanupMetaKey(firstUploadId));
    }

    @Test
    void should_return_success_when_uploadChunk_given_valid_chunk() {
        mockWritableNote();
        byte[] content = "chunk-content".getBytes();
        String uploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);
        MockMultipartFile chunkFile = multipart("chunk-0.mp4", content);

        JSONObject result = service.uploadChunk(uploadId, 0, md5Hex(content), chunkFile);

        assertThat(result.getBoolean("uploaded")).isTrue();
        assertThat(result.getBoolean("alreadyUploaded")).isFalse();
        assertThat(result.getLongValue("uploadedCount")).isEqualTo(1L);
        assertThat(result.getString("status")).isEqualTo("UPLOADED");
        assertThat(objectStore)
                .containsKey("ainote/chunks/" + uploadId + "/0");
    }

    @Test
    void should_throw_exception_when_uploadChunk_given_md5_mismatch() {
        mockWritableNote();
        byte[] content = "chunk-content".getBytes();
        String uploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);

        assertThatThrownBy(() -> service.uploadChunk(uploadId, 0, md5Hex("wrong".getBytes()), multipart("bad.bin", content)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("分片MD5校验失败");
    }

    @Test
    void should_remain_idempotent_when_uploadChunk_given_same_uploadId_and_chunkIndex_twice() {
        mockWritableNote();
        byte[] content = "repeatable-chunk".getBytes();
        String uploadId = service.initChunkedUpload((long) content.length, md5Hex(content), "mp4", NOTE_ID);

        JSONObject first = service.uploadChunk(uploadId, 0, md5Hex(content), multipart("chunk.bin", content));
        JSONObject second = service.uploadChunk(uploadId, 0, md5Hex(content), multipart("chunk.bin", content));

        assertThat(first.getLongValue("uploadedCount")).isEqualTo(second.getLongValue("uploadedCount"));
        assertThat(second.getBoolean("alreadyUploaded")).isTrue();
        assertThat(second.getBoolean("uploaded")).isTrue();
    }

    @Test
    void should_return_materialId_when_finalizeUpload_given_complete_chunks() {
        mockWritableNote();
        byte[] content = randomBytes(CHUNK_SIZE + 512, 11L);
        String uploadId = initAndUploadAllChunks(content);

        JSONObject result = service.finalizeUpload(uploadId);

        assertThat(result.getString("materialId")).isEqualTo(uploadId);
        assertThat(result.getString("status")).isEqualTo("COMPLETED");
        AinoteMaterial material = materialStore.get(uploadId);
        assertThat(material).isNotNull();
        assertThat(material.getFileType()).isEqualTo("video");
        assertThat(md5Hex(objectStore.get(material.getFilePath()))).isEqualTo(md5Hex(content));
    }

    @Test
    void should_throw_exception_when_finalizeUpload_given_total_md5_mismatch() {
        mockWritableNote();
        byte[] content = randomBytes(CHUNK_SIZE + 256, 23L);
        String uploadId = initUploadSession(content.length, md5Hex("other".getBytes()));
        uploadAllChunks(uploadId, content);

        assertThatThrownBy(() -> service.finalizeUpload(uploadId))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("文件MD5校验失败");
    }

    @Test
    void should_throw_exception_when_finalizeUpload_given_incomplete_chunks() {
        mockWritableNote();
        String uploadId = initUploadSession(CHUNK_SIZE + 1L, md5Hex("placeholder".getBytes()));
        Map<Object, Object> session = hashStore.get(sessionKey(uploadId));
        session.put("uploadedCount", "1");
        session.put("status", "UPLOADING");

        assertThatThrownBy(() -> service.finalizeUpload(uploadId))
                .isInstanceOf(JeecgBootException.class)
                .hasMessageContaining("仍有分片未上传完成");
    }

    @Test
    void should_return_uploaded_chunk_indices_when_queryUploadProgress_given_existing_upload() {
        mockWritableNote();
        byte[] content = randomBytes(CHUNK_SIZE + 128, 31L);
        String uploadId = initUploadSession(content.length, md5Hex(content));
        byte[] firstChunk = Arrays.copyOfRange(content, 0, CHUNK_SIZE);
        service.uploadChunk(uploadId, 0, md5Hex(firstChunk), multipart("chunk-0.bin", firstChunk));

        JSONObject progress = service.queryUploadProgress(uploadId);

        assertThat(progress.getJSONArray("uploadedChunkIndices").toJavaList(Integer.class))
                .containsExactly(0);
        assertThat(progress.getLongValue("uploadedCount")).isEqualTo(1L);
    }

    @Test
    void should_return_empty_list_when_queryUploadProgress_given_unknown_uploadId() {
        JSONObject progress = service.queryUploadProgress("missing-upload");

        assertThat(progress.getJSONArray("uploadedChunkIndices").toJavaList(Integer.class)).isEmpty();
        assertThat(progress.getLongValue("uploadedCount")).isZero();
        assertThat(progress.getInteger("totalChunks")).isZero();
        assertThat(progress.getBoolean("completed")).isFalse();
    }

    @Test
    void should_preserve_final_md5_when_finalizeUpload_given_sampled_payloads() {
        mockWritableNote();
        List<Integer> payloadSizes = List.of(CHUNK_SIZE + 17, CHUNK_SIZE + 1024, CHUNK_SIZE * 2 + 33);

        for (int i = 0; i < payloadSizes.size(); i++) {
            byte[] content = randomBytes(payloadSizes.get(i), 100L + i);
            String uploadId = initAndUploadAllChunks(content);

            JSONObject result = service.finalizeUpload(uploadId);
            AinoteMaterial material = materialStore.get(result.getString("materialId"));

            assertThat(md5Hex(objectStore.get(material.getFilePath()))).isEqualTo(md5Hex(content));
        }
    }

    @Test
    void should_keep_chunk_upload_idempotent_when_uploadChunk_repeated_for_sampled_inputs() {
        mockWritableNote();
        for (int i = 0; i < 4; i++) {
            byte[] chunk = randomBytes(128 + i * 33, 200L + i);
            String uploadId = initUploadSession(chunk.length, md5Hex(chunk));

            JSONObject first = service.uploadChunk(uploadId, 0, md5Hex(chunk), multipart("sample.bin", chunk));
            JSONObject second = service.uploadChunk(uploadId, 0, md5Hex(chunk), multipart("sample.bin", chunk));

            assertThat(second.getBoolean("alreadyUploaded")).isTrue();
            assertThat(second.getLongValue("uploadedCount")).isEqualTo(first.getLongValue("uploadedCount"));
        }
    }

    @Test
    void should_reject_every_oversized_file_when_initChunkedUpload_given_boundary_samples() {
        mockWritableNote();
        for (long fileSize : List.of(MAX_VIDEO_SIZE + 1, MAX_VIDEO_SIZE + 1024, MAX_VIDEO_SIZE + 16 * 1024 * 1024)) {
            assertThatThrownBy(() -> service.initChunkedUpload(fileSize, "deadbeef", "mp4", NOTE_ID))
                    .isInstanceOf(JeecgBootException.class)
                    .hasMessageContaining("500MB");
        }
    }

    private void wireRedisTemplate() {
        when(hashOperations.entries(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return new LinkedHashMap<>(hashStore.getOrDefault(key, Map.of()));
        });
        when(hashOperations.get(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object hashKey = invocation.getArgument(1);
            Map<Object, Object> entries = hashStore.get(key);
            return entries == null ? null : entries.get(hashKey);
        });
        when(hashOperations.increment(anyString(), any(), anyLong())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object hashKey = invocation.getArgument(1);
            long delta = invocation.getArgument(2);
            Map<Object, Object> entries = hashStore.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
            long current = Long.parseLong(String.valueOf(entries.getOrDefault(hashKey, "0")));
            long updated = current + delta;
            entries.put(hashKey, String.valueOf(updated));
            return updated;
        });
        when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            boolean removed = hashStore.remove(key) != null;
            removed = valueStore.remove(key) != null || removed;
            removed = zSetStore.remove(key) != null || removed;
            return removed;
        });
        when(valueOperations.get(anyString())).thenAnswer(invocation -> valueStore.get(invocation.getArgument(0)));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            if (valueStore.containsKey(key)) {
                return false;
            }
            valueStore.put(key, value);
            return true;
        });

        try {
            java.lang.reflect.Method hashPutAll = HashOperations.class.getMethod("putAll", Object.class, Map.class);
            java.lang.reflect.Method hashPut = HashOperations.class.getMethod("put", Object.class, Object.class, Object.class);
            java.lang.reflect.Method valueSet = ValueOperations.class.getMethod("set", Object.class, Object.class, Duration.class);
            java.lang.reflect.Method zSetAdd = ZSetOperations.class.getMethod("add", Object.class, Object.class, double.class);
            java.lang.reflect.Method zSetRemove = ZSetOperations.class.getMethod("remove", Object.class, Object[].class);

            org.mockito.Mockito.doAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Map<?, ?> entries = invocation.getArgument(1);
                hashStore.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).putAll(entries);
                return null;
            }).when(hashOperations).putAll(anyString(), anyMap());

            org.mockito.Mockito.doAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Object hashKey = invocation.getArgument(1);
                Object value = invocation.getArgument(2);
                hashStore.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(hashKey, value);
                return null;
            }).when(hashOperations).put(anyString(), any(), any());

            org.mockito.Mockito.doAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                valueStore.put(key, value);
                return null;
            }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                String value = invocation.getArgument(1);
                double score = invocation.getArgument(2);
                zSetStore.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(value, score);
                return true;
            });

            when(zSetOperations.remove(anyString(), any())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Map<String, Double> entries = zSetStore.get(key);
                if (entries == null) {
                    return 0L;
                }
                Object raw = invocation.getArgument(1);
                long removed = 0L;
                if (raw instanceof Object[] values) {
                    for (Object value : values) {
                        if (entries.remove(String.valueOf(value)) != null) {
                            removed++;
                        }
                    }
                    return removed;
                }
                return entries.remove(String.valueOf(raw)) != null ? 1L : 0L;
            });

            assertThat(hashPutAll).isNotNull();
            assertThat(hashPut).isNotNull();
            assertThat(valueSet).isNotNull();
            assertThat(zSetAdd).isNotNull();
            assertThat(zSetRemove).isNotNull();
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Redis mock wiring failed", e);
        }
    }

    private void wireObjectStorage() throws IOException {
        org.mockito.Mockito.doAnswer(invocation -> {
            MockMultipartFile file = invocation.getArgument(0);
            String objectKey = invocation.getArgument(1);
            objectStore.put(objectKey, file.getBytes());
            return null;
        }).when(minioUtil).upload(any(), anyString());

        commonMinioUtilMock = org.mockito.Mockito.mockStatic(org.jeecg.common.util.MinioUtil.class);
        commonMinioUtilMock.when(org.jeecg.common.util.MinioUtil::getBucketName).thenReturn(BUCKET_NAME);
        commonMinioUtilMock.when(() -> org.jeecg.common.util.MinioUtil.getMinioFile(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String bucketName = invocation.getArgument(0);
                    String objectKey = invocation.getArgument(1);
                    if (!BUCKET_NAME.equals(bucketName)) {
                        return null;
                    }
                    byte[] payload = objectStore.get(objectKey);
                    return payload == null ? null : new ByteArrayInputStream(payload);
                });
        commonMinioUtilMock.when(() -> org.jeecg.common.util.MinioUtil.upload(any(InputStream.class), anyString()))
                .thenAnswer(invocation -> {
                    InputStream inputStream = invocation.getArgument(0);
                    String objectKey = invocation.getArgument(1);
                    objectStore.put(objectKey, inputStream.readAllBytes());
                    return objectKey;
                });
        commonMinioUtilMock.when(() -> org.jeecg.common.util.MinioUtil.removeObject(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    objectStore.remove(invocation.getArgument(1));
                    return null;
                });
    }

    private void mockLoginUser() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setRoleCode("student");

        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(loginUser);

        securityUtilsMock = org.mockito.Mockito.mockStatic(SecurityUtils.class);
        securityUtilsMock.when(SecurityUtils::getSubject).thenReturn(subject);
    }

    private void mockWritableNote() {
        AinoteNote note = new AinoteNote();
        note.setId(NOTE_ID);
        note.setTenantId(TENANT_ID);
        note.setStudentId(USER_ID);
        note.setCreateBy(USER_ID);
        note.setNoteStatus(1);
        when(ainoteNoteMapper.selectById(NOTE_ID)).thenReturn(note);
    }

    private String initAndUploadAllChunks(byte[] content) {
        String uploadId = initUploadSession(content.length, md5Hex(content));
        uploadAllChunks(uploadId, content);
        return uploadId;
    }

    private String initUploadSession(long fileSize, String fileMd5) {
        return service.initChunkedUpload(fileSize, fileMd5, "mp4", NOTE_ID);
    }

    private void uploadAllChunks(String uploadId, byte[] content) {
        int totalChunks = (content.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        for (int i = 0; i < totalChunks; i++) {
            int start = i * CHUNK_SIZE;
            int end = Math.min(content.length, start + CHUNK_SIZE);
            byte[] chunk = Arrays.copyOfRange(content, start, end);
            service.uploadChunk(uploadId, i, md5Hex(chunk), multipart("chunk-" + i + ".bin", chunk));
        }
    }

    private MockMultipartFile multipart(String fileName, byte[] payload) {
        return new MockMultipartFile("file", fileName, "application/octet-stream", payload);
    }

    private String sessionKey(String uploadId) {
        return "ainote:chunked-upload:" + uploadId;
    }

    private String cleanupMetaKey(String uploadId) {
        return "ainote:chunked-upload:cleanup:" + uploadId;
    }

    private byte[] randomBytes(int size, long seed) {
        byte[] payload = new byte[size];
        new Random(seed).nextBytes(payload);
        return payload;
    }

    private String md5Hex(byte[] bytes) {
        return DigestUtils.md5DigestAsHex(bytes).toLowerCase(Locale.ROOT);
    }
}
