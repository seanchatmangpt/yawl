# Ralph Loop - Complete Usage Guide

**Command**: `/ralph-loop`
**Purpose**: Self-referential iterative task completion with YAWL validation integration

**DEFAULTS**:
- **Team mode**: ENABLED (5 agents)
- **Completion promise**: "DONE"

---

## Quick Start

```bash
# DEFAULT: Spawns 5-agent team, completion promise "DONE"
/ralph-loop "Fix all guard violations"
/ralph-loop "Research engine architecture"
/ralph-loop "Design new schema"

# With iteration limit
/ralph-loop "Implement feature X" --max-iterations 20

# Single agent mode (disable team)
/ralph-loop "Simple fix" --no-team

# Custom completion promise
/ralph-loop "Complex task" --completion-promise "ALL_TESTS_PASS"

# Cancel active loop
/cancel-ralph
```

---

## Team Mode (DEFAULT)

### What It Does
By default, ralph-loop:
1. Routes your task to the appropriate 5-agent team (explore/plan/implement)
2. Spawns 5 parallel agents using the Task tool
3. Each agent works on a different aspect of the task
4. Agents coordinate via messaging
5. Consolidation phase at end of iteration

### Team Routing (Automatic)
| Keywords | Team | Agents |
|----------|------|--------|
| research, investigate, analyze, explore, discover | explore | explorer_1-5 |
| plan, design, architect, strategy, refactor | plan | planner_1-5 |
| implement, build, code, fix, test, deploy | implement | implementer_1-5 |

### Override Routing
```bash
# Force specific team
/ralph-loop "Task" --team-name explore
/ralph-loop "Task" --team-name plan
/ralph-loop "Task" --team-name implement
```

### Disable Team Mode
```bash
# Use single agent instead of 5
/ralph-loop "Simple task" --no-team
```

### Team Mode Example
```bash
/ralph-loop "Fix all H_GUARD violations in yawl-engine"
```

This spawns 5 implementer agents that each work on different files/modules simultaneously.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    RALPH LOOP ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐     │
│  │ ralph-loop.sh│────▶│  Claude      │────▶│  stop-hook.sh│     │
│  │ (initiate)   │     │  (work)      │     │  (intercept) │     │
│  └──────────────┘     └──────────────┘     └──────┬───────┘     │
│                                                    │             │
│                              ┌─────────────────────▼────────┐    │
│                              │     VALIDATION PIPELINE       │    │
│                              │  ┌─────┐ ┌─────┐ ┌─────┐     │    │
│                              │  │ Ψ   │ │ Λ   │ │ H   │     │    │
│                              │  │Facts│ │Build│ │Gates│     │    │
│                              │  └─────┘ └─────┘ └─────┘     │    │
│                              │  ┌─────┐ ┌─────┐              │    │
│                              │  │ Q   │ │ Ω   │              │    │
│                              │  │Inv. │ │ Git │              │    │
│                              │  └─────┘ └─────┘              │    │
│                              └─────────────┬────────────────┘    │
│                                            │                    │
│                         ┌──────────────────▼──────────────────┐ │
│                         │          DECISION ENGINE            │ │
│                         │  GREEN ──▶ Exit (code 0)            │ │
│                         │  RED   ──▶ Analyze ─▶ Remediate     │ │
│                         │           ─▶ Re-inject (code 127)   │ │
│                         └─────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| Task description | Yes | - | The task to complete (first positional argument) |
| `--completion-promise` | Yes | - | Text that signals task completion |
| `--max-iterations` | No | 50 | Maximum loop iterations before stopping |

---

## YAWL Smart Validation

### Auto-Detection
Ralph-loop automatically detects YAWL context by checking for:
- `pom.xml` (Maven project)
- `scripts/dx.sh` (YAWL build script)
- `scripts/observatory/` (Observatory directory)

### Baseline Optimization
- Runs `dx.sh all` before first iteration to establish baseline
- **If GREEN**: Skips validation on iteration 1 (smart mode)
- **If RED**: Validation runs on all iterations

### Validation Phases

| Phase | Name | Script | Purpose |
|-------|------|--------|---------|
| Ψ | Observatory | `observatory.sh` | Generate codebase facts |
| Λ | Build | `dx.sh compile` | Compile and test |
| H | Guards | `hyper-validate.sh` | Detect forbidden patterns |
| Q | Invariants | `q-phase-invariants.sh` | Ensure real implementations |
| Ω | Git | `stop-hook-git-check.sh` | Verify clean state |

---

## H Phase: Guard Patterns

The `hyper-validate.sh` script detects 7 forbidden patterns:

| Pattern | Code | Detection | Fix |
|---------|------|-----------|-----|
| Deferred work | H_TODO | `// TODO`, `// FIXME`, etc. | Implement or remove |
| Mock implementations | H_MOCK | `mockX()`, `MockClass` | Delete or implement |
| Empty returns | H_STUB | `return ""`, `return null` | Throw exception |
| No-op bodies | H_EMPTY | `{ }` void methods | Add logic or throw |
| Silent fallback | H_FALLBACK | `catch { return fake }` | Propagate exception |
| Documentation lie | H_LIE | Code ≠ docs | Update code or docs |
| Silent logging | H_SILENT | `log.error` instead of throw | Throw exception |

---

## State Management

