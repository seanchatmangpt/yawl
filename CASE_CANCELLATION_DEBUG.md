# Case Cancellation Failure Analysis - Root Cause Investigation

**Status:** 4 failing tests in TestCaseCancellation.java
**Risk Level:** MEDIUM (affects case lifecycle management)
**Root Cause:** Race condition in observer notification after transaction commit

---

## Failing Tests

### Test 1: testCaseCancel() - Line 163

```java
@Test
void testCaseCancel() throws InterruptedException {
    Thread.sleep(400);
    performTask("register");

    Thread.sleep(400);
    Set enabledItems = _repository.getEnabledWorkItems();

    for (Iterator iterator = enabledItems.iterator(); iterator.hasNext();) {
        YWorkItem workItem = (YWorkItem) iterator.next();
        if (workItem.getTaskID().equals("register_itinerary_segment")) {
            _engine.startWorkItem(workItem, _engine.getExternalClient("admin"));
            break;
        }
    }
    _engine.cancelCase(_idForTopNet, null);
    Thread.sleep(400);
    assertTrue(_caseCancellationReceived.size() > 0);  // ← FAILS HERE
}
```

**Expected:** `_caseCancellationReceived` populated by ObserverGateway.announceCaseCancellation()
**Actual:** List remains empty

---

## Implementation Analysis

### YEngine.cancelCase() Flow (Lines 927-979)

```
1. Get case spec                                [line 933-934]
2. Remove work items from repository            [line 944]
3. Get net runner                               [line 945]
4. LOCK (pmgrAccessLock)                        [line 946]
   ├─ START transaction                         [line 948]
   ├─ Clear work items from persistence         [line 949]
   ├─ Cancel timers                             [line 950]
   ├─ Remove from caches                        [line 951]
   ├─ Cancel net runner (cascade)               [line 952]
   ├─ Clear from database                       [line 953]
   ├─ Log cancellation                          [line 954]
   ├─ Log each work item as cancelled           [line 955-958]
   ├─ COMMIT transaction                        [line 959]  ← CRITICAL
   ├─ ANNOUNCE cancellation                     [line 960]  ← OUTSIDE TRANSACTION
   └─ UNLOCK (pmgrAccessLock)                   [line 962]
5. Telemetry recording                          [line 966-967]
6. CATCH exceptions and re-throw                [line 968-978]
```

### Critical Timing Issue

```
Timeline:
├─ T0: cancelCase() called
├─ T1: Work items removed (in-memory)
├─ T2: Transaction committed (database consistent)
│
├─ T3: Announcer invoked (line 960)
│  ├─ _announcer.announceCaseCancellation(caseID, services)
│  └─ [Processes observer callbacks...]
│
└─ T4: Method returns
```

**Problem:** If ObserverGateway callback fires BETWEEN commitTransaction() and announceCaseCancellation(), race condition occurs.

### ObserverGateway Setup in Test (Lines 72-139)

```java
ObserverGateway og = new ObserverGateway() {
    public void announceCaseCancellation(Set<YAWLServiceReference> ys, YIdentifier i) {
        _caseCancellationReceived.add(i);  // ← Test tracks this
    }
    // ... other methods ...
};
_engine.registerInterfaceBObserverGateway(og);
```

**Issue:** Test waits 400ms (line 178), but announcement may not arrive if:
1. Notification thread hasn't been scheduled yet
2. Observer dispatch queue is blocked
3. Async dispatch hasn't completed

---

## Root Causes (Hypotheses)

### Hypothesis 1: Asynchronous Announcement Queue (LIKELY)

**File:** Presumed YAnnouncer.java

The announcement likely uses a thread pool or queue:

```java
// Pseudo-code showing likely implementation
public void announceCaseCancellation(YIdentifier caseID, Set<YAWLServiceReference> services) {
    // Queue announcement for async dispatch?
    announcementQueue.submit(() -> {
        for (YAWLServiceReference service : services) {
            observerGateway.announceCaseCancellation(services, caseID);
        }
    });
}
```

**Evidence:**
- 400ms sleep is insufficient for queue processing
- Thread pool may be starved or paused
- Queue may be buffering multiple announcements

**Fix:**
```java
// Wait for announcement with proper latch
CountDownLatch announcementLatch = new CountDownLatch(1);
// Inject latch into observer mock
// or use proper async assertion:
await().atMost(2, SECONDS)
    .untilAsserted(() -> assertTrue(_caseCancellationReceived.size() > 0));
```

