# Team Session Resumption — Phase 3 Architecture

**Problem**: Team state is ephemeral. If lead times out, all team context (task_list, mailbox, teammate_ids) is lost. Teammates orphaned. Expensive work abandoned.

**Goal**: Design durable team state persistence + recovery protocol. Enable leads to re-attach to running teams without restart penalty.

---

## 1. State Persistence Model

### 1.1 Team State Definition

**Minimal state to persist**:

```yaml
team_session:
  id: "team-<quantums>-<sessionId>"  # UUID, immutable
  created_at: "2026-02-20T14:23:45Z"
  status: {pending, active, consolidating, complete}
  lead_session_id: "claude-<leadId>"

  teammates: [
    {
      id: "tm_1",
      name: "Engineer A",
      quantum: "engine",
      status: {idle, in_progress, blocked, complete},
      claimed_task_id: "task_engine_001",
      context_window_usage: 45,  # %
      last_heartbeat: "2026-02-20T14:25:12Z",
      last_message_seq: 42
    }
  ]

  tasks:
    pending: ["task_schema_001", "task_integration_001"]
    in_progress: [
      {
        id: "task_engine_001",
        quantum: "engine",
        assigned_to: "tm_1",
        description: "Fix YNetRunner deadlock",
        dependencies: [],
        status: in_progress,
        created_at: "2026-02-20T14:20:00Z"
      }
    ]
    completed: ["task_test_001"]

  mailbox: [
    {
      seq: 40,
      from: "tm_1",
      to: "tm_2",
      timestamp: "2026-02-20T14:24:18Z",
      type: {info, question, challenge, resolution},
      body: "Found race in executor.advance() line 427"
    }
  ]

  shared_facts:
    modules_json_sha: "abc123def456",
    gates_json_sha: "xyz789uvw012",
    shared_src_json_sha: "pqr345stu678"

  metadata:
    N: 3,  # teammate count
    divergence_detected: false,
    lead_last_seen: "2026-02-20T14:25:30Z"
```

### 1.2 Persistence Targets

**Option A (Primary): Git branch**
- Store state in `.claude/teams/<team-id>/state.json`
- Append-only mailbox: `.claude/teams/<team-id>/mailbox.log` (JSONL)
- Task history: `.claude/teams/<team-id>/tasks.jsonl`
- Advantages: Version control, searchable, audit trail
- Risk: Git overhead if high message frequency

**Option B (Fallback): Temp directory**
- `~/.claude/teams/<team-id>/state.json` (persistent across sessions)
- Fast writes, no git overhead
- Risk: Lost if temp cleared (mitigated by Option A)

**Hybrid (Recommended)**:
- Real-time state → Option B (low latency)
- Checkpoint every 30 sec → Option A (git commit)
- On-demand checkpoint: `claude ... --checkpoint-team <team-id>`

### 1.3 File Format & Serialization

```json
{
  "version": "1.0",
  "team_id": "team-engine+schema+test-iDs6b",
  "checkpoint_sequence": 127,
  "created_utc_ms": 1740069825000,
  "updated_utc_ms": 1740069945000,
  "lead_session_id": "claude-lead-ABC123",
  "status": "active",
  "teammate_count": 3,
  "teammates": [
    {
      "id": "tm_1",
      "index": 0,
      "role": "engineer",
      "quantum": "engine",
      "status": "in_progress",
      "task_claimed": "task_engine_001",
      "context_usage_percent": 45,
      "heartbeat_utc_ms": 1740069912000,
      "last_message_seq": 42,
      "last_checkpoint_seq": 127
    }
  ],
  "task_queue": {
    "pending": ["task_schema_001", "task_int_001"],
    "in_progress": [
      {
        "id": "task_engine_001",
        "quantum": "engine",
        "assigned_to": "tm_1",
        "dependencies": [],
        "status": "in_progress",
        "created_utc_ms": 1740069800000,
        "description": "Fix YNetRunner deadlock"
      }
    ],
    "completed": ["task_test_001"]
  },
  "mailbox_seq": 45,
  "facts_checksums": {
    "modules": "abc123",
    "gates": "xyz789",
    "shared_src": "pqr345"
  }
}
```

---

## 2. Resume Protocol

### 2.1 Lead Re-Attachment

