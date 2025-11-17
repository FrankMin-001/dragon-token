# DragonToken 配置参考

## 完整配置项

```yaml
dragon:
  token:
    # ===== 基础配置 =====
    # Token 名称（请求头、Cookie 中的键名）
    name: DRAGON_TOKEN

    # 认证策略：SESSION/JWT/STATELESS
    strategy-type: SESSION

    # Token 有效期（秒），0 表示永不过期
    retention-time: 7200

    # 是否启用框架
    enabled: true

    # ===== 路径排除配置 =====
    # 排除路径（无需认证的路径）
    exclude-paths:
      - "/login"
      - "/register"
      - "/logout"
      - "/public/**"
      - "/static/**"
      - "/swagger-ui/**"
      - "/v3/api-docs/**"

    # ===== Redis 配置 =====
    redis:
      # Redis 服务器地址
      host: localhost

      # Redis 端口
      port: 6379

      # Redis 数据库索引
      database: 0

      # Redis 密码（有密码时填写）
      password: ""

      # 连接超时时间（毫秒）
      timeout: 3000

      # 连接池配置
      lettuce:
        pool:
          # 最大连接数
          max-active: 8
          # 最大空闲连接数
          max-idle: 8
          # 最小空闲连接数
          min-idle: 0
          # 连接超时时间（毫秒）
          max-wait: -1ms

    # ===== 高级配置 =====
    # Token 前缀
    token-prefix: "Bearer "

    # 是否在响应头中返回 Token
    include-token-in-header: true

    # Token 刷新策略：REFRESH/EXPIRE
    refresh-policy: REFRESH

    # ===== 缓存预热配置 =====
    cache-warmup:
      # 是否启用缓存预热
      enabled: true
      # 应用启动后延迟时间（毫秒）
      delay-millis: 5000
      # 是否在应用启动时自动预热
      auto-warmup-on-startup: true
      # 预热超时时间（毫秒）
      timeout-millis: 30000

      # 分布式配置（微服务环境）
      distributed:
        # 是否启用分布式模式
        enabled: false
        # 分布式锁超时时间（秒）
        lock-timeout: 300
        # 心跳间隔（秒）
        heartbeat-interval: 30
        # 服务超时时间（秒）
        service-timeout: 90
        # 版本检查间隔（秒）
        version-check-interval: 60
        # 事件通知频道
        event-channel: "dragon:token:cluster:events"

    # ===== 会话热刷新配置 =====
    session-hot-refresh:
      # 是否启用会话热刷新
      enabled: true
      # 刷新间隔（秒）
      interval: 300
      # 防抖时间（毫秒）- 避免频繁刷新
      debounce: 1000
      # 刷新阈值 - session剩余时间少于总时间的百分比时才刷新
      refresh-threshold: 0.33

    # ===== 数据一致性配置 =====
    consistency:
      # 是否启用一致性检查
      enabled: true
      # 一致性检查间隔（秒）
      check-interval: 60
      # 冲突解决策略：VERSION/TIMESTAMP/PRIORITY
      conflict-resolution-strategy: VERSION
      # 最大重试次数
      max-retry-attempts: 3
```

## 环境配置文件

### 开发环境 (application-dev.yml)

```yaml
dragon:
  token:
    strategy-type: SESSION
    retention-time: 3600  # 1小时，便于开发调试
    exclude-paths:
      - "/login"
      - "/register"
      - "/debug/**"
    redis:
      host: localhost
      port: 6379
      database: 0
    hot-refresh:
      enabled: true
      interval: 60  # 1分钟刷新，开发环境更频繁
```

### 测试环境 (application-test.yml)

```yaml
dragon:
  token:
    strategy-type: JWT
    retention-time: 1800  # 30分钟
    exclude-paths:
      - "/login"
      - "/register"
      - "/health"
    redis:
      host: test-redis.internal
      port: 6379
      database: 1
```

### 生产环境 (application-prod.yml)

```yaml
dragon:
  token:
    strategy-type: JWT
    retention-time: 7200  # 2小时
    exclude-paths:
      - "/login"
      - "/register"
      - "/health"
      - "/actuator/**"
    redis:
      cluster:
        nodes:
          - redis1.internal:6379
          - redis2.internal:6379
          - redis3.internal:6379
      password: ${REDIS_PASSWORD}
      database: 0
    hot-refresh:
      enabled: true
      interval: 600  # 10分钟刷新
      debounce: 5000
```

## Java 配置类

### 自定义配置类

```java
@Configuration
@EnableConfigurationProperties(DragonTokenProperties.class)
public class DragonTokenConfig {

    @Bean
    @ConditionalOnMissingBean
    public DragonTokenProperties dragonTokenProperties() {
        return new DragonTokenProperties();
    }

    @Bean
    @ConditionalOnMissingBean
    public StpInterface stpInterface() {
        // 使用默认实现
        return new StpInterfaceImpl();
    }

    @Bean
    @ConditionalOnProperty(name = "dragon.token.hot-refresh.enabled", havingValue = "true")
    public SessionHotRefreshUtil sessionHotRefreshUtil() {
        return new SessionHotRefreshUtil();
    }
}
```

