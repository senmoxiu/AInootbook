package org.jeecg.modules.airag.teaching.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 章节树形视图对象
 */
@Data
@Schema(description = "章节树形视图对象")
public class AinoteChapterTreeVO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "章节ID")
    private String id;

    @Schema(description = "课程ID")
    private String courseId;

    @Schema(description = "章节名称")
    private String chapterName;

    @Schema(description = "章节排序")
    private Integer chapterOrder;

    @Schema(description = "父章节ID")
    private String parentId;

    @Schema(description = "章节描述")
    private String chapterDesc;

    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "子章节列表")
    private List<AinoteChapterTreeVO> children = new ArrayList<>();
}
