# Configure Virtual Threads

**Version**: v6.0.0-GA
**Scope**: Java 21+ virtual thread integration for YAWL workflow execution

---

## Virtual Thread Basics

Virtual threads (Project Loom) in Java 21+ enable massive concurrency with minimal resource overhead. YAWL leverages virtual threads for high-throughput workflow execution while maintaining structured concurrency guarantees.

### Key Benefits
- **High Concurrency**: Millions of virtual threads per JVM
- **Lightweight**: ~2KB stack size vs 1MB for platform threads
- **Structured Concurrency**: Parent-child thread relationship guarantees
- **Mappable to Platform Threads**: Underlying thread pool control

### Integration Points
- `YEngine`: Virtual thread task execution
- `YNetRunner`: Virtual thread pool management
- `YWorkItem`: Virtual thread task wrappers
- `YMonitor`: Virtual thread metrics

---

## JVM Flags for YAWL

Configure JVM flags for optimal virtual thread performance in YAWL.

### Production Configuration

```bash
# Production JVM startup script for YAWL v6.0.0-GA
#!/bin/bash

# Memory allocation
export JVM_MEMORY="-Xms4g -Xmx8g -XX:+UseZGC"

# Virtual thread configuration
export JVM_VIRTUAL_THREADS="--enable-preview -Djdk.virtualThreadScheduler.parallelism=16"

# Metaspace and GC
export JVM_GC="-XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+ZGenerational"

# G1GC with large pages
export JVM_GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:ParallelGCThreads=4 -XX:ConcGCThreads=2"

# JIT optimization
export JVM_JIT="-XX:+UseNUMA -XX:+UseAdaptiveSizePolicy -XX:ReservedCodeCacheSize=256m"

# Monitoring
export JVM_MONITORING="-XX:+FlightRecorder -XX:StartFlightRecording=filename=yawl.jfr,duration=60s,settings=profile"

# Classpath
export JVM_CP="yawl-engine.jar:yawl-elements.jar:yawl-rt.jar"

# JVM startup command
java $JVM_MEMORY $JVM_VIRTUAL_THREADS $JVM_GC $JVM_GC_OPTS $JVM_JIT $JVM_MONITORING \
    -jar yawl-engine.jar
```

### Development Configuration

```bash
# Development JVM configuration
export JVM_MEMORY="-Xms2g -Xmx4g -XX:+UseG1GC"
export JVM_VIRTUAL_THREADS="--enable-preview"
export JVM_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
export JVM_PROFILING="-XX:+UnlockDiagnosticVMOptions -XX:+PrintGCApplicationStoppedTime"

java $JVM_MEMORY $JVM_VIRTUAL_THREADS $JVM_DEBUG $JVM_PROFILING -jar yawl-engine-debug.jar
```

### Docker Configuration

```dockerfile
# Dockerfile for YAWL with virtual threads
FROM eclipse-temurin:21-jdk-alpine

# Configure JVM options
ENV JAVA_OPTS="-Xms4g -Xmx8g \
    --enable-preview \
    -Djdk.virtualThreadScheduler.parallelism=16 \
    -XX:+UseZGC \
    -XX:ZAllocationSpikeTolerance=5 \
    -XX:MaxGCPauseMillis=200"

# Add YAWL jars
COPY yawl-engine.jar /app/
COPY yawl-elements.jar /app/
COPY yawl-rt.jar /app/

# Work directory
WORKDIR /app

# Start YAWL with virtual threads
CMD ["sh", "-c", "java $JAVA_OPTS -jar yawl-engine.jar"]
```

---

## Thread Pool Sizing

Configure virtual thread pools for optimal YAWL performance.

### Thread Pool Configuration

