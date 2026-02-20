# WCP-19 to WCP-24: Detailed Test Scenario Recommendations

## Overview
This document provides concrete test scenario specifications for comprehensive validation of cancellation and state-based workflow patterns.

---

## WCP-19: Milestone Pattern Testing

### Scenario 1.1: Preventative Milestone (Normal Path)
```
Specification:
  Variables: milestoneReached = false
  Tasks:
    - StartProcess (AND split) -> [ParallelWork, MilestoneTask]
    - ParallelWork -> MilestoneReached
    - MilestoneTask -> WaitForMilestone
    - MilestoneReached (sets milestoneReached=true) -> WaitForMilestone
    - WaitForMilestone (milestone: milestoneReached) -> Continue
    - Continue -> end

Execution Trace:
  1. StartProcess begins
  2. ParallelWork and MilestoneTask start in parallel
  3. ParallelWork completes -> MilestoneReached fires
  4. MilestoneReached sets milestoneReached=true
  5. WaitForMilestone now sees milestone true
  6. WaitForMilestone enables and completes
  7. Continue executes
  8. Case completes

Expected Result:
  - Trace: [StartProcess, ParallelWork, MilestoneTask, MilestoneReached, WaitForMilestone, Continue]
  - No deadlock
  - Case completes normally
  - Items started = items completed

Assertion Points:
  assertEquals("MilestoneReached", trace.get(3));
  assertTrue(trace.indexOf("MilestoneReached") < trace.indexOf("WaitForMilestone"));
```

### Scenario 1.2: Delayed Milestone (Oscillation Risk)
```
Specification:
  Variables: ready = false, processCount = 0
  Tasks:
    - Start -> Worker
    - Worker (processCount++, milestone: ready) -> SetReady
    - SetReady (ready=true) -> CheckAgain
    - CheckAgain -> end

Execution Trace:
  1. Start completes
  2. Worker enabled, processCount=0, ready=false -> WAITING
  3. External agent sets ready=true
  4. Worker now sees milestone=true -> can complete
  5. Worker completes, processCount=1
  6. SetReady executes (sets ready=true again)
  7. CheckAgain executes
  8. Case completes

Expected Result:
  - processCount = 1 (executed once despite ready becoming true)
  - No infinite loop if ready oscillates

Assertion Points:
  assertTrue(completedItems.contains("Worker"));
  assertTrue(variables.get("processCount") == 1);
```

### Scenario 1.3: Multiple Tasks on Same Milestone
```
Specification:
  Variables: milestoneReached = false
  Tasks:
    - Start -> [Task1, Task2, Task3, ReachMilestone]
    - Task1 (milestone: milestoneReached) -> end
    - Task2 (milestone: milestoneReached) -> end
    - Task3 (milestone: milestoneReached) -> end
    - ReachMilestone (sets milestoneReached=true) -> end

Execution:
  1. Start completes
  2. Four tasks become enabled: Task1, Task2, Task3, ReachMilestone
  3. ReachMilestone executes, sets milestoneReached=true
  4. Task1, Task2, Task3 see milestone=true -> all execute
  5. All complete
  6. Case ends

Expected Result:
  - Trace contains Task1, Task2, Task3 all after ReachMilestone
  - All complete (no deadlock)
  - Order of Task1/Task2/Task3 may vary (parallel)

Assertion Points:
  assertTrue(trace.contains("Task1"));
  assertTrue(trace.contains("Task2"));
  assertTrue(trace.contains("Task3"));
  assertTrue(trace.indexOf("ReachMilestone") < trace.indexOf("Task1"));
  assertEquals(3, completedItems.stream().filter(t -> t.startsWith("Task")).count());
```

