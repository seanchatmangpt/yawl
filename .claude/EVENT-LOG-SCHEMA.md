# Event Log Schema: Formal Specification & Examples

**Framework**: Van der Aalst Process Mining + YAWL Execution Events
**Purpose**: Define all event types, trace format, and serialization for process mining
**Status**: Complete formal specification
**Last Updated**: 2026-03-06

---

## I. Core Event Structure (JSON Schema)

All events conform to this canonical form:

```json
{
  "event_id": "string (UUID)",
  "trace_id": "string (UUID, references this execution)",
  "timestamp": "string (ISO8601, UTC)",
  "sequence_number": "integer (per trace, 0-indexed)",
  "phase": "enum (parse|compile|test|validate|commit|team|agent)",
  "actor": "string (executor name: maven, git, hyper-validate, engineer-1, agent-engine)",
  "event_type": "string (Verb+Noun, e.g., CompileStarted, TestFailed)",
  "state_before": "object (execution state pre-event)",
  "state_after": "object (execution state post-event)",
  "duration_ms": "integer (0 for instantaneous, elapsed for phase)",
  "artifacts": "[string] (file paths created/modified/deleted)",
  "status": "enum (success|failure|timeout|cancelled)",
  "error": "object or null (if status=failure)",
  "metadata": "object (event-specific context)"
}
```

### Validation Rules

1. **event_id**: UUID4, globally unique across all traces
2. **trace_id**: Same UUID for all events in one execution
3. **sequence_number**: 0..N, strictly increasing per trace
4. **timestamp**: ISO8601 UTC, monotonically increasing within trace
5. **phase**: Lowercase, one of {parse, compile, test, validate, commit, team, agent}
6. **status**: One of {success, failure, timeout, cancelled}
7. **artifacts**: List of absolute paths, may be empty
8. **duration_ms**: ≥ 0, must be reasonable (<1 hour for any phase)

---

## II. Phase-Specific Event Types

### PARSE Phase (Pre-Build)

```json
{
  "event_type": "ParseStarted",
  "phase": "parse",
  "metadata": {
    "file_count": 487,
    "total_lines": 128492,
    "parser": "tree-sitter-java"
  }
}
```

```json
{
  "event_type": "ParseSuccess",
  "phase": "parse",
  "status": "success",
  "duration_ms": 450,
  "state_after": {
    "ast_nodes": 12847,
    "files_parsed": 487
  },
  "artifacts": [".yawl-ast/yawl-engine.ast", ".yawl-ast/yawl-elements.ast"]
}
```

```json
{
  "event_type": "ParseFailed",
  "phase": "parse",
  "status": "failure",
  "duration_ms": 123,
  "error": {
    "code": "SYNTAX_ERROR",
    "message": "Unexpected token at line 427 in YWorkItem.java",
    "file": "yawl-engine/src/main/java/org/yawl/engine/YWorkItem.java",
    "line": 427,
    "column": 15
  }
}
```

### COMPILE Phase (Maven)

```json
{
  "event_type": "CompileStarted",
  "phase": "compile",
  "actor": "maven-executor",
  "metadata": {
    "module": "yawl-engine",
    "goal": "compile",
    "maven_version": "3.9.2"
  }
}
```

```json
{
  "event_type": "CompileSuccess",
  "phase": "compile",
  "status": "success",
  "duration_ms": 2300,
  "state_before": { "modules_compiled": 4 },
  "state_after": { "modules_compiled": 5 },
  "artifacts": [
    "yawl-engine/target/classes",
    "yawl-engine/target/generated-sources"
  ],
  "metadata": {
    "module": "yawl-engine",
    "class_count": 156,
    "warnings": 3
  }
}
```

```json
{
  "event_type": "CompileFailed",
  "phase": "compile",
  "status": "failure",
  "duration_ms": 1200,
  "state_before": { "modules_compiled": 4 },
  "state_after": { "modules_compiled": 4 },
  "error": {
    "code": "COMPILATION_ERROR",
    "message": "[ERROR] error: incompatible types",
    "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
    "line": 512,
    "details": "required: String, found: int"
  }
}
```

