# YAWL Architecture Rules Documentation

This document describes the architecture compliance rules enforced by ArchUnit tests in Phase 2 of the modernization effort. These rules ensure YAWL follows Java 25 best practices, maintains clean architecture, and enforces observability standards.

## Overview

The architecture test suite consists of three main test classes:
- **ArchitectureTests.java** - Core architecture patterns and layering
- **SecurityArchitectureTests.java** - Security and authentication constraints
- **ObservabilityArchitectureTests.java** - Observability and monitoring requirements

## Running the Tests

```bash
# Run all architecture tests
bash scripts/dx.sh test -Dtest=ArchitectureTests,SecurityArchitectureTests,ObservabilityArchitectureTests

# Run specific test class
bash scripts/dx.sh test -Dtest=ArchitectureTests

# Run with verbose output
bash scripts/dx.sh test -Dtest=ArchitectureTests -X
```

---

## Architecture Rules

### 1. Resilience4j Pattern Enforcement

**Rule**: Classes in `org.yawlfoundation.yawl.resilience` must use Resilience4j framework exclusively.

**Why**: Resilience4j provides standardized, battle-tested implementations of circuit breaker, retry, and rate limiting patterns. Custom implementations introduce maintenance overhead and inconsistency.

**Compliant Code**:
```java
// Good: Using Resilience4j
private final CircuitBreaker circuitBreaker = CircuitBreaker.of("myservice",
    CircuitBreakerConfig.custom()
        .failureThreshold(5)
        .slowCallDurationThreshold(Duration.ofSeconds(2))
        .build()
);

public String callExternalService() {
    return circuitBreaker.executeSupplier(() -> externalApi.doWork());
}
```

**Non-Compliant Code**:
```java
// Bad: Custom circuit breaker implementation
public class CustomCircuitBreaker {
    private int failureCount = 0;

    public String execute() {
        if (failureCount > 5) {
            throw new RuntimeException("Circuit open");
        }
        // ...
    }
}
```

**Remediation**:
1. Replace custom implementations with Resilience4j equivalents
2. Remove custom circuit breaker, retry, and rate limiter classes
3. Import from `io.github.resilience4j.*` packages
4. Update pom.xml to include required Resilience4j dependencies

---

### 2. Java 25 Thread Safety

**Rule**: No `synchronized` blocks; use `ReentrantLock` instead for Java 25 virtual thread compatibility.

**Why**: Synchronized blocks pin virtual threads to OS threads, eliminating the primary benefit of virtual threads (millions of concurrent tasks). `ReentrantLock` allows virtual threads to unmount during blocking operations.

**Compliant Code**:
```java
// Good: Using ReentrantLock for Java 25
import java.util.concurrent.locks.ReentrantLock;

class WorkflowState {
    private final ReentrantLock stateLock = new ReentrantLock();
    private WorkflowStatus status = WorkflowStatus.CREATED;

    public void transitionTo(WorkflowStatus newStatus) {
        stateLock.lock();
        try {
            this.status = newStatus;
        } finally {
            stateLock.unlock();
        }
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Using synchronized blocks (pins virtual threads)
class WorkflowState {
    private WorkflowStatus status = WorkflowStatus.CREATED;

    public synchronized void transitionTo(WorkflowStatus newStatus) {
        this.status = newStatus; // PINS virtual thread!
    }
}
```

**Remediation**:
1. Find all `synchronized` keyword usages: `grep -r "synchronized" src/`
2. Replace with `ReentrantLock` + try-finally pattern
3. If true read-heavy workload, consider `ReentrantReadWriteLock` in observability code only

---

### 3. Dependency Layering

**Rule**: Modules must follow a clean dependency hierarchy:
- `Engine` (core) ← `Stateless`, `Resourcing`, `Integration`, etc.
- `Security` (cross-cutting) ← `Authentication`
- No circular dependencies between modules

**Why**: Clean layering enables:
- Independent testing and deployment
- Clear separation of concerns
- Ability to understand impact of changes
- Prevention of accidental coupling