### Scenario 1.4: Complex XPath Milestone Condition
```
Specification:
  Variables:
    - status: "pending"
    - approvalCount: 0
    - budget: 10000
  Tasks:
    - Start -> [Approve, ProcessPayment]
    - Approve (increments approvalCount) -> end
    - ProcessPayment
      (milestone: status=="approved" AND approvalCount>=2 AND budget>=amount)
      -> end

Execution:
  1. Start completes
  2. ProcessPayment blocked (status != "approved")
  3. Approve executes, approvalCount=1, still blocked
  4. Approve executes again, approvalCount=2
  5. Need status change to "approved"
  6. External update: status="approved"
  7. ProcessPayment now sees milestone=true
  8. ProcessPayment executes
  9. Case completes

Expected Result:
  - Milestone condition is conjunction (AND)
  - ProcessPayment doesn't execute until ALL conditions true
  - Works with complex XPath expressions

Assertion Points:
  assertTrue(variables.get("approvalCount") >= 2);
  assertEquals("approved", variables.get("status"));
  assertTrue(completedItems.contains("ProcessPayment"));
```

---

## WCP-20: Cancel Activity Pattern Testing

### Scenario 2.1: Preventative Cancellation
```
Specification:
  Tasks:
    - Start -> [DoWork, MonitorCancel]
    - DoWork -> WorkComplete
    - MonitorCancel (condition: cancelRequested) -> CancelWork
    - CancelWork (cancels: DoWork) -> end
    - WorkComplete -> end

Execution:
  1. Start completes
  2. DoWork and MonitorCancel both enabled
  3. MonitorCancel sees cancelRequested=false initially
  4. MonitorCancel routes to default path (not executed here for preventative)
  5. External agent sets cancelRequested=true
  6. CancelWork task fires
  7. CancelWork cancels DoWork before it executes
  8. DoWork is removed from enabled queue
  9. Case continues to completion

Expected Result:
  - DoWork never starts
  - Trace doesn't contain "DoWork"
  - CancelWork completes, case ends

Assertion Points:
  assertFalse(completedItems.contains("DoWork"));
  assertTrue(completedItems.contains("CancelWork"));
  assertEquals(0, workItemsInState("DoWork", ENABLED));
```

### Scenario 2.2: Preemptive Cancellation
```
Specification:
  Tasks:
    - Start -> [DoWork, MonitorCancel]
    - DoWork (sleep 1000ms) -> end
    - MonitorCancel (delay 500ms, then cancel) -> CancelWork
    - CancelWork (cancels: DoWork) -> end

Execution:
  1. Start completes
  2. DoWork starts (EXECUTING state)
  3. MonitorCancel waits 500ms
  4. MonitorCancel fires at 500ms
  5. CancelWork executes, sends cancellation to DoWork
  6. DoWork receives cancellation signal
  7. DoWork's execution is interrupted
  8. DoWork moves to CANCELLED state
  9. CancelWork completes
  10. Case ends

Expected Result:
  - DoWork started but not completed
  - Trace shows DoWork started and then cancelled
  - No exception thrown
  - Case completes normally
  - Total time ~500ms (not 1000ms)

Assertion Points:
  assertTrue(trace.contains("DoWork"));
  assertEquals(CANCELLED, workItemState("DoWork"));
  assertTrue(elapsedTime < 700); // 500 + margin
```

### Scenario 2.3: Post-Completion Cancellation (Noop)
```
Specification:
  Tasks:
    - Start -> [DoWork, MonitorCancel]
    - DoWork (delay 100ms) -> end
    - MonitorCancel (delay 200ms, then cancel) -> CancelWork
    - CancelWork (cancels: DoWork) -> end

Execution:
  1. Start completes
  2. DoWork starts at T=0, completes at T=100
  3. MonitorCancel fires at T=200
  4. CancelWork tries to cancel DoWork
  5. DoWork already completed (not EXECUTING, not ENABLED)
  6. Cancellation is noop (no effect)
  7. CancelWork completes
  8. Case ends

Expected Result:
  - DoWork completes normally
  - Cancellation has no effect (already done)
  - No exception
  - Case completes with both paths
  - Trace: [Start, DoWork, MonitorCancel, CancelWork]

Assertion Points:
  assertTrue(completedItems.contains("DoWork"));
  assertTrue(completedItems.contains("CancelWork"));
  assertEquals(COMPLETED, workItemState("DoWork"));
```

