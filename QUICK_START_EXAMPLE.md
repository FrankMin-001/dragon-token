# DragonToken 快速上手示例

## 示例项目结构

```
dragon-token-demo/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/example/demo/
        │       ├── DemoApplication.java
        │       ├── config/
        │       │   └── WebConfig.java
        │       ├── controller/
        │       │   ├── AuthController.java
        │       │   ├── UserController.java
        │       │   └── AdminController.java
        │       ├── service/
        │       │   ├── AuthService.java
        │       │   ├── UserService.java
        │       │   └── PermissionImpl.java
        │       ├── model/
        │       │   ├── User.java
        │       │   ├── LoginRequest.java
        │       │   └── Result.java
        │       └── exception/
        │           └── GlobalExceptionHandler.java
        └── resources/
            ├── application.yml
            └── application-dev.yml
```

## 1. 创建 Spring Boot 项目

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>dragon-token-demo</artifactId>
    <version>1.0.0</version>
    <name>dragon-token-demo</name>

    <properties>
        <java.version>1.8</java.version>
    </properties>

    <dependencies>
        <!-- DragonToken 核心依赖 -->
        <dependency>
            <groupId>com.smalldragon.yml</groupId>
            <artifactId>dragon-token-core</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Redis 支持 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## 2. 配置文件

### application.yml

```yaml
server:
  port: 8080

spring:
  # Redis 配置
  redis:
    host: localhost
    port: 6379
    database: 0

# DragonToken 配置
dragon:
  token:
    # 使用 SESSION 模式
    strategy-type: SESSION
    # Token 有效期 2 小时
    retention-time: 7200
    # 排除路径
    exclude-paths:
      - "/login"
      - "/register"
      - "/public/**"
    # Redis 配置
    redis:
      host: ${spring.redis.host}
      port: ${spring.redis.port}
      database: ${spring.redis.database}

# 日志配置
logging:
  level:
    com.smalldragon.yml: DEBUG
    root: INFO
```

## 3. 启动类

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

## 4. 实体类

### User.java

```java
package com.example.demo.model;

import lombok.Data;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String role;
}
```

### LoginRequest.java

```java
package com.example.demo.model;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
```

### Result.java

```java
package com.example.demo.model;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}
```

## 5. 权限实现

### PermissionImpl.java

```java
package com.example.demo.service;

import com.smalldragon.yml.core.StpInterface;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class PermissionImpl implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());

        // 模拟从数据库查询用户权限
        if (userId == 1L) {
            // 管理员权限
            return Arrays.asList(
                "user:create", "user:read", "user:update", "user:delete",
                "order:create", "order:read", "order:update", "order:delete"
            );
        } else if (userId == 2L) {
            // 普通用户权限
            return Arrays.asList("order:read", "user:read");
        }

        return Arrays.asList(); // 默认无权限
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(loginId.toString());

        // 模拟从数据库查询用户角色
        if (userId == 1L) {
            return Arrays.asList("admin");
        } else if (userId == 2L) {
            return Arrays.asList("user");
        }

        return Arrays.asList(); // 默认无角色
    }
}
```

## 6. 认证控制器

### AuthController.java

```java
package com.example.demo.controller;

import com.example.demo.model.LoginRequest;
import com.example.demo.model.Result;
import com.smalldragon.yml.utils.DragonUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    // 模拟用户数据
    private static final java.util.Map<String, User> USERS = java.util.Map.of(
        "admin", createAdmin(),
        "user", createNormalUser()
    );

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginRequest request) {
        // 1. 验证用户名和密码
        User user = USERS.get(request.getUsername());
        if (user == null || !user.getPassword().equals(request.getPassword())) {
            return Result.error(401, "用户名或密码错误");
        }

        // 2. 执行登录
        DragonUtils.login(user.getId());

        return Result.success("登录成功，用户ID: " + user.getId());
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        DragonUtils.logout();
        return Result.success("退出成功");
    }

    @GetMapping("/current")
    public Result<String> getCurrentUser() {
        String userId = DragonUtils.getLoginId().toString();
        return Result.success("当前登录用户ID: " + userId);
    }

    private static User createAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPassword("admin123");
        admin.setEmail("admin@example.com");
        admin.setRole("admin");
        return admin;
    }

    private static User createNormalUser() {
        User user = new User();
        user.setId(2L);
        user.setUsername("user");
        user.setPassword("user123");
        user.setEmail("user@example.com");
        user.setRole("user");
        return user;
    }
}
```

