# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DragonToken is a Java web lightweight authentication and authorization framework inspired by SA-Token. It provides comprehensive security features including login authentication, permission verification, role-based access control, and session management.

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
- **DragonUtils**: Main utility class for programmatic permission checks
- **TokenManager**: Core token management interface with strategy pattern implementation
- **AuthInterceptor**: Main authentication interceptor for request filtering
- **DragonTokenProperties**: Configuration properties with extensive customization options

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

1. **Authentication Strategy**: Always configure the appropriate strategy type based on deployment architecture
2. **Redis Configuration**: Required for SESSION and JWT modes
3. **Permission Design**: Use fine-grained permissions following the pattern `resource:action`
4. **Error Handling**: Leverage built-in exception classes for consistent error responses
5. **Testing**: Use provided mock utilities for comprehensive unit testing