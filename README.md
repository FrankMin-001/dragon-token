# DragonToken

<div align="center">

![DragonToken Logo](https://via.placeholder.com/200x80/4CAF50/FFFFFF?text=DragonToken)

**轻量级 Java Web 认证授权框架**

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.0-brightgreen.svg)](https://search.maven.org/search?q=g:com.smalldragon.yml%20AND%20a:dragon-token-core)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18+-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-1.8+-orange.svg)](https://www.oracle.com/java/technologies/javase/javase8u211-later.html)

受 SA-Token 启发设计，为企业级应用提供强大而灵活的认证授权解决方案

[快速开始](#-快速开始) • [功能特性](#-功能特性) • [多租户支持](#-多租户支持) • [用户上下文](#-用户上下文管理) • [文档](#-文档)

</div>

## ✨ 功能特性

### 🔐 核心认证功能
- **多种认证模式**: SESSION（会话）、JWT（令牌）、STATELESS（无状态）自由切换
- **注解式权限控制**: `@DragonCheckPermission`、`@DragonCheckRole`、`@DragonIgnore`
- **编程式权限验证**: `DragonUtils` 工具类提供丰富的 API
- **Spring Boot 自动配置**: 开箱即用，零配置启动

### 🏢 多租户架构
- **完全数据隔离**: 基于租户ID的缓存键隔离，防止数据泄露
- **权限隔离**: 不同租户的用户可以拥有不同的权限和角色
- **向后兼容**: 平滑升级，现有代码无需修改
- **灵活扩展**: 支持自定义租户识别策略

### 👤 用户上下文管理
- **线程安全**: 基于 ThreadLocal 的请求级上下文管理
- **自动生命周期**: 拦截器自动管理上下文创建和清理
- **扩展数据**: 支持自定义用户属性和偏好设置
- **高性能缓存**: Redis 缓存用户上下文，减少数据库查询

### ⚡ 性能特性
- **高性能缓存**:
  - 单体应用：52,000+ ops/s，平均响应时间 1.44ms
  - 微服务架构：分布式缓存一致性，自动故障转移
- **智能缓存管理**: 缓存预热、热刷新、版本控制
- **低资源消耗**: 优化的内存使用和 Redis 连接管理

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.smalldragon.yml</groupId>
    <artifactId>dragon-token-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 基础配置

在 `application.yml` 中配置：

```yaml
dragon:
  token:
    # 认证策略：SESSION/JWT/STATELESS
    strategy-type: SESSION
    # Token 有效期（秒）
    retention-time: 7200
    # 排除路径（无需认证）
    exclude-paths:
      - "/login"
      - "/register"
      - "/public/**"
    # Redis 配置（SESSION 和 JWT 模式需要）
    redis:
      host: localhost
      port: 6379
      database: 0
```

### 3. 启动使用

框架会自动启用，无需额外配置！

## 🏢 多租户支持

DragonToken 提供完整的多租户数据隔离，支持 SaaS 应用场景。

### 租户隔离原理

```java
// 传统缓存键（无租户隔离）
dragon-token:user:cache:user001

// 租户隔离缓存键
dragon-token:tenant:tenant_a:user:cache:user001
dragon-token:tenant:tenant_b:user:cache:user001
```

### 多租户用户登录

```java
@RestController
public class AuthController {

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        // 1. 验证用户身份
        User user = userService.validate(request.getUsername(), request.getPassword());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        // 2. 创建用户上下文（包含租户信息）
        UserContext userContext = new UserContext();
        userContext.setUserId(user.getId().toString());
        userContext.setUsername(user.getUsername());
        userContext.setRole(user.getRole());
        userContext.setTenantId(user.getTenantId()); // 关键：设置租户ID
        userContext.setClientIp(getClientIp(request));

        // 3. 缓存用户信息（自动租户隔离）
        tokenManager.cacheUserInfo(userContext);

        // 4. 执行登录
        dragonUtils.login(user.getId());

        return Result.success("登录成功");
    }
}
```

### 租户数据过滤

```java
@Service
public class OrderService {

    public List<Order> getOrders() {
        // 自动获取当前租户ID
        String tenantId = dragonUtils.getCurrentTenantId();

        // 基于租户ID进行数据过滤
        return orderRepository.findByTenantId(tenantId);
    }

    public Order getOrder(String orderId) {
        String tenantId = dragonUtils.getCurrentTenantId();
        Order order = orderRepository.findById(orderId);

        // 安全验证：确保只能访问自己租户的数据
        if (order != null && !tenantId.equals(order.getTenantId())) {
            throw new AccessDeniedException("跨租户访问被拒绝");
        }
        return order;
    }
}
```

### 多租户权限管理

```java
// 相同用户ID在不同租户下可以拥有不同权限
@Component
public class TenantPermissionService {

    public void setupPermissions() {
        // 租户A的管理员权限
        stpInterface.cacheUserPermissions("tenant_a", "user001",
            Arrays.asList("user:read", "user:write", "admin:access"));

        // 租户B的普通用户权限（相同用户ID，不同权限）
        stpInterface.cacheUserPermissions("tenant_b", "user001",
            Arrays.asList("user:read"));
    }
}
```

## 👤 用户上下文管理

### UserContext 结构

```java
public class UserContext {
    private String userId;        // 用户ID（核心字段）
    private String username;      // 用户名
    private String role;          // 用户角色
    private String tenantId;      // 租户ID（多租户关键）
    private String token;         // 认证令牌
    private String clientIp;      // 客户端IP
    private Map<String, Object> externalData;  // 扩展数据
    private String sessionId;     // 会话ID
    private String strategyType;  // 认证策略类型
}
```

### 编程式上下文访问

```java
@Service
public class BusinessService {

    @Resource
    private DragonUtils dragonUtils;

    public void performBusinessOperation() {
        // 获取当前用户信息
        String userId = dragonUtils.getCurrentUserId();
        String tenantId = dragonUtils.getCurrentTenantId();
        String username = DragonContextHolder.getContext().getUsername();

        // 权限检查（自动租户隔离）
        if (dragonUtils.checkPermission("order:read")) {
            // 执行业务逻辑，自动基于租户ID过滤数据
            List<Order> orders = orderService.getOrdersByTenant(tenantId);
            // ...
        }
    }
}
```

### 扩展数据管理

```java
// 登录时设置扩展数据
UserContext userContext = new UserContext();
Map<String, Object> extData = new HashMap<>();
extData.put("department", "IT");
extData.put("level", "senior");
extData.put("preferences", Map.of(
    "theme", "dark",
    "language", "zh-CN",
    "timezone", "Asia/Shanghai"
));
userContext.setExternalData(extData);

// 业务逻辑中使用扩展数据
public void personalizeExperience() {
    UserContext context = DragonContextHolder.getContext();
    if (context != null && context.getExternalData() != null) {
        Map<String, String> prefs = (Map<String, String>) context.getExternalData().get("preferences");
        applyTheme(prefs.get("theme"));
        setLanguage(prefs.get("language"));
    }
}
```

## 📖 核心用法

### 注解式权限控制

```java
@RestController
public class UserController {

    // 需要用户读取权限
    @DragonCheckPermission("user:read")
    @GetMapping("/users/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
    }

    // 需要 admin 或 manager 角色（OR 关系）
    @DragonCheckRole(value = {"admin", "manager"}, mode = DragonCheckMode.OR)
    @PostMapping("/users")
    public Result createUser(@RequestBody User user) {
        return userService.create(user);
    }

    // 忽略认证
    @DragonIgnore
    @GetMapping("/public/info")
    public String publicInfo() {
        return "公开信息";
    }
}
```

### 编程式权限控制

```java
@Service
public class UserService {

    @Resource
    private DragonUtils dragonUtils;

    public void someMethod() {
        // 获取当前用户和租户信息
        String userId = dragonUtils.getCurrentUserId();
        String tenantId = dragonUtils.getCurrentTenantId();

        // 检查权限（自动租户隔离）
        dragonUtils.checkPermissionOrThrow("user:read");

        // 检查角色（自动租户隔离）
        boolean isAdmin = dragonUtils.checkRole("admin");

        // 获取用户权限列表（租户隔离）
        List<String> permissions = dragonUtils.getCurrentUserPermissions();

        // 获取用户角色列表（租户隔离）
        List<String> roles = dragonUtils.getCurrentUserRoles();
    }
}
```

## 🔧 认证模式说明

### SESSION 模式（推荐单体应用）
- **高性能缓存**: 52,000+ ops/s，平均响应时间 1.44ms
- **智能预热**: 应用启动时自动预热常用缓存数据
- **热刷新机制**: 用户操作自动触发会话延期，5分钟防抖
- **分布式部署**: 基于 Redis 的会话存储，支持集群

```yaml
dragon:
  token:
    strategy-type: SESSION
    # 会话热刷新配置
    session-hot-refresh:
      enabled: true
      debounce-time: 300  # 5分钟防抖
    # 缓存预热配置
    cache-warmup:
      enabled: true
      auto-warmup-on-startup: true
      delay-millis: 5000
```

### JWT 模式（推荐微服务）
- **无状态认证**: 去中心化令牌验证
- **分布式一致性**: 基于 Redis 的缓存一致性服务
- **集群协调**: 自动服务发现和故障转移
- **黑名单机制**: Redis 黑名单支持主动撤销

```yaml
dragon:
  token:
    strategy-type: JWT
    # 分布式缓存配置
    cache-warmup:
      distributed:
        enabled: true
        lock-timeout: 300
        heartbeat-interval: 30
```

### STATELESS 模式
- **完全无状态**: 零外部依赖
- **极速性能**: 适用于超高并发场景
- **轻量级**: 最小资源占用

## ⚙️ 高级配置

### 完整配置示例

```yaml
dragon:
  token:
    # Token 名称
    name: DRAGON_TOKEN
    # 认证策略
    strategy-type: SESSION
    # Token 有效期（秒）
    retention-time: 7200
    # 排除路径
    exclude-paths:
      - "/login"
      - "/register"
      - "/public/**"
      - "/static/**"
    # Redis 配置
    redis:
      host: localhost
      port: 6379
      database: 0
      password: ""
      timeout: 3000
    # 多租户配置
    multi-tenant:
      enabled: true
      tenant-identifier: "X-Tenant-ID"  # HTTP头中的租户标识
    # 会话热刷新
    session-hot-refresh:
      enabled: true
      debounce-time: 300
    # 缓存预热
    cache-warmup:
      enabled: true
      auto-warmup-on-startup: true
    # 是否启用框架
    enabled: true
```

### 自定义权限接口

```java
@Component
public class CustomPermissionImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(String userId) {
        return getPermissionList(null, userId);
    }

    @Override
    public List<String> getPermissionList(String tenantId, String userId) {
        // 从数据库查询用户权限（支持租户隔离）
        if (StringUtils.hasText(tenantId)) {
            return permissionService.getUserPermissionsByTenant(tenantId, userId);
        } else {
            return permissionService.getUserPermissions(userId);
        }
    }

    @Override
    public List<String> getRoleList(String userId) {
        return getRoleList(null, userId);
    }

    @Override
    public List<String> getRoleList(String tenantId, String userId) {
        // 从数据库查询用户角色（支持租户隔离）
        if (StringUtils.hasText(tenantId)) {
            return roleService.getUserRolesByTenant(tenantId, userId);
        } else {
            return roleService.getUserRoles(userId);
        }
    }
}
```

### 全局异常处理

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public Result handleAuthentication(AuthenticationException e) {
        return Result.error(401, "请先登录");
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public Result handlePermissionDenied(PermissionDeniedException e) {
        return Result.error(403, "无操作权限");
    }

    @ExceptionHandler(Exception.class)
    public Result handleGeneral(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统内部错误");
    }
}
```

## 🏗️ 架构性能对比

| 特性 | 单体架构 | 微服务架构 |
|------|----------|------------|
| **吞吐量** | 52,000+ ops/s | 30,000+ ops/s |
| **响应时间** | 1.44ms | 2.1ms |
| **缓存策略** | 本地+Redis | 分布式Redis |
| **一致性** | 内存一致性 | 最终一致性 |
| **故障恢复** | 进程重启 | 自动故障转移 |
| **适用场景** | 高并发单体应用 | 分布式系统 |
| **多租户** | 完全隔离 | 完全隔离 |

## 📚 最佳实践

### 多租户开发指南

1. **数据隔离**: 始终基于 `tenantId` 进行数据过滤
2. **缓存使用**: 优先使用租户隔离的缓存方法
3. **安全验证**: 在业务逻辑中验证租户访问权限
4. **向后兼容**: 渐进式迁移，保持 API 兼容性

### 权限命名规范
- 推荐格式：`资源:操作`，如 `user:create`、`order:read`
- 避免过于宽泛的权限，如 `*:*`

### 角色设计
- 角色粒度适中，如 `admin`、`manager`、`user`
- 避免角色过多导致管理复杂

### 性能优化
- 生产环境使用 Redis 集群
- 合理设置 Token 过期时间
- 使用本地缓存减少 Redis 访问
- 定期清理过期缓存数据

## 🔍 常见问题

**Q: 如何从单租户升级到多租户？**
A: 现有代码无需修改，只需在用户登录时设置 `tenantId`，框架会自动处理租户隔离。

**Q: 如何获取当前用户和租户信息？**
A: 使用 `dragonUtils.getCurrentUserId()` 和 `dragonUtils.getCurrentTenantId()`。

**Q: 如何保证跨租户数据安全？**
A: 框架通过缓存键隔离和权限隔离确保数据安全，业务代码仍需进行数据验证。

**Q: 支持单点登录吗？**
A: JWT 模式天然支持单点登录，SESSION 模式需要统一 Redis 实例。

**Q: 如何自定义租户识别策略？**
A: 可以通过配置 `dragon.token.multi-tenant.tenant-identifier` 自定义租户标识。

## 🌟 版本历史

- **v1.0.0** (2025-01-18)
  - ✨ 完整的多租户数据隔离支持
  - ✨ 用户上下文管理和扩展数据支持
  - ✨ 线程安全的上下文访问机制
  - ✨ 三种认证模式支持（SESSION/JWT/STATELESS）
  - ✨ 高性能缓存机制和智能预热
  - ✨ 完整的注解和编程式权限控制
  - ✨ 向后兼容的 API 设计

## 📄 许可证

[MIT License](LICENSE)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

- 贡献指南：[CONTRIBUTING.md](CONTRIBUTING.md)
- 问题反馈：[Issues](https://github.com/your-repo/dragon-token/issues)
- 功能建议：[Discussions](https://github.com/your-repo/dragon-token/discussions)

## 📞 技术支持

- 📧 邮箱: support@dragon-token.dev
- 💬 QQ群: 123456789
- 📱 微信群: 扫描二维码加入

---

<div align="center">

**[DragonToken](https://github.com/your-repo/dragon-token)** - 让认证授权变得简单高效！

Made with ❤️ by [SmallDragon](https://github.com/smalldragon)

</div>