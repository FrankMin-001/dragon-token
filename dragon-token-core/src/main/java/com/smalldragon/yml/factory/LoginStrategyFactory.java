package com.smalldragon.yml.factory;

import com.smalldragon.yml.manager.LoginInterface;
import com.smalldragon.yml.manager.impl.JwtLoginImpl;
import com.smalldragon.yml.manager.impl.SessionLoginImpl;
import com.smalldragon.yml.manager.impl.StatelessLoginImpl;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 登录策略工厂类
 * 根据配置的策略类型自动返回对应的登录实现
 * 
 * @author YML
 * @date 2025/01/11
 */
@Component
public class LoginStrategyFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginStrategyFactory.class);
    
    @Autowired
    private SessionLoginImpl sessionLogin;
    
    @Autowired
    private JwtLoginImpl jwtLogin;
    
    @Autowired
    private StatelessLoginImpl statelessLogin;
    
    @Autowired
    private DragonTokenProperties dragonTokenProperties;
    
    /**
     * 根据当前配置的策略类型获取对应的登录实现
     * 
     * @return 对应的登录实现
     */
    public LoginInterface getLoginStrategy() {
        String strategyType = dragonTokenProperties.getStrategyType();
        return getLoginStrategy(strategyType);
    }
    
    /**
     * 根据指定的策略类型获取对应的登录实现
     * 
     * @param strategyType 策略类型 (SESSION, JWT, STATELESS)
     * @return 对应的登录实现
     */
    public LoginInterface getLoginStrategy(String strategyType) {
        if (strategyType == null) {
            logger.warn("策略类型为空，使用默认的SESSION模式");
            return sessionLogin;
        }
        
        switch (strategyType.toUpperCase()) {
            case "SESSION":
                logger.debug("使用SESSION登录策略");
                return sessionLogin;
            case "JWT":
                logger.debug("使用JWT登录策略");
                return jwtLogin;
            case "STATELESS":
                logger.debug("使用STATELESS登录策略");
                return statelessLogin;
            default:
                logger.warn("不支持的策略类型: {}, 使用默认的SESSION模式", strategyType);
                return sessionLogin;
        }
    }
    
    /**
     * 获取当前配置的策略类型
     * 
     * @return 当前策略类型
     */
    public String getCurrentStrategyType() {
        return dragonTokenProperties.getStrategyType();
    }
    
    /**
     * 检查当前是否为SESSION模式
     * 
     * @return true-SESSION模式，false-其他模式
     */
    public boolean isSessionMode() {
        return DragonTokenProperties.SESSION.equals(dragonTokenProperties.getStrategyType());
    }
    
    /**
     * 检查当前是否为JWT模式
     * 
     * @return true-JWT模式，false-其他模式
     */
    public boolean isJwtMode() {
        return DragonTokenProperties.JWT.equals(dragonTokenProperties.getStrategyType());
    }
    
    /**
     * 检查当前是否为STATELESS模式
     * 
     * @return true-STATELESS模式，false-其他模式
     */
    public boolean isStatelessMode() {
        return DragonTokenProperties.STATELESS.equals(dragonTokenProperties.getStrategyType());
    }
    
    /**
     * 获取当前策略的详细描述
     * 
     * @return 策略描述信息
     */
    public String getStrategyDescription() {
        String strategyType = dragonTokenProperties.getStrategyType();
        switch (strategyType) {
            case DragonTokenProperties.SESSION:
                return "SESSION模式 - 基于传统Session机制，支持热刷新，需要Redis";
            case DragonTokenProperties.JWT:
                return "JWT模式 - 基于JWT Token，支持分布式部署，需要Redis缓存";
            case DragonTokenProperties.STATELESS:
                return "STATELESS模式 - 完全无状态，性能最优，无需Redis";
            default:
                return "未知策略类型: " + strategyType;
        }
    }
    
    /**
     * 验证当前策略配置是否有效
     * 
     * @return true-有效，false-无效
     */
    public boolean isValidStrategy() {
        String strategyType = dragonTokenProperties.getStrategyType();
        return DragonTokenProperties.STRATEGY_TYPE_CONSTANTS.contains(strategyType);
    }
}