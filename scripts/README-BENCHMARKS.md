# YAWL Benchmark Orchestration Suite v6.0.0-GA

## Overview

The YAWL Benchmark Orchestration Suite provides comprehensive benchmarking capabilities for YAWL v6.0.0-GA. It includes performance testing, stress testing, chaos engineering, and regression detection tools with real-time monitoring and reporting.

## Architecture

```
scripts/
├── run-benchmarks.sh              # Main benchmark orchestration
├── run-stress-tests.sh           # Production-like stress testing
├── run-chaos-tests.sh            # Chaos engineering experiments
├── run-regression-tests.sh       # Regression detection
├── analyze-benchmark-results.sh   # Result analysis and reporting
├── monitor-benchmark-execution.sh # Real-time monitoring
├── benchmark-profiles.conf       # Configuration profiles
└── README-BENCHMARKS.md          # This documentation
```

## Quick Start

### 1. Development Profile (Quick Tests)
```bash
# Run development benchmarks
./scripts/run-benchmarks.sh development

# Run with verbose output
./scripts/run-benchmarks.sh development --verbose

# Run specific test categories
./scripts/run-benchmarks.sh custom --tests "ConcurrencyBenchmarkSuite,A2ACommunicationBenchmarks"
```

### 2. CI Profile (Comprehensive Testing)
```bash
# Run CI benchmarks
./scripts/run-benchmarks.sh ci

# Enable parallel execution
./scripts/run-benchmarks.sh ci --parallel 8 --verbose
```

### 3. Production Profile (Full Suite)
```bash
# Run full production benchmarks
./scripts/run-benchmarks.sh production

# Include stress tests and chaos engineering
./scripts/run-benchmarks.sh production --stress-tests --chaos-tests
```

## Detailed Usage

### Main Benchmark Script (`run-benchmarks.sh`)

#### Profiles
- **development**: Quick subset of tests (5-10 minutes)
- **ci**: Comprehensive tests (30-60 minutes)
- **production**: Full suite (2-4 hours)
- **custom**: User-defined test selection

#### Options
```bash
# Basic usage
./scripts/run-benchmarks.sh <profile> [options]

# Examples:
./scripts/run-benchmarks.sh ci --output-dir ./results
./scripts/run-benchmarks.sh custom --tests "BenchmarkConfig,MCPPerformanceBenchmarks" --jmh-only
./scripts/run-benchmarks.sh production --stress-tests --chaos-tests --parallel 16
```

#### Environment Variables
- `JAVA_HOME`: Java home directory
- `MAVEN_OPTS`: Maven options
- `BENCHMARK_CONFIG`: Path to custom configuration
- `SLACK_WEBHOOK_URL`: Slack webhook for notifications
- `EMAIL_RECIPIENTS`: Comma-separated email addresses

### Stress Testing (`run-stress-tests.sh`)

#### Scenarios
- **baseline**: Basic stress test with increasing load
- **sustained**: Sustained high load over extended period
- **spike**: Sudden load spikes
- **mixed**: Mixed workload pattern
- **chaos**: Chaos engineering with fault injection

#### Options
```bash
# Basic stress test
./scripts/run-stress-tests.sh baseline --duration 60 --concurrent 1000

# Production-like testing
./scripts/run-stress-tests.sh sustained --duration 120 --rate-per-sec 2000 --enable-monitoring

# Chaos engineering
./scripts/run-stress-tests.sh chaos --interval 30 --severity high --enable-monitoring
```

### Chaos Engineering (`run-chaos-tests.sh`)

#### Experiments
- **network-faults**: Network partition, latency, and packet loss
- **service-faults**: Service failures, restarts, and delays
- **resource-faults**: CPU, memory, and disk pressure
- **data-faults**: Data corruption, loss, and inconsistency
- **hybrid**: Combination of all fault types

#### Options
```bash
# Network chaos
./scripts/run-chaos-tests.sh network-faults --duration 30 --severity medium

# Hybrid chaos experiment
./scripts/run-chaos-tests.sh hybrid --simultaneous-faults 3 --enable-alerts --include-probes

# Production chaos testing
./scripts/run-chaos-tests.sh chaos --duration 60 --severity high --no-recovery
```

### Regression Detection (`run-regression-tests.sh`)

#### Modes
- **performance**: Performance regression detection
- **functionality**: Functional regression detection
- **comprehensive**: Both performance and functional regressions
- **baseline**: Establish new baseline

#### Options
```bash
# Performance regression detection
./scripts/run-regression-tests.sh performance --threshold 15 --baseline baseline.json

# Functional regression testing
./scripts/run-regression-tests.sh functionality --days 30 --include-trends

# Establish baseline
./scripts/run-regression-tests.sh baseline --auto-fix --enable-notifications
```

### Result Analysis (`analyze-benchmark-results.sh`)

#### Features
- HTML report generation with charts
- JSON data export for processing
- CSV format for spreadsheet analysis
- Trend analysis across multiple runs
- Baseline comparison

#### Options
```bash
# Basic analysis
./scripts/analyze-benchmark-results.sh ./benchmark-results

# Generate multiple formats
./scripts/analyze-benchmark-results.sh ./results --formats html,json,csv

# Include trend analysis
./scripts/analyze-benchmark-results.sh ./results --trend-analysis --baseline baseline.json
```

