# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DragonToken is a Java web lightweight authentication and authorization framework inspired by SA-Token. It provides comprehensive security features including login authentication, permission verification, role-based access control, session management, and multi-tenant support.

### Multi-Tenant Architecture

The framework now includes comprehensive multi-tenant support with complete data isolation:
- **Tenant Isolation**: Cache keys, permissions, and user data are isolated by tenant
- **Backward Compatibility**: All existing APIs remain functional for gradual migration
- **Thread-Safe Context**: Request-scoped user context with automatic lifecycle management
- **Flexible Data Model**: Extensible UserContext for custom business requirements

### Core Features

- **Authentication**: Multiple strategies (SESSION, JWT, STATELESS) with automatic switching
- **Authorization**: Annotation-based and programmatic permission checking
- **Session Management**: Redis-based sessions with hot refresh and performance monitoring
- **Multi-Tenant Support**: Complete data isolation with tenant-aware APIs
- **Context Management**: Thread-safe user context with automatic lifecycle handling
- **Performance Optimization**: Redis caching with 52,000+ ops/s throughput

## Architecture and Structure

This is a multi-module Maven project with the following structure:

- **dragon-token-core**: Main authentication framework module
  - `src/main/java/com/smalldragon/yml/` - Core package structure
  - `annotations/` - Security annotations (@DragonCheckPermission, @DragonCheckRole, @DragonIgnore)
  - `config/` - Spring Boot configuration classes (RedisSessionConfig, WebConfig)
  - `context/` - Application context and auto-configuration
  - `core/` - Core authentication utilities and interfaces (DragonUtils, StpInterface)
  - `factory/` - Strategy factory for different authentication modes
  - `interceptors/` - Authentication and permission interceptors
  - `manager/` - Token management interfaces and implementations
  - `service/` - Authentication and authorization services
  - `utils/` - Utility classes (JWT, session handling, validation)
  - `controller/` - REST controllers for auth and cache management

### Key Components

- **DragonTokenAutoConfiguration**: Spring Boot auto-configuration entry point
- **DragonUtils**: Main utility class for programmatic permission checks and user context access
- **TokenManager**: Core token management interface with multi-tenant support and strategy pattern implementation
- **AuthInterceptor**: Main authentication interceptor for request filtering with automatic user context management
- **DragonTokenProperties**: Configuration properties with extensive customization options
- **UserContext**: Core data structure containing user information with multi-tenant support
- **DragonContextHolder**: Thread-safe context manager using ThreadLocal for request-scoped user data
- **StpInterface**: Permission and role management interface with tenant isolation support

### Authentication Strategies

The framework supports three authentication strategies:
- **SESSION**: Traditional session-based authentication with Redis storage
- **JWT**: JWT token-based authentication
- **STATELESS**: Stateless authentication mode

## Build Commands

### Maven Commands

```bash
# Build the entire project
mvn clean install

# Build only the core module
mvn clean install -pl dragon-token-core

# Run tests
mvn test

# Run tests for specific module
mvn test -pl dragon-token-core

# Skip tests during build
mvn clean install -DskipTests

# Generate source jar
mvn source:jar

# Generate Javadoc
mvn javadoc:javadoc
```

### Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=DragonUtilsTest

# Run integration tests
mvn test -Dtest=*IntegrationTest

# Run performance tests
mvn test -Dtest=*PerformanceTest
```

## Configuration

### Application Configuration

The framework uses Spring Boot configuration under `dragon.token` prefix:

```yaml
dragon:
  token:
    name: DRAGON_TOKEN                    # Token name
    strategy-type: SESSION                # SESSION, JWT, STATELESS
    retention-time: 7200                  # Token retention time in seconds
    exclude-paths:                        # Whitelist paths
      - "/login"
      - "/public/**"
    redis:                                # Redis configuration for SESSION mode
      host: localhost
      port: 6379
      database: 0
```

### Configuration Files

- `application.yml` - Main configuration
- `application-microservice-example.yml` - Microservice deployment example
- `application-monolithic-example.yml` - Monolithic application example
- `application-cache-example.yml` - Cache configuration example
- `application-hotrefresh-example.yml` - Session hot refresh configuration

## Testing Infrastructure

### Test Utilities

The project includes comprehensive test utilities in `MockTestUtils`:
- `MockRedisConnectionFactory` - Mock Redis connection for unit tests
- `MockSessionRepository` - Mock session repository
- `MockSession` - Mock session implementation

### Test Categories

- **Unit Tests**: Individual component testing
- **Integration Tests**: End-to-end functionality testing
- **Performance Tests**: Load and performance benchmarking

## Key Development Patterns

### Permission Checking

The framework provides two approaches for permission verification:

1. **Annotation-based**:
```java
@DragonCheckPermission("user:read")
@DragonCheckRole(value = {"admin", "manager"}, mode = DragonCheckMode.OR)
public void someMethod() {}
```

2. **Programmatic**:
```java
@Resource
private DragonUtils dragonUtils;

