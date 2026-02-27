# YAWL Performance Regression Detection Framework

## Overview

The YAWL Performance Regression Detection Framework is a comprehensive system for detecting performance regressions in the YAWL workflow engine. It leverages existing BenchmarkRunner.java and AllBenchmarksRunner.java classes to compare current performance against historical baselines using statistical significance testing.

## Features

- **Statistical Significance Testing**: Uses t-tests with configurable p-value thresholds (default: 0.05)
- **Automatic Threshold Detection**: Configurable regression thresholds (default: 20%)
- **CI/CD Integration**: Seamless integration with GitHub Actions and other CI systems
- **Comprehensive Reporting**: Detailed regression reports with visual trend analysis
- **Baseline Management**: Automatic baseline creation and cleanup
- **Multi-Benchmark Support**: Supports all YAWL benchmark suites:
  - YAWLEngineBenchmarks
  - WorkflowPatternBenchmarks
  - ConcurrencyBenchmarks
  - MemoryBenchmarks

## Architecture

The framework consists of:
1. **Regression Detection Script** (`regression-detection.sh`): Main execution script
2. **CI/CD Integration** (`.github/workflows/performance-regression.yml`): Automated pipeline
3. **Performance Baselines**: Historical performance data stored in JSON format
4. **Regression Reports**: Markdown-formatted analysis reports

## Usage

### Basic Usage

```bash
# Run regression detection on all benchmarks
./scripts/regression-detection.sh

# Compare specific files
./scripts/regression-detection.sh -b baseline.json -c current.json

# Use custom thresholds
./scripts/regression-detection.sh -t 15 -p 0.01
```

### CI/CD Pipeline

The framework integrates with GitHub Actions to automatically:
- Run regression detection on code changes
- Generate performance trend reports
- Create issues for performance regressions
- Send Slack notifications

### Command Line Options

| Option | Description | Default |
|--------|-------------|---------|
| `-b, --baseline` | Use specific baseline file | Auto-detect |
| `-c, --current` | Use specific current results file | Run benchmarks |
| `-t, --threshold` | Regression threshold percentage (%) | 20 |
| `-p, --p-value` | P-value threshold for significance | 0.05 |
| `-o, --output` | Output report file | Auto-generate |
| `-v, --verbose` | Enable verbose output | false |
| `-q, --quiet` | Quiet mode | false |
| `--dry-run` | Show what would be tested without execution | false |
| `--ci-mode` | CI/CD pipeline mode | false |
| `--save-baseline` | Save current results as new baseline | false |
| `--cleanup-old` | Cleanup old baseline files (keep last 10) | false |

## Configuration

### Performance Targets

The framework monitors the following performance targets:

| Metric | Target | Current Baseline |
|--------|--------|-----------------|
| Engine Startup | < 60s | 45.2s ± 2.1s |
| Case Creation (P95) | < 500ms | 380ms |
| Work Item Checkout (P95) | < 200ms | 180ms |
| Work Item Checkin (P95) | < 300ms | 275ms |
| Task Transition | < 100ms | 85ms ± 15ms |
| DB Query (P95) | < 50ms | 45ms |
| GC Time | < 5% | 3.2% |

### Regression Detection Thresholds

- **Warning**: > 20% increase in P95 latency
- **Critical**: > 50% increase in P95 latency
- **Auto-fail**: > 100% increase in P95 latency

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | No regressions detected |
| 1 | Transient error (retryable) |
| 2 | Regressions detected |
| 3 | Configuration error |
| 4 | Benchmark execution error |

## Working with Baselines

### Creating Baselines

```bash
# Run benchmarks and create baseline
./scripts/regression-detection.sh --save-baseline

# Use specific baseline file
./scripts/regression-detection.sh -b my-baseline.json
```

### Managing Baselines

The framework automatically:
- Stores baselines in `performance/baselines/`
- Maintains timestamped baseline files
- Cleans up old baselines (keeps last 10)
- Creates new baselines when requested

```bash
# Clean up old baselines
./scripts/regression-detection.sh --cleanup-old

# Show all baselines
ls -la performance/baselines/baseline-*.json
```

## Statistical Analysis

### T-Test Implementation

The framework uses Python's scipy.stats for t-test calculations:

```python
from scipy import stats
t_stat, p_value = stats.t_ind(baseline_values, current_values)
```

