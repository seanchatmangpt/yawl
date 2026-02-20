# YAWL Engine Implementation Tasks
## WCP-34 to WCP-39 Pattern Support

**Date:** 2026-02-20
**Scope:** Implementation tasks derived from Phase 1 validation analysis
**Format:** Actionable task list with file paths and code locations

---

## CRITICAL PATH: Foundation Tasks

### Task 1: Dynamic Partial Join Threshold Calculation

**Effort:** 1-2 weeks | **Blocker for:** WCP-35

**Files to Create:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/core/marking/IPartialJoinEvaluator.java (new)
```

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/core/marking/IMarkingTask.java
  → Add methods:
    - String getThresholdMode()         // "count" or "percentage"
    - String getThresholdQuery()        // threshold expression
    - Integer getJoinThreshold()        // for backward compatibility

/home/user/yawl/src/org/yawlfoundation/yawl/engine/core/marking/YCoreMarking.java
  → Add methods:
    - protected int evaluatePartialJoinThreshold(IMarkingTask, Object)
    - protected int computeThresholdFromPercentage(int preset, int percentage)
    - protected int evaluateThresholdQuery(String query, Object context)
  → Modify:
    - doPreliminaryMarkingSetBasedOnJoinType() to use evaluatePartialJoinThreshold()

/home/user/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
  → Add fields:
    - private String _thresholdMode = "count"
    - private String _thresholdQuery
  → Add methods:
    - getThresholdMode()
    - getThresholdQuery()
    - setThresholdMode()
    - setThresholdQuery()

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add fields:
    - private final Map<String, Integer> _thresholdCache = new ConcurrentHashMap<>()
  → Add method:
    - private int evaluateTaskThreshold(YTask task)
    - private boolean isPartialJoinReady(YTask partialJoin)
  → Modify:
    - kick() to check isPartialJoinReady() for partial join tasks
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/engine/core/marking/PartialJoinThresholdTest.java
  → testStaticThreshold()           // WCP-34
  → testPercentageThreshold()       // WCP-35
  → testVariableRefThreshold()      // WCP-35 with branchCount
  → testThresholdCaching()          // Performance
  → testThresholdRecalculation()    // Threshold changes mid-execution

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp35DynamicPartialJoinTest.java
  → testYamlLoadAndConvert()        // YAML -> XML -> Spec
  → testDynamicBranchExecution()    // 1-7 branches with 60% threshold
  → testThresholdRespected()        // Fires when threshold met
```

**Code Changes Summary:**
- Add threshold evaluation infrastructure (+~300 LOC)
- Modify marking algorithm to use dynamic evaluation (+~50 LOC)
- Add YNetRunner integration (+~100 LOC)
- Total: ~450 LOC

**Review Checklist:**
- [ ] IMarkingTask interface updated
- [ ] YCoreMarking evaluation logic correct
- [ ] YTask threshold properties properly persisted
- [ ] YNetRunner kick logic updated
- [ ] Unit tests all passing
- [ ] Integration test traces verified
- [ ] Performance baseline established

---

### Task 2: Multi-Instance Discriminator Completion

**Effort:** 3-5 days | **Blocker for:** WCP-36

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
  → Add field:
    - private YTask _completeMIParent
  → Add methods:
    - YTask getCompleteMIParent()
    - void setCompleteMIParent(YTask parent)
    - boolean isCompleteMI()

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add field:
    - private final AtomicBoolean _miContainerComplete = new AtomicBoolean(false)
  → Add methods:
    - private void completeMultiInstanceContainer(YTask miContainer, YPersistenceManager pmgr)
    - private List<YWorkItem> getInstancesForContainer(YTask container)
  → Modify:
    - completeTask() to check task.isCompleteMI()
    - completeTask() to call completeMultiInstanceContainer() when needed

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YWorkItemRepository.java
  → Add method:
    - List<YWorkItem> getInstancesForMIContainer(YTask container)
    - (query items where taskID equals container.getID())
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/engine/YNetRunnerMultiInstanceTest.java
  → testCompleteMIProperty()         // Property set/get
  → testAtomicCancellation()         // All instances cancelled in transaction
  → testNoOrphanedItems()            // No items left after completion
  → testRaceConditionPrevention()    // compareAndSet prevents double-completion
  → testSubsequentInstancesIgnored() // If instance N+1 completes after MI done, ignored

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp36DiscriminatorTest.java
  → testYamlLoadAndConvert()         // WCP-36 YAML pattern
  → testFirstInstanceWins()          // First to complete cancels others
  → testVariableConsistency()        // Output data consistent
  → testNoDeadlock()                 // No locking issues
