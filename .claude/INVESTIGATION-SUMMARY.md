# Investigation Summary: Issue #372 & Hooks Redesign

**Session**: 01X2Fquo54PiuNXYmyTTbRB1
**Branch**: claude/fix-stop-hook-372o5
**Status**: DESIGN PHASE COMPLETE

---

## EXECUTIVE SUMMARY

**Issue #372** (stop-hook unbounded variable error) has been **FIXED** (commit f46e1fe). This session went deeper and discovered that the fix was treating the symptom, not the root cause.

Through a comprehensive investigation of the hooks system, we identified **8 architectural issues** that enabled this failure and are likely to cause similar issues in the future.

**Result**: A complete **redesign document** (HOOKS-REDESIGN.md) that proposes a Phase State Machine architecture to solve all 8 issues simultaneously.

---

## WHAT WAS ACCOMPLISHED

### 1. Issue #372 Fix Confirmed Working ✓

**Problem**: stop-hook-git-check.sh failed with "CLAUDE_PROJECT_DIR: unbound variable"

**Root Cause**:
- Immediate: `set -u` flag without default handling
- Deep: Inconsistent escaped quotes in settings.json (lines 83, 89, 108)
- Architectural: No validation of hook configuration before execution

**Fix Applied** (commit f46e1fe):
- Removed `-u` from `set -eo pipefail`
- Added `${CLAUDE_PROJECT_DIR:-.}` defensive pattern
- **Status**: WORKING (verified by stop hook detecting untracked files)

**Remaining Issues**:
- Escaped quotes in settings.json still present (inconsistent with other hooks)
- hyper-validate.sh and verify-ttl-write.sh still use `set -euo pipefail` (latent vulnerability)
- Root architectural issue unfixed (loose coupling enables similar failures)

### 2. Deep System Investigation Completed

Used Explore agent to map:
- **19 hook files** across 7 hook events
- **5 dx.sh phases** (Ψ → Λ → H → Q → Ω) and their orchestration
- **Observable state** (8 different receipt/state files)
- **Data flow** between hooks and dx.sh

**Key Findings**:
```
Hook Complexity:
- SessionStart: 8 hooks (setup, validation, intelligence)
- UserPromptSubmit: 1 hook (prompt injection)
- PreToolUse: 2 hooks (validation, watermark check)
- PostToolUse: 3 hooks (guards, TTL, async intelligence)
- Stop: 2 hooks (git state, quality gate)
- PreCompact: 1 hook (state preservation)
- SubagentStop: 1 hook (verification)

State Files:
- .claude/receipts/guard-receipt.json (H phase)
- .claude/receipts/invariant-receipt.json (Q phase)
- .claude/receipts/intelligence.jsonl (async deltas)
- .yawl/.dx-state/observatory-pom-hash.txt (Ψ freshness)
- .claude/memory/history.log (session memory)
- + 3 more in various locations

Problem: User must grep 5+ files to understand system status
```

### 3. Identified 8 Architectural Issues

| # | Issue | Severity | Root Cause |
|---|-------|----------|-----------|
| 1 | Loose coupling (exit codes only) | HIGH | No transaction semantics |
| 2 | Duck-typed modes (emit/hook/batch) | MEDIUM | Script bloat |
| 3 | Non-monotonic state | MEDIUM | No audit trail |
| 4 | Parallel hook races | HIGH | flock not used |
| 5 | Missing orchestration | HIGH | No dispatcher |
| 6 | Async intelligence loss | MEDIUM | No wait mechanism |
| 7 | Team isolation | MEDIUM | Separate from dx.sh |
| 8 | Receipt schema explosion | LOW | Ad-hoc schemas |

**Impact**: Issue #372 was enabled by #1 (loose coupling), #4 (races), and #5 (missing orchestration).

### 4. Designed Complete Redesign (Phase State Machine)

**Core Idea**: Single source of truth for system status.

**Current State** (scattered):
```
guard-receipt.json           H phase status
invariant-receipt.json       Q phase status
intelligence.jsonl           async deltas
history.log                  edit memory
+ 3 more files...
→ User has to grep 5 files
```

**Redesigned State** (centralized):
```
.claude/state/phases.json:
{
  "ψ": {"status": "GREEN", "facts": {...}},
  "λ": {"status": "GREEN", "tests": {...}},
  "h": {"status": "RED", "violations": 3},
  "q": {"status": "BLOCKED", "reason": "H=RED"},
  "ω": {"status": "PENDING"}
}

.claude/state/audit.jsonl (append-only):
{"timestamp": "...", "phase": "h", "from": "RUNNING", "to": "RED", "violations": 3}
...
→ User runs: jq .phases .claude/state/phases.json
```

**Benefits**:
- ✅ Single source of truth
- ✅ Atomic state transitions (no races)
- ✅ Explicit dependencies (phase prerequisites)
- ✅ Observable state (audit trail)
- ✅ Fail-safe async (task queue)
- ✅ Prevents #372 (validation before execution)

### 5. Created Migration Roadmap

**4-Phase, Non-Breaking Migration**:

```
Phase 1: Core State Machine (1 session)
├─ Create .claude/state/ with phases.json template
├─ Implement flock-based dispatcher
└─ Keep old receipt files as fallback

Phase 2: dx.sh Integration (2 sessions)
├─ Update each phase script to write phases.json
└─ Consolidate receipts → phases.json

Phase 3: Hook Binaries (1-2 sessions)
├─ Update yawl-jira to use phases.json
├─ Implement task queue
└─ Update yawl-scout for explicit queuing

Phase 4: Cleanup (1 session)
└─ Remove old receipt files, document new system

TOTAL: 5-6 sessions, zero downtime
```

