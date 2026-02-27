# YAWL v6.0.0-GA Benchmark Orchestration Scripts

This comprehensive benchmark orchestration suite provides automated testing, monitoring, and reporting for the YAWL workflow engine.

## Overview

The benchmark orchestration scripts provide:

- **Automated benchmark execution** with multiple profiles (development, CI, production, custom)
- **Real-time monitoring** with configurable thresholds and alerting
- **Regression detection** with trend analysis and baseline comparison
- **Chaos engineering** testing for resilience validation
- **Stress testing** for production-like scenarios
- **Comprehensive reporting** in multiple formats (HTML, JSON, CSV)
- **CI/CD integration** for automated quality gates

## Quick Start

### 1. Installation and Setup

```bash
# Make scripts executable
chmod +x benchmark-scripts/*.sh

# Verify Java 25 is installed
java -version  # Should show Java 25
```

### 2. Run a Basic Benchmark

```bash
# Development profile (quick tests)
./benchmark-scripts/run-benchmarks.sh development

# CI profile (comprehensive tests)
./benchmark-scripts/run-benchmarks.sh ci

# Production profile (full suite)
./benchmark-scripts/run-benchmarks.sh production

# Custom profile with specific parameters
./benchmark-scripts/run-benchmarks.sh custom \
  --include-pattern "A2A*" \
  --report-html \
  --parallel 4
```

### 3. Process Results

```bash
# Process benchmark results
./benchmark-scripts/process-results.sh \
  --results-dir benchmark-results \
  --format html \
  --threshold-detection \
  --trend-analysis
```

### 4. Monitor in Real-time

```bash
# Start monitoring with alerts
./benchmark-scripts/monitoring.sh \
  --benchmark-suite stress \
  --alerts \
  --threshold-file thresholds.json \
  --interval 30
```

## Scripts Overview

### Core Orchestration Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| `run-benchmarks.sh` | Main benchmark orchestration | `./run-benchmarks.sh [profile] [options]` |
| `run-stress-tests.sh` | Production-like stress testing | `./run-stress-tests.sh --duration 1h --users 100` |
| `run-chaos-tests.sh` | Chaos engineering validation | `./run-chaos-tests.sh --scenario network-partition` |
| `run-regression-tests.sh` | Regression detection | `./run-regression-tests.sh --baseline-comparison` |
| `process-results.sh` | Result processing and reporting | `./process-results.sh --format html` |
| `monitoring.sh` | Real-time monitoring and alerting | `./monitoring.sh --benchmark-suite stress` |

### Configuration Files

| File | Purpose |
|------|---------|
| `benchmark-profiles.toml` | Benchmark profile definitions |
| `thresholds.json` | Performance threshold configuration |
| `ci-cd-integration.md` | CI/CD integration guide |

## Benchmark Profiles

### Development Profile
- **Purpose**: Quick verification during development
- **Duration**: 5-10 minutes
- **Benchmarks**: JMH unit, memory basic, integration fast
- **Use Case**: Local development feedback

### CI Profile
- **Purpose**: Comprehensive testing in CI/CD
- **Duration**: 30-60 minutes
- **Benchmarks**: JMH unit, integration, memory, stress, chaos, A2A
- **Use Case**: Automated pipeline validation

### Production Profile
- **Purpose**: Full production validation
- **Duration**: 2-4 hours
- **Benchmarks**: All available suites
- **Use Case**: Release readiness validation

### Custom Profile
- **Purpose**: User-defined benchmark selection
- **Duration**: Variable
- **Benchmarks**: Configurable subset
- **Use Case**: Specific testing scenarios

## Configuration Options

### Benchmark Profiles (`benchmark-profiles.toml`)

```toml
[profiles.custom]
name = "My Custom Profile"
duration = "30 minutes"
parallel_jobs = 4

[profiles.custom.benchmarks]
enabled = ["jmh_unit", "memory_basic", "stress_medium"]
disabled = ["production_load"]

[profiles.custom.thresholds]
response_time_ms = 3000
throughput_ops = 500
error_rate = 0.02
```

### Threshold Configuration (`thresholds.json`)

```json
{
    "thresholds": {
        "hard_limit_cpu": 90,
        "hard_limit_memory": 80,
        "hard_limit_disk": 95,
        "hard_limit_throughput": 1000,
        "hard_limit_latency": 5000,
        "hard_limit_error_rate": 0.05,
        "soft_limit_cpu": 70,
        "soft_limit_memory": 60,
        "soft_limit_disk": 80,
        "soft_limit_throughput": 500,
        "soft_limit_latency": 3000,
        "soft_limit_error_rate": 0.01
    }
}
```

## Features

### 1. Comprehensive Benchmark Suites

- **JMH Benchmarks**: Microbenchmarking with Java Microbenchmark Harness
- **Memory Profiling**: Heap usage, GC pressure, memory leak detection
- **Stress Testing**: Production-like load scenarios
- **Chaos Engineering**: Network, resource, service, and data chaos
- **Regression Detection**: Performance trend analysis and regression scoring
- **A2A Communication**: Agent-to-agent communication benchmarks

### 2. Real-time Monitoring

```bash
# Monitor with automatic alerts
./benchmark-scripts/monitoring.sh \
  --benchmark-suite stress \
  --alerts \
  --threshold-file thresholds.json \
  --email-alerts \
  --slack-alerts
```

