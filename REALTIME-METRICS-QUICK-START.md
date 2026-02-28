# Real-time Metrics Analysis - Quick Start Guide

## What Is This?

A live monitoring system that watches 3 parallel stress tests and provides **real-time alerts and dashboards** without waiting for tests to complete. Instead of running tests for 4 hours and then analyzing results, you get intermediate insights every 30 seconds.

## Key Features

- **10-second metric sampling** from JVM (heap, GC, threads, throughput)
- **Real-time analysis** - calculates heap growth rate, GC frequencies
- **Live alerts** when thresholds are breached (memory leak, GC storm)
- **Beautiful dashboard** showing all 3 tests side-by-side
- **JSON analysis files** for automated downstream processing
- **Early warning** - identify breaking points before test ends

## 30-Second Start

### Terminal 1: Start the analyzer

```bash
cd /home/user/yawl

# Create metrics directory
mkdir -p stress-test-results/metrics

# Start analyzer (runs in foreground, ^C to stop)
java -cp "yawl-benchmark/target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout -pl yawl-benchmark)" \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  stress-test-results/metrics \
  stress-test-results/metrics/realtime-analysis.txt \
  false
```

### Terminal 2: View live dashboard

```bash
cd /home/user/yawl

# View dashboard (updates every 30 seconds)
bash scripts/metrics-dashboard.sh stress-test-results/metrics 30
```

### Terminal 3: Run a stress test

```bash
cd /home/user/yawl

# Run 1-hour conservative test at 10 cases/sec
mvn -q -pl yawl-benchmark test \
  -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=10
```

That's it! Now you have:
- **Terminal 1**: Shows analysis updates as new metrics arrive
- **Terminal 2**: Beautiful live dashboard of all metrics
- **Terminal 3**: Stress test running

## What Happens

Every 10 seconds during the stress test:

1. **BenchmarkMetricsCollector** samples: heap, GC, threads, throughput
2. **Metrics written** to `metrics-conservative.jsonl` (JSONL format)
3. **Analyzer reads** new lines every 10 seconds
4. **Calculates**: heap growth rate, GC frequency, trends
5. **Alerts** if thresholds crossed (heap > 1000 MB/h, GC > 2/min)
6. **Updates** JSON analysis file
7. **Dashboard** automatically picks up new data

## Example Output

### Analyzer Output (Terminal 1)

```
[ANALYSIS] [conservative] 2026-02-28T14:25:30Z | Heap: 2048->2050->2052 MB | GC: 5 total | Throughput: 42.5 cases/sec | Threads: 125 | Alert: OK
[ANALYSIS] [conservative] 2026-02-28T14:25:40Z | Heap: 2048->2100->2150 MB | GC: 7 total | Throughput: 45.1 cases/sec | Threads: 128 | Alert: OK
[ALERT] 14:26:00: moderate - HEAP_LEAK - Heap growth: 1200.50 MB/hour | GC: 1.8 pauses/min | Throughput: 48.3 cases/sec
```

### Dashboard (Terminal 2)

```
╔════════════════════════════════════════════════════════════════════════════════╗
║                YAWL STRESS TEST METRICS DASHBOARD                             ║
║                        [2026-02-28 14:26:15]                                 ║
╚════════════════════════════════════════════════════════════════════════════════╝

┌─ TEST: conservative ────────────────────────────────────────────────────────┐
│                                                                              │
│  Heap Memory:                                                               │
│    Growth Rate:   150.5 MB/hour  │  Min: 1024MB  │  Avg: 2050.3MB  │  Max: 2150MB │
│                                                                              │
│  Garbage Collection:                                                        │
│    Total GC Events: 8  │  Pauses:   0.89 /minute                           │
│                                                                              │
│  Throughput & Threads:                                                      │
│    Cases/sec:    45.1  │  Active Threads: 128  │  Samples: 42            │
│                                                                              │
│  Status: ✓ OK                                                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

## Alert Interpretation

### Alert: HEAP_LEAK
- **Condition**: Heap growth > 1000 MB/hour
- **Means**: Objects accumulating without garbage collection
- **Action**: 
  - If early: May be initial setup objects, normal
  - If sustained: Investigate object retention
  - Look at case completion - are old cases released?

### Alert: GC_STORM
- **Condition**: GC pauses > 2 per minute
- **Means**: System spending >30% CPU on garbage collection
- **Action**:
  - Heap may be full or nearly full
  - Increase heap size if sustainable
  - Profile to find object creation hotspots

### No Alert (OK)
- **Healthy**: Heap growth < 100 MB/hour
- **Healthy**: GC < 0.5 pauses/minute
- **System is stable** at current load

## File Locations

After running the test, check these locations:

```bash
# View all metrics collected
ls -lh stress-test-results/metrics/metrics-*.jsonl

