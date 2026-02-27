# YAWL v6.0.0-GA Stress Testing Guide

## Table of Contents

1. [Overview](#overview)
2. [Test Categories](#test-categories)
3. [Running Stress Tests](#running-stress-tests)
4. [Test Configuration](#test-configuration)
5. [Production Workload Simulation](#production-workload-simulation)
6. [Memory Leak Detection](#memory-leak-detection)
7. [Multi-Tenant Testing](#multi-tenant-testing)
8. [Long-Running Tests](#long-running-tests)
9. [Analyzing Results](#analyzing-results)
10. [Troubleshooting](#troubleshooting)

---

## Overview

Stress testing validates YAWL's behavior under extreme conditions, ensuring the system remains stable and performant when pushed beyond normal operational limits. The stress test suite covers:

- **Production Workload Simulation**: Realistic workflow distribution patterns
- **Memory Leak Detection**: Long-running memory trend analysis
- **Multi-Tenant Isolation**: Resource sharing and tenant isolation
- **Long-Running Stability**: 24-hour continuous operation testing
- **Edge Cases**: Extreme concurrency and payload sizes

### Test Suite Architecture

```
test/org/yawlfoundation/yawl/performance/stress/
├── StressTestSuite.java              # Aggregates all stress tests
├── ProductionWorkloadStressTest.java # Production simulation
├── MemoryLeakDetectionTest.java      # Memory leak detection
├── MultiTenantStressTest.java        # Multi-tenant isolation
├── LongRunningStressTest.java        # 24-hour stability
├── DatabaseConnectionStorm.java      # DB connection exhaustion
├── MemoryPressureTest.java           # Memory pressure scenarios
├── WorkItemFloodTest.java            # Work item flooding
└── CaseCreationStressTest.java       # Case creation storm
```

---

## Test Categories

### 1. Production Workload Stress Test

Simulates realistic production workload with configurable distribution:

| Workflow Type | Distribution | Characteristics |
|---------------|--------------|-----------------|
| Simple | 60% | Single-task workflows, <1s execution |
| Complex | 30% | Multi-task workflows, parallel branches |
| Priority | 10% | High-priority workflows, SLA tracking |

**Location**: `/test/org/yawlfoundation/yawl/performance/stress/ProductionWorkloadStressTest.java`

### 2. Memory Leak Detection Test

Detects memory leaks during sustained load:

- Continuous case creation over extended periods
- Memory trend analysis with linear regression
- Threshold-based alerts for memory growth
- GC behavior monitoring

**Location**: `/test/org/yawlfoundation/yawl/performance/stress/MemoryLeakDetectionTest.java`

### 3. Multi-Tenant Stress Test

Validates tenant isolation under load:

- 100 concurrent tenants
- Per-tenant resource quotas
- Cross-tenant isolation verification
- Fair resource distribution

**Location**: `/test/org/yawlfoundation/yawl/performance/stress/MultiTenantStressTest.java`

### 4. Long-Running Stress Test

24-hour continuous operation validation:

- Sustained throughput measurement
- Resource consumption trends
- Error rate monitoring over time
- Recovery from transient failures

**Location**: `/test/org/yawlfoundation/yawl/performance/stress/LongRunningStressTest.java`

---

## Running Stress Tests

### Prerequisites

```bash
# System requirements
- Java 25+ with --enable-preview
- 16GB+ available memory
- SSD storage recommended
- Dedicated machine preferred (no other workloads)
```

### Quick Commands

```bash
# Run all stress tests
mvn test -Dtest=StressTestSuite -Dgroups=stress

# Run individual test classes
mvn test -Dtest=ProductionWorkloadStressTest -Dgroups=stress
mvn test -Dtest=MemoryLeakDetectionTest -Dgroups=stress
mvn test -Dtest=MultiTenantStressTest -Dgroups=stress
mvn test -Dtest=LongRunningStressTest -Dgroups=stress

# Run with increased memory
mvn test -Dtest=StressTestSuite -Dgroups=stress \
  -DargLine="-Xms8g -Xmx16g -XX:+UseZGC --enable-preview"
```

### Running Production Workload Test

```bash
# Standard production simulation
mvn test -Dtest=ProductionWorkloadStressTest -Dgroups=stress

# With custom configuration
mvn test -Dtest=ProductionWorkloadStressTest \
  -Dcases=5000 \
  -Dworkers=100 \
  -Dduration=60 \
  -Dgroups=stress
```

### Running Memory Leak Detection

```bash
# Short leak detection (10 minutes)
mvn test -Dtest=MemoryLeakDetectionTest \
  -Dduration=600 \
  -Dgroups=stress

# Extended leak detection (1 hour)
mvn test -Dtest=MemoryLeakDetectionTest \
  -Dduration=3600 \
  -Dgroups=stress
```

### Running Long-Running Tests

```bash
# 24-hour test (use nohup for background execution)
nohup mvn test -Dtest=LongRunningStressTest \
  -Dduration=86400 \
  -Dgroups=stress \
  > long-running-test.log 2>&1 &

# Monitor progress
tail -f long-running-test.log
```

---

## Test Configuration

### Production Workload Configuration

```java
// Default configuration in ProductionWorkloadStressTest.java
private static final int SIMPLE_WORKFLOW_RATIO = 60;
private static final int COMPLEX_WORKFLOW_RATIO = 30;
private static final int PRIORITY_WORKFLOW_RATIO = 10;
private static final int TOTAL_CASES = 1000;
private static final int CONCURRENT_WORKERS = 50;
private static final int TEST_DURATION_MINUTES = 30;
```

### Ramp-Up/Down Scenarios

The production workload test simulates realistic daily patterns:

| Phase | Workers | Case Duration |
|-------|---------|---------------|
| Morning | 10 | 5s |
| Ramp-up | 50 | 3s |
| Peak | 100 | 2s |
| Sustained | 80 | 2s |
| Evening | 30 | 4s |
| Night | 5 | 10s |

### Memory Leak Detection Configuration

```java
// Memory leak detection thresholds
private static final long INITIAL_MEMORY_THRESHOLD_MB = 100;
private static final long GROWTH_RATE_THRESHOLD_MB_PER_MIN = 5;
private static final double LEAK_CONFIDENCE_THRESHOLD = 0.95;
private static final int SAMPLE_INTERVAL_SECONDS = 10;
```

### Multi-Tenant Configuration

```java
// Multi-tenant test configuration
private static final int TENANT_COUNT = 100;
private static final int CASES_PER_TENANT = 50;
private static final long TENANT_MEMORY_LIMIT_MB = 50;
private static final int ISOLATION_CHECK_INTERVAL_MS = 1000;
```

---

## Production Workload Simulation

### Workflow Distribution

The production workload test creates a realistic mix of workflow types:

```java
private String selectWorkflowType() {
    Random random = new Random();
    int value = random.nextInt(100);
    
    if (value < SIMPLE_WORKFLOW_RATIO) {
        return "simple";      // 60% - Fast, single-task
    } else if (value < SIMPLE_WORKFLOW_RATIO + COMPLEX_WORKFLOW_RATIO) {
        return "complex";     // 30% - Multi-task, parallel
    } else {
        return "priority";    // 10% - High-priority, SLA
    }
}
```

### Workflow Specifications

#### Simple Workflow

```xml
<specification id="SimpleWorkflow" version="1.0">
    <name>Simple Process</name>
    <process id="simpleProcess" name="Simple Process">
        <start id="start"/>
        <task id="task1" name="Simple Task"/>
        <end id="end"/>
        <flow from="start" to="task1"/>
        <flow from="task1" to="end"/>
    </process>
</specification>
```

#### Complex Workflow

```xml
<specification id="ComplexWorkflow" version="1.0">
    <name>Complex Process</name>
    <process id="complexProcess" name="Complex Process">
        <start id="start"/>
        <task id="task1" name="Initial Task"/>
        <task id="task2" name="Parallel Task 1"/>
        <task id="task3" name="Parallel Task 2"/>
        <task id="task4" name="Sync Task"/>
        <task id="task5" name="Final Task"/>
        <end id="end"/>
        <!-- Parallel split and join -->
        <flow from="start" to="task1"/>
        <flow from="task1" to="task2"/>
        <flow from="task1" to="task3"/>
        <flow from="task2" to="task4"/>
        <flow from="task3" to="task4"/>
        <flow from="task4" to="task5"/>
        <flow from="task5" to="end"/>
    </process>
</specification>
```

### Performance Targets

| Workflow Type | Average Duration Target | P95 Target |
|---------------|------------------------|------------|
| Simple | <1000ms | <2000ms |
| Complex | <3000ms | <5000ms |
| Priority | <500ms | <1000ms |

---

## Memory Leak Detection

### Detection Methodology

1. **Baseline Measurement**: Capture initial memory state
2. **Continuous Sampling**: Record memory at regular intervals
3. **Trend Analysis**: Apply linear regression to memory growth
4. **Threshold Comparison**: Alert if growth exceeds thresholds
5. **GC Analysis**: Monitor GC behavior and effectiveness

### Sample Output

```
Memory Leak Detection Test
==========================
Initial Memory: 256 MB
Test Duration: 60 minutes
Sample Interval: 10 seconds

Memory Samples (MB): [256, 258, 261, 259, 262, 264, ...]

Linear Regression Analysis:
  Slope: 0.05 MB/minute
  R-squared: 0.85
  Trend: STABLE

GC Events: 245
  Minor GC: 240 (avg pause: 5ms)
  Major GC: 5 (avg pause: 150ms)

Result: PASS - No memory leak detected
```

### Leak Detection Criteria

| Criterion | Pass Threshold | Fail Threshold |
|-----------|----------------|----------------|
| Memory Growth Rate | <1 MB/min | >5 MB/min |
| Trend Correlation | <0.7 (random) | >0.95 (deterministic) |
| GC Recovery | >80% freed | <50% freed |
| Memory Churn | Stable | Increasing |

---

## Multi-Tenant Testing

### Tenant Isolation Validation

The multi-tenant stress test validates:

1. **Data Isolation**: Tenants cannot access each other's data
2. **Resource Fairness**: Resources distributed fairly among tenants
3. **Performance Isolation**: One tenant's load doesn't affect others
4. **Quota Enforcement**: Memory and CPU limits per tenant

### Test Configuration

```java
@Test
void testMultiTenantIsolation() throws Exception {
    String[] tenants = {"tenant-1", "tenant-2", ..., "tenant-100"};
    int messagesPerTenant = 20;
    
    // Execute concurrent operations per tenant
    for (String tenant : tenants) {
        for (int i = 0; i < messagesPerTenant; i++) {
            // Send tenant-isolated request
            sendRequest(tenant, i);
        }
    }
    
    // Verify isolation
    verifyNoCrossTenantAccess();
    verifyFairResourceDistribution();
}
```

### Isolation Metrics

| Metric | Target | Description |
|--------|--------|-------------|
| Cross-tenant access | 0 | No tenant sees another's data |
| Resource fairness | >90% | Equal resource distribution |
| Latency variance | <20% | Similar performance per tenant |
| Throughput fairness | >95% | Equal throughput per tenant |

---

## Long-Running Tests

### 24-Hour Test Configuration

```java
@Test
@Timeout(value = 24, unit = TimeUnit.HOURS)
void test24HourContinuousOperation() throws Exception {
    Instant testStart = Instant.now();
    Duration testDuration = Duration.ofHours(24);
    
    while (Duration.between(testStart, Instant.now()).compareTo(testDuration) < 0) {
        // Execute sustained workload
        executeWorkloadBatch();
        
        // Record metrics
        recordMetrics();
        
        // Check for degradation
        assertNoPerformanceDegradation();
        
        // Brief pause between batches
        Thread.sleep(1000);
    }
}
```

### Monitored Metrics

| Metric | Collection Interval | Alert Threshold |
|--------|---------------------|-----------------|
| Throughput | 1 minute | >10% degradation |
| Latency P95 | 1 minute | >50% increase |
| Error Rate | 1 minute | >0.1% |
| Memory Usage | 10 seconds | >90% of max |
| GC Pause Time | Per GC event | >500ms |
| Thread Count | 10 seconds | >10k threads |

### Expected Results

```
24-Hour Stress Test Summary
===========================
Duration: 24:00:00
Total Cases Processed: 1,234,567
Average Throughput: 14.3 cases/second
Error Rate: 0.003%
Memory High Water Mark: 6.2 GB (of 8 GB)
GC Overhead: 2.3%

Hourly Breakdown:
  Hour 1:  52,000 cases, 14.4/s, 0.002% errors
  Hour 6:  51,500 cases, 14.3/s, 0.003% errors
  Hour 12: 51,800 cases, 14.4/s, 0.002% errors
  Hour 18: 51,200 cases, 14.2/s, 0.004% errors
  Hour 24: 51,600 cases, 14.3/s, 0.003% errors

Result: PASS - Stable performance over 24 hours
```

---

## Analyzing Results

### Test Report Format

Stress tests generate comprehensive reports:

```markdown
# YAWL Stress Test Report

**Test Run**: 2026-02-26T10:30:00Z
**Duration**: 30 minutes
**Configuration**: Production Workload

## Summary

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Total Cases | 1,000 | 1,000 | PASS |
| Success Rate | 98.5% | >95% | PASS |
| Average Latency | 245ms | <500ms | PASS |
| P95 Latency | 450ms | <1000ms | PASS |
| Memory Usage | 3.2 GB | <4 GB | PASS |

## Workload Distribution

| Type | Count | Percentage | Target |
|------|-------|------------|--------|
| Simple | 602 | 60.2% | 60% |
| Complex | 298 | 29.8% | 30% |
| Priority | 100 | 10.0% | 10% |

## Performance Over Time

[Chart: Throughput vs Time]
[Chart: Latency vs Time]
[Chart: Memory vs Time]
```

### Interpreting Results

#### Success Indicators

- **Throughput**: Stable or improving over time
- **Latency**: P95 within target, no increasing trend
- **Memory**: Stable with periodic GC, no continuous growth
- **Errors**: <0.1% error rate, no error bursts

#### Warning Signs

- **Memory Growth**: Continuous upward trend indicates leak
- **Latency Degradation**: Increasing latency over time
- **Error Bursts**: Sudden error spikes indicate instability
- **Thread Proliferation**: Growing thread count

---

## Troubleshooting

### Common Issues

#### 1. OutOfMemoryError

```bash
# Increase heap size
-DargLine="-Xms16g -Xmx32g -XX:+UseZGC"

# Enable heap dump on OOM
-DargLine="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heap-dump.hprof"
```

#### 2. Test Timeout

```bash
# Increase JUnit timeout
@Timeout(value = 2, unit = TimeUnit.HOURS)

# Or disable timeout for debugging
@Test(timeout = 0)
```

#### 3. Slow Test Execution

```bash
# Reduce test data size
-Dcases=100 -Dworkers=10

# Skip expensive validations
-DskipValidation=true
```

#### 4. Flaky Tests

```bash
# Add retry logic
@Retry(maxAttempts = 3, minDelay = 1000)

# Stabilize timing-sensitive tests
@RepeatedTest(5)
```

### Debug Mode

```bash
# Enable verbose logging
-DlogLevel=DEBUG

# Enable GC logging
-Xlog:gc*:file=gc.log:time,level,tags

# Enable JFR recording
-XX:StartFlightRecording=duration=60s,filename=recording.jfr
```

---

## Best Practices

### 1. Test Environment

- Use dedicated machines for stress testing
- Disable unnecessary services and daemons
- Ensure consistent hardware configuration
- Monitor system resources during tests

### 2. Test Data

- Use production-like data sizes and distributions
- Include edge cases (empty, maximum, boundary values)
- Avoid synthetic data that doesn't match production patterns
- Clean up test data between runs

### 3. Test Duration

- Run tests long enough to detect memory leaks (30+ minutes)
- Include warm-up period before measurements
- Allow for JVM JIT compilation to stabilize
- Consider time-of-day effects for long-running tests

### 4. Result Interpretation

- Look for trends, not just point-in-time values
- Compare against established baselines
- Consider statistical significance of variations
- Document any anomalies or unexpected behavior

---

## Related Documentation

- [BENCHMARKS.md](./BENCHMARKS.md) - JMH benchmark guide
- [CHAOS-ENGINEERING.md](./CHAOS-ENGINEERING.md) - Chaos testing guide
- [PERFORMANCE-GUIDE.md](./PERFORMANCE-GUIDE.md) - Performance optimization

---

*Generated for YAWL v6.0.0-GA | Last updated: 2026-02-26*
