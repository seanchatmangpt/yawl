# YAWL Performance Analysis Guide

**Status**: PRODUCTION READY
**Scope**: Comprehensive performance benchmarking and analysis for YAWL v6.0.0

## Table of Contents

1. [JMH Basics & Key Metrics](#jmh-basics--key-metrics)
2. [Running Benchmarks](#running-benchmarks)
3. [Interpreting Results](#interpreting-results)
4. [Performance Targets](#performance-targets)
5. [SPARQL Engine Comparison](#sparql-engine-comparison)
6. [Scaling Guidance](#scaling-guidance)
7. [Memory & GC Analysis](#memory--gc-analysis)
8. [CI/CD Integration](#cicd-integration)
9. [Advanced Analysis](#advanced-analysis)

---

## JMH Basics & Key Metrics

### What is JMH?

JMH (Java Microbenchmark Harness) is the industry standard for Java microbenchmarking. It eliminates common benchmarking pitfalls:

- **Warmup**: JVM warmup iterations
- **Forking**: Separate JVM processes
- **Profiling**: Built-in profilers
- **Metrics**: Aggregated statistical results

### Key Metrics

| Metric | Description | Interpretation |
|--------|-------------|---------------|
| **Average Time** | Mean execution time across iterations | Central tendency |
| **Percentiles (p50, p95, p99, p99.9)** | Value at which X% of measurements fall | Latency distribution |
| **Throughput** | Operations per second | Capacity measurement |
| **Error** | Standard error of the mean | Statistical confidence |

### Percentile Interpretation

```
p50: 50th percentile (median)
p95: 95th percentile (1 in 20 slower)
p99: 99th percentile (1 in 100 slower)
p99.9: 99.9th percentile (1 in 1000 slower)
```

**Example**: p95 < 500ms means 95% of operations complete within 500ms

---

## Running Benchmarks

### Quick Start

```bash
# Run all SPARQL engine benchmarks
./scripts/benchmark-qlever.sh

# Run specific engine
./scripts/benchmark-qlever.sh qlever-http
./scripts/benchmark-qlever.sh qlever-embedded
./scripts/benchmark-qlever.sh oxigraph

# Run core YAWL engine benchmarks
mvn -pl yawl-benchmark clean install
java -jar yawl-benchmark/target/benchmarks.jar YAWLEngineBenchmarks -wi 5 -i 10 -f 2
```

### JMH Configuration

```bash
# Typical configuration for stable measurements
java -jar benchmarks.jar BenchmarkName \
  -wi 10        # 10 warmup iterations \
  -i 50         # 50 measurement iterations \
  -f 3          # 3 forks (separate JVMs) \
  -t 1          # 1 thread at a time \
  -rf json      # JSON output format \
  -rff results.json
```

### Environment Setup

```bash
# Ensure Java 25+ and Maven 3.9+
java -version
mvn -version

# Start required engines
# QLever HTTP (port 7001)
docker run -p 7001:7001 qlever/qlever:latest

# Oxigraph (port 8083)
./scripts/start-yawl-native.sh
```

### Memory Configuration

```bash
# ZGC with large heap for benchmarking
java -Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders \
     -jar benchmarks.jar ...
```

---

## Interpreting Results

### Result Structure

```json
{
  "benchmark": "BenchmarkName",
  "mode": "AverageTime",
  "params": {...},
  "primary_metric": "ops/time",
  "iterations": 50,
  "forks": 3,
  "warmup_iterations": 10,
  "results": [
    {
      "iteration": 1,
      "score": 0.245,
      "score_error": 0.012,
      "score_error_stddev": 0.008
    }
  ],
  "percentiles": {
    "p50": 0.258,
    "p95": 0.487,
    "p99": 0.891
  }
}
```

### Key Analysis Techniques

#### 1. Stability Check

```bash
# Check coefficient of variation (CV < 5% is good)
jq '.benchmarks[] | .score.error / .score.mean * 100' results.json
```

#### 2. Regression Detection

```bash
# Compare with baseline
compare-benchmarks baseline.json new-results.json --threshold 10
```

#### 3. Statistical Significance

- **p-value < 0.05**: Statistically significant difference
- **Confidence interval**: 95% CI around mean
- **Effect size**: Magnitude of difference

#### 4. Visual Analysis

```bash
# Generate histogram
python -c "
import json
import matplotlib.pyplot as plt
with open('results.json') as f:
    data = json.load(f)
plt.hist([r['score'] for r in data['results']])
plt.xlabel('Execution Time (ms)')
plt.ylabel('Frequency')
plt.title('Benchmark Distribution')
plt.show()
"
```

### Status Classification

| Status | Meaning | Action |
|--------|---------|--------|
| **GREEN** | Within target | Proceed to production |
| **YELLOW** | Near threshold | Investigate |
| **RED** | Exceeds threshold | Fix before deployment |

---

## Performance Targets

### Critical Metrics

| Component | Target | Measurement |
|-----------|--------|-------------|
| **Engine Startup** | < 60 seconds | YStatelessEngine initialization |
| **Case Creation** | P95 < 500ms | New case creation latency |
| **Task Launch** | P95 < 100ms | Work item enablement |
| **Task Completion** | P95 < 50ms | Work item completion |
| **SPARQL Query** | P95 < 1s | Complex queries |
| **Memory Alloc** | < 1MB/case | Per-case memory footprint |

### Scaling Targets

```
Registry Size   | Latency Delta | Status
----------------|---------------|--------
100K cases      | Baseline      | GREEN
250K cases     | < 5%          | GREEN
500K cases     | < 10%         | GREEN
750K cases     | < 20%         | YELLOW
1M cases       | < 50%         | YELLOW
```

### Throughput Targets

| Workload | Target | Scenario |
|----------|--------|----------|
| **Case Creation** | 10K+/sec | Batch import |
| **Task Processing** | 50K+/sec | High load |
| **SPARQL Queries** | 500+/sec | Analytics |

---

## SPARQL Engine Comparison

### Engine Types

| Engine | Mode | Latency | Throughput | Memory Use |
|--------|------|---------|------------|------------|
| **QLever HTTP** | Remote | High | Medium | Low |
| **QLever Embedded** | FFI | Low | High | High |
| **Oxigraph** | FFI | Medium | Medium | Medium |

### Benchmark Patterns

```java
// 1. Simple Construct Query
@Benchmark
public void simpleConstruct(Blackhole bh) throws Exception {
    String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                   "CONSTRUCT { ?case a yawl:Case . } " +
                   "WHERE { ?case a yawl:Case . } LIMIT 100";

    String result = engine.constructToTurtle(query);
    bh.consume(result);
}

// 2. Complex Join Query
@Benchmark
public void complexJoin(Blackhole bh) throws Exception {
    String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                   "CONSTRUCT { ?case yawl:hasTask ?task . } " +
                   "WHERE { " +
                   "  ?case a yawl:Case . " +
                   "  ?case yawl:hasTask ?task . " +
                   "  ?task yawl:hasStatus 'running' " +
                   "} LIMIT 200";

    String result = engine.constructToTurtle(query);
    bh.consume(result);
}

// 3. Large Result Set
@Benchmark
public void largeResult(Blackhole bh) throws Exception {
    String query = "PREFIX yawl: <http://www.yawlfoundation.org/yawl#> " +
                   "CONSTRUCT { ?s ?p ?o . } " +
                   "WHERE { ?s ?p ?o . } LIMIT 5000";

    String result = engine.constructToTurtle(query);
    bh.consume(result);
}
```

### Expected Results

```json
{
  "qlever-http": {
    "simpleConstruct": {"p95_ms": 12.5, "throughput_ops_sec": 80000},
    "complexJoin": {"p95_ms": 45.2, "throughput_ops_sec": 22000},
    "largeResult": {"p95_ms": 234.7, "throughput_ops_sec": 4200}
  },
  "qlever-embedded": {
    "simpleConstruct": {"p95_ms": 2.1, "throughput_ops_sec": 476000},
    "complexJoin": {"p95_ms": 8.7, "throughput_ops_sec": 114000},
    "largeResult": {"p95_ms": 45.3, "throughput_ops_sec": 22000}
  },
  "oxigraph": {
    "simpleConstruct": {"p95_ms": 4.5, "throughput_ops_sec": 222000},
    "complexJoin": {"p95_ms": 15.2, "throughput_ops_sec": 65700},
    "largeResult": {"p95_ms": 78.9, "throughput_ops_sec": 12600}
  }
}
```

### Selection Guide

- **Embedded QLever**: Best performance for high-load applications
- **QLever HTTP**: Easier deployment, shared resources
- **Oxigraph**: Good balance, easier to maintain

---

## Scaling Guidance

### Registry Size Thresholds

| Registry Size | Memory | CPU Cores | Recommended Actions |
|---------------|--------|-----------|-------------------|
| **< 100K cases** | 2-4GB | 2-4 | Normal configuration |
| **100K - 500K** | 4-8GB | 4-8 | Enable ZGC, increase heap |
| **500K - 1M** | 8-16GB | 8-16 | Consider partitioning |
| **> 1M cases** | 16GB+ | 16+ | Distributed registry |

### Scaling Strategies

#### 1. Horizontal Scaling

```bash
# Partition registry by case ID range
# Node 1: cases 0-999999
# Node 2: cases 1000000-1999999
# ...

# Load balancing
nginx upstream yawl_nodes {
    server node1:8080;
    server node2:8080;
    server node3:8080;
}
```

#### 2. Vertical Scaling

```bash
# ZGC configuration for large heaps
java -Xms16g -Xmx16g \
     -XX:+UseZGC \
     -XX:+ZGCUncommit \
     -XX:+UseCompactObjectHeaders \
     -XX:+AlwaysPreTouch \
     -XX:+DisableExplicitGC
```

#### 3. Caching Strategy

```java
// Cache frequently accessed cases
private final Map<String, YWorkItem> caseCache =
    new ConcurrentHashMap<>(10000);

// Cache with expiration
Cache<String, YWorkItem> cachedCases =
    Caffeine.newBuilder()
           .maximumSize(10000)
           .expireAfterWrite(5, TimeUnit.MINUTES)
           .build();
```

### Capacity Planning

| Throughput | Cases/Hour | Memory/CPU | Notes |
|------------|------------|------------|-------|
| **1K cases/hr** | 1,000 | 2GB/2CPU | Development |
| **10K cases/hr** | 10,000 | 4GB/4CPU | Production |
| **100K cases/hr** | 100,000 | 8GB/8CPU | High load |
| **1M cases/hr** | 1,000,000 | 16GB/16CPU | Very high load |

---

## Memory & GC Analysis

### ZGC Configuration

```java
// Optimal ZGC settings for YAWL
-XX:+UseZGC
-XX:+ZGCUncommit               # Return memory to OS
-XX:+UseCompactObjectHeaders    # 5-10% memory savings
-XX:+AlwaysPreTouch             # Pre-touch pages on startup
-XX:ZGCHeapRegionSize=2m        # Region size
-XX:ParallelGCThreads=4         # GC threads
-XX:ConcGCThreads=4             # Concurrent GC threads
```

### Memory Metrics

```java
// Monitor memory usage
MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

long used = heapUsage.getUsed();
long committed = heapUsage.getCommitted();
long max = heapUsage.getMax();

double utilization = (double) used / committed * 100;

if (utilization > 80) {
    log.warn("High memory utilization: {}%", utilization);
}
```

### GC Analysis

```bash
# Monitor GC activity
jstat -gcutil <pid> 1s

# Generate GC log
java -Xlog:gc*=info:gc.log \
     -XX:+PrintGCDetails \
     -XX:+PrintGCDateStamps \
     ...
```

### GC Tuning Tips

1. **ZGC Preferred for Large Heaps**: Sub-millisecond pause times
2. **AlwaysPreTouch**: Avoids page faults during execution
3. **Uncommit Memory**: Returns unused memory to OS
4. **Compact Object Headers**: Reduces memory footprint by 5-10%

### Memory Leak Detection

```java
// VisualVM for memory profiling
# Start VisualVM
visualvm

# Monitor memory over time
# Look for increasing heap usage without GC
```

### Object Size Analysis

```java
# Using JOL (Java Object Layout)
<dependency>
    <groupId>org.openjdk.jol</groupId>
    <artifactId>jol-core</artifactId>
    <version>0.17</version>
</dependency>

// Analyze object layout
System.out.println(ClassLayout.parseClass(YWorkItem.class).toPrintable());
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Performance Regression Check
on: [pull_request, push]

jobs:
  benchmark:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 25
      uses: actions/setup-java@v3
      with:
        java-version: '25'
        distribution: 'temurin'

    - name: Run benchmarks
      run: |
        ./scripts/benchmark-qlever.sh

        # Run core engine benchmarks
        mvn -pl yawl-benchmark clean install
        java -jar yawl-benchmark/target/benchmarks.jar \
          -rf json \
          -rff benchmark-results/pr.json \
          YAWLEngineBenchmarks

    - name: Compare with baseline
      run: |
        python scripts/compare-benchmarks.py \
          baseline.json \
          benchmark-results/pr.json \
          --threshold 10

    - name: Upload results
      uses: actions/upload-artifact@v3
      with:
        name: benchmark-results
        path: benchmark-results/
```

### Baseline Management

```python
# scripts/compare-benchmarks.py
import json
import sys
import argparse
import statistics

def load_results(file_path):
    with open(file_path) as f:
        data = json.load(f)

    # Extract p99 values
    results = {}
    for benchmark in data.get('benchmarks', []):
        name = benchmark['benchmark']
        results[name] = {
            'p99_ms': benchmark['score']['p99'],
            'mean_ms': benchmark['score']['mean'],
            'error_ms': benchmark['score']['error']
        }
    return results

def compare_baselines(baseline, current, threshold=10):
    regressions = []

    for name, current_vals in current.items():
        if name not in baseline:
            continue

        baseline_vals = baseline[name]

        # Compare p99 values
        p99_delta = (current_vals['p99_ms'] - baseline_vals['p99_ms']) / baseline_vals['p99_ms'] * 100

        if p99_delta > threshold:
            regressions.append({
                'benchmark': name,
                'p99_baseline': baseline_vals['p99_ms'],
                'p99_current': current_vals['p99_ms'],
                'delta_percent': p99_delta
            })

    return regressions

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('baseline_file')
    parser.add_argument('current_file')
    parser.add_argument('--threshold', type=float, default=10)

    args = parser.parse_args()

    baseline = load_results(args.baseline_file)
    current = load_results(args.current_file)

    regressions = compare_baselines(baseline, current, args.threshold)

    if regressions:
        print("Performance Regressions Detected:")
        for reg in regressions:
            print(f"  {reg['benchmark']}: {reg['delta_percent']:.1f}% increase")
        sys.exit(1)
    else:
        print("No significant regressions detected")
```

### Alerting Configuration

```yaml
# Alert for performance degradation
- name: Check Performance Threshold
  if: steps.benchmark.outputs.regressions > 0
  run: |
    echo "::warning Performance regressions detected!"
    echo "Please review and fix performance issues before merging"
```

### Dashboard Integration

```python
# Generate performance dashboard
import matplotlib.pyplot as plt
import pandas as pd

def create_dashboard(results_file):
    df = pd.read_json(results_file)

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))

    # Latency chart
    df.plot.bar(y=['p50', 'p95', 'p99'], ax=ax1)
    ax1.set_title('Latency Distribution (ms)')
    ax1.set_ylabel('Time (ms)')

    # Throughput chart
    df.plot.bar(y=['throughput_ops_sec'], ax=ax2, color='orange')
    ax2.set_title('Throughput (ops/sec)')
    ax2.set_ylabel('Operations/sec')

    plt.tight_layout()
    plt.savefig('performance-dashboard.png')
```

---

## Advanced Analysis

### Profiling Techniques

#### 1. Flight Recorder

```bash
# Record flight data
java -XX:+FlightRecorder -XX:StartFlightRecording:duration=60s,filename=jfr.jfr \
     -Xms4g -Xmx4g \
     -jar app.jar

# Analyze recording
jfr analyze jfr.jfr
```

#### 2. Async Profiler

```bash
# Download async profiler
wget -O async-profiler-linux-x64.tar.gz https://github.com/jvm-profiling-tools/async-profiler/releases/download/v2.9/async-profiler-linux-x64.tar.gz

# Run profiling
./profiler.sh -e cpu -f profile.jfr <pid>
```

### Bottleneck Analysis

```java
// Custom timing annotations
@Benchmark
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public void caseCreationLatency(Blackhole bh) throws Exception {
    long start = System.nanoTime();

    // Case creation
    String caseId = engine.launchCase(specId);

    long duration = System.nanoTime() - start;
    metrics.record("case_creation_latency_ns", duration);

    bh.consume(caseId);
}
```

### Contention Analysis

```bash
# Monitor thread contention
jstack -l <pid> > thread_dump.txt

# Analyze with Thread Dump Analyzer
# Look for:
# - Thread contention on locks
# - Blocked threads
# - CPU utilization
```

### Memory Contention Analysis

```java
// Monitor GC pressure
@Benchmark
@BenchmarkMode(Mode.AverageTime)
public void gcPressureMeasurement() throws Exception {
    long gcBefore = ManagementFactory.getGarbageCollectorMXBean().getCollectionTime();

    // Memory-intensive operation
    for (int i = 0; i < 1000; i++) {
        String xml = marshallCase(caseId);
        parseSpecification(xml);
    }

    long gcAfter = ManagementFactory.getGarbageCollectorMXBean().getCollectionTime();

    long gcDelta = gcAfter - gcBefore;
    metrics.record("gc_time_ms", gcDelta);
}
```

### Correlation Analysis

```python
# Correlate metrics
import pandas as pd
import seaborn as sns

# Load benchmark results
latency_data = pd.read_json('latency_results.json')
memory_data = pd.read_json('memory_results.json')

# Merge datasets
combined = pd.merge(latency_data, memory_data, on='benchmark')

# Calculate correlation
correlation = combined[['p99_ms', 'heap_usage_mb']].corr()

# Visualize
sns.heatmap(correlation, annot=True)
plt.title('Latency-Memory Correlation')
plt.savefig('correlation.png')
```

### Trend Analysis

```python
# Track performance trends over time
import matplotlib.pyplot as plt

def plot_trend(results_files):
    all_data = []

    for file in results_files:
        with open(file) as f:
            data = json.load(f)
            # Add timestamp
            data['timestamp'] = file.split('_')[1].replace('.json', '')
            all_data.append(data)

    # Plot trends
    plt.figure(figsize=(12, 6))

    for benchmark in ['caseCreation', 'taskCompletion']:
        values = [d[benchmark]['p99_ms'] for d in all_data]
        plt.plot([d['timestamp'] for d in all_data], values, label=benchmark)

    plt.xlabel('Date')
    plt.ylabel('P99 Latency (ms)')
    plt.title('Performance Trends')
    plt.legend()
    plt.savefig('trends.png')
```

---

## Troubleshooting

### Common Issues

#### 1. High Variability in Results

```bash
# Check for:
# - Insufficient warmup (-wi 3 may be too low)
# - Too few iterations (-i 5 may be too low)
# - JIT compilation still happening

# Solution: Increase warmup and iterations
java -jar benchmarks.jar -wi 20 -i 100 -f 3
```

#### 2. Memory Pressure

```bash
# Monitor memory usage
jstat -gcutil <pid> 1s

# If GC is too frequent:
# - Increase heap size
# - Optimize data structures
# - Reduce object allocation
```

#### 3. Thread Contention

```bash
# Check for lock contention
jstack -l <pid> | grep -i locked

# Solutions:
# - Use ConcurrentHashMap instead of synchronized
# - Consider read-write locks
# - Use thread-local caches
```

### Optimization Checklist

- [ ] Check JIT warmup
- [ ] Monitor GC pauses
- [ ] Look for memory leaks
- [ ] Check for CPU bottlenecks
- [ ] Profile with Flight Recorder
- [ ] Analyze thread contention
- [ ] Review algorithm complexity
- [ ] Consider caching strategies

---

## Best Practices

### Benchmarking

1. **Always fork**: Use `-f 3` to separate JVM processes
2. **Warm sufficiently**: Use `-wi 10` to ensure JIT compilation
3. **Measure in isolation**: One benchmark at a time
4. **Control environment**: Disable power management, consistent load

### Performance

1. **Profile before optimizing**: Don't guess, measure
2. **Measure impact**: Ensure changes actually help
3. **Consider trade-offs**: Latency vs throughput vs memory
4. **Set realistic targets**: Know your workload

### Monitoring

1. **Baseline everything**: Record performance before changes
2. **Automate regression checks**: Integrate into CI
3. **Monitor continuously**: Set up dashboards
4. **Alert on degradation**: Get notified of issues

### Documentation

1. **Document assumptions**: Note test conditions
2. **Record baselines**: Keep historical data
3. **Share findings**: Document performance characteristics
4. **Update targets**: Adjust as workload evolves

---

## References

- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [ZGC Guide](https://docs.oracle.com/en/java/javase/17/guide)
- [Java Performance Tuning](https://shipilev.net/talks/JCPDevo2019-performance.pdf)
- [Async Profiler](https://github.com/jvm-profiling-tools/async-profiler)
- [VisualVM](https://visualvm.github.io/)

---

**Maintained by**: YAWL Performance Team
**Last Updated**: 2026-03-01
**Version**: 1.0