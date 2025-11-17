package com.smalldragon.yml.context;

import com.smalldragon.yml.config.RedisSessionConfig;
import com.smalldragon.yml.config.RedisSessionProperties;
import com.smalldragon.yml.config.WebConfig;
import com.smalldragon.yml.core.StpInterface;
import com.smalldragon.yml.core.StpInterfaceImpl;
import com.smalldragon.yml.interceptors.AuthInterceptor;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.manager.impl.TokenManagerImpl;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.utils.SessionUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Dragon Token 自动配置类
 * 当项目中存在 dragon.token 相关配置时自动启用
 */
@Configuration
@EnableConfigurationProperties({RedisSessionProperties.class, DragonTokenProperties.class})
@Import({RedisSessionConfig.class, WebConfig.class})
@ConditionalOnProperty(prefix = "dragon.token", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DragonTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DragonTokenProperties dragonTokenProperties() {
        return new DragonTokenProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenManager tokenManager() {
        return new TokenManagerImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public StpInterface stpInterface() {
        return new StpInterfaceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthInterceptor authInterceptor() {
        return new AuthInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionUtil sessionUtil() {
        return new SessionUtil();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil() {
        return new JwtUtil();
    }

    @Bean
    @ConditionalOnMissingBean
    public DragonContextHolder dragonContextHolder() {
        return new DragonContextHolder();
    }
}