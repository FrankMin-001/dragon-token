package com.smalldragon.yml.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableRedisHttpSession
@EnableConfigurationProperties(RedisSessionProperties.class)
@ConditionalOnProperty(name = "dragon.token.strategy-type", havingValue = "SESSION")
public class RedisSessionConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisSessionConfig.class);
    
    @Autowired
    private RedisSessionProperties redisSessionProperties;
    
    public void setRedisSessionProperties(RedisSessionProperties redisSessionProperties) {
        this.redisSessionProperties = redisSessionProperties;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        // 自动配置 Spring Session
        autoConfigureSpringSession();
        
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
    
    /**
     * 自动配置 Spring Session 相关属性
     */
    private void autoConfigureSpringSession() {
        if (redisSessionProperties.isRedisConfigured()) {
            // 设置系统属性，这些属性会被 Spring Session 自动读取
            System.setProperty("spring.session.store-type", "redis");
            System.setProperty("spring.session.timeout", "7200");
            
            // 设置 Redis 连接属性
            System.setProperty("spring.redis.host", redisSessionProperties.getHost());
            System.setProperty("spring.redis.port", String.valueOf(redisSessionProperties.getPort()));
            System.setProperty("spring.redis.database", String.valueOf(redisSessionProperties.getDatabase()));
            
            if (redisSessionProperties.getPassword() != null && !redisSessionProperties.getPassword().isEmpty()) {
                System.setProperty("spring.redis.password", redisSessionProperties.getPassword());
            }
            
            logger.info("✅ 自动配置 Spring Session Redis: {}:{}/{}", 
                redisSessionProperties.getHost(), 
                redisSessionProperties.getPort(), 
                redisSessionProperties.getDatabase());
            
            logger.info("✅ Spring Session 配置: store-type=redis, timeout=7200");
        } else {
            logger.warn("⚠️ 未检测到完整的 Redis 配置，Spring Session 可能无法正常工作");
        }
    }
}
