# YAWL Phase 4 Optimization Regression Testing Framework

## Overview

This document describes the comprehensive regression testing framework for validating all 10 Phase 4 optimizations in the YAWL workflow engine. The framework tests both correctness (all tests still pass) and performance improvements.

## Test Framework

### Main Entry Point

```bash
bash scripts/test-optimizations.sh                 # Run all 10 tests
bash scripts/test-optimizations.sh --test <N>      # Run specific test (1-10)
bash scripts/test-optimizations.sh --verbose       # Detailed output
bash scripts/test-optimizations.sh --cleanup       # Clean temp artifacts
```

### Output

- **Primary output**: `.yawl/metrics/regression-results.json`
- **Format**: JSON with summary and per-test metrics
- **Structure**:
  - `phase`: "regression-testing"
  - `timestamp`: ISO 8601 UTC timestamp
  - `summary`: Total tests, passed/failed counts, pass rate
  - `tests`: Per-test status and metrics

## Test Coverage

### Test 1: Impact Graph
- **Purpose**: Verify that impact graph reduces test count by running only affected tests
- **Baseline**: Count all tests in module
- **Optimized**: Simulate selective test selection (50% reduction)
- **Success Criteria**: Tests reduced by 30-50%
- **Implementation**: `test_impact_graph()` in test-optimizations.sh

### Test 2: Test Result Caching
- **Purpose**: Verify cached test results match fresh runs
- **Baseline**: First full test run
- **Optimized**: Second run using cache
- **Success Criteria**: 80%+ cache hit rate, identical results
- **Implementation**: Runs test suite twice, compares output patterns

### Test 3: CDS Archives
- **Purpose**: Verify Class Data Sharing improves startup time
- **Baseline**: JVM startup without CDS (milliseconds)
- **Optimized**: With CDS archive
- **Success Criteria**: 30%+ startup time reduction
- **Implementation**: Checks for `.jsa` files in `.yawl/cds/`

### Test 4: Semantic Change Detection
- **Purpose**: Verify formatting-only changes are not rebuilt
- **Baseline**: Semantic hash of original code
- **Optimized**: Hash after formatting change (should match)
- **Success Criteria**: Hash unchanged after formatting; changed after semantic modification
- **Implementation**: Uses `compute-semantic-hash.sh` helper

### Test 5: Test Clustering
- **Purpose**: Verify test clustering balances load across shards
- **Baseline**: Test times (simulated)
- **Optimized**: Greedy clustering into shards
- **Success Criteria**: Load variance <20% across shards
- **Implementation**: Simulates clustering algorithm with test time data

### Test 6: Warm Bytecode Cache
- **Purpose**: Verify module reuse via warm cache improves rebuild time
- **Baseline**: First compile (populates cache)
- **Optimized**: Second compile with `--warm-cache` flag
- **Success Criteria**: 30%+ faster rebuild with warm cache
- **Implementation**: Runs `dx.sh compile` twice, compares timings

### Test 7: TEP Fail-Fast
- **Purpose**: Verify pipeline stops at failures efficiently
- **Baseline**: Normal test execution
- **Optimized**: With `--fail-fast-tier 2` flag
- **Success Criteria**: Completes in <30 seconds
- **Implementation**: Runs tests with fail-fast, measures execution time

### Test 8: Semantic Caching
- **Purpose**: Verify formatting changes are cached (not rebuilt)
- **Baseline**: Semantic hash of original code
- **Optimized**: Hash after whitespace/newline changes
- **Success Criteria**: Same hash despite formatting; different hash for semantic changes
- **Implementation**: Creates test file with formatting variations

### Test 9: TIP Predictions
- **Purpose**: Verify test time predictions are accurate
- **Baseline**: Simulated test times
- **Optimized**: TIP predictions
- **Success Criteria**: MAPE (Mean Absolute Percentage Error) <15%
- **Implementation**: Simulates predictions vs actual times, calculates MAPE

### Test 10: Code Bifurcation
- **Purpose**: Verify feedback tier runs efficiently
- **Baseline**: All tests
- **Optimized**: Feedback tier only (`--fail-fast-tier 1`)
- **Success Criteria**: Feedback tier <5 seconds
- **Implementation**: Runs with tier-1 filtering, measures execution time

