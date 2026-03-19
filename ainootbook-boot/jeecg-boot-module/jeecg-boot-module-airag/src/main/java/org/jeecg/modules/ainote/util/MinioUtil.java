package org.jeecg.modules.ainote.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Component
public class MinioUtil {

    public void upload(MultipartFile file, String objectKey) {
        if (file == null || file.isEmpty()) {
            throw new JeecgBootException("文件不能为空");
        }
        if (oConvertUtils.isEmpty(objectKey) || objectKey.isBlank()) {
            throw new JeecgBootException("objectKey不能为空");
        }
        String key = objectKey;
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        // 根据文件扩展名设置正确的 Content-Type，解决智谱 OCR 等第三方 API 依赖 Content-Type 判断格式的问题
        String contentType = resolveContentType(key);
        try (InputStream inputStream = file.getInputStream()) {
            String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
            io.minio.MinioClient minioClient = getMinioClient();
            if (!minioClient.bucketExists(io.minio.BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
            }
            io.minio.PutObjectArgs objectArgs = io.minio.PutObjectArgs.builder()
                    .object(key)
                    .bucket(bucketName)
                    .contentType(contentType)
                    .stream(inputStream, file.getSize(), -1)
                    .build();
            minioClient.putObject(objectArgs);
            log.info("MinIO上传成功: objectKey={}, contentType={}, size={}", key, contentType, file.getSize());
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传MinIO失败: objectKey={}", objectKey, e);
            throw new JeecgBootException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 MinIO 直接读取文件字节（用于传 base64 给第三方 API，避免预签名 URL 内网不可达问题）
     */
    public byte[] getFileBytes(String filePath) {
        if (oConvertUtils.isEmpty(filePath) || filePath.isBlank()) {
            throw new JeecgBootException("filePath不能为空");
        }
        String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
        if (oConvertUtils.isEmpty(bucketName)) {
            throw new JeecgBootException("MinIO bucket 未配置");
        }
        String objectName = normalizeObjectName(filePath, bucketName);
        try (InputStream in = org.jeecg.common.util.MinioUtil.getMinioFile(bucketName, objectName)) {
            if (in == null) {
                throw new JeecgBootException("读取MinIO文件失败: objectName=" + objectName);
            }
            return in.readAllBytes();
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取MinIO文件失败: filePath={}, objectName={}", filePath, objectName, e);
            throw new JeecgBootException("读取MinIO文件失败: " + e.getMessage());
        }
    }

    public String getPresignedUrl(String filePath, int ttlSeconds) {
        if (oConvertUtils.isEmpty(filePath) || filePath.isBlank()) {
            throw new JeecgBootException("filePath不能为空");
        }
        String bucketName = org.jeecg.common.util.MinioUtil.getBucketName();
        if (oConvertUtils.isEmpty(bucketName)) {
            throw new JeecgBootException("MinIO bucket 未配置");
        }
        String objectName = normalizeObjectName(filePath, bucketName);
        String url = org.jeecg.common.util.MinioUtil.getObjectUrl(bucketName, objectName, ttlSeconds);
        if (oConvertUtils.isEmpty(url)) {
            throw new JeecgBootException("获取预签名URL失败");
        }
        return url;
    }

    private String normalizeObjectName(String filePath, String bucketName) {
        String path = filePath.trim();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        String minioUrl = org.jeecg.common.util.MinioUtil.getMinioUrl();
        if (oConvertUtils.isNotEmpty(minioUrl) && path.startsWith(minioUrl)) {
            path = path.substring(minioUrl.length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
        }

        if (path.startsWith(bucketName + "/")) {
            path = path.substring(bucketName.length() + 1);
        }
        return path;
    }

    /**
     * 根据文件扩展名推断 Content-Type，确保第三方 API（如智谱 OCR）能正确识别文件格式
     */
    private String resolveContentType(String objectKey) {
        if (oConvertUtils.isEmpty(objectKey)) {
            return "application/octet-stream";
        }
        int dotIdx = objectKey.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == objectKey.length() - 1) {
            return "application/octet-stream";
        }
        String ext = objectKey.substring(dotIdx + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "m4a":
                return "audio/mp4";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mkv":
                return "video/x-matroska";
            case "webm":
                return "video/webm";
            case "md":
                return "text/markdown";
            case "txt":
                return "text/plain";
            default:
                return "application/octet-stream";
        }
    }

    private io.minio.MinioClient getMinioClient() {
        // 复用 base-core 的 MinIO 配置，通过反射或直接构建
        String minioUrl = org.jeecg.common.util.MinioUtil.getMinioUrl();
        if (oConvertUtils.isEmpty(minioUrl)) {
            throw new JeecgBootException("MinIO URL 未配置");
        }
        // 注意：这里每次新建 client 有开销，但 ainote 上传频率不高，可接受
        // 后续可优化为缓存 client 实例
        return io.minio.MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(getMinioName(), getMinioPass())
                .build();
    }

    private String getMinioName() {
        try {
            java.lang.reflect.Field field = org.jeecg.common.util.MinioUtil.class.getDeclaredField("minioName");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            throw new JeecgBootException("获取MinIO用户名失败");
        }
    }

    private String getMinioPass() {
        try {
            java.lang.reflect.Field field = org.jeecg.common.util.MinioUtil.class.getDeclaredField("minioPass");
            field.setAccessible(true);
            return (String) field.get(null);
        } catch (Exception e) {
            throw new JeecgBootException("获取MinIO密码失败");
        }
    }
}
