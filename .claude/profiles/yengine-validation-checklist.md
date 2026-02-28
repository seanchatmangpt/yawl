# YEngine Parallelization Pre-Validation Checklist

**Status**: Ready for Phase 2 Analysis
**Last Updated**: 2026-02-28
**Objective**: Determine if YEngine can safely support parallel test execution

---

## Executive Summary

This checklist documents the conditions required for safe YEngine parallelization. It serves as a decision matrix that answers: "Is it safe to run integration tests in parallel?"

**Key Finding Areas**:
1. YEngine singleton management
2. Database connection pooling and isolation
3. Test state isolation mechanisms
4. Concurrent case creation and management
5. Specification loading and caching
6. Memory and resource cleanup

---

## Phase 1: Static Code Analysis

### 1.1 Singleton Pattern & Instance Management

| Item | Status | Evidence | Notes |
|------|--------|----------|-------|
| **YEngine is singleton** | ✅ | YEngine._thisInstance static | Single instance per JVM |
| **getInstance() is thread-safe** | ❓ | Need to verify synchronized access | Critical for parallel safety |
| **createClean() resets state** | ✅ | Code at line 216-228 | Allows isolated test instances |
| **resetInstance() blocks active cases** | ✅ | Code at line 237-252 | Prevents cleanup during execution |

**Critical**: Verify that getInstance() doesn't have race conditions during first initialization.

**Decision Gate**: IF getInstance() is properly synchronized THEN proceed to 1.2, ELSE parallelize with separate threads.

---

### 1.2 Database Connection & Persistence

| Item | Status | Evidence | Notes |
|------|--------|----------|-------|
| **H2 in-memory DB** | ✅ | Test uses H2, confirmed in codebase | Per-test isolation required |
| **Connection pooling** | ❓ | YPersistenceManager at line 91 | Need to verify pool isolation |
| **DB schema cleanup** | ❓ | Check @BeforeEach/@AfterEach hooks | May need explicit cleanup |
| **Transaction isolation level** | ❓ | Check persistence config | Default H2 isolation may leak state |

**Critical**: Verify that each test's database schema is isolated or cleaned between test runs.

**Decision Gate**: IF each test has isolated DB schema THEN safe, ELSE require explicit cleanup hooks.

---

### 1.3 Case & Specification Caching

| Item | Status | Evidence | Notes |
|------|--------|----------|-------|
| **Specification cache** | ✅ | YSpecificationTable at line 111 | Shared across engine instance |
| **Case ID uniqueness** | ✅ | YCaseNbrStore at line 96 | Global counter |
| **NetRunner cache** | ✅ | YNetRunnerRepository at line 107 | Maps case IDs to runners |
| **Case cache cleanup** | ❓ | Check if caches clear on case completion | May cause memory leaks |

**Critical**: Case ID counter and caches must be properly isolated between parallel tests.

**Decision Gate**: IF case ID generator is atomic and isolated THEN safe, ELSE serialize case creation.

---

### 1.4 Thread-Local & Tenant Isolation

| Item | Status | Evidence | Notes |
|------|--------|----------|-------|
| **ThreadLocal for tenant** | ✅ | Line 137: _currentTenant ThreadLocal | Supports multi-tenant |
| **ThreadLocal cleanup** | ❓ | Check AfterEach cleanup | Thread pool reuse may leak values |
| **Scoped values** | ❓ | Check if Java 21+ scoped values used | Better than ThreadLocal |

**Critical**: ThreadLocal values must be cleaned up after each test to prevent pollution.

**Decision Gate**: IF tests explicitly clear ThreadLocals THEN safe, ELSE use thread-per-test pattern.

---

### 1.5 Lock & Synchronization Analysis

| Item | Status | Evidence | Notes |
|------|--------|----------|-------|
| **ReentrantLock usage** | ✅ | Lines 123-124: _persistLock, _pmgrAccessLock | Good (not synchronized) |
| **No synchronized blocks** | ✅ | Code avoids synchronized keyword | Allows virtual threads |
| **Lock contention points** | ⚠️ | Need to identify all lock acquisitions | May serialize parallel tests |
| **Lock timeouts** | ❓ | Check if locks have timeouts | Prevents deadlocks |

**Critical**: Excessive lock contention can make parallelization ineffective.

**Decision Gate**: IF lock contention is <10% of test time THEN parallelizable, ELSE keep sequential.

---

## Phase 2: Dynamic Testing Results

