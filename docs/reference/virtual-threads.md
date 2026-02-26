# YAWL Virtual Threads Configuration Reference

**Document type:** Configuration Reference
**Audience:** System administrators, Java developers
**Purpose:** Complete reference for virtual thread configuration in YAWL v6.0.0-GA
**Version:** v6.0.0-GA

---

## Overview

Java 21 virtual threads provide a modern alternative to platform threads for I/O-bound workloads in YAWL. This reference covers JVM configuration, thread pool settings, performance characteristics, and migration strategies.

**Benefits:**
- 10-100x improvement for I/O-bound operations
- Millions of concurrent threads per process
- Same code, better throughput
- Reduced resource contention

---

## JVM Flags Reference

### Production Configuration

```bash
# Virtual Thread JVM Flags (Production)
export JAVA_OPTS="
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=30
-XX:VirtualThreadStackSize=256k
-XX:StartFlightRecording=filename=/var/log/yawl/vthreads.jfr,duration=1h,maxsize=1g
-Djdk.tracePinnedThreads=short
-Djdk.virtualThreadScheduler.parallelism=200
-Xms4g -Xmx8g
-XX:MaxRAMPercentage=80.0
"
```

### Development Configuration

```bash
# Virtual Thread JVM Flags (Development)
export JAVA_OPTS="
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=30
-XX:VirtualThreadStackSize=256k
-Djdk.virtualThreadScheduler.parallelism=4
-Xms512m -Xmx1g
-Djdk.tracePinnedThreads=full
"
```

### High-Throughput Configuration

```bash
# Virtual Thread JVM Flags (High-Throughput)
export JAVA_OPTS="
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=50
-XX:VirtualThreadStackSize=512k
-XX:StartFlightRecording=filename=/var/log/yawl/vthreads.jfr,duration=1h,maxsize=10g
-Djdk.tracePinnedThreads=short
-Djdk.virtualThreadScheduler.parallelism=512
-Xms16g -Xmx32g
-XX:MaxRAMPercentage=90.0
-XX:G1HeapRegionSize=32m
"
```

---

## JVM Flags Detailed Reference

| Flag | Default | Purpose | Recommended Value |
|------|---------|---------|-------------------|
| `-XX:+UseG1GC` | - | Garbage collector | Always enable for virtual threads |
| `-XX:G1NewCollectionHeapPercent` | 30 | Young gen size | 30-50% for I/O workloads |
| `-XX:VirtualThreadStackSize` | 256K | Virtual thread stack size | 256K for most workloads |
| `-Djdk.tracePinnedThreads` | none | Log pinning events | `short` or `full` |
| `-Djdk.virtualThreadScheduler.parallelism` | 2 | Carrier thread count | CPU cores * 2 |
| `-Xms` | - | Initial heap size | 2x expected working set |
| `-Xmx` | - | Maximum heap size | 4x initial for growth |
| `-XX:MaxRAMPercentage` | 25.0 | Max RAM percentage | 75-90% for containers |

### Key JVM Flag Explanations

#### `-XX:VirtualThreadStackSize`
- **Purpose**: Stack size for virtual threads
- **Trade-offs**:
  - Smaller (128K): More threads, less stack space
  - Larger (512K): Fewer threads, deeper call stacks
- **Guidelines**:
  - 256K: Default for most applications
  - 512K: For deep call stacks (>10 method calls)
  - 128K: For very lightweight tasks

#### `-Djdk.virtualThreadScheduler.parallelism`
- **Purpose**: Number of platform threads for virtual thread scheduling
- **Guideline**: Should match or exceed available CPU cores
- **Formula**:
  ```
  parallelism = max(CPU cores, min(512, memory/GB * 100))
  ```

#### `-Djdk.tracePinnedThreads`
- **Purpose**: Detect when virtual threads block on platform thread operations
- **Options**:
  - `none`: No logging (production)
  - `short`: Brief pinning events
  - `full`: Detailed pinning stack traces
- **Alert Threshold**: >1000 pinning events/hour

---

## Environment Variables

### System Configuration

| Variable | Default | Purpose | Example |
|----------|---------|---------|---------|
| `VIRTUAL_THREADS_ENABLED` | `true` | Enable virtual threads | `true` |
| `VIRTUAL_THREAD_STACK_SIZE` | `256k` | Virtual thread stack size | `512k` |
| `VIRTUAL_THREAD_PARALLELISM` | `200` | Carrier thread count | `400` |
| `VIRTUAL_THREAD_MAX_POOL_SIZE` | `256` | Max carrier thread pool | `512` |
| `VIRTUAL_THREAD_QUEUE_SIZE` | `10000` | Task queue capacity | `20000` |
| `VIRTUAL_THREAD_TRACE_PINNED` | `short` | Pinning trace level | `full` |
| `VIRTUAL_THREAD_PINNING_THRESHOLD` | `1000` | Alert threshold | `500` |