**Command**:
```bash
# List all dormant teams
claude ... --list-teams

# Resume specific team (lead reconnects)
claude ... --resume-team team-engine+schema+test-iDs6b

# Verify team is still alive before resuming
claude ... --probe-team team-engine+schema+test-iDs6b
```

### 2.2 Resume Validation Sequence

```
Lead calls: --resume-team <team-id>
    ↓
1. Load state.json from ~/.claude/teams/<team-id>/
   ✓ Team found? Proceed.
   ✗ Not found? Error: "Team <team-id> not found. Use --list-teams"
    ↓
2. Check team_status
   ✓ active? Proceed.
   ✓ consolidating? Warn: "Team in consolidation. Wait for completion."
   ✗ complete? Error: "Team is complete. Use --review-team <team-id>"
   ✗ pending? Error: "Team not initialized. Spawn with --create-team"
    ↓
3. Verify lead_session_id matches current session
   ✓ Match? Proceed (re-attachment).
   ✗ Mismatch? Allow only if old session timed out (>30 min stale).
   ⚠ Different lead? Warn: "Different lead detected. Coordinator override needed."
    ↓
4. Probe all teammates (check heartbeats)
   For each tm_i:
     timestamp_now = current UTC
     stale_threshold = 10 minutes
     ✓ heartbeat_utc_ms > (timestamp_now - 10min) → Online
     ✗ heartbeat_utc_ms < (timestamp_now - 10min) → Offline (see section 3)
    ↓
5. Verify facts checksums match (observatory hasn't invalidated state)
   ✓ All checksums match? Proceed.
   ✗ Mismatch? Warn: "Facts stale. Run `bash scripts/observatory/observatory.sh`"
    ↓
6. Load mailbox from mailbox.log (append-only)
   Reconstruct full conversation history
   Display last 10 messages to lead
    ↓
7. Display team status summary + task queue
   ✓ Ready to consolidate / continue / monitor

Exit codes:
  0 = success, team re-attached
  1 = transient error (retry)
  2 = permanent error (team unrecoverable)
```

### 2.3 Lead Session State

After re-attachment, lead inherits:

```yaml
resume_context:
  team_id: "team-..."
  lead_session_new: "claude-lead-XYZ789"  # current session
  lead_session_old: "claude-lead-ABC123"  # previous (timed out)

  visible_state:
    - task_queue (all tasks, not just lead's assignments)
    - mailbox (last 50 messages, full history in .log)
    - teammate_status (online/offline, context usage, last heartbeat)
    - progress_metrics (tasks completed %, ETA)

  actions_available:
    - message <teammate>: send direct message
    - broadcast: announce to all teammates
    - checkpoint: save state to git branch
    - force-reassign <task> <new_tm>: if original assigned teammate offline
    - graceful-shutdown: mark team for consolidation
    - abort-team: kill all teammates (emergency)
```

---

## 3. Teammate Reconnection

### 3.1 Heartbeat Protocol

**Teammate heartbeat**: Every 30 seconds, teammates emit:

```json
{
  "heartbeat": {
    "teammate_id": "tm_1",
    "timestamp_utc_ms": 1740069945000,
    "task_claimed": "task_engine_001",
    "context_usage_percent": 47,
    "status": "in_progress",
    "message_seq": 43,
    "checkpoint_seq": 127
  }
}
```

**Detection**:
- Teammate alive if: `now - heartbeat_utc_ms < 10 minutes`
- Teammate offline if: `now - heartbeat_utc_ms > 10 minutes`

### 3.2 Detecting Lead Timeout

**Teammate detects lead gone**:
```
Teammate's periodic check (every 60 sec):
  ↓
  1. Read state.json
  2. Check: now - lead_last_seen > timeout_threshold (5 min)
  3. ✓ Lead online → Continue
  4. ✗ Lead offline → Enter ZOMBIE mode
     - Mark state.json: "lead_timeout_detected_utc_ms"
     - Pause new messages to mailbox
     - Continue running local task (no blocking)
     - Auto-save checkpoints to temp every 30 sec
     - Wait for lead to resume OR timeout_max (30 min)
```

**Resolution**:
```
Lead resumes:
  ↓
  1. Read state.json, detect "lead_timeout_detected_utc_ms"
  2. Replay mailbox from last_checkpoint_seq
  3. Message all teammates: "Lead resumed. Proceed or wait?"
  4. Mark state: lead_last_seen = now
  5. Sync any teammate auto-saves
```