```

**Code Changes Summary:**
- Add completeMI property to YTask (+~20 LOC)
- Add cancellation logic to YNetRunner (+~80 LOC)
- Add instance query to repository (+~30 LOC)
- Total: ~130 LOC

**Review Checklist:**
- [ ] YTask completeMI property properly serialized
- [ ] Atomic compare-and-set prevents double-completion
- [ ] All instances of container properly cancelled
- [ ] No orphaned work items in database
- [ ] Unit tests verify atomicity
- [ ] Integration test traces verified

---

### Task 3: Local Event Trigger Mechanism

**Effort:** 1-2 weeks | **Blocker for:** WCP-37

**Files to Create:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/elements/YTaskTrigger.java (new)
  → Record class with fields:
    - String taskID
    - String event               // e.g., "external_signal"
    - TriggerType type          // LOCAL, GLOBAL, RESET
    - Duration timeout
    - String timeoutFlowID      // XOR alternative
    - String triggerFlowID      // primary path

/home/user/yawl/src/org/yawlfoundation/yawl/elements/TriggerType.java (new)
  → Enum: LOCAL, GLOBAL, RESET

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YTriggerRegistry.java (new)
  → Manages local trigger subscriptions per case
  → Methods:
    - void subscribe(String caseID, String event, YTask task)
    - void unsubscribe(String caseID, String event, String taskID)
    - void fireEvent(String caseID, String event, Object data)
    - Set<YTask> getSubscribersFor(String event)
```

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
  → Add field:
    - private YTaskTrigger _trigger
  → Add methods:
    - YTaskTrigger getTrigger()
    - void setTrigger(YTaskTrigger trigger)
    - boolean hasTrigger()

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add fields:
    - private final Map<String, YTaskTrigger> _taskTriggers = new ConcurrentHashMap<>()
    - private final YTriggerRegistry _triggerRegistry
    - private final Map<String, YWorkItemTimer> _triggerTimers = new ConcurrentHashMap<>()
  → Add methods:
    - public void handleTriggerEvent(String eventName, Object data, YPersistenceManager pmgr)
    - public void handleTriggerTimeout(String taskID, YPersistenceManager pmgr)
    - private void startTriggerTimer(YTask task)
    - private void cancelTriggerTimer(String taskID)
  → Modify:
    - enableTask() to start trigger timers
    - completeTask() to cancel trigger timers
    - kick() to check for trigger-enabled tasks

/home/user/yawl/src/org/yawlfoundation/yawl/stateless/YStatelessEngine.java
  → Add method:
    - public void sendLocalTrigger(String caseID, String eventName, Map<String, String> data)
    - (delegates to YNetRunner.handleTriggerEvent())
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/engine/YTriggerRegistryTest.java
  → testSubscription()               // Subscribe/unsubscribe
  → testEventDispatching()           // Event routed to subscriber
  → testUnsubscribedEvents()         // No dispatch if not subscribed
  → testMultipleSubscribers()        // N tasks for same event

/home/user/yawl/test/org/yawlfoundation/yawl/engine/YNetRunnerTriggerTest.java
  → testTriggerTimerStart()          // Timer created on task enable
  → testTriggerArrival()             // Event routes to triggerFlowID
  → testTimeoutFallback()            // No event -> timeoutFlowID
  → testTimerCancellation()          // Timer cancelled on task complete
  → testMutualExclusion()            // Trigger XOR timeout (not both)

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp37LocalTriggerTest.java
  → testYamlLoadAndConvert()         // WCP-37 YAML
  → testTriggerArrivalPath()         // Send trigger -> ProcessTrigger
  → testTimeoutPath()                // No trigger -> HandleTimeout
  → testTimeoutDuration()            // Timeout respects PT5M
