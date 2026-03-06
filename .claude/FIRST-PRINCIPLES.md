# First Principles: Observable Processes & Event-Driven Architecture

**Framework**: Van der Aalst Process Mining (Events → Traces → Process Models → Conformance)
**WIGGUM**: "What Would Dr. Wil Van Der Aalst Do?" — Ground everything in observable events, never abstract notation.
**Status**: Core foundation for all YAWL documentation refactoring
**Last Updated**: 2026-03-06

---

## I. Why Observable Events?

### The Problem with Abstract Notation

Previous YAWL documentation used:
- **Ψ** for "Observatory" (opaque)
- **Λ** for "Build" (unmeasurable)
- **μ** for "Agents" (unobservable)

**Consequence**: Engineers cannot verify what's actually happening. Notation hides execution reality.

### The Solution: Events

An **event** is the smallest unit of truth in a software system:
- **Timestamp** when something occurred
- **Actor** who/what caused it
- **Phase** where it occurred (compile, test, validate, commit)
- **State transition** before → after
- **Artifacts** files/code affected
- **Success/failure** outcome

**Benefit**: Events are observable, measurable, repeatable. They form a causal log (trace) that completely describes what happened.

### Three Pillars of Process Excellence

1. **Process Mining** (Discovery)
   - Observe actual event traces from YAWL runs
   - Extract the real process (not intended, actual)
   - Answer: "What is our build actually doing?"

2. **Trace Conformance** (Verification)
   - Spec says: compile → test → validate
   - Event log shows: compile → test → fail
   - Detected: Deviation in phase sequence
   - Action: Trace why test failed (bottleneck analysis)

3. **Process Performance** (Optimization)
   - Compile: 2.3s, Test: 18.5s, Validate: 4.1s
   - Bottleneck: Test phase (75% of total time)
   - Decision: Parallelize tests or optimize test suite

---

## II. Event Specification

### Event Anatomy

Every event has this structure (YAWL canonical form):

```json
{
  "event_id": "evt-compile-001",
  "timestamp": "2026-03-06T01:30:45.123Z",
  "phase": "build",
  "actor": "maven-executor",
  "event_type": "CompileStarted | CompileSuccess | CompileFailed",
  "state_before": { "modules_compiled": 0, "status": "ready" },
  "state_after": { "modules_compiled": 5, "status": "compiling" },
  "artifacts": ["yawl-engine/target/classes", "yawl-elements/target/classes"],
  "duration_ms": 2300,
  "metadata": {
    "module": "yawl-engine",
    "errors": 0,
    "warnings": 3
  }
}
```

### Core Event Properties

| Property | Type | Example | Purpose |
|----------|------|---------|---------|
| **event_id** | UUID | evt-compile-001 | Unique trace element |
| **timestamp** | ISO8601 | 2026-03-06T01:30:45.123Z | Causal ordering |
| **phase** | Enum | build\|validate\|commit | Process stage |
| **actor** | String | maven-executor\|git-actor\|hyper-validate | What caused event |
| **event_type** | String | CompileStarted, CompileSuccess, CompileFailed | What happened |
| **state_before** | Object | {modules_compiled: 0} | Precondition |
| **state_after** | Object | {modules_compiled: 5} | Postcondition |
| **artifacts** | [String] | [yawl-engine/target] | Files created/modified |
| **duration_ms** | Integer | 2300 | How long phase took |
| **metadata** | Object | {errors: 0, warnings: 3} | Rich context |

### Event Types by Phase

#### BUILD Phase Events
```
CompileStarted → CompileSuccess/CompileFailed
TestStarted → TestSuccess/TestFailed
ParseStarted → ParseSuccess/ParseFailed
```

#### VALIDATE Phase Events
```
ConformanceCheckStarted → ConformanceCheckPassed/ConformanceCheckFailed
InvariantCheckStarted → InvariantCheckPassed/InvariantCheckFailed
GuardViolationDetected → GuardViolationFixed/GuardViolationIgnored
```

#### COMMIT Phase Events
```
CommitStarted → CommitSuccess/CommitFailed
PushStarted → PushSuccess/PushFailed
BranchCreated/BranchDeleted
TagCreated
```

