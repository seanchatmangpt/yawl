# Process Specification: BPMN Workflows & Decision Points

**Framework**: BPMN 2.0 (Business Process Model & Notation) + Event Log Integration
**Purpose**: Formal specification of YAWL build, validation, and orchestration processes
**Status**: Complete workflow models
**Last Updated**: 2026-03-06

---

## I. Build Process (dx.sh all)

### Process Overview

The `dx.sh all` command executes a sequence of phases with explicit decision gates:

```
START
  ↓
[Parse Code]
  ├─ Success → [Compile]
  └─ Error → FAIL (exit 1)
  ↓
[Compile]
  ├─ Success → [Test]
  └─ Error → FAIL (exit 2)
  ↓
[Test]
  ├─ Success → [Validate]
  └─ Error → FAIL (exit 2)
  ↓
[Validate]
  ├─ Success → [Commit]
  └─ Error → FAIL (exit 2)
  ↓
[Commit]
  ├─ Success → GREEN (exit 0)
  ├─ Error (network) → RETRY (up to 4×)
  └─ Critical Error → FAIL (exit 2)

FAIL → END (cleanup, report to user)
GREEN → END (ready for merge)
RETRY → [Commit] (exponential backoff: 2s, 4s, 8s, 16s)
```

### BPMN Flow Notation

```
┌─────────────────────────────────────────────────────────────┐
│ Process: dx.sh all                                           │
│ Status: ACTIVE                                              │
│ Swimlane: Developer (trigger) / Maven (executor) / Git      │
└─────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│ Lane: Developer                                              │
│ ┌────────────┐                                              │
│ │   START    │                                              │
│ │ dx.sh all  │                                              │
│ └────────┬───┘                                              │
└──────────┼──────────────────────────────────────────────────┘
           │
┌──────────┼──────────────────────────────────────────────────┐
│ Lane: Build System (Maven / Parser)                          │
│ │        ↓                                                   │
│ │  ┌──────────────┐                                         │
│ │  │ Parse Code   │ ←─ ParseStarted event                  │
│ │  │ (tree-sitter)│                                         │
│ │  └──┬───────┬───┘                                         │
│ │     │       │                                             │
│ │  Success  Error                                           │
│ │     │       │                                             │
│ │     ↓       └──→ [FAIL / exit 1]                         │
│ │  ┌──────────────┐                                         │
│ │  │ Compile      │ ←─ CompileStarted event               │
│ │  │ (mvn compile)│                                         │
│ │  └──┬───────┬───┘                                         │
│ │     │       │                                             │
│ │  Success  Error                                           │
│ │     │       │                                             │
│ │     ↓       └──→ [FAIL / exit 2]                         │
│ │  ┌──────────────┐                                         │
│ │  │ Test         │ ←─ TestStarted event                   │
│ │  │ (mvn test)   │                                         │
│ │  └──┬───────┬───┘                                         │
│ │     │       │                                             │
│ │  Success  Error                                           │
│ │     │       │                                             │
│ │     ↓       └──→ [FAIL / exit 2]                         │
│ │  ┌──────────────────┐                                    │
│ │  │ Validate         │ ←─ ConformanceCheckStarted        │
│ │  │ (H-Guards / Q)   │    InvariantCheckStarted           │
│ │  └──┬───────┬───────┘                                    │
│ │     │       │                                             │
│ │  Success  Violations                                      │
│ │     │       │                                             │
│ │     ↓       └──→ [FAIL / exit 2]                         │
│ │  ┌──────────────┐                                         │
│ │  │ Commit       │ ←─ CommitStarted event                │
│ │  │ (git add/    │                                         │
│ │  │  commit)     │                                         │
│ │  └──┬───────┬───┘                                         │
│ └────┼───────┼────────────────────────────────────────────┘
│      │       │
│      │       └──→ [FAIL / exit 2]
│      ↓
┌──────────────────────────────────────────────────────────────┐
│ Lane: Git                                                    │
│ ┌──────────────┐                                            │
│ │ Push Commit  │ ←─ PushStarted event                      │
│ │ (git push)   │                                            │
│ └──┬───────┬───┘                                            │
│    │       │                                                 │
│ Success  Error (network)                                    │
│    │       │                                                 │
│    ↓       └──→ [Retry Logic]                              │
│ ┌──────────────┐        ↓                                    │
│ │   GREEN      │   ┌─────────────┐                          │
│ │ (exit 0)     │   │ Wait 2s, 4s │                          │
│ │              │   │ 8s, 16s     │                          │
│ └──────┬───────┘   └──────┬──────┘                          │
│        │                  │                                  │
│        │                  ↓                                  │
│        │           Retry ← [Push Commit]                   │
│        │                  ↓                                  │
│        │           Success? → GREEN (exit 0)               │
│        │           Failure? → [FAIL / exit 2]              │
│        │                                                     │
└────────┼─────────────────────────────────────────────────────┘
         ↓
      [END]
```

