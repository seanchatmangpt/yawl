# YAWL Production Load Testing Suite

This suite provides comprehensive performance testing for the YAWL workflow engine, validating production scalability and performance under load.

## Overview

The production load test simulates 10,000+ concurrent users for 60 minutes, validating that YAWL meets enterprise performance requirements:
- **Case Creation (p95)**: < 500ms
- **Work Item Checkout (p95)**: < 200ms  
- **Work Item Checkin (p95)**: < 300ms
- **Task Transition (p95)**: < 100ms
- **DB Query (p95)**: < 50ms
- **Overall Error Rate**: < 1%

## Test Components

### 1. Production Load Test (`production-load-test.js`)
- **Purpose**: Full-scale production load test
- **Duration**: 60 minutes (10 min ramp-up, 30 min sustain, 10 min ramp-down)
- **Scale**: 10,000 concurrent users
- **Workload**: Realistic user behavior patterns
- **Metrics**: Comprehensive performance tracking

### 2. Test Runner (`run-production-load-test.sh`)
- **Purpose**: Automated test execution with pre-flight checks
- **Features**:
  - Service health validation
  - Dry run capability
  - Configurable parameters
  - Environment variable support
- **Usage**: Simple command-line interface

### 3. Results Analysis (`analyze-production-results.py`)
- **Purpose**: Detailed analysis and reporting
- **Features**:
  - Performance threshold validation
  - Visual chart generation
  - Improvement recommendations
  - Comprehensive reports
- **Output**: Charts, summaries, and analysis files

### 4. Configuration (`production-test-config.json`)
- **Purpose**: Test configuration and thresholds
- **Contents**:
  - Performance SLAs
  - Workload patterns
  - Scaling parameters
  - System requirements

## Quick Start

### Prerequisites
- k6 v0.47+ installed: https://k6.io/docs/getting-started/installation/
- Python 3.8+ for analysis scripts
- Matplotlib for chart generation: `pip install matplotlib pandas numpy`

### Basic Test Execution
```bash
# Run with default settings
./run-production-load-test.sh

# Run against production environment
./run-production-load-test.sh --url http://prod.yawl:8080

# Dry run with 10 users for 1 minute
./run-production-load-test.sh --vus 10 --duration 1m
```

### Test Analysis
```bash
# Analyze results
python analyze-production-results.py k6-prod-load-summary.json

# Custom output directory
python analyze-production-results.py --output-dir ./reports

# Custom thresholds
python analyze-production-results.py --thresholds custom-thresholds.json
```

## Test Configuration

### Environment Variables
```bash
export SERVICE_URL=http://localhost:8080          # Target service
export K6_OPTIONS="--vus 1 --duration 1s"        # Additional k6 options
```

### Command Line Options
```bash
./run-production-load-test.sh [OPTIONS]

Options:
  --url URL               YAWL service URL (default: http://localhost:8080)
  --vus COUNT             Number of virtual users for dry run
  --duration DURATION     Duration for dry run (default: 30s)
  --help                  Show help message
```

### Custom Test Configuration
Edit `production-test-config.json` to modify:
- Performance thresholds
- Workload patterns
- User behaviors
- Scaling stages

## Test Scenarios

### 1. Ramp-Up Phase (0-10 minutes)
- Gradually increase from 0 to 10,000 users
- Validates system capacity and stability
- Measures warm-up performance

### 2. Peak Load Phase (10-40 minutes)  
- Sustain 10,000 concurrent users
- Validates production readiness
- Identifies performance bottlenecks
- Measures SLA compliance

### 3. Ramp-Down Phase (40-60 minutes)
- Gradually decrease to 0 users
- Validates graceful shutdown
- Measures resource cleanup

## Workload Distribution

The test simulates realistic user behavior with the following operation distribution:

| Operation | Weight | Description |
|-----------|--------|-------------|
| Status Check | 10% | Engine health monitoring |
| Launch Workflow | 25% | New case creation |
| Get Work Items | 30% | Work item retrieval |
| Work Item Checkout | 20% | Task assignment |
| Work Item Checkin | 10% | Task completion |
| Task Transition | 4% | Workflow progression |
| Resource Query | 1% | Resource availability check |

## User Behavior Patterns

Three distinct user patterns simulate real-world usage:

1. **Power Users (20%)**: 5 operations per iteration, fast-paced
2. **Regular Users (50%)**: 3 operations per iteration, moderate pace  
3. **Casual Users (30%)**: 1 operation per iteration, slow pace

## Performance Metrics

### Response Time Thresholds
- **P95**: 500ms for all operations
- **P99**: 1000ms for all operations
- **Case Creation**: 500ms
- **Work Item Checkout**: 200ms
- **Work Item Checkin**: 300ms
- **Task Transition**: 100ms
- **DB Query**: 50ms

### Reliability Metrics
- **Error Rate**: < 1% for HTTP requests
- **Success Rate**: > 99% for critical operations
- **Timeout Rate**: < 0.5%

### Capacity Metrics
- **Max Concurrent Users**: 10,000+
- **Peak Throughput**: Measured during sustained load
- **Resource Utilization**: Monitored during test

## Test Outputs

### Summary Reports
- `k6-prod-load-summary.json`: Raw k6 metrics
- `analysis-summary.txt`: Human-readable analysis
- Performance charts in PNG format

### Key Files
- **Test Script**: `production-load-test.js`
- **Runner**: `run-production-load-test.sh`
- **Analyzer**: `analyze-production-results.py`
- **Config**: `production-test-config.json`

## System Requirements

### Minimum Requirements
- CPU: 8 cores
- Memory: 16GB RAM
- Network: 1Gbps
- Storage: 100GB SSD

### Recommended Requirements  
- CPU: 16 cores
- Memory: 32GB RAM
- Network: 10Gbps
- Storage: 500GB SSD

### Production Requirements
- CPU: 32 cores
- Memory: 64GB RAM
- Network: 10Gbps
- Storage: 1TB SSD

## Troubleshooting

### Common Issues

#### High Response Times
- Check database query performance
- Review caching strategies
- Monitor CPU utilization
- Optimize connection pooling

#### High Error Rates
- Increase connection pool size
- Check timeout configurations
- Monitor resource utilization
- Review log files for errors

#### Memory Issues
- Monitor JVM memory usage
- Check for memory leaks
- Adjust heap size if needed
- Monitor garbage collection

### Debug Options
Enable debug logging for troubleshooting:
```bash
# Enable detailed k6 logging
export K6_OPTIONS="$K6_OPTIONS --verbose"

# Monitor specific metrics
k6 run --out statsd statsd_endpoint production-load-test.js
```

## Best Practices

### Before Testing
1. **Ensure service stability**: Run health checks before test
2. **Prepare test environment**: Isolate from production
3. **Configure monitoring**: Set up real-time monitoring
4. **Schedule maintenance**: Avoid during maintenance windows

### During Testing
1. **Monitor system resources**: CPU, memory, network, disk
2. **Watch logs for errors**: Critical errors indicate issues
3. **Track performance trends**: Identify degradation
4. **Check thresholds**: Real-time alerting

### After Testing
1. **Analyze results**: Use provided analysis tools
2. **Identify bottlenecks**: Focus on worst-performing operations
3. **Document findings**: Keep records for future tests
4. **Plan optimizations**: Address identified issues

## Integration with CI/CD

### Pipeline Example
```yaml
# .gitlab-ci.yml
production_load_test:
  stage: performance
  image: loadimpact/k6:latest
  script:
    - npm install -g k6
    - ./run-production-load-test.sh --url $CI_ENVIRONMENT_URL
    - python analyze-production-results.py --output-dir ./reports
  artifacts:
    reports:
      performance: k6-prod-load-summary.json
    paths:
      - analysis-summary.txt
      - charts/
```

### Quality Gates
```yaml
quality_gates:
  performance:
    p95_response_time: '< 500ms'
    error_rate: '< 0.01'
    success_rate: '> 0.99'
```

## Support

For issues and questions:
1. Check the troubleshooting section
2. Review generated reports and charts
3. Monitor system logs during test execution
4. Validate system configuration
5. Contact development team for assistance

## Contributing

To contribute improvements:
1. Fork the repository
2. Create feature branch
3. Update test configuration as needed
4. Test changes thoroughly
5. Submit pull request with detailed description

---

**Note**: This test suite is designed for production validation. Always run in a safe environment before deployment to production.
