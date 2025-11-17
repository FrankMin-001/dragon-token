package com.smalldragon.yml.manager;


import com.smalldragon.yml.context.UserContext;
import org.springframework.session.Session;

public interface TokenManager {
    // 为用户创建 token (无状态)
    String createToken(Object userInfo);

    // JWT模式/Session模式
    String createToken(String userId);

    String createToken(String userId,Session session);

    // 验证 token 是否有效
    boolean verifyToken(String token);

    // 验证Session是否有效
    boolean verifySession(String sessionId);

    // 根据 token 获取用户ID
    String getUserId(String token);

    String getUserIdBySession(String sessionId);

    String getUserInfo(String token);

    UserContext getUserInfoById(String userId);

    UserContext getUserInfoBySessionId(String sessionId);

    // 删除 token（登出）
    void removeToken(String token);

    void removeSession(String session);

    // 刷新 token 有效期（可选）
    String refreshTimeoutByToken(String token);

    // 刷新 session 有效期（可选）
    void refreshTimeoutBySession(String sessionId);

    // 检查token是否在黑名单中
    boolean isTokenBlacklisted(String token);

}
