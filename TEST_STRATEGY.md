# YAWL Test Strategy

This document describes the test execution strategy, tag taxonomy, and recommended
local development workflows for fast feedback loops.

## Test Tag Taxonomy

All test classes carry an explicit `@Tag` annotation from `org.junit.jupiter.api.Tag`.
The taxonomy has five categories:

| Tag           | Count | Description                                               | Typical Time    |
|---------------|-------|-----------------------------------------------------------|-----------------|
| `unit`        | ~131  | Pure in-memory logic. No database, no I/O, no network.   | < 100ms/class   |
| `integration` | ~53   | Real YEngine + H2 in-memory DB. No Docker required.      | 200ms–2s/class  |
| `slow`        | ~19   | Perf benchmarks, ArchUnit class scans, scalability tests. | 5s–60s/class    |
| `docker`      | ~3    | Testcontainers: MySQL 8.4, PostgreSQL 16. Docker required.| 30s–120s/class  |
| `chaos`       | ~2    | Network delay and service failure injection tests.        | 10s–30s/class   |

## Maven Profiles

### `quick-test` — Fastest local feedback (unit tests only)

```bash
mvn clean test -P quick-test
# or for a single module:
mvn clean test -P quick-test -pl yawl-engine
```

Characteristics:
- Runs only `@Tag("unit")` tests (approximately 131 classes)
- JaCoCo disabled (saves 15-25% overhead from bytecode weaving)
- Single forked JVM (`forkCount=1 reuseForks=true`)
- Fails on first broken test (`skipAfterFailureCount=1`)
- Estimated time: 8-15 seconds on a 4-core laptop

### `fast` (default) — Standard local dev run

```bash
mvn clean test
# equivalent to:
mvn clean test -P fast
```

Characteristics:
- Excludes `integration`, `slow`, `docker`, `containers`, `chaos`
- Runs only `unit`-tagged and untagged tests
- Parallel execution at class and method level (`classesAndMethods`)
- `threadCount=2` per core
- Estimated time: 10-20 seconds

### `integration-test` — Unit + integration (no Docker)

```bash
mvn clean test -P integration-test
```

Characteristics:
- Runs `unit` and `integration` tagged tests
- Excludes Docker, slow, and chaos tests
- Requires H2 in-memory database (auto-configured via `database.type=h2`)
- Estimated time: 45-90 seconds

### `docker` — Full integration suite (Docker required)

```bash
mvn clean test -P docker
```

Characteristics:
- Runs `integration` and `docker` tagged tests
- Requires Docker daemon (uses Testcontainers for MySQL and PostgreSQL)
- Excludes `slow` and `containers` (chaos/benchmark)
- Estimated time: 3-8 minutes (Docker container pull + startup)

### `ci` — CI pipeline with coverage

```bash
mvn clean verify -P ci
```

Characteristics:
- Auto-activates when `CI` environment variable is set
- Enables JaCoCo coverage reporting
- Stricter enforcer rules (Java >= 25, Maven >= 3.9)
- Used in GitHub Actions

## Slow Tests Reference

The following test classes are marked `@Tag("slow")` and are excluded by default.
Run them explicitly when validating performance characteristics before a release.

