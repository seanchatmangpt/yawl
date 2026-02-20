# ADR-026: Sealed Class Hierarchies for YAWL Domain Model

## Status

**PROPOSED**

## Date

2026-02-20

## Context

YAWL's domain model is built on an open inheritance hierarchy defined before Java 17
sealed classes existed. Three areas cause correctness and maintainability problems:

### 1. YWorkItemStatus — flat enum, dispersed state machine

`src/org/yawlfoundation/yawl/engine/YWorkItemStatus.java` defines 13 enum constants
with no encoded knowledge of which transitions are legal. The state machine is
distributed across `YNetRunner`, `YWorkItem`, and `YEngine`, each guarding
`status == X` in separate if-chains. New status values added here require a
grep-and-update across the engine, with no compiler verification.

The 13 values currently defined:

```
statusEnabled, statusFired, statusExecuting, statusComplete,
statusIsParent, statusDeadlocked, statusDeleted, statusWithdrawn,
statusForcedComplete, statusFailed, statusSuspended,
statusCancelledByCase, statusDiscarded
```

The `_prevStatus` field on `YWorkItemEvent`
(`src/org/yawlfoundation/yawl/stateless/listener/event/YWorkItemEvent.java`)
is necessary only because the enum carries no history. Removing it is a secondary
benefit of the sealed redesign.

### 2. YElement hierarchy — open, unconstrained inheritance

`YNetElement` → `YExternalNetElement` → `YTask` / `YCondition` is open to
subclassing by any external code. Pattern-matching switches over net elements
need a `default` branch to be safe, which means the compiler cannot warn if a
new element type is added and a switch site is missed. The domain model rule
in `.claude/rules/elements/domain-model.md` explicitly calls for sealed
hierarchies here.

Concrete classes in the YAWL element hierarchy:
- `YNetElement` (abstract, base)
- `YExternalNetElement extends YNetElement` (abstract)
- `YTask extends YExternalNetElement` (abstract)
- `YAtomicTask extends YTask`
- `YCompositeTask extends YTask`
- `YCondition extends YExternalNetElement`
- `YInputCondition extends YCondition`
- `YOutputCondition extends YCondition`

### 3. YEvent hierarchy — mutable abstract class, post-construction setters

`src/org/yawlfoundation/yawl/stateless/listener/event/YEvent.java` is an abstract
class with mutable fields `_specID`, `_item`, `_dataDoc`, `_engineNbr` that are set
after construction via public setters. The concrete subclasses `YCaseEvent`,
`YWorkItemEvent`, `YTimerEvent`, `YExceptionEvent`, `YLogEvent` carry different
meaningful data but all inherit the same mutable base. The `MultiThreadEventNotifier`
(already using virtual threads) dispatches these events concurrently — mutable event
objects shared across threads are a latent data race.

## Decision

Introduce sealed class hierarchies for the three areas above across two phases.

### Phase A: WorkItemState sealed interface (replaces YWorkItemStatus enum)

Define a new sealed type that encodes state transitions as data rather than as
separate enum constants with guard logic scattered through the engine.

