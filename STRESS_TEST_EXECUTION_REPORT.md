# YAWL Conservative Load Stress Test — Execution Report

**Date**: 2026-02-28  
**Test Type**: Conservative Load (4 hours, 500 cases/sec)  
**Status**: READY FOR EXECUTION (blocked on environment fix)

---

## Executive Summary

This document provides a comprehensive plan for executing and analyzing YAWL's conservative load stress test to validate handling of 1M+ concurrent cases under normal, sustainable conditions.

### Key Metrics
- **Duration**: 4 hours (achievable in CI environment)
- **Load**: 500 cases/sec (Poisson-distributed)
- **Expected Total**: 7.2M cases created, 6.9M completed
- **Success Threshold**: >450 cases/sec sustained, <500 MB/hour heap growth

### Test Infrastructure Status
- LongRunningStressTest: ✓ Implemented
- MixedWorkloadSimulator: ✓ Implemented  
- BenchmarkMetricsCollector: ✓ Implemented
- CapacityBreakingPointAnalyzer: ✓ Implemented
- Maven Profile (soak-tests): ✓ Configured
- Configuration Files: ✓ Created

### Blockers
- **JAVA_TOOL_OPTIONS Environment Issue**: Empty option values breaking Java invocation
  - Symptom: `Error: Could not find or load main class #`
  - Impact: Cannot run Maven or direct Java commands
  - Solution: Clear/override JAVA_TOOL_OPTIONS before execution

---

## Test Design

### Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│              LongRunningStressTest                      │
│          (Main test orchestrator)                       │
└──────────┬──────────────────────────────────────────────┘
           │
    ┌──────┴────────────────────────────────────┐
    │                                           │
┌───▼─────────┐  ┌──────────────────────┐  ┌───▼───────┐
│ YStateless  │  │ MixedWorkload        │  │ Metrics   │
│ Engine      │  │ Simulator            │  │ Collector │
│             │  │ (POISSON events)     │  │           │
│ Real impl   │  │ 20% arrival          │  │ Samples   │
│ No mocks    │  │ 70% execution        │  │ every 2   │
│             │  │ 10% completion       │  │ minutes   │
└─────────────┘  └──────────────────────┘  └───────────┘
                       │
                       │ WorkloadEvent stream
                       ▼
    ┌──────────────────────────────────┐
    │ Case launcher (virtual threads)   │
    │ - nextEvent() → case_arrival?     │
    │   → launchCase(spec)              │
    │ - Complete enabled work items     │
    │ - Record completion in metrics    │
    └──────────────────────────────────┘
```

### Test Flow

1. **Initialization (T=0)**
   - Load YStatelessEngine with realistic specifications
   - Initialize MixedWorkloadSimulator (500 cases/sec Poisson)
   - Start BenchmarkMetricsCollector background thread
   - Start CapacityBreakingPointAnalyzer
   - Start LatencyDegradationAnalyzer

2. **Main Loop (T=0 to T=14400 sec)**
   - While current_time < end_time:
     - Get next event from workload simulator
     - If case_arrival: launch new case with random spec
     - If task_execution: execute next enabled work item
     - If case_completion: complete case
     - Metrics collector samples JVM state every 2 minutes
     - Breaking point analyzer checks for throughput cliffs

3. **Cleanup (T>14400)**
   - Shut down case executor (wait for running tasks)
   - Stop metrics collection
   - Interrupt background analyzer threads
   - Collect final metrics

4. **Analysis**
   - Calculate heap growth rate (MB/hour)
   - Verify throughput sustained (>450 cases/sec)
   - Check for breaking points (>30% drop)
   - Validate GC pause times (p99 <100ms)
   - Verify thread count remains bounded

### Expected Behavior

#### Case Lifecycle
```
Case Created (event = case_arrival)
  → UUID: soak-case-<uuid>
  → Spec: random from loaded specifications
  → Runner: YNetRunner instance
  ↓