### 3.3 Teammate In-Progress Recovery

**Task interrupted mid-work**:

```
Scenario: Teammate tm_1 compiling at line 427, lead times out.

tm_1 detects lead offline:
  ✓ Keep compiling (no blocking on lead)
  ✓ Write checkpoint to ~/.claude/teams/<team-id>/tm_1_checkpoint.json
    - file_edits: [...pending writes...]
    - compile_state: {...partial compile output...}
    - last_good_state: {...previous working state...}
  ✓ Enter ZOMBIE mode (poll lead every 60 sec)

Lead resumes:
  ✓ Read state.json, detect tm_1 in_progress
  ✓ Read tm_1_checkpoint.json
  ✓ Message tm_1: "Detected you were interrupted. Status?"
  ✓ tm_1 replies: "Compiled 60%, found 3 warnings in engine module"
  ✓ Lead decides: continue or rollback?
    - Continue: tm_1 resumes from checkpoint
    - Rollback: git checkout <module>, restart task
```

---

## 4. Failure Scenarios

### 4.1 Lead Dies Mid-Consolidation

**State**: Lead in PostTeam consolidation, has called `git add <files>`, not yet `git commit`.

```
Scenario:
  Lead staged 5 files (engine, schema, tests, integration, stateless)
  Git status: added 5 files
  Lead: typing commit message... [TIMEOUT]

Detection (next session):
  ↓
  1. Check git status: unstaged files present?
  2. Check state.json: consolidating = true?
  3. ✓ Both = interrupted consolidation

Recovery:
  Lead resumes:
    ↓
    1. Load state.json
    2. Message teammates: "Consolidation interrupted. Verify your changes?"
    3. Each teammate confirms: "My edits OK" / "Need to fix"
    4. Lead re-runs: bash scripts/dx.sh all (full compile)
    5. Re-runs hooks (H gate)
    6. Either commits (Ω) OR aborts (rollback git)
```

**Prevention**: `--checkpoint-team` before consolidation.

### 4.2 Teammate Dies Mid-Task

**State**: Teammate tm_1 claimed task_engine_001, made file edits, then orphaned.

```
Detection:
  1. Lead monitor: heartbeat_utc_ms stale for tm_1 > 10 min
  2. Load state.json: tm_1.status = in_progress, tm_1.task_claimed = task_engine_001
  3. Check git: any unstaged changes in tm_1's files?
    ✓ Yes → auto-save checkpoint
    ✗ No → proceed to reassign

Recovery (3 options):
  a) Wait 5 min (auto-restart): Claude SDK may restart teammate
  b) Manual reassign: --force-reassign task_engine_001 tm_2
     - tm_2 takes over, inherits checkpoint from tm_1
  c) Rollback: git checkout <files>, return task to pending queue
```

### 4.3 Network Partition (Lead ↔ Teammate)

**State**: Lead + teammates mutually visible to engine, but communication severed.

```
Scenario:
  Lead sends: message tm_1 "What's your status?"
  Message queue: [PENDING, not delivered for 5 min]
  Lead sees: tm_1.heartbeat_utc_ms fresh, but no message ACK

Detection:
  Lead's message timeout: 5 minutes
  Message marked: UNDELIVERED
  Lead decides:
    a) Retry: resend message
    b) Assume stale: tm_1 offline, reassign task
    c) Wait: network may heal

Guarantee:
  No duplicate execution (idempotent messaging).
  If tm_1 already received message once, mark as ACK'd in mailbox.
  Use seq numbers: mailbox[i].seq is monotonic, idempotent key.
```

### 4.4 Clock Skew

**Problem**: Timestamps unreliable across distributed lead + teammates.

```
Mitigation:
  1. Use UTC milliseconds (not local time)
  2. Sync check on resume:
     - Load state.json: created_utc_ms
     - Compute elapsed = now - created_utc_ms
     - If elapsed < 0 → clock skew detected
     ✓ Warn user, don't fail
     ✓ Use relative timestamps (seq numbers) for ordering

  3. Mailbox ordering:
     - Primary key: message.seq (monotonic, assigned by lead)
     - Secondary: message.timestamp (for human readability)
     - Teammate messages: include lead-assigned seq in ACK
```

