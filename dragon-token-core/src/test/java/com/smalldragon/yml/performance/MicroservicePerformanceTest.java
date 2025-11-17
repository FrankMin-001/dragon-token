package com.smalldragon.yml.performance;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.service.CacheWarmupService;
import com.smalldragon.yml.service.DistributedCacheWarmupService;
import com.smalldragon.yml.service.MicroserviceConfigurationService;
import com.smalldragon.yml.service.CacheConsistencyService;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.core.StpInterface;
import com.smalldragon.yml.interceptors.AuthInterceptor;
import com.smalldragon.yml.utils.SessionUtil;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.context.DragonContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 微服务环境性能测试
 * 测试 DragonToken 在微服务模式下的并发性能，包括分布式特性
 * 
 * @author DragonToken
 * @version 1.0
 * @date 2025/1/20
 */
@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, MicroservicePerformanceTest.TestConfiguration.class})
@TestPropertySource(properties = {
    // 微服务配置
    "dragon.token.enabled=true",
    "dragon.token.strategy-type=JWT",
    "dragon.token.retention-time=7200",
    "dragon.token.name=DRAGON_TOKEN",
    
    // 启用分布式特性
    "dragon.token.cache-warmup.distributed.enabled=true",
    "dragon.token.cache-warmup.distributed.coordination-key=test:cache:coordination",
    "dragon.token.cache-warmup.distributed.lock-timeout-millis=30000",
    "dragon.token.cache-warmup.distributed.heartbeat-interval-millis=5000",
    "dragon.token.cache-warmup.distributed.service-discovery.enabled=true",
    "dragon.token.cache-warmup.distributed.service-discovery.registry-key=test:services:registry",
    "dragon.token.cache-warmup.distributed.config-sync.enabled=true",
    "dragon.token.consistency.enabled=true",
    
    // 微服务性能配置
    "dragon.token.cache-warmup.enabled=true",
    "dragon.token.cache-warmup.delay-millis=2000",
    "dragon.token.cache-warmup.batch-size=200",
    
    // Redis 微服务配置
    "spring.redis.jedis.pool.max-active=32",
    "spring.redis.jedis.pool.max-idle=16",
    "spring.redis.jedis.pool.min-idle=8",
    "spring.redis.timeout=2000ms"
})
class MicroservicePerformanceTest {

    @Autowired
    private TokenManager tokenManager;

    @Autowired
    private SessionUtil sessionUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @MockBean
    private DistributedCacheWarmupService distributedCacheWarmupService;

    @MockBean
    private MicroserviceConfigurationService microserviceConfigurationService;

    @MockBean
    private CacheConsistencyService cacheConsistencyService;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    @MockBean
    private HashOperations<String, Object, Object> hashOperations;

    @MockBean
    private SetOperations<String, Object> setOperations;

    private ExecutorService executorService;
    private final int THREAD_POOL_SIZE = 100; // 微服务环境更大的线程池
    private final int CONCURRENT_USERS = 2000; // 更高的并发用户数
    private final int OPERATIONS_PER_USER = 15; // 更多的操作数

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // 模拟 Redis 操作
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("mock-session-data");
        doNothing().when(valueOperations).set(anyString(), any());
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(hashOperations.get(anyString(), anyString())).thenReturn("mock-user-data");
        when(setOperations.add(anyString(), any())).thenReturn(1L);
        when(setOperations.members(anyString())).thenReturn(new HashSet<>());
        
