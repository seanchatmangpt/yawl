# YAWL Build Timing Metrics

## Overview

The build timing infrastructure provides fast feedback for unit test execution and tracks performance trends across builds.

## Fast-Verify Profile

The `fast-verify` profile is optimized for ultra-fast unit test verification (<10 seconds):

```bash
# Run unit tests only (all modules)
mvn clean test -P fast-verify

# Run unit tests for a single module
mvn clean test -P fast-verify -pl yawl-engine

# Run via dx.sh with timing metrics enabled
DX_TIMINGS=1 bash scripts/dx.sh
```

### Profile Features

- **Single JVM Execution** (`forkCount=1`): Avoids JVM startup overhead (300-500ms per fork)
- **Fork Reuse** (`reuseForks=true`): Keeps single JVM alive across all test classes
- **Fast Fail** (`skipAfterFailureCount=1`): Stops on first test failure for immediate feedback
- **Unit Tests Only** (`@Tag("unit")`): Excludes integration, docker, slow, chaos test groups
- **Analysis Disabled**: JaCoCo, SpotBugs, PMD, Checkstyle all skipped
- **Test Reports**: Generates Surefire XML/plain text reports with execution times
- **Target Execution**: <10 seconds total for full unit suite

### Test Tag Taxonomy

All 200+ test classes are explicitly tagged with `@Tag(...)`:

| Tag | Count | Description | Excluded from fast-verify? |
|-----|-------|-------------|----------------------------|
| `unit` | ~131 | Pure in-memory tests, no I/O, no DB | ✗ (included) |
| `integration` | ~53 | Real engine/DB via H2, no Docker | ✓ |
| `slow` | ~19 | Performance benchmarks, ArchUnit scans | ✓ |
| `docker` | ~3 | Testcontainers requiring Docker | ✓ |
| `chaos` | ~2 | Network/failure injection tests | ✓ |
| `validation` | ~1 | Parallelization safety validation | ✓ |

## Build Timing Metrics

When `DX_TIMINGS=1` is enabled, build metrics are recorded in append-only JSON Lines format.

### Schema

See `build-timings-schema.json` for the complete JSON schema.

### Entry Format

```json
{
  "timestamp": "2026-02-28T03:05:12Z",
  "elapsed_sec": 8.45,
  "test_count": 127,
  "test_failed": 0,
  "modules_count": 12,
  "success": true
}
```

### Recording Metrics

```bash
# Enable timing metrics for a build
DX_TIMINGS=1 bash scripts/dx.sh

# Or with specific modules
DX_TIMINGS=1 bash scripts/dx.sh -pl yawl-engine,yawl-stateless
```

After each build, an entry is appended to `build-timings.json`.

## Analysis Tools

### Basic Timing Analysis

```bash
# View overall timing statistics
bash scripts/analyze-build-timings.sh

# Show recent 5 builds
bash scripts/analyze-build-timings.sh --recent 5

# Display trend analysis (bar graphs)
bash scripts/analyze-build-timings.sh --trend

# Show percentile distribution (P50, P95, P99)
bash scripts/analyze-build-timings.sh --percentile
```

### Output Example

```
Build Timing Summary
=====================

Build Count:    42 (40 passed, 2 failed)
Total Time:     327.2 seconds
Min Time:       7.89 sec
Max Time:       12.45 sec
Avg Time:       7.79 sec

Trend Analysis (Last 10 Builds)
================================

  ✓  7.89 sec | ════════════════
  ✓  8.12 sec | ════════════════
  ✓  7.95 sec | ════════════════
  ✗  11.23 sec | ████████████████████  (⚠ Regression: +44% vs average)
  ✓  7.88 sec | ════════════════
  ...

Slowest Tests (Last Build)
==========================

  0.087 sec - YWorkItemTest.testWorkItemCreation
  0.062 sec - YSpecificationTest.testSpecificationLoading
  0.051 sec - YEngineTest.testEngineInitialization
```

## Workflow Integration

### Local Development

1. Make code changes
2. Run fast verification: `DX_TIMINGS=1 bash scripts/dx.sh`
3. View timing metrics: `bash scripts/analyze-build-timings.sh --recent 1`
4. Commit if tests pass

