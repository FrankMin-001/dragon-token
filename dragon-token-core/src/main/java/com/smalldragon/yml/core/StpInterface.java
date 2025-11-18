package com.smalldragon.yml.core;

import java.util.List;

public interface StpInterface {

    List<String> getPermissionList(String userId);

    List<String> getRoleList(String userId);

    /**
     * 获取用户权限列表（支持租户隔离）
     *
     * @param tenantId 租户ID，如果为null则使用传统方式
     * @param userId 用户ID
     * @return 权限列表
     */
    List<String> getPermissionList(String tenantId, String userId);

    /**
     * 获取用户角色列表（支持租户隔离）
     *
     * @param tenantId 租户ID，如果为null则使用传统方式
     * @param userId 用户ID
     * @return 角色列表
     */
    List<String> getRoleList(String tenantId, String userId);

}
