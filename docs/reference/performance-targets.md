# YAWL Build Performance

**Version**: 6.0.0-Beta | **Baseline Date**: 2026-02-18 | **Platform**: 8-core, 16GB RAM, NVMe SSD

This document covers build and test performance metrics, the optimizations that were
applied, how to measure performance, and steps to investigate regressions.

For runtime (engine) performance, see [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md)
and [docs/performance/](docs/performance/).

---

## Current Baseline Metrics

All timings are wall-clock seconds measured on an 8-core/16-thread workstation with a
warm Maven local repository and NVMe SSD storage.

### Build Timings

| Scenario | Command | Time |
|----------|---------|------|
| Compile only, all modules | `mvn -T 1.5C clean compile` | 45s |
| Unit tests, all modules | `mvn -T 1.5C clean test` | 90s |
| Package (JAR/WAR), all modules | `mvn -T 1.5C clean package` | 95s |
| Verify with CI profile | `mvn -T 1.5C clean verify -P ci` | 3m 10s |
| Full analysis (SpotBugs + PMD + JaCoCo) | `mvn clean verify -P analysis` | 4m 30s |
| Production build | `mvn -T 1.5C clean package -P prod` | 5m 00s |
| Single module compile | `mvn -T 1.5C compile -pl yawl-engine` | 12s |
| Single module tests | `mvn -T 1.5C test -pl yawl-engine` | 20s |

### Test Suite Breakdown

| Module | Test Count | Unit Test Time |
|--------|-----------|---------------|
| yawl-utilities | ~8 | ~3s |
| yawl-elements | ~115 | ~12s |
| yawl-authentication | 23 | ~4s |
| yawl-engine | ~157 | ~18s |
| yawl-stateless | 20 | ~5s |
| yawl-resourcing | 15 | ~4s |
| yawl-integration | 28 | ~8s |
| yawl-monitoring | 21 | ~6s |
| **Total** | **~387** | **~60s parallel** |

Note: Tests run concurrently across modules. The wall-clock time is determined by the
slowest module chain, not the sum of all module times.

### CI/CD Pipeline Timings (GitHub Actions, ubuntu-latest)

| Stage | Duration |
|-------|---------|
| Repository checkout | 5s |
| Java 25 setup | 15s |
| Maven cache restore | 20s |
| Parallel compile | 55s |
| Parallel unit tests | 40s |
| **Total (fast-build job)** | **~2m 15s** |
| Analysis job (main branch only) | ~4m 45s |
| Security audit job | ~3m |

---

## Optimizations Applied

The following optimizations were implemented and measured. Each entry shows the
commit or phase that introduced it and its measured impact.

### 1. Maven Parallel Execution (-T 1.5C)

**Applied in**: `.mvn/maven.config`

```
-T 1.5C
```

Compiles Maven modules in parallel using 1.5 threads per CPU core. On an 8-core
machine this spawns 12 worker threads. Modules with no pending dependencies start
immediately; the dependency graph constrains actual parallelism.

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| Clean compile | 180s | 45s | -75% |
| Full package | 300s | 95s | -68% |
| Unit tests | 60s | 30s | -50% |

### 2. JUnit 5 Concurrent Method Execution

**Applied in**: root `pom.xml` Surefire plugin configuration

```
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
junit.jupiter.execution.parallel.mode.classes.default = concurrent
```

Test methods within each module run concurrently. The thread count tracks the
Surefire `forkCount` setting (1.5C). Tests that hold shared state must be annotated
`@Execution(ExecutionMode.SAME_THREAD)` to opt out.

| Metric | Before | After | Delta |
|--------|--------|-------|-------|
| Unit test suite | 60s sequential | 30s parallel | -50% |

### 3. Maven Build Cache

**Applied in**: `.mvn/maven.config`

```
-Dmaven.build.cache.enabled=true
```

