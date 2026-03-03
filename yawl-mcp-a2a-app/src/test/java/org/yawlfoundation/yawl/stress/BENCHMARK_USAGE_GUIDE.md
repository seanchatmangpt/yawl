# YAWL MCP A2A Message Throughput Benchmark - Usage Guide

## Overview

The MessageThroughputBenchmark package provides comprehensive performance testing for YAWL MCP A2A messaging patterns. This guide covers how to use, configure, and extend the benchmark.

## Quick Start

### 1. Running the Basic Benchmark

```bash
# Compile and run the benchmark
mvn compile exec:java -Dexec.mainClass="org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner" -q

# Or use the provided script
./run-message-throughput-benchmark.sh
```

### 2. Expected Output

```
=== YAWL MCP A2A Message Throughput Benchmark Runner ===

Running simplified benchmark to verify implementation...

1. Testing small messages with virtual threads...
  Messages processed: 1000
  Duration: 3.456 seconds
  Throughput: 289.23 messages/sec
  Average latency: 45.67 μs

2. Testing small messages with platform threads...
  Messages processed: 1000
  Duration: 5.678 seconds
  Throughput: 176.20 messages/sec
  Average latency: 67.89 μs

=== Benchmark runner completed successfully ===
```

## Advanced Usage

### 1. Custom Benchmark Configuration

```java
// Create custom benchmark with specific parameters
MessageThroughputBenchmark benchmark = new MessageThroughputBenchmark();

// Override test parameters
benchmark.setWarmupIterations(5000);
benchmark.setMeasurementIterations(20000);
benchmark.setTimeout(120); // 120 seconds

// Run specific patterns
benchmark.test1to1Pattern();
benchmark.test1toNPattern();
```

### 2. Integrating with YAWL Components

```java
// Create YAWL work item benchmark
YAWLWorkItemBenchmark workItemBenchmark = new YAWLWorkItemBenchmark(4);

// Run different workload patterns
workItemBenchmark.testSingleThreadWorkload();
workItemBenchmark.testMultiThreadWorkload();
workItemBenchmark.testBatchWorkload();

// Clean up
workItemBenchmark.shutdown();
```

### 3. Custom Metrics Collection

```java
// Extend the benchmark with custom metrics
CustomThroughputBenchmark customBenchmark = new CustomThroughputBenchmark();

// Run with custom metrics
customBenchmark.withCustomMetrics(() -> {
    customBenchmark.runNtoMPattern();
});

// Access custom metrics
long totalBytes = customBenchmark.getTotalBytesProcessed();
int peakConcurrent = customBenchmark.getPeakConcurrentMessages();
```

## Test Configuration

### Threading Models

| Model | Description | Best For |
|-------|-------------|----------|
| Virtual Threads | Lightweight threads managed by JVM | I/O-bound workloads |
| Platform Threads | Traditional OS threads | CPU-bound workloads |

### Communication Patterns

| Pattern | Description | Use Case |
|---------|-------------|----------|
| 1:1 | Single producer, single consumer | Simple message processing |
| 1:N | Single producer, multiple consumers | Broadcast/multicast scenarios |
| N:1 | Multiple producers, single consumer | Message aggregation |
| N:M | Multiple producers, multiple consumers | High-throughput processing |

### Message Sizes

| Size | Bytes | Description |
|------|-------|-------------|
| Small | 128 | Simple messages, notifications |
| Medium | 4096 | Workflow definitions, data payloads |
| Large | 65536 | Batch operations, large data transfers |

## Performance Analysis

### 1. Running the Analysis Script

```bash
# Save benchmark output to a file
./run-message-throughput-benchmark.sh > benchmark_output.txt

# Analyze results
python analyze-benchmark-results.py benchmark_output.txt

# This generates:
# - benchmark_report.md (text report)
# - performance_comparison.png (visualization)
```

### 2. Understanding the Report

The generated report includes:
- **Summary Statistics**: Overall performance metrics
- **Pattern Analysis**: Performance by communication pattern
- **Threading Comparison**: Virtual vs platform thread performance
- **Recommendations**: Optimization suggestions

### 3. Performance Visualization

The analysis script creates four charts:
1. **Throughput by Pattern and Message Size**: Bar chart showing messages/second
2. **Latency P95**: Line chart showing 95th percentile latency
3. **Virtual vs Platform Comparison**: Side-by-side threading performance
4. **Performance Improvement Heatmap**: Color-coded improvement percentages

## Customization

### 1. Adding New Message Patterns

```java
// Add new pattern to the benchmark
public void testCustomPattern() {
    // Implement custom message exchange pattern
    // Follow existing pattern of producer/consumer threads
}
```

### 2. Adding New Metrics

```java
// Add new metric tracking
private final AtomicLong customMetric = new AtomicLong(0);

// Update during benchmark
private void recordCustomMetric(long value) {
    customMetric.addAndGet(value);
}

// Access in results
long metricValue = customMetric.get();
```

### 3. Custom Message Generation

```java
// Override message generation
private String generateCustomMessage(String size, int id) {
    // Create custom message formats
    return "{\"custom\": \"message\", \"id\": " + id + "}";
}
```

## Integration with CI/CD

### 1. Maven Plugin Configuration

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <phase>test</phase>
            <goals>
                <goal>java</goal>
            </goals>
            <configuration>
                <mainClass>org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner</mainClass>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. GitHub Actions Example

```yaml
name: Performance Benchmark
on: [push, pull_request]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 17
        uses: actions/setup-java@v2
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run benchmark
        run: |
          cd yawl-mcp-a2a-app
          ./run-message-throughput-benchmark.sh > benchmark.log
      - name: Analyze results
        run: |
          python analyze-benchmark-results.py benchmark.log
      - name: Upload results
        uses: actions/upload-artifact@v2
        with:
          name: benchmark-results
          path: |
            yawl-mcp-a2a-app/benchmark_report.md
            yawl-mcp-a2a-app/performance_comparison.png
```

## Troubleshooting

### Common Issues

1. **Compilation Errors**
   - Ensure Java 17+ is installed
   - Check Maven dependencies
   - Verify project structure

2. **Memory Issues**
   - Increase JVM heap size: `-Xms2g -Xmx4g`
   - Reduce message batch sizes
   - Monitor GC logs

3. **Timeout Issues**
   - Increase timeout value in benchmark
   - Check for deadlocks
   - Monitor thread activity

4. **Performance Inconsistencies**
   - Run warmup iterations
   - Avoid JVM background processes
   - Use consistent test environment

### Optimization Tips

1. **Virtual Threads**: Best for I/O-bound workloads
2. **Message Batching**: Improve throughput with larger batches
3. **Connection Pooling**: Reduce connection overhead
4. **Asynchronous Processing**: Use CompletableFuture for non-blocking operations

## Best Practices

1. **Run Tests Multiple Times**: Average results for accuracy
2. **Monitor System Resources**: CPU, memory, network
3. **Use Realistic Payloads**: Simulate actual message sizes
4. **Test Different Patterns**: Find optimal configuration
5. **Document Results**: Track performance over time

## Support

For issues and questions:
1. Check the existing documentation
2. Review benchmark examples
3. Examine source code for implementation details
4. Run tests in isolation to isolate problems

---

**Note**: This benchmark is part of the YAWL project and follows the YAWL coding standards. Ensure all changes align with the project's contribution guidelines.