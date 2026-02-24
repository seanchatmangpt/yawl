# YAWL Benchmark Module

## Overview

The `yawl-benchmark` module provides comprehensive performance testing tools for the YAWL workflow engine. It includes microbenchmarks for core engine operations, workflow pattern performance analysis, concurrency testing, and memory usage profiling using the Java Microbenchmark Harness (JMH).

## Purpose

This module enables performance optimization and capacity planning through:

- **Engine Benchmarks**: Core YAWL engine performance metrics
- **Workflow Pattern Testing**: Performance analysis of control flow patterns
- **Concurrency Analysis**: Multi-threaded performance evaluation
- **Memory Profiling**: Memory usage patterns and garbage collection impact
- **Data Generation**: Synthetic test data for benchmarking scenarios

## Key Classes and Interfaces

### Core Engine Benchmarks

| Class | Purpose |
|-------|---------|
| `YAWLEngineBenchmarks` | Core engine operations (task execution, throughput) |
| `WorkflowPatternBenchmarks` | Pattern-specific performance analysis |
| `ConcurrencyBenchmarks` | Multi-threaded performance testing |
| `MemoryBenchmarks` | Memory usage and GC impact analysis |
| `TestDataGenerator` | Synthetic test data generation |

### Benchmark Categories

#### 1. Engine Operations (`YAWLEngineBenchmarks`)
- **YNetRunner Task Execution**: Latency for individual task processing
- **YWorkItem Throughput**: Tasks per second processing rate
- **YStatelessEngine Scalability**: Stateless engine performance under load
- **Case Creation**: Performance of new case instantiation
- **Task Completion**: End-to-end task completion latency

#### 2. Workflow Patterns (`WorkflowPatternBenchmarks`)
- **Sequential Patterns**: Linear workflow performance
- **Parallel Split/Synchronization**: Fork/join pattern performance
- **Multi-Choice/Merge**: Decision-based workflow performance
- **Cancel Region**: Cancellation pattern overhead
- **N-out-of-M**: Quorum-based pattern performance

#### 3. Concurrency Testing (`ConcurrencyBenchmarks`)
- **Virtual vs Platform Threads**: Performance comparison
- **Thread Scalability**: Performance with increasing thread counts
- **Concurrent Case Management**: Multi-threaded case handling
- **Resource Contention**: Performance under contention
- **Deadlock Detection**: Impact of deadlock detection mechanisms

#### 4. Memory Analysis (`MemoryBenchmarks`)
- **Memory Footprint**: Heap usage patterns
- **Garbage Collection Impact**: GC pause times and throughput
- **Object Allocation**: Allocation rate analysis
- **Memory Leaks**: Detection of memory leaks during long runs
- **Peak Memory**: Memory usage spikes under load

## Dependencies

### Internal Dependencies
- `yawl-engine`: Core YAWL engine
- `yawl-elements`: Specification and element definitions
- `yawl-stateless`: Stateless engine implementation
- `yawl-utilities`: Utility classes and helper methods

### External Dependencies
- `org.openjdk.jmh`: Java Microbenchmark Harness
- `org.junit.jupiter`: JUnit 5 testing framework
- `org.apache.commons`: Commons utilities
- `com.google.gson`: JSON processing for results

## Benchmark Configuration

### JMH Configuration

All benchmarks use consistent configuration:
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(3)
```

### Thread Configuration

| Benchmark | Threads | Purpose |
|----------|---------|---------|
| `YAWLEngineBenchmarks` | 1 | Single-threaded baseline |
| `ConcurrencyBenchmarks` | 1-32 | Scaling test |
| `WorkflowPatternBenchmarks` | 1 | Pattern isolation |

## Usage Examples

### Running Individual Benchmarks

```bash
# Run YAWL engine benchmarks
mvn clean exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.YAWLEngineBenchmarks"

# Run workflow pattern benchmarks
mvn clean exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.WorkflowPatternBenchmarks"

# Run concurrency tests
mvn clean exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.ConcurrencyBenchmarks"

# Run memory benchmarks
mvn clean exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.MemoryBenchmarks"
```

### Generating Test Data

```java
// Create synthetic workflow specifications
List<YSpecification> specs = TestDataGenerator.generateSpecifications(
    10,  // number of specs
    5,   // average tasks per spec
    0.3  // branching factor
);