### Phase Details & Event Emissions

#### Phase 1: Parse (tree-sitter)

```
Input: src/**/*.java
Process:
  1. Read all Java files
  2. Parse with tree-sitter-java
  3. Extract AST nodes
  4. Validate syntax

Events:
  ParseStarted
    {phase: "parse", file_count: 487, total_lines: 128492}
  ↓
  ParseSuccess
    {duration_ms: 450, ast_nodes: 12847, files_parsed: 487}
    OR
  ParseFailed
    {error: "SYNTAX_ERROR at line 427 column 15 in YWorkItem.java"}

Output: .yawl-ast/*.ast (serialized AST)
Exit Code: 0 (success) or 1 (error)
Next Phase: Compile (if success)
Fallback: FAIL (if error)
```

#### Phase 2: Compile (Maven)

```
Input: pom.xml modules, .yawl-ast/*.ast
Process:
  1. Trigger: mvn clean compile
  2. Maven resolves dependencies
  3. javac compiles each module in order
  4. Emit warnings/errors

Events:
  CompileStarted
    {module: "yawl-engine", goal: "compile"}
  ↓
  Per-module:
    CompileSuccess (yawl-engine)
      {duration_ms: 1200, class_count: 156, warnings: 3}
    CompileSuccess (yawl-elements)
      {duration_ms: 800, class_count: 89, warnings: 0}
    ... (repeat for each module)
  ↓
  CompileSuccess (all)
    {modules_compiled: 5, total_duration_ms: 4300}
    OR
  CompileFailed
    {error: "incompatible types: String vs int at YNetRunner.java:512"}

Output: target/classes (compiled bytecode)
Exit Code: 0 (success) or 2 (error)
Next Phase: Test (if success)
Fallback: FAIL (if error)
```

#### Phase 3: Test (JUnit + Maven)

```
Input: target/classes, src/test/**/*.java
Process:
  1. Trigger: mvn test
  2. JUnit discovers test classes
  3. Run tests in parallel (if configured)
  4. Aggregate results

Events:
  TestStarted
    {module: "yawl-engine", test_suite: "YNetRunnerTest", test_count: 127}
  ↓
  Test-level events (optional):
    TestCaseStarted (YNetRunnerTest.testDeadlockDetection)
    TestCaseSuccess (duration_ms: 2300)
    ... or TestCaseFailed
  ↓
  TestSuccess (module)
    {tests_run: 127, tests_passed: 127, tests_failed: 0, coverage: "87.3%"}
  ↓
  TestSuccess (all)
    {total_tests: 469, passed: 469, failed: 0, coverage: "89.1%"}
    OR
  TestFailed
    {failures: [
      {test: "testDeadlock", message: "expected timeout=5s, got 12s"}
    ]}

Output: target/surefire-reports/*.xml (test results)
Exit Code: 0 (success) or 2 (error)
Next Phase: Validate (if success)
Fallback: FAIL (if error)
```

