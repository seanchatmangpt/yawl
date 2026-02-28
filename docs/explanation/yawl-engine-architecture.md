# YAWL Engine Architecture — Design and Evolution

Understand the design decisions and architecture of the YAWL stateful engine.

## Core Design Philosophy

The YAWL Engine is built on **formal Petri net theory**. Rather than inventing ad-hoc workflow semantics, YAWL grounds itself in decades of research on workflow nets (WF-nets) and provides precise, mathematically sound execution semantics.

### Why Petri Nets?

**Petri nets provide:**
1. **Formal semantics** — No ambiguity in control flow
2. **Decidable properties** — Soundness, reachability, termination can be verified
3. **Analysis tools** — Deadlock detection, reachability analysis
4. **Rich control flow** — OR-joins, multi-instance tasks, cancellation regions

**Example: Ambiguity without formal semantics**

```
"If A and B both complete, do C"

In informal workflow languages:
- Does C start when A completes (waiting for B)?
- Or only when both complete?
- What if B never completes?

In Petri nets:
- Clear: C is only enabled when tokens exist in both A's output and B's output
- Analysis detects if B can be unreachable (soundness violation)
```

## Component Architecture

### YEngine (Orchestrator)

```
┌─────────────────────────────────────────┐
│           YEngine Singleton             │
│                                         │
│  - Case lifecycle management            │
│  - Work item routing                    │
│  - Interface B/E/X handlers             │
│  - Persistence coordination             │
└────────┬────────────────┬───────────────┘
         │                │
    ┌────▼────┐     ┌─────▼──────┐
    │YNetRunner│     │Persistence │
    │ (per     │     │ (Hibernate)│
    │ case)    │     │            │
    └─────────┘     └────────────┘
```

**Responsibilities:**
- Create/destroy cases
- Enable work items when predecessor tasks complete
- Route work items through state transitions
- Persist state to database on critical operations
- Emit events to subscribers

**Thread Safety:**
- Singleton with synchronized access
- Per-case runners are not thread-safe (checkout enforces exclusive lock)
- Document (case data) requires explicit synchronization from caller

### YNetRunner (Petri Net Executor)

Executes the formal Petri net for a single case.

```
Token Distribution (Marking)
         │
         ↓
Identify Enabled Transitions
         │
         ↓
Fire Transition (remove input tokens, add output)
         │
         ↓
Check for enabled output tasks
         │
         ↓
Enable work items in YEngine
```

**Key Algorithm: Continuous Net Runner**

```java
public void continueIfPossible() {
    while (hasEnabledTransitions()) {
        // Get all currently enabled tasks
        Set<YTask> enabled = getEnabledTransitions();

        // Fire each enabled task (non-blocking)
        for (YTask task : enabled) {
            fireTransition(task);
            updateMarking(task);
        }

        // Check if new tasks are ready
        Set<YWorkItem> readyItems = getEnabledWorkItems();
        engine.enableWorkItems(readyItems);

        // Stop if no more transitions without work items
        if (readyItems.isEmpty()) break;
    }
}
```

### YWorkItem (State Machine)

Each work item tracks its own state:

```
┌─────────┐
│Enabled  │  ← Created, ready for execution
└────┬────┘
     │ checkout()
     ↓
┌──────────┐
│Executing │  ← Being worked on (locked)
└────┬─────┘
     │ complete() or fail()
     ↓
┌──────────┐
│Completed │  ← Result recorded
└──────────┘
```

**State Transitions:**

- **Enabled → Executing**: Exclusive lock via `checkout()`. Prevents duplicate work.
- **Executing → Completed**: `completeWorkItem()` records output, unlocks, triggers runner continuation
- **Executing → Failed**: Error handling. Case may be cancelled or retry attempted.

## Persistence Architecture

### Hybrid Approach: In-Memory + Database

```
┌──────────────────────────────────┐
│   YEngine (in-memory state)      │
│   - Case map: Map<ID, YNetCase>  │
│   - Active runners: per-case     │
└────────┬─────────────────────────┘
         │ on critical events
         ↓
┌──────────────────────────────────┐
│  Hibernate ORM (persistent)      │
│  - YAWL_CASES table              │
│  - YAWL_WORK_ITEMS table         │
│  - YAWL_AUDIT_LOG table          │
└──────────────────────────────────┘
```

