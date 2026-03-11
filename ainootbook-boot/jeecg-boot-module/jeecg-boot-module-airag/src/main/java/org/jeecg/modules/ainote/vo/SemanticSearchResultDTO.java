package org.jeecg.modules.ainote.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 语义检索结果
 */
@Data
@Schema(description = "语义检索结果")
public class SemanticSearchResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "笔记ID")
    private String noteId;

    @Schema(description = "笔记标题")
    private String noteTitle;

    @Schema(description = "命中片段")
    private String snippet;

    @Schema(description = "相似度分值")
    private Double score;

    @Schema(description = "作者名称")
    private String authorName;

    @Schema(description = "课程标题")
    private String courseTitle;

    @Schema(description = "是否公开：0-私有，1-公开")
    private Integer isPublic;
}