#### Phase 4: Validate (H-Guards + Q-Invariants)

```
Input: Generated code (or src code for validation), compiled classes
Process:
  (See detailed Validate section below)

Events:
  ConformanceCheckStarted
    {patterns: ["H_TODO", "H_MOCK", ...], file_count: 487}
  ↓
  ConformanceCheckPassed
    {violations: 0}
    OR
  GuardViolationDetected (multiple)
    {pattern: "H_TODO", file: "YWorkItem.java", line: 427}
  ↓
  ConformanceCheckFailed
    {violations: 3, error_summary: "..."}
  ↓
  InvariantCheckStarted
    {invariants: ["real_impl ∨ throw", ...]}
  ↓
  InvariantCheckPassed
    {invariants_satisfied: 3}
    OR
  InvariantCheckFailed
    {violated_invariant: "real_impl ∨ throw", reason: "..."}

Output: .claude/receipts/guard-receipt.json, invariant-receipt.json
Exit Code: 0 (success) or 2 (error)
Next Phase: Commit (if success)
Fallback: FAIL (if error, do NOT commit)
```

#### Phase 5: Commit (Git)

```
Input: Modified files (pom.xml, .java files, .md docs, etc.)
Process:
  1. git add <specific files>
  2. git commit -m "message"
  3. Verify commit created

Events:
  CommitStarted
    {branch: "claude/refactor-wiggum-abc123", files: 5, lines_added: 427}
  ↓
  CommitSuccess
    {commit_hash: "a1b2c3d4...", files_changed: 3, insertions: 427}
    OR
  CommitFailed
    {error: "nothing to commit" or "hook rejected"}

Output: .git/refs/heads/claude/refactor-wiggum-abc123 (new commit)
Exit Code: 0 (success) or 2 (error)
Next Phase: Push (if success)
Fallback: FAIL (if error)
```

#### Phase 6: Push (Git Remote)

```
Input: New commit, remote origin
Process:
  1. git push -u origin <branch>
  2. Check remote response
  3. On failure: retry with exponential backoff

Events:
  PushStarted
    {remote: "origin", branch: "claude/refactor-wiggum-abc123"}
  ↓
  PushSuccess
    {commits_pushed: 1, remote_url: "github.com/..."}
    OR
  PushFailed (transient)
    {error: "connection timeout", retry_count: 0, next_retry_in_seconds: 2}
    → Wait 2s, retry
    OR
  PushFailed (transient, retry 1)
    {retry_count: 1, next_retry_in_seconds: 4}
    → Wait 4s, retry
    OR
  PushFailed (terminal)
    {error: "403 Forbidden: invalid credentials", retry_count: 4}
    → FAIL, human intervention needed

Output: Remote branch created/updated
Exit Code: 0 (GREEN) or 2 (FAIL)
Terminal: END
```

---

## II. Validate Process (H-Guards → Q-Invariants)

### Detailed Validation Workflow