Task Execution Loop (while enabled work items)
  → For each YWorkItem:
    - Simulate execution time (exponential distribution)
    - Call engine.startWorkItem()
    - Call engine.completeWorkItem()
    - totalTasksExecuted++
  ↓
Case Completed
  → casesCompleted++
  → Record in metrics
```

#### Workload Pattern (20:70:10 mix)
```
Time window [T, T+dT]:
  - 20% of events: case_arrival (Poisson λ=500/sec)
  - 70% of events: task_execution (Exponential median=100ms)
  - 10% of events: case_completion

Example 100 events:
  Event 1: case_arrival (delay=2ms) → launch case
  Event 2: task_execution (delay=85ms) → sleep, then complete item
  Event 3: task_execution (delay=150ms)
  Event 4: case_completion (delay=45ms)
  ...
  Event 20: case_arrival (delay=3ms) → launch case
```

---

## Configuration Details

### JVM Configuration
```bash
-Xms8g -Xmx8g                    # 8GB heap
-XX:+UseZGC                      # Low-pause GC
-XX:+UseCompactObjectHeaders     # Java 25: save 4-8 bytes/object
-XX:+DisableExplicitGC           # Prevent System.gc() calls
-XX:+AlwaysPreTouch              # Pre-allocate heap pages
```

### Test Parameters
```properties
soak.duration.hours=4                   # 4 hours
soak.rate.cases.per.second=500          # Conservative load
soak.rate.tasks.per.second=2500         # Task rate
soak.load.profile=POISSON               # Realistic arrivals
soak.metrics.sample.interval.min=2      # 2-min samples
soak.throughput.cliff.percent=30        # Breaking point threshold
soak.gc.pause.warning.ms=100            # GC pause warning
soak.heap.warning.threshold.mb=500      # Heap growth warning
```

### Success Criteria (Hard Limits)
```
Throughput:        >450 cases/sec (90% of 500)  ✓ = PASS
Heap growth:       <500 MB/hour                 ✓ = PASS
GC pause p99:      <100ms (typical <10ms)       ✓ = PASS
Thread count:      <10,000 (typical <1,000)     ✓ = PASS
Breaking point:    Not detected                 ✓ = PASS
Latency p99:       <1 second                    ✓ = PASS
```

---

## Execution Instructions

### Environment Setup

```bash
# 1. Clear problematic Java options
unset JAVA_TOOL_OPTIONS
export _JAVA_OPTIONS="-Dhttp.proxyUser= -Dhttp.proxyPassword="

# 2. Verify Java 25+
java -version  # Expect: openjdk 25.0.2

# 3. Verify system resources
free -h        # Expect: 12+ GB RAM free
df -h /        # Expect: 10+ GB disk free

# 4. Navigate to project
cd /home/user/yawl
```

### Maven Execution (Preferred)

```bash
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 \
  -Dsoak.rate.cases.per.second=500 \
  -Dsoak.rate.tasks.per.second=2500 \
  -Dsoak.load.profile=POISSON \
  -Dsoak.metrics.sample.interval.min=2 \
  -Dsoak.throughput.cliff.percent=30 \
  -Dsoak.gc.pause.warning.ms=100 \
  -Dsoak.heap.warning.threshold.mb=500 \
  -DargLine="-Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders \
    -XX:+DisableExplicitGC -XX:+AlwaysPreTouch"
```

### Alternative: Direct JUnit Execution

```bash
# If Maven fails, compile and run JUnit directly
mvn clean package -pl yawl-benchmark -DskipTests -q

java -Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders \
  -cp "yawl-benchmark/target/classes:yawl-benchmark/target/test-classes:$(mvn dependency:build-classpath -q)" \
  org.junit.platform.console.ConsoleLauncher \
  --scan-classpath \
  --include-classname='.*LongRunningStressTest' \
  --config junit.jupiter.execution.parallel.enabled=true
