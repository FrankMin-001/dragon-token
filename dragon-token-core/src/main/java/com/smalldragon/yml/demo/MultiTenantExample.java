package com.smalldragon.yml.demo;

import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.core.DragonUtils;
import com.smalldragon.yml.core.StpInterfaceImpl;
import com.smalldragon.yml.manager.impl.TokenManagerImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 多租户使用示例
 * 展示如何在DragonToken框架中使用多租户功能
 *
 * @author DragonToken
 * @version 1.0
 */
@Component
public class MultiTenantExample {

    @Autowired
    private TokenManagerImpl tokenManager;

    @Autowired
    private StpInterfaceImpl stpInterface;

    @Autowired
    private DragonUtils dragonUtils;

    /**
     * 模拟多租户用户登录
     */
    public void simulateMultiTenantLogin() {
        System.out.println("=== 多租户登录示例 ===");

        // 租户A的用户登录
        UserContext userA = new UserContext();
        userA.setUserId("user001");
        userA.setUsername("alice");
        userA.setRole("ADMIN");
        userA.setTenantId("tenant_a");  // 设置租户ID
        userA.setClientIp("192.168.1.100");

        // 缓存用户信息（自动支持租户隔离）
        tokenManager.cacheUserInfo(userA);

        // 租户B的用户登录
        UserContext userB = new UserContext();
        userB.setUserId("user001");  // 相同的用户ID，但不同租户
        userB.setUsername("bob");
        userB.setRole("USER");
        userB.setTenantId("tenant_b");  // 不同的租户ID
        userB.setClientIp("192.168.1.101");

        // 缓存用户信息（自动支持租户隔离）
        tokenManager.cacheUserInfo(userB);

        System.out.println("多租户用户登录完成，相同用户ID在不同租户下被正确隔离");
    }

    /**
     * 模拟多租户权限和角色缓存
     */
    public void simulateMultiTenantPermissions() {
        System.out.println("\n=== 多租户权限示例 ===");

        // 为租户A的用户设置权限
        List<String> permissionsA = Arrays.asList("user:read", "user:write", "admin:access");
        stpInterface.cacheUserPermissions("tenant_a", "user001", permissionsA);

        List<String> rolesA = Arrays.asList("ADMIN", "MANAGER");
        stpInterface.cacheUserRoles("tenant_a", "user001", rolesA);

        // 为租户B的用户设置权限（相同用户ID，不同权限）
        List<String> permissionsB = Arrays.asList("user:read");  // 租户B只有读权限
        stpInterface.cacheUserPermissions("tenant_b", "user001", permissionsB);

        List<String> rolesB = Arrays.asList("USER");  // 租户B只有普通用户角色
        stpInterface.cacheUserRoles("tenant_b", "user001", rolesB);

        System.out.println("多租户权限设置完成，相同用户ID在不同租户下拥有不同的权限和角色");
    }

    /**
     * 演示租户隔离的数据访问
     */
    public void demonstrateTenantIsolation() {
        System.out.println("\n=== 租户隔离演示 ===");

        // 测试租户A的用户信息获取
        UserContext userFromTenantA = tokenManager.getUserInfoById("tenant_a", "user001");
        if (userFromTenantA != null) {
            System.out.println("租户A用户: " + userFromTenantA.getUsername() +
                ", 角色: " + userFromTenantA.getRole() +
                ", 租户ID: " + userFromTenantA.getTenantId());
        }

        // 测试租户B的用户信息获取
        UserContext userFromTenantB = tokenManager.getUserInfoById("tenant_b", "user001");
        if (userFromTenantB != null) {
            System.out.println("租户B用户: " + userFromTenantB.getUsername() +
                ", 角色: " + userFromTenantB.getRole() +
                ", 租户ID: " + userFromTenantB.getTenantId());
        }

        // 测试权限隔离
        List<String> permissionsA = stpInterface.getPermissionList("tenant_a", "user001");
        System.out.println("租户A用户权限: " + permissionsA);

        List<String> permissionsB = stpInterface.getPermissionList("tenant_b", "user001");
        System.out.println("租户B用户权限: " + permissionsB);

        // 测试角色隔离
        List<String> rolesA = stpInterface.getRoleList("tenant_a", "user001");
        System.out.println("租户A用户角色: " + rolesA);

        List<String> rolesB = stpInterface.getRoleList("tenant_b", "user001");
        System.out.println("租户B用户角色: " + rolesB);
    }

    /**
     * 演示编程式权限检查
     */
    public void demonstratePermissionCheck() {
        System.out.println("\n=== 权限检查示例 ===");

        // 注意：在实际应用中，DragonContextHolder会由拦截器自动设置
        // 这里我们手动设置来演示功能

        // 模拟租户A的权限检查
        System.out.println("租户A用户权限检查:");
        System.out.println("- 检查读权限: " + dragonUtils.checkPermission("user:read"));
        System.out.println("- 检查写权限: " + dragonUtils.checkPermission("user:write"));
        System.out.println("- 检查管理员权限: " + dragonUtils.checkPermission("admin:access"));

        // 模拟租户B的权限检查
        System.out.println("租户B用户权限检查:");
        System.out.println("- 检查读权限: " + dragonUtils.checkPermission("user:read"));
        System.out.println("- 检查写权限: " + dragonUtils.checkPermission("user:write"));
        System.out.println("- 检查管理员权限: " + dragonUtils.checkPermission("admin:access"));
    }

    /**
     * 演示缓存键格式
     */
    public void demonstrateCacheKeyFormat() {
        System.out.println("\n=== 缓存键格式示例 ===");
        com.smalldragon.yml.constants.CacheKeyConstants constants =
            new com.smalldragon.yml.constants.CacheKeyConstants();

        // 展示租户隔离的缓存键格式
        String userCacheKey = com.smalldragon.yml.constants.CacheKeyConstants.buildUserCacheKey("tenant_a", "user001");
        System.out.println("租户隔离的用户缓存键: " + userCacheKey);

        String permissionCacheKey = com.smalldragon.yml.constants.CacheKeyConstants.buildUserPermissionsKey("tenant_a", "user001");
        System.out.println("租户隔离的权限缓存键: " + permissionCacheKey);

        String roleCacheKey = com.smalldragon.yml.constants.CacheKeyConstants.buildUserRolesKey("tenant_a", "user001");
        System.out.println("租户隔离的角色缓存键: " + roleCacheKey);

        // 对比传统缓存键格式
        String legacyUserCacheKey = com.smalldragon.yml.constants.CacheKeyConstants.buildUserCacheKeyLegacy("user001");
        System.out.println("传统用户缓存键: " + legacyUserCacheKey);
    }

    /**
     * 运行完整的示例
     */
    public void runExample() {
        simulateMultiTenantLogin();
        simulateMultiTenantPermissions();
        demonstrateTenantIsolation();
        demonstrateCacheKeyFormat();

        System.out.println("\n=== 多租户示例运行完成 ===");
        System.out.println("主要特性:");
        System.out.println("1. 数据隔离：相同用户ID在不同租户下完全隔离");
        System.out.println("2. 权限隔离：不同租户的相同用户可以拥有不同权限");
        System.out.println("3. 缓存隔离：Redis缓存键包含租户信息，确保数据不会混淆");
        System.out.println("4. 向后兼容：支持传统存储方式，平滑升级");
        System.out.println("5. API兼容：新增方法支持租户参数，原有方法保持兼容");
    }
}