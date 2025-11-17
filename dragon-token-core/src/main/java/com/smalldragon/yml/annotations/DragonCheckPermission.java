package com.smalldragon.yml.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义权限检查注解
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface DragonCheckPermission {

    /**
     * 需要校验的权限码
     */
    String[] value() default {};

    /**
     * 校验模式：AND-必须具有所有权限，OR-具有任意一个权限即可
     */
    DragonCheckMode mode() default DragonCheckMode.AND;

}