**Module Hierarchy**:
```
Elements (data model)
    ↓
Engine (workflow execution)
    ↓ ← Stateless, Resourcing, Integration, Monitoring, etc.

Authentication (session/JWT)
    ↓
Security (PKI/digital signatures) - independent of resourcing
```

**Compliant Code**:
```java
// Good: Engine does NOT depend on Integration
package org.yawlfoundation.yawl.engine;

class YNetRunner {
    // Only depends on Elements, internal engine classes
    private YSpecification specification;
    private YWorkItem workItem;
}

// Integration depends on Engine (one-way dependency)
package org.yawlfoundation.yawl.integration;

class EventPublisher {
    private YEngine engine; // OKAY: Integration → Engine

    public void publishEngineEvent(YWorkItem item) {
        // ...
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Circular dependency - Engine imports Integration classes
package org.yawlfoundation.yawl.engine;

class YNetRunner {
    private EventPublisher publisher; // WRONG: Engine → Integration
}

package org.yawlfoundation.yawl.integration;

class EventPublisher {
    private YNetRunner runner; // ALSO WRONG: Circular
}
```

**Remediation**:
1. Run ArchUnit: identifies the circular dependency
2. Use observer/event pattern to break the cycle
3. Integration publishes events; Engine doesn't know about Integration
4. If Integration needs Engine state, use read-only accessor methods

---

### 4. Logging in Core Classes

**Rule**: Core engine classes (`YEngine`, `YNetRunner`, `YWorkItem`) may only use `Logger.debug()`, not `info/warn/error`.

**Why**:
- Reduces noise in logs during normal operation
- Core engine should be "silent unless told to be verbose"
- Warnings and errors go through dedicated handlers

**Compliant Code**:
```java
// Good: Core engine uses debug logging
class YNetRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(YNetRunner.class);

    public void executeTask(YTask task) {
        LOGGER.debug("Executing task: {} in case: {}", task.getName(), getCaseID());
        // ... execution logic
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Core engine logs warnings/errors directly
class YNetRunner {
    public void executeTask(YTask task) {
        LOGGER.warn("About to execute task: " + task.getName()); // NOT ALLOWED
        // ...
    }
}
```

**Remediation**:
1. Change `LOGGER.info/warn/error` to `LOGGER.debug` in engine classes
2. Use dedicated error handling classes for warnings/errors
3. Security/Authentication classes CAN use info/warn/error

---

### 5. Exception Handling

**Rule**: Exceptions caught must be logged OR rethrown; no silent swallowing.

**Why**: Silent exception swallowing hides bugs and makes debugging impossible in production.

**Compliant Code**:
```java
// Good: Exception logged and rethrown
try {
    workItem.execute();
} catch (TaskExecutionException e) {
    LOGGER.error("Task execution failed for workItem: {}", workItem.getId(), e);
    throw new WorkflowException("Cannot execute task: " + workItem.getName(), e);
}

// Also good: Just rethrow
try {
    workItem.execute();
} catch (TaskExecutionException e) {
    throw new WorkflowException("Cannot execute task: " + workItem.getName(), e);
}
```

**Non-Compliant Code**:
```java
// Bad: Exception swallowed silently
try {
    workItem.execute();
} catch (TaskExecutionException e) {
    // Silent failure - WRONG!
}

// Also bad: Empty catch with only printStackTrace
try {
    workItem.execute();
} catch (TaskExecutionException e) {
    e.printStackTrace(); // Not proper logging
}
```

**Remediation**:
1. Find all empty catch blocks: `grep -r "catch.*{" src/`
2. Log exception with context: `LOGGER.error("Operation failed", exception)`
3. Rethrow wrapped in domain exception if appropriate
4. Never silently ignore checked exceptions

---

### 6. Configuration Class Localization

**Rule**: All `@Configuration` and `*Properties` classes must be in `.config`, `.observability`, or `.properties` packages.

