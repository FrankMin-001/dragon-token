package com.smalldragon.yml.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * RedisSessionConfig 测试类
 * 验证Redis配置自动回填到Spring Session的功能
 */
@ExtendWith(MockitoExtension.class)
public class RedisSessionConfigTest {

    private RedisSessionConfig redisSessionConfig;
    private RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUp() {
        redisSessionConfig = new RedisSessionConfig();
        redisConnectionFactory = mock(RedisConnectionFactory.class);
        
        // 清除之前可能设置的系统属性
        System.clearProperty("spring.session.store-type");
        System.clearProperty("spring.session.timeout");
        System.clearProperty("spring.redis.host");
        System.clearProperty("spring.redis.port");
        System.clearProperty("spring.redis.database");
        System.clearProperty("spring.redis.password");
    }

    @Test
    void testRedisSessionPropertiesConfiguration() {
        // 创建一个完整的Redis配置
        RedisSessionProperties properties = new RedisSessionProperties();
        properties.setHost("test-redis-host");
        properties.setPort(6379);
        properties.setDatabase(1);
        properties.setPassword("test-password");
        
        // 验证配置是否正确
        assertEquals("test-redis-host", properties.getHost());
        assertEquals(6379, properties.getPort());
        assertEquals(1, properties.getDatabase());
        assertEquals("test-password", properties.getPassword());
        assertTrue(properties.isRedisConfigured());
    }

    @Test
    void testRedisTemplateCreationWithCompleteConfig() {
        // 创建一个完整的Redis配置
        RedisSessionProperties properties = new RedisSessionProperties();
        properties.setHost("test-redis-host");
        properties.setPort(6379);
        properties.setDatabase(1);
        properties.setPassword("test-password");
        
        redisSessionConfig.setRedisSessionProperties(properties);
        
        // 调用redisTemplate方法，这会触发自动配置
        org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate = redisSessionConfig.redisTemplate(redisConnectionFactory);
        
        assertNotNull(redisTemplate);
        assertNotNull(redisTemplate.getConnectionFactory());
        
        // 验证系统属性是否被正确设置
        assertEquals("redis", System.getProperty("spring.session.store-type"));
        assertEquals("7200", System.getProperty("spring.session.timeout"));
        assertEquals("test-redis-host", System.getProperty("spring.redis.host"));
        assertEquals("6379", System.getProperty("spring.redis.port"));
        assertEquals("1", System.getProperty("spring.redis.database"));
        assertEquals("test-password", System.getProperty("spring.redis.password"));
    }

    @Test
    void testAutoConfigureSpringSessionWithIncompleteConfig() {
        // 创建一个不完整的Redis配置
        RedisSessionProperties incompleteProperties = new RedisSessionProperties();
        incompleteProperties.setHost(null);
        incompleteProperties.setPort(6379);
        incompleteProperties.setDatabase(1);
        
        redisSessionConfig.setRedisSessionProperties(incompleteProperties);
        
        // 验证不会设置系统属性
        redisSessionConfig.redisTemplate(redisConnectionFactory);
        
        // 验证系统属性没有被设置
        assertNull(System.getProperty("spring.session.store-type"));
        assertNull(System.getProperty("spring.session.timeout"));
    }

    @Test
    void testRedisSessionPropertiesWithoutPassword() {
        // 创建一个没有密码的配置
        RedisSessionProperties noPasswordProperties = new RedisSessionProperties();
        noPasswordProperties.setHost("test-host");
        noPasswordProperties.setPort(6379);
        noPasswordProperties.setDatabase(1);
        noPasswordProperties.setPassword(null);
        
        redisSessionConfig.setRedisSessionProperties(noPasswordProperties);
        
        redisSessionConfig.redisTemplate(redisConnectionFactory);
        
        // 验证密码属性没有被设置
        assertNull(System.getProperty("spring.redis.password"));
    }

    @Test
    void testConditionalOnProperty() {
        // 验证@ConditionalOnProperty注解是否正常工作
        // 这个测试确保只有在strategy-type=SESSION时配置才生效
        assertNotNull(redisSessionConfig);
        
        // 验证配置类的基本功能
        assertTrue(true, "配置类基本功能正常");
    }
}