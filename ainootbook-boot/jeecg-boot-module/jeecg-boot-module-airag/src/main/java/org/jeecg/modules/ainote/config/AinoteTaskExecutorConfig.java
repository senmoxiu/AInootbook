package org.jeecg.modules.ainote.config;

import org.jeecg.common.util.ShiroThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * AInote 任务线程池配置
 */
@Configuration
public class AinoteTaskExecutorConfig {

    private static final int DEFAULT_CORE_POOL_SIZE = 10;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private static final int VIEW_COUNT_CORE_POOL_SIZE = 1;
    private static final int VIEW_COUNT_MAX_POOL_SIZE = 2;
    private static final int VIEW_COUNT_QUEUE_CAPACITY = 256;
    private static final long VIEW_COUNT_KEEP_ALIVE_SECONDS = 30L;

    private final AinoteProperties ainoteProperties;

    public AinoteTaskExecutorConfig(AinoteProperties ainoteProperties) {
        this.ainoteProperties = ainoteProperties;
    }

    @Bean(name = "ainoteTaskExecutor", destroyMethod = "shutdown")
    public ShiroThreadPoolExecutor ainoteTaskExecutor() {
        AinoteProperties.Task.Executor config = ainoteProperties.getTask().getExecutor();

        int corePoolSize = config.getCorePoolSize() > 0 ? config.getCorePoolSize() : DEFAULT_CORE_POOL_SIZE;
        int maxPoolSize = config.getMaxPoolSize() > 0 ? config.getMaxPoolSize() : DEFAULT_MAX_POOL_SIZE;
        int queueCapacity = config.getQueueCapacity() > 0 ? config.getQueueCapacity() : DEFAULT_QUEUE_CAPACITY;

        if (maxPoolSize < corePoolSize) {
            maxPoolSize = corePoolSize;
        }

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(queueCapacity);
        ShiroThreadPoolExecutor executor = new ShiroThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                workQueue
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    @Bean(name = "viewCountExecutor", destroyMethod = "shutdown")
    public ShiroThreadPoolExecutor viewCountExecutor() {
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(VIEW_COUNT_QUEUE_CAPACITY);
        ShiroThreadPoolExecutor executor = new ShiroThreadPoolExecutor(
                VIEW_COUNT_CORE_POOL_SIZE,
                VIEW_COUNT_MAX_POOL_SIZE,
                VIEW_COUNT_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                workQueue
        );
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