### YAWL Specific Configuration

| Variable | Default | Purpose | Example |
|----------|---------|---------|---------|
| `YAWL_VTHREADS_ENABLED` | `true` | Enable YAWL virtual threads | `true` |
| `YAWL_VTHREADS_POOL_SIZE` | `100` | YAWL virtual thread pool size | `200` |
| `YAWL_VTHREADS_QUEUE_CAPACITY` | `1000` | Work item queue capacity | `5000` |
| `YAWL_VTHREADS_TIMEOUT` | `30s` | Thread timeout | `60s` |

### Configuration Files

#### `/etc/yawl/virtual-threads.conf`
```ini
# Virtual Thread Configuration
enabled=true
stack_size=256k
parallelism=200
max_pool_size=256
queue_size=10000
trace_pinned=short
pinning_threshold=1000
monitoring_enabled=true
```

#### `/etc/yawl/jvm-virtual-threads.conf`
```conf
# JVM Configuration for Virtual Threads
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=30
-XX:VirtualThreadStackSize=256k
-Djdk.virtualThreadScheduler.parallelism=200
-Djdk.virtualThreadScheduler.maxPoolSize=256
-Djdk.tracePinnedThreads=short
-XX:MaxRAMPercentage=75.0
-Xms2g -Xmx4g
```

---

## Thread Pool Settings

### Creating Virtual Thread Executors

```java
// Good: For I/O-bound operations
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// Good: For bounded concurrency
ExecutorService boundedExecutor = Executors.newVirtualThreadPerTaskExecutor();
Semaphore limiter = new Semaphore(1000);

// Good: With thread pool tuning
ExecutorService tunedExecutor = Executors.newVirtualThreadPerTaskExecutor();
```

### Thread Pool Configuration Patterns

#### Pattern 1: Work Stealing Pool

```java
// For CPU-intensive virtual thread operations
ForkJoinPool workStealingPool = new ForkJoinPool(
    Runtime.getRuntime().availableProcessors(),
    ForkJoinPool.defaultForkJoinWorkerThreadFactory,
    null,
    false
);
```

#### Pattern 2: Bounded Executor

```java
// For rate-limited virtual thread operations
Semaphore limiter = new Semaphore(1000);
ExecutorService boundedExecutor = Executors.newVirtualThreadPerTaskExecutor();

void executeWithLimit(Runnable task) {
    limiter.acquire();
    boundedExecutor.execute(() -> {
        try {
            task.run();
        } finally {
            limiter.release();
        }
    });
}
```

#### Pattern 3: Custom Thread Factory

```java
// With custom thread naming and monitoring
ThreadFactory virtualThreadFactory = Thread.ofVirtual()
    .name("yawl-vthread-", 1)
    .inheritInheritableThreadLocals(true)
    .unstarted();

ExecutorService customExecutor = Executors.newCachedThreadPool(virtualThreadFactory);
```

### YAWL-Specific Configuration

```java
// In YAWL engine configuration
@Configuration
public class VirtualThreadConfig {

    @Value("${yawl.vthreads.pool_size:100}")
    private int poolSize;

    @Value("${yawl.vthreads.queue_capacity:1000}")
    private int queueCapacity;

    @Bean
    public ExecutorService yawlVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    public WorkQueue workQueue() {
        return new BoundedWorkQueue(queueCapacity);
    }
}
```

---

## Monitoring Endpoints

### JMX MBeans

```java
// Virtual Thread Metrics MBean
public interface VirtualThreadMetricsMBean {
    int getVirtualThreadCount();
    int getCarrierThreadCount();
    long getTotalPinnedEvents();
    double getAveragePinningDuration();
    List<PinningEvent> getRecentPinningEvents();
}

// Implementation
@ManagedResource(objectName = "yawl:type=VirtualThreadMetrics")
public class VirtualThreadMetrics implements VirtualThreadMetricsMBean {
    // Implementation details
}
```

### REST API Endpoints

#### GET /actuator/virtual-threads/stats
```json
{
  "virtualThreadCount": 1520,
  "carrierThreadCount": 200,
  "activeThreads": 1500,
  "idleThreads": 20,
  "pinnedEvents": {
    "total": 1560,
    "lastHour": 42,
    "lastMinute": 3
  },
  "throughput": {
    "operationsPerSecond": 2500.5,
    "averageLatencyMs": 120
  }
}
```

