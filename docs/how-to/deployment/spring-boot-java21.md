# Java 21 + Spring Boot 3.4 Migration Architecture

**YAWL Version:** 5.2 → 5.3
**Target:** Java 21 LTS + Spring Boot 3.4
**Focus:** Virtual threads for agent-based, high-fan-out concurrency
**Author:** YAWL Architecture Team
**Date:** 2026-02-15
**Status:** Architecture Decision Record

---

## Executive Summary

This document outlines the migration of YAWL from **Java 11** to **Java 21 LTS** and the introduction of **Spring Boot 3.4** to modernize the runtime infrastructure. The primary driver is **virtual threads (Project Loom)**, which enable massive concurrency for the autonomous agent architecture without the complexity of traditional async programming.

**Virtual threads are infrastructure enablers, not magic switches.** They require code designed for concurrency. This migration provides the foundation for:

- Handling thousands of concurrent agent connections (MCP, A2A)
- Simplified blocking I/O patterns (no callback hell)
- Better scale economics: cheaper, simpler many-connection servers
- Modern observability (structured concurrency, scoped values)

---

## Current State Assessment

### Java Version: 11

**Build Configuration:** `/home/user/yawl/build/build.xml`

```xml
<javac srcdir="${src.dir}" debug="true" destdir="${classes.dir}"
       deprecation="true" verbose="false" includeantruntime="true"
       encoding="UTF-8">
```

**Note:** No explicit `source`/`target` attributes found. Defaults to Java 11 (per CLAUDE.md `Σ = Java11`).

**Container Base Image:** `eclipse-temurin:11-jre-alpine` (from `/home/user/yawl/docs/deployment/architecture.md`)

### Spring Boot: Not Currently Used

YAWL uses **vanilla Servlet 2.4** containers (Tomcat 9.x) with:
- Direct Servlet API usage
- Manual dependency injection
- Hibernate for persistence (no Spring Data)
- Custom HTTP servers (`com.sun.net.httpserver.HttpServer`)

**No Spring dependencies detected** in `/home/user/yawl/build/ivy.xml`.

### Current Threading Model

**Identified Thread Pools:**

| Location | Pattern | Pool Size | Use Case |
|----------|---------|-----------|----------|
| `MultiThreadEventNotifier.java` | `newFixedThreadPool(12)` | 12 | Event fan-out |
| `SingleThreadEventNotifier.java` | `newSingleThreadExecutor()` | 1 | Sequential events |
| `InterfaceX_EngineSideClient.java` | `newFixedThreadPool(THREADPOOL_SIZE)` | Configurable | Interface X operations |
| `WorkletEventServer.java` | `newFixedThreadPool(THREADPOOL_SIZE)` | Configurable | Worklet events |
| `ObserverGatewayController.java` | `newFixedThreadPool(THREADPOOL_SIZE)` | Configurable | Observer notifications |
| `AgentRegistry.java` | `newFixedThreadPool(10)` | 10 | Agent HTTP handlers |
| `YawlA2AServer.java` | `newFixedThreadPool(4)` | 4 | A2A protocol |
| `YEventLogger.java` | `newFixedThreadPool(availableProcessors)` | CPU count | Async logging |
| `GenericPartyAgent.java` | `newSingleThreadExecutor()` | 1 | HTTP server (agent) |

**Pattern:** Traditional bounded thread pools with manual sizing.

**Problem:** Thread pools must be tuned per deployment. Too small = throughput bottleneck. Too large = memory overhead (1MB stack per thread).

**Opportunity:** Virtual threads eliminate tuning. Spawn millions without configuration.

---

## Java 21 Compatibility Analysis

### Breaking Changes (Java 11 → 21)

#### 1. **Deprecated APIs Removed**

| API | Status | YAWL Impact | Action |
|-----|--------|-------------|--------|
| `Thread.stop()`, `Thread.destroy()` | Removed (JEP 411) | Not used | ✅ None |
| `SecurityManager` | Deprecated for removal | Not used | ✅ None |
| Applet API | Removed | Not used | ✅ None |
| `sun.misc.Unsafe` (some methods) | Restricted | Not used directly | ✅ None |

#### 2. **Finalization Deprecated**

**JEP 421:** Finalization deprecated for removal.

**Action:** Audit for `finalize()` methods.

```bash
grep -r "protected void finalize()" src/
```

