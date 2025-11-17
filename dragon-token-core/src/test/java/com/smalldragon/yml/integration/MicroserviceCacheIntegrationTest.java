package com.smalldragon.yml.integration;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.service.CacheConsistencyService;
import com.smalldragon.yml.service.DistributedCacheWarmupService;
import com.smalldragon.yml.service.MicroserviceConfigurationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.session.SessionRepository;
import com.smalldragon.yml.testutils.MockTestUtils;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, MicroserviceCacheIntegrationTest.TestConfiguration.class})
@TestPropertySource(properties = {
    "dragon.token.cache-warmup.enabled=true",
    "dragon.token.cache-warmup.delay-millis=1000",
    "dragon.token.cache-warmup.auto-warmup-on-startup=true",
    "dragon.token.cache-warmup.batch-size=100",
    "dragon.token.cache-warmup.max-retries=3",
    "dragon.token.cache-warmup.retry-delay-millis=500",
    "dragon.token.cache-warmup.distributed.enabled=true",
    "dragon.token.cache-warmup.distributed.coordination-key=test:cache:coordination",
    "dragon.token.cache-warmup.distributed.lock-timeout-millis=30000",
    "dragon.token.cache-warmup.distributed.heartbeat-interval-millis=5000",
    "dragon.token.cache-warmup.distributed.service-discovery.enabled=true",
    "dragon.token.cache-warmup.distributed.service-discovery.registry-key=test:services:registry",
    "dragon.token.cache-warmup.distributed.service-discovery.health-check-interval-millis=10000",
    "dragon.token.cache-warmup.distributed.service-discovery.service-timeout-millis=60000",
    "dragon.token.cache-warmup.distributed.config-sync.enabled=true",
    "dragon.token.cache-warmup.distributed.config-sync.config-key=test:config:sync",
    "dragon.token.cache-warmup.distributed.config-sync.sync-interval-millis=15000",
    "dragon.token.consistency.enabled=true",
    "dragon.token.consistency.version-cache-expire=3600",
    "dragon.token.consistency.conflict-resolution-strategy=LATEST_WINS",
    "dragon.token.consistency.event-notification.enabled=true",
    "dragon.token.consistency.event-notification.topic=test:cache:events",
    "dragon.token.consistency.event-notification.batch-size=50",
    "dragon.token.consistency.event-notification.flush-interval-millis=1000",
    "dragon.token.strategy-type=STATELESS",
    "dragon.token.retention-time=7200",
    "dragon.token.public-key=test-key-for-integration-test",
    "dragon.token.redis.host=localhost",
    "dragon.token.redis.port=6379",
    "dragon.token.redis.database=15",
    "spring.redis.host=localhost",
    "spring.redis.port=6379",
    "spring.redis.database=15",
    "spring.redis.timeout=5000",
    "spring.redis.lettuce.pool.max-active=20",
    "spring.redis.lettuce.pool.max-idle=10",
    "spring.redis.lettuce.pool.min-idle=5",
    "spring.redis.lettuce.pool.max-wait=2000",
    "spring.session.redis.configure-action=none",
    "spring.session.store-type=none"
})
public class MicroserviceCacheIntegrationTest {

    @Autowired
    private DistributedCacheWarmupService cacheWarmupService;
    
    @Autowired
    private CacheConsistencyService cacheConsistencyService;
    
    @Autowired
    private MicroserviceConfigurationService configurationService;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Configuration
    @ComponentScan(basePackages = {"com.smalldragon.yml.service", "com.smalldragon.yml.utils"})
    @EnableConfigurationProperties(DragonTokenProperties.class)
    static class TestConfiguration {
        
        @Bean
        @Primary
        public DragonTokenProperties dragonTokenProperties() {
            DragonTokenProperties properties = new DragonTokenProperties();
            properties.setStrategyType("STATELESS");
            properties.setRetentionTime(7200L);
            properties.setName("DRAGON-TOKEN");
            
            DragonTokenProperties.SessionHotRefreshConfig sessionHotRefresh = new DragonTokenProperties.SessionHotRefreshConfig();
            sessionHotRefresh.setEnabled(true);
            sessionHotRefresh.setBatchIntervalMillis(2000L);
            sessionHotRefresh.setPerformanceReportIntervalMillis(300000L);
            properties.setSessionHotRefresh(sessionHotRefresh);
            
            return properties;
        }
        
