# Java 25 Virtual Threads - Quick Reference

**YAWL v5.2 | Java 25 Upgrade**
**Quick Reference for Developers**

---

## Installation

```bash
# Install Java 25 (SDKMAN)
sdk install java 25-tem

# Verify
java --version  # Should show: openjdk 25

# Set as default
sdk default java 25-tem
```

---

## Build Commands

```bash
# Maven build with Java 25
mvn clean package --enable-preview

# Run tests
mvn test --enable-preview

# Skip tests
mvn clean package -DskipTests

# Specific test
mvn test -Dtest=VirtualThreadScalabilityTest
```

```bash
# Ant build with Java 25
cd build
ant buildWebApps

# Clean and rebuild
ant clean buildAll
```

---

## Virtual Thread Patterns

### Pattern 1: Simple Thread Pool Replacement

```java
// BEFORE
ExecutorService executor = Executors.newFixedThreadPool(10);

// AFTER
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
```

### Pattern 2: HTTP Server Executor

```java
// BEFORE
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newFixedThreadPool(10));

// AFTER
HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

### Pattern 3: Structured Concurrency

```java
import java.util.concurrent.StructuredTaskScope;

try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var task1 = scope.fork(() -> fetchData1());
    var task2 = scope.fork(() -> fetchData2());

    scope.join();  // Wait for all tasks

    return task1.get() + task2.get();
}
```

### Pattern 4: Timeout Protection

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var task = scope.fork(() -> longRunningOperation());

    // Timeout after 30 seconds
    scope.joinUntil(Instant.now().plus(30, ChronoUnit.SECONDS));

    if (task.state() == Subtask.State.SUCCESS) {
        return task.get();
    } else {
        throw new TimeoutException("Operation timed out");
    }
}
```

---

## Avoiding Pinning

### Bad: Synchronized with I/O

```java
// PINNING!
public synchronized Data get(String id) {
    if (cache.containsKey(id)) {
        return cache.get(id);
    }
    Data data = database.fetch(id);  // I/O blocks platform thread
    cache.put(id, data);
    return data;
}
```

### Good: ReentrantLock

```java
// NO PINNING
private final ReentrantLock lock = new ReentrantLock();

public Data get(String id) {
    lock.lock();
    try {
        if (cache.containsKey(id)) {
            return cache.get(id);
        }
        Data data = database.fetch(id);  // Virtual thread can yield
        cache.put(id, data);
        return data;
    } finally {
        lock.unlock();
    }
}
```

---

## JVM Flags

### Development
```bash
java --enable-preview \
     -Djdk.tracePinnedThreads=full \
     -jar yawl.jar
```

### Production
```bash
java --enable-preview \
     -XX:+UseG1GC \
     -XX:MaxRAMPercentage=75.0 \
     -Djdk.virtualThreadScheduler.parallelism=64 \
     -Djdk.tracePinnedThreads=short \
     -XX:+FlightRecorder \
     -jar yawl.jar
```

---

## Docker

### Build
```bash
docker build -f Dockerfile.java25 -t yawl:java25 .
```

### Run
```bash
docker run -p 8080:8080 \
  -e DB_HOST=postgres \
  -e DB_PASSWORD=secret \
  yawl:java25
```

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

---

## Monitoring

### Check Pinning
```bash
# Enable pinning detection
-Djdk.tracePinnedThreads=full

# Check logs
grep "monitors:" logs/yawl.log
```

### JFR Recording
```bash
# Start recording
java -XX:StartFlightRecording=filename=recording.jfr \
     -jar yawl.jar

# Analyze
jfr print --events jdk.VirtualThreadStart recording.jfr
jfr print --events jdk.VirtualThreadPinned recording.jfr
```

### Metrics
```bash
# Platform threads (should be low)
curl http://localhost:8080/actuator/metrics/jvm.threads.live

# Memory usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Custom virtual thread metrics
curl http://localhost:8080/actuator/metrics/yawl.virtual.threads.active
```

---

## Common Issues

### Issue: Compilation Error

```
error: release version 25 not supported
```

**Fix:**
```bash
# Verify Java 25
java --version

# Check JAVA_HOME
echo $JAVA_HOME

# Update Maven compiler plugin to 3.13.0
```

### Issue: Virtual Thread Pinning

```
Thread[#123] pinned
    <== monitors:1
```

**Fix:**
Replace `synchronized` with `ReentrantLock` in the indicated method.

### Issue: Preview Feature Warning

```
warning: using preview features
```

**Fix:**
Add `--enable-preview` to compiler and runtime arguments (already included in configs).

---

## Testing

### Unit Tests
```bash
# All tests
mvn test

# Virtual thread tests
mvn test -Dtest=VirtualThreadScalabilityTest

# High concurrency
mvn test -Dtest=VirtualThreadScalabilityTest -DtaskCount=100000
```

### Load Testing
```bash
# HTTP load test (1000 concurrent)
ab -n 10000 -c 1000 http://localhost:8080/yawl/ib

# Agent discovery
time curl -X POST http://localhost:9090/agents/discover \
  -d '{"agent_count": 100}'
```

---

## Migration Checklist

- [ ] Java 25 installed
- [ ] Maven 3.9.6+ installed
- [ ] Update pom.xml (compiler source/target to 25)
- [ ] Update build.xml (javac source/target to 25)
- [ ] Update Dockerfiles (eclipse-temurin:25)
- [ ] Replace thread pools with virtual threads
- [ ] Test compilation: `mvn clean compile`
- [ ] Run tests: `mvn test`
- [ ] Check for pinning: `grep "monitors:" logs/yawl.log`
- [ ] Load test
- [ ] Deploy to staging
- [ ] Monitor production

---

## Performance Expectations

| Metric | Before (Java 21) | After (Java 25) |
|--------|------------------|-----------------|
| HTTP throughput | 100 req/s | 1,000+ req/s |
| Memory per thread | 1 MB | 200 bytes |
| Max concurrent operations | Limited by thread pool | Unlimited |

---

## Resources

- **Upgrade Guide:** `/docs/deployment/java25-upgrade-guide.md`
- **Checklist:** `/docs/deployment/java25-implementation-checklist.md`
- **Migration Script:** `/scripts/migrate-to-java25.sh`
- **JEP 444:** https://openjdk.org/jeps/444

---

## Support

- **GitHub Issues:** https://github.com/yawlfoundation/yawl/issues
- **Mailing List:** yawl@list.unsw.edu.au

---

**Version:** 1.0.0 | **Last Updated:** 2026-02-15
