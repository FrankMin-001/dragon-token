package com.smalldragon.yml.interceptors;

import com.smalldragon.yml.exceptions.AuthenticationException;
import com.smalldragon.yml.manager.impl.TokenManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AuthInterceptor黑名单功能测试
 * 测试token黑名单检查机制
 */
@ExtendWith(MockitoExtension.class)
class AuthInterceptorBlacklistTest {

    @Mock
    private TokenManagerImpl tokenManager;

    @InjectMocks
    private AuthInterceptor authInterceptor;

    private MockHttpServletRequest request;
    private static final String TEST_TOKEN = "test.jwt.token";
    private static final String BLACKLISTED_TOKEN = "blacklisted.jwt.token";

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
    }

    @Test
    void testValidTokenAccess() throws Exception {
        // 设置有效token
        request.addHeader("DRAGON-TOKEN", TEST_TOKEN);
        
        // Mock token验证通过且不在黑名单
        when(tokenManager.verifyToken(TEST_TOKEN)).thenReturn(true);
        when(tokenManager.isTokenBlacklisted(TEST_TOKEN)).thenReturn(false);

        // 通过反射调用私有方法进行测试
        String result = invokeGetTokenByHeader(TEST_TOKEN);
        
        assertEquals(TEST_TOKEN, result);
        verify(tokenManager).verifyToken(TEST_TOKEN);
        verify(tokenManager).isTokenBlacklisted(TEST_TOKEN);
    }

    @Test
    void testBlacklistedTokenRejected() {
        // 设置黑名单token
        request.addHeader("DRAGON-TOKEN", BLACKLISTED_TOKEN);
        
        // Mock token验证通过但在黑名单中
        when(tokenManager.verifyToken(BLACKLISTED_TOKEN)).thenReturn(true);
        when(tokenManager.isTokenBlacklisted(BLACKLISTED_TOKEN)).thenReturn(true);

        // 应该抛出认证异常 (通过反射调用会被包装在InvocationTargetException中)
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeGetTokenByHeader(BLACKLISTED_TOKEN)
        );
        
        // 验证内部异常是AuthenticationException
        assertTrue(exception.getCause() instanceof AuthenticationException);
        assertTrue(exception.getCause().getMessage().contains("Token已失效"));
        verify(tokenManager).verifyToken(BLACKLISTED_TOKEN);
        verify(tokenManager).isTokenBlacklisted(BLACKLISTED_TOKEN);
    }

    @Test
    void testNullTokenRejected() {
        // 不设置token header
        
        // 应该抛出认证异常 (通过反射调用会被包装在InvocationTargetException中)
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeGetTokenByHeader(null)
        );
        
        // 验证内部异常是AuthenticationException
        assertTrue(exception.getCause() instanceof AuthenticationException);
        assertTrue(exception.getCause().getMessage().contains("未登录或登录已失效"));
        verify(tokenManager, never()).verifyToken(anyString());
        verify(tokenManager, never()).isTokenBlacklisted(anyString());
    }

    @Test
    void testEmptyTokenRejected() {
        // 设置空token
        request.addHeader("DRAGON-TOKEN", "");
        
        // 应该抛出认证异常 (通过反射调用会被包装在InvocationTargetException中)
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeGetTokenByHeader("")
        );
        
        // 验证内部异常是AuthenticationException
        assertTrue(exception.getCause() instanceof AuthenticationException);
        assertTrue(exception.getCause().getMessage().contains("未登录或登录已失效"));
        verify(tokenManager, never()).verifyToken(anyString());
        verify(tokenManager, never()).isTokenBlacklisted(anyString());
    }

    @Test
    void testInvalidTokenRejected() {
        String invalidToken = "invalid.token";
        request.addHeader("DRAGON-TOKEN", invalidToken);
        
        // Mock token验证失败
        when(tokenManager.verifyToken(invalidToken)).thenThrow(new RuntimeException("Invalid token"));

        // 应该抛出认证异常 (通过反射调用会被包装在InvocationTargetException中)
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeGetTokenByHeader(invalidToken)
        );
        
        // 验证内部异常是AuthenticationException
        assertTrue(exception.getCause() instanceof AuthenticationException);
        verify(tokenManager).verifyToken(invalidToken);
        // 验证失败时不应该检查黑名单
        verify(tokenManager, never()).isTokenBlacklisted(anyString());
    }

    @Test
    void testBlacklistCheckException() {
        // 设置token
        request.addHeader("DRAGON-TOKEN", TEST_TOKEN);
        
        // Mock token验证通过但黑名单检查异常
        when(tokenManager.verifyToken(TEST_TOKEN)).thenReturn(true);
        when(tokenManager.isTokenBlacklisted(TEST_TOKEN)).thenThrow(new RuntimeException("Redis error"));

        // 应该抛出认证异常 (通过反射调用会被包装在InvocationTargetException中)
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> invokeGetTokenByHeader(TEST_TOKEN)
        );
        
        // 验证内部异常是AuthenticationException
        assertTrue(exception.getCause() instanceof AuthenticationException);
        verify(tokenManager).verifyToken(TEST_TOKEN);
        verify(tokenManager).isTokenBlacklisted(TEST_TOKEN);
    }

    @Test
    void testNonTokenManagerImplInstance() throws Exception {
        // 使用非TokenManagerImpl实例的mock
        com.smalldragon.yml.manager.TokenManager genericTokenManager = mock(com.smalldragon.yml.manager.TokenManager.class);
        
        // 通过反射设置tokenManager字段
        java.lang.reflect.Field field = AuthInterceptor.class.getDeclaredField("tokenManager");
        field.setAccessible(true);
        field.set(authInterceptor, genericTokenManager);
        
        request.addHeader("DRAGON-TOKEN", TEST_TOKEN);
        when(genericTokenManager.verifyToken(TEST_TOKEN)).thenReturn(true);

        // 应该正常通过，不进行黑名单检查
        String result = invokeGetTokenByHeader(TEST_TOKEN);
        
        assertEquals(TEST_TOKEN, result);
        verify(genericTokenManager).verifyToken(TEST_TOKEN);
        // 不应该调用黑名单检查方法
        verify(tokenManager, never()).isTokenBlacklisted(anyString());
    }

    /**
     * 通过反射调用私有的getTokenByHeader方法
     */
    private String invokeGetTokenByHeader(String token) throws Exception {
        if (token != null) {
            request.addHeader("DRAGON-TOKEN", token);
        }
        
        java.lang.reflect.Method method = AuthInterceptor.class.getDeclaredMethod("getTokenByHeader", 
            javax.servlet.http.HttpServletRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(authInterceptor, request);
    }
}