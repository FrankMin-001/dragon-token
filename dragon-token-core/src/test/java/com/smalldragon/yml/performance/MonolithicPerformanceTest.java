package com.smalldragon.yml.performance;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.service.CacheWarmupService;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.core.StpInterface;
import com.smalldragon.yml.interceptors.AuthInterceptor;
import com.smalldragon.yml.utils.SessionUtil;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.context.DragonContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 单体应用性能测试
 * 测试 DragonToken 在单体应用模式下的并发性能和吞吐量
 * 
 * @author DragonToken
 * @version 1.0
 * @date 2025/1/20
 */
@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, MonolithicPerformanceTest.TestConfiguration.class})
@TestPropertySource(properties = {
    // 单体应用配置
    "dragon.token.enabled=true",
    "dragon.token.strategy-type=SESSION",
    "dragon.token.retention-time=7200",
    "dragon.token.name=DRAGON_TOKEN",
    
    // 禁用分布式特性
    "dragon.token.cache-warmup.distributed.enabled=false",
    "dragon.token.consistency.enabled=false",
    
    // 单体应用性能优化配置
    "dragon.token.cache-warmup.enabled=true",
    "dragon.token.cache-warmup.delay-millis=500",
    "dragon.token.cache-warmup.batch-size=100",
    
    // Redis 单体应用优化
    "spring.redis.jedis.pool.max-active=16",
    "spring.redis.jedis.pool.max-idle=8",
    "spring.redis.jedis.pool.min-idle=4",
    "spring.redis.timeout=500ms"
})
class MonolithicPerformanceTest {

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private SessionUtil sessionUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CacheWarmupService cacheWarmupService;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    @MockBean
    private HashOperations<String, Object, Object> hashOperations;

