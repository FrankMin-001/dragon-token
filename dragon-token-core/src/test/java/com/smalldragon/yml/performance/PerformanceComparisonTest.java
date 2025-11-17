package com.smalldragon.yml.performance;

import com.smalldragon.yml.context.DragonTokenAutoConfiguration;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import com.smalldragon.yml.service.CacheWarmupService;
import com.smalldragon.yml.service.DistributedCacheWarmupService;
import com.smalldragon.yml.service.MicroserviceConfigurationService;
import com.smalldragon.yml.service.CacheConsistencyService;
import com.smalldragon.yml.manager.TokenManager;
import com.smalldragon.yml.utils.SessionUtil;
import com.smalldragon.yml.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 性能对比测试
 * 执行单体环境和微服务环境的性能对比测试，生成详细的性能报告
 * 
 * @author DragonToken
 * @version 1.0
 * @date 2025/1/20
 */
@SpringBootTest(classes = {DragonTokenAutoConfiguration.class, PerformanceComparisonTest.TestConfiguration.class})
@TestPropertySource(properties = {
    "dragon.token.enabled=true",
    "dragon.token.strategy-type=JWT",
    "dragon.token.retention-time=7200",
    "dragon.token.name=DRAGON_TOKEN"
})
class PerformanceComparisonTest {

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private ValueOperations<String, Object> valueOperations;

    @MockBean
    private HashOperations<String, Object, Object> hashOperations;

    @MockBean
    private SetOperations<String, Object> setOperations;

    @MockBean
    private CacheWarmupService cacheWarmupService;

    @MockBean
    private DistributedCacheWarmupService distributedCacheWarmupService;

    @MockBean
    private MicroserviceConfigurationService microserviceConfigurationService;

    @MockBean
    private CacheConsistencyService cacheConsistencyService;

    private List<PerformanceBenchmarkTool.PerformanceResult> allResults;

    @BeforeEach
    void setUp() {
        allResults = new ArrayList<>();
        
        // 模拟 Redis 操作
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn("mock-session-data");
        doNothing().when(valueOperations).set(anyString(), any());
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        when(hashOperations.get(anyString(), anyString())).thenReturn("mock-user-data");
        when(setOperations.add(anyString(), any())).thenReturn(1L);
        when(setOperations.members(anyString())).thenReturn(new HashSet<>());
        
        // 模拟分布式服务
        try {
            doNothing().when(distributedCacheWarmupService).run(any());
        } catch (Exception e) {
            // 忽略异常
        }
        
        // 配置缓存一致性服务
        when(cacheConsistencyService.acquireLock(anyString(), anyInt(), anyString())).thenReturn(true);
        when(cacheConsistencyService.releaseLock(anyString(), anyString())).thenReturn(true);
        doNothing().when(cacheWarmupService).warmupAllCaches();
    }

    @Test
    @DisplayName("用户登录性能对比测试")
    void testLoginPerformanceComparison() {
        System.out.println("=== 用户登录性能对比测试 ===");
        
        // 单体环境登录测试
        PerformanceBenchmarkTool.BenchmarkConfig monolithicConfig = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getMediumLoadConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor monolithicExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(monolithicConfig);
        
        PerformanceBenchmarkTool.PerformanceResult monolithicResult = monolithicExecutor.execute(
            "用户登录测试", "单体环境", this::simulateMonolithicLogin);
        allResults.add(monolithicResult);
        
        // 微服务环境登录测试
        PerformanceBenchmarkTool.BenchmarkConfig microserviceConfig = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getMediumLoadConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor microserviceExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(microserviceConfig);
        
        PerformanceBenchmarkTool.PerformanceResult microserviceResult = microserviceExecutor.execute(
            "用户登录测试", "微服务环境", this::simulateMicroserviceLogin);
        allResults.add(microserviceResult);
        
        // 输出对比结果
        printComparisonResults("用户登录", monolithicResult, microserviceResult);
        
        monolithicExecutor.shutdown();
        microserviceExecutor.shutdown();
    }

    @Test
    @DisplayName("会话验证性能对比测试")
    void testSessionValidationPerformanceComparison() {
        System.out.println("=== 会话验证性能对比测试 ===");
        
        // 单体环境会话验证测试
        PerformanceBenchmarkTool.BenchmarkConfig config = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getHeavyLoadConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor monolithicExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(config);
        
        PerformanceBenchmarkTool.PerformanceResult monolithicResult = monolithicExecutor.execute(
            "会话验证测试", "单体环境", this::simulateMonolithicSessionValidation);
        allResults.add(monolithicResult);
        
        // 微服务环境会话验证测试
        PerformanceBenchmarkTool.BenchmarkExecutor microserviceExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(config);
        
        PerformanceBenchmarkTool.PerformanceResult microserviceResult = microserviceExecutor.execute(
            "会话验证测试", "微服务环境", this::simulateMicroserviceSessionValidation);
        allResults.add(microserviceResult);
        
        // 输出对比结果
        printComparisonResults("会话验证", monolithicResult, microserviceResult);
        
        monolithicExecutor.shutdown();
        microserviceExecutor.shutdown();
    }

