# YAWL v6.0.0-GA Enhanced Core Engine Performance Benchmarks

## Overview

This document describes the enhanced performance benchmarks for YAWL v6.0.0-GA, focusing on virtual thread scalability, Java 25 compact object headers, and memory efficiency optimizations.

## Benchmark Suite Components

### 1. YAWLEngineBenchmarks.java

**Location**: `yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/YAWLEngineBenchmarks.java`

**Enhanced Features**:
- Virtual thread scalability testing (10, 100, 1000, 10,000 concurrent cases)
- Java 25 compact object headers benchmark
- Memory usage per case tracking with different workflow patterns
- CPU efficiency measurements
- Priority queue performance with virtual threads

**Key Benchmarks**:
```java
@Benchmark
public void virtualThreadScaling10() throws Exception {
    runVirtualThreadScalingBenchmark(10);
}

@Benchmark
public void compactObjectHeadersBenchmark() {
    // Memory efficiency with compact headers
}

@Benchmark
public void memoryEfficiencyPerCase(String workflowType) throws Exception {
    // Memory tracking for simple, complex, high-priority workflows
}

@Benchmark
public void cpuEfficiencyMeasurement() throws Exception {
    // CPU time vs wall time efficiency
}
```

### 2. VirtualThreadScalingBenchmark.java

**Location**: `yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/VirtualThreadScalingBenchmark.java`

**Focus Areas**:
- Virtual thread throughput scaling (10 to 10,000 threads)
- Memory efficiency with virtual threads
- CPU utilization patterns
- Context switching overhead
- Resource contention under high concurrency

**Key Features**:
```java
// Virtual thread executors for scalability testing
private Executors.VirtualThreadBuilder virtualThreadBuilder;

// Structured concurrency for better error handling
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    for (int i = 0; i < threadCount; i++) {
        scope.fork(() -> {
            virtualThreadBuilder.start(task).join();
            return null;
        });
    }
}
```

### 3. CompactObjectHeadersBenchmark.java

**Location**: `yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/CompactObjectHeadersBenchmark.java`

**Performance Targets**:
- Memory usage reduction: 5-10% for workflow objects
- CPU throughput improvement: 2-5% for object-intensive operations
- Reduced GC pressure due to smaller object sizes

**Key Comparisons**:
- Traditional classes vs Java 25 records
- Cache efficiency with compact objects
- Serialization/deserialization performance
- Garbage collection impact assessment

```java
// Traditional workflow object class
public static class TraditionalWorkflowObject {
    // Standard implementation
}

// Compact workflow object record (Java 25)
public static record CompactWorkflowObject(
    String caseId,
    long timestamp,
    boolean virtualThread,
    WorkflowMetrics metrics,
    List<String> events,
    Map<String, Integer> attributes
) {
    // Compact headers optimized for memory efficiency
}
```

### 4. EngineMemoryEfficiencyBenchmark.java

**Location**: `yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/EngineMemoryEfficiencyBenchmark.java`

**Performance Targets**:
- Memory per case: < 100KB for simple workflows
- Memory per case: < 500KB for complex workflows
- GC pause time: < 100ms during normal operation
- Memory scalability: Linear growth with case count

**Key Features**:
- Memory usage per workflow case (simple vs complex)
- Memory efficiency across different workflow patterns
- Garbage collection behavior analysis
- Memory fragmentation analysis

## Realistic Workload Scenarios

### 1. Simple Workflows (100ms duration)
```java
// Sequential workflow with minimal branching
private void executeSimpleWorkflow(String caseId) throws Exception {
    YWorkItem workItem = findEnabledWorkItem(caseId);
    if (workItem != null) {
        engine.startWorkItem(workItem);
        Thread.sleep(10); // Simulate work
        engine.completeWorkItem(workItem, Collections.emptyMap());
    }
}
```

### 2. Complex Workflows (500ms duration)
```java
// Parallel workflow with multiple tasks
private void executeComplexWorkflow(String caseId) throws Exception {
    List<CompletableFuture<Void>> tasks = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
        final int taskNum = i;
        tasks.add(CompletableFuture.runAsync(() -> {
            YWorkItem workItem = findEnabledWorkItem(caseId + "-" + taskNum);
            if (workItem != null) {
                engine.startWorkItem(workItem);
                simulateCpuWork(200); // 200ms CPU work
                engine.completeWorkItem(workItem, Collections.emptyMap());
            }
        }, Executors.newVirtualThreadPerTaskExecutor()));
    }

    CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
}
```

### 3. High-Priority Workflows with Priority Queueing
```java
// Priority-based workflow execution
private void executeHighPriorityWorkflow(String caseId) throws Exception {
    YWorkItem workItem = findEnabledWorkItem(caseId);
    if (workItem != null) {
        engine.startWorkItem(workItem);
        // Simulate priority processing with faster completion
        simulateCpuWork(100); // Reduced processing time
        engine.completeWorkItem(workItem, Collections.emptyMap());
    }
}
```