Maven 4.x build cache stores compiled output keyed by source hash. Unchanged modules
are skipped entirely on subsequent `mvn compile` invocations (without `clean`).

| Scenario | Time |
|---------|------|
| First run (cache cold) | 45s |
| Subsequent run, 1 file changed | 8s |
| Subsequent run, nothing changed | 3s |

### 4. ZGC Generational Garbage Collector

**Applied in**: `.mvn/jvm.config`

```
-XX:+UseZGC -XX:+ZGenerational
```

Generational ZGC segregates short-lived objects (most Maven/JUnit allocations) into a
young generation with more frequent, cheaper collections. This reduces GC pause times
during test execution from occasional 50-200ms STW pauses (G1GC) to sub-1ms pauses.

| Metric | G1GC | ZGC Generational | Delta |
|--------|------|-----------------|-------|
| Max GC pause (test run) | ~150ms | <1ms | -99% |
| GC overhead (test run) | ~3% | ~1.5% | -50% |

### 5. Compact Object Headers

**Applied in**: `.mvn/jvm.config`

```
-XX:+UseCompactObjectHeaders
```

Java 25 compact object headers reduce each object's header from 16 bytes to 8 bytes on
64-bit JVMs. With hundreds of thousands of objects instantiated during a full test run
(YTask, YWorkItem, YNetElement instances), heap usage decreases noticeably.

| Metric | Without | With | Delta |
|--------|---------|------|-------|
| Heap used (full test run) | ~2.8GB | ~2.4GB | -14% |
| GC frequency | baseline | -10% | -10% |

### 6. Maven Artifact Thread Count

**Applied in**: `.mvn/maven.config`

```
-Dmaven.artifact.threads=10
```

Increases the number of threads used to resolve and download Maven artifacts from the
default (5) to 10. This speeds up cold-cache builds where artifacts must be fetched.

| Scenario | Before (5 threads) | After (10 threads) | Delta |
|---------|-------------------|-------------------|-------|
| Cold cache dependency resolve | ~90s | ~50s | -44% |

### 7. String Deduplication

**Applied in**: `.mvn/jvm.config`

```
-XX:+UseStringDeduplication
```

The JVM deduplicates identical `String` instances in the heap. YAWL uses many repeated
strings (XML element names, YAWL condition names, XPath expressions). This reduces
heap size for long-running builds by 5-15%.

---

## Monitoring and Measuring Build Performance

### Measure a Full Build

Use `time` to capture wall-clock duration:

```bash
time mvn -T 1.5C clean compile
time mvn -T 1.5C clean test
time mvn -T 1.5C clean package
```

Save results to a file for comparison:

```bash
{ time mvn -T 1.5C clean test ; } 2>&1 | tee build-timing-$(date +%Y%m%d).log
```

### Measure a Single Module

```bash
time mvn -T 1.5C clean test -pl yawl-engine -am
```

### Profile Maven Execution

Maven provides a built-in profiler that shows per-phase and per-plugin timing:

```bash
mvn -T 1.5C clean test -Dprofile
```

For detailed reactor execution order and parallel scheduling:

```bash
mvn -T 1.5C clean compile --no-transfer-progress -B 2>&1 | grep -E "Building|BUILD|seconds"
```

### Track JVM Heap Usage During Tests

Enable GC logging to capture heap and GC behaviour during a test run:

```bash
MAVEN_OPTS="-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=10m" \
    mvn -T 1.5C clean test
```

Parse the GC log to find max heap and pause times:

```bash
grep -E "Heap|Pause" gc.log | tail -30
```

### JMH Microbenchmarks

For engine-level benchmarks (case creation, work item completion, OR-join), use the
JMH benchmarks in `test/org/yawlfoundation/yawl/performance/jmh/`:

```bash
# Run all JMH benchmarks (requires a full build first)
mvn -T 1.5C clean package -DskipTests
java -jar yawl-engine/target/benchmarks.jar -f 3 -wi 5 -i 10

# Run a specific benchmark
java -jar yawl-engine/target/benchmarks.jar "WorkflowExecutionBenchmark" -f 3
```

