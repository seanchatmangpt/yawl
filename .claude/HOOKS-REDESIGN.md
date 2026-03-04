# YAWL Hooks System Redesign — From-Scratch Architecture

**Status**: DESIGN PHASE
**Based On**: Deep investigation of current system (19 hooks, 5 dx.sh phases, 8 architectural issues)
**Goal**: Design an ideal hooks system that eliminates fragility while maintaining current capabilities

---

## I. CURRENT STATE ASSESSMENT

### Existing System Health

**What Works Well ✓**:
- Clear phase separation (Ψ→Λ→H→Q→Ω)
- Comprehensive pattern detection (7 guards, 4 invariants)
- Session-aware memory (history, intelligence, receipts)
- Integration with Claude Code events (SessionStart, PostToolUse, Stop)

**Critical Issues ✗**:
- No transaction semantics (incomplete receipts not detected)
- No centralized orchestration (5 hooks, 5 different state files)
- Async intelligence can be lost (no wait mechanism)
- Parallel SessionStart hooks have race conditions
- Team features isolated from dx.sh pipeline
- Receipt schemas are ad-hoc and inconsistent

**Brittleness Indicator**: Issue #372 (unbound variable) was easy to fix in the script, but the root cause (inconsistent hook configuration) went unfixed for 3 commits.

---

## II. DESIGN PRINCIPLES

### For an Ideal System:

**1. Single Source of Truth**
```
Current:
  ├─ guard-receipt.json (H phase status)
  ├─ invariant-receipt.json (Q phase status)
  ├─ intelligence.jsonl (async deltas)
  ├─ .dx-state/pom-hash.txt (Ψ freshness)
  └─ .claude/memory/history.log (edit history)
  → User must grep 5 files to understand build status

Ideal:
  ├─ .claude/state/phases.json (CANONICAL, single source of truth)
  │  └─ {ψ_status, λ_status, h_status, q_status, ω_status, timestamp}
  └─ .claude/state/audit.jsonl (append-only audit log)
  → User runs: `jq .phases .claude/state/phases.json` → single answer
```

**2. Atomic State Transitions**
```
Current:
  hyper-validate.sh (H phase):
    1. scan files (10s)
    2. generate violations
    3. CRASH before writing guard-receipt.json
    → State is inconsistent (H started but no receipt)

Ideal:
  hyper-validate.sh (H phase):
    1. acquire .claude/state/phases.json.lock
    2. read current state (H must be in RUNNING)
    3. scan files, generate violations
    4. write .claude/state/phases.json: {h_status: GREEN|RED, violations: [...]}
    5. release lock
    → All-or-nothing: Either H is RUNNING or has FINAL state
```

**3. Explicit Dependencies**
```
Current:
  dx.sh all (line 755):
    if mvn compile succeeds: run hyper-validate.sh
    if hyper-validate.sh succeeds: run q-phase-invariants.sh
    (No explicit contract; depends on exit codes)

Ideal:
  Phase State Machine:
    Ψ → requires: pom.xml exists, JDK 25 available
         produces: modules.json, reactor.json
    Λ → requires: Ψ.status = GREEN, modules.json available
         produces: target/**/*.class
    H → requires: Λ.status = GREEN, target/**/*.class available
         produces: guard-receipt.json
    Q → requires: H.status = GREEN, src/**/*.java available
         produces: invariant-receipt.json
    (Explicit contract: downstream phase verifies upstream prereqs)
```

**4. Observable State**
```
Current:
  yawl-state.sh (on SessionStart):
    "H (Guards) last run: never (run dx.sh all)"
    → No timestamps, no audit trail, no way to know if H passed or failed

Ideal:
  .claude/state/phases.json:
    {
      "ψ": {"status": "GREEN", "timestamp": "2026-03-04T05:44:20Z", "facts_count": 6},
      "λ": {"status": "GREEN", "timestamp": "2026-03-04T05:45:00Z", "modules": 25, "tests": 142},
      "h": {"status": "RED", "timestamp": "2026-03-04T05:46:30Z", "violations": 3, "last_fix": "6 hours ago"},
      "q": {"status": "BLOCKED", "reason": "H exited 2"},
      "ω": {"status": "PENDING"},
      "audit_file": ".claude/state/audit.jsonl"
    }
  → yawl-state.sh simply reads and formats phases.json
```

**5. Fail-Safe Async Operations**
```
Current:
  PostToolUse hook:
    yawl-scout fetch --async &  # spawns background, returns immediately
    # But context might compact before fetch completes
    # Intelligence deltas lost silently

Ideal:
  Session-end hook:
    1. Check if any background tasks in .claude/state/tasks.json
    2. Wait for all with timeout (e.g., 30 seconds)
    3. If timeout, warn user and save tasks for next session
    4. On session-start, resume any pending tasks
  → Guaranteed intelligence freshness or explicit warning
```

