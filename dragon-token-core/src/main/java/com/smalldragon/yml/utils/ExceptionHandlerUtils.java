package com.smalldragon.yml.utils;

import org.slf4j.Logger;

/**
 * 异常处理工具类
 * 提供统一的异常处理方法，减少重复代码
 * 
 * @author smalldragon
 */
public class ExceptionHandlerUtils {

    /**
     * 执行操作并处理异常
     * 
     * @param operation 要执行的操作
     * @param logger 日志记录器
     * @param errorMessage 错误消息
     */
    public static void executeWithExceptionHandling(Runnable operation, Logger logger, String errorMessage) {
        try {
            operation.run();
        } catch (Exception e) {
            logger.error(errorMessage + ": {}", e.getMessage(), e);
        }
    }

    /**
     * 执行操作并处理异常，支持返回值
     * 
     * @param <T> 返回值类型
     * @param operation 要执行的操作
     * @param logger 日志记录器
     * @param errorMessage 错误消息
     * @param defaultValue 异常时的默认返回值
     * @return 操作结果或默认值
     */
    public static <T> T executeWithExceptionHandling(SupplierWithException<T> operation, Logger logger, 
                                                     String errorMessage, T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            logger.error(errorMessage + ": {}", e.getMessage(), e);
            return defaultValue;
        }
    }

    /**
     * 执行操作并处理异常，支持finally块
     * 
     * @param operation 要执行的操作
     * @param finallyOperation finally块中要执行的操作
     * @param logger 日志记录器
     * @param errorMessage 错误消息
     */
    public static void executeWithExceptionHandlingAndFinally(Runnable operation, Runnable finallyOperation,
                                                              Logger logger, String errorMessage) {
        try {
            operation.run();
        } catch (Exception e) {
            logger.error(errorMessage + ": {}", e.getMessage(), e);
        } finally {
            if (finallyOperation != null) {
                finallyOperation.run();
            }
        }
    }

    /**
     * 执行操作并处理异常，支持返回值和finally块
     * 
     * @param <T> 返回值类型
     * @param operation 要执行的操作
     * @param finallyOperation finally块中要执行的操作
     * @param logger 日志记录器
     * @param errorMessage 错误消息
     * @param defaultValue 异常时的默认返回值
     * @return 操作结果或默认值
     */
    public static <T> T executeWithExceptionHandlingAndFinally(SupplierWithException<T> operation, 
                                                               Runnable finallyOperation,
                                                               Logger logger, String errorMessage, 
                                                               T defaultValue) {
        try {
            return operation.get();
        } catch (Exception e) {
            logger.error(errorMessage + ": {}", e.getMessage(), e);
            return defaultValue;
        } finally {
            if (finallyOperation != null) {
                finallyOperation.run();
            }
        }
    }

    /**
     * 支持抛出异常的Supplier接口
     * 
     * @param <T> 返回值类型
     */
    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}