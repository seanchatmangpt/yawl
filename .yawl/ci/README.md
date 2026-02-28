# YAWL Test Clustering & Sharding System

## Overview

The YAWL test clustering system provides intelligent test distribution and parallel execution across multiple shards in CI/CD environments. Tests are automatically categorized into clusters based on execution time, then distributed across shards using a greedy bin-packing algorithm to minimize total duration variance.

## Features

- **Automatic Test Analysis**: Parse Surefire/Failsafe XML reports to extract test execution times
- **Intelligent Clustering**: Categorize tests into 4 clusters based on execution characteristics:
  - Cluster 1: Fast tests (<100ms)
  - Cluster 2: Medium tests (100ms-5s)
  - Cluster 3: Slow tests (5s-30s)
  - Cluster 4: Resource-heavy tests (>30s)
- **Load Balancing**: Distribute tests across 8-16 shards using greedy bin-packing algorithm
- **Variance Minimization**: Balance test duration across shards (target: <20% variance)
- **CI/CD Integration**: GitHub Actions workflow with 8-way parallel test execution
- **Timeout Management**: Cluster-specific timeouts prevent runaway tests

## Files

### Generated Configuration
- `.yawl/ci/test-times.json` - Test execution times extracted from reports
- `.yawl/ci/test-histogram.json` - Cluster distribution histogram
- `.yawl/ci/test-shards.json` - Shard configuration (8 shards, greedy bin-packed)

### Scripts
- `scripts/analyze-test-times.sh` - Analyze test execution times and create clusters
- `scripts/cluster-tests.sh` - Generate test shards configuration
- `scripts/run-shard.sh` - Run a single test shard
- `scripts/run-all-shards.sh` - Run all shards sequentially (local testing)

### Workflows
- `.github/workflows/test-parallel.yml` - GitHub Actions workflow for parallel testing

## Usage

### Analyze Test Times
Extract test execution times from Maven test reports:

```bash
bash scripts/analyze-test-times.sh [output-file] [histogram-file]

# Default locations:
# - Output: .yawl/ci/test-times.json
# - Histogram: .yawl/ci/test-histogram.json
```

**Output Examples:**

```json
{
  "timestamp": "2026-02-28T07:22:27Z",
  "total_tests": 6,
  "total_time_ms": 48835,
  "avg_time_ms": 8139,
  "min_time_ms": 50,
  "max_time_ms": 25000,
  "tests": [
    { "name": "org.yawlfoundation.yawl.engine.YNetRunnerTest.testSimpleWorkflow", "time_ms": 50 },
    { "name": "org.yawlfoundation.yawl.engine.YNetRunnerTest.testParallelTasks", "time_ms": 3200 }
  ]
}
```

### Generate Test Shards

Create sharding configuration with load balancing:

```bash
bash scripts/cluster-tests.sh [shard-count] [input-file] [output-file]

# Examples:
bash scripts/cluster-tests.sh              # Default: 8 shards
bash scripts/cluster-tests.sh 16           # Use 16 shards
bash scripts/cluster-tests.sh 8 /tmp/test-times.json
```

**Output Structure:**

```json
{
  "timestamp": "2026-02-28T07:23:25Z",
  "shard_count": 8,
  "total_tests": 6,
  "total_estimated_duration_ms": 48835,
  "shards": [
    {
      "shard_id": 0,
      "tests": ["org.yawlfoundation.yawl.integration.ServiceIntegrationTest.testA2A"],
      "test_count": 1,
      "estimated_duration_ms": 25000,
      "cluster": 3,
      "clusters": [3],
      "cluster_description": "C3: Slow (5s-30s)"
    }
  ],
  "cluster_definitions": {
    "1": "Fast (<100ms)",
    "2": "Medium (100ms-5s)",
    "3": "Slow (5s-30s)",
    "4": "Heavy (>30s)"
  },
  "load_distribution": {
    "min_duration_ms": 0,
    "max_duration_ms": 25000,
    "avg_duration_ms": 6100,
    "duration_range_ms": 25000,
    "std_deviation_ms": 8600,
    "variance": 73960000.0,
    "balance_score": 0.0
  }
}
```

