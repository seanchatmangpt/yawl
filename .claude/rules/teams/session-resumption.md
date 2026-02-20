# Session Resumption Architecture — Team State Persistence (Phase 3)

**Problem**: Team state is ephemeral. If lead times out, all team context (task_list, mailbox, teammate_ids) is lost. Teammates orphaned. Expensive work abandoned.

**Goal**: Design durable team state persistence + recovery protocol. Enable leads to re-attach to running teams without restart penalty.

---

## 1. State Persistence Model

### 1.1 Team State Inventory

**Complete state to persist**:

```json
{
  "schema_version": "1.0",
  "team_metadata": {
    "team_id": "τ-engine+schema+test-iDs6b",
    "created_at": "2026-02-20T14:32:00Z",
    "lead_session_id": "claude-lead-ABC123",
    "status": "active|suspended|consolidating|completed",
    "teammate_count": 3,
    "last_heartbeat": "2026-02-20T14:45:32Z"
  },
  "task_list": [
    {
      "task_id": "task-engine-001",
      "quantum": "Engine semantic",
      "assigned_to": "Engineer A",
      "status": "pending|in_progress|blocked|completed",
      "dependencies": ["task-schema-001"],
      "created_at": "2026-02-20T14:23:00Z",
      "started_at": "2026-02-20T14:34:00Z",
      "completed_at": null,
      "checkpoint": {
        "last_progress_at": "2026-02-20T14:44:00Z",
        "files_modified": ["yawl/engine/YNetRunner.java"],
        "local_dx_status": "green|red|pending",
        "checkpoint_seq": 127
      }
    }
  ],
  "mailbox": [
    {
      "msg_id": "msg-001",
      "seq": 42,
      "from": "Engineer A",
      "to": "Engineer B|*",
      "timestamp": "2026-02-20T14:43:15Z",
      "type": "info|question|challenge|resolution",
      "subject": "State machine finding",
      "body": "Found deadlock at line 427...",
      "acked_by": ["Engineer B", "lead"]
    }
  ],
  "teammate_state": [
    {
      "name": "Engineer A",
      "session_id": "claude-tm-DEF456",
      "status": "active|idle|suspended|disconnected",
      "role": "engineer|validator|tester|integrator",
      "last_seen": "2026-02-20T14:45:30Z",
      "last_heartbeat_seq": 87,
      "current_task": "task-engine-001",
      "local_changes": {
        "staged": ["yawl/engine/YNetRunner.java"],
        "unstaged": [],
        "branch": "claude/task-engine-001-DEF456"
      },
      "context_usage_percent": 45
    }
  ],
  "session_log": {
    "messages_count": 12,
    "tasks_completed": 1,
    "tasks_in_progress": 2,
    "session_duration_seconds": 847,
    "next_checkpoint_at": "2026-02-20T15:00:00Z",
    "last_action": "task_assignment|message_sent|checkpoint_saved|consolidation_started",
    "last_action_at": "2026-02-20T14:45:00Z"
  }
}
```

**Size estimate**: 50-100 KB per team (even with 1000+ messages).

### 1.2 Persistence Locations (Hybrid Strategy)

**Primary: Git branch** (durable, auditable)
```
.team-state/<team-id>/
  ├─ metadata.json           # Team metadata + task_list
  ├─ mailbox.jsonl           # Append-only message log
  ├─ teammates.json          # Teammate session references
  └─ checkpoints/
     └─ checkpoint-<seq>.json # Periodic snapshots
```

**Secondary: Temp files** (fast access during active session)
```
/tmp/yawl-team-<team-id>/
  ├─ state.json              # Live runtime state
  ├─ messages.log            # Append-only message log
  ├─ heartbeats/
  │  ├─ engineer-a.txt       # Last heartbeat timestamp
  │  └─ engineer-b.txt
  └─ checkpoints/
     └─ task-001-<seq>.json  # Teammate auto-saves
```

**Rationale**:
- **Git**: Durable, searchable, version-controllable
- **Temp**: Fast I/O, no git overhead during collaboration
- **Hybrid**: Eventual consistency (sync every 30s)

### 1.3 Format: JSON (Flat Structure, Schema Versioning)

**Why JSON?**
- Native to agent frameworks
- Easy diff/merge in git
- Queryable via jq
- Version-controllable

