# GC Profiling Guide: YAWL 1M Case Scale

## Overview

This guide documents the comprehensive GC profiling infrastructure for validating ZGC performance at 1M case scale. The profiling measures:

1. **GC Pause Times**: p50, p95, p99, max pause times
2. **Full GC Events**: Frequency and duration of major GC cycles
3. **Memory Allocation**: Heap growth rate, committed vs used memory
4. **Heap Stability**: Verification that heap recovers after GC
5. **String Deduplication**: Effectiveness with compact object headers

## Success Criteria

| Metric | Target | Rationale |
|--------|--------|-----------|
| **Average GC pause** | <5ms | ZGC low-latency target |
| **p99 GC pause** | <50ms | User-perceptible threshold |
| **Max GC pause** | <100ms | Hard upper bound |
| **Heap growth** | <1GB/hour | Linear leak detection |
| **Full GC events** | <10/hour | Should be minimal |
| **Heap recovery** | Yes | Pauses should reduce heap usage |

## Files Created

### Test Classes

1. **GCProfilingTest.java** — Main profiling test
   - Location: `/home/user/yawl/yawl-benchmark/src/test/java/org/yawlfoundation/yawl/benchmark/GCProfilingTest.java`
   - Tests ZGC performance at 1M case scale
   - Collects GC pause times via MXBean notifications
   - Samples heap memory every 1 second
   - Generates `gc-profile-*.json` output

### Scripts

1. **gc-profile.sh** — Main profiling script
   - Location: `/home/user/yawl/scripts/gc-profile.sh`
   - Runs GCProfilingTest with optimized ZGC settings
   - Configurable heap size, duration, target case count
   - Parses GC logs and produces summary
   
2. **analyze-gc-logs.sh** — GC log analyzer
   - Location: `/home/user/yawl/scripts/analyze-gc-logs.sh`
   - Parses JVM GC logs
   - Extracts pause time statistics
   - Analyzes string deduplication effectiveness

### Configuration Files

1. **jvm.config.gc-profile** — ZGC Profiling Configuration
   - Location: `/home/user/yawl/.mvn/jvm.config.gc-profile`
   - ZGC settings optimized for 1M case scale
   - 8GB heap (adjustable)
   - Compact object headers enabled
   - String deduplication enabled
   - Large page support (2MB pages)

## Running GC Profiling

### Quick Start (1 hour, 1M cases)

```bash
cd /home/user/yawl
bash scripts/gc-profile.sh
```

### Custom Configuration

```bash
# 30-minute test with 500K cases
bash scripts/gc-profile.sh \
  --cases 500000 \
  --duration-hours 0.5 \
  --heap-min 4g \
  --heap-max 6g \
  --output ./gc-profiles/custom

# 2-hour test with 2M cases (stress test)
bash scripts/gc-profile.sh \
  --cases 2000000 \
  --duration-hours 2 \
  --heap-min 8g \
  --heap-max 16g \
  --output ./gc-profiles/stress
```

## Output Artifacts

### JSON Profile Output

File: `gc-profile-{timestamp}.json`

Example structure:
```json
{
  "durationMs": 3600000,
  "casesProcessed": 1000000,
  "tasksExecuted": 5000000,
  "totalGCEvents": 287,
  "fullGCCount": 2,
  "youngGCCount": 285,
  "avgPauseMs": 2.34,
  "p50PauseMs": 1,
  "p95PauseMs": 8,
  "p99PauseMs": 15,
  "maxPauseMs": 42,
  "heapGrowthMBPerHour": 256.5,
  "heapRecoveryDetected": true,
  "casesPerSecond": 277.78
}
```

### GC Log Output

File: `gc-profile-{timestamp}.log`

Contains:
- Detailed GC event timestamps and durations
- Heap statistics before/after GC
- String deduplication statistics
- Object tenuring distribution
- Concurrent marking statistics

### Analysis Summary

Printed to console:
- Total GC events breakdown
- Pause time percentiles
- Heap growth rate
- String deduplication effectiveness

## Analyzing Results

### Step 1: Run Profiling

```bash
bash scripts/gc-profile.sh --duration-hours 1 --output ./results
```

Output:
```
gc-profile-1740681234.json     # JSON results
gc-profile-1740681234.log      # JVM GC log
```

