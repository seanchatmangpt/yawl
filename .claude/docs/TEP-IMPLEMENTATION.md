# Test Execution Pipeline (TEP) Implementation - Complete Reference

**Status**: ✅ Production Ready
**Last Updated**: 2026-02-28
**Version**: 1.0

---

## Executive Summary

The Test Execution Pipeline (TEP) is a multi-tier test framework that executes tests sequentially by tier with fail-fast semantics, enabling rapid feedback on code changes.

**Key Features**:
- ✅ 4-tier architecture: Fast (Tier 1) → Medium (Tier 2) → Slow (Tier 3) → Heavy (Tier 4)
- ✅ Parallel execution within each tier (configurable parallelism per tier)
- ✅ Fail-fast mode: stops at first tier failure (configurable)
- ✅ Per-tier reporting with pass/fail/error/skip metrics
- ✅ Integrated with `dx.sh` via `--fail-fast-tier N` flag
- ✅ Estimated time prediction for next tier (via TIP)
- ✅ Zero-configuration: tier definitions in `.yawl/ci/tier-definitions.json`

---

## Quick Start

### Run Fast Tests Only (Tier 1 Fail-Fast)
```bash
bash scripts/dx.sh test --fail-fast-tier 1
# Output: ~3-5 seconds for most changes
# Stops at first Tier 1 failure, never runs Tiers 2-4
```

### Run Tiers 1-2 (Fast + Medium)
```bash
bash scripts/dx.sh test --fail-fast-tier 2
# Output: ~10-15 seconds (if Tier 1 passes)
# Stops at first failure in Tiers 1-2, never runs Tiers 3-4
```

### Run All Tiers with Fail-Fast
```bash
bash scripts/dx.sh test --fail-fast-tier 4
# Output: ~30-60 seconds (depends on test suite)
# Stops at first failure in any tier
```

### Continue Despite Failures
```bash
TEP_CONTINUE_ON_FAILURE=1 bash scripts/dx.sh test --fail-fast-tier 2
# Continues running all tiers even if failures occur
```

---

## Architecture

### Tier Definitions

```json
{
  "tier_1": {
    "name": "Fast Unit Tests",
    "description": "Unit tests with execution time <100ms",
    "timeout_seconds": 20,
    "parallelism": 8,
    "cluster_ids": [1],
    "tags": ["unit"]
  },
  "tier_2": {
    "name": "Medium Integration Tests",
    "description": "Integration tests with execution time 100ms-5s",
    "timeout_seconds": 60,
    "parallelism": 6,
    "cluster_ids": [2],
    "tags": ["integration"]
  },
  "tier_3": {
    "name": "Slow Acceptance Tests",
    "description": "Slow tests with execution time 5s-30s",
    "timeout_seconds": 180,
    "parallelism": 2,
    "cluster_ids": [3],
    "tags": ["slow", "acceptance"]
  },
  "tier_4": {
    "name": "Heavy Resource Tests",
    "description": "Resource-intensive tests with execution time >30s",
    "timeout_seconds": 300,
    "parallelism": 1,
    "cluster_ids": [4],
    "tags": ["heavy", "stress"]
  }
}
```

### Test Clustering

Test shards are pre-computed and stored in `.yawl/ci/test-shards.json`:
- Each shard contains 1-10 tests
- Each shard has a cluster ID (1-4) indicating its tier
- Each cluster has estimated duration
- Load balancing minimizes skew

Example:
```json
{
  "shard_id": 4,
  "tests": ["org.yawlfoundation.yawl.engine.YWorkItemTest.testCreate"],
  "estimated_duration_ms": 85,
  "cluster": 1,
  "cluster_description": "C1: Fast (<100ms)"
}
```

### Execution Flow

```
User: bash scripts/dx.sh test --fail-fast-tier 2
  ↓
Compile all changed modules (Maven)
  ↓
[TEP_FAIL_FAST_TIER=2] → Run Tier 1
  ├─ Extract tests for cluster_ids=[1]
  ├─ Run all Tier 1 tests in parallel (8 threads)
  ├─ Report: "Tier 1 Results: 45 passed, 0 failed in 3.2s"
  ├─ Exit 0 → Continue to Tier 2
  ↓
[TEP_FAIL_FAST_TIER=2] → Run Tier 2
  ├─ Extract tests for cluster_ids=[2]
  ├─ Run all Tier 2 tests in parallel (6 threads)
  ├─ Report: "Tier 2 Results: 12 passed, 1 failed in 8.5s"
  ├─ Exit 1 → STOP (fail-fast)
  ↓
Final Status: FAILED (Tier 2, 1 test failed)
Exit code: 1
```

