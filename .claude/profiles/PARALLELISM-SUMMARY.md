# YAWL Parallelism Configuration Summary

**Quick Reference for Build Optimization Team**

---

## Current State (2026-02-28)

### Configuration Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    YAWL Build Parallelism                    │
│                    (16-core CPU system)                      │
└─────────────────────────────────────────────────────────────┘

LAYER 1: Maven Module Parallelism
├─ Setting: -T 2C
├─ Effective: 32 parallel Maven threads
├─ Status: ✅ OPTIMAL (2 threads per core)
└─ File: .mvn/maven.config

LAYER 2: JUnit Test Parallelism (Virtual Threads)
├─ Factor: 4.0
├─ Effective: 64 concurrent test methods (16 × 4.0)
├─ Max pool: 512 (allows oversaturation)
├─ Status: ✅ WELL-TUNED (good for H2 + I/O)
└─ Files: test/resources/junit-platform.properties
│         .mvn/maven.config

LAYER 3: Surefire JVM Forks
├─ Setting: ${surefire.forkCount} = 1.5C (24 forks)
├─ Effective: 24 parallel JVM instances
├─ Issue: ⚠️ CONFLICTING CONFIG (legacy + JUnit Platform)
└─ File: pom.xml (lines 1437-1462)

LAYER 4: Virtual Thread Scheduler
├─ Setting: -Djdk.virtualThreadScheduler.parallelism=16
├─ Effective: 16 carrier threads (1 per CPU core)
├─ Status: ✅ CORRECT (matches core count)
└─ File: .mvn/jvm.config

LAYER 5: ForkJoinPool (Common)
├─ Setting: -Djava.util.concurrent.ForkJoinPool.common.parallelism=15
├─ Effective: 15 threads for shared work
├─ Status: ✅ CONSERVATIVE (good for mixed workload)
└─ File: .mvn/jvm.config
```

---

## Performance Metrics

| Phase | Current Time | Bottleneck | Status |
|-------|-------------|-----------|--------|
| **Compilation** | ~20-25s | Module dependency chains | Normal |
| **Unit Tests** | ~5-7s | JUnit parallelism (64 threads) | Optimal |
| **Analysis** | ~15-20s | Static analysis tools | Normal |
| **Total (default)** | ~60-90s | IO/disk phase ordering | Acceptable |
| **Total (fast-verify)** | ~8-10s | Single JVM startup | Excellent |

---

## Key Findings

### ✅ What's Working Well

1. **Maven 2C**: Perfect balance for 16-core system
2. **JUnit 4.0 factor**: Virtual threads handling 64 concurrent tests smoothly
3. **ForkJoinPool 15**: Conservative but well-reasoned
4. **Virtual thread scheduler**: Correctly sized to core count

### ⚠️ What Needs Attention

1. **Surefire fork config**: Dual configuration (legacy + JUnit Platform)
   - Legacy: `<parallel>classesAndMethods</parallel>`, `<threadCount>4</threadCount>`
   - Modern: JUnit Platform properties file takes precedence
   - **Result**: Redundant and confusing (works, but suboptimal)

2. **No explicit fail-fast**: Tests continue even after failures
   - Could add `<maxFailures>1</maxFailures>` for faster feedback

---

## Recommended Actions

### Priority 1: Clean Up Surefire Config (1 day)

**File**: `pom.xml` (default profile, ~line 1437)

**Current**:
```xml
<forkCount>${surefire.forkCount}</forkCount>
<parallel>classesAndMethods</parallel>
<threadCount>${surefire.threadCount}</threadCount>
<perCoreThreadCount>true</perCoreThreadCount>
```

**Change To**:
```xml
<forkCount>2C</forkCount>
<reuseForks>true</reuseForks>
<!-- Remove above 4 lines (handled by JUnit Platform) -->
```

**Impact**: Cleaner config, no functional change

**Risk**: VERY LOW (same effective parallelism)

---

### Priority 2: Monitor & Decide on Factor 5.0 (2-4 weeks)

**Decision**: After collecting 2-3 weeks of build metrics

**If metrics show**:
- CPU util: <80% consistently → Try factor 5.0
- P95 test time: <1.5s → Can safely increase parallelism
- Flakiness: <0.1% → System is stable

**If metrics show**:
- CPU util: >95% → Stick with 4.0
- Flakiness: >0.2% → Revert any changes
- Memory: >2GB peak → Reduce pool size to 256

---

### Priority 3: Add Parallelism Metrics (Optional, future)

Enhance `.yawl/timings/build-timings.json` with:
```json
"parallelism_config": {
  "maven_threads": "2C",
  "junit_factor": 4.0,
  "surefire_forks": "2C"
},
"jvm_metrics": {
  "peak_memory_mb": 1024,
  "gc_time_sec": 0.2
}
```

---

## Configuration Files Reference

| File | Key Settings | Status |
|------|--------------|--------|
| `.mvn/maven.config` | `-T 2C`, junit factor | ✅ Keep |
| `.mvn/jvm.config` | Virtual thread parallelism, FJP | ✅ Keep |
| `test/resources/junit-platform.properties` | Factor 4.0, max pool 512 | ✅ Keep |
| `pom.xml` (default profile) | Surefire fork, parallel | ⚠️ Needs cleanup |
| `pom.xml` (fast-verify profile) | Single JVM, fail fast | ✅ Optimal |

---

## Test Results Baseline

**Current Configuration Performance** (fast-verify profile):
- Execution time: 8-10 seconds
- Test count: ~131 unit tests
- Flakiness rate: <0.1%
- CPU utilization: 70-85%
- Peak memory: ~800-1000MB

---

## Decision Matrix

### Should You Increase Parallelism?

```
Q1: CPU utilization < 75%?
├─ YES → Can increase (-T 2.5C or factor 5.0)
└─ NO → At limit, don't increase

Q2: Tests failing intermittently (>0.2%)?
├─ YES → Decrease parallelism (factor 3.5)
└─ NO → Current level is stable

Q3: Peak memory > 2GB?
├─ YES → Reduce max pool to 256
└─ NO → Current level is fine

Q4: Want to tune further?
├─ YES → Wait 2-3 weeks, collect metrics
└─ NO → Keep current config (already optimized)
```

---

## Team Guidance

### For Individual Engineers
- Use `DX_TIMINGS=1 bash scripts/dx.sh` for local feedback
- Current parallelism is transparent to you (handled by Maven)
- No configuration needed for development

### For Build Optimization Team
- Priority 1: Merge the pom.xml cleanup
- Priority 2: Monitor metrics for 2-3 weeks
- Priority 3: Decide on factor 5.0 based on data

### For DevOps/CI
- No changes needed to CI configuration
- Current Maven config works on any system
- Test sharding (8 shards) available in properties if needed later

---

## Quick Command Reference

```bash
# Run tests with current optimal configuration
mvn clean test -P fast-verify

# Run full build with parallelism
mvn clean verify -T 2C

# Analyze build trends
bash scripts/analyze-build-timings.sh --trend

# Check test flakiness
bash scripts/analyze-build-timings.sh --percentile
```

---

## Next Steps

1. **Today**: Review parallelism-analysis.md
2. **This Week**: Merge pom.xml cleanup (Priority 1)
3. **This Month**: Collect metrics, decide on factor 5.0
4. **Next Month**: Optional Tier 2 tuning based on data

**Status**: Analysis complete, ready for team review and implementation