```

**Code Changes Summary:**
- Create YTaskTrigger record (+~30 LOC)
- Create YTriggerRegistry (+~150 LOC)
- Modify YTask with trigger field (+~20 LOC)
- Modify YNetRunner trigger handling (+~200 LOC)
- Enhance YStatelessEngine API (+~30 LOC)
- Total: ~430 LOC

**Review Checklist:**
- [ ] YTaskTrigger properly serialized/deserialized
- [ ] Trigger subscription activated during task enablement
- [ ] Timer creation and cancellation correct
- [ ] Event routing to correct flow (trigger vs timeout)
- [ ] No memory leaks (timers, subscriptions cleaned up)
- [ ] Unit tests pass
- [ ] Integration tests verify traces

---

### Task 4: Global Event Broadcasting

**Effort:** 1-2 weeks | **Blocker for:** WCP-38

**Files to Create:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEventBroadcaster.java (new)
  → Singleton pub/sub for workflow events
  → Methods:
    - void subscribe(String eventName, String caseID, GlobalEventSubscriber sub)
    - void unsubscribe(String eventName, String caseID)
    - void broadcast(GlobalEvent event)
    - Set<GlobalEventSubscriber> getSubscribers(String eventName)

  → Record: GlobalEvent(eventName, payload, timestamp, sourceID)
  → Interface: GlobalEventSubscriber { void onEvent(GlobalEvent) }

/home/user/yawl/src/org/yawlfoundation/yawl/engine/GlobalEventSubscriber.java (new)
  → Functional interface for event handlers
```

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add field:
    - private final GlobalEventSubscriber _globalEventHandler
  → Add methods:
    - public void subscribeToGlobalEvent(String eventName)
    - public void unsubscribeFromGlobalEvent(String eventName)
    - private void handleGlobalEvent(YEventBroadcaster.GlobalEvent event)
  → Modify:
    - enableTask() to subscribe for GLOBAL trigger type
    - completeTask() to unsubscribe when task completes

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YAnnouncer.java
  → Add field:
    - private YEventBroadcaster _eventBroadcaster
  → Add methods:
    - public void broadcastGlobalEvent(String eventName, Map<String, Object> data)

/home/user/yawl/src/org/yawlfoundation/yawl/stateless/YStatelessEngine.java
  → Add method:
    - public void broadcastGlobalEvent(String eventName, Map<String, Object> payload)
    - (delegates to YAnnouncer.broadcastGlobalEvent())
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/engine/YEventBroadcasterTest.java
  → testSubscription()               // Subscribe/unsubscribe
  → testBroadcast()                  // Event delivered to all subscribers
  → testConcurrentBroadcast()        // Multiple cases receive event
  → testVirtualThreadExecution()     // Events processed concurrently
  → testOrderingNotGuaranteed()      // No ordering requirement

/home/user/yawl/test/org/yawlfoundation/yawl/engine/YNetRunnerGlobalTriggerTest.java
  → testGlobalTriggerSubscription()  // Subscribe on task enable
  → testGlobalEventRouting()         // Event enables correct task
  → testAndJoinSynchronization()     // Joins on all presets ready
  → testConcurrentCases()            // 3+ cases receive same event

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp38GlobalTriggerTest.java
  → testYamlLoadAndConvert()         // WCP-38 YAML
  → testParallelCasesReceiveEvent()  // N cases wait for broadcast
  → testGlobalEventUnsubscribe()     // Cleanup on case completion
  → testBurstLoad()                  // 1000 events/sec
```

**Code Changes Summary:**
- Create YEventBroadcaster (+~200 LOC)
- Enhance YNetRunner global trigger support (+~100 LOC)
- Integrate with YAnnouncer (+~40 LOC)
- Expose API in YStatelessEngine (+~20 LOC)
- Total: ~360 LOC

**Review Checklist:**
- [ ] Event broadcaster uses virtual thread executor
- [ ] No event loss under burst load
- [ ] Subscribers properly cleaned up on case completion
- [ ] AND-join logic respects global trigger constraint
- [ ] Unit tests verify concurrent delivery
- [ ] Integration tests scale to 100+ cases

---

### Task 5: Reset Checkpoint Recovery

**Effort:** 1 week | **Blocker for:** WCP-39

**Files to Create:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/elements/YCheckpoint.java (new)
  → Record with fields:
    - String caseID
    - String checkpointID
    - YMarking marking          // snapshot
    - YNetData variables        // snapshot
    - Instant createdAt
    - Set<String> workItemIDs   // to cancel on restore
  → Static factory:
    - static YCheckpoint from(String caseID, String checkpointID, YNetRunner runner)

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YCheckpointManager.java (new)
  → Manages checkpoint lifecycle
  → Methods:
    - void saveCheckpoint(YCheckpoint checkpoint)
    - YCheckpoint loadCheckpoint(String caseID, String checkpointID)
    - void deleteCheckpoint(String caseID, String checkpointID)
    - void cleanupOldCheckpoints(String caseID, int maxCount, int retentionDays)
```

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/elements/YTask.java
  → Add fields:
    - private YTaskTrigger _resetTrigger
    - private String _resetPoint        // checkpoint ID
  → Add methods:
    - YTaskTrigger getResetTrigger()
    - void setResetTrigger(YTaskTrigger trigger)
    - String getResetPoint()
    - void setResetPoint(String point)