```

### Monitoring During Test

```bash
# Terminal 1: Tail metrics (update every 2 minutes)
tail -f metrics-*.jsonl | jq '.[] | {heap: .heap_used_mb, throughput: .throughput_cases_per_sec, threads: .thread_count}'

# Terminal 2: Monitor process
jps -lm | grep LongRunning
jstat -gc -h 10 <pid> 1000  # 1-second intervals

# Terminal 3: System load
watch -n 5 'free -h && echo "---" && top -bn1 | head -15'
```

---

## Expected Outputs

### 1. Metrics JSONL (appended every 2 minutes)
```json
{
  "timestamp": 1709088000123,
  "heap_used_mb": 2456,
  "heap_committed_mb": 8192,
  "heap_max_mb": 8192,
  "gc_collection_count": 12,
  "gc_collection_time_ms": 450,
  "thread_count": 512,
  "peak_thread_count": 2048,
  "cases_processed": 600000,
  "throughput_cases_per_sec": 485.5
}
```
Expected: ~120 samples over 4 hours

### 2. Latency Percentiles (every 100K cases)
```json
{
  "timestamp": 1709088300000,
  "cases_completed": 100000,
  "percentiles": {
    "p50_ms": 45,
    "p95_ms": 230,
    "p99_ms": 850,
    "p99_9_ms": 1500,
    "max_ms": 2300
  }
}
```
Expected: ~60-70 files (one per 100K cases, up to 7M total)

### 3. Breaking Point Analysis (if detected)
```json
{
  "breaking_point_detected": true,
  "timestamp": 1709089200000,
  "elapsed_seconds": 1200,
  "cases_processed": 600000,
  "throughput_before_cliff": 485.0,
  "throughput_after_cliff": 315.0,
  "cliff_percentage": 35.0,
  "analysis": "GC pause >500ms triggered cascading latency..."
}
```

### 4. Console Output
```
======================================
SOAK TEST RESULTS
======================================
Duration: PT4H0M15.234S
Cases Created: 7,237,500
Cases Completed: 6,925,600
Total Tasks Executed: 34,628,000
Throughput: 478.35 cases/sec
Heap Growth: 245.60 MB/hour
Final Thread Count: 512
Breaking Point Detected: false
Heap Exhaustion: false
======================================
```

---

## Analysis & Interpretation

### Throughput Analysis
```
Expected: 500 cases/sec (ideal)
Acceptable: 450+ cases/sec (90%, allows POISSON variance)
Warning: 400-449 cases/sec (80%, inspect GC)
Critical: <400 cases/sec (80% drop, breaking point detected)
```

### Heap Growth Analysis
```
Normal: 50-100 MB/hour
Acceptable: 100-500 MB/hour (no unbounded leak)
Warning: 500-1000 MB/hour (investigate retention)
Critical: >1000 MB/hour (memory leak, OOM imminent)
```

### GC Pause Analysis
```
Excellent: <10ms p99 (ZGC typical)
Good: 10-50ms p99
Acceptable: 50-100ms p99
Warning: 100-500ms p99
Critical: >500ms p99 (cascading effect possible)
```

### Thread Count Analysis
```
Healthy: <1000 active threads
Acceptable: 1000-5000 (still pooled)
Warning: 5000-10000 (thread creation slowing)
Critical: >10000 (thread explosion)
```

---

## Regression Detection

### Baseline Comparison

Before making changes to the engine:
```bash
# Capture current commit
git log --oneline -1 > /tmp/baseline-commit.txt

# Run baseline test
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 ... 2>&1 | tee baseline-test.log

# Save baseline metrics
cp metrics-*.jsonl baseline-metrics.jsonl
```

After changes:
```bash
# Run current test
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 ... 2>&1 | tee current-test.log

