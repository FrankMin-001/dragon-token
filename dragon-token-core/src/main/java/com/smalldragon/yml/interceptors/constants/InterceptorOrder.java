package com.smalldragon.yml.interceptors.constants;

/**
 * 拦截器执行顺序常量
 */
public class InterceptorOrder {
    public static final int AUTHENTICATION = 200;       // 登录认证 + 权限鉴定
    public static final int LOGGING = 300;              // 日志最后
}
