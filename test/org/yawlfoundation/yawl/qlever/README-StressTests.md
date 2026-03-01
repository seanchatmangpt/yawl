# QLever Stress Tests

This directory contains comprehensive stress tests for the YAWL QLever engine designed to identify breaking points, performance bottlenecks, and system limits under various workload conditions.

## Overview

The stress test suite provides:

1. **Concurrent Query Testing** - Measures throughput and latency under increasing load
2. **Memory Pressure Testing** - Identifies memory leaks and excessive memory usage
3. **Query Complexity Analysis** - Tests performance impact of different query types
4. **Timeout Detection** - Validates timeout mechanism for long-running queries
5. **Resource Exhaustion Testing** - Tests behavior with limited resources
6. **Rapid Open/Close Cycles** - Tests system resilience to frequent resource creation/destruction
7. **Mixed Workload Testing** - Simulates production-like mixed read/write scenarios
8. **Breaking Point Identification** - Finds system limits and failure thresholds

## Test Files

- `QLeverStressTest.java` - Main stress test suite with JUnit 5 tests
- `StressTestRunner.java` - Standalone test runner for comprehensive testing
- `StressTestConfig.java` - Configuration class for test parameters
- `run-stress-tests.sh` - Shell script for running all stress tests

## Running the Tests

### Option 1: Using Maven (Recommended)

```bash
# Run specific stress test
mvn test -Dtest=QLeverStressTest#testConcurrentQueryThroughput -q

# Run all stress tests (CI mode)
mvn test -Dtest=QLeverStressTest -q -Dspring.profiles.active=stress

# Run with specific thread count and duration
mvn test -Dtest=QLeverStressTest#testConcurrentQueryThroughput \
    -Dtest.threadCount=100 \
    -Dtest.duration=60 \
    -q
```

### Option 2: Using the Shell Script

```bash
# Run comprehensive stress test suite
./scripts/run-stress-tests.sh

# This script will:
# 1. Check prerequisites
# 2. Compile test classes
# 3. Run all stress test scenarios
# 4. Generate comprehensive report
```

### Option 3: Running with IDE

Run the `StressTestRunner` class as a Java application for detailed testing and reporting.

## Test Scenarios

### 1. Concurrent Query Throughput

Tests system performance with varying numbers of concurrent threads.

```java
@Test
@DisplayName("Stress Test: Concurrent Query Throughput")
void testConcurrentQueryThroughput() throws InterruptedException
```

**Parameters:**
- Thread counts: 10, 50, 100, 500
- Query: Simple SELECT statement
- Duration: 30 seconds per test

**Metrics:**
- Queries per second (throughput)
- Average, P95, P99 latencies
- Failure rates

### 2. Memory Pressure Test

Tests system behavior with large result sets and memory usage.

```java
@Test
@DisplayName("Stress Test: Memory Pressure with Large Results")
void testMemoryPressure() throws InterruptedException
```

**Simulated:**
- Large join queries returning extensive data
- Memory tracking during test execution
- Garbage collection monitoring

**Metrics:**
- Memory consumption
- Memory growth during test
- GC cycle count

### 3. Query Complexity Impact

Analyzes performance differences between simple and complex queries.

```java
@Test
@DisplayName("Stress Test: Query Complexity Impact")
void testQueryComplexityImpact() throws InterruptedException
```

**Query Types:**
- Simple: Single table SELECT
- Medium: JOIN operations
- Complex: Multi-table JOINs with WHERE clauses
- Metadata: DISTINCT queries

### 4. Long Running Queries with Timeout

Tests timeout mechanism for queries that take too long.

```java
@Test
@DisplayName("Stress Test: Long Running Queries with Timeout")
void testLongRunningQueries() throws InterruptedException
```

**Features:**
- 50% normal queries, 50% slow queries (simulated)
- 2-second timeout per query
- Timeout counting and statistics

### 5. Resource Exhaustion Test

Tests behavior with limited resources and open handles.

```java
@Test
@DisplayName("Stress Test: Resource Exhaustion (Open Handles)")
void testResourceExhaustion() throws InterruptedException
```

**Simulated:**
- Opening 1000 resource handles
- Mixed normal and resource-limited operations
- Handle cleanup simulation

### 6. Rapid Open/Close Cycles

Tests system resilience to frequent engine instance creation/destruction.

```java
@Test
@DisplayName("Stress Test: Rapid Open/Close Cycles")
void testRapidOpenCloseCycles() throws InterruptedException
```

**Parameters:**
- 1000 rapid cycles
- 50 concurrent threads
- 1ms delay between cycles

### 7. Mixed Workload Test

Simulates production-like mixed read and metadata queries.

```java
@Test
@DisplayName("Stress Test: Mixed Workload (Read + Metadata)")
void testMixedWorkload() throws InterruptedException
```

**Workload Mix:**
- 70% read queries
- 30% metadata queries
- 500 total queries

### 8. Breaking Point Identification

Identifies system limits by gradually increasing load.

```java
@Test
@DisplayName("Stress Test: Breaking Point Identification")
void testBreakingPointIdentification() throws InterruptedException
```

