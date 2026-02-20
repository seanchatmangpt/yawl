---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/engine/**"
  - "*/src/main/java/org/yawlfoundation/yawl/stateless/**"
  - "*/src/test/java/org/yawlfoundation/yawl/engine/**"
  - "*/src/test/java/org/yawlfoundation/yawl/stateless/**"
---

# Engine & Stateless Workflow Rules

## Concurrency Model
- `YNetRunner.continueIfPossible()` runs on dedicated virtual thread via `Thread.ofVirtual()`
- Use `StructuredTaskScope.ShutdownOnFailure` for parallel work item processing
- Replace `ThreadLocal` with `ScopedValue<WorkflowContext>` for context propagation
- Never use `synchronized` blocks with virtual threads (causes pinning)

## Dual Family Boundary
- `engine` = stateful (DB persistence, YEngine singleton)
- `stateless` = event-driven (in-memory, XML snapshots, YStatelessEngine)
- Never mix stateful and stateless engines in same deployment
- Shared code lives in `elements` module, not duplicated

## Entry Points
- `YEngine` - Stateful workflow engine (singleton, Interface A/B)
- `YStatelessEngine` - Stateless execution (no DB, event-driven)
- `YNetRunner` - Net execution coordinator (virtual thread per case)
- `YWorkItem` - Fundamental unit of work in a case

## Petri Net Semantics
- All workflow patterns derive from rigorous Petri net formalism
- Tokens flow through conditions (places) and tasks (transitions)
- OR-join semantics require careful handling (non-local semantics)
- Cancellation regions must be explicit in specification