#### TEAM Orchestration Events
```
TeamCreated → TeamDispatched → TaskAssigned → TaskCompleted → TeamConsolidated
TeammateOnline/TeammateOffline/TeammateTimeout
MessageSent/MessageAckReceived/MessageTimeout
```

#### AGENT Autonomy Events
```
AgentStarted → AgentTaskAssigned → AgentTaskCompleted → AgentStopped
AgentCheckpoint (state saved)
AgentCrash/AgentRecovered
```

---

## III. Traces: Event Sequences

A **trace** is the complete event log for one execution. Example:

```
Trace ID: tr-2026-03-06-001

evt-build-001: CompileStarted (2026-03-06T01:30:45.123Z, yawl-engine)
evt-build-002: CompileSuccess (2026-03-06T01:30:47.400Z, yawl-engine, 2.3s)
evt-build-003: CompileStarted (2026-03-06T01:30:47.400Z, yawl-elements)
evt-build-004: TestStarted (2026-03-06T01:30:48.100Z, yawl-engine)
evt-build-005: TestSuccess (2026-03-06T01:30:66.500Z, yawl-engine, 18.4s, 427 tests passed)
evt-build-006: CompileSuccess (2026-03-06T01:30:66.600Z, yawl-elements, 19.2s)
evt-validate-001: ConformanceCheckStarted (2026-03-06T01:30:67.100Z)
evt-validate-002: ConformanceCheckPassed (2026-03-06T01:30:71.300Z, 0 violations)
evt-validate-003: InvariantCheckStarted (2026-03-06T01:30:71.300Z)
evt-validate-004: InvariantCheckPassed (2026-03-06T01:30:73.800Z, 0 failures)
evt-commit-001: CommitStarted (2026-03-06T01:30:74.100Z, claude/refactor-wiggum-abc123)
evt-commit-002: CommitSuccess (2026-03-06T01:30:75.200Z, "Refactor to first principles")
evt-commit-003: PushStarted (2026-03-06T01:30:75.200Z, origin/claude/refactor-wiggum-abc123)
evt-commit-004: PushSuccess (2026-03-06T01:30:77.800Z)

Total Duration: 32.7s
Status: GREEN (all phases succeeded, no violations)
```

### Trace Properties

- **Complete**: Every state transition recorded
- **Ordered**: Events sorted by timestamp
- **Causal**: Each event's state_after = next event's state_before
- **Atomic**: Git commit is one atomic trace event (all-or-nothing)
- **Idempotent**: Replaying trace produces identical results

---

## IV. Process Models: Specifications

A **process model** describes the intended workflow as a directed graph:

### Simplified YAWL Workflow

```
START
  ↓
[Parse Code] → (success) → [Compile]
  ↓ (error)
  └─────────────→ FAIL

[Compile] → (success) → [Test]
  ↓ (error)
  └─────────────→ FAIL

[Test] → (success) → [Validate]
  ↓ (error)
  └─────────────→ FAIL

[Validate] → (success) → [Commit]
  ↓ (error: violations)
  └─────────────→ FAIL

[Commit] → (success) → GREEN
  ↓ (error: network)
  └─────────────→ FAIL (retry possible)

FAIL → END
GREEN → END
```

### Process Semantics

- **Activity** (rectangle): Compile, Test, Validate
- **Gateway** (diamond): Success/error decision
- **Sequence flow** (arrow): Temporal order
- **Parallel flow** (for Teams): Non-blocking activities

### Conformance = Trace ≈ Model

A trace **conforms** to a model if:
1. Event sequence respects activity order (no jumping)
2. No mandatory activities skipped
3. No activities repeat beyond allowed cycles
4. All termination conditions reached (SUCCESS or caught FAIL)

**Example**: If trace shows `[Compile] → [Commit]` (skipped Test), trace does NOT conform.

---

## V. Conformance Checking: H & Q Phases

### H Phase: Guard Violations (Trace Properties)

Guards detect **forbidden patterns** in generated code that violate process assumptions:

