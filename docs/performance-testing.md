# YAWL Performance Testing

This document describes the performance testing infrastructure integrated into YAWL's CI/CD pipeline.

## Overview

The YAWL project includes comprehensive performance testing capabilities that are automatically executed during CI/CD pipelines. These tests ensure that performance regressions are caught early and blocked from merging.

## Performance Test Types

### 1. Microbenchmarks
- **Purpose**: Measure individual component performance
- **Categories**:
  - JMH (Java Microbenchmark Harness) benchmarks
  - Concurrency benchmarks
  - Memory usage benchmarks
- **Location**: `test/org/yawlfoundation/yawl/performance/`
- **Key Classes**:
  - `EnginePerformanceBaseline.java`
  - `ConcurrencyBenchmarkSuite.java`
  - `MemoryUsageProfiler.java`

### 2. Load Tests
- **Purpose**: Simulate realistic user workloads
- **Profiles**:
  - Light: 10 threads, 30 seconds, 1000 requests
  - Medium: 50 threads, 60 seconds, 5000 requests
  - Heavy: 100 threads, 120 seconds, 10000 requests
- **Key Class**: `LoadTestSuite.java`

### 3. Chaos Tests
- **Purpose**: Test system resilience under failure conditions
- **Failure Types**:
  - Latency spikes
  - Random failures
  - Resource constraints
- **Key Class**: `ChaosTestRunner.java`

## CI/CD Integration

### GitHub Actions Workflow

The performance testing is integrated into `.github/workflows/performance-check.yml`:

```yaml
name: Performance Regression Detection
on:
  pull_request:
    branches: [master, main]
  push:
    branches: [master, main]
```

### Pipeline Steps

1. **Setup**: Environment preparation and dependency installation
2. **Build**: Parallel Maven compilation (-T 1.5C)
3. **Microbenchmarks**: Execute matrix of microbenchmarks
4. **Load Tests**: Execute matrix of load tests
5. **Chaos Tests**: Execute matrix of chaos tests
6. **Regression Detection**: Compare results against baselines
7. **Publish Report**: Generate and upload performance reports

### Java 25 Optimization

The pipeline uses Java 25 with performance optimizations:

```bash
JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders -XX:+UseZGC -Xms2g -Xmx4g -XX:MaxGCPauseMillis=10"
```

## Regression Detection

### Threshold Configuration

- Default regression threshold: 10%
- Configurable per test run
- Hard fail on any regression exceeding threshold

### Regression Detection Script

The `scripts/regression-detection.sh` script:

1. Compares current results against baseline
2. Calculates percentage change
3. Flags regressions exceeding threshold
4. Generates detailed JSON report

### Example Usage

```bash
# Run with default settings
./scripts/regression-detection.sh

# Specify custom files and threshold
./scripts/regression-detection.sh baseline.json current.json 5
```

## Running Performance Tests Locally

### Prerequisites

```bash
# Install system dependencies
sudo apt-get install -y bc jq

# Install JMH (if needed)
wget -O /usr/share/java/jmh.jar https://repo1.maven.org/maven2/org/openjdk/jmh/jmh-core/1.36/jmh-core-1.36.jar
```

### Run Full Performance Suite

```bash
# Build project
mvn clean compile -T 1.5C

# Run all performance tests
./run-performance-tests.sh

# Run quick tests
./run-performance-tests.sh --quick
```

### Individual Test Types

```bash
# Run microbenchmarks
cd test/org/yawlfoundation/yawl/performance
java -cp "$(find ../../../target -name "*.jar" | tr '\n' ':')" org.openjdk.jmh.Main EnginePerformanceBaseline

# Run load tests
java -cp "$(find ../../../target -name "*.jar" | tr '\n' ':')" LoadTestSuite -t 10 -d 30 -r 1000

# Run chaos tests
java -cp "$(find ../../../target -name "*.jar" | tr '\n' ':')" ChaosTestRunner --latency-spike 500
```

## Performance Artifacts

### Generated Files

- `benchmark-results.json`: Combined all performance results
- `performance-regression-report-*.json`: Regression detection report
- `performance-report.html`: HTML summary report
- Individual test results in `test-results/`

### Artifacts Retention

- Individual test results: 30 days
- Combined results and reports: 90 days
- HTML reports: 90 days

## Performance Baselines

### Creating Baselines

```bash
# Run performance tests to generate current baseline
./run-performance-tests.sh

# Copy current results as new baseline
cp benchmark-results.json performance-baseline.json

# Commit baseline to repository
git add performance-baseline.json
git commit -m "Update performance baseline"
```

### Updating Baselines

When intentional performance improvements are made:

1. Run tests to verify improvement
2. Update baseline with new values
3. Commit updated baseline
4. Document the improvement

## Troubleshooting

### Common Issues

1. **JMH Classpath Issues**
   ```
   Error: Could not find or load main class org.openjdk.jmh.Main
   ```
   Solution: Install JMH jar or adjust classpath

2. **Memory Issues**
   ```
   Error: OutOfMemoryError
   ```
   Solution: Increase Java heap size or reduce test duration

3. **Regression Detection Errors**
   ```
   Error: jq is required but not installed
   ```
   Solution: Install `jq` and `bc`

### Debug Mode

Run with verbose logging:

```bash
# Enable Maven debug output
mvn clean compile -X

# Run performance tests with verbose output
./run-performance-tests.sh 2>&1 | tee performance-test.log
```

## Performance Targets

### Current Targets (subject to change)

- **Engine Operations**: < 50ms average response time
- **Throughput**: > 1000 operations/second
- **Memory Usage**: < 2GB for standard workload
- **Concurrency**: Support 100+ concurrent users
- **Error Rate**: < 0.1% under load

### Monitoring

Performance metrics are continuously monitored in CI/CD and reported via:

- GitHub Actions comments on PRs
- Performance reports in artifacts
- Slack notifications (if configured)

## Contributing

### Adding New Benchmarks

1. Create new test class in `test/org/yawlfoundation/yawl/performance/`
2. Extend JMH benchmark class
3. Add to appropriate matrix in workflow
4. Update documentation

### Optimizing Tests

- Use appropriate warmup iterations
- Measure stable operations only
- Avoid measurement phase distractions
- Use realistic data sizes

## References

- [JMH (Java Microbenchmark Harness) Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [Maven Build Optimization](https://maven.apache.org/guides/mini/guide-parallel-builds.html)
- [Java 25 Performance Features](https://openjdk.org/projects/jdk/25/)

## Support

For performance testing questions or issues:
1. Check existing documentation
2. Review CI/CD pipeline logs
3. Consult team performance experts
4. File GitHub issue with detailed logs