public void checkAccess() {
    dragonUtils.checkPermissionOrThrow("user:read");
    boolean hasRole = dragonUtils.checkRole("admin");
}
```

### Strategy Pattern

Authentication is implemented using the Strategy pattern with `LoginStrategyFactory` supporting multiple authentication modes that can be switched via configuration.

### UserContext Management

The framework provides comprehensive user context management with thread-safe operations and multi-tenant support.

#### UserContext Structure

UserContext is the core data structure containing user authentication and session information:

```java
public class UserContext {
    private String userId;        // User identifier (core field)
    private String username;      // Display username
    private String role;          // User role
    private String tenantId;      // Tenant identifier for multi-tenant isolation
    private String token;         // Authentication token
    private String clientIp;      // Client IP address for security auditing
    private Map<String, Object> externalData;  // Custom extension data
    private String sessionId;     // Session identifier
    private String strategyType;  // Authentication strategy type (SESSION/JWT/STATELESS)
}
```

#### Context Lifecycle Management

**1. Initialization (Login Phase)**
```java
// During user login, create and populate UserContext
UserContext userContext = new UserContext();
userContext.setUserId("1001");
userContext.setUsername("admin");
userContext.setRole("ADMIN");
userContext.setTenantId("tenant_001");  // Critical for multi-tenant isolation
userContext.setClientIp(getClientIp(request));

// Cache user information with tenant isolation
tokenManager.cacheUserInfo(userContext);
```

**2. Request Processing (Automatic Management)**
```java
// AuthInterceptor automatically loads UserContext from Redis
// JWT Mode:
UserContext userContext = tokenManager.getUserInfoById(tenantId, userId);

// Session Mode:
UserContext userContext = tokenManager.getUserInfoBySessionId(sessionId);

// Set to ThreadLocal for request-wide access
DragonContextHolder.setContext(userContext);
```

**3. Request Completion (Automatic Cleanup)**
```java
// AuthInterceptor.afterCompletion automatically clears context
DragonContextHolder.clear();
```

#### DragonContextHolder: Thread-Safe Context Access

The framework uses ThreadLocal to ensure thread safety:

```java
public class DragonContextHolder {
    private static final ThreadLocal<UserContext> CONTEXT = new ThreadLocal<>();

    public static void setContext(UserContext context) {
        CONTEXT.set(context);
    }

    public static UserContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
```

#### Programmatic Context Access with DragonUtils

**1. Getting Current User Information**
```java
@Resource
private DragonUtils dragonUtils;

// Get current user ID
String userId = dragonUtils.getCurrentUserId();

// Get current tenant ID (multi-tenant key)
String tenantId = dragonUtils.getCurrentTenantId();

// Get user permissions (tenant-isolated)
List<String> permissions = dragonUtils.getCurrentUserPermissions();

// Get user roles (tenant-isolated)
List<String> roles = dragonUtils.getCurrentUserRoles();
```

**2. Direct Context Access**
```java
// Access complete user context
UserContext currentUser = DragonContextHolder.getContext();

if (currentUser != null) {
    System.out.println("User ID: " + currentUser.getUserId());
    System.out.println("Tenant ID: " + currentUser.getTenantId());
    System.out.println("Username: " + currentUser.getUsername());
    System.out.println("Role: " + currentUser.getRole());
    System.out.println("Client IP: " + currentUser.getClientIp());

    // Access extension data
    Map<String, Object> extData = currentUser.getExternalData();
    if (extData != null) {
        String department = (String) extData.get("department");
        // Process department-specific logic
    }
}
```

#### Multi-Tenant Data Isolation

**1. Cache Key Isolation**
```java
// Tenant-isolated cache keys prevent data leakage between tenants
// Format: dragon-token:tenant:{tenantId}:user:cache:{userId}
String userCacheKey = CacheKeyConstants.buildUserCacheKey("tenant_a", "user001");
String permissionKey = CacheKeyConstants.buildUserPermissionsKey("tenant_a", "user001");
String roleKey = CacheKeyConstants.buildUserRolesKey("tenant_a", "user001");

// Example isolation:
// tenant_a:user001 → dragon-token:tenant:tenant_a:user:cache:user001
// tenant_b:user001 → dragon-token:tenant:tenant_b:user:cache:user001
```

**2. Permission and Role Isolation**
```java
// Same user ID can have different permissions in different tenants
// Tenant A: user001 has admin permissions
List<String> tenantAPerms = stpInterface.getPermissionList("tenant_a", "user001");
// Result: ["user:read", "user:write", "admin:access"]

// Tenant B: user001 has only user permissions
List<String> tenantBPerms = stpInterface.getPermissionList("tenant_b", "user001");
// Result: ["user:read"]
```

**3. Data Filtering in Business Logic**
```java
@Service
public class OrderService {

