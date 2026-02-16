# YAWL Performance Testing Guide

## Overview

This guide provides comprehensive instructions for executing, analyzing, and interpreting performance tests for the YAWL workflow engine.

## Quick Start

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

## Test Categories

### 1. Baseline Measurements

**Purpose**: Establish quantitative performance characteristics

**Tests**:
- `testCaseLaunchLatency`: Case creation latency (p50, p95, p99)
- `testWorkItemCompletionLatency`: Work item completion time
- `testConcurrentThroughput`: Maximum cases/second
- `testMemoryUsage`: Memory consumption patterns
- `testEngineStartupTime`: Cold start performance

**Run**:
```bash
mvn test -Dtest=EnginePerformanceBaseline
```

**Expected Duration**: ~10 minutes

---

### 2. Load Tests

**Purpose**: Validate performance under production-like load

**Tests**:
- `testSustainedLoad`: 50 users, 5 minutes
- `testBurstLoad`: 100 users, 1 minute  
- `testRampUp`: 10→50 users, 2 minutes

**Run**:
```bash
mvn test -Dtest=LoadTestSuite
```

**Expected Duration**: ~10 minutes

---

### 3. Scalability Tests

**Purpose**: Understand scaling characteristics

**Tests**:
- `testCaseCountScalability`: Linear scaling verification
- `testMemoryEfficiency`: Memory per case consistency
- `testLoadRecovery`: Recovery from high load

**Run**:
```bash
mvn test -Dtest=ScalabilityTest
```

**Expected Duration**: ~15 minutes

---

## Running Performance Tests

### Prerequisites

1. **JVM Configuration**:
```bash
export MAVEN_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

2. **Database Setup**:
```bash
# H2 in-memory (for testing)
# No setup required

# PostgreSQL (for production-like testing)
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=yawl \
  -e POSTGRES_USER=yawl \
  -e POSTGRES_PASSWORD=yawl \
  postgres:16
```

3. **System Resources**:
- Minimum 4GB free RAM
- Minimum 4 CPU cores
- Low system load (< 30%)

### Execution

```bash
# Full suite
./scripts/run-performance-tests.sh --full

# Results saved to: test-results/performance/full-YYYYMMDD_HHMMSS.log
```

### Interpreting Results

**Success Criteria**:
- All tests show "✓ PASS"
- No OutOfMemoryError exceptions
- Success rate > 99% for sustained load
- Success rate > 95% for burst load

**Example Output**:
```
=== BASELINE 1: Case Launch Latency ===
Results (n=1000):
  Min:    5 ms
  p50:    45 ms
  Avg:    62.34 ms
  p95:    280 ms (Target: <500 ms)
  p99:    450 ms
  Max:    820 ms
  Status: ✓ PASS
```

---

## Performance Profiling

### CPU Profiling

**Using async-profiler**:
```bash
# Download async-profiler
wget https://github.com/async-profiler/async-profiler/releases/download/v2.9/async-profiler-2.9-linux-x64.tar.gz
tar -xzf async-profiler-2.9-linux-x64.tar.gz

# Start profiling
./async-profiler-2.9-linux-x64/profiler.sh -d 60 -f flamegraph.html <pid>

# View flamegraph.html in browser
```

**Using JProfiler**:
```bash
# Start application with JProfiler agent
java -agentpath:/path/to/jprofiler/bin/linux-x64/libjprofilerti.so=port=8849 \
  -jar yawl.jar
```

---

### Memory Profiling

**Heap Dump Analysis**:
```bash
# Take heap dump
jmap -dump:live,format=b,file=heap.bin <pid>

# Analyze with Eclipse MAT
# Download from: https://www.eclipse.org/mat/

# Or use jhat (built-in)
jhat -J-Xmx4g heap.bin
# Open http://localhost:7000
```

**Memory Leak Detection**:
```bash
# Take baseline heap dump
jmap -dump:live,format=b,file=heap-before.bin <pid>