### Significance Testing

- **Null Hypothesis**: No performance difference between baseline and current
- **Alternative Hypothesis**: Performance has changed
- **Decision**: Reject null if p-value < threshold (0.05)

### Effect Size Calculation

Cohen's d for effect size:
- d < 0.2: Small effect
- d = 0.5: Medium effect  
- d > 0.8: Large effect

## CI/CD Integration

### GitHub Actions

The workflow includes three jobs:

1. **Regression Detection**: Main regression testing
2. **Trend Analysis**: Long-term performance tracking
3. **Optimization Check**: Identifies optimization opportunities

### Configuration

```yaml
# .github/workflows/performance-regression.yml
env:
  CI_MODE: true
  SAVE_BASELINE: true
  CLEANUP_OLD: true
```

### Notifications

- **PR Comments**: Automated comments on pull requests
- **Slack Alerts**: Notifications for failures
- **GitHub Issues**: Created for regressions

## Report Generation

### Report Structure

```markdown
# YAWL Performance Regression Detection Report

## Summary
- Baseline: performance/baselines/baseline-20260225_143000.json
- Current: performance/results/current-20260225_143000.json
- Threshold: 20%
- P-value: 0.05

## Performance Comparison
| Benchmark | Metric | Baseline | Current | Change | Status |
|-----------|--------|----------|---------|--------|--------|
| YAWLEngineBenchmarks | P95 Latency | 380ms | 420ms | +10.53% | ✅ OK |
| ConcurrencyBenchmarks | P95 Latency | 180ms | 240ms | +33.33% | 🔴 REGRESSION |

## Recommendations
- Investigate performance degradation in ConcurrencyBenchmarks
- Check for recent code changes that might impact performance
```

### Visualizations

- **Trend Charts**: Performance over time using gnuplot
- **Regression Heatmaps**: Visual representation of affected metrics
- **Performance Dashboards**: Interactive HTML reports

## Troubleshooting

### Common Issues

1. **JMH Benchmark Failures**
   ```bash
   # Check Maven dependencies
   cd yawl-performance
   mvn clean install -DskipTests
   
   # Verify Java version
   java -version
   ```

2. **Missing jq/bc**
   ```bash
   # Install dependencies
   sudo apt-get install jq bc
   ```

3. **Permission Errors**
   ```bash
   # Make script executable
   chmod +x scripts/regression-detection.sh
   ```

### Debug Mode

```bash
# Enable verbose output
./scripts/regression-detection.sh -v

# Dry run to see what would happen
./scripts/regression-detection.sh --dry-run -v
```

## Extending the Framework

### Adding New Benchmarks

1. Create new benchmark class extending AllBenchmarksRunner
2. Add to DEFAULT_BENCHMARKS array in regression-detection.sh
3. Update baseline-metrics.md with new targets

```bash
DEFAULT_BENCHMARKS=(
    "org.yawlfoundation.yawl.performance.YAWLEngineBenchmarks"
    "org.yawlfoundation.yawl.performance.MyNewBenchmark"
)
```

### Custom Thresholds

```bash
# Custom threshold for specific metric
./scripts/regression-detection.sh -t 10 -p 0.01
```

### Integration with Monitoring Systems

The framework can be extended to:
- Send metrics to Prometheus
- Integrate with Grafana dashboards
- Export to monitoring platforms

## Performance Optimization

### Optimization Opportunities

1. **Work Item Checkout**: Current P95 = 180ms, target < 150ms
2. **Parallel Scaling**: Investigate why 32 threads only provide 10% gain over 16 threads
3. **Memory Reduction**: Current 2MB/case, target < 1.5MB/case

### Implementation Steps

1. Run baseline benchmarks
2. Implement optimizations
3. Compare against new baselines
4. Document improvements

## Contributing

### Adding New Features

1. Fork the repository
2. Create feature branch
3. Implement changes
4. Update regression detection
5. Submit pull request

### Code Style

- Follow existing bash conventions
- Use meaningful variable names
- Include error handling
- Add verbose logging

## Contact

For questions or support:
- **Performance Team**: yawl-performance@yawlfoundation.org
- **Documentation**: scripts/regression-detection/
- **Issues**: GitHub Issues

## License

This framework is part of the YAWL project and is licensed under the GNU Lesser General Public License v3.0.

---
*Last Updated: 2026-02-26*
