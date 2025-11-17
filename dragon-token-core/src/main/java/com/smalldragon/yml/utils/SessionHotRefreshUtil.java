package com.smalldragon.yml.utils;

import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Session热刷新工具类
 * 实现用户操作触发的session自动延期功能，提升用户体验
 * 
 * 功能特性：
 * 1. 用户操作自动触发session延期
 * 2. 防抖机制避免频繁刷新
 * 3. 性能优化：批量处理、本地缓存
 * 4. 可配置的刷新策略
 * 
 * @Author YML
 * @Date 2025/1/21
 */
@Component
public class SessionHotRefreshUtil {

    private static final Logger logger = LoggerFactory.getLogger(SessionHotRefreshUtil.class);

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    // 本地缓存：记录最近刷新时间，避免频繁刷新
    private final ConcurrentHashMap<String, Long> lastRefreshTimeCache = new ConcurrentHashMap<>();

    // 默认防抖时间：5分钟内不重复刷新同一个session
    private static final long DEFAULT_DEBOUNCE_TIME = 5 * 60 * 1000L;

    // 默认刷新阈值：session剩余时间少于总时间的1/3时才刷新
    private static final double DEFAULT_REFRESH_THRESHOLD = 0.33;

    /**
     * 用户操作触发的热刷新
     * 在用户进行操作时自动调用，延长session有效期
     * 
     * @param sessionId session ID
     * @return 是否成功刷新
     */
    public boolean hotRefreshOnUserAction(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }

        try {
            // 检查是否需要刷新
            if (!shouldRefresh(sessionId)) {
                return false;
            }

            // 执行刷新
            boolean refreshed = performRefresh(sessionId);
            
            if (refreshed) {
                // 更新本地缓存
                lastRefreshTimeCache.put(sessionId, System.currentTimeMillis());
                logger.debug("Session热刷新成功: {}", sessionId);
            }

            return refreshed;

        } catch (Exception e) {
            logger.error("Session热刷新失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 批量热刷新多个session
     * 用于处理多用户同时操作的场景
     * 
     * @param sessionIds session ID列表
     * @return 成功刷新的数量
     */
    public int batchHotRefresh(String... sessionIds) {
        if (sessionIds == null || sessionIds.length == 0) {
            return 0;
        }

        int successCount = 0;
        for (String sessionId : sessionIds) {
            if (hotRefreshOnUserAction(sessionId)) {
                successCount++;
            }
        }

        logger.debug("批量热刷新完成: 总数={}, 成功={}", sessionIds.length, successCount);
        return successCount;
    }

    /**
     * 智能刷新：根据当前用户上下文自动刷新
     * 在拦截器中调用，用户每次请求时自动判断是否需要刷新
     * 
     * @return 是否成功刷新
     */
    public boolean smartRefresh() {
        UserContext context = DragonContextHolder.getContext();
        if (context == null || !StringUtils.hasText(context.getSessionId())) {
            return false;
        }

        return hotRefreshOnUserAction(context.getSessionId());
    }

    /**
     * 检查session是否需要刷新
     * 
     * @param sessionId session ID
     * @return 是否需要刷新
     */
    private boolean shouldRefresh(String sessionId) {
        // 1. 检查防抖：避免频繁刷新
        Long lastRefreshTime = lastRefreshTimeCache.get(sessionId);
        if (lastRefreshTime != null) {
            long timeSinceLastRefresh = System.currentTimeMillis() - lastRefreshTime;
            if (timeSinceLastRefresh < getDebounceTime()) {
                logger.debug("Session刷新防抖中: sessionId={}, 距离上次刷新={}ms", 
                    sessionId, timeSinceLastRefresh);
                return false;
            }
        }

        // 2. 检查session是否存在且有效
        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            logger.debug("Session不存在或已过期: {}", sessionId);
            return false;
        }

        // 3. 检查是否达到刷新阈值
        long remainingTime = getRemainingTime(session);
        long totalTime = session.getMaxInactiveInterval().getSeconds() * 1000;
        double remainingRatio = (double) remainingTime / totalTime;

        if (remainingRatio > getRefreshThreshold()) {
            logger.debug("Session剩余时间充足，无需刷新: sessionId={}, 剩余比例={}", 
                sessionId, remainingRatio);
            return false;
        }

        return true;
    }

    /**
     * 执行session刷新
     * 
     * @param sessionId session ID
     * @return 是否成功
     */
    private boolean performRefresh(String sessionId) {
        try {
            Session session = sessionRepository.findById(sessionId);
            if (session == null) {
                return false;
            }

            // 更新最后访问时间
            session.setLastAccessedTime(Instant.now());
            
            // 重新设置过期时间
            long retentionTime = dragonTokenProperties.getRetentionTime();
            session.setMaxInactiveInterval(Duration.ofMillis(retentionTime));

            // 保存到Redis
            sessionRepository.save(session);

            // 可选：更新Redis中的用户活跃时间统计
            updateUserActivityStats(sessionId);

            return true;

        } catch (Exception e) {
            logger.error("执行session刷新失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 获取session剩余时间
     * 
     * @param session session对象
     * @return 剩余时间（毫秒）
     */
    private long getRemainingTime(Session session) {
        long lastAccessedTime = session.getLastAccessedTime().toEpochMilli();
        long maxInactiveInterval = session.getMaxInactiveInterval().getSeconds() * 1000;
        return (lastAccessedTime + maxInactiveInterval) - System.currentTimeMillis();
    }

    /**
     * 更新用户活跃统计
     * 
     * @param sessionId session ID
     */
    private void updateUserActivityStats(String sessionId) {
        try {
            String statsKey = "session:activity:" + sessionId;
            redisTemplate.opsForValue().set(statsKey, System.currentTimeMillis(), 
                dragonTokenProperties.getRetentionTime(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 统计更新失败不影响主流程
            logger.debug("更新用户活跃统计失败: {}", e.getMessage());
        }
    }

    /**
     * 清理过期的本地缓存
     * 定期调用以释放内存
     */
    public void cleanupExpiredCache() {
        long currentTime = System.currentTimeMillis();
        long expireTime = getDebounceTime() * 2; // 缓存保留时间为防抖时间的2倍

        lastRefreshTimeCache.entrySet().removeIf(entry -> {
            long age = currentTime - entry.getValue();
            return age > expireTime;
        });

        logger.debug("清理过期缓存完成，当前缓存大小: {}", lastRefreshTimeCache.size());
    }

    /**
     * 获取防抖时间配置
     * 
     * @return 防抖时间（毫秒）
     */
    private long getDebounceTime() {
        return dragonTokenProperties.getSessionHotRefresh().getDebounceDelayMillis();
    }

    /**
     * 获取刷新阈值配置
     * 
     * @return 刷新阈值（0-1之间的比例）
     */
    private double getRefreshThreshold() {
        long thresholdSeconds = dragonTokenProperties.getSessionHotRefresh().getSmartRefreshThresholdSeconds();
        long retentionTimeSeconds = dragonTokenProperties.getRetentionTime();
        return (double) thresholdSeconds / retentionTimeSeconds;
    }

    /**
     * 获取缓存统计信息
     * 
     * @return 缓存统计
     */
    public String getCacheStats() {
        return String.format("本地缓存大小: %d, 防抖时间: %dms, 刷新阈值: %.2f", 
            lastRefreshTimeCache.size(), getDebounceTime(), getRefreshThreshold());
    }
}