---

## WCP-21: Cancel Case Pattern Testing

### Scenario 3.1: Case-Level Cancellation (Mid-Flight)
```
Specification:
  Tasks:
    - StartCase -> [TaskA, TaskB, CancelMonitor]
    - TaskA -> TaskC
    - TaskB -> TaskC
    - CancelMonitor (condition: caseCancelled) -> HandleCancel
    - TaskC -> end
    - HandleCancel (cancels: TaskA, TaskB, TaskC) -> end

Execution:
  1. StartCase completes
  2. TaskA, TaskB, CancelMonitor all enabled
  3. TaskA starts (EXECUTING)
  4. CancelMonitor sets caseCancelled=true
  5. HandleCancel fires
  6. HandleCancel cancels all: TaskA, TaskB, TaskC
  7. TaskA execution is interrupted
  8. TaskB is removed from ENABLED queue
  9. TaskC never becomes enabled
  10. Case terminates

Expected Result:
  - All specified tasks are cancelled
  - No downstream tasks execute
  - Case completes (EndHandler task completes)
  - Trace: [StartCase, TaskA, CancelMonitor, HandleCancel]

Assertion Points:
  assertTrue(completedItems.contains("HandleCancel"));
  assertFalse(completedItems.contains("TaskC"));
  assertEquals(3, cancelledItems.size()); // TaskA, TaskB, TaskC
```

### Scenario 3.2: Cascading Cancellation Notification
```
Specification:
  Tasks:
    - StartCase -> [ProcessA, ProcessB, CancelMonitor]
    - ProcessA -> MergeResults
    - ProcessB -> MergeResults
    - CancelMonitor (condition: cancelCaseRequested) -> CancellationHandler
    - CancellationHandler (cancels: ProcessA, ProcessB, MergeResults) -> CaseCancelled
    - MergeResults -> CaseComplete
    - CaseCancelled -> end
    - CaseComplete -> end

Execution:
  1. StartCase completes
  2. ProcessA and ProcessB start
  3. CancelMonitor sees cancelCaseRequested=true
  4. CancellationHandler fires
  5. Cancellation sent to: ProcessA, ProcessB, MergeResults
  6. ProcessA in-progress task cancelled
  7. ProcessB enabled task cancelled
  8. MergeResults not yet enabled, removed from queue
  9. CancellationHandler proceeds to CaseCancelled
  10. CaseCancelled completes, case ends

Expected Result:
  - All cancelled items reported via event listener
  - CaseCancellation event fired with case ID
  - Proper cleanup of all affected tasks
  - Case ends via CaseCancelled path

Assertion Points:
  assertTrue(caseCancellationEvents.size() > 0);
  assertTrue(cancelledItems.contains("ProcessA"));
  assertTrue(cancelledItems.contains("ProcessB"));
  assertFalse(completedItems.contains("CaseComplete"));
  assertTrue(completedItems.contains("CaseCancelled"));
```

---

## WCP-22: Cancel Region Pattern Testing

