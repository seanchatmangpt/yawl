# Java Migration Guide for YAWL v6.0.0

**YAWL Version**: 5.2 → 6.0.0  
**Target**: Java 21/25 + Spring Boot 3.4  
**Focus**: Virtual threads for agent-based, high-fan-out concurrency  
**Last Updated**: February 22, 2026

---

## Executive Summary

This guide consolidates migration information for upgrading YAWL from v5.2 to v6.0.0, covering Java 21/25 adoption, virtual thread implementation, Spring Boot integration, and deployment strategies.

**Key Changes in v6.0.0**:
- **Java 25 LTS** with virtual threads, pattern matching, and records
- **MCP Server** for LLM integration (Model Context Protocol)
- **A2A Server** for agent-to-agent coordination
- **YStatelessEngine** for cloud-native deployments
- **Spring Boot 3.4** integration for new services

---

## Prerequisites and Requirements

### System Requirements

| Component | Minimum | Recommended |
|-----------|---------|-------------|
| Java | 21 (LTS) / 25 (LTS) | 25.0.1+ |
| Memory | 1GB heap | 2-4GB heap |
| CPU | 2 cores | 4+ cores |
| Container | Docker 24+ | Docker 25+ |
| Build Tools | Maven 3.9+ | Maven 3.9+ |

### Java Version Selection

| Java Version | Use Case | Status |
|--------------|----------|--------|
| **Java 25** | Production deployments with virtual threads | ✅ Recommended |
| **Java 21** | Development, testing, gradual migration | ✅ Supported |
| **Java 11** | Legacy systems (deprecated in v6.0) | ❌ Not supported |

---

## Phase 1: Java Foundation Migration

### 1.1 Update Build Configuration

**Update `pom.xml` for Java 25**:

```xml
<properties>
    <maven.compiler.release>25</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
    <!-- Java 25 features supported -->
    <dependency>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-core</artifactId>
        <version>1.37</version>
    </dependency>
</dependencies>
```

**Dockerfile Update**:

```dockerfile
# Use Java 25 base image
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw -T 1.5C clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
```

### 1.2 Java 21 Compatibility (For Gradual Migration)

If maintaining Java 21 support alongside Java 25:

```xml
<profiles>
    <profile>
        <id>java21</id>
        <activation>
            <jdk>21</jdk>
        </activation>
        <properties>
            <maven.compiler.release>21</maven.compiler.release>
        </properties>
    </profile>
    <profile>
        <id>java25</id>
        <activation>
            <jdk>25</jdk>
        </activation>
        <properties>
            <maven.compiler.release>25</maven.compiler.release>
        </properties>
    </profile>
</profiles>
```

### 1.3 Update Environment

```bash
# Check Java version
java -version

# Should show:
# openjdk version "25.0.1" 2026-02-22
# OpenJDK 64-Bit Server VM (build 25.0.1+10-Debian-1deb12u1)

# Set JAVA_HOME if needed
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
```

---

## Phase 2: Virtual Thread Migration

### 2.1 Virtual Thread Adoption Strategy

**Before (Java 11/21 - Platform Threads)**:
```java
// Traditional bounded thread pools
private final ExecutorService executor = Executors.newFixedThreadPool(12);

public void notifyListeners(Set<Listener> listeners, Event event) {
    for (Listener listener : listeners) {
        executor.execute(() -> listener.handle(event));
    }
}
```

**After (Java 25 - Virtual Threads)**:
```java
// Virtual threads for unlimited concurrency
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

public void notifyListeners(Set<Listener> listeners, Event event) {
    for (Listener listener : listeners) {
        executor.execute(() -> listener.handle(event));  // Now uses virtual threads
    }
}
```

### 2.2 Thread Pool Migration Priority

**High Priority** (Immediate impact):
1. `MultiThreadEventNotifier` - Event fan-out to thousands of listeners
2. `AgentRegistry` - HTTP server for agent registration
3. `YawlA2AServer` - A2A protocol handling
4. `GenericPartyAgent` - Agent HTTP server

**Medium Priority** (Performance improvement):
5. `InterfaceX_EngineSideClient` - Interface X operations
6. `WorkletEventServer` - Worklet event handling
7. `ObserverGatewayController` - Observer notifications
8. `YEventLogger` - Async logging

**Low Priority** (Scheduled tasks):
9. `JobTimer` - Scheduled job execution (keep as-is)
10. `Sessions` - Session cleanup (keep as-is)

### 2.3 Structured Concurrency Implementation

For complex parallel operations:

```java
// Before: Manual thread management
ExecutorService executor = Executors.newFixedThreadPool(10);
Future<String> future1 = executor.submit(() -> fetchAgentCard(url1));
Future<String> future2 = executor.submit(() -> fetchAgentCard(url2));
String result1 = future1.get(); // Blocks
String result2 = future2.get();
executor.shutdown();

// After: Structured concurrency
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> task1 = scope.fork(() -> fetchAgentCard(url1));
    Subtask<String> task2 = scope.fork(() -> fetchAgentCard(url2));
    
    scope.join();           // Wait for all
    scope.throwIfFailed();  // Propagate exceptions
    
    String result1 = task1.get();
    String result2 = task2.get();
} // Auto-cleanup, guaranteed cancellation
```

### 2.4 Scoped Values for Context

Replace `ThreadLocal` with `ScopedValue`:

```java
// Before: ThreadLocal (not virtual-thread friendly)
private static final ThreadLocal<String> currentUser = new ThreadLocal<>();

// After: Scoped Values
private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();

// Usage
ScopedValue.where(CURRENT_USER, "admin").run(() -> {
    // CURRENT_USER.get() == "admin" in this scope
    engine.processCase(caseID);
});
```

---

## Phase 3: Spring Boot 3.4 Integration

### 3.1 Hybrid Migration Strategy

| Component | Strategy | Rationale |
|-----------|----------|-----------|
| **Core Engine** (yawl.war) | Keep Servlet-based | Stable, well-tested, low risk |
| **Resource Service** | Keep Servlet-based | Complex JSF UI, high refactor cost |
| **Worklet Service** | Keep Servlet-based | Tightly coupled to engine |
| **MCP Server** | **NEW: Spring Boot** | Modern, I/O-intensive, benefits from virtual threads |
| **Agent Registry** | **NEW: Spring Boot** | Microservice pattern, cloud-native |
| **A2A Server** | **NEW: Spring Boot** | Protocol server, REST-friendly |

### 3.2 Spring Boot MCP Server Implementation

**New Module Structure**:
```
yawl-mcp-server/
├── pom.xml                          # Spring Boot parent
├── src/main/java/
│   └── org/yawlfoundation/yawl/mcp/
│       ├── YawlMcpServerApplication.java    # @SpringBootApplication
│       ├── config/
│       │   ├── YawlEngineConfig.java        # @ConfigurationProperties
│       │   └── VirtualThreadConfig.java     # Virtual thread customization
│       ├── controller/
│       │   └── HealthController.java        # Actuator extensions
│       ├── service/
│       │   ├── McpToolService.java          # Business logic
│       │   └── YawlIntegrationService.java  # Interface B client
│       └── metrics/
│           └── McpMetrics.java              # Micrometer metrics
└── src/main/resources/
    ├── application.yml                       # Configuration
    └── application-prod.yml                  # Production overrides
```

**pom.xml**:
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.0</version>
</parent>

<dependencies>
    <!-- Spring Web with virtual threads -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Virtual thread support -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Actuator for monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Micrometer for metrics -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-otel</artifactId>
    </dependency>
</dependencies>
```

**application.yml**:
```yaml
spring:
  application:
    name: yawl-mcp-server
  threads:
    virtual:
      enabled: true  # Enable virtual threads
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

**Main Application with Virtual Threads**:
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

    @Bean
    public McpToolService mcpToolService(YawlEngine engine) {
        return new McpToolService(engine);
    }
}
```

### 3.3 Virtual Thread Patterns

#### Pattern 1: HTTP Server with Virtual Threads

```java
// Before: Platform threads with limited concurrency
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newFixedThreadPool(10));  // Max 10 requests

// After: Unlimited virtual threads
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());  // Unlimited
```

#### Pattern 2: Batch Processing with Structured Concurrency

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<Subtask<WorkItem>> tasks = workItems.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();
    
    scope.join();
    scope.throwIfFailed();
    
    return tasks.stream()
        .map(Subtask::resultNow)
        .toList();
}
```

---

## Phase 4: Performance Optimization

### 4.1 JVM Configuration for Java 25

#### Production JVM Flags

```bash
# Baseline production flags
JAVA_OPTS="-XX:+UseCompactObjectHeaders \
           -XX:+UseZGC \
           -Xms1g -Xmx4g \
           -XX:+UseContainerSupport \
           -XX:MaxRAMPercentage=75.0 \
           -XX:+UseAOTCache \
           -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m \
           -Djava.security.properties=/app/security.properties"
```

#### Container-Specific Flags

