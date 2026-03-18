package org.jeecg.modules.ainote.service;

import com.alibaba.fastjson.JSONObject;
import org.springframework.web.multipart.MultipartFile;

/**
 * 视频分片上传 Service 接口
 */
public interface IAinoteChunkedUploadService {

    /**
     * 初始化分片上传会话。
     *
     * @param fileSize 文件大小（字节）
     * @param fileMd5  文件 MD5
     * @param ext      文件扩展名
     * @param noteId   笔记ID
     * @return 上传会话ID
     */
    String initChunkedUpload(Long fileSize, String fileMd5, String ext, String noteId);

    /**
     * 上传单个分片。
     *
     * @param uploadId   上传会话ID
     * @param chunkIndex 分片索引（从0开始）
     * @param chunkMd5   分片MD5
     * @param file       分片文件
     * @return 上传结果
     */
    JSONObject uploadChunk(String uploadId, Integer chunkIndex, String chunkMd5, MultipartFile file);

    /**
     * 完成分片上传并合并文件。
     *
     * @param uploadId 上传会话ID
     * @return 素材ID和文件访问地址
     */
    JSONObject finalizeUpload(String uploadId);

    /**
     * 查询分片上传进度。
     *
     * @param uploadId 上传会话ID
     * @return 上传进度
     */
    JSONObject queryUploadProgress(String uploadId);
}
