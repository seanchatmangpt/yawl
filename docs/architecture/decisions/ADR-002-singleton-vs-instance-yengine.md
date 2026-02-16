# ADR-002: Singleton vs Instance-based YEngine

## Status
**ACCEPTED with CAVEATS**

**Current:** Singleton pattern maintained for backward compatibility
**Future:** Migration path documented for instance-based architecture

## Context

YEngine has historically used the singleton pattern (`YEngine.getInstance()`) for global engine access. This design was appropriate for single-server deployments but creates challenges for modern cloud-native architectures.

### Current Implementation

```java
public class YEngine {
    private static YEngine _instance;

    public static synchronized YEngine getInstance() {
        if (_instance == null) {
            _instance = new YEngine();
        }
        return _instance;
    }

    // Engine operations
}
```

### Problems with Singleton

1. **Horizontal Scaling Limitations**
   - Cannot run multiple engine instances per JVM
   - Load balancing requires sticky sessions or external state
   - Cannot partition workload within single process

2. **Testing Difficulties**
   - Singleton state persists between tests
   - Difficult to reset for test isolation
   - Mock/stub injection problematic

3. **Configuration Rigidity**
   - Single configuration for all operations
   - Cannot have different configurations per tenant
   - No multi-tenancy support within JVM

4. **Resource Contention**
   - Single database connection pool
   - Shared thread pools
   - Contention on singleton lock

5. **Cloud-Native Incompatibility**
   - Kubernetes expects stateless pods
   - Difficult to implement Circuit Breaker per tenant
   - Cannot isolate failures

### Benefits of Current Singleton

1. **Backward Compatibility**
   - Existing codebases work without changes
   - All tutorials and documentation valid
   - No migration burden for users

2. **Simplicity**
   - No dependency injection needed
   - Simple initialization
   - Global accessibility

3. **Resource Sharing**
   - Single database connection pool
   - Shared caches
   - Reduced memory footprint

## Decision

**Phase 1 (v5.2): Maintain Singleton with Deprecation Warning**
- Keep `YEngine.getInstance()` functional
- Add deprecation JavaDoc
- Provide alternative instance-based constructor
- Document migration path

**Phase 2 (v5.3-v5.4): Dual API Support**
- Both singleton and instance-based APIs work
- Spring Boot applications use instance-based
- Legacy applications use singleton
- Migration guide published

**Phase 3 (v6.0): Singleton Removal**
- Remove `getInstance()` method
- Instance-based only
- Breaking change, major version bump

### Architecture

```
┌────────────────────────────────────────────────────────┐
│                 Application Layer                      │
└─────┬────────────────────────────────────────┬─────────┘
      │                                        │
      │ (v5.2+)                                │ (v6.0+)
      ▼                                        ▼
┌──────────────────┐                  ┌──────────────────┐
│ YEngine.         │                  │ new YEngine(     │
│   getInstance()  │ ─────────────>   │   config)        │
│ @Deprecated      │   Migration      │                  │
└──────────────────┘                  └──────────────────┘
      │                                        │
      └────────────────┬───────────────────────┘
                       ▼
            ┌────────────────────┐
            │   YEngineImpl      │
            │  (Shared Logic)    │
            └────────────────────┘
```

### Implementation Strategy

#### Phase 1 (v5.2): Deprecation

```java
public class YEngine {
    private static YEngine _instance;

    /**
     * @deprecated Use {@link #YEngine(YEngineConfig)} instead.
     * Singleton pattern will be removed in v6.0.
     */
    @Deprecated(since = "5.2", forRemoval = true)
    public static synchronized YEngine getInstance() {
        if (_instance == null) {
            _instance = new YEngine(YEngineConfig.defaultConfig());
        }
        return _instance;
    }

    // New instance-based constructor
    public YEngine(YEngineConfig config) {
        this.config = config;
        this.persistenceManager = new YPersistenceManager(config.getDataSource());
        this.eventDispatcher = new YEventDispatcher(config.getEventConfig());
        // Initialize with provided configuration
    }
}
```

#### Phase 2 (v5.3): Spring Boot Integration

```java
@Configuration
public class YawlEngineConfiguration {

    @Bean
    public YEngine yawlEngine(YEngineConfig config) {
        return new YEngine(config);
    }

    @Bean
    public YEngineConfig yawlEngineConfig(
            DataSource dataSource,
            @Value("${yawl.engine.max-threads}") int maxThreads) {
        return YEngineConfig.builder()
            .dataSource(dataSource)
            .maxThreads(maxThreads)
            .enableMetrics(true)
            .build();
    }
}
```

#### Phase 3 (v6.0): Remove Singleton