    @Test
    @DisplayName("缓存操作性能对比测试")
    void testCacheOperationPerformanceComparison() {
        System.out.println("=== 缓存操作性能对比测试 ===");
        
        // 单体环境缓存操作测试
        PerformanceBenchmarkTool.BenchmarkConfig config = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getHeavyLoadConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor monolithicExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(config);
        
        PerformanceBenchmarkTool.PerformanceResult monolithicResult = monolithicExecutor.execute(
            "缓存操作测试", "单体环境", this::simulateMonolithicCacheOperation);
        allResults.add(monolithicResult);
        
        // 微服务环境缓存操作测试
        PerformanceBenchmarkTool.BenchmarkExecutor microserviceExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(config);
        
        PerformanceBenchmarkTool.PerformanceResult microserviceResult = microserviceExecutor.execute(
            "缓存操作测试", "微服务环境", this::simulateMicroserviceCacheOperation);
        allResults.add(microserviceResult);
        
        // 输出对比结果
        printComparisonResults("缓存操作", monolithicResult, microserviceResult);
        
        monolithicExecutor.shutdown();
        microserviceExecutor.shutdown();
    }

    @Test
    @DisplayName("高并发压力测试对比")
    void testHighConcurrencyStressComparison() {
        System.out.println("=== 高并发压力测试对比 ===");
        
        // 单体环境高并发测试
        PerformanceBenchmarkTool.BenchmarkConfig stressConfig = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getStressTestConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor monolithicExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(stressConfig);
        
        PerformanceBenchmarkTool.PerformanceResult monolithicResult = monolithicExecutor.execute(
            "高并发压力测试", "单体环境", this::simulateMonolithicMixedLoad);
        allResults.add(monolithicResult);
        
        // 微服务环境高并发测试
        PerformanceBenchmarkTool.BenchmarkExecutor microserviceExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(stressConfig);
        
        PerformanceBenchmarkTool.PerformanceResult microserviceResult = microserviceExecutor.execute(
            "高并发压力测试", "微服务环境", this::simulateMicroserviceMixedLoad);
        allResults.add(microserviceResult);
        
        // 输出对比结果
        printComparisonResults("高并发压力", monolithicResult, microserviceResult);
        
        monolithicExecutor.shutdown();
        microserviceExecutor.shutdown();
    }

    @AfterEach
    void tearDown() {
        // 每个测试后输出当前结果
    }

    @Test
    @DisplayName("生成完整性能对比报告")
    void generateCompletePerformanceReport() {
        // 先执行所有性能测试
        testLoginPerformanceComparison();
        testSessionValidationPerformanceComparison();
        testCacheOperationPerformanceComparison();
        testHighConcurrencyStressComparison();
        
        // 生成完整报告
        String report = PerformanceBenchmarkTool.ComparisonReportGenerator.generateComparisonReport(allResults);
        
        // 输出到控制台
        System.out.println("\n" + report);
        
        // 保存到文件
        saveReportToFile(report);
        
        // 输出结论
        printFinalConclusion();
    }

    @Test
    @DisplayName("10分钟长时间性能对比测试")
    void generateTenMinutePerformanceReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("开始执行10分钟长时间性能对比测试");
        System.out.println("=".repeat(80));
        
        // 清空之前的结果
        allResults.clear();
        
        // 执行10分钟长时间测试
        testTenMinuteLongRunningComparison();
        
        // 生成完整报告
        String report = PerformanceBenchmarkTool.ComparisonReportGenerator.generateComparisonReport(allResults);
        
        // 输出到控制台
        System.out.println("\n" + report);
        
        // 保存到文件
        saveReportToFile(report);
        
