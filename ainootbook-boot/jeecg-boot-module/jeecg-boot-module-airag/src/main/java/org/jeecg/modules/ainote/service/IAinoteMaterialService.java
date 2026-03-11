package org.jeecg.modules.ainote.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.ainote.entity.AinoteMaterial;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IAinoteMaterialService extends IService<AinoteMaterial> {

    AinoteMaterial uploadFile(MultipartFile file, String noteId);

    String generatePresignedUrl(String materialId);

    List<AinoteMaterial> listByNoteId(String noteId, Integer tenantId);

    List<AinoteMaterial> listByNoteIdAndType(String noteId, String fileType, Integer tenantId);

    boolean updateProcessStatus(String id, Integer status, Integer tenantId);

    boolean deleteByNoteId(String noteId);

    Integer getRequiredTenantId();
}
