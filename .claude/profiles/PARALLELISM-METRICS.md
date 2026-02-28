# Build Parallelism Metrics Collection Guide

**Purpose**: Track parallelism effectiveness over time  
**Target Audience**: DevOps, Build Optimization Team  
**Update Frequency**: Weekly (passive, automatic collection)

---

## Metrics to Collect

### Weekly Summary Metrics

Collect these numbers every Friday or after significant changes:

```json
{
  "week": 1,
  "date": "2026-02-28",
  "builds_total": 25,
  "builds_passed": 25,
  "builds_failed": 0,
  
  "timing_seconds": {
    "min": 7.8,
    "max": 10.2,
    "mean": 8.9,
    "median": 8.7,
    "p95": 9.8
  },
  
  "test_metrics": {
    "test_count": 131,
    "test_time_sec": 5.2,
    "test_time_percent_of_total": 58,
    "flakiness_rate_percent": 0.08,
    "timeout_failures": 0
  },
  
  "resource_metrics": {
    "cpu_utilization_percent": 76,
    "peak_memory_mb": 920,
    "gc_time_percent": 2.1,
    "full_gc_count": 2
  },
  
  "config": {
    "maven_threads": "2C",
    "junit_factor": 4.0,
    "surefire_forks": "1.5C",
    "java_version": "25.0.2"
  }
}
```

---

## Collection Methods

### Option 1: Manual Collection (Excel/CSV)

Create a weekly spreadsheet with these columns:

| Week | Avg Time | P95 Time | Flakiness % | CPU % | Memory MB | Notes |
|------|----------|----------|-------------|-------|-----------|-------|
| 1    | 8.9      | 9.8      | 0.08       | 76    | 920       | Baseline |
| 2    | 8.7      | 9.6      | 0.06       | 75    | 910       | Config cleanup |
| 3    | 8.8      | 9.7      | 0.05       | 77    | 925       | Post-cleanup |

---

### Option 2: Automated Collection

Enhance `scripts/analyze-build-timings.sh` to capture:

```bash
#!/bin/bash

# Existing: Extract from .yawl/timings/build-timings.json
avg_time=$(jq '.builds[-20:] | map(.elapsed_sec) | add / length' build-timings.json)
p95_time=$(jq '.builds[-20:] | map(.elapsed_sec) | sort | .[-1:][0]' build-timings.json)

# New: Extract system metrics
cpu_util=$(top -n 1 -b | grep "Cpu(s)" | awk '{print $2}' | cut -d% -f1)
memory=$(free | grep Mem | awk '{printf "%.0f", $3 * 100 / $2}')

# New: Extract test metrics
flakiness=$(grep -c FAIL build-timings.json || echo 0)

# Append to metrics file
echo "$(date +%Y-%m-%d),$avg_time,$p95_time,$cpu_util,$memory,$flakiness" >> .yawl/parallelism/weekly-metrics.csv
```

---

### Option 3: CI Pipeline Integration

Add to GitHub Actions / GitLab CI:

```yaml
- name: Collect parallelism metrics
  run: |
    METRICS=$(cat << 'JSON'
    {
      "timestamp": "$(date -Iseconds)",
      "build_time_sec": $(mvn -T 2C clean test | grep -oP 'Total time: \K[0-9.]+'),
      "cpu_util": $(top -n 1 -b | grep Cpu | awk '{print $2}'),
      "peak_memory_mb": $(jq '.peak_memory_mb' .yawl/build-metrics.json)
    }
    JSON
    )
    echo "$METRICS" >> .yawl/parallelism/ci-metrics.jsonl
```

---

## Key Metrics Explained

### Build Timing

**avg_time**: Average build time across all runs this week  
**p95_time**: 95th percentile (5% of builds are slower than this)  
**Target**: <10 seconds average, P95 <10.5 seconds

### Test Metrics

**flakiness_rate**: % of test runs that fail intermittently  
**test_time_percent**: What % of total build time is spent running tests  
**Target**: <0.1% flakiness, 50-60% test time

### Resource Utilization

**cpu_utilization**: Average CPU % during build (from `top`, `systemstat`)  
**peak_memory_mb**: Maximum heap usage during build  
**gc_time_percent**: What % of time spent in garbage collection  
**Target**: CPU 75-85%, Memory <1500MB, GC <5%

---

## Thresholds & Alerts

### Red Flags (Investigate Immediately)

| Metric | Threshold | Action |
|--------|-----------|--------|
| Avg time | >12 seconds | Check for regression, review recent changes |
| P95 time | >15 seconds | Look for outlier slowness, may need tuning |
| Flakiness | >0.5% | Reduce parallelism or investigate timing issues |
| CPU util | >95% | System is saturated, reduce parallelism |
| Memory | >2GB | OOM risk, reduce JUnit pool size to 256 |

### Yellow Flags (Monitor Closely)

