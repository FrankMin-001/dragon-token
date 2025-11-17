package com.smalldragon.yml.performance;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;

/**
 * 性能基准测试工具
 * 提供统一的性能测试框架，支持单体和微服务环境的对比测试
 * 
 * @author DragonToken
 * @version 1.0
 * @date 2025/1/20
 */
public class PerformanceBenchmarkTool {

    /**
     * 性能测试结果
     */
    public static class PerformanceResult {
        private String testName;
        private String environment;
        private int totalOperations;
        private int successfulOperations;
        private int failedOperations;
        private long totalTimeMs;
        private double throughputPerSecond;
        private double averageResponseTimeMs;
        private double minResponseTimeMs;
        private double maxResponseTimeMs;
        private double p95ResponseTimeMs;
        private double p99ResponseTimeMs;
        private double successRate;
        private Map<String, Object> additionalMetrics;
        private Date testTime;

        public PerformanceResult(String testName, String environment) {
            this.testName = testName;
            this.environment = environment;
            this.additionalMetrics = new HashMap<>();
            this.testTime = new Date();
        }

        // Getters and Setters
        public String getTestName() { return testName; }
        public void setTestName(String testName) { this.testName = testName; }
        
        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }
        
        public int getTotalOperations() { return totalOperations; }
        public void setTotalOperations(int totalOperations) { this.totalOperations = totalOperations; }
        
        public int getSuccessfulOperations() { return successfulOperations; }
        public void setSuccessfulOperations(int successfulOperations) { this.successfulOperations = successfulOperations; }
        
        public int getFailedOperations() { return failedOperations; }
        public void setFailedOperations(int failedOperations) { this.failedOperations = failedOperations; }
        
        public long getTotalTimeMs() { return totalTimeMs; }
        public void setTotalTimeMs(long totalTimeMs) { this.totalTimeMs = totalTimeMs; }
        
        public double getThroughputPerSecond() { return throughputPerSecond; }
        public void setThroughputPerSecond(double throughputPerSecond) { this.throughputPerSecond = throughputPerSecond; }
        
        public double getAverageResponseTimeMs() { return averageResponseTimeMs; }
        public void setAverageResponseTimeMs(double averageResponseTimeMs) { this.averageResponseTimeMs = averageResponseTimeMs; }
        
        public double getMinResponseTimeMs() { return minResponseTimeMs; }
        public void setMinResponseTimeMs(double minResponseTimeMs) { this.minResponseTimeMs = minResponseTimeMs; }
        
        public double getMaxResponseTimeMs() { return maxResponseTimeMs; }
        public void setMaxResponseTimeMs(double maxResponseTimeMs) { this.maxResponseTimeMs = maxResponseTimeMs; }
        
        public double getP95ResponseTimeMs() { return p95ResponseTimeMs; }
        public void setP95ResponseTimeMs(double p95ResponseTimeMs) { this.p95ResponseTimeMs = p95ResponseTimeMs; }
        