---

## III. PROPOSED ARCHITECTURE

### Overview: Two-Layer System

```
┌─────────────────────────────────────────────────────────┐
│         Claude Code Hook Events (Rust System)           │
│  SessionStart | UserPromptSubmit | PreToolUse | PostToolUse | Stop
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│      Layer 1: Hook Dispatcher (run-yawl-hooks.sh)       │
│  Routes events to Layer 2, validates event JSON         │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        │                         │
        ▼                         ▼
   ┌──────────────┐      ┌──────────────────────┐
   │ dx.sh all    │      │  Hook Binaries       │
   │ (5 phases)   │      │  (Session/Tool/Stop) │
   │              │      │                      │
   │ Ψ→Λ→H→Q→Ω   │      │ yawl-jira (Rust)    │
   │              │      │ yawl-scout (Rust)   │
   │ SYNC         │      │ yawl-state.sh (bash)│
   │ Ordered      │      │ ASYNC, Concurrent   │
   └──────────────┘      └──────────────────────┘
        │                         │
        └────────────┬────────────┘
                     │
                     ▼
       ┌─────────────────────────────────┐
       │  Layer 2: State Machine         │
       │  .claude/state/phases.json      │
       │  (Single Source of Truth)       │
       │                                 │
       │  - Atomic state transitions     │
       │  - Lock-free reads (RCU)        │
       │  - Audit trail (append-only)    │
       │  - Task queue (background work) │
       └─────────────────────────────────┘
```

### Layer 1: Hook Dispatcher

**File**: `.claude/hooks/run-yawl-hooks.sh` (REFACTORED)

**Responsibility**: Route Claude Code events to appropriate handlers, validate input, ensure state consistency.

**Current state**: 3.3KB dispatcher for 5 events
**New state**: 8KB dispatcher with validation + state checks

```bash
#!/bin/bash
# Hook Dispatcher v2.0
# Entry point for all Claude Code events

set -eo pipefail

EVENT="$1"      # SessionStart, UserPromptSubmit, etc.
INPUT_JSON="$2" # JSON from Claude Code framework

# ┌─────────────────────────────────────────────┐
# │ PHASE 0: VALIDATE INPUT                     │
# └─────────────────────────────────────────────┘
validate_event_json() {
    # Verify $INPUT_JSON is valid JSON
    # Verify required fields exist for this EVENT type
    # Exit 2 if invalid (don't corrupt state)
    jq empty >/dev/null 2>&1 || exit 2
    case "$EVENT" in
        SessionStart)
            jq -e '.session_id | length > 0' >/dev/null || exit 2
            ;;
        PostToolUse)
            jq -e '.tool_input.file_path | length > 0' >/dev/null || exit 2
            ;;
        # ... more validations
    esac
}

# ┌─────────────────────────────────────────────┐
# │ PHASE 1: ACQUIRE LOCK (Prevent races)       │
# └─────────────────────────────────────────────┘
acquire_state_lock() {
    local lock_file=".claude/state/phases.json.lock"
    local lock_timeout=30

    # Use flock with timeout to prevent deadlock
    # If another hook holds lock >30s, warn but proceed
    exec 3>"$lock_file"
    flock -n 3 || {
        echo "WARN: State lock held, may cause race condition" >&2
        sleep 2  # Backoff
        flock 3
    }
}

# ┌─────────────────────────────────────────────┐
# │ PHASE 2: ROUTE TO HANDLER                   │
# └─────────────────────────────────────────────┘
case "$EVENT" in
    SessionStart)
        . "$HOOKS_DIR/handlers/session-start-v2.sh"
        handle_session_start "$INPUT_JSON"
        ;;
    UserPromptSubmit)
        . "$HOOKS_DIR/handlers/prompt-submit-v2.sh"
        handle_prompt_submit "$INPUT_JSON"
        ;;
    # ... more routes
esac

# ┌─────────────────────────────────────────────┐
# │ PHASE 3: UPDATE STATE MACHINE               │
# └─────────────────────────────────────────────┘
update_phases_json() {
    jq \
        --arg event "$EVENT" \
        --arg timestamp "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        '.event = $event | .last_hook = $timestamp' \
        .claude/state/phases.json > .claude/state/phases.json.tmp
    mv .claude/state/phases.json.tmp .claude/state/phases.json
}

# ┌─────────────────────────────────────────────┐
# │ PHASE 4: RELEASE LOCK                       │
# └─────────────────────────────────────────────┘
release_state_lock() {
    exec 3>&-  # Close FD, implicitly releases flock
}
```