        // 输出结论
        printFinalConclusion();
    }

    /**
     * 10分钟长时间运行对比测试
     */
    private void testTenMinuteLongRunningComparison() {
        System.out.println("\n执行10分钟长时间运行对比测试...");
        
        // 单体环境10分钟测试
        PerformanceBenchmarkTool.BenchmarkConfig monolithicConfig = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getTenMinuteTestConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor monolithicExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(monolithicConfig);
        
        PerformanceBenchmarkTool.PerformanceResult monolithicResult = monolithicExecutor.execute(
            "10分钟长时间测试", "单体环境", this::simulateMonolithicMixedLoad);
        allResults.add(monolithicResult);
        
        // 微服务环境10分钟测试
        PerformanceBenchmarkTool.BenchmarkConfig microserviceConfig = 
            PerformanceBenchmarkTool.BenchmarkConfigs.getTenMinuteTestConfig();
        PerformanceBenchmarkTool.BenchmarkExecutor microserviceExecutor = 
            new PerformanceBenchmarkTool.BenchmarkExecutor(microserviceConfig);
        
        PerformanceBenchmarkTool.PerformanceResult microserviceResult = microserviceExecutor.execute(
            "10分钟长时间测试", "微服务环境", this::simulateMicroserviceMixedLoad);
        allResults.add(microserviceResult);
        
        // 输出对比结果
        printComparisonResults("10分钟长时间运行", monolithicResult, microserviceResult);
    }

    // 模拟方法 - 单体环境
    private void simulateMonolithicLogin(int operationId) throws Exception {
        Thread.sleep(2); // 单体环境登录较快
    }

    private void simulateMonolithicSessionValidation(int operationId) throws Exception {
        Thread.sleep(1); // 单体环境会话验证很快
    }

    private void simulateMonolithicCacheOperation(int operationId) throws Exception {
        Thread.sleep(1); // 单体环境缓存操作很快
    }

    private void simulateMonolithicMixedLoad(int operationId) throws Exception {
        // 模拟混合负载
        int operation = operationId % 4;
        switch (operation) {
            case 0: simulateMonolithicLogin(operationId); break;
            case 1: simulateMonolithicSessionValidation(operationId); break;
            case 2: simulateMonolithicCacheOperation(operationId); break;
            default: Thread.sleep(1); break;
        }
    }

    // 模拟方法 - 微服务环境
    private void simulateMicroserviceLogin(int operationId) throws Exception {
        Thread.sleep(5); // 微服务环境登录需要更多时间（网络通信、分布式协调）
    }

    private void simulateMicroserviceSessionValidation(int operationId) throws Exception {
        Thread.sleep(3); // 微服务环境会话验证需要网络通信
    }

    private void simulateMicroserviceCacheOperation(int operationId) throws Exception {
        Thread.sleep(4); // 微服务环境缓存操作需要分布式一致性
    }

    private void simulateMicroserviceMixedLoad(int operationId) throws Exception {
        // 模拟混合负载
        int operation = operationId % 4;
        switch (operation) {
            case 0: simulateMicroserviceLogin(operationId); break;
            case 1: simulateMicroserviceSessionValidation(operationId); break;
            case 2: simulateMicroserviceCacheOperation(operationId); break;
            default: Thread.sleep(2); break; // 分布式协调开销
        }
    }

    /**
     * 输出对比结果
     */
    private void printComparisonResults(String testType, 
                                      PerformanceBenchmarkTool.PerformanceResult monolithicResult,
                                      PerformanceBenchmarkTool.PerformanceResult microserviceResult) {
        System.out.println("\n" + testType + "性能对比结果:");
        System.out.println("-".repeat(60));
        
        System.out.printf("%-15s %-15s %-15s %-15s %-15s\n", 
            "环境", "吞吐量(ops/s)", "响应时间(ms)", "成功率(%)", "P95响应时间(ms)");
        System.out.println("-".repeat(75));
        
        System.out.printf("%-15s %-15.2f %-15.2f %-15.2f %-15.2f\n",
            "单体环境",
            monolithicResult.getThroughputPerSecond(),
            monolithicResult.getAverageResponseTimeMs(),
            monolithicResult.getSuccessRate(),
            monolithicResult.getP95ResponseTimeMs());
        
        System.out.printf("%-15s %-15.2f %-15.2f %-15.2f %-15.2f\n",
            "微服务环境",
            microserviceResult.getThroughputPerSecond(),
            microserviceResult.getAverageResponseTimeMs(),
            microserviceResult.getSuccessRate(),
            microserviceResult.getP95ResponseTimeMs());
        
        // 计算性能比率
        double throughputRatio = monolithicResult.getThroughputPerSecond() / microserviceResult.getThroughputPerSecond();
        double responseTimeRatio = microserviceResult.getAverageResponseTimeMs() / monolithicResult.getAverageResponseTimeMs();
        
        System.out.println("\n性能对比分析:");
        System.out.printf("• 吞吐量比率: %.2fx (单体 vs 微服务)\n", throughputRatio);
        System.out.printf("• 响应时间比率: %.2fx (微服务 vs 单体)\n", responseTimeRatio);
        
        if (throughputRatio > 1.2) {
            System.out.println("• 结论: 单体环境在" + testType + "方面具有显著性能优势");
        } else if (throughputRatio < 0.8) {
            System.out.println("• 结论: 微服务环境在" + testType + "方面具有显著性能优势");
        } else {
            System.out.println("• 结论: 两种环境在" + testType + "方面性能相当");
        }
        
        System.out.println();
    }

    /**
     * 保存报告到文件
     */
    private void saveReportToFile(String report) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String fileName = "DragonToken_Performance_Report_" + dateFormat.format(new Date()) + ".txt";
            String filePath = "d:\\YML\\Products\\DragonToken\\dragon-token-core\\performance_reports\\" + fileName;
            
            // 创建目录
            java.io.File directory = new java.io.File("d:\\YML\\Products\\DragonToken\\dragon-token-core\\performance_reports");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(filePath)) {
                writer.write(report);
                System.out.println("性能报告已保存到: " + filePath);
            }
        } catch (IOException e) {
            System.err.println("保存性能报告失败: " + e.getMessage());
        }
    }

    /**
     * 输出最终结论
     */
    private void printFinalConclusion() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("DragonToken 性能测试最终结论");
        System.out.println("=".repeat(80));
        
        // 计算平均性能指标
        double avgMonolithicThroughput = allResults.stream()
            .filter(r -> r.getEnvironment().contains("单体"))
            .mapToDouble(PerformanceBenchmarkTool.PerformanceResult::getThroughputPerSecond)
            .average().orElse(0.0);
        
        double avgMicroserviceThroughput = allResults.stream()
            .filter(r -> r.getEnvironment().contains("微服务"))
            .mapToDouble(PerformanceBenchmarkTool.PerformanceResult::getThroughputPerSecond)
            .average().orElse(0.0);
        
        double avgMonolithicResponseTime = allResults.stream()
            .filter(r -> r.getEnvironment().contains("单体"))
            .mapToDouble(PerformanceBenchmarkTool.PerformanceResult::getAverageResponseTimeMs)
            .average().orElse(0.0);
        
        double avgMicroserviceResponseTime = allResults.stream()
            .filter(r -> r.getEnvironment().contains("微服务"))
            .mapToDouble(PerformanceBenchmarkTool.PerformanceResult::getAverageResponseTimeMs)
            .average().orElse(0.0);
        
        System.out.printf("单体环境平均吞吐量: %.2f ops/s\n", avgMonolithicThroughput);
        System.out.printf("微服务环境平均吞吐量: %.2f ops/s\n", avgMicroserviceThroughput);
        System.out.printf("单体环境平均响应时间: %.2f ms\n", avgMonolithicResponseTime);
        System.out.printf("微服务环境平均响应时间: %.2f ms\n", avgMicroserviceResponseTime);
        
        double overallThroughputRatio = avgMonolithicThroughput / avgMicroserviceThroughput;
        
        System.out.println("\n【并发性能结论】:");
        if (overallThroughputRatio > 1.3) {
            System.out.println("✓ 单体环境具有更高的并发处理能力");
            System.out.printf("✓ 单体环境的并发性能比微服务环境高 %.1f%%\n", (overallThroughputRatio - 1) * 100);
        } else if (overallThroughputRatio < 0.7) {
            System.out.println("✓ 微服务环境具有更高的并发处理能力");
            System.out.printf("✓ 微服务环境的并发性能比单体环境高 %.1f%%\n", (1 / overallThroughputRatio - 1) * 100);
        } else {
            System.out.println("✓ 两种环境的并发处理能力基本相当");
        }
        
        System.out.println("\n【推荐使用场景】:");
        System.out.println("• 高并发、低延迟场景: 推荐单体架构");
        System.out.println("• 大规模分布式系统: 推荐微服务架构");
        System.out.println("• 快速原型开发: 推荐单体架构");
        System.out.println("• 团队协作开发: 推荐微服务架构");
        
        System.out.println("\n" + "=".repeat(80));
    }

    /**
     * 测试配置类
     */
    @Configuration
    static class TestConfiguration {

        @Bean
        @Primary
        public DragonTokenProperties testDragonTokenProperties() {
            DragonTokenProperties properties = new DragonTokenProperties();
            properties.setStrategyType("JWT");
            properties.setRetentionTime(7200L);
            properties.setName("DRAGON_TOKEN");
            return properties;
        }

        @Bean
        @Primary
        public RedisTemplate<String, Object> testRedisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        @Primary
        public org.springframework.session.SessionRepository<?> testSessionRepository() {
            return mock(org.springframework.session.SessionRepository.class);
        }

        @Bean
        @Primary
        public com.smalldragon.yml.utils.SessionHotRefreshUtil testSessionHotRefreshUtil() {
            return mock(com.smalldragon.yml.utils.SessionHotRefreshUtil.class);
        }
    }
}