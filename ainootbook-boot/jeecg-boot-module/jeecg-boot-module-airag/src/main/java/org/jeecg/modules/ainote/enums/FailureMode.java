package org.jeecg.modules.ainote.enums;

import lombok.Getter;

/**
 * AI 任务失败策略
 */
@Getter
public enum FailureMode {

    SKIP("skip"),
    RETRY("retry"),
    FAIL_ALL("fail_all");

    private final String code;

    FailureMode(String code) {
        this.code = code;
    }

    public static FailureMode fromValue(String value) {
        if (value == null) {
            return RETRY;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return RETRY;
        }
        for (FailureMode mode : values()) {
            if (mode.code.equalsIgnoreCase(normalized) || mode.name().equalsIgnoreCase(normalized)) {
                return mode;
            }
        }
        return RETRY;
    }
}
