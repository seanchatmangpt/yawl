# YAWL Teams Guide — τ (Teams) Architecture

## Table of Contents

1. [Overview](#overview)
2. [Decision Framework](#decision-framework)
3. [Error Recovery](#error-recovery)
4. [Session Resumption](#session-resumption)

---

## Overview

**Default behavior**: Multi-quantum tasks → Teams (τ)

This guide consolidates team operations into a single reference. Teams are the default for suitable work requiring collaboration and iteration between orthogonal quantums.

### When to Use Teams ✓

| Signal | Reason | Example |
|--------|--------|---------|
| **Competing hypotheses** | Parallel investigation converges faster | Deadlock debugging: state-mgmt vs race vs guard |
| **Cross-layer changes** | Independent experts per layer | Schema + engine + integration changes |
| **Code review by concern** | Parallel review across domains | Security + perf + coverage reviews |
| **Research + implement** | Concurrent research & implementation | Research MCP spec while building endpoint |
| **Schema + validation** | Spec writer + implementer coordination | Schema defines type, engine implements logic |
| **Shared discovery** | Teammates share findings before consolidating | "My module calls Y.Z()—do yours?" |

### When Teams Are WRONG ✗

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| **Single quantum** | 3-5× cost for sequential work | Use single session |
| **No inter-team messaging** | Just parallel isolation | Use subagents (cheaper) |
| **>5 teammates** | Coordination overhead exceeds benefit | Split into 2-3 phases, max 5 per team |
| **<30 min per task** | Team setup > task time | Combine into single session |
| **Same-file edits** | No MVCC, teammates overwrite | Reduce team size or refactor ownership |
| **Tightly coupled logic** | Teammates block each other | Reorder tasks or add dependencies |

---

## Decision Framework

### Quick Decision Tree

```
Does your task have N ∈ {2,3,4,5} orthogonal quantums?
├─ YES + teammates need to message/iterate?
│  └─ → Use TEAM (τ)  [Default: collaborative]
├─ YES + quantums are purely independent?
│  └─ → Use SUBAGENTS (μ)  [Report-only]
├─ YES + team size > 5?
│  └─ → SPLIT into 2-3 phases, max 5 per team
├─ NO (single quantum)?
│  └─ → Use SINGLE SESSION  [Fast, cheap]
└─ UNSURE?
   └─ → Run hook: bash .claude/hooks/team-recommendation.sh "your task"
```

### YAWL-Specific Team Patterns

#### Pattern 1: Engine Investigation (3 engineers)
**Scenario**: YNetRunner deadlock debugging
- **Cost**: ~$3-4C | **ROI**: 2-3× faster convergence
- **Flow**: State machine + concurrency + guard logic investigators share findings

#### Pattern 2: Schema + Implementation (2 engineers)
**Scenario**: Add workflow attribute (e.g., SLA tracking)
- **Cost**: ~$2-3C | **ROI**: Avoids back-and-forth iterations
- **Flow**: Schema defines → implements → validates

#### Pattern 3: Code Review by Concern (3 reviewers)
**Scenario**: PR review with multiple lenses
- **Cost**: ~$2-3C | **ROI**: Catches cross-cutting issues
- **Flow**: Security + performance + testing reviewers parallel review

#### Pattern 4: Observability & Monitoring (2 engineers)
**Scenario**: Add metrics to YNetRunner
- **Cost**: ~$2-3C | **ROI**: Enables production debugging
- **Flow**: Design schema → integrate → validate

#### Pattern 5: Performance Optimization (2-3 engineers)
**Scenario**: Task queue optimization
- **Cost**: ~$3-4C | **ROI**: 3× throughput gain
- **Flow**: Profile → optimize → test → validate

#### Pattern 6: Autonomous Agent Integration (2-3 engineers)
**Scenario**: Add MCP endpoint for case monitoring
- **Cost**: ~$3-4C | **ROI**: Real-time case monitoring
- **Flow**: Design schema → implement → test protocol

### Team Alternatives

| Alternative | When to Use | Cost |
|------------|-----------|------|
| **Single Session** | Single quantum, tight feedback | ~$C |
| **Subagents** | Independent verification, no messaging | ~$C + summaries |
| **Git Worktrees** | Manual parallel sessions | ~$C × N, high overhead |

### Implementation Checklist

Before summoning a team:
- [ ] Run team-recommendation.sh hook
- [ ] N teammates ∈ {2,3,4,5}?
- [ ] Each task ≥30 min?
- [ ] No file conflicts (Ψ.facts/shared-src.json clean)
- [ ] Teammates will message/iterate?
- [ ] Facts current (bash scripts/observatory/observatory.sh)?
- [ ] Team feature enabled (CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1)?

### Metrics & Tuning

| Metric | Target | Calculation |
|--------|--------|-------------|
| **Teammate utilization** | >80% | active_tasks / blocked_tasks |
| **Messages per teammate** | >2 | total_messages / teammate_count |
| **Iteration cycles** | 1-2 | message_rounds before consolidation |
| **Cost/quality ratio** | >1.5 | quality_gain / 3C cost |

**Tune team size**:
- Too many messages? → Reduce to 2-3 teammates
- Teammates idle? → Add more tasks or combine
- All green first try? → Scope too simple, use single session

---

## Error Recovery

### Timeout Handling

#### Teammate Idle Timeout (30 min)
- **Trigger**: No status/progress for 30 min
- **Action**: 
  - Check if blocked on other teammate
  - If local DX failed: message for help
  - If message queue full: resend critical + timeout
  - If unresponsive: assume crash
- **Recovery**: Reassign task to new teammate

#### Task Timeout (2+ hours)
- **Trigger**: Task exceeds 2 hours without completion
- **Action**:
  - If still investigating: allow +1 hour
  - If blocked: escalate blocker
  - If too large: split into subtasks
  - If crashed: reassign
- **Hard limit**: 3 hours total, then escalate

#### Message Timeout (15 min)
- **Trigger**: No reply to critical message in 15 min
- **Action**:
  - **Critical**: Resend with [URGENT], wait 5 min → crash if no reply
  - **Informational**: Wait until teammate checkpoints, resend after 30 min
  - **Question**: Provide default option, don't block

### Circular Dependencies & Deadlock

#### Detection
- **Definition**: Task A ← Task B ← Task A (or longer chain)
- **Method**: DFS cycle detection on task dependency graph
- **Prevention**: Validate DAG before team formation

#### Resolution Options
1. **Task Ordering**: One teammate makes initial decision (e.g., schema defines states first)
2. **Parallel Independent Work**: Implement based on interface contracts, verify in consolidation
3. **Sequential Phases**: Last resort, loses parallelism benefit

### Async Failures

#### Teammate Crash (Context Lost)
- **Detection**: Unresponsive >5 min, context maxed out
- **Action**:
  1. Save work: checkpoint commit
  2. Reassign based on completion:
     - 80%+ → new teammate continues from checkpoint
     - <80% critical → new teammate with checkpoint
     - <80% independent → lead finishes alone

#### Lead's Full DX Fails
- **Scenario**: All teammates GREEN, but lead's `dx.sh all` fails
- **Action**:
  1. Identify failing module
  2. Diagnose root cause:
     - Missing API contract → message teammates
     - Incompatible signatures → decide ownership
     - Transitive mismatch → rebuild in order
  3. Assign fix responsibility
  4. Re-run until GREEN or >2 iterations → rollback

#### Git Push Fail
- **Branch Conflict**: Fetch, rebase/merge, or use new branch
- **Permission Error**: Configure origin, authenticate, retry
- **Status Check Failed**: Don't force push, contact DevOps

### Message Delivery Guarantees

#### Ordering (FIFO per Teammate)
- **Mechanism**: Sequence numbers ensure order
- **Detection**: Gap in sequence_number → request resend
- **Enforcement**: Teammate acknowledges before processing next

#### Lost Message Recovery
- **Detection**: No ACK within 30 sec
- **Action**: Resend with note, wait 30 sec for ACK
- **If no ACK**: escalate to health check

#### Duplicate Messages
- **Detection**: Same sequence_number + timestamp
- **Handler**: Check if already processed, silent ACK if duplicate

### Invariant Violations

#### Hook Violation Mid-Task
- **Trigger**: teammate's code triggers hyper-validate.sh
- **Action**:
  1. Identify teammate via git log
  2. Message: "Fix violation in 10 min or reassign"
  3. Teammate fixes + re-runs local dx.sh
  4. Lead re-runs full dx.sh

#### Assumption Mismatch in Consolidation
- **Scenario**: Teammate assumes behavior another didn't implement
- **Action**:
  1. Identify missing piece
  2. Message responsible teammate
  3. Fix applied + re-run

### Recovery Workflows

#### Halt vs Continue Decision Tree
```
Failure occurs
├─ Blocks ALL teammates? → HALT, session resumption
└─ Local to 1-2 teammates?
   └─ Recoverable in <30 min?
      ├─ YES → Attempt recovery (one pass only)
      └─ NO → HALT, post-mortem
```

#### One-Pass Recovery Protocol
1. Determine recovery in <30 min?
2. If yes: Message teammate with specific fix (20 min deadline)
3. If response: Implement fix + re-run dx.sh
4. If no response: Reassign (6.2.1)

#### Post-Mortem (After Team Failure)
1. Preserve state: checkpoint commit
2. Diagnose root cause
3. Document findings: timeline + analysis
4. Decide: Retry with fixes or rollback

### STOP Conditions (HALT immediately)

| Condition | Action | Resolution |
|-----------|--------|-----------|
| **Circular dependency** | Halt team formation | Lead breaks tie via task ordering |
| **Teammate timeout** (30 min idle) | Message, await 5 min | If no response: CRASH |
| **Task timeout** (2+ hours) | Message for status | If no response: Reassign |
| **Message timeout** (15 min, critical) | Resend [URGENT] | If still no: CRASH |
| **Lead DX fails** | Halt consolidation | Fix incompatibility |
| **Hook violation** | Halt build | Teammate fixes locally |
| **Teammate crash** (>5 min) | Halt task | Reassign or save work |
| **Message delivery fail** | Detect via gap | Resend + ACK |
| **Unrecoverable failure** | Halt team | Post-mortem + retry/rollback |

---

## Session Resumption

### State Persistence

#### What to Persist (50-100 KB per team)
```json
{
  "team_metadata": {team_id, status, teammate_count},
  "task_list": {tasks with checkpoints},
  "mailbox": {append-only message log},
  "teammate_state": {status, heartbeats, changes},
  "session_log": {metrics, last_action}
}
```

#### Where to Persist
- **Primary**: `.team-state/<team-id>/` (Git, durable)
  - metadata.json, mailbox.jsonl, teammates.json, checkpoints/
- **Secondary**: `/tmp/yawl-team-<team-id>/` (Fast, temporary)
  - state.json, messages.log, heartbeats/, checkpoints/

#### Format: JSON with Schema Versioning
- Native to agent frameworks
- Easy diff/merge in git
- Queryable via jq
- Version-controllable for upgrades

### Resume Protocol

#### Commands
```bash
# Resume specific team
claude ... --resume-team τ-engine+schema+test-iDs6b

# List resumable teams
claude ... --list-teams

# Probe team liveness
claude ... --probe-team τ-engine+schema+test-iDs6b
```

#### Resume Validation Sequence (20 sec total)
1. **DISCOVERY** (0.5s): Load metadata.json
2. **VERIFY TEAM STATUS** (1s): Check "active"/"suspended"
3. **VALIDATE LEAD SESSION** (1s): Check session ID
4. **PROBE TEAMMATES** (2-10s): Check heartbeats (<10min: online, 10-30min: idle, >30min: offline)
5. **VERIFY FACTS** (1s): Compare checksums
6. **LOAD MAILBOX** (2s): Show last 5 messages
7. **DISPLAY SUMMARY** (1s): Team status ready

#### Exit Codes
- 0 = success, team attached
- 1 = transient error (retry)
- 2 = permanent error (unrecoverable)

### Teammate Reconnection

#### Heartbeat Mechanism
- **Every 60s**: Teammate emits timestamp heartbeat
- **Lead monitoring**: Every 30s, check for stale teammates
- **Status logic**:
  - <10 min: online (can message)
  - 10-30 min: idle (send wake-up, wait 30s)
  - 30-60 min: suspended (can reassign)
  - >60 min: stale (fail resume)

#### ZOMBIE Mode (Lead Timeout)
- **Trigger**: Lead offline >5 min
- **Behavior**:
  - Continue local work
  - Auto-checkpoint every 30s
  - Pause new messages
  - Await lead resume

#### Teammate In-Progress Recovery
1. Teammate executing task → lead timeout → enters ZOMBIE
2. Lead resumes → detects ZOMBIE → messages teammate
3. Teammate responds with status
4. Lead decides: continue, commit, or rollback

### Checkpointing Strategy

#### Triggers
| Trigger | Frequency | Latency |
|---------|-----------|---------|
| Task status change | per transition | <5 sec |
| Message sent/received | every 5 messages OR 30s | <30 sec |
| DX run complete | on-demand | <5 sec |
| Periodic timer | every 5 min | <10 sec |
| Pre-consolidation | before git add | <10 sec |

#### Minimal State to Restore (~10 KB)
```json
{
  "team_id": "τ-abc123",
  "status": "active",
  "task_queue": {pending, in_progress, completed},
  "teammate_roster": {name, status, heartbeat},
  "checkpoint_info": {last_seq, timestamp}
}
```

#### Recovery Time Target: <5 Minutes
- Timeline: 0s invoke → 1s load → 3s probe → 10s mailbox → 15s complete ✓

### Failure Scenarios & Recovery

#### Lead Timeout (Mid-Consolidation)
- **Detection**: Dirty git state + consolidating status
- **Recovery options**:
  - RETRY: Re-run dx.sh all + hook check
  - ABORT: Reset to last good commit
  - INSPECT: Show staged changes manually

#### Teammate Timeout (Mid-Task)
- **Recovery options**:
  - AUTO-RESTART: Wait for auto-reconnect, load checkpoint
  - MANUAL REASSIGN: Force reassign, new teammate inherits checkpoint
  - ROLLBACK: Reset, task returns to pending

#### Network Partition
- **Scenario**: Lead ↔ teammate isolated (both alive)
- **Detection**: Message delivery timeout
- **Action**: Retry, reassign, or wait for heal
- **Guarantee**: Idempotent messaging (seq numbers)

#### Clock Skew
- **Mitigation**:
  1. Use UTC milliseconds (canonical)
  2. Use monotonic sequence numbers (immune to clock)
  3. Warn on negative elapsed time

### Architecture

#### State Machine (Team Lifecycle)
```
[INIT] → [PENDING] → [ACTIVE]
    ↓           ↓         ↓
  spawn    all online  lead online?
            ↓           ↓
         [CONSOLIDATING] → [COMPLETE]
            ↓
        retry or rollback
```

#### ZOMBIE Mode States
- **Lead online**: Normal operation
- **Lead timeout (5-30 min)**: ZOMBIE mode, auto-checkpoint
- **Lead offline (>30 min)**: Fail resume, recommend new team

### Implementation Checklist

**Core**:
- [ ] `.team-state/` schema + validation
- [ ] `resume-team-validation.sh` hook
- [ ] `checkpoint-team.sh` hook
- [ ] `--list-teams` command
- [ ] `--probe-team` liveness check

**Teammate side**:
- [ ] Auto-heartbeat every 60s
- [ ] ZOMBIE mode detection
- [ ] Auto-checkpoint every 30s
- [ ] `--resume-team <id> --task <task-id>` re-attachment

**Recovery**:
- [ ] Partial consolidation detection
- [ ] Task reassignment
- [ ] Checkpoint recovery
- [ ] Post-mortem workflow

---

## Reference

- CLAUDE.md: "Teams" section and GODSPEED flow
- `.claude/hooks/team-recommendation.sh`: Hook for team validation
- `ggen.toml`: Generation configuration
- Observatory script: Fact validation for state persistence