**Schema versioning**:
```json
{
  "schema_version": "1.0",
  "schema_date": "2026-02-20",
  "compatibility": {
    "v1.0": "initial",
    "v1.1": "adds checkpoint_seq field",
    "upgrade_rule": "v1.0 → v1.1: auto-assigns checkpoint_seq = 0"
  }
}
```

---

## 2. Resume Protocol

### 2.1 Lead Re-Attachment Command

**CLI syntax**:
```bash
# Resume specific team
claude ... --resume-team τ-engine+schema+test-iDs6b

# List resumable teams
claude ... --list-teams

# Probe team liveness before resume
claude ... --probe-team τ-engine+schema+test-iDs6b
```

### 2.2 Resume Validation Sequence (Lead Perspective)

```
Lead invokes: --resume-team τ-abc123
    ↓
1. DISCOVERY (0.5 sec)
   Load .team-state/τ-abc123/metadata.json
   ✓ File exists? Continue.
   ✗ Not found? Error: "Team not found. Use --list-teams"

2. VERIFY TEAM STATUS (1 sec)
   Check: status field
   ✓ "active" or "suspended"? Continue.
   ✗ "consolidating"? Warn: "Team mid-consolidation. Wait or abort?"
   ✗ "completed"? Error: "Team complete. Use --review-team"

3. VALIDATE LEAD SESSION (1 sec)
   Check: lead_session_id
   ✓ Matches current session? Fast-path.
   ⚠ Different session but >30 min old? Allow re-attach.
   ✗ Current session active elsewhere? Error: "Lead already attached"

4. PROBE TEAMMATES (2-10 sec)
   For each teammate:
     hb_file = /tmp/yawl-team-<team-id>/heartbeat-<name>.txt
     ✓ Exists AND fresh (<10 min)? Status = "online"
     ⚠ Exists BUT stale (10-30 min)? Status = "idle"
     ✗ Missing OR >30 min old? Status = "offline"

5. VERIFY FACTS (1 sec)
   Load .team-state/τ-abc123/metadata.json → facts_checksums
   Compare to current: bash scripts/observatory/observatory.sh --checksum
   ✓ Match? Continue.
   ⚠ Mismatch? Warn: "Facts stale. Observatory changed. Safe to proceed but run observatory?"

6. LOAD MAILBOX (2 sec)
   Read .team-state/τ-abc123/mailbox.jsonl
   Reconstruct conversation (100 lines max)
   Display last 5 messages to lead

7. DISPLAY SUMMARY (1 sec)
   Status:
     • Team ID: τ-abc123
     • Created: 847 seconds ago
     • Tasks: 1 completed, 2 in_progress, 1 pending
     • Teammates: 3 online, 0 idle, 0 offline
     • Last activity: 2 min ago (message from Engineer A)
     • Next checkpoint: scheduled for 14:50

   Ready for: continue, consolidate, or message teammates

Exit codes:
  0 = success, team attached, ready to proceed
  1 = transient error (retry logic)
  2 = permanent error (team unrecoverable)
```

**Elapsed time**: ~20 seconds total (well under 5-min SLA).

### 2.3 Team Liveness Verification

**Heartbeat freshness logic**:

```
for each teammate:
  last_hb = read(heartbeat-<name>.txt)
  elapsed = now - last_hb

  if elapsed < 10 min:
    status = "online"      → can message immediately
  elif elapsed < 30 min:
    status = "idle"        → send wake-up, wait 30s
  elif elapsed < 60 min:
    status = "suspended"   → can reassign task
  else:
    status = "stale"       → fail resume, recommend new team
```

---

## 3. Teammate Reconnection

### 3.1 Teammate Heartbeat Mechanism

**Every 60 seconds**, each teammate emits:

```bash
# In teammate session (post-edit hook):
echo "$(date +%s)" > /tmp/yawl-team-<team-id>/heartbeat-<name>.txt
```

**Content**: Unix timestamp (monotonic, clock-skew safe).

**Lead monitoring** (every 30 sec):
```bash
for teammate in $(ls /tmp/yawl-team-<team-id>/heartbeat-*.txt 2>/dev/null); do
  last_hb=$(cat "$teammate" 2>/dev/null || echo 0)
  elapsed=$(($(date +%s) - last_hb))

  if [ $elapsed -gt 600 ]; then  # 10 min
    mark_teammate_idle "$teammate"
  fi
done
```