```bash
# Docker/Kubernetes environments
JAVA_OPTS="-XX:+UseCompactObjectHeaders \
           -XX:+UseZGC \
           -XX:+UseContainerSupport \
           -XX:MaxRAMPercentage=75.0 \
           -XX:+UseAOTCache \
           -XX:InitialRAMPercentage=30.0 \
           -XX:MaxRAMPercentage=75.0"
```

#### AOT Cache Configuration (Java 25 Only)

```bash
# Generate training profile
java -XX:StartFlightRecording=filename=profile.jfr \
     -XX:+TieredCompilation \
     -jar app.jar < test_cases.txt

# Use cached profile in production
java -XX:+UseAOTCache \
     -XX:AOTCacheFile=profile.jfr \
     -jar app.jar
```

### 4.2 Garbage Collector Selection

| Heap Size | Workload | Recommended GC | JVM Flags |
|-----------|----------|----------------|-----------|
| < 4GB | General | G1GC (default) | None needed |
| 4-64GB | Low-latency | Shenandoah | `-XX:+UseShenandoahGC` |
| > 64GB | Ultra-low-latency | ZGC | `-XX:+UseZGC` |
| Any | Maximum throughput | G1GC | `-XX:+UseG1GC -XX:MaxGCPauseMillis=200` |

#### ZGC Configuration for Large Heaps

```bash
JAVA_OPTS="-XX:+UseZGC \
           -XX:ZFragmentLimit=5 \
           -Xms64g -Xmx128g \
           -Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags"
```

### 4.3 Memory Sizing

| Component | Minimum | Recommended | Formula |
|-----------|---------|-------------|---------|
| Heap | 1GB | 2-4GB | 100MB per 1000 cases |
| Metaspace | 128MB | 256MB | `-XX:MaxMetaspaceSize=256m` |
| Direct Memory | 256MB | 512MB | `-XX:MaxDirectMemorySize=512m` |
| Code Cache | 128MB | 256MB | `-XX:ReservedCodeCacheSize=256m` |

---

## Phase 5: Security Configuration

### 5.1 TLS 1.3 Enforcement

```properties
# security.properties
jdk.tls.disabledAlgorithms=SSLv3, TLSv1, TLSv1.1, TLSv1.2, DTLSv1.0, DTLSv1.2
jdk.tls.client.protocols=TLSv1.3
jdk.tls.server.protocols=TLSv1.3
```

### 5.2 Cryptographic Requirements

| Algorithm | Minimum | Status |
|-----------|---------|--------|
| AES | AES-256-GCM | Required |
| RSA | RSA-3072 | Required |
| ECDSA | P-256+ | Required |
| SHA | SHA-256+ | Required |
| MD5, SHA-1 | N/A | Prohibited |
| DES, 3DES | N/A | Prohibited |

### 5.3 Security Manager Removal

Java 25 has removed the Security Manager. Replace with:
- Spring Security for RBAC
- Container security (Docker/K8s)
- OPA/Gatekeeper for policy enforcement

---

## Phase 6: Monitoring and Observability

### 6.1 JVM Metrics with Micrometer

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,threaddump
  metrics:
    tags:
      application: ${spring.application.name}
    export:
      prometheus:
        enabled: true
```

### 6.2 Virtual Thread Monitoring

```bash
# Java Flight Recording for virtual threads
jcmd <pid> JFR.start settings=profile filename=virtual-threads.jfr

# Thread dump includes virtual threads
jcmd <pid> Thread.dump_to_file -format=json threads.json

# Virtual thread pinning detection
jcmd <pid> VM.flags | grep virtual
```

### 6.3 GC Logging Configuration

```bash
# Production GC logging
-Xlog:gc*:file=/app/logs/gc.log:time,uptime,level,tags:filecount=5,filesize=10m \
-Xlog:gc*=info:file=/app/logs/gc-details.log:time,uptime,level,tags \
-XX:+PrintGCApplicationStoppedTime \
-XX:+PrintGCApplicationConcurrentTime
```

---

## Testing Strategy

### 6.1 Compatibility Testing

```bash
# Test with Java 25
java -version

# Run existing test suite
mvn test

# Check for deprecated APIs
jdeprscan --for-removal target/yawl-engine.jar
```

### 6.2 Virtual Thread Validation

```java
@Test
public void testEventNotification_VirtualThreads() {
    EventNotifier notifier = new MultiThreadEventNotifier();
    
    Set<Listener> listeners = createListeners(1000);
    CountDownLatch latch = new CountDownLatch(1000);
    
    listeners.forEach(l -> l.setLatch(latch));
    
    notifier.announceEvent(listeners, new TestEvent());
    
    boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertTrue(completed, "All 1000 listeners should be notified concurrently");
}
```

### 6.3 Load Testing

```bash
# Test 10,000 concurrent agent registrations
ab -n 10000 -c 1000 -p agent-payload.json \
   -T application/json \
   http://localhost:9090/agents/register