**Why**: Configuration is a cross-cutting concern; co-locating it makes it easy to find and audit.

**Compliant Code**:
```java
// Good: Configuration in dedicated package
package org.yawlfoundation.yawl.config;

@Configuration
public class ResilienceConfiguration {
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
    }
}

package org.yawlfoundation.yawl.config;

@ConfigurationProperties(prefix = "yawl.engine")
public class EngineProperties {
    private int maxCaseInstances;
    private Duration caseTimeout;
    // getters/setters
}
```

**Non-Compliant Code**:
```java
// Bad: Configuration mixed with business logic
package org.yawlfoundation.yawl.engine;

@Configuration
public class YNetRunner {
    // Configuration and business logic mixed - HARD TO FIND
}
```

**Remediation**:
1. Create `org.yawlfoundation.yawl.config` package
2. Move all `@Configuration` classes there
3. Move all `*Properties` classes to `.config`
4. Update package imports in modules that reference config

---

### 7. Testing Conventions

**Rule**:
- Test classes must end with `Test` or `Tests`
- Tests must not depend on real service implementations
- Use `@Disabled` (not `@Ignore` or commented code)

**Why**:
- Ensures IDE and Maven surefire discover tests automatically
- Prevents accidental test→production coupling
- Makes skipped tests explicit and auditable

**Compliant Code**:
```java
// Good: Proper test naming and use of test doubles
@ExtendWith(SpringExtension.class)
@SpringBootTest
class WorkflowEngineTests {

    @MockBean
    private ExternalServiceClient externalClient;

    @Test
    void testWorkflowCompletion() {
        // Test using mock
        when(externalClient.call()).thenReturn("mocked result");
        // ...
    }

    @Test
    @Disabled("Waiting for external service API documentation")
    void testIntegrationWithRealService() {
        // Explicitly disabled, not commented out
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Ignoring tests with @Ignore
@Test
@Ignore("Remove this after fixing issue #123")
void testWorkflowCompletion() {
    // ...
}

// Bad: Test depends on real service
class WorkflowEngineTest {
    private RealExternalServiceClient client = new RealExternalServiceClient();

    @Test
    void testIntegration() {
        // Directly calls production service!
    }
}

// Bad: Commented-out test
// @Test
// void testSomething() { ... }
```

**Remediation**:
1. Rename all test files to end with `Test` or `Tests`
2. Replace `@Ignore` with `@Disabled("reason")`
3. Use `@MockBean` or `@Mock` for all external service dependencies
4. Delete all commented-out tests; use version control history instead

---

## Security Architecture Rules

### 8. No Plaintext Passwords

**Rule**: Passwords must never appear in source code as string literals.

**Why**: Hardcoded passwords in source expose credentials in:
- Version control history
- Artifact archives
- Compiled bytecode
- Team member machines

**Compliant Code**:
```java
// Good: Password resolved from environment/config
@Service
public class AuthenticationService {
    @Value("${yawl.auth.admin.password}")
    private String adminPassword;

    public boolean authenticateAdmin(String provided) {
        return BCrypt.checkpw(provided, BCrypt.hashpw(adminPassword, BCrypt.gensalt()));
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Hardcoded password
public class AuthenticationService {
    private static final String ADMIN_PASSWORD = "admin123"; // EXPOSED!

    public boolean authenticateAdmin(String provided) {
        return provided.equals(ADMIN_PASSWORD);
    }
}
```

**Remediation**:
1. Search codebase: `grep -r "password\|secret\|apiKey" src/ | grep "="`
2. Move all credentials to `application.properties` (git-ignored)
3. Use Spring `@Value` or `@ConfigurationProperties` to inject at runtime
4. Rotate all exposed passwords immediately

---

### 9. Credentials via PropertyResolver

**Rule**: All credential resolution must use Spring `PropertyResolver` (or equivalent) for centralized secret management.

**Why**:
- Enables secret rotation without code changes
- Supports cloud-native secret backends (Vault, Secrets Manager)
- Allows per-environment credentials without recompile

