# YAWL Virtual Thread Migration Performance Benchmarks

This directory contains comprehensive performance benchmarks for migrating YAWL workflow engine to Java 25 virtual threads.

## Overview

The benchmark suite measures performance improvements from migrating from platform threads to virtual threads, focusing on:

1. **Concurrency Performance**: Virtual vs platform thread throughput and latency
2. **Memory Efficiency**: Compact object headers and ScopedValue optimization
3. **Thread Contention**: Lock performance comparison and bottleneck analysis

## Benchmark Suites

### 1. ConcurrencyBenchmarkSuite

Measures core YAWL workflow performance with different thread strategies:

- **Platform Thread Task Execution**: Current YAWL approach performance
- **Virtual Thread Task Execution**: New virtual thread per task approach
- **Virtual Thread Pool**: Auto-scaling virtual thread pool performance
- **Structured Concurrency**: Structured task scope with virtual threads
- **Work Item Operations**: Checkout/checkin performance comparison
- **Context Switching**: Thread switching overhead measurement
- **Case Creation**: Case initialization performance
- **Task Transitions**: Task state transition performance

**Key Metrics:**
- Task execution throughput (ops/sec)
- Latency percentiles (p50, p95, p99)
- Context switching overhead
- Throughput under load

### 2. MemoryUsageProfiler

Analyzes memory usage patterns and optimizations:

- **Compact Object Headers**: Memory allocation and access performance
- **ScopedValue vs ThreadLocal**: Memory and performance comparison
- **Virtual Thread Memory Footprint**: Memory usage with many virtual threads
- **Thread Pool Memory Comparison**: Memory efficiency vs platform threads
- **Memory Analysis**: Heap usage and efficiency metrics

**Key Metrics:**
- Memory allocation throughput
- Heap usage percentage
- Memory savings from compact headers
- Context switching memory cost

### 3. ThreadContentionAnalyzer

Measures lock performance and identifies bottlenecks:

- **Synchronized Blocks**: Performance with virtual vs platform threads
- **ReentrantLock**: Fair vs unfair lock performance
- **StampedLock**: Optimistic read and write performance
- **Read-Write Locks**: Read/write pattern performance
- **Contention Patterns**: High contention scenario analysis
- **Fine vs Coarse Locking**: Lock granularity impact

**Key Metrics:**
- Lock acquisition latency
- Throughput under contention
- Contention ratio
- Performance degradation curves

## Running Benchmarks

### Prerequisites

- Java 25+ with virtual thread support
- JMH (Java Microbenchmark Harness) dependency
- At least 4GB heap memory

### Running Individual Suites

```bash
# Run all benchmarks
java -jar target/benchmarks.jar org.yawlfoundation.yawl.performance.BenchmarkConfig

# Run specific benchmark suite
java -jar target/benchmarks.jar org.yawlfoundation.yawl.performance.ConcurrencyBenchmarkSuite

# Run with custom JVM options
java -XX:+UseCompactObjectHeaders -Xms4g -Xmx8g -jar target/benchmarks.jar org.yawlfoundation.yawl.performance.BenchmarkConfig
```

### Running with Maven

```bash
# Compile benchmarks
mvn clean compile

# Run all benchmarks
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.BenchmarkConfig"

# Run specific suite
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.ConcurrencyBenchmarkSuite"
```

## Expected Results

### Concurrency Benchmarks

| Operation | Platform Threads | Virtual Threads | Improvement |
|-----------|----------------|----------------|-------------|
| Task Execution | 500 ops/sec | 5,000 ops/sec | 10x |
| Work Item Checkout | 200 ops/sec | 2,000 ops/sec | 10x |
| Case Creation | 100 ops/sec | 800 ops/sec | 8x |
| Task Transitions | 300 ops/sec | 2,500 ops/sec | 8x |

### Memory Benchmarks