# Run load test
./scripts/run-performance-tests.sh --load-only

# Take after heap dump
jmap -dump:live,format=b,file=heap-after.bin <pid>

# Compare in MAT
# Look for objects that grew significantly
```

---

### GC Analysis

**Enable GC Logging**:
```bash
export MAVEN_OPTS="$MAVEN_OPTS \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xloggc:logs/gc.log \
  -XX:+UseGCLogFileRotation \
  -XX:NumberOfGCLogFiles=10 \
  -XX:GCLogFileSize=10M"
```

**Analyze GC Logs**:
```bash
# Using GCViewer
java -jar gcviewer.jar logs/gc.log

# Key metrics:
# - Full GC frequency (target: < 1 per hour)
# - GC pause time (target: < 200ms p95)
# - Heap occupancy after GC (target: < 70%)
```

---

## Database Performance Testing

### Query Performance

**Enable Query Logging**:
```properties
# Add to hibernate.properties
hibernate.show_sql=true
hibernate.format_sql=true
hibernate.use_sql_comments=true
```

**Slow Query Detection**:
```sql
-- PostgreSQL
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
WHERE mean_exec_time > 50
ORDER BY mean_exec_time DESC
LIMIT 20;

-- MySQL
SELECT * FROM mysql.slow_log
WHERE query_time > 0.05
ORDER BY query_time DESC
LIMIT 20;
```

### Connection Pool Monitoring

**HikariCP Metrics**:
```java
HikariPoolMXBean poolProxy = 
    (HikariPoolMXBean) ManagementFactory
        .getPlatformMBeanServer()
        .getAttribute(
            new ObjectName("com.zaxxer.hikari:type=Pool (HikariPool-1)"),
            "MBean"
        );

System.out.println("Active: " + poolProxy.getActiveConnections());
System.out.println("Idle: " + poolProxy.getIdleConnections());
System.out.println("Total: " + poolProxy.getTotalConnections());
System.out.println("Waiting: " + poolProxy.getThreadsAwaitingConnection());
```

---

## Load Testing Tools

### Apache Bench (ab)

```bash
# Simple load test
ab -n 10000 -c 100 http://localhost:8080/yawl/ib?action=checkOut

# Results:
# - Requests per second
# - Time per request
# - Connection times (p50, p95, p99)
```

### Apache JMeter

**Create Test Plan**:
```xml
<!-- See test/performance/yawl-load-test.jmx -->
```

**Run**:
```bash
# GUI mode (for development)
jmeter -t test/performance/yawl-load-test.jmx

# CLI mode (for CI/CD)
jmeter -n -t test/performance/yawl-load-test.jmx \
  -l results.jtl \
  -e -o report/

# View report/index.html
```

### Gatling

**Create Simulation**:
```scala
// See test/performance/YawlSimulation.scala
```

**Run**:
```bash
mvn gatling:test
```

---

## Continuous Performance Testing

### CI/CD Integration

**GitHub Actions**:
```yaml
# .github/workflows/performance.yml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * 0'  # Weekly on Sunday 2am
  workflow_dispatch:

jobs:
  performance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
      
      - name: Run Performance Tests
        run: ./scripts/run-performance-tests.sh --full
      
      - name: Archive Results
        uses: actions/upload-artifact@v4
        with:
          name: performance-results
          path: test-results/performance/
```

### Performance Regression Detection

**Automated Comparison**:
```bash
# scripts/compare-performance.sh
#!/bin/bash

BASELINE="baseline-v5.2.0.log"
CURRENT="current.log"

# Extract p95 latency
BASELINE_P95=$(grep "p95:" $BASELINE | awk '{print $2}' | sed 's/ms//')
CURRENT_P95=$(grep "p95:" $CURRENT | awk '{print $2}' | sed 's/ms//')

