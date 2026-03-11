package org.jeecg.modules.ainote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 分享创建结果
 */
@Data
@Schema(description = "笔记分享信息")
public class AinoteNoteShareVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "分享ID")
    private String shareId;

    @Schema(description = "笔记ID")
    private String noteId;

    @Schema(description = "分享码")
    private String shareCode;

    @Schema(description = "分享类型：1-链接分享，2-二维码分享")
    private Integer shareType;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "过期时间（NULL表示永久）")
    private Date expireTime;
}