```java
// New file:
// src/org/yawlfoundation/yawl/engine/WorkItemState.java

package org.yawlfoundation.yawl.engine;

import java.time.Instant;

/**
 * Sealed type hierarchy for work item lifecycle states.
 *
 * <p>Replaces the flat {@link YWorkItemStatus} enum. Each permitted subtype
 * carries exactly the data relevant to that state, making illegal states
 * unrepresentable and enabling exhaustive compiler-verified pattern matching.</p>
 *
 * <p>Terminal states implement {@link TerminalState}. A suspended state
 * wraps the state from which the item was suspended, eliminating the
 * separate {@code _prevStatus} field previously required on event objects.</p>
 */
public sealed interface WorkItemState
    permits WorkItemState.EnabledState,
            WorkItemState.FiredState,
            WorkItemState.ExecutingState,
            WorkItemState.IsParentState,
            WorkItemState.SuspendedState,
            WorkItemState.TerminalState {

    /** Work item is ready; tokens are in all required preset conditions. */
    record EnabledState(Instant enabledAt) implements WorkItemState {}

    /** Work item has been claimed by a service but execution has not started. */
    record FiredState(Instant enabledAt, Instant firedAt) implements WorkItemState {}

    /** Work item is actively executing within a service or user worklist. */
    record ExecutingState(
        Instant enabledAt,
        Instant firedAt,
        Instant startedAt,
        String executingParticipant
    ) implements WorkItemState {}

    /** Multi-instance parent work item; individual child instances are in their own states. */
    record IsParentState(Instant enabledAt, int instanceCount) implements WorkItemState {}

    /**
     * Execution temporarily paused.
     * Wraps the state from which the item was suspended, removing the need for
     * a separate {@code _prevStatus} field on event objects.
     */
    record SuspendedState(WorkItemState suspendedFrom, Instant suspendedAt)
        implements WorkItemState {}

    /**
     * Sealed sub-hierarchy for all terminal states.
     * Once an item enters a terminal state it cannot transition further.
     */
    sealed interface TerminalState extends WorkItemState
        permits WorkItemState.CompleteState,
                WorkItemState.ForcedCompleteState,
                WorkItemState.FailedState,
                WorkItemState.DeadlockedState,
                WorkItemState.DeletedState,
                WorkItemState.WithdrawnState,
                WorkItemState.CancelledByCaseState,
                WorkItemState.DiscardedState {}

    record CompleteState(Instant completedAt) implements TerminalState {}
    record ForcedCompleteState(Instant completedAt) implements TerminalState {}
    record FailedState(Instant failedAt, String reason) implements TerminalState {}
    record DeadlockedState(Instant detectedAt) implements TerminalState {}
    record DeletedState(Instant deletedAt, String cancelSetId) implements TerminalState {}
    record WithdrawnState(Instant withdrawnAt) implements TerminalState {}
    record CancelledByCaseState(Instant cancelledAt) implements TerminalState {}
    record DiscardedState(Instant discardedAt) implements TerminalState {}
}
```

**Backward compatibility bridge.** `YWorkItemStatus` is retained as a deprecated
adapter that maps from the new `WorkItemState` to the existing enum string for XML
serialization and any callers that still use the enum directly. Both representations
exist until the next major version removes `YWorkItemStatus`.

```java
// In YWorkItemStatus.java — add static factory (no enum constants removed)
/** @deprecated Use {@link WorkItemState} directly. */
@Deprecated(since = "6.0", forRemoval = true)
public static YWorkItemStatus fromState(WorkItemState state) {
    return switch (state) {
        case WorkItemState.EnabledState _        -> statusEnabled;
        case WorkItemState.FiredState _          -> statusFired;
        case WorkItemState.ExecutingState _      -> statusExecuting;
        case WorkItemState.IsParentState _       -> statusIsParent;
        case WorkItemState.SuspendedState _      -> statusSuspended;
        case WorkItemState.CompleteState _       -> statusComplete;
        case WorkItemState.ForcedCompleteState _ -> statusForcedComplete;
        case WorkItemState.FailedState _         -> statusFailed;
        case WorkItemState.DeadlockedState _     -> statusDeadlocked;
        case WorkItemState.DeletedState _        -> statusDeleted;
        case WorkItemState.WithdrawnState _      -> statusWithdrawn;
        case WorkItemState.CancelledByCaseState _-> statusCancelledByCase;
        case WorkItemState.DiscardedState _      -> statusDiscarded;
    };
}
```

**Exhaustive pattern matching at dispatch sites.** Switch expressions on
`WorkItemState` require no `default` branch — the compiler verifies all cases:

```java
// Example: status label in monitoring/reporting code
String label = switch (workItem.getState()) {
    case WorkItemState.EnabledState _    -> "Waiting";
    case WorkItemState.FiredState _      -> "Fired";
    case WorkItemState.ExecutingState e  -> "Executing: " + e.executingParticipant();
    case WorkItemState.IsParentState p   -> "Parent (" + p.instanceCount() + " instances)";
    case WorkItemState.SuspendedState s  -> "Suspended from " + fromState(s.suspendedFrom());
    case WorkItemState.CompleteState _   -> "Complete";
    case WorkItemState.ForcedCompleteState _ -> "Force-Completed";
    case WorkItemState.FailedState f     -> "Failed: " + f.reason();
    case WorkItemState.DeadlockedState _ -> "Deadlocked";
    case WorkItemState.DeletedState _    -> "Deleted";
    case WorkItemState.WithdrawnState _  -> "Withdrawn";
    case WorkItemState.CancelledByCaseState _ -> "Cancelled";
    case WorkItemState.DiscardedState _  -> "Discarded";
    // Compiler error if any TerminalState subtype is missing
};
```

