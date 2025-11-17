package com.smalldragon.yml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Redis Session 配置属性自动映射
 * 自动读取项目中的 Redis 配置并应用到 Session 配置
 */
@Component
@ConfigurationProperties(prefix = "spring.redis")
public class RedisSessionProperties {
    
    private String host;
    private Integer port;
    private Integer database;
    private String password;
    
    // 默认值
    private Integer timeout = 2000;
    private Integer maxTotal = 8;
    private Integer maxIdle = 8;
    private Integer minIdle = 0;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public Integer getDatabase() {
        return database;
    }

    public void setDatabase(Integer database) {
        this.database = database;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(Integer maxTotal) {
        this.maxTotal = maxTotal;
    }

    public Integer getMaxIdle() {
        return maxIdle;
    }

    public void setMaxIdle(Integer maxIdle) {
        this.maxIdle = maxIdle;
    }

    public Integer getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(Integer minIdle) {
        this.minIdle = minIdle;
    }
    
    /**
     * 检查 Redis 配置是否完整
     */
    public boolean isRedisConfigured() {
        return host != null && port != null && database != null;
    }
    
    /**
     * 获取 Redis 连接信息摘要
     */
    public String getRedisInfo() {
        return String.format("Redis配置: %s:%d/%d", host, port, database);
    }
}