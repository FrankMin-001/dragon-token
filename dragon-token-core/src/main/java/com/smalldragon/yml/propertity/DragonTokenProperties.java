package com.smalldragon.yml.propertity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.util.Arrays;
import java.util.List;

/**
 * DragonToken配置属性类
 * 使用@ConfigurationProperties替代静态变量，提供更好的配置管理
 * 
 * @author YML
 * @date 2025/9/23 17:30
 */
@Component
@ConfigurationProperties(prefix = "dragon.token")
public class DragonTokenProperties {

    private static final Logger logger = LoggerFactory.getLogger(DragonTokenProperties.class);

    // 策略类型常量
    public static final List<String> STRATEGY_TYPE_CONSTANTS = Arrays.asList("SESSION", "JWT", "STATELESS");
    public static final String JWT = "JWT";
    public static final String SESSION = "SESSION";
    public static final String STATELESS = "STATELESS";
    public static final String DEFAULT_NAME = "DRAGON-TOKEN";
    public static final String USER_INFO = "userInfo";

    // 默认白名单路径
    private static final String[] FALLBACK_PATHS = {
            "/login", "/public/**", "/swagger-ui/**", "/v3/api-docs"
    };

    /**
     * Token名称
     */
    @NotBlank(message = "Token名称不能为空")
    private String name = DEFAULT_NAME;

    /**
     * 策略类型：SESSION、JWT、STATELESS
     */
    @NotBlank(message = "策略类型不能为空")
    private String strategyType = JWT;

    /**
     * Token保留时间（秒）
     */
    @NotNull(message = "保留时间不能为空")
    @Positive(message = "保留时间必须为正数")
    private Long retentionTime = 7200L;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 排除路径（逗号分隔）
     */
    private String excludePaths;

    /**
     * 白名单路径数组（由excludePaths解析得到）
     */
    private String[] whitePaths;

    /**
     * Redis配置
     */
    private RedisConfig redis = new RedisConfig();

    /**
     * 缓存预热配置
     */
    private CacheWarmupConfig cacheWarmup = new CacheWarmupConfig();

    /**
     * Session热刷新配置
     */
    private SessionHotRefreshConfig sessionHotRefresh = new SessionHotRefreshConfig();

    /**
     * 数据一致性配置
     */
    private ConsistencyConfig consistency = new ConsistencyConfig();

    /**
     * 缓存预热配置内部类
     */
    public static class CacheWarmupConfig {
        /**
         * 是否启用缓存预热
         */
        private boolean enabled = true;

        /**
         * 预热延迟时间（毫秒），应用启动后延迟多久开始预热
         */
        private long delayMillis = 5000L;

        /**
         * 是否在应用启动时自动预热
         */
        private boolean autoWarmupOnStartup = true;

        /**
         * 预热超时时间（毫秒）
         */
        private long timeoutMillis = 30000L;

        /**
         * 分布式配置
         */
        private DistributedConfig distributed = new DistributedConfig();

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDelayMillis() {
            return delayMillis;
        }

        public void setDelayMillis(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        public boolean isAutoWarmupOnStartup() {
            return autoWarmupOnStartup;
        }

        public void setAutoWarmupOnStartup(boolean autoWarmupOnStartup) {
            this.autoWarmupOnStartup = autoWarmupOnStartup;
        }

        public long getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public DistributedConfig getDistributed() {
            return distributed;
        }

        public void setDistributed(DistributedConfig distributed) {
            this.distributed = distributed;
        }

        /**
         * 分布式配置内部类
         */
        public static class DistributedConfig {
            /**
             * 是否启用分布式模式
             */
            private boolean enabled = false;

            /**
             * 分布式锁超时时间（秒）
             */
            private int lockTimeout = 300;

            /**
             * 心跳间隔（秒）
             */
            private int heartbeatInterval = 30;

            /**
             * 服务超时时间（秒）
             */
            private int serviceTimeout = 90;

            /**
             * 版本检查间隔（秒）
             */
            private int versionCheckInterval = 60;

            /**
             * 事件通知频道
             */
            private String eventChannel = "dragon:token:cluster:events";

            // Getters and Setters
            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getLockTimeout() {
                return lockTimeout;
            }

            public void setLockTimeout(int lockTimeout) {
                this.lockTimeout = lockTimeout;
            }

            public int getHeartbeatInterval() {
                return heartbeatInterval;
            }

            public void setHeartbeatInterval(int heartbeatInterval) {
                this.heartbeatInterval = heartbeatInterval;
            }

            public int getServiceTimeout() {
                return serviceTimeout;
            }

            public void setServiceTimeout(int serviceTimeout) {
                this.serviceTimeout = serviceTimeout;
            }

            public int getVersionCheckInterval() {
                return versionCheckInterval;
            }

            public void setVersionCheckInterval(int versionCheckInterval) {
                this.versionCheckInterval = versionCheckInterval;
            }

            public String getEventChannel() {
                return eventChannel;
            }

            public void setEventChannel(String eventChannel) {
                this.eventChannel = eventChannel;
            }
        }
    }