| Metric | Without Compact Headers | With Compact Headers | Savings |
|--------|-------------------------|---------------------|---------|
| Object Allocation | 1.2M ops/sec | 1.5M ops/sec | 25% |
| Heap Usage | 2.1GB | 1.8GB | 14% |
| Context Switch Memory | 12KB/thread | 8KB/thread | 33% |

### Thread Contention Benchmarks

| Lock Type | Platform Threads | Virtual Threads | Performance |
|-----------|-----------------|-----------------|-------------|
| synchronized | 150 ops/sec | 1,200 ops/sec | 8x |
| ReentrantLock (unfair) | 200 ops/sec | 2,000 ops/sec | 10x |
| StampedLock (optimistic) | 300 ops/sec | 3,000 ops/sec | 10x |
| Read-Write Lock | 180 ops/sec | 1,500 ops/sec | 8x |

## JVM Configuration

For optimal performance, use these JVM flags:

```bash
# Recommended configuration
-XX:+UseCompactObjectHeaders    # Enable compact object headers
-XX:+UseZGC                      # Use Z garbage collector
-Xms2g -Xmx4g                    # Heap size
-XX:+UnlockExperimentalVMOptions # Enable experimental features
-XX:+UseContainerSupport         # Container awareness
-Djava.util.concurrent.ForkJoinPool.common.parallelism=16 # Parallelism
```

## Implementation Guide

### Phase 1: Core Migration (Week 1)

1. **Replace synchronized blocks with ReentrantLock**
   ```java
   // Before
   synchronized (lock) {
       // critical section
   }
   
   // After
   ReentrantLock lock = new ReentrantLock();
   lock.lock();
   try {
       // critical section
   } finally {
       lock.unlock();
   }
   ```

2. **Update thread pools**
   ```java
   // Before
   ExecutorService executor = Executors.newFixedThreadPool(100);
   
   // After
   ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
   ```

3. **Enable compact object headers**
   ```bash
   -XX:+UseCompactObjectHeaders
   ```

### Phase 2: Advanced Optimizations (Week 2)

1. **Replace ThreadLocal with ScopedValue**
   ```java
   // Before
   private static final ThreadLocal<WorkflowContext> context = new ThreadLocal<>();
   
   // After
   private static final ScopedValue<WorkflowContext> context = ScopedValue.newInstance();
   ```

2. **Implement structured concurrency**
   ```java
   try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
       scope.fork(() -> processTask());
       scope.join();
       scope.throwIfFailed();
   }
   ```

3. **Use StampedLock for read-heavy operations**
   ```java
   long stamp = stampedLock.tryOptimisticRead();
   int value = sharedValue;
   if (!stampedLock.validate(stamp)) {
       stamp = stampedLock.readLock();
       try {
           value = sharedValue;
       } finally {
           stampedLock.unlockRead(stamp);
       }
   }
   ```

### Phase 3: Validation (Week 3)

1. **Run benchmark suites to validate improvements**
2. **Monitor production metrics**
3. **Fine-tune configurations based on workload**

## Troubleshooting

### Common Issues

1. **Out of Memory Errors**
   - Increase heap size: `-Xms4g -Xmx8g`
   - Use ZGC: `-XX:+UseZGC`

2. **Poor Virtual Thread Performance**
   - Check for blocking operations in virtual threads
   - Ensure proper lock usage (no synchronized blocks)

3. **High Contention**
   - Use unfair ReentrantLock for better throughput
   - Consider fine-grained locking strategies

### Performance Monitoring

Monitor these metrics in production:

- **Thread count**: Virtual threads should scale to millions
- **CPU utilization**: Should be high (>80%) with I/O-bound work
- **Memory usage**: Monitor heap and GC pause times
- **Lock contention**: Monitor blocked thread time

## References

- [Java 25 Virtual Threads](https://docs.oracle.com/en/java/javase/25/core/virtual-threads.html)
- [Project Loom](https://openjdk.org/projects/loom/)
- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [YAWL Workflow Engine](http://www.yawlfoundation.org/)
