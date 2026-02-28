# YAWL Baseline Metrics

This directory contains performance baseline metrics for the YAWL build and test system.

## Files

- **baselines.json** - Main baseline data file (append-only, cumulative history)
- **baselines-YYYYMMDD_HHMMSS.json** - Per-run snapshot (created for each baseline collection)
- **run-*.json** - Individual run metrics (temporary, used during collection)

## Baseline Scenarios

The baseline collection measures three distinct scenarios to establish performance expectations:

### 1. Cold Start (dx.sh all)
- Fresh checkout with no cache
- Full build of all modules
- Expected: ~90 seconds
- Tests: Compile and package all modules

### 2. Warm Start (dx.sh)
- Incremental build with single file change
- Only recompile affected modules
- Expected: ~18 seconds
- Tests: Compile changed modules and dependencies

### 3. Full Suite (mvn clean verify)
- Complete Maven build with all tests and validations
- Includes: compile, test, integration tests, JAR creation, verification
- Expected: ~180 seconds
- Tests: All tests, including slow/integration tests

## Usage

### Collect Baselines

```bash
# Collect all three scenarios (3 runs each)
bash scripts/establish-baselines.sh

# Collect specific scenario only
bash scripts/establish-baselines.sh --cold-only
bash scripts/establish-baselines.sh --warm-only
bash scripts/establish-baselines.sh --full-only

# Dry run (show what would execute)
bash scripts/establish-baselines.sh --dry-run --verbose

# Verify existing baseline
bash scripts/establish-baselines.sh --verify
```

### Data Structure

```json
{
  "baseline_date": "2026-02-28T07:00:00Z",
  "environment": {
    "java_version": "25.0.2",
    "java_home": "/usr/lib/jvm/temurin-25-jdk-amd64",
    "cpu_cores": 16,
    "total_memory_mb": 21504
  },
  "runs": [
    {
      "scenario": "cold-start",
      "run_number": 1,
      "timestamp": "2026-02-28T07:00:15Z",
      "total_time_ms": 90000,
      "phases": {
        "compile": 45000,
        "test": 40000,
        "verify": 5000
      },
      "memory_peak_mb": 2048,
      "test_counts": {
        "passed": 1024,
        "failed": 0,
        "skipped": 42
      }
    }
  ],
  "summary": {
    "cold_start_avg_ms": 90000,
    "cold_start_std_dev_ms": 2000,
    "warm_start_avg_ms": 18000,
    "warm_start_std_dev_ms": 1000,
    "full_suite_avg_ms": 180000,
    "full_suite_std_dev_ms": 5000,
    "test_counts": {
      "passed": 3072,
      "failed": 0,
      "skipped": 126
    }
  }
}
```

## Interpretation

### Mean and Standard Deviation

- **Mean** (`avg_ms`): Average execution time across 3 runs
- **Std Dev** (`std_dev_ms`): Standard deviation (measure of consistency)
- **CV (Coefficient of Variation)**: (std_dev / mean) × 100%

### Validity

Baselines are considered valid when:
- Standard deviation < 10% of mean (CV < 10%)
- All runs complete successfully (exit code 0)
- Test counts are consistent across runs

If CV > 10%, system may have high variability:
- Check system load during baseline collection
- Ensure no background processes interfering
- Re-run baselines during quiet periods

## Performance Constraints

Baselines must meet these constraints:

| Scenario | Constraint | Reason |
|----------|-----------|--------|
| Cold start | < 120 seconds | Full rebuild should be fast with modern build system |
| Warm start | < 30 seconds | Incremental builds must be responsive |
| Full suite | < 180 seconds | Complete verify should be acceptable CI runtime |

## Monitoring

The baseline collection system captures:

### Timing Metrics
- **Total execution time** (nanosecond precision)
- **Phase breakdown** (compile, test, verify phases)

### Memory Metrics
- **Peak RSS** (Resident Set Size in MB)
- **Peak virtual memory** (from /proc/self/status)

### CPU Metrics
- **Average CPU utilization** (percentage)
- **CPU core count** (system info)

### Test Metrics
- **Passed tests** (extracted from Surefire XML)
- **Failed tests** (failure count)
- **Skipped tests** (skipped count)

### System Info
- Java version and location
- Maven version
- CPU cores and RAM
- Kernel version
- Collection timestamp

## Troubleshooting

### High Variability (CV > 10%)

```bash
# Check system load
uptime
top -b -n 1 | head -15

# Check disk I/O
iostat -x 1 5

# Run again during quiet period
bash scripts/establish-baselines.sh --verbose
```

### Memory Issues

If peak memory exceeds 4GB, Java may be using excessive heap:

```bash
# Check effective Java heap settings
java -XX:+PrintFlagsFinal -version | grep MaxHeapSize

# Investigate in dx.sh or Maven config
grep -r "Xmx\|Xms" scripts/ pom.xml
```

### Slow Builds

If baseline times exceed constraints:

1. Check Maven proxy configuration
2. Verify no external network calls blocking
3. Profile with `dx.sh --verbose` or `mvn -X`
4. Check for missing warm cache files

## Integration with CI/CD

Baselines are used for:

1. **Performance regression detection** - Alert if build times exceed baseline + margin
2. **Build optimization validation** - Confirm optimizations improve over baseline
3. **Historical tracking** - Track performance improvements/regressions over time
4. **SLA compliance** - Verify build times meet service level agreements

## See Also

- `/home/user/yawl/scripts/establish-baselines.sh` - Main baseline collection script
- `/home/user/yawl/scripts/collect-metrics.sh` - Metrics collection helper functions
- `/home/user/yawl/scripts/metrics-runner.sh` - Per-run metrics wrapper
- `/home/user/yawl/scripts/dx.sh` - Fast build-test loop