### Layer 2: State Machine (THE REDESIGN CENTERPIECE)

**File**: `.claude/state/phases.json` (NEW)

**Canonical Schema v1.0**:

```json
{
  "version": "1.0",
  "session_id": "session_01X2Fquo54PiuNXYmyTTbRB1",
  "session_start_time": "2026-03-04T05:44:20Z",

  "phases": {
    "ψ": {
      "name": "Observatory",
      "status": "GREEN",                     // GREEN, RED, RUNNING, PENDING, BLOCKED, SKIPPED
      "timestamp": "2026-03-04T05:44:20Z",
      "duration_ms": 2500,
      "facts": {
        "modules": 25,
        "deps_conflicts": 0,
        "pom_hash": "sha256:abc123...",
        "last_stale_check": "2026-03-04T05:44:00Z"
      },
      "next_phase": "λ",
      "exit_code": 0
    },

    "λ": {
      "name": "Build (Compile + Test)",
      "status": "GREEN",
      "timestamp": "2026-03-04T05:45:00Z",
      "duration_ms": 45000,
      "metrics": {
        "modules_compiled": 25,
        "tests_run": 142,
        "tests_passed": 142,
        "tests_failed": 0,
        "build_warnings": 3
      },
      "next_phase": "h",
      "exit_code": 0
    },

    "h": {
      "name": "Guards (Hyper-Standards)",
      "status": "RED",
      "timestamp": "2026-03-04T05:46:30Z",
      "duration_ms": 8000,
      "violations": {
        "h_todo": 2,
        "h_mock": 1,
        "h_mock_class": 0,
        "h_silent": 0,
        "total": 3
      },
      "violations_file": ".claude/receipts/guard-receipt-v2.json",
      "next_phase": "q",
      "exit_code": 2,
      "error": "3 guard violations detected; fix and run dx.sh all"
    },

    "q": {
      "name": "Invariants",
      "status": "BLOCKED",
      "timestamp": null,
      "reason": "Cannot run Q phase while H.status = RED",
      "next_phase": null,
      "exit_code": 2
    },

    "ω": {
      "name": "Git (Manual)",
      "status": "PENDING",
      "timestamp": null,
      "next_phase": null,
      "exit_code": null
    }
  },

  "audit_trail": ".claude/state/audit.jsonl",
  "background_tasks": ".claude/state/tasks.json",
  "last_update": "2026-03-04T05:46:30Z"
}
```

**Audit Trail** (`.claude/state/audit.jsonl`, append-only):

```jsonl
{"timestamp": "2026-03-04T05:44:20Z", "event": "SessionStart", "session_id": "session_01X2Fquo54PiuNXYmyTTbRB1"}
{"timestamp": "2026-03-04T05:44:20Z", "event": "phase_status_change", "phase": "ψ", "from": "PENDING", "to": "RUNNING"}
{"timestamp": "2026-03-04T05:44:22Z", "event": "phase_status_change", "phase": "ψ", "from": "RUNNING", "to": "GREEN", "facts_count": 6}
{"timestamp": "2026-03-04T05:45:00Z", "event": "phase_status_change", "phase": "λ", "from": "PENDING", "to": "RUNNING"}
{"timestamp": "2026-03-04T05:46:30Z", "event": "phase_status_change", "phase": "λ", "from": "RUNNING", "to": "GREEN", "tests_passed": 142}
{"timestamp": "2026-03-04T05:46:30Z", "event": "phase_status_change", "phase": "h", "from": "PENDING", "to": "RUNNING"}
{"timestamp": "2026-03-04T05:46:38Z", "event": "phase_status_change", "phase": "h", "from": "RUNNING", "to": "RED", "violations": 3}
{"timestamp": "2026-03-04T05:46:38Z", "event": "phase_status_change", "phase": "q", "from": "PENDING", "to": "BLOCKED", "reason": "H.status = RED"}
```

**Benefits**:
- User can run `jq .phases.h .claude/state/phases.json` to see H phase status instantly
- Audit trail shows exactly when each phase started/ended and why
- yawl-state.sh becomes trivial (just pretty-print phases.json)
- Downstream tools can subscribe to audit.jsonl for real-time updates

---

## IV. REFACTORED HOOKS

### SessionStart Hooks (Parallel → Sequenced for Safety)

**Before**:
```json
{
  "SessionStart": [
    {
      "hooks": [
        {"type": "command", "command": "session-start.sh", "timeout": 120},
        {"type": "command", "command": "yawl-state.sh", "timeout": 15},
        {"type": "command", "command": "pre-task.sh", "timeout": 10}
      ]
    }
  ]
}
```