| Metric | Threshold | Action |
|--------|-----------|--------|
| Avg time | 10-12 seconds | Trending slowly, likely OK |
| Flakiness | 0.1-0.5% | Acceptable but monitor trends |
| CPU util | 85-95% | Healthy, approaching limits |
| Memory | 1.5-2GB | Getting full, be cautious with Tier 2 |

### Green Flags (No Action)

| Metric | Threshold | Action |
|--------|-----------|--------|
| Avg time | <10 seconds | Excellent |
| Flakiness | <0.1% | Perfect |
| CPU util | <85% | Headroom for tuning |
| Memory | <1.5GB | Comfortable |

---

## Timeline for Decision Making

### Weeks 1-2: Baseline Collection
- Collect metrics from current configuration
- Establish baseline numbers for comparison
- No changes made

### Weeks 2-3: Config Cleanup (Priority 1)
- Merge pom.xml cleanup (Surefire config)
- Continue collecting metrics
- Verify no regression post-cleanup

### Weeks 3-4: Analysis & Decision Point
- Review collected 3+ weeks of data
- Analyze trends (is CPU util <80%? Flakiness stable?)
- Decide: Proceed with Tier 2 (factor 5.0) or not?

### Week 4+: Optional Tier 2 (If Approved)
- If metrics show headroom: Implement factor 5.0
- Collect 1-2 more weeks of data
- Confirm 5-8% speedup achieved
- Document final tuning decision

---

## Example Dashboard

### Weekly at-a-glance view

```
YAWL Build Parallelism Status
Week of 2026-02-28

Timing:
  Avg:    8.9 sec ▃▃▃ (stable)
  P95:    9.8 sec ▃▃▃ (stable)
  Trend:  ↔️ (no change)

Tests:
  Flakiness: 0.08% ▃ (excellent)
  Time %:    58% ▃ (expected)

Resources:
  CPU:  76% ▃▃▃ (healthy)
  Mem:  920MB ▃▃ (comfortable)
  GC:   2.1% ▃ (excellent)

Config:
  Maven: 2C | JUnit: 4.0 | Forks: 1.5C

Status: ✅ All metrics nominal
```

---

## Reporting Template

### Weekly Status Report

```markdown
## Build Parallelism Status Report
Week of 2026-02-28

### Performance
- Avg build time: 8.9 sec (target: <10s) ✅
- P95 test time: <1s (target: <1.5s) ✅
- Flakiness rate: 0.08% (target: <0.1%) ✅

### Resource Usage
- CPU utilization: 76% (target: 75-85%) ✅
- Peak memory: 920MB (limit: 1500MB) ✅
- GC time: 2.1% (target: <5%) ✅

### Actions Taken
- [List any changes made]

### Upcoming
- [List planned changes/decisions]

### Notes
[Any observations or concerns]
```

---

## Tools & Integration

### Existing YAWL Infrastructure

**What we already have**:
- `.yawl/timings/build-timings.json` (JSON Lines, append-only)
- `scripts/analyze-build-timings.sh` (existing analysis tool)
- `BUILD-OPTIMIZATION.md` (fast-verify profile documentation)

**What to add**:
- `.yawl/parallelism/weekly-metrics.csv` (new: for tracking)
- Enhanced `analyze-build-timings.sh` to compute CPU/memory percentiles
- Weekly metrics collection hook in CI

### Suggested Enhancements

```bash
# New script: scripts/collect-parallelism-metrics.sh
#!/bin/bash
# Runs weekly, appends to .yawl/parallelism/weekly-metrics.csv

echo "$(date +%Y-%m-%d),$(get_avg_time),$(get_p95_time),$(get_cpu),$(get_memory),$(get_flakiness)" >> .yawl/parallelism/weekly-metrics.csv
```

---

## Data Retention

### Keep Forever
- Weekly summary CSV (lightweight, ~100KB/year)
- Configuration changes and dates

### Keep 3 Months
- Daily build timings (in build-timings.json)
- JVM logs (can get large)

### Keep 1 Month
- Detailed test execution logs
- CPU/memory sample data

---

## Success Criteria

### After 4 Weeks

- [ ] 4 weeks of baseline metrics collected
- [ ] All metrics show stable trends
- [ ] CPU utilization consistently 75-85%
- [ ] Flakiness rate consistent <0.1%
- [ ] Decision made on Tier 2 implementation
- [ ] If proceeding to Tier 2: 5-8% speedup target

---

## FAQ

**Q: How often should we collect metrics?**  
A: Weekly is good (automated). Daily is too much noise.

**Q: What if we see a spike in build time?**  
A: Investigate that specific build (check what changed, PR activity).

**Q: When can we declare "optimization successful"?**  
A: After 2-3 weeks of stable metrics meeting targets.

**Q: Should we alert on every metric change?**  
A: No. Only alert if threshold exceeded (red flags table).

**Q: Can we share these metrics with the team?**  
A: Yes! Weekly dashboard would be great for transparency.

---

**Document Status**: READY FOR IMPLEMENTATION  
**Last Updated**: 2026-02-28  