        public double getP99ResponseTimeMs() { return p99ResponseTimeMs; }
        public void setP99ResponseTimeMs(double p99ResponseTimeMs) { this.p99ResponseTimeMs = p99ResponseTimeMs; }
        
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        
        public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Object> additionalMetrics) { this.additionalMetrics = additionalMetrics; }
        
        public Date getTestTime() { return testTime; }
        public void setTestTime(Date testTime) { this.testTime = testTime; }

        public void addMetric(String key, Object value) {
            this.additionalMetrics.put(key, value);
        }
    }

    /**
     * 性能测试配置
     */
    public static class BenchmarkConfig {
        private int concurrentUsers = 1000;
        private int operationsPerUser = 10;
        private int threadPoolSize = 50;
        private int timeoutSeconds = 120;
        private boolean enableDetailedMetrics = true;
        private boolean enableWarmup = true;
        private int warmupOperations = 100;

        public BenchmarkConfig() {}

        public BenchmarkConfig(int concurrentUsers, int operationsPerUser, int threadPoolSize) {
            this.concurrentUsers = concurrentUsers;
            this.operationsPerUser = operationsPerUser;
            this.threadPoolSize = threadPoolSize;
        }

        // Getters and Setters
        public int getConcurrentUsers() { return concurrentUsers; }
        public void setConcurrentUsers(int concurrentUsers) { this.concurrentUsers = concurrentUsers; }
        
        public int getOperationsPerUser() { return operationsPerUser; }
        public void setOperationsPerUser(int operationsPerUser) { this.operationsPerUser = operationsPerUser; }
        
        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
        
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        
        public boolean isEnableDetailedMetrics() { return enableDetailedMetrics; }
        public void setEnableDetailedMetrics(boolean enableDetailedMetrics) { this.enableDetailedMetrics = enableDetailedMetrics; }
        
        public boolean isEnableWarmup() { return enableWarmup; }
        public void setEnableWarmup(boolean enableWarmup) { this.enableWarmup = enableWarmup; }
        
        public int getWarmupOperations() { return warmupOperations; }
        public void setWarmupOperations(int warmupOperations) { this.warmupOperations = warmupOperations; }
    }

    /**
     * 性能测试操作接口
     */
    @FunctionalInterface
    public interface BenchmarkOperation {
        void execute(int operationId) throws Exception;
    }

    /**
     * 性能测试执行器
     */
    public static class BenchmarkExecutor {
        private final BenchmarkConfig config;
        private final ExecutorService executorService;

        public BenchmarkExecutor(BenchmarkConfig config) {
            this.config = config;
            this.executorService = Executors.newFixedThreadPool(config.getThreadPoolSize());
        }

        /**
         * 执行性能测试
         */
        public PerformanceResult execute(String testName, String environment, BenchmarkOperation operation) {
            PerformanceResult result = new PerformanceResult(testName, environment);
            
            try {
                // 预热
                if (config.isEnableWarmup()) {
                    performWarmup(operation);
                }

                // 执行主测试
                return performBenchmark(result, operation);
                
            } catch (Exception e) {
                System.err.println("性能测试执行失败: " + e.getMessage());
                e.printStackTrace();
                return result;
            }
        }

        /**
         * 预热操作
         */
        private void performWarmup(BenchmarkOperation operation) {
            System.out.println("开始预热操作...");
            CountDownLatch warmupLatch = new CountDownLatch(config.getWarmupOperations());
            
            for (int i = 0; i < config.getWarmupOperations(); i++) {
                final int operationId = i;
                executorService.submit(() -> {
                    try {
                        operation.execute(operationId);
                    } catch (Exception e) {
                        // 预热阶段忽略错误
                    } finally {
                        warmupLatch.countDown();
                    }
                });
            }
            
            try {
                warmupLatch.await(30, TimeUnit.SECONDS);
                System.out.println("预热操作完成");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("预热操作被中断");
            }
        }

        /**
         * 执行基准测试
         */
        private PerformanceResult performBenchmark(PerformanceResult result, BenchmarkOperation operation) throws InterruptedException {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);
            AtomicLong totalResponseTime = new AtomicLong(0);
            
            List<Long> responseTimes = config.isEnableDetailedMetrics() ? 
                Collections.synchronizedList(new ArrayList<>()) : null;
            
            int totalOperations = config.getConcurrentUsers() * config.getOperationsPerUser();
            CountDownLatch latch = new CountDownLatch(totalOperations);
            
            long startTime = System.currentTimeMillis();
            
            // 并发执行操作
            for (int i = 0; i < config.getConcurrentUsers(); i++) {
                final int userId = i;
                for (int j = 0; j < config.getOperationsPerUser(); j++) {
                    final int operationId = userId * config.getOperationsPerUser() + j;
                    
                    executorService.submit(() -> {
                        try {
                            long operationStart = System.nanoTime();
                            operation.execute(operationId);
                            long operationEnd = System.nanoTime();
                            
                            long responseTime = (operationEnd - operationStart) / 1_000_000; // 转换为毫秒
                            totalResponseTime.addAndGet(responseTime);
                            
                            if (responseTimes != null) {
                                responseTimes.add(responseTime);
                            }
                            
                            successCount.incrementAndGet();
                            
                        } catch (Exception e) {
                            failureCount.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }
            }
            
            // 等待所有操作完成
            latch.await(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();
            
            // 计算性能指标
            calculateMetrics(result, successCount.get(), failureCount.get(), 
                           totalResponseTime.get(), endTime - startTime, responseTimes);
            
            return result;
        }

        /**
         * 计算性能指标
         */
        private void calculateMetrics(PerformanceResult result, int successCount, int failureCount,
                                    long totalResponseTime, long totalTime, List<Long> responseTimes) {
            
            result.setTotalOperations(successCount + failureCount);
            result.setSuccessfulOperations(successCount);
            result.setFailedOperations(failureCount);
            result.setTotalTimeMs(totalTime);
            
            if (successCount > 0) {
                result.setThroughputPerSecond((double) successCount / (totalTime / 1000.0));
                result.setAverageResponseTimeMs((double) totalResponseTime / successCount);
                result.setSuccessRate((double) successCount / (successCount + failureCount) * 100);
                
                if (responseTimes != null && !responseTimes.isEmpty()) {
                    Collections.sort(responseTimes);
                    result.setMinResponseTimeMs(responseTimes.get(0));
                    result.setMaxResponseTimeMs(responseTimes.get(responseTimes.size() - 1));
                    result.setP95ResponseTimeMs(getPercentile(responseTimes, 0.95));
                    result.setP99ResponseTimeMs(getPercentile(responseTimes, 0.99));
                }
            }
        }

        /**
         * 计算百分位数
         */
        private double getPercentile(List<Long> sortedList, double percentile) {
            int index = (int) Math.ceil(sortedList.size() * percentile) - 1;
            index = Math.max(0, Math.min(index, sortedList.size() - 1));
            return sortedList.get(index);
        }

        public void shutdown() {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 性能对比报告生成器
     */
    public static class ComparisonReportGenerator {
        
        /**
         * 生成对比报告
         */
        public static String generateComparisonReport(List<PerformanceResult> results) {
            StringBuilder report = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            report.append("=".repeat(80)).append("\n");
            report.append("DragonToken 性能对比测试报告\n");
            report.append("生成时间: ").append(dateFormat.format(new Date())).append("\n");
            report.append("=".repeat(80)).append("\n\n");
            
            // 按测试名称分组
            Map<String, List<PerformanceResult>> groupedResults = results.stream()
                .collect(Collectors.groupingBy(PerformanceResult::getTestName));
            
            for (Map.Entry<String, List<PerformanceResult>> entry : groupedResults.entrySet()) {
                String testName = entry.getKey();
                List<PerformanceResult> testResults = entry.getValue();
                
                report.append("测试项目: ").append(testName).append("\n");
                report.append("-".repeat(60)).append("\n");
                
                // 生成对比表格
                generateComparisonTable(report, testResults);
                
                // 生成性能分析
                generatePerformanceAnalysis(report, testResults);
                
                report.append("\n");
            }
            
            // 生成总体结论
            generateOverallConclusion(report, results);
            
            return report.toString();
        }

        /**
         * 生成对比表格
         */
        private static void generateComparisonTable(StringBuilder report, List<PerformanceResult> results) {
            report.append(String.format("%-15s %-12s %-12s %-15s %-15s %-12s %-12s\n",
                "环境", "总操作数", "成功操作", "吞吐量(ops/s)", "平均响应时间(ms)", "成功率(%)", "P95响应时间"));
            report.append("-".repeat(100)).append("\n");
            
            for (PerformanceResult result : results) {
                report.append(String.format("%-15s %-12d %-12d %-15.2f %-15.2f %-12.2f %-12.2f\n",
                    result.getEnvironment(),
                    result.getTotalOperations(),
                    result.getSuccessfulOperations(),
                    result.getThroughputPerSecond(),
                    result.getAverageResponseTimeMs(),
                    result.getSuccessRate(),
                    result.getP95ResponseTimeMs()));
            }
            report.append("\n");
        }

        /**
         * 生成性能分析
         */
        private static void generatePerformanceAnalysis(StringBuilder report, List<PerformanceResult> results) {
            if (results.size() < 2) return;
            
            // 找出单体和微服务环境的结果
            PerformanceResult monolithicResult = results.stream()
                .filter(r -> r.getEnvironment().contains("单体") || r.getEnvironment().contains("Monolithic"))
                .findFirst().orElse(null);
            
            PerformanceResult microserviceResult = results.stream()
                .filter(r -> r.getEnvironment().contains("微服务") || r.getEnvironment().contains("Microservice"))
                .findFirst().orElse(null);
            
            if (monolithicResult != null && microserviceResult != null) {
                report.append("性能对比分析:\n");
                
                // 吞吐量对比
                double throughputRatio = monolithicResult.getThroughputPerSecond() / microserviceResult.getThroughputPerSecond();
                report.append(String.format("• 吞吐量对比: 单体环境 %.2f ops/s vs 微服务环境 %.2f ops/s (比率: %.2fx)\n",
                    monolithicResult.getThroughputPerSecond(),
                    microserviceResult.getThroughputPerSecond(),
                    throughputRatio));
                
                // 响应时间对比
                double responseTimeRatio = microserviceResult.getAverageResponseTimeMs() / monolithicResult.getAverageResponseTimeMs();
                report.append(String.format("• 响应时间对比: 单体环境 %.2f ms vs 微服务环境 %.2f ms (微服务慢 %.2fx)\n",
                    monolithicResult.getAverageResponseTimeMs(),
                    microserviceResult.getAverageResponseTimeMs(),
                    responseTimeRatio));
                
                // 成功率对比
                report.append(String.format("• 成功率对比: 单体环境 %.2f%% vs 微服务环境 %.2f%%\n",
                    monolithicResult.getSuccessRate(),
                    microserviceResult.getSuccessRate()));
                
                // 性能优势分析
                if (throughputRatio > 1.2) {
                    report.append("• 单体环境在吞吐量方面具有显著优势\n");
                } else if (throughputRatio < 0.8) {
                    report.append("• 微服务环境在吞吐量方面具有显著优势\n");
                } else {
                    report.append("• 两种环境的吞吐量相当\n");
                }
                
                report.append("\n");
            }
        }

        /**
         * 生成总体结论
         */
        private static void generateOverallConclusion(StringBuilder report, List<PerformanceResult> results) {
            report.append("总体结论:\n");
            report.append("=".repeat(40)).append("\n");
            
            // 计算平均性能指标
            Map<String, Double> monolithicAvg = calculateAverageMetrics(results, "单体");
            Map<String, Double> microserviceAvg = calculateAverageMetrics(results, "微服务");
            
            if (!monolithicAvg.isEmpty() && !microserviceAvg.isEmpty()) {
                double avgThroughputRatio = monolithicAvg.get("throughput") / microserviceAvg.get("throughput");
                double avgResponseTimeRatio = microserviceAvg.get("responseTime") / monolithicAvg.get("responseTime");
                
                report.append(String.format("1. 并发性能: 单体环境平均吞吐量为 %.2f ops/s，微服务环境为 %.2f ops/s\n",
                    monolithicAvg.get("throughput"), microserviceAvg.get("throughput")));
                
                if (avgThroughputRatio > 1.1) {
                    report.append("   结论: 单体环境具有更高的并发处理能力\n");
                } else if (avgThroughputRatio < 0.9) {
                    report.append("   结论: 微服务环境具有更高的并发处理能力\n");
                } else {
                    report.append("   结论: 两种环境的并发处理能力相当\n");
                }
                
                report.append(String.format("2. 响应延迟: 单体环境平均响应时间为 %.2f ms，微服务环境为 %.2f ms\n",
                    monolithicAvg.get("responseTime"), microserviceAvg.get("responseTime")));
                
                report.append("3. 适用场景建议:\n");
                if (avgThroughputRatio > 1.2) {
                    report.append("   • 对于高并发、低延迟要求的场景，推荐使用单体架构\n");
                    report.append("   • 单体架构在性能方面具有明显优势，适合性能敏感的应用\n");
                } else {
                    report.append("   • 微服务架构在分布式特性方面具有优势，适合大规模分布式系统\n");
                    report.append("   • 虽然性能略有损失，但提供了更好的可扩展性和容错性\n");
                }
                
                report.append("4. 优化建议:\n");
                report.append("   • 单体环境: 优化JVM参数、数据库连接池、缓存策略\n");
                report.append("   • 微服务环境: 优化网络通信、分布式缓存、服务发现机制\n");
            }
            
            report.append("\n");
            report.append("=".repeat(80)).append("\n");
        }

        /**
         * 计算平均性能指标
         */
        private static Map<String, Double> calculateAverageMetrics(List<PerformanceResult> results, String environmentType) {
            List<PerformanceResult> filteredResults = results.stream()
                .filter(r -> r.getEnvironment().contains(environmentType))
                .collect(Collectors.toList());
            
            if (filteredResults.isEmpty()) {
                return new HashMap<>();
            }
            
            Map<String, Double> avgMetrics = new HashMap<>();
            avgMetrics.put("throughput", filteredResults.stream()
                .mapToDouble(PerformanceResult::getThroughputPerSecond)
                .average().orElse(0.0));
            avgMetrics.put("responseTime", filteredResults.stream()
                .mapToDouble(PerformanceResult::getAverageResponseTimeMs)
                .average().orElse(0.0));
            avgMetrics.put("successRate", filteredResults.stream()
                .mapToDouble(PerformanceResult::getSuccessRate)
                .average().orElse(0.0));
            
            return avgMetrics;
        }
    }

    /**
     * 预定义的基准测试配置
     */
    public static class BenchmarkConfigs {
        
        public static BenchmarkConfig getLightLoadConfig() {
            return new BenchmarkConfig(500, 5, 25);
        }
        
        public static BenchmarkConfig getMediumLoadConfig() {
            return new BenchmarkConfig(1000, 10, 50);
        }
        
        public static BenchmarkConfig getHeavyLoadConfig() {
            BenchmarkConfig config = new BenchmarkConfig(2000, 15, 100);
            config.setTimeoutSeconds(180);
            return config;
        }
        
        public static BenchmarkConfig getStressTestConfig() {
            BenchmarkConfig config = new BenchmarkConfig(5000, 20, 200);
            config.setTimeoutSeconds(300);
            config.setEnableWarmup(true);
            config.setWarmupOperations(200);
            return config;
        }
        
        /**
         * 10分钟长时间测试配置
         * 适用于长期性能稳定性测试
         */
        public static BenchmarkConfig getTenMinuteTestConfig() {
            BenchmarkConfig config = new BenchmarkConfig(1000, 600, 100); // 1000用户，每用户600次操作，100线程池
            config.setTimeoutSeconds(720); // 12分钟超时，留出缓冲时间
            config.setEnableWarmup(true);
            config.setWarmupOperations(100);
            config.setEnableDetailedMetrics(true);
            return config;
        }
    }
}