```java
public class YEngine {
    // Singleton method removed entirely

    public YEngine(YEngineConfig config) {
        // Only constructor
    }
}
```

## Consequences

### Positive

1. **Horizontal Scalability**
   - Multiple engine instances per JVM
   - Partition workload by tenant or specification
   - Better CPU utilization on multi-core systems

2. **Testability**
   - Each test creates fresh engine instance
   - No state leakage between tests
   - Easy to mock dependencies

3. **Configuration Flexibility**
   - Different configurations per instance
   - Multi-tenancy support
   - Per-tenant resource limits

4. **Cloud-Native Compatibility**
   - Kubernetes-friendly stateless design
   - Circuit breaker per tenant
   - Failure isolation

5. **Dependency Injection**
   - Spring/CDI integration
   - Better lifecycle management
   - Easier testing with mocks

### Negative

1. **Breaking Change (v6.0)**
   - All existing code must migrate
   - Update all documentation
   - Customer impact analysis required

2. **Migration Effort**
   - Refactor existing codebases
   - Update integration tests
   - Retrain developers

3. **Increased Complexity**
   - Dependency injection required
   - Configuration management
   - More boilerplate code

4. **Resource Overhead**
   - Multiple connection pools possible
   - Higher memory footprint
   - Potential resource waste

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Customer resistance to breaking change | HIGH | MEDIUM | Long deprecation cycle (3 versions) |
| Migration bugs | MEDIUM | HIGH | Comprehensive test suite, migration guide |
| Performance regression | LOW | MEDIUM | Benchmark before/after |
| Documentation lag | MEDIUM | MEDIUM | Update docs in parallel |

## Alternatives Considered

### Alternative 1: Keep Singleton Forever
**Rejected:** Blocks horizontal scaling and cloud-native adoption.

**Pros:**
- No breaking changes
- No migration effort
- Backward compatibility

**Cons:**
- Cannot horizontally scale within JVM
- Poor testability
- Cloud-native limitations

### Alternative 2: Immediate Removal (v5.2)
**Rejected:** Too disruptive, not enough deprecation time.

**Pros:**
- Clean architecture immediately
- No legacy code

**Cons:**
- Customer impact too high
- No migration runway
- Support burden

### Alternative 3: Both Patterns Forever
**Rejected:** Maintains technical debt, confusing API.

**Pros:**
- No forced migration
- Flexibility

**Cons:**
- Dual maintenance
- Confusing for new users
- Technical debt accumulates

## Related ADRs

- ADR-001: Dual Engine Architecture (stateful vs stateless)
- ADR-010: Virtual Threads for Scalability (concurrency model)
- ADR-004: Spring Boot 3.4 + Java 25 (dependency injection)

## Implementation Notes

### Migration Guide for v6.0

**Before (v5.x):**
```java
// Singleton access
YEngine engine = YEngine.getInstance();
String caseId = engine.createCase(specId, data);
```

**After (v6.0):**
```java
// Dependency injection
@Autowired
private YEngine engine;

// Or manual instantiation
YEngineConfig config = YEngineConfig.builder()
    .dataSource(dataSource)
    .build();
YEngine engine = new YEngine(config);

String caseId = engine.createCase(specId, data);
```

### Configuration Example

```java
public class YEngineConfig {
    private DataSource dataSource;
    private int maxThreads = 200;
    private boolean enableMetrics = true;
    private Duration transactionTimeout = Duration.ofMinutes(5);

    public static Builder builder() {
        return new Builder();
    }

    public static YEngineConfig defaultConfig() {
        return builder()
            .dataSource(createDefaultDataSource())
            .build();
    }

    public static class Builder {
        // Fluent builder pattern
    }
}
```

### Spring Boot Starter

Create `yawl-spring-boot-starter` module:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-spring-boot-starter</artifactId>
    <version>5.3.0</version>
</dependency>
```

Auto-configuration:
```java
@Configuration
@ConditionalOnClass(YEngine.class)
@EnableConfigurationProperties(YawlProperties.class)
public class YawlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public YEngine yawlEngine(YawlProperties properties, DataSource dataSource) {
        YEngineConfig config = YEngineConfig.builder()
            .dataSource(dataSource)
            .maxThreads(properties.getMaxThreads())
            .enableMetrics(properties.isEnableMetrics())
            .build();
        return new YEngine(config);
    }
}
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-10
**Implementation Status:** PHASE 1 (Deprecation warnings added)
**Next Review:** 2026-05-01 (Phase 2 planning)

---

**Revision History:**
- 2026-02-10: Initial version approved
- 2026-02-16: Added Spring Boot integration plan
