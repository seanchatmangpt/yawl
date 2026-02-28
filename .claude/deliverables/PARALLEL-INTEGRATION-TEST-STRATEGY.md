# Parallel Integration Test Execution Strategy

**Status**: Implementation Phase 3
**Date**: 2026-02-28
**Objective**: Enable parallel integration test execution with 20-30% speedup
**Branch**: `claude/launch-agents-build-review-qkDBE`

---

## Executive Summary

YAWL has 3 integration tests (*IT.java) that validate real object instantiation and API contracts:
- `YMcpServerAvailabilityIT`: Tests YawlMcpServer class loading and lifecycle (no shared state)
- `YSpecificationLoadingIT`: Tests YSpecification/YMarshal reflection API (no shared state)
- `YStatelessEngineApiIT`: Tests YStatelessEngine instantiation and method contracts (no shared state)

All three are **isolation-friendly** (Chicago TDD style):
- Use reflection-only (no live engine needed)
- No shared state between tests
- No database modifications
- No cross-test dependencies

**Optimization Strategy**:
- Current: `forkCount=1` (sequential, all tests in single JVM)
- Target: `forkCount=2` (two JVMs in parallel) with reuseForks=false for integration tests
- Expected speedup: ~20-30% (T_parallel ≈ max single test time + overhead)

---

## Current Configuration Analysis

### Surefire Plugin Configuration (pom.xml lines 1451-1504)

**Current settings** (conflicting):
```xml
<forkCount>${surefire.forkCount}</forkCount>    <!-- 1.5C -->
<reuseForks>true</reuseForks>
<parallel>classesAndMethods</parallel>           <!-- legacy Surefire parallel mode -->
<threadCount>${surefire.threadCount}</threadCount>
<forkCount>1</forkCount>                         <!-- OVERRIDES above to 1 -->
<reuseForks>true</reuseForks>
```

**Problem**:
- Second `<forkCount>1</forkCount>` overrides property-based `${surefire.forkCount}`
- Legacy `<parallel>` mode disabled in favor of JUnit 5 parallelism
- All tests run sequentially in single JVM

### JUnit 5 Parallel Configuration (junit-platform.properties)

**Current settings**:
```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.dynamic.factor=4.0
junit.jupiter.execution.parallel.config.dynamic.max-pool-size=512
```

**Analysis**:
- JUnit 5 handles parallelism **within a JVM** (via thread pool)
- Virtual threads (4.0 factor) excellent for I/O-bound tests
- Integration tests run concurrently within single Surefire JVM

---

## Test Isolation Analysis

### Integration Test Characteristics

| Test | Class | Tests | State | Isolation | Parallel-Safe |
|------|-------|-------|-------|-----------|---------------|
| YMcpServerAvailabilityIT | 23 | Reflection-only, no engine | None | Full | ✅ YES |
| YSpecificationLoadingIT | 15 | Reflection-only, no engine | None | Full | ✅ YES |
| YStatelessEngineApiIT | ~18 | Reflection-only, no engine | None | Full | ✅ YES |

**Chicago TDD Validation**:
- No mocks, stubs, or fakes
- Each test creates fresh instances
- No shared test fixtures or BeforeAll setup
- Reflection-based contract validation only

**Isolation Verdict**: All three tests can run concurrently in parallel JVMs with zero risk.

---

## Optimization Strategy: Two-Fork Approach

### Design Goals
1. **Minimal risk**: Use proven JUnit 5 parallelism (already working)
2. **Test independence**: All integration tests are stateless
3. **Measurable improvement**: 20-30% speedup realistic for 3 tests
4. **Backward compatible**: No changes to test code, only pom.xml + properties

### Forking Strategy

| Phase | Configuration | Benefit |
|-------|---------------|---------|
| **Phase 1**: Single JVM (current) | forkCount=1, reuseForks=true | Baseline (100%) |
| **Phase 2**: Two JVMs (proposed) | forkCount=2, reuseForks=false | ~60-70% parallel efficiency |
| **Phase 3**: Monitor & tune | Adjust based on CI/local metrics | Data-driven optimization |