```toml
# Virtual Thread Configuration for YAWL v6.0.0-GA
# Path: .claude/config/virtual-threads.toml

[thread_pool]
# Virtual thread pool configuration
virtual_thread_count = 10000  # Number of virtual threads
platform_thread_count = 16    # Platform threads for ForkJoinPool

# ForkJoinPool configuration for virtual threads
[fork_join_pool]
parallelism = 16
async_mode = true
thread_factory = "yawl.virtualthread.VirtualThreadFactory"
work_stealing = true
keep_alive_time = "60s"

# Virtual thread factory
[virtual_thread_factory]
thread_name_prefix = "yawl-vt-"
thread_priority = 5
inherit_inheritable_thread_locals = false
virtual_thread_builder = "VirtualThread.Builder"

# Uncaught exception handler
[exception_handler]
enabled = true
handler_class = "yawl.virtualthread.YawlUncaughtExceptionHandler"
logging_enabled = true
metrics_enabled = true

# Thread affinity
[thread_affinity]
enabled = false
core_threads_only = false
affinity_strategy = "round_robin"
```

### Dynamic Thread Pool Sizing

```toml
[scaling]
# Dynamic scaling configuration
enabled = true
min_threads = 1000
max_threads = 50000
initial_threads = 10000

# Scaling policies
[scaling.policies]
cpu_threshold = 0.8
memory_threshold = 0.9
throughput_target = "10000tps"
response_time_target = "100ms"

# Scaling triggers
[scaling.triggers]
on_load_increase = true
on_load_decrease = true
on_memory_pressure = true
on_cpu_saturate = true

# Scaling algorithm
[scaling.algorithm]
adjustment_interval = "5m"
cool_down_period = "10m"
backoff_ratio = 0.8
growth_factor = 1.2
```

### Resource-aware Sizing

```toml
[resource_aware]
# Resource-based sizing
cpu_cores = 16
total_memory_gb = 32
reserved_memory_gb = 4
available_memory = "total_memory - reserved_memory"

# Virtual thread calculation
virtual_thread_per_gb = 1000  # 1000 virtual threads per GB
virtual_threads = (available_memory * cpu_cores * virtual_thread_per_gb) / 2

# Contention settings
contention_threshold = 100  # Threads waiting
max_wait_time = "5ms"
spin_waits = 0  # Disable spinning for virtual threads
```

---

## Structured Concurrency Setup

Configure structured concurrency for reliable workflow execution.

### Structured Concurrency Configuration

```toml
[structured_concurrency]
# Structured concurrency settings
enabled = true
shutdown_timeout = "30s"
inherit_scope = true
capture_locals = true
task_timeout = "10s"

# Task tracking
[tracking]
enabled = true
max_tasks = 10000
task_sampling_rate = 0.1
metrics_retention = "1h"

# Error handling
[error_handling]
propagate_immediately = true
create_suppressed = true
keep_stack_traces = true
log_errors = true

# Example: Task group configuration
[example.workflow]
name = "OrderProcessing"
timeout = "5m"
tasks = [
    "validateOrder",
    "checkInventory",
    "processPayment",
    "shipOrder"
]
dependencies = [
    "validateOrder -> checkInventory",
    "validateOrder -> processPayment",
    "checkInventory -> shipOrder",
    "processPayment -> shipOrder"
]
```

### Structured Task Builder

```java
// Example: Structured concurrency in YAWL
import java.util.concurrent.StructuredTaskScope;

public class YawlStructuredWorkflow {
    public void executeWorkflow(WorkflowContext context) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            // Submit tasks
            var validateOrder = scope.fork(() -> validateOrder(context));
            var checkInventory = scope.fork(() -> checkInventory(context));
            var processPayment = scope.fork(() -> processPayment(context));

            // Wait for all tasks to complete
            scope.join();
            scope.throwIfFailed();

            // Process results
            Order validated = validateOrder.resultNow();
            Inventory inventory = checkInventory.resultNow();
            Payment payment = processPayment.resultNow();

            // Continue workflow
            shipOrder(validated, inventory, payment);
        } catch (Exception e) {
            throw new YawlWorkflowException("Workflow failed", e);
        }
    }
}
```

