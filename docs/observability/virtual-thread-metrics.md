# VirtualThreadRuntime Metrics

## Overview

VirtualThreadRuntime now includes comprehensive metrics for tracking spawn performance and system health. Metrics are collected using both JMX for production monitoring and Micrometer for integration with observability platforms.

## Spawn Latency Metrics

### Core Metrics

| Metric | Type | Description | Unit |
|--------|------|-------------|------|
| `virtual.thread.spawn.latency` | Timer | Time to spawn a new virtual thread actor | Nanoseconds |
| `virtual.thread.spawn.count` | Counter | Total number of actors spawned | Count |
| `virtual.thread.stopped` | Counter | Total number of actors stopped | Count |
| `virtual.thread.messages` | Counter | Total number of messages sent | Count |

### Derived Metrics

| Metric | Type | Description | Unit |
|--------|------|-------------|------|
| `virtual.thread.spawn.latency.avg.nanos` | Gauge | Average spawn latency | Nanoseconds |
| `virtual.thread.spawn.latency.total.nanos` | Gauge | Total accumulated spawn time | Nanoseconds |
| `virtual.thread.spawn.latency.measurements` | Gauge | Number of spawn latency measurements | Count |

## JMX Integration

### MBean Object Name
```
org.yawlfoundation.yawl.engine:type=VirtualThreadRuntime
```

### MBean Interface

```java
@MXBean
public interface VirtualThreadRuntimeMBean {
    double getAverageSpawnLatencyMillis();
    long getTotalSpawnTimeNanos();
    long getSpawnCount();
    long getSpawnLatencyMeasurementCount();
}
```

### JMX Query Examples

```bash
# Get average spawn latency
jcmd <pid> VM.native_memory | grep VirtualThreadRuntime

# Using VisualVM or JConsole
# Navigate to MBeans → org.yawlfoundation.yawl.engine → VirtualThreadRuntime

# Using command line
java -jar jconsole localhost:9999
```

## Micrometer Integration

### Constructor with Registry
```java
// Create runtime with metrics registry
MeterRegistry registry = new PrometheusMeterRegistry();
VirtualThreadRuntime runtime = new VirtualThreadRuntime(registry);
```

### Manual Registration
```java
// Register metrics with existing registry
runtime.registerMetrics(meterRegistry);
```

### Prometheus Export
```java
// Add Prometheus endpoint
@RequestMapping("/actuator/prometheus")
public String prometheus() {
    return ((PrometheusMeterRegistry) meterRegistry).scrape();
}
```

## Usage Examples

### Basic Monitoring
```java
VirtualThreadRuntime runtime = new VirtualThreadRuntime();

// Spawn actors
ActorRef actor1 = runtime.spawn(self -> { /* behavior */ });
ActorRef actor2 = runtime.spawn(self -> { /* behavior */ });

// Check metrics
Duration avgLatency = runtime.getAverageSpawnLatency();
System.out.println("Average spawn latency: " + avgLatency.toMillis() + "ms");
System.out.println("Total spawns: " + runtime.getSpawnCount());
```

### Advanced Monitoring
```java
// With Micrometer registry
MeterRegistry registry = new SimpleMeterRegistry();
VirtualThreadRuntime runtime = new VirtualThreadRuntime(registry);

// Spawn actors with timing
runtime.spawn(self -> {
    long start = System.nanoTime();
    // Actor behavior
    long duration = System.nanoTime() - start;
    System.out.println("Actor execution: " + duration + "ns");
});

// Export metrics
runtime.registerMetrics(registry);
```

## Metric Interpretation

### Spawn Latency

- **< 1ms**: Excellent - Virtual thread creation is very fast
- **1-5ms**: Good - Normal virtual thread creation overhead
- **5-10ms**: Fair - May indicate system contention
- **> 10ms**: Poor - Investigate system resources

### Spawn Count

- Monitor for rapid actor creation which might indicate:
  - Message storm scenarios
  - Uncontrolled actor spawning
  - Potential memory leaks

### Message Count

- Track message processing rates
- Monitor for queue backing up
- Identify hotspots in actor messaging

## Performance Considerations

### Metric Collection Overhead

- Timer collection adds minimal overhead (< 1μs per spawn)
- AtomicLong operations are lock-free
- Metrics are thread-safe

### Memory Usage

- Metrics use fixed amount of memory regardless of actor count
- No memory leaks in metric collection
- Registry objects should be closed when no longer needed

## Best Practices

1. **Monitor in Production**: Set up alerts for:
   - Average spawn latency > 5ms
   - Zero spawn latency (indicates collection issues)
   - Rapid increase in spawn count

2. **Sampling**: For high-throughput systems, consider sampling metrics:
   ```java
   if (random.nextDouble() < 0.1) { // Sample 10%
       metrics.record(duration);
   }
   ```

3. **Integration**: Connect metrics to:
   - Grafana dashboards
   - Prometheus alerts
   - APM tools (New Relic, Datadog)
   - Custom monitoring systems

## Troubleshooting

### Common Issues

1. **Zero Metrics**:
   - Verify actors are being spawned
   - Check MeterRegistry is properly configured
   - Ensure JMX is enabled in JVM

2. **High Latency**:
   - Check system CPU and memory usage
   - Monitor carrier thread pool
   - Investigate virtual thread pinning

3. **Missing Metrics**:
   - Verify MeterRegistry registration
   - Check metric naming conventions
   - Look for metric filtering

### Debug Commands

```bash
# Check JMX connectivity
jcmd <pid> VM.version

# Monitor JVM thread pool
jcmd <pid> Thread.print

# Check virtual thread usage
jcmd <pid> VM.native_memory | grep Thread
```

## API Reference

### VirtualThreadRuntime Class

```java
public class VirtualThreadRuntime implements ActorRuntime {
    // Metrics
    public Duration getAverageSpawnLatency();
    public long getTotalSpawnTimeNanos();
    public long getSpawnLatencyMeasurementCount();
    public void registerMetrics(MeterRegistry registry);

    // JMX
    public interface VirtualThreadRuntimeMBean { ... }
}
```

This comprehensive metrics system enables production-grade monitoring of virtual thread performance with minimal overhead.