---

## 5. Checkpointing Strategy

### 5.1 Checkpoint Frequency

```yaml
checkpoint_policy:
  task_completion:
    trigger: Teammate marks task complete (before messaging)
    action: Save state.json + commit to git branch
    latency: <5 sec

  message_received:
    trigger: Every message from any teammate
    action: Append to mailbox.log
    batch_flush: every 30 sec OR 10 messages
    latency: <30 sec

  milestone:
    trigger: Every 30 sec of elapsed time
    action: Save full state.json snapshot
    latency: 30 sec

  consolidation_gate:
    trigger: Before lead consolidates (git add)
    action: Full checkpoint + verify all teammates green
    latency: <10 sec

  manual:
    trigger: claude ... --checkpoint-team <team-id>
    action: Force checkpoint now
    latency: <5 sec
```

### 5.2 Minimal State to Restore

**Critical state** (required to resume):

```yaml
minimal_state:
  team_id: "team-..."
  status: active
  lead_session_id: "current-session"

  # Enough to continue work
  task_queue_current: [pending, in_progress] tasks only
  mailbox_tail: [last 50 messages]
  teammate_roster: [id, status, heartbeat]

  # Enough to verify nothing broke
  git_commit_sha: "abc123def456"  # last known good
  facts_checksums: {...}
```

**Size**: ~50 KB per team (vs 5 MB full codebase).
**Storage**: `.claude/teams/<team-id>/state.json` (git tracked).

### 5.3 Recovery Time Target

**Goal**: Restore team in <5 minutes.

```
Timeline:
  1. Lead calls --resume-team (0 sec)
  2. Load state.json (0.5 sec)
  3. Probe all teammates (2 sec per tm, max 10 sec for 5 teammates)
  4. Replay mailbox (5 sec)
  5. Verify facts checksums (1 sec)
  6. Display summary (0.5 sec)
  ────────────────────────────
  Total: ~20 seconds
  Target met: < 5 min ✓
```

---

## 6. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│ Lead Session (Session 1)                                        │
│  • Creates team, spawns teammates                               │
│  • Reads state.json, sends tasks to mailbox                     │
│  • [TIMEOUT after 30 min of inactivity]                         │
└────┬────────────────────────────────────────────────────────────┘
     │
     ↓ Team state persists

┌─────────────────────────────────────────────────────────────────┐
│ Persistent Storage Layer                                        │
│                                                                 │
│  ~/.claude/teams/team-<id>/                                     │
│    state.json             [Latest checkpoint]                  │
│    mailbox.log            [Append-only message log]            │
│    tasks.jsonl            [Task history]                       │
│    tm_1_checkpoint.json   [Teammate auto-save]                │
│    ...                                                         │
│                                                                 │
│  .claude/teams/team-<id>/                                       │
│    state.json             [Git-tracked backup]                 │
└────┬────────────────────────────────────────────────────────────┘
     │
     ↓ Lead resumes (Session 2)

┌─────────────────────────────────────────────────────────────────┐
│ Lead Session (Session 2 - RESUME)                               │
│  • Call: claude ... --resume-team team-<id>                     │
│  • Validate: state, teammates alive?, facts current?            │
│  • Replay mailbox, display summary                              │
│  • Option: continue work OR consolidate                         │
│  • Update lead_session_id = current session                     │
│  • Mark: lead_last_seen = now                                   │
└────┬────────────────────────────────────────────────────────────┘
     │
     ↓ Lead messages teammates

┌─────────────────────────────────────────────────────────────────┐
│ Teammate Sessions (Always alive, polling for lead)              │
│  • tm_1: in_progress on task_engine_001                         │
│  • tm_2: idle, waiting for task                                 │
│  • tm_3: complete, idle                                         │
│                                                                 │
│  Each teammate:                                                 │
│   - Heartbeat every 30 sec (update state.json)                 │
│   - Poll mailbox every 60 sec for lead messages                 │
│   - On lead timeout: ZOMBIE mode (continue work, pause msgs)   │
│   - On lead resume: sync + proceed                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. State Machine

