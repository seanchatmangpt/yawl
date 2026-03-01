# Task Execution Latency Analysis & Performance Roadmap

**Document**: YAWL v6.0.0 Performance Analysis  
**Date**: 2026-02-28  
**Analyst**: YAWL Performance Specialist  
**Status**: Ready for Benchmark Execution  

---

## Executive Summary

The task execution latency benchmark measures the **critical path for case throughput** in YAWL workflow engine. This document provides:

1. **Benchmark Overview**: What is being measured and why it matters
2. **Expected Performance**: Baseline targets and scaling predictions
3. **Execution Instructions**: How to run the benchmark
4. **Analysis Framework**: How to interpret results and detect regressions
5. **Optimization Roadmap**: Next steps if performance degrades

---

## Part 1: Benchmark Overview

### Mission

Measure the latency of the **task execution lifecycle** across case scales:
- **100K cases**: Baseline (minimal contention)
- **500K cases**: Moderate scale (5× baseline)
- **1M cases**: Maximum production scale (10× baseline)

### What Is "Task Execution"?

In YAWL workflow terminology:

```
Case Instance
  ↓
Work Item (enabled, ready to execute)
  ↓
Participant starts task (fire) ← STARTPOINT
  ↓
Task logic executes
  ↓
Participant completes task (returns data) ← ENDPOINT
  ↓
Next task enabled (or case completes)
```

**The benchmark measures**: Fire → Completion (STARTPOINT to ENDPOINT)

### Why This Matters

1. **Throughput**: Case throughput is limited by task execution latency
   - If task exec = 100ms, max throughput = 10 tasks/sec
   - At 1M cases with 70% task execution rate: need sub-100ms latency

2. **Scaling**: Non-linear latency growth indicates lock contention
   - Linear: p95(1M) ≈ p95(100K) ✓ Good
   - Quadratic: p95(1M) >> p95(100K) ✗ Lock bottleneck

3. **Lock Contention**: Dominant cost at scale
   - YNetRunner state machine transitions
   - WorkItemRepository enabled item tracking
   - Task firing + completion state updates

---

## Part 2: Expected Performance Baselines

### Current Baseline

From `/home/user/yawl/test/resources/performance-baseline.json`:

```json
{
  "p50Ms": 11.0,
  "p95Ms": 14.0,      ← Regression threshold: 14 × 1.1 = 15.4ms
  "p99Ms": 17.0,
  "throughputOpsPerSec": 850.0,
  "capturedAt": "2026-02-27T00:00:00Z"
}
```

### Performance Targets (from CLAUDE.md)

| Operation | Target | Notes |
|-----------|--------|-------|
| **Task execution** (p95) | < 100ms | Fire → complete cycle |
| **Task execution** (p99) | < 500ms | Acceptable tail latency |
| **Work item checkout** (p95) | < 200ms | Before fire |
| **Work item checkin** (p95) | < 300ms | After complete |
| **Task transition** | < 100ms | State machine overhead |

### Scaling Predictions

Based on YNetRunner architecture (ReentrantLock-based synchronization):

#### 100K Cases (Baseline)
- **Setup throughput**: ~100K cases/sec
- **Lock contention**: Minimal (16 concurrent threads × 1M total cases)
- **Expected p95**: 11-15ms (baseline)
- **Expected p99**: 14-20ms
- **Heap usage**: ~800MB for 100K × 2 work items

#### 500K Cases (5× Baseline)
- **Setup throughput**: ~50K cases/sec
- **Lock contention**: Moderate
- **Expected p95**: 12-20ms (+20-30% vs baseline)
- **Expected p99**: 18-30ms
- **Heap usage**: ~3.5GB (approach heap limit)

#### 1M Cases (10× Baseline)
- **Setup throughput**: ~30K cases/sec
- **Lock contention**: High
- **Expected p95**: 15-30ms (+50-100% vs baseline, but still linear)
- **Expected p99**: 25-50ms
- **Heap usage**: ~7GB (exceeds 4GB heap → GC pressure)

### Scaling Ratio Analysis

**Healthy scaling** (linear):
- Ratio = p95(1M) / p95(100K)
- **Target**: < 1.2 (20% degradation acceptable)
- **Acceptable**: < 1.5 (50% degradation, investigate)
- **Unacceptable**: > 1.5 (indicates lock bottleneck)

