# Build Tuning Reference: Complete Profile Guide

**Version**: 1.0
**Scope**: All Maven profiles in YAWL v6.0.0
**Purpose**: Choose the right profile for your task
**Last Updated**: February 28, 2026

---

## Table of Contents

1. [Profile Overview](#profile-overview)
2. [When to Use Each Profile](#when-to-use-each-profile)
3. [Profile Details](#profile-details)
4. [Performance Expectations](#performance-expectations)
5. [Common Issues and Solutions](#common-issues-and-solutions)
6. [Decision Tree](#decision-tree)

---

## Profile Overview

YAWL provides 8 built-in Maven profiles optimized for different scenarios:

| Profile | Time | Tests | Use Case | Parallelism |
|---------|------|-------|----------|-------------|
| `java25` | - | - | Default Java 25 setup (active by default) | N/A |
| `quick-test` | 10s | Unit only | Dev feedback loop | None |
| `agent-dx` | 10s | Unit only | Agent development | Within module |
| `fast-verify` | 12s | Unit + metrics | Timing analysis | Within module |
| `integration-parallel` | 85s | Unit + integration | Fast comprehensive test | 2C parallel |
| `ci` | 120s | All | CI/CD with coverage | Sequential + coverage |
| `prod` | 150s | All | Production release | Sequential + CVE scanning |
| *(default)* | 150s | All | Conservative safe | Sequential |

---

## When to Use Each Profile

### 1. `quick-test` — Dev Loop (10 seconds)

**Best for**: Rapid local development feedback

**What it does**:
- Runs unit tests only (@Tag("unit"))
- Disables JaCoCo (saves 15-25% time)
- Single JVM fork (forkCount=1)
- Fails fast on first error
- Skips docker/integration/slow tests

**Command**:
```bash
mvn clean test -P quick-test
```

**Time**: ~10 seconds (vs 150s default)

**When to use**:
- You changed a single Java file
- You want <15s feedback
- You're in active development (red-green-refactor)
- You're NOT testing engine state or persistence

**When NOT to use**:
- You modified engine code (need integration tests)
- You modified database schema (need integration tests)
- You're preparing a commit (use integration-parallel instead)

**Config**:
```xml
<groups>unit</groups>
<excludedGroups>integration,docker,containers,slow,chaos</excludedGroups>
<forkCount>1</forkCount>
<reuseForks>true</reuseForks>
<skipAfterFailureCount>1</skipAfterFailureCount>
```

**Example workflow**:
```bash
# Write code
vim YNetRunner.java

# Quick test
mvn test -P quick-test

# Fix failures
vim YNetRunner.java

# Test again
mvn test -P quick-test

# When satisfied, run full tests
mvn verify -P integration-parallel
```

---

### 2. `agent-dx` — Agent Development (10 seconds)

**Best for**: AI/agent code development

**What it does**:
- Runs unit tests only
- Disables all analysis (JaCoCo, SpotBugs, PMD, etc.)
- Sets threadCount=8 for parallelism
- Skips docker/integration/slow tests
- Focuses on code compilation and unit testing

**Command**:
```bash
# Single module
mvn compile test -P agent-dx

# Specific module with dependencies
mvn compile test -P agent-dx -pl yawl-engine -amd

# All modules
mvn compile test -P agent-dx
```

**Time**: ~10 seconds per module

**When to use**:
- You're developing a new feature with agent assistance
- You want minimal overhead (no analysis tools)
- You're testing locally before committing
- You need quick feedback loops

**When NOT to use**:
- You need code coverage reports
- You need static analysis (SpotBugs)
- You're testing integration logic
- You're preparing for production

**Config**:
```xml
<jacoco.skip>true</jacoco.skip>
<maven.javadoc.skip>true</maven.javadoc.skip>
<maven.source.skip>true</maven.source.skip>
<checkstyle.skip>true</checkstyle.skip>
<spotbugs.skip>true</spotbugs.skip>
<pmd.skip>true</pmd.skip>
<enforcer.skip>true</enforcer.skip>
<excludedGroups>integration,docker,containers,slow</excludedGroups>
```

**Typical use**:
```bash
# Start development session
bash scripts/dx.sh all

# Detects changed modules, compiles + tests in parallel
# Uses agent-dx profile automatically
```

---

### 3. `fast-verify` — Timing Analysis (12 seconds)

**Best for**: Performance debugging and test timing analysis

**What it does**:
- Runs unit tests with detailed timing reports
- Generates HTML test report in target/site/
- Identifies slowest tests
- Shows test execution timeline
- Disables coverage (like quick-test)

**Command**:
```bash
mvn clean test -P fast-verify
```

**Time**: ~12 seconds

**When to use**:
- You want to identify slow tests
- You're optimizing test performance
- You need to track timing trends
- You want visual reports

**Output**:
```
Slowest tests:
  • testDeadlockDetection (2.34s)
  • testComplexWorkflow (1.89s)
  • testStateRecovery (1.56s)

Report: target/site/surefire-report.html
```

**Config**:
```xml
<reportFormat>xml</reportFormat>
<reportFormat>plain</reportFormat>
<!-- Enables Surefire Report Plugin -->
```

---

### 4. `integration-parallel` — Production Testing (85 seconds)

**Best for**: Comprehensive verification with speed

**What it does**:
- Runs unit + integration tests in parallel
- forkCount=2C (2 processes per core)
- Each fork runs one test class (reuseForks=false)
- Thread-local YEngine isolation (Phase 3)
- Reduced timeout (120s vs 180s)
- 1.77x speedup vs sequential

**Command**:
```bash
# Full build with parallelization
mvn clean verify -P integration-parallel

# Just integration tests
mvn verify -P integration-parallel -DskipUnitTests=true

# Specific module
mvn verify -P integration-parallel -pl yawl-engine -amd
```

**Time**: ~85 seconds (vs 150s default)

**When to use**:
- You modified engine/core logic
- You need comprehensive testing quickly
- You're preparing a commit/PR
- You're in CI pipeline with time constraints
- You want 1.77x speedup with no code changes

**When NOT to use**:
- You need maximum safety (stress/chaos tests)
- You have <4GB RAM available
- You're testing in a constrained CI environment
- You need full static analysis

**Config**:
```xml
<failsafe.forkCount>2C</failsafe.forkCount>
<failsafe.reuseForks>false</failsafe.reuseForks>
<failsafe.threadCount>8</failsafe.threadCount>
<yawl.test.threadlocal.isolation>true</yawl.test.threadlocal.isolation>
<parallel>classesAndMethods</parallel>
```

**Safety guarantees**:
- Process isolation (separate JVMs)
- Thread-local state isolation
- 100% test pass rate
- <0.1% corruption risk

---

### 5. `ci` — Continuous Integration (120 seconds)

**Best for**: CI/CD pipelines with coverage requirements

**What it does**:
- Runs all tests (unit + integration)
- Enables JaCoCo coverage reporting
- Runs static analysis (SpotBugs)
- Enforces stricter rules (dependency convergence)
- Auto-activates when CI=true environment variable set

**Command**:
```bash
# Explicit activation
mvn clean verify -P ci

# Auto-activate in CI/CD
CI=true mvn clean verify

# With coverage report
mvn clean verify -P ci
open target/site/jacoco/index.html
```

**Time**: ~120 seconds

**When to use**:
- Running in GitHub Actions / Jenkins / GitLab CI
- You need code coverage metrics
- You need static analysis reports (SpotBugs, PMD)
- You need dependency convergence checks
- You're doing quality gates

**When NOT to use**:
- Local development (too slow)
- You need fast feedback (<30s)
- You don't need coverage reports

**Config**:
```xml
<jacoco.skip>false</jacoco.skip>
<maven.test.skip>false</maven.test.skip>
<!-- Enforces stricter rules -->
<!-- Runs SpotBugs and coverage -->
```

**CI/CD integration**:
```yaml
# GitHub Actions
- name: Test
  run: mvn clean verify -P ci

# Jenkins
sh 'mvn clean verify -P ci'

# GitLab CI
script:
  - mvn clean verify -P ci
```

---

### 6. `prod` — Production Release (150 seconds)

**Best for**: Pre-release validation and production deployments

**What it does**:
- Runs all tests (unit + integration + stress)
- Enables JaCoCo with strict coverage thresholds
- Runs OWASP CVE scanning (fails on CVSS >= 7)
- No time constraints (thorough validation)
- Requires NVD_API_KEY for CVE data

**Command**:
```bash
# With NVD API key for CVE scanning
NVD_API_KEY=your_key mvn clean verify -P prod

# Without CVE scanning (if unavailable)
mvn clean verify -P prod
```

**Time**: ~150+ seconds

**When to use**:
- You're preparing a production release
- You need comprehensive CVE scanning
- You need strict coverage thresholds
- You're doing final validation before deployment
- Security is paramount

**When NOT to use**:
- Local development
- Quick feedback loops
- Resource-constrained environments

**Config**:
```xml
<jacoco.skip>false</jacoco.skip>
<failBuildOnCVSS>7</failBuildOnCVSS>
<nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
```

---

### 7. `java25` — Java Version Configuration

**Best for**: Java 25 specific compilation and runtime

**What it does**:
- Sets maven.compiler.release=25
- Enables preview features (--enable-preview)
- Sets JVM options for virtual threads and compact object headers
- Auto-activates by default

**When to use**:
- Always (it's the default)
- Explicitly when working with Java 25 features

**Config**:
```xml
<maven.compiler.release>25</maven.compiler.release>
<argLine>--enable-preview -Djdk.tracePinnedThreads=full</argLine>
```

---

### 8. Default (Sequential, No Profile)

**Best for**: Maximum safety when parallelization is not desired

**What it does**:
- Runs all tests sequentially (one at a time)
- JaCoCo disabled by default
- Full static analysis
- Conservative timeout settings
- Most predictable behavior

**Command**:
```bash
mvn clean verify
```

**Time**: ~150 seconds

**When to use**:
- You want maximum safety
- You're testing in a constrained environment
- You're troubleshooting flaky tests
- You want baseline sequential behavior
- You need to disable parallelization

---

## Profile Details

### Matrix: All Properties

| Property | java25 | quick-test | agent-dx | fast-verify | integration-parallel | ci | prod | default |
|----------|--------|-----------|----------|-------------|---------------------|----|----|---------|
| jacoco.skip | true | true | true | true | false* | false | false | true |
| maven.test.skip | false | false | false | false | false | false | false | true |
| forkCount (unit) | default | 1 | default | 1 | 2C | default | default | default |
| forkCount (int) | default | - | - | - | 2C | default | default | default |
| reuseForks (unit) | default | true | default | true | false | default | default | default |
| reuseForks (int) | default | - | - | - | false | default | default | default |
| parallel (unit) | false | false | false | false | classesAndMethods | false | false | false |
| parallel (int) | false | - | - | - | classesAndMethods | false | false | false |
| excludedGroups | - | integration,docker,... | integration,... | integration,... | stress,breaking-point | - | - | - |

*jacoco.skip is not explicitly set but integration tests skip coverage

### Environment Variables

```bash
# Force offline mode
DX_OFFLINE=1 mvn clean verify

# Enable verbose output
DX_VERBOSE=1 bash scripts/dx.sh

# Capture timing metrics
DX_TIMINGS=1 bash scripts/dx.sh

# Resume from failure
DX_RESUME=1 mvn clean verify
```

---

## Performance Expectations

### Build Times by Profile (on 4-core / 8GB RAM machine)

| Profile | Time | What's Included | Best Case | Worst Case |
|---------|------|-----------------|-----------|-----------|
| quick-test | 10s | Unit tests | 8s | 12s |
| agent-dx | 10s | Unit tests | 8s | 12s |
| fast-verify | 12s | Unit tests + reports | 10s | 15s |
| integration-parallel | 85s | Unit + integration | 75s | 100s |
| ci | 120s | All + coverage + analysis | 110s | 140s |
| prod | 150s | All + CVE scan | 140s | 180s |
| *(default)* | 150s | Sequential execution | 140s | 180s |

### Time by Phase

```
java25 profile: (always active)
├─ Compile: 30s
├─ Unit tests: 10s
├─ Integration tests: 110s (sequential)
│  ├─ YEngine tests: 45s
│  ├─ DataModelling tests: 35s
│  └─ Other: 30s
└─ Total: 150s

integration-parallel profile:
├─ Compile: 30s
├─ Unit tests: 10s (parallel, 2 JVMs)
├─ Integration tests: 45s (parallel, 2 JVMs)
│  ├─ YEngine tests: 25s (2 concurrent)
│  ├─ DataModelling tests: 20s (2 concurrent)
│  └─ Other: 15s (2 concurrent)
└─ Total: 85s (1.77x faster)
```

### Speedup Factors

- **JVM startup overhead**: 5-10% (unavoidable)
- **I/O contention**: 5-15% (disk/network shared)
- **Memory pressure**: 0-5% (GC overhead)
- **Actual parallelism gain**: 30-45%

Formula: `Speedup = Sequential / (Overhead + Min(Parallelize %))`

---

## Common Issues and Solutions

### Issue 1: Profile Not Activating

**Symptom**: You run `mvn verify -P quick-test` but tests still take 150s.

**Root cause**: Profile not properly activated.

**Solution**:
```bash
# Check active profiles
mvn help:active-profiles

# Explicit activation
mvn verify -P quick-test

# Or list all profiles
mvn help:describe -Dcmd=profiles
```

### Issue 2: Tests Pass with One Profile, Fail with Another

**Symptom**: `mvn test` passes, but `mvn test -P quick-test` fails.

**Root cause**: Integration tests are running in quick-test, or parallel execution exposed state issues.

**Solution**:
```bash
# Make sure quick-test excludes integration
grep excludedGroups pom.xml | grep quick-test

# Or be explicit
mvn test -P quick-test -DexcludedGroups=integration
```

### Issue 3: Coverage Reports Missing

**Symptom**: No JaCoCo report after `mvn verify`.

**Root cause**: JaCoCo disabled in default profile.

**Solution**:
```bash
# Use ci profile for coverage
mvn verify -P ci

# Or enable explicitly
mvn verify -Djacoco.skip=false

# View report
open target/site/jacoco/index.html
```

### Issue 4: CVE Scan Failures

**Symptom**: `mvn verify -P prod` fails with CVE warnings.

**Root cause**: High-severity CVEs detected, or NVD API key invalid.

**Solution**:
```bash
# Check your API key
echo $NVD_API_KEY

# Run without CVE scanning (risky)
mvn verify -P prod -Dowasp.skip=true

# Or get updated NVD data
NVD_API_KEY=your_key mvn verify -P prod

# See CVE details
find . -name "dependency-check-report.html"
```

### Issue 5: Out of Memory with Parallelization

**Symptom**: `mvn verify -P integration-parallel` fails with OutOfMemoryError.

**Root cause**: Too many JVM forks, exhausting available RAM.

**Solution**:
```bash
# Reduce fork count
mvn verify -P integration-parallel -Dfailsafe.forkCount=1.5C

# Or increase heap
export MAVEN_OPTS="-Xmx4g"
mvn verify -P integration-parallel

# Check memory usage
free -h  # Linux
vm_stat  # macOS
```

### Issue 6: Build is Slower Than Expected

**Symptom**: `integration-parallel` only gives 1.2x speedup instead of 1.77x.

**Root cause**: Not enough parallelizable tests or I/O bottleneck.

**Solution**:
```bash
# Check test distribution
mvn verify -P integration-parallel -X | grep "fork"

# Increase parallel factor
mvn verify -P integration-parallel \
  -Djunit.jupiter.execution.parallel.config.dynamic.factor=2.5

# Run fewer tests (to identify bottleneck)
mvn verify -P integration-parallel -Dtest=YNetRunner*
```

---

## Decision Tree

Use this flowchart to pick the right profile:

```
Do you need comprehensive testing?
│
├─ NO (just dev feedback)
│  │
│  └─ How fast do you need feedback?
│     │
│     ├─ <15s (ASAP)
│     │  └─ Use: -P quick-test
│     │
│     ├─ <20s (very fast)
│     │  └─ Use: -P agent-dx
│     │
│     └─ <30s (with timing reports)
│        └─ Use: -P fast-verify
│
└─ YES (need all tests)
   │
   └─ What's your environment?
      │
      ├─ Local development
      │  │
      │  └─ Need speed?
      │     │
      │     ├─ YES (1.77x faster)
      │     │  └─ Use: -P integration-parallel
      │     │
      │     └─ NO (maximum safety)
      │        └─ Use: (default, sequential)
      │
      ├─ CI/CD pipeline
      │  │
      │  └─ Need coverage + analysis?
      │     │
      │     ├─ YES
      │     │  └─ Use: -P ci
      │     │
      │     └─ NO
      │        └─ Use: -P integration-parallel
      │
      └─ Production release
         │
         └─ Use: -P prod (CVE scanning + strict coverage)
```

---

## Quick Reference

### One-Liners

```bash
# Unit tests ASAP
mvn test -P quick-test

# Integration tests fast
mvn verify -P integration-parallel

# Full validation with coverage
mvn verify -P ci

# Production release
NVD_API_KEY=xxx mvn verify -P prod

# Agent development
mvn compile test -P agent-dx

# See timing metrics
DX_TIMINGS=1 bash scripts/dx.sh all
```

### Environment Setup

```bash
# ~/.bashrc
export MAVEN_OPTS="-Xmx2g -XX:+UseZGC"
alias mvn-quick="mvn clean test -P quick-test"
alias mvn-test="mvn clean verify -P integration-parallel"
alias mvn-full="mvn clean verify"
```

---

## References

- **Full Developer Guide**: `DEVELOPER-GUIDE-PARALLELIZATION.md`
- **Quick Start**: `QUICK-START-PARALLEL-TESTS.md`
- **POM Configuration**: `pom.xml` (search for "profiles")
- **Build Script**: `scripts/dx.sh`

---

**Version**: 1.0
**Status**: Production Ready
**Last Updated**: February 28, 2026
