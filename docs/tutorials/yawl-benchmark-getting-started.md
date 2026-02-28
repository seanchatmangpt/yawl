# Getting Started with YAWL Benchmark Suite

Learn how to measure and optimize YAWL engine performance using the comprehensive benchmark suite.

## What You'll Learn

By the end of this tutorial, you'll understand:
- How to run YAWL benchmarks for core components
- How to interpret benchmark results and identify bottlenecks
- How to compare performance across different configurations
- How to detect performance regressions
- How to optimize your YAWL deployments

## Prerequisites

- Java 25 or higher
- Maven 3.9 or later
- At least 4GB of available memory
- 30-45 minutes

## Step 1: Understanding the Benchmark Suite

YAWL Benchmark Suite uses JMH (Java Microbenchmark Harness) from OpenJDK to measure performance with statistical rigor:

```
Benchmark Classes:
├── YAWLEngineBenchmarks
│   ├── Engine startup performance
│   ├── Case creation throughput
│   ├── Work item operations
│   └── Task execution latency
├── WorkflowPatternBenchmarks
│   ├── Sequential execution
│   ├── Parallel splits and joins
│   ├── Multi-choice routing
│   └── Cancel region behavior
├── ConcurrencyBenchmarks
│   ├── Virtual vs platform threads
│   ├── Thread scaling efficiency
│   ├── Resource contention
│   └── Context switching overhead
└── MemoryBenchmarks
    ├── Heap usage during execution
    ├── Garbage collection pressure
    ├── Memory scaling with cases
    ├── Leak detection
    └── Memory recovery patterns
```

## Step 2: Build the Benchmark Module

First, ensure the benchmark module is compiled:

```bash
# Build just the benchmark module
mvn -pl yawl-benchmark clean package

# Or build all dependencies with benchmark
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-benchmark \
    clean package

# Skip tests if you want faster builds
mvn -pl yawl-benchmark clean package -DskipTests
```

## Step 3: Run Your First Benchmark

Run a simple engine performance benchmark:

```bash
# Navigate to the benchmark directory
cd yawl-benchmark

# Run a specific benchmark class
java -jar target/benchmarks.jar YAWLEngineBenchmarks

# Run a specific benchmark method
java -jar target/benchmarks.jar \
    ".*YAWLEngineBenchmarks.engineStartupPerformance"

# Standard options
java -jar target/benchmarks.jar \
    -n 30          # number of measurement iterations
    -w 10          # number of warmup iterations
    -f 3           # number of forks (statistical repeats)
    -t 4           # threads per benchmark
    YAWLEngineBenchmarks
```

## Step 4: Run Concurrency Benchmarks

Measure how YAWL scales with virtual threads:

```bash
# Compare virtual vs platform thread performance
java -jar target/benchmarks.jar \
    -n 50 -w 20 -f 5 \
    ".*ConcurrencyBenchmarks.virtualVsPlatformThreadPerformance"

# Measure thread scaling efficiency
java -jar target/benchmarks.jar \
    -n 50 -w 20 -f 5 \
    ".*ConcurrencyBenchmarks.threadScalingPerformance"

# Test resource contention under load
java -jar target/benchmarks.jar \
    -n 100 -w 20 -f 5 \
    ".*ConcurrencyBenchmarks.resourceContentionUnderLoad"
```

## Step 5: Interpret Benchmark Results

Understanding benchmark output:

```bash
# Typical JMH output:

Benchmark                                                Mode  Cnt    Score   Error  Units
YAWLEngineBenchmarks.engineStartupPerformance          thrpt    5  1234.567 ±234.5 ops/ms
YAWLEngineBenchmarks.caseCreationThroughput            thrpt    5   456.789 ±45.6  ops/ms
YAWLEngineBenchmarks.workItemCheckoutLatency            avgt    5    12.345 ±2.3  ms/op
YAWLEngineBenchmarks.taskTransitionPerformance         avgt    5     3.456 ±0.5  ms/op
```

### Understanding Metrics

| Metric | Meaning | Good Value |
|--------|---------|-----------|
| `thrpt` | Throughput (ops/sec) | Higher is better |
| `avgt` | Average time per operation | Lower is better |
| `Score` | Primary performance metric | Context-dependent |
| `Error` | Statistical uncertainty (±) | Narrower is better |
| `Units` | Measurement unit | ops/ms, ms/op, etc. |

