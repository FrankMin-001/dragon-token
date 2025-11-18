package com.smalldragon.yml.interceptors;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.smalldragon.yml.annotations.DragonCheckMode;
import com.smalldragon.yml.annotations.DragonCheckPermission;
import com.smalldragon.yml.annotations.DragonCheckRole;
import com.smalldragon.yml.annotations.DragonIgnore;
import com.smalldragon.yml.constants.CacheKeyConstants;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.core.StpInterface;
import com.smalldragon.yml.exceptions.AuthenticationException;
import com.smalldragon.yml.exceptions.PermissionDeniedException;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.manager.impl.TokenManagerImpl;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.RedisOperationUtils;
import com.smalldragon.yml.utils.SessionHotRefreshUtil;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 集成式认证拦截器 - 同时处理Session上下文和登录认证
 * 职责：初始化Session上下文 + 登录检查 + 基础权限验证
 */

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);

    @Resource
    private TokenManager tokenManager;

    @Resource
    private StpInterface stpInterface;

    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    @Resource
    private SessionHotRefreshUtil sessionHotRefreshUtil;

    private final String HEAD_TOKEN = "DRAGON-TOKEN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        String requestURI = request.getRequestURI();

        // 记录请求开始时间（用于性能监控）
        request.setAttribute("requestStartTime", System.currentTimeMillis());

        // 检查类级别或方法级别的免登录注解
        if (hasIgnoreLoginAnnotation(handlerMethod)) {
            logger.debug("检测到免登录注解，跳过登录验证");
            return true;
        }

        // Step1.检查是否是白名单路径 (通过则放行,免鉴权)
        if (isWhiteList(requestURI)) {
            return true;
        }

        // Step2.判断鉴权方式 ( SESSION / TOKEN ),进行登录校验
        switch (dragonTokenProperties.getStrategyType()) {
            case DragonTokenProperties.SESSION:
                String sessionId = getSessionIdByRequest(request);
                getContextBySession(sessionId);
                // Session模式下执行热刷新（如果启用）
                if (dragonTokenProperties.getSessionHotRefresh().isEnabled()) {
                    sessionHotRefreshUtil.hotRefreshOnUserAction(sessionId);
                }
                break;
            case DragonTokenProperties.JWT:
                getContextByJWT(getTokenByHeader(request));
                break;
            case DragonTokenProperties.STATELESS:
                getContextByStateless(getTokenByHeader(request));
                break;
            default:
                logger.info("default strategy is SESSION");
        }

        // Step3.进行基础权限鉴权
        // 3.1 获取类级别的注解
        Class<?> clazz = handlerMethod.getBeanType();
        Method method = handlerMethod.getMethod();

        boolean permissionBoolean = checkPermissionAnnotation(clazz, method, request);
        boolean roleBoolean = checkRoleAnnotation(clazz, method, request);

        if (!(permissionBoolean && roleBoolean)) {
            throw new PermissionDeniedException();
        }

        return true; // 认证通过，放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Step1.删除用户上下文
        DragonContextHolder.clear();

        // 记录请求耗时（可选）
        Long startTime = (Long) request.getAttribute("requestStartTime");
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            // 可以记录到日志或监控系统
            if (duration > 5000) {
                // 超过5秒的请求记录警告
                logger.warn("slow request: " + request.getRequestURI() + " - " + duration + "ms");
            }
        }
    }

    /* 白名单判断 */
    private boolean isWhiteList(String requestURI) {
        // 精准匹配
        for (String whitePath : dragonTokenProperties.getWhitePaths()) {
            if (whitePath.endsWith("/")) {
                return requestURI.startsWith(whitePath);
            } else {
                return requestURI.equals(whitePath);
            }
        }
        return false;
    }

    // 检查是否含有某权限标识符
    private boolean checkPermissionAnnotation(Class<?> clazz, Method method, HttpServletRequest request) {
        // 基础权限检查：管理员接口需要ADMIN角色
        DragonCheckPermission classAnnotation = clazz.getAnnotation(DragonCheckPermission.class);
        DragonCheckPermission methodAnnotation = method.getAnnotation(DragonCheckPermission.class);

        // 方法注解优先于类注解
        DragonCheckPermission annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation != null) {
            String[] permissions = annotation.value().length > 0 ? annotation.value() : new String[]{};

            if (permissions.length == 0) {
                return true;
            }

            // 该用户本身所拥有的权限集合（支持租户隔离）
            UserContext currentUser = DragonContextHolder.getContext();
            List<String> userPermissions = stpInterface.getPermissionList(currentUser.getTenantId(), currentUser.getUserId());

            return checkPermissions(userPermissions, permissions, annotation.mode());
        }

        // 其他接口默认允许所有登录用户访问
        return true;
    }

    // 校验是否含有某角色
    private boolean checkRoleAnnotation(Class<?> clazz, Method method, HttpServletRequest request) {
        // 基础权限检查：管理员接口需要ADMIN角色
        DragonCheckRole classAnnotation = clazz.getAnnotation(DragonCheckRole.class);
        DragonCheckRole methodAnnotation = method.getAnnotation(DragonCheckRole.class);

        // 方法注解优先于类注解
        DragonCheckRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        if (annotation != null) {
            String[] roles = annotation.value().length > 0 ? annotation.value() : new String[]{};

            if (roles.length == 0) {
                return true;
            }

            // 该用户本身所拥有的角色集合（支持租户隔离）
            UserContext currentUser = DragonContextHolder.getContext();
            List<String> userRoles = stpInterface.getRoleList(currentUser.getTenantId(), currentUser.getUserId());

            return checkPermissions(userRoles, roles, annotation.mode());
        }

        // 其他接口默认允许所有登录用户访问
        return true;
    }

    /* 获取Token */
    private String getTokenByHeader(HttpServletRequest request) {
        try {
            String token = request.getHeader(HEAD_TOKEN);
            if (token == null || token.trim().isEmpty()) {
                throw new AuthenticationException("未登录或登录已失效");
            }
            
            // 验证token格式和有效性
            tokenManager.verifyToken(token);
            
            // 检查token是否在黑名单中
            if (tokenManager.isTokenBlacklisted(token)) {
                logger.warn("Token已被加入黑名单: {}", token.substring(0, Math.min(token.length(), 20)) + "...");
                throw new AuthenticationException("Token已失效，请重新登录");
            }
            
            return token;
        } catch (Exception e) {
            throw new AuthenticationException(e.toString());
        }
    }

    /* 获取Session */
    private String getSessionIdByRequest(HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        boolean sessionExpired = tokenManager.verifySession(sessionId);
        if (sessionExpired) {
            throw new AuthenticationException();
        }
        return sessionId;
    }

    // JWT 模式
    private void getContextByJWT(String jwtToken) {
        String userId = tokenManager.getUserId(jwtToken);

        // 从Redis中获取完整的用户信息，支持租户隔离
        // 先尝试获取租户隔离的用户信息，如果没有再尝试传统方式
        UserContext userContext = tokenManager.getUserInfoById(userId); // 向后兼容
        if (userContext == null) {
            // 尝试传统Hash存储方式
            Object cachedUserInfo = redisTemplate.opsForHash().get(CacheKeyConstants.USER_CACHE_KEY, userId);
            if (cachedUserInfo != null) {
                userContext = JSONObject.parseObject(cachedUserInfo.toString(), UserContext.class);
            }
        }

        if (userContext != null) {
            // 如果Redis中有缓存，直接使用缓存的数据
            userContext.setToken(jwtToken); // 确保token是最新的
            DragonContextHolder.setContext(userContext);
            logger.debug("Loaded user context from cache: userId={}, tenantId={}",
                userContext.getUserId(), userContext.getTenantId());
        } else {
            // 如果Redis中没有缓存，创建基础用户上下文
            userContext = new UserContext();
            userContext.setUserId(userId);
            userContext.setToken(jwtToken);
            DragonContextHolder.setContext(userContext);
            logger.debug("Created basic user context: userId={}", userId);
        }
    }

    // SESSION 模式
    private void getContextBySession(String sessionId) {
        UserContext userContext = tokenManager.getUserInfoBySessionId(sessionId);
        if (ValidationUtils.isObjectEmpty(userContext)) {
            UserContext userContextSimple = new UserContext();
            String userId = tokenManager.getUserIdBySession(sessionId);
            userContextSimple.setUserId(userId);
            userContextSimple.setSessionId(sessionId);
            DragonContextHolder.setContext(userContextSimple);
        }
        DragonContextHolder.setContext(userContext);
    }

    // STATELESS 模式
    private void getContextByStateless(String completeToken) {
        String userInfo = tokenManager.getUserInfo(completeToken);
        UserContext userContext = JSONObject.parseObject(userInfo, UserContext.class);
        DragonContextHolder.setContext(userContext);
    }

    private boolean checkPermissions(List<String> userPermissions, String[] requiredPermissions, DragonCheckMode mode) {
        if (mode == DragonCheckMode.AND) {
            // AND 模式：需要拥有所有要求的权限
            return Arrays.stream(requiredPermissions)
                    .allMatch(requiredPermission -> hasPermission(userPermissions, requiredPermission));
        } else {
            // OR 模式：只需要拥有任意一个要求的权限
            return Arrays.stream(requiredPermissions)
                    .anyMatch(requiredPermission -> hasPermission(userPermissions, requiredPermission));
        }
    }

    /**
     * 检查用户是否拥有特定权限
     */
    private boolean hasPermission(List<String> userPermissions, String requiredPermission) {
        return userPermissions.stream()
                .anyMatch(userPermission -> userPermission.equals(requiredPermission));
    }

    /**
     *  检查免登录注解
     */
    private boolean hasIgnoreLoginAnnotation(HandlerMethod handlerMethod) {
        // 检查方法上的注解
        if (handlerMethod.getMethod().isAnnotationPresent(DragonIgnore.class)) {
            return true;
        }

        // 检查类上的注解
        Class<?> beanType = handlerMethod.getBeanType();
        if (beanType.isAnnotationPresent(DragonIgnore.class)) {
            return true;
        }

        return false;
    }

}