---

## Monitoring Virtual Threads

Configure monitoring for virtual thread performance and health.

### Monitoring Configuration

```toml
[monitoring]
# Virtual thread monitoring
enabled = true
interval = "5s"
retention = "1d"

# Metrics to collect
metrics = [
    "virtual_thread_count",
    "platform_thread_count",
    "thread_utilization",
    "contention_count",
    "blocked_time",
    "wait_time",
    "cpu_time"
]

# Alert thresholds
[alerts]
enabled = true

[alerts.thread_utilization]
warning = 0.8
critical = 0.95

[alerts.contention]
warning = 100
critical = 500

[alerts.blocked_time]
warning = "100ms"
critical = "500ms"
```

### Monitoring Commands

```bash
# Virtual thread monitoring commands
# JVM flight recording for virtual threads
jcmd <pid> JFR.start name=yawl-virtual-threads settings=profile

# Thread dump with virtual thread information
jcmd <pid> Thread.print -l

# Virtual thread specific metrics
jcmd <pid> VM.native_memory | grep Thread

# Monitoring with visualvm
visualvm --cp "yawl-engine.jar" --openjdk21

# Prometheus metrics export
java -jar yawl-monitoring.jar \
    --metrics-port=9090 \
    --jfr-file=/tmp/yawl.jfr \
    --virtual-thread-enabled=true
```

### Custom Monitoring Agents

```java
// Custom virtual thread monitoring agent
public class VirtualThreadMonitoringAgent {
    private final AtomicLong virtualThreadCount = new AtomicLong(0);
    private final AtomicLong contentionCount = new AtomicLong(0);
    private final LongAdder blockedTime = new LongAdder();

    // Instrument virtual thread creation
    @Before("execution(* java.lang.Thread$Builder::start)")
    public void beforeThreadStart(JoinPoint joinPoint) {
        if (isVirtualThread(joinPoint)) {
            virtualThreadCount.incrementAndGet();
        }
    }

    // Monitor contention
    @Around("execution(* java.util.concurrent.ForkJoinPool.*(..))")
    public Object monitorContention(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.nanoTime() - startTime;
            if (duration > 100_000) { // 100ms threshold
                contentionCount.incrementAndGet();
                blockedTime.add(duration);
            }
        }
    }
}
```

---

## Performance Optimization

Optimize virtual thread performance for YAWL workloads.

### Performance Tuning Configuration

```toml
[performance]
# Performance optimization settings
enable_contended_monitoring = false
enable_monitor_contention = false
enable_block_monitoring = false

# Virtual thread specific settings
virtual_thread_stack_size = "2m"
virtual_thread_pin_count = 8
virtual_thread_scheduler = "default"

# ForkJoinPool optimization
[forkjoin_optimization]
async_mode = true
local_work_queue_capacity = 4096
local_work_queue_max = 65536
steal_count = 1
do_not_fork_if_not_running = true

# Memory optimization
[memory_optimization]
stack_page_size = "4k"
stack_commit_size = "4m"
stack_reserved_pages = 1
```

### Performance Benchmarks

```bash
# Benchmark virtual thread configuration
#!/bin/bash

# Test different virtual thread configurations
configurations=(
    "1000 virtual threads"
    "5000 virtual threads"
    "10000 virtual threads"
    "20000 virtual threads"
)

for config in "${configurations[@]}"; do
    echo "Testing: $config"

    # Start YAWL with specific configuration
    java --enable-preview \
        -Xms4g -Xmx8g \
        -Djdk.virtualThreadScheduler.parallelism=16 \
        -jar yawl-benchmark.jar \
        --virtual-threads 1000 \
        --duration 60s \
        --output "results/${config// /_}.json"

    # Collect metrics
    jcmd <pid> GC.heap_info > "results/${config// /_}_gc.log"
done
```

