package org.jeecg.modules.ainote.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 创建笔记分享请求对象
 */
@Data
@Schema(description = "创建笔记分享请求")
public class AinoteNoteShareCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "笔记ID不能为空")
    @Schema(description = "笔记ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String noteId;

    @Schema(description = "分享类型：1-链接分享，2-二维码分享", example = "1")
    private Integer shareType;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间（NULL表示永久）")
    private Date expireTime;
}