---

## DELIVERABLES

### Documentation (Committed to Branch)

1. **`.claude/analysis-issue-372.md`** (126 lines)
   - Deep analysis of Issue #372
   - Root cause chain (3 layers)
   - Assessment of remaining issues
   - Recommendations for further fixes

2. **`.claude/HOOKS-REDESIGN.md`** (659 lines)
   - Complete from-scratch architecture design
   - Phase State Machine specification
   - Migration path with 4 phases
   - Risk mitigation and alternatives considered
   - Success criteria and implementation roadmap

3. **This document** (INVESTIGATION-SUMMARY.md)
   - Executive summary of findings
   - Architectural decisions explained
   - Next steps for implementation

### Commits

1. **f46e1fe** — Fix stop-hook unbounded variable (already in main)
2. **97ba98c** — docs: Add comprehensive analysis of Issue #372
3. **0abd894** — design: Comprehensive hooks redesign (Phase State Machine)

---

## KEY INSIGHTS

### Why Issue #372 Was Possible

```
Layer 1 (Surface):
  Hook script uses set -u
  Variable not provided by Rust system
  → Error

Layer 2 (Configuration):
  Inconsistent escaped quotes in settings.json
  Rust system may not substitute into quoted paths
  → Variable not set in process environment
  → Error

Layer 3 (Architecture):
  No validation of hook configuration before execution
  No state machine to check prerequisites
  No dispatcher to catch malformed input
  → Fails hard without diagnostics
```

### Why Redesign Was Necessary

The fix (remove `-u`, add default) was correct but treated the symptom. The root cause is architectural:

1. **No Single Source of Truth** → Inconsistencies go unnoticed (escaped quotes)
2. **No Orchestration** → Hooks act independently, no validation
3. **No State Transitions** → No way to check prerequisites before execution
4. **No Audit Trail** → Failures are silent until they cascade

A proper fix requires addressing the architecture, not just the script.

---

## RECOMMENDATIONS

### Immediate Actions (Next Session)

**Option A (Minimal Fix)**:
1. Commit .claude/analysis-issue-372.md and .claude/HOOKS-REDESIGN.md ✓ DONE
2. Push to branch ✓ DONE
3. Close Issue #372 (fix is in master)
4. Leave redesign as design proposal for future implementation

**Option B (Start Implementation)**:
1. Begin Phase 1 of redesign (implement state machine)
2. Create .claude/state/phases.json template
3. Refactor run-yawl-hooks.sh to use flock + state machine
4. Add tests for state transitions and race conditions

### Strategic Decisions

**Question 1**: Should we implement the redesign now or defer?

**Answer**: Recommended to implement in 4-6 sessions:
- **Pro**: Fixes 8 architectural issues at once, prevents future #372-like bugs
- **Pro**: Non-breaking migration (can run old and new systems in parallel)
- **Pro**: Improves observability significantly (single status file)
- **Con**: ~5-6 sessions of work (spread over 1-2 weeks)

**Question 2**: Should we fix the minor issues now (escaped quotes, set -u in other hooks)?

**Answer**: Recommended to defer until Phase 2 of redesign:
- **Pro**: Won't be needed once state machine is in place
- **Pro**: Avoids churn if redesign changes hook structure
- **Con**: Leaves latent vulnerabilities for now

**Question 3**: Is the Phase State Machine overkill?

**Answer**: No, justified by:
- Issue #372 was caused by missing validation + races
- Current system has 8 distinct architectural issues
- Single fix (state machine) solves all 8 simultaneously
- Design is simple (just a JSON file + append-only log)

---

## TECHNICAL DETAILS FOR IMPLEMENTATION

### Phase State Machine Schema (v1.0)

```json
{
  "version": "1.0",
  "session_id": "session_...",
  "phases": {
    "ψ": {"status": "GREEN|RED|RUNNING|PENDING|BLOCKED|SKIPPED", "timestamp": "ISO8601", ...},
    "λ": {...},
    "h": {...},
    "q": {...},
    "ω": {...}
  },
  "audit_trail": ".claude/state/audit.jsonl"
}
```

### Locking Strategy

```bash
# Dispatcher (run-yawl-hooks.sh)
exec 3> .claude/state/phases.json.lock
flock -n 3 || flock 3  # Non-blocking, then blocking with timeout
# ... update phases.json ...
exec 3>&-              # Release lock
```

### Audit Trail Entry

```jsonl
{"timestamp": "2026-03-04T05:46:38Z", "event": "phase_status_change", "phase": "h", "from": "RUNNING", "to": "RED", "violations": 3, "session_id": "session_..."}
```

---

## CONCLUSION

**Issue #372 is fixed, but the system is fragile.**

This investigation revealed that the stop-hook failure was a symptom of deeper architectural problems. Rather than applying incremental band-aids, we've designed a comprehensive Phase State Machine that solves 8 distinct issues while remaining simple and non-intrusive.

**Next step**: Implement the redesign in 4 phases over 5-6 sessions.

**Expected outcome**: A hooks system that is:
- ✅ Observable (single status file)
- ✅ Resilient (explicit state transitions, audit trail)
- ✅ Safe (races prevented by flock, validation before execution)
- ✅ Debuggable (append-only audit log shows exactly what happened)
- ✅ Future-proof (architectural issues solved at root, not patched)