### Performance Analysis Tools

```bash
# Performance analysis script
#!/bin/bash

# Analyze virtual thread performance
analyze_virtual_threads() {
    local jfr_file=$1

    # Extract virtual thread metrics
    jfr print --include "VirtualThread*" "$jfr_file" > "vt_metrics.log"

    # Analyze contention
    jfr print --include "Contention*" "$jfr_file" > "contention.log"

    # Generate performance report
    python3 analyze_performance.py \
        --jfr-file "$jfr_file" \
        --output "performance_report.html" \
        --virtual-thread-analysis
}

# Example usage
analyze_virtual_threads "/tmp/yawl_vt.jfr"
```

---

## Best Practices

### Virtual Thread Usage Guidelines

```java
// Best practices for virtual threads in YAWL
public class YawlVirtualThreadBestPractices {

    // ✅ Good: Use virtual threads for I/O bound operations
    public void handleHttpRequest(HttpServletRequest request) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var userTask = scope.fork(() -> fetchUser(request));
            var orderTask = scope.fork(() -> fetchOrders(request));

            scope.join();
            scope.throwIfFailed();

            return buildResponse(userTask.resultNow(), orderTask.resultNow());
        }
    }

    // ❌ Bad: Don't block in virtual threads
    public void badExample() {
        VirtualThread.start(() -> {
            // This blocks the platform thread
            Thread.sleep(1000); // ❌ Avoid blocking in virtual threads
        });
    }

    // ✅ Good: Use virtual threads for concurrent operations
    public List<Order> processOrdersConcurrently(List<Order> orders) {
        return orders.parallelStream()  // ✅ Good for virtual threads
            .map(this::processOrder)
            .toList();
    }
}
```

### Configuration Validation

```bash
# Validate virtual thread configuration
#!/bin/bash

validate_virtual_thread_config() {
    local config_file=$1

    # Check JVM version
    if [[ $(java -version 2>&1) != *"21."* ]]; then
        echo "Error: Java 21+ required for virtual threads"
        return 1
    fi

    # Check preview features enabled
    if [[ $(java --version) != *"--enable-preview"* ]]; then
        echo "Warning: Preview features not enabled"
    fi

    # Validate configuration file
    if [[ ! -f "$config_file" ]]; then
        echo "Error: Configuration file not found: $config_file"
        return 1
    fi

    # Check for required configuration
    if ! grep -q "virtual_thread_count" "$config_file"; then
        echo "Error: virtual_thread_count not configured"
        return 1
    fi

    echo "Configuration validation passed"
}

# Example usage
validate_virtual_thread_config ".claude/config/virtual-threads.toml"
```

---

## Troubleshooting

### Common Issues and Solutions

#### Problem: Virtual threads not starting
**Solution**:
```bash
# Check JVM version
java -version

# Enable preview features
export JVM_OPTS="--enable-preview"

# Check configuration
cat .claude/config/virtual-threads.toml | grep virtual_thread_count
```

#### Problem: High memory usage
**Solution**:
```toml
# Reduce virtual thread count
[thread_pool]
virtual_thread_count = 5000
platform_thread_count = 8

# Enable memory monitoring
[monitoring]
enabled = true
track_memory = true
```

#### Problem: Poor performance
**Solution**:
```toml
# Increase platform threads
[fork_join_pool]
parallelism = 32

# Enable async mode
[fork_join_pool]
async_mode = true

# Adjust scaling
[scaling]
min_threads = 2000
max_threads = 20000
```

---

## Related Documentation

- [Java 21 Virtual Threads Guide](https://openjdk.org/projects/loom/)
- [YAWL Concurrency Model](../concurrency-model.md)
- [Performance Benchmarking](../guides/performance-benchmarks.md)
- [Monitoring and Observability](../guides/monitoring.md)
- [JVM Tuning Guide](../jvm-tuning.md)
- [Configuration Reference](../reference/configuration.md)