# Java 25 Libraries — Best Practices & Recommendations

> Proven, production-grade libraries for Java 25. No reinvention. Speed and safety.

**Date**: 2026-02-20
**Scope**: Recommendations for YAWL ecosystem
**Status**: Ready to adopt

---

## HTTP/REST — java.net.http + Clients

### Tier 1: Built-In Standard Library ✅ (USE THIS FIRST)

**java.net.http.HttpClient** (Java 11+)

```java
// Already used in YAWL! (HttpTransportProvider.java)
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(30))
    .executor(Executors.newVirtualThreadPerTaskExecutor())
    .build();

// GET
HttpRequest request = HttpRequest.newBuilder()
    .GET()
    .uri(URI.create("https://api.example.com/data"))
    .timeout(Duration.ofSeconds(10))
    .header("User-Agent", "YAWL/6.0")
    .build();

HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
```

✅ **Pros**:
- Zero external dependencies
- First-class virtual thread support
- Built-in timeouts, redirects, cookies
- Async/sync both supported

❌ **Cons**:
- No automatic retries (use autonomics engine instead)
- No request pooling built-in (HttpClient reuses internally)
- No circuit breaker (use Resilience4j)

### Tier 2: Specialized Needs

**OkHttp 4.x** (If needing advanced features)
```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

✅ Use if:
- Need automatic retry + backoff
- Heavy connection pooling required
- Custom DNS resolution

❌ Don't use if:
- java.net.http.HttpClient meets needs
- Want to minimize dependencies

**Retrofit 2.x** (REST-specific wrapper)
```gradle
implementation 'com.squareup.retrofit2:retrofit:2.11.0'
```

✅ Use if:
- Defining typed REST clients
- Extensive annotation-based routing

❌ Don't use if:
- One-off API calls
- Using graphQL (see below)

### Recommendation

**✅ YAWL: Continue with java.net.http.HttpClient**
- Already in place
- Virtual thread ready
- Minimal overhead

---

## JSON Processing — Jackson (Already Using!)

### Current: Jackson 2.17+

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.17.0</version>
</dependency>
```

✅ **Best-in-class for**:
- Performance (fastest JSON parser in Java)
- Records support (Java 17+)
- Pattern matching with sealed classes
- Streaming (for large payloads)

**Modern usage with Records**:

```java
// Define as record with Jackson annotations
public record WorkflowInput(
    @JsonProperty("caseID") String caseID,
    @JsonProperty("variables") Map<String, String> variables
) { }

// Automatic serialization
WorkflowInput input = objectMapper.readValue(json, WorkflowInput.class);
String jsonOutput = objectMapper.writeValueAsString(input);
```

### Avoid These

❌ **Don't use**:
- `org.json` (slow, verbose)
- `google.gson` (slower than Jackson, less flexible)
- `fastjson` (security concerns)

### Recommendation

**✅ YAWL: Double down on Jackson + Records**
- Add `jackson-jsr310` for java.time support
- Use `jackson-dataformat-xml` for XML workflows
- Enable annotation processing for best performance

```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>com.fasterxml.jackson.datatype</groupId>
    <artifactId>jackson-datatype-jsr310</artifactId>
    <version>2.17.0</version>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-xml</artifactId>
    <version>2.17.0</version>
</dependency>
```

---

## Logging — SLF4J + Logback (Already Using!)

### Current Setup (Good!)

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.1.0</version>
</dependency>

<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.0</version>
</dependency>
```

✅ **Why this combo**:
- SLF4J: Facade pattern, zero lock-in
- Logback: Fastest implementation, flexible config
- Bridge: Log4j1 → SLF4J automatically

### Modern Java 25 Logging Features

**Use structured logging** (better than string concatenation):

```java
// OLD: String concatenation (slow)
logger.info("Agent " + agentID + " completed " + caseCount + " cases");

// NEW: Structured (parametric, faster)
logger.info("Agent completed cases",
    structuredKey("agentID", agentID),
    structuredKey("caseCount", caseCount));

// Or with SLF4J named args (Java 15+)
logger.info("Agent {} completed {} cases", agentID, caseCount);
```

### Alternative: Avoid Log4j 2.x

❌ **Log4j 2.x** — Only if:
- Need async appenders (better than SLF4J/Logback async)
- Performance is 100x more important than simplicity

✅ Better: Logback async mode:

```xml
<!-- logback.xml -->
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="FILE"/>
</appender>
```

### Recommendation

**✅ YAWL: Keep SLF4J + Logback, modernize log statements**
- Use parametric logging (faster, cleaner)
- Consider structured logging libraries if doing analysis
- Monitor async appender performance under load

---

## Concurrency/Resilience — Resilience4j + Loom

### Core: Java 21+ StructuredTaskScope (Already Recommended!)

```java
// No external dependency!
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    for (Workflow w : workflows) {
        scope.fork(() -> execute(w));
    }
    scope.join();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