## 7. 业务控制器

### UserController.java

```java
package com.example.demo.controller;

import com.example.demo.model.Result;
import com.smalldragon.yml.annotations.DragonCheckPermission;
import com.smalldragon.yml.annotations.DragonCheckRole;
import com.smalldragon.yml.utils.DragonUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class UserController {

    @DragonCheckPermission("user:read")
    @GetMapping("/{id}")
    public Result<String> getUser(@PathVariable Long id) {
        String currentUserId = DragonUtils.getLoginId().toString();
        return Result.success("查看用户 " + id + "，当前用户: " + currentUserId);
    }

    @DragonCheckPermission("user:create")
    @PostMapping
    public Result<String> createUser() {
        return Result.success("创建用户成功");
    }

    @DragonCheckPermission("user:update")
    @PutMapping("/{id}")
    public Result<String> updateUser(@PathVariable Long id) {
        return Result.success("更新用户 " + id + " 成功");
    }

    @DragonCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        return Result.success("删除用户 " + id + " 成功");
    }
}
```

### AdminController.java

```java
package com.example.demo.controller;

import com.example.demo.model.Result;
import com.smalldragon.yml.annotations.DragonCheckRole;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @DragonCheckRole("admin")
    @GetMapping("/dashboard")
    public Result<String> dashboard() {
        return Result.success("管理员仪表板数据");
    }

    @DragonCheckRole(value = {"admin", "manager"})
    @GetMapping("/reports")
    public Result<String> reports() {
        return Result.success("系统报表数据");
    }

    @GetMapping("/public")
    public Result<String> publicInfo() {
        return Result.success("公开信息，无需认证");
    }
}
```

## 8. 异常处理

### GlobalExceptionHandler.java

```java
package com.example.demo.exception;

import com.example.demo.model.Result;
import com.smalldragon.yml.exception.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public Result<String> handleNotLogin() {
        return Result.error(401, "请先登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<String> handleNotPermission(NotPermissionException e) {
        return Result.error(403, "无操作权限: " + e.getPermission());
    }

    @ExceptionHandler(NotRoleException.class)
    public Result<String> handleNotRole(NotRoleException e) {
        return Result.error(403, "无操作角色: " + e.getRole());
    }
}
```

## 9. 运行和测试

### 启动应用

```bash
mvn spring-boot:run
```

### 测试接口

#### 1. 公开接口（无需认证）
```bash
curl -X GET http://localhost:8080/admin/public
# 返回: {"code":200,"message":"success","data":"公开信息，无需认证"}
```

#### 2. 用户登录
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 返回: {"code":200,"message":"success","data":"登录成功，用户ID: 1"}
```

#### 3. 获取当前用户
```bash
curl -X GET http://localhost:8080/auth/current \
  -H "DRAGON_TOKEN: 1"  # 使用登录返回的用户ID作为Token

# 返回: {"code":200,"message":"success","data":"当前登录用户ID: 1"}
```

#### 4. 需要权限的接口
```bash
curl -X GET http://localhost:8080/users/1 \
  -H "DRAGON_TOKEN: 1"

# 返回: {"code":200,"message":"success","data":"查看用户 1，当前用户: 1"}
```

#### 5. 无权限访问
```bash
# 普通用户登录
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"user123"}'

# 普通用户尝试删除用户（需要 admin 角色）
curl -X DELETE http://localhost:8080/users/1 \
  -H "DRAGON_TOKEN: 2"

# 返回: {"code":403,"message":"无操作角色: admin"}
```

## 10. 功能扩展

### 添加 JWT 模式

修改 `application.yml`：

```yaml
dragon:
  token:
    strategy-type: JWT  # 改为 JWT 模式
    retention-time: 7200
```

重启应用后，所有功能继续可用，只是认证方式变为 JWT。

### 添加自定义拦截器

```java
package com.example.demo.config;

import com.smalldragon.yml.interceptors.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/public/**");
    }
}
```

## 总结

这个示例展示了：

1. **基础配置**: Maven 依赖、应用配置
2. **用户认证**: 登录、登出、获取当前用户
3. **权限控制**: 注解式权限、角色验证
4. **异常处理**: 统一异常响应
5. **多模式支持**: SESSION/JWT 模式切换

基于这个示例，您可以快速构建完整的认证授权系统！