### 3.2 Teammate Detects Lead Timeout (ZOMBIE Mode)

**Teammate's periodic check** (every 60 sec):

```bash
# In teammate session:
team_state_file=.team-state/<team-id>/metadata.json
lead_last_seen=$(jq -r '.session_log.lead_last_seen_at' "$team_state_file")
elapsed=$(($(date +%s) - lead_last_seen))

if [ $elapsed -gt 300 ]; then  # 5 min
  # Lead offline, enter ZOMBIE mode
  jq '.session_log.lead_timeout_detected_at = "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'"' \
    "$team_state_file" > "$team_state_file.tmp"
  mv "$team_state_file.tmp" "$team_state_file"

  # Continue work but pause new messages
  echo "[ZOMBIE] Lead offline. Continuing task locally. Checkpoints every 30s."

  # Auto-checkpoint every 30s (non-blocking)
  while [ $elapsed -gt 300 ]; do
    bash .claude/hooks/checkpoint-team.sh <team-id> <task-id> &
    sleep 30
    elapsed=$(($(date +%s) - lead_last_seen))
  done
fi
```

### 3.3 Teammate In-Progress Recovery

**Task interrupted mid-execution**:

```
Teammate executing task-engine-001
  ↓
Lead timeout detected (>5 min stale)
  ↓
Teammate enters ZOMBIE:
  • Continues running local DX
  • Auto-saves checkpoint to /tmp/yawl-team-<team-id>/task-001-<seq>.json
  • Checkpoint includes:
    - files_modified: [list of changed files]
    - dx_status: "green|red|pending"
    - git_branch: current branch state
    - last_progress: timestamp
  ↓
Lead resumes: --resume-team τ-abc123
  ↓
Lead reads metadata.json:
  • Detects lead_timeout_detected_at
  • Loads latest checkpoint from checkpoints/
  • Messages Teammate: "Detected ZOMBIE at 14:42. Status?"
  ↓
Teammate responds (in live session):
  "Fixed guard logic, tests passing. Ready to proceed or commit?"
  ↓
Lead decides:
  A) Continue: Teammate resumes from checkpoint
  B) Commit: Lead runs dx.sh all, commits changes
  C) Rollback: git reset --hard, start task over
```

---

## 4. Failure Scenarios & Recovery

### 4.1 Lead Timeout (Mid-Consolidation)

**Scenario**: Lead at PostTeam consolidation step.
```
Lead: git add yawl/engine/** schema/** yawl/integration/** [DONE]
Lead: Typing commit message... [TIMEOUT - no more input]
```

**Detection**:
```
Next session (lead resumes):
  ↓
  1. Check git status:
     git status → "Changes to be committed: [5 files]"
  ↓
  2. Check state.json:
     status = "consolidating"
     last_action = "consolidation_started"
     last_action_at = "2026-02-20T14:42:00Z" (was 8 minutes ago)
  ↓
  3. Conclusion: Partial consolidation detected
```

**Recovery options**:
```
Lead resumes → Resume hook detects dirty state
  ↓
Prompt lead: "Last session was consolidating. Action?"
  ↓
  A) RETRY: Run consolidation again
     → Re-run: bash scripts/dx.sh all (verify green)
     → Re-run: hook check (verify H gate clear)
     → Commit with original message
  ↓
  B) ABORT: Rollback to last known good
     → git reset --hard <last-good-commit>
     → Reopen all tasks for team to redo
  ↓
  C) INSPECT: Show me what's staged
     → Display: git diff --cached
     → Manual intervention on each file
```

**Prevention**: Auto-checkpoint before consolidation (`--checkpoint-team`).

### 4.2 Teammate Timeout (Mid-Task)

**Scenario**: Engineer A executing task-engine-001, session dies mid-DX.

```
Teammate: bash scripts/dx.sh -pl yawl-engine [33% complete]
          Then [TIMEOUT - network hiccup, session ends]
```