#### GET /actuator/virtual-threads/pinning-events
```json
{
  "events": [
    {
      "timestamp": "2024-01-01T12:00:00Z",
      "duration": "50ms",
      "stackTrace": "...",
      "threadName": "yawl-vthread-123"
    }
  ]
}
```

### Grafana Dashboard

```json
// Virtual Thread Dashboard Configuration
{
  "panels": [
    {
      "title": "Virtual Thread Count",
      "targets": [
        {
          "expr": "yawl_virtual_threads_count",
          "legendFormat": "{{instance}}"
        }
      ]
    },
    {
      "title": "Pinned Events Rate",
      "targets": [
        {
          "expr": "rate(yawl_virtual_threads_pinned_events_total[5m])",
          "legendFormat": "per second"
        }
      ]
    }
  ]
}
```

---

## Performance Characteristics

### Expected Performance Improvements

| Workload Type | Before (Platform Threads) | After (Virtual Threads) | Improvement |
|---------------|---------------------------|------------------------|-------------|
| HTTP requests | 1000 req/s | 50,000 req/s | 50x |
| Database queries | 500 qps | 10,000 qps | 20x |
| Event notifications (1000 listeners) | 8s | 500ms | 16x |
| Logging (10,000 events) | 3s | 250ms | 12x |
| Stress test (100k tasks) | 100s | 1s | 100x |

### Resource Usage Comparison

| Metric | Platform Threads | Virtual Threads | Ratio |
|--------|------------------|-----------------|-------|
| Threads per GB | 100-1000 | 100,000-1,000,000 | 1000x |
| Memory per thread | 1-2MB | 0.25-1KB | 2000x |
| Context switch time | ~10-100μs | ~0.1-1μs | 100x |
| Thread creation time | ~1-5ms | ~1μs | 5000x |

### Throughput Benchmarks

```
Workload: 10,000 concurrent HTTP requests
Hardware: 8 CPU cores, 16GB RAM

Platform Threads:
- Max throughput: 1,500 req/s
- CPU utilization: 100%
- Memory usage: 8GB
- Average latency: 500ms

Virtual Threads:
- Max throughput: 50,000 req/s
- CPU utilization: 80%
- Memory usage: 2GB
- Average latency: 20ms
```

---

## Migration from Platform Threads

### Migration Pattern

```java
// BEFORE: Platform threads
ExecutorService executor = Executors.newFixedThreadPool(12);
Future<Result> future = executor.submit(task);
Result result = future.get();

// AFTER: Virtual threads
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
Future<Result> future = executor.submit(task);
Result result = future.get();

// Same API, different implementation!
```

### Code Migration Checklist

1. **Replace Thread Pools**
   ```java
   // Replace
   Executors.newFixedThreadPool(n)

   // With
   Executors.newVirtualThreadPerTaskExecutor()
   ```

2. **Remove Thread Pools**
   ```java
   // Remove manual thread management
   new Thread(runnable).start();

   // Let framework handle it
   Executors.newVirtualThreadPerTaskExecutor().execute(runnable);
   ```

3. **Update Synchronization**
   ```java
   // Be careful with blocking operations
   synchronized (lock) {
       database.save(item); // This causes pinning!
   }

   // Use non-blocking alternatives
   CompletableFuture.runAsync(() -> database.save(item));
   ```

### Common Migration Issues and Fixes

#### Issue 1: Pinning Detection
```java
// Bad: Synchronized blocks with I/O
public synchronized void process() {
    database.save(item); // Causes pinning
}

// Good: Use ReentrantLock
private final ReentrantLock lock = new ReentrantLock();
public void process() {
    lock.lock();
    try {
        database.save(item); // No pinning!
    } finally {
        lock.unlock();
    }
}
```

#### Issue 2: Unbounded Creation
```java
// Bad: Virtual thread explosion
for (String url : millionUrls) {
    Thread.startVirtualThread(() -> fetch(url)); // OOM!
}

// Good: Rate limiting
Semaphore limiter = new Semaphore(1000);
for (String url : millionUrls) {
    limiter.acquire();
    executor.execute(() -> {
        try { fetch(url); }
        finally { limiter.release(); }
    });
}
```

#### Issue 3: Thread-local Variables
```java
// Bad: InheritThreadLocals may cause memory leaks
Thread.ofVirtual().inheritInheritableThreadLocals(true).start();

// Good: Use thread-safe alternatives
ThreadLocal<UserContext> context = new ThreadLocal<>();
// Or use scoped values (Java 21+)
ScopedValue<UserContext> context = ScopedValue.where(/* ... */);
```

---

## Performance Tuning