| Test Class                          | Why It Is Slow                                     | When to Run         |
|-------------------------------------|----------------------------------------------------|---------------------|
| `PerformanceTest`                   | 1000-iteration benchmark, DB latency measurement   | Pre-release         |
| `ScalabilityTest`                   | 50-concurrent-user load test with real engine      | Pre-release         |
| `EnginePerformanceBaseline`         | Capture throughput baseline for regression         | Pre-release         |
| `HibernatePerformanceRegressionTest`| Hibernate vs raw JDBC regression test              | After ORM changes   |
| `MigrationPerformanceBenchmark`     | Full schema migration timing                       | After schema changes|
| `TestConcurrentCaseExecution`       | 20+ concurrent threads, timing-sensitive           | Pre-release         |
| `VirtualThreadPinningTest`          | Detects JVM pinning via `jdk.tracePinnedThreads`   | After sync changes  |
| `Interface_ClientVirtualThreadsTest`| High-concurrency virtual thread stress test        | After async changes |
| `TestUnmarshalPerformance`          | Large-XML parse benchmark, memory leak detection   | After XML changes   |
| `PerformanceRegressionParametrizedTest` | Multi-backend parametrized performance test    | Pre-release         |
| `PerformanceMonitoringTest`         | JVM snapshot capture with file I/O                 | After lib updates   |
| `YawlCycleDetectionTest`            | ArchUnit: scans all compiled class files            | After refactoring   |
| `YawlLayerArchitectureTest`         | ArchUnit: enforces layer dependency rules           | After refactoring   |
| `YawlPackageBoundaryTest`           | ArchUnit: checks package boundary violations        | After refactoring   |
| `DependencySecurityPolicyTest`      | Parses OWASP Dependency-Check XML report            | After dep updates   |
| `TestExecutionTimeAnalyzer`         | Parses JUnit XML reports from filesystem            | After test changes  |
| `TestMaintenanceScorer`             | Scans source files for complexity metrics           | After refactoring   |
| `BuildSystemTest`                   | Executes real `mvn clean compile` subprocess        | After build changes |
| `Java25BuildWorkflowIntegrationTest`| Executes Java 25 build workflow end-to-end          | After build changes |

## Docker Tests Reference

| Test Class                          | Docker Image Required    | Why Docker Is Required            |
|-------------------------------------|--------------------------|-----------------------------------|
| `PostgresContainerIntegrationTest`  | postgres:16              | Real PostgreSQL persistence layer |
| `MySQLContainerIntegrationTest`     | mysql:8.4                | Real MySQL persistence layer      |
| `NetworkDelayResilienceTest`        | H2 + network simulation  | Network delay injection           |

## Best Practices for Test Development

### Writing New Tests

1. Always add `@Tag` to every new test class. Choose the lowest-friction category
   that still validates the behavior under test.

2. Prefer `@Tag("unit")` whenever possible. Unit tests run in every developer's
   inner loop. A test that can be made unit-level should be unit-level.

3. Use `@Tag("integration")` only when the test actually needs a running YEngine
   or an H2 database connection. Do not use it for tests that merely import
   engine classes without using them.

4. Use `@Tag("slow")` for any test with a `Thread.sleep`, timing assertion,
   ArchUnit scan, or iteration count above 100.

5. JMH benchmark classes in `performance/jmh/` are not executed by Surefire
   and do not need `@Tag`. They are invoked via `AllBenchmarksRunner.main()`.

### Parallel Execution Safety

Tests tagged `unit` must be stateless and safe to run in parallel. The Surefire
configuration uses `parallel=classesAndMethods` with `threadCount=2`. Unit tests
must not:
- Share mutable static state
- Write to the same file path
- Bind to a fixed network port

Integration tests that use `YEngine.getInstance()` share the same H2 singleton.
These tests run with the `integration` tag which is excluded from the parallel
`quick-test` profile, avoiding contention.

### Running a Single Test Class

```bash
# Run one test class by name
mvn clean test -Dtest=TestYSpecificationID -pl yawl-engine

# Run all tests in a package
mvn clean test -Dtest="org.yawlfoundation.yawl.elements.*" -pl yawl-elements

# Run tests by tag without a profile
mvn clean test -Dgroups=unit -pl yawl-engine
```

### Estimated Speedup Summary

| Profile           | Tests Run | Approx Time | vs Full Suite |
|-------------------|-----------|-------------|---------------|
| `quick-test`      | ~131      | 8-15s       | ~20x faster   |
| `fast` (default)  | ~131      | 10-20s      | ~15x faster   |
| `integration-test`| ~184      | 45-90s      | ~5x faster    |
| `docker`          | ~55       | 3-8 min     | slower (Docker)|
| Full (`-P ci`)    | 204+      | 5-15 min    | baseline       |