**Detection**:
```
Lead monitoring (every 30s):
  ↓
  1. Check heartbeat for Engineer A:
     last_hb = /tmp/yawl-team-<team-id>/heartbeat-engineer-a.txt
     stale >15 min? → Mark offline
  ↓
  2. Load state.json:
     Engineer A.status = "in_progress"
     Engineer A.current_task = "task-engine-001"
  ↓
  3. Check for auto-checkpoint:
     ls .team-state/<team-id>/checkpoints/task-engine-001-*.json
     Found? → Checkpoint exists (good!)
```

**Recovery (3 options)**:

```
A) AUTO-RESTART (preferred):
   Lead waits 5 min for teammate to auto-restart
   Teammate re-attaches: claude ... --resume-team <team-id> --task task-engine-001
   Teammate loads checkpoint from disk
   Teammate resumes DX from last known state

B) MANUAL REASSIGN:
   Lead: --force-reassign task-engine-001 Engineer-B
   Engineer B claims task
   Engineer B: --resume-team <team-id> --task task-engine-001
   Engineer B inherits checkpoint from Engineer A
   Engineer B verifies checkpoint state, continues

C) ROLLBACK:
   Lead: git reset --hard <last-good-commit>
   Task returned to pending queue
   New teammate starts from scratch
```

### 4.3 Network Partition (Lead ↔ Teammate Isolated)

**Scenario**: Lead can't reach teammates (but both alive, TCP/IP broken).

```
Lead sends: message Engineer-A "What's your status?"
Message queued locally: /tmp/yawl-team-<team-id>/outbox/msg-001.json
[Network hiccup, no delivery for 5 min]

Meanwhile, Engineer A:
  • Still executing task-engine-001
  • Unaware of lead's message
  • Auto-checkpointing every 30s
  • Heartbeat updating every 60s
```

**Detection** (in lead):
```
Lead checks message delivery status (after 5 min):
  outbox/msg-001.json.status = "PENDING"
  ↓
  Decision:
    A) Retry: resend message
    B) Assume offline: reassign task
    C) Wait: network may heal
```

**Guarantee**: Idempotent messaging (seq numbers prevent duplicates).

### 4.4 Clock Skew (Unreliable Timestamps)

**Scenario**: Lead clock differs from teammate clock by 5 minutes.

```
Heartbeat logic fails:
  Lead: now = 14:45
  Teammate HB: now = 14:40 (5 min behind)
  elapsed = 14:45 - 14:40 = 5 min (appears fresh, but really offline 30+ min!)
```

**Mitigation**:

1. **Use UTC milliseconds** (not local time)
   ```json
   "timestamp_ms": 1740069825000,  // Canonical
   "timestamp_iso": "2026-02-20T14:45:30Z"  // Human-readable only
   ```

2. **Use monotonic sequence numbers** (immune to clock)
   ```json
   "seq": 127,  // Ordering key, not tied to wall clock
   "timestamp_ms": null  // Ignored if seq newer
   ```

3. **Warn on negative elapsed**:
   ```bash
   elapsed=$((current_ts - hb_ts))
   if [ $elapsed -lt -30000 ]; then  # More than 30s skew
     echo "⚠️  Clock skew detected: $(abs($elapsed))ms"
     use_seq_for_ordering  # Switch to seq-based ordering
   fi
   ```

---

## 5. Checkpointing Strategy

### 5.1 Checkpoint Frequency (Event-Driven + Time-Based)

**Triggers**:

| Trigger | Frequency | Action | Latency | Destination |
|---------|-----------|--------|---------|-------------|
| Task status change | per state transition | Save task + metadata | <5 sec | .team-state/ + /tmp/ |
| Message sent/received | every 5 messages OR 30s | Append to mailbox.jsonl | <30 sec | .team-state/ |
| DX run complete | on-demand | Snapshot state.json | <5 sec | .team-state/ |
| Periodic timer | every 5 min | Full state backup | <10 sec | .team-state/ + git commit |
| Explicit --checkpoint-team | on-demand | Force checkpoint now | <5 sec | .team-state/ + git |
| Pre-consolidation | before git add | Verify all tasks green | <10 sec | .team-state/ |

### 5.2 Minimal State to Restore