```
H_TODO    : Code has unresolved "TODO" comment
            → Event: GuardViolationDetected(H_TODO, line 42)
            → Conformance: Not allowed → process must FAIL

H_MOCK    : Code has mock class/method
            → Event: GuardViolationDetected(H_MOCK, MockDataService)
            → Conformance: Not allowed → process must FAIL

H_STUB    : Method returns empty/placeholder
            → Event: GuardViolationDetected(H_STUB, getData returns "")
            → Conformance: Not allowed → process must FAIL

H_EMPTY   : Method body is empty
            → Event: GuardViolationDetected(H_EMPTY, initialize() { })
            → Conformance: Not allowed → process must FAIL

H_FALLBACK: Catch block returns fake data
            → Event: GuardViolationDetected(H_FALLBACK, catch {return empty})
            → Conformance: Not allowed → process must FAIL

H_LIE     : Code ≠ documentation
            → Event: GuardViolationDetected(H_LIE, @return never null but returns null)
            → Conformance: Not allowed → process must FAIL

H_SILENT  : Logs instead of throwing
            → Event: GuardViolationDetected(H_SILENT, log.error not implemented)
            → Conformance: Not allowed → process must FAIL
```

**Result Event**:
```json
{
  "event_type": "ConformanceCheckPassed | ConformanceCheckFailed",
  "phase": "validate",
  "violations": [
    {"pattern": "H_TODO", "file": "YWorkItem.java", "line": 427},
    {"pattern": "H_MOCK", "file": "MockDataService.java", "line": 12}
  ],
  "count": 2,
  "status": "FAILED"
}
```

### Q Phase: Invariant Violations (State Reachability)

Invariants enforce **reachability constraints** on process state:

```
Invariant 1: real_impl ∨ throw UnsupportedOperationException
             ↓ Translation:
             Code must be real implementation OR explicit exception.
             State before: {has_real_code: false, has_exception: false}
             State after: {has_real_code: true} ∨ {has_exception: true}
             Failure: Neither state reached → process FAILS

Invariant 2: No silent fallbacks in error paths
             ↓ Translation:
             Catch block must NOT return fake/empty data.
             State before: {exception_caught: true}
             State after: {exception_propagated: true}
             Failure: {fake_data_returned: true} → process FAILS

Invariant 3: Documentation matches code
             ↓ Translation:
             @return type T in Javadoc = actual return type in code
             @throws ExceptionX = actual throw in code
             Failure: Mismatch detected → process FAILS
```

**Result Event**:
```json
{
  "event_type": "InvariantCheckPassed | InvariantCheckFailed",
  "phase": "validate",
  "invariant": "real_impl ∨ throw",
  "violations": [
    {"file": "YDataService.java", "method": "getData()", "reason": "returns fake empty list"}
  ],
  "count": 1,
  "status": "FAILED"
}
```

---

## VI. Process Orchestration: Teams & Agents

### Parallel Event Streams (Teams)

When multiple engineers work on independent tasks, each generates an event stream:

```
Engineer 1 (Schema):        Evt1 → Evt2 → Evt3 → Evt4
Engineer 2 (Engine):        Evt5 → Evt6 → Evt7 → Evt8
Engineer 3 (Integration):   Evt9 → Evt10 → Evt11 → Evt12

Synchronization points:
  - All three must reach "ready to commit" before consolidation
  - Messages between streams: "I need your API contract" = message event
  - Lead consolidates: all streams merge into single commit trace
```

**Key constraint**: No two event streams write to same file (MVCC violation).

### Autonomous Execution (Agents)

Agents execute tasks independently, each recording its own trace:

```
Agent 1: Process research (discovery)
  → Parse code → Extract patterns → Mine process → Report findings
  → All events recorded, no side effects

Agent 2: Validate conformance (verification)
  → Load code → Run SPARQL queries → Check invariants → Report violations
  → All events recorded, independent from Agent 1

Lead: Consolidate findings
  → Merge traces → Make decisions → Write consolidated trace
```

**Guarantee**: Each agent's trace is independent, reproducible, and doesn't interfere with others.

---

## VII. Atomic Commits: Ω Phase

A commit is **atomic** when:
1. All events in trace executed successfully
2. Git commit created with complete trace reference
3. Push succeeds (or fails with retry/rollback option)