### Phase B: Sealed YExternalNetElement hierarchy

Restrict the open `YExternalNetElement` hierarchy by adding `sealed` to the
abstract classes and `final` or `non-sealed` to the concrete leaves.

```java
// Modified:
// src/org/yawlfoundation/yawl/elements/YExternalNetElement.java
public abstract sealed class YExternalNetElement extends YNetElement
    implements YVerifiable
    permits YTask, YCondition {}

// src/org/yawlfoundation/yawl/elements/YTask.java
public abstract sealed class YTask extends YExternalNetElement
    implements IMarkingTask
    permits YAtomicTask, YCompositeTask {}

// src/org/yawlfoundation/yawl/elements/YCondition.java
public sealed class YCondition extends YExternalNetElement
    permits YInputCondition, YOutputCondition {}

// src/org/yawlfoundation/yawl/elements/YAtomicTask.java
public final class YAtomicTask extends YTask { ... }

// src/org/yawlfoundation/yawl/elements/YCompositeTask.java
public final class YCompositeTask extends YTask { ... }

// src/org/yawlfoundation/yawl/elements/YInputCondition.java
public final class YInputCondition extends YCondition { ... }

// src/org/yawlfoundation/yawl/elements/YOutputCondition.java
public final class YOutputCondition extends YCondition { ... }
```

Engine routing code in `YNetRunner` can then use exhaustive switches:

```java
// In YNetRunner — net element processing (no default needed)
void processNetElement(YExternalNetElement element) {
    switch (element) {
        case YAtomicTask t    -> fireAtomicTask(t);
        case YCompositeTask t -> fireCompositeTask(t);
        case YInputCondition c  -> handleInputCondition(c);
        case YOutputCondition c -> handleOutputCondition(c);
        case YCondition c       -> updateConditionTokens(c);
    }
}
```

### Phase C: Sealed YEvent hierarchy (stateless engine)

Convert `YEvent` from a mutable abstract class to a sealed interface with
record implementations. All data is supplied at construction time; no setters.

```java
// New file:
// src/org/yawlfoundation/yawl/stateless/listener/event/YWorkflowEvent.java

package org.yawlfoundation.yawl.stateless.listener.event;

import java.time.Instant;
import java.util.Set;

import org.jdom2.Document;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

/**
 * Sealed event hierarchy for stateless engine lifecycle events.
 *
 * <p>Replaces the mutable {@link YEvent} abstract class. All event data is
 * supplied at construction time and is immutable, making concurrent dispatch
 * via {@code MultiThreadEventNotifier} safe without synchronization.</p>
 */
public sealed interface YWorkflowEvent
    permits YWorkflowEvent.YCaseLifecycleEvent,
            YWorkflowEvent.YWorkItemLifecycleEvent,
            YWorkflowEvent.YTimerLifecycleEvent,
            YWorkflowEvent.YConstraintEvent,
            YWorkflowEvent.YExceptionEvent,
            YWorkflowEvent.YLogEvent,
            YWorkflowEvent.YNetLifecycleEvent {

    /** Timestamp at which the event occurred. */
    Instant timestamp();

    /** The event category. */
    YEventType type();

    /** The case to which this event belongs. */
    YIdentifier caseID();

    /**
     * Case-level lifecycle events: CASE_STARTING, CASE_STARTED, CASE_COMPLETED,
     * CASE_CANCELLED, CASE_DEADLOCKED, CASE_SUSPENDING, CASE_SUSPENDED,
     * CASE_RESUMED, CASE_UNLOADED, CASE_RESTORED, CASE_IDLE_TIMEOUT,
     * CASE_START_FAILED.
     */
    record YCaseLifecycleEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YNetRunner runner,
        Set<YTask> deadlockedTasks,   // non-null only for CASE_DEADLOCKED
        Document data,                // non-null only for CASE_COMPLETED
        int engineNbr
    ) implements YWorkflowEvent {}

    /**
     * Work item lifecycle events: ITEM_ENABLED, ITEM_STARTED, ITEM_COMPLETED,
     * ITEM_CANCELLED, ITEM_STATUS_CHANGE, ITEM_ABORT,
     * ITEM_ENABLED_REANNOUNCE, ITEM_STARTED_REANNOUNCE, ITEM_DATA_VALUE_CHANGE.
     */
    record YWorkItemLifecycleEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YWorkItem workItem,
        org.yawlfoundation.yawl.engine.YWorkItemStatus previousStatus,
        Document data,
        int engineNbr
    ) implements YWorkflowEvent {}

    /**
     * Timer events: TIMER_STARTED, TIMER_EXPIRED, TIMER_CANCELLED.
     */
    record YTimerLifecycleEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        String taskID,
        int engineNbr
    ) implements YWorkflowEvent {}

    /**
     * Constraint check events: ITEM_CHECK_PRECONSTRAINTS, ITEM_CHECK_POSTCONSTRAINTS,
     * CASE_CHECK_PRECONSTRAINTS, CASE_CHECK_POSTCONSTRAINTS.
     */
    record YConstraintEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YWorkItem workItem,   // null for case-level constraint events
        int engineNbr
    ) implements YWorkflowEvent {}

    /** Exception events from worklet / compensation handling. */
    record YExceptionEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YWorkItem workItem,
        String exceptionMessage,
        int engineNbr
    ) implements YWorkflowEvent {}

    /** Logging / audit events. */
    record YLogEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YWorkItem workItem,
        Document data,
        int engineNbr
    ) implements YWorkflowEvent {}

    /** Sub-net lifecycle: NET_STARTED, NET_COMPLETED, NET_CANCELLED. */
    record YNetLifecycleEvent(
        Instant timestamp,
        YEventType type,
        YIdentifier caseID,
        YSpecificationID specID,
        YNetRunner runner,
        int engineNbr
    ) implements YWorkflowEvent {}
}
```

