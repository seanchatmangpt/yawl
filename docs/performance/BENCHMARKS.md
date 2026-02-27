# YAWL v6.0.0-GA Benchmark Guide

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Benchmark Categories](#benchmark-categories)
4. [Running Benchmarks](#running-benchmarks)
5. [Configuration](#configuration)
6. [Performance Targets](#performance-targets)
7. [Analyzing Results](#analyzing-results)
8. [Troubleshooting](#troubleshooting)
9. [Best Practices](#best-practices)

---

## Overview

YAWL v6.0.0-GA includes a comprehensive benchmark suite built on JMH (Java Microbenchmark Harness) for measuring and validating performance across multiple dimensions. The benchmark infrastructure supports:

- **JMH Microbenchmarks**: Precise performance measurements with statistical analysis
- **Load Tests**: Realistic workload simulation with configurable parameters
- **Concurrency Benchmarks**: Virtual thread scaling and structured concurrency testing
- **Memory Benchmarks**: Memory efficiency and leak detection
- **A2A Communication Benchmarks**: Agent-to-Agent message latency and throughput

### Architecture

```
yawl-benchmark/
├── src/main/java/org/yawlfoundation/yawl/benchmark/
│   ├── ConcurrencyBenchmarks.java      # Thread scaling tests
│   ├── MemoryBenchmarks.java           # Memory efficiency tests
│   └── WorkflowPatternBenchmarks.java  # Workflow pattern tests
├── pom.xml                             # Maven configuration
└── target/
    └── benchmarks.jar                  # Shaded JAR for running

test/org/yawlfoundation/yawl/performance/jmh/
├── AllBenchmarksRunner.java            # Main entry point
├── VirtualThreadScalingBenchmarks.java # Virtual thread tests
├── A2ACommunicationBenchmarks.java     # A2A protocol tests
├── MemoryUsageBenchmark.java           # Memory profiling
└── WorkflowExecutionBenchmark.java     # End-to-end workflow tests
```

---

## Quick Start

### Prerequisites

- Java 25+ (required for virtual threads and compact object headers)
- Maven 3.9+
- 8GB+ available memory for benchmarking

### Running All Benchmarks

```bash
# Build the benchmark module
cd /Users/sac/cre/vendors/yawl
mvn clean package -pl yawl-benchmark -DskipTests

# Run all benchmarks
java -jar yawl-benchmark/target/benchmarks.jar

# Run with custom options
java -jar yawl-benchmark/target/benchmarks.jar \
  -wi 3 -i 5 -f 1 \
  -rf json -rff target/jmh-results.json
```

### Running Specific Benchmarks

```bash
# Run only virtual thread benchmarks
java -jar yawl-benchmark/target/benchmarks.jar "VirtualThread.*"

# Run A2A communication benchmarks
java -jar yawl-benchmark/target/benchmarks.jar "A2A.*"

# Run memory benchmarks
java -jar yawl-benchmark/target/benchmarks.jar "Memory.*"
```

### Using Maven

```bash
# Run via Maven exec plugin
mvn exec:java -pl yawl-benchmark \
  -Dexec.mainClass="org.openjdk.jmh.Main" \
  -Dexec.args="-rf json -rff target/results.json"

# Run with JMH profile
mvn verify -pl yawl-benchmark -P jmh-benchmark
```

---

## Benchmark Categories

### 1. Virtual Thread Benchmarks

Tests Java 25 virtual thread performance vs platform threads.

| Benchmark | Description | Target |
|-----------|-------------|--------|
| `testVirtualThreadConcurrency` | Virtual thread scaling (100-50k threads) | Linear scaling >90% efficiency |
| `testPlatformThreadConcurrency` | Baseline platform thread performance | - |
| `testCarrierThreadPoolOptimization` | Carrier thread pool tuning | <1ms per task |
| `testVirtualThreadMixedWorkload` | I/O-bound vs CPU-bound mix | Throughput >500 ops/sec |
| `testVirtualThreadMemoryEfficiency` | Memory per virtual thread | <1KB per thread |

**Location**: `/test/org/yawlfoundation/yawl/performance/jmh/VirtualThreadScalingBenchmarks.java`

### 2. A2A Communication Benchmarks

Tests Agent-to-Agent protocol performance.

| Benchmark | Description | Target |
|-----------|-------------|--------|
| `benchmarkMessageLatency` | Point-to-point message latency | P95 <100ms |
| `benchmarkMessageThroughput` | Messages per second | >500 ops/sec |
| `benchmarkConcurrentMessageHandling` | 4-16 concurrent threads | Linear scaling |
| `benchmarkHighConcurrencyHandling` | 1000+ concurrent requests | Throughput >1000/sec |
| `benchmarkMessageSerialization` | JSON serialization overhead | <10% of total time |
| `benchmarkNetworkPartitionResilience` | Failure handling | >95% success rate |

**Location**: `/test/org/yawlfoundation/yawl/performance/jmh/A2ACommunicationBenchmarks.java`

### 3. Memory Benchmarks

Tests memory efficiency and leak detection.

| Benchmark | Description | Target |
|-----------|-------------|--------|
| `testMemoryUsagePerCase` | Memory per workflow case | <50MB |
| `testMemoryLeakDetection` | Leak detection under load | Stable memory |
| `testGCEfficiency` | Garbage collection overhead | <10% GC time |
| `testObjectAllocation` | Object allocation rate | <1M objects/sec |

### 4. Workflow Execution Benchmarks

Tests end-to-end workflow performance.

| Benchmark | Description | Target |
|-----------|-------------|--------|
| `benchmarkCaseLaunch` | Case creation time | P95 <200ms |
| `benchmarkTaskExecution` | Task processing time | P95 <100ms |
| `benchmarkQueueLatency` | Queue processing latency | <12ms average |
| `benchmarkWorkflowThroughput` | Cases per second | >1000/sec |

---

## Running Benchmarks

### Command Line Options

```bash
# JMH options
-wi, --warmupIterations <n>     # Warmup iterations (default: 3)
-i,  --iterations <n>           # Measurement iterations (default: 5)
-f,  --forks <n>                # JVM forks (default: 1)
-t,  --threads <n>              # Thread count (default: 1)
-rf, --resultFormat <format>    # Output format: json, csv, text
-rff, --resultFile <file>       # Output file path

# JVM options
-jvmArgs "<args>"               # JVM arguments for forked process
-jvmArgsAppend "<args>"         # Append to default JVM args
```

### Example Commands

```bash
# Quick benchmark run (for CI)
java -jar benchmarks.jar -wi 1 -i 3 -f 1

# Comprehensive benchmark run
java -jar benchmarks.jar -wi 5 -i 10 -f 3

# Profiling with async-profiler
java -jar benchmarks.jar -wi 3 -i 5 \
  -jvmArgs "-agentpath:/path/to/libasyncProfiler.so=start,file=profile.html"

# Memory-constrained run
java -Xms2g -Xmx4g -XX:+UseZGC -jar benchmarks.jar
```

### Running via AllBenchmarksRunner

```bash
# Run the comprehensive suite
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"

# Expected output:
# ================================================================================
# YAWL Virtual Threads Performance Benchmark Suite
# ================================================================================
# 
# This suite compares platform threads vs virtual threads across:
#   - Virtual thread scaling (10 to 1M threads)
#   - Structured concurrency vs traditional patterns
#   ...
```

---

## Configuration

### benchmark-config.properties

Located at `/src/main/resources/benchmark-config.properties`:

```properties
# General Configuration
benchmark.enabled=true
benchmark.mode=comprehensive
benchmark.concurrency.level=10
benchmark.warmup.duration=30
benchmark.measurement.duration=60

# Performance Targets
throughput.target=1000
latency.p95.target=200
latency.p99.target=500
error.rate.target=0.01
memory.usage.max=4096

# Virtual Thread Configuration
virtual.threads.enabled=true
virtual.threads.count=-1
virtual.threads.name.prefix=yawl-benchmark

# JVM Configuration
executor.type=virtual
executor.core.pool.size=2
executor.max.pool.size=100
```

### JVM Flags for Optimal Performance

```bash
# Production benchmark flags
-XX:+UseCompactObjectHeaders    # Java 25 compact headers
-XX:+UseZGC                     # Z Garbage Collector
-Xms4g -Xmx8g                   # Heap sizing
-XX:MaxGCPauseMillis=10         # Low latency GC
--enable-preview                # Enable preview features

# Full command
java -XX:+UseCompactObjectHeaders -XX:+UseZGC -Xms4g -Xmx8g \
     --enable-preview -jar benchmarks.jar
```

---

## Performance Targets

### SLO Targets (Service Level Objectives)

| Metric | Target | Critical Threshold |
|--------|--------|-------------------|
| Case Launch P95 | <200ms | >500ms |
| Queue Latency Average | <12ms | >50ms |
| Throughput Efficiency | >95% | <90% |
| Memory Per Case | <50MB | >100MB |
| Error Rate | <0.1% | >1% |
| A2A Message Latency P95 | <100ms | >200ms |

### Virtual Thread Targets

| Metric | Target |
|--------|--------|
| Virtual thread startup | <1ms |
| Context switching overhead | <0.1ms |
| Memory per virtual thread | <8KB |
| Linear scaling efficiency | >90% up to 100k threads |

### Quality Gate Thresholds

```json
{
  "performance_gates": {
    "case_launch_p95_ms": { "target": 200, "critical": 500 },
    "queue_latency_avg_ms": { "target": 12, "critical": 50 },
    "throughput_efficiency": { "target": 0.95, "critical": 0.90 },
    "memory_per_case_mb": { "target": 50, "critical": 100 },
    "error_rate": { "target": 0.001, "critical": 0.01 },
    "a2a_latency_p95_ms": { "target": 100, "critical": 200 }
  }
}
```

---

## Analyzing Results

### JSON Output Format

```json
[
  {
    "benchmark": "org.yawlfoundation.yawl.performance.jmh.VirtualThreadScalingBenchmarks.testVirtualThreadConcurrency",
    "mode": "thrpt",
    "threads": 1,
    "forks": 1,
    "warmupIterations": 3,
    "measurementIterations": 5,
    "params": { "concurrency": 1000 },
    "primaryMetric": {
      "score": 1250.456,
      "scoreError": 25.123,
      "scoreConfidence": [1225.333, 1275.579],
      "scorePercentiles": {
        "0.0": 1200.123,
        "50.0": 1248.567,
        "90.0": 1270.234,
        "95.0": 1280.456,
        "99.0": 1295.678,
        "100.0": 1300.000
      },
      "scoreUnit": "ops/ms"
    }
  }
]
```

### Generating Reports

```bash
# Generate HTML report
./scripts/generate-performance-report.sh \
  --input-dir target/jmh-results \
  --output-dir reports/performance \
  --formats html,json

# Generate with trend analysis
./scripts/generate-performance-report.sh \
  --include-trends \
  --baseline-name 5.1.0
```

### Performance Report Output

The report generator creates:

1. **HTML Report**: Visual dashboard with charts
2. **JSON Report**: Machine-readable detailed results
3. **Markdown Summary**: Executive summary with key findings
4. **PDF Report**: Printable report (requires wkhtmltopdf)

---

## Troubleshooting

### Common Issues

#### 1. OutOfMemoryError during benchmarks

```bash
# Increase heap size
java -Xms8g -Xmx16g -jar benchmarks.jar

# Or reduce concurrency
java -jar benchmarks.jar -jvmArgs "-Xmx8g" -p concurrency=100
```

#### 2. Inconsistent benchmark results

```bash
# Increase warmup and iterations
java -jar benchmarks.jar -wi 10 -i 10 -f 3

# Disable CPU frequency scaling
sudo cpupower frequency-set -g performance
```

#### 3. Virtual thread pinning warnings

```bash
# Enable pinning diagnostics
-Djdk.tracePinnedThreads=full

# Check for synchronized blocks
-Djdk.virtualThreadScheduler.maxPoolSize=8
```

#### 4. JMH annotation processor errors

```bash
# Ensure annotation processor is configured
mvn clean compile -pl yawl-benchmark

# Check pom.xml has jmh-generator-annprocess
```

### Debug Mode

```bash
# Enable verbose JMH output
java -jar benchmarks.jar -v EXTRA

# Enable JVM diagnostics
java -XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintAssembly -jar benchmarks.jar
```

---

## Best Practices

### 1. Benchmark Isolation

- Run benchmarks on dedicated hardware
- Disable power management and frequency scaling
- Close unnecessary applications
- Use isolated CPU cores if available

### 2. Statistical Rigor

- Use at least 3 warmup iterations
- Use at least 5 measurement iterations
- Fork at least once to avoid JIT state pollution
- Report confidence intervals, not just averages

### 3. Realistic Workloads

- Use production-like data sizes
- Include realistic think-time between operations
- Test with concurrent users matching production
- Consider network latency in distributed tests

### 4. Continuous Monitoring

- Track benchmark results over time
- Set up regression alerts (>10% degradation)
- Compare against baseline on every commit
- Store historical results for trend analysis

### 5. CI/CD Integration

```yaml
# Example GitHub Actions integration
- name: Run Performance Benchmarks
  run: |
    java -jar yawl-benchmark/target/benchmarks.jar \
      -wi 1 -i 3 -f 1 \
      -rf json -rff target/benchmark-results.json
    
- name: Check for Regressions
  run: |
    ./scripts/regression-detection.sh \
      performance-baseline.json \
      target/benchmark-results.json \
      10  # 10% threshold
```

---

## Appendix

### JMH Annotations Reference

| Annotation | Purpose |
|------------|---------|
| `@Benchmark` | Marks method as benchmark |
| `@BenchmarkMode` | Throughput, AverageTime, SampleTime |
| `@OutputTimeUnit` | Time unit for results |
| `@State` | Scope: Benchmark, Group, Thread |
| `@Setup` | Initialization before benchmark |
| `@TearDown` | Cleanup after benchmark |
| `@Warmup` | Warmup configuration |
| `@Measurement` | Measurement configuration |
| `@Fork` | JVM fork configuration |
| `@Threads` | Thread count |
| `@Param` | Parameterized benchmark |

### Related Documentation

- [STRESS-TESTS.md](./STRESS-TESTS.md) - Stress testing guide
- [CHAOS-ENGINEERING.md](./CHAOS-ENGINEERING.md) - Chaos testing guide
- [PERFORMANCE-GUIDE.md](./PERFORMANCE-GUIDE.md) - Performance optimization

---

*Generated for YAWL v6.0.0-GA | Last updated: 2026-02-26*
