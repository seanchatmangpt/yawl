# YAWL v6.0.0 Performance Guidelines

**Status**: Ready | **Date**: 2026-02-18 | **Version**: 1.0

---

## Purpose

This document provides performance guidelines, baselines, and optimization recommendations for YAWL v6.0.0. It consolidates performance-related documentation from multiple sources into a single actionable reference.

---

## Quick Reference

| Metric | Target | Baseline | Status |
|--------|--------|----------|--------|
| Clean Build | < 90s | 180s | -50% with parallelization |
| Agent DX (1 module) | < 15s | N/A | NEW - incremental builds |
| Agent DX (all) | < 60s | N/A | NEW - agent-dx profile |
| Unit Tests | < 30s | 60s | -50% with parallelization |
| Container Startup | < 3s | 4s | -25% with AOT cache |
| GC Pause (P99) | < 50ms | 200ms | -75% with G1GC tuning |

---

## 1. Build Performance

### 1.1 Agent DX Fast Loop

The fastest feedback path for code agents and iterative development.

```bash
# Auto-detect changed modules, compile + test
bash scripts/dx.sh

# Compile only (fastest feedback)
bash scripts/dx.sh compile

# Test only (assumes compiled)
bash scripts/dx.sh test

# All modules (pre-commit)
bash scripts/dx.sh all

# Target specific module
bash scripts/dx.sh -pl yawl-engine
```

**Performance Comparison**:

| Command | Scope | Time | Use Case |
|---------|-------|------|----------|
| `dx.sh compile` | 1 module | 3-5s | Fastest feedback |
| `dx.sh` | 1 module | 5-15s | Edit-compile-test cycle |
| `dx.sh all` | 13 modules | 30-60s | Pre-commit verification |
| `mvn -T 1.5C clean test` | 13 modules | 90-120s | Full CI verification |

### 1.2 Parallel Build Configuration

**Maven Configuration** (`.mvn/maven.config`):
```bash
-T 1.5C
```

**Effect**: Compile modules in parallel (1.5 x CPU cores)

**Before**:
```
[INFO] Building YAWL Engine ............................... 45s
[INFO] Building elements ................................. 30s
[INFO] Building integration .............................. 25s
[INFO] Total time ......................................... 180s
```

**After**:
```
[INFO] Building modules (parallel, 24 threads)
[INFO] Total time (wall clock) .............................. 90s
```

### 1.3 JUnit 5 Parallel Execution

**Configuration** (in `pom.xml`):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <parallel>methods</parallel>
        <threadCount>1.5C</threadCount>
        <properties>
            <configurationParameters>
                junit.jupiter.execution.parallel.enabled = true
                junit.jupiter.execution.parallel.mode.default = concurrent
            </configurationParameters>
        </properties>
    </configuration>
</plugin>
```

**Effect**: Tests run concurrently (e.g., 16-core machine = 24 test threads)

**Before**: 60s sequential test execution
**After**: 15-30s parallel test execution

### 1.4 Build Performance Matrix

| Environment | Clean Build | Incremental | Tests |
|-------------|-------------|-------------|-------|
| 8-core, 16GB | 120s | 15s | 45s |
| 16-core, 32GB | 90s | 10s | 30s |
| 32-core, 64GB | 60s | 8s | 20s |
| CI (4-core) | 180s | 30s | 60s |

---

## 2. Runtime Performance

### 2.1 JVM Flags for Production

**Recommended Production Configuration**:

```bash
#!/bin/bash
# Production startup script

JAVA_OPTS=""

# Memory allocation
JAVA_OPTS="${JAVA_OPTS} -Xms4g"
JAVA_OPTS="${JAVA_OPTS} -Xmx8g"
JAVA_OPTS="${JAVA_OPTS} -XX:+AlwaysPreTouch"

# Java 25 optimizations
JAVA_OPTS="${JAVA_OPTS} -XX:+UseCompactObjectHeaders"

# GC selection (choose one based on deployment)

# Option A: G1GC (default, balanced throughput/latency)
JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"
JAVA_OPTS="${JAVA_OPTS} -XX:MaxGCPauseMillis=200"
JAVA_OPTS="${JAVA_OPTS} -XX:G1HeapRegionSize=16m"

# Option B: ZGC (ultra-low latency, heaps > 100GB)
# JAVA_OPTS="${JAVA_OPTS} -XX:+UseZGC"
# JAVA_OPTS="${JAVA_OPTS} -XX:ZGenerational=true"