### Pre-Commit Hook

Add timing metric capture to your pre-commit hook:

```bash
#!/bin/bash
DX_TIMINGS=1 bash scripts/dx.sh || exit 1
bash scripts/analyze-build-timings.sh --recent 1
```

### CI/CD Pipeline

Enable timing in your CI configuration:

```yaml
# .github/workflows/build.yml
- name: Build with timing metrics
  run: |
    DX_TIMINGS=1 bash scripts/dx.sh
    bash scripts/analyze-build-timings.sh --trend
```

## Performance Tuning

### Baseline Target: <10 seconds

The fast-verify profile targets <10 seconds for the complete unit test suite. If your builds exceed this:

1. **Check slowest tests**: `bash scripts/analyze-build-timings.sh`
2. **Identify bottlenecks**: Look for tests >100ms
3. **Investigate test isolation issues**:
   - Are tests creating expensive resources (DB connections)?
   - Are tests missing proper cleanup in `@AfterEach`?
   - Can tests share setup via `@BeforeAll`?
4. **Consider test parallelization**: Surefire can run tests in parallel
   - Set `<threadCount>4</threadCount>` in Surefire config
   - Watch for test isolation issues with shared state

### Regression Detection

The analysis script flags regressions automatically:

- **Deviation > 10%**: Reported as "⚠ Performance Regression"
- **Deviation < -10%**: Reported as "✓ Performance Improvement"

### Monitoring Trends

Track 50th, 95th, and 99th percentile build times:

```bash
bash scripts/analyze-build-timings.sh --percentile
```

This helps detect gradual slowdowns (P95 creeping up over weeks).

## FAQ

### Q: How do I reset timing history?

```bash
# Clear all timing data
rm /home/user/yawl/.yawl/timings/build-timings.json

# Keep schema reference
# (Schema file is not cleared)
```

### Q: Can I use fast-verify in CI/CD?

Yes! Fast-verify is designed for both local and CI use. The profile is safe for continuous integration because:
- It only runs unit tests (no environment dependencies)
- It produces deterministic results
- Timing variations are expected and tracked

### Q: Why does the profile disable JaCoCo?

JaCoCo bytecode instrumentation adds 15-25% overhead. For local development feedback, this overhead is wasted. Use the default profile for coverage reports before merging:

```bash
mvn clean test  # Default profile includes JaCoCo
```

### Q: How do I identify slow tests?

Three options:

1. **Fast method**: `bash scripts/analyze-build-timings.sh` (last 5 builds)
2. **Detailed method**: Review Surefire reports in `target/surefire-reports/`
3. **Manual method**: Look for `Time elapsed:` in Maven output

### Q: Can I exclude specific test classes?

Add the appropriate `@Tag` to test classes:

```java
@Tag("slow")  // Exclude from fast-verify
class MySlowTest {
    // ...
}
```

Then customize the `fast-verify` profile's `excludedGroups`.

### Q: Why is timing still >10 seconds?

Common causes:
- **Test isolation**: Tests not cleaning up resources (DB, file handles, threads)
- **Slow I/O**: Tests hitting filesystem or network unexpectedly
- **Heavy test setup**: Database initialization, class loading in `@BeforeEach`
- **JVM warmup**: First few tests are slower due to JIT compilation

Use `--percentile` to check if the issue is widespread or just a few slow tests.

## Files

- `.yawl/timings/build-timings.json` — Append-only metrics (JSON Lines)
- `.yawl/timings/build-timings-schema.json` — Schema reference
- `.yawl/timings/README.md` — This documentation
- `scripts/dx.sh` — Build script with `DX_TIMINGS=1` support
- `scripts/analyze-build-timings.sh` — Analysis tool
- `pom.xml` — `fast-verify` profile definition

## References

- [CLAUDE.md](../../CLAUDE.md) — Project standards
- [Surefire Test Report Plugin](https://maven.apache.org/surefire/maven-surefire-report-plugin/)
- [JUnit 5 Tags](https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering)
