package com.smalldragon.yml.integration;

import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Session Hot Refresh 集成测试
 * 简单验证配置类和功能的基本工作状态
 * 
 * @author DragonToken
 * @version 1.0
 * @date 2025/1/27
 */
class SessionHotRefreshIntegrationTest {

    @Test
    void testSessionHotRefreshConfigCreation() {
        // 测试配置类的创建和基本功能
        DragonTokenProperties properties = new DragonTokenProperties();
        DragonTokenProperties.SessionHotRefreshConfig config = new DragonTokenProperties.SessionHotRefreshConfig();
        
        // 设置基本配置
        config.setEnabled(true);
        config.setDebounceDelayMillis(5000L);
        config.setBatchSize(100);
        config.setBatchIntervalMillis(3000L);
        config.setLocalCacheSize(1000);
        config.setLocalCacheExpireMillis(300000L);
        config.setSmartRefreshThresholdSeconds(1800L);
        config.setAsyncThreadPoolSize(2);
        config.setPerformanceMonitorEnabled(true);
        config.setPerformanceReportIntervalMillis(60000L);
        
        properties.setSessionHotRefresh(config);
        
        // 验证配置
        assertNotNull(properties.getSessionHotRefresh());
        assertTrue(properties.getSessionHotRefresh().isEnabled());
        assertEquals(5000L, properties.getSessionHotRefresh().getDebounceDelayMillis());
        assertEquals(100, properties.getSessionHotRefresh().getBatchSize());
        assertEquals(3000L, properties.getSessionHotRefresh().getBatchIntervalMillis());
        assertEquals(1000, properties.getSessionHotRefresh().getLocalCacheSize());
        assertEquals(300000L, properties.getSessionHotRefresh().getLocalCacheExpireMillis());
        assertEquals(1800L, properties.getSessionHotRefresh().getSmartRefreshThresholdSeconds());
        assertEquals(2, properties.getSessionHotRefresh().getAsyncThreadPoolSize());
        assertTrue(properties.getSessionHotRefresh().isPerformanceMonitorEnabled());
        assertEquals(60000L, properties.getSessionHotRefresh().getPerformanceReportIntervalMillis());
    }

    @Test
    void testDefaultValues() {
        // 测试默认值
        DragonTokenProperties.SessionHotRefreshConfig config = new DragonTokenProperties.SessionHotRefreshConfig();
        
        assertTrue(config.isEnabled()); // 默认启用
        assertEquals(1000L, config.getDebounceDelayMillis()); // 1秒
        assertEquals(50, config.getBatchSize());
        assertEquals(2000L, config.getBatchIntervalMillis()); // 2秒
        assertEquals(1000, config.getLocalCacheSize());
        assertEquals(300000L, config.getLocalCacheExpireMillis()); // 5分钟
        assertEquals(1800L, config.getSmartRefreshThresholdSeconds()); // 30分钟
        assertEquals(2, config.getAsyncThreadPoolSize());
        assertTrue(config.isPerformanceMonitorEnabled()); // 默认启用
        assertEquals(300000L, config.getPerformanceReportIntervalMillis()); // 5分钟
    }

    @Test
    void testConfigurationValidation() {
        // 测试配置验证
        DragonTokenProperties.SessionHotRefreshConfig config = new DragonTokenProperties.SessionHotRefreshConfig();
        
        // 测试边界值
        config.setBatchSize(1);
        assertEquals(1, config.getBatchSize());
        
        config.setBatchSize(1000);
        assertEquals(1000, config.getBatchSize());
        
        config.setAsyncThreadPoolSize(1);
        assertEquals(1, config.getAsyncThreadPoolSize());
        
        config.setAsyncThreadPoolSize(10);
        assertEquals(10, config.getAsyncThreadPoolSize());
    }
}