---

## Part 3: Benchmark Specification

### Workflow Specification

```xml
<!-- BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC -->
<net id="main">
  <localVariable>...</localVariable>
  <task id="task_start" />
  <task id="task_a" />
  <task id="task_b" />
  <task id="task_join" />
  <task id="task_end" />
  
  <!-- Flow: start → [AND-split] → task_a, task_b → [AND-join] → end -->
  <flow source="task_start" target="task_a" />
  <flow source="task_start" target="task_b" />
  <flow source="task_a" target="task_join" />
  <flow source="task_b" target="task_join" />
  <flow source="task_join" target="task_end" />
</net>
```

**Why this spec?**
- 2 parallel tasks per case (good stress test)
- AND-join requires both tasks to complete (realistic workflow)
- No data variables (pure engine overhead, no query evaluation)

### Benchmark Methods

#### 1. executeTask() - Full Cycle

```java
@Benchmark
public void executeTask(Blackhole blackhole) throws Exception {
    YWorkItem item = taskWorkItems.get(idx % size);
    engine.startWorkItem(item);          // Fire
    engine.completeWorkItem(item, ...);  // Complete
}
```

**Measures**:
- Lock contention (fire operation)
- State machine transitions
- Data marshaling (output)

**Expected p95**: 10-30ms depending on scale

#### 2. startWorkItemOnly() - Fire Operation

```java
@Benchmark
public void startWorkItemOnly(Blackhole blackhole) throws Exception {
    YWorkItem item = taskWorkItems.get(idx % size);
    engine.startWorkItem(item);  // Just fire
}
```

**Measures**: Task enable latency, guard evaluation, lock contention

**Expected p95**: 5-15ms

#### 3. completeWorkItemOnly() - Completion Operation

```java
@Benchmark
public void completeWorkItemOnly(Blackhole blackhole) throws Exception {
    YWorkItem item = taskWorkItems.get(idx % size);
    engine.completeWorkItem(item, ...);  // Just complete
}
```

**Measures**: Data marshaling, state update, persistence

**Expected p95**: 3-10ms

### JVM Configuration

```
-Xms4g -Xmx4g                              # 4GB heap (matches 1M cases)
-XX:+UseZGC                                # Zero GC pauses
-XX:+ZGenerational                         # Young/old generation separation
-XX:+UseCompactObjectHeaders               # 8-byte headers vs 16
-XX:+DisableExplicitGC                     # Prevent full GCs
-XX:+AlwaysPreTouch                        # Pre-fault pages (deterministic behavior)
```

### Test Configuration

```
Threads:        16 (8 cores × 2)
Warmup iters:   5 × 2 seconds = 10 seconds
Measurement:    25 × 2 seconds = 50 seconds per scale
Forks:          3 JVMs (reduce JIT bias)
Total time:     ~30 minutes (all scales + JVM startup)
```

---

## Part 4: Running the Benchmark

### Step 1: Prepare Environment

```bash
cd /home/user/yawl

# Clear JAVA_TOOL_OPTIONS (can corrupt command line)
unset JAVA_TOOL_OPTIONS
export JAVA_TOOL_OPTIONS=""

# Verify tools
java -version          # Should be Java 25
mvn -version           # Should be Maven 4.0+

# Build benchmark module
mvn clean compile -pl yawl-benchmark -am -DskipTests
```

### Step 2: Run Full Benchmark Suite

```bash
# Compile JAR with all dependencies
mvn clean verify -pl yawl-benchmark -DskipTests

# Run benchmark (all three scales: 100K, 500K, 1M)
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -f 3 \
  -wi 5 \
  -i 25 \
  -t 16 \
  -o /tmp/task-execution-full.json
```

**Duration**: ~30 minutes

**Output**: JSON with detailed metrics and percentiles

### Step 3: Quick Smoke Test (5 minutes)

For development/quick feedback:

```bash
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -p caseCount=100000 \
  -f 1 -wi 2 -i 5 -t 16
```

### Step 4: Single-Scale Test (Worst Case)

To test 1M cases specifically:

```bash
java -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark \
  -p caseCount=1000000 \
  -f 1 -wi 3 -i 10 -t 16
```

---