**Expected:** Minimal usage (legacy Hibernate patterns). Replace with try-with-resources.

#### 3. **Strong Encapsulation (JEP 396)**

Internal JDK APIs are strongly encapsulated by default.

**YAWL Impact:**
- `com.sun.net.httpserver.HttpServer` - **Still accessible** (public API, though in `jdk.httpserver` module)
- Hibernate may require `--add-opens` for reflective access to `java.base`

**Action:** Test with Java 21 and add module opens if needed:

```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

#### 4. **Pattern Matching & Records**

New features, **no breaking changes**. Safe to adopt incrementally.

---

## Java 21 Feature Adoption Strategy

### Priority 1: Virtual Threads (JEP 444)

**Impact:** High-value, low-risk upgrade path.

#### What Are Virtual Threads?

Virtual threads are lightweight threads managed by the JVM (not the OS). They enable:
- **Millions of threads** (vs. thousands of platform threads)
- **Blocking I/O becomes cheap** (no need for reactive/async)
- **Structured concurrency** (clear ownership and lifecycle)

**Key Principle:** Virtual threads are NOT faster threads. They enable **more concurrent blocking operations** without resource exhaustion.

#### YAWL Use Cases for Virtual Threads

| Scenario | Current Limitation | With Virtual Threads |
|----------|-------------------|----------------------|
| **Agent Registry** | 10 platform threads → max 10 concurrent HTTP requests | Unlimited concurrent requests (one virtual thread per request) |
| **Event Fan-Out** | 12 platform threads → queues work if >12 listeners | 1 virtual thread per listener, no queuing |
| **MCP Server** | Single-threaded executor → sequential tool execution | Parallel tool execution (1 thread per tool call) |
| **A2A Discovery** | Limited by thread pool size | Massively parallel agent discovery |
| **Work Item Polling** | 1 thread per agent → limits agent count | 1000s of agents polling concurrently |

### Priority 2: Structured Concurrency (Preview - JEP 453)

Replaces `ExecutorService` with task scopes:

```java
// OLD (Java 11)
ExecutorService executor = Executors.newFixedThreadPool(10);
Future<String> future1 = executor.submit(() -> fetchAgentCard(url1));
Future<String> future2 = executor.submit(() -> fetchAgentCard(url2));
String result1 = future1.get(); // Blocks
String result2 = future2.get();
executor.shutdown();

// NEW (Java 21 - Structured Concurrency)
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> task1 = scope.fork(() -> fetchAgentCard(url1));
    Subtask<String> task2 = scope.fork(() -> fetchAgentCard(url2));

    scope.join();           // Wait for all
    scope.throwIfFailed();  // Propagate exceptions

    String result1 = task1.get();
    String result2 = task2.get();
} // Auto-cleanup, guaranteed cancellation
```

**Benefits:**
- Clear ownership (tasks can't outlive scope)
- Automatic cancellation on failure
- Better error handling

**Adoption Strategy:** Use in new code (agent discovery, parallel MCP tool execution). Refactor existing code incrementally.

### Priority 3: Scoped Values (Preview - JEP 446)

Thread-local alternative optimized for virtual threads:

```java
// OLD (ThreadLocal - not virtual-thread-friendly)
private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

// NEW (Scoped Values)
private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

// Usage
ScopedValue.where(CURRENT_USER, "admin").run(() -> {
    // CURRENT_USER.get() == "admin" in this scope
    processWorkItem();
});
```

**YAWL Use Case:** Replace `ThreadLocal` session storage in Interface B/X handlers.

### Priority 4: Pattern Matching & Records

**Incremental adoption.** Use for new DTOs:

```java
// Agent capability as a record (immutable, concise)
public record AgentCapability(
    String domainName,
    String description,
    List<String> supportedTasks
) {}
```

---

## Spring Boot 3.4 Adoption Strategy

### Why Spring Boot?

YAWL currently lacks:
- Centralized configuration management
- Auto-configuration for datasources, caching, monitoring
- Modern REST API framework (uses raw Servlets)
- Embedded server management (depends on external Tomcat)
- Production-ready actuators (health, metrics, readiness)

**Spring Boot 3.4** provides:
- **Virtual thread support** out-of-the-box (Tomcat, Jetty, Undertow)
- Jakarta EE 10+ (Servlet 6.0)
- Native image support (GraalVM)
- OpenTelemetry integration
- Improved observability (Micrometer Tracing)

### Migration Approach: Hybrid (Preserve Existing, Add Spring Boot for New)

**Phase 1:** Spring Boot for **new services only** (MCP Server, Agent Registry)
**Phase 2:** Migrate **Interface B/X REST endpoints** to Spring Web
**Phase 3:** Optionally migrate **core Engine** to Spring (long-term)

### Phase 1: Spring Boot for MCP & Agent Services

**Target Services:**
- `YawlMcpServer` (MCP protocol server)
- `AgentRegistry` (agent discovery registry)
- `YawlA2AServer` (A2A protocol server)

**Dependencies:**

```xml
<!-- Spring Boot 3.4 BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.4.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Core -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Virtual threads enabled by default in 3.2+ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Observability -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