### TEST Phase (JUnit + Maven)

```json
{
  "event_type": "TestStarted",
  "phase": "test",
  "actor": "maven-executor",
  "metadata": {
    "module": "yawl-engine",
    "test_suite": "org.yawl.engine.YNetRunnerTest",
    "test_count": 127
  }
}
```

```json
{
  "event_type": "TestSuccess",
  "phase": "test",
  "status": "success",
  "duration_ms": 18500,
  "state_before": { "tests_passed": 342, "tests_failed": 0 },
  "state_after": { "tests_passed": 469, "tests_failed": 0 },
  "metadata": {
    "module": "yawl-engine",
    "tests_run": 127,
    "tests_passed": 127,
    "tests_failed": 0,
    "tests_skipped": 0,
    "coverage": "87.3%"
  }
}
```

```json
{
  "event_type": "TestFailed",
  "phase": "test",
  "status": "failure",
  "duration_ms": 4200,
  "state_before": { "tests_passed": 342, "tests_failed": 0 },
  "state_after": { "tests_passed": 345, "tests_failed": 2 },
  "error": {
    "code": "TEST_FAILURE",
    "message": "2 test(s) failed in org.yawl.engine.YNetRunnerTest",
    "failures": [
      {
        "test": "testDeadlockDetection",
        "file": "yawl-engine/src/test/java/org/yawl/engine/YNetRunnerTest.java",
        "line": 512,
        "assertion": "Expected timeout=5s, got 12s",
        "stack_trace": "..."
      }
    ]
  }
}
```

### VALIDATE Phase (H Guards + Q Invariants)

#### H-Guards (Trace Conformance)

```json
{
  "event_type": "ConformanceCheckStarted",
  "phase": "validate",
  "actor": "hyper-validate.sh",
  "metadata": {
    "patterns": ["H_TODO", "H_MOCK", "H_STUB", "H_EMPTY", "H_FALLBACK", "H_LIE", "H_SILENT"],
    "file_count": 487
  }
}
```

```json
{
  "event_type": "GuardViolationDetected",
  "phase": "validate",
  "status": "failure",
  "metadata": {
    "pattern": "H_TODO",
    "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
    "line": 427,
    "content": "// TODO: Add deadlock detection",
    "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
  }
}
```

```json
{
  "event_type": "ConformanceCheckPassed",
  "phase": "validate",
  "status": "success",
  "duration_ms": 4900,
  "state_after": {
    "violations_found": 0,
    "files_scanned": 487
  },
  "metadata": {
    "h_todo_violations": 0,
    "h_mock_violations": 0,
    "h_stub_violations": 0,
    "h_empty_violations": 0,
    "h_fallback_violations": 0,
    "h_lie_violations": 0,
    "h_silent_violations": 0,
    "total_violations": 0
  }
}
```

```json
{
  "event_type": "ConformanceCheckFailed",
  "phase": "validate",
  "status": "failure",
  "duration_ms": 2100,
  "state_after": {
    "violations_found": 3,
    "files_scanned": 487
  },
  "error": {
    "code": "CONFORMANCE_VIOLATION",
    "violations": [
      { "pattern": "H_TODO", "file": "YNetRunner.java", "line": 427 },
      { "pattern": "H_MOCK", "file": "MockDataService.java", "line": 12 },
      { "pattern": "H_STUB", "file": "YDataProvider.java", "line": 89 }
    ]
  }
}
```

#### Q-Invariants (State Reachability)

```json
{
  "event_type": "InvariantCheckStarted",
  "phase": "validate",
  "actor": "invariant-validator",
  "metadata": {
    "invariants": [
      "real_impl ∨ throw",
      "¬mock ∧ ¬stub ∧ ¬silent_fallback ∧ ¬lie",
      "code ≈ documentation"
    ]
  }
}
```

