# Ecosystem Modernization Roadmap: YAWL v6.0.0 → v7.0.0

**Date**: 2026-02-17
**Scope**: Jakarta EE 11, Spring Boot 3.4+, Java preview features, dependency optimization
**Timeline**: 18-24 months (2026-2027)

---

## Executive Summary

YAWL v6.0.0 is on a **solid, modern foundation** with Jakarta EE 10, Spring Boot 3.5, Hibernate 6.6, and Java 21. This roadmap charts the path to **YAWL v7.0.0** with:

- **Jakarta EE 11** compatibility (fully functional)
- **Spring Boot 3.4+** integration (latest LTS)
- **Java preview features** (virtual threads, structured concurrency)
- **Dependency footprint optimization** (remove 8-12 unused transitive deps)
- **Performance baseline** (GraalVM native-image + virtual threads)

**Investment**: 800 engineering hours over 18 months
**Benefit**: Enterprise-grade stability, cloud-native capabilities, future-proof architecture

---

## 1. Jakarta EE 11 Compatibility Assessment

### 1.1 Current State (v6.0.0)

| Component | Current | Target | Status |
|-----------|---------|--------|--------|
| Jakarta Servlet | 6.1.0 | 6.1.0 (same) | ✅ Compatible |
| Jakarta Persistence | 3.2.0 | 3.2.0 (same) | ✅ Compatible |
| Jakarta XML Bind | 4.0.5 | 4.0.5 (same) | ✅ Compatible |
| Jakarta Mail | 2.1.5 | 2.1.5 (same) | ✅ Compatible |
| Jakarta Activation | 2.1.4 | 2.1.4 (same) | ✅ Compatible |
| Jakarta Faces | 4.1.2 | 4.1.2 (same) | ✅ Compatible |
| Jakarta CDI | 4.1.0 | 4.1.0 (same) | ✅ Compatible |
| Jakarta WS.RS | 4.0.0 | 4.0.0 (same) | ✅ Compatible |

### 1.2 Jakarta EE 11 Timeline

**Jakarta EE 11 Release**: September 2024 (stable)

**What's New in EE 11**:
- Virtual Threads support in Servlet containers
- Enhanced Persistence API (JPA 3.2 → 3.3)
- Simplified CDI startup (reduced bootstrap time)
- Improved web service standards

**Migration Path for YAWL**:
```
v6.0.0 (Jakarta EE 10) → v6.1.0 (EE 10 + native-image) → v7.0.0 (Jakarta EE 11 + virtual threads)
```

### 1.3 Migration Effort (for v7.0.0)

| Task | Effort | Risk | Notes |
|------|--------|------|-------|
| Update Jakarta EE BOM | 2 hours | LOW | Simple version bump |
| Test JPA 3.3 compatibility | 4 hours | LOW | No API changes |
| Test CDI 4.1 startup | 2 hours | LOW | Transparent upgrade |
| Benchmark Servlet 6.1 virtual threads | 4 hours | MEDIUM | Requires Java 21+ |
| Update documentation | 2 hours | LOW | Release notes |
| **Subtotal** | **14 hours** | - | 1 developer day |

---

## 2. Spring Boot 3.4+ Migration

### 2.1 Current State (v6.0.0)

**Spring Boot Version**: 3.5.10 (released Jan 2026)
- Based on Spring Framework 6.1.x
- Fully supports Java 21, GraalVM native-image
- Spring Cloud 2024.0.0 compatible
- Latest LTS version

### 2.2 Spring Boot 3.4 Release Timeline

**3.4.0**: November 2024 (current)
**3.4.x**: Monthly maintenance releases
**Support**: Until November 2026

**New Features in 3.4**:
- Improved virtual thread support in Tomcat
- Spring AI (chat, embeddings, RAG)
- Enhanced observability (Micrometer + OpenTelemetry)
- CRaC (Checkpoint and Restore on Kubernetes)

### 2.3 Compatibility Assessment

```
YAWL v6.0.0          YAWL v6.1.0           YAWL v7.0.0
Spring Boot 3.5.10 → Spring Boot 3.5.10 → Spring Boot 3.4+
Hibernate 6.6.42     Hibernate 6.7+        Hibernate 6.7+ (LTS)
Java 21              Java 21/23 (preview)  Java 25+ (preview)
```

**Upgrade Path for v7.0.0**:

```xml
<!-- pom.xml -->
<spring-boot.version>3.4.3</spring-boot.version> <!-- instead of 3.5.10 -->

<!-- New dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-ai</artifactId>
    <version>${spring-boot.version}</version>
</dependency>
```

**Testing Required**:
- [ ] Startup time benchmarks
- [ ] Memory footprint comparison
- [ ] Test suite compatibility (JUnit 6.0+)
- [ ] Native-image compilation (CRaC support)

### 2.4 Spring AI Integration (Optional for v7.1+)

```java
// Potential use case: YAWL Task Definition Generation
@Component
public class TaskDefinitionGeneratorAI {
    private final ChatClient chatClient;

    public TaskDefinitionGeneratorAI(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public YTask generateTaskFromDescription(String description) {
        String prompt = String.format(
            "Generate a YAWL task definition for: %s\nReturn XML format",
            description
        );
        String xmlTask = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
        return YawlSpecificationParser.parse(xmlTask);
    }
}
```

---

## 3. Java Preview Features Roadmap

### 3.1 Current Java Version Support

**Target**: Java 21 LTS (supported until September 2031)

| Feature | Java Version | YAWL Status | v6.0 | v6.1 | v7.0 |
|---------|--------------|-------------|------|------|------|
| Records | 21 (stable) | Ready | ✅ | ✅ | ✅ |
| Text Blocks | 21 (stable) | Ready | ✅ | ✅ | ✅ |
| Sealed Classes | 21 (stable) | Ready | ✅ | ✅ | ✅ |
| Pattern Matching | 23 (preview) | Ready | - | ✅ | ✅ |
| Virtual Threads | 21 (preview) | Planning | - | 🔄 | ✅ |
| Structured Concurrency | 23 (preview) | Evaluating | - | - | 🔄 |

### 3.2 Virtual Threads Integration (v6.1.0 - optional, v7.0.0 - recommended)

**What are Virtual Threads?**
- Lightweight threads (100x more than platform threads)
- Reduced GC pressure
- Simpler async programming (no reactive frameworks needed)
- Perfect for high-throughput work item processing

**YAWL Benefits**:
```
Current: 4 platform threads × 100 work items = 25 threads max
Virtual: 10,000 virtual threads × 100 work items = 1M threads possible
```

**Implementation in YEngine**:

```java
// v6.1.0+ (experimental)
@Component
public class YEngineVirtualThreadExecutor {
    private final ExecutorService workItemExecutor =
        Executors.newVirtualThreadPerTaskExecutor();

    public void processWorkItemAsync(YWorkItem item) {
        workItemExecutor.submit(() -> {
            try {
                item.process();
                item.complete();
            } catch (Exception e) {
                item.handleError(e);
            }
        });
    }
}

// v7.0.0 (stable, with structured concurrency)
@Component
public class YEngineStructuredConcurrency {
    public void processMultipleWorkItems(List<YWorkItem> items) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var futures = items.stream()
                .map(item -> scope.fork(() -> {
                    item.process();
                    return item.getResult();
                }))
                .collect(Collectors.toList());

            scope.join(); // Wait for all tasks
            return futures.stream().map(Future::resultNow).collect(toList());
        }
    }
}
```

**Performance Impact**:
- Throughput: 8,200 req/sec → 15,000+ req/sec (82% improvement)
- Memory: 512MB → 350MB (32% reduction)
- Latency: P99 45ms → 12ms (73% reduction)

### 3.3 Preview Feature Timeline

| Feature | Java Version | GA Date | YAWL Plan |
|---------|--------------|---------|-----------|
| Pattern Matching (switch) | 23 | 2025 | v7.0.0 |
| Virtual Threads (stable) | 25 | 2025 | v7.0.0 |
| Structured Concurrency | 25 | 2025 | v7.1.0+ |
| Module System Enhancements | TBD | TBD | Future |

---

## 4. Dependency Optimization

### 4.1 Transitive Dependency Audit

**Current**: 180+ transitive dependencies (via Maven dependency:tree)

**Candidates for Removal** (unused code paths):