### Scenario 4.1: Cancel Region (Selective Cancellation)
```
Specification:
  Tasks:
    - StartTask -> [CheckCondition, CancelRegion, Proceed]
    - CheckCondition (condition: shouldCancel) -> [CancelRegion, Proceed]
    - CancelRegion (region: [TaskA, TaskB, TaskC]) -> HandleCancel
    - TaskA -> RegionComplete
    - TaskB -> RegionComplete
    - TaskC -> RegionComplete
    - RegionComplete (AND-join) -> end
    - Proceed -> end
    - HandleCancel -> end

Execution (Cancel Path):
  1. StartTask completes
  2. CheckCondition evaluates shouldCancel=true
  3. CancelRegion path taken
  4. CancelRegion fires (region cancellation signal)
  5. TaskA, TaskB, TaskC are cancelled
  6. RegionComplete AND-join sees no inputs (all cancelled)
  7. HandleCancel executes
  8. Case ends via HandleCancel

Execution (No Cancel Path):
  1. StartTask completes
  2. CheckCondition evaluates shouldCancel=false
  3. Proceed path taken
  4. Proceed completes, case ends

Expected Result (Cancel):
  - Only TaskA, TaskB, TaskC in region cancelled
  - Proceed not affected
  - HandleCancel completes
  - RegionComplete never completes (no inputs from region)

Assertion Points:
  assertTrue(cancelledItems.contains("TaskA"));
  assertTrue(cancelledItems.contains("TaskB"));
  assertTrue(cancelledItems.contains("TaskC"));
  assertFalse(cancelledItems.contains("Proceed"));
  assertTrue(completedItems.contains("HandleCancel"));
```

### Scenario 4.2: Nested Cancel Regions
```
Specification:
  Tasks:
    - Start -> OuterRegionControl
    - OuterRegionControl
      (region: [OuterTaskA, InnerRegionControl, OuterTaskB])
      -> CancelOuterRegion
    - OuterTaskA -> OuterTaskB
    - InnerRegionControl (region: [InnerTaskA, InnerTaskB]) -> end
    - OuterTaskB -> end
    - CancelOuterRegion -> end

Execution:
  1. Start completes
  2. OuterRegionControl fires
  3. Cancels outer region: OuterTaskA, InnerRegionControl, OuterTaskB
  4. Cancellation propagates to inner region
  5. InnerTaskA, InnerTaskB also cancelled
  6. CancelOuterRegion completes
  7. Case ends

Expected Result:
  - Both outer and inner region tasks cancelled
  - Nested cancellation properly handled
  - No partial cancellation

Assertion Points:
  assertEquals(6, cancelledItems.size()); // 3 outer + 2 inner + trigger
  assertTrue(cancelledItems.contains("InnerTaskA"));
  assertTrue(cancelledItems.contains("InnerTaskB"));
```

---

## WCP-23: Cancel Multiple Instances Pattern Testing

### Scenario 5.1: Cancel Before Instances Created
```
Specification:
  Variables: itemCount=5, shouldCancel=true
  Tasks:
    - StartTask -> ProcessItems
    - ProcessItems (MI: min=1, max=5, dynamic) -> CheckCancel
    - CheckCancel (condition: shouldCancel) -> [CancelAllInstances, AllComplete]
    - CancelAllInstances (cancelMI: ProcessItems) -> HandleCancellation
    - AllComplete -> end
    - HandleCancellation -> end

Execution:
  1. StartTask completes
  2. ProcessItems begins creating instances
  3. CheckCancel evaluates shouldCancel=true
  4. CancelAllInstances fires immediately (before threshold reached)
  5. All ProcessItems instances cancelled
  6. HandleCancellation executes
  7. Case ends

Expected Result:
  - No instances started or only first few
  - Cancellation prevents creation of remaining instances
  - Trace: [StartTask, (few ProcessItems instances?), CheckCancel, CancelAllInstances, HandleCancellation]

Assertion Points:
  assertTrue(cancelledInstanceCount >= 3); // At least threshold worth
  assertTrue(completedInstanceCount < 5); // Not all completed
  assertTrue(completedItems.contains("HandleCancellation"));
```

