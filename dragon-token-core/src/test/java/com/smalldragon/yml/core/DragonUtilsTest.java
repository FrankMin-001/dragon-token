package com.smalldragon.yml.core;

import com.smalldragon.yml.annotations.DragonCheckMode;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.exceptions.PermissionDeniedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DragonUtils 工具类测试
 * 主要测试所有public方法的正确性
 */
@ExtendWith(MockitoExtension.class)
class DragonUtilsTest {

    @Mock
    private StpInterface stpInterface;

    @InjectMocks
    private DragonUtils dragonUtils;

    private UserContext userContext;

    @BeforeEach
    void setUp() {
        // 创建测试用户上下文
        userContext = new UserContext();
        userContext.setUserId("test-user-123");
        // 设置ThreadLocal上下文
        DragonContextHolder.setContext(userContext);
    }

    @AfterEach
    void tearDown() {
        // 清理ThreadLocal上下文
        DragonContextHolder.clear();
    }

    @Test
    void testCheckPermission_WithValidPermissions_ShouldReturnTrue() {
        // 模拟用户权限数据
        List<String> userPermissions = Arrays.asList("user:read", "user:write", "user:delete");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试AND模式：用户拥有所有权限
        boolean result = dragonUtils.checkPermission("user:read", "user:write");
        
        assertTrue(result);
        verify(stpInterface).getPermissionList("test-user-123");
    }

    @Test
    void testCheckPermission_WithInvalidPermissions_ShouldReturnFalse() {
        // 模拟用户权限数据（缺少user:delete权限）
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试AND模式：用户缺少user:delete权限
        boolean result = dragonUtils.checkPermission("user:read", "user:write", "user:delete");
        
        assertFalse(result);
    }

    @Test
    void testCheckPermission_WithEmptyPermissions_ShouldReturnTrue() {
        // 测试空权限列表
        boolean result = dragonUtils.checkPermission();
        
        assertTrue(result);
        // 空权限列表不应该调用权限查询
        verify(stpInterface, never()).getPermissionList(anyString());
    }

    @Test
    void testCheckPermissionWithMode_ORMode_ShouldReturnTrueWhenAnyPermissionExists() {
        // 模拟用户权限数据
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试OR模式：用户拥有其中一个权限
        boolean result = dragonUtils.checkPermissionWithMode(
            new String[]{"user:read", "user:admin"}, DragonCheckMode.OR);
        
        assertTrue(result);
    }

    @Test
    void testCheckPermissionWithMode_ORMode_ShouldReturnFalseWhenNoPermissionExists() {
        // 模拟用户权限数据
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试OR模式：用户没有任何权限
        boolean result = dragonUtils.checkPermissionWithMode(
            new String[]{"user:admin", "user:super"}, DragonCheckMode.OR);
        
        assertFalse(result);
    }

    @Test
    void testCheckRole_WithValidRoles_ShouldReturnTrue() {
        // 模拟用户角色数据
        List<String> userRoles = Arrays.asList("admin", "manager", "user");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试AND模式：用户拥有所有角色
        boolean result = dragonUtils.checkRole("admin", "manager");
        
        assertTrue(result);
        verify(stpInterface).getRoleList("test-user-123");
    }

    @Test
    void testCheckRole_WithInvalidRoles_ShouldReturnFalse() {
        // 模拟用户角色数据（缺少super角色）
        List<String> userRoles = Arrays.asList("admin", "manager");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试AND模式：用户缺少super角色
        boolean result = dragonUtils.checkRole("admin", "manager", "super");
        
        assertFalse(result);
    }

    @Test
    void testCheckRoleWithMode_ORMode_ShouldReturnTrueWhenAnyRoleExists() {
        // 模拟用户角色数据
        List<String> userRoles = Arrays.asList("admin", "manager");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试OR模式：用户拥有其中一个角色
        boolean result = dragonUtils.checkRoleWithMode(
            new String[]{"admin", "super"}, DragonCheckMode.OR);
        
        assertTrue(result);
    }

    @Test
    void testCheckPermissionOrThrow_WithValidPermissions_ShouldNotThrowException() {
        // 模拟用户权限数据
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试：拥有权限时不应该抛出异常
        assertDoesNotThrow(() -> {
            dragonUtils.checkPermissionOrThrow("user:read", "user:write");
        });
    }

    @Test
    void testCheckPermissionOrThrow_WithInvalidPermissions_ShouldThrowPermissionDeniedException() {
        // 模拟用户权限数据（缺少user:delete权限）
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试：缺少权限时应该抛出异常
        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, () -> {
            dragonUtils.checkPermissionOrThrow("user:read", "user:write", "user:delete");
        });
        
        assertTrue(exception.getMessage().contains("权限不足"));
        assertTrue(exception.getMessage().contains("user:delete"));
    }

    @Test
    void testCheckRoleOrThrow_WithValidRoles_ShouldNotThrowException() {
        // 模拟用户角色数据
        List<String> userRoles = Arrays.asList("admin", "manager");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试：拥有角色时不应该抛出异常
        assertDoesNotThrow(() -> {
            dragonUtils.checkRoleOrThrow("admin", "manager");
        });
    }

    @Test
    void testCheckRoleOrThrow_WithInvalidRoles_ShouldThrowPermissionDeniedException() {
        // 模拟用户角色数据（缺少super角色）
        List<String> userRoles = Arrays.asList("admin", "manager");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试：缺少角色时应该抛出异常
        PermissionDeniedException exception = assertThrows(PermissionDeniedException.class, () -> {
            dragonUtils.checkRoleOrThrow("admin", "manager", "super");
        });
        
        assertTrue(exception.getMessage().contains("角色不足"));
        assertTrue(exception.getMessage().contains("super"));
    }

    @Test
    void testGetCurrentUserPermissions_WithValidContext_ShouldReturnPermissions() {
        // 模拟用户权限数据
        List<String> userPermissions = Arrays.asList("user:read", "user:write");
        when(stpInterface.getPermissionList("test-user-123")).thenReturn(userPermissions);

        // 测试获取当前用户权限
        List<String> result = dragonUtils.getCurrentUserPermissions();
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("user:read"));
        assertTrue(result.contains("user:write"));
    }

    @Test
    void testGetCurrentUserRoles_WithValidContext_ShouldReturnRoles() {
        // 模拟用户角色数据
        List<String> userRoles = Arrays.asList("admin", "manager");
        when(stpInterface.getRoleList("test-user-123")).thenReturn(userRoles);

        // 测试获取当前用户角色
        List<String> result = dragonUtils.getCurrentUserRoles();
        
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("manager"));
    }

    @Test
    void testGetCurrentUserId_WithValidContext_ShouldReturnUserId() {
        // 测试获取当前用户ID
        String result = dragonUtils.getCurrentUserId();
        
        assertNotNull(result);
        assertEquals("test-user-123", result);
    }
}