**Compliant Code**:
```java
// Good: Centralized credential resolution
@Service
public class OAuthTokenService {
    @Autowired
    private Environment env;

    public String getClientSecret() {
        return env.getProperty("oauth.client.secret");
    }
}

// Better: Using @ConfigurationProperties
@Service
@ConfigurationProperties(prefix = "oauth")
public class OAuthConfig {
    private String clientId;
    private String clientSecret; // Auto-injected from environment
}
```

**Non-Compliant Code**:
```java
// Bad: Hardcoding or using system environment directly
public String getClientSecret() {
    return System.getenv("OAUTH_SECRET"); // Brittle, not Spring-managed
}
```

**Remediation**:
1. Create `OAuthConfig` or similar configuration class
2. Use `@ConfigurationProperties` annotation
3. Reference via `@Autowired` in services
4. Define properties in externalized config (secrets, environment)

---

### 10. Rate Limiting on Auth Endpoints

**Rule**: All authentication endpoints must enforce rate limiting using Resilience4j.

**Why**:
- Prevents brute-force password attacks
- Limits token enumeration
- Protects against credential stuffing

**Compliant Code**:
```java
// Good: Rate limiting on login endpoint
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private RateLimiter loginRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest req) {
        return RateLimiter.executeSupplier(
            () -> performLogin(req.getUsername(), req.getPassword())
        );
    }

    private ResponseEntity<TokenResponse> performLogin(String username, String password) {
        // Authentication logic
    }
}
```

**Non-Compliant Code**:
```java
// Bad: No rate limiting on login
@RestController
public class AuthController {
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest req) {
        // No rate limiting - vulnerable to brute force!
        return performLogin(req.getUsername(), req.getPassword());
    }
}
```

**Remediation**:
1. Add Resilience4j rate limiter dependency
2. Create rate limiter bean in configuration
3. Wrap authentication endpoints with `RateLimiter.executeSupplier()`
4. Configure appropriate limits (e.g., 5 attempts per minute)

---

## Observability Architecture Rules

### 11. Resilience4j Event Logging

**Rule**: All Resilience4j events (circuit breaker state changes, retries, rate limit rejections) must be logged.

**Why**:
- Enables root cause analysis of failures
- Allows alerting on pattern detection
- Helps validate resilience strategy effectiveness

**Compliant Code**:
```java
// Good: Logging resilience4j events
@Configuration
public class ResilienceEventLogging {
    @Bean
    public EventConsumer<CircuitBreakerOnStateTransitionEvent> cbStateChangeLogger() {
        return event -> LOGGER.info("CircuitBreaker {} transitioned to {}",
            event.getCircuitBreakerName(), event.getStateTransition().getToState());
    }

    @Bean
    public EventConsumer<RetryOnRetryEvent> retryLogger() {
        return event -> LOGGER.warn("Retry attempt {} for {}",
            event.getNumberOfRetryAttempts(), event.getName());
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Resilience4j events silently ignored
@Configuration
public class ResilienceConfiguration {
    @Bean
    public CircuitBreaker circuitBreaker() {
        return CircuitBreaker.of("service", config);
        // Events published but not consumed or logged!
    }
}
```

**Remediation**:
1. Register event listeners for all Resilience4j components
2. Log at appropriate level (warn for state changes, debug for retries)
3. Include relevant context (component name, counts, thresholds)
4. Test that events are actually published in integration tests

---

### 12. Metrics via MeterRegistry

**Rule**: All metrics must be exposed through Micrometer `MeterRegistry`, never direct Prometheus client calls.

**Why**:
- Enables vendor independence (Prometheus, Datadog, CloudWatch)
- Provides consistent metric naming and dimensioning
- Allows centralized metric configuration

