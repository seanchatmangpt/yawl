# ADR-026: Sealed Classes for Domain Hierarchies

## Status

**ACCEPTED**

## Date

2026-02-20

## Context

YAWL's domain model uses open inheritance hierarchies for entities like `YElement`, `YWorkItemStatus`, and `YEvent`. Without sealed class restrictions:

1. **No compiler verification**: Subclasses can appear anywhere in codebase
2. **Pattern matching incomplete**: Switch statements over supertype may miss cases
3. **Unexpected implementations**: Refactoring is risky; unknown subclasses may break
4. **Testing complexity**: Hard to mock; must account for unknown subtypes

Java 25 provides sealed classes (JEP 409), allowing explicit restriction of permitted subtypes with compiler-enforced exhaustiveness in pattern matching.

### Example Problem

```java
// Current: No way to know all subclasses of YElement
public abstract class YElement {
    abstract String getName();
}

// Somewhere: Unexpected subclass
public class CustomElement extends YElement {
    // ...
}

// Refactoring is risky: Did we miss a subclass?
String description = element switch {
    YTask t -> t.getName(),
    YFlow f -> f.getLabel(),
    // Missing CustomElement - no compiler error!
    default -> "Unknown"
};
```

## Decision

**YAWL v6.0.0 adopts sealed classes for all domain hierarchies with bounded, compiler-verified subtypes.**

### 1. Element Hierarchy

```java
// org.yawlfoundation.yawl.elements.core.YElement
public sealed interface YElement
    permits YTask, YFlow, YCondition, YDecomposition,
            YMultiInstanceTask, YSplitJoin, YTimerTask {
    String getID();
    String getName();
}

// Concrete implementations
public non-sealed record YTask(
    String id,
    String name,
    String documentation,
    YDecomposition decomposition
) implements YElement {}

public non-sealed record YFlow(
    String id,
    YElement source,
    YElement target,
    XPathExpression predicate
) implements YElement {}

public non-sealed record YCondition(
    String id,
    String name,
    boolean isImplicit
) implements YElement {}

// Sealed subtypes for decompositions
public sealed interface YDecomposition
    permits YNetDecomposition, YWebServiceDecomposition,
            YSubnetDecomposition {}

public non-sealed record YNetDecomposition(
    String id,
    YNet net
) implements YDecomposition {}
```

**Compiler Benefit**: Switch over YElement must cover all cases.

```java
String describe = element switch {
    YTask t -> "Task: " + t.name(),
    YFlow f -> "Flow: " + f.source().getID() + "->" + f.target().getID(),
    YCondition c -> "Condition: " + c.name(),
    YDecomposition d -> "Decomposition",
    YMultiInstanceTask m -> "MultiInstance",
    YSplitJoin s -> "SplitJoin",
    YTimerTask t -> "Timer",
    // Compiler error if any case missing!
};
```

---

### 2. Work Item State Machine

**File**: `org.yawlfoundation.yawl.engine.YWorkItem`

```java
// Sealed state hierarchy
public sealed interface WorkItemState
    permits EnabledState, FiredState, ExecutingState,
            SuspendedState, CompleteState, FailedState,
            DeadlockedState, CancelledState {}

// Enabled: Waiting for start
public record EnabledState(
    Instant enabledAt,
    @Nullable String enabledBy,
    Set<String> enabledParticipants
) implements WorkItemState {}

// Fired: Ready to execute (post-fire, pre-execute)
public record FiredState(
    Instant enabledAt,
    Instant firedAt,
    @Nullable String firedBy
) implements WorkItemState {}

// Executing: Currently running
public record ExecutingState(
    Instant enabledAt,
    Instant firedAt,
    Instant startedAt,
    String executingParticipant,
    @Nullable String codeletId
) implements WorkItemState {}

// Suspended: On hold by work item
public record SuspendedState(
    WorkItemState suspendedFrom,
    Instant suspendedAt,
    String suspensionCode
) implements WorkItemState {}

// Terminal states (sealed subtree)
public sealed interface TerminalState extends WorkItemState
    permits CompleteState, FailedState, DeadlockedState, CancelledState {}

public record CompleteState(
    Instant completedAt,
    String completionType  // Normal, Forced, Compensated
) implements TerminalState {}

public record FailedState(
    Instant failedAt,
    String failureReason,
    @Nullable YWorkItemException exception
) implements TerminalState {}

public record DeadlockedState(
    Instant deadlockedAt
) implements TerminalState {}

public record CancelledState(
    Instant cancelledAt,
    String cancellationReason
) implements TerminalState {}
```

**State Transition Guard**:

```java
public void transitionTo(WorkItemState newState) throws YStateException {
    // Exhaustive validation by sealed type
    boolean isValidTransition = (currentState, newState) match {
        case (EnabledState _, FiredState _) -> true,
        case (FiredState _, ExecutingState _) -> true,
        case (ExecutingState _, CompleteState _) -> true,
        case (ExecutingState _, SuspendedState _) -> true,
        case (SuspendedState s, ExecutingState _) when s.suspendedFrom() instanceof ExecutingState -> true,
        case (_, TerminalState _) -> true,  // Any -> Terminal is valid
        case _ -> false
    };

    if (!isValidTransition) {
        throw new YStateException(
            "Cannot transition from " + currentState + " to " + newState
        );
    }
    this.currentState = newState;
}
```

---

### 3. Event Hierarchy

**File**: `org.yawlfoundation.yawl.stateless.listener.event`