---

## File Structure

### Core Files

| File | Purpose |
|------|---------|
| `scripts/dx.sh` | Main build script, orchestrates TEP |
| `scripts/run-test-tier.sh` | Executes single tier with parallelism |
| `.yawl/ci/tier-definitions.json` | Tier configuration (name, timeout, parallelism, tags) |
| `.yawl/ci/test-shards.json` | Pre-computed test shards & cluster assignments |

### Metrics & Logs

| File | Purpose |
|------|---------|
| `/tmp/dx-build-log.txt` | Full Maven build log |
| `/tmp/tep-tier-N-log.txt` | Tier N execution log |
| `/tmp/tep-tier-N-metrics.json` | Tier N metrics (pass/fail/skip counts) |
| `/tmp/tep-tier-N-output.txt` | Tier N stdout (for parsing by dx.sh) |

---

## Implementation Details

### TEP Integration in dx.sh

**Lines 250-268**: Phase handling
```bash
case "$PHASE" in
    test)
        if [[ "$TEP_FAIL_FAST_TIER" -eq 0 ]]; then
            GOALS+=("test")  # Standard Maven test
        fi
        ;;
esac
```

**Lines 536-609**: TEP execution loop
```bash
if [[ "$TEP_FAIL_FAST_TIER" -gt 0 && ... && $EXIT_CODE -eq 0 ]]; then
    # Run tiers 1 through fail-fast-tier
    for tier_num in {1..4}; do
        [[ $tier_num -gt $TEP_FAIL_FAST_TIER ]] && break
        bash run-test-tier.sh $tier_num
        [[ $? -ne 0 ]] && break  # Fail-fast
    done
fi
```

### run-test-tier.sh Workflow

1. **Load tier config** from `tier-definitions.json`
2. **Extract tests** from `test-shards.json` for tier clusters
3. **Build Maven args**:
   - JUnit 5 parallel execution (concurrent mode)
   - Per-tier parallelism (8 threads for Tier 1, 1 for Tier 4)
   - Per-tier timeout (20s for Tier 1, 300s for Tier 4)
4. **Run Maven test** with Surefire includes filter
5. **Parse results** from build log
6. **Emit metrics** to JSON for dx.sh to parse

### Fail-Fast Logic

```bash
# For each tier
TIER_EXIT=$?
if [[ $TIER_EXIT -ne 0 ]]; then
    EXIT_CODE=$TIER_EXIT
    if [[ "${TEP_CONTINUE_ON_FAILURE:-0}" != "1" ]]; then
        break  # Stop at first failure
    fi
fi
```

---

## Command-Line Options

### dx.sh Options

```bash
# Enable TEP with tier limit
bash scripts/dx.sh test --fail-fast-tier 1   # Tier 1 only
bash scripts/dx.sh test --fail-fast-tier 2   # Tiers 1-2
bash scripts/dx.sh test --fail-fast-tier 3   # Tiers 1-3
bash scripts/dx.sh test --fail-fast-tier 4   # All tiers

# Combine with other options
bash scripts/dx.sh test --fail-fast-tier 1 -pl yawl-engine,yawl-elements
bash scripts/dx.sh compile test --fail-fast-tier 2
```

### Environment Variables

```bash
# Enable TEP
TEP_FAIL_FAST_TIER=2          # Run Tiers 1-2 with fail-fast
TEP_CONTINUE_ON_FAILURE=1     # Continue despite failures
TEP_DRY_RUN=1                 # Show tests without running

# dx.sh options
DX_VERBOSE=1                  # Show Maven output
DX_OFFLINE=1                  # Force offline mode
DX_TIMINGS=1                  # Capture timing metrics
```

### run-test-tier.sh Options

```bash
bash scripts/run-test-tier.sh 1                # Run Tier 1
bash scripts/run-test-tier.sh 2 --continue     # Run Tier 2, continue on failure
bash scripts/run-test-tier.sh 3 --dry-run      # Show Tier 3 tests
```

