# DragonToken

轻量级 Java Web 认证授权框架，受 SA-Token 启发设计

## ✨ 特性

- **三种认证模式**: SESSION（会话）、JWT（令牌）、STATELESS（无状态）
- **双架构支持**: 完美支持单体应用和微服务架构
- **智能缓存系统**:
  - 单体应用：52,000+ ops/s，平均响应时间 1.44ms
  - 微服务架构：分布式缓存一致性，自动故障转移
- **高级缓存功能**: 缓存预热、热刷新、版本控制
- **注解式权限**: 支持 `@DragonCheckPermission`、`@DragonCheckRole` 注解
- **编程式权限**: 提供 `DragonUtils` 工具类进行编程控制
- **Spring Boot 自动配置**: 开箱即用

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.smalldragon.yml</groupId>
    <artifactId>dragon-token-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置文件

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
    # Redis 配置（SESSION 和 JWT 模式需要）
    redis:
      host: localhost
      port: 6379
```

### 3. 启动使用

框架会自动启用，无需额外配置！

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
        // 检查权限（无权限抛出异常）
        dragonUtils.checkPermissionOrThrow("user:read");

        // 检查角色（返回 boolean）
        boolean isAdmin = dragonUtils.checkRole("admin");

        // 获取当前登录用户 ID
        String userId = dragonUtils.getLoginId().toString();

        // 用户登录
        dragonUtils.login(userId);

        // 用户登出
        dragonUtils.logout();
    }
}
```

### 用户登录示例

```java
@RestController
public class AuthController {

    @Resource
    private DragonUtils dragonUtils;

    @PostMapping("/login")
    public Result login(@RequestBody LoginRequest request) {
        // 1. 验证用户名密码
        User user = userService.validate(request.getUsername(), request.getPassword());
        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        // 2. 执行登录
        dragonUtils.login(user.getId());

        return Result.success("登录成功");
    }

    @PostMapping("/logout")
    public Result logout() {
        dragonUtils.logout();
        return Result.success("退出成功");
    }
}
```

## 🔧 认证模式说明

### SESSION 模式（推荐单体应用）
- **高性能缓存**: 52,000+ ops/s，平均响应时间 1.44ms
- **智能预热**: 应用启动时自动预热常用缓存数据
- **热刷新机制**: 用户操作自动触发会话延期，5分钟防抖
- **本地缓存优化**: 单体模式下的缓存优化策略
- **分布式部署**: 基于 Redis 的会话存储，支持集群

```yaml
dragon:
  token:
    strategy-type: SESSION
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
- **版本控制**: 缓存数据版本管理和冲突解决
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

```yaml
dragon:
  token:
    strategy-type: STATELESS
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

## 🎯 自定义权限

### 实现 StpInterface 接口

```java
@Component
public class MyPermissionImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 从数据库查询用户权限
        return permissionService.getUserPermissions((Long) loginId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        // 从数据库查询用户角色
        return roleService.getUserRoles((Long) loginId);
    }
}
```

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
    # 是否启用框架
    enabled: true
```

### 全局异常处理

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result handleNotLogin() {
        return Result.error(401, "请先登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result handleNotPermission() {
        return Result.error(403, "无操作权限");
    }

    @ExceptionHandler(NotRoleException.class)
    public Result handleNotRole() {
        return Result.error(403, "无操作角色");
    }
}
```

## 📚 最佳实践

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

## 🔍 常见问题

**Q: 如何切换认证模式？**
A: 修改 `dragon.token.strategy-type` 配置即可。

**Q: 如何获取当前用户信息？**
A: 使用 `dragonUtils.getLoginId()` 获取用户 ID，然后查询详细信息。

**Q: 支持单点登录吗？**
A: JWT 模式天然支持单点登录，SESSION 模式需要统一 Redis 实例。

**Q: 如何自定义拦截器？**
A: 继承 `AuthInterceptor` 并注册到 Spring 中。

## 📄 许可证

[MIT License](LICENSE)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

**DragonToken** - 让认证授权变得简单高效！