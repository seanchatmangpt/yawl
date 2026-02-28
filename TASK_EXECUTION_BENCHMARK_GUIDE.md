# Task Execution Latency Benchmark — Execution Guide

**Version**: 6.0.0  
**Date**: 2026-02-28  
**Target Metrics**: Task enable → fire → complete latency at scale  

## Overview

This guide documents how to run the task execution latency benchmark for YAWL workflow engine. The benchmark measures the critical path for case throughput: the time required to fire a work item (task) and complete it across different case scales (100K, 500K, 1M).

## Benchmark Specification

### What It Measures

**Task Lifecycle** (four phases):
1. **Enable**: Task becomes available for execution (YNetRunner marks task as enabled)
2. **Fire**: Work item starts execution (YNetRunner.startWorkItem())
3. **Execute**: Task logic runs (simulated as work in JMH benchmark)
4. **Complete**: Work item completes with output (YNetRunner.completeWorkItem())

The benchmark measures **phases 2-4**: the critical path from fire → complete.

### Test Configuration

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| **Workflow Pattern** | Parallel split + sync (AND) | 2 enabled tasks per case |
| **Case Scales** | 100K, 500K, 1M | Test scaling from baseline → 10× |
| **Concurrent Threads** | 16 | Match CPU core count (8 cores × 2) |
| **JMH Iterations** | 25 measurement + 5 warmup | JIT warmup + stable measurements |
| **JVM Heap** | 4GB | YNetRunner object pools |
| **GC** | ZGC generational | Sub-millisecond pauses |
| **Object Headers** | Compact (-XX:+UseCompactObjectHeaders) | 8 bytes/object vs 16 |

### Specifications Used

**BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC**:
```
Start → [AND-Split] → TaskA (async)
                   → TaskB (async)
                   ← [AND-Join] ← End
```

Each case starts with 2 work items (TaskA, TaskB) already enabled, allowing immediate execution without waiting for upstream tasks.

## Running the Benchmark

### Prerequisites

```bash
# Verify Java 25 is available
java -version
# Expected: openjdk version "25.0.2" 2026-01-20 LTS

# Verify Maven 4.x is available
mvn -version
# Expected: Apache Maven 4.0.0+ 

# Verify project is compiled
mvn clean compile -DskipTests -pl yawl-benchmark -am
```

### Command: Full Benchmark Suite

**Recommended**: Run once for complete baseline data.

```bash
mvn clean verify -pl yawl-benchmark -DskipTests

java -jar yawl-benchmark/target/benchmarks.jar TaskExecutionLatencyBenchmark \
  -f 3 \
  -wi 5 \
  -i 25 \
  -t 16 \
  -o /tmp/task-execution-full.json
```

**Interpretation**:
- `-f 3`: Fork into 3 separate JVMs (reduces JIT bias)
- `-wi 5`: 5 warmup iterations (20 seconds each)
- `-i 25`: 25 measurement iterations (50 seconds each)
- `-t 16`: 16 concurrent threads
- `-o`: JSON output for parsing

**Expected Duration**: ~30 minutes (5 warmup × 3 forks + 25 measurement × 3 forks)

### Command: Quick Smoke Test (5 minutes)

**Recommended**: During development for quick feedback.

```bash
java -jar yawl-benchmark/target/benchmarks.jar TaskExecutionLatencyBenchmark \
  -p caseCount=100000 \
  -f 1 \
  -wi 2 \
  -i 5 \
  -t 16
```

### Command: Single-Scale Test (1M Cases Only)

**Recommended**: When testing worst-case scaling.

```bash
java -jar yawl-benchmark/target/benchmarks.jar TaskExecutionLatencyBenchmark \
  -p caseCount=1000000 \
  -f 1 \
  -wi 3 \
  -i 10 \
  -t 16
```

## Interpreting Results

### Sample Output

