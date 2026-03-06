# YAWL v6.0.0 | Observable Process Architecture

**WIGGUM Principle**: "What Would Dr. Wil Van Der Aalst Do?" — Ground everything in observable events, never abstract notation.

---

## FIRST PRINCIPLES

Process excellence flows from observable events, not notation. Every phase emits measurable events with explicit state transitions.

### Observable Events Model

Every action produces an **event** with these properties:
- **Timestamp** (ISO8601 UTC): When did it happen?
- **Phase** (parse|compile|test|validate|commit|team|agent): Where?
- **Actor** (maven-executor, git-actor, engineer-1): Who/what?
- **Event Type** (CompileStarted, TestSuccess, GuardViolationDetected): What happened?
- **State Before/After**: What changed?
- **Artifacts**: Files created/modified?

**Why**: Events are facts. Facts are observable, measurable, reproducible. We make decisions from facts, never opinions.

### Process Flow: Event Stream

```
dx.sh all executes phases in strict sequence:
  Parse (tree-sitter) → ParseSuccess/ParseFailed event
    ↓
  Compile (Maven) → CompileSuccess/CompileFailed event
    ↓
  Test (JUnit) → TestSuccess/TestFailed event
    ↓
  Validate (H-Guards + Q-Invariants) → ConformanceCheckPassed/Failed + InvariantCheckPassed/Failed events
    ↓
  Commit (Git) → CommitSuccess/CommitFailed event
    ↓
  Push (Git Remote) → PushSuccess/PushFailed event
```

Each phase is **atomic**: either all events succeed (exit 0) or any event fails (exit 2).

### Three Pillars of Process Excellence

1. **Process Mining** (Discovery): Observe actual event traces from YAWL runs. Extract real process, not intended.
2. **Trace Conformance** (Verification): Does observed event sequence match specification? H-Guards detect deviations.
3. **Process Performance** (Optimization): Which phase is the bottleneck? Use event timing to optimize.

---

## BUILD PIPELINE: Observable Phases

### Overview

`dx.sh all` emits structured events for every phase transition. No phase skipping, no silent failures.

### Phase 1: Parse Code (tree-sitter)

**Input**: All Java source files
**Events Emitted**:
- `ParseStarted` {file_count, total_lines}
- `ParseSuccess` {duration_ms, ast_nodes} OR `ParseFailed` {error, line, column}

**Output**: Serialized AST → `.yawl-ast/`
**Exit Code**: 0 (success) or 1 (error)
**Next Phase**: Compile (if success) or FAIL

### Phase 2: Compile (Maven)

**Input**: pom.xml modules, AST
**Command**: `mvn clean compile`
**Events Emitted**:
- `CompileStarted` {module}
- Per-module: `CompileSuccess` {class_count, warnings} OR `CompileFailed` {error}
- Aggregate: `CompileSuccess` (all modules) OR `CompileFailed`

**Output**: target/classes (bytecode)
**Exit Code**: 0 (success) or 2 (error)
**Next Phase**: Test (if success) or FAIL

### Phase 3: Test (JUnit)

**Input**: Compiled classes, test sources
**Command**: `mvn test`
**Events Emitted**:
- `TestStarted` {test_suite, test_count}
- `TestSuccess` {tests_run, tests_passed, coverage%} OR `TestFailed` {failures}

