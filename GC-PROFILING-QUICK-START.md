# GC Profiling Quick Start Guide

## One-Minute Overview

YAWL now includes comprehensive GC profiling infrastructure to measure ZGC performance at 1M case scale. The system automatically measures:
- GC pause times (p50, p95, p99, max)
- Memory allocation patterns
- Heap stability and recovery
- Full GC vs Young GC events
- Throughput (cases/second)

## Quick Commands

### Run 1-Hour Baseline Profile

```bash
cd /home/user/yawl
bash scripts/gc-profile.sh
```

**What it does**: Processes 1M synthetic cases, collects GC metrics, outputs JSON report

**Output files**:
- `gc-profile-{timestamp}.json` - Metrics report
- `gc-profile-{timestamp}.log` - JVM GC log

### View Results

```bash
# Pretty-print JSON results
cat gc-profile-*.json | jq .

# Show just the key metrics
cat gc-profile-*.json | jq '{avgPauseMs, p99PauseMs, heapGrowthMBPerHour, casesPerSecond}'

# Analyze GC log
bash scripts/analyze-gc-logs.sh gc-profile-*.log
```

## Expected Results (Baseline)

| Metric | Target | Expected |
|--------|--------|----------|
| Avg GC pause | <5ms | 2.5ms |
| p99 GC pause | <50ms | 15ms |
| Max GC pause | <100ms | 40ms |
| Heap growth | <1GB/hour | 300 MB/h |
| Full GC events | <10/hour | <5 |
| Cases/second | >250 | 280 |

## Interpretation

- **Avg pause < 5ms**: Excellent ZGC performance
- **p99 pause < 50ms**: Service latency impact minimal
- **Heap growth < 500MB/h**: No unbounded leak
- **Heap recovery detected**: GC is freeing memory

If any metric fails targets, see troubleshooting below.

## Custom Configurations

### Quick Test (15 minutes)

```bash
bash scripts/gc-profile.sh --duration-hours 0.25 --cases 250000
```

### Stress Test (2 hours, 2M cases)

```bash
bash scripts/gc-profile.sh --duration-hours 2 --cases 2000000
```

### Larger Heap (for heavier workloads)

```bash
bash scripts/gc-profile.sh --heap-min 8g --heap-max 16g
```

## Regression Detection

Compare before/after optimization:

```bash
# Baseline (before)
bash scripts/gc-profile.sh --output results/before

# After optimization
bash scripts/gc-profile.sh --output results/after

# Compare
diff <(jq . results/before/gc-profile-*.json) <(jq . results/after/gc-profile-*.json)
```

Fail if avg pause increased >10%

## Troubleshooting

### High GC Pauses (avg >10ms)

**Solution 1**: Increase concurrent threads
```bash
# Edit /home/user/yawl/.mvn/jvm.config.gc-profile
# Change: -XX:ConcGCThreads=12  # was 8
bash scripts/gc-profile.sh
```

**Solution 2**: Lower GC trigger threshold
```
-XX:InitiatingHeapOccupancyPercent=25  # was 35
```

### Out of Memory

```bash
# Increase heap
bash scripts/gc-profile.sh --heap-max 16g

# Or reduce case count
bash scripts/gc-profile.sh --cases 500000
```

### No Heap Recovery Detected

- Extend test duration (need 30+ GC cycles)
- Check heap occupancy in log: `grep "Heap\|100%"`

## Files Reference

| File | Purpose | Path |
|------|---------|------|
| **GCProfilingTest.java** | Main test | `/home/user/yawl/yawl-benchmark/src/test/java/org/yawlfoundation/yawl/benchmark/GCProfilingTest.java` |
| **gc-profile.sh** | Run script | `/home/user/yawl/scripts/gc-profile.sh` |
| **analyze-gc-logs.sh** | Log analyzer | `/home/user/yawl/scripts/analyze-gc-logs.sh` |
| **jvm.config.gc-profile** | ZGC config | `/home/user/yawl/.mvn/jvm.config.gc-profile` |
| **gc-profiling-guide.md** | Full docs | `/home/user/yawl/gc-profiling-guide.md` |
| **GC-PROFILING-IMPLEMENTATION.md** | Design docs | `/home/user/yawl/GC-PROFILING-IMPLEMENTATION.md` |

## Key Metrics Explained

### GC Pause Times

**p50** (median): Half of GC pauses are faster than this
**p95**: 95% of pauses are faster than this
**p99**: 99% of pauses are faster than this (most important for user experience)
**max**: Worst-case pause time (should be rare)

Example: p99=15ms means 99 out of 100 GC pauses are <15ms

### Heap Growth

`heapGrowthMBPerHour = (final_heap_MB - initial_heap_MB) / duration_hours`

- < 250 MB/h: Excellent (tight allocation)
- < 500 MB/h: Good (normal for 1M cases)
- > 1000 MB/h: Warning (possible leak)

### Heap Recovery

`heapRecoveryDetected = (max_heap - min_heap_after_max) > 100MB`

- **Yes**: GC is freeing memory (expected)
- **No**: Either test is too short or GC isn't compacting

## Next Steps

1. Run baseline: `bash scripts/gc-profile.sh`
2. Examine results: `cat gc-profile-*.json | jq .`
3. Compare targets: All should be green
4. If yellow/red: See troubleshooting above
5. Establish baseline for regression detection

## Performance Targets Reference

From YAWL v6.0.0 SPR specification:

```
Engine startup: < 60s
Case creation (p95): < 500ms
Work item checkout (p95): < 200ms
Work item checkin (p95): < 300ms
Task transition: < 100ms
DB query (p95): < 50ms
GC time: < 5%          ← Measured by this profiling
Full GCs: < 10/hour    ← Measured by this profiling
```

---

For detailed documentation, see `/home/user/yawl/gc-profiling-guide.md`

For implementation details, see `/home/user/yawl/GC-PROFILING-IMPLEMENTATION.md`
