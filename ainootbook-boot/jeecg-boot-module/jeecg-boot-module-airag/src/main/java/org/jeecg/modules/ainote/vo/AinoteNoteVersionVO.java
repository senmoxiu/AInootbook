package org.jeecg.modules.ainote.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 笔记版本历史视图
 */
@Data
@Schema(description = "笔记版本历史")
public class AinoteNoteVersionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "版本记录ID")
    private String id;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "版本摘要")
    private String summary;

    @Schema(description = "关键词（逗号分隔）")
    private String keywords;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createdAt;

    @Schema(description = "创建人")
    private String createdBy;
}
