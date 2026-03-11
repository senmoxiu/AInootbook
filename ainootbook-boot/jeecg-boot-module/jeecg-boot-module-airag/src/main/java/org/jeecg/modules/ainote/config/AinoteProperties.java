package org.jeecg.modules.ainote.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AInote 模块配置属性
 * 映射 application*.yml 中的 ainote.* 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ainote")
public class AinoteProperties {

    private Task task = new Task();
    private Material material = new Material();
    private Minio minio = new Minio();
    private Share share = new Share();
    private Ai ai = new Ai();
    private Frontend frontend = new Frontend();

    @Data
    public static class Task {
        private Executor executor = new Executor();
        private Timeout timeout = new Timeout();
        private Retry retry = new Retry();

        @Data
        public static class Executor {
            private int corePoolSize;
            private int maxPoolSize;
            private int queueCapacity;
        }

        @Data
        public static class Timeout {
            @Name("default")
            private long defaultTimeout;
            private long asrPoll;
        }

        @Data
        public static class Retry {
            private int maxAttempts;
            private List<Long> delays;
        }
    }

    @Data
    public static class Material {
        private MaxSize maxSize = new MaxSize();
        private AllowedTypes allowedTypes = new AllowedTypes();

        @Data
        public static class MaxSize {
            private long audio;
            private long document;
            private long image;
            private long video;
        }

        @Data
        public static class AllowedTypes {
            private List<String> audio;
            private List<String> document;
            private List<String> image;
            private List<String> video;
        }
    }

    @Data
    public static class Minio {
        private long presignedUrlTtl;
    }

    @Data
    public static class Share {
        private int codeLength;
    }

    @Data
    public static class Ai {
        private Summary summary = new Summary();
        private Keywords keywords = new Keywords();

        @Data
        public static class Summary {
            private int maxLength;
        }

        @Data
        public static class Keywords {
            private int maxCount;
        }
    }

    @Data
    public static class Frontend {
        private long pollInterval;
    }
}