# View latest analysis for each test
cat stress-test-results/metrics/analysis-conservative.json | jq .
cat stress-test-results/metrics/analysis-moderate.json | jq .
cat stress-test-results/metrics/analysis-aggressive.json | jq .

# View all alerts and analyses
tail -100 stress-test-results/metrics/realtime-analysis.txt

# Extract specific metrics
jq '.heap_growth_mb_per_hour' stress-test-results/metrics/analysis-*.json
```

## Running 3 Tests in Parallel

Want to stress test at conservative, moderate, and aggressive rates simultaneously?

### Terminal 1: Analyzer

```bash
java -cp "yawl-benchmark/target/classes:..." \
  org.yawlfoundation.yawl.benchmark.soak.RealtimeMetricsAnalyzer \
  stress-test-results/metrics \
  stress-test-results/metrics/realtime-analysis.txt \
  false
```

### Terminal 2: Dashboard

```bash
bash scripts/metrics-dashboard.sh stress-test-results/metrics 30
```

### Terminal 3: Conservative (10 cases/sec, 1 hour)

```bash
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=10
```

### Terminal 4: Moderate (50 cases/sec, 1 hour)

```bash
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=50
```

### Terminal 5: Aggressive (200 cases/sec, 1 hour)

```bash
mvn -q -pl yawl-benchmark test -Dtest=SoakTestRunner \
  -Dsoak.duration.seconds=3600 \
  -Dsoak.rate.cases.per.second=200
```

Now watch the dashboard update with all 3 tests showing real-time metrics and alerts!

## Analyzing Results After Test Completes

### Extract heap growth for all tests

```bash
echo "Test Name | Heap Growth (MB/h) | Alert Status"
echo "-----------|-------------------|---------------"
for f in stress-test-results/metrics/analysis-*.json; do
  name=$(jq -r '.test_name' "$f")
  growth=$(jq -r '.heap_growth_mb_per_hour' "$f")
  alert=$(jq -r '.alert_status' "$f")
  printf "%-12s | %18s | %s\n" "$name" "$growth" "$alert"
done
```

### Find the peak heap usage

```bash
jq -s 'max_by(.heap_max_mb)' stress-test-results/metrics/analysis-*.json
```

### Count how many times alerts fired

```bash
grep -c "ALERT\|HEAP_LEAK\|GC_STORM" stress-test-results/metrics/realtime-analysis.txt
```

### Export metrics for plotting

```bash
# Extract heap growth over time
jq -s '.[] | select(.test_name=="conservative") | .heap_growth_mb_per_hour' \
  stress-test-results/metrics/analysis-conservative.json

# Or from raw metrics
jq '.heap_used_mb' stress-test-results/metrics/metrics-conservative.jsonl > conservative-heap.txt
```

## Troubleshooting

### I don't see metrics appearing

**Check 1**: Is the stress test actually running?
```bash
ps aux | grep SoakTestRunner
```

**Check 2**: Are metrics being written?
```bash
tail -f stress-test-results/metrics/metrics-conservative.jsonl
```

**Check 3**: Check test logs for errors
```bash
tail -50 stress-test-results/logs/conservative.log
```

### Analyzer is running but no analysis files

**Check 1**: Is analyzer reading the metrics files?
```bash
ls -l stress-test-results/metrics/analysis-*.json
```

**Check 2**: Check analyzer logs
```bash
tail -50 stress-test-results/metrics/realtime-analysis.txt
```

### Dashboard shows no tests

**Check 1**: Are analysis JSON files present?
```bash
ls stress-test-results/metrics/analysis-*.json
```

**Check 2**: Run dashboard with verbose output
```bash
bash scripts/metrics-dashboard.sh stress-test-results/metrics 10
```

## Advanced: Custom Alert Thresholds

Edit alert thresholds in `RealtimeMetricsAnalyzer.java`:

```java
private static final long HEAP_GROWTH_ALERT_THRESHOLD_MB_PER_HOUR = 1000;
private static final long GC_PAUSE_ALERT_THRESHOLD_PER_MINUTE = 2;
```

Then rebuild:

```bash
mvn -q -pl yawl-benchmark clean compile
```

## Key Metrics Explained

| Metric | Unit | Healthy | Warning | Critical |
|--------|------|---------|---------|----------|
| Heap Growth Rate | MB/hour | <100 | 100-500 | >1000 |
| GC Pauses | per minute | <0.5 | 0.5-2 | >2 |
| Throughput | cases/sec | stable | degrading | dropping |
| Thread Count | count | stable | growing | unbounded |

## See Also

- Full documentation: `/home/user/yawl/STRESS-TEST-MONITORING.md`
- Java analyzer: `/home/user/yawl/yawl-benchmark/src/main/java/org/yawlfoundation/yawl/benchmark/soak/RealtimeMetricsAnalyzer.java`
- Performance targets: `/home/user/yawl/CLAUDE.md`
