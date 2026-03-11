package org.jeecg.modules.ainote.util;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.util.oConvertUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

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
        try (InputStream inputStream = file.getInputStream()) {
            org.jeecg.common.util.MinioUtil.upload(inputStream, key);
        } catch (JeecgBootException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传MinIO失败: objectKey={}", objectKey, e);
            throw new JeecgBootException("文件上传失败: " + e.getMessage(), e);
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
}
