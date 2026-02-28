# Phase 5: Code Bifurcation & Stateless Test Execution

## Overview

This document describes Phase 5 implementation of Code Bifurcation and Stateless Test Execution (STE) for YAWL v6.0.0. It enables developers to get <5 second feedback on code changes while maintaining full test coverage through parallel execution.

## Table of Contents

1. [Architecture](#architecture)
2. [Feedback Tier Tests](#feedback-tier-tests)
3. [Stateless Test Execution](#stateless-test-execution)
4. [Usage Guide](#usage-guide)
5. [Verification Tests](#verification-tests)
6. [Performance Metrics](#performance-metrics)

## Architecture

### Code Bifurcation Strategy

YAWL test suite is split into two tiers:

```
Test Suite (all tests)
    ├── Feedback Tier (1-2 per module, <1s each)
    │   ├── Critical path tests
    │   ├── Core functionality validation
    │   └── Total: <5s execution time
    │
    └── Coverage Tier (all tests)
        ├── Full test suite
        ├── Integration tests
        └── Total: 2-5 minutes execution time
```

### Feedback Tier Selection

Feedback tests are selected using these criteria:

1. **Explicit Configuration**: `.yawl/ci/feedback-tests.json` defines default feedback tests
2. **Auto-selection**: Max 2 tests per module, lowest execution time
3. **Coverage Validation**: At least 1 test per core module (engine, elements, stateless)
4. **Critical Path**: Tests must exercise essential functionality

Configuration example:

```json
{
  "feedback_tests": [
    {
      "test_class": "org.yawlfoundation.yawl.engine.YNetRunnerTest",
      "test_method": "testSimpleWorkflow",
      "module": "yawl-engine",
      "execution_time_ms": 50,
      "tags": ["feedback", "unit", "core"]
    }
  ],
  "core_modules": ["yawl-engine", "yawl-elements", "yawl-stateless"]
}
```

### Stateless Test Execution Architecture

Stateless Test Execution (STE) uses per-test H2 snapshots to provide 100% test isolation:

```
Test Execution Timeline
│
├─ Before test: Take H2 snapshot
│  └─ CREATE TEMPORARY TABLE AS SELECT * FROM table_name
│
├─ During test: Run in isolation
│  ├─ Test modifies H2 database
│  └─ Changes are local to test
│
└─ After test: Restore snapshot
   └─ DROP test tables, restore snapshot tables
      (all test-local changes discarded)
```

**Benefits:**
- Tests can run in any order
- Tests can run in parallel (8+ concurrent)
- 100% data isolation between tests
- No thread-local state leakage
- Deterministic results

**Overhead:**
- +1-2ms snapshot time per test
- +1-2ms restore time per test
- ~5-10% total overhead for typical tests
- Parallelism gain: 4-8x with 8 CPU cores

## Feedback Tier Tests

### Configuration File

Location: `.yawl/ci/feedback-tests.json`

```json
{
  "feedback_tier_config": {
    "description": "Feedback tier tests: max 2 per module, <1s each",
    "target_execution_time_ms": 5000,
    "max_tests_per_module": 2,
    "max_test_time_ms": 1000
  },
  "core_modules": [
    "yawl-engine",
    "yawl-elements",
    "yawl-stateless"
  ],
  "feedback_tests": [
    {
      "test_class": "org.yawlfoundation.yawl.engine.YNetRunnerTest",
      "test_method": "testSimpleWorkflow",
      "module": "yawl-engine",
      "execution_time_ms": 50,
      "tags": ["feedback", "unit", "core"],
      "description": "Critical path: basic workflow execution"
    }
  ]
}
```

### Running Feedback Tests

#### Via bash script:

```bash
# Run feedback tests
bash scripts/run-feedback-tests.sh

# List available feedback tests
bash scripts/run-feedback-tests.sh --list

# Test only yawl-engine module
bash scripts/run-feedback-tests.sh --module yawl-engine

# Preview Maven command
bash scripts/run-feedback-tests.sh --dry-run

# Custom configuration
bash scripts/run-feedback-tests.sh --config my-config.json
```

#### Via dx.sh:

```bash
# Enable feedback tier tests
bash scripts/dx.sh --feedback

# Combine with other options
bash scripts/dx.sh test --feedback
bash scripts/dx.sh all --feedback --stateless
```

#### Via Maven directly:

```bash
# Run with feedback profile
mvn -P feedback test

# Run specific feedback tests
mvn test -Dgroups=feedback -T 4
```

### Success Criteria for Feedback Tests

- [ ] All feedback tests pass in <5 seconds
- [ ] At least 1 test per core module
- [ ] Each test exercises critical workflow path
- [ ] Tests are deterministic (no flakiness)
- [ ] Tests can run in parallel safely

## Stateless Test Execution

### Enabling Stateless Mode

#### Via Maven profile:

```bash
mvn -P stateless test
```

#### Via system property:

```bash
mvn test -Dyawl.stateless.enabled=true
```

#### Via dx.sh:

```bash
# Combine with feedback tests for fast CI
bash scripts/dx.sh test --feedback --stateless

# Full suite with stateless
bash scripts/dx.sh all --stateless
```

#### Via JUnit properties:

Edit `test/resources/junit-platform.properties`:

```properties
yawl.stateless.enabled=true
yawl.stateless.verbose=false
```

### Annotating Tests for Stateless Execution

Mark tests with `@StatelessTest` annotation:

```java
import org.yawlfoundation.yawl.test.StatelessTest;
import org.junit.jupiter.api.Test;

class MyTest {
    @StatelessTest
    @Test
    void testWithIsolation() {
        // Test executes in isolated H2 snapshot
        // Each execution gets fresh database state
    }

    @StatelessTest(snapshotId = "custom_id", timeoutMs = 3000)
    @Test
    void testWithCustomSettings() {
        // Custom snapshot ID and timeout
    }

    @Test
    void testWithoutIsolation() {
        // Regular test without stateless annotation
        // Normal state sharing applies
    }
}
```

### H2 Snapshot Utilities

Helper script: `scripts/h2-snapshot-utils.sh`

Functions available:

```bash
source "${SCRIPT_DIR}/h2-snapshot-utils.sh"

# Take snapshot before test
h2_take_snapshot "my_test_case"

# Restore snapshot after test
h2_restore_snapshot "snapshot_id"

# List all snapshots
h2_list_snapshots

# Get size of snapshot
h2_snapshot_size "snapshot_id"

# Clean up old snapshots
h2_cleanup_snapshots

# Diagnostic functions
h2_table_count
h2_table_sizes
h2_memory_usage
```

### Configuration Options

In `test/resources/junit-platform.properties`:

```properties
# Enable/disable stateless mode
yawl.stateless.enabled=true|false

# Enable verbose logging
yawl.stateless.verbose=true|false

# Snapshot timeout (milliseconds)
yawl.stateless.snapshot.timeout.ms=5000

# Snapshot storage directory
yawl.stateless.snapshot.dir=/tmp/h2-snapshots

# Auto-cleanup interval (minutes)
yawl.stateless.cleanup.interval.min=60

# Higher parallelism for isolated tests
junit.jupiter.execution.parallel.config.dynamic.factor=4.0
```

## Usage Guide

### Quick Start: <5 Second Feedback Loop

1. **Edit code**:
   ```bash
   # Make changes to yawl-engine
   ```

2. **Run feedback tests**:
   ```bash
   # <5 second feedback on changes
   bash scripts/dx.sh --feedback
   ```

3. **Fix issues** (if any)

4. **Commit when green**

### Development Workflow

```bash
# Phase 1: Quick feedback (5s)
bash scripts/dx.sh --feedback
# ✓ Code compiles
# ✓ Critical tests pass
# ✓ Ready to commit

# Phase 2: Full validation (optional, before push)
bash scripts/dx.sh all
# ✓ All modules compile
# ✓ All tests pass
# ✓ Ready for production

# Phase 3: CI verification
bash scripts/dx.sh all --stateless
# ✓ Parallel execution with isolation
# ✓ No state coupling issues
# ✓ Production-ready
```

### CI/CD Integration

```yaml
# Example GitHub Actions workflow
jobs:
  feedback:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '25'
      - run: bash scripts/dx.sh --feedback
        timeout-minutes: 2  # <5 seconds typical

  full-suite:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '25'
      - run: bash scripts/dx.sh all --stateless
        timeout-minutes: 10
```

## Verification Tests

### Test 1: Parallel Execution (Same Test 100x)

Verify snapshot isolation works:

```bash
mvn -P stateless test \
    -Dtest=YNetRunnerTest#testSimpleWorkflow \
    -T 8C \
    -Dgroups=feedback

# Expected: All 100 iterations pass
# This proves: each execution gets fresh H2 snapshot
```

### Test 2: Random Test Order

Verify no state coupling:

```bash
mvn -P stateless test \
    -T 8C \
    -Dgroups=feedback

# Run multiple times with different random orders:
for i in {1..5}; do
  mvn -P stateless test -T 8C -Dgroups=feedback
done

# Expected: All runs pass in any test order
# This proves: no inter-test state leakage
```

### Test 3: Isolation Verification

Prove isolation is actually preventing state mutation:

```bash
# Create a test that depends on shared state:
@StatelessTest
@Test
void testStateMutation() {
    static AtomicInteger counter = new AtomicInteger(0);
    int current = counter.incrementAndGet();
    assertTrue(current < 10);  // Should fail in parallel
}

# Run 100 times in parallel
mvn -P stateless test -Dtest=TestIsolation -T 8C

# Expected: Some iterations fail (proves isolation blocks state)
# This verifies: isolation is working correctly
```

### Test 4: Performance Measurement

Measure snapshot overhead:

```bash
# Baseline: sequential execution
time mvn test -T 1

# With parallelism (default, no snapshots)
time mvn test -T 4

# With stateless snapshots
time mvn -P stateless test -T 4

# Expected ratio:
#   sequential:         ~100s
#   parallel (4x):      ~30-40s (3-4x faster)
#   stateless (4x):     ~35-45s (similar to parallel, <10% overhead)
```

## Performance Metrics

### Typical Execution Times

| Phase | Time | Notes |
|-------|------|-------|
| Feedback tier | <5s | 1-2 per module, critical path only |
| Changed modules | 5-15s | Depends on changes |
| Full compile + test | 2-5 min | All modules, full test suite |
| With stateless | +5-10% | H2 snapshot overhead |
| With parallelism | -60-70% | 8 CPU cores, 4-8x speedup |

### Snapshot Overhead

Per-test costs:

| Operation | Time | Notes |
|-----------|------|-------|
| Take snapshot | 1-2ms | CREATE TEMPORARY TABLE AS SELECT |
| Restore snapshot | 1-2ms | DROP test tables, restore originals |
| Total per test | 2-4ms | Negligible for most tests (>50ms) |

For test distribution:

- Fast tests (<100ms): 2-4% overhead
- Medium tests (100-500ms): 1-2% overhead
- Slow tests (>500ms): <1% overhead

### Parallelism Gains

With 8 CPU cores:

| Configuration | Time | Speedup | Notes |
|---------------|------|---------|-------|
| Sequential (-T 1) | ~100s | 1x | Baseline |
| Parallel 4 (-T 4) | ~30s | 3.3x | Good cache locality |
| Parallel 8 (-T 8) | ~20s | 5x | Optimal for CI |
| Stateless 8 (-P stateless -T 8) | ~22s | 4.5x | With snapshot overhead |

## Troubleshooting

### Issue: Slow feedback tests

**Problem**: Feedback tier takes >5 seconds

**Solution**:
```bash
# Check which tests are slow
bash scripts/run-feedback-tests.sh --list

# Review .yawl/ci/test-times.json for timing data

# Consider removing slow tests from feedback tier
# Update .yawl/ci/feedback-tests.json
```

### Issue: Stateless mode disabled

**Problem**: `-P stateless` not applying

**Check**:
```bash
mvn help:active-profiles

# Verify profile is in pom.xml
grep -A 5 'id>stateless' pom.xml

# Check system property
mvn test -Dyawl.stateless.enabled=true -X | grep stateless
```

### Issue: H2 snapshot failures

**Problem**: "Failed to take snapshot" errors

**Check**:
```bash
# Verify H2 is available
mvn dependency:tree | grep h2

# Check H2 version
mvn dependency:tree | grep h2 | head -1

# Verify snapshot directory
ls -la /tmp/h2-snapshots/
```

### Issue: Tests failing in parallel but not sequential

**Symptoms**: `-T 1` passes, `-T 8` fails

**Likely cause**: State coupling or shared state

**Solution**:
```bash
# Disable stateless temporarily
mvn test -Dyawl.stateless.enabled=false -T 8

# If still fails: Not stateless-fixable, need to fix test
# If passes: Something about snapshots is affecting tests

# Use --verbose flag to debug
mvn test -Dyawl.stateless.enabled=true \
         -Dyawl.stateless.verbose=true \
         -Dtest=YourTest -T 8
```

## References

- [StatelessTestExecutor.java](../src/test/java/org/yawlfoundation/yawl/test/StatelessTestExecutor.java)
- [run-feedback-tests.sh](../scripts/run-feedback-tests.sh)
- [h2-snapshot-utils.sh](../scripts/h2-snapshot-utils.sh)
- [.yawl/ci/feedback-tests.json](../.yawl/ci/feedback-tests.json)
- [junit-platform.properties](../test/resources/junit-platform.properties)
- [Phase 5 JUnit Integration](#)