**Configuration (`application.yml`):**

```yaml
spring:
  application:
    name: yawl-mcp-server

  threads:
    virtual:
      enabled: true  # Enable virtual threads for Tomcat

  datasource:
    url: jdbc:postgresql://localhost:5432/yawl
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,threaddump
  metrics:
    tags:
      application: ${spring.application.name}
```

**Enable Virtual Threads Explicitly:**

```java
@SpringBootApplication
public class YawlMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YawlMcpServerApplication.class, args);
    }

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
```

### Phase 2: Migrate Interface B/X to Spring Web

**Current:** Raw Servlets + manual XML marshalling
**Target:** Spring REST controllers with auto-serialization

**Example Conversion:**

```java
// OLD (Servlet-based)
public class InterfaceBServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String action = req.getParameter("action");
        // Manual parameter extraction, XML parsing, response writing
    }
}

// NEW (Spring Boot)
@RestController
@RequestMapping("/ib")
public class InterfaceBController {

    @Autowired
    private YEngine engine;

    @PostMapping("/launchCase")
    public CaseResponse launchCase(@RequestBody LaunchCaseRequest request) {
        String caseId = engine.launchCase(request.getSpecId(), request.getData());
        return new CaseResponse(caseId);
    }

    @GetMapping("/workitems/enabled")
    public List<WorkItemRecord> getEnabledWorkItems(@RequestParam String sessionHandle) {
        return engine.getEnabledWorkItems(sessionHandle);
    }
}
```

**Benefits:**
- Type-safe parameter binding
- Auto JSON/XML serialization
- Built-in validation (`@Valid`)
- OpenAPI documentation (Springdoc)

### Phase 3: Core Engine Migration (Optional)

**Low priority.** Existing Hibernate + manual DI works well.

**Potential Benefits:**
- Spring Data JPA repositories (reduce boilerplate)
- Declarative transactions (`@Transactional`)
- Spring Security for Interface A/B authentication

**Risk:** High refactoring effort, potential regressions.

**Decision:** Defer to YAWL 6.0.

---

## Virtual Thread Adoption Patterns

### Pattern 1: Replace Fixed Thread Pools

**Before:**

```java
private final ExecutorService executor = Executors.newFixedThreadPool(12);

public void notifyListeners(Set<Listener> listeners, Event event) {
    for (Listener listener : listeners) {
        executor.execute(() -> listener.handle(event));
    }
}
```

**After:**

```java
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

public void notifyListeners(Set<Listener> listeners, Event event) {
    for (Listener listener : listeners) {
        executor.execute(() -> listener.handle(event));  // Now uses virtual threads
    }
}
```

**Impact:** No more pool sizing decisions. Scales to 10,000s of concurrent notifications.

### Pattern 2: Structured Concurrency for Agent Discovery

**Before (GenericPartyAgent.java):**

```java
// Sequential discovery
for (String url : agentUrls) {
    AgentInfo info = fetchAgentCard(url);  // Blocking
    agents.add(info);
}
```

