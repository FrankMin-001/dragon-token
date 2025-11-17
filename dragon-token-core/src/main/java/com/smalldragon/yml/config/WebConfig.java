package com.smalldragon.yml.config;

import com.smalldragon.yml.interceptors.AuthInterceptor;
import com.smalldragon.yml.interceptors.constants.InterceptorOrder;
import com.smalldragon.yml.propertity.DragonTokenProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;
    
    @Resource
    private DragonTokenProperties dragonTokenProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 登录认证及鉴权拦截器
        registry.addInterceptor(authInterceptor)
                .order(InterceptorOrder.AUTHENTICATION)
                .addPathPatterns("/**")
                .excludePathPatterns(dragonTokenProperties.getWhitePaths());

    }



}
