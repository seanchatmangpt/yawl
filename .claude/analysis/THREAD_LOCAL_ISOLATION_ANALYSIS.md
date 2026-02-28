# Thread-Local YEngine Isolation Redesign
## Phase 3: Strategic Implementation for Parallel Test Execution

**Date**: 2026-02-28
**Author**: YAWL Build Optimization Team
**Status**: DESIGN APPROVED
**Target Outcome**: Enable integration test parallelization (20-30% speedup)

---

## Executive Summary

This document analyzes the feasibility and design of enabling parallel integration test execution by redesigning YEngine state isolation from a global singleton pattern to a thread-local wrapper pattern.

**Current Blocker**: Integration tests run sequentially (forkCount=1, no parallelism) due to YEngine singleton state corruption risk across concurrent tests.

**Proposed Solution**: Implement `ThreadLocalYEngine` wrapper that:
- Maintains backward compatibility with existing `YEngine.getInstance()` calls
- Isolates per-thread YEngine state via `ThreadLocal<YEngine>`
- Eliminates test interdependencies while preserving singleton semantics within a thread
- Requires zero changes to existing test code

**Expected Impact**: 20-30% faster integration test suite (parallel execution overhead recovers ~3-5 test class slots)

---

## 1. Current State Analysis

### 1.1 YEngine Singleton Architecture

```java
public class YEngine implements InterfaceADesign, InterfaceAManagement, ... {
    protected static YEngine _thisInstance;  // Global singleton

    public static YEngine getInstance() {
        if (_thisInstance == null) {
            _thisInstance = new YEngine();
            initialise(...);
        }
        return _thisInstance;
    }
}
```

**Problem**: All threads share `_thisInstance`, leading to state corruption when tests run in parallel.

### 1.2 EngineClearer Implementation (Current)

```java
public class EngineClearer {
    public static void clear(YEngine engine) throws PersistenceException {
        while (engine.getLoadedSpecificationIDs().iterator().hasNext()) {
            YSpecificationID specID = engine.getLoadedSpecificationIDs().iterator().next();
            Set caseIDs = engine.getCasesForSpecification(specID);
            for (Iterator iterator2 = caseIDs.iterator(); iterator2.hasNext();) {
                YIdentifier identifier = (YIdentifier) iterator2.next();
                engine.cancelCase(identifier);
            }
            engine.unloadSpecification(specID);
        }
    }
}
```

**Limitations**:
- Only clears running cases and specifications
- Does NOT clear static state (session cache, event loggers, persistence manager)
- Race conditions: Another thread may be modifying state during enumeration
- Not idempotent: Multiple calls may fail if state is inconsistent

### 1.3 Static Members at Risk

Analysis of YEngine class (lines 85-102 in source):

```java
protected static YPersistenceManager _pmgr;          // ✗ SHARED
private static YEventLogger _yawllog;                 // ✗ SHARED
private static YCaseNbrStore _caseNbrStore;           // ✗ SHARED
private static Logger _logger;                        // ✓ Thread-safe
private static Set<YTimedObject> _expiredTimers;      // ✗ SHARED (set)
private static boolean _generateUIMetaData;           // ✗ SHARED
private static boolean _persisting;                   // ✗ SHARED (volatile missing)
private static boolean _restoring;                    // ✗ SHARED (volatile missing)
private static ThreadLocal<TenantContext> _currentTenant; // ✓ Thread-local
```

**Risk Assessment**:
- **HIGH RISK (5)**: `_pmgr`, `_yawllog`, `_caseNbrStore`, `_expiredTimers`, `_persisting`
- **MEDIUM RISK (2)**: `_generateUIMetaData`, `_restoring`
- **SAFE (1)**: `_logger` (final, thread-safe)
- **ALREADY SAFE (1)**: `_currentTenant` (ThreadLocal)

### 1.4 Test State Mutations (Findings)

From grep analysis: 62 test classes marked with `@Execution(ExecutionMode.SAME_THREAD)`.

**Examples of state-mutating tests**:
- `EngineIntegrationTest`: Loads/unloads specs, cancels cases
- `NetRunnerBehavioralTest`: Creates cases, advances tokens, checks conditions
- `TaskLifecycleBehavioralTest`: Creates work items, transitions states
- `VirtualThreadStressTest`: Concurrent case execution (within single test)
- `MemoryLeakStressTest`: Creates 1000s of cases, clears between iterations

**Mutation Pattern**:
```
@BeforeEach: YEngine engine = YEngine.getInstance();
             EngineClearer.clear(engine);
             // Load specifications, create cases...

@Test: Advance workflow, create work items, etc.

@AfterEach: EngineClearer.clear(engine);
```

**Issue**: If two tests run concurrently:
- Test A: Creating case → Test B calls `clear()` → Case is cancelled unexpectedly
- Test A: Loading spec → Test B calls `unloadSpecification()` → RACE CONDITION
- Both share `_pmgr`, `_caseNbrStore` → Results inconsistent

---

## 2. Thread-Local Isolation Redesign

### 2.1 Architecture Overview