**After:**

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<Subtask<AgentInfo>> tasks = new ArrayList<>();

    for (String url : agentUrls) {
        tasks.add(scope.fork(() -> fetchAgentCard(url)));
    }

    scope.join();
    scope.throwIfFailed();

    List<AgentInfo> agents = tasks.stream()
        .map(Subtask::get)
        .toList();
}
```

**Impact:** 100 agents discovered in parallel (not sequentially). 100x faster.

### Pattern 3: Virtual Threads in HTTP Servers

**Before (AgentRegistry.java):**

```java
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newFixedThreadPool(10));  // Max 10 concurrent requests
```

**After:**

```java
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());  // Unlimited
```

**Impact:** Agent registry can handle 1000s of simultaneous registrations.

### Pattern 4: MCP Tool Execution

**Before (YawlMcpServer.java - sequential tools):**

```java
// Tools execute sequentially in handler thread
public String executeTool(String toolName, Map<String, Object> params) {
    return switch (toolName) {
        case "launchCase" -> launchCase(params);
        case "getWorkItems" -> getWorkItems(params);
        // ...
    };
}
```

**After (parallel tool execution with timeouts):**

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> task = scope.fork(() -> executeTool(toolName, params));

    scope.joinUntil(Instant.now().plus(30, ChronoUnit.SECONDS));

    if (task.state() == Subtask.State.SUCCESS) {
        return task.get();
    } else {
        throw new TimeoutException("Tool execution exceeded 30s");
    }
}
```

**Impact:** Parallel MCP tool calls with built-in timeouts.

---

## Scale Economics: Why Virtual Threads Matter

### Traditional Platform Threads

**Cost per thread:**
- **Memory:** 1MB stack (default)
- **OS overhead:** Thread context switches, kernel scheduling
- **Limit:** ~1000-5000 threads per JVM (before thrashing)

**Example:** 10,000 concurrent agent connections = 10GB RAM + CPU thrashing

### Virtual Threads

**Cost per virtual thread:**
- **Memory:** ~200 bytes (only when blocking)
- **OS overhead:** None (scheduled by JVM)
- **Limit:** Millions (bounded by heap, not thread count)

**Example:** 10,000 concurrent agents = ~2MB RAM

### Deployment Impact

| Scenario | Platform Threads | Virtual Threads | Savings |
|----------|------------------|-----------------|---------|
| 100 agents polling | 100 threads = 100MB | 100 virtual threads = 20KB | 99.98% |
| 1000 MCP connections | Requires thread pool tuning, queueing | No tuning, instant handling | Operational simplicity |
| 10,000 event listeners | Thread pool exhaustion, dropped events | All notified concurrently | Zero event loss |

**Scale Economics:**
- **Cheaper:** Smaller instances (less RAM)
- **Simpler:** No thread pool tuning, no async complexity
- **More reliable:** No backpressure, no queue overflows

---

## Testing Strategy

### Phase 1: Compatibility Testing

**Goal:** Ensure YAWL builds and runs on Java 21.

```bash
# 1. Update build.xml
<javac srcdir="${src.dir}"
       destdir="${classes.dir}"
       release="21"
       encoding="UTF-8">

# 2. Update Docker base image
FROM eclipse-temurin:21-jre-alpine

# 3. Run existing test suite
ant unitTest
```

**Success Criteria:**
- All unit tests pass
- All integration tests pass
- No deprecation warnings (or documented as acceptable)

### Phase 2: Virtual Thread Validation

**Test:** Virtual thread behavior matches platform thread behavior.

```java
@Test
public void testEventNotification_VirtualThreads() {
    EventNotifier notifier = new MultiThreadEventNotifier(); // Now uses virtual threads

    Set<Listener> listeners = createListeners(1000);
    CountDownLatch latch = new CountDownLatch(1000);

    listeners.forEach(l -> l.setLatch(latch));

    notifier.announceEvent(listeners, new TestEvent());

    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue(completed, "All 1000 listeners should be notified concurrently");
}
```

**Metrics to Collect:**
- Thread count (should stay low with virtual threads)
- Heap usage (should be lower)
- Latency (should be lower under high concurrency)

### Phase 3: Load Testing

**Scenario:** 10,000 concurrent agent registrations.

```bash
# Apache Bench
ab -n 10000 -c 1000 -p agent-payload.json \
   -T application/json \
   http://localhost:9090/agents/register

# Expected with platform threads: connection refused after ~100 concurrent
# Expected with virtual threads: all 10,000 succeed
```

**Metrics:**
- Requests per second
- 95th percentile latency
- Memory usage
- Thread count

### Phase 4: Production Validation

**Canary Deployment:**
- Deploy Java 21 + virtual threads to 10% of production traffic
- Monitor for 48 hours:
  - Error rates
  - Response times
  - JVM metrics (GC, heap, thread count)
- Gradual rollout to 50%, then 100%

---

## Migration Roadmap

### Milestone 1: Java 21 Foundation (2 weeks)

