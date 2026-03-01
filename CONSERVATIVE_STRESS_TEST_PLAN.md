# YAWL Conservative Load Stress Test Plan

## Executive Summary
Execute a conservative load stress test to validate YAWL's ability to handle 1M concurrent active cases under normal, sustainable conditions.

**Test Configuration:**
- Duration: 4 hours (achievable in test environment)
- Case creation rate: 500 cases/sec (conservative)
- Load profile: POISSON (realistic, variable arrival times)
- Workload mix: 20% creation, 70% execution, 10% completion
- JVM Heap: 8GB with ZGC + compact object headers

**Expected Total Cases:**
- Total cases created: 500 cases/sec × 14,400 sec = 7.2M cases
- This exceeds 1M concurrent active cases target

## Test Objectives

### Primary Questions
1. **Can we handle 1M concurrent active cases?** 
   - Yes, if throughput sustained >450 cases/sec during 4 hours
   - With conservative 500 cases/sec, this provides 10% headroom

2. **How does latency degrade under realistic mixed workload?**
   - P99 latency should remain <1 second
   - Heap growth should be <500 MB/hour (no unbounded leak)

3. **What's case creation throughput at scale?**
   - Expect >450 cases/sec (90% of target with POISSON variance)
   - Breaking point not expected at 500 cases/sec

## Test Configuration

### JVM Settings
```bash
-Xms8g -Xmx8g                          # 8GB heap (256MB + 8GB = total ~2-4GB usable)
-XX:+UseZGC                            # Low-pause GC (<10ms typical)
-XX:+UseCompactObjectHeaders           # Save 4-8 bytes per object (~5% improvement)
-XX:+DisableExplicitGC                 # Ignore System.gc() calls
-XX:+AlwaysPreTouch                    # Prevent JIT allocation pauses
```

### Test Parameters
```properties
soak.duration.hours=4                  # 4 hours = 14400 seconds
soak.rate.cases.per.second=500         # Conservative load
soak.rate.tasks.per.second=2500        # Tasks per second
soak.load.profile=POISSON              # Realistic arrivals
soak.metrics.sample.interval.min=2     # 2-min samples = ~120 snapshots
soak.throughput.cliff.percent=30       # 30% drop = breaking point
soak.gc.pause.warning.ms=100           # Warn on GC >100ms
soak.heap.warning.threshold.mb=500     # Warn on >500MB/hour growth
```

## Success Criteria

All criteria must pass for test to be considered successful:

| Criterion | Target | Justification |
|-----------|--------|---------------|
| **No crashes** | OOM, deadlock, exception free | System must be stable |
| **Heap growth** | <500 MB/hour | Indicates no unbounded leak |
| **GC pause (p99)** | <100ms (ZGC typical <10ms) | Meets performance target |
| **Throughput sustained** | >450 cases/sec (90% of 500) | Validates 1M case capacity |
| **Thread growth** | <10,000 active threads | Virtual threads must be pooled |
| **Latency p99** | <1 second | Task completion time acceptable |
| **No breaking point** | Throughput cliff not detected | System scales linearly |

## Expected Outputs

### 1. Metrics JSONL Stream
```
metrics-<timestamp>.jsonl              # 2-min samples (~120 lines)
  - heap_used_mb
  - heap_committed_mb
  - gc_collection_count
  - gc_collection_time_ms
  - thread_count
  - peak_thread_count
  - cases_processed
  - throughput_cases_per_sec
```

### 2. Latency Percentiles
```
latency-percentiles-<timestamp>.json   # Every 100K cases
  - p50_ms
  - p95_ms
  - p99_ms
  - p99.9_ms
  - max_ms
```

### 3. Breaking Point Analysis
```
breaking-point-analysis-<timestamp>.json  # If detected
  - cliff_detected: true/false
  - cliff_percentage: 30.0
  - throughput_before_cliff: 450.0
  - throughput_after_cliff: 315.0
  - timestamp: when detected
```

## Execution Command

```bash
cd /home/user/yawl

# Option 1: Maven (preferred, if environment fixed)
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 \
  -Dsoak.rate.cases.per.second=500 \
  -Dsoak.rate.tasks.per.second=2500 \
  -Dsoak.load.profile=POISSON \
  -Dsoak.metrics.sample.interval.min=2 \
  -Dsoak.throughput.cliff.percent=30 \
  -Dsoak.gc.pause.warning.ms=100 \
  -Dsoak.heap.warning.threshold.mb=500 \
  -DargLine="-Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders"

# Option 2: JUnit directly (if Maven unavailable)
java -Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders \
  -cp yawl-benchmark/target/test-classes:yawl-benchmark/target/classes:$(mvn dependency:build-classpath -q) \
  org.junit.platform.console.ConsoleLauncher \
  --scan-classpath --include-classname=".*LongRunningStressTest"
```

## Architecture & Design

### Key Components

1. **LongRunningStressTest** (`yawl-benchmark/src/test/java/...`)
   - Main test class
   - Real YStatelessEngine (no mocks)
   - Chicago TDD style (integration tests with real components)
   - Configurable via system properties

2. **MixedWorkloadSimulator**
   - Generates Poisson-distributed case arrivals
   - Exponential task execution times
   - 20% creation, 70% execution, 10% completion mix
   - Thread-safe with ThreadLocalRandom

3. **BenchmarkMetricsCollector**
   - Samples JVM MXBeans every 2 minutes
   - Writes append-only JSONL stream
   - Tracks heap growth, GC pauses, thread count
   - Background daemon thread

4. **CapacityBreakingPointAnalyzer**
   - Detects throughput cliffs (>30% drop)
   - Compares consecutive measurement windows
   - Generates analysis report on detection

