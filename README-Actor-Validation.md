# YAWL Actor Model Validation Suite

## Phase 2: 10M Agent Scalability Validation

This comprehensive validation suite validates YAWL's actor model scalability claims for supporting 10M concurrent agents with precise metrics and failure mode analysis.

## Overview

The validation suite consists of three main test categories:

1. **Scale Testing** - Validates performance at critical scale points (100K, 500K, 1M, 2M, 5M, 10M agents)
2. **Performance Validation** - Confirms p99 latency <100ms and message delivery rate >10K/sec/agent
3. **Stress & Stability Testing** - Validates 24-hour stability and handling of extreme scenarios

## Quick Start

### Prerequisites

- **Java**: 21+ with Virtual Threads support
- **Memory**: Minimum 64GB RAM (recommended for 10M agent testing)
- **CPU**: 8+ cores recommended
- **Disk**: 50GB+ free space for test artifacts

### Running the Validation Suite

```bash
# Run comprehensive validation
./scripts/run-actor-validation.sh

# Run specific test category
./scripts/run-actor-validation.sh --scale-only
./scripts/run-actor-validation.sh --performance-only
./scripts/run-actor-validation.sh --stress-only
```

## Test Categories

### 1. Scale Testing

Validates the system's ability to handle increasing numbers of agents while maintaining performance.

**Test Points:**
- 100,000 agents
- 500,000 agents
- 1,000,000 agents
- 2,000,000 agents
- 5,000,000 agents
- 10,000,000 agents

**Metrics Tracked:**
- Heap consumption per agent (target: ≤150 bytes)
- GC pressure and pause times
- Carrier thread utilization
- Memory scaling linearity

### 2. Performance Validation

Validates performance claims under various load conditions.

**Performance Thresholds:**
- p99 scheduling latency <100ms
- Message delivery rate >10,000/second/agent
- Zero message loss at any scale
- Memory scaling linearity (≤10% deviation)

**Tests Include:**
- Latency validation at all scales
- Message delivery rate measurement
- Message loss prevention
- Memory linearity verification
- Carrier thread utilization
- Scheduling throughput

### 3. Stress & Stability Testing

Validates the system's resilience under extreme conditions.

**Stress Scenarios:**
- 24-hour stability test at 5M agents
- Message flood testing (100K messages/second total)
- Burst pattern testing (10x load spikes)
- Memory leak verification
- Mixed stress scenarios
- Recovery stress testing

## Expected Results

### Scale Testing Results

| Scale | Heap/Agent | GC Pauses | Thread Util | Status |
|-------|------------|-----------|-------------|---------|
| 100K  | ≤150 bytes | <10K      | <80%        | ✓ PASS  |
| 500K  | ≤150 bytes | <50K      | <80%        | ✓ PASS  |
| 1M    | ≤150 bytes | <100K     | <80%        | ✓ PASS  |
| 2M    | ≤150 bytes | <200K     | <80%        | ✓ PASS  |
| 5M    | ≤150 bytes | <500K     | <85%        | ✓ PASS  |
| 10M   | ≤150 bytes | <1M       | <85%        | ✓ PASS  |

### Performance Validation Results

| Metric | Threshold | Expected | Status |
|--------|-----------|----------|---------|
| p99 Latency | <100ms | <100ms | ✓ PASS |
| Message Rate | >10K/sec/agent | >12K/sec | ✓ PASS |
| Message Loss | 0% | 0% | ✓ PASS |
| Memory Linearity | ≤10% deviation | ≤10% | ✓ PASS |

### Stress Testing Results

| Test | Duration | Recovery Rate | Status |
|------|----------|---------------|---------|
| 5M Agent Stability | 24 hours | 100% | ✓ PASS |
| Message Flood | 1 minute | 99.99% | ✓ PASS |
| Burst Pattern | 2 minutes | 98.5% | ✓ PASS |
| Memory Leaks | 3 phases | 0% growth | ✓ PASS |

## Running Individual Tests

### Scale Testing

```bash
# Test 100K scale
mvn test -Dtest=ActorModelScaleTest#test100KAgents

# Test 10M scale (requires 64GB heap)
mvn test -Dtest=ActorModelScaleTest#test10MAgents -Djava_OPTS="-Xms32g -Xmx64g"
```

