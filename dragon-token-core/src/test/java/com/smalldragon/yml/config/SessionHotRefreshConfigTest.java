package com.smalldragon.yml.config;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.SessionHotRefreshUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session热刷新配置测试类
 * 验证配置属性的正确加载和默认值设置
 * 
 * @author YML
 * @date 2025/1/27
 */
@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, SessionHotRefreshConfigTest.TestConfiguration.class})
@TestPropertySource(properties = {
        "dragon.token.strategy-type=STATELESS",
        "dragon.token.session-hot-refresh.enabled=true",
        "dragon.token.session-hot-refresh.debounce-delay-millis=2000",
        "dragon.token.session-hot-refresh.batch-size=100",
        "dragon.token.session-hot-refresh.batch-interval-millis=3000",
        "dragon.token.session-hot-refresh.local-cache-size=2000",
        "dragon.token.session-hot-refresh.local-cache-expire-millis=600000",
        "dragon.token.session-hot-refresh.smart-refresh-threshold-seconds=3600",
        "dragon.token.session-hot-refresh.async-thread-pool-size=4",
        "dragon.token.session-hot-refresh.performance-monitor-enabled=false",
        "dragon.token.session-hot-refresh.performance-report-interval-millis=600000"
})
class SessionHotRefreshConfigTest {

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    @Test
    void testSessionHotRefreshConfigLoading() {
        DragonTokenProperties.SessionHotRefreshConfig config = dragonTokenProperties.getSessionHotRefresh();
        
        assertNotNull(config, "Session热刷新配置不应为null");
        
        // 验证配置值是否正确加载
        assertTrue(config.isEnabled(), "热刷新应该启用");
        assertEquals(2000L, config.getDebounceDelayMillis(), "防抖延迟时间应为2000ms");
        assertEquals(100, config.getBatchSize(), "批处理大小应为100");
        assertEquals(3000L, config.getBatchIntervalMillis(), "批处理间隔应为3000ms");
        assertEquals(2000, config.getLocalCacheSize(), "本地缓存大小应为2000");
        assertEquals(600000L, config.getLocalCacheExpireMillis(), "本地缓存过期时间应为600000ms");
        assertEquals(3600L, config.getSmartRefreshThresholdSeconds(), "智能刷新阈值应为3600秒");
        assertEquals(4, config.getAsyncThreadPoolSize(), "异步线程池大小应为4");
        assertFalse(config.isPerformanceMonitorEnabled(), "性能监控应该禁用");
        assertEquals(600000L, config.getPerformanceReportIntervalMillis(), "性能报告间隔应为600000ms");
    }

    @Test
    void testDefaultValues() {
        // 创建新的配置对象测试默认值
        DragonTokenProperties.SessionHotRefreshConfig defaultConfig = 
                new DragonTokenProperties.SessionHotRefreshConfig();
        
        // 验证默认值
        assertTrue(defaultConfig.isEnabled(), "默认应该启用热刷新");
        assertEquals(1000L, defaultConfig.getDebounceDelayMillis(), "默认防抖延迟应为1000ms");
        assertEquals(50, defaultConfig.getBatchSize(), "默认批处理大小应为50");
        assertEquals(2000L, defaultConfig.getBatchIntervalMillis(), "默认批处理间隔应为2000ms");
        assertEquals(1000, defaultConfig.getLocalCacheSize(), "默认本地缓存大小应为1000");
        assertEquals(300000L, defaultConfig.getLocalCacheExpireMillis(), "默认本地缓存过期时间应为300000ms");
        assertEquals(1800L, defaultConfig.getSmartRefreshThresholdSeconds(), "默认智能刷新阈值应为1800秒");
        assertEquals(2, defaultConfig.getAsyncThreadPoolSize(), "默认异步线程池大小应为2");
        assertTrue(defaultConfig.isPerformanceMonitorEnabled(), "默认应该启用性能监控");
        assertEquals(300000L, defaultConfig.getPerformanceReportIntervalMillis(), "默认性能报告间隔应为300000ms");
    }

    @Test
    void testConfigurationValidation() {
        DragonTokenProperties.SessionHotRefreshConfig config = dragonTokenProperties.getSessionHotRefresh();
        
        // 验证配置的合理性
        assertTrue(config.getDebounceDelayMillis() > 0, "防抖延迟时间应大于0");
        assertTrue(config.getBatchSize() > 0, "批处理大小应大于0");
        assertTrue(config.getBatchIntervalMillis() > 0, "批处理间隔应大于0");
        assertTrue(config.getLocalCacheSize() > 0, "本地缓存大小应大于0");
        assertTrue(config.getLocalCacheExpireMillis() > 0, "本地缓存过期时间应大于0");
        assertTrue(config.getSmartRefreshThresholdSeconds() > 0, "智能刷新阈值应大于0");
        assertTrue(config.getAsyncThreadPoolSize() > 0, "异步线程池大小应大于0");
        assertTrue(config.getPerformanceReportIntervalMillis() > 0, "性能报告间隔应大于0");
    }

    @Test
    void testGettersAndSetters() {
        DragonTokenProperties.SessionHotRefreshConfig config = 
                new DragonTokenProperties.SessionHotRefreshConfig();
        
        // 测试所有setter和getter
        config.setEnabled(false);
        assertFalse(config.isEnabled());
        
        config.setDebounceDelayMillis(5000L);
        assertEquals(5000L, config.getDebounceDelayMillis());
        
        config.setBatchSize(200);
        assertEquals(200, config.getBatchSize());
        
        config.setBatchIntervalMillis(10000L);
        assertEquals(10000L, config.getBatchIntervalMillis());
        
        config.setLocalCacheSize(5000);
        assertEquals(5000, config.getLocalCacheSize());
        
        config.setLocalCacheExpireMillis(1200000L);
        assertEquals(1200000L, config.getLocalCacheExpireMillis());
        
        config.setSmartRefreshThresholdSeconds(7200L);
        assertEquals(7200L, config.getSmartRefreshThresholdSeconds());
        
        config.setAsyncThreadPoolSize(8);
        assertEquals(8, config.getAsyncThreadPoolSize());
        
        config.setPerformanceMonitorEnabled(false);
        assertFalse(config.isPerformanceMonitorEnabled());
        
        config.setPerformanceReportIntervalMillis(1200000L);
        assertEquals(1200000L, config.getPerformanceReportIntervalMillis());
    }

    @Configuration
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
        public SessionHotRefreshUtil sessionHotRefreshUtil() {
            return Mockito.mock(SessionHotRefreshUtil.class);
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
                public void start() {
                    // Mock implementation - do nothing
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
}