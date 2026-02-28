# Real-time Metrics Analysis Pipeline for YAWL Stress Tests

## Overview

This system provides **real-time monitoring and analysis** of metrics from parallel stress tests running concurrently. It continuously monitors 3 stress tests (conservative, moderate, aggressive) and provides:

- **Heap growth rate trending** (MB/hour, MB/minute)
- **GC pause anomaly detection** (pauses per minute)
- **Throughput trend analysis** (cases/sec over time)
- **Threshold breach alerts** (early warning of breaking points)
- **Intermediate insights** (updated every 30 seconds)
- **Live dashboard** (display of all metrics in real-time)

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────┐
│  Stress Test Orchestrator (stress-test-orchestrator.sh)     │
│  ├─ Conservative: 3600s @ 10 cases/sec                      │
│  ├─ Moderate:     3600s @ 50 cases/sec                      │
│  └─ Aggressive:   3600s @ 200 cases/sec                     │
└─────────────┬───────────────────────────────────────────────┘
              │ (emit metrics every 10 seconds)
              ▼
┌─────────────────────────────────────────────────────────────┐
│  metrics-conservative.jsonl                                  │
│  metrics-moderate.jsonl                                      │
│  metrics-aggressive.jsonl                                    │
│  (JSONL format: one JSON object per line per 10 seconds)     │
└─────────────┬───────────────────────────────────────────────┘
              │ (polling every 10 seconds)
              ▼
┌─────────────────────────────────────────────────────────────┐
│  RealtimeMetricsAnalyzer (Java)                              │
│  ├─ Read metrics files incrementally                         │
│  ├─ Parse JSONL lines                                        │
│  ├─ Calculate statistics (heap, GC, throughput)              │
│  ├─ Detect anomalies (thresholds)                            │
│  ├─ Write JSON analysis files                                │
│  └─ Output alerts to stderr                                  │
└─────────────┬───────────────────────────────────────────────┘
              │ (generates)
              ▼
┌─────────────────────────────────────────────────────────────┐
│  analysis-conservative.json                                  │
│  analysis-moderate.json                                      │
│  analysis-aggressive.json                                    │
│  realtime-analysis-summary.txt                               │
└─────────────┬───────────────────────────────────────────────┘
              │ (polled by dashboard every 30 seconds)
              ▼
┌─────────────────────────────────────────────────────────────┐
│  metrics-dashboard.sh                                        │
│  (Beautiful live dashboard with colors and formatting)       │
└─────────────────────────────────────────────────────────────┘
```

### Metrics Schema (JSONL format)

Each line in `metrics-*.jsonl` contains:

```json
{
  "timestamp": 1735689600000,
  "heap_used_mb": 2048,
  "heap_max_mb": 4096,
  "heap_committed_mb": 3072,
  "gc_collection_count": 45,
  "gc_collection_time_ms": 1250,
  "thread_count": 125,
  "peak_thread_count": 150,
  "cases_processed": 12500,
  "throughput_cases_per_sec": 45.2
}
```

### Analysis Output Schema

Each `analysis-*.json` file contains:

```json
{
  "test_name": "conservative",
  "timestamp": 1735689620000,
  "lines_analyzed": 42,
  "heap_growth_mb_per_hour": 250.5,
  "heap_growth_mb_per_min": 4.17,
  "heap_min_mb": 1024,
  "heap_max_mb": 2560,
  "heap_avg_mb": 2048.3,
  "gc_count_total": 87,
  "gc_pauses_per_minute": 0.15,
  "avg_throughput": 42.3,
  "thread_count_final": 128,
  "alert_status": "OK"
}
```

## Quick Start

### 1. Run Stress Tests with Monitoring

```bash
# Option A: Full orchestration (tests + analyzer)
bash /home/user/yawl/scripts/stress-test-orchestrator.sh

# Option B: Just start the analyzer (if tests already running elsewhere)
java -cp "yawl-benchmark/target/classes:..." \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  /path/to/metrics/dir \
  /path/to/summary.txt \
  false