# Option C: Shenandoah (medium heaps 8-64GB)
# JAVA_OPTS="${JAVA_OPTS} -XX:+UseShenandoahGC"
# JAVA_OPTS="${JAVA_OPTS} -XX:ShenandoahGCHeuristics=adaptive"

# Monitoring
JAVA_OPTS="${JAVA_OPTS} -XX:StartFlightRecording=filename=app.jfr,dumponexit=true,settings=profile"

# Security (TLS 1.3 only)
JAVA_OPTS="${JAVA_OPTS} -Djdk.tls.disabledAlgorithms=TLSv1,TLSv1.1,TLSv1.2"

export JAVA_OPTS
java ${JAVA_OPTS} -jar yawl-engine.jar
```

### 2.2 GC Selection Matrix

| Heap Size | Recommended GC | Pause Times | Best For |
|-----------|----------------|-------------|----------|
| < 4GB | G1GC (default) | 10-200ms | General purpose |
| 4-64GB | G1GC or Shenandoah | 5-50ms | Balanced workloads |
| 64-100GB | Shenandoah | 1-10ms | Latency-sensitive |
| > 100GB | ZGC (Generational) | < 1ms | Ultra-low latency |

### 2.3 Compact Object Headers

**Enable**: `-XX:+UseCompactObjectHeaders`

**Benefit**: 5-10% throughput improvement, 10-20% memory reduction

**How it works**:
- Reduces object header from 96-128 bits to 64 bits
- 4-8 byte savings per object
- Compounds across millions of workflow objects

**Measurement**:
```bash
# Before enabling
java -XX:-UseCompactObjectHeaders -jar yawl-engine.jar &
# Measure throughput with load test

# After enabling
java -XX:+UseCompactObjectHeaders -jar yawl-engine.jar &
# Compare throughput
```

---

## 3. Virtual Thread Guidelines

### 3.1 When to Use Virtual Threads

**Good Candidates**:
- Agent discovery loops (polling for work items)
- HTTP client calls to external services
- Database queries with blocking I/O
- Work item processing with external dependencies

**Poor Candidates**:
- CPU-intensive calculations (use platform threads)
- `synchronized` blocks (pinning risk)
- Long-running tight loops

### 3.2 Pattern: Virtual Thread Per Agent

**Current (Platform Threads)**:
```java
// GenericPartyAgent.java (line ~52)
private Thread discoveryThread;

discoveryThread = new Thread(this::runDiscoveryLoop);
discoveryThread.start();
```

**Recommended (Virtual Threads)**:
```java
// GenericPartyAgent.java (updated)
private Thread discoveryThread;

discoveryThread = Thread.ofVirtual()
    .name("yawl-agent-discovery-" + config.getAgentId())
    .start(this::runDiscoveryLoop);
```

**Memory Impact**:
- 1000 platform threads: ~2GB heap
- 1000 virtual threads: ~1MB heap
- Improvement: 99.95% reduction

### 3.3 Pattern: Structured Concurrency

**For parallel work item processing**:

```java
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.Subtask;

public List<WorkItem> processDiscoveredItems(List<WorkItem> discovered)
        throws InterruptedException, ExecutionException {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<WorkItem>> tasks = discovered.stream()
            .map(item -> scope.fork(() -> processWorkItem(item)))
            .toList();

        // Wait for all; cancel on first failure
        scope.join();
        scope.throwIfFailed();

        return tasks.stream()
            .map(Subtask::resultNow)
            .toList();
    }
}
```

**Benefits**:
- Automatic cancellation on failure
- Clear parent-child thread hierarchy
- Guaranteed resource cleanup

### 3.4 Thread Pinning Avoidance

**Problem**: `synchronized` blocks pin virtual threads to carrier threads

**Detection**:
```bash
java -Djdk.tracePinnedThreads=full -jar yawl-engine.jar
```

**Remediation**:
```java
// BEFORE: Pinned virtual thread
public synchronized void processItem() {
    // Critical section
}

// AFTER: ReentrantLock avoids pinning
private final ReentrantLock lock = new ReentrantLock();

