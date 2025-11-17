# DragonToken 使用指南

## 项目简介

DragonToken 是一个轻量级的 Java Web 认证授权框架，受 SA-Token 启发设计。提供了完整的认证、授权、会话管理功能，支持多种认证策略。

## Maven 引入

在您的 Spring Boot 项目中添加 DragonToken 依赖：

```xml
<dependency>
    <groupId>com.smalldragon.yml</groupId>
    <artifactId>dragon-token-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 基础配置

在 `application.yml` 中配置 DragonToken：

```yaml
dragon:
  token:
    # Token 名称 (默认: DRAGON_TOKEN)
    name: DRAGON_TOKEN
    # 认证策略: SESSION(会话), JWT(令牌), STATELESS(无状态)
    strategy-type: SESSION
    # Token 有效期 (秒)
    retention-time: 7200
    # 排除路径 (无需认证的路径)
    exclude-paths:
      - "/login"
      - "/register"
      - "/public/**"
    # Redis 配置 (用于 SESSION 和 JWT 模式)
    redis:
      host: localhost
      port: 6379
      database: 0
      password: ""
      timeout: 3000
```

## 认证策略说明

### 1. SESSION 模式 (推荐用于单体应用)
- 基于 Redis 存储会话信息
- 支持会话热刷新
- 高性能，支持分布式

### 2. JWT 模式 (推荐用于微服务)
- 无状态令牌认证
- 支持 Redis 黑名单机制
- 适合微服务架构

### 3. STATELESS 模式
- 完全无状态认证
- 适用于高并发场景
- 不依赖外部存储

## 基本使用

### 1. 注解方式权限控制

```java
@RestController
@RequestMapping("/api/user")
public class UserController {

    @DragonCheckPermission("user:read")
    @GetMapping("/info")
    public UserInfo getUserInfo() {
        // 业务逻辑
        return userInfo;
    }

    @DragonCheckRole(value = {"admin", "manager"}, mode = DragonCheckMode.OR)
    @PostMapping("/create")
    public Result createUser() {
        // 业务逻辑
        return Result.success();
    }

    @DragonIgnore
    @GetMapping("/public")
    public String publicEndpoint() {
        // 无需认证的公开接口
        return "Public content";
    }
}
```

### 2. 编程方式权限检查

```java
@Service
public class UserService {

    @Resource
    private DragonUtils dragonUtils;

    public void someBusinessMethod() {
        // 检查权限，如果无权限会抛出异常
        dragonUtils.checkPermissionOrThrow("user:read");

        // 检查角色
        if (dragonUtils.checkRole("admin")) {
            // 管理员逻辑
        }

        // 获取当前登录用户
        String currentUserId = dragonUtils.getLoginId().toString();

        // 注销登录
        dragonUtils.logout();
    }
}
```

### 3. 登录认证

```java
@Service
public class AuthService {

    @Resource
    private DragonUtils dragonUtils;

    public Result login(String username, String password) {
        // 1. 验证用户名和密码
        User user = userService.validateUser(username, password);
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        // 2. 执行登录
        dragonUtils.login(user.getId());

        // 3. 返回登录成功信息
        return Result.success("登录成功");
    }
}
```

## 高级功能

### 1. 自定义权限验证

```java
@Component
public class CustomPermissionImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 查询用户权限列表
        return permissionService.getUserPermissions((Long) loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 查询用户角色列表
        return roleService.getUserRoles((Long) loginId);
    }
}
```

### 2. 自定义拦截器

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Resource
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/register", "/public/**");
    }
}
```

### 3. 会话热刷新

```java
@Service
public class SessionService {

    @Resource
    private SessionHotRefreshUtil hotRefreshUtil;

    public void refreshUserSession(Long userId, UserInfo newUserInfo) {
        // 热刷新用户会话信息
        hotRefreshUtil.refreshSession(userId.toString(), newUserInfo);
    }
}
```

## 最佳实践

### 1. 权限设计
- 推荐使用 `资源:操作` 格式，如 `user:create`, `order:read`
- 细粒度权限控制，避免过于宽泛的权限

### 2. 异常处理
```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result handleNotLogin(NotLoginException e) {
        return Result.error(401, "请先登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result handleNotPermission(NotPermissionException e) {
        return Result.error(403, "无操作权限");
    }
}
```

### 3. 配置建议
- 生产环境建议配置 Redis 集群
- 合理设置 Token 过期时间
- 定期清理过期会话

## 性能特性

- **高并发支持**: 52,000+ ops/s
- **低延迟**: 平均响应时间 1.44ms
- **内存优化**: 支持本地缓存 + 分布式缓存
- **水平扩展**: 支持微服务架构部署

## 常见问题

### Q: 如何切换认证策略？
A: 修改配置文件中的 `dragon.token.strategy-type` 即可，无需修改代码。

### Q: 如何自定义配置？
A: 实现 `StpInterface` 接口并注入 Spring 容器。

### Q: 如何处理分布式会话？
A: 配置 Redis 集群地址，框架会自动处理分布式会话同步。

### Q: 如何实现单点登录？
A: 使用 JWT 模式或在 SESSION 模式下配置统一的 Redis 实例。

## 技术支持

- 项目地址: https://github.com/your-repo/dragon-token
- 问题反馈: https://github.com/your-repo/dragon-token/issues
- 文档: [完整文档链接]

## 版本历史

- **v1.0.0**: 初始版本，支持基础认证授权功能
  - 支持 SESSION、JWT、STATELESS 三种模式
  - 完整的注解和编程式权限控制
  - 高性能缓存机制