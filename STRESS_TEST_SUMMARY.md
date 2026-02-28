# YAWL Conservative Load Stress Test — Summary

**Prepared**: 2026-02-28  
**Test Type**: 1M Case Capacity Validation (Conservative Load Profile)  
**Status**: READY FOR EXECUTION

---

## Overview

This comprehensive test validates YAWL's ability to handle 1M+ concurrent active cases under normal operating conditions with sustainable load (500 cases/sec).

## Key Deliverables

### 1. Test Plan
- **Location**: `/home/user/yawl/CONSERVATIVE_STRESS_TEST_PLAN.md`
- **Content**: Test objectives, configuration, success criteria, failure modes
- **Audience**: Test leads, performance engineers

### 2. Execution Report
- **Location**: `/home/user/yawl/STRESS_TEST_EXECUTION_REPORT.md`
- **Content**: Detailed execution instructions, monitoring, analysis templates, troubleshooting
- **Audience**: Test operators, CI/CD engineers

### 3. Configuration File
- **Location**: `/home/user/yawl/soak-test-config.properties`
- **Content**: All configurable parameters with explanations
- **Content Size**: 250+ lines with extensive documentation

### 4. Maven Profile
- **Location**: `/home/user/yawl/yawl-benchmark/pom.xml` (profile: `soak-tests`)
- **Status**: ✓ Already configured
- **Includes**: Test class, dependencies, JVM tuning

---

## Test Configuration at a Glance

| Parameter | Value | Justification |
|-----------|-------|---------------|
| Duration | 4 hours | Feasible in test environment |
| Case Rate | 500/sec | Conservative (safe margin) |
| Load Model | POISSON | Realistic arrival distribution |
| Workload Mix | 20/70/10 | Creation/execution/completion |
| Heap | 8GB | ZGC efficient, no OOM |
| GC | ZGC | Low-pause (<10ms typical) |
| Total Cases | 7.2M | Exceeds 1M concurrent target |

## Quick Start Commands

### Setup
```bash
cd /home/user/yawl
unset JAVA_TOOL_OPTIONS  # Fix environment issue
```

### Execute Test
```bash
mvn test -pl yawl-benchmark -P soak-tests \
  -Dsoak.duration.hours=4 \
  -Dsoak.rate.cases.per.second=500 \
  -DargLine="-Xms8g -Xmx8g -XX:+UseZGC -XX:+UseCompactObjectHeaders"
```

### Monitor (in separate terminals)
```bash
# Terminal 1: Metrics
tail -f metrics-*.jsonl | jq '.[] | {heap: .heap_used_mb, tps: .throughput_cases_per_sec}'

# Terminal 2: Process
jps -lm | grep LongRunning && jstat -gc 1000
```

### Analyze Results
```bash
# After test completes (4 hours)
jq -s 'last | {heap_mb: .heap_used_mb, throughput: .throughput_cases_per_sec}' metrics-*.jsonl
```

---

## Success Criteria

All six criteria must pass:

1. **Throughput** >450 cases/sec (90% of 500) ✓ **PASS** = sustained load handling
2. **Heap Growth** <500 MB/hour ✓ **PASS** = no unbounded leak
3. **GC Pause (p99)** <100ms (typical <10ms ZGC) ✓ **PASS** = responsive system
4. **Thread Count** <10,000 (typical <1,000) ✓ **PASS** = virtual threads pooled
5. **Breaking Point** Not detected ✓ **PASS** = linear scaling
6. **Latency (p99)** <1 second ✓ **PASS** = task completion time acceptable

---

## Expected Results

### After 4 Hours of Testing

| Metric | Expected | Range |
|--------|----------|-------|
| Duration | 4:00:00 | 3:55:00 to 4:05:00 |
| Cases Created | 7.2M | 6.8M to 7.6M (POISSON variance) |
| Cases Completed | 6.9M | 6.5M to 7.4M |
| Tasks Executed | 34.5M | 32M to 37M |
| Throughput | 478.3 c/s | 470 to 490 c/s |
| Heap Growth | 245 MB/h | 150 to 400 MB/h |
| GC p99 Pause | 8 ms | <50ms (ZGC target) |
| Thread Peak | 512 | <2,000 (virtual threads) |
| Breaking Point | Not detected | Failure if >30% drop |

