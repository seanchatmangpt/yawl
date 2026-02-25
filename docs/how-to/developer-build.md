# YAWL Developer Build Guide

**Version**: 6.0.0-Beta | **Java**: 25 | **Maven**: 4.x | **Updated**: 2026-02-18

This guide covers every build and test scenario for the YAWL project, from the fastest
compilation check to a full analysis run. All timings are measured on an 8-core machine
with a warm Maven local repository.

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Parallelization Explained](#parallelization-explained)
3. [Build Profiles](#build-profiles)
4. [Timing Reference](#timing-reference)
5. [Developer Scripts](#developer-scripts)
6. [Troubleshooting Slow Builds](#troubleshooting-slow-builds)
7. [Best Practices](#best-practices)

---

## Quick Reference

The most common commands, in order from fastest to most thorough.

```bash
# Check compilation only (fastest feedback)
./scripts/build-fast.sh

# Run unit tests only (standard feedback loop)
./scripts/test-quick.sh

# Run all tests including integration (pre-commit)
./scripts/test-full.sh

# Watch for changes and rebuild automatically
./scripts/watch-build.sh

# Equivalent Maven commands (scripts wrap these)
mvn -T 1.5C clean compile                           # ~45s
mvn -T 1.5C clean test -pl yawl-engine,yawl-elements  # ~35s (specific modules)
mvn -T 1.5C clean test                              # ~90s
mvn clean verify -P analysis                        # ~4m30s
```

| Goal | Command | Estimated Time |
|------|---------|---------------|
| Compilation check | `./scripts/build-fast.sh` | ~45s |
| Unit tests | `./scripts/test-quick.sh` | ~60s |
| All tests | `./scripts/test-full.sh` | ~90s |
| Static analysis | `mvn clean verify -P analysis` | ~4m30s |
| Security audit | `mvn clean verify -P security-audit` | ~3m |
| Production build | `mvn -T 1.5C clean package -P prod` | ~5m |

---

## Parallelization Explained

### What `-T 1.5C` Means

The `-T` flag controls Maven's parallel execution thread count. The `C` suffix means
"per CPU core". So `-T 1.5C` on an 8-core machine spawns 12 worker threads.

```
Cores  |  -T 1.5C threads  |  Old sequential  |  New parallel  |  Speedup
-------|-------------------|-----------------|----------------|----------
4      |  6                |  ~180s          |  ~105s         |  1.7x
8      |  12               |  ~180s          |  ~90s          |  2.0x
16     |  24               |  ~180s          |  ~70s          |  2.6x
```

### Why Not `-T 2C` or Higher?

The YAWL module dependency graph limits how much parallelism is useful. Modules must
compile in dependency order:

```
yawl-utilities
    |
yawl-elements  yawl-authentication
    |               |
    +-------+-------+
            |
       yawl-engine
            |
  +---------+---------+
  |                   |
yawl-stateless   yawl-resourcing
  |                   |
  +--------+----------+
           |
    yawl-worklet  yawl-scheduling  yawl-security
           |
    yawl-integration  yawl-monitoring
           |
      yawl-webapps  yawl-control-panel
```

With `-T 1.5C`, each horizontal tier compiles in parallel. Increasing threads beyond
the widest tier (3-4 modules) yields diminishing returns.

### How JUnit 5 Parallelism Works

Unit tests run concurrently within each module. Configuration lives in
`.mvn/maven.config` and the root `pom.xml` Surefire plugin settings:

```
junit.jupiter.execution.parallel.enabled = true
junit.jupiter.execution.parallel.mode.default = concurrent
junit.jupiter.execution.parallel.mode.classes.default = concurrent
```

Tests annotated `@Execution(ExecutionMode.SAME_THREAD)` run sequentially. Any test
with shared static state must carry that annotation to prevent race conditions.

### JVM Settings

The Maven JVM is pre-configured in `.mvn/jvm.config`:

```
-Xms1g -Xmx4g          # Heap: 1GB initial, 4GB max
-XX:+UseZGC             # Generational ZGC (low pause times)
-XX:+ZGenerational      # Generational mode for ZGC
-XX:+UseCompactObjectHeaders  # 4-8 bytes less per object
-XX:+UseStringDeduplication   # Deduplicate identical Strings
--enable-preview        # Java 25 preview features
```

If memory is constrained (less than 8GB RAM on the build machine), lower `-Xmx4g` to
`-Xmx2g` in `.mvn/jvm.config`.

---

## Build Profiles

Profiles are activated with `-P <name>`. Multiple profiles can be combined: `-P ci,sonar`.

| Profile | When to Use | Extra Tools | Approximate Overhead |
|---------|-------------|-------------|---------------------|
| _(default)_ | Daily development | None | Baseline |
| `java25` | Default for Java 25 builds | Compiler target 25 | None |
| `java24` | Forward-compat testing | Compiler target 24 | None |
| `fast` | Skipping all analysis | Analysis skipped | -30s |
| `ci` | Pull request gates | JaCoCo + SpotBugs | +90s |
| `prod` | Release builds | JaCoCo + SpotBugs + OWASP CVE | +3m |
| `security-audit` | Security review | OWASP CVE (non-failing) | +2m |
| `analysis` | Deep code quality check | JaCoCo + SpotBugs + Checkstyle + PMD | +3m |
| `sonar` | SonarQube push | JaCoCo + SonarQube upload | +2m |
| `online` | Update upstream BOMs | Spring Boot, OTel, Resilience4j BOMs | Varies |
| `docker` | Container image builds | Docker buildx | +5m |
| `release` | Maven Central publishing | GPG signing + Javadoc | +5m |

### Profile Examples

```bash
# Development: fastest possible (skip analysis entirely)
mvn -T 1.5C clean package -P fast

# CI gate on every pull request
mvn -T 1.5C clean verify -P ci

# Production release candidate
mvn -T 1.5C clean package -P prod

# Security audit without breaking the build
mvn -T 1.5C clean verify -P security-audit

# Full static analysis (weekly or pre-release)
mvn clean verify -P analysis

# Push coverage to SonarQube (requires SONAR_TOKEN env var)
mvn -T 1.5C clean verify -P sonar

# Resolve latest upstream BOM versions
mvn -T 1.5C clean compile -P online
```

### Enforcer Rules

The `validate` phase fails the build immediately if any of these conditions are true:

- Maven version is below 3.9
- Java version is below 21
- Any POM declares a duplicate dependency
- Any plugin lacks an explicit version number

These checks run before compilation, so a misconfigured environment is caught in under
5 seconds.

---

## Timing Reference

All timings are wall-clock seconds on an 8-core/16-thread workstation with 16GB RAM,
NVMe SSD, and a warm Maven local repository (`~/.m2`).

### Clean Builds (Cold Start)

| Scenario | Command | Time |
|----------|---------|------|
| Compile only | `mvn -T 1.5C clean compile` | ~45s |
| Compile + unit tests | `mvn -T 1.5C clean test` | ~90s |
| Package (JAR/WAR) | `mvn -T 1.5C clean package` | ~95s |
| Verify (package + integration) | `mvn -T 1.5C clean verify` | ~110s |
| With CI profile | `mvn -T 1.5C clean verify -P ci` | ~3m |
| With analysis profile | `mvn clean verify -P analysis` | ~4m30s |
| With prod profile | `mvn -T 1.5C clean package -P prod` | ~5m |

### Incremental Builds (Files Changed)

Maven 4.x build cache skips unchanged modules automatically. With cache warm:

| Change scope | Command | Time |
|-------------|---------|------|
| Single file, no tests | `mvn -T 1.5C compile` | ~8s |
| Single module, no tests | `mvn -T 1.5C compile -pl yawl-engine` | ~12s |
| Single module + tests | `mvn -T 1.5C test -pl yawl-engine` | ~20s |

### Baseline Before `-T 1.5C` (Historical)

For comparison, the sequential build times before parallel execution was enabled:

| Scenario | Sequential | Parallel | Improvement |
|----------|-----------|----------|-------------|
| Clean compile | ~180s | ~45s | 75% faster |
| Unit tests | ~60s | ~30s | 50% faster |
| Full verify | ~300s | ~110s | 63% faster |

### CI/CD Pipeline Timings (GitHub Actions, ubuntu-latest)

| Stage | Time |
|-------|------|
| Checkout | ~5s |
| Setup Java 25 | ~15s |
| Restore Maven cache | ~20s |
| Parallel compile | ~55s |
| Parallel unit tests | ~40s |
| Total fast-build job | ~2m15s |
| Analysis job (main branch only) | ~4m45s |
| Security job | ~3m |

---

## Developer Scripts

Four scripts in `scripts/` provide pre-configured, annotated build commands. Each
script prints an estimated time before running so you know what to expect.

### `scripts/build-fast.sh` — Compile Only

```bash
./scripts/build-fast.sh [MODULE]
```

Compiles all modules (or one module if specified) without running tests. Use this
when you want the fastest syntax and type-check feedback.

```bash
./scripts/build-fast.sh                     # All modules (~45s)
./scripts/build-fast.sh yawl-engine         # Engine only (~12s)
./scripts/build-fast.sh yawl-elements       # Elements only (~8s)
```

### `scripts/test-quick.sh` — Unit Tests Only

```bash
./scripts/test-quick.sh [MODULE]
```

Runs unit tests in parallel. Skips integration tests. Designed for the inner
development loop.

```bash
./scripts/test-quick.sh                     # All unit tests (~60s)
./scripts/test-quick.sh yawl-engine         # Engine tests only (~20s)
```

### `scripts/test-full.sh` — All Tests

```bash
./scripts/test-full.sh [--profile PROFILE]
```

Runs unit tests and integration tests. Suitable for pre-commit verification and CI.

```bash
./scripts/test-full.sh                      # All tests, default profile (~90s)
./scripts/test-full.sh --profile ci         # With JaCoCo + SpotBugs (~3m)
```

### `scripts/watch-build.sh` — Auto-Rebuild on Change

```bash
./scripts/watch-build.sh [MODULE]
```

Watches `src/` for file changes and triggers a fast recompile automatically.
Requires `inotifywait` (part of `inotify-tools` on Linux) or `fswatch` on macOS.

```bash
./scripts/watch-build.sh                    # Watch all source files
./scripts/watch-build.sh yawl-engine        # Watch engine source only
```

---

## Troubleshooting Slow Builds

### Symptom: Full build takes more than 3 minutes

1. Verify `-T 1.5C` is active. Check `.mvn/maven.config` contains `-T 1.5C`.
2. Check CPU throttling: `cat /proc/cpuinfo | grep "cpu MHz"` — should not be at idle floor.
3. Verify Maven local repository is on a fast disk (SSD vs HDD):
   ```bash
   time mvn help:effective-settings | grep localRepository
   ```
4. Check if a profile with heavy analysis is activated by default:
   ```bash
   mvn help:active-profiles
   ```

### Symptom: Tests fail only with parallel execution

Tests with shared static state fail non-deterministically under concurrent execution.

Diagnose by running the failing test class sequentially:
```bash
mvn test -pl <module> -Dtest=<FailingTestClass> -Djunit.jupiter.execution.parallel.enabled=false
```

If the test passes sequentially, it has a concurrency bug. Fix options:

- Annotate the class: `@Execution(ExecutionMode.SAME_THREAD)`
- Replace static fields with instance fields
- Use `@TestInstance(Lifecycle.PER_CLASS)` with proper setup/teardown

### Symptom: OutOfMemoryError during build

The default JVM settings allocate 4GB max. If other JVMs are running, this may exceed
available RAM.

Reduce heap in `.mvn/jvm.config`:
```
-Xmx2g
```

Or run with an explicit override:
```bash
MAVEN_OPTS="-Xmx2g" mvn -T 1.5C clean compile
```

### Symptom: Maven build cache causes incorrect incremental results

The build cache is enabled by default (`-Dmaven.build.cache.enabled=true` in
`.mvn/maven.config`). If a module produces wrong output after an incremental build,
invalidate the cache for that module:

```bash
mvn -T 1.5C clean install -pl <module> -Dmaven.build.cache.enabled=false
```

To disable the cache globally for one run:
```bash
mvn -T 1.5C clean test -Dmaven.build.cache.enabled=false
```

### Symptom: SpotBugs or PMD takes too long

The `analysis` profile runs SpotBugs, PMD, Checkstyle, and JaCoCo. Each adds 30-90
seconds. For day-to-day work, use the default profile and reserve `analysis` for
weekly checks or pre-release.

```bash
# Skip all static analysis and coverage
mvn -T 1.5C clean test -P fast
```

### Symptom: Maven version mismatch (Enforcer failure)

```
[ERROR] Rule 0: ... Maven version 3.8.x is below minimum 3.9
```

Use the project wrapper instead of a system Maven:
```bash
./mvnw -T 1.5C clean compile
```

The wrapper downloads and caches the correct Maven version automatically.

### Symptom: Compilation fails with `--enable-preview` errors

Preview features require matching `--enable-preview` at both compile time and test
runtime. Both are configured in `.mvn/jvm.config` and the root `pom.xml`. If running
Maven outside of the wrapper, ensure the JVM flags are applied:

```bash
MAVEN_OPTS="--enable-preview" mvn -T 1.5C clean compile
```

---

## Best Practices

### Development Cycle

1. Start the watch script in a terminal: `./scripts/watch-build.sh`
2. Edit source files — the watch script recompiles on save
3. Run targeted tests: `./scripts/test-quick.sh yawl-<module>`
4. Before committing: `./scripts/test-full.sh`

### Module-Targeted Builds

Running a full multi-module build for a single-module change wastes time. Use `-pl`
(project list) and `-am` (also make dependencies):

```bash
# Test only the engine and its dependencies
mvn -T 1.5C clean test -pl yawl-engine -am

# Test only the integration module
mvn -T 1.5C clean test -pl yawl-integration -am
```

### Keeping Tests Fast

- Avoid `Thread.sleep()` in tests; use `Awaitility` or `CountDownLatch`
- Mark I/O-bound tests with `@Tag("integration")` and exclude them from unit runs
- Use `@TestInstance(Lifecycle.PER_CLASS)` with `@BeforeAll` to share expensive setup
- Prefer in-memory H2 over full Hibernate/RDBMS where possible for unit tests

### CI vs Local Profiles

| Environment | Profile | Rationale |
|-------------|---------|-----------|
| Local dev | _(default)_ | Fast feedback, no analysis overhead |
| Pull requests | `ci` | JaCoCo gate, SpotBugs, must pass |
| Main branch | `ci` + nightly `analysis` | Full coverage tracking |
| Release | `prod` | OWASP CVE gate, no CVSS >= 7 deps |
| Security review | `security-audit` | OWASP report without blocking CI |

### Commit Pre-Flight Checklist

```bash
# 1. Ensure compilation is clean
./scripts/build-fast.sh

# 2. Run all tests
./scripts/test-full.sh

# 3. Stage only your files (never git add .)
git add src/... test/...

# 4. Commit with session URL
git commit -m "feat: description"
```

---

## References

- [PERFORMANCE.md](PERFORMANCE.md) — Baseline metrics and optimization impact
- [.claude/BUILD-PERFORMANCE.md](.claude/BUILD-PERFORMANCE.md) — Detailed Maven 4.x and JUnit 5 configuration
- [.claude/JAVA-25-FEATURES.md](.claude/JAVA-25-FEATURES.md) — Java 25 feature adoption
- [.mvn/maven.config](.mvn/maven.config) — Active Maven CLI flags
- [.mvn/jvm.config](.mvn/jvm.config) — JVM heap and GC configuration
- [pom.xml](pom.xml) — Root POM with all profiles and plugin versions