The `YAnnouncer` in the stateless engine dispatches using exhaustive switches:

```java
// In stateless YAnnouncer — no default branch required
void dispatch(YWorkflowEvent event) {
    switch (event) {
        case YWorkflowEvent.YCaseLifecycleEvent e     -> notifyCaseListeners(e);
        case YWorkflowEvent.YWorkItemLifecycleEvent e -> notifyWorkItemListeners(e);
        case YWorkflowEvent.YTimerLifecycleEvent e    -> notifyTimerListeners(e);
        case YWorkflowEvent.YConstraintEvent e        -> notifyConstraintListeners(e);
        case YWorkflowEvent.YExceptionEvent e         -> notifyExceptionListeners(e);
        case YWorkflowEvent.YLogEvent e               -> notifyLogListeners(e);
        case YWorkflowEvent.YNetLifecycleEvent e      -> notifyNetListeners(e);
    }
}
```

## Consequences

### Positive

- **Compiler-verified exhaustiveness.** Every switch over `WorkItemState`,
  `YExternalNetElement`, or `YWorkflowEvent` is checked at compile time. Missing
  a newly added permitted subtype produces a compile error, not a silent runtime
  bug.

- **`_prevStatus` field eliminated.** `YWorkItemEvent._prevStatus` is removed
  because `WorkItemState.SuspendedState.suspendedFrom()` carries that information
  structurally. This removes 1 mutable field from the event object.

- **Thread-safe event dispatch.** `YWorkflowEvent` record implementations are
  fully immutable. `MultiThreadEventNotifier`, which already dispatches on virtual
  threads, no longer risks data races on event fields.

- **Test construction is trivial.** Tests create events with a single constructor
  call rather than constructing a mutable object and calling multiple setters.

  ```java
  // Before
  YWorkItemEvent ev = new YWorkItemEvent(ITEM_COMPLETED, item);
  ev.setPreviousStatus(statusExecuting);
  ev.setSpecID(specID);

  // After
  YWorkflowEvent ev = new YWorkflowEvent.YWorkItemLifecycleEvent(
      Instant.now(), ITEM_COMPLETED, item.getCaseID(),
      specID, item, statusExecuting, null, 1);
  ```

- **Illegal states unrepresentable.** A `WorkItemState.ExecutingState` carries
  `startedAt` and `executingParticipant` by type; there is no way to construct
  an executing state without those fields.

### Negative and Mitigations

- **Dual representations during transition.** `YWorkItemStatus` (enum) and
  `WorkItemState` (sealed interface) coexist until the next major version.
  The `YWorkItemStatus.fromState(WorkItemState)` bridge method prevents
  regressions in XML serialisation and external callers that depend on the enum.