# Expected with virtual threads: all 10,000 succeed
# Expected with platform threads: connection refused after ~100
```

---

## Migration Commands

### 7.1 Build Commands

```bash
# Compile with Java 25
mvn clean compile -Dmaven.compiler.release=25

# Parallel build (50% faster)
mvn -T 1.5C clean compile

# Package with tests
mvn clean package

# Generate AOT cache (Java 25 only)
mvn spring-boot:build-image -Dspring-boot.build-image.aot=true
```

### 7.2 Testing Commands

```bash
# Run all tests
mvn test

# Run tests with coverage
mvn clean jacoco:prepare-agent test jacoco:report

# Run integration tests
mvn failsafe:integration-test

# Run specific test
mvn test -Dtest="YawlMcpServerTest"
```

### 7.3 Validation Commands

```bash
# Check Java version
java -version

# Validate JVM flags
java -XX:+PrintFlagsFinal -version | grep -E "(UseCompactObjectHeaders|UseZGC|UseAOTCache)"

# Check virtual thread support
java -version | grep "25"

# Run observatory validation
bash scripts/observatory/observatory.sh
```

---

## Troubleshooting

### 8.1 Common Issues

| Issue | Symptoms | Resolution |
|-------|----------|------------|
| High GC pause | Latency spikes | Switch to ZGC/Shenandoah |
| OOM | Container killed | Increase heap, check for leaks |
| Slow startup | >10s cold start | Enable AOT cache (Java 25) |
| Thread exhaustion | Task timeouts | Use virtual threads |
| Virtual thread pinning | Poor performance | Avoid synchronized blocks with I/O |

### 8.2 Diagnostic Commands

```bash
# Check JVM version
java -version

# Check GC configuration
jcmd <pid> VM.flags | grep GC

# Check virtual thread count
jcmd <pid> Thread.dump_to_file -format=json - | grep virtual

# Memory analysis
jcmd <pid> GC.heap_info

# Thread dump
jcmd <pid> Thread.print
```

### 8.3 Performance Tuning

```bash
# Monitor GC pauses
jstat -gcutil <pid> 1s

# Monitor heap usage
jstat -gccapacity <pid>

# Monitor thread count
jstat -thread <pid>

# Generate heap dump
jcmd <pid> GC.heap_dump /tmp/heapdump.hprof
```

---

## Release Timeline

### Phase 1: Java 25 Foundation (Completed)
- [x] Update build configuration for Java 25
- [x] Update Docker images to Java 25
- [x] Audit and remove deprecated APIs
- [x] Test with Java 25

### Phase 2: Virtual Threads (In Progress)
- [ ] Replace `MultiThreadEventNotifier` with virtual threads
- [ ] Replace `AgentRegistry` thread pool with virtual threads
- [ ] Replace `YawlA2AServer` thread pool with virtual threads
- [ ] Load test each service

### Phase 3: Structured Concurrency (Planned)
- [ ] Implement agent discovery with `StructuredTaskScope`
- [ ] Implement parallel MCP tool execution
- [ ] Create reusable utility classes

### Phase 4: Spring Boot Integration (Planned)
- [ ] Migrate MCP Server to Spring Boot
- [ ] Migrate Agent Registry to Spring Boot
- [ ] Add monitoring and metrics

---

## References

### Official Documentation
- [Java 25 Release Notes](https://www.oracle.com/java/technologies/javase/25-relnotes.html)
- [Virtual Threads Guide](https://openjdk.org/jeps/444)
- [Spring Boot 3.4 Documentation](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes)

### Best Practices
- [Virtual Threads Best Practices](https://inside.java/2021/05/10/lets-talk-about-virtual-threads/)
- [Spring Boot with Virtual Threads](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#web.server.tomcat.thread-pool)
- [Java 25 Performance Tuning](https://docs.oracle.com/en/java/javase/25/core/)

### Migration Resources
- [YAWL v6.0.0 Release Notes](../v6/V6-RELEASE-SUMMARY.md)
- [Integration Architecture Guide](../v6/INTEGRATION-ARCHITECTURE-REFERENCE.md)
- [Performance Guidelines](../v6/performance/PERFORMANCE-GUIDELINES.md)

---

**Document Status**: Active  
**Version**: 1.0  
**Last Updated**: February 22, 2026  
**Maintainers**: YAWL Architecture Team