```json
{
  "event_type": "InvariantCheckPassed",
  "phase": "validate",
  "status": "success",
  "duration_ms": 3100,
  "state_after": {
    "invariants_satisfied": 3,
    "reachability_verified": true
  },
  "metadata": {
    "invariant_1_real_impl_throw": "satisfied",
    "invariant_2_no_deception": "satisfied",
    "invariant_3_code_equals_docs": "satisfied"
  }
}
```

```json
{
  "event_type": "InvariantCheckFailed",
  "phase": "validate",
  "status": "failure",
  "duration_ms": 1800,
  "state_after": {
    "invariants_satisfied": 2,
    "reachability_verified": false
  },
  "error": {
    "code": "INVARIANT_VIOLATION",
    "violated_invariant": "real_impl ∨ throw",
    "reason": "Method returns fake empty list instead of throwing or returning real data",
    "file": "yawl-engine/src/main/java/org/yawl/engine/YDataProvider.java",
    "method": "getData()",
    "line": 89
  }
}
```

### COMMIT Phase (Git)

```json
{
  "event_type": "CommitStarted",
  "phase": "commit",
  "actor": "git-executor",
  "metadata": {
    "branch": "claude/refactor-wiggum-abc123",
    "files_to_stage": 5,
    "lines_added": 427,
    "lines_deleted": 89
  }
}
```

```json
{
  "event_type": "CommitSuccess",
  "phase": "commit",
  "status": "success",
  "duration_ms": 1100,
  "artifacts": [
    ".claude/FIRST-PRINCIPLES.md",
    ".claude/EVENT-LOG-SCHEMA.md",
    "CLAUDE.md"
  ],
  "metadata": {
    "commit_hash": "a1b2c3d4e5f6g7h8i9j0",
    "commit_message": "Refactor CLAUDE.md to first principles (wiggum pattern)",
    "files_changed": 3,
    "insertions": 427,
    "deletions": 89,
    "trace_reference": "tr-2026-03-06-001"
  }
}
```

```json
{
  "event_type": "PushStarted",
  "phase": "commit",
  "actor": "git-executor",
  "metadata": {
    "remote": "origin",
    "branch": "claude/refactor-wiggum-abc123"
  }
}
```

```json
{
  "event_type": "PushSuccess",
  "phase": "commit",
  "status": "success",
  "duration_ms": 2300,
  "metadata": {
    "remote": "origin",
    "branch": "claude/refactor-wiggum-abc123",
    "remote_url": "github.com/seanchatmangpt/yawl.git",
    "commits_pushed": 1
  }
}
```

```json
{
  "event_type": "PushFailed",
  "phase": "commit",
  "status": "failure",
  "duration_ms": 1800,
  "error": {
    "code": "NETWORK_ERROR",
    "message": "Connection timeout connecting to GitHub",
    "remote": "origin",
    "retry_count": 0,
    "next_retry_in_seconds": 2
  }
}
```

### TEAM Phase (Parallel Orchestration)

```json
{
  "event_type": "TeamCreated",
  "phase": "team",
  "actor": "lead-orchestrator",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "teammate_count": 3,
    "quantums": ["engine", "schema", "test"],
    "estimated_duration_seconds": 600
  }
}
```

```json
{
  "event_type": "TaskAssigned",
  "phase": "team",
  "actor": "lead-orchestrator",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "teammate_id": "tm-1",
    "task_id": "refactor-engine-core",
    "task_description": "Refactor YNetRunner to use VirtualThreads",
    "estimated_duration_seconds": 180
  }
}
```

```json
{
  "event_type": "TaskCompleted",
  "phase": "team",
  "status": "success",
  "duration_ms": 165000,
  "actor": "tm-1",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "teammate_id": "tm-1",
    "task_id": "refactor-engine-core",
    "files_changed": 3,
    "commits_created": 1,
    "verification": "dx.sh -pl yawl-engine passed, GREEN"
  }
}
```