### Step 2: Analyze GC Log

```bash
bash scripts/analyze-gc-logs.sh ./results/gc-profile-1740681234.log
```

Output:
```
GC Event Summary:
  Total GC events: 287
  Full GC events: 2
  Young GC events: 285

GC Pause Time Statistics:
  Count: 287
  Min: 0.5 ms
  p50: 1.0 ms
  p95: 8.2 ms
  p99: 15.3 ms
  Max: 42.0 ms
  Avg: 2.34 ms
```

### Step 3: Review JSON Output

```bash
# Pretty-print results
cat ./results/gc-profile-1740681234.json | jq .

# Check specific metrics
cat ./results/gc-profile-1740681234.json | jq '.avgPauseMs, .p99PauseMs, .heapGrowthMBPerHour'
```

## Interpreting Results

### GC Pause Times

- **Avg < 5ms**: ZGC is performing as expected
- **Avg 5-10ms**: Good performance, monitor for improvement
- **Avg > 10ms**: Investigate concurrent thread count or heap sizing
- **p99 < 50ms**: User-acceptable latency impact
- **p99 > 100ms**: May impact service latency, tune ConcGCThreads

### Heap Growth

- **< 256 MB/hour**: Excellent (linear allocation, no leak)
- **256-512 MB/hour**: Good (normal for high throughput)
- **512-1024 MB/hour**: Monitor (acceptable for 1M cases)
- **> 1024 MB/hour**: Warning (possible memory leak)

### Heap Recovery

If `heapRecoveryDetected` is false:
1. Check if test duration is too short (need multiple GC cycles)
2. Verify `-XX:+DisableExplicitGC` is set
3. Increase `-XX:InitiatingHeapOccupancyPercent` to trigger GC sooner

## Baseline Comparison

### Expected Results (ZGC + Compact Headers)

| Metric | Expected | Current | Status |
|--------|----------|---------|--------|
| Avg pause | 2-4 ms | - | - |
| p99 pause | 10-20 ms | - | - |
| Max pause | 30-50 ms | - | - |
| Heap growth | 250-500 MB/h | - | - |
| Full GCs/hour | <5 | - | - |
| Cases/sec | 250-300 | - | - |

Create baseline by running on known-good configuration.

## Troubleshooting

### High GC Pauses (>10ms avg)

1. **Increase concurrent threads**:
   ```bash
   bash scripts/gc-profile.sh --heap-min 4g --heap-max 8g
   # Edit .mvn/jvm.config.gc-profile: -XX:ConcGCThreads=12
   ```

2. **Lower heap occupancy threshold**:
   ```
   -XX:InitiatingHeapOccupancyPercent=25  # Default is 35
   ```

3. **Enable large pages**:
   ```
   -XX:+UseLargePages -XX:LargePageSizeInBytes=2m
   ```

### No Heap Recovery Detected

1. Extend test duration (need ~30-60 GC cycles)
2. Verify GC notifications are enabled (should be automatic)
3. Check heap occupancy is above InitiatingHeapOccupancyPercent

### Out of Memory

1. Increase `-Xmx`: `bash scripts/gc-profile.sh --heap-max 16g`
2. Increase `-XX:SoftMaxHeapSize`: Value < -Xmx by 1GB
3. Reduce target case count: `--cases 500000`

## Performance Tuning Checklist

- [ ] Verify Java 25 is installed: `java -version`
- [ ] Check ZGC is enabled: grep UseZGC in gc log
- [ ] Verify compact headers: grep CompactObjectHeaders
- [ ] Confirm string dedup: grep -i "string dedup"
- [ ] Check concurrent threads: grep ConcGCThreads in log
- [ ] Review heap occupancy: grep InitiatingHeapOccupancy

## References

- **ZGC Tuning**: `/home/user/yawl/.mvn/GC-TUNING.md`
- **Java 25 GC**: https://docs.oracle.com/en/java/javase/25/gctuning/
- **ZGC Wiki**: https://wiki.openjdk.org/display/zgc/
- **Compact Headers**: https://openjdk.org/jeps/450

## Next Steps

1. **Establish baseline** (run once on known-good setup)
2. **Profile changes** (before/after optimization)
3. **Trend analysis** (track metrics over time)
4. **Production validation** (run at real workload scale)