```

### 2. View Live Dashboard

In a separate terminal:

```bash
# Monitor metrics every 30 seconds
bash /home/user/yawl/scripts/metrics-dashboard.sh \
  /home/user/yawl/stress-test-results/metrics 30

# Or use the shell-based monitor
bash /home/user/yawl/scripts/realtime-metrics-monitor.sh \
  /home/user/yawl/stress-test-results/metrics \
  false
```

### 3. Check Intermediate Results

```bash
# View all analyses
ls -lh /home/user/yawl/stress-test-results/metrics/analysis-*.json

# Check latest analysis
cat /home/user/yawl/stress-test-results/metrics/analysis-conservative.json | jq .

# Monitor in real-time
tail -f /home/user/yawl/stress-test-results/metrics/realtime-analysis.txt
```

## Alert Conditions

### Heap Growth Alert (HEAP_LEAK)

**Condition**: Heap growth > 1000 MB/hour

**Indicates**:
- Potential memory leak
- Object accumulation without garbage collection
- Unbounded cache or queue

**Action**:
- Review heap trends graph
- Check for unusual object retention
- Consider enabling heap dumps

### GC Pause Alert (GC_STORM)

**Condition**: GC pause frequency > 2 per minute

**Indicates**:
- Excessive garbage collection frequency
- Heap pressure approaching max
- GC pauses consuming significant CPU

**Action**:
- Increase heap size if sustainable
- Profile to find object creation hotspots
- Consider GC algorithm tuning (G1GC vs ZGC)

## Key Metrics Explained

### Heap Growth Rate (MB/hour, MB/minute)

Calculated as linear trend of heap usage over time:

```
Growth = (final_heap_mb - initial_heap_mb) / duration_hours
```

**Healthy**: < 100 MB/hour for long-running tests
**Warning**: 100-500 MB/hour (sustainable but watch)
**Critical**: > 1000 MB/hour (memory leak suspected)

### GC Pauses per Minute

Calculated from GC collection counts:

```
GC_rate = (gc_collection_count * 60) / duration_minutes
```

**Healthy**: < 0.5 per minute
**Warning**: 0.5-2 per minute
**Critical**: > 2 per minute (GC storm)

### Throughput (cases/sec)

Calculated as cases processed divided by sampling interval:

```
Throughput = (cases_delta) / (time_delta_seconds)
```

**Expected**: Stable at configured rate
**Degradation**: Throughput dropping indicates breaking point approaching

### Thread Count

- **Initial baseline**: Typical 50-100 threads
- **During stress test**: Should grow linearly with concurrent cases
- **Final count**: Should stabilize when load stops growing
- **Concern**: Unbounded growth indicates thread leak

## Performance Targets (from CLAUDE.md)

```
Engine startup: < 60s
Case creation (p95): < 500ms
Work item checkout (p95): < 200ms
Work item checkin (p95): < 300ms
Task transition: < 100ms
DB query (p95): < 50ms
GC time: < 5%, Full GCs: < 10/hour
```

## Files Generated

After a 1-hour stress test run:

```
stress-test-results/
├── metrics/
│   ├── metrics-conservative.jsonl      (360 lines, ~50 KB)
│   ├── metrics-moderate.jsonl          (360 lines, ~50 KB)
│   ├── metrics-aggressive.jsonl        (360 lines, ~50 KB)
│   ├── analysis-conservative.json      (~1 KB, latest analysis)
│   ├── analysis-moderate.json          (~1 KB, latest analysis)
│   ├── analysis-aggressive.json        (~1 KB, latest analysis)
│   └── realtime-analysis.txt           (~100 KB, all analyses + alerts)
├── logs/
│   ├── build.log
│   ├── conservative.log
│   ├── moderate.log
│   ├── aggressive.log
│   └── analyzer.log
└── reports/
    └── summary.txt                     (final summary)
```

## Usage Examples

### Example 1: Running a Quick 5-Minute Stress Test

```bash
# Start the analyzer
java -cp "yawl-benchmark/target/classes:..." \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  ./test-metrics \
  ./test-metrics/summary.txt \
  true &

# Run stress test (300 seconds, 10 cases/sec)
mvn -pl yawl-benchmark test \
  -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=300 \
  -Dsoak.rate.cases.per.second=10

