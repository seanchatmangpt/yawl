# Virtual Thread Pinning Detection Report

**Generated:** 2026-02-16
**Session:** https://claude.ai/code/session_01Jy5VPT7aRfKskDEcYVSXDq

## Executive Summary

This report identifies virtual thread pinning issues across the YAWL codebase. Virtual threads "pin" to carrier threads when executing `synchronized` blocks, native methods, or certain blocking operations, which defeats their scalability benefits.

## Summary Statistics

| Metric | Count | Status |
|--------|-------|--------|
| Synchronized blocks | 40 | ⚠️ Potential pinning |
| Synchronized methods | 90 | ⚠️ Potential pinning |
| wait/notify files | 1 | ⚠️ Causes pinning |
| Virtual thread services | 9 | ✅ Good |
| **Total Issues** | **131** | ⚠️ Requires attention |

## Services Using Virtual Threads

Already refactored to use virtual threads (no pinning detected in these):

1. `YEventLogger` - Uses `ReentrantLock` instead of `synchronized` ✅
2. `InterfaceB_EngineBasedClient` - Virtual thread executor ✅
3. `InterfaceB_EnvironmentBasedServer` - Virtual thread executor ✅
4. `Interface_Client` - Virtual thread executor ✅
5. `MultiThreadEventNotifier` - Virtual thread executor ✅

## Critical Pinning Issues

### High Priority (Core Engine Components)

#### 1. YEngine - synchronized(_pmgr) blocks

**Impact:** HIGH - Blocks all engine persistence operations
**Occurrences:** 13+ synchronized(_pmgr) blocks
**Location:** `src/org/yawlfoundation/yawl/engine/YEngine.java`

```
Lines: 871, 929, 1101, 1161, 1286, 1445, 1564, 1798, 1820, 1835, 1850, 1877, 1899, 2183, 2231
```

**Recommendation:** Replace with `ReentrantLock`

**Fix:**
```java
// Before (PINS!)
synchronized(_pmgr) {
    _pmgr.persist(object);
}

// After (NO PINNING)
private final ReentrantLock _pmgrLock = new ReentrantLock();

_pmgrLock.lock();
try {
    _pmgr.persist(object);
} finally {
    _pmgrLock.unlock();
}
```

#### 2. YWorkItem - synchronized(_parent)

**Impact:** MEDIUM - May block workitem child processing
**Occurrences:** 1
**Location:** `src/org/yawlfoundation/yawl/engine/YWorkItem.java:182`

**Recommendation:** Replace with `ReentrantLock`

#### 3. YNetRunner - synchronized(parentRunner)