### Run a Test Shard

Execute tests for a specific shard:

```bash
bash scripts/run-shard.sh <shard-index> [config-file]

# Examples:
bash scripts/run-shard.sh 0                    # Run shard 0
bash scripts/run-shard.sh 3 /tmp/test-shards.json
```

### Run All Shards Locally

Run all shards sequentially for testing:

```bash
bash scripts/run-all-shards.sh [config-file]
```

## Test Clustering Levels

### Cluster 1: Fast Tests (<100ms)
- **Use Case**: Unit tests, simple integration tests
- **Timeout**: 30 seconds
- **Characteristics**: No DB access, lightweight setup
- **Examples**:
  - YWorkItemTest.testCreate
  - YNetRunnerTest.testSimpleWorkflow

### Cluster 2: Medium Tests (100ms-5s)
- **Use Case**: Typical integration tests with DB
- **Timeout**: 60 seconds
- **Characteristics**: H2 database setup, light XML parsing
- **Examples**:
  - YNetRunnerTest.testParallelTasks
  - ResourceQueueTest.testAllocate

### Cluster 3: Slow Tests (5s-30s)
- **Use Case**: Complex workflow operations, multi-step scenarios
- **Timeout**: 120 seconds
- **Characteristics**: Full engine initialization, state management
- **Examples**:
  - ServiceIntegrationTest.testMCP
  - ServiceIntegrationTest.testA2A

### Cluster 4: Heavy Tests (>30s)
- **Use Case**: Stress tests, performance benchmarks
- **Timeout**: 180 seconds
- **Characteristics**: Virtual thread management, large data volumes
- **Examples**:
  - EngineStressTest
  - RateLimiterBreakingPointTest

## Test Timeout Configuration

Timeouts are defined in `test/resources/junit-platform.properties`:

```properties
# Cluster-specific timeouts
yawl.test.cluster.fast.timeout=30 s
yawl.test.cluster.medium.timeout=60 s
yawl.test.cluster.slow.timeout=120 s
yawl.test.cluster.heavy.timeout=180 s

# Default timeout (for untagged tests)
junit.jupiter.execution.timeout.default=90 s
```

### Using @Timeout Annotation

Tag tests with explicit timeouts:

```java
@Test
@Timeout(value = 30, unit = TimeUnit.SECONDS)
void testFastOperation() {
    // Fast test (<100ms)
}

@Test
@Timeout(value = 120, unit = TimeUnit.SECONDS)
void testComplexWorkflow() {
    // Slow test (5s-30s)
}
```

## CI/CD Integration

### GitHub Actions Workflow

The `.github/workflows/test-parallel.yml` workflow:

1. **Generate Shards** (1 job):
   - Analyzes test times
   - Generates shard configuration
   - Uploads config as artifact

2. **Test Parallel** (8 jobs, matrix):
   - Downloads shard configuration
   - Runs tests for assigned shard
   - Uploads test results

3. **Aggregate Results** (1 job):
   - Downloads all test results
   - Merges XML reports
   - Generates summary

**Execution Timeline:**
```
Generate Shards (2 min)
    ↓
Test Parallel (8 jobs in parallel, 5-10 min each)
    ├─ Shard 0: 25.0s (testA2A)
    ├─ Shard 1: 15.0s (testMCP)
    ├─ Shard 2: 5.5s (testAllocate)
    ├─ Shard 3: 3.2s (testParallelTasks)
    ├─ Shard 4: 0.085s (testCreate)
    ├─ Shard 5: 0.05s (testSimpleWorkflow)
    ├─ Shard 6: 0.0s (empty)
    └─ Shard 7: 0.0s (empty)
    ↓
Aggregate Results (2 min)
```

**Total CI time**: ~25s parallel + 2-3min overhead = ~5-7 minutes

### Manual CI Trigger

Run tests in CI context locally:

```bash
# 1. Generate shards
bash scripts/analyze-test-times.sh
bash scripts/cluster-tests.sh

# 2. Run shard 0
bash scripts/run-shard.sh 0

# 3. Run all shards
bash scripts/run-all-shards.sh
```

## Load Balancing Algorithm

The system uses a **greedy bin-packing algorithm**:

1. **Sort tests** by duration (descending)
2. **Initialize shards** with zero load
3. **For each test** (largest first):
   - Find shard with minimum current load
   - Assign test to that shard
   - Add test duration to shard's estimated duration

**Time Complexity**: O(N log N) where N = test count

**Load Balance Score**:
```
balance_score = 1.0 - (max_duration - min_duration) / max_duration
- 1.0 = Perfect balance (all shards equal)
- 0.0 = Worst case (one shard has all tests)
```

**Target Variance**: <20% (balance_score > 0.8)

## Performance Metrics

### Example Results (6 tests, 8 shards)

```
Total tests:        6
Total duration:     48.8s
Min shard duration: 0.0s  (empty shards)
Max shard duration: 25.0s (testA2A)
Avg shard duration: 6.1s
Std deviation:      8.6s
Balance score:      0.00%
```

### Optimization Tips

1. **Merge small tests**: Combine multiple fast tests per shard
2. **Isolate slow tests**: One slow test per shard minimum
3. **Monitor variance**: Re-generate shards after major test changes
4. **Adjust shard count**:
   - 2-4 shards: Local/PR testing
   - 8 shards: Standard CI (GitHub Actions)
   - 16 shards: Large test suites or slow CI runners

## Automation

### Weekly Shard Regeneration

Add to cron job or GitHub Actions:

```bash
#!/bin/bash
# Regenerate test shards weekly to account for test changes
cd /home/user/yawl

# Run full test suite
mvn clean verify

# Analyze new times and regenerate shards
bash scripts/analyze-test-times.sh
bash scripts/cluster-tests.sh

# Commit if shards changed
if git diff --quiet .yawl/ci/test-shards.json; then
    exit 0
fi

git add .yawl/ci/test-shards.json test-histogram.json
git commit -m "chore: regenerate test shards based on current timing"
git push origin main
```

### New Test Inclusion

New tests are automatically included in the next shard generation:

1. Tests run locally or in CI
2. Test times written to Surefire/Failsafe reports
3. `analyze-test-times.sh` extracts times
4. `cluster-tests.sh` assigns to shards
5. Next CI run uses updated configuration

## Troubleshooting

### No Test Reports Found

If the scripts create sample data instead of using real reports:

```bash
# 1. Run tests first
mvn clean verify

# 2. Check reports exist
ls -la target/surefire-reports/TEST-*.xml
ls -la target/failsafe-reports/TEST-*.xml

# 3. Regenerate shards
bash scripts/analyze-test-times.sh
bash scripts/cluster-tests.sh
```

### Shard Imbalance

If balance_score is low (<0.5):

```bash
# 1. Check distribution
cat .yawl/ci/test-shards.json | jq '.load_distribution'

# 2. Regenerate with more shards
bash scripts/cluster-tests.sh 16

# 3. Or split problematic tests
# Manually edit test-shards.json to redistribute large tests
```

### Test Timeout Failures

If tests timeout in CI:

1. Check cluster assignment:
```bash
cat .yawl/ci/test-shards.json | jq '.shards[] | select(.estimated_duration_ms > 180000)'
```

2. Increase timeout for that cluster:
```properties
yawl.test.cluster.slow.timeout=240 s
```

3. Add explicit @Timeout to test:
```java
@Timeout(value = 240, unit = TimeUnit.SECONDS)
void slowTest() { ... }
```

## References

- Test configuration: `test/resources/junit-platform.properties`
- Shard generation: `scripts/cluster-tests.sh`
- Maven configuration: `pom.xml` (surefire/failsafe plugins)
- GitHub Actions: `.github/workflows/test-parallel.yml`