# View results
cat test-metrics/analysis-*.json | jq .
```

### Example 2: Monitoring Multiple Concurrent Tests

Terminal 1 - Start analyzer:
```bash
java -cp "yawl-benchmark/target/classes:..." \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  ~/metrics verbose | tee ~/metrics/monitor.log
```

Terminal 2 - Dashboard:
```bash
bash scripts/metrics-dashboard.sh ~/metrics 30
```

Terminal 3 - Run test 1:
```bash
mvn -pl yawl-benchmark test \
  -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=10
```

Terminal 4 - Run test 2:
```bash
mvn -pl yawl-benchmark test \
  -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=50
```

### Example 3: Parsing Analysis for Custom Reports

```bash
# Extract all heap growth rates
jq '.heap_growth_mb_per_hour' stress-test-results/metrics/analysis-*.json

# Extract all alert statuses
jq '.alert_status' stress-test-results/metrics/analysis-*.json

# Count alerts
grep -o '"alert_status":"[^"]*"' stress-test-results/metrics/analysis-*.json | sort | uniq -c

# Find peak heap usage
jq '.heap_max_mb' stress-test-results/metrics/analysis-*.json | sort -n | tail -1
```

## Troubleshooting

### No metrics files appearing

**Problem**: Metrics directory is empty after starting test

**Solution**:
1. Verify test is actually running: `ps aux | grep SoakTestRunner`
2. Check test logs: `tail -f logs/conservative.log`
3. Verify metrics collector started: Check for "MetricsCollector" in output
4. Check file permissions: `ls -ld stress-test-results/metrics/`

### Analyzer not reading new lines

**Problem**: Analysis files not updating

**Solution**:
1. Verify metrics files are being written: `tail -f metrics-*.jsonl`
2. Check analyzer is running: `ps aux | grep RealtimeMetricsAnalyzer`
3. Check analyzer logs: `tail -f logs/analyzer.log`
4. Verify analyzer has read permissions: `ls -l metrics-*.jsonl`

### Alerts firing but no visible issues

**Problem**: Alert threshold too sensitive

**Solution**:
1. Review alert thresholds in `RealtimeMetricsAnalyzer.java`
2. Adjust based on your system baseline
3. Common baseline for 4GB heap: ~200 MB/hour growth is normal during ramp-up

### Dashboard not showing metrics

**Problem**: Dashboard script not finding analysis files

**Solution**:
1. Verify analysis files exist: `ls -l metrics/analysis-*.json`
2. Check script permissions: `chmod +x scripts/metrics-dashboard.sh`
3. Test jq installation: `echo '{"test":"ok"}' | jq .`

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Stress Test with Monitoring
on: [push]
jobs:
  stress-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start Real-time Monitor
        run: |
          mkdir -p metrics
          java ... RealtimeMetricsAnalyzer ./metrics &
      - name: Run Stress Tests
        run: |
          mvn -pl yawl-benchmark test \
            -Dtest=SoakTestRunner \
            -Dsoak.duration.seconds=300 \
            -Dsoak.rate.cases.per.second=10
      - name: Generate Report
        run: cat metrics/realtime-analysis.txt
      - name: Upload Metrics
        uses: actions/upload-artifact@v3
        with:
          name: stress-test-metrics
          path: metrics/
```

## Performance Tuning Tips

### Reduce CPU Usage by Analyzer

- Increase `ALERT_SUMMARY_INTERVAL_SECS` (default 30s)
- Reduce metrics collection frequency in stress test
- Run analyzer on separate machine

### Increase Metrics Granularity

- Edit `BenchmarkMetricsCollector.java` to sample more frequently
- Trade-off: more disk I/O, more precise trending

### Optimize Storage

- Archive old metrics files to compressed format
- Rotate metrics files daily
- Delete analysis after generating reports

## See Also

- `/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/soak/RealtimeMetricsAnalyzer.java` - Java analyzer implementation
- `/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/soak/BenchmarkMetricsCollector.java` - Metrics collection
- `/home/user/yawl/CLAUDE.md` - Performance targets
