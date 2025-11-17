package com.smalldragon.yml.manager;

import com.smalldragon.yml.context.UserContext;

/**
 * 登录接口
 * 提供统一的登录、登出和用户信息管理功能
 * 
 * @author YML
 * @date 2025/9/23 18:00
 */
public interface LoginInterface {

    /**
     * 用户登录
     * 
     * @param loginInfo 登录信息（用户ID或其他标识）
     * @return 用户上下文信息，包含token等
     */
    UserContext login(String loginInfo);

    /**
     * 用户登录（带用户信息）
     * 
     * @param userContext 完整的用户上下文信息
     * @return 登录后的用户上下文信息，包含token等
     */
    UserContext login(UserContext userContext);

    /**
     * 设置用户信息到缓存
     * 
     * @param userInfo 用户信息
     */
    void setUserInfo(UserContext userInfo);

    /**
     * 获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户上下文信息
     */
    UserContext getUserInfo(String userId);

    /**
     * 用户登出
     */
    void loginOut();

    /**
     * 用户登出（指定用户ID）
     * 
     * @param userId 用户ID
     */
    void loginOut(String userId);

    /**
     * 检查用户是否已登录
     * 
     * @param userId 用户ID
     * @return 是否已登录
     */
    boolean isLoggedIn(String userId);

    /**
     * 刷新用户token（如果支持）
     * 
     * @param token 当前token
     * @return 新的token，如果不支持刷新则返回原token
     */
    String refreshToken(String token);

    /**
     * 获取token剩余有效时间（秒）
     * 
     * @param token token字符串
     * @return 剩余时间（秒），-1表示永不过期，0表示已过期
     */
    long getTokenRemainingTime(String token);
}
