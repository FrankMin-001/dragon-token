package com.smalldragon.yml.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

/**
 * JDK1.8高性能缓存配置
 * 使用CompletableFuture和优化的线程池模拟虚拟线程的效果
 */
@Configuration
@EnableAsync
public class HighPerformanceCacheConfig {

    /**
     * 高性能缓存操作线程池
     * 核心线程数 = CPU核心数，最大线程数 = CPU核心数 * 4
     */
    @Bean("cacheExecutor")
    public Executor cacheExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 4;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("dragon-cache-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        System.out.println("高性能缓存线程池初始化完成:");
        System.out.println("- 核心线程数: " + corePoolSize);
        System.out.println("- 最大线程数: " + maxPoolSize);
        System.out.println("- 队列容量: 1000");

        return executor;
    }

    /**
     * 异步操作线程池（用于非阻塞IO）
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int corePoolSize = Runtime.getRuntime().availableProcessors();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize * 2);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("dragon-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        return executor;
    }

    /**
     * 并行处理线程池（基于ForkJoinPool）
     */
    @Bean("parallelExecutor")
    public Executor parallelExecutor() {
        return Executors.newWorkStealingPool(
            Runtime.getRuntime().availableProcessors()
        );
    }

    /**
     * 用于CompletableFuture的默认线程池
     */
    @Bean("completableFutureExecutor")
    public ExecutorService completableFutureExecutor() {
        int threads = Runtime.getRuntime().availableProcessors() * 2;
        return Executors.newFixedThreadPool(
            threads,
            r -> {
                Thread t = new Thread(r, "dragon-cf-" + System.currentTimeMillis());
                t.setDaemon(false);
                return t;
            }
        );
    }

    /**
     * 轻量级任务执行器（模拟虚拟线程效果）
     * 使用工作窃取线程池
     */
    @Bean("lightweightTaskExecutor")
    public ExecutorService lightweightTaskExecutor() {
        return Executors.newWorkStealingPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
    }
}