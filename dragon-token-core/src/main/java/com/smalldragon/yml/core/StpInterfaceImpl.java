package com.smalldragon.yml.core;

import com.smalldragon.yml.constants.CacheKeyConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * StpInterface的默认实现类
 * 提供用户权限和角色的查询功能，支持租户隔离
 *
 * @author DragonToken
 * @version 1.0
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取用户权限列表
     *
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(String userId) {
        return getPermissionList(null, userId);
    }

    /**
     * 获取用户角色列表
     *
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<String> getRoleList(String userId) {
        return getRoleList(null, userId);
    }

    /**
     * 获取用户权限列表（支持租户隔离）
     *
     * @param tenantId 租户ID，如果为null则使用传统方式
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(String tenantId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }

        try {
            String cacheKey;
            if (StringUtils.hasText(tenantId)) {
                // 使用租户隔离的缓存键
                cacheKey = CacheKeyConstants.buildUserPermissionsKey(tenantId, userId);
            } else {
                // 向后兼容：使用传统缓存键
                cacheKey = CacheKeyConstants.buildUserPermissionsKeyLegacy(userId);
            }

            Object cachedPermissions = redisTemplate.opsForValue().get(cacheKey);
            if (cachedPermissions != null) {
                return (List<String>) cachedPermissions;
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常，确保系统可用性
            System.err.println("Error getting user permissions: " + e.getMessage());
        }

        // 缓存未命中时返回空列表，实际项目中可以结合数据库查询
        return new ArrayList<>();
    }

    /**
     * 获取用户角色列表（支持租户隔离）
     *
     * @param tenantId 租户ID，如果为null则使用传统方式
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<String> getRoleList(String tenantId, String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }

        try {
            String cacheKey;
            if (StringUtils.hasText(tenantId)) {
                // 使用租户隔离的缓存键
                cacheKey = CacheKeyConstants.buildUserRolesKey(tenantId, userId);
            } else {
                // 向后兼容：使用传统缓存键
                cacheKey = CacheKeyConstants.buildUserRolesKeyLegacy(userId);
            }

            Object cachedRoles = redisTemplate.opsForValue().get(cacheKey);
            if (cachedRoles != null) {
                return (List<String>) cachedRoles;
            }
        } catch (Exception e) {
            // 记录日志但不抛出异常，确保系统可用性
            System.err.println("Error getting user roles: " + e.getMessage());
        }

        // 缓存未命中时返回空列表，实际项目中可以结合数据库查询
        return new ArrayList<>();
    }

    /**
     * 缓存用户权限列表（支持租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param permissions 权限列表
     */
    public void cacheUserPermissions(String tenantId, String userId, List<String> permissions) {
        if (!StringUtils.hasText(userId) || permissions == null) {
            return;
        }

        try {
            String cacheKey;
            if (StringUtils.hasText(tenantId)) {
                // 使用租户隔离的缓存键
                cacheKey = CacheKeyConstants.buildUserPermissionsKey(tenantId, userId);
            } else {
                // 向后兼容：使用传统缓存键
                cacheKey = CacheKeyConstants.buildUserPermissionsKeyLegacy(userId);
            }

            redisTemplate.opsForValue().set(cacheKey, permissions);
        } catch (Exception e) {
            System.err.println("Error caching user permissions: " + e.getMessage());
        }
    }

    /**
     * 缓存用户角色列表（支持租户隔离）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param roles 角色列表
     */
    public void cacheUserRoles(String tenantId, String userId, List<String> roles) {
        if (!StringUtils.hasText(userId) || roles == null) {
            return;
        }

        try {
            String cacheKey;
            if (StringUtils.hasText(tenantId)) {
                // 使用租户隔离的缓存键
                cacheKey = CacheKeyConstants.buildUserRolesKey(tenantId, userId);
            } else {
                // 向后兼容：使用传统缓存键
                cacheKey = CacheKeyConstants.buildUserRolesKeyLegacy(userId);
            }

            redisTemplate.opsForValue().set(cacheKey, roles);
        } catch (Exception e) {
            System.err.println("Error caching user roles: " + e.getMessage());
        }
    }
}