/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add fields:
    - private final Map<String, YCheckpoint> _checkpoints = new ConcurrentHashMap<>()
  → Add methods:
    - public void createCheckpoint(String checkpointID)
    - public void resetToCheckpoint(String checkpointID, YPersistenceManager pmgr)
    - private void cancelWorkItemsForCheckpoint(YCheckpoint checkpoint, YPersistenceManager pmgr)
    - private void restoreMarkingAndVariables(YCheckpoint checkpoint)
  → Modify:
    - enableTask() to create checkpoint if task has resetPoint
    - handleTriggerEvent() to check for reset trigger type
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/elements/YCheckpointTest.java
  → testCheckpointCreation()         // Snapshot created correctly
  → testCheckpointSerialization()    // Persisted/restored
  → testVariableSnapshot()           // Variables captured
  → testMarkinSnapshot()             // Token positions captured

/home/user/yawl/test/org/yawlfoundation/yawl/engine/YCheckpointManagerTest.java
  → testSaveRetrieve()               // Persist and reload
  → testMaxCheckpoints()             // Enforce max count
  → testRetentionPolicy()            // Old checkpoints deleted
  → testCleanup()                    // Cleanup on case complete

/home/user/yawl/test/org/yawlfoundation/yawl/engine/YNetRunnerCheckpointTest.java
  → testCheckpointCreation()         // Created at task
  → testResetRestoresMarking()       // Marking restored
  → testResetRestoresVariables()     // Variables restored
  → testResetCancelsWorkItems()      // Cleanup on reset
  → testNoOrphanedItems()            // No DB orphans

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp39ResetTriggerTest.java
  → testYamlLoadAndConvert()         // WCP-39 YAML
  → testCheckpointCreation()         // Checkpoint at StartTask
  → testResetTriggering()            // Send reset signal
  → testStateRecovery()              // Case returns to checkpoint
  → testAuditTrail()                 // Reset recorded
```

**Code Changes Summary:**
- Create YCheckpoint record (+~40 LOC)
- Create YCheckpointManager (+~150 LOC)
- Modify YTask with reset fields (+~20 LOC)
- Enhance YNetRunner checkpoint API (+~150 LOC)
- Total: ~360 LOC

**Review Checklist:**
- [ ] Checkpoint creation at correct task
- [ ] Marking/variables deep-copied (not referenced)
- [ ] All active work items cancelled atomically
- [ ] Reset timer clears (no dangling timers)
- [ ] Checkpoint persistence/retrieval correct
- [ ] Audit trail records all resets
- [ ] No orphaned work items
- [ ] Integration tests verify state recovery

---

## SUPPORTING TASKS: Enhancement & Observability

### Task 6: Event Handler Virtualization

**Effort:** 3-5 days | **Enhancement for:** WCP-37, WCP-38 (high throughput)

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/stateless/engine/MultiThreadEventNotifier.java
  → Replace ThreadPoolExecutor with virtual thread executor:
    - private final Executor _executor = Executors.newVirtualThreadPerTaskExecutor()
  → Update event dispatch to use virtual thread pool
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/performance/EventHandlerThroughputTest.java
  → testVirtualThreadEventThroughput()  // 1000+ events/sec
  → testConcurrentEventProcessing()     // No blocking
  → testEventLatency()                  // < 10ms p95
```

---

### Task 7: Trigger Event Tracing

**Effort:** 3-5 days | **Enhancement for:** Observability

**Files to Modify:**
```
/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java
  → Add to handleTriggerEvent():
    - emit OpenTelemetry span
    - set attributes (event name, case ID, timestamp)
    - record exception if handling fails

/home/user/yawl/src/org/yawlfoundation/yawl/engine/observability/YAWLTracing.java
  → Add method:
    - recordTriggerEvent(String caseID, String eventName, Map<String, String> attributes)
```

**Tests to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/observability/TriggerTracingTest.java
  → testTriggerEventSpan()            // Span created
  → testTriggerAttributes()           // Attributes set correctly
  → testErrorSpanStatus()             // Failures recorded
```

---

## Integration & Verification Tasks

### Task 8: WCP Pattern Integration Tests

**Effort:** 1-2 weeks (ongoing throughout implementation)

**Files to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/WcpAdvancedPatternTest.java
  → Parent class for WCP-34 through WCP-39 tests
  → Provides pattern loading, YAML conversion, execution harness

/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp34StaticPartialJoinTest.java
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp35DynamicPartialJoinTest.java
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp36DiscriminatorCompleteTest.java
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp37LocalTriggerTest.java
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp38GlobalTriggerTest.java
/home/user/yawl/test/org/yawlfoundation/yawl/integration/pattern/Wcp39ResetTriggerTest.java

Each test:
  - Loads YAML pattern from resources
  - Converts to XML via ExtendedYamlConverter
  - Launches case via YStatelessEngine
  - Drives to completion with event injection
  - Verifies execution trace
  - Asserts state consistency
```

