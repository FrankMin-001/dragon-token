package com.smalldragon.yml.manager.impl;

import com.alibaba.fastjson.JSON;
import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.manager.LoginInterface;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.utils.RedisOperationUtils;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author YML
 * @Date 2025/10/10 15:58
 **/
@Service
public class JwtLoginImpl implements LoginInterface {

    private static final Logger logger = LoggerFactory.getLogger(JwtLoginImpl.class);

    @Resource
    TokenManager tokenManager;
    @Resource
    RedisTemplate<String, Object> redisTemplate;
    @Resource
    JwtUtil jwtUtil;
    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    public UserContext login(String loginInfo) {
        UserContext userContext = new UserContext();
        String token = tokenManager.createToken(loginInfo);
        userContext.setToken(token);
        userContext.setUserId(loginInfo);
        return userContext;
    }

    @Override
    public void setUserInfo(UserContext userInfo) {
        String hashKey = userInfo.getUserId();
        String hashValue = JSON.toJSONString(userInfo);
        redisTemplate.opsForHash().put(CacheKeyConstants.USER_CACHE_KEY, hashKey, hashValue);
        redisTemplate.expire(CacheKeyConstants.USER_CACHE_KEY, dragonTokenProperties.getRetentionTime(), TimeUnit.SECONDS);
    }

    @Override
    public void loginOut() {
        UserContext context = DragonContextHolder.getContext();
        if (ValidationUtils.isNull(context)) {
            logger.warn("No user context found during JWT logout");
            return;
        }

        String token = context.getToken();
        String userId = context.getUserId();

        if (ValidationUtils.isEmpty(token)) {
            logger.warn("No token found in user context during JWT logout");
            // 仍然清理用户缓存和上下文
            if (ValidationUtils.hasText(userId)) {
                RedisOperationUtils.deleteHashFields(redisTemplate, CacheKeyConstants.USER_CACHE_KEY, userId);
            }
            DragonContextHolder.clear();
            return;
        }

        ExceptionHandlerUtils.executeWithExceptionHandlingAndFinally(
            () -> {
                // 将token添加到黑名单
                addTokenToBlacklist(token);
                logger.info("JWT token blacklisted successfully: {}", 
                    token.substring(0, Math.min(token.length(), 20)) + "...");

                // 清除用户缓存
                if (ValidationUtils.hasText(userId)) {
                    RedisOperationUtils.deleteHashFields(redisTemplate, CacheKeyConstants.USER_CACHE_KEY, userId);
                    logger.info("User cache cleared for userId: {}", userId);
                }
            },
            () -> {
                // 无论是否成功，都要清理上下文
                DragonContextHolder.clear();
                logger.info("JWT logout completed, context cleared");
            },
            logger,
            "Failed to blacklist JWT token during logout: " + token
        );
    }

    /**
     * 将JWT token添加到黑名单
     */
    private void addTokenToBlacklist(String token) {
        try {
            long remainingTimeMinutes = jwtUtil.getTokenRemainingTime(token);
            if (remainingTimeMinutes > 0) {
                long remainingTimeSeconds = remainingTimeMinutes * 60;
                String blacklistKey = CacheKeyConstants.buildJwtBlacklistKey(token);
                redisTemplate.opsForValue().set(blacklistKey, "blacklisted", remainingTimeSeconds, TimeUnit.SECONDS);
                logger.debug("JWT token added to blacklist with remaining time: {} minutes", remainingTimeMinutes);
            } else {
                logger.warn("Token has already expired, no need to blacklist");
            }
        } catch (Exception e) {
            logger.error("Error adding JWT token to blacklist: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 检查token是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        try {
            String blacklistKey = CacheKeyConstants.buildJwtBlacklistKey(token);
            Boolean exists = redisTemplate.hasKey(blacklistKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Error checking JWT token blacklist status: {}", e.getMessage(), e);
            // 出现异常时，为了安全起见，认为token可能被黑名单
            return true;
        }
    }

    @Override
    public UserContext login(UserContext userContext) {
        if (userContext == null || !StringUtils.hasText(userContext.getUserId())) {
            throw new IllegalArgumentException("用户上下文信息不能为空");
        }
        
        String token = tokenManager.createToken(userContext.getUserId());
        userContext.setToken(token);
        
        // 设置用户信息到缓存
        setUserInfo(userContext);
        
        logger.info("用户登录成功: {}", userContext.getUserId());
        return userContext;
    }

    @Override
    public UserContext getUserInfo(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        
        try {
            Object cachedUserInfo = redisTemplate.opsForHash().get(CacheKeyConstants.USER_CACHE_KEY, userId);
            if (cachedUserInfo != null) {
                return JSON.parseObject(cachedUserInfo.toString(), UserContext.class);
            }
        } catch (Exception e) {
            logger.error("获取用户信息失败: {}", e.getMessage(), e);
        }
        
        return null;
    }

    @Override
    public void loginOut(String userId) {
        if (!StringUtils.hasText(userId)) {
            logger.warn("用户ID为空，无法执行登出操作");
            return;
        }
        
        try {
            // 清理用户缓存
            redisTemplate.opsForHash().delete(CacheKeyConstants.USER_CACHE_KEY, userId);
            logger.info("用户登出成功: {}", userId);
        } catch (Exception e) {
            logger.error("用户登出失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isLoggedIn(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        
        try {
            Boolean exists = redisTemplate.opsForHash().hasKey(CacheKeyConstants.USER_CACHE_KEY, userId);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("检查登录状态失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String refreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }
        
        try {
            // 验证当前token
            if (!tokenManager.verifyToken(token)) {
                logger.warn("Token验证失败，无法刷新");
                return token;
            }
            
            // 获取用户ID
            String userId = tokenManager.getUserId(token);
            if (!StringUtils.hasText(userId)) {
                logger.warn("无法从token中获取用户ID");
                return token;
            }
            
            // 生成新token
            String newToken = tokenManager.createToken(userId);
            
            // 将旧token加入黑名单
            addTokenToBlacklist(token);
            
            logger.info("Token刷新成功，用户: {}", userId);
            return newToken;
        } catch (Exception e) {
            logger.error("Token刷新失败: {}", e.getMessage(), e);
            return token;
        }
    }

    @Override
    public long getTokenRemainingTime(String token) {
        if (!StringUtils.hasText(token)) {
            return 0;
        }
        
        try {
            // 这里需要JwtUtil提供获取剩余时间的方法
            // 暂时返回配置的保留时间作为默认值
            return dragonTokenProperties.getRetentionTime();
        } catch (Exception e) {
            logger.error("获取token剩余时间失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}