### 3. Multiple Output Formats

```bash
# Generate reports in different formats
./benchmark-scripts/process-results.sh \
  --format html          # HTML dashboard
  --format json          # Machine-readable JSON
  --format csv           # Spreadsheet-friendly CSV
  --format all           # All formats
```

### 4. Trend Analysis and Regression Detection

```bash
# Advanced regression analysis
./benchmark-scripts/run-regression-tests.sh \
  --baseline-comparison \
  --trend-analysis \
  --anomaly-detection \
  --historical-days 30
```

### 5. CI/CD Integration

The scripts integrate seamlessly with CI/CD systems:

- **GitHub Actions**: Complete workflows in `ci-cd-integration.md`
- **Jenkins Pipeline**: Pipeline examples and quality gates
- **GitLab CI**: YAML configuration examples
- **Custom Integration**: Adaptable to any CI system

## Advanced Usage

### 1. Custom Benchmark Selection

```bash
# Run specific benchmarks only
./benchmark-scripts/run-benchmarks.sh custom \
  --include-pattern "Memory*" \
  --exclude-pattern "Stress*"
```

### 2. Parallel Execution

```bash
# Use multiple parallel jobs
./benchmark-scripts/run-benchmarks.sh ci \
  --parallel 8 \
  --jmh-threads 4 \
  --jmh-forks 2
```

### 3. Continuous Monitoring

```bash
# Run monitoring continuously
./benchmark-scripts/monitoring.sh \
  --continuous \
  --interval 30 \
  --duration 60
```

### 4. Chaos Engineering Scenarios

```bash
# Run specific chaos scenarios
./benchmark-scripts/run-chaos-tests.sh \
  --scenario network-partition \
  --recovery-validation \
  --auto-recovery
```

## Performance Optimization

### 1. JVM Configuration

The scripts optimize JVM settings automatically:

```toml
[profiles.production.jvm_args]
- "-Xms4g"
- "-Xmx8g"
- "-XX:+UseCompactObjectHeaders"
- "-XX:+UseZGC"
- "-XX:+UseContainerSupport"
```

### 2. Parallel Execution

Configure parallel jobs for faster execution:

```bash
# Parallel job optimization
./benchmark-scripts/run-benchmarks.sh production \
  --parallel $(nproc) \
  --jmh-threads $(nproc)
```

### 3. Resource Management

```bash
# Resource-aware execution
./benchmark-scripts/run-benchmarks.sh ci \
  --resource-monitoring \
  --memory-limit 8g \
  --cpu-limit 80%
```

## Monitoring and Alerting

### 1. Alert Channels

Configure multiple alert channels:

```json
{
    "alert_channels": ["console", "log", "email", "slack"],
    "notification_settings": {
        "email": {
            "enabled": true,
            "recipients": ["team@yawlfoundation.org"]
        },
        "slack": {
            "enabled": true,
            "webhook_url": "https://hooks.slack.com/..."
        }
    }
}
```

### 2. Threshold Management

Configure thresholds by severity:

- **Hard Limits**: System failures (immediate action required)
- **Soft Limits**: Warning conditions (monitor closely)

### 3. Real-time Metrics

Monitor key metrics in real-time:

```bash
# Live dashboard
./benchmark-scripts/monitoring.sh \
  --benchmark-suite stress \
  --pager "less -R"
```

## Troubleshooting

### Common Issues

1. **Java Version Mismatch**
   ```bash
   # Ensure Java 25 is installed
   java -version
   ```

2. **Memory Issues**
   ```bash
   # Increase JVM memory
   export MAVEN_OPTS="-Xmx8g -XX:+UseZGC"
   ```

3. **Permission Issues**
   ```bash
   # Make scripts executable
   chmod +x benchmark-scripts/*.sh
   ```

4. **CI/CD Integration Issues**
   - Check Java version compatibility
   - Verify Maven configuration
   - Ensure artifact storage is configured

### Debug Mode

```bash
# Enable verbose logging
./benchmark-scripts/run-benchmarks.sh ci --verbose

# Dry run to test configuration
./benchmark-scripts/run-benchmarks.sh ci --dry-run

# Debug monitoring
./benchmark-scripts/monitoring.sh --log-level debug
```

## Best Practices

### 1. Development Workflow

```bash
# 1. Local development
./benchmark-scripts/run-benchmarks.sh development

# 2. CI integration
./benchmark-scripts/run-benchmarks.sh ci --baseline-comparison

# 3. Production validation
./benchmark-scripts/run-benchmarks.sh production
```

### 2. Continuous Monitoring

```bash
# Monitor critical systems
./benchmark-scripts/monitoring.sh \
  --benchmark-suite stress \
  --continuous \
  --alerts \
  --email-alerts
```

### 3. Historical Comparison

```bash
# Compare with historical baselines
./benchmark-scripts/run-regression-tests.sh \
  --baseline-comparison \
  --historical-days 30
```

### 4. Chaos Engineering

```bash
# Regular chaos testing
./benchmark-scripts/run-chaos-tests.sh \
  --scenario all \
  --recovery-validation
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add new benchmarks or profiles
4. Test thoroughly
5. Submit a pull request

## Support

For issues and questions:
- Create an issue on GitHub
- Check the troubleshooting section
- Review the CI/CD integration guide

## License

This project is part of the YAWL Foundation and follows the YAWL licensing terms.