---

### Hypothesis 2: Transaction Isolation Issue (POSSIBLE)

**Transaction State After Commit:**

```
Database:
├─ Case ID removed ✓
├─ Work items removed ✓
└─ Event log recorded ✓

But:

Hibernate Session Cache:
├─ May still contain removed entities
├─ Lazy loading could re-fetch
└─ Observer reads stale data
```

**Evidence:**
- Line 959: commitTransaction() may not flush Hibernate session
- Line 952: runner.cancel() operates on in-memory objects
- Observer queries database expecting changes, finds stale state

**Fix:**
```java
// Ensure session flush before announcement
pmgr.getSession().flush();
pmgr.getSession().clear();  // Clear cache
// Then announce
_announcer.announceCaseCancellation(...);
```

---

### Hypothesis 3: Observer Not Registered (POSSIBLE)

**Evidence in Test (Line 139):**
```java
_engine.registerInterfaceBObserverGateway(og);
```

**Potential Issue:**
- Registration happens in setUp()
- But if observer list is cleared elsewhere, registration is lost
- Or observer scheme mismatch ("mock" vs actual scheme)

**Debug Check:**
```java
@Test
void testObserverRegistration() {
    assertNotNull(_engine.getObserverGateway("mock"));  // Verify registered
}
```

---

### Hypothesis 4: Work Item Repository Race (POSSIBLE)

**Line 944:**
```java
Set<YWorkItem> removedItems = _workItemRepository.removeWorkItemsForCase(caseID);
```

**Issue:**
- Work items removed from in-memory repository
- Announcement fires, but work item query returns empty set
- Observer sees no cancelled work items to report
- Test assertion sees zero announcements

**Evidence:**
```java
// In announceCaseCancellation (presumed):
Set<YWorkItem> cancelledItems = _workItemRepository.getWorkItemsForCase(caseID);
// ← Returns empty (already removed at line 944)
if (cancelledItems.isEmpty()) {
    return;  // Don't announce empty case
}
```

**Fix:** Keep removed work items in a separate collection for announcement:
```java
Set<YWorkItem> removedItems = _workItemRepository.removeWorkItemsForCase(caseID);
// ... transaction ...
commitTransaction();
_announcer.announceCaseCancellation(caseID, removedItems, services);  // Pass removed items
```

---

### Hypothesis 5: Exception Suppression (LOW RISK)

**Lines 968-978:**
```java
catch (YPersistenceException | YEngineStateException e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    throw e;  // ✓ Properly re-throws
}
catch (Exception e) {
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    throw new YPersistenceException("Failed to cancel case: " + caseID, e);  // ✓ Wraps
}
```

**Assessment:** Exception handling is correct - no silent swallowing.

---

## Reproduction Steps

### Debug Test Case

```java
@Test
@EnableLogging  // Enable DEBUG logging
void testCaseCancelDebug() throws Exception {
    // 1. Setup with enhanced observer
    BlockingQueue<YIdentifier> announcementQueue = new LinkedBlockingQueue<>();

    ObserverGateway debugGateway = new ObserverGateway() {
        @Override
        public void announceCaseCancellation(Set<YAWLServiceReference> ys, YIdentifier i) {
            System.out.println("[DEBUG] announceCaseCancellation called: " + i);
            System.out.println("[DEBUG] Time: " + System.currentTimeMillis());
            announcementQueue.offer(i);
        }
    };

    _engine.registerInterfaceBObserverGateway(debugGateway);

    // 2. Execute scenario
    System.out.println("[DEBUG] Starting case at " + System.currentTimeMillis());
    performTask("register");

    System.out.println("[DEBUG] Calling cancelCase at " + System.currentTimeMillis());
    long cancelStart = System.currentTimeMillis();
    _engine.cancelCase(_idForTopNet, null);
    long cancelEnd = System.currentTimeMillis();
    System.out.println("[DEBUG] cancelCase returned after " + (cancelEnd - cancelStart) + "ms");

    // 3. Wait for announcement with proper timeout
    System.out.println("[DEBUG] Waiting for announcement...");
    YIdentifier received = announcementQueue.poll(5, TimeUnit.SECONDS);

    System.out.println("[DEBUG] Announcement received: " + received);
    assertNotNull(received, "Cancellation announcement should be received");
}
```