### 自定义权限实现

```java
@Component
public class CustomStpInterface implements StpInterface {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());

        // 获取用户角色
        List<Role> roles = roleService.getUserRoles(userId);

        // 获取角色权限
        Set<String> permissions = new HashSet<>();
        for (Role role : roles) {
            permissions.addAll(permissionService.getRolePermissions(role.getId()));
        }

        return new ArrayList<>(permissions);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());
        List<Role> roles = roleService.getUserRoles(userId);
        return roles.stream()
                .map(Role::getCode)
                .collect(Collectors.toList());
    }
}
```

## 拦截器配置

### 自定义拦截器

```java
@Component
public class CustomAuthInterceptor extends AuthInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预处理逻辑
        String uri = request.getRequestURI();

        // API 版本控制
        if (uri.startsWith("/api/v1/")) {
            // V1 版本特殊处理
            request.setAttribute("apiVersion", "v1");
        }

        // 调用父类方法
        return super.preHandle(request, response, handler);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 后处理逻辑
        // 记录访问日志
        logAccess(request, response);

        super.afterCompletion(request, response, handler, ex);
    }

    private void logAccess(HttpServletRequest request, HttpServletResponse response) {
        // 自定义日志逻辑
    }
}
```

### Web 配置

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/login",
                    "/register",
                    "/logout",
                    "/public/**",
                    "/static/**",
                    "/error",
                    "/actuator/**"
                )
                .order(1); // 设置拦截器顺序
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

## 注解详细说明

### @DragonCheckPermission

```java
// 基础用法
@DragonCheckPermission("user:read")
public Result getUser() { }

// 多个权限（AND 关系）
@DragonCheckPermission({"user:read", "order:read"})
public Result getUserAndOrder() { }

// 指定模式
@DragonCheckPermission(
    value = "user:delete",
    mode = DragonCheckMode.AND  // AND 模式（默认）
)
public Result deleteUser() { }
```

### @DragonCheckRole

```java
// 单个角色
@DragonCheckRole("admin")
public Result adminOnly() { }

// 多个角色（OR 关系）
@DragonCheckRole(value = {"admin", "manager"}, mode = DragonCheckMode.OR)
public Result adminOrManager() { }

// 多个角色（AND 关系）
@DragonCheckRole(value = {"admin", "auditor"}, mode = DragonCheckMode.AND)
public Result adminAndAuditor() { }
```

### @DragonIgnore

```java
// 忽略当前方法的认证
@DragonIgnore
public Result publicApi() { }

// 忽略整个类的认证
@RestController
@DragonIgnore
public class PublicController {
    // 所有方法都不需要认证
}
```

## 异常处理

### 异常类型

```java
// 未登录异常
@ExceptionHandler(NotLoginException.class)
public Result handleNotLogin(NotLoginException e) {
    String message = switch (e.getMessage()) {
        case NotLoginException.NOT_TOKEN -> "未提供 Token";
        case NotLoginException.INVALID_TOKEN -> "Token 无效";
        case NotLoginException.TOKEN_TIMEOUT -> "Token 已过期";
        case NotLoginException.BE_REPLACED -> "Token 已被替换";
        case NotLoginException.KICK_OUT -> "Token 已被踢下线";
        default -> "当前会话未登录";
    };
    return Result.error(401, message);
}

// 无权限异常
@ExceptionHandler(NotPermissionException.class)
public Result handleNotPermission(NotPermissionException e) {
    return Result.error(403, "无权限操作: " + e.getPermission());
}

// 无角色异常
@ExceptionHandler(NotRoleException.class)
public Result handleNotRole(NotRoleException e) {
    return Result.error(403, "无角色访问: " + e.getRole());
}
```

## 性能优化建议

### Redis 配置优化

```yaml
dragon:
  token:
    redis:
      lettuce:
        pool:
          max-active: 20        # 增加连接池大小
          max-idle: 10
          min-idle: 5
          max-wait: 3000ms
      shutdown-timeout: 100ms  # 优雅关闭超时
```

### 缓存策略

```java
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        return cacheManager;
    }
}
```

### 监控配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

## 调试配置

### 开发环境调试

```yaml
logging:
  level:
    com.smalldragon.yml: DEBUG
    org.springframework.web: DEBUG

dragon:
  token:
    hot-refresh:
      enabled: false  # 开发时可关闭热刷新
```

### 测试环境模拟

```java
@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public TokenManager mockTokenManager() {
        return new MockTokenManager();
    }
}
```