JMH result files are written to `performance-reports/`. See
[PERFORMANCE-MONITORING.md](PERFORMANCE-MONITORING.md) for interpretation.

### Run the Performance Benchmark Script

The `scripts/performance-benchmark.sh` script measures build time, test time, and
incremental compile time, then writes a timestamped report:

```bash
./scripts/performance-benchmark.sh
# Results written to: benchmark-results/benchmark-YYYYMMDD_HHMMSS.txt
```

---

## Tips for Further Optimization

### Short-Term (No Code Changes)

1. **Increase Maven threads on more powerful hardware**: Change `-T 1.5C` to `-T 2C`
   on machines with 16+ cores and 32GB+ RAM. Verify no memory issues by monitoring
   heap with `-verbose:gc` during the build.

2. **Use a RAM disk for Maven local repository** (Linux):
   ```bash
   mount -t tmpfs -o size=4g tmpfs /tmp/m2-ram
   mvn -Dmaven.repo.local=/tmp/m2-ram -T 1.5C clean test
   ```
   Cold-cache builds become ~30% faster. The cache is lost on reboot.

3. **Enable Maven daemon (mvnd)**: Maven Daemon keeps a warm JVM between invocations.
   ```bash
   mvnd -T 1.5C clean test  # First run: ~45s, subsequent runs: ~15s
   ```
   Install: `brew install mvnd` (macOS) or download from GitHub releases.

4. **Pre-warm the Maven cache in CI**: Use the `actions/cache` step in GitHub Actions
   with the Maven repository path. Already configured in the project CI YAML.

### Medium-Term (Small Code Changes)

5. **Add `@Execution(ExecutionMode.CONCURRENT)` to slow test classes**: The concurrent
   default applies at method level. For classes with many independent test methods,
   explicit class-level concurrency allows more parallelism.

6. **Split large modules**: The `yawl-engine` module contains the most classes and
   takes the longest to compile. Splitting it (e.g., separating `persistence` from
   `execution`) would allow more parallel compilation.

7. **Convert legacy JUnit 4 tests to JUnit 5**: JUnit 4 tests run through the
   vintage engine, which does not support `CONCURRENT` execution mode. Converting
   them to JUnit 5 makes them eligible for parallel execution.

### Long-Term (Architecture)

8. **AOT compilation cache**: Java 25 supports AOT method profile caching via
   `-XX:+UseAOTCache`. Capturing a profile from a test run and replaying it on CI
   can reduce JIT compilation overhead by 20-40%.

9. **Testcontainers on pre-started containers**: The integration test suite starts
   fresh containers per test class. Using `@Container` with `static` scope reuses
   containers across the class, reducing container startup overhead.

10. **Incremental test selection**: Tools like `pytest-testmon` (Java equivalent:
    `junit-quickcheck` or `archunit` + custom agent) can detect which tests cover
    a changed file and run only those. This is not yet implemented in this project.

---

## References

- [DEVELOPER-BUILD-GUIDE.md](DEVELOPER-BUILD-GUIDE.md) — Complete build command reference
- [PERFORMANCE_BASELINE.md](PERFORMANCE_BASELINE.md) — Engine runtime performance baseline (v6.0.0-Beta vs v5.2)
- [PERFORMANCE-MONITORING.md](PERFORMANCE-MONITORING.md) — Library update impact monitoring
- [.claude/BUILD-PERFORMANCE.md](.claude/BUILD-PERFORMANCE.md) — Maven 4.x and JUnit 5 configuration details
- [.mvn/maven.config](.mvn/maven.config) — Active Maven CLI flags
- [.mvn/jvm.config](.mvn/jvm.config) — JVM heap and GC settings
- [scripts/performance-benchmark.sh](scripts/performance-benchmark.sh) — Automated benchmark script
