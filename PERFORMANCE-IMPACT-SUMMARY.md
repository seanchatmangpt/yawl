# Performance Monitoring Framework - Library Update Impact Analysis

## Executive Summary

This framework enables systematic tracking of performance impacts when updating YAWL dependencies. It has been designed specifically for the recent Java 25 and library migration (Hibernate 6.6.42, Jakarta EE 10, Log4j 2.25.3, Jackson 2.18.3, HikariCP 7.0.2).

## Files Created

### Core Monitoring Tools
```
test/org/yawlfoundation/yawl/performance/monitoring/
├── LibraryUpdatePerformanceMonitor.java    (1,100 lines)
│   └── Captures baseline and current performance snapshots
│   └── Compares metrics and identifies regressions
│   └── Tracks 7 key performance areas
│
└── PerformanceMonitoringTest.java          (150 lines)
    └── Unit tests for monitoring framework
    └── Validates metric capture and comparison

test/org/yawlfoundation/yawl/performance/
├── EnginePerformanceBaseline.java          (Already exists)
│   └── Establishes performance targets
│   └── Tests: startup, latency, throughput, memory, GC
│
├── jmh/WorkflowExecutionBenchmark.java     (Already exists)
├── jmh/InterfaceBClientBenchmark.java      (Already exists)
├── jmh/MemoryUsageBenchmark.java           (Already exists)
└── jmh/AllBenchmarksRunner.java            (Already exists)
```

### Documentation
```
performance-reports/
└── README.md                               (500 lines)
    └── Detailed metric descriptions
    └── Usage workflows
    └── Regression thresholds
    └── Troubleshooting guides

PERFORMANCE-MONITORING.md                   (800 lines)
└── Complete performance monitoring guide
└── Before/after library update workflow
└── Regression severity levels
└── Optimization tips

run-performance-monitoring.sh               (Interactive script)
└── Menu-driven interface
└── Baseline capture
└── Current metrics capture
└── JMH benchmark execution
```

## Quick Start (3 Steps)

### 1. Capture Baseline (Before Update)
```bash
./run-performance-monitoring.sh
# Select option 1: Capture baseline
# Wait 5-10 minutes
# Result: performance-reports/baseline-YYYYMMDD-HHMMSS.txt
```

### 2. Update Libraries
```bash
# Edit pom.xml - update versions
vim pom.xml

# Example changes made:
# - hibernate.version: 6.6.42.Final
# - log4j.version: 2.25.3
# - jackson.version: 2.18.3
# - hikaricp.version: 7.0.2
```

### 3. Capture Current Metrics & Compare
```bash
./run-performance-monitoring.sh
# Select option 2: Capture current
# Wait 5-10 minutes
# Select option 6: Compare reports
# Review regression analysis
```

## Performance Metrics Tracked

| Metric | Target | Impact | Critical For |
|--------|--------|--------|--------------|
| Engine Startup | < 60s | Hibernate, Spring Boot | Production deployments |
| Case Launch p95 | < 500ms | Hibernate, HikariCP | User operations |
| Work Item Throughput | > 100 ops/sec | Database connections | Execution speed |
| Concurrent Throughput | > 100 cases/sec | Thread pools | High load |
| Memory Usage | < 512MB/1K cases | Hibernate cache, Jackson | Scalability |
| GC Pause Time | < 500ms | Object allocations | Latency |
| Thread Count | < 100 | Executors | Resource efficiency |

## Regression Severity

- **CRITICAL (>25%)**: STOP - Rollback required
- **MAJOR (10-25%)**: Investigation required before production
- **MODERATE (5-10%)**: Monitor in staging
- **MINOR (<5%)**: Document and accept

## Example Output

### Baseline Capture
```
=== BASELINE 1: Case Launch Latency ===
Results (n=1000):
  Min:    120 ms
  p50:    450 ms
  Avg:    465.23 ms
  p95:    480 ms (Target: <500 ms)
  p99:    520 ms
  Max:    680 ms
  Status: ✓ PASS

=== BASELINE 3: Concurrent Case Throughput ===
Results:
  Threads:    10
  Cases:      1000 (target: 1000)
  Duration:   9850 ms
  Throughput: 101.5 cases/sec (Target: >100)
  Status:     ✓ PASS
```

### Comparison Report
```
=== PERFORMANCE COMPARISON REPORT ===
Overall Impact: MODERATE - Some performance impact detected

Library Updates:
  hibernate.version: 6.5.1.Final → 6.6.42.Final
  log4j.version: 2.23.1 → 2.25.3
  jackson.version: 2.17.0 → 2.18.3

Detailed Metrics:
✓ Engine Startup Time: 58.5 → 59.2 sec (+1.2%) [MINOR]
• Case Launch p95: 480 → 495 ms (+3.1%) [MINOR]
⚠ Work Item Throughput: 110 → 95 ops/sec (-13.6%) [MAJOR]
✓ Memory Usage: 485 → 478 MB (-1.4%) [IMPROVEMENT]

⚠ CRITICAL REGRESSIONS:
  • Work item throughput degraded: -13.6%
```

## Recent Library Updates

### 2026-02-16: Java 25 Migration + Library Upgrades

**Updates:**
- Java 21 → 25 (pattern matching, virtual threads mature)
- Hibernate 6.5.1 → 6.6.42 (query generation improvements)
- Jakarta Servlet 5.0 → 6.1 (API updates)
- Log4j 2.23.1 → 2.25.3 (bug fixes, GraalVM support)
- Jackson 2.17.0 → 2.18.3 (optimizations)
- HikariCP 5.1.0 → 7.0.2 (connection management)