---

### Task 9: Performance Benchmarking

**Effort:** 1 week (after Phase 3 complete)

**Files to Create:**
```
/home/user/yawl/test/org/yawlfoundation/yawl/performance/TriggerPatternBenchmark.java
  → JMH benchmarks:
    - benchmarkDynamicThresholdEvaluation()
    - benchmarkLocalTriggerDispatch()
    - benchmarkGlobalEventBroadcast()
    - benchmarkCheckpointCreation()
    - benchmarkCheckpointRestore()

/home/user/yawl/test/org/yawlfoundation/yawl/performance/EngineCapacityTest.java
  → Load tests:
    - testConcurrentDynamicJoins(100 cases)
    - testTriggerBurst(1000 events/sec)
    - testCheckpointConcurrency(10 cases resetting)
```

---

## Documentation & Release Tasks

### Task 10: Developer Documentation

**Files to Create:**
```
/home/user/yawl/docs/patterns/TRIGGER-PATTERNS-GUIDE.md
  → Overview of trigger mechanism
  → Code examples (local, global, reset)
  → Configuration reference

/home/user/yawl/docs/patterns/DYNAMIC-PARTIAL-JOIN-GUIDE.md
  → Threshold calculation semantics
  → Static vs percentage vs variable
  → Examples

/home/user/yawl/docs/patterns/CHECKPOINT-RECOVERY-GUIDE.md
  → Checkpoint creation/restoration
  → Audit trail access
  → Best practices
```

---

## Summary: Files Changed By Phase

| Phase | New Files | Modified Files | LOC Added |
|-------|-----------|----------------|-----------|
| Phase 1 (Threshold) | 1 | 3 | ~450 |
| Phase 2 (MI Complete) | 0 | 2 | ~130 |
| Phase 3 (Local Trigger) | 3 | 3 | ~430 |
| Phase 4 (Global Broadcast) | 2 | 3 | ~360 |
| Phase 5 (Checkpoint) | 3 | 2 | ~360 |
| Phase 6-7 (Observability) | 1 | 2 | ~100 |
| **TOTAL** | **10** | **15** | **~1830** |

---

## Success Criteria: Implementation Checklist

### Build & Compile
- [ ] All new files compile without errors
- [ ] All modified files compile without errors
- [ ] No deprecation warnings introduced

### Unit Tests
- [ ] All unit tests pass (200+ tests)
- [ ] Code coverage ≥90% for new code
- [ ] No test skips or @Disabled tests

### Integration Tests
- [ ] WCP-34 static partial join test passes
- [ ] WCP-35 dynamic partial join test passes
- [ ] WCP-36 discriminator complete test passes
- [ ] WCP-37 local trigger test passes
- [ ] WCP-38 global broadcast test passes
- [ ] WCP-39 reset checkpoint test passes
- [ ] All traces verified (no missing events)

### Performance
- [ ] Dynamic threshold eval: <5ms p95
- [ ] Trigger dispatch: <10ms p95
- [ ] Global broadcast (100 cases): <50ms p95
- [ ] Checkpoint restore: <50ms p95
- [ ] No regression in non-trigger cases

### Code Quality
- [ ] HYPER_STANDARDS compliance (no TODO/FIXME/mock)
- [ ] All code reviewed (peer + architect)
- [ ] Thread safety verified (locks, atomics)
- [ ] No memory leaks (profiler verification)

### Documentation
- [ ] Javadoc complete for all public APIs
- [ ] Pattern guides written and reviewed
- [ ] Configuration reference updated
- [ ] Release notes prepared

---

## Task Dependencies

```
Task 1 (Dynamic Threshold)  ─→ Task 8 (Integration)
Task 2 (MI Complete)        ─→ Task 8
Task 3 (Local Trigger)      ─→ Task 6 (Virtualization) ─→ Task 8
Task 4 (Global Broadcast)   ─→ Task 6
Task 5 (Checkpoint)         ─→ Task 8
Task 7 (Tracing)            ─→ Task 8
Task 8 (Integration)        ─→ Task 9 (Benchmarking) ─→ Task 10 (Docs)
```

**Critical Path:** Task 1 → Task 3 → Task 4 → Task 9 (8 weeks)

---

**Report Date:** 2026-02-20
**Prepared by:** YAWL Engine Specialist
**Stakeholder Review Required:** Yes