```
START (Validate Phase)
  ↓
┌─────────────────────────────────────────────┐
│ H Phase: Guard Conformance Checks           │
├─────────────────────────────────────────────┤
│ [Scan Code for H_TODO]                      │
│   ├─ Find: // TODO: ...                     │
│   ├─ Event: GuardViolationDetected(H_TODO)  │
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_MOCK]                      │
│   ├─ Find: class Mock* or mock* methods     │
│   ├─ Event: GuardViolationDetected(H_MOCK)  │
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_STUB]                      │
│   ├─ Find: return ""; or return null;       │
│   ├─ Event: GuardViolationDetected(H_STUB)  │
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_EMPTY]                     │
│   ├─ Find: void method { }                  │
│   ├─ Event: GuardViolationDetected(H_EMPTY) │
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_FALLBACK]                  │
│   ├─ Find: catch { return fake; }           │
│   ├─ Event: GuardViolationDetected(H_FALLBACK)│
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_LIE]                       │
│   ├─ Find: @return != code return           │
│   ├─ Event: GuardViolationDetected(H_LIE)   │
│   └─ Result: violations[]                   │
│                                              │
│ [Scan Code for H_SILENT]                    │
│   ├─ Find: log.error("not impl");           │
│   ├─ Event: GuardViolationDetected(H_SILENT)│
│   └─ Result: violations[]                   │
│                                              │
│ [Aggregate Violations]                      │
│   ├─ Total: count(violations)               │
│   ├─ Event: ConformanceCheckPassed          │
│   │         if count == 0                   │
│   └─ Event: ConformanceCheckFailed          │
│             if count > 0                    │
└─────────────────────────────────────────────┘
  ↓
  Violations = 0?
  ├─ NO → [FAIL / report violations] (exit 2)
  └─ YES → continue
  ↓
┌─────────────────────────────────────────────┐
│ Q Phase: Invariant Checks                   │
├─────────────────────────────────────────────┤
│ [Invariant 1: real_impl ∨ throw]            │
│   ├─ Verify: every method either:           │
│   │   • has real implementation, OR         │
│   │   • throws UnsupportedOperationException│
│   ├─ Event: InvariantCheckStarted           │
│   └─ Event: InvariantCheckPassed/Failed     │
│                                              │
│ [Invariant 2: ¬mock ∧ ¬stub ∧ ¬silent]     │
│   ├─ Verify: no deceptive patterns          │
│   ├─ Combine with H results                 │
│   └─ Event: InvariantCheckPassed/Failed     │
│                                              │
│ [Invariant 3: code ≈ documentation]         │
│   ├─ Verify: Javadoc matches actual code    │
│   ├─ @return type = actual return type      │
│   ├─ @throws X = throws X in code           │
│   └─ Event: InvariantCheckPassed/Failed     │
│                                              │
│ [Aggregate Invariant Results]               │
│   ├─ All satisfied?                         │
│   └─ Event: InvariantCheckPassed/Failed     │
└─────────────────────────────────────────────┘
  ↓
  All Invariants Satisfied?
  ├─ NO → [FAIL / report violations] (exit 2)
  └─ YES → [SUCCESS] (exit 0)
  ↓
[Generate Validation Receipts]
  ├─ guard-receipt.json (H results)
  ├─ invariant-receipt.json (Q results)
  └─ validation-summary.json (both)
  ↓
[END Validate Phase]
```

### Guard Pattern Detection (H Phase)

Each guard pattern is detected as a separate process:

```
H_TODO Detection Process:
  Input: all .java files
  ↓
  for file in src/**/*.java:
    for line in file.readLines():
      if REGEX_MATCH(line, /\/\/\s*(TODO|FIXME|XXX|HACK)/):
        emit GuardViolationDetected(H_TODO, file, line_num, line_content)
  ↓
  Output: violations[] or empty
```

Parallel execution (optional, for performance):
```
[Parse for H_TODO] ──┐
[Parse for H_MOCK] ──├─→ [Merge Violations]
[Parse for H_STUB] ──┤   ↓
  ... (all 7)        └─→ emit ConformanceCheckPassed/Failed
```

### Invariant Verification (Q Phase)

Invariant checks must verify reachability in the code's state space:

```
Invariant 1: real_impl ∨ throw

For each method M:
  1. Check if body has real implementation
     (not TODO, not mock, not stub, not fallback)
  2. Check if body throws UnsupportedOperationException
  3. If neither → Invariant violated for M

Result: all methods satisfy (real ∨ throw)?
  YES → InvariantCheckPassed
  NO  → InvariantCheckFailed
        {violated_methods: [M1, M2, ...]}
```

---

## III. Team Orchestration Process

### Team Dispatch & Consolidation