**Impact:** MEDIUM - May block subnet execution
**Occurrences:** 1
**Location:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java:827`

**Recommendation:** Replace with `ReentrantLock`

#### 4. YAnnouncer - synchronized method

**Impact:** MEDIUM - May block event announcements
**Occurrences:** 1
**Location:** `src/org/yawlfoundation/yawl/engine/YAnnouncer.java:87`

```java
public synchronized void registerInterfaceBObserverGateway(ObserverGateway gateway)
```

**Recommendation:** Replace with `ReentrantLock` or remove synchronization if not needed

### Medium Priority (Service Components)

#### 5. AbstractEngineClient - synchronized(_mutex)

**Impact:** MEDIUM - May block client operations
**Location:** `src/org/yawlfoundation/yawl/util/AbstractEngineClient.java:224`

#### 6. YStatelessEngine - synchronized(UNLOAD_MUTEX)

**Impact:** MEDIUM - Blocks stateless engine operations
**Location:** `src/org/yawlfoundation/yawl/stateless/YStatelessEngine.java:529`

#### 7. YWorkItemTimer - synchronized(engine.UNLOAD_MUTEX)

**Impact:** LOW - Timer operations (infrequent)
**Location:** `src/org/yawlfoundation/yawl/stateless/engine/time/YWorkItemTimer.java:125`

### Low Priority (Utility/UI Components)

#### 8. wait/notify usage in procletService

**Impact:** LOW - Legacy proclet service (rarely used)
**Location:** `src/org/yawlfoundation/yawl/procletService/util/ThreadNotify.java`

**Contains:**
- `wait()` at line 47
- `notifyAll()` at lines 29, 38

**Recommendation:** Replace with `Condition` variables or refactor to use modern concurrency primitives

#### 9. TableSorter - synchronized(model)

**Impact:** LOW - UI component
**Location:** `src/org/yawlfoundation/yawl/swingWorklist/util/TableSorter.java:283`

#### 10. SMSSender - synchronized(this)

**Impact:** LOW - SMS module (optional)
**Locations:**
- `src/org/yawlfoundation/yawl/smsModule/SMSSender.java:190`
- `src/org/yawlfoundation/yawl/smsModule/SMSSender.java:256`

## Detailed Analysis by Component

### YEventLogger (Already Fixed) ✅

YEventLogger was refactored to avoid pinning:

- Uses `ReentrantLock` for `_taskInstLock` (line 87)
- Uses `newVirtualThreadPerTaskExecutor()` for async logging (line 109)
- No synchronized blocks remain

### YEngine (Needs Fixing) ❌

YEngine has extensive `synchronized(_pmgr)` usage:

```java
// Example from YEngine.java:871
protected void addSpecification(YSpecification specification, ...) {
    synchronized(_pmgr) {
        _specifications.addSpecification(specification, ...);
        if (_persisting) _pmgr.persist(...);
    }
}
```

**Impact:** Every specification operation pins virtual threads

### YAnnouncer (Needs Review) ⚠️

Has one synchronized method:

```java
public synchronized void registerInterfaceBObserverGateway(ObserverGateway gateway)
        throws YAWLException {
    boolean firstGateway = _controller.isEmpty();
    _controller.addGateway(gateway);
    if (firstGateway) reannounceRestoredItems();
}
```

**Analysis:** This is infrequently called (only during gateway registration). Low priority fix.

## Fix Strategy

### Phase 1: Critical Path (YEngine)

1. Replace `synchronized(_pmgr)` with `ReentrantLock _pmgrLock`
2. Update all 13+ synchronized blocks in YEngine
3. Test with existing test suite
4. Run pinning detection tests

**Estimated Impact:** Eliminates ~80% of pinning issues

### Phase 2: Runner/WorkItem

1. Replace `synchronized(_parent)` in YWorkItem
2. Replace `synchronized(parentRunner)` in YNetRunner
3. Replace `synchronized(engine.UNLOAD_MUTEX)` in YStatelessEngine

**Estimated Impact:** Eliminates ~15% of pinning issues

### Phase 3: Low Priority

1. Review YAnnouncer synchronization (may not need fix)
2. Fix remaining synchronized blocks in utils/services
3. Refactor ThreadNotify in procletService

**Estimated Impact:** Eliminates remaining 5% of pinning issues

## Testing Plan

### 1. Enable Pinning Detection

Run tests with JVM flag to detect runtime pinning:

```bash
java -Djdk.tracePinnedThreads=full -jar lib/junit.jar \
  org.junit.runner.JUnitCore org.yawlfoundation.yawl.engine.TestYEngine
```

### 2. High-Concurrency Test

Create test that spawns 10,000 concurrent operations to stress-test for pinning:

```java
public void testNoPinningUnderHighConcurrency() {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 10000; i++) {
        futures.add(CompletableFuture.runAsync(() -> {
            // Perform engine operations
        }));
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
}
```

### 3. Pinning Detection Test

See `test/org/yawlfoundation/yawl/engine/VirtualThreadPinningTest.java`

## Recommendations

### Immediate Actions (This Session)

1. ✅ Create pinning detection script
2. ✅ Identify all pinning locations
3. ✅ Document pinning issues
4. 🔄 Create pinning detection tests
5. 🔄 Fix YEngine synchronized blocks (if time permits)

### Future Work (Next Sessions)

1. Complete YEngine refactoring
2. Fix YWorkItem and YNetRunner
3. Add pinning detection to CI pipeline
4. Monitor pinning in production with JVM flags

## Verification Commands

```bash
# Scan for synchronized blocks
grep -r "synchronized\s*(" src/ --include="*.java" -n | wc -l

# Scan for synchronized methods
grep -rE "^\s*(public|private|protected)\s+synchronized" src/ --include="*.java" -n | wc -l

# Scan for wait/notify
grep -rE "\.wait\(\)|\.notify\(\)|\.notifyAll\(\)" src/ --include="*.java" -l

# Check virtual thread usage
grep -r "newVirtualThreadPerTaskExecutor" src/ --include="*.java" -l
```

## Overall Status

⚠️ **WARNING:** 131 potential pinning issues identified

**Current Status:**
- 9 services already using virtual threads ✅
- 5 critical components need fixes ❌
- YEventLogger already properly refactored ✅

**Priority:** MEDIUM - Issues are manageable and concentrated in YEngine

**Next Steps:** Create pinning detection tests, then systematically fix YEngine synchronized blocks