---

## Performance Characteristics

### Typical Execution Times

| Scenario | Time | Notes |
|----------|------|-------|
| Tier 1 only (--fail-fast-tier 1) | 3-5s | Fast unit tests |
| Tier 1 + 2 (--fail-fast-tier 2) | 10-15s | If T1 passes, includes medium tests |
| All tiers (--fail-fast-tier 4) | 30-60s | Full test suite |
| Single test failure in T1 | <10s | Stop immediately |
| Single test failure in T2 | <20s | T1 passes, T2 fails early |

### Parallelism Per Tier

| Tier | Max Threads | Rationale |
|------|-------------|-----------|
| Tier 1 | 8 | Fast tests (100ms each) can run in parallel |
| Tier 2 | 6 | Medium tests (100ms-5s) need some serialization |
| Tier 3 | 2 | Slow tests (5s-30s) use mostly resources |
| Tier 4 | 1 | Heavy tests (>30s) must run sequentially |

### Timeout Limits

| Tier | Timeout | Rationale |
|------|---------|-----------|
| Tier 1 | 20s | If 45 tests, avg 400ms (too slow, investigate) |
| Tier 2 | 60s | If 20 tests, avg 3s (reasonable) |
| Tier 3 | 180s | If 8 tests, avg 22.5s (reasonable) |
| Tier 4 | 300s | Single test up to 300s (5 min) |

---

## Output Examples

### Successful Run (Tier 1 Only)

```
dx: compile-test [yawl-engine]
dx: scope=yawl-engine | phase=compile-test | fail-strategy=fast

[Compile output...]

TEP: Tiered Test Execution (fail-fast-tier=1)

Tier 1: Fast Unit Tests
Description: Unit tests with execution time <100ms
Tests: 45 | Timeout: 20s | Parallelism: 8

[Test output...]

Tier 1 Results:
  Passed: 45
  Failed: 0
  Errors: 0
  Skipped: 0
  Duration: 3s

✓ Tier 1 PASSED

✓ SUCCESS | time: 15s | modules: 1 | tests: 45
```

### Failed Run (Tier 1)

```
dx: test [yawl-engine]
dx: scope=yawl-engine | phase=test | fail-strategy=fast

TEP: Tiered Test Execution (fail-fast-tier=1)

Tier 1: Fast Unit Tests
Tests: 45 | Timeout: 20s | Parallelism: 8

[Test output...]

Tier 1 Results:
  Passed: 44
  Failed: 1
  Errors: 0
  Skipped: 0
  Duration: 4s

✗ Tier 1 FAILED — stopping at tier 1
TEP: Stopping at tier 1 (fail-fast-tier=1)

✗ FAILED | time: 5s (exit 1) | failures: 1

→ Debug: cat /tmp/tep-tier-1-log.txt | tail -50
```

### Partial Run (Tiers 1-2, Fail in Tier 2)

```
TEP: Tiered Test Execution (fail-fast-tier=2)

Tier 1: Fast Unit Tests
Tests: 45 | Timeout: 20s | Parallelism: 8
✓ Tier 1 PASSED (45 passed in 3s)

Tier 2: Medium Integration Tests
Tests: 20 | Timeout: 60s | Parallelism: 6
✗ Tier 2 FAILED (19 passed, 1 failed in 12s)

✗ Tier 2 FAILED — stopping at tier 2
TEP: Stopping at tier 2 (fail-fast-tier=2)

✗ FAILED | time: 17s (exit 1) | failures: 1
```

---

## Customization

### Adding New Tests to Tier 1

1. **Tag your test** in `src/test/java/`:
   ```java
   @DisplayName("Fast unit test")
   @Tag("unit")
   public class MyFastTest {
       @Test
       @DisplayName("should complete in <100ms")
       void testFastOperation() {
           // Must execute in <100ms
       }
   }
   ```

2. **Re-generate test shards**:
   ```bash
   bash scripts/cluster-tests.sh
   ```

3. **Verify** the test appears in `.yawl/ci/test-shards.json` with `"cluster": 1`

### Adjusting Parallelism

Edit `.yawl/ci/tier-definitions.json`:
```json
{
  "tier_1": {
    "parallelism": 16  // Increase from 8 to 16 threads
  }
}
```

### Adjusting Timeouts

