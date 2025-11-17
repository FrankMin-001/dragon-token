package com.smalldragon.yml.annotations;

/**
 * 权限校验模式
 */
public enum DragonCheckMode {

    /**
     * 必须具有所有权限
     */
    AND,

    /**
     * 只需具有其中一个权限
     */
    OR
}