Run YEngineParallelizationTest and document outcomes:

### Test Results Summary

| Test | Pass | Issue | Severity |
|------|------|-------|----------|
| T1: Static field pollution | ⏳ | TBD | HIGH |
| T2: Singleton isolation | ⏳ | TBD | HIGH |
| T3: Case state contamination | ⏳ | TBD | CRITICAL |
| T4: Spec loading races | ⏳ | TBD | HIGH |
| T5: Corruption detection | ⏳ | TBD | MEDIUM |
| T6: ThreadLocal pollution | ⏳ | TBD | HIGH |
| T7: Case completion leaks | ⏳ | TBD | MEDIUM |
| T8: Memory leaks | ⏳ | TBD | MEDIUM |
| T9: DB connection isolation | ⏳ | TBD | HIGH |

### Corruption Detection Results

| Metric | Threshold | Actual | Status |
|--------|-----------|--------|--------|
| Corruptions detected | ≤ 0 | ? | ? |
| Case ID collisions | = 0 | ? | ? |
| Spec ID duplicates | = 0 | ? | ? |
| ThreadLocal leaks | = 0 | ? | ? |
| Memory growth (MB) | < 100 | ? | ? |

---

## Phase 3: Critical Dependencies

### 3.1 YEngine State Isolation Requirements

For parallel safety, ALL of the following must be true:

1. **Singleton Independence**
   - [ ] Multiple engine instances can coexist without conflicts
   - [ ] getInstance() returns singleton, createClean() allows new instances
   - [ ] resetInstance() safely clears state

2. **Database Isolation**
   - [ ] Each test uses separate H2 in-memory DB schema
   - [ ] OR each test's transactions are isolated via ISOLATION_LEVEL_SERIALIZABLE
   - [ ] No persistent DB files shared between tests

3. **Case Management Isolation**
   - [ ] Case ID counter is thread-safe (AtomicLong or similar)
   - [ ] Case IDs don't collide across concurrent tests
   - [ ] NetRunner cache keys are unique per case

4. **Specification Management**
   - [ ] Specification cache doesn't cross-contaminate between tests
   - [ ] Spec IDs are globally unique
   - [ ] Spec modifications don't affect running cases

5. **ThreadLocal Cleanup**
   - [ ] Tests explicitly clear ThreadLocal values in @AfterEach
   - [ ] OR use VirtualThread-compatible scoped values
   - [ ] OR use ThreadPool size of 1 per test

6. **Lock Contention**
   - [ ] Lock hold times are <10ms
   - [ ] No deadlock-prone lock ordering
   - [ ] Timeouts prevent indefinite waits

7. **Memory Management**
   - [ ] No memory leaks from case/spec caching
   - [ ] Proper cleanup on case completion
   - [ ] GC doesn't pause for >1 second during test run

---

## Phase 4: Decision Matrix

### Parallelization Decision Logic

```
IF (Phase_2_Corruption_Count == 0) AND
   (T1_Pass AND T2_Pass AND T3_Pass AND T4_Pass) AND
   (All_Critical_Dependencies_Met)
THEN
   Parallel_Safe = true
   Recommend: mvn -T 1.5C clean test
ELSE
   Parallel_Safe = false
   Recommend: mvn clean test (sequential)
   Action: Fix highest-severity failures and rerun
END IF
```

### Failure Scoring

| Failure Type | Score | Blocking | Notes |
|--------------|-------|----------|-------|
| Case ID collision | 100 | YES | Indicates broken uniqueness |
| Singleton violation | 90 | YES | Multiple instances where one expected |
| ThreadLocal leak | 70 | YES | Pollutes subsequent tests |
| Spec ID duplicate | 80 | YES | Cache corruption |
| DB connection timeout | 60 | NO | Transient, may resolve with tuning |
| Memory growth >100MB | 50 | NO | May indicate leak, recheck GC |
| Lock timeout | 40 | NO | Contention, may need optimization |

**GO Decision**: Score < 50 and no blocking issues
**NO-GO Decision**: Any blocking issue or score > 100

---

## Phase 5: Rollback Strategy

If parallelization is enabled and failures occur in production:

### Immediate Actions (< 5 min)

1. **Detect Failure**
   - CI build shows flaky test (fails intermittently on parallel, passes on sequential)
   - Example: Test A passes alone, fails when run with Test B

2. **Capture Evidence**
   - Save full build log with timestamps
   - Note which tests failed together
   - Record heap dumps if OOM occurred

