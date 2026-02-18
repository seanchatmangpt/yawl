# Case and Work Item Lifecycle

> Every running workflow instance (case) and every unit of work (work item) progresses through a defined set of states; understanding the full state machine — and which method in which class causes each transition — is essential for modifying engine behaviour, debugging stuck cases, or implementing new services.

## Overview: Two Parallel State Machines

The engine runs two interlocked state machines simultaneously:

1. **Case lifecycle** — managed by `YNetRunner` in `org.yawlfoundation.yawl.engine`. Tracks one instance of a workflow specification from launch to completion.
2. **Work item lifecycle** — tracked by `YWorkItem` and its `YWorkItemStatus` enum in `org.yawlfoundation.yawl.engine`. Each atomic task that needs human or service execution becomes a work item with its own status.

The two machines interlock: a case advances because work items complete; work items exist only while their enclosing case is running.

## Case Lifecycle

### Starting a Case

A case starts when a client calls `YEngine.launchCase()` with a `YSpecificationID` and optional case data. The engine creates a `YNetRunner` for the root net of the specification:

```java
// YNetRunner constructor
_caseIDForNet = new YIdentifier(caseID);   // the case token
_net = (YNet) netPrototype.clone();         // independent copy of the net
prepare(pmgr);                             // places token in input condition
```

`prepare()` puts the case's `YIdentifier` into the net's `YInputCondition`. With the token placed, the runner calls `start()` which calls `kick()`.

### The Execution Loop: kick() and continueIfPossible()

`kick()` is the primary re-entry point. It calls `continueIfPossible()` and inspects the return value:

- **Returns true**: at least one task is still active (enabled or busy). The case is live. No action taken.
- **Returns false**: no tasks are active. If this is the root net, the case has completed (or deadlocked).

`continueIfPossible()` at `YNetRunner:598` iterates every task in the net and classifies it:

- If the task is now enabled (tokens in preset conditions, not previously enabled) it is added to a `YEnabledTransitionSet`.
- If the task was enabled before but is no longer enabled, it is *withdrawn* — its work item is cancelled and removed.
- If the task is marked busy in the net but not in the runner's busy list, a `RuntimeException` is thrown (internal consistency violation).

After classification, `fireTasks()` fires the newly enabled transitions.

### Case Completion

A case completes normally when a token reaches `YOutputCondition`. `YNetRunner.endOfNetReached()` returns true when `_net.getOutputCondition().containsIdentifier()`. On the next call to `kick()`:

1. `continueIfPossible()` returns false (the output condition is terminal; no tasks will fire).
2. `kick()` detects `isRootNet()` is true (the parent identifier is null).
3. `announceCaseCompletion()` fires — notifying the case observer and any Interface X listeners.
4. `_net.postCaseDataToExternal()` writes final case data to the external data gateway if configured.
5. `YEngine.removeCaseFromCaches()` removes the case from all runtime indexes.
6. `YEventLogger.logNetCompleted()` writes the completion event to the process log.

### Suspension and Resumption

The `YNetRunner` maintains an `ExecutionStatus` enum: `Normal`, `Suspending`, `Suspended`, `Resuming`.

When a case is suspended, `setStateSuspending()` transitions to `Suspending`, then `setStateSuspended()` reaches `Suspended`. At this point `continueIfPossible()` detects `isInSuspense()` and returns true immediately without firing any tasks — freezing the case. Existing work items remain in whatever state they were in when suspension was requested.

Resumption calls `setStateResuming()` then `setStateNormal()`, followed by `kick()` to restart the execution loop. Any tasks that were enabled before suspension re-fire if their conditions still hold tokens.

### Case Cancellation

`YNetRunner.cancel()` stops a case unconditionally. It:

1. Calls `task.cancel()` on every busy task, which removes all internal condition tokens.
2. Calls `condition.removeAll()` on every condition that holds tokens.
3. Clears `_enabledTasks` and `_busyTasks`.
4. Removes the runner from `YEngine.getNetRunnerRepository()`.
5. Calls `YWorkItemRepository.removeWorkItemsForCase()` to delete all pending work items.

For sub-net runners (composite tasks), cancellation also logs `YEventLogger.logNetCancelled()` with the containing task ID.

## Work Item Lifecycle

### YWorkItemStatus Values

`org.yawlfoundation.yawl.engine.YWorkItemStatus` is an enum with 13 values. The principal ones are:

| Status | Meaning |
|--------|---------|
| `statusEnabled` | Task has tokens in all required preset conditions; work item created and announced |
| `statusFired` | Task has fired — token consumed from conditions, child identifier created; waiting for start |
| `statusExecuting` | Work item has been started by a service or user; actively being performed |
| `statusComplete` | Task completed normally |
| `statusFailed` | Task failed during execution |
| `statusForcedComplete` | Task completed via engine force-complete operation |
| `statusSuspended` | Execution temporarily paused |
| `statusCancelledByCase` | Cancelled because the enclosing case was cancelled |
| `statusDeleted` | Cancelled by a cancellation region (another task's `removeSet`) |
| `statusWithdrawn` | Cancelled because the task's enablement condition was invalidated by XOR/deferred choice |
| `statusDeadlocked` | Reported when OR-join deadlock is detected |
| `statusDiscarded` | Work item was executing when the case completed (tokens remained) |

### Enabled: Work Item Creation

When `YNetRunner.fireTasks()` encounters a newly-enabled `YAtomicTask`, it calls `fireAtomicTask()`. This:

1. Adds the task to `_enabledTasks`.
2. Calls `createEnabledWorkItem()` which constructs a `YWorkItem` with `statusEnabled`.
3. Creates a `YAnnouncement` to deliver to the appropriate YAWL service.

At this point the work item exists in `YWorkItemRepository` and the external service will receive the announcement.

### Fired: Automatic Transition

When a service calls back and accepts a work item, the engine calls `YNetRunner.attemptToFireAtomicTask()`:

```java
// YNetRunner.attemptToFireAtomicTask()
List<YIdentifier> newChildIdentifiers = task.t_fire(pmgr);
_enabledTasks.remove(task);
_busyTasks.add(task);
kick(pmgr);
return newChildIdentifiers;
```

`task.t_fire()` consumes the token from preset conditions and creates child identifiers. The work item status moves from `statusEnabled` to `statusFired`. In the common single-instance case this transition is essentially instantaneous — the same service call that fires the task immediately proceeds to start it.

### Executing: Human or Service Work Begins

Starting a work item is a separate operation. `YNetRunner.startWorkItemInTask()` calls `task.t_start()`:

```java
// YTask.t_start()
if (t_isBusy()) {
    startOne(pmgr, child);
}
```

`startOne()` (implemented differently in `YAtomicTask` vs `YCompositeTask`) moves the child identifier from `_mi_entered` to `_mi_executing`. The work item status becomes `statusExecuting`.

The distinction between `statusFired` and `statusExecuting` matters for multi-instance tasks: a task can have several fired instances where some have transitioned to executing while others are still waiting to start.

For composite tasks, `t_start()` creates a child `YNetRunner` for the sub-net. The composite task stays in `statusExecuting` (its parent task is busy) until the child net reaches its output condition.

### Complete: Task Exit

When a service finishes work, it posts completion data back to the engine. `YEngine.completeWorkItem()` routes the call to `YNetRunner.completeWorkItemInTask()`:

```java
// YNetRunner.completeWorkItemInTask()
boolean success = completeTask(pmgr, workItem, task, caseID, outputData);
```

Inside `completeTask()`, `atomicTask.t_complete()` is called. It:

1. Validates output data against the specification schema.
2. Runs XPath output mappings to update net variables.
3. Moves the identifier from `_mi_executing` to `_mi_complete`.
4. Checks `t_isExitEnabled()`: if the completion threshold is met, calls `t_exit()`.

`t_exit()` fires cancellation sets, performs final data assignments, and places tokens into postset conditions via the split routing (`doAndSplit`, `doOrSplit`, or `doXORSplit`). The work item family is removed from the repository. `continueIfPossible()` is called again to pick up any newly enabled tasks.

### Failed and ForcedComplete

`statusFailed` is set when the engine cannot process a work item's completion data — for example, when output data fails schema validation inside `YTask.t_complete()`. The item remains in the repository for inspection.

`statusForcedComplete` is used by administrative operations that bypass normal completion validation. The engine drives the task through `t_complete()` with whatever data is available, marking the item `statusForcedComplete` rather than `statusComplete`.

### Withdrawn: Deferred Choice Resolution

In a deferred choice, multiple tasks are enabled from the same condition (the condition holds one token, and multiple tasks have it in their preset). When one of these tasks fires and consumes the token, the others lose their enablement. `continueIfPossible()` detects this on its next pass and calls `withdrawEnabledTask()` for each task that is no longer enabled:

```java
// YNetRunner.withdrawEnabledTask()
_enabledTasks.remove(task);
// log and announce cancellation
YEventLogger.logWorkItemEvent(wItem, YWorkItemStatus.statusWithdrawn, null);
// cancel any live timer
// delete from persistence
```

Work items set to `statusWithdrawn` are removed from persistence. No further action can be taken on them.

### Deadlock Detection

After `continueIfPossible()` returns false, if the case has not completed and `_cancelling` is false, `deadLocked()` is called. It returns true if any net element holds tokens but its postset is non-empty — meaning there are tokens stuck in the net with no way to progress. When deadlock is detected, `notifyDeadLock()` creates `statusDeadlocked` work items (for logging and reporting) and announces the deadlock via the announcer.

## Cancellation Regions and Live Cases

When `task.t_exit()` fires, it iterates the task's `_removeSet`:

```java
// YTask.t_exit()
for (YExternalNetElement netElement : _removeSet) {
    if (netElement instanceof YTask task) {
        task.cancel(pmgr);            // sets statusCancelledByCase on executing items
    } else if (netElement instanceof YCondition cond) {
        cond.removeAll(pmgr);         // removes all tokens from this condition
    }
}
```

Any executing work item belonging to a task in the remove set gets `statusCancelledByCase`. From the service's perspective, the work item vanishes — any attempt to complete it will find no matching work item in the repository. This is intentional: the cancellation is immediate and non-negotiable.

## Suspend vs. Cancel: The Critical Difference

Suspension is reversible; cancellation is not.

- **Suspend**: `continueIfPossible()` exits early without firing tasks. All existing work items remain in their current state (`statusEnabled`, `statusFired`, `statusExecuting`). The case resumes from exactly the same state when `setStateNormal()` and `kick()` are called.
- **Cancel**: All tasks are cancelled, all conditions drained, all work items deleted. The case is irrevocably terminated. A cancelled case cannot be resumed; a new case must be launched.

## Thread Safety

`continueIfPossible()`, `completeWorkItemInTask()`, `startWorkItemInTask()`, and `cancel()` are all `synchronized` on the `YNetRunner` instance. For sub-net completion that must notify the parent runner, a `ReentrantLock` (`_runnerLock`) is used to avoid ABBA deadlock between a child runner's synchronized method and the parent runner's synchronized method:

```java
// YNetRunner.completeTask() — sub-net completion path
parentRunner._runnerLock.lock();
try {
    parentRunner.processCompletedSubnet(...);
} finally {
    parentRunner._runnerLock.unlock();
}
```

This ensures the lock acquisition order is always parent-then-child, preventing the classic deadlock where two threads each hold one lock and wait for the other.