    /**
     * Session热刷新配置内部类
     */
    public static class SessionHotRefreshConfig {
        /**
         * 是否启用Session热刷新
         */
        private boolean enabled = true;

        /**
         * 防抖延迟时间（毫秒），防止频繁刷新
         */
        private long debounceDelayMillis = 1000L;

        /**
         * 批处理大小，批量处理刷新请求的数量
         */
        private int batchSize = 50;

        /**
         * 批处理间隔时间（毫秒）
         */
        private long batchIntervalMillis = 2000L;

        /**
         * 本地缓存大小，用于缓存最近刷新的Session
         */
        private int localCacheSize = 1000;

        /**
         * 本地缓存过期时间（毫秒）
         */
        private long localCacheExpireMillis = 300000L; // 5分钟

        /**
         * 智能刷新阈值，Session剩余时间低于此值时才刷新（秒）
         */
        private long smartRefreshThresholdSeconds = 1800L; // 30分钟

        /**
         * 异步处理线程池大小
         */
        private int asyncThreadPoolSize = 2;

        /**
         * 性能监控是否启用
         */
        private boolean performanceMonitorEnabled = true;

        /**
         * 性能统计报告间隔（毫秒）
         */
        private long performanceReportIntervalMillis = 300000L; // 5分钟

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getDebounceDelayMillis() {
            return debounceDelayMillis;
        }

        public void setDebounceDelayMillis(long debounceDelayMillis) {
            this.debounceDelayMillis = debounceDelayMillis;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getBatchIntervalMillis() {
            return batchIntervalMillis;
        }

        public void setBatchIntervalMillis(long batchIntervalMillis) {
            this.batchIntervalMillis = batchIntervalMillis;
        }

        public int getLocalCacheSize() {
            return localCacheSize;
        }

        public void setLocalCacheSize(int localCacheSize) {
            this.localCacheSize = localCacheSize;
        }

        public long getLocalCacheExpireMillis() {
            return localCacheExpireMillis;
        }

        public void setLocalCacheExpireMillis(long localCacheExpireMillis) {
            this.localCacheExpireMillis = localCacheExpireMillis;
        }

        public long getSmartRefreshThresholdSeconds() {
            return smartRefreshThresholdSeconds;
        }

        public void setSmartRefreshThresholdSeconds(long smartRefreshThresholdSeconds) {
            this.smartRefreshThresholdSeconds = smartRefreshThresholdSeconds;
        }

        public int getAsyncThreadPoolSize() {
            return asyncThreadPoolSize;
        }

        public void setAsyncThreadPoolSize(int asyncThreadPoolSize) {
            this.asyncThreadPoolSize = asyncThreadPoolSize;
        }

        public boolean isPerformanceMonitorEnabled() {
            return performanceMonitorEnabled;
        }

        public void setPerformanceMonitorEnabled(boolean performanceMonitorEnabled) {
            this.performanceMonitorEnabled = performanceMonitorEnabled;
        }

        public long getPerformanceReportIntervalMillis() {
            return performanceReportIntervalMillis;
        }

        public void setPerformanceReportIntervalMillis(long performanceReportIntervalMillis) {
            this.performanceReportIntervalMillis = performanceReportIntervalMillis;
        }
    }

    /**
     * 数据一致性配置内部类
     */
    public static class ConsistencyConfig {
        /**
         * 是否启用数据一致性保障
         */
        private boolean enabled = false;

        /**
         * 版本冲突检测
         */
        private boolean versionConflictDetection = true;

        /**
         * 自动冲突解决
         */
        private boolean autoConflictResolution = true;

        /**
         * 事件通知超时时间（毫秒）
         */
        private long eventNotificationTimeout = 5000L;

        /**
         * 本地版本缓存大小
         */
        private int localVersionCacheSize = 1000;

        /**
         * 版本缓存过期时间（秒）
         */
        private long versionCacheExpire = 3600L;

        // Getters and Setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isVersionConflictDetection() {
            return versionConflictDetection;
        }

        public void setVersionConflictDetection(boolean versionConflictDetection) {
            this.versionConflictDetection = versionConflictDetection;
        }

