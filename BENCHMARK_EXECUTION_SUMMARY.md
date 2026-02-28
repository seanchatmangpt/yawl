# Task Execution Latency Benchmark — Execution Summary

**Date**: 2026-02-28  
**Status**: Ready for Execution  
**Owner**: YAWL Performance Specialist  

---

## What Has Been Prepared

A comprehensive task execution latency benchmark suite has been created to measure YAWL workflow engine performance under load. The benchmark measures the critical path for case throughput: task firing and completion latency across three case scales (100K, 500K, 1M).

### Documents Created

1. **TASK_EXECUTION_BENCHMARK_GUIDE.md** (`/home/user/yawl/`)
   - Step-by-step execution instructions
   - Command reference for all benchmark variations
   - Output interpretation guide
   - Troubleshooting section

2. **TASK_EXECUTION_LATENCY_ANALYSIS.md** (`/home/user/yawl/`)
   - Comprehensive performance analysis framework
   - Expected baseline predictions
   - Regression detection methodology
   - Optimization roadmap
   - Decision tree for interpreting results

3. **This Summary** (`/home/user/yawl/BENCHMARK_EXECUTION_SUMMARY.md`)
   - Quick reference guide
   - Key metrics and targets
   - Next steps

---

## Benchmark Overview

### What It Measures

The benchmark quantifies the latency of the **task execution lifecycle** in YAWL:

```
Participant starts task
  ↓
YNetRunner.startWorkItem()       ← Fire operation
  ↓
Task state machine transition
  ↓
YNetRunner.completeWorkItem()    ← Complete operation
  ↓
Next task enabled or case ends
```

**Time measured**: Fire → Complete (typically 10-30ms depending on scale)

### Key Metrics

| Metric | Target | Measured By |
|--------|--------|-------------|
| **p95 latency** | < 100ms | JMH percentile output |
| **p99 latency** | < 500ms | JMH percentile output |
| **Scaling ratio** | < 1.2 (linear) | p95(1M) / p95(100K) |
| **Throughput** | 850+ ops/sec | JMH score (ops/sec) |
| **GC pauses** | < 1ms | gc.log analysis |
| **Lock contention** | Minimal | JFR profile (lock time) |

### Baseline Reference

Current baseline from `/home/user/yawl/test/resources/performance-baseline.json`:

```json
{
  "p50Ms": 11.0,
  "p95Ms": 14.0,    ← Regression threshold: 14 × 1.1 = 15.4ms
  "p99Ms": 17.0,
  "throughputOpsPerSec": 850.0,
  "capturedAt": "2026-02-27T00:00:00Z"
}
```

---

## Test Configuration

### Scales Being Tested

- **100K cases**: Baseline (minimal contention)
- **500K cases**: Moderate scale (5× baseline), approaches 4GB heap limit
- **1M cases**: Maximum scale (10× baseline), stresses lock contention

### JVM Configuration

```
Heap:       -Xms4g -Xmx4g (4GB, matches 1M cases)
GC:         -XX:+UseZGC (zero pauses)
Objects:    -XX:+UseCompactObjectHeaders (8-byte headers)
Threads:    16 concurrent (8 CPU cores × 2)
```

### Benchmark Methods

1. **executeTask()** - Full cycle (fire + complete)
   - Measures: State machine + lock contention
   - Expected: p95 10-30ms depending on scale

2. **startWorkItemOnly()** - Fire operation only
   - Measures: Task enable latency
   - Expected: p95 5-15ms

3. **completeWorkItemOnly()** - Completion operation only
   - Measures: Data marshaling + state update
   - Expected: p95 3-10ms

### Test Parameters

- **Warmup iterations**: 5 (10 seconds each)
- **Measurement iterations**: 25 (50 seconds each)
- **JVM forks**: 3 (reduces JIT bias)
- **Total duration**: ~30 minutes for full suite

---

## How to Execute

### Prerequisites

```bash
cd /home/user/yawl

# Verify Java 25
java -version

# Verify Maven 4.0+
mvn -version

# Clear environment (critical!)
unset JAVA_TOOL_OPTIONS
export JAVA_TOOL_OPTIONS=""
```