### Performance Validation

```bash
# Test latency at 1M scale
mvn test -Dtest=ActorModelPerformanceTest#testLatency1M

# Test message delivery rate
mvn test -Dtest=ActorModelPerformanceTest#testMessageDeliveryRate
```

### Stress Testing

```bash
# Run 24-hour stability test at 5M agents
mvn test -Dtest=ActorModelStressTest#testStability5M24Hours

# Run message flood test
mvn test -Dtest=ActorModelStressTest#testMessageFlood
```

## Monitoring and Reports

### Test Reports

All tests generate detailed JSON reports in the following locations:

- `reports/validation/scale_tests/` - Scale test results
- `reports/validation/performance/` - Performance test results
- `reports/validation/stress_tests/` - Stress test results
- `reports/validation/final_validation_report.html` - Comprehensive report

### Metrics Collection

The `MetricsCollector` class provides comprehensive real-time metrics:

```java
// Initialize metrics collector
MetricsCollector metrics = new MetricsCollector();

// Start test
metrics.startTest();

// Record various metrics
metrics.recordLatency(System.nanoTime());
metrics.recordMessageEvent("sent", "agent123", 1000000);
metrics.recordGcEvent(50000000);

// End test and generate report
metrics.endTest();
String report = metrics.generateReport("TestName", "Scale");
```

## Test Configuration

### JVM Options

```bash
# For scale testing
export JAVA_OPTS="-Xms16g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# For 10M agent testing
export JAVA_OPTS="-Xms32g -Xmx64g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### Virtual Thread Configuration

```bash
# Enable virtual thread scheduling
export JAVA_OPTS="$JAVA_OPTS -Djava.util.concurrent.ForkJoinPool.common.parallelism=16"
```

## Failure Mode Analysis

The validation suite tests various failure scenarios:

### Memory Failures
- Heap exhaustion
- GC pressure spikes
- Memory leaks

### Concurrency Failures
- Thread starvation
- Lock contention
- Race conditions

### Network Failures
- Message loss
- Network partitions
- Latency spikes

### Application Failures
- Actor crashes
- State corruption
- Recovery failures

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**
   - Increase heap size with `-Xmx`
   - Reduce test scale
   - Check for memory leaks

2. **GC Pauses Too Long**
   - Use G1GC with `-XX:MaxGCPauseMillis`
   - Adjust heap sizes
   - Reduce test concurrency

3. **Thread Starvation**
   - Increase virtual thread count
   - Reduce carrier thread load
   - Optimize thread pool configuration

### Performance Tuning

1. **Heap Sizing**
   - Scale tests: 2x agent count bytes
   - Production: 4-8x peak expected load

2. **GC Configuration**
   - G1GC recommended for large heaps
   - Target 200ms max GC pause

3. **Thread Configuration**
   - Virtual threads for agent execution
   - Carrier thread pool optimized for CPU cores

## Integration Guide

### CI/CD Integration

```yaml
# GitHub Actions example
- name: Run Actor Validation
  run: |
    chmod +x scripts/run-actor-validation.sh
    ./scripts/run-actor-validation.sh

- name: Upload Reports
  uses: actions/upload-artifact@v3
  with:
    name: validation-reports
    path: reports/
```

### Post-Validation Steps

1. **Report Analysis**
   - Review comprehensive HTML report
   - Check JSON metrics files
   - Validate against targets

2. **Performance Baseline**
   - Establish performance baselines
   - Set up alerting thresholds
   - Configure monitoring

3. **Production Deployment**
   - Scale test in staging environment
   - Validate production configuration
   - Implement monitoring and alerting

## Support

For issues or questions:
- Review test logs in `reports/validation/logs/`
- Check JVM performance metrics
- Validate system requirements
- Contact YAWL development team

## References

- [YAWL Architecture Documentation](../docs/architecture/)
- [Actor Model Implementation Guide](../docs/actor-model/)
- [Performance Tuning Guide](../docs/performance/)
- [Validation Methodology](../docs/validation/)