        public boolean isAutoConflictResolution() {
            return autoConflictResolution;
        }

        public void setAutoConflictResolution(boolean autoConflictResolution) {
            this.autoConflictResolution = autoConflictResolution;
        }

        public long getEventNotificationTimeout() {
            return eventNotificationTimeout;
        }

        public void setEventNotificationTimeout(long eventNotificationTimeout) {
            this.eventNotificationTimeout = eventNotificationTimeout;
        }

        public int getLocalVersionCacheSize() {
            return localVersionCacheSize;
        }

        public void setLocalVersionCacheSize(int localVersionCacheSize) {
            this.localVersionCacheSize = localVersionCacheSize;
        }

        public long getVersionCacheExpire() {
            return versionCacheExpire;
        }

        public void setVersionCacheExpire(long versionCacheExpire) {
            this.versionCacheExpire = versionCacheExpire;
        }
    }

    /**
     * Redis配置内部类
     */
    public static class RedisConfig {
        private String host;
        private Integer port;
        private Integer database;
        private String password;

        // Getters and Setters
        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public Integer getDatabase() {
            return database;
        }

        public void setDatabase(Integer database) {
            this.database = database;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 检查Redis配置是否完整
         */
        public boolean isComplete() {
            return StringUtils.hasText(host) && port != null;
        }
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public Long getRetentionTime() {
        return retentionTime;
    }

    public void setRetentionTime(Long retentionTime) {
        this.retentionTime = retentionTime;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(String excludePaths) {
        this.excludePaths = excludePaths;
        // 解析白名单路径
        if (StringUtils.hasText(excludePaths)) {
            this.whitePaths = excludePaths.split(",");
            // 去除空格
            for (int i = 0; i < this.whitePaths.length; i++) {
                this.whitePaths[i] = this.whitePaths[i].trim();
            }
        } else {
            this.whitePaths = FALLBACK_PATHS;
        }
    }

    public String[] getWhitePaths() {
        return whitePaths != null ? whitePaths : FALLBACK_PATHS;
    }

    public RedisConfig getRedis() {
        return redis;
    }

    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }

    public CacheWarmupConfig getCacheWarmup() {
        return cacheWarmup;
    }

    public void setCacheWarmup(CacheWarmupConfig cacheWarmup) {
        this.cacheWarmup = cacheWarmup;
    }

    public SessionHotRefreshConfig getSessionHotRefresh() {
        return sessionHotRefresh;
    }

    public void setSessionHotRefresh(SessionHotRefreshConfig sessionHotRefresh) {
        this.sessionHotRefresh = sessionHotRefresh;
    }

    public ConsistencyConfig getConsistency() {
        return consistency;
    }

    public void setConsistency(ConsistencyConfig consistency) {
        this.consistency = consistency;
    }

    /**
     * 应用启动完成后的验证
     */
    @PostConstruct
    public void init() {
        validateConfiguration();
    }

    /**
     * 验证配置
     */
    private void validateConfiguration() {
        // 验证策略类型
        if (!STRATEGY_TYPE_CONSTANTS.contains(strategyType)) {
            throw new IllegalArgumentException(
                String.format("不支持的策略类型: %s，支持的类型: %s", 
                    strategyType, STRATEGY_TYPE_CONSTANTS)
            );
        }

        // 验证保留时间
        if (retentionTime <= 0) {
            throw new IllegalArgumentException("保留时间必须大于0");
        }

        // 验证Redis配置（SESSION模式必须配置Redis）
        if (SESSION.equals(strategyType) && !redis.isComplete()) {
            throw new IllegalArgumentException(
                "SESSION模式需要完整的Redis配置（host和port）"
            );
        }

        // 设置默认白名单路径
        if (whitePaths == null || whitePaths.length == 0) {
            this.whitePaths = FALLBACK_PATHS;
        }

        logger.info("DragonToken配置验证完成 - 策略类型: {}, 保留时间: {}秒, 白名单路径: {}", 
            strategyType, retentionTime, Arrays.toString(whitePaths));
    }

    /**
     * 检查是否需要Redis
     */
    public boolean requiresRedis() {
        return SESSION.equals(strategyType) || JWT.equals(strategyType);
    }

    /**
     * 获取Redis连接信息摘要（用于日志）
     */
    public String getRedisConnectionSummary() {
        if (redis.isComplete()) {
            return String.format("%s:%d/%d", redis.getHost(), redis.getPort(), 
                redis.getDatabase() != null ? redis.getDatabase() : 0);
        }
        return "未配置";
    }
}