```
START (Team Formation)
  ↓
[Create Team]
  ├─ Assign team_id: "τ-engine+schema+test-abc123"
  ├─ Assign N teammates: {tm-1, tm-2, tm-3}
  └─ Event: TeamCreated
  ↓
[Dispatch Tasks]
  ├─ Assign Task1 to tm-1 (engine refactoring)
  │  └─ Event: TaskAssigned
  ├─ Assign Task2 to tm-2 (schema design)
  │  └─ Event: TaskAssigned
  └─ Assign Task3 to tm-3 (test coverage)
     └─ Event: TaskAssigned
  ↓
┌──────────────────────────────────────────────────────────────────┐
│ Parallel Execution (Non-blocking Event Streams)                   │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Teammate 1 (Engine):          Teammate 2 (Schema):               │
│ ┌─────────────────────────┐   ┌──────────────────────────┐      │
│ │ evt1: TaskStarted       │   │ evt5: TaskStarted        │      │
│ │ evt2: FileModified      │   │ evt6: FileCreated        │      │
│ │ evt3: CodeReviewNeeded  │   │ evt7: SchemaPublished    │      │
│ │ evt4: Checkpoint        │ ↔ │ evt8: MessageSent to tm1 │      │
│ │ ...                     │ (↓)│ evt9: MessageAckReceived │      │
│ │ evt20: TaskCompleted    │   │ evt10: Checkpoint        │      │
│ │ GREEN: dx.sh passed     │   │ evt11: TaskCompleted     │      │
│ │                         │   │ GREEN: dx.sh passed      │      │
│ └─────────────────────────┘   └──────────────────────────┘      │
│         ↓ (no file conflicts)       ↓ (no file conflicts)        │
│                                                                   │
│ Teammate 3 (Test):                                               │
│ ┌──────────────────────────────────────┐                        │
│ │ evt12: TaskStarted                   │                        │
│ │ evt13: TestCaseCreated               │                        │
│ │ evt14: TestCasePassed (bulk)         │                        │
│ │ evt15: CoverageMetrics: 92%          │                        │
│ │ evt16: Checkpoint                    │                        │
│ │ ...                                  │                        │
│ │ evt25: TaskCompleted                 │                        │
│ │ GREEN: dx.sh passed                  │                        │
│ └──────────────────────────────────────┘                        │
│         ↓                                                        │
│ Synchronization Point:                                           │
│   All three teammates report TaskCompleted + GREEN             │
│   ↓                                                              │
│   [Synchronization Gate] ← All ready?                           │
│   └─→ YES: Proceed to consolidation                            │
│   └─→ TIMEOUT (30min): Message, wait 5min, then reassign      │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
  ↓
[Lead Consolidation]
  ├─ Event: TeamConsolidationStarted
  ├─ Merge all event streams
  ├─ Verify no conflicts (check shared-src.json)
  ├─ git add <specific files from all teammates>
  ├─ dx.sh all (final validation)
  ├─ git commit (atomic commit with trace reference)
  ├─ git push
  └─ Event: TeamConsolidated or TeamConsolidationFailed
  ↓
  Consolidation Success?
  ├─ YES → GREEN (ready to merge)
  └─ NO → Identify failure, message teammates, fix, retry
  ↓
[END Team Orchestration]
```

### Message Protocol (Teammate to Teammate)

```
Teammate 1 → Teammate 2:
  Event: MessageSent
    {
      from: "tm-1",
      to: "tm-2",
      sequence: 42,
      timestamp: "2026-03-06T01:35:00Z",
      content: "API contract: YNetRunner.execute() returns Future<YWorkItem>",
      timeout: "15min"
    }
  ↓
  Teammate 2 receives, processes:
    Event: MessageReceived (internal, not transmitted back)
    ... update local implementation ...
  ↓
  Teammate 2 → Teammate 1:
    Event: MessageAckReceived
      {
        ack_sequence: 42,
        timestamp: "2026-03-06T01:35:30Z",
        status: "understood"
      }
```

### Timeout & Recovery