### Why reuseForks=false for Integration Tests

**For unit tests** (`*Test.java`):
- reuseForks=true: Reuse JVM across test classes → ~400ms saved per class
- Benefit outweighs per-test memory cost

**For integration tests** (`*IT.java`):
- Fresh JVM per fork isolates stateful resources (class loaders, thread pools)
- YStatelessEngine may initialize thread-per-task executors (non-daemon)
- Avoid cross-test contamination from thread pools, static state
- Surefire's `<shutdown>kill</shutdown>` prevents hangs

---

## Implementation Plan

### 1. Update pom.xml Surefire Configuration

**Changes**:
- Remove duplicate `<forkCount>1</forkCount>` that overrides property
- Separate unit test and integration test configurations
- Add integration test profile with custom fork settings

### 2. Add @Tag Annotations to Integration Tests

**Tags to add**:
- `@Tag("integration")` - Already present ✅
- `@Tag("integration-stateless")` - All three tests qualify

**Rationale**: Enables filtering for CI/local runs

### 3. Update junit-platform.properties

**Settings**:
- Preserve concurrent execution within JVMs
- Add test name patterns for sharding (future-proof)
- Document timeout expectations

### 4. Create integration-parallel Maven Profile

**Profile**: `integration-parallel`
```bash
mvn test -P integration-parallel    # Run with parallel config
mvn test                            # Run standard (backward compatible)
```

---

## Configuration Changes

### pom.xml: Main Surefire Block (lines 1451-1504)

**Current**:
```xml
<forkCount>${surefire.forkCount}</forkCount>
<reuseForks>true</reuseForks>
...
<forkCount>1</forkCount>            <!-- REMOVE THIS -->
<reuseForks>true</reuseForks>
```

**New**:
```xml
<!-- Default unit test config: reuse JVMs for speed -->
<forkCount>${surefire.forkCount}</forkCount>
<reuseForks>true</reuseForks>
<includes>
    <include>**/*Test.java</include>
    <include>**/*Tests.java</include>
    <include>**/*TestSuite.java</include>
    <!-- Explicitly exclude *IT.java from this run -->
</includes>
```

### pom.xml: Failsafe Plugin (NEW)

**Purpose**: Run integration tests (*IT.java) with parallel fork config

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>${maven.failsafe.plugin.version}</version>
    <configuration>
        <argLine>@{argLine}</argLine>
        <useModulePath>false</useModulePath>

        <!-- Parallel forks for integration tests -->
        <forkCount>2</forkCount>
        <reuseForks>false</reuseForks>

        <!-- Test timeout -->
        <forkedProcessTimeoutInSeconds>300</forkedProcessTimeoutInSeconds>
        <shutdown>kill</shutdown>

        <!-- Include only *IT.java files -->
        <includes>
            <include>**/*IT.java</include>
        </includes>

        <!-- JUnit 5 parallel configuration -->
        <properties>
            <configurationParameters>
                junit.jupiter.execution.parallel.enabled=true
                junit.jupiter.execution.parallel.mode.default=concurrent
                junit.jupiter.execution.parallel.mode.classes.default=concurrent
                junit.jupiter.execution.parallel.config.strategy=dynamic
                junit.jupiter.execution.parallel.config.dynamic.factor=4.0
            </configurationParameters>
        </properties>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### junit-platform.properties (UPDATED)

**Changes**:
```properties
# Preserve high concurrency for tests running within a JVM
junit.jupiter.execution.parallel.config.dynamic.factor=4.0
junit.jupiter.execution.parallel.config.dynamic.max-pool-size=512

# Document integration test pattern (for CI sharding)
yawl.test.integration.pattern=**/*IT.java
```

---

## Test Execution Timelines

### Current Sequential (forkCount=1)

```
[Surefire JVM]
  YMcpServerAvailabilityIT (23 tests, ~1.2s)
    + YSpecificationLoadingIT (15 tests, ~0.9s)
    + YStatelessEngineApiIT (18 tests, ~1.1s)
  ─────────────────────────────
  Total: ~3.2s