(All run in parallel, race conditions on shared state files)

**After**:
```json
{
  "SessionStart": [
    {"type": "command", "command": ".claude/hooks/session-start-v2.sh", "timeout": 120},
    {"type": "command", "command": ".claude/hooks/yawl-state-v2.sh", "timeout": 15},
    {"type": "command", "command": ".claude/hooks/pre-task-v2.sh", "timeout": 10}
  ]
}
```

(Run SEQUENTIALLY via run-yawl-hooks.sh dispatcher, state machine locks ensure atomicity)

### PostToolUse Hooks (Async Intelligence)

**Before**:
```bash
hyper-validate.sh           # Blocks on guard violations
verify-ttl-write.sh         # Watermark check (legacy)
yawl-scout fetch --async &  # Spawned in background, may be lost
```

**After**:
```bash
hyper-validate.sh                    # Blocks on guard violations (unchanged)
tasks-enqueue.sh                    # NEW: Adds fetch task to .claude/state/tasks.json
post-edit-v2.sh                     # Records edit to audit trail (changed)
# (async fetch happens in background with explicit queue management)
```

### Stop Hook (Simplified)

**Before**:
```bash
stop-hook-git-check.sh      # Checks git state only
(plus prompt gate for self-play experiments)
```

**After**:
```bash
stop-hook-dispatcher.sh:
  1. Read phases.json
  2. If H = RED or Q = RED, exit 1 (warn, don't block)
  3. Check background tasks (.claude/state/tasks.json) — wait up to 10s
  4. If tasks pending, show warning but allow stop (async will resume next session)
  5. Check git status
  6. If clean, exit 0
  7. If dirty (uncommitted changes), exit 1 with message
```

---

## V. MIGRATION PATH

### Phase 1: Implement State Machine (Non-Breaking)