```
Benchmark                                          Mode  Cnt  Score      Error  Units
TaskExecutionLatencyBenchmark.completeWorkItemOnly avgt   25  3.456 ±   0.234  ms/op
TaskExecutionLatencyBenchmark.executeTask          avgt   25 11.234 ±   1.123  ms/op
TaskExecutionLatencyBenchmark.startWorkItemOnly    avgt   25  7.234 ±   0.678  ms/op
```

### Percentile Output

```
TaskExecutionLatencyBenchmark.executeTask
p0.00:    5.000 ms/op
p50.00:   10.000 ms/op
p95.00:   15.000 ms/op    ← CRITICAL: Should be < 100ms
p99.00:   25.000 ms/op    ← CRITICAL: Should be < 500ms
p99.90:   50.000 ms/op
p99.99:   100.000 ms/op
p100.00:  250.000 ms/op   ← Check for spikes > 1000ms
```

### Success Criteria

#### PASS: All metrics pass
- p95 < 100ms at all scales (100K, 500K, 1M)
- p99 < 500ms
- No outliers > 1 second
- Linear scaling: p95(1M) ≤ p95(100K) × 1.2

#### WARN: Investigate these
- p95 100-150ms (minor contention detected)
- p99 500-1000ms (tail latency at limit)
- Scaling ratio 1.2-1.5 (non-linear but acceptable)

#### FAIL: Regression detected
- p95 > 150ms (likely lock contention)
- p99 > 1s (unacceptable tail latency)
- Spikes > 2s (potential deadlock)
- Scaling ratio > 1.5 (severe degradation)

## Performance Targets

From CLAUDE.md performance specification:

| Operation | Target (p95) | Target (p99) | Current |
|-----------|--------------|--------------|---------|
| Work item checkout | < 200ms | - | ? |
| Work item checkin | < 300ms | - | ? |
| Task transition | < 100ms | - | ? |
| Full task execution | Implied: <100ms | <500ms | TBD |

**Baseline** (from `/home/user/yawl/test/resources/performance-baseline.json`):
```json
{
  "p50Ms": 11.0,    
  "p95Ms": 14.0,    ← Regression threshold: 14 × 1.1 = 15.4ms
  "p99Ms": 17.0,
  "throughputOpsPerSec": 850.0,
  "capturedAt": "2026-02-27T00:00:00Z"
}
```

## Analysis Checklist

After running the benchmark:

- [ ] Record the three runs (100K, 500K, 1M cases) separately
- [ ] Extract p95, p99 for each scale
- [ ] Calculate scaling ratio: p95(1M) / p95(100K)
  - ✓ Expected: < 1.2
  - ⚠ Warning: 1.2-1.5
  - ✗ Fail: > 1.5
- [ ] Check for regressions vs baseline (14ms)
  - ✓ New p95 ≤ 15.4ms
  - ⚠ New p95 15.4-20ms
  - ✗ New p95 > 20ms
- [ ] Analyze which method is slowest:
  - `executeTask()`: Full cycle (lock + state machine)
  - `startWorkItemOnly()`: Fire operation (lock contention)
  - `completeWorkItemOnly()`: Completion (data marshaling)

## Troubleshooting

### Issue: "Cannot find or load main class java.lang.Object"

**Cause**: Corrupted JAVA_TOOL_OPTIONS environment variable

**Fix**:
```bash
# Clear proxy settings
unset JAVA_TOOL_OPTIONS
export JAVA_TOOL_OPTIONS=""

# Re-run benchmark
java -jar yawl-benchmark/target/benchmarks.jar ...
```

### Issue: p95 Significantly Higher Than Expected

**Investigate**:
1. Check JVM GC logs:
   ```bash
   java -Xlog:gc*:file=/tmp/gc.log \
     -jar yawl-benchmark/target/benchmarks.jar ...
   ```
   Look for: Full GCs, pause times > 10ms

2. Check system load:
   ```bash
   # During benchmark run, in another terminal
   watch -n1 'vmstat 1 2 | tail -1'
   ```
   Look for: CPU stealing, high I/O wait