### Example Analysis

```
ConcurrencyBenchmarks.virtualVsPlatformThreadPerformance
  platform threads:  234.5 ops/ms ± 10.2  → 234 cases/ms
  virtual threads:   1256.3 ops/ms ± 15.8 → 1256 cases/ms
  improvement:       5.4× better with virtual threads

Interpretation:
✓ Virtual threads provide 5× throughput improvement
✓ Error bars are tight (good statistical confidence)
✓ This validates virtual thread adoption for YAWL
```

## Step 6: Run Memory Benchmarks

Monitor memory usage patterns:

```bash
# Measure heap usage during execution
java -Xms2g -Xmx4g -jar target/benchmarks.jar \
    -n 50 -w 20 -f 3 \
    ".*MemoryBenchmarks.heapUsageDuringWorkflowExecution"

# Detect memory leaks
java -Xms2g -Xmx8g -jar target/benchmarks.jar \
    -n 100 -w 20 -f 5 \
    ".*MemoryBenchmarks.memoryLeakDetection"

# Analyze GC pressure
java -XX:+PrintGCDetails -XX:+PrintGCTimeStamps \
    -jar target/benchmarks.jar \
    ".*MemoryBenchmarks.gcPressureUnderLoad"
```

### Interpreting Memory Results

```java
// Example: Memory benchmark output
Benchmark: heapUsageDuringWorkflowExecution
├── Initial heap: 256 MB
├── Peak heap:    512 MB
├── Average:      384 MB
├── Final heap:   267 MB
└── GC time:      2.3% of total
```

**What this tells us:**
- Peak memory ~2x initial (normal for temporary objects)
- Heap recovers after execution (no leaks)
- GC < 5% (acceptable overhead)
- Good memory efficiency

## Step 7: Workflow Pattern Benchmarks

Test specific control flow patterns:

```bash
# Sequential workflow performance
java -jar target/benchmarks.jar \
    ".*WorkflowPatternBenchmarks.sequentialWorkflowPerformance"

# Parallel split/sync performance
java -jar target/benchmarks.jar \
    -n 50 -w 20 -f 5 \
    ".*WorkflowPatternBenchmarks.parallelSplitSyncPerformance"

# Complex multi-pattern execution
java -jar target/benchmarks.jar \
    -n 100 -w 20 -f 5 \
    ".*WorkflowPatternBenchmarks.mixedPatternComplexityPerformance"
```

## Step 8: Compare Results Against Baselines

YAWL maintains baseline metrics for regression detection:

```bash
# Run benchmarks and save results
java -jar target/benchmarks.jar \
    -n 50 -w 20 -f 3 \
    -rf json -rff results.json \
    YAWLEngineBenchmarks

# Compare new results to baseline (using custom script)
./scripts/compare-benchmarks.sh results.json baselines/v6.0.0.json
```

### Regression Thresholds

| Metric | Threshold | Action |
|--------|-----------|--------|
| Latency | > 20% increase | ⚠️ Investigate |
| Throughput | > 15% decrease | ⚠️ Investigate |
| Memory | > 25% increase | ⚠️ Investigate |
| Error rate | > 0.5% | 🔴 Fail |

## Step 9: Optimize Your Configuration

Use benchmark results to tune your YAWL deployment:

```bash
# Test with different JVM options
JAVA_OPTS="-XX:+UseZGC -XX:-ZUncommit"
java $JAVA_OPTS -jar target/benchmarks.jar \
    YAWLEngineBenchmarks

# Compare with default settings
JAVA_OPTS=""
java $JAVA_OPTS -jar target/benchmarks.jar \
    YAWLEngineBenchmarks

# Test with virtual thread pooling
JAVA_OPTS="-Djdk.virtualThreadScheduler.maxPoolSize=256"
java $JAVA_OPTS -jar target/benchmarks.jar \
    ConcurrencyBenchmarks
```

### Recommended JVM Options for YAWL v6

```bash
export JAVA_OPTS="
  -server
  -XX:+UseZGC              # Low-latency GC
  -XX:+ZUncommit           # Reclaim unused memory
  -XX:ZStatisticsInterval=0 # Reduce overhead
  -Djdk.virtualThreadScheduler.maxPoolSize=256
  -Djdk.virtualThreadScheduler.parallelism=8
"

java $JAVA_OPTS -jar target/benchmarks.jar YAWLEngineBenchmarks
```