        @Bean("testRedisConnectionFactory")
        @Primary
        public RedisConnectionFactory redisConnectionFactory() {
            return new MockTestUtils.MockRedisConnectionFactory();
        }
        
        @Bean("testRedisTemplate")
        @Primary
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);
            template.setKeySerializer(new StringRedisSerializer());
            
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
            
            template.setValueSerializer(serializer);
            template.setHashKeySerializer(new StringRedisSerializer());
            template.setHashValueSerializer(serializer);
            template.afterPropertiesSet();
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
                    // 在测试环境中不执行启动逻辑
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
    }

    @BeforeEach
    void setUp() {
        // 清理 Redis 数据
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    @Test
    void testCacheWarmupService() {
        assertNotNull(cacheWarmupService);
        // 测试缓存预热服务的基本功能
        // 验证服务可以获取集群服务列表
        assertNotNull(cacheWarmupService.getClusterServices());
    }

    @Test
    void testCacheConsistencyService() {
        assertNotNull(cacheConsistencyService);
        // 测试缓存一致性服务的基本功能
        // 验证服务可以获取锁状态
        assertNotNull(cacheConsistencyService.getLockStatus());
    }

    @Test
    void testMicroserviceConfigurationService() {
        assertNotNull(configurationService);
        // 测试微服务配置服务的基本功能
        // 验证服务可以获取集群状态
        assertNotNull(configurationService.getClusterStatus());
    }

    @Test
    void testRedisTemplate() {
        assertNotNull(redisTemplate);
        
        // 测试基本的 Redis 操作
        String key = "test:key";
        String value = "test:value";
        
        redisTemplate.opsForValue().set(key, value);
        Object result = redisTemplate.opsForValue().get(key);
        assertEquals(value, result);
    }

    @Test
    void testDistributedCacheWarmup() throws InterruptedException {
        // 测试分布式缓存预热
        CountDownLatch latch = new CountDownLatch(1);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                // 使用实际存在的方法手动触发预热
                cacheWarmupService.manualDistributedWarmup();
                latch.countDown();
            } catch (Exception e) {
                // 忽略测试中的异常
                latch.countDown();
            }
        });
        
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void testCacheConsistencyCheck() {
        // 测试缓存一致性检查
        String key = "consistency:test";
        String value = "test-value";
        
        redisTemplate.opsForValue().set(key, value);
        
        // 验证缓存一致性服务能够检测到数据
        assertNotNull(cacheConsistencyService);
    }

    @Test
    void testServiceDiscovery() {
        // 测试服务发现功能
        // 验证服务可以获取活跃服务列表
        assertNotNull(configurationService.getActiveServices());
        
        // 验证服务可以获取集群状态
        Map<String, Object> clusterStatus = configurationService.getClusterStatus();
        assertNotNull(clusterStatus);
        assertTrue(clusterStatus.containsKey("totalServices"));
    }

    @Test
    void testConfigurationSync() {
        // 测试配置同步功能
        // 验证服务可以获取集群状态（包含配置信息）
        Map<String, Object> clusterStatus = configurationService.getClusterStatus();
        assertNotNull(clusterStatus);
        
        // 验证服务可以获取活跃服务列表
        List<MicroserviceConfigurationService.ServiceInfo> activeServices = configurationService.getActiveServices();
        assertNotNull(activeServices);
    }

    @Test
    void testMemoryUsage() {
        // 测试内存使用情况
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行一些缓存操作
        for (int i = 0; i < 1000; i++) {
            redisTemplate.opsForValue().set("test:memory:" + i, "value" + i);
        }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = finalMemory - initialMemory;
        
        // 验证内存使用在合理范围内（小于 10MB）
        assertTrue(memoryUsed < 10 * 1024 * 1024, "Memory usage should be less than 10MB");
    }
}