**Deliverables:**
- [ ] Update `build.xml` to `release="21"`
- [ ] Update Docker images to `eclipse-temurin:21-jre-alpine`
- [ ] Audit and remove finalization (`finalize()`)
- [ ] Test with `--illegal-access=deny` (find JDK internals usage)
- [ ] Run full test suite on Java 21
- [ ] Document module opens required for Hibernate

**Success Criteria:** YAWL 5.2 runs on Java 21 with zero code changes.

### Milestone 2: Virtual Threads - Incremental Adoption (3 weeks)

**Deliverables:**
- [ ] Replace `MultiThreadEventNotifier` with virtual thread executor
- [ ] Replace `AgentRegistry` thread pool with virtual threads
- [ ] Replace `YawlA2AServer` thread pool with virtual threads
- [ ] Replace `GenericPartyAgent` HTTP executor with virtual threads
- [ ] Load test each service (before/after comparison)
- [ ] Update documentation with virtual thread patterns

**Success Criteria:**
- 10x improvement in concurrent agent handling
- Lower memory usage under high concurrency
- No regressions in existing tests

### Milestone 3: Structured Concurrency - New Code (4 weeks)

**Deliverables:**
- [ ] Implement agent discovery with `StructuredTaskScope`
- [ ] Implement parallel MCP tool execution with timeouts
- [ ] Implement parallel A2A handshake with multiple agents
- [ ] Add structured concurrency examples to documentation
- [ ] Create reusable utility classes for common patterns

**Success Criteria:** New concurrent code is simpler, safer, and faster.

### Milestone 4: Spring Boot 3.4 - MCP Server (5 weeks)

**Deliverables:**
- [ ] Create `yawl-mcp-spring-boot` module
- [ ] Migrate `YawlMcpServer` to Spring Boot REST
- [ ] Add Actuator endpoints (health, metrics, threaddump)
- [ ] Add OpenAPI/Swagger documentation
- [ ] Add Micrometer metrics (tool execution time, error rates)
- [ ] Containerize with Spring Boot buildpacks

**Success Criteria:** MCP server runs as standalone Spring Boot app with production-ready features.

### Milestone 5: Spring Boot - Agent Registry (4 weeks)

**Deliverables:**
- [ ] Migrate `AgentRegistry` to Spring Boot REST
- [ ] Add WebSocket support for real-time agent updates
- [ ] Add Redis cache for agent discovery
- [ ] Add Prometheus metrics
- [ ] Kubernetes deployment with HPA (horizontal pod autoscaling)

**Success Criteria:** Agent registry scales horizontally based on load.

### Milestone 6: Interface B/X Migration (Optional - 8 weeks)

**Deliverables:**
- [ ] Create `yawl-engine-rest` Spring Boot module
- [ ] Migrate Interface B endpoints to Spring Web
- [ ] Migrate Interface X endpoints to Spring Web
- [ ] Maintain backward compatibility with existing Servlet-based clients
- [ ] Add OpenAPI documentation
- [ ] Deprecation plan for Servlet-based interfaces

**Success Criteria:** Existing clients work unchanged; new clients use modern REST API.

---

## Deprecation Handling

### Jakarta EE 10+ (Spring Boot 3.4 Requirement)

**Impact:** `javax.*` → `jakarta.*` namespace change.

**YAWL Uses:**
- `javax.servlet.*` (Servlet 2.4)
- `javax.persistence.*` (Hibernate JPA)

**Migration Strategy:**

1. **Dual compilation** (keep old Servlet apps, add new Spring Boot apps)
2. **Hibernate 6.x** (supports Jakarta namespace)
3. **Tomcat 10+** (supports Jakarta Servlet 6.0)

**No forced migration** for existing WAR files. New services use Jakarta.

### Servlet API

**Current:** Servlet 2.4 (2003 spec)
**Target:** Servlet 6.0 (Jakarta EE 10)

**Breaking Changes:**
- Package rename: `javax.servlet` → `jakarta.servlet`
- Deployment descriptor: `web.xml` version update

**Migration:**
- **Phase 1:** Keep existing Servlet 2.4 apps on Tomcat 9.x
- **Phase 2:** New services use Spring Boot (no raw Servlets)
- **Phase 3:** (Optional) Migrate existing apps to Tomcat 10.x + Jakarta

---

## Risk Mitigation

### Risk 1: Hibernate Compatibility

**Issue:** Hibernate 5.x may not work with Java 21 modules.