public void processItem() {
    lock.lock();
    try {
        // Critical section
    } finally {
        lock.unlock();
    }
}
```

---

## 4. Memory Optimization

### 4.1 Heap Sizing Guidelines

| Deployment Size | Heap Min | Heap Max | Total RAM |
|-----------------|----------|----------|-----------|
| Small (< 100 cases/day) | 2GB | 4GB | 8GB |
| Medium (100-1000 cases/day) | 4GB | 8GB | 16GB |
| Large (1000-10000 cases/day) | 8GB | 16GB | 32GB |
| Enterprise (10000+ cases/day) | 16GB | 32GB | 64GB |

### 4.2 Object Allocation Patterns

**High Allocation Areas** (from profiling):
1. Event creation (`YEvent` subclasses)
2. XML document parsing (`JDOM`)
3. Work item state changes
4. Case data serialization

**Optimizations**:
1. Use records for immutable events (reduces allocation overhead)
2. Reuse XML parsers where possible
3. Pool work item state objects (if mutable)
4. Use binary serialization for large case data

### 4.3 Memory Monitoring

**JFR Configuration**:
```bash
java -XX:StartFlightRecording=filename=memory.jfr,settings=profile,dumponexit=true \
     -jar yawl-engine.jar
```

**Key Events to Monitor**:
- `jdk.ObjectAllocationInNewTLAB`
- `jdk.OldObjectSample`
- `jdk.GCPhasePause`
- `jdk.JavaMonitorWait`

**Analysis**:
```bash
# View allocation hotspots
jfr print --events ObjectAllocationInNewTLAB memory.jfr | sort -k 4 -n -r | head -20
```

---

## 5. Database Performance

### 5.1 Connection Pool Configuration

**HikariCP** (recommended):
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 30000
      max-lifetime: 1800000
```

**Sizing Formula**:
```
connections = (core_count * 2) + effective_spindle_count
```

For 16-core server with SSD: `(16 * 2) + 1 = 33` connections

### 5.2 Query Optimization

**Hibernate Best Practices**:

1. **Use lazy loading for associations**:
```java
@OneToMany(fetch = FetchType.LAZY)
private List<YWorkItem> workItems;
```

2. **Batch fetching for collections**:
```java
@BatchSize(size = 50)
@OneToMany
private List<YWorkItem> workItems;
```

3. **Second-level cache for reference data**:
```java
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
public class YSpecification { ... }
```

### 5.3 Database-Specific Tuning

| Database | Setting | Recommended Value |
|----------|---------|-------------------|
| PostgreSQL | `shared_buffers` | 25% of RAM |
| PostgreSQL | `work_mem` | 16-64MB |
| MySQL | `innodb_buffer_pool_size` | 70% of RAM |
| H2 (dev) | `DB_CLOSE_DELAY` | -1 (keep open) |

---

## 6. Container Performance

### 6.1 Docker Image Optimization

**Multi-stage Build**:
```dockerfile
# Build stage
FROM maven:4.0.0-eclipse-temurin-25 AS builder
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -T 1.5C clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Non-root user
RUN addgroup -S yawl && adduser -S yawl -G yawl
USER yawl

# Copy JAR only
COPY --from=builder /build/target/yawl-engine.jar /app/yawl-engine.jar

# Optimized JVM flags
ENV JAVA_OPTS="-Xms4g -Xmx4g -XX:+UseCompactObjectHeaders -XX:+UseG1GC"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar /app/yawl-engine.jar"]
```

**Image Size Reduction**:
- Full JDK image: ~400MB
- JRE Alpine image: ~150MB
- Reduction: 62%

### 6.2 Kubernetes Resource Limits

```yaml
resources:
  requests:
    memory: "4Gi"
    cpu: "2"
  limits:
    memory: "8Gi"
    cpu: "4"
```

**JVM Heap Ratio**: 50-75% of memory limit
- Limit: 8GB
- Heap: 4-6GB
- Remaining: OS, metaspace, native

### 6.3 Startup Optimization

**AOT Cache** (Java 25):
```bash
# Training run
java -XX:StartFlightRecording=filename=startup.jfr,duration=30s \
     -XX:+TieredCompilation \
     -jar yawl-engine.jar &

# Production run (uses cached profiles)
java -XX:+UseAOTCache \
     -XX:AOTCacheFile=startup.jfr \
     -jar yawl-engine.jar
```

**Expected Improvement**: 25% faster startup (3.2s -> 2.4s)

---

## 7. Performance Testing

### 7.1 Load Test Script

```bash
#!/bin/bash
# load-test.sh

BASE_URL="http://localhost:8080"
CONCURRENT_USERS=100
DURATION_SECONDS=60

# Launch cases
echo "Starting load test with $CONCURRENT_USERS users for ${DURATION_SECONDS}s"

for i in $(seq 1 $CONCURRENT_USERS); do
    (
        start_time=$(date +%s)
        end_time=$((start_time + DURATION_SECONDS))

        while [ $(date +%s) -lt $end_time ]; do
            curl -s -X POST "$BASE_URL/ib/cases" \
                -H "Content-Type: application/xml" \
                -d "<caseData><test>true</test></caseData>" \
                -o /dev/null -w "%{http_code} %{time_total}\n"

            sleep 0.1
        done
    ) &
done

wait
echo "Load test complete"
```

