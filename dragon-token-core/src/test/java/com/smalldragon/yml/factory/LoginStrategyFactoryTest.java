package com.smalldragon.yml.factory;

import com.smalldragon.yml.manager.LoginInterface;
import com.smalldragon.yml.manager.impl.JwtLoginImpl;
import com.smalldragon.yml.manager.impl.SessionLoginImpl;
import com.smalldragon.yml.manager.impl.StatelessLoginImpl;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * LoginStrategyFactory 测试类
 * 
 * @author YML
 * @date 2025/01/11
 */
@ExtendWith(MockitoExtension.class)
public class LoginStrategyFactoryTest {
    
    @Mock
    private SessionLoginImpl sessionLogin;
    
    @Mock
    private JwtLoginImpl jwtLogin;
    
    @Mock
    private StatelessLoginImpl statelessLogin;
    
    @Mock
    private DragonTokenProperties dragonTokenProperties;
    
    @InjectMocks
    private LoginStrategyFactory loginStrategyFactory;
    
    @BeforeEach
    void setUp() {
        // Setup mock behavior for DragonTokenProperties
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
    }
    
    @Test
    void testGetLoginStrategy_SessionMode() {
        // 设置SESSION模式
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
        
        // 执行测试
        LoginInterface strategy = loginStrategyFactory.getLoginStrategy();
        
        // 验证结果
        assertSame(sessionLogin, strategy);
        assertTrue(loginStrategyFactory.isSessionMode());
        assertFalse(loginStrategyFactory.isJwtMode());
        assertFalse(loginStrategyFactory.isStatelessMode());
    }
    
    @Test
    void testGetLoginStrategy_JwtMode() {
        // 设置JWT模式
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        
        // 执行测试
        LoginInterface strategy = loginStrategyFactory.getLoginStrategy();
        
        // 验证结果
        assertSame(jwtLogin, strategy);
        assertFalse(loginStrategyFactory.isSessionMode());
        assertTrue(loginStrategyFactory.isJwtMode());
        assertFalse(loginStrategyFactory.isStatelessMode());
    }
    
    @Test
    void testGetLoginStrategy_StatelessMode() {
        // 设置STATELESS模式
        when(dragonTokenProperties.getStrategyType()).thenReturn("STATELESS");
        
        // 执行测试
        LoginInterface strategy = loginStrategyFactory.getLoginStrategy();
        
        // 验证结果
        assertSame(statelessLogin, strategy);
        assertFalse(loginStrategyFactory.isSessionMode());
        assertFalse(loginStrategyFactory.isJwtMode());
        assertTrue(loginStrategyFactory.isStatelessMode());
    }
    
    @Test
    void testGetLoginStrategy_WithSpecificType() {
        // 测试指定策略类型
        LoginInterface jwtStrategy = loginStrategyFactory.getLoginStrategy("JWT");
        LoginInterface sessionStrategy = loginStrategyFactory.getLoginStrategy("SESSION");
        LoginInterface statelessStrategy = loginStrategyFactory.getLoginStrategy("STATELESS");
        
        // 验证结果
        assertSame(jwtLogin, jwtStrategy);
        assertSame(sessionLogin, sessionStrategy);
        assertSame(statelessLogin, statelessStrategy);
    }
    
    @Test
    void testGetLoginStrategy_CaseInsensitive() {
        // 测试大小写不敏感
        LoginInterface strategy1 = loginStrategyFactory.getLoginStrategy("jwt");
        LoginInterface strategy2 = loginStrategyFactory.getLoginStrategy("Jwt");
        LoginInterface strategy3 = loginStrategyFactory.getLoginStrategy("JWT");
        
        // 验证结果
        assertSame(jwtLogin, strategy1);
        assertSame(jwtLogin, strategy2);
        assertSame(jwtLogin, strategy3);
    }
    
    @Test
    void testGetLoginStrategy_NullType() {
        // 测试null类型，应该返回默认的SESSION
        LoginInterface strategy = loginStrategyFactory.getLoginStrategy(null);
        
        // 验证结果
        assertSame(sessionLogin, strategy);
    }
    
    @Test
    void testGetLoginStrategy_UnknownType() {
        // 测试未知类型，应该返回默认的SESSION
        LoginInterface strategy = loginStrategyFactory.getLoginStrategy("UNKNOWN");
        
        // 验证结果
        assertSame(sessionLogin, strategy);
    }
    
    @Test
    void testGetCurrentStrategyType() {
        // 设置不同的策略类型并测试
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        assertEquals("JWT", loginStrategyFactory.getCurrentStrategyType());
        
        when(dragonTokenProperties.getStrategyType()).thenReturn("STATELESS");
        assertEquals("STATELESS", loginStrategyFactory.getCurrentStrategyType());
        
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
        assertEquals("SESSION", loginStrategyFactory.getCurrentStrategyType());
    }
    
    @Test
    void testGetStrategyDescription() {
        // 测试SESSION模式描述
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
        String description = loginStrategyFactory.getStrategyDescription();
        assertTrue(description.contains("SESSION模式"));
        assertTrue(description.contains("hot refresh"));
        
        // 测试JWT模式描述
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        description = loginStrategyFactory.getStrategyDescription();
        assertTrue(description.contains("JWT模式"));
        assertTrue(description.contains("distributed"));
        
        // 测试STATELESS模式描述
        when(dragonTokenProperties.getStrategyType()).thenReturn("STATELESS");
        description = loginStrategyFactory.getStrategyDescription();
        assertTrue(description.contains("STATELESS模式"));
        assertTrue(description.contains("performance"));
        
        // 测试未知类型
        when(dragonTokenProperties.getStrategyType()).thenReturn("UNKNOWN");
        description = loginStrategyFactory.getStrategyDescription();
        assertTrue(description.contains("未知策略类型"));
    }
    
    @Test
    void testIsValidStrategy() {
        // 测试有效策略
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
        assertTrue(loginStrategyFactory.isValidStrategy());
        
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        assertTrue(loginStrategyFactory.isValidStrategy());
        
        when(dragonTokenProperties.getStrategyType()).thenReturn("STATELESS");
        assertTrue(loginStrategyFactory.isValidStrategy());
        
        // 测试无效策略
        when(dragonTokenProperties.getStrategyType()).thenReturn("UNKNOWN");
        assertFalse(loginStrategyFactory.isValidStrategy());
        
        when(dragonTokenProperties.getStrategyType()).thenReturn(null);
        assertFalse(loginStrategyFactory.isValidStrategy());
    }
    
    @Test
    void testModeCheckers() {
        // Test SESSION mode checker
        when(dragonTokenProperties.getStrategyType()).thenReturn("SESSION");
        assertTrue(loginStrategyFactory.isSessionMode());
        assertFalse(loginStrategyFactory.isJwtMode());
        assertFalse(loginStrategyFactory.isStatelessMode());
        
        // Test JWT mode checker
        when(dragonTokenProperties.getStrategyType()).thenReturn("JWT");
        assertFalse(loginStrategyFactory.isSessionMode());
        assertTrue(loginStrategyFactory.isJwtMode());
        assertFalse(loginStrategyFactory.isStatelessMode());
        
        // Test STATELESS mode checker
        when(dragonTokenProperties.getStrategyType()).thenReturn("STATELESS");
        assertFalse(loginStrategyFactory.isSessionMode());
        assertFalse(loginStrategyFactory.isJwtMode());
        assertTrue(loginStrategyFactory.isStatelessMode());
    }
}