## Java 25 Features Utilized

### 1. Virtual Threads
```java
// Virtual thread builders for per-case threading
private Thread.Builder virtualBuilder = Thread.ofVirtual()
    .namePrefix("yawl-benchmark-");

// Structured concurrency for automatic error handling
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // Fork multiple virtual tasks
}
```

### 2. Compact Object Headers
```java
// Records automatically use compact headers
public static record WorkflowMetrics(
    double cpuUtilization,
    long memoryUsage,
    int threadCount
) {
    // Compact headers optimized for memory usage
}
```

### 3. Pattern Matching
```java
// Exhaustive switch for workflow patterns
switch (workloadType) {
    case "simple" -> executeSimpleWorkflow(caseId);
    case "complex" -> executeComplexWorkflow(caseId);
    case "high-priority" -> executeHighPriorityWorkflow(caseId);
}
```

## Performance Targets and Expectations

### Virtual Thread Scalability
- **Efficiency Target**: 80% throughput efficiency at 1,000 concurrent threads
- **Memory Overhead**: < 1KB per virtual thread
- **Context Switching**: < 0.1ms per switch

### Compact Object Headers
- **Memory Reduction**: 5-10% for workflow objects
- **CPU Improvement**: 2-5% for object-intensive operations
- **GC Impact**: 15-20% reduction in GC pressure

### Memory Efficiency
- **Simple Workflows**: < 100KB per case
- **Complex Workflows**: < 500KB per case
- **GC Pauses**: < 100ms during normal operation

## Running the Benchmarks

### Prerequisites
- Java 25 or later
- Maven 3.8+
- JMH 1.37

### Compilation
```bash
mvn clean compile -DskipTests
```

### Running Individual Benchmarks
```bash
# Run virtual thread benchmarks
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.VirtualThreadScalingBenchmark"

# Run compact headers benchmarks
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.CompactObjectHeadersBenchmark"

# Run memory efficiency benchmarks
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.EngineMemoryEfficiencyBenchmark"

# Run engine benchmarks
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.YAWLEngineBenchmarks"
```

### Running All Benchmarks
```bash
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.BenchmarkRunner"
```

### Using JMH Directly
```bash
# Run with JMH
mvn clean install -Pbenchmark
```

## Output Formats

### JSON Output
```json
{
  "benchmark_name": "virtualThreadScaling1000",
  "mode": "Throughput",
  "threads": 1000,
  "time_unit": "ops/sec",
  "value": 245.67,
  "error": 5.23,
  "percentile_95": 289.45
}
```

### CSV Output
```csv
benchmark_name,mode,threads,time_unit,value,error,percentile_95
virtualThreadScaling10,Throughput,10,ops/sec,256.78,4.32,278.91
virtualThreadScaling100,Throughput,100,ops/sec,234.56,6.78,298.34
virtualThreadScaling1000,Throughput,1000,ops/sec,198.76,12.34,342.67
virtualThreadScaling10000,Throughput,10000,ops/sec,145.23,45.67,412.89
```

## Performance Analysis

### Key Metrics to Monitor
1. **Throughput**: Operations per second
2. **Latency**: Average time per operation
3. **Memory Usage**: Heap consumption per case
4. **CPU Utilization**: CPU time vs wall time ratio
5. **GC Behavior**: Pause times and frequency
6. **Thread Scalability**: Performance at different concurrency levels

### Performance Optimization Opportunities
1. **Virtual Thread Pool Sizing**: Optimize for workload patterns
2. **Memory Pool Tuning**: Adjust heap sizes based on workflow complexity
3. **GC Configuration**: Use G1GC or ZGC for large heaps
4. **Object Reuse**: Implement object pooling for frequently created objects
5. **Cache Optimization**: Leverage CPU caches better with compact objects

## Troubleshooting

### Common Issues
1. **OutOfMemoryError**: Increase heap size with `-Xmx`
2. **GC Pauses**: Consider using ZGC for large heaps
3. **Thread Starvation**: Monitor virtual thread scheduling
4. **Memory Leaks**: Check for proper cleanup of workflow objects

### Debug Commands
```bash
# Enable verbose GC
java -Xlog:gc*=info:file=gc.log

# Monitor memory usage
jcmd <pid> GC.heap_info

# Monitor thread usage
jcmd <pid> Thread.print
```

## Future Enhancements

1. **Microprofile Metrics**: Add JAX-RS endpoints for real-time metrics
2. **Distributed Tracing**: Integrate with OpenTelemetry
3. **Load Testing**: Add integration with Gatling or JMeter
4. **AI-driven Optimization**: Machine learning for performance tuning
5. **Advanced Memory Analysis**: Heap dumps and memory profiling

## Conclusion

The enhanced benchmark suite provides comprehensive performance testing for YAWL v6.0.0-GA with Java 25 features. These benchmarks help identify optimization opportunities and validate performance improvements before deployment to production environments.

For questions or issues, please refer to the YAWL documentation or contact the development team.