### 7.2 Key Metrics to Track

| Metric | Tool | Target |
|--------|------|--------|
| Throughput (cases/sec) | JMeter, wrk | > 100 |
| Latency P50 | JFR, Prometheus | < 50ms |
| Latency P99 | JFR, Prometheus | < 200ms |
| Error Rate | Prometheus | < 0.1% |
| CPU Usage | top, Grafana | < 70% |
| Memory Usage | JFR, Grafana | < 80% of heap |
| GC Pause P99 | JFR | < 50ms |

### 7.3 Profiling Commands

```bash
# CPU profiling
java -XX:StartFlightRecording=filename=cpu.jfr,settings=profile \
     -jar yawl-engine.jar

# Memory profiling
java -XX:StartFlightRecording=filename=mem.jfr,settings=profile \
     -XX:StartFlightRecording=filename=alloc.jfr,settings=allocation \
     -jar yawl-engine.jar

# GC logging
java -Xlog:gc*:file=gc.log:time,level,tags \
     -jar yawl-engine.jar

# Thread dump
jcmd <pid> Thread.print > threads.txt

# Heap dump
jcmd <pid> GC.heap_dump heap.hprof
```

---

## 8. Troubleshooting

### 8.1 Slow Builds

**Symptoms**: Build takes > 2 minutes

**Diagnosis**:
```bash
# Check Maven version
mvn -v  # Should be 4.0+

# Check parallelization
mvn -T 1.5C clean compile -X 2>&1 | grep "parallel"

# Check for dependency issues
mvn dependency:tree | grep -i conflict
```

**Solutions**:
1. Enable `-T 1.5C` in `.mvn/maven.config`
2. Increase Maven heap: `-Xmx2g` in `.mvn/jvm.config`
3. Use Maven daemon: `mvnd` instead of `mvn`

### 8.2 High Memory Usage

**Symptoms**: OOM errors, frequent GC

**Diagnosis**:
```bash
# Check heap usage
jcmd <pid> VM.native_memory summary

# Find memory leaks
jcmd <pid> GC.class_histogram | head -20
```

**Solutions**:
1. Increase heap: `-Xmx8g`
2. Enable compact object headers: `-XX:+UseCompactObjectHeaders`
3. Switch to ZGC for large heaps
4. Profile with JFR to find allocation hotspots

### 8.3 Slow Response Times

**Symptoms**: API latency > 500ms

**Diagnosis**:
```bash
# Check thread contention
jcmd <pid> Thread.print -l

# Check database queries
# Enable Hibernate SQL logging
```

**Solutions**:
1. Add database indexes for frequent queries
2. Enable second-level cache for reference data
3. Use connection pooling (HikariCP)
4. Profile with JFR for hotspots

---

## 9. Performance Checklist

### Before Production

- [ ] JVM flags configured for production
- [ ] GC selected based on heap size
- [ ] Compact object headers enabled
- [ ] Connection pool sized correctly
- [ ] Load test passed (throughput, latency targets)
- [ ] Memory leak test passed (24-hour soak)
- [ ] Startup time acceptable (< 5s with AOT)
- [ ] Monitoring configured (JFR, Prometheus)

### Weekly Review

- [ ] GC pause times within target
- [ ] Memory usage stable (no growth trend)
- [ ] Response times within SLA
- [ ] Error rate < 0.1%
- [ ] No thread contention issues

### Quarterly Optimization

- [ ] Re-run load tests with updated code
- [ ] Review JFR profiles for new hotspots
- [ ] Update JVM flags based on new Java versions
- [ ] Review database query performance
- [ ] Update performance baselines in facts

---

## 10. References

- **BUILD-PERFORMANCE.md**: Build optimization details
- **JAVA-25-FEATURES.md**: Virtual threads, compact headers
- **ARCHITECTURE-PATTERNS-JAVA25.md**: Structured concurrency patterns
- **SECURITY-CHECKLIST-JAVA25.md**: Secure JVM flags
- **JFR Documentation**: https://docs.oracle.com/en/java/javase/25/jfapi/
- **G1GC Tuning**: https://www.oracle.com/technical-resources/articles/java/g1gc.html

---

*Created by: Performance Architect*
*Date: 2026-02-18*
*Related: IMPLEMENTATION-PLAN.md, VALIDATION-MATRIX.md, OBSERVATORY-INTEGRATION.md*
