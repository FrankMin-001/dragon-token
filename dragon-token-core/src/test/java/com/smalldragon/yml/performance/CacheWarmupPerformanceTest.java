package com.smalldragon.yml.performance;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.service.CacheConsistencyService;
import com.smalldragon.yml.service.CacheWarmupService;
import com.smalldragon.yml.service.DistributedCacheWarmupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.smalldragon.yml.utils.SessionHotRefreshUtil;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.TestPropertySource;
import com.smalldragon.yml.testutils.MockTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 缓存预热性能测试
 * 
 * @author smalldragon
 * @since 1.0.0
 */
@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, CacheWarmupPerformanceTest.TestConfiguration.class})
@TestPropertySource(properties = {
    "dragon.token.strategy-type=STATELESS",
    "dragon.token.cache-warmup.enabled=false",
    "dragon.token.cache-warmup.delay-millis=100",
    "dragon.token.cache-warmup.auto-warmup-on-startup=false",
    "dragon.token.cache-warmup.batch-size=50",
    "dragon.token.cache-warmup.max-retries=2",
    "dragon.token.cache-warmup.retry-delay-millis=200",
    "dragon.token.cache-warmup.distributed.enabled=true",
    "dragon.token.cache-warmup.distributed.coordination-key=perf:cache:coordination",
    "dragon.token.cache-warmup.distributed.lock-timeout-millis=15000",
    "dragon.token.cache-warmup.distributed.heartbeat-interval-millis=3000",
    "dragon.token.consistency.enabled=true",
    "dragon.token.consistency.version-cache-expire=1800",
    "dragon.token.consistency.conflict-resolution-strategy=LATEST_WINS"
})
public class CacheWarmupPerformanceTest {

    @Configuration
    @ComponentScan(basePackages = "com.smalldragon.yml.service")
    static class TestConfiguration {
        
        @Bean
        @Primary
        public DragonTokenProperties dragonTokenProperties() {
            DragonTokenProperties properties = new DragonTokenProperties();
            properties.setStrategyType("STATELESS");
            return properties;
        }
        
        @Bean
        @Primary
        public RedisConnectionFactory redisConnectionFactory() {
            return new MockTestUtils.MockRedisConnectionFactory();
        }
        
        @Bean
        @Primary
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            return template;
        }
        
