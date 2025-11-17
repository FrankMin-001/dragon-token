package com.smalldragon.yml.constants;

/**
 * 缓存键常量类
 * 统一管理所有Redis缓存键的命名规范
 * 
 * @author smalldragon
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
        // 工具类，禁止实例化
    }

    /**
     * 键分隔符
     */
    public static final String KEY_SEPARATOR = ":";

    /**
     * 项目前缀
     */
    public static final String PROJECT_PREFIX = "dragon-token";

    /**
     * JWT相关键前缀
     */
    public static final String JWT_PREFIX = PROJECT_PREFIX + KEY_SEPARATOR + "jwt";

    /**
     * Session相关键前缀
     */
    public static final String SESSION_PREFIX = PROJECT_PREFIX + KEY_SEPARATOR + "session";

    /**
     * 用户相关键前缀
     */
    public static final String USER_PREFIX = PROJECT_PREFIX + KEY_SEPARATOR + "user";

    /**
     * 黑名单相关键前缀
     */
    public static final String BLACKLIST_PREFIX = PROJECT_PREFIX + KEY_SEPARATOR + "blacklist";

    /**
     * JWT黑名单键
     * 格式: dragon-token:blacklist:jwt:{token}
     */
    public static final String JWT_BLACKLIST_KEY = BLACKLIST_PREFIX + KEY_SEPARATOR + "jwt" + KEY_SEPARATOR;

    /**
     * Stateless黑名单键
     * 格式: dragon-token:blacklist:stateless:{token}
     */
    public static final String STATELESS_BLACKLIST_KEY = BLACKLIST_PREFIX + KEY_SEPARATOR + "stateless" + KEY_SEPARATOR;

    /**
     * 用户缓存键
     * 格式: dragon-token:user:cache:{userId}
     */
    public static final String USER_CACHE_KEY = USER_PREFIX + KEY_SEPARATOR + "cache" + KEY_SEPARATOR;

    /**
     * Session存储键
     * 格式: dragon-token:session:{sessionId}
     */
    public static final String SESSION_STORAGE_KEY = SESSION_PREFIX + KEY_SEPARATOR;

    /**
     * 用户权限缓存键
     * 格式: dragon-token:user:permissions:{userId}
     */
    public static final String USER_PERMISSIONS_KEY = USER_PREFIX + KEY_SEPARATOR + "permissions" + KEY_SEPARATOR;

    /**
     * 用户角色缓存键
     * 格式: dragon-token:user:roles:{userId}
     */
    public static final String USER_ROLES_KEY = USER_PREFIX + KEY_SEPARATOR + "roles" + KEY_SEPARATOR;

    /**
     * 登录失败计数键
     * 格式: dragon-token:user:login-fail:{userId}
     */
    public static final String LOGIN_FAIL_COUNT_KEY = USER_PREFIX + KEY_SEPARATOR + "login-fail" + KEY_SEPARATOR;

    /**
     * 构建JWT黑名单键
     * @param token JWT token
     * @return 完整的Redis键
     */
    public static String buildJwtBlacklistKey(String token) {
        return JWT_BLACKLIST_KEY + token;
    }

    /**
     * 构建Stateless黑名单键
     * @param token Stateless token
     * @return 完整的Redis键
     */
    public static String buildStatelessBlacklistKey(String token) {
        return STATELESS_BLACKLIST_KEY + token;
    }

    /**
     * 构建用户缓存键
     * @param userId 用户ID
     * @return 完整的Redis键
     */
    public static String buildUserCacheKey(String userId) {
        return USER_CACHE_KEY + userId;
    }

    /**
     * 构建Session存储键
     * @param sessionId Session ID
     * @return 完整的Redis键
     */
    public static String buildSessionStorageKey(String sessionId) {
        return SESSION_STORAGE_KEY + sessionId;
    }

    /**
     * 构建用户权限缓存键
     * @param userId 用户ID
     * @return 完整的Redis键
     */
    public static String buildUserPermissionsKey(String userId) {
        return USER_PERMISSIONS_KEY + userId;
    }

    /**
     * 构建用户角色缓存键
     * @param userId 用户ID
     * @return 完整的Redis键
     */
    public static String buildUserRolesKey(String userId) {
        return USER_ROLES_KEY + userId;
    }

    /**
     * 构建登录失败计数键
     * @param userId 用户ID
     * @return 完整的Redis键
     */
    public static String buildLoginFailCountKey(String userId) {
        return LOGIN_FAIL_COUNT_KEY + userId;
    }

    /**
     * 验证键是否符合项目规范
     * @param key Redis键
     * @return 是否符合规范
     */
    public static boolean isValidKey(String key) {
        return key != null && key.startsWith(PROJECT_PREFIX + KEY_SEPARATOR);
    }

    /**
     * 从键中提取实体ID
     * @param key Redis键
     * @param keyPrefix 键前缀
     * @return 实体ID
     */
    public static String extractIdFromKey(String key, String keyPrefix) {
        if (key != null && key.startsWith(keyPrefix)) {
            return key.substring(keyPrefix.length());
        }
        return null;
    }
}