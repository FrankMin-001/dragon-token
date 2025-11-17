package com.smalldragon.yml.manager.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.utils.SessionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @Author YML
 * @Date 2025/10/9 10:52
 **/
@Service
public class TokenManagerImpl implements TokenManager {

    private static final Logger logger = LoggerFactory.getLogger(TokenManagerImpl.class);

    @Resource
    private SessionUtil sessionUtil;

    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Resource
    private JwtUtil jwtUtil;

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    @Override
    public String createToken(Object userInfo) {
        return jwtUtil.generateTokenByStateless(JSON.toJSONString(userInfo));
    }

    @Override
    public String createToken(String userId) {
        // JWT模式通过TOKEN获取
        return jwtUtil.generateTokenByLoginId(userId);
    }

    @Override
    public String createToken(String userId, Session session) {
        return sessionUtil.generateSession(userId, session);
    }

    @Override
    public boolean verifyToken(String token) {
        return jwtUtil.validateToken(token);
    }

    @Override
    public boolean verifySession(String sessionId) {
        return sessionUtil.getSessionRemainingTime(sessionId) > 0 ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    public String getUserId(String token) {
        return jwtUtil.getLoginIdByParseToken(token);
    }

    @Override
    public String getUserIdBySession(String sessionId) {
        return sessionUtil.getUserIdBySession(sessionId);
    }

    @Override
    public String getUserInfo(String token) {
        return jwtUtil.getLoginInfoByParseToken(token);
    }

    @Override
    public UserContext getUserInfoById(String userId) {
        Object cachedUserInfo = redisTemplate.opsForHash().get(CacheKeyConstants.USER_CACHE_KEY, userId);
        if (cachedUserInfo != null) {
           return JSONObject.parseObject((String) cachedUserInfo,UserContext.class);
        }
        return null;
    }

    @Override
    public UserContext getUserInfoBySessionId(String sessionId) {
        return sessionUtil.getUserInfoBySession(sessionId);
    }

    @Override
    public void removeToken(String token) {
        if (!StringUtils.hasText(token)) {
            logger.warn("Token is null or empty, cannot remove");
            return;
        }

        try {
            // 验证token是否有效
            if (!jwtUtil.validateToken(token)) {
                logger.warn("Invalid token provided for removal: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
                return;
            }

            // 根据策略类型处理不同的token
            if (DragonTokenProperties.JWT.equals(dragonTokenProperties.getStrategyType())) {
                // JWT模式：将token加入黑名单并清除用户缓存
                addJwtTokenToBlacklist(token);
                String userId = jwtUtil.getLoginIdByParseToken(token);
                if (StringUtils.hasText(userId)) {
                    redisTemplate.opsForHash().delete(CacheKeyConstants.USER_CACHE_KEY, userId);
                    logger.info("Cleared user cache for userId: {}", userId);
                }
            } else if (DragonTokenProperties.STATELESS.equals(dragonTokenProperties.getStrategyType())) {
                // 无状态模式：将token加入黑名单
                addStatelessTokenToBlacklist(token);
            }

            logger.info("Token successfully removed and blacklisted");
        } catch (Exception e) {
            logger.error("Error removing token: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to remove token", e);
        }
    }

    /**
     * 将JWT token添加到黑名单
     */
    private void addJwtTokenToBlacklist(String token) {
        try {
            long remainingTimeMinutes = jwtUtil.getTokenRemainingTime(token);
            if (remainingTimeMinutes > 0) {
                long remainingTimeSeconds = remainingTimeMinutes * 60;
                String blacklistKey = CacheKeyConstants.buildJwtBlacklistKey(token);
                redisTemplate.opsForValue().set(blacklistKey, "blacklisted", remainingTimeSeconds, TimeUnit.SECONDS);
                logger.debug("JWT token added to blacklist with remaining time: {} minutes", remainingTimeMinutes);
            } else {
                logger.warn("JWT token has already expired, no need to blacklist");
            }
        } catch (Exception e) {
            logger.error("Error adding JWT token to blacklist: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 将Stateless token添加到黑名单
     */
    private void addStatelessTokenToBlacklist(String token) {
        try {
            long remainingTimeMinutes = jwtUtil.getTokenRemainingTime(token);
            if (remainingTimeMinutes > 0) {
                long remainingTimeSeconds = remainingTimeMinutes * 60;
                String blacklistKey = CacheKeyConstants.buildStatelessBlacklistKey(token);
                redisTemplate.opsForValue().set(blacklistKey, "blacklisted", remainingTimeSeconds, TimeUnit.SECONDS);
                logger.debug("Stateless token added to blacklist with remaining time: {} minutes", remainingTimeMinutes);
            } else {
                logger.warn("Stateless token has already expired, no need to blacklist");
            }
        } catch (Exception e) {
            logger.error("Error adding Stateless token to blacklist: {}", e.getMessage(), e);
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
            // 根据策略类型检查不同的黑名单
            String blacklistKey;
            if (DragonTokenProperties.JWT.equals(dragonTokenProperties.getStrategyType())) {
                blacklistKey = CacheKeyConstants.buildJwtBlacklistKey(token);
            } else if (DragonTokenProperties.STATELESS.equals(dragonTokenProperties.getStrategyType())) {
                blacklistKey = CacheKeyConstants.buildStatelessBlacklistKey(token);
            } else {
                return false;
            }

            Boolean exists = redisTemplate.hasKey(blacklistKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Error checking token blacklist status: {}", e.getMessage(), e);
            // 出现异常时，为了安全起见，认为token可能被黑名单
            return true;
        }
    }

    @Override
    public void removeSession(String session) {
        sessionUtil.expireSession(session);
    }

    // 默认刷新
    @Override
    public String refreshTimeoutByToken(String token) {
        if (dragonTokenProperties.getStrategyType().equals(DragonTokenProperties.JWT)){
            if (jwtUtil.isTokenAboutToExpire(token)){
                String userId = jwtUtil.getLoginIdByParseToken(token);
                return jwtUtil.generateTokenByLoginId(userId);
            }
        }
        if (dragonTokenProperties.getStrategyType().equals(DragonTokenProperties.STATELESS)){
            if (jwtUtil.isTokenAboutToExpire(token)){
                String userInfo = jwtUtil.getLoginInfoByParseToken(token);
                return jwtUtil.generateTokenByStateless(userInfo);
            }
        }
        return null;
    }

    // 默认重新刷新两小时
    @Override
    public void refreshTimeoutBySession(String sessionId) {
        sessionUtil.setSessionExpired(sessionId, false);
    }

}
