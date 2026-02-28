# YEngine Parallelization Decision Framework

**Status**: Phase 1 Complete (Validation Harness Ready)
**Effective Date**: 2026-02-28
**Owner**: Validation Engineer
**Phase 2 Owner**: YEngine Investigator

---

## Overview

This document establishes the decision framework for determining whether YAWL integration tests can safely run in parallel (using Maven's `-T` flag).

**Current Status**: ✅ Phase 1 Complete
- Validation harness designed and implemented
- Test isolation detection framework ready
- Automated validation script created
- Decision matrix documented

**Next Phase**: YEngine Investigator conducts deep analysis
- Static code review of YEngine singleton management
- Lock contention analysis
- Database isolation verification
- Thread safety audit

---

## The Problem We're Solving

Current build time: ~2-3 minutes (sequential)
Goal: <1 minute (parallel) - 3× speedup

**Blocker**: Unknown if parallel execution is safe
- YEngine is a singleton (global state)
- If tests pollute singleton state, parallel fails
- Need automated corruption detection

**Solution**: Validation harness that can:
1. Run concurrent tests
2. Detect state corruption
3. Make GO/NO-GO decision automatically

---

## Validation Harness (Phase 1)

### Components Delivered

#### 1. Test Suite: YEngineParallelizationTest.java
- **Location**: `/home/user/yawl/src/test/java/org/yawlfoundation/yawl/validation/YEngineParallelizationTest.java`
- **Purpose**: Comprehensive state isolation testing
- **Tests**: 9 scenarios (T1-T9)

**Test Coverage**:

| Test | Scenario | Detects |
|------|----------|---------|
| T1 | Concurrent isolated engines | Static field pollution |
| T2 | Singleton thread safety | Multiple engine instances |
| T3 | Concurrent case creation | Case ID collisions, spec pollution |
| T4 | Parallel spec loading | Duplicate spec IDs |
| T5 | Intentional corruption | Test harness effectiveness |
| T6 | ThreadLocal usage | Tenant context leaks |
| T7 | Case completion | State leakage after completion |
| T8 | Memory accumulation | Leak detection (50 cases) |
| T9 | DB connection usage | Connection pool isolation |

**Key Features**:
- Real YEngine instances (no mocks)
- Real H2 in-memory database
- Concurrent test execution (2-4 threads per test)
- Corruption counting and reporting
- Execution time: <30 seconds total

#### 2. Decision Checklist: yengine-validation-checklist.md
- **Location**: `/home/user/yawl/.claude/profiles/yengine-validation-checklist.md`
- **Purpose**: Document decision criteria and track analysis
- **Sections**:
  - Static code analysis framework
  - Dynamic test results logging
  - Critical dependencies matrix
  - Failure scoring system
  - Rollback procedures

#### 3. Automated Validation Script: validate-yengine-parallelization.sh
- **Location**: `/home/user/yawl/scripts/validate-yengine-parallelization.sh`
- **Purpose**: One-command validation check
- **Capabilities**:
  - Compile test harness
  - Run validation tests
  - Analyze corruption metrics
  - Make GO/NO-GO decision
  - Generate detailed report

**Exit Codes**:
- 0 = PASS (safe to parallelize)
- 1 = FAIL (keep sequential)
- 2 = ERROR (validation harness issue)

---

## Decision Matrix

### GO Conditions (All required)

| Condition | Status | Owner |
|-----------|--------|-------|
| YEngineParallelizationTest: 0 corruptions | ⏳ | Phase 2 |
| All T1-T9 tests pass | ⏳ | Phase 2 |
| Singleton management thread-safe | ⏳ | Phase 2 |
| Case ID generator is atomic | ⏳ | Phase 2 |
| Database connections properly isolated | ⏳ | Phase 2 |
| No ThreadLocal pollution | ⏳ | Phase 2 |
| Lock contention <10% | ⏳ | Phase 2 |
| Memory growth <100MB per test | ⏳ | Phase 2 |

### NO-GO Conditions (Any blocking)

| Blocker | Impact | Recovery |
|---------|--------|----------|
| Case ID collision detected | Parallel broken | Fix ID generator atomicity |
| Singleton violation (multiple instances) | Parallel broken | Add synchronization to getInstance() |
| State pollution across tests | Tests fail intermittently | Implement test isolation hooks |
| ThreadLocal not cleaned | Later tests affected | Add @AfterEach cleanup |
| Database schema not isolated | Data corruption | Use separate schemas per test |
| Deadlock or lock timeout | Tests hang | Implement lock timeouts |

---

## Phase 2: YEngine Deep Dive (Investigator's Work)

**Task**: Analyze YEngine code to identify parallelization blockers

**Entry Point**:
```
bash scripts/validate-yengine-parallelization.sh  # Run tests first
# If FAIL, proceed with Phase 2
```

### Key Areas to Review

#### A. Singleton Pattern (Lines 78-229)

```java
// QUESTION: Is getInstance() thread-safe?
public static YEngine getInstance(boolean persisting, ...)
        throws YPersistenceException {
    if (_thisInstance == null) {
        _thisInstance = new YEngine();
        initialise(...);
    }
    return _thisInstance;
}
```

**Concern**: Classic double-checked locking issue
- First check: no synchronization
- Race condition if two threads call simultaneously
- Test T2 should detect this

**Investigation**:
- [ ] Is there synchronization around `_thisInstance` assignment?
- [ ] Does `initialise()` protect against concurrent calls?
- [ ] Do tests hang during parallel execution?

#### B. Case ID Generation (Lines 96, 154-155)

```java
private static YCaseNbrStore _caseNbrStore;

// In constructor:
_caseNbrStore = YCaseNbrStore.getInstance();
```

**Concern**: Is YCaseNbrStore thread-safe?
- Test T4/T5 checks for ID collisions
- If counter not atomic, parallel tests will fail

**Investigation**:
- [ ] Is YCaseNbrStore.getInstance() a singleton?
- [ ] Is the counter (YCaseNbrStore._counter) AtomicLong?
- [ ] Are ID generation methods synchronized?

#### C. Specification Caching (Line 111)

```java
private YSpecificationTable _specifications;

// In constructor:
_specifications = new YSpecificationTable();
```

**Concern**: YSpecificationTable may not be thread-safe
- Test T3/T4 check for spec ID collisions
- If cache not thread-safe, parallel tests interfere

**Investigation**:
- [ ] Is YSpecificationTable thread-safe?
- [ ] Does it use ConcurrentHashMap?
- [ ] Does loadSpecification() have race conditions?

#### D. Lock Analysis (Lines 123-124)

```java
private final ReentrantLock _persistLock = new ReentrantLock();
private final ReentrantLock _pmgrAccessLock = new ReentrantLock();
```

**Concern**: Lock contention during parallel test execution
- Heavy locks can make parallelization ineffective
- Test T1 may show throughput degradation

**Investigation**:
- [ ] Where are locks acquired?
- [ ] How long are locks held?
- [ ] Are there nested lock acquisitions (deadlock risk)?
- [ ] Profile lock wait times

#### E. ThreadLocal Usage (Line 137)

```java
private static final ThreadLocal<TenantContext> _currentTenant = new ThreadLocal<>();
```

**Concern**: ThreadLocal values leak across thread pool reuse
- Test T6 checks ThreadLocal isolation
- If tests reuse threads, ThreadLocal values persist

**Investigation**:
- [ ] Are ThreadLocal values cleared in @AfterEach?
- [ ] Do tests use ThreadPool (reuse) or new threads?
- [ ] Should we use Java 21+ scoped values instead?

---

## Phase 3: Build Profile Readiness

**Assumption**: Once Phase 2 approves parallelization, Phase 3 creates Maven profile

**Target Configuration**:
```bash
mvn -T 1.5C clean test  # 1.5C = 1.5 × available cores
```

**Current Config** (pom.xml):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <parallel>none</parallel>  <!-- CHANGE TO 'suites' or 'methods' -->
        <threadCount>4</threadCount>  <!-- TUNE BASED ON PHASE 2 -->
    </configuration>
</plugin>
```

**Decision Gate**: Only enable if Phase 2 approves all 8 GO conditions.

---

## Rollback Strategy

If parallelization is enabled and failures occur:

### Detection (Automated)

```bash
# CI script detects flaky tests
if [ tests_flaky_on_parallel && tests_pass_sequentially ]; then
    echo "ROLLBACK: Flaky test detected in parallel mode"
    # Disable parallel
    sed -i 's/<parallel>suites/<parallel>none/' pom.xml
    # Alert team
    send_alert "Parallel testing disabled due to flakiness"
fi
```

### Root Cause Analysis

**Flaky Test A fails with Test B** → State leak in A's teardown
- Check A's @AfterEach
- Add isolation hook

**Random failures in parallel** → Race condition
- Profile with `-XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation`
- Add synchronization

**Memory OOM in parallel** → Leak in caching
- Profile with jfr
- Fix cache cleanup

### Recovery

1. Identify root cause (see analysis above)
2. Add regression test to YEngineParallelizationTest
3. Fix the code
4. Re-run validation script
5. Re-enable parallel mode

### Prevention

- **Nightly parallel run**: `0 2 * * * mvn -T 1.5C test`
- **Flaky test detection**: Track test that pass/fail inconsistently
- **Alert on new failures**: CI marks any regressions

---

## Success Criteria (Phase 1 Complete)

- [x] Validation harness designed and coded
- [x] 9 state isolation tests created
- [x] Corruption detection framework implemented
- [x] Automated validation script created
- [x] Decision matrix documented
- [x] Rollback strategy defined
- [ ] Phase 2: YEngine Investigator analysis complete
- [ ] Phase 3: Maven profile enabled
- [ ] Phase 4: Canary parallel run successful
- [ ] Phase 5: Full parallel CI/CD enabled

---

## Risk Assessment

### High Risk Areas

| Area | Risk | Mitigation |
|------|------|-----------|
| Singleton initialization race | Test crash | Add synchronization or use eager initialization |
| Case ID collision | Data corruption | Use AtomicLong counter |
| Spec cache pollution | Tests fail randomly | Use ConcurrentHashMap, verify isolation |
| Lock deadlock | Tests hang indefinitely | Add lock timeouts, profile |
| ThreadLocal leak | Tests fail in sequence | Clear in @AfterEach, use scoped values |

### Confidence Levels

| Phase | Confidence | Notes |
|-------|------------|-------|
| Phase 1 (Validation) | ✅ High | Test harness ready, automated decision |
| Phase 2 (Analysis) | ⏳ TBD | Depends on code review findings |
| Phase 3 (Build) | ⏳ TBD | Only if Phase 2 approves |
| Phase 4 (Canary) | ⏳ TBD | Only if Phase 3 enables |
| Phase 5 (Production) | ⏳ TBD | Only if Phase 4 succeeds |

---

## Timeline & Ownership

### Phase 1: Validation Harness (COMPLETE ✅)
- **Owner**: Validation Engineer
- **Deadline**: 2026-02-28
- **Deliverables**: ✅ All delivered
- **Status**: Ready for Phase 2

### Phase 2: YEngine Analysis (IN PROGRESS)
- **Owner**: YEngine Investigator
- **Deadline**: 2026-03-07 (1 week)
- **Input**: Run `bash scripts/validate-yengine-parallelization.sh`
- **Output**:
  - YEngineParallelizationTest results
  - Code review findings
  - GO/NO-GO recommendation
- **Key Questions**:
  - getInstance() thread-safe?
  - Case ID generator atomic?
  - DB isolated per test?
  - Lock contention acceptable?
  - ThreadLocal properly cleaned?

### Phase 3: Build Configuration (READY TO START)
- **Owner**: Build Optimizer
- **Blocker**: Phase 2 approval (GO condition)
- **Work**: Update pom.xml surefire config
- **Timeline**: <1 day once approved

### Phase 4: Canary Parallel Run (PLANNED)
- **Owner**: QA Lead
- **Blocker**: Phase 3 completion
- **Test**: Run subset with `-T 1.5C`
- **Success Criteria**: 0 flaky failures

### Phase 5: Production (PLANNED)
- **Owner**: DevOps
- **Blocker**: Phase 4 success
- **Deployment**: Enable in CI/CD
- **Monitoring**: Weekly regression checks

---

## How to Use This Framework

### For Validation Engineer (NOW)
1. ✅ Review this document
2. ✅ Review test harness code
3. ✅ Message team with readiness status

### For YEngine Investigator (PHASE 2)
1. Run: `bash scripts/validate-yengine-parallelization.sh`
2. Review: `.claude/profiles/validation-reports/parallelization-report-*.txt`
3. If FAIL, analyze YEngineParallelizationTest logs
4. Deep dive Phase 2 "Key Areas to Review" section
5. Document findings in yengine-validation-checklist.md
6. Make GO/NO-GO recommendation

### For Build Optimizer (PHASE 3)
1. Wait for Phase 2 GO decision
2. Update pom.xml surefire config
3. Enable `-T 1.5C` in local build
4. Test: `mvn -T 1.5C clean test`
5. Document results

### For CI/CD / DevOps (PHASE 5)
1. Once Phase 4 succeeds
2. Update CI pipeline to use `-T 1.5C`
3. Add nightly flaky test detection
4. Monitor build time trends
5. Alert on regressions

---

## Appendix: Key Files

### Validation Code
- **Test Suite**: `/home/user/yawl/src/test/java/org/yawlfoundation/yawl/validation/YEngineParallelizationTest.java`
  - 9 isolation tests (T1-T9)
  - Real YEngine, real H2 database
  - Corruption counting and reporting

### Decision Documents
- **Checklist**: `/home/user/yawl/.claude/profiles/yengine-validation-checklist.md`
  - Go/No-Go conditions
  - Failure scoring
  - Rollback procedures

- **This Document**: `/home/user/yawl/.claude/profiles/PARALLELIZATION-DECISION.md`
  - Decision framework overview
  - Phase 2 investigator guidance
  - Risk assessment

### Automation
- **Validation Script**: `/home/user/yawl/scripts/validate-yengine-parallelization.sh`
  - One-command validation check
  - Runs test harness
  - Generates decision report
  - Exit code: 0 (GO) or 1 (NO-GO)

### Build Config (TO BE UPDATED)
- **pom.xml**: `/home/user/yawl/pom.xml`
  - Surefire plugin configuration
  - Parallel execution settings
  - Thread pool tuning

---

**Next Step**: YEngine Investigator runs Phase 2 analysis.
```bash
bash /home/user/yawl/scripts/validate-yengine-parallelization.sh
```
Then reviews results and provides GO/NO-GO recommendation.
