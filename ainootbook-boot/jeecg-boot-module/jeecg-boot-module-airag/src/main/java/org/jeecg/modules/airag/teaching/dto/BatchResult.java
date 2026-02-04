package org.jeecg.modules.airag.teaching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量操作结果响应对象
 */
@Data
@NoArgsConstructor
@Schema(description = "批量操作结果")
public class BatchResult implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "成功数量")
    private int successCount;

    @Schema(description = "失败列表")
    private List<FailedItem> failedList = new ArrayList<>();

    @Schema(description = "总数量")
    private int totalCount;

    /**
     * 构造函数
     */
    public BatchResult(int successCount, List<FailedItem> failedList) {
        this.successCount = successCount;
        this.failedList = failedList != null ? failedList : new ArrayList<>();
        this.totalCount = this.successCount + this.failedList.size();
    }

    /**
     * 失败项
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "失败项")
    public static class FailedItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "组织ID")
        private String departId;

        @Schema(description = "组织名称")
        private String departName;

        @Schema(description = "失败原因")
        private String reason;

        /**
         * 简化构造函数（无组织名称）
         */
        public FailedItem(String departId, String reason) {
            this.departId = departId;
            this.departName = null;
            this.reason = reason;
        }
    }

    public void addFailed(String departId, String departName, String reason) {
        this.failedList.add(new FailedItem(departId, departName, reason));
    }
}
