package com.smalldragon.yml.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 缓存一致性服务
 * 提供微服务环境下的缓存数据一致性保障
 * 
 * 核心功能：
 * 1. 分布式锁管理 - 基于Redis的分布式锁实现
 * 2. 版本控制系统 - 缓存数据版本管理和冲突检测
 * 3. 事件通知机制 - 实例间状态同步和变更通知
 * 4. 数据冲突解决 - 自动冲突检测和解决策略
 * 5. 缓存失效策略 - 智能缓存失效和刷新
 * 
 * @Author YML
 * @Date 2025/01/21
 */
@Service
public class CacheConsistencyService implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(CacheConsistencyService.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisMessageListenerContainer messageListenerContainer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 分布式锁相关常量
    private static final String LOCK_PREFIX = "dragon:token:lock:";
    private static final String VERSION_PREFIX = "dragon:token:version:";
    private static final String EVENT_CHANNEL = "dragon:token:consistency:events";
    
    // 本地缓存版本管理
    private final Map<String, String> localVersionCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lockTimeouts = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock versionLock = new ReentrantReadWriteLock();
    
    // 事件监听器
    private final Map<String, List<ConsistencyEventListener>> eventListeners = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 订阅一致性事件频道
        messageListenerContainer.addMessageListener(this, new ChannelTopic(EVENT_CHANNEL));
        logger.info("缓存一致性服务初始化完成，已订阅事件频道: {}", EVENT_CHANNEL);
    }

    @PreDestroy
    public void destroy() {
        // 清理资源
        eventListeners.clear();
        localVersionCache.clear();
        lockTimeouts.clear();
        logger.info("缓存一致性服务已关闭");
    }

    /**
     * 获取分布式锁
     * 
     * @param lockKey 锁键
     * @param timeout 超时时间（秒）
     * @param serviceId 服务ID
     * @return 是否成功获取锁
     */
    public boolean acquireLock(String lockKey, int timeout, String serviceId) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        
        String lockScript = 
            "if redis.call('get', KEYS[1]) == false then " +
            "  redis.call('setex', KEYS[1], ARGV[1], ARGV[2]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lockScript);
        script.setResultType(Long.class);
        
        Long result = redisTemplate.execute(script, 
            Collections.singletonList(fullLockKey), 
            String.valueOf(timeout), serviceId);
        
        boolean acquired = result != null && result == 1L;
        if (acquired) {
            lockTimeouts.put(fullLockKey, System.currentTimeMillis() + timeout * 1000L);
            logger.debug("成功获取分布式锁: {}, 服务ID: {}", lockKey, serviceId);
        }
        return acquired;
    }

    /**
     * 释放分布式锁
     * 
     * @param lockKey 锁键
     * @param serviceId 服务ID
     * @return 是否成功释放锁
     */
    public boolean releaseLock(String lockKey, String serviceId) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        
        String unlockScript = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(unlockScript);
        script.setResultType(Long.class);
        
        Long result = redisTemplate.execute(script, 
            Collections.singletonList(fullLockKey), serviceId);
        
        boolean released = result != null && result == 1L;
        if (released) {
            lockTimeouts.remove(fullLockKey);
            logger.debug("成功释放分布式锁: {}, 服务ID: {}", lockKey, serviceId);
        }
        return released;
    }

    /**
     * 续期分布式锁
     * 
     * @param lockKey 锁键
     * @param timeout 新的超时时间（秒）
     * @param serviceId 服务ID
     * @return 是否成功续期
     */
    public boolean renewLock(String lockKey, int timeout, String serviceId) {
        String fullLockKey = LOCK_PREFIX + lockKey;
        
        String renewScript = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  redis.call('expire', KEYS[1], ARGV[2]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(renewScript);
        script.setResultType(Long.class);
        
        Long result = redisTemplate.execute(script, 
            Collections.singletonList(fullLockKey), 
            serviceId, String.valueOf(timeout));
        
        boolean renewed = result != null && result == 1L;
        if (renewed) {
            lockTimeouts.put(fullLockKey, System.currentTimeMillis() + timeout * 1000L);
            logger.debug("成功续期分布式锁: {}, 服务ID: {}", lockKey, serviceId);
        }
        return renewed;
    }

    /**
     * 设置缓存版本
     * 
     * @param cacheKey 缓存键
     * @param version 版本号
     * @param ttl 生存时间（秒）
     */
    public void setCacheVersion(String cacheKey, String version, long ttl) {
        String versionKey = VERSION_PREFIX + cacheKey;
        
        versionLock.writeLock().lock();
        try {
            redisTemplate.opsForValue().set(versionKey, version, ttl, TimeUnit.SECONDS);
            localVersionCache.put(cacheKey, version);
            
            // 发送版本更新事件
            publishVersionUpdateEvent(cacheKey, version);
            
            logger.debug("设置缓存版本: {} -> {}", cacheKey, version);
        } finally {
            versionLock.writeLock().unlock();
        }
    }

    /**
     * 获取缓存版本
     * 
     * @param cacheKey 缓存键
     * @return 版本号
     */
    public String getCacheVersion(String cacheKey) {
        versionLock.readLock().lock();
        try {
            // 先检查本地缓存
            String localVersion = localVersionCache.get(cacheKey);
            if (localVersion != null) {
                return localVersion;
            }
            
            // 从Redis获取
            String versionKey = VERSION_PREFIX + cacheKey;
            String remoteVersion = (String) redisTemplate.opsForValue().get(versionKey);
            
            if (remoteVersion != null) {
                localVersionCache.put(cacheKey, remoteVersion);
            }
            
            return remoteVersion;
        } finally {
            versionLock.readLock().unlock();
        }
    }

    /**
     * 检查版本冲突
     * 
     * @param cacheKey 缓存键
     * @param expectedVersion 期望版本
     * @return 是否存在冲突
     */
    public boolean hasVersionConflict(String cacheKey, String expectedVersion) {
        String currentVersion = getCacheVersion(cacheKey);
        boolean conflict = currentVersion != null && !currentVersion.equals(expectedVersion);
        
        if (conflict) {
            logger.warn("检测到版本冲突: {} 期望版本={}, 当前版本={}", 
                cacheKey, expectedVersion, currentVersion);
        }
        
        return conflict;
    }

    /**
     * 原子性更新缓存和版本
     * 
     * @param cacheKey 缓存键
     * @param cacheValue 缓存值
     * @param expectedVersion 期望版本
     * @param newVersion 新版本
     * @param ttl 生存时间（秒）
     * @return 是否更新成功
     */
    public boolean atomicUpdateCacheWithVersion(String cacheKey, Object cacheValue, 
                                              String expectedVersion, String newVersion, long ttl) {
        String versionKey = VERSION_PREFIX + cacheKey;
        
        String updateScript = 
            "local currentVersion = redis.call('get', KEYS[2]) " +
            "if currentVersion == false or currentVersion == ARGV[1] then " +
            "  redis.call('setex', KEYS[1], ARGV[3], ARGV[2]) " +
            "  redis.call('setex', KEYS[2], ARGV[3], ARGV[4]) " +
            "  return 1 " +
            "else " +
            "  return 0 " +
            "end";
        
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(updateScript);
        script.setResultType(Long.class);
        
        try {
            String cacheValueStr = objectMapper.writeValueAsString(cacheValue);
            Long result = redisTemplate.execute(script, 
                Arrays.asList(cacheKey, versionKey),
                expectedVersion != null ? expectedVersion : "", 
                cacheValueStr, 
                String.valueOf(ttl), 
                newVersion);
            
            boolean success = result != null && result == 1L;
            if (success) {
                versionLock.writeLock().lock();
                try {
                    localVersionCache.put(cacheKey, newVersion);
                } finally {
                    versionLock.writeLock().unlock();
                }
                
                // 发送缓存更新事件
                publishCacheUpdateEvent(cacheKey, newVersion);
                logger.debug("原子性更新缓存成功: {} -> {}", cacheKey, newVersion);
            } else {
                logger.warn("原子性更新缓存失败，版本冲突: {}", cacheKey);
            }
            
            return success;
        } catch (Exception e) {
            logger.error("原子性更新缓存异常: {}", cacheKey, e);
            return false;
        }
    }

    /**
     * 失效缓存
     * 
     * @param cacheKey 缓存键
     * @param reason 失效原因
     */
    public void invalidateCache(String cacheKey, String reason) {
        try {
            // 删除缓存数据
            redisTemplate.delete(cacheKey);
            
            // 删除版本信息
            String versionKey = VERSION_PREFIX + cacheKey;
            redisTemplate.delete(versionKey);
            
            // 清理本地缓存
            versionLock.writeLock().lock();
            try {
                localVersionCache.remove(cacheKey);
            } finally {
                versionLock.writeLock().unlock();
            }
            
            // 发送缓存失效事件
            publishCacheInvalidationEvent(cacheKey, reason);
            
            logger.info("缓存失效: {}, 原因: {}", cacheKey, reason);
        } catch (Exception e) {
            logger.error("缓存失效失败: {}", cacheKey, e);
        }
    }

    /**
     * 发布版本更新事件
     */
    private void publishVersionUpdateEvent(String cacheKey, String version) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "version_update");
            event.put("cacheKey", cacheKey);
            event.put("version", version);
            event.put("timestamp", System.currentTimeMillis());
            
            redisTemplate.convertAndSend(EVENT_CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.warn("发布版本更新事件失败: {}", cacheKey, e);
        }
    }

    /**
     * 发布缓存更新事件
     */
    private void publishCacheUpdateEvent(String cacheKey, String version) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "cache_update");
            event.put("cacheKey", cacheKey);
            event.put("version", version);
            event.put("timestamp", System.currentTimeMillis());
            
            redisTemplate.convertAndSend(EVENT_CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.warn("发布缓存更新事件失败: {}", cacheKey, e);
        }
    }

    /**
     * 发布缓存失效事件
     */
    private void publishCacheInvalidationEvent(String cacheKey, String reason) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "cache_invalidation");
            event.put("cacheKey", cacheKey);
            event.put("reason", reason);
            event.put("timestamp", System.currentTimeMillis());
            
            redisTemplate.convertAndSend(EVENT_CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            logger.warn("发布缓存失效事件失败: {}", cacheKey, e);
        }
    }

    /**
     * 处理接收到的消息
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String messageBody = new String(message.getBody());
            Map<String, Object> event = objectMapper.readValue(messageBody, Map.class);
            
            String eventType = (String) event.get("type");
            String cacheKey = (String) event.get("cacheKey");
            
            switch (eventType) {
                case "version_update":
                    handleVersionUpdateEvent(event);
                    break;
                case "cache_update":
                    handleCacheUpdateEvent(event);
                    break;
                case "cache_invalidation":
                    handleCacheInvalidationEvent(event);
                    break;
                default:
                    logger.debug("未知事件类型: {}", eventType);
            }
            
            // 通知注册的监听器
            notifyEventListeners(eventType, event);
            
        } catch (Exception e) {
            logger.error("处理一致性事件失败", e);
        }
    }

    /**
     * 处理版本更新事件
     */
    private void handleVersionUpdateEvent(Map<String, Object> event) {
        String cacheKey = (String) event.get("cacheKey");
        String version = (String) event.get("version");
        
        versionLock.writeLock().lock();
        try {
            localVersionCache.put(cacheKey, version);
            logger.debug("更新本地版本缓存: {} -> {}", cacheKey, version);
        } finally {
            versionLock.writeLock().unlock();
        }
    }

    /**
     * 处理缓存更新事件
     */
    private void handleCacheUpdateEvent(Map<String, Object> event) {
        String cacheKey = (String) event.get("cacheKey");
        String version = (String) event.get("version");
        
        // 更新本地版本缓存
        handleVersionUpdateEvent(event);
        
        logger.debug("接收到缓存更新事件: {} -> {}", cacheKey, version);
    }

    /**
     * 处理缓存失效事件
     */
    private void handleCacheInvalidationEvent(Map<String, Object> event) {
        String cacheKey = (String) event.get("cacheKey");
        String reason = (String) event.get("reason");
        
        versionLock.writeLock().lock();
        try {
            localVersionCache.remove(cacheKey);
            logger.debug("清理本地版本缓存: {}, 原因: {}", cacheKey, reason);
        } finally {
            versionLock.writeLock().unlock();
        }
    }

    /**
     * 注册事件监听器
     * 
     * @param eventType 事件类型
     * @param listener 监听器
     */
    public void registerEventListener(String eventType, ConsistencyEventListener listener) {
        eventListeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
        logger.debug("注册事件监听器: {}", eventType);
    }

    /**
     * 通知事件监听器
     */
    private void notifyEventListeners(String eventType, Map<String, Object> event) {
        List<ConsistencyEventListener> listeners = eventListeners.get(eventType);
        if (listeners != null) {
            for (ConsistencyEventListener listener : listeners) {
                try {
                    listener.onEvent(eventType, event);
                } catch (Exception e) {
                    logger.warn("事件监听器处理失败: {}", eventType, e);
                }
            }
        }
    }

    /**
     * 获取锁状态信息
     */
    public Map<String, Object> getLockStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("activeLocks", lockTimeouts.size());
        status.put("lockDetails", new HashMap<>(lockTimeouts));
        return status;
    }

    /**
     * 获取版本缓存状态
     */
    public Map<String, Object> getVersionCacheStatus() {
        versionLock.readLock().lock();
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("localVersionCount", localVersionCache.size());
            status.put("versionDetails", new HashMap<>(localVersionCache));
            return status;
        } finally {
            versionLock.readLock().unlock();
        }
    }

    /**
     * 一致性事件监听器接口
     */
    public interface ConsistencyEventListener {
        void onEvent(String eventType, Map<String, Object> event);
    }
}