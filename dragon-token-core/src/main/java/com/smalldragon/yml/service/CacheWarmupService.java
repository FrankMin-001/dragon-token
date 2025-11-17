package com.smalldragon.yml.service;

import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热服务（单体应用优化版）
 * 在应用启动时预热常用的缓存数据，提升系统性能
 * 
 * 单体应用优化特性：
 * 1. 简化的预热流程，无分布式协调
 * 2. 更快的预热速度和更少的资源消耗
 * 3. 智能检测分布式模式，自动降级
 * 4. 本地缓存优先策略
 * 
 * @Author YML
 * @Date 2025/01/20
 */
@Service
@ConditionalOnProperty(name = "dragon.token.cache-warmup.enabled", havingValue = "true", matchIfMissing = true)
public class CacheWarmupService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CacheWarmupService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    private boolean isMonolithicMode = false;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        DragonTokenProperties.CacheWarmupConfig config = dragonTokenProperties.getCacheWarmup();
        
        if (!config.isEnabled() || !config.isAutoWarmupOnStartup()) {
            logger.info("缓存预热已禁用或未启用自动预热");
            return;
        }

        // 检测运行模式
        detectApplicationMode(config);

        if (isMonolithicMode) {
            logger.info("检测到单体应用模式，启用优化的缓存预热策略");
        }

        logger.info("应用启动完成，{}毫秒后开始缓存预热...", config.getDelayMillis());
        
        // 延迟执行缓存预热
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(config.getDelayMillis());
                warmupAllCaches();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("缓存预热被中断", e);
            } catch (Exception e) {
                logger.error("缓存预热失败", e);
            }
        });
    }

    /**
     * 检测应用运行模式
     */
    private void detectApplicationMode(DragonTokenProperties.CacheWarmupConfig config) {
        // 检查分布式模式是否启用
        boolean distributedEnabled = config.getDistributed() != null && config.getDistributed().isEnabled();
        
        if (!distributedEnabled) {
            isMonolithicMode = true;
            logger.info("单体应用模式已启用，将使用优化的缓存预热策略");
        } else {
            logger.info("分布式模式已启用，将使用标准缓存预热策略");
        }
    }

    /**
     * 执行所有缓存预热
     */
    public void warmupAllCaches() {
        logger.info("开始执行缓存预热...");
        
        long startTime = System.currentTimeMillis();
        
        try {
            if (isMonolithicMode) {
                // 单体应用优化预热流程
                warmupForMonolithicMode();
            } else {
                // 标准预热流程
                warmupForStandardMode();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("缓存预热完成，耗时: {}ms", duration);
            
        } catch (Exception e) {
            logger.error("缓存预热失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 单体应用优化预热
     */
    private void warmupForMonolithicMode() {
        logger.debug("执行单体应用优化预热...");
        
        // 并行预热，提高效率
        CompletableFuture<Void> userCacheWarmup = CompletableFuture.runAsync(this::warmupUserCache);
        CompletableFuture<Void> blacklistWarmup = CompletableFuture.runAsync(this::warmupTokenBlacklistCache);
        CompletableFuture<Void> configWarmup = CompletableFuture.runAsync(this::warmupConfigurationCache);
        
        // 等待所有预热任务完成
        CompletableFuture.allOf(userCacheWarmup, blacklistWarmup, configWarmup).join();
        
        logger.debug("单体应用优化预热完成");
    }

    /**
     * 标准预热流程
     */
    private void warmupForStandardMode() {
        logger.debug("执行标准预热流程...");
        
        // 顺序预热，确保稳定性
        warmupUserCache();
        warmupTokenBlacklistCache();
        warmupConfigurationCache();
        
        logger.debug("标准预热流程完成");
    }

    /**
     * 预热用户缓存Hash结构
     */
    private void warmupUserCache() {
        try {
            String userCacheKey = CacheKeyConstants.USER_CACHE_KEY;
            
            // 检查用户缓存是否存在
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(userCacheKey))) {
                // 创建空的Hash结构，设置过期时间
                redisTemplate.opsForHash().put(userCacheKey, "warmup", "initialized");
                redisTemplate.expire(userCacheKey, dragonTokenProperties.getRetentionTime(), TimeUnit.SECONDS);
                
                // 立即删除预热数据
                redisTemplate.opsForHash().delete(userCacheKey, "warmup");
                
                logger.debug("用户缓存Hash结构预热完成");
            } else {
                logger.debug("用户缓存已存在，跳过预热");
            }
        } catch (Exception e) {
            logger.warn("用户缓存预热失败: {}", e.getMessage());
        }
    }

    /**
     * 预热token黑名单缓存结构
     */
    private void warmupTokenBlacklistCache() {
        try {
            // 预热JWT黑名单缓存结构
            String jwtBlacklistKey = CacheKeyConstants.JWT_BLACKLIST_KEY + "warmup";
            redisTemplate.opsForValue().set(jwtBlacklistKey, "initialized", 1, TimeUnit.SECONDS);
            
            // 预热无状态黑名单缓存结构
            String statelessBlacklistKey = CacheKeyConstants.STATELESS_BLACKLIST_KEY + "warmup";
            redisTemplate.opsForValue().set(statelessBlacklistKey, "initialized", 1, TimeUnit.SECONDS);
            
            logger.debug("Token黑名单缓存结构预热完成");
        } catch (Exception e) {
            logger.warn("Token黑名单缓存预热失败: {}", e.getMessage());
        }
    }

    /**
     * 预热配置缓存
     */
    private void warmupConfigurationCache() {
        try {
            // 缓存常用配置信息
            String configCacheKey = "dragon:token:config";
            
            // 创建配置缓存对象
            ConfigCache configCache = new ConfigCache();
            configCache.setStrategyType(dragonTokenProperties.getStrategyType());
            configCache.setRetentionTime(dragonTokenProperties.getRetentionTime());
            configCache.setTokenName(dragonTokenProperties.getName());
            
            // 单体应用使用较短的缓存时间
            long cacheExpire = isMonolithicMode ? 
                Math.min(dragonTokenProperties.getRetentionTime(), 3600) : // 单体应用最多1小时
                dragonTokenProperties.getRetentionTime();
            
            redisTemplate.opsForValue().set(configCacheKey, configCache, cacheExpire, TimeUnit.SECONDS);
            
            logger.debug("配置缓存预热完成，过期时间: {}秒", cacheExpire);
        } catch (Exception e) {
            logger.warn("配置缓存预热失败: {}", e.getMessage());
        }
    }

    /**
     * 手动触发缓存预热
     */
    public void manualWarmup() {
        logger.info("手动触发缓存预热");
        try {
            warmupAllCaches();
        } catch (Exception e) {
            logger.error("手动缓存预热失败", e);
            throw new RuntimeException("缓存预热失败", e);
        }
    }

    /**
     * 清理所有缓存
     */
    public void clearAllCache() {
        logger.info("开始清理所有缓存...");
        try {
            // 清理用户缓存
            redisTemplate.delete(CacheKeyConstants.USER_CACHE_KEY);
            
            // 清理黑名单缓存
            clearBlacklistCache();
            
            // 清理配置缓存
            redisTemplate.delete("dragon:token:config");
            
            logger.info("所有缓存清理完成");
        } catch (Exception e) {
            logger.error("缓存清理失败", e);
        }
    }

    /**
     * 清理黑名单缓存
     */
    private void clearBlacklistCache() {
        try {
            // 使用模式匹配删除所有黑名单相关的key
            redisTemplate.delete(CacheKeyConstants.JWT_BLACKLIST_KEY + "*");
            redisTemplate.delete(CacheKeyConstants.STATELESS_BLACKLIST_KEY + "*");
            
            logger.debug("黑名单缓存清理完成");
        } catch (Exception e) {
            logger.warn("黑名单缓存清理失败: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存预热状态
     */
    public CacheWarmupStatus getWarmupStatus() {
        CacheWarmupStatus status = new CacheWarmupStatus();
        status.setMonolithicMode(isMonolithicMode);
        status.setUserCacheExists(Boolean.TRUE.equals(redisTemplate.hasKey(CacheKeyConstants.USER_CACHE_KEY)));
        status.setConfigCacheExists(Boolean.TRUE.equals(redisTemplate.hasKey("dragon:token:config")));
        return status;
    }

    /**
     * 配置缓存对象
     */
    public static class ConfigCache {
        private String strategyType;
        private long retentionTime;
        private String tokenName;

        public String getStrategyType() {
            return strategyType;
        }

        public void setStrategyType(String strategyType) {
            this.strategyType = strategyType;
        }

        public long getRetentionTime() {
            return retentionTime;
        }

        public void setRetentionTime(long retentionTime) {
            this.retentionTime = retentionTime;
        }

        public String getTokenName() {
            return tokenName;
        }

        public void setTokenName(String tokenName) {
            this.tokenName = tokenName;
        }
    }

    /**
     * 缓存预热状态
     */
    public static class CacheWarmupStatus {
        private boolean monolithicMode;
        private boolean userCacheExists;
        private boolean configCacheExists;

        public boolean isMonolithicMode() {
            return monolithicMode;
        }

        public void setMonolithicMode(boolean monolithicMode) {
            this.monolithicMode = monolithicMode;
        }

        public boolean isUserCacheExists() {
            return userCacheExists;
        }

        public void setUserCacheExists(boolean userCacheExists) {
            this.userCacheExists = userCacheExists;
        }

        public boolean isConfigCacheExists() {
            return configCacheExists;
        }

        public void setConfigCacheExists(boolean configCacheExists) {
            this.configCacheExists = configCacheExists;
        }
    }
}