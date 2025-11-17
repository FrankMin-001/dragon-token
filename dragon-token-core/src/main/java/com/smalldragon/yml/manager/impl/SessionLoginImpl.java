package com.smalldragon.yml.manager.impl;

import com.alibaba.fastjson.JSONObject;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.manager.LoginInterface;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.SessionUtil;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @Author YML
 * @Date 2025/10/10 15:57
 **/
@Service
public class SessionLoginImpl implements LoginInterface {

    private static final Logger logger = LoggerFactory.getLogger(SessionLoginImpl.class);

    @Resource
    private SessionUtil sessionUtil;
    
    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    public UserContext login(String loginInfo, Session session) {
        String sessionId = sessionUtil.generateSession(loginInfo, session);
        UserContext userContext = new UserContext();
        userContext.setUserId(loginInfo);
        userContext.setSessionId(sessionId);
        return userContext;
    }

    @Override
    public UserContext login(String loginInfo) {
        ValidationUtils.requireNonEmptyString(loginInfo, "登录信息");
        
        UserContext userContext = new UserContext();
        userContext.setUserId(loginInfo);
        
        // Session模式下需要在HTTP请求上下文中创建session
        // 这里暂时返回基本的用户上下文，实际的session会在拦截器中处理
        logger.info("用户登录成功: {}", loginInfo);
        return userContext;
    }

    @Override
    public UserContext login(UserContext userContext) {
        ValidationUtils.requireNonNull(userContext, "用户上下文");
        ValidationUtils.requireNonEmptyString(userContext.getUserId(), "用户ID");
        
        // 设置用户信息
        setUserInfo(userContext);
        
        logger.info("用户登录成功: {}", userContext.getUserId());
        return userContext;
    }

    @Override
    public void setUserInfo(UserContext userInfo) {
        String sessionId = userInfo.getSessionId();
        String userInfoJSON = JSONObject.toJSONString(userInfo);
        sessionUtil.setSessionAttribute(sessionId, DragonTokenProperties.USER_INFO, userInfoJSON);
    }

    @Override
    public UserContext getUserInfo(String userId) {
        if (ValidationUtils.isEmpty(userId)) {
            return null;
        }
        
        return ExceptionHandlerUtils.executeWithExceptionHandling(
            () -> {
                // Session模式下需要通过当前请求的session获取用户信息
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
            logger.warn("No user context found during session logout");
            return;
        }
        
        ExceptionHandlerUtils.executeWithExceptionHandlingAndFinally(
            () -> {
                String sessionId = context.getSessionId();
                if (ValidationUtils.hasText(sessionId)) {
                    sessionUtil.expireSession(sessionId);
                    logger.info("用户登出成功: {}", context.getUserId());
                }
            },
            () -> DragonContextHolder.clear(),
            logger,
            "用户登出失败"
        );
    }

    @Override
    public void loginOut(String userId) {
        if (ValidationUtils.isEmpty(userId)) {
            logger.warn("用户ID为空，无法执行登出操作");
            return;
        }
        
        ExceptionHandlerUtils.executeWithExceptionHandling(
            () -> {
                // Session模式下需要找到对应用户的session并使其失效
                // 这里简化处理，实际应用中可能需要维护用户ID到session的映射
                UserContext context = DragonContextHolder.getContext();
                if (context != null && userId.equals(context.getUserId())) {
                    loginOut();
                } else {
                    logger.warn("无法找到用户{}的session信息", userId);
                }
            },
            logger,
            "用户登出失败"
        );
    }

    @Override
    public boolean isLoggedIn(String userId) {
        if (ValidationUtils.isEmpty(userId)) {
            return false;
        }
        
        return ExceptionHandlerUtils.executeWithExceptionHandling(
            () -> {
                UserContext context = DragonContextHolder.getContext();
                return context != null && userId.equals(context.getUserId());
            },
            logger,
            "检查登录状态失败",
            false
        );
    }

    @Override
    public String refreshToken(String token) {
        // Session模式下不需要刷新token
        logger.debug("Session模式下不需要刷新token");
        return token;
    }

    @Override
    public long getTokenRemainingTime(String token) {
        // Session模式下返回配置的session超时时间
        return dragonTokenProperties.getRetentionTime();
    }

}