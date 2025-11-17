package com.smalldragon.yml.core;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * StpInterface的默认实现类
 * 提供用户权限和角色的查询功能
 * 
 * @author DragonToken
 * @version 1.0
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    /**
     * 获取用户权限列表
     * 
     * @param userId 用户ID
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(String userId) {
        // 默认实现，返回空列表
        // 实际项目中应该从数据库或缓存中查询用户权限
        return new ArrayList<>();
    }

    /**
     * 获取用户角色列表
     * 
     * @param userId 用户ID
     * @return 角色列表
     */
    @Override
    public List<String> getRoleList(String userId) {
        // 默认实现，返回空列表
        // 实际项目中应该从数据库或缓存中查询用户角色
        return new ArrayList<>();
    }
}