**What gets persisted:**
- Case state (data, status)
- Completed work items (for audit trail)
- Current Petri net marking (token distribution)
- Event log (for process mining)

**What stays in memory:**
- Active runners (performance)
- Enabled work items (responsiveness)
- Checkout locks (exclusivity)

**Persistence Points:**

1. **On case creation** — Establish database record
2. **After work item completion** — Commit result, persist new marking
3. **On case completion** — Final state
4. **Periodically** — Checkpoint active state (configurable)

### Recovery on Restart

```
Engine startup:
  ↓
Load all non-terminal cases from DB
  ↓
Reconstruct YNetRunner per case with saved marking
  ↓
Call continueIfPossible() to re-enable any ready tasks
  ↓
Restore checkpoint state
  ↓
Ready to serve requests
```

## Concurrency Model Evolution

### Platform Threads (Java 8-20)

**Limitation:** One OS thread per concurrent case
- 10,000 cases × 1 thread = problematic memory/context switching
- High latency between task completion and successor enablement

**Model:**
```
Case 1: ├─────┤ (sleeping, waiting for work)
Case 2: ├──────┤
Case 3: ├────┤
...
Case N: └────────┘
     ↓
     ~10K threads × 1MB stack = 10GB memory
```

### Virtual Threads (Java 21+)

**Enablement:** 1 million lightweight threads on 8 cores
- One OS thread per core
- JVM schedules millions of virtual threads
- Minimal memory overhead (~100 bytes per thread)

**Model:**
```
Virtual threads (1,000,000): ────────────────────────────
                ↓
Carrier threads (8):  ├───┤├───┤├───┤├───┤...
                      └─────────────────────┘
                        1 per CPU core
```

**Architectural Impact:**

With virtual threads, the execution model shifts:

```java
// Before: One platform thread per case (problematic)
Thread caseThread = new Thread(() -> {
    executeCase(caseID);  // Blocks entire OS thread
});
caseThread.start();

// After: Virtual thread (lightweight, efficient)
Thread.ofVirtual().start(() -> {
    executeCase(caseID);  // JVM parks thread when blocked
});
```

## Control Flow Semantics

### OR-Join (Non-Local Synchronization)

The most complex aspect of YAWL:

```
Task A ──┐
         ├─→ OR-Join Task ──→ Task D
Task B ──┘

Semantics:
- Task C fires when (A completes) OR (B completes) OR (both complete)
- NOT when (A completes AND token still in predecessor)

Implementation:
- Cannot use naive token-counting
- Requires reachability analysis:
  * Can predecessor generate more tokens? → Wait
  * Can predecessor not generate more? → Fire
```

**Why it matters:**

```
Example: Approval workflow

Manager review ──┐
                ├──→ OR-Join ──→ Notify result
Director review ┘

Case 1: Only manager approves
  - Manager completes → can director still approve?
  - Check: is there any remaining path from input to director?
  - If no → Fire OR-join (director will never approve)
  - If yes → Wait

Case 2: Director is optional approver
  - Same logic applies
  - OR-join ensures exactly one approval flow (not deadlock)
```

## Task Types and Decomposition

### Atomic Tasks
```java
YTask task = new YTask("TaskID", YTask._AND, YTask._AND);
// Executes inline, no subprocess
```

### Composite Tasks with Decomposition
```java
YCompositeTask task = new YCompositeTask(...);
task.setDecompositionPrototype(decomposition);
// Executes a subprocess when enabled
```

**Decomposition Types:**
1. **YNet** — Nested workflow (hierarchical)
2. **YWebService** — Call external HTTP endpoint
3. **YCustomForm** — Render custom UI for manual task
4. **YEbService** — Event-based service

### Multi-Instance Tasks

Enable task to repeat N times:

```
Input: [item1, item2, item3, item4, item5]

Task (MI mode):
  Instance 1: Process item1
  Instance 2: Process item2
  Instance 3: Process item3
  Instance 4: Process item4
  Instance 5: Process item5

Join: Wait for N completions (configurable: all, any, threshold)

Output: [result1, result2, result3, result4, result5]
```

**Completion Modes:**
- **All**: All instances complete (AND-join semantics)
- **Any**: First instance completion (OR-join semantics)
- **Threshold**: N of M instances (partial synchronization)

## Event Logging and Process Mining

### XES Format

YAWL logs events in XES (eXtensible Event Stream) format:

```xml
<log>
  <trace>
    <event>
      <string key="concept:name" value="SubmitRequest"/>
      <date key="time:timestamp" value="2026-02-28T10:30:00"/>
      <string key="lifecycle:transition" value="complete"/>
      <string key="org:resource" value="user@example.com"/>
    </event>
    ...
  </trace>
</log>
```

**Logged events:**
- Case creation
- Task enablement
- Task execution (checkout)
- Task completion
- Task failure
- Case completion

**Use cases:**
- **Process Mining** — Discover actual process from logs
- **Compliance** — Audit trail of who did what when
- **Performance Analysis** — Identify bottlenecks
- **ML Training** — Train predictive models

## Reliability and Error Handling

### Failure Scenarios

| Scenario | Handling |
|----------|----------|
| Task execution fails | Mark work item failed, optionally retry |
| Database connection lost | Retry with exponential backoff |
| Case data invalid | Reject case creation with validation error |
| Deadlock in workflow | Case gets stuck; manual intervention needed |
| Engine crash | Recovery from DB on restart |

### Graceful Degradation

If database is temporarily unavailable:

1. **Cache**: Recent cases stay in memory
2. **Read-through**: Service requests from cache
3. **Persistence**: Flush to DB when connectivity restored
4. **Conflict resolution**: Last-write-wins or explicit merge

## Design Trade-Offs

### In-Memory vs Persistent State

**Choice**: Hybrid (in-memory with sync to DB)

**Reasoning:**
- **Performance**: In-memory access is sub-millisecond
- **Reliability**: DB serves as recovery point
- **Scalability**: Can shard in-memory state across nodes

**Cost**: Synchronization complexity (two-phase commit, replication lag)

### Stateful vs Stateless Engine

**YAWL v6 includes both:**

1. **Stateful** (YEngine)
   - Pros: Fast case execution, minimal I/O
   - Cons: Limited by single node memory
   - Use: High-throughput, low-latency cases

2. **Stateless** (YStatelessEngine)
   - Pros: Horizontal scaling, fault tolerance
   - Cons: Every operation requires state reconstruction
   - Use: Resilience, arbitrary scale

### Pessimistic Locking (Checkout) vs Optimistic

**Choice**: Pessimistic (exclusive checkout)

**Reasoning:**
- Workflow tasks are long-running (hours, days)
- Conflict is rare
- Pessimistic avoids retry storms

**Trade-off**: Cannot edit same task simultaneously (by design)

## Future Architecture

### Planned Evolution

1. **Virtual Thread per Case** (Java 21+)
   - Each case execution on dedicated virtual thread
   - Automatic cancellation on timeout (structured concurrency)

2. **Structured Concurrency for AND-splits**
   - Use `StructuredTaskScope` for parallel branches
   - Automatic exception propagation and cleanup

3. **ScopedValues for Workflow Context** (Java 21+)
   - Replace ThreadLocal (unsafe with virtual threads)
   - Safe propagation of execution context

4. **CQRS Split on Interface B**
   - Separate read (queries) from write (commands)
   - Better caching and eventually consistent reads

5. **Temporal Queries**
   - Query "what-if" scenarios
   - Replay execution from arbitrary point in time

---

**See also:**
- [Dual-Engine Architecture](./dual-engine-architecture.md) — Stateful vs stateless comparison
- [Execution Profiles](./execution-profiles.md) — Deferred, continuous, persistent modes
- [Case Lifecycle](./case-lifecycle.md) — Birth to death of a workflow instance