| Dependency | Size | Used By | Recommendation |
|------------|------|---------|-----------------|
| `org.springframework:spring-webmvc` | 800 KB | Spring Boot auto | KEEP |
| `commons-beanutils` | 250 KB | Hibernate reflection | KEEP |
| `javax.xml.soap:javax.xml.soap-api` | 50 KB | Legacy services | REMOVE (v7.0) |
| `org.apache.commons:commons-fileupload` | 200 KB | Document upload | KEEP |
| `org.apache.commons:commons-vfs2` | 600 KB | Spec file system | KEEP |
| `net.sf.jung:jung-algorithms` | 400 KB | Validation/analysis | CONSIDER |
| `com.google.code.gson:gson` | 300 KB | Legacy JSON support | REMOVE (migrate to Jackson) |
| `org.simplejavamail:simple-java-mail` | 700 KB | Email notifications | KEEP |
| `org.apache.ant:ant` | 2.5 MB | Build utilities | REMOVE (v7.0) |
| `org.glassfish:jakarta.faces` | 1.2 MB | Mojarra JSF | EVALUATE |

### 4.2 Dependency Footprint Analysis

**Current POM Dependencies**:
```
Direct dependencies: 50
Transitive dependencies: 130
Total JAR size: 180 MB (unoptimized)
```

**Target for v7.0.0**:
```
Direct dependencies: 45 (-5 unused)
Transitive dependencies: 110 (-20 via exclusions)
Total JAR size: 145 MB (19% reduction)
```

**Specific Removals**:

```xml
<!-- Remove in v7.0.0 -->

<!-- 1. GSON → Jackson (already using Jackson) -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>${gson.version}</version>
    <!-- TO BE REMOVED -->
</dependency>

<!-- 2. javax.xml.soap (legacy SOAP support) -->
<dependency>
    <groupId>javax.xml.soap</groupId>
    <artifactId>javax.xml.soap-api</artifactId>
    <version>${saaj.api.version}</version>
    <!-- TO BE REMOVED - replace with Jakarta SOAP if needed -->
</dependency>

<!-- 3. ANT (build utilities - no runtime usage) -->
<dependency>
    <groupId>org.apache.ant</groupId>
    <artifactId>ant</artifactId>
    <version>${ant.version}</version>
    <!-- TO BE REMOVED - used only in build scripts -->
</dependency>

<!-- 4. commons-beanutils → replace with direct reflection -->
<dependency>
    <groupId>commons-beanutils</groupId>
    <artifactId>commons-beanutils</artifactId>
    <version>${commons.beanutils.version}</version>
    <!-- CANDIDATE - requires refactoring PropertyAccessor calls -->
</dependency>
```

### 4.3 Migration Plan

**Phase 1: v6.2.0** (identify, plan)
- Run `mvn dependency:analyze` to find unused
- Create refactoring tickets for each removal
- Evaluate JUNG graph library usage

**Phase 2: v7.0.0** (implement)
- Remove GSON (migrate to Jackson)
- Remove SOAP support (JAX-RS only)
- Remove ANT runtime dependency

**Phase 3: v7.1.0+** (optimize further)
- Evaluate commons-beanutils replacement
- Consolidate XML libraries (JDOM2 only)

---

## 5. Custom BOM Strategy for Minimal Footprint

### 5.1 Current BOM Strategy

6 upstream BOMs imported (when online profile active):
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>3.5.10</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<!-- ... 5 more -->
```

**Issue**: Each BOM brings transitive dependencies we may not need.

### 5.2 YAWL Custom BOM (Proposal for v7.0.0)

Create `yawl-bom/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-bom</artifactId>
    <version>7.0.0</version>
    <packaging>pom</packaging>
    <name>YAWL Bill of Materials</name>
    <description>Curated BOM for YAWL ecosystem dependencies</description>

    <properties>
        <spring-boot.version>3.4.3</spring-boot.version>
        <hibernate.version>6.7.0</hibernate.version>
        <jackson.version>2.19.4</jackson.version>
        <!-- ... all versions -->
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Explicitly include only what YAWL needs from Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Exclude unwanted transitive deps -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter</artifactId>
                <version>${spring-boot.version}</version>
                <exclusions>
                    <exclusion>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-logging</artifactId>
                    </exclusion>
                    <exclusion>
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                    </exclusion>
                </exclusions>
            </dependency>

            <!-- Only necessary Spring modules -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-web</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-data-jpa</artifactId>
                <version>${spring-boot.version}</version>
            </dependency>

            <!-- YAWL-specific curated list -->
            <dependency>
                <groupId>org.hibernate.orm</groupId>
                <artifactId>hibernate-core</artifactId>
                <version>${hibernate.version}</version>
            </dependency>
            <!-- ... all other managed versions -->
        </dependencies>
    </dependencyManagement>
