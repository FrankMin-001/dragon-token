package com.smalldragon.yml.interceptors;

import com.smalldragon.yml.annotations.DragonCheckPermission;
import com.smalldragon.yml.annotations.DragonCheckRole;
import com.smalldragon.yml.annotations.DragonIgnore;
import com.smalldragon.yml.core.StpInterface;
import com.smalldragon.yml.exceptions.AuthenticationException;
import com.smalldragon.yml.exceptions.PermissionDeniedException;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.SessionHotRefreshUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 简化的 AuthInterceptor 测试类
 * 专注于核心逻辑测试，避免复杂的依赖
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthInterceptorSimpleTest {

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private StpInterface stpInterface;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private DragonTokenProperties dragonTokenProperties;

    @Mock
    private SessionHotRefreshUtil sessionHotRefreshUtil;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        
        // 配置 DragonTokenProperties 模拟行为
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        when(dragonTokenProperties.getWhitePaths()).thenReturn(new String[]{"/login", "/public/**"});
        
        // 配置 SessionHotRefresh 相关的模拟行为
        DragonTokenProperties.SessionHotRefreshConfig sessionHotRefresh = mock(DragonTokenProperties.SessionHotRefreshConfig.class);
        when(sessionHotRefresh.isEnabled()).thenReturn(false);
        when(dragonTokenProperties.getSessionHotRefresh()).thenReturn(sessionHotRefresh);
    }

    // 测试白名单路径
    @Test
    void testWhiteListPath_ShouldPassWithoutAuthentication() throws Exception {
        // 准备
        request.setRequestURI("/login");
        
        // 创建一个真实的HandlerMethod，而不是mock
        Method method = TestController.class.getMethod("publicMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        // 执行
        boolean result = authInterceptor.preHandle(request, response, handlerMethod);
        
        // 验证
        assertTrue(result, "白名单路径应该直接通过");
        verifyNoInteractions(tokenManager, stpInterface);
    }

    // 测试免登录注解
    @Test
    void testIgnoreLoginAnnotation_ShouldPassWithoutAuthentication() throws Exception {
        // 准备
        request.setRequestURI("/api/public");
        
        Method method = TestController.class.getMethod("publicMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        // 执行
        boolean result = authInterceptor.preHandle(request, response, handlerMethod);
        
        // 验证
        assertTrue(result, "免登录注解方法应该直接通过");
        verifyNoInteractions(tokenManager, stpInterface);
    }

    // 测试权限检查 - 拥有权限
    @Test
    void testPermissionCheck_ShouldPassWhenHasPermission() throws Exception {
        // 准备
        request.setRequestURI("/api/admin");
        request.addHeader("DRAGON-TOKEN", "valid-token");
        
        Method method = TestController.class.getMethod("adminMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        when(tokenManager.getUserId("valid-token")).thenReturn("user123");
        when(stpInterface.getPermissionList("user123")).thenReturn(Arrays.asList("user:read", "user:write"));
        
        // 设置redisTemplate的mock行为
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps = 
            mock(org.springframework.data.redis.core.HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), anyString())).thenReturn(null); // Redis中没有缓存
        
        // 执行
        boolean result = authInterceptor.preHandle(request, response, handlerMethod);
        
        // 验证
        assertTrue(result, "用户拥有权限时应该通过");
        verify(tokenManager).getUserId("valid-token");
        verify(stpInterface).getPermissionList("user123");
    }

    // 测试权限不足
    @Test
    void testInsufficientPermission_ShouldThrowPermissionDeniedException() throws Exception {
        // 准备
        request.setRequestURI("/api/admin");
        request.addHeader("DRAGON-TOKEN", "valid-token");
        
        Method method = TestController.class.getMethod("adminMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        when(tokenManager.getUserId("valid-token")).thenReturn("user123");
        when(stpInterface.getPermissionList("user123")).thenReturn(Arrays.asList("user:read"));
        
        // 设置redisTemplate的mock行为
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps = 
            mock(org.springframework.data.redis.core.HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), anyString())).thenReturn(null); // Redis中没有缓存
        
        // 执行和验证
        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, 
            () -> authInterceptor.preHandle(request, response, handlerMethod));
        
        assertEquals("权限不足", exception.getMessage());
        verify(tokenManager).getUserId("valid-token");
        verify(stpInterface).getPermissionList("user123");
    }

    // 测试无Token访问需要认证的接口
    @Test
    void testNoToken_ShouldThrowAuthenticationException() throws Exception {
        // 准备
        request.setRequestURI("/api/secure");
        
        Method method = TestController.class.getMethod("secureMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        // 执行和验证 - 只验证异常类型，不验证具体消息
        assertThrows(AuthenticationException.class, 
            () -> authInterceptor.preHandle(request, response, handlerMethod));
    }

    // 测试角色检查
    @Test
    void testRoleCheck_ShouldPassWhenHasRequiredRole() throws Exception {
        // 准备
        request.setRequestURI("/api/manager");
        request.addHeader("DRAGON-TOKEN", "valid-token");
        
        Method method = TestController.class.getMethod("managerMethod");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), method);
        
        when(tokenManager.getUserId("valid-token")).thenReturn("user123");
        when(stpInterface.getRoleList("user123")).thenReturn(Arrays.asList("admin", "manager"));
        
        // 设置redisTemplate的mock行为
        org.springframework.data.redis.core.HashOperations<String, Object, Object> hashOps = 
            mock(org.springframework.data.redis.core.HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), anyString())).thenReturn(null); // Redis中没有缓存
        
        // 执行
        boolean result = authInterceptor.preHandle(request, response, handlerMethod);
        
        // 验证
        assertTrue(result, "用户拥有所需角色时应该通过");
        verify(tokenManager).getUserId("valid-token");
        verify(stpInterface).getRoleList("user123");
    }

    // 测试类用于模拟控制器方法
    static class TestController {
        @DragonIgnore
        public void publicMethod() {}
        
        @DragonCheckPermission(value = {"user:read", "user:write"})
        public void adminMethod() {}
        
        @DragonCheckRole("manager")
        public void managerMethod() {}
        
        public void secureMethod() {}
    }
}