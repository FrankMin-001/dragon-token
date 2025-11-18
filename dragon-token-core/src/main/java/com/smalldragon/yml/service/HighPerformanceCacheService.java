package com.smalldragon.yml.service;

import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.context.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/**
 * JDK1.8高性能缓存服务
 * 使用CompletableFuture和优化线程池模拟虚拟线程的效果
 */
@Service
@ConditionalOnProperty(name = "dragon.token.high-performance.enabled", havingValue = "true", matchIfMissing = false)
public class HighPerformanceCacheService {

    private static final Logger logger = LoggerFactory.getLogger(HighPerformanceCacheService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    @Qualifier("cacheExecutor")
    private Executor cacheExecutor;

    @Autowired
    @Qualifier("completableFutureExecutor")
    private ExecutorService completableFutureExecutor;

    /**
     * 异步获取用户信息（CompletableFuture方式）
     */
    public CompletableFuture<UserContext> getUserInfoAsync(String tenantId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = StringUtils.hasText(tenantId)
                        ? CacheKeyConstants.buildUserCacheKey(tenantId, userId)
                        : CacheKeyConstants.buildUserCacheKeyLegacy(userId);

                Object cachedUserInfo = redisTemplate.opsForValue().get(cacheKey);
                if (cachedUserInfo != null) {
                    logger.debug("Async cache hit for user: {} in tenant: {}", userId, tenantId);
                    return (UserContext) cachedUserInfo;
                }
                logger.debug("Async cache miss for user: {} in tenant: {}", userId, tenantId);
                return null;
            } catch (Exception e) {
                logger.error("Error getting user info async: {}", e.getMessage(), e);
                return null;
            }
        }, completableFutureExecutor);
    }

    /**
     * 批量异步获取用户信息（并行处理）
     */
    public CompletableFuture<Map<String, UserContext>> batchGetUserInfoAsync(
            String tenantId, List<String> userIds) {

        // 并行处理所有用户ID
        List<CompletableFuture<Map.Entry<String, UserContext>>> futures = userIds.stream()
                .map(userId -> getUserInfoAsync(tenantId, userId)
                        .thenApply(userContext -> Map.entry(userId, userContext)))
                .collect(Collectors.toList());

        // 等待所有任务完成并合并结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(entry -> entry.getValue() != null)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        )));
    }

    /**
     * 异步缓存用户信息
     */
    @Async("cacheExecutor")
    public CompletableFuture<Void> cacheUserInfoAsync(UserContext userContext) {
        return CompletableFuture.runAsync(() -> {
            try {
                String cacheKey;
                if (StringUtils.hasText(userContext.getTenantId())) {
                    cacheKey = CacheKeyConstants.buildUserCacheKey(
                            userContext.getTenantId(), userContext.getUserId());
                } else {
                    cacheKey = CacheKeyConstants.buildUserCacheKeyLegacy(userContext.getUserId());
                }

                redisTemplate.opsForValue().set(cacheKey, userContext);
                logger.debug("Async cached user: {} in tenant: {}",
                        userContext.getUserId(), userContext.getTenantId());
            } catch (Exception e) {
                logger.error("Error caching user info async: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 并行批量缓存用户信息
     */
    @Async("cacheExecutor")
    public CompletableFuture<Void> batchCacheUserInfoAsync(List<UserContext> userContexts) {
        List<CompletableFuture<Void>> futures = userContexts.stream()
                .map(this::cacheUserInfoAsync)
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.debug("Async batch cached {} users", userContexts.size()));
    }

    /**
     * 异步获取权限列表
     */
    public CompletableFuture<List<String>> getPermissionsAsync(String tenantId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = StringUtils.hasText(tenantId)
                        ? CacheKeyConstants.buildUserPermissionsKey(tenantId, userId)
                        : CacheKeyConstants.buildUserPermissionsKeyLegacy(userId);

                Object cached = redisTemplate.opsForValue().get(cacheKey);
                return cached != null ? (List<String>) cached : Collections.emptyList();
            } catch (Exception e) {
                logger.error("Error getting permissions async: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        }, completableFutureExecutor);
    }

    /**
     * 异步获取角色列表
     */
    public CompletableFuture<List<String>> getRolesAsync(String tenantId, String userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cacheKey = StringUtils.hasText(tenantId)
                        ? CacheKeyConstants.buildUserRolesKey(tenantId, userId)
                        : CacheKeyConstants.buildUserRolesKeyLegacy(userId);

                Object cached = redisTemplate.opsForValue().get(cacheKey);
                return cached != null ? (List<String>) cached : Collections.emptyList();
            } catch (Exception e) {
                logger.error("Error getting roles async: {}", e.getMessage(), e);
                return Collections.emptyList();
            }
        }, completableFutureExecutor);
    }

    /**
     * 并行预热多个租户的缓存
     */
    @Async("parallelExecutor")
    public CompletableFuture<Void> preWarmTenantsAsync(List<String> tenantIds, List<String> userIds) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 并行处理每个租户
        for (String tenantId : tenantIds) {
            // 为每个租户创建并行任务
            CompletableFuture<Void> tenantFuture = CompletableFuture.runAsync(() -> {
                List<CompletableFuture<?>> userFutures = userIds.stream()
                        .flatMap(userId -> {
                            // 为每个用户创建多个并行任务
                            return Arrays.asList(
                                    getUserInfoAsync(tenantId, userId),
                                    getPermissionsAsync(tenantId, userId),
                                    getRolesAsync(tenantId, userId)
                            ).stream();
                        })
                        .collect(Collectors.toList());

                // 等待该租户的所有用户任务完成
                CompletableFuture.allOf(userFutures.toArray(new CompletableFuture[0])).join();
            }, cacheExecutor);

            futures.add(tenantFuture);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.info("Async pre-warmed cache for {} tenants and {} users",
                        tenantIds.size(), userIds.size()));
    }

    /**
     * 高性能批量操作（分片处理）
     */
    public CompletableFuture<Void> highPerformanceBatchProcess(
            String tenantId, List<String> userIds, int batchSize) {

        List<List<String>> batches = partitionList(userIds, batchSize);
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();

        for (int i = 0; i < batches.size(); i++) {
            List<String> batch = batches.get(i);
            final int batchNumber = i;

            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                logger.debug("Processing batch {} with {} users", batchNumber, batch.size());

                List<CompletableFuture<Void>> userFutures = batch.stream()
                        .map(userId -> getUserInfoAsync(tenantId, userId)
                                .thenAccept(userContext -> {
                                    if (userContext != null) {
                                        // 处理用户信息
                                    }
                                }))
                        .collect(Collectors.toList());

                CompletableFuture.allOf(userFutures.toArray(new CompletableFuture[0])).join();

                logger.debug("Completed batch {}", batchNumber);
            }, cacheExecutor);

            batchFutures.add(batchFuture);
        }

        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.info("High performance batch processing completed for {} users", userIds.size()));
    }

    /**
     * 工具方法：将列表分片
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    /**
     * 性能监控方法
     */
    public Map<String, Object> getPerformanceStats() {
        try {
            Runtime runtime = Runtime.getRuntime();
            return Map.of(
                    "availableProcessors", runtime.availableProcessors(),
                    "highPerformanceMode", true,
                    "maxMemory", runtime.maxMemory(),
                    "totalMemory", runtime.totalMemory(),
                    "freeMemory", runtime.freeMemory(),
                    "usedMemory", runtime.totalMemory() - runtime.freeMemory(),
                    "memoryUsagePercent", (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory() * 100
            );
        } catch (Exception e) {
            logger.error("Error getting performance stats: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 创建轻量级异步任务（模拟虚拟线程的轻量级特性）
     */
    public <T> CompletableFuture<T> supplyLightweight(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, completableFutureExecutor);
    }

    /**
     * 运行轻量级异步任务
     */
    public CompletableFuture<Void> runLightweight(Runnable runnable) {
        return CompletableFuture.runAsync(runnable, completableFutureExecutor);
    }
}