```json
{
  "event_type": "MessageSent",
  "phase": "team",
  "actor": "tm-1",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "from_teammate": "tm-1",
    "to_teammate": "tm-2",
    "message_type": "coordination",
    "content": "API contract: YNetRunner.execute() returns Future<YWorkItem>",
    "sequence_number": 42
  }
}
```

```json
{
  "event_type": "MessageAckReceived",
  "phase": "team",
  "status": "success",
  "actor": "tm-2",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "ack_sequence_number": 42,
    "received_timestamp": "2026-03-06T01:35:12.456Z"
  }
}
```

```json
{
  "event_type": "TeammateTimeout",
  "phase": "team",
  "status": "failure",
  "actor": "lead-orchestrator",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "teammate_id": "tm-1",
    "last_heartbeat": "2026-03-06T01:34:00.000Z",
    "current_time": "2026-03-06T01:35:15.000Z",
    "timeout_seconds": 75,
    "action_taken": "message_sent_to_recover"
  }
}
```

```json
{
  "event_type": "TeamConsolidated",
  "phase": "team",
  "status": "success",
  "duration_ms": 187000,
  "actor": "lead-orchestrator",
  "metadata": {
    "team_id": "τ-engine+schema+test-abc123",
    "teammates_completed": 3,
    "total_commits": 3,
    "total_files_changed": 8,
    "consolidation_action": "dx.sh all passed, ready to push"
  }
}
```

### AGENT Phase (Autonomous Execution)

```json
{
  "event_type": "AgentStarted",
  "phase": "agent",
  "actor": "agent-orchestrator",
  "metadata": {
    "agent_id": "agent-yawl-engineer-001",
    "agent_type": "yawl-engineer",
    "task": "refactor dx.sh to use event logs",
    "session_id": "sess-abc123"
  }
}
```

```json
{
  "event_type": "AgentTaskAssigned",
  "phase": "agent",
  "actor": "agent-orchestrator",
  "metadata": {
    "agent_id": "agent-yawl-engineer-001",
    "task_id": "research-vthread-performance",
    "task_type": "research",
    "description": "Investigate VirtualThread performance on YNetRunner",
    "estimated_duration_seconds": 300
  }
}
```

```json
{
  "event_type": "AgentCheckpoint",
  "phase": "agent",
  "status": "success",
  "actor": "agent-yawl-engineer-001",
  "metadata": {
    "agent_id": "agent-yawl-engineer-001",
    "checkpoint_id": "ckpt-001",
    "state_snapshot": {
      "current_file": "scripts/dx.sh",
      "lines_parsed": 847,
      "findings": [
        "Phase ordering: Ψ→Λ→H→Q→Ω",
        "Observable events approach needed"
      ]
    },
    "timestamp": "2026-03-06T01:40:30.000Z"
  }
}
```

```json
{
  "event_type": "AgentTaskCompleted",
  "phase": "agent",
  "status": "success",
  "duration_ms": 298000,
  "actor": "agent-yawl-engineer-001",
  "metadata": {
    "agent_id": "agent-yawl-engineer-001",
    "task_id": "research-vthread-performance",
    "findings": "VirtualThreads reduce memory by 95%, throughput +3x, latency -25%",
    "artifacts_produced": [
      "RESEARCH-VTHREAD-PERFORMANCE.md",
      "performance-benchmark-results.json"
    ],
    "verification": "All findings backed by experimental data, reproducible"
  }
}
```

---

## III. Trace Format (Complete Example)

A full trace representing one `dx.sh all` run:

```json
{
  "trace_id": "tr-2026-03-06-001",
  "start_timestamp": "2026-03-06T01:30:45.123Z",
  "end_timestamp": "2026-03-06T01:31:17.823Z",
  "total_duration_ms": 32700,
  "status": "success",
  "actor": "claude-code-session-abc123",
  "events": [
    {
      "event_id": "evt-parse-001",
      "timestamp": "2026-03-06T01:30:45.123Z",
      "sequence_number": 0,
      "event_type": "ParseStarted",
      "phase": "parse"
    },
    {
      "event_id": "evt-parse-002",
      "timestamp": "2026-03-06T01:30:45.573Z",
      "sequence_number": 1,
      "event_type": "ParseSuccess",
      "phase": "parse",
      "duration_ms": 450,
      "status": "success"
    },
    {
      "event_id": "evt-compile-001",
      "timestamp": "2026-03-06T01:30:45.573Z",
      "sequence_number": 2,
      "event_type": "CompileStarted",
      "phase": "compile"
    },
    {
      "event_id": "evt-compile-002",
      "timestamp": "2026-03-06T01:30:49.873Z",
      "sequence_number": 3,
      "event_type": "CompileSuccess",
      "phase": "compile",
      "duration_ms": 4300,
      "status": "success",
      "artifacts": ["yawl-engine/target/classes"]
    },
    {
      "event_id": "evt-test-001",
      "timestamp": "2026-03-06T01:30:49.873Z",
      "sequence_number": 4,
      "event_type": "TestStarted",
      "phase": "test"
    },
    {
      "event_id": "evt-test-002",
      "timestamp": "2026-03-06T01:31:08.373Z",
      "sequence_number": 5,
      "event_type": "TestSuccess",
      "phase": "test",
      "duration_ms": 18500,
      "status": "success"
    },
    {
      "event_id": "evt-validate-001",
      "timestamp": "2026-03-06T01:31:08.373Z",
      "sequence_number": 6,
      "event_type": "ConformanceCheckStarted",
      "phase": "validate"
    },
    {
      "event_id": "evt-validate-002",
      "timestamp": "2026-03-06T01:31:12.573Z",
      "sequence_number": 7,
      "event_type": "ConformanceCheckPassed",
      "phase": "validate",
      "duration_ms": 4200,
      "status": "success"
    },
    {
      "event_id": "evt-validate-003",
      "timestamp": "2026-03-06T01:31:12.573Z",
      "sequence_number": 8,
      "event_type": "InvariantCheckStarted",
      "phase": "validate"
    },
    {
      "event_id": "evt-validate-004",
      "timestamp": "2026-03-06T01:31:15.773Z",
      "sequence_number": 9,
      "event_type": "InvariantCheckPassed",
      "phase": "validate",
      "duration_ms": 3200,
      "status": "success"
    },
    {
      "event_id": "evt-commit-001",
      "timestamp": "2026-03-06T01:31:15.773Z",
      "sequence_number": 10,
      "event_type": "CommitStarted",
      "phase": "commit"
    },
    {
      "event_id": "evt-commit-002",
      "timestamp": "2026-03-06T01:31:16.873Z",
      "sequence_number": 11,
      "event_type": "CommitSuccess",
      "phase": "commit",
      "duration_ms": 1100,
      "status": "success"
    },
    {
      "event_id": "evt-commit-003",
      "timestamp": "2026-03-06T01:31:16.873Z",
      "sequence_number": 12,
      "event_type": "PushStarted",
      "phase": "commit"
    },
    {
      "event_id": "evt-commit-004",
      "timestamp": "2026-03-06T01:31:17.823Z",
      "sequence_number": 13,
      "event_type": "PushSuccess",
      "phase": "commit",
      "duration_ms": 950,
      "status": "success"
    }
  ],
  "phase_summary": {
    "parse": { "duration_ms": 450, "status": "success" },
    "compile": { "duration_ms": 4300, "status": "success" },
    "test": { "duration_ms": 18500, "status": "success" },
    "validate": { "duration_ms": 7400, "status": "success" },
    "commit": { "duration_ms": 2050, "status": "success" }
  },
  "metrics": {
    "critical_path_phase": "test",
    "critical_path_duration_ms": 18500,
    "critical_path_percentage": 56.5,
    "total_events": 14,
    "violations_detected": 0,
    "conformance": "pass",
    "invariants": "satisfied"
  }
}
```

---

## IV. Serialization & Storage

### JSON Lines Format (for log streams)