- **YEvent abstract class retained as deprecated.** The existing
  `YCaseEvent`, `YWorkItemEvent`, `YTimerEvent`, `YExceptionEvent`, `YLogEvent`
  classes are deprecated but not removed in 6.0. The `YAnnouncer` implementations
  accept `YWorkflowEvent` in new code paths while existing callers continue to
  compile. Removal is scheduled for 7.0 per ADR-016 deprecation policy.

- **External subclassers of YExternalNetElement.** Making the class sealed is a
  binary-incompatible change for any code outside `org.yawlfoundation.yawl.elements`
  that extends `YExternalNetElement` or `YTask`. ADR-016's 12-month deprecation
  notice applies. The approach is to compile with `--release 25` and publish
  migration guidance before sealing. If third-party extensions are discovered,
  mark those extension points `non-sealed` and document them explicitly.

## Alternatives Considered

### Alternative A: Leave enum, add state machine validator class

A `WorkItemStateMachine` class could validate transitions without changing
`YWorkItemStatus`. Rejected: the compiler still cannot verify exhaustiveness
of switch statements over the enum; the validator is called only at runtime.

### Alternative B: Record-only, no sealed hierarchy for elements

Convert only `YEvent` to records; leave `YExternalNetElement` open.
Rejected: the domain model rule explicitly requires sealed hierarchies for element
types, and pattern matching over `YExternalNetElement` already appears in engine
routing code where correctness is critical.

### Alternative C: Sealed interfaces for both stateful and stateless event paths

The stateful engine has its own event type `YEngineEvent` in
`src/org/yawlfoundation/yawl/engine/announcement/`. This ADR focuses on the
stateless hierarchy; a follow-on ADR will address `YEngineEvent` once the
stateless pattern is validated in production.

## Related ADRs

- ADR-001: Dual Engine Architecture — establishes the stateful / stateless split;
  both `YEvent` (stateless) and `YEngineEvent` (stateful) are affected.
- ADR-004: Spring Boot 3.4 + Java 25 — sealed classes and records are finalized
  in Java 17 and production-ready; no preview flag required.
- ADR-016: API Changelog and Deprecation Policy — governs the 12-month notice
  for removing `YWorkItemStatus` and `YEvent`.
- ADR-027: Records for Immutable Data (companion ADR) — record types used as
  `WorkItemState` permits and `YWorkflowEvent` permits are governed there.

## Files Affected

| File | Change |
|------|--------|
| `src/org/yawlfoundation/yawl/engine/YWorkItemStatus.java` | Add deprecated `fromState()` adapter method |
| `src/org/yawlfoundation/yawl/engine/WorkItemState.java` | **New file** — sealed interface + nested records |
| `src/org/yawlfoundation/yawl/engine/YWorkItem.java` | Add `WorkItemState getState()` alongside existing `getStatus()` |
| `src/org/yawlfoundation/yawl/elements/YExternalNetElement.java` | Add `sealed` + `permits YTask, YCondition` |
| `src/org/yawlfoundation/yawl/elements/YTask.java` | Add `sealed` + `permits YAtomicTask, YCompositeTask` |
| `src/org/yawlfoundation/yawl/elements/YAtomicTask.java` | Add `final` |
| `src/org/yawlfoundation/yawl/elements/YCompositeTask.java` | Add `final` |
| `src/org/yawlfoundation/yawl/elements/YCondition.java` | Add `sealed` + `permits YInputCondition, YOutputCondition` |
| `src/org/yawlfoundation/yawl/elements/YInputCondition.java` | Add `final` |
| `src/org/yawlfoundation/yawl/elements/YOutputCondition.java` | Add `final` |
| `src/org/yawlfoundation/yawl/stateless/listener/event/YEvent.java` | `@Deprecated(since="6.0", forRemoval=true)` |
| `src/org/yawlfoundation/yawl/stateless/listener/event/YWorkflowEvent.java` | **New file** — sealed interface + nested records |

## Implementation Sequence

1. **Phase A** (WorkItemState) — no existing class structure changes; new type alongside enum.
   Effort: 2 days.
2. **Phase B** (YExternalNetElement sealed) — requires checking all external subclassers
   via `javac --release 25` compilation of dependent modules.
   Effort: 1 day.
3. **Phase C** (YWorkflowEvent records) — requires updating `YAnnouncer` dispatch and
   all listener registration sites in the stateless engine.
   Effort: 2 days.

Total estimated effort: **5 days**. Each phase is a separate commit per ADR-016 policy.