5. **LatencyDegradationAnalyzer**
   - Samples latency every 100K cases
   - Calculates p50/p95/p99/p99.9 percentiles
   - Detects increasing degradation over time

## Failure Modes & Recovery

### OOM Exception
- **Cause**: Unbounded memory growth, heap too small
- **Detection**: JVM terminates with OutOfMemoryError
- **Recovery**: Increase -Xmx, investigate memory leak in YAWL engine

### Deadlock
- **Cause**: Circular lock dependencies in concurrent code
- **Detection**: Test times out, thread count frozen
- **Recovery**: Analyze thread dump, fix synchronization logic

### Throughput Cliff
- **Cause**: GC pauses, thread starvation, resource contention
- **Detection**: CapacityBreakingPointAnalyzer detects >30% drop
- **Recovery**: Profile with JFR, optimize hot paths

### Thread Explosion
- **Cause**: Virtual thread pool not bounded, creating millions
- **Detection**: Thread count >10,000
- **Recovery**: Check ExecutorService configuration, limit pool size

## Performance Targets (From Task Header)

These are the targets we're validating:

| Metric | Target | Notes |
|--------|--------|-------|
| Engine startup | <60s | Not tested in soak (startup once) |
| Case creation (p95) | <500ms | Should see <100ms in soak |
| Work item checkout (p95) | <200ms | Measured in test |
| Work item checkin (p95) | <300ms | Measured in test |
| Task transition | <100ms | Measured as latency p99 |
| DB query (p95) | <50ms | Measured indirectly in latency |
| GC time | <5% | ZGC achieves <1% typical |
| Full GCs | <10/hour | ZGC does concurrent GC mostly |

## Regression Detection

### Baseline
Before making changes, capture baseline metrics:
```bash
# Run with HEAD commit
git log --oneline -1
mvn test -pl yawl-benchmark -P soak-tests ... 
cp metrics-*.jsonl baseline-metrics.jsonl
```

### After Changes
```bash
# Run with modified code
mvn test -pl yawl-benchmark -P soak-tests ...
cp metrics-*.jsonl current-metrics.jsonl

# Compare
jq -s 'max_by(.throughput_cases_per_sec)' baseline-metrics.jsonl current-metrics.jsonl
```

**Fail if:** Throughput degradation >10% or heap growth >2× baseline

## Timeline

- **T+0 min**: Test starts, case launcher begins
- **T+30 min**: Metrics stabilize, initial metrics window complete
- **T+60 min**: First latency percentiles captured (100K cases)
- **T+120 min**: 50% of test duration, steady-state analysis
- **T+180 min**: 75% through, check for breaking point
- **T+240 min**: Test complete, results analysis

## Monitoring During Test

### Watch These Metrics
```bash
# Terminal 1: Tail metrics
tail -f metrics-*.jsonl | jq '{heap_used_mb, throughput_cases_per_sec}'

# Terminal 2: Monitor system
watch -n 5 'jps -lm | grep LongRunning'

# Terminal 3: GC events
jstat -gc -h 10 <pid> 1000
```

### Red Flags
- Heap growth spike (>2GB in 1 hour)
- GC pause >500ms
- Throughput drop >30%
- Thread count >5000
- Test timeout (>5 hours when target is 4)

## Analysis & Reporting

### Success Report Template
```
YAWL 1M Case Conservative Stress Test — PASSED

Configuration: 4-hour soak, 500 cases/sec, POISSON load
Environment: Java 25, ZGC, 8GB heap

Results:
  Duration: 4:00:00.123
  Cases Created: 7,200,100
  Cases Completed: 6,900,050
  Tasks Executed: 34,500,250
  Throughput: 478.3 cases/sec (95.7% of target)
  Heap Growth: 245 MB/hour (pass <500)
  GC Pauses: p99 8ms (pass <100)
  Thread Count: 512 (pass <10,000)
  Breaking Point: Not detected (pass)

Conclusion: System stable under conservative load.
Validates 1M concurrent case handling at scale.
```

### Failure Report Template
```
YAWL 1M Case Conservative Stress Test — FAILED

Configuration: 4-hour soak, 500 cases/sec, POISSON load
Failure: Throughput cliff detected at T+120min

Root Cause: GC pause >500ms triggered cascading effect
  - Baseline throughput: 485 cases/sec
  - Post-cliff throughput: 245 cases/sec (49% drop)
  - GC pause: 1.2 seconds (major collection)

Recovery Actions:
  1. Increase -XX:+UseZGC tuning
  2. Profile YNetRunner hot path
  3. Optimize work item repository query
  4. Re-run baseline comparison

Status: BLOCKED until root cause fixed
```

## References

- LongRunningStressTest: `/home/user/yawl/yawl-benchmark/src/test/java/.../soak/LongRunningStressTest.java`
- MixedWorkloadSimulator: `/home/user/yawl/yawl-benchmark/src/main/java/.../soak/MixedWorkloadSimulator.java`
- BenchmarkMetricsCollector: `/home/user/yawl/yawl-benchmark/src/main/java/.../soak/BenchmarkMetricsCollector.java`
- CapacityBreakingPointAnalyzer: `/home/user/yawl/yawl-benchmark/src/main/java/.../soak/CapacityBreakingPointAnalyzer.java`
- LatencyDegradationAnalyzer: `/home/user/yawl/yawl-benchmark/src/main/java/.../soak/LatencyDegradationAnalyzer.java`

---

**Status**: Test infrastructure ready, execution blocked on Maven environment issue

**Next Steps**:
1. Fix JAVA_TOOL_OPTIONS environment issue (empty option values breaking Java)
2. Build yawl-benchmark module with clean Maven environment
3. Execute 4-hour stress test with configurations above
4. Analyze metrics, validate against success criteria
5. Report results with baseline comparison