Each event on one line, suitable for streaming:

```
{"event_id":"evt-parse-001","timestamp":"2026-03-06T01:30:45.123Z",...}
{"event_id":"evt-parse-002","timestamp":"2026-03-06T01:30:45.573Z",...}
{"event_id":"evt-compile-001","timestamp":"2026-03-06T01:30:45.573Z",...}
```

### Storage Location

```
.yawl-logs/
├── traces/
│   ├── 2026-03-06/
│   │   ├── tr-2026-03-06-001.json  (full trace as single JSON)
│   │   ├── tr-2026-03-06-002.json
│   │   └── tr-2026-03-06-003.json
│   └── 2026-03-05/
│       └── tr-2026-03-05-001.json
└── streams/
    ├── build.jsonl              (all build events, append-only)
    ├── validate.jsonl           (all validation events)
    ├── commit.jsonl             (all git events)
    ├── team.jsonl               (all team orchestration)
    └── agent.jsonl              (all agent execution)
```

### Compression

For long-term storage, gzip with retention:
- **Keep online**: 7 days
- **Archive**: 30 days
- **Delete**: 1 year

---

## V. Querying Event Logs

### Common Queries (jq)

```bash
# Get all test failures
cat traces/2026-03-06/*.json | jq '.events[] | select(.event_type == "TestFailed")'

# Get critical path (longest phase per trace)
cat traces/2026-03-06/*.json | jq '.phase_summary | to_entries | sort_by(.value.duration_ms) | reverse | .[0]'

# Count violations by pattern
cat traces/2026-03-06/*.json | jq '[.events[] | select(.event_type == "GuardViolationDetected") | .metadata.pattern] | group_by(.) | map({pattern: .[0], count: length})'

# Get average test duration
cat traces/2026-03-06/*.json | jq '[.events[] | select(.event_type == "TestSuccess") | .duration_ms] | add / length'

# Get all failed commits (with reason)
cat traces/2026-03-06/*.json | jq '.events[] | select(.event_type == "CommitFailed") | {timestamp: .timestamp, error: .error.message}'
```

### Conformance Analysis

```bash
# Check if any trace skipped Validate phase
cat traces/2026-03-06/*.json | jq 'select(.events[].event_type | contains("Validate") | not)'

# Get traces with violations
cat traces/2026-03-06/*.json | jq 'select(.metrics.violations_detected > 0)'

# Get median critical path
cat traces/2026-03-06/*.json | jq '[.metrics.critical_path_duration_ms] | sort | .[length/2]'
```

---

## VI. Reference: Event Type Naming Convention

All event types follow: `<Verb><Noun>`

| Pattern | Example | Meaning |
|---------|---------|---------|
| `<Action>Started` | CompileStarted | Phase/action beginning |
| `<Action>Success` | CompileSuccess | Phase/action succeeded |
| `<Action>Failed` | CompileFailed | Phase/action failed |
| `<Noun>Detected` | GuardViolationDetected | Condition found |
| `<Noun>Assigned` | TaskAssigned | Resource allocated |
| `<Noun>Completed` | TaskCompleted | Work finished |
| `<Noun>Created/Deleted` | BranchCreated | Resource lifecycle |
| `<Noun>Ack` | MessageAckReceived | Confirmation received |

---

## VII. Validation

All events in a trace must satisfy:

```
1. Monotonic timestamps: event[i].timestamp ≤ event[i+1].timestamp
2. Sequence continuity: event[i].sequence_number = i
3. Causal consistency: state_after[i] ≥ state_before[i+1]
4. Phase order: parse → compile → test → validate → commit
5. No orphans: all artifact files exist or are intentionally deleted
6. Exit status: SUCCESS iff all phase events are *Success types
```

**Validation command**:
```bash
bash scripts/validate-event-log.sh .yawl-logs/traces/2026-03-06/tr-2026-03-06-001.json
```

---

## GODSPEED. ✈️

*An event log is the truth. All process understanding flows from measurable facts.*