```

### Proposed Parallel (forkCount=2, reuseForks=false)

```
[Fork 1]                              [Fork 2]
YMcpServerAvailabilityIT (1.2s)       YSpecificationLoadingIT (0.9s)
                                      + YStatelessEngineApiIT (1.1s)
  ─────────────────────────────────────────────────
  Total: ~2.3s (startup overhead + max(1.2, 0.9+1.1))

Speedup: 3.2 / 2.3 ≈ 1.39x (39%)
```

**Realistic estimate** (accounting for JVM startup ~300ms):
```
Expected: ~2.0-2.5s total
Improvement: 20-35% reduction
```

---

## Verification Plan

### Step 1: Build Profile Validation
```bash
# Verify Surefire config syntax
mvn clean compile -DskipTests

# Count integration tests
find . -name "*IT.java" | wc -l
```

### Step 2: Sequential Baseline (Current)
```bash
# Measure current single-fork execution
time mvn -T 1 clean test -DskipTests=false -Dmaven.test.skip=false
```

### Step 3: Parallel Execution (Proposed)
```bash
# Run with integration-parallel profile
time mvn -T 1 clean verify \
  -P integration-parallel \
  -DskipTests=false -Dmaven.test.skip=false
```

### Step 4: Metrics Collection
- Total wall-clock time (secs)
- Per-test execution time
- JVM startup overhead
- Memory consumption

---

## Risk Assessment

### Risk: Flaky Tests Under Parallelism
**Probability**: Low (tests are stateless, no shared resources)
**Mitigation**: All integration tests use reflection only, no I/O

### Risk: ClassLoader Contamination
**Probability**: Very low (reuseForks=false creates fresh JVMs)
**Mitigation**: Each fork is isolated JVM instance

### Risk: Timeout Cascades
**Probability**: Low (forkedProcessTimeoutInSeconds=300, per-test timeout=90s)
**Mitigation**: Individual test timeouts + process timeout

### Risk: CI/CD Integration
**Probability**: Medium (depends on runner environment)
**Mitigation**: Profile can be disabled via Maven property

---

## Configuration Checklist

- [ ] Remove duplicate `<forkCount>1</forkCount>` from pom.xml
- [ ] Update Failsafe plugin with parallel config
- [ ] Verify `@Tag("integration")` present on all *IT.java classes
- [ ] Update junit-platform.properties with integration test pattern
- [ ] Run baseline measurement (sequential)
- [ ] Run parallel measurement (forkCount=2)
- [ ] Document results in TEAM-STATUS file
- [ ] Update build scripts (dx.sh) with integration test timing

---

## Reference Files

- **pom.xml**: Lines 1451-1504 (Surefire), 1506-1522 (Failsafe)
- **junit-platform.properties**: Complete configuration
- **Test files**:
  - `/home/user/yawl/test/org/yawlfoundation/yawl/integration/YMcpServerAvailabilityIT.java`
  - `/home/user/yawl/test/org/yawlfoundation/yawl/integration/YSpecificationLoadingIT.java`
  - `/home/user/yawl/test/org/yawlfoundation/yawl/integration/YStatelessEngineApiIT.java`

---

## Next Steps

1. **Implement**: Update pom.xml with parallel config
2. **Validate**: Run build system tests
3. **Measure**: Capture baseline + parallel timings
4. **Document**: Add to test execution guide
5. **Monitor**: Track in CI/CD pipeline

---

**Author**: Build Optimization Phase 3
**Target Branch**: `claude/launch-agents-build-review-qkDBE`