### Real-time Monitoring (`monitor-benchmark-execution.sh`)

#### Features
- Live dashboard with real-time metrics
- Alert notifications for critical failures
- System resource monitoring
- Test progress tracking
- Comprehensive reporting

#### Options
```bash
# Show dashboard
./scripts/monitor-benchmark-execution.sh --dashboard

# Enable notifications
./scripts/monitor-benchmark-execution.sh --email alerts@example.com --slack-webhook URL

# Custom monitoring interval
./scripts/monitor-benchmark-execution.sh --interval 15 --verbose
```

## Configuration

### Benchmark Profiles (`benchmark-profiles.conf`)

The configuration file defines test profiles and their settings:

```ini
# Development profile
[profile.development]
name = "Development"
duration = "5-10 minutes"
tests = ["jmh-basic", "integration-core", "performance-baseline"]

# CI profile
[profile.ci]
name = "CI"
duration = "30-60 minutes"
tests = ["jmh-full", "integration-all", "performance-comprehensive"]

# Production profile
[profile.production]
name = "Production"
duration = "2-4 hours"
tests = ["jmh-extended", "integration-production", "stress-advanced"]
```

### Performance Thresholds

```ini
[thresholds]
latency = { warning = 100, critical = 500, unit = "ms" }
throughput = { warning = 1000, critical = 500, unit = "ops/sec" }
memory = { warning = 70, critical = 90, unit = "percent" }
cpu = { warning = 70, critical = 90, unit = "percent" }
error_rate = { warning = 5, critical = 10, unit = "percent" }
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: YAWL Benchmark Pipeline

on: [push, pull_request]

jobs:
  benchmark:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        java-version: '25'
        distribution: 'temurin'

    - name: Run CI Benchmarks
      run: ./scripts/run-benchmarks.sh ci --output-dir ./ci-results

    - name: Analyze Results
      run: ./scripts/analyze-benchmark-results.sh ./ci-results --formats html

    - name: Upload Results
      uses: actions/upload-artifact@v3
      with:
        name: benchmark-results
        path: |
          ci-results/
          *.html
          *.json
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('Benchmark') {
            parallel {
                stage('Development') {
                    steps {
                        sh './scripts/run-benchmarks.sh development --output-dir dev-results'
                    }
                }
                stage('CI') {
                    steps {
                        sh './scripts/run-benchmarks.sh ci --output-dir ci-results'
                    }
                }
            }
        }

        stage('Analyze') {
            steps {
                sh './scripts/analyze-benchmark-results.sh dev-results ci-results --formats html,json'
            }
        }

        stage('Notify') {
            steps {
                emailext (
                    subject: 'Benchmark Results',
                    body: 'See attached reports',
                    attachmentsPattern: '**/*.html,**/*.json'
                )
            }
        }
    }
}
```

## Output Formats

### HTML Reports
- Interactive charts using Chart.js
- Performance metrics visualization
- Trend analysis graphs
- Optimization recommendations

### JSON Reports
- Machine-readable format
- Complete benchmark data
- Metadata and timestamps
- Structured for processing

### CSV Reports
- Spreadsheet-compatible format
- Summary and detailed metrics
- Historical data analysis
- Integration with BI tools

## Performance Targets

### Development Profile
- Virtual thread startup: < 1ms
- Context switching: < 0.1ms
- Memory overhead: < 8KB per thread
- Test execution: < 10 minutes

### CI Profile
- Throughput: > 500 ops/sec
- Latency p95: < 100ms
- Memory usage: < 70%
- Success rate: > 95%

### Production Profile
- Throughput: > 1000 ops/sec
- Latency p95: < 50ms
- Availability: > 99.9%
- Error rate: < 0.1%

## Best Practices

### 1. Environment Preparation
- Ensure sufficient resources (CPU, memory, disk)
- Configure proper JVM settings
- Set up monitoring and logging
- Prepare test data and workflows

### 2. Test Selection
- Use development profile for quick feedback
- Use CI profile for comprehensive validation
- Use production profile for release validation
- Customize profiles based on requirements

### 3. Monitoring
- Enable real-time monitoring during execution
- Set appropriate alert thresholds
- Monitor system resources
- Track test progress and failures

### 4. Result Analysis
- Generate reports in multiple formats
- Compare with baselines
- Analyze trends over time
- Identify performance bottlenecks

### 5. Integration
- Integrate with CI/CD pipelines
- Set up automated notifications
- Archive historical results
- Establish performance budgets

## Troubleshooting

### Common Issues

1. **Memory Issues**
   - Increase JVM heap size
   - Check for memory leaks
   - Monitor garbage collection

2. **Performance Degradation**
   - Review recent code changes
   - Check database queries
   - Monitor thread contention

3. **Test Failures**
   - Check test dependencies
   - Verify environment setup
   - Review error logs

### Debug Commands

```bash
# Check system resources
top -c
free -h
df -h

# Monitor JVM performance
jps -l
jstat -gc <pid> 1s

# Check benchmark logs
tail -f benchmark-results/*.log
```

## Support

For issues and questions:
1. Check the YAWL documentation
2. Review GitHub issues
3. Contact the YAWL Foundation
4. Consult the community forums

## License

This project is part of YAWL v6.0.0-GA and follows the same license terms.

---

*Last Updated: 2026-02-26*
*Version: 6.0.0-GA*