**Critical state** (required to resume, ~10 KB):
```json
{
  "team_id": "τ-abc123",
  "status": "active",
  "lead_session_id": "current-session",

  "task_queue": {
    "pending": ["task-id-1"],
    "in_progress": [
      {
        "id": "task-001",
        "assigned_to": "Engineer A",
        "status": "in_progress"
      }
    ],
    "completed": ["task-id-0"]
  },

  "teammate_roster": [
    {
      "name": "Engineer A",
      "session_id": "claude-tm-DEF456",
      "status": "online",
      "last_heartbeat": "2026-02-20T14:45:30Z"
    }
  ],

  "checkpoint_info": {
    "last_checkpoint_seq": 127,
    "last_checkpoint_at": "2026-02-20T14:44:00Z"
  }
}
```

**Size**: ~5-10 KB (vs 500 MB+ full codebase).

### 5.3 Recovery Time Target: <5 Minutes

**Timeline breakdown**:

```
[  0s ] Lead invokes: --resume-team τ-abc123
[  1s ] Load metadata.json from disk
[  3s ] Probe teammate heartbeats (2s each × 1 tm)
[ 10s ] Replay mailbox (5s) + verify facts (1s)
[ 15s ] Display summary + ready to proceed
       ↓
Total elapsed: ~15 seconds ✓
SLA target: <5 min (achieved!)
```

---

## 6. Architecture Diagram (ASCII)

```
PERSISTENT LAYER (Git + /tmp)                RUNTIME LAYER (Teammates)

Lead Session 1 (ACTIVE)            ┌─────────────────────────────────┐
├─ Execute Ψ→Λ→H→Q→Ω            │ Teammate 1 (Engineer A)         │
├─ Manage team (task_list)         │ ├─ Task: task-engine-001       │
├─ Monitor via mailbox             │ ├─ Status: in_progress          │
│                                 │ ├─ Heartbeat: <10 min old      │
└─ [TIMEOUT after 30 min idle]   │ └─ Auto-checkpoint every 30s    │
   ↓                              └─────────────────────────────────┘
[STATE PERSISTED]                 ┌─────────────────────────────────┐
                                  │ Teammate 2 (Engineer B)         │
.team-state/τ-abc123/             │ ├─ Task: task-schema-001       │
├─ metadata.json                   │ ├─ Status: idle                │
│  ├─ team_id                     │ ├─ Heartbeat: <2 min old       │
│  ├─ task_list                   │ └─ Waiting for task            │
│  ├─ teammate_state              │ └─────────────────────────────────┘
│  └─ session_log                 ┌─────────────────────────────────┐
│     └─ last_action="task_assigned"                             │ Teammate 3 (Tester C)    │
│                                 │ ├─ Task: task-test-001         │
├─ mailbox.jsonl                  │ ├─ Status: idle                │
│  └─ [seq 40-42: recent msgs]   │ └─ [WAITING FOR ENGINE FIX]     │
│                                 └─────────────────────────────────┘
├─ checkpoints/
│  ├─ checkpoint-126.json         /tmp/yawl-team-<team-id>/
│  └─ checkpoint-127.json         ├─ heartbeat-engineer-a.txt
│     └─ [backed up to git]       ├─ heartbeat-engineer-b.txt
│                                 ├─ heartbeat-engineer-c.txt
   ↓ [3 min later]                └─ state.json (symlink to .team-state)

Lead Session 2 (RESUME)            ┌─────────────────────────────────┐
├─ --resume-team τ-abc123         │ Teammate 1 (STILL ALIVE)       │
├─ Load metadata.json              │ ├─ Heartbeat: fresh           │
├─ Validate teammates              │ ├─ Task: task-engine-001       │
├─ Replay mailbox (last 5 msgs)   │ ├─ Status: in_progress        │
├─ Ready to consolidate            │ └─ Awaiting lead message       │
│                                 └─────────────────────────────────┘
└─ Continue Ψ→Λ→H→Q→Ω
   on PostTeam consolidation
```

---

## 7. State Machine (Team Lifecycle)