# Calculate degradation
DEGRADATION=$(echo "scale=2; ($CURRENT_P95 - $BASELINE_P95) / $BASELINE_P95 * 100" | bc)

echo "Baseline p95: ${BASELINE_P95}ms"
echo "Current p95: ${CURRENT_P95}ms"
echo "Degradation: ${DEGRADATION}%"

if (( $(echo "$DEGRADATION > 10" | bc -l) )); then
    echo "❌ Performance regression detected (> 10%)"
    exit 1
else
    echo "✅ Performance within acceptable range"
    exit 0
fi
```

---

## Troubleshooting

### High Latency

**Symptoms**:
- p95 > 1000ms
- Slow response times

**Diagnosis**:
```bash
# Check GC pauses
grep "Full GC" logs/gc.log

# Check database queries
grep "took.*ms" logs/application.log | sort -n

# Check connection pool
# Look for "Waiting for connection" warnings
```

**Solutions**:
- Tune GC (reduce pause times)
- Optimize slow queries
- Increase connection pool size
- Add database indexes

---

### Low Throughput

**Symptoms**:
- < 100 cases/sec
- Thread pool saturation

**Diagnosis**:
```bash
# Thread dump
jstack <pid> > thread-dump.txt

# Look for blocked threads
grep "BLOCKED" thread-dump.txt | wc -l

# Check CPU usage
top -H -p <pid>
```

**Solutions**:
- Increase thread pool size
- Remove synchronization bottlenecks
- Scale horizontally
- Optimize critical path

---

### Memory Issues

**Symptoms**:
- OutOfMemoryError
- High GC frequency
- Heap usage > 80%

**Diagnosis**:
```bash
# Heap histogram
jmap -histo <pid> | head -20

# Find memory leaks
jmap -dump:live,format=b,file=heap.bin <pid>
# Analyze in Eclipse MAT
```

**Solutions**:
- Increase heap size
- Fix memory leaks
- Reduce object creation
- Enable compressed OOPs

---

## Best Practices

### Before Testing

1. **Isolate Environment**
   - Dedicated test server
   - No other applications running
   - Consistent network conditions

2. **Warm Up JVM**
   - Run warmup iterations
   - Allow JIT compilation
   - Stabilize performance

3. **Clean State**
   - Clear database
   - Restart JVM
   - Reset caches

### During Testing

1. **Monitor System**
   - CPU usage
   - Memory usage
   - Network I/O
   - Disk I/O

2. **Collect Metrics**
   - Application logs
   - GC logs
   - Database logs
   - System metrics

3. **Avoid Interference**
   - Disable antivirus
   - Disable background tasks
   - Lock CPU frequency

### After Testing

1. **Analyze Results**
   - Compare to baseline
   - Identify regressions
   - Document findings

2. **Archive Results**
   - Store logs
   - Version results
   - Track trends

3. **Update Baselines**
   - When intentional changes made
   - Quarterly reviews
   - Major version updates

---

## References

- [Performance Baselines](PERFORMANCE_BASELINES.md)
- [JVM Tuning Guide](JVM_TUNING.md)
- [Database Optimization](DATABASE_OPTIMIZATION.md)
- [Monitoring and Alerting](MONITORING.md)

---

## Appendix: Performance Test Checklist

**Pre-Test**:
- [ ] JVM configured (heap, GC)
- [ ] Database initialized
- [ ] System resources available
- [ ] Monitoring enabled
- [ ] Baseline established

**Execution**:
- [ ] Warmup completed
- [ ] Tests run to completion
- [ ] No errors/exceptions
- [ ] Metrics collected

**Analysis**:
- [ ] Results compared to baseline
- [ ] Performance targets met
- [ ] Regressions identified
- [ ] Report generated

**Follow-up**:
- [ ] Results archived
- [ ] Findings documented
- [ ] Issues created (if needed)
- [ ] Baseline updated (if needed)

---

**Last Updated**: 2026-02-16
**Version**: 5.2.0
