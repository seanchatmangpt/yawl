# YAWL Actor Model Validation Tools

This package contains comprehensive validation tools for the YAWL Actor Model implementation, designed to verify performance targets and identify scaling limits.

## Overview

The validation tools are organized into several components:

1. **EnhancedAgentDensityStressTest** - Comprehensive stress testing at scale
2. **ScalabilityBenchmark** - Throughput measurement benchmarks  
3. **MemoryProfiler** - Detailed heap analysis and optimization
4. **LatencyMetrics** - Precise percentile latency tracking
5. **GCMonitor** - GC pause time and behavior monitoring
6. **LoadGenerationUtilities** - Various load generation patterns
7. **ValidationSuite** - Integration suite for end-to-end testing

## Performance Targets

### Memory Efficiency
- **Target**: ≤150 bytes per agent
- **Components**: Agent (24 bytes) + Queue (40 bytes) + Overhead
- **Validation**: MemoryProfiler.profileAgentSystem()

### Scheduling Latency  
- **p50**: < 100μs
- **p90**: < 500μs  
- **p95**: < 1ms
- **p99**: < 2ms
- **p99.9**: < 5ms
- **Validation**: LatencyMetrics.recordLatency()

### Throughput
- **Spawn**: >100K agents/second
- **Message**: >1M messages/second across 100K agents
- **Validation**: ScalabilityBenchmark benchmarks

### GC Behavior
- **Pause time**: <5% of execution time
- **Frequency**: <10 full GCs/hour
- **Validation**: GCMonitor.monitorGC()

## Usage Examples

### Enhanced Density Testing

```java
// Run comprehensive density validation
EnhancedAgentDensityStressTest test = new EnhancedAgentDensityStressTest();
test.comprehensiveDensityValidation();
```

### Performance Benchmarking

```java
// Test spawn throughput
ScalabilityBenchmark benchmark = new ScalabilityBenchmark();
benchmark.spawnThroughputBenchmark();

// Test message throughput  
benchmark.messageThroughputBenchmark();
```

### Memory Analysis

```java
// Profile memory usage
MemoryProfiler profiler = new MemoryProfiler();
MemoryProfiler.MemorySnapshot snapshot = profiler.profileAgentSystem(100000);

// Detect memory leaks
MemoryProfiler.LeakAnalysis leaks = profiler.detectMemoryLeaks(100000, 60000);
```

### Latency Tracking

```java
// Track scheduling latency
LatencyMetrics metrics = new LatencyMetrics();
metrics.recordLatency(System.nanoTime(), "message_delivery");

// Calculate percentiles
LatencyMetrics.PercentileResults results = metrics.calculatePercentiles();
System.out.printf("p95 latency: %.3f ms%n", results.p95Millis);
```

### GC Monitoring

```java
// Monitor GC behavior during test
GCMonitor monitor = new GCMonitor();
GCMonitor.GCResults results = monitor.monitorGC(60000, 100000);

// Predict future behavior
GCMonitor.GCPrediction prediction = monitor.predictGCBehavior(1000000, 24);
```

### Load Generation

```java
Runtime runtime = new Runtime();
LoadGenerationUtilities loadGen = new LoadGenerationUtilities(runtime);

// Uniform load
LoadGenerationUtilities.LoadResult result = loadGen.generateUniformLoad(
    10000, 100, msg -> {
        // Process message
    }
);

// Hotspot load
loadGen.generateHotspotLoad(10000, 1000, 100, msg -> {
    // Process message  
});
```

## Test Execution

### Running Individual Tests

```bash
# Run density stress test
mvn test -Dtest=EnhancedAgentDensityStressTest

# Run benchmarks
mvn test -Dtest=ScalabilityBenchmark

# Run validation suite
mvn test -Dtest=ValidationSuite
```

### Running with Tag Filters

```bash
# Run all validation tests
mvn test -Dtest="**/validation/*Test" -Dgroups="validation"

# Run stress tests only
mvn test -Dgroups="stress,validation"
```

### Running with JVM Tuning

```bash
# Optimal JVM settings for validation
java -Xmx4g -XX:+UseZGC -XX:+UseCompactObjectHeaders \
  -Djdk.virtualThreadScheduler.parallelism=4 \
  -jar target/test-classes org.yawlfoundation.yawl.engine.actor.validation.ValidationSuite
```

## Integration

The validation tools are designed to work together seamlessly:

1. **ValidationSuite** orchestrates all tools in sequence
2. **MemoryProfiler** provides baseline measurements for all tests
3. **LatencyMetrics** collects latency data during all operations
4. **GCMonitor** tracks GC impact during benchmarks
5. **LoadGenerationUtilities** provides standardized load patterns

## Customization

### Adding Custom Load Patterns

```java
public class CustomLoadPatterns {
    public void generateCustomLoad(Runtime runtime, int agentCount) {
        LoadGenerationUtilities loadGen = new LoadGenerationUtilities(runtime);
        // Implement custom load generation logic
    }
}
```

### Extending Latency Metrics

```java
public class CustomLatencyMetrics extends LatencyMetrics {
    public void recordCustomLatency(long startTime, String operation, Map<String, Object> context) {
        // Add context-specific latency tracking
        super.recordLatency(startTime, operation);
    }
}
```

### Custom GC Analysis

```java
public class CustomGCMonitor extends GCMonitor {
    public void analyzeCustomPatterns() {
        // Add custom GC analysis logic
    }
}
```

## Output Interpretation

### Density Test Results

```
Scale    Heap/Agent   GC Pauses   CPU Eff    p90(ms)    p95(ms)    p99(ms)    p99.9(ms)   Status
100K        132            2     98.5%      0.2       0.4       0.8        1.2        OK
500K        135            3     97.2%      0.3       0.6       1.1        1.8        OK
1M          142            5     95.8%      0.5       1.0       2.1        3.5        OK
2M          155          FAILED (155 bytes/agent > target 150)
```

### Benchmark Results

```
Spawn Throughput Benchmark:
Agents: 1,000 | Rate: 150,000/s | Time: 0.007s | Heap/Agent: 132 bytes

Message Throughput Benchmark:  
Agents: 100,000 | Rate: 2,500,000 msg/s | Latency p95: 0.75ms | GC: 10 pauses
```

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**: Reduce agent count or increase heap size
2. **Timeout**: Increase timeout values or reduce load intensity
3. **High GC Pressure**: Check for memory leaks or optimize data structures

### Performance Optimization Tips

1. Use `-XX:+UseZGC` for low-pause garbage collection
2. Enable `-XX:+UseCompactObjectHeaders` for memory efficiency
3. Limit virtual thread parallelism for CPU-bound work
4. Monitor GC behavior with `-XX:+PrintGCDetails`

## Contributing

When adding new validation tools:

1. Follow the existing pattern of comprehensive metrics collection
2. Include appropriate tags (@Tag("validation"), @Tag("stress"))
3. Document performance targets clearly
4. Add integration points to ValidationSuite when appropriate
5. Ensure thread safety for concurrent test scenarios

## References

- YAWL Actor Model Design Documentation
- Java Virtual Machine Tuning Guide
- JMH Benchmarking Best Practices
- GC Analysis and Optimization Techniques
