package com.smalldragon.yml.manager.impl;

import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.manager.LoginInterface;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.JwtUtil;
import com.smalldragon.yml.utils.RedisOperationUtils;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


/**
 * @Author YML
 * @Date 2025/10/10 16:06
 **/
@Service
public class StatelessLoginImpl implements LoginInterface {

    private static final Logger logger = LoggerFactory.getLogger(StatelessLoginImpl.class);

    @Resource
    TokenManager tokenManager;

    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Resource
    JwtUtil jwtUtil;

    @Override
    public UserContext login(String loginInfo) {
        ValidationUtils.requireNonEmptyString(loginInfo, "登录信息");

        UserContext userContext = new UserContext();
        userContext.setUserId(loginInfo);

        String token = tokenManager.createToken(userContext);
        userContext.setToken(token);

        logger.info("无状态用户登录成功: {}", loginInfo);
        return userContext;
    }

    @Override
    public UserContext login(UserContext userContext) {
        ValidationUtils.requireNonNull(userContext, "用户上下文");
        ValidationUtils.requireNonEmptyString(userContext.getUserId(), "用户ID");

        String token = tokenManager.createToken(userContext);
        userContext.setToken(token);

        logger.info("无状态用户登录成功: {}", userContext.getUserId());
        return userContext;
    }

    @Override
    public void setUserInfo(UserContext userInfo) {
        // 不需要,无状态用户信息都放在了TOKEN里面
    }

    @Override
    public UserContext getUserInfo(String userId) {
        if (ValidationUtils.isEmpty(userId)) {
            return null;
        }

        return ExceptionHandlerUtils.executeWithExceptionHandling(
            () -> {
                // 无状态模式下，用户信息存储在token中
                UserContext context = DragonContextHolder.getContext();
                if (context != null && userId.equals(context.getUserId())) {
                    return context;
                }
                return null;
            },
            logger,
            "获取用户信息失败",
            null
        );
    }

    @Override
    public void loginOut() {
        UserContext context = DragonContextHolder.getContext();
        if (ValidationUtils.isNull(context)) {
            logger.warn("No user context found during Stateless logout");
            return;
        }

        String token = context.getToken();
        if (ValidationUtils.isEmpty(token)) {
            logger.warn("No token found in user context during Stateless logout");
            DragonContextHolder.clear();
            return;
        }

        ExceptionHandlerUtils.executeWithExceptionHandlingAndFinally(
            () -> {
                // 将token添加到黑名单
                addTokenToBlacklist(token);
                logger.info("Stateless token blacklisted successfully: {}",
                    token.substring(0, Math.min(token.length(), 20)) + "...");
            },
            () -> {
                // 无论是否成功，都要清理上下文
                DragonContextHolder.clear();
                logger.info("Stateless logout completed, context cleared");
            },
            logger,
            "Failed to blacklist Stateless token during logout: " + token
        );
    }

    @Override
    public void loginOut(String userId) {
        if (ValidationUtils.isEmpty(userId)) {
            logger.warn("用户ID为空，无法执行登出操作");
            return;
        }

        try {
            // 无状态模式下，需要通过当前上下文判断是否为同一用户
            UserContext context = DragonContextHolder.getContext();
            if (context != null && userId.equals(context.getUserId())) {
                loginOut();
            } else {
                logger.warn("无法找到用户{}的token信息", userId);
            }
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
            UserContext context = DragonContextHolder.getContext();
            if (context != null && userId.equals(context.getUserId())) {
                String token = context.getToken();
                return StringUtils.hasText(token) && !isTokenBlacklisted(token);
            }
        } catch (Exception e) {
            logger.error("检查登录状态失败: {}", e.getMessage(), e);
        }

        return false;
    }

    @Override
    public String refreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            return token;
        }

        try {
            // 验证当前token
            if (!tokenManager.verifyToken(token) || isTokenBlacklisted(token)) {
                logger.warn("Token验证失败或已被列入黑名单，无法刷新");
                return token;
            }

            // 获取用户信息
            String userInfoStr = tokenManager.getUserInfo(token);
            if (userInfoStr == null) {
                logger.warn("无法从token中获取用户信息");
                return token;
            }

            // 生成新token
            String newToken = tokenManager.createToken(userInfoStr);

            // 将旧token加入黑名单
            addTokenToBlacklist(token);

            logger.info("Token刷新成功，用户信息: {}", userInfoStr);
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
            return jwtUtil.getTokenRemainingTime(token);
        } catch (Exception e) {
            logger.error("获取token剩余时间失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 将Stateless token添加到黑名单
     */
    private void addTokenToBlacklist(String token) {
        long remainingTimeMinutes = ExceptionHandlerUtils.executeWithExceptionHandling(
            () -> jwtUtil.getTokenRemainingTime(token),
            logger,
            "获取token剩余时间失败",
            0L
        );

        if (remainingTimeMinutes > 0) {
            long remainingTimeSeconds = remainingTimeMinutes * 60;
            String blacklistKey = CacheKeyConstants.buildStatelessBlacklistKey(token);
            RedisOperationUtils.setWithExpire(redisTemplate, blacklistKey, "blacklisted",
                                            remainingTimeSeconds, TimeUnit.SECONDS);
            logger.debug("Stateless token added to blacklist with remaining time: {} minutes", remainingTimeMinutes);
        } else {
            logger.warn("Token has already expired, no need to blacklist");
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
            String blacklistKey = CacheKeyConstants.buildStatelessBlacklistKey(token);
            Boolean exists = redisTemplate.hasKey(blacklistKey);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            logger.error("Error checking stateless token blacklist status: {}", e.getMessage(), e);
            // 出现异常时，为了安全起见，认为token可能被黑名单
            return true;
        }
    }

}
