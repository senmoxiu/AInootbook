package org.jeecg.modules.ainote.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.jeecg.modules.ainote.service.IAinoteChunkedUploadService;
import org.jeecg.modules.ainote.service.IAinoteMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;

@Tag(name = "素材管理")
@RestController
@RequestMapping("/ainote/material")
@Slf4j
public class AinoteMaterialController extends JeecgController<AinoteMaterial, IAinoteMaterialService> {

    @Autowired
    private IAinoteChunkedUploadService ainoteChunkedUploadService;

    @Autowired
    private IAinoteMaterialService ainoteMaterialService;

    @Operation(summary = "文件上传")
    @PostMapping(value = "/upload")
    @RequiresPermissions("ainote:material:upload")
    public Result<AinoteMaterial> upload(@RequestParam(name = "file", required = true) MultipartFile file,
                                         @RequestParam(name = "noteId", required = true) String noteId) {
        try {
            AinoteMaterial material = ainoteMaterialService.uploadFile(file, noteId);
            return Result.OK("上传成功！", material);
        } catch (JeecgBootException e) {
            log.warn("上传素材失败: noteId={}, msg={}", noteId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传素材失败: noteId={}", noteId, e);
            return Result.error("上传失败！");
        }
    }

    @Operation(summary = "初始化分片上传")
    @PostMapping(value = "/initChunkedUpload")
    @RequiresPermissions("ainote:material:upload")
    public Result<String> initChunkedUpload(@RequestParam(name = "fileSize", required = true) Long fileSize,
                                            @RequestParam(name = "fileMd5", required = true) String fileMd5,
                                            @RequestParam(name = "ext", required = true) String ext,
                                            @RequestParam(name = "noteId", required = true) String noteId) {
        try {
            String uploadId = ainoteChunkedUploadService.initChunkedUpload(fileSize, fileMd5, ext, noteId);
            return Result.OK("初始化成功！", uploadId);
        } catch (JeecgBootException e) {
            log.warn("初始化分片上传失败: noteId={}, fileMd5={}, msg={}", noteId, fileMd5, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("初始化分片上传失败: noteId={}, fileMd5={}", noteId, fileMd5, e);
            return Result.error("初始化分片上传失败！");
        }
    }

    @Operation(summary = "上传单个分片")
    @PostMapping(value = "/uploadChunk")
    @RequiresPermissions("ainote:material:upload")
    public Result<JSONObject> uploadChunk(@RequestParam(name = "uploadId", required = true) String uploadId,
                                          @RequestParam(name = "chunkIndex", required = true) Integer chunkIndex,
                                          @RequestParam(name = "chunkMD5", required = true) String chunkMD5,
                                          @RequestParam(name = "file", required = true) MultipartFile file) {
        try {
            JSONObject uploadResult = ainoteChunkedUploadService.uploadChunk(uploadId, chunkIndex, chunkMD5, file);
            String message = Boolean.TRUE.equals(uploadResult.getBoolean("alreadyUploaded"))
                    ? "分片已上传，已跳过重复请求！"
                    : "分片上传成功！";
            return Result.OK(message, uploadResult);
        } catch (ResponseStatusException e) {
            String message = oConvertUtils.isNotEmpty(e.getReason()) ? e.getReason() : "分片上传失败！";
            log.warn("上传分片失败: uploadId={}, chunkIndex={}, status={}, msg={}",
                    uploadId, chunkIndex, e.getStatusCode().value(), message);
            return Result.error(message);
        } catch (JeecgBootException e) {
            log.warn("上传分片失败: uploadId={}, chunkIndex={}, msg={}", uploadId, chunkIndex, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传分片失败: uploadId={}, chunkIndex={}", uploadId, chunkIndex, e);
            return Result.error("分片上传失败！");
        }
    }

    @Operation(summary = "完成分片上传")
    @PostMapping(value = "/finalizeUpload")
    @RequiresPermissions("ainote:material:upload")
    public Result<JSONObject> finalizeUpload(@RequestParam(name = "uploadId", required = true) String uploadId) {
        try {
            JSONObject result = ainoteChunkedUploadService.finalizeUpload(uploadId);
            return Result.OK("文件合并成功！", result);
        } catch (ResponseStatusException e) {
            String message = oConvertUtils.isNotEmpty(e.getReason()) ? e.getReason() : "完成分片上传失败！";
            log.warn("完成分片上传失败: uploadId={}, status={}, msg={}",
                    uploadId, e.getStatusCode().value(), message);
            return Result.error(message);
        } catch (JeecgBootException e) {
            log.warn("完成分片上传失败: uploadId={}, msg={}", uploadId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("完成分片上传失败: uploadId={}", uploadId, e);
            return Result.error("完成分片上传失败！");
        }
    }

    @Operation(summary = "查询分片上传进度")
    @GetMapping(value = "/queryUploadProgress")
    @RequiresPermissions("ainote:material:upload")
    public Result<JSONObject> queryUploadProgress(@RequestParam(name = "uploadId", required = true) String uploadId) {
        try {
            JSONObject result = ainoteChunkedUploadService.queryUploadProgress(uploadId);
            return Result.OK(result);
        } catch (ResponseStatusException e) {
            String message = oConvertUtils.isNotEmpty(e.getReason()) ? e.getReason() : "查询分片上传进度失败！";
            log.warn("查询分片上传进度失败: uploadId={}, status={}, msg={}",
                    uploadId, e.getStatusCode().value(), message);
            return Result.error(message);
        } catch (JeecgBootException e) {
            log.warn("查询分片上传进度失败: uploadId={}, msg={}", uploadId, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询分片上传进度失败: uploadId={}", uploadId, e);
            return Result.error("查询分片上传进度失败！");
        }
    }

    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    @RequiresPermissions("ainote:material:list")
    public Result<IPage<AinoteMaterial>> queryPageList(AinoteMaterial ainoteMaterial,
                                                       @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                       @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                       HttpServletRequest req) {
        try {
            QueryWrapper<AinoteMaterial> queryWrapper =
                    QueryGenerator.initQueryWrapper(ainoteMaterial, req.getParameterMap());
            Integer tenantId = ainoteMaterialService.getRequiredTenantId();
            queryWrapper.eq("tenant_id", tenantId);
            queryWrapper.eq("del_flag", 0);
            Page<AinoteMaterial> page = new Page<>(pageNo, pageSize);
            IPage<AinoteMaterial> pageList = ainoteMaterialService.page(page, queryWrapper);
            return Result.OK(pageList);
        } catch (JeecgBootException e) {
            log.warn("分页查询素材失败: msg={}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("分页查询素材失败", e);
            return Result.error("查询失败！");
        }
    }

    @Operation(summary = "通过id查询")
    @GetMapping(value = "/queryById")
    @RequiresPermissions("ainote:material:query")
    public Result<AinoteMaterial> queryById(@RequestParam(name = "id") String id) {
        try {
            Integer tenantId = ainoteMaterialService.getRequiredTenantId();
            QueryWrapper<AinoteMaterial> wrapper = new QueryWrapper<>();
            wrapper.eq("id", id);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("del_flag", 0);
            AinoteMaterial material = ainoteMaterialService.getOne(wrapper);
            if (material == null) {
                return Result.error("未找到对应数据");
            }
            return Result.OK(material);
        } catch (JeecgBootException e) {
            log.warn("查询素材失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询素材失败: id={}", id, e);
            return Result.error("查询失败！");
        }
    }

    @Operation(summary = "获取签名URL")
    @GetMapping(value = "/presignedUrl/{id}")
    @RequiresPermissions("ainote:material:presignedUrl")
    public Result<String> getPresignedUrl(@PathVariable(name = "id") String id) {
        try {
            String url = ainoteMaterialService.generatePresignedUrl(id);
            return Result.OK(url);
        } catch (JeecgBootException e) {
            log.warn("获取预签名URL失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取预签名URL失败: id={}", id, e);
            return Result.error("获取预签名URL失败！");
        }
    }

    @Operation(summary = "删除")
    @DeleteMapping(value = "/delete")
    @RequiresPermissions("ainote:material:delete")
    public Result<String> delete(@RequestParam(name = "id") String id) {
        try {
            if (oConvertUtils.isEmpty(id)) {
                return Result.error("请选择要删除的数据！");
            }
            Integer tenantId = ainoteMaterialService.getRequiredTenantId();
            QueryWrapper<AinoteMaterial> wrapper = new QueryWrapper<>();
            wrapper.eq("id", id);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("del_flag", 0);
            boolean success = ainoteMaterialService.remove(wrapper);
            if (!success) {
                return Result.error("删除失败，数据不存在！");
            }
            return Result.OK("删除成功！");
        } catch (JeecgBootException e) {
            log.warn("删除素材失败: id={}, msg={}", id, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除素材失败: id={}", id, e);
            return Result.error("删除失败！");
        }
    }

    @Operation(summary = "批量删除")
    @DeleteMapping(value = "/deleteBatch")
    @RequiresPermissions("ainote:material:deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids") String ids) {
        try {
            if (oConvertUtils.isEmpty(ids)) {
                return Result.error("请选择要删除的数据！");
            }
            List<String> idList = Arrays.stream(ids.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();
            if (idList.isEmpty()) {
                return Result.error("请选择要删除的数据！");
            }
            Integer tenantId = ainoteMaterialService.getRequiredTenantId();
            QueryWrapper<AinoteMaterial> wrapper = new QueryWrapper<>();
            wrapper.in("id", idList);
            wrapper.eq("tenant_id", tenantId);
            wrapper.eq("del_flag", 0);
            boolean success = ainoteMaterialService.remove(wrapper);
            if (!success) {
                return Result.error("批量删除失败！");
            }
            return Result.OK("批量删除成功！");
        } catch (JeecgBootException e) {
            log.warn("批量删除素材失败: ids={}, msg={}", ids, e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("批量删除素材失败: ids={}", ids, e);
            return Result.error("批量删除失败！");
        }
    }
}