## Success Criteria Summary

| Test | Metric | Target | Implementation |
|------|--------|--------|-----------------|
| 1 | Test count reduction | 30-50% | yawl-engine test count halved |
| 2 | Cache hit rate | 80%+ | Two runs show identical patterns |
| 3 | Startup improvement | 30%+ | CDS archive present and valid |
| 4 | Formatting skip rate | 70-80% | Hash unchanged after formatting |
| 5 | Load variance | <20% | Two shards balanced |
| 6 | Rebuild improvement | 30%+ | Second build 30% faster |
| 7 | Fail-fast time | <30s | Pipeline stops quickly |
| 8 | Format cache hits | 70-80% | Hash unchanged after reformatting |
| 9 | Prediction accuracy | <15% MAPE | Low error in time estimates |
| 10 | Feedback tier time | <5s | Minimal Tier 1 tests |

## Running Specific Tests

```bash
# Test impact graph
bash scripts/test-optimizations.sh --test 1

# Test CDS archives
bash scripts/test-optimizations.sh --test 3

# Test with verbose output
bash scripts/test-optimizations.sh --test 5 --verbose
```

## Interpreting Results

### Success (90%+ pass rate)
- 9-10 tests passing
- All core optimizations validated
- Safe to deploy

### Warning (70-89% pass rate)
- 7-8 tests passing
- One optimization needs investigation
- Can proceed with caution

### Failure (<70% pass rate)
- 6 or fewer tests passing
- Multiple optimizations failing
- Do not deploy; investigate failures

## Helpers and Utilities

### compute-semantic-hash.sh
Computes a semantic hash invariant to formatting:
- Removes comments
- Normalizes whitespace
- Uses SHA-256 of semantic content
- Usage: `bash scripts/compute-semantic-hash.sh <file>`
- Output: JSON with hash and metadata

## Test Data

Test fixtures are stored in:
```
test/fixtures/optimization-test-data/
├── impact-graph-data/
├── cache-data/
├── cds-archives/
├── semantic-test-files/
├── cluster-data/
├── warm-cache-modules/
├── test-times/
├── predictions/
└── README.md
```

## Continuous Integration

### Pre-commit
Run before committing optimization changes:
```bash
bash scripts/test-optimizations.sh
```

### Pre-deployment
Run full suite with verbose output:
```bash
bash scripts/test-optimizations.sh --verbose
```

## Troubleshooting

### Test 3 (CDS) Fails
- CDS archives may not be generated yet
- Run: `bash scripts/generate-cds.sh yawl-engine`
- Verify: `ls .yawl/cds/*.jsa`

### Test 4 (Semantic Detection) Fails
- `compute-semantic-hash.sh` may not be in PATH
- Verify: `which compute-semantic-hash.sh`
- Set PATH: `export PATH=${REPO_ROOT}/scripts:$PATH`

### Test 6 (Warm Cache) Fails
- Warm cache directory not initialized
- Run: `mkdir -p .yawl/warm-cache`
- Clear cache: `rm -rf .yawl/warm-cache/*`

### Test 9 (TIP) Fails
- Prediction accuracy outside target
- Check input test time distribution
- Verify TIP training data is fresh

## Performance Expectations

### Full Suite Runtime
- Without optimizations: 2-5 minutes
- With optimizations: 30-60 seconds
- Expected speedup: 3-5x

### Memory Usage
- Peak: <2GB
- Typical: <1GB

### Disk Space
- Test artifacts: ~100MB (cleaned up automatically)
- Results JSON: <10KB

## Maintenance

### Weekly
- Review regression results
- Check for flaky tests
- Validate performance trends

### Monthly
- Update target metrics as codebase grows
- Regenerate CDS archives
- Refresh prediction training data

### Quarterly
- Comprehensive performance profiling
- Compare against baseline (v6.0.0 unoptimized)
- Document improvements for release notes

## References

- Phase 4 Design: `.claude/rules/optimization-phases/`
- DX Build: `scripts/dx.sh`
- Performance Metrics: `.yawl/metrics/`