**Expected Impact:**
- Startup: Neutral to +2% (Java 25 compilation improvements)
- Latency: Neutral to -3% (Hibernate query optimizations)
- Throughput: Neutral to +5% (HikariCP improvements)
- Memory: Neutral to -5% (better GC in Java 25)

**To Monitor:**
- Hibernate query generation changes (check SQL logs)
- HikariCP connection pool behavior (monitor metrics)
- Virtual thread performance (if enabled)

## Detailed Usage Workflows

### Workflow 1: Pre-Update Baseline
```bash
# 1. Clean build
mvn clean compile

# 2. Run baseline tests
mvn test -Dtest=EnginePerformanceBaseline

# 3. Review results
cat performance-reports/baseline-*.txt

# 4. Verify all tests PASS
# Example:
#   ✓ Case Launch Latency: PASS (p95=480ms < 500ms target)
#   ✓ Work Item Throughput: PASS (110 ops/sec > 100 target)
#   ✓ Memory Usage: PASS (485MB < 512MB target)
```

### Workflow 2: Post-Update Analysis
```bash
# 1. Update pom.xml versions

# 2. Clean build with new libraries
mvn clean compile

# 3. Verify compilation success
# Fix any API incompatibilities

# 4. Run current metrics
mvn test -Dtest=EnginePerformanceBaseline

# 5. Compare results
diff -u performance-reports/baseline-*.txt \
        performance-reports/current-*.txt

# 6. Decision matrix:
#    - No regressions → Proceed to testing
#    - Minor/Moderate → Document and monitor
#    - Major → Investigate root cause
#    - Critical → Rollback or block production
```

### Workflow 3: Detailed Investigation (If Needed)
```bash
# For Hibernate query changes:
mvn test -Dhibernate.show_sql=true \
         -Dhibernate.format_sql=true \
         -Dtest=EnginePerformanceBaseline

# For memory analysis:
jmap -dump:live,format=b,file=heap.bin <pid>
# Analyze with Eclipse MAT

# For GC analysis:
mvn test -Dtest=EnginePerformanceBaseline \
         -Xlog:gc*:file=gc.log
# Review gc.log for pause patterns

# For detailed profiling:
mvn test -Dtest=AllBenchmarksRunner
# Review target/jmh-results.json
```

## Integration with Existing Tests

This framework complements existing YAWL tests:

```
Existing:
├── test/org/yawlfoundation/yawl/engine/*Test.java
│   └── Functional correctness tests
│
├── test/org/yawlfoundation/yawl/performance/PerformanceTestSuite.java
│   └── Basic performance smoke tests
│
└── test/org/yawlfoundation/yawl/performance/jmh/*Benchmark.java
    └── Detailed JMH benchmarks

New:
└── test/org/yawlfoundation/yawl/performance/monitoring/
    └── LibraryUpdatePerformanceMonitor.java
        └── Systematic before/after comparison
        └── Regression detection
        └── Impact analysis
```

## Troubleshooting Common Issues

### Issue: Baseline tests fail
```bash
# Check database connectivity
mvn test -Dtest=TestDatabaseConnection

# Verify H2 database
ls -lh ~/yawl-db/

# Check heap size
java -XX:+PrintFlagsFinal -version | grep HeapSize
```

### Issue: High memory usage
```bash
# Increase heap for testing
export MAVEN_OPTS="-Xms4g -Xmx8g"

# Check for memory leaks
mvn test -Dtest=MemoryUsageBenchmark
```

### Issue: Inconsistent results
```bash
# Ensure JVM warmup
# EnginePerformanceBaseline includes warmup phase

# Run multiple times and average
for i in {1..3}; do
    mvn test -Dtest=EnginePerformanceBaseline
    sleep 60
done
```

## Future Enhancements

### Planned Features
- [ ] Automated comparison in ComparisonReport
- [ ] JSON output format for metrics
- [ ] Prometheus metrics export
- [ ] CI/CD integration for automatic regression detection
- [ ] Historical trend analysis
- [ ] Performance dashboard

### CI/CD Integration (Planned)
```yaml
# .github/workflows/performance-regression.yml
on:
  pull_request:
    paths: ['**/pom.xml']

jobs:
  performance:
    - name: Compare performance
      run: ./scripts/check-performance-regression.sh
    - name: Fail if critical regression
      run: |
        if grep "CRITICAL" comparison.txt; then
          exit 1
        fi
```

## Decision Matrix

| Scenario | Action |
|----------|--------|
| All metrics improved | ✓ Proceed with deployment |
| Minor regressions only | ✓ Document and deploy |
| Moderate regressions | ⚠ Test in staging, monitor |
| Major regression (1 metric) | ⚠ Investigate root cause first |
| Major regressions (>2 metrics) | ✗ Block deployment |
| Critical regression | ✗ Rollback immediately |

## Performance Budget

Acceptable trade-offs for security/features:

| Update Type | Max Regression |
|-------------|----------------|
| Security patch | 5% |
| Bug fix | 10% |
| Feature enhancement | 15% |
| Major version upgrade | 20% (with investigation) |

## Contact

**Performance Questions:**
- Email: performance@yawlfoundation.org
- GitHub: https://github.com/yawlfoundation/yawl/issues
- Tag with: `performance`, `regression`, `library-update`

**Report Issues:**
Include:
1. Baseline report
2. Current report
3. Library versions changed
4. Regression percentage
5. Environment details (OS, JVM, hardware)

## Version History

**v1.0.0 - 2026-02-16**
- Initial performance monitoring framework
- Support for Java 25 migration
- Baseline metrics for 7 key performance areas
- Before/after comparison workflow
- Interactive script interface

## License

Copyright (c) 2004-2026 The YAWL Foundation
Licensed under GNU Lesser General Public License

---

**Last Updated**: 2026-02-16
**Author**: YAWL Performance Team
**Version**: 1.0.0