```
Timeout Scenario: Teammate 1 idle >30 min

[Lead Monitoring] ← heartbeat not updated
  ↓
  Event: TeammateTimeout
  ↓
[Send Recovery Message]
  ├─ Message: "Status check: are you still working?"
  ├─ Timeout: 5 minutes for response
  └─ Event: RecoveryMessageSent
  ↓
  Response Received?
  ├─ YES: Event: TeammateRecovered
  │       Continue task
  └─ NO: Event: TeammateUnresponsive
         Halt team task, save checkpoint
         Reassign to new teammate (with checkpoint)
```

---

## IV. Agent Autonomous Execution

### Agent Task Lifecycle

```
START (Agent Dispatch)
  ↓
[Create Agent]
  ├─ Agent ID: "agent-yawl-engineer-001"
  ├─ Agent Type: "yawl-engineer"
  ├─ Task: "Research VirtualThread performance on YNetRunner"
  └─ Event: AgentStarted
  ↓
[Execute Task Autonomously]
  ├─ Event: AgentTaskAssigned
  │
  ├─ [Subtask 1: Investigate]
  │   ├─ Event: AgentCheckpoint (state snapshot)
  │   └─ Findings stored
  │
  ├─ [Subtask 2: Analyze]
  │   ├─ Event: AgentCheckpoint
  │   └─ Findings stored
  │
  ├─ [Subtask 3: Validate]
  │   ├─ Event: AgentCheckpoint
  │   └─ Findings reproducible
  │
  └─ Event: AgentTaskCompleted
     {
       findings: ["VirtualThreads: -95% memory", "+3x throughput"],
       artifacts: ["RESEARCH.md", "benchmark.json"],
       verification: "all reproducible"
     }
  ↓
[Lead Review & Consolidation]
  ├─ Read agent findings
  ├─ Verify artifacts
  ├─ Make decision: use findings in next phase
  └─ Merge into team trace (if applicable)
  ↓
[END Agent Task]
```

### Agent Crash & Recovery

```
Agent crashes (context lost):
  ↓
[Detect Crash]
  Event: AgentCrash
  ↓
[Check Checkpoint]
  ├─ Load last saved state
  │   (timestamp, files processed, findings so far)
  └─ Event: AgentRecovered (restart from checkpoint)
  ↓
[Resume Task]
  ├─ Skip already-processed work
  └─ Continue from checkpoint
  ↓
[Verify Recovery]
  ├─ Findings consistent with pre-crash state?
  ├─ YES → Continue
  └─ NO → Report inconsistency, manual review needed
```

---

## V. Reference: Process Conformance Metrics

### Trace Conformance Analysis

For each executed trace, compute:

| Metric | Formula | Interpretation |
|--------|---------|-----------------|
| **Process Deviation** | events_in_trace ÷ expected_event_sequence | 100% = perfect conformance |
| **Phase Completion** | completed_phases ÷ total_phases | 100% = all phases ran |
| **Guard Violations** | violations_detected | 0 = no conformance issues |
| **Invariant Satisfaction** | satisfied_invariants ÷ total_invariants | 100% = all reachability constraints met |
| **Critical Path** | max(phase durations) | Bottleneck identification |
| **Trace Success Rate** | successful_traces ÷ total_traces | Stability metric |

### Example: Conformance Report

```json
{
  "trace_id": "tr-2026-03-06-001",
  "conformance": {
    "phase_completion": "100%",
    "events_expected": 14,
    "events_observed": 14,
    "deviation": "0%",
    "guard_violations": 0,
    "invariants_satisfied": 3,
    "verdict": "FULLY_CONFORMS"
  },
  "performance": {
    "critical_path_phase": "test",
    "critical_path_duration_ms": 18500,
    "critical_path_percentage": "56.5%",
    "total_duration_ms": 32700
  }
}
```

---

## GODSPEED. ✈️

*A process is only as strong as its observable event sequence. Specification without events is metaphor; events without specification is chaos.*