## Part 5: Interpreting Results

### JMH Output Format

```
Benchmark                             Mode  Cnt   Score     Error  Units
TaskExecutionLatencyBenchmark.executeTask avgt   25  42.123 ±  1.234  ms/op
```

- **Score**: Average latency (in ms/op = milliseconds per operation)
- **Error**: Standard deviation (±)
- **Units**: ms/op

### Percentiles Output

Look for histogram output:

```
p0.00:    10.000 ms/op
p50.00:   40.000 ms/op
p95.00:   85.000 ms/op    ← CRITICAL
p99.00:  120.000 ms/op    ← CRITICAL
p99.90:  250.000 ms/op
p99.99:  500.000 ms/op
p100.00: 800.000 ms/op    ← Outlier check
```

### Parsing JSON Output

```bash
# Extract all metrics
jq '.[] | {benchmark: .benchmark, mean: .primaryMetric.mean, p95: .primaryMetric.scorePercentiles["95.0"]}' /tmp/task-execution-full.json

# Extract percentiles for one benchmark
jq '.[] | select(.benchmark | contains("executeTask")) | .primaryMetric.scorePercentiles' /tmp/task-execution-full.json
```

---

## Part 6: Success Criteria & Regression Detection

### PASS: All Green

- p95 < 100ms at all scales
- p99 < 500ms
- No spikes > 1 second
- Linear scaling: p95(1M) / p95(100K) < 1.2
- No regression vs baseline: new p95 ≤ 15.4ms

**Action**: Accept and update baseline if intentional improvement

### WARN: Investigate

- p95 100-150ms (moderate contention)
- p99 500-1000ms (tail latency at limit)
- Scaling ratio 1.2-1.5 (non-linear but acceptable)
- p95 regression 15-20ms (minor regression, < 50%)

**Action**: Profile with JFR, analyze lock contention patterns

### FAIL: Regression Confirmed

- p95 > 150ms (severe bottleneck)
- p99 > 1 second (unacceptable)
- Spikes > 2 seconds (potential deadlock)
- Scaling ratio > 1.5 (non-linear degradation)
- p95 regression > 20ms (major regression, > 50%)

**Action**: Profile and fix before committing

---

## Part 7: Regression Root Cause Analysis

### If p95 Jumps at Specific Scale

Example: p95(100K)=15ms, p95(500K)=15ms, p95(1M)=150ms

**Indicates**: Threshold at 1M cases (e.g., OutOfMemoryError edge, GC trigger)

**Investigate**:
```bash
java -Xlog:gc*:file=/tmp/gc-1m.log \
  -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=1000000 -f 1 -wi 2 -i 5
```

Look for:
- Full GC pause times > 10ms
- Metaspace pressure
- Out-of-memory errors in the background

### If p95 Gradually Increases

Example: p95(100K)=15ms, p95(500K)=40ms, p95(1M)=80ms

**Indicates**: Lock contention (linear or sublinear scaling)

**Investigate with JFR**:
```bash
java -XX:+FlightRecorder \
  -XX:StartFlightRecording=filename=/tmp/profile.jfr,duration=60s \
  -jar yawl-benchmark/target/benchmarks.jar \
  TaskExecutionLatencyBenchmark -p caseCount=1000000 -f 1 -wi 2 -i 5

# Analyze with JDK Mission Control
jmc /tmp/profile.jfr
```

Look for:
- Hot locks (ReentrantLock.lock())
- High contention count
- Lock wait time (Time waiting for lock)

### If Only startWorkItemOnly() Is Slow

**Indicates**: Task fire operation (YNetRunner.fire() or guard evaluation)

**Check**:
1. Guard evaluation performance (XPath expressions, conditions)
2. Task state transitions
3. Work item repository synchronization

### If Only completeWorkItemOnly() Is Slow

**Indicates**: Completion overhead (data marshaling, persistence)

**Check**:
1. XML serialization of output data
2. Database updates for work item
3. Hibernate lazy loading

### If executeTask() Is Slow But Components Are Fast

**Indicates**: Interactions between fire and complete