```
┌───────────────────────────────────────────────────────────────────┐
│ TEAM STATE TRANSITIONS                                             │
└───────────────────────────────────────────────────────────────────┘

[INIT]
  └─ create-team <quantum-1> <quantum-2> ... <quantum-N>
      └─ status := "pending"
      └─ Generate team_id
      └─ Spawn N teammates
      ↓

[PENDING]
  └─ All teammates online?
      ├─ NO (>10 min timeout) → error, retry
      └─ YES → transition
      ↓

[ACTIVE]
  └─ status := "active"
  └─ Teammates execute tasks, send messages
  │
  ├─ Lead ONLINE (last_seen <5 min):
  │  └─ Monitor via mailbox
  │  └─ Assign new tasks
  │  └─ Message teammates
  │  └─ [normal operation]
  │
  ├─ Lead TIMEOUT (last_seen >5 min, <30 min):
  │  └─ Teammates enter ZOMBIE mode
  │  └─ Auto-checkpoint every 30s
  │  └─ Continue work (non-blocking)
  │  └─ Await lead resume
  │  └─ [can survive 30 min timeout]
  │
  └─ Lead RESUME:
      └─ --resume-team τ-abc123
      └─ Validate, load state, display summary
      └─ Resume [ACTIVE] or proceed to [CONSOLIDATING]
      ↓

[CONSOLIDATING]
  └─ status := "consolidating"
  └─ Lead: compile all (bash scripts/dx.sh all)
  └─ Lead: hook check (H gate)
  └─ Lead: git add <files>
  │
  ├─ All green → transition
  └─ Red OR hook blocked:
      ├─ Message teammates: "Fix needed"
      ├─ Teammates: fix, re-run local DX
      ├─ Teammates: "Ready to retry"
      └─ Lead: retry consolidation
      ↓

[COMPLETE]
  └─ status := "completed"
  └─ git commit + git push
  └─ Mailbox archived
  └─ .team-state/τ-abc123/ marked IMMUTABLE
  └─ Lead: --review-team (read-only)
```

---

## 8. Implementation Checklist

**Core**:
- [ ] Create `.team-state/` schema + validation script
- [ ] Implement `resume-team-validation.sh` hook
- [ ] Implement `checkpoint-team.sh` hook
- [ ] Extend `session-start.sh` to handle `--resume-team`
- [ ] Add heartbeat monitor to `post-edit.sh`
- [ ] Implement `--list-teams` discovery command
- [ ] Implement `--probe-team` liveness check

**Teammate side**:
- [ ] Auto-emit heartbeat every 60s
- [ ] Detect lead timeout (ZOMBIE mode)
- [ ] Auto-checkpoint every 30s (non-blocking)
- [ ] Implement `--resume-team <id> --task <task-id>` re-attachment

**Recovery**:
- [ ] Detect partial consolidation (dirty git state)
- [ ] Idempotent consolidation retry (post-consolidation.sh)
- [ ] Task reassignment (--force-reassign)
- [ ] Checkpoint recovery from /tmp/

**Testing**:
- [ ] Test 1: Resume after 5 min timeout (happy path)
- [ ] Test 2: Resume after 60 min timeout (ZOMBIE mode)
- [ ] Test 3: Teammate offline, reassign task
- [ ] Test 4: Partial consolidation recovery
- [ ] Test 5: Network partition + message delivery timeout
- [ ] Test 6: Clock skew (negative elapsed) handling

**Documentation**:
- [ ] Add to CLAUDE.md "Deep References" section
- [ ] Create `.claude/TEAM_RESUMPTION_GUIDE.md` (user-facing)

---

## 9. Glossary

| Term | Definition |
|------|-----------|
| **Team ID** | Immutable UUID (τ-quantums-sessionId) |
| **Lead session** | Main orchestration session (lead agent) |
| **Teammate** | Collaborating agent (1 per quantum) |
| **Task** | Atomic work unit (assigned to 1 teammate) |
| **Mailbox** | Append-only message log (JSONL) |
| **Checkpoint** | Snapshot of state.json (git-backed) |
| **Heartbeat** | Periodic ping from teammate (30-60s interval) |
| **ZOMBIE mode** | Teammate state when lead offline (>5 min) |
| **Consolidation** | Lead final phase (compile, hook check, commit) |
| **Stale state** | state.json older than facts checksums |
| **Seq number** | Monotonic message/checkpoint counter (clock-immune) |

---

## 10. Reference

- CLAUDE.md: "⚡ GODSPEED!!! Teams" section
- Team Decision Framework: `.claude/rules/teams/team-decision-framework.md`
- Related hooks: `session-start.sh`, `post-edit.sh`, `session-end.sh`, `post-consolidation.sh`
- Next phase: `.claude/TEAM_RESUMPTION_GUIDE.md` (implementation guide)