**Compliant Code**:
```java
// Good: Using Micrometer MeterRegistry
@Service
public class WorkflowMetrics {
    private final MeterRegistry meterRegistry;
    private final Timer executionTimer;

    @Autowired
    public WorkflowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.executionTimer = Timer.builder("workflow.task.execution")
            .description("Task execution time")
            .tag("version", "1.0")
            .register(meterRegistry);
    }

    public void recordTaskExecution(String taskName, Duration duration) {
        executionTimer.record(duration);
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Direct Prometheus client usage
import io.prometheus.client.*;

@Service
public class WorkflowMetrics {
    private final Counter taskCounter = Counter.build()
        .name("workflow_tasks_total")
        .help("Total tasks executed")
        .register(CollectorRegistry.defaultRegistry);

    public void recordTask() {
        taskCounter.inc(); // Direct Prometheus - vendor locked!
    }
}
```

**Remediation**:
1. Replace all `io.prometheus.client.*` imports with `io.micrometer.core.instrument.*`
2. Inject `MeterRegistry` (not Prometheus registry)
3. Create metrics via builder pattern: `Timer.builder()`, `Counter.builder()`
4. Register with MeterRegistry
5. Update monitoring to scrape Micrometer endpoints

---

### 13. OpenTelemetry Tracing

**Rule**: Distributed tracing must use OpenTelemetry, with trace IDs in SLF4J MDC for log correlation.

**Why**:
- Enables request tracing across services
- Correlates logs with traces for observability
- Provides standard instrumentation APIs

**Compliant Code**:
```java
// Good: OpenTelemetry tracing with MDC correlation
@Service
public class WorkflowExecutor {
    private final Tracer tracer;

    @Autowired
    public WorkflowExecutor(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(WorkflowExecutor.class.getName());
    }

    public void executeWorkflow(YWorkflow workflow) {
        Span span = tracer.spanBuilder("workflow.execution")
            .setAttribute("workflow.name", workflow.getName())
            .setAttribute("case.id", workflow.getCaseID())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String traceId = span.getSpanContext().getTraceId();
            MDC.put("traceId", traceId); // Correlate logs with trace

            // Execution logic
        } finally {
            MDC.clear();
            span.end();
        }
    }
}
```

**Non-Compliant Code**:
```java
// Bad: Instrumentation without OpenTelemetry
@Service
public class WorkflowExecutor {
    public void executeWorkflow(YWorkflow workflow) {
        // No tracing at all, no correlation
        LOGGER.info("Executing workflow: {}", workflow.getName());
    }
}
```

**Remediation**:
1. Add OpenTelemetry auto-instrumentation dependency
2. Create Tracer instances via `OpenTelemetry.getTracer()`
3. Wrap logical operations with spans
4. Put trace ID in MDC before logging
5. Test span export in integration tests

---

## Testing the Rules

### Running Individual Test Classes

```bash
# Run core architecture tests
mvn test -Dtest=ArchitectureTests

# Run security architecture tests
mvn test -Dtest=SecurityArchitectureTests

# Run observability architecture tests
mvn test -Dtest=ObservabilityArchitectureTests

# Run all architecture tests
mvn test -Dtest=*ArchitectureTests
```

### Freezing Rules

ArchUnit supports "freezing" rules to suppress violations during gradual refactoring:

```java
@Test
void ruleCanBeIgnored() {
    rule()
        .check(classes)
        .freeze(); // Tolerates existing violations
}
```

To unfreeze, remove `.freeze()` call.

---

## Migration Path

### Phase 1 (Completed)
- Identify all violations using ArchUnit
- Create frozen rules for existing violations
- Document remediation steps

### Phase 2 (Current)
- Implement ArchUnit tests with comprehensive documentation
- Enable tests in CI/CD pipeline
- Begin gradual remediation of violations

### Phase 3 (Future)
- Unfreeze rules one module at a time
- Remediate violations per module
- Validate clean architecture before unfreezing next module

---

## References

- [ArchUnit Documentation](https://www.archunit.org/)
- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/instrumentation/java/)
- [Micrometer Documentation](https://micrometer.io/)
- [Java 25 Features](https://docs.oracle.com/en/java/javase/25/docs/api/)
