package com.smalldragon.yml.utils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * Redis操作工具类
 * 提供统一的Redis操作方法，减少重复代码
 * 
 * @author smalldragon
 */
public class RedisOperationUtils {

    /**
     * 设置带过期时间的键值对
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @param value 值
     * @param timeout 过期时间
     * @param timeUnit 时间单位
     */
    public static void setWithExpire(RedisTemplate<String, Object> redisTemplate, String key, Object value, 
                                   long timeout, TimeUnit timeUnit) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
    }

    /**
     * 获取字符串值
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @return 字符串值，如果不存在返回null
     */
    public static String getString(RedisTemplate<String, Object> redisTemplate, String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取Hash中的值
     * 
     * @param redisTemplate Redis模板
     * @param key Hash键
     * @param hashKey Hash字段
     * @return 值，如果不存在返回null
     */
    public static Object getHashValue(RedisTemplate<String, Object> redisTemplate, String key, String hashKey) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(hashKey)) {
            return null;
        }
        return redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 设置Hash中的值
     * 
     * @param redisTemplate Redis模板
     * @param key Hash键
     * @param hashKey Hash字段
     * @param value 值
     */
    public static void setHashValue(RedisTemplate<String, Object> redisTemplate, String key, String hashKey, Object value) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(hashKey) || value == null) {
            return;
        }
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 删除Hash中的字段
     * 
     * @param redisTemplate Redis模板
     * @param key Hash键
     * @param hashKeys Hash字段
     */
    public static void deleteHashFields(RedisTemplate<String, Object> redisTemplate, String key, String... hashKeys) {
        if (!StringUtils.hasText(key) || hashKeys == null || hashKeys.length == 0) {
            return;
        }
        redisTemplate.opsForHash().delete(key, (Object[]) hashKeys);
    }

    /**
     * 检查键是否存在
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @return 是否存在
     */
    public static boolean hasKey(RedisTemplate<String, Object> redisTemplate, String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    /**
     * 删除键
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @return 是否删除成功
     */
    public static boolean deleteKey(RedisTemplate<String, Object> redisTemplate, String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        Boolean deleted = redisTemplate.delete(key);
        return deleted != null && deleted;
    }

    /**
     * 设置键的过期时间
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @param timeout 过期时间
     * @param timeUnit 时间单位
     * @return 是否设置成功
     */
    public static boolean expire(RedisTemplate<String, Object> redisTemplate, String key, long timeout, TimeUnit timeUnit) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        Boolean result = redisTemplate.expire(key, timeout, timeUnit);
        return result != null && result;
    }

    /**
     * 获取键的剩余过期时间（秒）
     * 
     * @param redisTemplate Redis模板
     * @param key 键
     * @return 剩余过期时间，-1表示永不过期，-2表示键不存在
     */
    public static long getExpire(RedisTemplate<String, Object> redisTemplate, String key) {
        if (!StringUtils.hasText(key)) {
            return -2;
        }
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null ? expire : -2;
    }
}