### Advanced: Resilience4j (Proven Patterns)

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-core</artifactId>
    <version>2.2.0</version>
</dependency>
```

**What it provides**:
- Retry policies with backoff
- Circuit breakers (fail-fast when service down)
- Rate limiters (throttle requests)
- Bulkheads (thread pool isolation)

**Modern usage**:

```java
// Retry with exponential backoff
RetryConfig config = RetryConfig.custom()
    .maxAttempts(3)
    .intervalFunction(IntervalFunction.ofExponentialBackoff(100, 2))
    .retryExceptions(TemporaryException.class)
    .ignoreExceptions(PermanentException.class)
    .build();

Retry retry = Retry.of("workflow-retry", config);
Callable<Result> callable = Retry.decorateCallable(retry, () -> executeWorkflow());
```

### Recommendation

**✅ YAWL: Use Resilience4j for**:
- Circuit breaker (A2A endpoints)
- Rate limiting (REST API gateway)
- Retries (external service calls)

**⚠️ Don't use Resilience4j for**:
- Internal workflow engine retries (use autonomics engine)
- Virtual thread context switching (use StructuredTaskScope)

---

## Testing — JUnit 5 + Testcontainers

### Already Good: JUnit 5 (Jupiter)

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.10.0</version>
    <scope>test</scope>
</dependency>
```

✅ **Modern features**:
- `@ParameterizedTest` (data-driven testing)
- `@Nested` (test organization)
- `@DisplayName` (human-readable names)
- Lambda support for custom assertions

### Integration Testing: Testcontainers

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.0</version>
    <scope>test</scope>
</dependency>

<!-- Database support -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.20.0</version>
    <scope>test</scope>
</dependency>
```

**Usage** (eliminate in-memory test databases):

```java
@Testcontainers
public class TestWorkflowEngine {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void enginePersistsToRealDatabase() {
        // Use real PostgreSQL, not H2 mock!
        DataSource ds = createDataSource(postgres.getJdbcUrl(), ...);
        YEngine engine = new YEngine(ds);
        // ...
    }
}
```

✅ **Why use it**:
- Tests run against real databases (not mocks)
- Eliminates "works in test, breaks in prod" surprises
- Testcontainers handles Docker cleanup automatically

### Assertion Libraries: AssertJ

```xml
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.26.0</version>
    <scope>test</scope>
</dependency>
```

**Fluent assertions** (more readable than JUnit asserts):

```java
// Old
assertEquals("test-agent-001", status.getAgentID());
assertTrue(status.isHealthy());
assertEquals(10, status.getCompletedCases());

// New (AssertJ)
assertThat(status)
    .extracting(AgentStatus::getAgentID)
    .isEqualTo("test-agent-001");

assertThat(status)
    .hasFieldOrPropertyWithValue("healthy", true)
    .hasFieldOrPropertyWithValue("completedCases", 10);
```

### Recommendation

**✅ YAWL: Adopt Testcontainers**
- Replace H2 in-memory tests with PostgreSQL containers
- Test against real database semantics
- Tests become more realistic and reliable

---

## Dependency Injection — Avoid Heavy DI for YAWL

### Current: Manual (Good!)

```java
// YAWL currently uses manual DI (factory pattern)
YEngine engine = YEngine.getInstance();
YStatelessEngine stateless = YStatelessEngine.getInstance();
```

### When NOT to Add Spring/Guice

❌ **Don't add if**:
- Core YAWL is well-factored (it is)
- Minimal dependency injection needed
- Performance is critical (DI frameworks add startup latency)

### When to Consider Lightweight DI

✅ **Consider Micronautt** (if adding microservices later):

```xml
<!-- Only if building separate microservice →  agent pods -->
<dependency>
    <groupId>io.micronaut</groupId>
    <artifactId>micronaut-core</artifactId>
    <version>4.2.0</version>