```json
{
  "tier_2": {
    "timeout_seconds": 120  // Increase from 60 to 120 seconds
  }
}
```

---

## Troubleshooting

### Tier 1 Taking Too Long

**Symptom**: Tier 1 execution time > 5 seconds
```bash
# Find slow tests
grep "Time elapsed:" /tmp/tep-tier-1-log.txt | grep -E '\.[5-9] sec|[1-9][0-9] sec'
# Move slow tests (>100ms) to Tier 2
bash scripts/cluster-tests.sh  # Re-cluster
```

### Tests Not Running in Tier 1

**Symptom**: Tier 1 shows "0 tests"
```bash
# Check test shards
jq '.shards[] | select(.cluster == 1) | .test_count' .yawl/ci/test-shards.json
# If 0, re-cluster: bash scripts/cluster-tests.sh
```

### Tier Timeout Exceeded

**Symptom**: "BUILD FAILURE ... Surefire timeout"
```bash
# Increase timeout for that tier
jq '.tier_definitions.tier_2.timeout_seconds = 120' .yawl/ci/tier-definitions.json | tee .yawl/ci/tier-definitions.json
```

### Maven Can't Find Tests

**Symptom**: "No tests found" in tier log
```bash
# Verify test includes patterns
grep "Dincludes" /tmp/tep-tier-1-log.txt
# Check test files exist: find yawl-engine -name "*Test.java"
```

---

## Migration from Traditional Test Runs

### Before (Standard dx.sh)
```bash
bash scripts/dx.sh test  # Run ALL tests, stop at first failure
# Time: 2-5 minutes
```

### After (TEP Tier 1 Only)
```bash
bash scripts/dx.sh test --fail-fast-tier 1  # Fast feedback
# Time: 3-5 seconds
```

### Workflow
1. Use `--fail-fast-tier 1` in local development (fast feedback)
2. Use `--fail-fast-tier 2` in CI for medium confidence
3. Use `--fail-fast-tier 4` before pushing to main (full validation)

---

## Integration with CI/CD

### GitHub Actions Example

```yaml
name: Test
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 25

      # Fast feedback: Tier 1 only
      - name: "Tier 1: Unit Tests (fail-fast)"
        run: bash scripts/dx.sh test --fail-fast-tier 1

      # Medium confidence: Tiers 1-2
      - name: "Tiers 1-2: Unit + Integration (fail-fast)"
        if: success()
        run: bash scripts/dx.sh test --fail-fast-tier 2

      # Full validation: All tiers
      - name: "All Tiers: Full Test Suite"
        if: success()
        run: bash scripts/dx.sh test --fail-fast-tier 4
```

---

## Metrics & Observability

### JSON Metrics Output

Each tier emits metrics to `/tmp/tep-tier-N-metrics.json`:
```json
{
  "tier": "1",
  "passed": 45,
  "failed": 0,
  "errors": 0,
  "skipped": 0,
  "duration_sec": 3
}
```

### Building Analytics

```bash
# Aggregate metrics across runs
for file in /tmp/tep-tier-*-metrics.json; do
  jq '.passed, .failed, .duration_sec' "$file"
done | jq -s 'group_by(.tier) | map({tier: .[0].tier, avg_passed: (map(.passed) | add / length)})'
```

### Monitoring Dashboard Integration

Post metrics to monitoring system:
```bash
for tier in {1..4}; do
  jq --arg tier "$tier" \
    '.metrics.tiers[$tier] = input' \
    /tmp/tep-tier-${tier}-metrics.json
done
```

---

## Future Enhancements

1. **Dynamic Tier Reassignment**: Move tests between tiers based on runtime
2. **Predictive Timeout**: Use machine learning to predict test duration
3. **Flaky Test Detection**: Quarantine tests that fail intermittently
4. **Test Impact Analysis**: Run only tests affected by changes
5. **Distributed Execution**: Run tiers across multiple machines

---

## References

- **Theory**: Chicago TDD model (fast feedback loops)
- **Frameworks**: JUnit 5 parallel execution, Maven Surefire
- **Related Docs**:
  - `.claude/rules/chicago-tdd.md` (TDD best practices)
  - `.claude/docs/BENCHMARK.md` (Performance benchmarking)

---

**Status**: ✅ Production Ready
**Last Verified**: 2026-02-28
**Maintainer**: Engineering Team