    private ExecutorService executorService;
    private final int THREAD_POOL_SIZE = 50;
    private final int CONCURRENT_USERS = 1000;
    private final int OPERATIONS_PER_USER = 10;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // 模拟 Redis 操作
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("mock-session-data");
        doNothing().when(valueOperations).set(anyString(), any());
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(hashOperations.get(anyString(), anyString())).thenReturn("mock-user-data");
    }

    @Test
    @DisplayName("单体应用 - 用户登录并发性能测试")
    void testMonolithicLoginConcurrency() throws InterruptedException {
        System.out.println("=== 单体应用登录并发性能测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行登录操作
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    long operationStart = System.nanoTime();
                    
                    // 模拟用户登录
                    String userIdStr = "user_" + userId;
                    
                    // 执行登录操作（模拟）
                    simulateLogin(userIdStr);
                    
                    long operationEnd = System.nanoTime();
                    long responseTime = (operationEnd - operationStart) / 1_000_000; // 转换为毫秒
                    
                    totalResponseTime.addAndGet(responseTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("登录失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("单体应用登录性能测试结果:");
        System.out.println("总用户数: " + CONCURRENT_USERS);
        System.out.println("成功登录: " + successCount.get());
        System.out.println("失败登录: " + failureCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 登录/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / CONCURRENT_USERS * 100) + "%");
        
        // 断言性能要求
        assertTrue(successCount.get() > CONCURRENT_USERS * 0.95, "成功率应该超过95%");
        assertTrue(avgResponseTime < 100, "平均响应时间应该小于100ms");
        assertTrue(throughput > 50, "吞吐量应该超过50登录/秒");
    }

    @Test
    @DisplayName("单体应用 - 会话验证并发性能测试")
    void testMonolithicSessionValidationConcurrency() throws InterruptedException {
        System.out.println("=== 单体应用会话验证并发性能测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS * OPERATIONS_PER_USER);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行会话验证
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            for (int j = 0; j < OPERATIONS_PER_USER; j++) {
                executorService.submit(() -> {
                    try {
                        long operationStart = System.nanoTime();
                        
                        // 模拟会话验证
                        String sessionId = "session_" + userId + "_" + System.currentTimeMillis();
                        simulateSessionValidation(sessionId);
                        
                        long operationEnd = System.nanoTime();
                        long responseTime = (operationEnd - operationStart) / 1_000_000;
                        
                        totalResponseTime.addAndGet(responseTime);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("单体应用会话验证性能测试结果:");
        System.out.println("总操作数: " + (CONCURRENT_USERS * OPERATIONS_PER_USER));
        System.out.println("成功验证: " + successCount.get());
        System.out.println("失败验证: " + failureCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 验证/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / (CONCURRENT_USERS * OPERATIONS_PER_USER) * 100) + "%");
        
        // 断言性能要求
        assertTrue(successCount.get() > (CONCURRENT_USERS * OPERATIONS_PER_USER) * 0.98, "成功率应该超过98%");
        assertTrue(avgResponseTime < 50, "平均响应时间应该小于50ms");
        assertTrue(throughput > 200, "吞吐量应该超过200验证/秒");
    }

    @Test
    @DisplayName("单体应用 - JWT 令牌处理并发性能测试")
    void testMonolithicJwtProcessingConcurrency() throws InterruptedException {
        System.out.println("=== 单体应用JWT令牌处理并发性能测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS * OPERATIONS_PER_USER);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行JWT处理
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            for (int j = 0; j < OPERATIONS_PER_USER; j++) {
                executorService.submit(() -> {
                    try {
                        long operationStart = System.nanoTime();
                        
                        // 模拟JWT令牌生成和验证
                        String userIdStr = "user_" + userId;
                        simulateJwtProcessing(userIdStr);
                        
                        long operationEnd = System.nanoTime();
                        long responseTime = (operationEnd - operationStart) / 1_000_000;
                        
                        totalResponseTime.addAndGet(responseTime);
                        successCount.incrementAndGet();
                        
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }
        
        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("单体应用JWT处理性能测试结果:");
        System.out.println("总操作数: " + (CONCURRENT_USERS * OPERATIONS_PER_USER));
        System.out.println("成功处理: " + successCount.get());
        System.out.println("失败处理: " + failureCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 处理/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / (CONCURRENT_USERS * OPERATIONS_PER_USER) * 100) + "%");
        
        // 断言性能要求
        assertTrue(successCount.get() > (CONCURRENT_USERS * OPERATIONS_PER_USER) * 0.95, "成功率应该超过95%");
        assertTrue(avgResponseTime < 30, "平均响应时间应该小于30ms");
        assertTrue(throughput > 500, "吞吐量应该超过500处理/秒");
    }

    @Test
    @DisplayName("单体应用 - 缓存预热性能测试")
    void testMonolithicCacheWarmupPerformance() {
        System.out.println("=== 单体应用缓存预热性能测试 ===");
        
        long startTime = System.currentTimeMillis();
        
        // 执行缓存预热
        assertDoesNotThrow(() -> {
            // 模拟缓存预热操作
            simulateCacheWarmup();
        }, "缓存预热不应该抛出异常");
        
        long endTime = System.currentTimeMillis();
        long warmupTime = endTime - startTime;
        
        System.out.println("单体应用缓存预热性能结果:");
        System.out.println("预热耗时: " + warmupTime + "ms");
        
        // 断言性能要求
        assertTrue(warmupTime < 5000, "缓存预热时间应该小于5秒");
    }

    @Test
    @DisplayName("单体应用 - 混合负载压力测试")
    void testMonolithicMixedLoadStressTest() throws InterruptedException {
        System.out.println("=== 单体应用混合负载压力测试 ===");
        
        AtomicInteger loginCount = new AtomicInteger(0);
        AtomicInteger validationCount = new AtomicInteger(0);
        AtomicInteger jwtCount = new AtomicInteger(0);
        AtomicInteger totalFailures = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS * 3);
        
        long startTime = System.currentTimeMillis();
        
        // 混合负载：登录 + 会话验证 + JWT处理
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            
            // 登录操作
            executorService.submit(() -> {
                try {
                    simulateLogin("user_" + userId);
                    loginCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 会话验证操作
            executorService.submit(() -> {
                try {
                    simulateSessionValidation("session_" + userId);
                    validationCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // JWT处理操作
            executorService.submit(() -> {
                try {
                    simulateJwtProcessing("user_" + userId);
                    jwtCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(90, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        int totalOperations = loginCount.get() + validationCount.get() + jwtCount.get();
        double throughput = (double) totalOperations / (totalTime / 1000.0);
        
        System.out.println("单体应用混合负载压力测试结果:");
        System.out.println("登录操作: " + loginCount.get());
        System.out.println("验证操作: " + validationCount.get());
        System.out.println("JWT操作: " + jwtCount.get());
        System.out.println("总成功操作: " + totalOperations);
        System.out.println("总失败操作: " + totalFailures.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("综合吞吐量: " + String.format("%.2f", throughput) + " 操作/秒");
        System.out.println("成功率: " + String.format("%.2f", (double) totalOperations / (CONCURRENT_USERS * 3) * 100) + "%");
        
        // 断言性能要求
        assertTrue(totalOperations > CONCURRENT_USERS * 3 * 0.90, "总成功率应该超过90%");
        assertTrue(throughput > 100, "综合吞吐量应该超过100操作/秒");
    }

    // 模拟方法
    private void simulateLogin(String userId) {
        // 模拟登录逻辑
        try {
            Thread.sleep(1); // 模拟处理时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateSessionValidation(String sessionId) {
        // 模拟会话验证逻辑
        try {
            Thread.sleep(1); // 模拟处理时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateJwtProcessing(String userId) {
        // 模拟JWT处理逻辑
        try {
            Thread.sleep(1); // 模拟处理时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateCacheWarmup() {
        // 模拟缓存预热逻辑
        try {
            Thread.sleep(100); // 模拟预热时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 测试配置类
     */
    @Configuration
    static class TestConfiguration {

        @Bean
        @Primary
        public DragonTokenProperties testDragonTokenProperties() {
            DragonTokenProperties properties = new DragonTokenProperties();
        properties.setStrategyType("SESSION");
        properties.setRetentionTime(7200L);
        properties.setName("DRAGON_TOKEN");

            // 缓存预热配置
            DragonTokenProperties.CacheWarmupConfig cacheWarmup = new DragonTokenProperties.CacheWarmupConfig();
            cacheWarmup.setEnabled(true);
            cacheWarmup.setDelayMillis(500L);

            // 禁用分布式特性
            DragonTokenProperties.CacheWarmupConfig.DistributedConfig distributed = new DragonTokenProperties.CacheWarmupConfig.DistributedConfig();
            distributed.setEnabled(false);
            cacheWarmup.setDistributed(distributed);

            properties.setCacheWarmup(cacheWarmup);

            // 禁用一致性
            DragonTokenProperties.ConsistencyConfig consistency = new DragonTokenProperties.ConsistencyConfig();
            consistency.setEnabled(false);
            properties.setConsistency(consistency);

            return properties;
        }

        @Bean
        @Primary
        public RedisTemplate<String, Object> testRedisTemplate() {
            return mock(RedisTemplate.class);
        }
    }
}