3. **Rollback**
   ```bash
   # Immediately fall back to sequential
   mvn clean test  # instead of mvn -T 1.5C clean test
   ```

### Root Cause Analysis (< 1 hour)

| Symptom | Probable Cause | Investigation |
|---------|---|---|
| Random test failures | Race condition | Run with `-Dtest=TestA,TestB` to reproduce |
| Always test Y fails when run after X | State leak | Check X's @AfterEach teardown |
| Heap OOM during parallel run | Memory leak in caching | Profile with jfr |
| Deadlock (test hangs) | Lock ordering issue | Get thread dump with jstack |
| DB locks/timeouts | Connection pool exhaustion | Check active connections |

### Fixing & Re-enabling

1. Fix the root cause (identified in analysis)
2. Add regression test to YEngineParallelizationTest
3. Re-run validation suite to verify fix
4. Update PARALLELIZATION_SAFE flag in ggen.toml
5. Re-enable in CI/CD pipeline

### Preventing Recurrence

- Add flaky test detection: Track test that pass/fail inconsistently
- Automated parallel run: Run `mvn -T 1.5C test` nightly
- Alert on regressions: CI marks any new failures during parallel runs

---

## Phase 6: Monitoring & Observability

### Metrics to Track

| Metric | Source | Target | Alert Threshold |
|--------|--------|--------|-----------------|
| Test suite time | Maven output | Baseline | >30% deviation |
| Memory peak | jcmd GC stats | < 2GB | > 3GB |
| DB connection count | Persistence logs | <20 | > 50 |
| Lock wait time | JFR profiling | <5ms p95 | > 50ms |
| Case collision rate | Test output | 0/1000 | > 1 |

### Automated Checks

```bash
# Run before enabling parallel:
bash scripts/validate-yengine-parallelization.sh

# Periodic validation (nightly):
0 2 * * * bash scripts/validate-yengine-parallelization.sh --monitor
```

---

## Phase 7: Stakeholder Sign-Off

### Required Approvals

- [ ] **Validation Engineer** (this document): Confirms test harness ready
- [ ] **YEngine Investigator** (Phase 2): Confirms no architectural blockers
- [ ] **Build Optimizer** (Phase 3): Confirms Maven profile ready
- [ ] **QA Lead**: Confirms regression test coverage
- [ ] **DevOps**: Confirms monitoring & rollback procedures

### Sign-Off Criteria

| Role | Criteria | Status |
|------|----------|--------|
| Validation | YEngineParallelizationTest runs, 0 corruptions | ⏳ |
| Investigator | No blockers in YEngine architecture | ⏳ |
| Optimizer | Maven -T flag configured correctly | ⏳ |
| QA | Regression tests added to suite | ⏳ |
| DevOps | Rollback script tested and documented | ⏳ |

---

## Implementation Checklist

### Pre-Parallel-Deployment

- [ ] YEngineParallelizationTest passes with 0 corruptions
- [ ] All 9 validation tests pass (T1-T9)
- [ ] Lock contention profiled (<10% hold time)
- [ ] Memory baseline established (<500MB per test)
- [ ] DB isolation verified (H2 in-memory, separate schemas)
- [ ] ThreadLocal cleanup audit completed
- [ ] Regression test suite created
- [ ] CI/CD rollback procedure documented
- [ ] Monitoring alerts configured
- [ ] Stakeholder sign-off obtained

### First Parallel Run (Canary)

- [ ] Run subset of tests with -T 1.5C
- [ ] Monitor for flaky failures
- [ ] Capture full logs and metrics
- [ ] Run same subset sequentially and compare results
- [ ] If clean, enable full parallel suite

### Continuous Monitoring

- [ ] Weekly parallel run comparison (time vs sequential)
- [ ] Monthly trend analysis (memory, locks)
- [ ] Quarterly review of failure patterns
- [ ] Alert on deviation from baseline metrics

---

## References

- YEngine source: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
- Test harness: `/home/user/yawl/src/test/java/org/yawlfoundation/yawl/validation/YEngineParallelizationTest.java`
- Validation script: `/home/user/yawl/scripts/validate-yengine-parallelization.sh`
- Build profile: `/home/user/yawl/pom.xml` (search for surefire parallel config)
- Related: CLAUDE.md § "Λ BUILD" (Maven parallelization notes)

---

**Next Step**: Run YEngineParallelizationTest and populate Phase 2 results above.