## Key Takeaways

1. **JMH provides statistical rigor** for performance measurements
2. **Multiple metrics matter**: throughput, latency, memory, GC
3. **Baseline comparisons detect regressions** before production
4. **Virtual threads enable massive scaling** (5-6× improvement)
5. **Regular benchmarking** is essential for large-scale deployments

## Common Patterns

### Identifying Performance Bottlenecks

```bash
#!/bin/bash
# Run all benchmarks and identify slowest operations

echo "Running comprehensive performance analysis..."

echo "=== Engine Performance ==="
java -jar benchmarks.jar YAWLEngineBenchmarks | grep -E "Score|Error"

echo "=== Concurrency Performance ==="
java -jar benchmarks.jar ConcurrencyBenchmarks | grep -E "Score|Error"

echo "=== Memory Usage ==="
java -jar benchmarks.jar MemoryBenchmarks | grep -E "Score|Error"

echo "=== Pattern Performance ==="
java -jar benchmarks.jar WorkflowPatternBenchmarks | grep -E "Score|Error"
```

### Automated Regression Detection

```bash
#!/bin/bash
# Compare new results to baseline

NEW_RESULTS=$(mktemp)
java -jar benchmarks.jar -rf json -rff "$NEW_RESULTS" YAWLEngineBenchmarks

# Check for regressions
python3 - <<'EOF'
import json
import sys

with open('$NEW_RESULTS') as f:
    new = json.load(f)

with open('baseline.json') as f:
    baseline = json.load(f)

for bench in new['results']:
    baseline_score = baseline['results'][bench['benchmark']]['score']
    new_score = bench['primaryMetric']['score']
    regression = ((baseline_score - new_score) / baseline_score) * 100

    if regression > 15:
        print(f"REGRESSION: {bench['benchmark']}")
        print(f"  Baseline: {baseline_score:.2f}")
        print(f"  New:      {new_score:.2f}")
        print(f"  Change:   {regression:.1f}%")
        sys.exit(1)

print("✓ No significant regressions detected")
EOF
```

## Troubleshooting

**"Out of Memory" during benchmarks:**
```bash
# Increase heap size
java -Xms4g -Xmx8g -jar target/benchmarks.jar YAWLEngineBenchmarks
```

**"Benchmarks run too slow":**
```bash
# Reduce iterations and forks
java -jar target/benchmarks.jar \
    -n 10 -w 5 -f 1 \  # Fewer iterations for quick feedback
    YAWLEngineBenchmarks
```

**"Results are too noisy (high error):"**
```bash
# Increase warmup and measurement iterations
java -jar target/benchmarks.jar \
    -n 100 -w 50 -f 10 \  # More iterations for stability
    YAWLEngineBenchmarks
```

**"Comparing against wrong baseline":**
```bash
# Ensure you're comparing v6.0.0 results to v6.0.0 baseline
cat baseline.json | grep -i "version"
cat results.json | grep -i "version"
```

## Next Steps

- Learn [Performance Optimization](../how-to/performance-testing.md)
- Explore [Capacity Planning](../reference/capacity-planning.md) for 1M cases
- Set up [Performance Baselines](../reference/performance-baselines.md)
- Read [Performance Targets](../reference/performance-targets.md)
- Implement [Monitoring and Observability](../reference/metrics.md)

## Benchmark Metrics Reference

| Benchmark | Measures | Good Performance |
|-----------|----------|-----------------|
| `engineStartupPerformance` | Initialization time | < 60 seconds |
| `caseCreationThroughput` | Cases/second | > 1000 ops/ms |
| `workItemCheckoutLatency` | P95 latency | < 200ms |
| `taskTransitionPerformance` | Task completion speed | < 100ms |
| `virtualVsPlatformThreadPerformance` | Threading model | 5× improvement |
| `memoryLeakDetection` | Memory stability | < 5MB growth |
| `gcPressureUnderLoad` | GC overhead | < 5% of time |

---

**Ready to optimize your YAWL deployment?** Continue with [Performance Testing Guide](../how-to/performance-testing.md).