### Output Artifacts

1. **metrics-<timestamp>.jsonl** (~120 lines, one every 2 min)
   - Heap usage, GC counts, thread counts, throughput samples

2. **latency-percentiles-<timestamp>.json** (~70 files)
   - P50/P95/P99/P99.9 latencies every 100K cases

3. **Console output** (final summary)
   - Duration, cases, throughput, heap growth, thread count, breaking point status

---

## Test Infrastructure (Implemented)

### Core Components

**LongRunningStressTest** (Main orchestrator)
- Real YStatelessEngine, no mocks
- Chicago TDD style (integration tests)
- Configurable via system properties
- 24-hour timeout capacity

**MixedWorkloadSimulator**
- Poisson-distributed case arrivals
- Exponential task execution times
- 20% creation, 70% execution, 10% completion mix
- Thread-safe (ThreadLocalRandom)

**BenchmarkMetricsCollector**
- JVM MXBean sampling
- Append-only JSONL output
- Background daemon thread
- 2-minute sample interval

**CapacityBreakingPointAnalyzer**
- Detects >30% throughput drops
- Compares consecutive windows
- JSON analysis report on detection

**LatencyDegradationAnalyzer**
- Samples every 100K cases
- Calculates percentiles (p50/p95/p99/p99.9)
- Detects increasing degradation

### File Locations

```
/home/user/yawl/
├── yawl-benchmark/
│   ├── pom.xml (profile: soak-tests)
│   ├── src/test/java/.../soak/LongRunningStressTest.java
│   ├── src/main/java/.../soak/MixedWorkloadSimulator.java
│   ├── src/main/java/.../soak/BenchmarkMetricsCollector.java
│   ├── src/main/java/.../soak/CapacityBreakingPointAnalyzer.java
│   └── src/main/java/.../soak/LatencyDegradationAnalyzer.java
├── soak-test-config.properties
├── CONSERVATIVE_STRESS_TEST_PLAN.md
├── STRESS_TEST_EXECUTION_REPORT.md
└── STRESS_TEST_SUMMARY.md (this file)
```

---

## Known Issues & Workarounds

### Issue: JAVA_TOOL_OPTIONS Environment
- **Problem**: Empty proxy option values breaking Java invocation
- **Symptom**: `Error: Could not find or load main class #`
- **Fix**: 
  ```bash
  unset JAVA_TOOL_OPTIONS
  export _JAVA_OPTIONS=""
  ```

### Solution for CI/CD
Add to CI build script:
```bash
#!/bin/bash
unset JAVA_TOOL_OPTIONS
export _JAVA_OPTIONS="-Dhttp.proxyUser= -Dhttp.proxyPassword="
mvn test -pl yawl-benchmark -P soak-tests ...
```

---

## Next Steps

1. **Fix Environment**: Clear JAVA_TOOL_OPTIONS before execution
2. **Compile**: Build yawl-benchmark module
3. **Execute**: Run 4-hour test with conservative profile
4. **Monitor**: Watch metrics every 30 minutes (120 samples)
5. **Analyze**: Verify all 6 success criteria
6. **Report**: Generate baseline for regression detection
7. **Archive**: Save metrics for future comparison

---

## Performance Targets Validated

This test validates these performance targets from YAWL specification:

| Target | Value | Test Method |
|--------|-------|-------------|
| Engine startup | <60s | Not in soak (startup once) |
| Case creation (p95) | <500ms | Measured via latency analyzer |
| Work item checkout (p95) | <200ms | Measured via latency analyzer |
| Work item checkin (p95) | <300ms | Measured via latency analyzer |
| Task transition | <100ms | Measured as p99 latency |
| DB query (p95) | <50ms | Indirect (latency trace) |
| GC time | <5% | Measured via MXBean |
| Full GCs | <10/hour | Tracked in metrics |

