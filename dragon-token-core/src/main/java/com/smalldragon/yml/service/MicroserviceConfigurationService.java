package com.smalldragon.yml.service;

import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.utils.ExceptionHandlerUtils;
import com.smalldragon.yml.utils.RedisOperationUtils;
import com.smalldragon.yml.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 微服务配置管理服务
 * 负责服务发现、配置同步和集群状态管理
 * 
 * 启用条件：
 * 1. dragon.token.cache-warmup.distributed.enabled=true
 * 2. 必须存在RedisTemplate Bean
 * 3. 必须存在DragonTokenProperties Bean
 * 
 * 功能特性：
 * - 自动服务注册与发现
 * - 分布式配置同步
 * - 集群健康状态监控
 * - 服务心跳检测
 * 
 * @author smalldragon
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(
    name = "dragon.token.cache-warmup.distributed.enabled", 
    havingValue = "true",
    matchIfMissing = false
)
@ConditionalOnBean({RedisTemplate.class, DragonTokenProperties.class})
public class MicroserviceConfigurationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MicroserviceConfigurationService.class);
    
    private static final String SERVICE_REGISTRY_KEY = "dragon:token:cluster:services";
    private static final String CONFIG_SYNC_KEY = "dragon:token:cluster:config";
    private static final String SERVICE_STATUS_KEY = "dragon:token:cluster:status";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private DragonTokenProperties properties;
    
    @Value("${spring.application.name:dragon-token-service}")
    private String serviceName;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    private String serviceId;
    private String serviceAddress;
    private ScheduledExecutorService scheduler;
    private final Map<String, ServiceInfo> localServiceCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        logger.info("=== 微服务配置管理服务初始化开始 ===");
        logger.info("分布式缓存预热已启用，开始初始化微服务配置管理");
        
        try {
            // 验证配置
            validateConfiguration();
            
            // 生成服务ID和地址
            this.serviceId = generateServiceId();
            this.serviceAddress = getLocalAddress() + ":" + serverPort;
            
            logger.info("服务标识信息 - ID: {}, 名称: {}, 地址: {}", serviceId, serviceName, serviceAddress);
            
            // 初始化调度器
            this.scheduler = Executors.newScheduledThreadPool(3, r -> {
                Thread t = new Thread(r, "DragonToken-MicroService-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            });
            
            // 注册服务
            registerService();
            
            // 启动心跳
            startHeartbeat();
            
            // 启动配置同步
            startConfigSync();
            
            // 启动服务发现
            startServiceDiscovery();
            
            logger.info("=== 微服务配置管理服务初始化完成 ===");
            logger.info("服务已成功注册到集群，开始提供分布式配置管理服务");
            
        } catch (Exception e) {
            logger.error("=== 微服务配置管理服务初始化失败 ===", e);
            logger.error("请检查以下配置项：");
            logger.error("1. dragon.token.cache-warmup.distributed.enabled 是否为 true");
            logger.error("2. Redis 连接是否正常");
            logger.error("3. 网络配置是否正确");
            throw new RuntimeException("微服务配置管理服务初始化失败", e);
        }
    }
    
    /**
     * 验证配置有效性
     */
    private void validateConfiguration() {
        logger.debug("开始验证微服务配置...");
        
        if (properties.getCacheWarmup() == null) {
            throw new IllegalStateException("缓存预热配置不能为空");
        }
        
        if (properties.getCacheWarmup().getDistributed() == null) {
            throw new IllegalStateException("分布式配置不能为空");
        }
        
        if (!properties.getCacheWarmup().getDistributed().isEnabled()) {
            throw new IllegalStateException("分布式模式未启用");
        }
        
        // 测试Redis连接
        try {
            redisTemplate.opsForValue().set("dragon:token:health:check", "ok", 1, TimeUnit.SECONDS);
            logger.debug("Redis连接测试成功");
        } catch (Exception e) {
            throw new IllegalStateException("Redis连接失败", e);
        }
        
        logger.debug("微服务配置验证通过");
    }
    
    @PreDestroy
    public void shutdown() {
        logger.info("=== 微服务配置管理服务关闭开始 ===");
        
        try {
            // 注销服务
            unregisterService();
            
            // 关闭调度器
            if (scheduler != null && !scheduler.isShutdown()) {
                logger.info("正在关闭调度器...");
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("调度器未能在5秒内正常关闭，强制关闭");
                    scheduler.shutdownNow();
                }
            }
            
            logger.info("=== 微服务配置管理服务关闭完成 ===");
            
        } catch (Exception e) {
            logger.error("微服务配置管理服务关闭时发生错误", e);
        }
    }
    
    /**
     * 注册服务到集群
     */
    private void registerService() {
        try {
            ServiceInfo serviceInfo = new ServiceInfo();
            serviceInfo.setServiceId(serviceId);
            serviceInfo.setServiceName(serviceName);
            serviceInfo.setAddress(serviceAddress);
            serviceInfo.setStartTime(LocalDateTime.now());
            serviceInfo.setLastHeartbeat(LocalDateTime.now());
            serviceInfo.setStatus("ACTIVE");
            serviceInfo.setVersion(getServiceVersion());
            serviceInfo.setMetadata(getServiceMetadata());
            
            // 注册到Redis
            redisTemplate.opsForHash().put(SERVICE_REGISTRY_KEY, serviceId, serviceInfo);
            
            // 设置服务状态
            redisTemplate.opsForValue().set(
                SERVICE_STATUS_KEY + ":" + serviceId, 
                "ACTIVE", 
                getHeartbeatInterval() * 3, 
                TimeUnit.SECONDS
            );
            
            logger.info("服务注册成功: {}", serviceInfo);
            
        } catch (Exception e) {
            logger.error("服务注册失败", e);
            throw new RuntimeException("服务注册失败", e);
        }
    }
    
    /**
     * 注销服务
     */
    private void unregisterService() {
        try {
            redisTemplate.opsForHash().delete(SERVICE_REGISTRY_KEY, serviceId);
            redisTemplate.delete(SERVICE_STATUS_KEY + ":" + serviceId);
            
            logger.info("服务注销成功: {}", serviceId);
            
        } catch (Exception e) {
            logger.error("服务注销失败", e);
        }
    }
    
    /**
     * 启动心跳机制
     */
    private void startHeartbeat() {
        int heartbeatInterval = getHeartbeatInterval();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 更新心跳时间
                ServiceInfo serviceInfo = (ServiceInfo) redisTemplate.opsForHash().get(SERVICE_REGISTRY_KEY, serviceId);
                if (serviceInfo != null) {
                    serviceInfo.setLastHeartbeat(LocalDateTime.now());
                    redisTemplate.opsForHash().put(SERVICE_REGISTRY_KEY, serviceId, serviceInfo);
                }
                
                // 更新服务状态
                redisTemplate.opsForValue().set(
                    SERVICE_STATUS_KEY + ":" + serviceId, 
                    "ACTIVE", 
                    heartbeatInterval * 3, 
                    TimeUnit.SECONDS
                );
                
                logger.debug("心跳发送成功: {}", serviceId);
                
            } catch (Exception e) {
                logger.error("心跳发送失败", e);
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
    }
    
    /**
     * 启动配置同步
     */
    private void startConfigSync() {
        int syncInterval = getVersionCheckInterval();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                syncConfiguration();
            } catch (Exception e) {
                logger.error("配置同步失败", e);
            }
        }, syncInterval, syncInterval, TimeUnit.SECONDS);
    }
    
    /**
     * 启动服务发现
     */
    private void startServiceDiscovery() {
        int discoveryInterval = getHeartbeatInterval();
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                discoverServices();
                cleanupInactiveServices();
            } catch (Exception e) {
                logger.error("服务发现失败", e);
            }
        }, discoveryInterval, discoveryInterval, TimeUnit.SECONDS);
    }
    
    /**
     * 同步配置
     */
    private void syncConfiguration() {
        try {
            // 获取集群配置版本
            String clusterConfigVersion = (String) redisTemplate.opsForValue().get(CONFIG_SYNC_KEY + ":version");
            String localConfigVersion = getLocalConfigVersion();
            
            if (clusterConfigVersion != null && !clusterConfigVersion.equals(localConfigVersion)) {
                // 配置版本不一致，需要同步
                Map<Object, Object> rawClusterConfig = redisTemplate.opsForHash().entries(CONFIG_SYNC_KEY);
                Map<String, Object> clusterConfig = new HashMap<>();
                
                // 安全的类型转换
                for (Map.Entry<Object, Object> entry : rawClusterConfig.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        clusterConfig.put((String) entry.getKey(), entry.getValue());
                    }
                }
                
                if (!clusterConfig.isEmpty()) {
                    updateLocalConfiguration(clusterConfig);
                    logger.info("配置同步完成，版本: {} -> {}", localConfigVersion, clusterConfigVersion);
                }
            }
            
        } catch (Exception e) {
            logger.error("配置同步过程中发生错误", e);
        }
    }
    
    /**
     * 发现服务
     */
    private void discoverServices() {
        try {
            Map<Object, Object> services = redisTemplate.opsForHash().entries(SERVICE_REGISTRY_KEY);
            
            for (Map.Entry<Object, Object> entry : services.entrySet()) {
                String sid = (String) entry.getKey();
                ServiceInfo serviceInfo = (ServiceInfo) entry.getValue();
                
                if (!sid.equals(serviceId)) {
                    localServiceCache.put(sid, serviceInfo);
                }
            }
            
            logger.debug("服务发现完成，发现 {} 个服务", localServiceCache.size());
            
        } catch (Exception e) {
            logger.error("服务发现过程中发生错误", e);
        }
    }
    
    /**
     * 清理不活跃的服务
     */
    private void cleanupInactiveServices() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(getServiceTimeout());
            
            Iterator<Map.Entry<String, ServiceInfo>> iterator = localServiceCache.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, ServiceInfo> entry = iterator.next();
                ServiceInfo serviceInfo = entry.getValue();
                
                if (serviceInfo.getLastHeartbeat().isBefore(threshold)) {
                    iterator.remove();
                    logger.warn("移除不活跃服务: {}", entry.getKey());
                }
            }
            
        } catch (Exception e) {
            logger.error("清理不活跃服务时发生错误", e);
        }
    }
    
    /**
     * 更新本地配置
     */
    private void updateLocalConfiguration(Map<String, Object> clusterConfig) {
        // 这里可以实现具体的配置更新逻辑
        // 例如更新缓存预热配置、热刷新配置等
        logger.info("本地配置已更新: {}", clusterConfig.keySet());
    }
    
    /**
     * 获取活跃服务列表
     */
    public List<ServiceInfo> getActiveServices() {
        return new ArrayList<>(localServiceCache.values());
    }
    
    /**
     * 获取服务信息
     */
    public ServiceInfo getServiceInfo(String serviceId) {
        return localServiceCache.get(serviceId);
    }
    
    /**
     * 检查服务是否活跃
     */
    public boolean isServiceActive(String serviceId) {
        ServiceInfo serviceInfo = localServiceCache.get(serviceId);
        if (serviceInfo == null) {
            return false;
        }
        
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(getServiceTimeout());
        return serviceInfo.getLastHeartbeat().isAfter(threshold);
    }
    
    /**
     * 获取集群状态
     */
    public Map<String, Object> getClusterStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("totalServices", localServiceCache.size() + 1); // +1 for current service
        status.put("activeServices", getActiveServices().size() + 1);
        status.put("currentService", serviceId);
        status.put("serviceAddress", serviceAddress);
        status.put("lastUpdate", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return status;
    }
    
    // 辅助方法
    
    private String generateServiceId() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            return serviceName + "-" + hostname + "-" + serverPort + "-" + System.currentTimeMillis();
        } catch (Exception e) {
            return serviceName + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    private String getLocalAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
    
    private String getServiceVersion() {
        return "1.0.0"; // 可以从配置文件或构建信息中获取
    }
    
    private Map<String, String> getServiceMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("framework", "DragonToken");
        metadata.put("version", getServiceVersion());
        metadata.put("startTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return metadata;
    }
    
    private String getLocalConfigVersion() {
        return String.valueOf(properties.hashCode()); // 简单的版本计算
    }
    
    private int getHeartbeatInterval() {
        return properties.getCacheWarmup().getDistributed().getHeartbeatInterval();
    }
    
    private int getServiceTimeout() {
        return properties.getCacheWarmup().getDistributed().getServiceTimeout();
    }
    
    private int getVersionCheckInterval() {
        return properties.getCacheWarmup().getDistributed().getVersionCheckInterval();
    }
    
    /**
     * 服务信息类
     */
    public static class ServiceInfo implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String serviceId;
        private String serviceName;
        private String address;
        private LocalDateTime startTime;
        private LocalDateTime lastHeartbeat;
        private String status;
        private String version;
        private Map<String, String> metadata;
        
        // Getters and Setters
        public String getServiceId() { return serviceId; }
        public void setServiceId(String serviceId) { this.serviceId = serviceId; }
        
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getLastHeartbeat() { return lastHeartbeat; }
        public void setLastHeartbeat(LocalDateTime lastHeartbeat) { this.lastHeartbeat = lastHeartbeat; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
        
        @Override
        public String toString() {
            return "ServiceInfo{" +
                    "serviceId='" + serviceId + '\'' +
                    ", serviceName='" + serviceName + '\'' +
                    ", address='" + address + '\'' +
                    ", status='" + status + '\'' +
                    ", version='" + version + '\'' +
                    '}';
        }
    }
}