</dependency>
```

**Reason**: Zero-reflection, compile-time DI, startup in <1s

### Recommendation

**✅ YAWL: Keep manual DI**
- No Spring/Quarkus/Micronaut overhead
- Use factory methods (already good)
- Add DI if deploying as cloud-native microservice

---

## Configuration Management — Avoid External Config Services

### Current: System Properties + XML (Good!)

YAWL uses:
- `System.getProperty()` for command-line overrides
- XML for workflow definitions
- Database for runtime state

### Modern Alternative: Hocon Config

```gradle
implementation 'com.typesafe:config:1.4.3'
```

**Usage**:

```hocon
// application.conf
yawl {
    engine {
        maxCases = 10000
        retryAttempts = 3
        healthCheckInterval = 30s
    }
}
```

```java
Config config = ConfigFactory.load();
int maxCases = config.getInt("yawl.engine.maxCases");
Duration healthCheck = config.getDuration("yawl.engine.healthCheckInterval");
```

### Recommendation

**⚠️ YAWL: Optional upgrade**
- Current XML config works fine
- Hocon if building REST API with complex configuration
- Avoid external config servers (Consul, etcd) for core YAWL

---

## API Frameworks — Minimal Adoption Strategy

### Option A: java.net.http (Current) ✅

```java
// Already used in A2A and MCP integration
HttpClient client = HttpClient.newBuilder()...build();
HttpRequest req = HttpRequest.newBuilder()...build();
HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());
```

✅ **Pros**: Zero dependencies, full control, virtual thread support
❌ **Cons**: Manual routing, serialization

### Option B: Lightweight Javalin (If Building REST API)

```gradle
implementation 'io.javalin:javalin:6.0.0'
```

```java
Javalin app = Javalin.create();

app.get("/api/agents/{id}", ctx -> {
    String agentID = ctx.pathParam("id");
    AgentStatus status = agent.getStatus();
    ctx.json(status);  // Jackson serialization
});

app.post("/api/workflows", ctx -> {
    WorkflowInput input = ctx.bodyAsClass(WorkflowInput.class);
    YIdentifier caseID = engine.execute(input.getSpec(), input.getVariables());
    ctx.json(new ExecutionResult(caseID));
});

app.start(8080);
```

✅ **Why Javalin**:
- Lightweight (no Spring overhead)
- Virtual thread ready
- Kotlin + Java friendly
- Built on Jetty (battle-tested)

❌ **Don't use if**:
- Exposing REST API via separate microservice (Quarkus better)
- Need heavy middleware (Spring Security, etc.)

### Recommendation

**✅ YAWL A2A/MCP: Keep java.net.http**
- Simple, proven, virtual thread ready

**⚠️ Future REST API: Consider Javalin**
- If exposing YAWL via REST endpoint
- Lightweight alternative to Spring Boot

---

## Serialization & Data Transfer

### XML → Modern Java + Jackson

**Current**: JAXB for XML workflows ✅ (Keep!)

```xml
<!-- pom.xml already has -->
<dependency>
    <groupId>jakarta.xml.bind</groupId>
    <artifactId>jakarta.xml.bind-api</artifactId>
</dependency>
```

✅ **Why JAXB is good**:
- Standard (jakarta.xml.bind)
- Schema-first (XSD → Java)
- Extensive validation

### JSON → Jackson Records ✅

```java
// Replace custom DTOs with records
public record CaseEvent(
    String caseID,
    String eventType,
    Instant timestamp
) { }

// Jackson handles automatically
CaseEvent event = mapper.readValue(json, CaseEvent.class);
```

### Protocol Buffers (Optional for Performance)

```gradle
implementation 'com.google.protobuf:protobuf-java:4.26.0'
```

✅ Use if:
- Need ultra-compact serialization (mobile agents)
- High-throughput streaming (1000s msg/sec)

❌ Don't use if:
- JSON sufficient (it usually is)
- Human readability important

### Recommendation

**✅ YAWL**:
- Keep JAXB for XML workflows
- Use Jackson + Records for JSON/REST
- Skip Protocol Buffers (overkill for current scale)

---

## Observability — Metrics & Tracing

### Metrics: Micrometer ✅ (Industry Standard)

```gradle
implementation 'io.micrometer:micrometer-core:1.13.0'
```

**Usage**:

```java
MeterRegistry registry = new MeterRegistry();

// Counter
Counter caseCounter = Counter.builder("yawl.cases.total")
    .register(registry);
caseCounter.increment();

// Gauge
AtomicInteger activeWorkflows = new AtomicInteger(0);
Gauge.builder("yawl.workflows.active", activeWorkflows, AtomicInteger::get)
    .register(registry);

