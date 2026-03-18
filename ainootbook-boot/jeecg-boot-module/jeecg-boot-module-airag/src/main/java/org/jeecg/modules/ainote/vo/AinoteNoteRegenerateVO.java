package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 重新生成笔记返回对象
 */
@Data
@Schema(description = "重新生成笔记结果")
public class AinoteNoteRegenerateVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "新版本号")
    private Integer version;

    @Schema(description = "笔记内容（Markdown格式）")
    private String noteContent;
}