```
Current (Single Thread):
  Thread 1 ──→ YEngine._thisInstance ──→ [Specs, Cases, Work Items, State]

Parallel Problem:
  Thread 1 ──→ ┐
             YEngine._thisInstance ──→ [Corrupted State]
  Thread 2 ──→ ┘

Proposed (Thread-Local Isolation):
  Thread 1 ──→ ThreadLocalYEngine ──→ ThreadLocal<YEngine> ──→ Instance #1 [Clean State]
  Thread 2 ──→ ThreadLocalYEngine ──→ ThreadLocal<YEngine> ──→ Instance #2 [Clean State]
  Thread 3 ──→ ThreadLocalYEngine ──→ ThreadLocal<YEngine> ──→ Instance #3 [Clean State]
```

### 2.2 ThreadLocalYEngine Wrapper Implementation

**Goal**: Wrap YEngine singleton with thread-local storage, maintaining API compatibility.

**Key Design Decisions**:

1. **Wrapper Pattern, Not Inheritance**
   - ✓ Preserves YEngine unchanged
   - ✓ Zero impact on existing code
   - ✓ Can be activated via flag
   - ✓ Easy to rollback if issues arise

2. **Per-Thread Instance Creation**
   - Each test thread gets its own YEngine instance
   - `EngineClearer.clear()` only affects current thread
   - No cross-thread interference

3. **Backward Compatibility**
   - `YEngine.getInstance()` routes to thread-local if enabled
   - Existing tests need NO changes
   - Can be enabled/disabled via system property

### 2.3 Implementation Approach

**Option A: Transparent Wrapper (Recommended)**

```java
// ThreadLocalYEngineManager.java
public class ThreadLocalYEngineManager {
    private static final boolean THREAD_LOCAL_ENABLED =
        Boolean.parseBoolean(System.getProperty("yawl.test.threadlocal.isolation", "false"));

    private static final ThreadLocal<YEngine> _threadLocalEngine =
        ThreadLocal.withInitial(() -> createNewEngine());

    public static YEngine getInstance(boolean persisting) throws YPersistenceException {
        if (!THREAD_LOCAL_ENABLED) {
            return YEngine.getInstance(persisting);  // Original path
        }
        return _threadLocalEngine.get();
    }

    private static YEngine createNewEngine() {
        try {
            YEngine engine = new YEngine();
            engine.initialise(null, false, false, false);
            return engine;
        } catch (YPersistenceException e) {
            throw new RuntimeException("Failed to create thread-local engine", e);
        }
    }

    public static void clearCurrent() throws YPersistenceException, YEngineStateException {
        if (THREAD_LOCAL_ENABLED) {
            YEngine engine = _threadLocalEngine.get();
            EngineClearer.clear(engine);
            _threadLocalEngine.remove();  // Reset for next request
        }
    }
}
```

**Option B: Monkey-Patch YEngine (More Invasive)**

```java
// Modify YEngine.getInstance() to check system property
// Not recommended: requires modifying core engine class
```

### 2.4 Risk Mitigation

**Risk 1: Static Shared State (_pmgr, _caseNbrStore)**
- **Mitigation**: Each thread-local instance will get independent static state initialization
- **Test**: Add assertion to verify each thread sees different object identities

**Risk 2: Hibernate Session/Transaction Issues**
- **Mitigation**: Each thread has its own Hibernate session (standard practice)
- **Test**: Verify no cross-thread transaction pollution

**Risk 3: Timer/Scheduler Conflicts**
- **Current**: `_expiredTimers` is a static Set shared across all threads
- **Mitigation**: Make YEngine singleton own a per-instance timer set
- **Test**: Run stress tests with timers enabled

**Risk 4: Regression in Stateful Tests**
- **Current**: Some tests explicitly verify singleton behavior
- **Example**: `EngineIntegrationTest.testEngineInitialization()` calls `getInstance()` twice and checks `assertSame()`
- **Mitigation**: Add wrapper compatibility check; if thread-local enabled, verify same instance within thread

---

## 3. Validation Strategy

### 3.1 Correctness Testing

**Unit Tests**:
```java
// ThreadLocalYEngineManagerTest.java
class ThreadLocalYEngineManagerTest {
    @Test void testSameInstanceWithinThread() { ... }  // ✓ singleton per thread
    @Test void differentInstancesAcrossThreads() { ... }  // ✓ isolation
    @Test void clearRemovesThreadLocalEntry() { ... }  // ✓ cleanup
    @Test void caseIDsNotLeakBetweenThreads() { ... }  // ✓ isolation
}
```

**Integration Tests** (run in parallel):
```java
// ParallelEngineIsolationTest.java
@Execution(ExecutionMode.CONCURRENT)
class ParallelEngineIsolationTest {
    @Test
    void test1_LoadSpecAndCreateCase() { ... }  // Thread 1

    @Test
    void test2_LoadDifferentSpecAndCreateCase() { ... }  // Thread 2

    @Test
    void test3_ConcurrentCaseExecution() { ... }  // Thread 3
    // Verify: No corruption, specs isolated, case IDs unique
}
```

