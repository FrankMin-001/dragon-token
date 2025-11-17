package com.smalldragon.yml.utils;

import cn.hutool.core.util.ObjectUtil;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * 验证工具类
 * 提供统一的验证方法，减少重复的验证代码
 * 
 * @author smalldragon
 */
public class ValidationUtils {

    /**
     * 检查字符串是否为空或null
     * 
     * @param str 要检查的字符串
     * @return 如果为空或null返回true
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 检查字符串是否不为空且不为null
     * 
     * @param str 要检查的字符串
     * @return 如果不为空且不为null返回true
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 检查字符串是否有文本内容（Spring的StringUtils.hasText的包装）
     * 
     * @param str 要检查的字符串
     * @return 如果有文本内容返回true
     */
    public static boolean hasText(String str) {
        return StringUtils.hasText(str);
    }

    /**
     * 检查对象是否为null
     * 
     * @param obj 要检查的对象
     * @return 如果为null返回true
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * 检查对象是否不为null
     * 
     * @param obj 要检查的对象
     * @return 如果不为null返回true
     */
    public static boolean isNotNull(Object obj) {
        return obj != null;
    }

    /**
     * 检查对象是否为空（使用HuTool的ObjectUtil.isEmpty）
     * 
     * @param obj 要检查的对象
     * @return 如果为空返回true
     */
    public static boolean isObjectEmpty(Object obj) {
        return ObjectUtil.isEmpty(obj);
    }

    /**
     * 检查对象是否不为空（使用HuTool的ObjectUtil.isEmpty）
     * 
     * @param obj 要检查的对象
     * @return 如果不为空返回true
     */
    public static boolean isObjectNotEmpty(Object obj) {
        return !ObjectUtil.isEmpty(obj);
    }

    /**
     * 检查集合是否为空或null
     * 
     * @param collection 要检查的集合
     * @return 如果为空或null返回true
     */
    public static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 检查集合是否不为空且不为null
     * 
     * @param collection 要检查的集合
     * @return 如果不为空且不为null返回true
     */
    public static boolean isCollectionNotEmpty(Collection<?> collection) {
        return !isCollectionEmpty(collection);
    }

    /**
     * 检查数组是否为空或null
     * 
     * @param array 要检查的数组
     * @return 如果为空或null返回true
     */
    public static boolean isArrayEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 检查数组是否不为空且不为null
     * 
     * @param array 要检查的数组
     * @return 如果不为空且不为null返回true
     */
    public static boolean isArrayNotEmpty(Object[] array) {
        return !isArrayEmpty(array);
    }

    /**
     * 验证必需的字符串参数
     * 
     * @param value 参数值
     * @param paramName 参数名称
     * @throws IllegalArgumentException 如果参数为空或null
     */
    public static void requireNonEmptyString(String value, String paramName) {
        if (isEmpty(value)) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }

    /**
     * 验证必需的对象参数
     * 
     * @param value 参数值
     * @param paramName 参数名称
     * @throws IllegalArgumentException 如果参数为null
     */
    public static void requireNonNull(Object value, String paramName) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
    }

    /**
     * 验证必需的集合参数
     * 
     * @param collection 集合参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 如果集合为空或null
     */
    public static void requireNonEmptyCollection(Collection<?> collection, String paramName) {
        if (isCollectionEmpty(collection)) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }

    /**
     * 验证必需的数组参数
     * 
     * @param array 数组参数
     * @param paramName 参数名称
     * @throws IllegalArgumentException 如果数组为空或null
     */
    public static void requireNonEmptyArray(Object[] array, String paramName) {
        if (isArrayEmpty(array)) {
            throw new IllegalArgumentException(paramName + " cannot be null or empty");
        }
    }

    /**
     * 安全地获取字符串，如果为null则返回默认值
     * 
     * @param str 原字符串
     * @param defaultValue 默认值
     * @return 原字符串或默认值
     */
    public static String defaultIfEmpty(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * 安全地获取对象，如果为null则返回默认值
     * 
     * @param <T> 对象类型
     * @param obj 原对象
     * @param defaultValue 默认值
     * @return 原对象或默认值
     */
    public static <T> T defaultIfNull(T obj, T defaultValue) {
        return obj != null ? obj : defaultValue;
    }
}