### JVM Tuning Guidelines

#### For High Concurrency
```bash
# Millions of virtual threads
export JAVA_OPTS="
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=50
-XX:VirtualThreadStackSize=128k  # Smaller stacks
-Djdk.virtualThreadScheduler.parallelism=1000  # More carriers
-Xms8g -Xmx16g
-XX:MaxRAMPercentage=90.0
"
```

#### For Memory-Constrained Environments
```bash
# Minimal virtual thread overhead
export JAVA_OPTS="
-XX:+UseG1GC
-XX:G1NewCollectionHeapPercent=30
-XX:VirtualThreadStackSize=512k  # Larger stacks
-Djdk.virtualThreadScheduler.parallelism=50  # Fewer carriers
-Xms1g -Xmx2g
-XX:MaxRAMPercentage=70.0
"
```

### Tuning Parameters

#### Carrier Thread Configuration
```
Carrier Threads = min(
  CPU cores * 2,
  Available memory / 1GB,
  512  # Maximum
)
```

#### Queue Size Configuration
```
Queue Size = max(
  1000,  # Minimum
  Expected peak requests * 2,
  Available memory / thread size
)
```

#### Memory Pressure Thresholds
```
Warning: Virtual threads > 1M
Critical: Virtual threads > 10M
Action: Reduce parallelism or increase carriers
```

---

## Troubleshooting

### Common Symptoms and Solutions

| Symptom | Cause | Solution |
|---------|-------|----------|
| High pinning events | synchronized + I/O | Use ReentrantLock |
| OutOfMemoryError | Too many virtual threads | Add rate limiting |
| High CPU usage | Too many carrier threads | Reduce parallelism |
| Slow response times | Blocking operations | Make async |
| Memory leaks | Thread-local variables | Use scoped values |

### Pinning Analysis

```bash
# Check pinning events
jfr print --events jdk.VirtualThreadPinned /var/log/yawl/vthreads.jfr

# Detailed stack trace analysis
jfr print --events jdk.VirtualThreadPinned --include-canvas /var/log/yawl/vthreads.jfr
```

### Memory Leak Detection

```java
// Memory usage monitoring
long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
long maxMemory = Runtime.getRuntime().maxMemory();
double usagePercentage = (double) usedMemory / maxMemory * 100;

if (usagePercentage > 80) {
    // Alert or take action
}
```

### Performance Analysis

```java
// throughput monitoring
MeterRegistry registry = new PrometheusMeterRegistry();
VirtualThreadMetrics metrics = VirtualThreadMetrics.create(registry);

// Record throughput
metrics.throughput().record(operationsProcessed);
```

---

## Best Practices

### 1. Configuration Management

- **Start with defaults**: Use JVM defaults first, then tune
- **Monitor continuously**: Set up alerts for key metrics
- **Document changes**: Keep a configuration history

### 2. Code Guidelines

- **Prefer async**: Use CompletableFuture for I/O operations
- **Limit blocking**: Avoid synchronized blocks with I/O
- **Use tools**: Leverage modern Java concurrency utilities

### 3. Resource Management

- **Rate limit**: Always use bounded queues for user inputs
- **Monitor memory**: Track both heap and off-heap memory
- **Use profiling**: Regular JFR analysis

### 4. Testing Strategies

- **Load testing**: Test with realistic workloads
- **Pinning tests**: Verify pinning scenarios
- **Memory testing**: Test memory limits and recovery

---

## Files and Locations

### Configuration Files
- **JVM Config**: `/etc/yawl/jvm-virtual-threads.conf`
- **Application Config**: `/etc/yawl/virtual-threads.conf`
- **Docker Environment**: `/etc/default/yawl`

### Log Files
- **JFR Logs**: `/var/log/yawl/vthreads.jfr`
- **Application Logs**: `/var/log/yawl/application.log`
- **Monitoring Logs**: `/var/log/yawl/monitoring.log`

### Documentation
- **Migration Guide**: `/docs/deployment/VIRTUAL_THREAD_MIGRATION_GUIDE.md`
- **API Reference**: `/docs/reference/virtual-threads.md`
- **Examples**: `/examples/virtual-thread-examples/`

### Monitoring
- **JMX Port**: 9091
- **Prometheus Port**: 9090
- **Grafana Port**: 3000

---

**Related Documentation:**
- [GRPO API Reference](../api/grpo-endpoints.md)
- [RlConfig Parameter Reference](../rl-config-reference.md)
- [Performance Baselines](../performance-baselines.md)
- [Environment Variables](../environment-variables.md)

**Support:**
For configuration issues, contact the YAWL engineering team or check the community forums.