### Command: Full Benchmark Suite (Recommended)

```bash
# Build
mvn clean verify -pl yawl-benchmark -DskipTests

# Run (30 minutes)
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -f 3 -wi 5 -i 25 -t 16 \
  -o /tmp/task-execution-full.json
```

### Command: Quick Test (5 minutes)

```bash
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -p caseCount=100000 \
  -f 1 -wi 2 -i 5 -t 16
```

### Command: Worst-Case Test (1M cases)

```bash
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -p caseCount=1000000 \
  -f 1 -wi 3 -i 10 -t 16
```

---

## Expected Results

### Sample Output

```
Benchmark                                Mode  Cnt  Score    Error  Units
TaskExecutionLatencyBenchmark.completeWorkItemOnly avgt  25  3.456 ± 0.234  ms/op
TaskExecutionLatencyBenchmark.executeTask         avgt  25 11.234 ± 1.123  ms/op
TaskExecutionLatencyBenchmark.startWorkItemOnly   avgt  25  7.234 ± 0.678  ms/op
```

### Percentiles (Critical to Check)

```
p0.00:    5.000 ms/op
p50.00:   10.000 ms/op
p95.00:   15.000 ms/op    ← CHECK THIS (should be < 100ms)
p99.00:   25.000 ms/op    ← CHECK THIS (should be < 500ms)
p99.90:   50.000 ms/op
p99.99:   100.000 ms/op
p100.00:  250.000 ms/op   ← Outlier check (should be < 1s)
```

---

## Success Criteria

### PASS (All Green)
- p95 < 100ms at all scales
- p99 < 500ms
- Scaling ratio p95(1M) / p95(100K) < 1.2
- No regression vs baseline (new p95 ≤ 15.4ms)

**Action**: Accept results, update baseline if improved

### WARN (Investigate)
- p95 100-150ms
- p99 500-1000ms
- Scaling ratio 1.2-1.5
- Minor regression (15.4-20ms)

**Action**: Profile with JFR, analyze lock contention

### FAIL (Critical Regression)
- p95 > 150ms
- p99 > 1 second
- Scaling ratio > 1.5
- Major regression > 20ms

**Action**: Profile and fix before committing

---

## Interpreting Results

### If All Metrics Are Good

Congratulations! The YAWL engine meets performance targets. Update the baseline:

```bash
# Save current results
cp /tmp/task-execution-full.json \
   /home/user/yawl/test/resources/performance-baseline.json
```

### If p95 Is Higher Than Expected

Check:
1. **Which method is slow?** (`executeTask`, `startWorkItemOnly`, `completeWorkItemOnly`)
2. **Is it consistent across scales?** (all scales, or only 1M?)
3. **How much higher?** (10%, 50%, 100%+?)

Example diagnosis:
- All methods slow at 1M: Lock contention (profile with JFR)
- Only `completeWorkItemOnly` slow: Data marshaling issue
- Only `startWorkItemOnly` slow: Guard evaluation or task firing

### If Scaling Is Non-Linear

Example: p95(100K)=15ms, p95(1M)=100ms

**Indicates**: Critical bottleneck at scale

**Investigate**:
```bash
java -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=/tmp/profile.jfr \
  -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=1000000
```

Look for:
- ReentrantLock contention (YNetRunner)
- Synchronized list contention (WorkItemRepository)
- GC pause times > 10ms

---

## Performance Targets (from CLAUDE.md)

The YAWL performance specification defines these targets:

| Operation | Target |
|-----------|--------|
| Engine startup | < 60s |
| Case creation (p95) | < 500ms |
| Work item checkout (p95) | < 200ms |
| Work item checkin (p95) | < 300ms |
| Task transition | < 100ms |
| DB query (p95) | < 50ms |
| GC time | < 5% |
| Full GCs | < 10/hour |

**This benchmark measures task transition & execution path** (fire → complete).

---

## Optimization Options (If Needed)