# Compare key metrics
echo "=== BASELINE vs CURRENT ===" > /tmp/regression-report.txt
echo "Throughput baseline: $(jq '.throughput_cases_per_sec' baseline-metrics.jsonl | tail -1)" >> /tmp/regression-report.txt
echo "Throughput current:  $(jq '.throughput_cases_per_sec' metrics-*.jsonl | tail -1)" >> /tmp/regression-report.txt
```

**Fail if:**
- Throughput degradation >10% (e.g., 485→437 cases/sec)
- Heap growth >2× baseline
- New breaking point detected

---

## Failure Modes & Recovery

| Failure | Symptom | Root Cause | Recovery |
|---------|---------|-----------|----------|
| OOM | OutOfMemoryError, JVM terminates | Heap too small, memory leak | Increase -Xmx, profile heap dump |
| Deadlock | Thread count frozen, test hangs | Lock cycle in engine | Thread dump, fix synchronization |
| Throughput cliff | >30% drop detected | GC pause, starvation | Profile with JFR, optimize hot path |
| Thread explosion | >10,000 threads | Virtual thread pool not bounded | Audit ExecutorService creation |
| Heap spike | +2GB in 1 hour | Object retention, leak | Capture heap dump, trace allocations |

---

## Success Report Template

```markdown
# YAWL Conservative Load Stress Test — PASSED

Configuration: 4-hour soak, 500 cases/sec POISSON load
Environment: Java 25.0.2 LTS, ZGC, 8GB heap, compact object headers
Date: 2026-02-28

## Test Results

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Duration | 4 hours | 4:00:15 | ✓ PASS |
| Cases Created | 7.2M | 7,237,500 | ✓ PASS |
| Cases Completed | 6.9M | 6,925,600 | ✓ PASS |
| Throughput | >450 c/s | 478.3 c/s | ✓ PASS |
| Heap Growth | <500 MB/h | 245.6 MB/h | ✓ PASS |
| GC Pause p99 | <100ms | 8.2ms | ✓ PASS |
| Thread Count | <10,000 | 512 | ✓ PASS |
| Breaking Point | Not detected | Not detected | ✓ PASS |
| Latency p99 | <1 sec | 850ms | ✓ PASS |

## Conclusion

System demonstrates stable, linear scaling under conservative load.
Successfully validates 1M+ concurrent case handling capacity.
No memory leaks, GC degradation, or breaking points detected.

Ready for production 24-hour soak test with aggressive load profile.
```

---

## Troubleshooting

### Issue: JAVA_TOOL_OPTIONS breaking Java execution
**Symptom**: `Error: Could not find or load main class #`  
**Fix**: 
```bash
unset JAVA_TOOL_OPTIONS
export _JAVA_OPTIONS="-Dhttp.proxyUser= -Dhttp.proxyPassword="
mvn ...
```

### Issue: Test times out after 4+ hours
**Symptom**: Maven hangs, no output  
**Cause**: Case launcher thread not respecting endTime  
**Fix**: Check if current_time properly incremented, verify system clock

### Issue: Metrics file not created
**Symptom**: No metrics-*.jsonl after test completes  
**Cause**: BenchmarkMetricsCollector.start() not called or failed  
**Fix**: Check for exceptions in System.err, verify file permissions

### Issue: Breaking point detected early
**Symptom**: Analysis shows cliff at T+30min  
**Cause**: GC tuning suboptimal for this load  
**Fix**: Try `-XX:ZCollectionInterval=180000` to reduce frequency

---

## References

- Test Infrastructure: `/home/user/yawl/yawl-benchmark/`
- LongRunningStressTest: `/home/user/yawl/yawl-benchmark/src/test/java/.../soak/LongRunningStressTest.java`
- Configuration: `/home/user/yawl/soak-test-config.properties`
- Plan: `/home/user/yawl/CONSERVATIVE_STRESS_TEST_PLAN.md`
- This Report: `/home/user/yawl/STRESS_TEST_EXECUTION_REPORT.md`

---

## Sign-Off

**Status**: READY FOR EXECUTION  
**Prepared**: 2026-02-28  
**Test Lead**: YAWL Performance Specialist  
**Last Updated**: 2026-02-28

Comprehensive infrastructure in place. Awaiting Maven environment fix and test execution.