**Commit event**:
```json
{
  "event_type": "CommitCreated",
  "timestamp": "2026-03-06T01:30:74.100Z",
  "commit_hash": "a1b2c3d4e5f6",
  "trace_id": "tr-2026-03-06-001",
  "events_included": 14,
  "files_changed": 5,
  "insertions": 427,
  "deletions": 89,
  "message": "Refactor CLAUDE.md to first principles (wiggum pattern)",
  "phase_events": {
    "build": 6,
    "validate": 4,
    "commit": 4
  },
  "status": "success"
}
```

**Invariant**: One logical change = one atomic commit trace.

---

## VIII. Process Metrics: Performance Analysis

### Key Metrics (from Event Logs)

| Metric | Formula | Example | Insight |
|--------|---------|---------|---------|
| **Phase Duration** | state_after.timestamp - state_before.timestamp | Compile: 2.3s | Bottleneck? |
| **Throughput** | events_per_minute | 127 events/min | How fast are we moving? |
| **Failure Rate** | failed_events / total_events | 3/100 = 3% | Is build flaky? |
| **Critical Path** | max(phase durations) | Test: 18.5s (75% total) | Where to optimize? |
| **Trace Variants** | count(distinct event_sequences) | 12 variants | How consistent? |

### Example Performance Analysis

```
Trace Duration: 32.7s
Breakdown:
  - Parse: 0.5s (1%)
  - Compile: 4.2s (13%)
  - Test: 18.5s (57%) ← BOTTLENECK
  - Validate: 4.9s (15%)
  - Commit: 4.6s (14%)

Finding: Test phase takes 57% of time
Optimization: Parallelize test suites
Expected improvement: 18.5s → 6s (saved 12.5s per trace)
ROI: 38% faster builds
```

---

## IX. The WIGGUM Covenant

### First Principles Checklist

Before writing any YAWL documentation section, ask:

- [ ] **Events**: What events does this phase emit? List them explicitly.
- [ ] **Trace**: Can I show an example trace (timestamps, state transitions)?
- [ ] **Conformance**: How do I verify the actual behavior matches intended behavior?
- [ ] **Measurable**: Can engineers see event metrics (duration, count, errors)?
- [ ] **Observable**: Is this rooted in what actually happens, not abstract notation?
- [ ] **Atomic**: Is this one logical change or decomposable trace?

### Process Mining Questions

**Process Discovery**:
- "What does the actual YAWL build event log look like?"
- "Are there unexpected deviations in the real traces?"

**Conformance**:
- "What violations appeared in yesterday's event logs?"
- "Did the trace match the spec?"

**Performance**:
- "Which phase is the critical path?"
- "Where are bottlenecks in the event stream?"

---

## X. Reference: Greek Notation → Observable Events

| Old | New | Meaning |
|-----|-----|---------|
| **Ψ Observatory** | **Event Log** | {CompileStarted, TestPassed, ViolationDetected, ...} |
| **Λ Build** | **Build Phase Process** | Compile → Test → Validate sequence with events |
| **H Guards** | **Trace Conformance** | GuardViolationDetected events must not occur |
| **Q Invariants** | **State Reachability** | {real_impl ∨ throw} reachable states only |
| **Ω Git** | **Atomic Event Trace** | CommitCreated event is atomic, immutable, traceable |
| **τ Teams** | **Parallel Event Streams** | Non-overlapping streams, synchronized consolidation |
| **μ Agents** | **Autonomous Executors** | Independent traces, mergeable findings |
| **R Rules** | **Process Constraints** | Rules enforce phase compliance (e.g., no skip Validate) |
| **φ Workflow** | **Process Model** | Directed graph of phases, decision points, loops |

---

## XI. Next Steps

This foundation supports three orthogonal documents:

1. **EVENT-LOG-SCHEMA.md** — Formal event type definitions, trace format
2. **PROCESS-SPECIFICATION.md** — BPMN models for each major workflow
3. **CONFORMANCE-CHECKING.md** — How H/Q phases verify traces against spec

---

## GODSPEED. ✈️

*Every observable event is a fact. Every fact is a stone in the bridge to understanding.*
