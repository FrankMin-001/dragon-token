package com.smalldragon.yml.demo;

import com.smalldragon.yml.factory.LoginStrategyFactory;
import com.smalldragon.yml.manager.LoginInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * LoginStrategyFactory 使用演示
 * 
 * @author smalldragon
 * @version 1.0.0
 */
@Component
public class LoginStrategyDemo {

    @Autowired
    private LoginStrategyFactory loginStrategyFactory;

    /**
     * 演示基本的登录策略使用
     */
    public void demonstrateBasicUsage() {
        System.out.println("=== DragonToken 登录策略演示 ===");
        
        // 获取当前策略类型
        String currentStrategy = loginStrategyFactory.getCurrentStrategyType();
        System.out.println("当前策略类型: " + currentStrategy);
        
        // 获取当前策略描述
        String description = loginStrategyFactory.getStrategyDescription();
        System.out.println("策略描述: " + description);
        
        // 获取登录实现
        LoginInterface loginImpl = loginStrategyFactory.getLoginStrategy();
        System.out.println("登录实现类: " + loginImpl.getClass().getSimpleName());
        
        // 检查策略有效性
        boolean isValid = loginStrategyFactory.isValidStrategy();
        System.out.println("策略是否有效: " + isValid);
    }

    /**
     * 演示不同策略模式的检查
     */
    public void demonstrateModeCheckers() {
        System.out.println("\n=== 策略模式检查演示 ===");
        
        System.out.println("是否为SESSION模式: " + loginStrategyFactory.isSessionMode());
        System.out.println("是否为JWT模式: " + loginStrategyFactory.isJwtMode());
        System.out.println("是否为STATELESS模式: " + loginStrategyFactory.isStatelessMode());
    }

    /**
     * 演示动态策略切换（仅用于演示，实际使用中不建议动态切换）
     */
    public void demonstrateDynamicStrategy() {
        System.out.println("\n=== 动态策略演示 ===");
        
        String[] strategies = {"SESSION", "JWT", "STATELESS"};
        
        for (String strategy : strategies) {
            // 注意：这里仅用于演示，实际生产环境中应通过配置文件设置策略
            System.out.println("\n演示 " + strategy + " 模式:");
            
            // 获取特定策略的实现
            LoginInterface strategyImpl = loginStrategyFactory.getLoginStrategy(strategy);
            System.out.println("  - 策略类型: " + strategy);
            System.out.println("  - 实现类: " + strategyImpl.getClass().getSimpleName());
        }
    }

    /**
     * 演示特定策略的获取
     */
    public void demonstrateSpecificStrategy() {
        System.out.println("\n=== 特定策略获取演示 ===");
        
        // 获取SESSION策略
        LoginInterface sessionLogin = loginStrategyFactory.getLoginStrategy("SESSION");
        System.out.println("SESSION策略实现: " + sessionLogin.getClass().getSimpleName());
        
        // 获取JWT策略
        LoginInterface jwtLogin = loginStrategyFactory.getLoginStrategy("JWT");
        System.out.println("JWT策略实现: " + jwtLogin.getClass().getSimpleName());
        
        // 获取STATELESS策略
        LoginInterface statelessLogin = loginStrategyFactory.getLoginStrategy("STATELESS");
        System.out.println("STATELESS策略实现: " + statelessLogin.getClass().getSimpleName());
        
        // 获取未知策略（会返回默认的SESSION）
        LoginInterface unknownLogin = loginStrategyFactory.getLoginStrategy("UNKNOWN");
        System.out.println("未知策略实现（默认SESSION）: " + unknownLogin.getClass().getSimpleName());
    }

    /**
     * 运行完整演示
     */
    public void runFullDemo() {
        demonstrateBasicUsage();
        demonstrateModeCheckers();
        demonstrateSpecificStrategy();
        demonstrateDynamicStrategy();
        
        System.out.println("\n=== 演示完成 ===");
        System.out.println("LoginStrategyFactory 提供了统一的登录策略管理接口，");
        System.out.println("支持SESSION、JWT和STATELESS三种模式的自动选择和管理。");
    }
}