3. Profile with JFR (Java Flight Recorder):
   ```bash
   java -XX:+FlightRecorder \
     -XX:StartFlightRecording=filename=/tmp/recording.jfr \
     -jar yawl-benchmark/target/benchmarks.jar ...
   
   # Analyze with jmc (JDK Mission Control)
   jmc /tmp/recording.jfr
   ```

### Issue: p95 Much Lower Than Expected

**Possible**:
- Benchmark is not CPU-bound (blocked on I/O)
- JIT hasn't fully optimized yet (increase warmup iterations)
- Case creation is fast but task execution still pending

**Action**: Run with `-profp jfr` to capture execution profile

## Scaling Analysis Deep Dive

### Expected Behavior

As case count increases from 100K → 1M:

**100K Cases** (Baseline):
- Few concurrent cases competing for work item repository locks
- Expected p95: ~10-15ms
- Lock contention: Minimal

**500K Cases** (5× baseline):
- Moderate contention on WorkItemRepository
- More GC pressure (5× more YWorkItem objects)
- Expected p95: ~12-20ms (20-30% increase)

**1M Cases** (10× baseline):
- High contention on WorkItemRepository locks
- Significant GC pressure
- Potential lock waits on task state transitions
- Expected p95: ~15-30ms (50-100% increase, but still linear)

### Regression Patterns

If p95 shows **non-linear scaling** (e.g., 100K=15ms, 1M=100ms):

**Likely cause**: Lock contention in YNetRunner or WorkItemRepository
- YNetRunner.fire() holds global lock too long
- WorkItemRepository.getEnabledWorkItems() has O(n) synchronization
- Hibernate lazy loading in task evaluation

**Action**:
1. Profile YNetRunner.fire() with JFR
2. Check WorkItemRepository implementation
3. Look for N+1 queries in task guard evaluation

## Files and Code References

### Benchmark Implementation

```
/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/
├── TaskExecutionLatencyBenchmark.java     ← Main benchmark
├── BenchmarkSpecFactory.java              ← Workflow specs
├── soak/
│   ├── BenchmarkMetricsCollector.java     ← Metrics collection
│   └── BenchmarkReportGenerator.java      ← Report generation
└── regression/
    └── BaselineManager.java               ← Baseline comparison
```

### Engine Implementation Being Tested

```
/home/user/yawl/yawl-stateless/src/main/java/org/yawlfoundation/yawl/stateless/
├── engine/
│   ├── YNetRunner.java                    ← Task firing logic
│   ├── YWorkItem.java                     ← Work item state
│   └── WorkItemRepository.java            ← Enabled item tracking
└── elements/
    └── YTask.java                         ← Task definition
```

### Performance Documentation

```
/home/user/yawl/
├── CLAUDE.md                              ← Performance targets
├── docs/reference/performance-targets.md  ← Build + engine targets
├── docs/reference/performance-baselines.md ← Historical data
└── .claude/
    └── agents/
        └── yawl-performance-benchmarker.md ← Benchmark agent specs
```

## Next Steps

1. **Build** the benchmark module
2. **Run** the full suite (30 minutes)
3. **Compare** results to baseline
4. **Investigate** any regressions
5. **Commit** baseline if improvements verified

## Command Summary

```bash
# Full benchmark (recommended for CI/CD)
mvn clean verify -pl yawl-benchmark -DskipTests && \
java -jar yawl-benchmark/target/benchmarks.jar TaskExecutionLatencyBenchmark \
  -f 3 -wi 5 -i 25 -t 16 -o /tmp/results.json

# Quick smoke test (development)
java -jar yawl-benchmark/target/benchmarks.jar TaskExecutionLatencyBenchmark \
  -p caseCount=100000 -f 1 -wi 2 -i 5 -t 16

# Parse JSON results
cat /tmp/results.json | jq '.[] | .primaryMetric.scorePercentiles'
```

---

**Last Updated**: 2026-02-28  
**Benchmark Version**: 6.0.0  
**Status**: Ready for execution