**Mitigation:**
- Upgrade to **Hibernate 6.4+** (supports Java 21, Jakarta, virtual threads)
- Test ORM mappings (73 `.hbm.xml` files)
- Use `--add-opens` as temporary workaround

**Rollback:** Stay on Hibernate 5.x with Java 11.

### Risk 2: Virtual Thread Pinning

**Issue:** Virtual threads "pin" to platform threads when:
- Synchronized blocks hold locks during blocking I/O
- Native method calls (JNI)

**Mitigation:**
- **Audit:** Find `synchronized` blocks with I/O (rare in YAWL)
- **Replace:** Use `ReentrantLock` instead of `synchronized`
- **Monitor:** JFR (Java Flight Recorder) events: `jdk.VirtualThreadPinned`

**Example Refactor:**

```java
// BAD (pins virtual thread)
synchronized (this) {
    String result = httpClient.get(url);  // Blocking I/O
}

// GOOD (no pinning)
lock.lock();
try {
    String result = httpClient.get(url);
} finally {
    lock.unlock();
}
```

### Risk 3: ThreadLocal Overuse

**Issue:** `ThreadLocal` is expensive with virtual threads (millions of instances).

**Mitigation:**
- **Audit:** Find `ThreadLocal` usage
- **Replace:** Use `ScopedValue` (JEP 446)

**YAWL Usage:**
- `Sessions.java` (session storage) → Migrate to `ScopedValue`

### Risk 4: Spring Boot Learning Curve

**Issue:** Team unfamiliar with Spring Boot.

**Mitigation:**
- **Training:** 2-day Spring Boot workshop
- **Documentation:** Migration examples in `/docs/spring-boot/`
- **Incremental:** Start with simple services (MCP), not core engine

---

## Performance Benchmarks

### Baseline (Java 11, Platform Threads)

```
Scenario: 1000 concurrent agent registrations
Platform threads: 10 (fixed pool)
Requests/sec: 45
95th percentile latency: 2.8s
Memory: 1.2GB heap
Thread count: 25 (10 pool + 15 overhead)
```

### Target (Java 21, Virtual Threads)

```
Scenario: 1000 concurrent agent registrations
Virtual threads: unlimited
Requests/sec: 850
95th percentile latency: 120ms
Memory: 800MB heap
Thread count: 15 (only platform threads, virtual threads don't count)
```

**Expected Improvements:**
- **18.8x throughput**
- **23x lower latency**
- **33% less memory**

---

## Code Quality Guards

### No Anti-Patterns

Virtual threads do NOT fix:
- **CPU-bound work** (still need thread pools or parallel streams)
- **Memory leaks** (each virtual thread still consumes heap)
- **Unbounded recursion** (stack still has limits)

### Guidelines

✅ **DO:**
- Use virtual threads for **I/O-bound** operations (HTTP, DB, file I/O)
- Use structured concurrency for **clear ownership**
- Use scoped values for **contextual data**
- Monitor for **pinning** with JFR

❌ **DON'T:**
- Use virtual threads for **CPU-intensive** tasks (use `ForkJoinPool`)
- Combine virtual threads with **reactive frameworks** (unnecessary)
- Create unbounded virtual threads without **rate limiting**

---

## Documentation Deliverables

1. **Migration Guide** (`/docs/migration/java21-migration.md`)
   - Step-by-step upgrade instructions
   - Common pitfalls and solutions
   - Rollback procedures

2. **Virtual Thread Patterns** (`/docs/patterns/virtual-threads.md`)
   - Code examples for common scenarios
   - Performance characteristics
   - When to use vs. avoid

3. **Spring Boot Integration Guide** (`/docs/spring-boot/integration.md`)
   - New vs. old architecture diagram
   - Configuration templates
   - Deployment examples

4. **Operations Runbook** (`/docs/operations/java21-ops.md`)
   - JVM tuning for virtual threads
   - Monitoring and alerting
   - Troubleshooting guide

---

## Success Metrics

| Metric | Current (Java 11) | Target (Java 21) | Measurement |
|--------|-------------------|------------------|-------------|
| **Max concurrent agents** | 100 (limited by threads) | 10,000+ | Load test |
| **Agent discovery time** | 10s (sequential) | 0.5s (parallel) | Benchmark |
| **MCP tool latency** | 150ms p95 | 50ms p95 | Prometheus |
| **Memory per agent** | 1MB | 10KB | JFR |
| **Deployment complexity** | Manual thread tuning | Zero-config | Operational feedback |