```java
// Root sealed interface
public sealed interface YWorkflowEvent
    permits YCaseLifecycleEvent, YWorkItemLifecycleEvent,
            YTimerEvent, YConstraintEvent, YExceptionEvent,
            YAgentServiceEvent {}

// Case-level events
public record YCaseLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    int engineNbr,
    @Nullable String parentCaseID
) implements YWorkflowEvent {}

// Work item events
public record YWorkItemLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    YWorkItem workItem,
    @Nullable Document data,
    int engineNbr
) implements YWorkflowEvent {}

// Timer/constraint events
public record YTimerEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    String taskID,
    int engineNbr,
    long timerMilliseconds
) implements YWorkflowEvent {}

public record YConstraintEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    String constraintID,
    ConstraintViolation violation
) implements YWorkflowEvent {}

public record YExceptionEvent(
    Instant timestamp,
    YIdentifier caseID,
    YException exception,
    ExceptionLevel level
) implements YWorkflowEvent {}

public record YAgentServiceEvent(
    Instant timestamp,
    String agentID,
    AgentEventType eventType,
    @Nullable Object metadata
) implements YWorkflowEvent {}
```

**Exhaustive Event Dispatch**:

```java
public void dispatch(YWorkflowEvent event) {
    switch (event) {
        case YCaseLifecycleEvent e -> handleCaseEvent(e);
        case YWorkItemLifecycleEvent e -> handleWorkItemEvent(e);
        case YTimerEvent e -> handleTimerEvent(e);
        case YConstraintEvent e -> handleConstraintEvent(e);
        case YExceptionEvent e -> handleExceptionEvent(e);
        case YAgentServiceEvent e -> handleAgentServiceEvent(e);
        // Compiler error if any event type missing!
    }
}
```

---

### 4. Exception Hierarchy

**File**: `org.yawlfoundation.yawl.core.exceptions`

```java
public sealed abstract class YException extends Exception
    permits YStateException, YDataStateException,
            YPersistenceException, YAuditException,
            YAgentException, YIntegrationException {}

public final class YStateException extends YException {
    public YStateException(String message) { super(message); }
}

public final class YDataStateException extends YException {
    public YDataStateException(String message) { super(message); }
}

public final class YPersistenceException extends YException {
    public YPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

public final class YAgentException extends YException {
    private final String agentID;
    public YAgentException(String agentID, String message) {
        super(message);
        this.agentID = agentID;
    }
    public String agentID() { return agentID; }
}

public final class YIntegrationException extends YException {
    public YIntegrationException(String message) { super(message); }
}
```

---

## Consequences

### Positive

1. **Compiler Safety**: Switch statements must cover all cases; refactoring is safer
2. **Explicit Design**: Sealed types document the intended hierarchy
3. **Pattern Matching**: Record patterns enable powerful destructuring
4. **Reduced Bugs**: No surprise subclasses breaking assumptions
5. **Performance**: JVM can optimize sealed type hierarchies (inlining, devirtualization)

### Negative

1. **Inflexibility**: Adding new subtypes requires source code change
2. **Learning Curve**: Sealed classes are new in Java; team training needed
3. **Backward Compatibility**: Third-party plugins cannot extend sealed types
   - Mitigation: Document extension points; use non-sealed intermediate classes

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Plugin incompatibility | MEDIUM | HIGH | Provide extension points, document breaking change |
| Type hierarchy incorrect | MEDIUM | MEDIUM | Extensive testing, review hierarchy design |
| Over-sealing | LOW | MEDIUM | Start with open hierarchies; seal only proven designs |

---

## Implementation

### 1. Convert YElement Hierarchy (2 days)

- [ ] Create `YElement` sealed interface
- [ ] Implement all subtypes as records
- [ ] Update factory methods
- [ ] Update tests
- [ ] Update documentation

### 2. Convert YWorkItem State Machine (2 days)

- [ ] Design state hierarchy
- [ ] Implement sealed interface + records
- [ ] Add transition guard logic
- [ ] Update YNetRunner transitions
- [ ] Update tests

### 3. Convert Event Hierarchy (2 days)

- [ ] Create YWorkflowEvent sealed interface
- [ ] Implement event records
- [ ] Update listeners to use switch
- [ ] Update tests

### 4. Update Persistence (2 days)

- [ ] Update Hibernate entity mappings for records
- [ ] Add JSON serialization support
- [ ] Test round-trip persistence

---

## Alternatives Considered

### Alternative 1: Keep Open Hierarchies

**Rejected.** Loses compiler safety and pattern matching benefits.

### Alternative 2: Use Abstract Base Class (Sealed)

```java
public sealed abstract class YElement
    permits YTask, YFlow, ... {}
```

**Acceptable**, but records are more concise and auto-generate boilerplate.

### Alternative 3: Interface-Only Design

```java
public sealed interface YElement permits ... {}
public record YTask(...) implements YElement {}
```

**Chosen.** Cleaner, more flexible, better with records.

---

## Related ADRs

- **ADR-027**: Records for Immutable Data
- **ADR-028**: Virtual Threads Strategy
- **ARCHITECTURE-PATTERNS-JAVA25.md**: Pattern 3 (DDD Alignment)

---

## References

- JEP 409: Sealed Classes (finalized in Java 17, Java 25)
- JEP 440: Records (finalized in Java 16, Java 25)
- Pattern Matching: JEP 394, 397, 406
- "Sealed Classes for Domain Modeling": https://inside.java/2021/09/27/patterns-3-sealed-classes/

---

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-20
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-20

---

**Revision History:**
- 2026-02-20: Initial ADR
