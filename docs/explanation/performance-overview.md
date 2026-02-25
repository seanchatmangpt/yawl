# YAWL Performance Documentation

## Overview

Complete performance testing, benchmarking, and capacity planning documentation for YAWL v6.0.0.

## Quick Links

### Essential Documents
- **[Performance Baselines](PERFORMANCE_BASELINES.md)** - Quantitative performance targets and measurements
- **[Performance Testing Guide](PERFORMANCE_TESTING_GUIDE.md)** - How to run and interpret performance tests
- **[Capacity Planning](CAPACITY_PLANNING.md)** - Infrastructure sizing and growth planning

### Quick Commands
```bash
# Run full performance suite
./scripts/run-performance-tests.sh --full

# Run baseline measurements only
./scripts/run-performance-tests.sh --baseline-only

# Run load tests only
./scripts/run-performance-tests.sh --load-only

# Quick smoke test
./scripts/run-performance-tests.sh --quick
```

## Performance Targets Summary

| Metric | Target | Baseline (v5.2) | Status |
|--------|--------|-----------------|--------|
| Case Launch (p95) | < 500ms | 280ms | ✓ PASS |
| Work Item Completion (p95) | < 200ms | 120ms | ✓ PASS |
| Concurrent Throughput | > 100/sec | 161.3/sec | ✓ PASS |
| Memory (1000 cases) | < 512MB | 364MB | ✓ PASS |
| Engine Startup | < 60s | 2.5s | ✓ PASS |
| Sustained Load Success | > 99% | 99.76% | ✓ PASS |
| Burst Load Success | > 95% | 96.0% | ✓ PASS |

## Test Categories

### 1. Baseline Measurements
**File**: `test/org/yawlfoundation/yawl/performance/EnginePerformanceBaseline.java`

**Tests**:
- Case launch latency (p50, p95, p99)
- Work item completion latency
- Concurrent case throughput
- Memory usage patterns
- Engine startup time

**Duration**: ~10 minutes

---

### 2. Load Tests
**File**: `test/org/yawlfoundation/yawl/performance/LoadTestSuite.java`

**Tests**:
- Sustained load (50 users, 5 min)
- Burst load (100 users, 1 min)
- Ramp-up (10→50 users, 2 min)

**Duration**: ~10 minutes

---

### 3. Scalability Tests
**File**: `test/org/yawlfoundation/yawl/performance/ScalabilityTest.java`

**Tests**:
- Case count scalability
- Memory efficiency
- Load recovery

**Duration**: ~15 minutes

---

## Getting Started

### 1. First-Time Setup

```bash
# Ensure JVM configured
export MAVEN_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Compile tests
mvn test-compile

# Run quick smoke test
./scripts/run-performance-tests.sh --quick
```

### 2. Establish Baseline

```bash
# Run full baseline measurements
./scripts/run-performance-tests.sh --baseline-only

# Save results for comparison
cp test-results/performance/baseline-*.log baseline-v5.2.0.log
```

### 3. Regular Testing

```bash
# Weekly: Quick check
./scripts/run-performance-tests.sh --quick

# Monthly: Full suite
./scripts/run-performance-tests.sh --full

# Before release: Full suite + comparison
./scripts/run-performance-tests.sh --full
diff baseline-v5.2.0.log test-results/performance/full-*.log
```

---

## Capacity Planning Quick Reference

| Workload | Engine Instances | DB | Memory | CPU |
|----------|------------------|-----|--------|-----|
| Small (50 users) | 1 | Single | 4 GB | 2 |
| Medium (200 users) | 3 | HA | 4 GB | 4 |
| Large (1000 users) | 10 | Cluster | 8 GB | 8 |
| Enterprise | 10+ | HA Cluster | 16 GB | 16 |

**Formula**:
```
Instances = ceil(Peak_RPS / 50)  # 50 req/sec per instance baseline
```

---

## Troubleshooting

### High Latency
**Symptoms**: p95 > 1000ms

**Check**:
1. GC logs for long pauses
2. Database query performance
3. Connection pool exhaustion

**Fix**:
- Tune GC settings
- Optimize slow queries
- Increase connection pool

---

### Low Throughput
**Symptoms**: < 100 cases/sec

**Check**:
1. CPU usage (should be < 80%)
2. Thread pool saturation
3. Database bottlenecks

**Fix**:
- Add more instances
- Increase thread pool
- Optimize database

---

### Memory Issues
**Symptoms**: OutOfMemoryError, high GC

**Check**:
1. Heap usage (should be < 80%)
2. Heap dump analysis
3. GC frequency

**Fix**:
- Increase heap size
- Fix memory leaks
- Tune GC parameters

---

## CI/CD Integration

### GitHub Actions
```yaml
# .github/workflows/performance.yml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Run Tests
        run: ./scripts/run-performance-tests.sh --full
      - name: Archive Results
        uses: actions/upload-artifact@v4
        with:
          name: performance-results
          path: test-results/performance/
```

---

## Performance Regression Detection

### Automated Comparison
```bash
# Establish baseline
./scripts/run-performance-tests.sh --full > baseline.log

# After changes
./scripts/run-performance-tests.sh --full > current.log

# Compare
./scripts/compare-performance.sh baseline.log current.log

# Fail if degradation > 10%
```

---

## Best Practices

### Before Testing
1. ✓ Isolate test environment
2. ✓ Configure JVM settings
3. ✓ Clear database
4. ✓ Restart services

### During Testing
1. ✓ Monitor system resources
2. ✓ Collect GC logs
3. ✓ Record database metrics
4. ✓ Avoid interference

### After Testing
1. ✓ Analyze results vs baseline
2. ✓ Archive logs
3. ✓ Document findings
4. ✓ Update baselines (if needed)

---

## Monitoring in Production

### Key Metrics
```
# Application
yawl.case.launch.latency.p95 < 500ms
yawl.workitem.completion.latency.p95 < 200ms
yawl.case.success_rate > 99%

# JVM
jvm.memory.heap.used < 80%
jvm.gc.pause.time.p95 < 200ms

# Database
postgresql.query.latency.p95 < 100ms
postgresql.connections.active < 80%
```

### Alert Thresholds
- **Critical**: Success rate < 95%, Latency > 2s
- **Warning**: Success rate < 99%, Latency > 1s
- **Info**: CPU > 70%, Memory > 75%

---

## References

### Documentation
- [Performance Baselines](PERFORMANCE_BASELINES.md)
- [Testing Guide](PERFORMANCE_TESTING_GUIDE.md)
- [Capacity Planning](CAPACITY_PLANNING.md)

### Tools
- Apache Bench (ab)
- Apache JMeter
- Gatling
- async-profiler
- Eclipse MAT

### External Resources
- [Java Performance Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- [PostgreSQL Performance](https://www.postgresql.org/docs/current/performance-tips.html)
- [G1GC Tuning](https://www.oracle.com/technical-resources/articles/java/g1gc.html)

---

## Revision History

| Date | Version | Changes |
|------|---------|---------|
| 2026-02-16 | 5.2.0 | Initial performance framework |

---

**Maintained By**: YAWL Performance Team
**Last Updated**: 2026-02-16
**Next Review**: 2026-05-16 (quarterly)
