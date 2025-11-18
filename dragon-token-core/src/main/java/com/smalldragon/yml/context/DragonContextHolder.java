package com.smalldragon.yml.context;

/**
 * 龙令牌上下文类 - 线程安全的静态字段访问
 * @Author YML
 * @Date 2025/10/14 11:49
 **/
public class DragonContextHolder {

    // 使用ThreadLocal保证线程安全
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(UserContext context) {
        CONTEXT.set(context);
    }

    // 获取完整上下文
    public static UserContext getContext() {
        return CONTEXT.get();
    }

    // 清除上下文
    public static void clear() {
        CONTEXT.remove();
    }

}
