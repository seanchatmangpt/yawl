# YAWL Pattern Specifications

This directory contains specifications and Java skeleton implementations for 5 new advanced workflow patterns for the YAWL engine.

## Pattern Overview

| Pattern | WCP Ref | Description | Spec File |
|---------|---------|-------------|-----------|
| Saga Orchestration | N/A | Distributed transactions with compensating actions | [01-saga-orchestration.md](./01-saga-orchestration.md) |
| Structured Discriminator | WCP-9 | First token passes, rest discarded | [02-structured-discriminator.md](./02-structured-discriminator.md) |
| Milestone | WCP-18 | Context-dependent enablement | [03-milestone.md](./03-milestone.md) |
| Interleaved Parallel Routing | WCP-17 | Concurrent enabling with mutex execution | [04-interleaved-parallel-routing.md](./04-interleaved-parallel-routing.md) |
| Event-Based Deferred Choice | WCP-16 | External event triggers workflow path | [05-event-based-deferred-choice.md](./05-event-based-deferred-choice.md) |

## Directory Structure

```
.specify/patterns/
├── 01-saga-orchestration.md       # Saga pattern specification
├── 02-structured-discriminator.md # Discriminator pattern specification
├── 03-milestone.md                # Milestone pattern specification
├── 04-interleaved-parallel-routing.md # Interleaved routing specification
├── 05-event-based-deferred-choice.md  # Deferred choice specification
├── README.md                      # This file
└── java/
    └── org/yawlfoundation/yawl/elements/patterns/
        ├── package-info.java      # Package documentation
        │
        # Saga Orchestration
        ├── YSagaOrchestrationTask.java
        ├── YSagaStep.java
        ├── YCompensatingAction.java
        └── YSagaState.java
        │
        # Structured Discriminator
        ├── YDiscriminatorTask.java
        └── YDiscriminatorState.java
        │
        # Milestone
        ├── YMilestoneCondition.java
        ├── YMilestoneGuardedTask.java
        └── MilestoneGuardOperator.java
        │
        # Interleaved Parallel Routing
        ├── YInterleavedRouterTask.java
        ├── YInterleavedSet.java
        ├── YMutexLock.java
        ├── InterleavedSelectionStrategy.java
        └── YInterleavedState.java
        │
        # Event-Based Deferred Choice
        ├── YDeferredChoiceTask.java
        ├── YEventListener.java
        ├── YTimerEventListener.java
        ├── YMessageEventListener.java
        ├── YSignalEventListener.java
        ├── YEvent.java
        ├── YEventRegistry.java
        └── YDeferredChoiceState.java
```

## Integration Points

Each pattern integrates with the existing YAWL architecture through:

### YTask Extensions
- New join type constants added to `YTask`:
  - `_DISCRIMINATOR = 64`
  - `_DISCRIMINATOR_N_OUT_OF_M = 65`
  - `_DISCRIMINATOR_WITH_MEMORY = 66`
  - `_INTERLEAVED_JOIN = 70`
  - `_INTERLEAVED_SPLIT = 71`
  - `_DEFERRED_CHOICE_JOIN = 80`
  - `_DEFERRED_CHOICE_SPLIT = 81`

### YCondition Extensions
- `YMilestoneCondition` extends `YCondition` for milestone pattern

### Existing Integration Points
| Existing Class | Pattern | Integration |
|----------------|---------|-------------|
| `YTask._mi_active` | Saga | Track active saga steps |
| `YTask._removeSet` | Saga | Track steps for compensation |
| `YNetRunner` | All | Event coordination |
| `YPersistenceManager` | All | State persistence |
| `E2WFOJNet` | Discriminator, Interleaved | Reset net analysis |

## Implementation Notes

### Thread Safety
All pattern implementations require:
- Synchronized state transitions
- Atomic token management
- Concurrent-safe data structures

### Persistence
All state objects must:
- Implement persistence callbacks
- Support crash recovery
- Respect transaction boundaries

### XML Schema Extensions
Each pattern requires XSD schema extensions for specification parsing. See individual specification files for XML examples.

## Next Steps

1. Move Java files from `.specify/patterns/java/` to `src/org/yawlfoundation/yawl/elements/patterns/`
2. Update YAWL XSD schema with new pattern elements
3. Implement schema parser extensions for new elements
4. Add unit tests for each pattern
5. Add integration tests with existing patterns
6. Update E2WFOJNet for structural analysis of new patterns

## References

- [Workflow Patterns Initiative](http://www.workflowpatterns.com/)
- [YAWL Foundation](http://www.yawlfoundation.org/)
- WCP-9: Discriminator
- WCP-16: Deferred Choice
- WCP-17: Interleaved Parallel Routing
- WCP-18: Milestone
