---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/engine/**"
  - "*/src/main/java/org/yawlfoundation/yawl/stateless/**"
  - "*/src/test/java/org/yawlfoundation/yawl/engine/**"
  - "*/src/test/java/org/yawlfoundation/yawl/stateless/**"
---

# Engine & Stateless Workflow Rules

## Concurrency Model
- `YNetRunner.continueIfPossible()` runs on dedicated virtual thread: `Thread.ofVirtual().name("case-" + caseId).start(runnable)`
- `StructuredTaskScope.ShutdownOnFailure` for parallel AND-split branch processing
- `ScopedValue<WorkflowContext>` replaces `ThreadLocal` — propagates automatically to forked virtual threads
- Never `synchronized` in engine code (causes virtual thread pinning) — use `ReentrantLock`

## Dual Family Boundary
- `engine` = stateful (Hibernate/DB persistence, `YEngine` singleton)
- `stateless` = event-driven (in-memory, JSON snapshots, `YStatelessEngine`, no DB)
- Never mix stateful and stateless engines in same deployment
- Shared domain logic lives in `yawl-elements` module — never duplicated

## Entry Points
- `YEngine` — Stateful singleton; manages cases across restarts; exposes Interface A/B/E/X
- `YStatelessEngine` — Stateless; each case is independent; serialise/restore via JSON
- `YNetRunner` — Per-case net execution coordinator; fires transitions, propagates tokens
- `YWorkItem` — One unit of work (enabled → executing → completed / failed / suspended)

## Split/Join Semantics (Critical — Petri Net Correctness)

| Type | Split | Join |
|------|-------|------|
| **AND** | All outgoing arcs fire simultaneously | Wait for ALL incoming arcs before enabling |
| **XOR** | Exactly one arc fires (predicate selects) | First incoming arc enables the task |
| **OR** | One or more arcs fire (predicates evaluated) | Wait for all expected (non-local semantics) |

**OR-join special case**: Requires global state analysis (backward reachability check).
`YNetRunner.orJoinEnabled()` must inspect the entire net marking — not just the immediate
predecessors. Never simplify OR-join to XOR-join without changing the specification.

## Cancellation Regions
```java
// Cancellation must be explicit in the specification
// YTask.getCancellationSet() returns the set of elements cancelled on task completion
Set<YExternalNetElement> cancelled = task.getCancellationSet();
for (YExternalNetElement element : cancelled) {
    runner.cancelElement(element);  // remove tokens from cancelled elements
}
// VIOLATION — silently ignoring cancellation set
```

## Multiple Instances
- Static MI: instance count fixed at design time (`YMultiInstanceAttributes.getMaxInstances()`)
- Dynamic MI: instance count determined at runtime from case data
- Threshold: `minInstances` must complete before task completes; `maxInstances` is the ceiling
- Completion condition: XPath boolean evaluated against aggregated instance output

## YWorkItem State Machine
```
ENABLED → EXECUTING → COMPLETE
           ↓              ↑
        SUSPENDED ────────┘
           ↓
        FAILED
```
- Transitions are engine-driven — never set work item status directly from service code
- Failed items may be retried (re-enable) or escalated (send to admin queue)

## Persistence Rules (Stateful Engine)
- All case state changes must be committed in a Hibernate transaction before returning
- `YPersistenceManager` wraps Hibernate session; use it, not raw `Session`
- Engine restart: `YEngine.init()` reloads all in-flight cases from DB on startup

## Stateless Engine Patterns
- Serialise case state to JSON before suspending: `YCaseExporter.export(case)`
- Restore: `YCaseImporter.restore(json)` — must produce identical `YNetRunner` state
- No Hibernate, no DB connection — all state lives in memory until exported
- Suitable for: serverless, reactive, and agent-based deployments