// Timer
Timer.Sample timer = Timer.start();
executeWorkflow();
timer.stop(Timer.builder("yawl.execution.duration").register(registry));
```

### Tracing: OpenTelemetry

```gradle
implementation 'io.opentelemetry:opentelemetry-api:1.36.0'
implementation 'io.opentelemetry:opentelemetry-sdk:1.36.0'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp:1.36.0'
```

**Usage**:

```java
Tracer tracer = GlobalOpenTelemetry.getTracer("yawl-engine");

Span span = tracer.spanBuilder("execute_workflow")
    .setAttribute("caseID", caseID)
    .startSpan();

try (var scope = span.makeCurrent()) {
    executeWorkflow();
} finally {
    span.end();
}
```

### Recommendation

**✅ YAWL: Add Micrometer**
- Agent health metrics
- Case execution metrics
- Swarm load metrics

**⚠️ OpenTelemetry: Optional**
- Add if deploying to Kubernetes
- Use for distributed tracing across agents

---

## Security Libraries

### Already Using: JWT (JJWT) ✅

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
```

✅ **Good for**:
- A2A authentication
- REST API tokens
- SPIFFE workload identity

### TLS/mTLS: Built-in + SPIFFE Validator ✅

YAWL already has:
- `SpiffeValidator` (mTLS)
- `PinnedTrustManager` (certificate pinning)

✅ **Don't add additional SSL libraries**

### Password Hashing: Argon2id (If needed)

```gradle
implementation 'de.mkammerer:argon2-jvm:2.11'
```

✅ Use if:
- User authentication (not A2A)
- Storing passwords in database

❌ Don't use:
- BCrypt (slower, outdated)
- PBKDF2 (too weak)

### Recommendation

**✅ YAWL: Current security stack is solid**
- Keep JJWT + SPIFFE
- Add Argon2id only if user auth needed

---

## External Service Integrations

### Message Queue: Choose One

| Library | Use Case | Notes |
|---------|----------|-------|
| RabbitMQ (AMQP) | Event streaming | Industry standard, proven |
| Apache Kafka | High-throughput streaming | If >1000 msg/sec |
| Redis Streams | Simple pub/sub | Fast, in-memory |
| JMS (ActiveMQ) | Workflow event delivery | Traditional J2EE |

**Recommendation**: **RabbitMQ** (proven in enterprise workflows)

```gradle
implementation 'com.rabbitmq:amqp-client:5.20.0'
```

### Database: Current Stack ✅

YAWL supports:
- PostgreSQL (recommend for prod)
- MySQL
- H2 (testing only)

**No need to add more ORM layers** (Hibernate already in place)

### API Gateways: Kong or Nginx

Don't add API gateway library (use reverse proxy):

```nginx
# nginx.conf
upstream yawl_engine {
    server localhost:8080;
}

server {
    listen 443 ssl;

    location /api/ {
        proxy_pass http://yawl_engine;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
}
```

### Recommendation

**✅ YAWL**:
- Add RabbitMQ integration (for async case events)
- Keep PostgreSQL as primary database
- Use Nginx reverse proxy (not library)

---

## Final Recommendations Summary

### Immediate Adoption (No Downside)

| Library | Purpose | Effort |
|---------|---------|--------|
| Records + Jackson | DTOs, serialization | Low |
| java.time.* | Date/time | Low |
| SequencedMap | Collections | Low |
| Pattern Matching | Type checks | Low |
| Testcontainers | Real DB testing | Medium |

### Future Consideration (When Needed)

| Library | When | Why |
|---------|------|-----|
| Resilience4j | External service calls | Circuit breaker patterns |
| Javalin | REST API exposure | Lightweight REST framework |
| Micrometer | Cloud deployment | Metrics export |
| RabbitMQ | Async case events | Event-driven architecture |

### Explicitly Don't Add

| Library | Why Not |
|---------|---------|
| Spring Framework | Heavy, unnecessary for YAWL core |
| Guice | Manual DI sufficient |
| Groovy/Kotlin | Java 25 expressiveness enough |
| Scala | Adds complexity, minimal benefit |

---

## Dependency Update Strategy

### Monthly: Runtime Improvements
```bash
# Check for minor version updates
mvn versions:display-dependency-updates | grep MINOR
```

### Quarterly: Major Version Reviews
```bash
# Check for major updates
mvn versions:display-dependency-updates | grep -E "MAJOR|RELEASE"
```

### Security: Continuous
```bash
# OWASP dependency checker
mvn org.owasp:dependency-check-maven:check
```

---

See also:
- `JAVA25-MODERNIZATION-SURVEY.md` — Internal implementation improvements
- `.claude/rules/java25/modern-java.md` — YAWL Java 25 style guide

Last updated: 2026-02-20