**Detection Criteria:**
- 20%+ throughput drop
- 100%+ latency increase
- 10%+ error rate increase

## Configuration

### StressTestConfig.java

Central configuration for all test parameters:

```java
// Thread count configurations
public static final int[] SCALE_TEST_THREADS = {10, 50, 100, 200, 500, 1000};

// Performance thresholds
public static class Thresholds {
    public static final int P95_LATENCY_MS = 200;
    public static final double MAX_ERROR_RATE = 0.05;
    public static final double MAX_MEMORY_GROWTH_MB = 100;
}

// Timeout configurations
public static class Timeouts {
    public static final long QUERY_TIMEOUT_MS = 10000;
    public static final long STRESS_TEST_TIMEOUT_MS = 300000;
}
```

### Customizing Tests

To modify test parameters:

1. Edit `StressTestConfig.java`
2. Adjust test scenarios in `QLeverStressTest.java`
3. Update runner script parameters

## Report Generation

### Test Reports

Each test generates:

1. **JSON Reports** - Raw metrics data
2. **Markdown Reports** - Human-readable summary
3. **CSV Exports** - Spreadsheet-compatible format

### Report Location

Reports are saved to:
- `stress-test-reports/` - Main report directory
- `stress-test-reports/reports/` - Individual test reports
- `stress-test-reports/logs/` - Test execution logs
- `stress-test-reports/data/` - Raw metrics data

### Sample Report Structure

```
stress-test-reports/
├── stress-test-report-20240228_143022.md  # Comprehensive report
├── reports/
│   ├── concurrent_test_100.json
│   ├── memory_test.json
│   ├── complexity_test.json
│   └── ...
├── logs/
│   ├── stress-test.log
│   └── ...
└── data/
    ├── raw_metrics.csv
    └── performance_chart.png
```

## CI Integration

### GitHub Actions Example

```yaml
jobs:
  stress-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Run Stress Tests
        run: ./scripts/run-stress-tests.sh
        continue-on-error: true

      - name: Upload Reports
        uses: actions/upload-artifact@v3
        with:
          name: stress-test-reports
          path: stress-test-reports/
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any
    stages {
        stage('Stress Tests') {
            steps {
                sh './scripts/run-stress-tests.sh'
                publishTestResults testResultsPattern: '**/test-results/**/*.xml'
                archiveArtifacts artifacts: 'stress-test-reports/**/*'
            }
        }
    }
}
```

## Performance Metrics

### Key Metrics Tracked

1. **Throughput**
   - Queries per second (QPS)
   - Peak throughput
   - Throughput regression

2. **Latency**
   - Average latency
   - P50, P95, P99 percentiles
   - Latency distribution

3. **Error Rates**
   - Total failures
   - Failure rate percentage
   - Error categorization

4. **Resource Usage**
   - Memory consumption
   - CPU usage
   - Thread counts
   - Garbage collection

5. **System Stability**
   - Breaking point identification
   - Recovery time
   - Resource leak detection

### Performance Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| P95 Latency | > 200ms | > 500ms |
| Error Rate | > 5% | > 20% |
| Memory Growth | > 100MB | > 500MB |
| Throughput Drop | > 20% | > 50% |

## Troubleshooting

### Common Issues

1. **Test Timeout**
   - Increase test duration in configuration
   - Check system resources
   - Verify query complexity

2. **High Error Rates**
   - Check for resource leaks
   - Monitor memory usage
   - Verify query timeouts

3. **Memory Issues**
   - Monitor memory growth patterns
   - Check for garbage collection problems
   - Verify query result cleanup

4. **Poor Performance**
   - Check database indexes
   - Verify query optimization
   - Monitor network latency

### Debug Mode

Enable detailed logging:

```java
// In test code
System.setProperty("stress.test.debug", "true");
```

### Test Isolation

Tests use separate engine instances to avoid interference:

```java
@BeforeEach
void setUp() {
    engine = new YQLeverEngine();
    engine.initialize();
}

@AfterEach
void tearDown() {
    if (engine != null) {
        engine.shutdown();
    }
}
```

## Best Practices

1. **Environment Setup**
   - Use dedicated test environment
   - Monitor system resources
   - Ensure adequate memory allocation

2. **Test Execution**
   - Run tests during off-peak hours
   - Monitor system health during tests
   - Save test results for analysis

3. **Analysis**
   - Compare performance over time
   - Look for performance regression
   - Identify optimization opportunities

4. **Maintenance**
   - Update test queries regularly
   - Adjust thresholds based on requirements
   - Review breaking points periodically

## Contributing

### Adding New Tests

1. Create test method in `QLeverStressTest.java`
2. Add configuration in `StressTestConfig.java`
3. Update runner script if needed
4. Document the new test scenario

### Test Guidelines

- Use descriptive test names
- Include meaningful assertions
- Provide detailed comments
- Follow existing patterns
- Use virtual threads for concurrency
- Include performance metrics

## Support

For questions or issues:

1. Check the existing test documentation
2. Review generated reports
3. Contact the development team
4. Check the project wiki

---

*Last Updated: February 2024*