// Generate work items for testing
List<YWorkItem> workItems = TestDataGenerator.generateWorkItems(
    1000,  // number of work items
    specs   // source specifications
);
```

### Custom Benchmark Scenarios

```java
@Benchmark
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public void caseCreationThroughput(Blackhole bh) {
    // Create multiple cases concurrently
    for (int i = 0; i < 100; i++) {
        String caseId = engine.createCase("test-spec");
        bh.consume(caseId);
    }
}
```

## Benchmark Results

### Typical Performance Metrics

| Operation | Baseline | Target | Notes |
|-----------|----------|---------|-------|
| Task Execution | 5-10ms | <5ms | Single task processing |
| Case Creation | 50-100ms | <50ms | New case instantiation |
| Throughput | 100-200 tasks/sec | >500 tasks/sec | Concurrent processing |
| Memory Usage | 50-100MB/case | <50MB/case | Heap per active case |

### Performance Considerations

1. **JVM Tuning**: Use G1GC for better GC behavior
2. **Memory Settings**: Ensure adequate heap size for workloads
3. **Thread Pooling**: Optimize thread pool sizes
4. **Caching**: Leverage appropriate caching strategies
5. **I/O Operations**: Minimize disk I/O in benchmarks

## Benchmark Data

### Test Data Generation

The `TestDataGenerator` creates realistic test scenarios:

- **Specifications**: Linear, branching, and parallel workflows
- **Work Items**: Various task states and priorities
- **Data Sizes**: Small (1KB), medium (10KB), large (100KB) payloads
- **Concurrency Levels**: 1, 10, 50, 100 concurrent threads

### Workload Profiles

| Profile | Operations | Concurrency | Duration |
|---------|------------|------------|----------|
| Light | 1,000 tasks | 1 thread | 5 minutes |
| Medium | 10,000 tasks | 10 threads | 10 minutes |
| Heavy | 100,000 tasks | 50 threads | 30 minutes |
| Stress | 1,000,000 tasks | 100 threads | 60 minutes |

## Performance Tuning

### Engine Optimization

1. **Task Processing**:
   - Use `YStatelessEngine` for read-heavy workloads
   - Optimize task completion handlers
   - Minimize serialization overhead

2. **Memory Management**:
   - Monitor heap usage patterns
   - Optimize object allocation
   - Use object pooling for frequently created objects

3. **Concurrency**:
   - Consider virtual threads for I/O-bound operations
   - Use appropriate synchronization strategies
   - Profile for contention points

### Benchmark Analysis

1. **Latency Analysis**: Identify slow operations and optimize
2. **Throughput Analysis**: Find scaling limits and bottlenecks
3. **Memory Analysis**: Detect memory leaks and optimize GC behavior
4. **Concurrency Analysis**: Identify thread contention and lock contention

## Integration with CI/CD

### Automated Benchmarking

```yaml
# GitHub Action example
name: Performance Regression Testing
on: [push, pull_request]
jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Java
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Run Benchmarks
        run: |
          mvn clean install
          mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.benchmark.YAWLEngineBenchmarks"
      - name: Upload Results
        uses: actions/upload-artifact@v2
        with:
          name: benchmark-results
          path: results/
```

### Performance Gates

Define performance regression thresholds:
- **Latency**: No more than 20% increase
- **Throughput**: No more than 10% decrease
- **Memory**: No more than 15% increase
- **GC Time**: No more than 50ms pause

## Extension Points

### Custom Benchmarks

Extend benchmark classes for custom scenarios:

```java
public class CustomWorkflowBenchmarks extends WorkflowPatternBenchmarks {
    @Benchmark
    public void customPatternThroughput(Blackhole bh) {
        // Implement custom benchmark logic
    }
}
```

### Custom Data Generation

Implement custom data generators:

```java
public interface TestDataGenerator {
    List<YWorkItem> generateWorkItems(int count, YSpecification spec);
    List<YSpecification> generateSpecifications(int count);
}
```

## Reporting

### Results Format

Benchmark results are formatted as:
```json
{
  "benchmark": "taskExecution",
  "mode": "AverageTime",
  "unit": "milliseconds",
  "score": 5.23,
  "error": 0.12,
  "opsPerSecond": 191.2
}
```

### Performance Dashboard

Consider integrating with monitoring tools:
- **Grafana**: Real-time performance dashboards
- **Prometheus**: Metrics collection and alerting
- **InfluxDB**: Time series data storage
- **Kibana**: Log and metric visualization