**Output**: target/surefire-reports/*.xml
**Exit Code**: 0 (success) or 2 (error)
**Next Phase**: Validate (if success) or FAIL

### Phase 4: Validate (H-Guards + Q-Invariants)

**Input**: Generated/compiled code
**Events Emitted**:
- H Phase: `ConformanceCheckStarted` → GuardViolationDetected (if any) × 7 patterns → `ConformanceCheckPassed` or `ConformanceCheckFailed`
- Q Phase: `InvariantCheckStarted` → `InvariantCheckPassed` or `InvariantCheckFailed`

**Output**: guard-receipt.json, invariant-receipt.json
**Exit Code**: 0 (success) or 2 (violations found)
**Next Phase**: Commit (if success) or FAIL (do NOT commit)

See `.claude/CONFORMANCE-CHECKING.md` for full H/Q specifications.

### Phase 5: Commit (Git)

**Input**: Modified files
**Command**: `git add <files>` → `git commit -m "message"`
**Events Emitted**:
- `CommitStarted` {files, lines_added}
- `CommitSuccess` {commit_hash, files_changed} OR `CommitFailed` {error}

**Output**: New commit in .git/refs
**Exit Code**: 0 (success) or 2 (error)
**Next Phase**: Push (if success) or FAIL

### Phase 6: Push (Git Remote)

**Input**: New commit
**Command**: `git push -u origin <branch>`
**Events Emitted**:
- `PushStarted` {branch, remote}
- `PushSuccess` OR `PushFailed` {error, retry_count}
- **Retry Logic**: Exponential backoff (2s, 4s, 8s, 16s) for network errors

**Output**: Remote branch updated
**Exit Code**: 0 (GREEN, ready for merge) or 2 (FAIL)
**Terminal**: END

---

## OBSERVABILITY: Event Log Collection

Don't explore files to understand state. **Consult event logs instead.**

### Fact Files (Compressed Observatory)

Pre-computed facts replace grep search:
- `modules.json`: All modules, source roots, dependencies
- `shared-src.json`: Files written by multiple teammates (conflict detection)
- `tests.json`: Test counts, coverage, pass/fail trends
- `deps-conflicts.json`: Dependency version mismatches

**Why**: 50 tokens (facts) vs 5000 tokens (grep) = 100× compression.

### Refreshing Facts

```bash
bash scripts/observatory/observatory.sh   # Regenerate all facts
DX_SKIP_OBSERVE=1 dx.sh all               # Skip in CI (facts stale but fast)
```

Facts auto-refresh when `pom.xml` changes.

---

## H GUARDS: Trace Conformance

**Purpose**: Detect forbidden patterns (deceptive/incomplete code) before commit.

**Hook**: `hyper-validate.sh` runs on every `git commit`, blocks if violations found.

### Seven Guard Patterns

```
H_TODO     → // TODO/FIXME comments (code unfinished)
H_MOCK     → mock/stub/fake class/method names (deceptive)
H_STUB     → return ""/""/null/empty (fake data)
H_EMPTY    → void method { } (no-op)
H_FALLBACK → catch { return fake; } (silent error swallowing)
H_LIE      → @return never null but returns null (docs ≠ code)
H_SILENT   → log.error("not implemented") (logging instead of throwing)
```

**All violations block commit** (exit 2). Fix for real, never work around hook.

See `.claude/CONFORMANCE-CHECKING.md` for detailed patterns, examples, and fixes.

---

## Q INVARIANTS: State Reachability

**Purpose**: Verify code satisfies three core reachability constraints.

### Three Invariants

```
1. real_impl ∨ throw UnsupportedOperationException
   Every method must do real work OR explicitly declare not implemented.

2. ¬mock ∧ ¬stub ∧ ¬silent_fallback ∧ ¬lie
   Defense-in-depth: no deceptive patterns in code (semantic + syntactic).

3. code ≈ documentation
   Javadoc must match actual code (types, exceptions, behavior).
```

**All invariants must be satisfied** before commit (exit 0).

See `.claude/CONFORMANCE-CHECKING.md` for verification algorithm and examples.

---

## ATOMIC COMMITS: Immutable Event Traces

**Principle**: One commit = one atomic trace (all-or-nothing).

**Rules**:
- `git add <specific-files>` only, never `git add .`
- One logical change per commit
- Branch: `claude/<desc>-<sessionId>`
- Never `git push --force`
- Never amend published commits (create new commit instead)

**Commit Creates Event**:
```json
{
  "event_type": "CommitSuccess",
  "commit_hash": "a1b2c3d4...",
  "trace_reference": "tr-2026-03-06-001",
  "phase_events": {"parse": 1, "compile": 3, "test": 2, "validate": 2, "commit": 1}
}
```

The commit hash becomes the immutable, traceable reference for this execution.

---

## PARALLEL ORCHESTRATION: Teams

**When to use Teams**: N ∈ {2..5} orthogonal quantums with inter-team messaging.

### Team Formation & Dispatch

```
Lead creates team:
  ├─ Assign N teammates (separate 200K context windows)
  ├─ Event: TeamCreated
  └─ Assign tasks: Task1→tm-1, Task2→tm-2, ...
     Events: TaskAssigned (per teammate)

Parallel execution (non-blocking event streams):
  tm-1: TaskStarted → FileModified → Checkpoint → TaskCompleted (GREEN)
  tm-2: TaskStarted → FileCreated → MessageSent → Checkpoint → TaskCompleted (GREEN)
  tm-3: TaskStarted → TestPassed → Checkpoint → TaskCompleted (GREEN)

Synchronization:
  All teammates report TaskCompleted + GREEN
  → Lead consolidates: dx.sh all → commit → push
```

**Constraints**:
- No teammate overlap on same file (verify shared-src.json)
- Message timeouts: 15min critical, 30min idle
- Lead runs final dx.sh all (H + Q must pass)

**Error Recovery**: See `.claude/rules/TEAMS-GUIDE.md`

---

## AUTONOMOUS AGENTS: Independent Executors

**When to use Agents**: Research, analysis, or independent verification (no inter-agent messaging).

### Agent Lifecycle

```
Lead dispatches agent:
  Event: AgentStarted
  ├─ Agent executes task autonomously
  ├─ Emits checkpoints (state snapshots)
  └─ Reports findings + artifacts
  Event: AgentTaskCompleted

Lead reviews agent findings:
  ├─ Verify artifacts reproducible
  └─ Decide: use findings or iterate
```

**Agent Types**:
- yawl-engineer (refactoring, implementation)
- yawl-validator (verification, conformance)
- yawl-architect (design reviews)
- yawl-tester (test coverage, benchmarks)

See `.claude/agents/` for specs.

---

## RULES: Context-Sensitive Guidance

Rules auto-activate based on file path:

```
pom.xml                     → dx-workflow.md + maven-modules.md
.claude/rules/teams/**      → team-decision-framework.md
yawl/engine/**              → workflow-patterns.md + interfaces.md
yawl/integration/**         → mcp-a2a-conventions.md
**/*.java                   → modern-java.md + chicago-tdd.md
scripts/**|*.sh             → shell-conventions.md
```

Rules override defaults. No option to ignore.

---

## WORKFLOW ORCHESTRATION: Task Lifecycle

```
Task Lifecycle:
  1. Plan: iff |steps| ≥ 3 ∨ architectural
  2. Verify Plan: Get approval before coding
  3. Implement: Track progress with TodoWrite
  4. Explain: Show diff, summarize changes
  5. Document: Update .claude/ references
  6. Capture: Add patterns to lessons.md

Decisions:
  • Sideways task? → Stop, replan (don't push through)
  • Research needed? → Offload to agent or subagent
  • Multi-quantum? → Consider team (see TEAMS-GUIDE.md)
  • Bug found? → Fix immediately, document root cause

Completion Criteria:
  ✓ Proved works (dx.sh all green)
  ✓ Diff reviewed (changes visible, necessary)
  ✓ Tests pass (all phases succeed)
  ✓ No deception (H + Q gates pass)
```

---

## CORE PRINCIPLES

### Simplicity First
- Minimize code changes (|Δcode| → min)
- Impact ⊆ necessary (no gratuitous refactoring)
- Root causes only (no temporary fixes)
- Senior engineer standard

### Minimal Impact
- Changes must be necessary
- No unintended side effects
- No new bugs introduced
- Observable ≠ speculative

### No Deception
- Real implementation or throw
- Never silently degrade
- Never fake progress
- Code ≈ documentation

---

## ARCHITECTURE: Entry Points & Interfaces

**Core Classes**:
- `YEngine` (stateful, long-lived process)
- `YStatelessEngine` (stateless, per-request)
- `YSpecification` (workflow definitions)
- `YawlMcpServer` (MCP integration)
- `YawlA2AServer` (A2A integration)

**Key Types**:
- `YNetRunner`: Executes a single workflow instance
- `YWorkItem`: Atomic unit of work

**Note**: 185 packages have `package-info.java` — read first before modifying.

---

## CRITICAL MAVEN DETAILS

### Source Roots
- yawl-engine: `../src` (NOT src/main/java)
- test: `../test`
- Always read `pom.xml <sourceDirectory>` before placing files

### Maven JVM Config
- `.mvn/jvm.config` has NO comment support
- Every line is passed to JVM literally
- `# lines` crash with "cannot find main class #"
- Use only valid JVM flags

### VirtualThread Patterns
- **CORRECT**: `queue.take()` (parks carrier thread)
- **WRONG**: `poll()+onSpinWait()` (saturates carrier)
- Wrong pattern causes starvation at >100 agents

---

## STOP CONDITIONS: When to Pause & Replan

| Condition | Action |
|-----------|--------|
| Unknown module | Check modules.json |
| >3 files for 1 answer | Run observatory.sh instead of grepping |
| Context >70% used | Checkpoint, batch remaining tasks |
| Team >5 teammates | Reduce scope or split into phases |
| Teammates not messaging | Use subagents instead (cheaper) |
| Lead's dx.sh fails after teammate success | Identify incompatibility, reassign |
| Tempted "for now"/"later" | Throw UnsupportedOperationException immediately |
| Unsure about emit vs archive | Ask user before proceeding |

---

## FOUNDATIONAL DOCUMENTATION

All major concepts grounded in observable processes:

| Document | Topic | Why It Matters |
|----------|-------|----------------|
| `.claude/FIRST-PRINCIPLES.md` | Event logs, van der Aalst framework | Understand why observable > abstract |
| `.claude/EVENT-LOG-SCHEMA.md` | Event types, trace format, serialization | Speak the language of execution |
| `.claude/PROCESS-SPECIFICATION.md` | BPMN workflows, build/validate/team processes | Know the ideal process |
| `.claude/CONFORMANCE-CHECKING.md` | H/Q verification, guard patterns, invariants | Know what we verify |
| `.claude/rules/` | Context-sensitive rules (24 files) | Apply guidance by file path |

---

## VERIFICATION CHECKLIST

Before marking task done:

- [ ] `dx.sh all` exits 0 (all phases green)
- [ ] H phase: no guard violations detected
- [ ] Q phase: all invariants satisfied
- [ ] Commit created with trace reference
- [ ] Diff visible (changes necessary, not gratuitous)
- [ ] No deception in code (real_impl ∨ throw)
- [ ] Tests pass (coverage tracked)
- [ ] Documentation updated if needed

---

## GODSPEED. ✈️

*Every observable event is a fact. Every fact is a stone in the bridge to understanding. Compile ≺ Test ≺ Validate ≺ Deploy.*
