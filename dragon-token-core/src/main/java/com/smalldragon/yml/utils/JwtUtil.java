package com.smalldragon.yml.utils;

import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT工具类
 * 提供JWT令牌的生成、验证和解析功能
 *
 * @Author YML
 * @Date 2025/9/26
 */
@Component
public class JwtUtil {

    // 签发者
    private static final String ISSUER = "dragon-token-system";

    @Autowired
    private DragonTokenProperties dragonTokenProperties;

    /**
     * 通过秘钥生成Token(默认走 DragonTokenProperties.retentionTime)
     *
     * @param userId 登录ID
     * @return JWT令牌字符串
     */
    public String generateTokenByLoginId(String userId) {
        return generateTokenByLoginId(userId, dragonTokenProperties.getRetentionTime());
    }

    /**
     * expirationTime 为毫秒,有效期默认为  当前时间 + expirationTime (如果 expirationTime <= 0 默认2小时)
     * @param expirationTime 过期时间（毫秒）
     * @return JWT令牌字符串
     */
    public String generateTokenByLoginId(String userId, Long expirationTime) {
        try {
            Date expireDate = new Date(System.currentTimeMillis() + expirationTime);
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());

            return JWT.create()
                    .withIssuer(ISSUER)
                    .withIssuedAt(new Date())
                    .withExpiresAt(expireDate)
                    .withClaim("userId", userId)
                    .sign(algorithm);
        } catch (Exception e) {
            throw new RuntimeException("生成JWT令牌失败", e);
        }
    }

    /**
     * 用于 JWT_STATELESS 的通过秘钥生成TOKEN(默认走 DragonTokenProperties.retentionTime)
     *
     * @param userInfo 令牌信息
     * @return JWT令牌字符串
     */
    public String generateTokenByStateless(String userInfo) {
        return generateTokenByStateless(userInfo, dragonTokenProperties.getRetentionTime());
    }

    /**
     * expirationTime 为毫秒,有效期默认为  当前时间 + expirationTime
     * (如果 expirationTime <= 0 默认走 DragonTokenProperties.retentionTime)
     *
     * @param userInfo 令牌信息
     * @param expirationTime 过期时间（毫秒）
     * @return JWT令牌字符串
     */
    public String generateTokenByStateless(String userInfo, Long expirationTime) {
        try {
            Date expireDate = new Date(System.currentTimeMillis() + expirationTime);
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());

            // 无状态直接把用户信息包裹到 TOKEN 中
            //String loginInfoJSON = JSONObject.toJSONString(userInfo);
            return JWT.create()
                    .withIssuer(ISSUER)
                    .withIssuedAt(new Date())
                    .withExpiresAt(expireDate)
                    .withClaim(DragonTokenProperties.USER_INFO,userInfo)
                    .sign(algorithm);
        } catch (Exception e) {
            throw new RuntimeException("生成JWT令牌失败", e);
        }
    }

    /**
     * 通过秘钥解析TOKEN 获取 loginId
     *
     * @param token JWT令牌
     * @return loginId
     */
    public String getLoginIdByParseToken(String token) {
        try {
            // 解析 token
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);
            return jwt.getClaim("userId").asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 通过秘钥解析TOKEN 获取 userInfo
     *
     * @param token JWT令牌
     * @return LoginInfo对象
     */
    public String getLoginInfoByParseToken(String token) {
        try {
            // 解析 token
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);

            // 转成我存储的类信息
            return jwt.getClaim(DragonTokenProperties.USER_INFO).asString();
        } catch (JWTVerificationException e) {
            throw new RuntimeException("JWT令牌验证失败", e);
        } catch (Exception e) {
            throw new RuntimeException("解析JWT令牌失败", e);
        }
    }

    /**
     * 校验TOKEN是否有效
     *
     * @param token JWT令牌
     * @return 是否有效
     */
    public Boolean validateToken(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("验证JWT令牌失败,请重新登录!", e);
        }
    }

    /**
     * 获取TOKEN剩余有效时间 (分钟)
     *
     * @param token JWT令牌
     * @return 剩余有效时间（分钟），-1表示TOKEN无效或解析失败，0表示已过期
     */
    public long getTokenRemainingTime(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(dragonTokenProperties.getPublicKey());
            JWTVerifier verifier = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build();
            DecodedJWT jwt = verifier.verify(token);

            Date expiresAt = jwt.getExpiresAt();
            if (expiresAt == null) {
                return -1;
            }

            long remainingTimeMillis = expiresAt.getTime() - System.currentTimeMillis();
            long remainingTimeMinutes = remainingTimeMillis / (60 * 1000); // 转换为分钟
            return Math.max(remainingTimeMinutes, 0); // 如果已过期返回0
        } catch (JWTVerificationException e) {
            return -1; // TOKEN无效
        } catch (Exception e) {
            throw new RuntimeException("获取TOKEN剩余时间失败", e);
        }
    }

    /**
     * 检查TOKEN是否即将过期（在指定时间内过期）
     *
     * @param token JWT令牌
     * @return true: 即将过期，false: 未过期或还有较长时间
     */
    public boolean isTokenAboutToExpire(String token) {
        long remainingTime = getTokenRemainingTime(token);
        if (remainingTime <= 0) {
            return true; // 已过期或无效
        }
        // 默认阀值为 5 分钟
        return remainingTime <= 5;
    }

}