    public List<Order> getOrders() {
        String tenantId = dragonUtils.getCurrentTenantId();

        // Always filter data by tenant ID to ensure isolation
        return orderRepository.findByTenantId(tenantId);
    }

    public Order getOrder(String orderId) {
        String tenantId = dragonUtils.getCurrentTenantId();

        // Ensure user can only access orders from their tenant
        Order order = orderRepository.findById(orderId);
        if (order != null && !tenantId.equals(order.getTenantId())) {
            throw new AccessDeniedException("Access denied: Order belongs to different tenant");
        }
        return order;
    }
}
```

#### Extension Data Management

**1. Setting Extension Data During Login**
```java
UserContext userContext = new UserContext();
Map<String, Object> extData = new HashMap<>();
extData.put("department", "IT");
extData.put("level", "senior");
extData.put("preferences", Map.of(
    "theme", "dark",
    "language", "zh-CN",
    "timezone", "Asia/Shanghai"
));
extData.put("metadata", Map.of(
    "lastLoginTime", Instant.now(),
    "loginDevice", "web"
));

userContext.setExternalData(extData);
```

**2. Using Extension Data in Business Logic**
```java
public void personalizeUserExperience() {
    UserContext context = DragonContextHolder.getContext();
    if (context != null && context.getExternalData() != null) {
        Map<String, Object> extData = context.getExternalData();

        // Apply user preferences
        Map<String, String> preferences = (Map<String, String>) extData.get("preferences");
        if (preferences != null) {
            applyTheme(preferences.get("theme"));
            setLanguage(preferences.get("language"));
            setTimezone(preferences.get("timezone"));
        }

        // Department-based access control
        String department = (String) extData.get("department");
        if ("HR".equals(department)) {
            // Grant HR-specific access
        }
    }
}
```

#### Context Usage Best Practices

**1. Thread Safety**
- Never manually modify UserContext during request processing
- Let the framework manage the lifecycle automatically
- Use DragonUtils for safe context access

**2. Performance Considerations**
- UserContext is cached in Redis for fast access
- Extension data should be lightweight to avoid performance impact
- Consider cache invalidation strategies for dynamic data

**3. Multi-Tenant Guidelines**
- Always filter database queries by tenant ID
- Use tenant-isolated cache keys
- Implement tenant-level data validation

**4. Security Considerations**
- Validate tenant ID changes during session updates
- Audit cross-tenant access attempts
- Implement tenant-level rate limiting

**5. Error Handling**
```java
public void safeContextAccess() {
    try {
        String userId = dragonUtils.getCurrentUserId();
        if (userId == null) {
            logger.warn("No user context available");
            return;
        }

        // Proceed with user-specific logic
    } catch (Exception e) {
        logger.error("Error accessing user context", e);
        // Handle gracefully without breaking the request
    }
}
```

#### Context Debugging and Monitoring

```java
// Add to request interceptor for debugging
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    UserContext context = DragonContextHolder.getContext();
    if (context != null) {
        logger.debug("Request context: userId={}, tenantId={}, ip={}",
            context.getUserId(), context.getTenantId(), context.getClientIp());
    }
    return true;
}
```

### Session Management

Advanced session management features include:
- Session hot refresh with debouncing
- Distributed session consistency
- Cache warmup capabilities
- Performance monitoring

## Performance Characteristics

Based on performance reports, the framework demonstrates:
- **High throughput**: 52,000+ ops/s for cache operations
- **Low latency**: Average 1.44ms response time
- **Scalability**: Performs 2x better than microservice alternatives in benchmark tests

## Dependencies

Key dependencies include:
- Spring Boot 2.7.18
- Hutool 5.8.21 (utility library)
- Redis/Caffeine (caching)
- JWT implementation
- FastJSON 2.0.43

## Development Guidelines

### Core Authentication Guidelines

1. **Authentication Strategy**: Always configure the appropriate strategy type based on deployment architecture
2. **Redis Configuration**: Required for SESSION and JWT modes
3. **Permission Design**: Use fine-grained permissions following the pattern `resource:action`
4. **Error Handling**: Leverage built-in exception classes for consistent error responses
5. **Testing**: Use provided mock utilities for comprehensive unit testing

### UserContext Management Guidelines

1. **Context Access**: Always use `DragonUtils` methods for safe context access instead of direct `DragonContextHolder` calls
2. **Lifecycle Management**: Never manually modify `UserContext` during request processing; let the framework manage it automatically
3. **Thread Safety**: Each request thread has its own context; never share context between threads
4. **Performance**: UserContext is cached in Redis; avoid storing large objects in `externalData`

### Multi-Tenant Development Guidelines

1. **Tenant Isolation**: Always include `tenantId` in database queries and cache operations:
   ```java
   // Correct: Tenant-filtered query
   List<Order> orders = orderRepository.findByTenantId(dragonUtils.getCurrentTenantId());

   // Incorrect: Unfiltered query (security risk)
   List<Order> orders = orderRepository.findAll();
   ```

2. **Cache Key Usage**: Use tenant-isolated cache key methods:
   ```java
   // Correct: Tenant-isolated caching
   String cacheKey = CacheKeyConstants.buildUserCacheKey(tenantId, userId);

   // Legacy: Non-isolated caching (deprecated)
   String legacyKey = CacheKeyConstants.buildUserCacheKeyLegacy(userId);
   ```

3. **Permission Isolation**: Use tenant-aware permission methods:
   ```java
   // Correct: Tenant-isolated permission check
   List<String> permissions = stpInterface.getPermissionList(tenantId, userId);

   // Legacy: Non-isolated permission check
   List<String> permissions = stpInterface.getPermissionList(userId);
   ```

4. **Security Validation**: Always validate tenant access in business logic:
   ```java
   public Order getOrder(String orderId) {
       String currentTenantId = dragonUtils.getCurrentTenantId();
       Order order = orderRepository.findById(orderId);

       // Critical: Validate tenant access
       if (order != null && !currentTenantId.equals(order.getTenantId())) {
           throw new AccessDeniedException("Cross-tenant access denied");
       }
       return order;
   }
   ```

### Programming Best Practices

1. **Permission Verification**:
   ```java
   // Annotation-based (simple cases)
   @DragonCheckPermission("user:read")
   public List<User> getUsers() { ... }

   // Programmatic (complex logic)
   public void performSensitiveOperation() {
       if (dragonUtils.checkPermission("admin:access")) {
           // Admin operation
       } else if (dragonUtils.checkPermission("user:read")) {
           // User operation
       }
   }
   ```

2. **Error Handling**:
   ```java
   public void safeContextOperation() {
       try {
           String userId = dragonUtils.getCurrentUserId();
           if (userId == null) {
               logger.warn("No user context available");
               return;
           }
           // Proceed with operation
       } catch (Exception e) {
           logger.error("Context access error", e);
           // Handle gracefully without breaking request
       }
   }
   ```

3. **Extension Data Usage**:
   ```java
   // During login: Set user preferences
   Map<String, Object> extData = new HashMap<>();
   extData.put("preferences", userPreferences);
   userContext.setExternalData(extData);

   // In business logic: Use preferences
   UserContext context = DragonContextHolder.getContext();
   if (context != null && context.getExternalData() != null) {
       Map<String, String> prefs = (Map<String, String>) context.getExternalData().get("preferences");
       applyUserPreferences(prefs);
   }
   ```

### Migration from Single-Tenant

When migrating existing applications to multi-tenant support:

1. **Backward Compatibility**: All existing APIs remain functional
2. **Gradual Migration**: Start by adding `tenantId` to new features
3. **Data Migration**: Ensure existing data has appropriate `tenantId` values
4. **Testing**: Use `MultiTenantExample` as reference for testing tenant isolation

### Debugging and Monitoring

1. **Context Logging**: Add context information to debug logs:
   ```java
   UserContext ctx = DragonContextHolder.getContext();
   logger.debug("Operation: userId={}, tenantId={}, ip={}",
       ctx.getUserId(), ctx.getTenantId(), ctx.getClientIp());
   ```

2. **Performance Monitoring**: Monitor context access patterns:
   ```java
   // Add to interceptors for performance tracking
   long startTime = System.currentTimeMillis();
   // ... business logic ...
   long duration = System.currentTimeMillis() - startTime;
   if (duration > 100) { // Log slow operations
       logger.warn("Slow operation: {}ms for userId={}", duration, dragonUtils.getCurrentUserId());
   }
   ```