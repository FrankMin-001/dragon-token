package com.smalldragon.yml.propertity;

import com.alibaba.fastjson.JSON;
import com.smalldragon.yml.interceptors.AuthInterceptor;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.print.DocFlavor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @Author YML
 * @Date 2025/9/23 17:30
 **/
@Component
public class DragonTokenSetting implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger logger = LoggerFactory.getLogger(DragonTokenSetting.class);

    public static String name;
    public static String strategyType;
    public static Long retentionTime;
    public static String publicKey;
    public static String excludePaths;
    public static String[] whitePaths;

    // Redis 配置属性
    public static String redisHost;
    public static Integer redisPort;
    public static Integer redisDatabase;
    public static String redisPassword;

    private static final String[] FALLBACK_PATHS = {
            "/login", "/public/**", "/swagger-ui/**", "/v3/api-docs"
    };
    public final static List<String> strategyTypeConstants = Arrays.asList("SESSION","JWT","STATELESS");
    public final static String JWT = "JWT";
    public final static String SESSION = "SESSION";
    public final static String STATELESS = "STATELESS";
    public final static String DEFAULT_NAME = "DRAGON-TOKEN";
    public final static String USER_INFO = "userInfo";
    public final static String USER_CACHE_KEY = "USER::CACHE::COLLECT";

    @Value("${dragon.token.name:DRAGON-TOKEN}")
    public void setName(String name) {
        DragonTokenSetting.name = (name == null || name.isEmpty()) ? DEFAULT_NAME : name;
    }


    @Value("${dragon.token.strategy-type}")
    public void setStrategyType(String strategyType) {
        DragonTokenSetting.strategyType = (strategyType == null || strategyType.isEmpty()) ?
            SESSION : strategyType;
        if (!strategyTypeConstants.contains(strategyType)){
            DragonTokenSetting.strategyType = SESSION;
            //throw new RuntimeException("strategyType is not matched such as "+ JSON.toJSONString(strategyTypeConstants));
        }
    }

    @Value("${dragon.token.retention-time}")
    public void setRetentionTime(Long retentionTime) {
        DragonTokenSetting.retentionTime = (retentionTime == null || retentionTime <= 300) ?
            7200L : retentionTime;
    }

    @Value("${dragon.token.public-key}")
    public void setPublicKey(String publicKey) {
        DragonTokenSetting.publicKey = publicKey;
    }

    @Value("${dragon.token.exclude-paths:}")
    public static void setExcludePaths(String excludePaths) {
        if (excludePaths == null) {
            excludePaths = "";
        }
        DragonTokenSetting.excludePaths = excludePaths;
        DragonTokenSetting.whitePaths = getSafeExcludePaths(excludePaths);
    }

    private static String[] getSafeExcludePaths(String excludePaths) {
        try {
            if (excludePaths == null) {
                return new String[0];
            }

            return Arrays.stream(excludePaths.split(","))
                    .map(String::trim)
                    .filter(path -> !path.isEmpty())
                    .filter(path -> path.startsWith("/") || path.startsWith("**"))
                    .toArray(String[]::new);

        } catch (Exception e) {
            System.err.println("解析排除路径配置失败: " + e.getMessage());
            return FALLBACK_PATHS;
        }
    }

    // Redis 配置 setter 方法
    @Value("${spring.redis.host:}")
    public void setRedisHost(String redisHost) {
        DragonTokenSetting.redisHost = redisHost;
    }

    @Value("${spring.redis.port:}")
    public void setRedisPort(Integer redisPort) {
        DragonTokenSetting.redisPort = redisPort;
    }

    @Value("${spring.redis.database:}")
    public void setRedisDatabase(Integer redisDatabase) {
        DragonTokenSetting.redisDatabase = redisDatabase;
    }

    @Value("${spring.redis.password:}")
    public void setRedisPassword(String redisPassword) {
        DragonTokenSetting.redisPassword = redisPassword;
    }

    /**
     * 应用启动完成后验证配置
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateConfiguration();
    }

    /**
     * 验证配置是否完整
     */
    private void validateConfiguration() {
        if (SESSION.equals(strategyType)) {
            validateRedisConfiguration();
            logger.info("✅ SESSION 模式配置验证通过，Redis 连接信息完整");
        } else {
            logger.info("✅ {} 模式不需要 Redis 配置", strategyType);
        }
    }

    /**
     * 验证 Redis 配置
     */
    private void validateRedisConfiguration() {
        if (isBlank(redisHost)) {
            throw new IllegalStateException("SESSION 模式必须配置 Redis 连接信息。请检查 spring.redis.host 配置");
        }
        if (redisPort == null) {
            throw new IllegalStateException("SESSION 模式必须配置 Redis 端口。请检查 spring.redis.port 配置");
        }
        if (redisDatabase == null) {
            throw new IllegalStateException("SESSION 模式必须配置 Redis 数据库。请检查 spring.redis.database 配置");
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

}
