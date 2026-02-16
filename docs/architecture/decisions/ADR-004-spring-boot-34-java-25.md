# ADR-004: Spring Boot 3.4 + Java 25

## Status
**ACCEPTED**

## Context

YAWL v5.2 modernizes the technology stack to leverage the latest Java and Spring Boot features. This decision enables cloud-native deployment, modern observability, and significant performance improvements.

### Technology Evolution

**Historical Context:**
- YAWL v4.x: Java 8, Tomcat 8, Java EE 7
- YAWL v5.0: Java 11, Tomcat 9, Java EE 8
- YAWL v5.2: Java 25, Spring Boot 3.4, Jakarta EE 10

### Java 25 Features (LTS Release)

1. **Project Loom (Virtual Threads)**
   - Lightweight threads (JEP 444)
   - 10,000+ concurrent connections with minimal overhead
   - Perfect for workflow engine with blocking I/O

2. **Pattern Matching**
   - Switch expressions with pattern matching (JEP 441)
   - Record patterns for data extraction
   - Cleaner conditional logic

3. **Sequenced Collections**
   - First/last element access (JEP 431)
   - Reversed views
   - Better workflow state traversal

4. **String Templates (Preview)**
   - Safe string interpolation (JEP 430)
   - Reduced SQL injection risks
   - Better XPath expression building

5. **Performance Improvements**
   - Generational ZGC (low latency GC)
   - Improved JIT compilation
   - Better memory efficiency

### Spring Boot 3.4 Features

1. **Cloud-Native First**
   - Native support for Kubernetes
   - Built-in health checks and metrics
   - Graceful shutdown
   - Configuration management (ConfigMaps, Secrets)

2. **Observability**
   - Micrometer metrics out-of-the-box
   - OpenTelemetry integration
   - Distributed tracing
   - Structured logging

3. **Jakarta EE 10**
   - Jakarta Persistence 3.1 (JPA)
   - Jakarta Servlet 6.0
   - Jakarta Mail 2.1
   - Modern API standards

4. **Spring AI Integration**
   - LLM integration for autonomous agents
   - Vector database support
   - RAG (Retrieval Augmented Generation)
   - Perfect for YAWL agent framework

5. **Security Enhancements**
   - OAuth 2.1 support
   - SPIFFE/SPIRE integration
   - mTLS out-of-the-box
   - Zero-trust architecture

### Business Drivers

1. **Cloud-Native Deployment**
   - GKE, EKS, AKS compatibility
   - Serverless (Cloud Run, Lambda)
   - Auto-scaling support
   - Cost optimization

2. **Performance Requirements**
   - 10,000+ concurrent workflow cases
   - Sub-second response times
   - Horizontal scaling
   - Efficient resource usage

3. **Enterprise Integration**
   - Modern observability (Prometheus, Grafana)
   - Security compliance (SPIFFE)
   - Event-driven architecture (Kafka, RabbitMQ)
   - API gateway integration

4. **Developer Experience**
   - Modern Java features
   - Better IDE support
   - Comprehensive documentation
   - Active community

## Decision

**YAWL v5.2 will adopt Spring Boot 3.4 and require Java 25 (LTS) as the minimum version.**

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Spring Boot 3.4 Application                │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring Boot Actuator                      │  │
│  │  - Health checks                                  │  │
│  │  - Metrics (Micrometer)                           │  │
│  │  - Distributed tracing (OpenTelemetry)            │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         YAWL Engine Core                          │  │
│  │  - YEngine (workflow execution)                   │  │
│  │  - YStatelessEngine (serverless)                  │  │
│  │  - Virtual threads for concurrency               │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring Data JPA (Hibernate 6.5)           │  │
│  │  - Jakarta Persistence API                        │  │
│  │  - PostgreSQL, MySQL, Oracle support              │  │
│  └──────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Spring AI + MCP Integration               │  │
│  │  - Autonomous agents                              │  │
│  │  - LLM-powered decision making                    │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              Java 25 Virtual Machine                    │
│  - Virtual threads (Project Loom)                       │
│  - Pattern matching                                     │
│  - Generational ZGC                                     │
└─────────────────────────────────────────────────────────┘
```

### Implementation Strategy

#### 1. Spring Boot Application Structure

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class YawlEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(YawlEngineApplication.class, args);
    }

    @Bean
    public YEngine yawlEngine(YEngineConfig config) {
        return new YEngine(config);
    }

    @Bean
    public Executor taskExecutor() {
        // Use virtual threads for async processing
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

#### 2. Configuration (application.yml)

```yaml
spring:
  application:
    name: yawl-engine

  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 50

  threads:
    virtual:
      enabled: true

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  metrics:
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
```

#### 3. Health Checks

```java
@Component
public class YawlEngineHealthIndicator implements HealthIndicator {

    private final YEngine engine;

