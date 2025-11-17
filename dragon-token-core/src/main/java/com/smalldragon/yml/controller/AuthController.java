package com.smalldragon.yml.controller;

import com.smalldragon.yml.annotations.DragonIgnore;
import com.smalldragon.yml.context.DragonContextHolder;
import com.smalldragon.yml.context.UserContext;
import com.smalldragon.yml.exceptions.Result;
import com.smalldragon.yml.factory.LoginStrategyFactory;
import com.smalldragon.yml.manager.LoginInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证控制器示例
 * 展示如何使用LoginStrategyFactory进行统一登录处理
 * 
 * @author YML
 * @date 2025/01/11
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private LoginStrategyFactory loginStrategyFactory;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    @DragonIgnore  // 忽略权限检查
    public Result<UserContext> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            logger.info("用户 {} 尝试登录", request.getUsername());
            
            // 1. 这里应该验证用户身份（示例代码，实际项目中需要连接数据库）
            if (!"admin".equals(request.getUsername()) || !"123456".equals(request.getPassword())) {
                return Result.fail("用户名或密码错误");
            }
            
            // 2. 创建用户上下文
            UserContext userContext = new UserContext();
            userContext.setUserId("1001");
            userContext.setUsername(request.getUsername());
            userContext.setRole("ADMIN");
            userContext.setTenantId("tenant_001");
            userContext.setClientIp(getClientIp(httpRequest));
            
            // 3. 自动选择登录策略并执行登录
            LoginInterface loginStrategy = loginStrategyFactory.getLoginStrategy();
            UserContext loginResult = loginStrategy.login(userContext);
            
            logger.info("用户 {} 登录成功，使用策略: {}", 
                request.getUsername(), loginStrategyFactory.getCurrentStrategyType());
            
            return Result.success(loginResult);
            
        } catch (Exception e) {
            logger.error("登录失败", e);
            return Result.fail("登录失败：" + e.getMessage());
        }
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        try {
            // 记录登出前的用户信息
            UserContext context = DragonContextHolder.getContext();
            String username = context != null ? context.getUsername() : "unknown";
            
            // 自动选择登录策略并执行登出
            LoginInterface loginStrategy = loginStrategyFactory.getLoginStrategy();
            loginStrategy.loginOut();
            
            logger.info("用户 {} 登出成功", username);
            return Result.success();
            
        } catch (Exception e) {
            logger.error("登出失败", e);
            return Result.fail("登出失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public Result<UserContext> getCurrentUser() {
        UserContext context = DragonContextHolder.getContext();
        if (context == null) {
            return Result.fail("用户未登录");
        }
        return Result.success(context);
    }
    
    /**
     * 获取当前登录策略信息
     */
    @GetMapping("/strategy")
    @DragonIgnore
    public Result<StrategyInfo> getStrategyInfo() {
        StrategyInfo info = new StrategyInfo();
        info.setCurrentStrategy(loginStrategyFactory.getCurrentStrategyType());
        info.setDescription(loginStrategyFactory.getStrategyDescription());
        info.setValid(loginStrategyFactory.isValidStrategy());
        info.setSessionMode(loginStrategyFactory.isSessionMode());
        info.setJwtMode(loginStrategyFactory.isJwtMode());
        info.setStatelessMode(loginStrategyFactory.isStatelessMode());
        
        return Result.success(info);
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    
    /**
     * 登录请求模型
     */
    public static class LoginRequest {
        private String username;
        private String password;
        private String captcha;  // 验证码（可选）
        
        // getters and setters
        public String getUsername() {
            return username;
        }
        
        public void setUsername(String username) {
            this.username = username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public void setPassword(String password) {
            this.password = password;
        }
        
        public String getCaptcha() {
            return captcha;
        }
        
        public void setCaptcha(String captcha) {
            this.captcha = captcha;
        }
    }
    
    /**
     * 策略信息模型
     */
    public static class StrategyInfo {
        private String currentStrategy;
        private String description;
        private boolean valid;
        private boolean sessionMode;
        private boolean jwtMode;
        private boolean statelessMode;
        
        // getters and setters
        public String getCurrentStrategy() {
            return currentStrategy;
        }
        
        public void setCurrentStrategy(String currentStrategy) {
            this.currentStrategy = currentStrategy;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        public boolean isSessionMode() {
            return sessionMode;
        }
        
        public void setSessionMode(boolean sessionMode) {
            this.sessionMode = sessionMode;
        }
        
        public boolean isJwtMode() {
            return jwtMode;
        }
        
        public void setJwtMode(boolean jwtMode) {
            this.jwtMode = jwtMode;
        }
        
        public boolean isStatelessMode() {
            return statelessMode;
        }
        
        public void setStatelessMode(boolean statelessMode) {
            this.statelessMode = statelessMode;
        }
    }
}