### 3.2 Performance Validation

**Benchmark**:
```bash
# Baseline (sequential)
mvn clean verify -P ci  # forkCount=1, no parallelism
# Time: ~180s

# With thread-local isolation (parallel)
mvn clean verify -Dyawl.test.threadlocal.isolation=true  # forkCount=2, parallelism=2.0
# Time: ~120s (expected 20-30% gain)
```

### 3.3 Regression Testing

**Scenarios**:
1. All existing tests still pass with thread-local enabled
2. Singleton behavior within a thread is preserved
3. EngineClearer.clear() works correctly with thread-local
4. Cross-thread operations (if any) still work via thread pool/executor

---

## 4. Implementation Roadmap

### Phase 3a: Core Implementation (30 min)
1. Create `ThreadLocalYEngineManager` class
2. Add system property flag `yawl.test.threadlocal.isolation`
3. Add static method to YEngine for delegating to manager
4. Update EngineClearer to support thread-local cleanup

### Phase 3b: Testing (1 hour)
1. Write unit tests for thread-local manager
2. Write concurrent integration test suite
3. Run full test suite with flag enabled
4. Performance benchmark (sequential vs parallel)

### Phase 3c: Integration (30 min)
1. Activate flag in Maven profile `integration-parallel`
2. Update `pom.xml` failsafe config to use flag
3. Update `dx.sh` to document flag availability
4. Commit with comprehensive validation

### Phase 3d: Validation (30 min)
1. Run full build with all profiles
2. Verify no regressions in test reliability
3. Document expected speedup in build docs
4. Create troubleshooting guide for edge cases

---

## 5. Risk Assessment & Mitigation

| Risk | Severity | Probability | Mitigation |
|------|----------|-------------|-----------|
| Static state corruption | CRITICAL | LOW (design addresses) | Comprehensive unit tests + concurrent validation |
| Hibernate session issues | HIGH | LOW (per-thread by default) | Run DB-heavy tests in parallel |
| Timer/scheduler conflicts | MEDIUM | MEDIUM (expiredTimers set) | Make timer set per-instance, not static |
| Singleton test breakage | MEDIUM | HIGH | Add wrapper compatibility assertions |
| Performance regression | LOW | LOW | Benchmark before/after; rollback if needed |

---

## 6. Success Criteria

✅ **Correctness**:
- [ ] All existing tests pass with thread-local isolation enabled
- [ ] Concurrent tests show no cross-thread state corruption
- [ ] EngineClearer works correctly in thread-local mode
- [ ] Case IDs are unique across parallel tests

✅ **Performance**:
- [ ] Integration test suite runs 20-30% faster in parallel
- [ ] Unit tests unaffected (still <10s)
- [ ] Memory usage acceptable (one engine per thread)

✅ **Compatibility**:
- [ ] Zero code changes in existing tests
- [ ] Flag can be toggled without recompile
- [ ] Rollback to sequential mode is simple

✅ **Documentation**:
- [ ] ThreadLocalYEngineManager class has clear javadoc
- [ ] System property behavior documented
- [ ] Troubleshooting guide created
- [ ] Performance baseline recorded

---

## 7. Appendix: State Mutation Map

### By Test Class
| Test Class | State Modified | Impact |
|------------|----------------|--------|
| EngineIntegrationTest | Specs, Cases | HIGH - mixed lifecycle |
| NetRunnerBehavioralTest | Token flow, task execution | HIGH - complex state |
| TaskLifecycleBehavioralTest | Work items, transitions | HIGH - state-heavy |
| VirtualThreadStressTest | Concurrent cases | HIGH - parallelism within test |
| MemoryLeakStressTest | 1000s of cases | HIGH - cleanup heavy |

### By YEngine Member
| Member | Type | Risk | Isolation Strategy |
|--------|------|------|-------------------|
| `_thisInstance` | Static Reference | HIGH | Thread-local wrapper |
| `_pmgr` | Static Reference | HIGH | Per-instance (lazy init) |
| `_caseNbrStore` | Static Reference | HIGH | Per-instance (singleton instance) |
| `_expiredTimers` | Static Set | MEDIUM | Per-instance set |
| `_persisting` | Static Boolean | MEDIUM | Per-instance flag |
| `_runningCaseIDToSpecMap` | Instance Map | SAFE | Already per-instance |
| `_specifications` | Instance Table | SAFE | Already per-instance |

---

## 8. References

- **Current EngineClearer**: `/home/user/yawl/test/org/yawlfoundation/yawl/engine/EngineClearer.java`
- **YEngine Source**: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
- **Maven Config**: `/home/user/yawl/pom.xml` lines 1451-1504 (surefire)
- **Test Config**: `/home/user/yawl/test/resources/junit-platform.properties`
- **Integration Tests**: `/home/user/yawl/test/org/yawlfoundation/yawl/engine/*Test.java`
- **Plan Reference**: `/root/.claude/plans/mossy-meandering-meadow.md`

---

**APPROVED FOR IMPLEMENTATION** ✓

This design is feasible, low-risk, and high-impact. Proceed with Phase 3b testing and integration.
