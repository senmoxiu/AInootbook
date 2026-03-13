package org.jeecg.modules.ainote.enums;

import lombok.Getter;

/**
 * Ainote 处理类型
 * 用于统一配置解析时区分处理类型
 */
@Getter
public enum AinoteProcessingType {

    ASR("asr", "语音转写"),
    OCR("ocr", "文字识别"),
    VIDEO("video", "视频处理"),
    SUMMARY("summary", "摘要生成"),
    KEYWORDS("keywords", "关键词提取"),
    INTEGRATE("integrate", "内容整合");

    AinoteProcessingType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    final String code;
    final String description;
}
