package com.smalldragon.yml.annotations;

import java.lang.annotation.*;

/**
 * 免登录注解
 * 被注解的方法或类将跳过登录验证
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DragonIgnore {

    /**
     * 是否完全忽略认证（包括登录和权限检查）
     * @return 默认true，只忽略登录检查
     */
    boolean ignoreAll() default false;

}