### State Directory
```
.claude/.ralph-state/
├── loop-state.json      # Loop configuration and progress
├── loop-edits.txt       # Files modified in each iteration
└── status               # active | completed | cancelled
```

### State File Structure
```json
{
  "task_description": "Fix guard violations",
  "completion_promise": "COMPLETE",
  "max_iterations": 50,
  "current_iteration": 3,
  "enable_smart_validation": true,
  "started_at": "2026-03-04T12:00:00Z",
  "status": "active",
  "dx_baseline_green": true
}
```

---

## Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| 0 | Success | Task complete, validation passed |
| 1 | Transient | Retry may help (IO, network) |
| 2 | Fatal | User must fix (config error) |
| 127 | Continue | Re-inject prompt, continue loop |

---

## Error Analysis & Remediation

### Automatic Remediation

| Violation | Remediation Strategy |
|-----------|---------------------|
| H_TODO | Remove comment marker |
| H_MOCK | Delete mock class file |
| H_STUB | Replace with `throw UnsupportedOperationException` |
| H_EMPTY | Add `throw UnsupportedOperationException` |
| H_SILENT | Convert `log.error()` to `throw` |
| H_FALLBACK | Convert to rethrow (may need manual) |
| H_LIE | Escalate to manual review |

### Remediation Flow
```
1. Detect violation
2. Create backup
3. Apply pattern-based fix
4. Re-run validation
5. Commit if GREEN
```

---

## Integration with dx.sh

### dx.sh Pipeline
```bash
# Full validation (used by ralph-loop)
bash scripts/dx.sh all

# Pipeline phases:
# 1. observe (Ψ) - Generate facts
# 2. compile (Λ) - Build source
# 3. test (Λ) - Run tests
# 4. guards (H) - hyper-validate.sh
# 5. invariants (Q) - Check real impl
# 6. report (Ω) - Generate receipts
```

### Receipt Files
```json
// guard-receipt.json
{
  "phase": "guards",
  "files_scanned": 3531,
  "violations": [...],
  "status": "RED",
  "summary": {
    "h_todo_count": 19,
    "total_violations": 59
  }
}
```

---

## Hook Integration

### stop-hook.sh Responsibilities
1. **Exit Interception**: Captures Claude exit attempts
2. **Loop Detection**: Checks `.ralph-state/status`
3. **Validation Pipeline**: Runs `dx.sh all`
4. **Error Analysis**: Calls `analyze-errors.sh`
5. **Auto-Remediation**: Calls `remediate-violations.sh`
6. **Decision Engine**: Returns exit code

### Hook Chain
```
PostToolUse (Write|Edit)
    ↓
hyper-validate.sh (H phase)
    ↓
stop-hook.sh (on exit)
    ↓
dx.sh all (full validation)
    ↓
Decision: exit 0 | exit 127
```

---

## Usage Examples

### Example 1: Fix Guard Violations
```bash
/ralph-loop "Fix all H_TODO and H_MOCK violations in yawl-engine" --completion-promise "ALL_GUARDS_PASS"
```

**Expected Flow:**
1. Claude finds and fixes violations
2. Outputs `ALL_GUARDS_PASS`
3. stop-hook runs `dx.sh all`
4. If RED → errors injected → continue
5. If GREEN → exit

### Example 2: Implement Feature
```bash
/ralph-loop "Add SLA tracking to YWorkItem" --completion-promise "SLA_TRACKING_DONE" --max-iterations 20
```

### Example 3: Fix Failing Tests
```bash
/ralph-loop "Fix all failing tests in yawl-stateless" --completion-promise "ALL_TESTS_PASS"
```

---

## Troubleshooting

### Loop Stuck in RED
1. Check `guard-receipt.json` for violations
2. Review `.ralph-state/loop-state.json` for iteration count
3. Run `bash scripts/dx.sh all` manually to see errors
4. Use `/cancel-ralph` if needed

### Max Iterations Reached
- Increase `--max-iterations`
- Break task into smaller subtasks
- Check for circular dependencies

### Remediation Not Working
- Some patterns require manual fix (H_LIE, H_FALLBACK)
- Check `remediate-violations.sh` logs
- Apply fix manually, loop will continue

---

## Related Files

| File | Purpose |
|------|---------|
| `.claude/skills/ralph-loop.sh` | Main loop script |
| `.claude/skills/cancel-ralph.sh` | Cancel script |
| `.claude/hooks/stop-hook.sh` | Exit interception |
| `.claude/hooks/hyper-validate.sh` | H phase validation |
| `scripts/dx.sh` | Build pipeline |
| `scripts/observatory/observatory.sh` | Fact generation |

---

## See Also

- **CLAUDE.md** - YAWL development rules
- **HYPER_STANDARDS.md** - Quality standards reference
- **TEAMS-GUIDE.md** - Multi-agent coordination
- **H-GUARDS-DESIGN.md** - Guard system architecture

---

## Summary

Ralph Loop enables autonomous, zero-defect task completion by:

1. **Self-Referential**: Feeds prompts back until completion
2. **Validation-Aware**: Integrates with YAWL quality gates
3. **Auto-Remediating**: Fixes common violations automatically
4. **State-Persistent**: Tracks progress across iterations
5. **Safety-Bounded**: Max iterations prevent infinite loops

**Philosophy**: `drift(A) → 0` - Continuously improve until code meets Fortune 5 standards.