### Scenario 5.2: Cancel After Threshold Met
```
Specification:
  Variables: itemCount=5, threshold=3
  Tasks:
    - StartTask -> ProcessItems
    - ProcessItems (MI: min=1, max=5, threshold=3, dynamic) -> CheckCancel
    - CheckCancel -> CancelAllInstances
    - CancelAllInstances (cancelMI: ProcessItems) -> HandleCancellation
    - HandleCancellation -> end

Execution:
  1. StartTask completes
  2. ProcessItems starts creating instances
  3. Instance 1 completes -> count=1
  4. Instance 2 completes -> count=2
  5. Instance 3 completes -> count=3 (threshold met!)
  6. CheckCancel enabled (due to threshold)
  7. CheckCancel fires -> CancelAllInstances
  8. CancelAllInstances cancels remaining instances (4, 5)
  9. HandleCancellation completes
  10. Case ends

Expected Result:
  - Exactly 3 instances completed
  - Instances 4 and 5 cancelled
  - Case completes normally
  - Time: 3x instance time (not 5x)

Assertion Points:
  assertEquals(3, completedInstanceCount);
  assertEquals(2, cancelledInstanceCount);
  assertTrue(elapsedTime < 4 * averageInstanceTime);
```

### Scenario 5.3: Partial Instance Cancellation
```
Specification:
  Variables: itemCount=5, cancelPartial=true
  Tasks:
    - StartTask -> ProcessItems
    - ProcessItems (MI: min=1, max=5, dynamic) -> MonitorCancel
    - MonitorCancel -> CancelSomeInstances
    - CancelSomeInstances (cancelMI: ProcessItems) -> HandlePartial
    - HandlePartial -> end

Execution:
  1. StartTask completes
  2. ProcessItems creates instance 1 -> executing
  3. ProcessItems creates instance 2 -> executing
  4. Instance 1 completes
  5. MonitorCancel fires (at threshold or timeout)
  6. CancelSomeInstances executes
  7. Remaining instances (2, 3, 4, 5) are cancelled
  8. Instance 1 already completed (not affected)
  9. HandlePartial completes
  10. Case ends

Expected Result:
  - Instance 1 completed normally
  - Instances 2-5 cancelled
  - Total completedInstanceCount = 1
  - No error

Assertion Points:
  assertEquals(1, completedInstanceCount);
  assertEquals(4, cancelledInstanceCount);
```

---

## WCP-25: Cancel and Complete Multiple Instances Pattern Testing

### Scenario 6.1: Cancel vs Complete Dual Operations
```
Specification:
  Variables: action="process"
  Tasks:
    - StartTask -> ProcessItems
    - ProcessItems (MI: dynamic, max=10) -> DetermineAction
    - DetermineAction (action=="cancel" -> CancelInstances; else -> CompleteInstances)
    - CancelInstances (cancelMI: ProcessItems) -> HandleResult
    - CompleteInstances (completeMI: ProcessItems) -> HandleResult
    - HandleResult -> end

Execution Path A (Cancel):
  1. ProcessItems creates 3 instances, 2 complete
  2. DetermineAction sees action="cancel"
  3. CancelInstances fires
  4. Remaining instances cancelled
  5. HandleResult processes 2 completions + 2 cancellations
  6. Case ends

Execution Path B (Complete):
  1. ProcessItems creates 3 instances, 1 complete
  2. DetermineAction sees action="complete"
  3. CompleteInstances fires (force-completes all remaining)
  4. All instances move to COMPLETED state
  5. HandleResult processes all 3 as completed
  6. Case ends

Expected Result:
  - Cancel path: mix of completions and cancellations
  - Complete path: all instances marked completed
  - Both paths reach HandleResult
  - Different instance count semantics

Assertion Points:
  // Cancel path
  assertEquals(2, completedInstanceCount);
  assertEquals(2, cancelledInstanceCount);
  
  // Complete path
  assertEquals(3, completedInstanceCount);
  assertEquals(0, cancelledInstanceCount);
```

---

## Cross-Pattern Testing Scenarios

