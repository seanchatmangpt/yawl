# YAWL Developer Build Guide

**Version:** 6.0.0-Beta | **Java:** 25 | **Last Updated:** 2026-02-18

---

## Quick Start (5 Minutes)

```bash
# 1. Compile changed modules only (~15 seconds)
bash scripts/dx.sh compile

# 2. Run tests for changed modules (~30 seconds)
bash scripts/dx.sh test

# 3. Full build all modules (~90 seconds)
bash scripts/dx.sh all
```

---

## Build System Overview

### DX Scripts (Preferred for Development)

| Script | Purpose | Time |
|--------|---------|------|
| `dx.sh` | Fast incremental build | 15-45s |
| `dx.sh compile` | Compile changed modules | ~15s |
| `dx.sh test` | Test changed modules | ~30s |
| `dx.sh all` | Full build all modules | ~90s |
| `dx-status.sh` | Build health dashboard | instant |
| `dx-lint.sh` | Fast SpotBugs check | ~20s |
| `dx-cache.sh` | Build artifact caching | varies |
| `dx-benchmark.sh` | Performance tracking | varies |
| `dx-security-scan.sh` | Security vulnerability scan | ~60s |

### Standard Maven Commands

| Command | Purpose | Time |
|---------|---------|------|
| `mvn -T 1.5C clean compile` | Parallel compile | ~45s |
| `mvn -T 1.5C clean test` | Parallel tests | ~90s |
| `mvn -T 1.5C clean package` | Full build with JAR | ~90s |
| `mvn clean verify -P analysis` | Full static analysis | ~180s |

---

## Agent DX Fast Loop

The fastest feedback loop for code agents:

```bash
# 1. Make code changes
# 2. Run fast verification
bash scripts/dx.sh compile

# 3. If compile succeeds, run tests
bash scripts/dx.sh test

# 4. Before commit, run full check
bash scripts/dx.sh all
```

### Environment Variables

```bash
DX_VERBOSE=1      # Show Maven output (default: quiet)
DX_CLEAN=1        # Force clean build (default: incremental)
DX_OFFLINE=1      # Force offline mode
DX_OFFLINE=0      # Force online mode
DX_FAIL_AT=end    # Run all modules even on failure
```

---

## Test Execution Strategies

### Single Test Execution

```bash
# Run specific test class
bash scripts/dx-test-single.sh YNetRunnerTest

# Run specific test method
bash scripts/dx-test-single.sh YNetRunnerTest#testCancelCase
```

### Continuous Testing

```bash
# Watch mode - runs tests on file change
bash scripts/test-watch.sh

# Watch specific module
bash scripts/test-watch.sh -m yawl-engine
```

### Flaky Test Detection

```bash
# Run tests 3 times to detect flakiness
bash scripts/test-analyze-flaky.sh 3

# Run 5 times on specific module
bash scripts/test-analyze-flaky.sh 5 -m yawl-engine
```

### Test Categories

Tests are tagged with categories:

```bash
# Run only unit tests
mvn test -Dgroups="unit"

# Run only integration tests
mvn test -Dgroups="integration"

# Run specific tags
mvn test -Dgroups="smoke,fast"
```

---

## Performance Optimization

### Build Artifact Caching

Cache target/ directories between sessions:

```bash
# Save artifacts before ending session
bash scripts/dx-cache.sh save

# Restore artifacts in new session
bash scripts/dx-cache.sh restore

# Check cache status
bash scripts/dx-cache.sh status
```

### RAM Disk for Build (Linux)

```bash
# Mount tmpfs for build output
sudo bash scripts/dx-setup-tmpfs.sh

# Check status
bash scripts/dx-setup-tmpfs.sh --status

# Unmount
sudo bash scripts/dx-setup-tmpfs.sh --undo
```

### Benchmark Tracking

```bash
# Benchmark current build
bash scripts/dx-benchmark.sh all

# View performance trend
bash scripts/dx-benchmark.sh trend
```

---

## Security Scanning

```bash
# Full security scan
bash scripts/dx-security-scan.sh

# Fast scan with cached NVD data
bash scripts/dx-security-scan.sh --fast

# Dependencies only
bash scripts/dx-security-scan.sh --deps

# Code analysis only
bash scripts/dx-security-scan.sh --code
```

---

## Docker Workflow

### Development Environment

```bash
# Start dev stack
bash docker/scripts/dev-up.sh

# Start with rebuild
bash docker/scripts/dev-up.sh --build

# View logs
bash docker/scripts/dev-logs.sh

# Stop
bash docker/scripts/dev-down.sh
```

### Containerized Testing

```bash
# Run tests in Docker container
bash scripts/dx-docker-test.sh

# Test specific module in container
bash scripts/dx-docker-test.sh --module yawl-engine

# Interactive shell in test container
bash scripts/dx-docker-test.sh --shell
```

### Production Build

```bash
# Build production image
docker build -t yawl-engine:6.0.0 -f docker/production/Dockerfile.engine .

# Run with Docker Compose
docker compose up -d
```

---

## Troubleshooting

### Module Not Found Error

**Problem:** `yawl-worklet` not found in pom.xml

**Solution:** This module was removed. Update scripts that reference it:
```bash
# Fixed in v6.0.0 - verify your scripts don't reference yawl-worklet
grep -r "yawl-worklet" scripts/
```

### Tests Fail with Parallel Execution

**Problem:** Tests with shared state fail when run in parallel

**Solution:** Add `@Execution(ExecutionMode.SAME_THREAD)` annotation:
```java
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.SAME_THREAD)
class MySingletonTest {
    // Tests now run sequentially
}
```

### Maven Out of Memory

**Problem:** `OutOfMemoryError: Java heap space`

**Solution:** Increase Maven memory:
```bash
export MAVEN_OPTS="-Xmx4g -XX:+UseContainerSupport"
```

### Missing File Watcher

**Problem:** `test-watch.sh` fails with "No file watcher found"

**Solution:** Install fswatch (macOS) or inotify-tools (Linux):
```bash
# macOS
brew install fswatch

# Linux (Debian/Ubuntu)
apt install inotify-tools
```

---

## Module Reference

| Module | Description | Dependencies |
|--------|-------------|--------------|
| `yawl-utilities` | Core utilities | none |
| `yawl-elements` | YAWL domain model | utilities |
| `yawl-authentication` | Auth providers | elements |
| `yawl-engine` | Workflow engine | elements, auth |
| `yawl-stateless` | Stateless engine | engine |
| `yawl-resourcing` | Resource service | engine |
| `yawl-scheduling` | Scheduling service | engine |
| `yawl-security` | Security layer | engine |
| `yawl-integration` | MCP/A2A integration | engine |
| `yawl-monitoring` | Observability | engine |
| `yawl-webapps` | Web applications | all |
| `yawl-control-panel` | Admin console | webapps |

---

## Best Practices

1. **Always compile before test** - Tests require compiled classes
2. **Use DX scripts for iteration** - Faster than full Maven
3. **Run `dx.sh all` before commits** - Catches issues early
4. **Cache artifacts** - `dx-cache.sh save` before ending session
5. **Check status** - `dx-status.sh` shows build health

---

## See Also

- [DX Cheatsheet](./DX-CHEATSHEET.md) - Quick command reference
- [Troubleshooting](./TROUBLESHOOTING.md) - Common issues and solutions
- [Java 25 Features](./JAVA-25-FEATURES.md) - Java 25 adoption guide
- [Build Performance](./BUILD-PERFORMANCE.md) - Maven optimization details