1. Create `.claude/state/phases.json` with current status snapshot
2. Create `.claude/state/audit.jsonl` (initially empty)
3. Refactor run-yawl-hooks.sh to read/write phases.json (but don't require it yet)
4. Keep old receipt files as fallback (dual-write during transition)

**Timeline**: 1 session
**Risk**: Low (new files, old system still works)

### Phase 2: Refactor dx.sh to Use State Machine

1. Update `dx.sh` to check phases.json before each phase
2. Update each phase script (observatory.sh, hyper-validate.sh, etc.) to write phases.json atomically
3. Consolidate receipt files into phases.json
4. Deprecate old receipt files (but keep reading them as fallback)

**Timeline**: 2 sessions
**Risk**: Medium (dx.sh behavior changes, but transparent to user)

### Phase 3: Refactor Hook Binaries (yawl-jira, yawl-scout)

1. Update yawl-jira to use phases.json for state (instead of reading individual receipts)
2. Update yawl-scout to enqueue tasks explicitly (instead of spawning async)
3. Implement task queue (`.claude/state/tasks.json`)

**Timeline**: 1-2 sessions
**Risk**: Medium (binaries behavior changes, need Rust testing)

### Phase 4: Cleanup (Remove Old Files)

1. Remove old receipt files (.claude/receipts/guard-receipt.json, etc.) once migration complete
2. Update yawl-state.sh to only read phases.json (remove fallback logic)
3. Document new system in CLAUDE.md

**Timeline**: 1 session
**Risk**: Low (cleanup only)

**Total**: 5-6 sessions, non-breaking migration path

---

## VI. EXPECTED IMPROVEMENTS

### Issue #372 Prevention

**Current problem**: Inconsistent hook configuration (escaped quotes) allowed unbound variable error to slip through.

**Redesigned system**:
- State machine lock ensures only one hook modifies state at a time (no races)
- Explicit validation in dispatcher catches malformed hook input before execution
- Audit trail shows exactly where failures occurred

**Result**: Similar issues will fail fast with clear diagnostics, not silently.

### Observability

**Current**: `yawl-state.sh` shows "H (Guards) last run: never" — no actionable info.

**Redesigned**:
```bash
$ jq '.phases.h' .claude/state/phases.json
{
  "status": "RED",
  "violations": 3,
  "violations_file": ".claude/receipts/guard-receipt-v2.json",
  "last_fix": "6 hours ago",
  "exit_code": 2
}

$ jq '.audit_trail | tail -5' .claude/state/audit.jsonl
# Shows exactly when H started, ended, and why it failed
```

### Resilience

**Current**: Async intelligence (yawl-scout fetch) can be lost if context compacts.

**Redesigned**:
- Explicit task queue in phases.json
- Session-end hook waits for all tasks with timeout
- Pending tasks are resumed next session
- User gets clear warning if intelligence fetch failed

### Debuggability

**Current**: 5 different receipt files + logs scattered across .claude/

**Redesigned**:
- Single source of truth: .claude/state/phases.json
- Append-only audit trail: .claude/state/audit.jsonl
- All phase transitions logged with timestamps and reasons
- User can `tail -f .claude/state/audit.jsonl` to watch progress in real-time

---

## VII. ALTERNATIVES CONSIDERED

### Alternative A: Keep Current System, Add Validation

**Idea**: Keep 5 receipt files, add cross-file validation.

**Pros**: Minimal changes
**Cons**: Doesn't fix fundamental issues (races, async loss, loose coupling)

**Verdict**: Rejected (band-aid solution)

### Alternative B: Use SQLite for State

**Idea**: Replace phases.json with SQLite database.

**Pros**: ACID transactions, no file locking
**Cons**: Adds dependency (sqlite3), harder to inspect (requires CLI), slower for small datasets

**Verdict**: Rejected (overkill for this use case)

### Alternative C: Use Distributed Coordination (etcd, Consul)

**Idea**: Use external coordinator for state management.

**Pros**: Enables team coordination across machines
**Cons**: Huge complexity, not needed for local development

**Verdict**: Rejected (out of scope)

---

## VIII. SUCCESS CRITERIA

### For Design to Be Accepted:

1. **Single Source of Truth**: `jq .phases .claude/state/phases.json` gives complete system status
2. **No Race Conditions**: Parallel SessionStart hooks with file locking (flock)
3. **Audit Trail**: 100% of phase transitions logged with timestamps
4. **Background Task Management**: Explicit queue for async operations
5. **Backwards Compatible**: Old receipt files still work during migration
6. **Clear Exit Codes**: Each hook exits with meaningful code (0=SUCCESS, 1=WARN, 2=FAIL)

### For Implementation to Be Ready:

1. [ ] Phase 1 complete: State machine files created
2. [ ] Phase 2 complete: dx.sh refactored to use state machine
3. [ ] Phase 3 complete: Hook binaries updated
4. [ ] Phase 4 complete: Old files removed
5. [ ] Comprehensive tests added (state transitions, race conditions, audit trail)
6. [ ] Documentation updated (CLAUDE.md, README-QUICK.md)
7. [ ] Team integration tested (team coordination with new state machine)

---

## IX. IMPLEMENTATION ROADMAP

### Week 1: Core State Machine

- [ ] Create `.claude/state/` directory + schema
- [ ] Implement phases.json template generator
- [ ] Implement flock-based locking in run-yawl-hooks.sh
- [ ] Add state validation utility functions

### Week 2: dx.sh Integration

- [ ] Update observatory.sh to write phases.json.ψ
- [ ] Update Maven wrapper to write phases.json.λ
- [ ] Update hyper-validate.sh to write phases.json.h
- [ ] Update q-phase-invariants.sh to write phases.json.q

### Week 3: Hook Refactoring

- [ ] Refactor session-start.sh for sequential execution
- [ ] Update yawl-jira (Rust) to use phases.json
- [ ] Implement task queue system
- [ ] Update yawl-scout (Rust) to enqueue instead of spawn

### Week 4: Testing + Cleanup

- [ ] Write integration tests (race conditions, phase transitions)
- [ ] Migrate existing receipts to new schema
- [ ] Remove deprecated files
- [ ] Update documentation

---

## X. RISK MITIGATION

### Risk 1: Rust Binary Changes (yawl-jira, yawl-scout)

**Mitigation**: Phase 3 can be deferred; system works without Rust binaries if needed.

### Risk 2: Breaking Changes to dx.sh

**Mitigation**: Add `DX_LEGACY=1` flag to fall back to old behavior during transition.

### Risk 3: File Locking Deadlock

**Mitigation**: Use non-blocking flock with timeout (exit gracefully if lock held >30s).

### Risk 4: State Corruption (Partial Writes)

**Mitigation**: Always write to temp file, then atomic rename. Never write directly to phases.json.

---

## CONCLUSION

The current hooks system is operationally fragile but strategically sound. By implementing a centralized **Phase State Machine** as the single source of truth, we can:

- ✅ Eliminate races (flock-based mutual exclusion)
- ✅ Enable observability (append-only audit trail)
- ✅ Fix async issues (explicit task queue)
- ✅ Simplify debugging (one place to check status)
- ✅ Prevent future issues like #372 (state validation before execution)

The migration is non-breaking and can be phased over 4-6 sessions with zero downtime.