### Scenario 7.1: Milestone with Cancellation Interaction
```
Spec combines WCP-19 and WCP-20:
  - Start -> [ProcessData, CancelMonitor, WaitMilestone]
  - ProcessData (sets milestone=true) -> end
  - CancelMonitor (may cancel ProcessData) -> end
  - WaitMilestone (milestone: milestoneReached) -> end

Timing Test 1: ProcessData completes before cancellation
  - ProcessData sets milestone=true
  - WaitMilestone enables
  - Cancellation has no effect (already complete)
  - Result: All complete normally

Timing Test 2: Cancellation before ProcessData completes
  - CancelMonitor fires, cancels ProcessData
  - ProcessData never sets milestone=true
  - WaitMilestone waits forever (deadlock!)
  - Result: Case hangs or times out

Expected Result:
  - First timing: normal completion
  - Second timing: recognizes deadlock, raises exception or timeout
  
Assertion Points:
  // Test 1
  assertTrue(completedItems.contains("WaitMilestone"));
  
  // Test 2
  assertTrue(elapsedTime > TIMEOUT || caseState == DEADLOCKED);
```

---

## Performance and Stress Scenarios

### Scenario 8.1: Cascading Cancellations (5 Tasks Deep)
```
Spec:
  - CancelA (cancels B) -> end
  - B (cancels C) -> end
  - C (cancels D) -> end
  - D (cancels E) -> end
  - E -> end

Execution:
  1. Trigger CancelA
  2. CancelA fires, cancels B
  3. B cancellation propagates to C (B cancels C)
  4. C cancellation propagates to D
  5. D cancellation propagates to E
  6. All 5 tasks cancelled transitively

Expected Result:
  - All 5 tasks show CANCELLED state
  - No memory leaks in cancellation queue
  - Latency < 50ms for entire cascade
  - No stack overflow

Assertion Points:
  assertEquals(5, cancelledItems.size());
  assertTrue(elapsedTime < 50);
```

### Scenario 8.2: 100 Concurrent Cancellations
```
Spec:
  - 100 independent task cancellation tasks running in parallel
  - Each cancels a unique target task
  - All targets are ENABLED (not yet executing)

Execution:
  1. Start fires 100 parallel cancellation tasks
  2. Each immediately cancels its target
  3. All complete concurrently

Expected Result:
  - All 100 targets cancelled
  - No deadlock or race condition
  - Throughput: > 1000 cancellations/sec
  - Memory usage: stable before/after GC

Assertion Points:
  assertEquals(100, cancelledItems.size());
  assertTrue(elapsedTime < 100); // milliseconds
```

---

## Error Scenario Testing

### Scenario 9.1: Cancel Non-Existent Task
```
Execution:
  - CancelTask attempts to cancel non-existent task ID "FakeTask"
  
Expected Result (Option A - Silent Noop):
  - Exception not raised
  - Cancellation has no effect
  - Case continues normally
  
Expected Result (Option B - Explicit Exception):
  - InvalidWorkItemException raised
  - Case may or may not continue (depends on error handling)
  
Assertion Points:
  try {
    engine.cancelWorkItem("FakeTask");
    // Option A: arrives here
    assertTrue(true);
  } catch (InvalidWorkItemException e) {
    // Option B: arrives here
    assertTrue(true);
  }
```

### Scenario 9.2: Circular Cancellation (A ↔ B)
```
Spec:
  - TaskA (cancels B) -> end
  - TaskB (cancels A) -> end

Execution:
  1. Start fires TaskA and TaskB
  2. TaskA starts, cancels B
  3. TaskB sees it's being cancelled
  4. TaskB's cancellation of A is processed
  5. Circular dependency?

Expected Result:
  - Engine detects cycle
  - One cancellation wins (deterministic order)
  - No deadlock, case completes
  - Both tasks end up cancelled

Assertion Points:
  assertTrue(cancelledItems.contains("TaskA") && cancelledItems.contains("TaskB") ||
             completedItems.contains("TaskA") && completedItems.contains("TaskB"));
```

---

**End of Test Scenario Recommendations**

All scenarios above are:
- Real-world based on YAWL semantics
- Executable with Chicago TDD principles (real engine, real objects)
- Measurable with clear assertions
- Ready for implementation in JUnit 5 test classes

