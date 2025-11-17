package com.smalldragon.yml.utils;

import com.alibaba.fastjson.JSONObject;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.ValidationUtils;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;

/**
 * Session工具类
 * 基于Spring Session Redis实现Session管理
 * Session会自动存储到Redis中
 *
 * @Author YML
 * @Date 2025/9/26
 */
@Component
public class SessionUtil {

    @Resource
    private SessionRepository sessionRepository;
    
    @Resource
    private DragonTokenProperties dragonTokenProperties;

    /**
     * 生成Session并返回Session ID（使用默认过期时间）
     * Session会自动存储到Redis中，客户端使用返回的Session ID进行后续操作
     *
     * @param userId 用户ID
     * @return Session ID，客户端可以使用此ID进行Session相关操作
     */
    public  String generateSession(String userId,Session session) {
        return generateSession(userId,dragonTokenProperties.getRetentionTime(),session);
    }

    /**
     * 生成Session并返回Session ID（指定过期时间）
     * Session会自动存储到Redis中，客户端使用返回的Session ID进行后续操作
     *
     * @param userId 用户ID
     * @param expirationTime 过期时间（毫秒）
     * @return Session ID，客户端可以使用此ID进行Session相关操作
     */
    public  String generateSession(String userId, long expirationTime,Session session) {
        ValidationUtils.requireNonEmptyString(userId, "用户ID不能为空");

        ValidationUtils.requireNonNull(session, "SessionRepository未初始化");

        session.setAttribute("userId", userId);
        session.setMaxInactiveInterval(Duration.ofDays(dragonTokenProperties.getRetentionTime()));

        // 返回Session ID给客户端，客户端后续使用此ID进行Session操作
        return session.getId();
    }

    /**
     * 根据Session ID获取用户ID
     * 客户端使用generateSession返回的Session ID来获取用户信息
     *
     * @param sessionId Session ID（由generateSession方法返回）
     * @return 用户ID，如果Session不存在或已过期则返回null
     */
    public String getUserIdBySession(String sessionId) {
        if (ValidationUtils.isEmpty(sessionId)) {
            return null;
        }
        Session session = sessionRepository.findById(sessionId);
        return (session != null && !session.isExpired()) ? session.getAttribute("userId") : null;
    }

    /**
     * 根据Session ID获取 userInfo
     * 客户端使用generateSession返回的Session ID来获取用户信息
     *
     * @param sessionId Session ID（由generateSession方法返回）
     * @return 用户ID，如果Session不存在或已过期则返回null
     */
    public UserContext getUserInfoBySession(String sessionId) {
        if (ValidationUtils.isEmpty(sessionId)) {
            return null;
        }
        Session session = sessionRepository.findById(sessionId);
        return (session != null && !session.isExpired()) ?
                JSONObject.parseObject(session.getAttribute(DragonTokenProperties.USER_INFO), UserContext.class):
                null;
    }


    /**
     * 判断Session是否过期
     * 客户端使用Session ID检查Session状态
     *
     * @param sessionId Session ID（由generateSession方法返回）
     * @return true: 已过期或不存在, false: 有效
     */
    public  boolean isSessionExpired(String sessionId) {
        if (ValidationUtils.isEmpty(sessionId)) {
            return true;
        }

        ValidationUtils.requireNonNull(sessionRepository, "SessionRepository未初始化");

        Session session = sessionRepository.findById(sessionId);
        return session == null || session.isExpired();
    }

    /**
     * 设置Session过期状态
     * 客户端使用Session ID来管理Session过期状态
     *
     * @param sessionId Session ID（由generateSession方法返回）
     * @return true: 设置成功, false: Session不存在
     */
    public  boolean setSessionExpired(String sessionId, boolean expired) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return false;
        }

        if (sessionRepository == null) {
            throw new IllegalStateException("SessionRepository未初始化");
        }

        if (expired) {
            // 删除Session（立即过期）
            sessionRepository.deleteById(sessionId);
            return true;
        } else {
            // 重新激活Session
            Session session = sessionRepository.findById(sessionId);
            if (session != null) {
                session.setLastAccessedTime(Instant.ofEpochSecond(dragonTokenProperties.getRetentionTime()));
                return true;
            }
            return false;
        }
    }

    /**
     * 强制使Session过期
     * 客户端使用Session ID来使Session立即过期
     *
     * @param sessionId Session ID（由generateSession方法返回）
     * @return true: 设置成功, false: Session不存在
     */
    public  boolean expireSession(String sessionId) {
        return setSessionExpired(sessionId, true);
    }

    /**
     * 获取Session创建时间
     * 从Redis中获取Session信息
     *
     * @param sessionId Session ID
     * @return 创建时间（毫秒），-1表示Session不存在
     */
    public  long getSessionCreateTime(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return -1;
        }

        if (sessionRepository == null) {
            throw new IllegalStateException("SessionRepository未初始化");
        }

        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            return -1;
        }

        Long createTime = session.getAttribute("createTime");
        return createTime != null ? createTime : session.getCreationTime().toEpochMilli();
    }

    /**
     * 获取Session剩余有效时间
     * 从Redis中计算剩余时间
     *
     * @param sessionId Session ID
     * @return 剩余时间（毫秒），-1表示Session不存在，0表示已过期
     */
    public  long getSessionRemainingTime(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return -1;
        }

        if (sessionRepository == null) {
            throw new IllegalStateException("SessionRepository未初始化");
        }

        Session session = sessionRepository.findById(sessionId);
        if (session == null) {
            return -1;
        }

        if (session.isExpired()) {
            return 0;
        }

        long lastAccessedTime = session.getLastAccessedTime().toEpochMilli();
        long maxInactiveInterval = session.getMaxInactiveInterval().getSeconds() * 1000;
        return (lastAccessedTime + maxInactiveInterval) - System.currentTimeMillis();
    }

    /**
     * 设置Session属性
     * 属性会自动存储到Redis中
     *
     * @param sessionId Session ID
     * @param attributeName 属性名
     * @param attributeValue 属性值
     * @return true: 设置成功, false: Session不存在
     */
    public  boolean setSessionAttribute(String sessionId, String attributeName, Object attributeValue) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("sessionId为空或不存在!");
        }

        if (sessionRepository == null) {
            throw new IllegalStateException("SessionRepository未初始化");
        }

        Session session = sessionRepository.findById(sessionId);
        if (session == null || session.isExpired()) {
            throw new RuntimeException("SessionId已过期或者不存在");
        }

        session.setAttribute(attributeName, attributeValue);
        return true;
    }

    /**
     * 获取Session属性
     * 从Redis中获取属性值
     *
     * @param sessionId Session ID
     * @param attributeName 属性名
     * @return 属性值，Session不存在或属性不存在时返回null
     */
    public  Object getSessionAttribute(String sessionId, String attributeName) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new RuntimeException("sessionId为空或不存在!");
        }

        if (sessionRepository == null) {
            throw new IllegalStateException("SessionRepository未初始化");
        }

        Session session = sessionRepository.findById(sessionId);
        return (session != null && !session.isExpired()) ? session.getAttribute(attributeName) : null;
    }

}