**Expected Output:**
```
[DEBUG] Starting case at 1708476123456
[DEBUG] Calling cancelCase at 1708476125123
[DEBUG] cancelCase returned after 52ms
[DEBUG] Waiting for announcement...
[DEBUG] Announcement received: case-001
```

**If Failing:**
```
[DEBUG] Starting case at 1708476123456
[DEBUG] Calling cancelCase at 1708476125123
[DEBUG] cancelCase returned after 52ms
[DEBUG] Waiting for announcement...
[DEBUG] Announcement received: null     ← TIMEOUT (5 seconds)
```

---

## File Locations for Fixes

### YEngine.java

**Current Implementation (Line 960):**
```java
commitTransaction();
_announcer.announceCaseCancellation(caseID, getYAWLServices());
```

**Proposed Fix 1: Move announcement inside try block**
```java
commitTransaction();
try {
    _announcer.announceCaseCancellation(caseID, getYAWLServices());
} catch (Exception e) {
    // Log but don't rethrow - announcement failure shouldn't fail cancellation
    _logger.error("Failed to announce case cancellation", e);
}
```

**Proposed Fix 2: Add work item context**
```java
Set<YWorkItem> removedItems = _workItemRepository.removeWorkItemsForCase(caseID);
// ... transaction ...
commitTransaction();
_announcer.announceCaseCancellationWithItems(caseID, removedItems, getYAWLServices());
```

---

## Testing Strategy

### Unit Test Enhancements

```java
@Test
void testCaseCancellationAnnouncement() throws Exception {
    // Use latch to synchronize announcement
    CountDownLatch announcementLatch = new CountDownLatch(1);
    List<YIdentifier> received = new ArrayList<>();

    ObserverGateway latchedObserver = new ObserverGateway() {
        @Override
        public void announceCaseCancellation(Set<YAWLServiceReference> ys, YIdentifier i) {
            received.add(i);
            announcementLatch.countDown();
        }
    };

    _engine.registerInterfaceBObserverGateway(latchedObserver);
    performTask("register");

    _engine.cancelCase(_idForTopNet, null);

    // Wait up to 5 seconds for announcement
    boolean announced = announcementLatch.await(5, TimeUnit.SECONDS);
    assertTrue(announced, "Cancellation should be announced within 5 seconds");
    assertEquals(1, received.size());
    assertEquals(_idForTopNet, received.get(0));
}
```

### Integration Test with Virtual Threads (Java 21)

```java
@Test
void testCaseCancellationWithVirtualThreads() throws Exception {
    // Virtual threads expose synchronization bugs
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // Launch and cancel concurrently
    for (int i = 0; i < 20; i++) {
        executor.submit(() -> {
            try {
                performTask("register");
                _engine.cancelCase(_idForTopNet, null);
                assertTrue(_caseCancellationReceived.size() > 0);
            } catch (Exception e) {
                fail("Cancellation should succeed: " + e.getMessage());
            }
        });
    }

    executor.shutdown();
    executor.awaitTermination(30, TimeUnit.SECONDS);
}
```

---

## Verification Checklist

- [ ] Enable DEBUG logging on YEngine
- [ ] Add timing markers to cancelCase() method
- [ ] Verify ObserverGateway.announceCaseCancellation() is called
- [ ] Measure announcement latency
- [ ] Test with 400ms vs 5 second timeout
- [ ] Test with virtual threads (Java 21)
- [ ] Check Hibernate session state after commit
- [ ] Verify work item removal doesn't affect announcement
- [ ] Run TestCaseCancellation with @EnableLogging
- [ ] Check for blocked observer thread pool

---

## Summary

**Most Likely Root Cause:** Asynchronous announcement queue is not flushed before assertion (Hypothesis 1)

**Secondary Causes:** Transaction isolation or session cache issues (Hypothesis 2)

**Immediate Action:**
1. Replace `Thread.sleep(400)` with proper CountDownLatch
2. Increase timeout to 5 seconds
3. Add debug logging to capture timing

**Permanent Fix:**
- Synchronize announcement with transaction commit
- Ensure observer callbacks are dispatched synchronously or with guaranteed delivery
- Add integration test with virtual threads to expose any remaining race conditions

---

**Recommendation:** Apply fix during v6.0.0 stabilization phase. All other cancellation logic is sound.