### Quick Wins (No Code Changes)
1. Increase heap to 6GB (`-Xmx6g`)
2. Tune ZGC: `-XX:ZCollectionInterval=<millis>`
3. Test different thread counts: `-t 8` vs `-t 16` vs `-t 32`

### Medium-Term (Code Changes)
1. Replace `ReentrantLock` with `ReadWriteLock` in YNetRunner
2. Use concurrent collections (ConcurrentHashMap) in WorkItemRepository
3. Cache XPath expressions in task guard evaluation

### Long-Term (Architecture)
1. Implement lock-free data structures
2. Migrate to virtual threads (eliminate thread pool contention)
3. Distribute execution across multiple engine instances

---

## Key Files

### Benchmark Code
```
/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/
├── TaskExecutionLatencyBenchmark.java   ← Main benchmark
├── BenchmarkSpecFactory.java            ← Workflow specs
└── soak/BenchmarkReportGenerator.java   ← Report generation
```

### Documentation
```
/home/user/yawl/
├── TASK_EXECUTION_BENCHMARK_GUIDE.md    ← Step-by-step guide
├── TASK_EXECUTION_LATENCY_ANALYSIS.md   ← Full analysis framework
└── BENCHMARK_EXECUTION_SUMMARY.md       ← This file
```

### Engine Code Being Tested
```
/home/user/yawl/yawl-stateless/src/main/java/org/yawlfoundation/yawl/stateless/engine/
├── YNetRunner.java                      ← Task execution engine
├── YWorkItem.java                       ← Work item state
└── WorkItemRepository.java              ← Enabled items tracking
```

---

## Next Steps

1. **Execute**: Run the full benchmark suite (30 minutes)
   ```bash
   mvn clean verify -pl yawl-benchmark -DskipTests && \
   java -jar yawl-benchmark/target/benchmarks.jar \
     TaskExecutionLatencyBenchmark \
     -f 3 -wi 5 -i 25 -t 16 -o /tmp/results.json
   ```

2. **Analyze**: Check p95, p99, and scaling ratio
   ```bash
   # Extract key metrics
   jq '.[] | {benchmark, p95: .primaryMetric.scorePercentiles["95.0"], \
                          p99: .primaryMetric.scorePercentiles["99.0"]}' /tmp/results.json
   ```

3. **Decide**: PASS, WARN, or FAIL
   - All green? Update baseline and commit
   - Warnings? Profile with JFR and investigate
   - Red? Revert changes and optimize

4. **Document**: Record findings in performance-analysis-report.md

---

## Command Reference

```bash
# Full benchmark (all scales, 30 minutes)
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -f 3 -wi 5 -i 25 -t 16

# Quick test (100K only, 5 minutes)
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=100000 -f 1 -wi 2 -i 5 -t 16

# Single-scale test (1M only)
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=1000000 -f 1 -wi 3 -i 10 -t 16

# With GC logging
java -Xlog:gc*:file=/tmp/gc.log \
  -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -f 1 -wi 2 -i 5

# With JFR profiling
java -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=/tmp/profile.jfr \
  -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=1000000 -f 1 -wi 2 -i 5
```

---

## Checklist for Execution

- [ ] Clear JAVA_TOOL_OPTIONS: `unset JAVA_TOOL_OPTIONS`
- [ ] Verify Java 25: `java -version`
- [ ] Verify Maven 4.0+: `mvn -version`
- [ ] Build benchmark: `mvn clean verify -pl yawl-benchmark -DskipTests`
- [ ] Run full suite (30 min): `java -jar yawl-benchmark/target/benchmarks.jar ...`
- [ ] Check p95 at all scales
- [ ] Check p99 values
- [ ] Calculate scaling ratio: p95(1M) / p95(100K)
- [ ] Compare to baseline: new p95 vs 14.0ms (threshold: 15.4ms)
- [ ] Document results
- [ ] Update baseline if PASS
- [ ] File optimization ticket if WARN/FAIL

---

**Status**: Ready for Benchmark Execution  
**Documents**: TASK_EXECUTION_BENCHMARK_GUIDE.md, TASK_EXECUTION_LATENCY_ANALYSIS.md  
**Contact**: YAWL Performance Team