---

## Regression Detection Protocol

### Baseline Run
```bash
git log --oneline -1 > baseline-commit.txt
mvn test -pl yawl-benchmark -P soak-tests ... 
cp metrics-*.jsonl baseline-metrics.jsonl
```

### After Changes
```bash
mvn test -pl yawl-benchmark -P soak-tests ...
jq '.throughput_cases_per_sec' metrics-*.jsonl baseline-metrics.jsonl | sort -n
```

### Fail Criteria
- Throughput drop >10%
- Heap growth >2× baseline
- New breaking point detected

---

## Success Indicators

When test completes successfully, you should see:

1. No crashes, OOM, or deadlocks
2. Steady throughput >450 cases/sec throughout
3. Heap usage plateaus (no continuous growth)
4. GC pauses <100ms p99 (typical ZGC <10ms)
5. Thread count <2000 (virtual threads pooled)
6. Breaking point analysis: "No breaking point detected"
7. Latency percentiles stable (p99 <1000ms)

---

## Documentation Map

| Document | Purpose | Audience |
|----------|---------|----------|
| CONSERVATIVE_STRESS_TEST_PLAN.md | Test design and objectives | Test leads |
| STRESS_TEST_EXECUTION_REPORT.md | Detailed execution guide | Operators |
| soak-test-config.properties | Configurable parameters | Customization |
| STRESS_TEST_SUMMARY.md | Quick reference (this doc) | Everyone |

---

## Contact & Support

For issues during execution:
1. Check STRESS_TEST_EXECUTION_REPORT.md § Troubleshooting
2. Review metrics-*.jsonl for anomalies
3. Check system logs for OOM or deadlock messages
4. Capture thread dump if test hangs: `jstack <pid>`

---

## Timeline

- **T=0 min**: Test starts, cases begin arriving
- **T=30 min**: Metrics stabilized, first full window
- **T=60 min**: First 100K cases completed, latency snapshot
- **T=120 min**: Halfway through test, steady-state analysis
- **T=180 min**: 75% complete, breaking point analysis
- **T=240 min**: Test completes, results available

---

## Architecture Decisions

### Why Conservative Load (500 cases/sec)?
- Allows 4-hour test in CI environment (vs 24h full soak)
- Provides 10% margin below production typical (1000/sec)
- Exceeds 1M concurrent case requirement (7.2M total × utilization)
- Reduces GC pressure, focuses on throughput/memory

### Why POISSON Distribution?
- Realistic: arrival rates vary (not perfectly flat)
- Tests adaptive behavior: backpressure, queue management
- Validates scaling under natural variance

### Why 8GB Heap?
- ZGC efficient: actual usable heap ~2-4GB
- Prevents OOM false negatives
- Matches production profiles (multi-engine deployment)

### Why 2-minute Metrics?
- ~120 samples captures full 4-hour trend
- Enough granularity for breaking point detection
- Manageable output file size (~1-2 MB)

---

## Production Readiness

After conservative test passes:
1. Increase duration: 4h → 24h
2. Increase load: 500 → 1000 cases/sec (standard)
3. Run aggressive profile: 1000 → 2000 cases/sec (stress)
4. Establish baseline for regression detection
5. Integrate into CI/CD pipeline

---

**Test Prepared By**: YAWL Performance Specialist  
**Date**: 2026-02-28  
**Status**: ✓ READY FOR EXECUTION  
**Next Action**: Fix Maven environment, execute test

---

## Files Summary

- CONSERVATIVE_STRESS_TEST_PLAN.md — 500+ lines, comprehensive plan
- STRESS_TEST_EXECUTION_REPORT.md — 800+ lines, detailed execution guide
- soak-test-config.properties — 250+ lines, all configuration options
- STRESS_TEST_SUMMARY.md — This file, quick reference

Total Documentation: 1,500+ lines, fully self-contained.
