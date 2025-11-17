package com.smalldragon.yml.controller;

import com.smalldragon.yml.service.CacheWarmupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存管理控制器
 * 提供缓存预热、清理等管理功能的REST API
 * 
 * @Author YML
 * @Date 2025/01/20
 */
@RestController
@RequestMapping("/dragon-token/cache")
public class CacheManagementController {

    private static final Logger logger = LoggerFactory.getLogger(CacheManagementController.class);

    @Autowired
    private CacheWarmupService cacheWarmupService;

    /**
     * 手动触发缓存预热
     * 
     * @return 预热结果
     */
    @PostMapping("/warmup")
    public ResponseEntity<Map<String, Object>> warmupCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            cacheWarmupService.manualWarmup();
            long endTime = System.currentTimeMillis();
            
            result.put("success", true);
            result.put("message", "缓存预热成功");
            result.put("duration", endTime - startTime + "ms");
            
            logger.info("手动缓存预热成功，耗时: {}ms", endTime - startTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "缓存预热失败: " + e.getMessage());
            
            logger.error("手动缓存预热失败", e);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 清理所有缓存
     * 
     * @return 清理结果
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            cacheWarmupService.clearAllCache();
            long endTime = System.currentTimeMillis();
            
            result.put("success", true);
            result.put("message", "缓存清理成功");
            result.put("duration", endTime - startTime + "ms");
            
            logger.info("缓存清理成功，耗时: {}ms", endTime - startTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "缓存清理失败: " + e.getMessage());
            
            logger.error("缓存清理失败", e);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 重新初始化缓存（清理后预热）
     * 
     * @return 初始化结果
     */
    @PostMapping("/reinitialize")
    public ResponseEntity<Map<String, Object>> reinitializeCache() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 先清理缓存
            cacheWarmupService.clearAllCache();
            
            // 再预热缓存
            cacheWarmupService.manualWarmup();
            
            long endTime = System.currentTimeMillis();
            
            result.put("success", true);
            result.put("message", "缓存重新初始化成功");
            result.put("duration", endTime - startTime + "ms");
            
            logger.info("缓存重新初始化成功，耗时: {}ms", endTime - startTime);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "缓存重新初始化失败: " + e.getMessage());
            
            logger.error("缓存重新初始化失败", e);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取缓存状态信息
     * 
     * @return 缓存状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 这里可以添加更详细的缓存状态检查逻辑
            result.put("success", true);
            result.put("message", "缓存状态正常");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取缓存状态失败: " + e.getMessage());
            
            logger.error("获取缓存状态失败", e);
            return ResponseEntity.internalServerError().body(result);
        }
    }
}