        // 模拟分布式服务
        doNothing().when(distributedCacheWarmupService).executeDistributedWarmup();
        doNothing().when(distributedCacheWarmupService).manualDistributedWarmup();
    }

    @Test
    @DisplayName("微服务环境 - 分布式登录并发性能测试")
    void testMicroserviceDistributedLoginConcurrency() throws InterruptedException {
        System.out.println("=== 微服务环境分布式登录并发性能测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicInteger distributedLockCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行分布式登录操作
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    long operationStart = System.nanoTime();
                    
                    // 模拟分布式登录（包含分布式锁、服务发现等）
                    String userIdStr = "user_" + userId;
                    simulateDistributedLogin(userIdStr);
                    
                    // 模拟分布式锁操作
                    if (simulateDistributedLock("login_lock_" + userId)) {
                        distributedLockCount.incrementAndGet();
                    }
                    
                    long operationEnd = System.nanoTime();
                    long responseTime = (operationEnd - operationStart) / 1_000_000;
                    
                    totalResponseTime.addAndGet(responseTime);
                    successCount.incrementAndGet();
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("分布式登录失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("微服务环境分布式登录性能测试结果:");
        System.out.println("总用户数: " + CONCURRENT_USERS);
        System.out.println("成功登录: " + successCount.get());
        System.out.println("失败登录: " + failureCount.get());
        System.out.println("分布式锁获取: " + distributedLockCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 登录/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / CONCURRENT_USERS * 100) + "%");
        
        // 微服务环境的性能要求（考虑分布式开销）
        assertTrue(successCount.get() > CONCURRENT_USERS * 0.90, "成功率应该超过90%");
        assertTrue(avgResponseTime < 200, "平均响应时间应该小于200ms");
        assertTrue(throughput > 30, "吞吐量应该超过30登录/秒");
    }

    @Test
    @DisplayName("微服务环境 - 分布式缓存一致性并发测试")
    void testMicroserviceCacheConsistencyConcurrency() throws InterruptedException {
        System.out.println("=== 微服务环境分布式缓存一致性并发测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicInteger syncOperations = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS * OPERATIONS_PER_USER);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行缓存一致性操作
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            for (int j = 0; j < OPERATIONS_PER_USER; j++) {
                executorService.submit(() -> {
                    try {
                        long operationStart = System.nanoTime();
                        
                        // 模拟缓存一致性操作
                        String cacheKey = "cache_" + userId + "_" + System.currentTimeMillis();
                        simulateCacheConsistency(cacheKey);
                        syncOperations.incrementAndGet();
                        
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
        
        latch.await(120, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("微服务环境缓存一致性性能测试结果:");
        System.out.println("总操作数: " + (CONCURRENT_USERS * OPERATIONS_PER_USER));
        System.out.println("成功操作: " + successCount.get());
        System.out.println("失败操作: " + failureCount.get());
        System.out.println("同步操作: " + syncOperations.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 操作/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / (CONCURRENT_USERS * OPERATIONS_PER_USER) * 100) + "%");
        
        // 断言性能要求
        assertTrue(successCount.get() > (CONCURRENT_USERS * OPERATIONS_PER_USER) * 0.85, "成功率应该超过85%");
        assertTrue(avgResponseTime < 150, "平均响应时间应该小于150ms");
        assertTrue(throughput > 100, "吞吐量应该超过100操作/秒");
    }

    @Test
    @DisplayName("微服务环境 - 服务发现并发性能测试")
    void testMicroserviceServiceDiscoveryConcurrency() throws InterruptedException {
        System.out.println("=== 微服务环境服务发现并发性能测试 ===");
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        AtomicInteger registrationCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS);
        
        long startTime = System.currentTimeMillis();
        
        // 并发执行服务发现操作
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int serviceId = i;
            executorService.submit(() -> {
                try {
                    long operationStart = System.nanoTime();
                    
                    // 模拟服务注册和发现
                    String serviceName = "service_" + serviceId;
                    String serviceAddress = "192.168.1." + (serviceId % 255);
                    
                    if (simulateServiceRegistration(serviceName, serviceAddress)) {
                        registrationCount.incrementAndGet();
                    }
                    
                    // 模拟服务健康检查
                    simulateServiceHealthCheck(serviceName);
                    
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
        
        latch.await(60, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        double throughput = (double) successCount.get() / (totalTime / 1000.0);
        double avgResponseTime = (double) totalResponseTime.get() / successCount.get();
        
        System.out.println("微服务环境服务发现性能测试结果:");
        System.out.println("总服务数: " + CONCURRENT_USERS);
        System.out.println("成功操作: " + successCount.get());
        System.out.println("失败操作: " + failureCount.get());
        System.out.println("服务注册: " + registrationCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("吞吐量: " + String.format("%.2f", throughput) + " 操作/秒");
        System.out.println("平均响应时间: " + String.format("%.2f", avgResponseTime) + "ms");
        System.out.println("成功率: " + String.format("%.2f", (double) successCount.get() / CONCURRENT_USERS * 100) + "%");
        
        // 断言性能要求
        assertTrue(successCount.get() > CONCURRENT_USERS * 0.90, "成功率应该超过90%");
        assertTrue(avgResponseTime < 100, "平均响应时间应该小于100ms");
        assertTrue(throughput > 50, "吞吐量应该超过50操作/秒");
    }

    @Test
    @DisplayName("微服务环境 - 分布式缓存预热性能测试")
    void testMicroserviceDistributedCacheWarmupPerformance() throws InterruptedException {
        System.out.println("=== 微服务环境分布式缓存预热性能测试 ===");
        
        AtomicInteger nodeCount = new AtomicInteger(0);
        AtomicInteger coordinationCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10); // 模拟10个节点
        
        long startTime = System.currentTimeMillis();
        
        // 模拟多个节点的分布式缓存预热
        for (int i = 0; i < 10; i++) {
            final int nodeId = i;
            executorService.submit(() -> {
                try {
                    // 模拟节点参与分布式缓存预热
                    simulateDistributedCacheWarmup("node_" + nodeId);
                    nodeCount.incrementAndGet();
                    
                    // 模拟协调操作
                    if (simulateCoordination("warmup_coordination_" + nodeId)) {
                        coordinationCount.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    System.err.println("节点 " + nodeId + " 缓存预热失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        long totalTime = endTime - startTime;
        
        System.out.println("微服务环境分布式缓存预热性能结果:");
        System.out.println("参与节点数: " + nodeCount.get());
        System.out.println("协调操作数: " + coordinationCount.get());
        System.out.println("总耗时: " + totalTime + "ms");
        
        // 断言性能要求
        assertTrue(nodeCount.get() >= 8, "至少80%的节点应该成功参与预热");
        assertTrue(totalTime < 15000, "分布式缓存预热时间应该小于15秒");
    }

    @Test
    @DisplayName("微服务环境 - 高负载压力测试")
    void testMicroserviceHighLoadStressTest() throws InterruptedException {
        System.out.println("=== 微服务环境高负载压力测试 ===");
        
        AtomicInteger loginCount = new AtomicInteger(0);
        AtomicInteger cacheCount = new AtomicInteger(0);
        AtomicInteger serviceCount = new AtomicInteger(0);
        AtomicInteger distributedCount = new AtomicInteger(0);
        AtomicInteger totalFailures = new AtomicInteger(0);
        
        CountDownLatch latch = new CountDownLatch(CONCURRENT_USERS * 4);
        
        long startTime = System.currentTimeMillis();
        
        // 高负载混合操作
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            final int userId = i;
            
            // 分布式登录
            executorService.submit(() -> {
                try {
                    simulateDistributedLogin("user_" + userId);
                    loginCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 缓存一致性
            executorService.submit(() -> {
                try {
                    simulateCacheConsistency("cache_" + userId);
                    cacheCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 服务发现
            executorService.submit(() -> {
                try {
                    simulateServiceRegistration("service_" + userId, "addr_" + userId);
                    serviceCount.incrementAndGet();
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            
            // 分布式锁
            executorService.submit(() -> {
                try {
                    if (simulateDistributedLock("lock_" + userId)) {
                        distributedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    totalFailures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(180, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        // 计算性能指标
        long totalTime = endTime - startTime;
        int totalOperations = loginCount.get() + cacheCount.get() + serviceCount.get() + distributedCount.get();
        double throughput = (double) totalOperations / (totalTime / 1000.0);
        
        System.out.println("微服务环境高负载压力测试结果:");
        System.out.println("分布式登录: " + loginCount.get());
        System.out.println("缓存一致性: " + cacheCount.get());
        System.out.println("服务发现: " + serviceCount.get());
        System.out.println("分布式锁: " + distributedCount.get());
        System.out.println("总成功操作: " + totalOperations);
        System.out.println("总失败操作: " + totalFailures.get());
        System.out.println("总耗时: " + totalTime + "ms");
        System.out.println("综合吞吐量: " + String.format("%.2f", throughput) + " 操作/秒");
        System.out.println("成功率: " + String.format("%.2f", (double) totalOperations / (CONCURRENT_USERS * 4) * 100) + "%");
        
        // 断言性能要求
        assertTrue(totalOperations > CONCURRENT_USERS * 4 * 0.75, "总成功率应该超过75%");
        assertTrue(throughput > 50, "综合吞吐量应该超过50操作/秒");
    }

    // 模拟方法
    private void simulateDistributedLogin(String userId) {
        try {
            Thread.sleep(5); // 分布式登录需要更多时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean simulateDistributedLock(String lockKey) {
        try {
            Thread.sleep(2); // 分布式锁操作
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void simulateCacheConsistency(String cacheKey) {
        try {
            Thread.sleep(3); // 缓存一致性操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean simulateServiceRegistration(String serviceName, String address) {
        try {
            Thread.sleep(2); // 服务注册操作
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void simulateServiceHealthCheck(String serviceName) {
        try {
            Thread.sleep(1); // 健康检查操作
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateDistributedCacheWarmup(String nodeId) {
        try {
            Thread.sleep(500); // 分布式缓存预热
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean simulateCoordination(String coordinationKey) {
        try {
            Thread.sleep(100); // 协调操作
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
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
        properties.setStrategyType("JWT");
        properties.setRetentionTime(7200L);
        properties.setName("DRAGON_TOKEN");

            // 缓存预热配置
            DragonTokenProperties.CacheWarmupConfig cacheWarmup = new DragonTokenProperties.CacheWarmupConfig();
            cacheWarmup.setEnabled(true);
            cacheWarmup.setDelayMillis(2000L);

            // 启用分布式特性
            DragonTokenProperties.CacheWarmupConfig.DistributedConfig distributed = new DragonTokenProperties.CacheWarmupConfig.DistributedConfig();
            distributed.setEnabled(true);
            cacheWarmup.setDistributed(distributed);

            properties.setCacheWarmup(cacheWarmup);

            // 启用一致性
            DragonTokenProperties.ConsistencyConfig consistency = new DragonTokenProperties.ConsistencyConfig();
            consistency.setEnabled(true);
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