    @Override
    public Health health() {
        try {
            boolean isRunning = engine.isRunning();
            if (isRunning) {
                return Health.up()
                    .withDetail("activeCases", engine.getActiveCaseCount())
                    .withDetail("uptime", engine.getUptime())
                    .build();
            } else {
                return Health.down()
                    .withDetail("reason", "Engine not running")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

#### 4. Metrics

```java
@Component
public class YawlEngineMetrics {

    private final MeterRegistry registry;

    public YawlEngineMetrics(MeterRegistry registry) {
        this.registry = registry;

        Gauge.builder("yawl.engine.active.cases",
                     () -> YEngine.getInstance().getActiveCaseCount())
            .description("Number of active workflow cases")
            .register(registry);

        Counter.builder("yawl.engine.cases.created")
            .description("Total cases created")
            .register(registry);
    }
}
```

## Consequences

### Positive

1. **Virtual Threads Performance**
   - Handle 10,000+ concurrent cases with minimal overhead
   - No complex async/await patterns needed
   - Blocking I/O operations are cheap
   - Better CPU utilization

2. **Cloud-Native Ready**
   - Kubernetes health checks built-in
   - Metrics export to Prometheus
   - Distributed tracing to Jaeger/Tempo
   - ConfigMap/Secret integration

3. **Modern Observability**
   - Real-time metrics dashboard
   - Request tracing across services
   - Performance profiling
   - Error tracking

4. **Spring AI Integration**
   - Autonomous agent framework
   - LLM-powered decision making
   - Easy RAG implementation
   - Vector database integration

5. **Developer Productivity**
   - Auto-configuration
   - Extensive documentation
   - Large community
   - Modern IDE support

6. **Security**
   - OAuth 2.1 support
   - SPIFFE/SPIRE integration
   - mTLS configuration
   - Secrets management

### Negative

1. **Version Requirements**
   - Java 25 minimum (some orgs still on Java 11)
   - Migration effort from Java 11/17
   - Potential compatibility issues
   - Training required

2. **Spring Boot Dependency**
   - Larger artifact size (Spring Boot JARs)
   - More dependencies to manage
   - Learning curve for non-Spring developers
   - Framework lock-in

3. **Complexity**
   - More configuration options
   - More features to learn
   - Potential for misconfiguration
   - Debugging can be harder

4. **Memory Footprint**
   - Spring Boot apps use more memory
   - Minimum 512MB heap recommended
   - Not suitable for very constrained environments

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Java 25 adoption resistance | MEDIUM | HIGH | Document benefits, provide migration guide |
| Virtual thread bugs | LOW | HIGH | Comprehensive testing, fallback to platform threads |
| Spring Boot version issues | LOW | MEDIUM | Lock versions, test upgrades |
| Performance regression | LOW | MEDIUM | Benchmark, optimize configuration |

## Alternatives Considered

### Alternative 1: Stay on Java 11 + Java EE 8
**Rejected:** Blocks modern features and cloud-native adoption.

**Pros:**
- No migration effort
- Stable and proven
- Wider compatibility

**Cons:**
- No virtual threads
- No modern observability
- Missing Jakarta EE features
- Limited cloud-native support

### Alternative 2: Java 21 LTS Instead of Java 25
**Rejected:** Java 25 is the current LTS with better features.

**Pros:**
- More conservative choice
- Longer support runway
- More proven in production

**Cons:**
- Missing latest features
- Less community momentum
- Shorter long-term support

### Alternative 3: Quarkus Instead of Spring Boot
**Rejected:** Spring Boot better fits YAWL's architecture and team skills.

**Pros:**
- Smaller footprint
- Faster startup
- Native image support

**Cons:**
- Smaller ecosystem
- Less documentation
- Team unfamiliar with Quarkus
- Migration complexity

## Related ADRs

- ADR-006: OpenTelemetry for Observability (enabled by Spring Boot)
- ADR-010: Virtual Threads for Scalability (Java 25 feature)
- ADR-011: Jakarta EE Migration (Spring Boot 3.x requirement)
- ADR-009: Multi-Cloud Strategy (Spring Boot cloud-native features)

## Implementation Notes

### Java Version Check

```java
public class JavaVersionValidator {
    static {
        if (Runtime.version().feature() < 25) {
            throw new IllegalStateException(
                "YAWL v5.2 requires Java 25 or later. " +
                "Current version: " + Runtime.version()
            );
        }
    }
}
```

### Maven Configuration

```xml
<properties>
    <java.version>25</java.version>
    <spring-boot.version>3.4.2</spring-boot.version>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
</properties>

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.2</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

### Virtual Thread Configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Or programmatically:
```java
@Configuration
public class VirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
```

### Pattern Matching Example

```java
// Before (Java 11)
public String describeWorkItem(Object obj) {
    if (obj instanceof YWorkItem) {
        YWorkItem wi = (YWorkItem) obj;
        return "WorkItem: " + wi.getId();
    } else if (obj instanceof YTask) {
        YTask task = (YTask) obj;
        return "Task: " + task.getName();
    }
    return "Unknown";
}

// After (Java 25)
public String describeWorkItem(Object obj) {
    return switch (obj) {
        case YWorkItem wi -> "WorkItem: " + wi.getId();
        case YTask task -> "Task: " + task.getName();
        case null -> "Null";
        default -> "Unknown";
    };
}
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-10
**Implementation Status:** COMPLETE (v5.2)
**Next Review:** 2026-08-01 (6 months)

---

**Revision History:**
- 2026-02-10: Initial version approved
- 2026-02-16: Added virtual thread configuration examples
