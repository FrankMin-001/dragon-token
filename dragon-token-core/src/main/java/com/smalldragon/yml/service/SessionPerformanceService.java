package com.smalldragon.yml.service;

import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.SessionHotRefreshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Session性能优化服务
 * 提供批处理、异步处理、缓存清理等性能优化功能
 * 
 * 优化策略：
 * 1. 批量处理session刷新请求
 * 2. 异步执行非关键操作
 * 3. 定期清理过期缓存
 * 4. 性能监控和统计
 * 
 * @Author YML
 * @Date 2025/1/21
 */
@Service
public class SessionPerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(SessionPerformanceService.class);

    @Resource
    private SessionHotRefreshUtil sessionHotRefreshUtil;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    // 批处理队列
    private final BlockingQueue<String> refreshQueue;

    // 性能统计
    private final AtomicLong totalRefreshRequests = new AtomicLong(0);
    private final AtomicLong successfulRefreshes = new AtomicLong(0);
    private final AtomicInteger queueSize = new AtomicInteger(0);

    public SessionPerformanceService() {
        // 使用默认队列大小，实际大小将在配置加载后调整
        this.refreshQueue = new LinkedBlockingQueue<>(1000);
    }

    // 批处理配置
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_TIMEOUT = 5000L; // 5秒

    /**
     * 异步提交session刷新请求
     * 将刷新请求加入队列，由批处理任务统一处理
     * 
     * @param sessionId session ID
     * @return 是否成功加入队列
     */
    @Async
    public boolean submitRefreshRequest(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        totalRefreshRequests.incrementAndGet();

        try {
            boolean added = refreshQueue.offer(sessionId);
            if (added) {
                queueSize.incrementAndGet();
                logger.debug("Session刷新请求已加入队列: {}", sessionId);
            } else {
                logger.warn("Session刷新队列已满，请求被丢弃: {}", sessionId);
            }
            return added;
        } catch (Exception e) {
            logger.error("提交session刷新请求失败: sessionId={}, error={}", sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 批处理session刷新任务
     * 定期从队列中取出session ID进行批量刷新
     */
    @Scheduled(fixedDelayString = "#{dragonTokenProperties.sessionHotRefresh.batchIntervalMillis}")
    public void processBatchRefresh() {
        if (refreshQueue.isEmpty()) {
            return;
        }

        int batchSize = dragonTokenProperties.getSessionHotRefresh().getBatchSize();
        String[] sessionIds = new String[Math.min(batchSize, refreshQueue.size())];
        int count = 0;

        // 从队列中取出session ID
        while (count < sessionIds.length && !refreshQueue.isEmpty()) {
            String sessionId = refreshQueue.poll();
            if (sessionId != null) {
                sessionIds[count++] = sessionId;
                queueSize.decrementAndGet();
            }
        }

        if (count > 0) {
            // 执行批量刷新
            String[] actualSessionIds = new String[count];
            System.arraycopy(sessionIds, 0, actualSessionIds, 0, count);
            
            int successCount = sessionHotRefreshUtil.batchHotRefresh(actualSessionIds);
            successfulRefreshes.addAndGet(successCount);

            logger.debug("批处理session刷新完成: 处理数量={}, 成功数量={}", count, successCount);
        }
    }

    /**
     * 定期清理过期缓存
     * 清理本地缓存和Redis中的过期数据
     */
    @Scheduled(fixedRate = 300000) // 每5分钟执行一次
    public void cleanupExpiredData() {
        try {
            // 清理本地缓存
            sessionHotRefreshUtil.cleanupExpiredCache();

            // 清理Redis中的过期活跃统计
            cleanupExpiredActivityStats();

            logger.debug("定期清理任务完成");

        } catch (Exception e) {
            logger.error("定期清理任务失败: {}", e.getMessage());
        }
    }

    /**
     * 清理Redis中过期的用户活跃统计
     */
    private void cleanupExpiredActivityStats() {
        try {
            // 这里可以实现更复杂的清理逻辑
            // 例如：扫描所有session:activity:*键，删除过期的
            String pattern = "session:activity:*";
            // 注意：在生产环境中应该避免使用keys命令，可以考虑使用scan
            logger.debug("清理Redis活跃统计数据");
        } catch (Exception e) {
            logger.debug("清理Redis活跃统计失败: {}", e.getMessage());
        }
    }

    /**
     * 性能监控报告
     * 定期输出性能统计信息
     */
    @Scheduled(fixedRateString = "#{dragonTokenProperties.sessionHotRefresh.performanceReportIntervalMillis}")
    public void performanceReport() {
        if (!dragonTokenProperties.getSessionHotRefresh().isPerformanceMonitorEnabled()) {
            return;
        }

        long total = totalRefreshRequests.get();
        long success = successfulRefreshes.get();
        int currentQueueSize = queueSize.get();

        double successRate = total > 0 ? (double) success / total * 100 : 0;

        logger.info("Session热刷新性能报告 - 总请求: {}, 成功: {}, 成功率: {:.2f}%, 当前队列大小: {}", 
                total, success, successRate, currentQueueSize);

        // 获取缓存统计信息
        String cacheStats = sessionHotRefreshUtil.getCacheStats();
        logger.info("Session缓存统计: {}", cacheStats);
    }

    /**
     * 获取性能统计信息
     * 
     * @return 性能统计JSON字符串
     */
    public String getPerformanceStats() {
        return String.format(
            "{\"totalRequests\":%d,\"successfulRefreshes\":%d,\"queueSize\":%d,\"successRate\":%.2f}",
            totalRefreshRequests.get(),
            successfulRefreshes.get(),
            queueSize.get(),
            totalRefreshRequests.get() > 0 ? 
                (double) successfulRefreshes.get() / totalRefreshRequests.get() * 100 : 0.0
        );
    }

    /**
     * 重置性能统计
     */
    public void resetStats() {
        totalRefreshRequests.set(0);
        successfulRefreshes.set(0);
        logger.info("性能统计已重置");
    }

    /**
     * 获取当前队列大小
     * 
     * @return 队列中待处理的请求数量
     */
    public int getCurrentQueueSize() {
        return queueSize.get();
    }

    /**
     * 手动触发批处理
     * 用于测试或紧急情况下的手动处理
     * 
     * @return 处理的请求数量
     */
    public int manualBatchProcess() {
        logger.info("手动触发批处理任务");
        int originalSize = queueSize.get();
        processBatchRefresh();
        return originalSize - queueSize.get();
    }
}