**Check**:
1. Task state conflicts (fire → complete → fire again)
2. Work item reuse across threads
3. Lock hold time (fire doesn't release lock until complete)

---

## Part 8: Optimization Roadmap

### Quick Wins (No Code Changes)

1. **JVM Tuning**:
   - Increase heap to 6GB if scaling to 2M cases
   - Tune ZGC pause threshold: `-XX:ZCollectionInterval=<millis>`

2. **Thread Count**:
   - Test with `-t 8` vs `-t 16` vs `-t 32`
   - Lock contention may change with thread count

3. **Warm-up**:
   - Increase `-wi` (warmup iterations) to 10
   - JIT may not fully optimize task firing at scale

### Medium-Term Optimizations (Code Changes)

1. **YNetRunner Lock Optimization**:
   - Use `ReadWriteLock` for fire (read lock) vs state update (write lock)
   - Consider lock-free data structures for work item repository

2. **WorkItemRepository**:
   - Use concurrent collections (ConcurrentHashMap) instead of synchronized list
   - Implement copy-on-write for enabled items list

3. **Task Guard Evaluation**:
   - Cache XPath expressions
   - Pre-compile conditions at startup

### Long-Term Architecture

1. **Lock-Free Task Execution**:
   - Compare-and-swap (CAS) for state transitions
   - Avoid mutex entirely if possible

2. **Virtual Thread Migration**:
   - Use virtual threads per task (Java 21+)
   - Eliminates thread pool contention

3. **Distributed Execution**:
   - Shard cases across multiple engine instances
   - Eliminate global YNetRunner lock

---

## Part 9: Files and References

### Benchmark Code

```
/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/
├── TaskExecutionLatencyBenchmark.java       ← Main benchmark (THIS DOCUMENT)
├── BenchmarkSpecFactory.java                ← Workflow specs
├── soak/
│   ├── BenchmarkMetricsCollector.java       ← Metrics collection utilities
│   ├── BenchmarkReportGenerator.java        ← Report generation
│   └── LatencyDegradationAnalyzer.java      ← Scaling analysis
└── regression/
    └── BaselineManager.java                 ← Baseline comparison
```

### Engine Implementation

```
/home/user/yawl/yawl-stateless/src/main/java/org/yawlfoundation/yawl/stateless/engine/
├── YNetRunner.java                          ← Task execution engine
├── YWorkItem.java                           ← Work item state
└── WorkItemRepository.java                  ← Enabled items tracking

/home/user/yawl/yawl-elements/src/main/java/org/yawlfoundation/yawl/elements/
└── YTask.java                               ← Task definition + firing
```

### Performance Documentation

```
/home/user/yawl/
├── CLAUDE.md                                ← Performance targets (Ψ OBSERVATORY section)
├── docs/reference/performance-targets.md    ← Build + engine targets
└── test/resources/performance-baseline.json ← Current baseline
```

---

## Part 10: Decision Tree

### After Benchmark Execution

```
Does p95 < 100ms at all scales?
├─ YES (ALL green)
│  └─ → Accept results, update baseline if improved
├─ NO (p95 > 100ms)
│  └─ Does p95 scale linearly (ratio < 1.2)?
│     ├─ YES (acceptable, investigate)
│     │  └─ → Profile with JFR
│     │     ├─ High lock contention? → Implement lock-free structures
│     │     ├─ GC pauses > 10ms? → Increase heap to 6GB
│     │     └─ Task guard evals slow? → Cache XPath expressions
│     └─ NO (non-linear scaling)
│        └─ → Critical regression!
│           └─ → Revert recent changes or optimize YNetRunner
```

---

## Summary Table

| Metric | Target | How to Measure | Success Threshold |
|--------|--------|----------------|-------------------|
| **p95 latency** | < 100ms | Average of p95 across scales | All scales < 100ms |
| **p99 latency** | < 500ms | 99th percentile | < 500ms at all scales |
| **Scaling ratio** | < 1.2 | p95(1M) / p95(100K) | Linear growth only |
| **Lock contention** | Minimal | JFR profile, lock time | < 30% total time |
| **GC pauses** | < 1ms | gc.log analysis | ZGC only, no Full GC |
| **Throughput** | 850+ ops/sec | Score in JMH output | 800+ ops/sec minimum |

---

**Benchmark Ready**: Execute using instructions in Part 4  
**Questions**: See TASK_EXECUTION_BENCHMARK_GUIDE.md for detailed execution steps