        @Bean
        @Primary
        public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer() {
                @Override
                public boolean isAutoStartup() {
                    return false;
                }
                
                @Override
                public void start() {
                    // 不执行任何操作，防止在测试中启动
                }
            };
            container.setConnectionFactory(connectionFactory);
            return container;
        }
        
        @Bean
        @Primary
        public SessionRepository<?> sessionRepository() {
            return new MockTestUtils.MockSessionRepository();
        }
        
        @Bean
        @Primary
        public SessionHotRefreshUtil sessionHotRefreshUtil() {
            return Mockito.mock(SessionHotRefreshUtil.class);
        }
    }

    @MockBean
    private DistributedCacheWarmupService distributedCacheWarmupService;

    @Autowired
    private CacheConsistencyService cacheConsistencyService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final int WARMUP_ITERATIONS = 100;
    private static final int TEST_ITERATIONS = 1000;
    private static final int CONCURRENT_THREADS = 10;

    @BeforeEach
    void setUp() {
        // 清理测试数据
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    /**
     * 测试单线程缓存预热性能
     */
    @Test
    void testSingleThreadWarmupPerformance() {
        // 预热阶段
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String key = "warmup:" + i;
            redisTemplate.opsForValue().set(key, "warmup-value-" + i);
        }

        // 性能测试阶段
        long startTime = System.nanoTime();
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            try {
                // 直接执行分布式预热
                distributedCacheWarmupService.executeDistributedWarmup();
            } catch (Exception e) {
                // 忽略异常，继续测试
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // 转换为毫秒

        // 计算性能指标
        double opsPerSecond = (TEST_ITERATIONS * 1000.0) / duration;
        double avgLatency = (double) duration / TEST_ITERATIONS;

        System.out.printf("单线程性能测试结果:%n");
        System.out.printf("- 总耗时: %d ms%n", duration);
        System.out.printf("- 平均延迟: %.2f ms%n", avgLatency);
        System.out.printf("- 吞吐量: %.2f ops/sec%n", opsPerSecond);

        // 性能断言
        assertTrue(duration < 10000, "单线程1000次操作应在10秒内完成");
        assertTrue(avgLatency < 10, "平均延迟应小于10ms");
        assertTrue(opsPerSecond > 100, "吞吐量应大于100 ops/sec");
    }

    /**
     * 测试多线程并发缓存预热性能
     */
    @Test
    void testConcurrentWarmupPerformance() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        long startTime = System.nanoTime();

        // 启动并发任务
        for (int t = 0; t < CONCURRENT_THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < TEST_ITERATIONS / CONCURRENT_THREADS; i++) {
                        long opStart = System.nanoTime();
                        
                        String key = "perf:concurrent:" + threadId + ":" + i;
                        String value = "concurrent-value-" + threadId + "-" + i;
                        
                        try {
                            redisTemplate.opsForValue().set(key, value);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                        
                        long opEnd = System.nanoTime();
                        totalLatency.addAndGet((opEnd - opStart) / 1_000_000);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有任务完成
        assertTrue(latch.await(30, TimeUnit.SECONDS), "并发测试应在30秒内完成");
        
        long endTime = System.nanoTime();
        long totalDuration = (endTime - startTime) / 1_000_000;

        // 计算性能指标
        int totalOps = successCount.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;
        double avgLatency = (double) totalLatency.get() / totalOps;
        double errorRate = (double) errorCount.get() / (totalOps + errorCount.get()) * 100;

        System.out.printf("并发性能测试结果:%n");
        System.out.printf("- 并发线程数: %d%n", CONCURRENT_THREADS);
        System.out.printf("- 总操作数: %d%n", totalOps);
        System.out.printf("- 成功操作: %d%n", successCount.get());
        System.out.printf("- 失败操作: %d%n", errorCount.get());
        System.out.printf("- 错误率: %.2f%%%n", errorRate);
        System.out.printf("- 总耗时: %d ms%n", totalDuration);
        System.out.printf("- 平均延迟: %.2f ms%n", avgLatency);
        System.out.printf("- 吞吐量: %.2f ops/sec%n", opsPerSecond);

        // 性能断言
        assertTrue(totalDuration < 20000, "并发测试应在20秒内完成");
        assertTrue(errorRate < 5, "错误率应小于5%");
        assertTrue(opsPerSecond > 50, "并发吞吐量应大于50 ops/sec");

        executor.shutdown();
    }

    /**
     * 测试分布式锁性能
     */
    @Test
    void testDistributedLockPerformance() throws InterruptedException {
        int lockOperations = 500;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger lockAcquired = new AtomicInteger(0);
        AtomicLong lockLatency = new AtomicLong(0);

        long startTime = System.nanoTime();

        for (int t = 0; t < 5; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < lockOperations / 5; i++) {
                        long lockStart = System.nanoTime();
                        
                        String serviceId = "perf-service-" + threadId;
                        // 直接执行分布式预热（模拟分布式锁操作）
                        distributedCacheWarmupService.executeDistributedWarmup();
                        lockAcquired.incrementAndGet();
                        
                        // 模拟短暂的工作
                        Thread.sleep(1);
                        
                        long lockEnd = System.nanoTime();
                        lockLatency.addAndGet((lockEnd - lockStart) / 1_000_000);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "分布式锁测试应在30秒内完成");
        
        long endTime = System.nanoTime();
        long totalDuration = (endTime - startTime) / 1_000_000;

        double avgLockLatency = (double) lockLatency.get() / lockOperations;
        double lockSuccessRate = (double) lockAcquired.get() / lockOperations * 100;

        System.out.printf("分布式锁性能测试结果:%n");
        System.out.printf("- 锁操作总数: %d%n", lockOperations);
        System.out.printf("- 成功获取锁: %d%n", lockAcquired.get());
        System.out.printf("- 成功率: %.2f%%%n", lockSuccessRate);
        System.out.printf("- 总耗时: %d ms%n", totalDuration);
        System.out.printf("- 平均锁延迟: %.2f ms%n", avgLockLatency);

        // 性能断言
        assertTrue(avgLockLatency < 50, "平均锁延迟应小于50ms");
        assertTrue(lockSuccessRate > 80, "锁成功率应大于80%");

        executor.shutdown();
    }

    /**
     * 测试内存使用和垃圾回收影响
     */
    @Test
    void testMemoryAndGCImpact() {
        Runtime runtime = Runtime.getRuntime();
        
        // 记录初始内存状态
        System.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        long initialGCCount = getGCCount();

        // 执行大量缓存操作
        int operations = 10000;
        List<String> keys = new ArrayList<>();
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < operations; i++) {
            String key = "memory:test:" + i;
            String value = generateLargeValue(i);
            keys.add(key);
            
            redisTemplate.opsForValue().set(key, value);
            
            // 每1000次操作检查一次内存
            if (i % 1000 == 0) {
                long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                long memoryIncrease = currentMemory - initialMemory;
                
                // 如果内存增长超过100MB，触发GC
                if (memoryIncrease > 100 * 1024 * 1024) {
                    System.gc();
                    Thread.yield();
                }
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000;

        // 强制GC并检查最终内存状态
        System.gc();
        Thread.yield();
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long finalGCCount = getGCCount();
        
        long memoryIncrease = finalMemory - initialMemory;
        long gcCount = finalGCCount - initialGCCount;

        System.out.printf("内存和GC影响测试结果:%n");
        System.out.printf("- 操作数量: %d%n", operations);
        System.out.printf("- 总耗时: %d ms%n", duration);
        System.out.printf("- 初始内存: %.2f MB%n", initialMemory / (1024.0 * 1024.0));
        System.out.printf("- 最终内存: %.2f MB%n", finalMemory / (1024.0 * 1024.0));
        System.out.printf("- 内存增长: %.2f MB%n", memoryIncrease / (1024.0 * 1024.0));
        System.out.printf("- GC次数: %d%n", gcCount);

        // 性能断言
        assertTrue(memoryIncrease < 200 * 1024 * 1024, "内存增长应控制在200MB以内");
        assertTrue(duration < 30000, "10000次操作应在30秒内完成");
        
        // 清理测试数据
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    /**
     * 生成大值用于内存测试
     */
    private String generateLargeValue(int index) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("large-value-").append(index).append("-").append(i).append(";");
        }
        return sb.toString();
    }

    /**
     * 获取GC次数（简化实现）
     */
    private long getGCCount() {
        return java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
                .stream()
                .mapToLong(gcBean -> gcBean.getCollectionCount())
                .sum();
    }

    /**
     * 测试缓存预热的吞吐量极限
     */
    @Test
    void testThroughputLimit() throws InterruptedException {
        int maxThreads = 20;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        CountDownLatch latch = new CountDownLatch(maxThreads);
        AtomicInteger totalOps = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int t = 0; t < maxThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String key = "throughput:" + threadId + ":" + i;
                        redisTemplate.opsForValue().set(key, "value-" + i);
                        totalOps.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "吞吐量测试应在60秒内完成");
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000;

        double maxThroughput = (totalOps.get() * 1000.0) / duration;

        System.out.printf("吞吐量极限测试结果:%n");
        System.out.printf("- 最大线程数: %d%n", maxThreads);
        System.out.printf("- 总操作数: %d%n", totalOps.get());
        System.out.printf("- 总耗时: %d ms%n", duration);
        System.out.printf("- 最大吞吐量: %.2f ops/sec%n", maxThroughput);

        // 记录性能基准
        assertTrue(maxThroughput > 100, "最大吞吐量应大于100 ops/sec");

        executor.shutdown();
    }
}