```
TEAM LIFECYCLE
──────────────

[INIT] → --create-team
    ↓ state.json created, teammates spawned

[PENDING] → all teammates online?
    ↓ yes → transition

[ACTIVE] → teammates execute Ψ→Λ→H→Q→Ω
    ├─ Lead ONLINE: monitor via mailbox
    ├─ Lead TIMEOUT: teammates enter ZOMBIE, auto-checkpoint
    └─ Lead RESUME: --resume-team <id>
       ↓ validate, replay mailbox
       ↓ update lead_last_seen
       ↓ continue [ACTIVE]

[CONSOLIDATING] → all teammates mark complete
    ├─ Lead calling: git add, compile check, hook check
    ├─ Lead TIMEOUT: state saved as "partial_consolidation"
    └─ Lead RESUME: verify git status, retry consolidation
       ↓ all green? → git commit
       ↓ red? → message teammates, fix, retry

[COMPLETE] → git push successful
    ├─ All tasks done, mailbox archived
    ├─ State: .claude/teams/<id>/COMPLETED (immutable)
    └─ Lead can: --review-team <id> (read-only)

TEAMMATE LIFECYCLE (per tm_i)
──────────────────────────────

[IDLE] → claim or wait for task
    ↓ lead assigns → [IN_PROGRESS]

[IN_PROGRESS] → execute Ψ→Λ→H→Q→Ω
    ├─ Lead online: send messages, mark task complete
    ├─ Lead offline: ZOMBIE mode
    │  ├─ Keep executing locally
    │  ├─ Auto-checkpoint every 30 sec
    │  └─ Pause mailbox updates
    └─ Compile red: fix locally, re-run Λ, then proceed

[BLOCKED] → waiting for peer or lead decision
    ├─ Lead online: message for direction
    ├─ Lead offline: auto-timeout after 10 min
    │  └─ Return task to pending queue
    └─ Lead resume: message resolve

[COMPLETE] → task marked done, findings in mailbox
    ├─ Idle for new task OR
    ├─ Auto-idle if no more pending tasks OR
    └─ Await lead shutdown

[ZOMBIE] → lead timeout detected
    ├─ Heartbeat continues (every 30 sec)
    ├─ Auto-checkpoint every 30 sec
    ├─ No new mailbox updates (paused)
    └─ On lead resume: sync checkpoint + proceed

[SHUTDOWN] → lead calls --abort-team OR timeout_max (30 min)
```

---

## 8. Failure Recovery Checklist

| Failure | Detection | Action | Recovery Time |
|---------|-----------|--------|---|
| **Lead timeout** | heartbeat_utc_ms stale | teammates → ZOMBIE | <5 min on resume |
| **Teammate timeout** | tm_i heartbeat > 10 min | reassign task to tm_j | <2 min |
| **Partial consolidation** | git status + state.json | replay, retry consolidation | <3 min |
| **Network partition** | message delivery timeout | retry OR assume offline | <10 min |
| **Clock skew** | elapsed < 0 | use seq numbers, warn | <1 min |
| **Facts stale** | checksum mismatch | warn, recommend observatory | <30 sec |
| **Zombie teammate** | >10 min idle in ZOMBIE | auto-timeout, reassign | <15 min |

---

## 9. Glossary

| Term | Definition |
|------|-----------|
| **Team ID** | Immutable UUID, generated at team creation |
| **Lead session** | Main orchestration session (Claude Code lead agent) |
| **Teammate** | Collaborating agent, one per quantum, own context window |
| **Task** | Atomic work unit, assigned to one teammate, dependencies tracked |
| **Mailbox** | Append-only message log, shared read-only across all agents |
| **Checkpoint** | Snapshot of state.json, persisted to git + temp |
| **ZOMBIE mode** | Teammate state when lead offline but teammate still executing |
| **Consolidation** | Lead final phase: compile all, hook check, git commit |
| **Stale state** | state.json older than facts checksums (facts changed) |
| **Heartbeat** | Periodic ping from teammate, includes status + context usage |

---

## 10. Implementation Roadmap

**Phase 3a (This PR)**: Architecture design (DONE)
**Phase 3b**: Implement state.json persistence layer
**Phase 3c**: Implement heartbeat + probe protocol
**Phase 3d**: Implement --resume-team command
**Phase 3e**: Implement ZOMBIE mode detection + auto-checkpoint
**Phase 3f**: Integration tests (lead timeout + resume scenarios)
**Phase 3g**: Production enable (CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=2)