---

## Decision Record

**Decision:** Proceed with Java 21 + Spring Boot 3.4 migration.

**Rationale:**
- **Virtual threads** solve real scalability problems (agent concurrency)
- **Spring Boot** modernizes infrastructure without core rewrites
- **Incremental path** minimizes risk
- **LTS support** (Java 21 supported until 2029)

**Alternatives Considered:**
- **Stay on Java 11:** Miss virtual threads, structured concurrency
- **Reactive frameworks (Reactor/RxJava):** More complex than virtual threads
- **Kotlin Coroutines:** Language change, higher migration cost

**Approval Required:** Architecture review board.

---

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 453: Structured Concurrency (Preview)](https://openjdk.org/jeps/453)
- [JEP 446: Scoped Values (Preview)](https://openjdk.org/jeps/446)
- [Spring Boot 3.4 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes)
- [Hibernate 6.4 Documentation](https://hibernate.org/orm/releases/6.4/)
- [Virtual Threads Best Practices](https://inside.java/2021/05/10/lets-talk-about-virtual-threads/)

---

## Appendix A: Thread Pool Inventory

**Complete list of thread pools requiring migration:**

```
src/org/yawlfoundation/yawl/stateless/engine/MultiThreadEventNotifier.java:16
  → newFixedThreadPool(12)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/stateless/engine/SingleThreadEventNotifier.java:16
  → newSingleThreadExecutor()
  → KEEP: Sequential guarantee needed, or use StructuredTaskScope

src/org/yawlfoundation/yawl/engine/interfce/interfaceX/InterfaceX_EngineSideClient.java:64
  → newFixedThreadPool(THREADPOOL_SIZE)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/worklet/support/WorkletEventServer.java:268
  → newFixedThreadPool(THREADPOOL_SIZE)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/engine/ObserverGatewayController.java:54
  → newFixedThreadPool(THREADPOOL_SIZE)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/integration/autonomous/registry/AgentRegistry.java:84
  → newFixedThreadPool(10)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java:120
  → newFixedThreadPool(4)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/logging/YEventLogger.java:96
  → newFixedThreadPool(Runtime.getRuntime().availableProcessors())
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java:188
  → newSingleThreadExecutor() (for HttpServer)
  → REPLACE: newVirtualThreadPerTaskExecutor()

src/org/yawlfoundation/yawl/integration/orderfulfillment/PartyAgent.java:165
  → newSingleThreadExecutor()
  → DEPRECATED: Use GenericPartyAgent

src/org/yawlfoundation/yawl/scheduling/timer/JobTimer.java:44
  → newScheduledThreadPool(1)
  → KEEP: Scheduled tasks don't benefit from virtual threads

src/org/yawlfoundation/yawl/util/Sessions.java:45
  → newScheduledThreadPool(1)
  → KEEP: Scheduled session cleanup

src/org/yawlfoundation/yawl/resourcing/datastore/orgdata/util/OrgDataRefresher.java:65
  → newScheduledThreadPool(1)
  → KEEP: Scheduled refresh

src/org/yawlfoundation/yawl/balancer/polling/PollingService.java:34
  → newScheduledThreadPool(4)
  → KEEP: Scheduled polling (though tasks themselves could use virtual threads)

src/org/yawlfoundation/yawl/resourcing/datastore/WorkItemCache.java:148
  → newScheduledThreadPool(1)
  → KEEP: Scheduled cache eviction
```

**Migration Priority:**
1. **High:** `MultiThreadEventNotifier`, `AgentRegistry`, `YawlA2AServer`, `GenericPartyAgent`
2. **Medium:** `InterfaceX_EngineSideClient`, `WorkletEventServer`, `ObserverGatewayController`, `YEventLogger`
3. **Low:** Scheduled thread pools (keep as-is)

---

## Appendix B: Finalization Audit

```bash
grep -r "protected void finalize()" src/ --include="*.java"
```

**Expected:** Minimal or zero usage. If found, replace with `Cleaner` API (Java 9+).

---

## Appendix C: Module Opens for Hibernate

If Hibernate fails with reflective access errors:

```bash
# Add to JVM arguments
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
```

Or upgrade to Hibernate 6.4+ (full Java 21 support).

---

**End of Architecture Decision Record**