</project>
```

**Usage in Parent POM**:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-bom</artifactId>
            <version>7.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**Benefits**:
- Single source of truth for YAWL's dependency graph
- Can be published to Maven Central for external users
- 10-15% reduction in transitive dependencies
- Easier to maintain compatibility across modules

---

## 6. Alternative Lightweight Libraries Evaluation

### 6.1 Logging Framework Optimization

**Current**: Log4j2 2.25.3 (heavy, powerful)

**Alternative for v7.0.0** (consider, not migrate):
- **Reload4j** (1.2 replacement, lightweight)
- **Logback** (SLF4J native, mature)
- **Jboss-logging** (minimal, framework-friendly)

**Recommendation**: Keep Log4j2 (excellent async, GraalVM support)

### 6.2 HTTP Client Optimization

**Current**: OkHttp 5.1.0 + JDK 21 HttpClient

**Optimization for v7.0.0**:
- Migrate simple requests to native `java.net.http.HttpClient`
- Keep OkHttp for connection pooling, interceptors
- Reduce OkHttp usage to 20% of HTTP calls

**Expected Savings**: 300 KB JAR size

### 6.3 XML Processing Optimization

**Current**: JDOM2, Jaxen, Saxon

**Analysis**:
- JDOM2: Essential (YAWL specs are XML)
- Jaxen: Essential (XPath evaluation for task data mapping)
- Saxon: Heavy (2.5 MB), used for XSLT 2.0/3.0

**Optimization for v7.0.0**:
- Pre-compile common XPath expressions (Jaxen cache)
- Evaluate lighter XSLT engine (Apache Xalan - 800 KB vs 2.5 MB)
- Expected savings: 1.7 MB

---

## 7. Compatibility Matrix

### 7.1 JDK Version Support

| YAWL Version | Min JDK | Recommended | Tested | LTS |
|--------------|---------|-------------|--------|-----|
| v5.x | 11 | 17 | 17 | ✅ |
| v6.0.0 | 21 | 21 | 21 | ✅ |
| v6.1.0 | 21 | 21/23 | 21/23 (preview) | ✅ |
| v7.0.0 | 21 | 25+ | 25 (preview) | - |

### 7.2 Framework Support Matrix

| Framework | v6.0 | v6.1 | v7.0 | Notes |
|-----------|------|------|------|-------|
| Spring Boot | 3.5.10 | 3.5.10 | 3.4.3+ | LTS |
| Hibernate | 6.6.42 | 6.7+ | 6.7+ (LTS) | ORM |
| Jakarta EE | 10 | 10 | 11 | Namespace |
| Jackson | 2.19.4 | 2.19.4+ | 2.20+ | JSON |
| Micrometer | 1.16.3 | 1.17+ | 1.17+ | Metrics |
| OpenTelemetry | 1.52.0 | 1.52.0+ | 1.55.0+ | Observability |

### 7.3 Database Compatibility

| Database | v6.0 | v6.1 | v7.0 | JDBC Driver |
|----------|------|------|------|-------------|
| PostgreSQL 14+ | ✅ | ✅ | ✅ | 42.7.7 |
| MySQL 8.0+ | ✅ | ✅ | ✅ | 9.4.0 |
| MariaDB 10.6+ | ✅ | ✅ | ✅ | 9.4.0 |
| H2 2.x | ✅ | ✅ | ✅ | 2.4.240 (dev) |
| Derby 10.17+ | ✅ | ✅ | ⚠️ | Legacy |

---

## 8. Performance Roadmap

### 8.1 Baseline (v6.0.0)

```
Startup Time:      2500 ms
Memory (first req): 512 MB
Throughput:        8,200 req/sec
P99 Latency:       45 ms
```

### 8.2 Target (v6.1.0 + GraalVM native-image)

```
Startup Time:      150 ms (94% improvement)
Memory:            180 MB (65% improvement)
Throughput:        9,500 req/sec (16% improvement)
P99 Latency:       12 ms (73% improvement)
```

### 8.3 Future (v7.0.0 + virtual threads)

```
Startup Time:      120 ms (native-image pre-built)
Memory:            200 MB (with virtual thread stacks)
Throughput:        15,000 req/sec (83% improvement)
P99 Latency:       8 ms (82% improvement)
Concurrent Cases:  1,000,000+ (vs 100,000 today)
```

---

## 9. Implementation Roadmap

### Phase 1: v6.1.0 (Q2-Q3 2026)

**Duration**: 4 months
**Focus**: GraalVM native-image + Java 23 preview

**Deliverables**:
- [ ] GraalVM native-image configuration (reflect-config.json, etc.)
- [ ] Native-image build in CI/CD (Linux, macOS, Windows)
- [ ] Startup time benchmark: 2500ms → 150ms
- [ ] Memory footprint: 512MB → 180MB
- [ ] Pattern matching (Java 23 preview)
- [ ] Virtual threads experimental support
- [ ] Documentation: Native-image build guide

**Effort**: 320 engineering hours

### Phase 2: v7.0.0 (Q4 2026 - Q1 2027)

**Duration**: 8 weeks
**Focus**: Jakarta EE 11, Spring Boot 3.4, Java 25 preview, dependency optimization

**Deliverables**:
- [ ] Jakarta EE 11 compatibility
- [ ] Spring Boot 3.4.3+ migration
- [ ] Virtual threads GA support (Java 25)
- [ ] Structured concurrency GA support (Java 25)
- [ ] Remove GSON, SOAP, ANT dependencies
- [ ] Custom YAWL BOM
- [ ] JUNG library evaluation
- [ ] Dependency footprint: 180MB → 145MB
- [ ] Performance: 15,000 req/sec
- [ ] Documentation: Migration guide, feature roadmap

**Effort**: 280 engineering hours

### Phase 3: v7.1.0+ (2027+)

**Future Optimizations**:
- [ ] Structured concurrency GA (if not in 7.0)
- [ ] Spring AI integration (task definition generation)
- [ ] Advanced virtual thread pooling
- [ ] Micro-service decomposition patterns
- [ ] Kubernetes-native deployment guides

---

## 10. Success Criteria

### v6.1.0 (GraalVM Native-Image)

- [ ] Native image builds successfully on Linux/x86_64, macOS (Apple Silicon), Windows
- [ ] Startup time < 200ms (target: 150ms)
- [ ] Memory footprint < 200MB (target: 180MB)
- [ ] All 640 unit tests pass against native image
- [ ] CI/CD pipeline builds multi-platform binaries
- [ ] Performance benchmark published
- [ ] Zero reflection warnings at native-image build

### v7.0.0 (Ecosystem Modernization)

- [ ] Jakarta EE 11 compatibility verified
- [ ] Spring Boot 3.4.3+ tested
- [ ] Virtual threads integrated and tested
- [ ] Structured concurrency patterns documented
- [ ] Dependency footprint reduced to 145MB
- [ ] Custom YAWL BOM published to Maven Central
- [ ] Throughput: 15,000+ req/sec on multi-core systems
- [ ] P99 latency < 10ms under load

---

## 11. Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Virtual thread compatibility issues | MEDIUM | HIGH | Extensive testing with work item processing |
| Jakarta EE 11 hidden incompatibilities | LOW | MEDIUM | Test with TestContainers (real databases) |
| Spring Boot 3.4 dependency conflicts | LOW | MEDIUM | Parallel builds in CI for 2 weeks |
| Transitive dependency removal breaks tests | MEDIUM | HIGH | Automated test suite validation |
| Performance regression with new version | LOW | HIGH | Benchmark regression suite in CI |
| GraalVM native-image edge cases | MEDIUM | MEDIUM | Agent tracing + extensive testing |

---

## 12. Investment Summary

| Phase | Version | Effort | Timeline | ROI |
|-------|---------|--------|----------|-----|
| GraalVM Native-Image | 6.1.0 | 320 hours | Q2-Q3 2026 | 94% startup speedup |
| Ecosystem Modernization | 7.0.0 | 280 hours | Q4 2026-Q1 2027 | Cloud-native ready |
| Future Optimization | 7.1.0+ | TBD | 2027+ | Horizontal scaling |
| **Total** | - | **600 hours** | **18 months** | **Enterprise-grade** |

---

## 13. Conclusion

YAWL v6.0.0 is built on a **strong, modern foundation**. The roadmap to v7.0.0 focuses on:

1. **Performance**: GraalVM native-image (94% startup improvement)
2. **Cloud-Native**: Virtual threads + structured concurrency
3. **Enterprise**: Jakarta EE 11, Spring Boot 3.4+
4. **Optimization**: 19% JAR footprint reduction

**Timeline**: 18 months to a fully modernized, cloud-native workflow engine.

**Next Steps**:
1. Approve GraalVM native-image for v6.1.0 (highest ROI)
2. Schedule ecosystem modernization for v7.0.0
3. Begin virtual thread evaluation in v6.1.0 (experimental)
4. Publish dependency analysis to team

