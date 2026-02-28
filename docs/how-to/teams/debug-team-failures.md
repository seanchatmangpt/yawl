# How to Debug Team Failures

**Version:** 6.0.0
**Status:** Production-Ready
**Audience:** Senior Engineers, Team Leads
**Last Updated:** 2026-02-28

---

## Overview

This how-to guide teaches you how to diagnose, recover from, and prevent team failures in YAWL's collaborative multi-agent system (τ architecture). You'll learn to recognize failure patterns, execute recovery procedures, and conduct post-mortems using real-world scenarios and decision trees.

### What You'll Learn

- Recognizing team failure signatures
- Diagnosing root causes via decision trees
- Executing recovery workflows (5+ scenarios)
- Understanding state transitions and checkpoints
- Preventing recurrence with lessons capture

### When to Use This Guide

- Your team is stuck (messages unanswered, builds failing)
- A teammate went offline unexpectedly
- Consolidation failed despite green teammates
- You need to decide: retry, reassign, or rollback

### Related References

- **Full theory**: [.claude/rules/TEAMS-GUIDE.md](./../../../.claude/rules/TEAMS-GUIDE.md)
- **Quick ref**: [.claude/rules/TEAMS-QUICK-REF.md](./../../../.claude/rules/TEAMS-QUICK-REF.md)
- **Decision framework**: [.claude/rules/team-decision-framework.md](./../../../.claude/rules/team-decision-framework.md)

---

## Part 1: Recognizing Team Failures

### Failure Signature Checklist

Use this table to quickly identify what's happening:

| Signature | Symptoms | Timeout | Severity | First Action |
|-----------|----------|---------|----------|--------------|
| **Teammate Idle** | No heartbeat for 10-30 min | 30 min threshold | HIGH | Message with wake-up |
| **Message Lost** | ACK not received, seq gap | 30 sec threshold | MEDIUM | Resend + verify delivery |
| **Circular Dependency** | A waits for B, B waits for A | Detect at formation | CRITICAL | Break tie immediately |
| **Hook Violation** | dx.sh fails with H-violation | Per message cycle | CRITICAL | Message teammate to fix |
| **Lead Offline (ZOMBIE)** | No lead status >5 min | Variable | HIGH | Auto-checkpoint, await lead |
| **Consolidation Fail** | All GREEN but dx.sh all fails | Per compile | CRITICAL | Diagnose incompatibility |
| **Network Partition** | Repeated message timeouts | Per message | MEDIUM | Retry with exponential backoff |
| **Context Exhausted** | Teammate unresponsive, context high | Progressive | HIGH | Force reassign with checkpoint |

### Real-World Examples

#### Example 1: Teammate Idle (Most Common)

```
14:23:45 Lead sends Task#1 to tm_engine: "Investigate YNetRunner deadlock"
14:25:10 tm_engine ACKs: "received, starting analysis"
14:45:30 [ALERT] No heartbeat from tm_engine for 20 minutes
         Last ACK was: "checking synchronized blocks..."
14:48:00 Lead checks .team-state/tm_engine/checkpoint.json
         {
           "last_activity": "2026-02-28T14:25:30Z",
           "current_file": "YNetRunner.java",
           "line": 342,
           "context": "analyzing lock contention patterns"
         }
```

**Diagnosis**: tm_engine is likely:
- Stuck in analysis paralysis (researching rather than implementing)
- Experiencing local dx.sh hang
- Network connectivity issue

**Recovery**: Message teammate with specific question, 10-minute deadline

#### Example 2: Message Delivery Failure (Sequence Gap)

```
Mailbox shows:
  seq: 41, ts: 14:32:15, lead→tm_schema: "Define SLA field as long"
  seq: 42, ts: 14:32:35, lead→tm_schema: "[RESEND] Define SLA field..."
  seq: 43, ts: 14:32:55, lead→tm_schema: "[URGENT] Respond to seq#41"
  MISSING: ack_sequence: 41, 42, 43
  seq: 44, ts: 14:33:30, lead→tm_schema: "[CRITICAL] Team blocked, must respond"
  MISSING: ack_sequence: 44
```

**Diagnosis**: tm_schema is either:
- Not receiving messages (network issue)
- Receiving but not processing (context overload)
- Crashed silently

**Recovery**: Check heartbeat age, attempt reassign if >5 min stale

#### Example 3: Circular Dependency (Prevents Formation)

```
Task dependency analysis:
  Task A (tm_schema): "Define workflow SLA attribute"
    └─ depends on: Task B output (interface)

  Task B (tm_engine): "Implement SLA tracking in runner"
    └─ depends on: Task A output (schema definition)

DFS cycle detected: A → B → A
Team formation HALTED at 14:30:20
```

**Diagnosis**: Task ordering is wrong; one task must be sequenced before the other

**Recovery**: Have one teammate make initial assumption, other validates in consolidation

#### Example 4: Hook Violation Mid-Task

```
tm_impl commits changes at 14:35:12:
  - YWorkItemImpl.java: Added getScheduledTime() method

Local dx.sh runs clean, commits.
Lead pulls changes, runs dx.sh all:
  $ bash scripts/dx.sh all
  [yawl-engine] BUILD FAILURE
  [yawl-engine] hyper-validate.sh H_STUB violation detected
  [yawl-engine] File: src/main/java/.../YWorkItemImpl.java:427
  [yawl-engine] Pattern: H_STUB (empty return)
  [yawl-engine] Content: public LocalDateTime getScheduledTime() { return null; }
  [yawl-engine] exit code: 2
```

**Diagnosis**: tm_impl implemented a stub (empty return) instead of real logic

**Recovery**: Message tm_impl: "Fix H_STUB violation in getScheduledTime(), re-run local dx.sh"

#### Example 5: Consolidation Failure (All Green Teammates)

```
Team status:
  tm_schema: GREEN ✓ (schema merged)
  tm_engine: GREEN ✓ (engine implementation complete)
  tm_test: GREEN ✓ (tests written and passing)

Lead consolidation phase at 14:50:00:
  $ git diff main..branch | wc -l
  427 lines changed

  $ bash scripts/dx.sh all
  [yawl-engine] BUILD SUCCESS
  [yawl-core] BUILD SUCCESS
  [yawl-test] BUILD SUCCESS
  [yawl-integration] BUILD FAILURE

  Error: Cannot find symbol: YWorkItem.getScheduledTime()
  @ YawnlIntegrationService.java:234

  Cause: tm_test wrote test that depends on method tm_schema didn't export
         in public API
```

**Diagnosis**: Teammates had incompatible assumptions about public interface

**Recovery**: Message responsible teammate, apply quick fix or rollback

### Decision Tree: Is It a Failure or Normal Workflow?

```
Team shows concerning sign
  │
  ├─ No heartbeat for <10 min?
  │  └─ NORMAL: Agent may be in long compute. Continue monitoring.
  │
  ├─ No heartbeat for 10-30 min, messages in mailbox?
  │  └─ IDLE: Agent is alive but unresponsive. Send wake-up message.
  │
  ├─ No heartbeat for >30 min?
  │  └─ STALE: Agent likely crashed. Prepare reassignment.
  │
  ├─ Message seq gap detected?
  │  └─ Message lost (network or delivery). Resend immediately.
  │
  ├─ All teammates GREEN but dx.sh all fails?
  │  └─ Integration issue. Message failing module owner.
  │
  ├─ Hook violation detected?
  │  └─ Code quality failure. Teammate fixes in <10 min or reassign.
  │
  ├─ Task interdependencies form a cycle?
  │  └─ Circular dependency. Break at task formation (recovery too late).
  │
  └─ Team stalled >2 hours without completion signal?
      └─ Task too large. Split and reassign, or escalate.
```

---

## Part 2: Root Cause Diagnosis Workflows

This section provides 5+ complete diagnosis procedures with JSON state examples. Each workflow is a step-by-step checklist you can run in <30 minutes.

### Workflow A: Teammate Unresponsive (No Heartbeat)

**Time to Complete**: 5-15 minutes
**Probability**: 35% of team issues
**Outcome**: Message teammate, reassign, or continue

**1. Gather Status (2 min)**

```bash
# Check last heartbeat
cat .team-state/τ-engine+schema+test-abc123/teammates.json | jq '.[] | select(.id == "tm_engine")'

# Expected output:
{
  "id": "tm_engine",
  "name": "Engineer A (YNetRunner Specialist)",
  "status": "active",
  "last_heartbeat": "2026-02-28T14:45:30Z",
  "heartbeat_age_seconds": 1247,
  "context_tokens_used": 127000,
  "context_tokens_max": 200000,
  "task_current": "Investigate YNetRunner deadlock in synchronized blocks",
  "task_started_at": "2026-02-28T14:25:10Z",
  "task_age_minutes": 20,
  "messages_pending": 0,
  "status_code": "idle"
}
```

**2. Check Age Bracket**

```
Heartbeat age: 1247 seconds = 20.8 minutes

Decision:
  < 600 sec (10 min)  → ONLINE: Can message immediately
  600-1800 sec        → IDLE: Send wake-up, wait 30 sec
  > 1800 sec (30 min) → STALE: Prepare for reassignment
  > 3600 sec (60 min) → DEAD: Abort task, reassign
```

Current situation: **IDLE** (20 min). Proceed to step 3.

**3. Examine Task State**

```bash
# Check task checkpoint
cat .team-state/τ-engine+schema+test-abc123/checkpoints/tm_engine-latest.json

# Expected output:
{
  "checkpoint_id": "ckpt-tm_engine-202602281425",
  "timestamp": "2026-02-28T14:25:30Z",
  "task_id": "Task#1-engine-investigate-deadlock",
  "task_name": "Investigate YNetRunner deadlock in synchronized blocks",
  "completion_percent": 45,
  "last_action": "git clone yawl && git log --oneline --max-count=20 | grep -i deadlock",
  "next_step": "Run profiler on YNetRunner.main loop",
  "blocking_factor": "Waiting for profiler results",
  "files_modified": 0,
  "git_staged_count": 0,
  "estimated_remaining_minutes": 15,
  "notes": "Found 3 potential deadlock scenarios in synchronized blocks. Narrowing down..."
}
```

**4. Message Teammate with Context**

If heartbeat is 10-30 minutes old and task appears stuck in research:

```json
{
  "sequence": 47,
  "timestamp": "2026-02-28T14:48:00Z",
  "from": "lead",
  "to": "tm_engine",
  "priority": "urgent",
  "payload": "Status check: You've been researching for 23 minutes. Your last action was profiling YNetRunner. Did you find the deadlock? If yes, implement the fix and run local dx.sh. If still investigating, send an update in 5 minutes or we'll reassign. You're at 45% completion; we need forward motion.",
  "timeout": "5min",
  "required_response": true,
  "escalation": "If no response in 5 min, task will be reassigned to backup tm_engine"
}
```

**5. Interpret Response**

| Response | Meaning | Next Action |
|----------|---------|------------|
| "Found deadlock in DataQueue.offer(). Implementing fix now." | Unblocked, continue | Continue monitoring, expect progress in 10 min |
| "Still narrowing down root cause. Need 10 more min." | Blocked but making progress | Allow extension, re-message in 10 min if needed |
| "Stuck on understanding the context. Need help." | Blocked, needs collab | Assign co-task to tm_util, have them pair |
| No response after 5 minutes | Possible crash | Initiate Workflow B (Teammate Crash) |

**6. Decision Point**

```
Is teammate responsive and making progress?
  ├─ YES: Continue team, monitor heartbeat every 30 sec
  ├─ MAYBE: Wait 10 min, re-message with specific question
  └─ NO: Continue to Workflow B (Crash Recovery)
```

### Workflow B: Teammate Crash (Context Exhausted)

**Time to Complete**: 10-20 minutes
**Probability**: 15% of team issues
**Outcome**: Checkpoint, reassign, continue or rollback

**Trigger Conditions**

- Heartbeat age > 30 minutes
- No ACK to 3+ consecutive messages
- Context usage >95% (190k+ of 200k tokens)
- Local dx.sh hang detected (>2 min, no progress)

**1. Confirm Crash Status**

```bash
# Check context usage
cat .team-state/τ-engine+schema+test-abc123/teammates.json \
  | jq '.[] | select(.id == "tm_engine") | .context_tokens_used'
# Output: 198743 (98.4% utilization) ← CRITICAL

# Check for any recent git activity from teammate
git log --author="tm_engine" --since="30 minutes ago" --oneline
# Output: (empty) - no commits in 30 minutes

# Check mailbox for ACKs
cat .team-state/τ-engine+schema+test-abc123/mailbox.jsonl \
  | jq 'select(.from == "tm_engine" and .type == "ack") | .timestamp' \
  | tail -1
# Output: "2026-02-28T14:25:30Z" - 23 minutes old
```

**2. Assess Checkpoint Readiness**

```bash
# Check completion percentage
jq '.completion_percent' .team-state/τ-engine+schema+test-abc123/checkpoints/tm_engine-latest.json
# Output: 45 (less than 80%, significant work in progress)

# List all files modified by tm_engine
git diff --name-only main..$(git rev-parse HEAD) | xargs -I {} git log --format=%an -- {} | grep tm_engine
# Output:
#   src/main/java/org/yawl/engine/YNetRunner.java
#   src/test/java/org/yawl/engine/YNetRunnerTest.java
```

**3. Save Checkpoint Commit**

```bash
# Stage teammate's work-in-progress
git add -A
git commit -m "Checkpoint: tm_engine crashed at 45% completion (YNetRunner deadlock investigation)"

# Tag checkpoint for easy rollback
git tag -a "checkpoint-tm_engine-crash-202602281450" -m "Before reassignment"
```

**4. Prepare Reassignment Brief**

Create a handoff document for new teammate:

```json
{
  "reassignment_id": "reassign-tm_engine-202602281450",
  "original_task": "Investigate YNetRunner deadlock in synchronized blocks",
  "completion_percent": 45,
  "original_teammate": "tm_engine",
  "new_teammate": "tm_engine_backup",
  "checkpoint_commit": "3c4e8f2a1b9d7e6f5c4a3b2",
  "previous_work_summary": "Found 3 potential deadlock scenarios in synchronized blocks (DataQueue.offer(), WorkQueue.run(), TaskRunner.execute()). Narrowed to DataQueue after profiling. Was implementing fix in YNetRunner.synchronized block at line 427.",
  "files_modified": [
    "src/main/java/org/yawl/engine/YNetRunner.java",
    "src/test/java/org/yawl/engine/YNetRunnerTest.java"
  ],
  "next_steps": [
    "Review checkpoint commit for context",
    "Run: git log --oneline -20 -- src/main/java/org/yawl/engine/YNetRunner.java",
    "Examine YNetRunner.java:427 (likely fix location)",
    "Complete the synchronization fix (tm_engine was 90% done)",
    "Run local dx.sh and commit",
    "Message lead when GREEN"
  ],
  "blocked_on": null,
  "estimated_remaining_minutes": 20,
  "context_preserved": true
}
```

**5. Message New Teammate**

```json
{
  "sequence": 48,
  "timestamp": "2026-02-28T14:50:15Z",
  "from": "lead",
  "to": "tm_engine_backup",
  "priority": "critical",
  "payload": "Reassignment: tm_engine crashed (context exhausted). Task: Complete YNetRunner deadlock fix. Checkpoint at 45% with detailed notes in .team-state/reassign-tm_engine-202602281450.json. The fix was 90% done in synchronized blocks. You have 20 minutes to complete + test + commit. Run local dx.sh before messaging GREEN.",
  "attachment": "reassign-tm_engine-202602281450.json",
  "timeout": "25min",
  "required_response": true
}
```

**6. Decision Point**

```
New teammate accepts reassignment?
  ├─ YES, ready in 25 min: Proceed with team
  ├─ NO, task too complex: Consider single engineer finishing alone
  └─ Task blocked on other team member: Wait for dependency, then reassign
```

### Workflow C: Message Delivery Failure (Sequence Gap)

**Time to Complete**: 3-8 minutes
**Probability**: 8% of team issues
**Outcome**: Resend, verify ACK, continue or diagnose network

**Trigger Condition**

```
Mailbox analysis shows:
  seq: 41, 42, 43, 44, 45 → sent to tm_schema
  ack_sequence: 40 (last ACK)
  Gap: messages 41-45 without ACKs
  Time elapsed: >60 seconds (threshold)
```

**1. Extract Mailbox Evidence**

```bash
# Show last 10 messages in mailbox
tail -10 .team-state/τ-engine+schema+test-abc123/mailbox.jsonl \
  | jq 'select(.to == "tm_schema") | {seq: .sequence, status: .type, ts: .timestamp}'

# Output:
# {"seq":41,"status":"sent","ts":"2026-02-28T14:32:15Z"}
# {"seq":42,"status":"sent","ts":"2026-02-28T14:32:35Z"}
# {"seq":43,"status":"sent","ts":"2026-02-28T14:32:55Z"}
# {"seq":44,"status":"sent","ts":"2026-02-28T14:33:15Z"}
# {"seq":45,"status":"sent","ts":"2026-02-28T14:33:35Z"}
```

**2. Check Teammate Status**

```bash
# Is tm_schema still alive?
jq '.[] | select(.id == "tm_schema")' .team-state/τ-engine+schema+test-abc123/teammates.json

# Output (check heartbeat_age_seconds):
{
  "id": "tm_schema",
  "status": "active",
  "last_heartbeat": "2026-02-28T14:33:45Z",
  "heartbeat_age_seconds": 35,  ← GOOD (under 600 sec)
  "context_tokens_used": 89000,  ← GOOD (under 95%)
  "messages_pending": 5          ← BAD (accumulating)
}
```

**Analysis**: tm_schema is alive but not processing messages (5 pending). Likely stuck on current task or local dx.sh hang.

**3. Resend with Explicit ACK Request**

```json
{
  "sequence": 46,
  "timestamp": "2026-02-28T14:33:52Z",
  "from": "lead",
  "to": "tm_schema",
  "priority": "high",
  "type": "resend_batch",
  "resending_sequences": [41, 42, 43, 44, 45],
  "payload": "[RESEND BATCH] Messages 41-45 were not acknowledged. Please ACK each: (1) Define SLA as long field, (2) Export SLA in WorkflowObject interface, (3) Add SLA getter method, (4) Update schema validation, (5) Run local dx.sh.",
  "ack_required": true,
  "ack_timeout": "30sec"
}
```

**4. Monitor for ACKs (30 seconds)**

```bash
# Poll mailbox for acknowledgments
while [ $(date +%s) -lt $(( $(date +%s) + 30 )) ]; do
  ack_count=$(jq -s 'map(select(.from == "tm_schema" and .type == "ack")) | length' \
    .team-state/τ-engine+schema+test-abc123/mailbox.jsonl)

  if [ "$ack_count" -ge 5 ]; then
    echo "ACKs received for all 5 messages"
    break
  fi

  sleep 5
done

# After 30 seconds, check result
jq -s 'map(select(.to == "tm_schema" and .sequence >= 41)) |
  group_by(.sequence) |
  map({seq: .[0].sequence, status: (if any(.type == "ack") then "acked" else "pending" end)})' \
  .team-state/τ-engine+schema+test-abc123/mailbox.jsonl
```

**5. Escalation Decision**

```
After 30 seconds:
  ├─ 5/5 ACKs received: Problem solved, continue team
  ├─ 0-3 ACKs received: Partial response, diagnose further
  │  └─ Check if tm_schema is in local dx.sh hang
  │  └─ If yes: Message to abort dx.sh, continue on main
  │  └─ If no: Unknown blocker, initiate Workflow A (idle check)
  └─ 0 ACKs received: No response after resend
      └─ Escalate to Workflow B (check if crashed)
```

### Workflow D: Circular Dependency Detection (Pre-Mortems)

**Time to Complete**: 5 minutes (detection) + 10 minutes (resolution)
**Probability**: 5% of team formations (preventable)
**Outcome**: Reorder tasks or abort formation

**Detection: Before Team Formation**

This should be caught BEFORE teammates are assigned, but if missed, here's how to detect:

```bash
# Analyze task dependency graph
cat .team-state/team-manifest.json | jq '.tasks[] | {id, name, depends_on}'

# Example output showing cycle:
{
  "id": "Task#1",
  "name": "Define SLA schema",
  "depends_on": ["Task#2"]
}
{
  "id": "Task#2",
  "name": "Implement SLA engine tracking",
  "depends_on": ["Task#1"]  ← CYCLE: Task#2 depends on Task#1, Task#1 depends on Task#2
}
```

**1. Run Cycle Detection**

```bash
# DFS-based cycle detection (pseudo-code, implement in your tool)
function detect_cycle(tasks):
  for each task in tasks:
    visited = set()
    if dfs_has_cycle(task, visited, tasks):
      return CYCLE_FOUND
  return NO_CYCLE

function dfs_has_cycle(task, visited, tasks):
  if task in visited:
    return TRUE  # Cycle detected
  visited.add(task)
  for each dependency in task.depends_on:
    if dfs_has_cycle(dependency, visited, tasks):
      return TRUE
  visited.remove(task)  # Backtrack
  return FALSE
```

**2. Report Findings**

```json
{
  "cycle_detection_timestamp": "2026-02-28T14:55:00Z",
  "status": "CYCLE_FOUND",
  "cycle_chain": [
    {
      "task_id": "Task#1",
      "task_name": "Define SLA schema",
      "depends_on": "Task#2"
    },
    {
      "task_id": "Task#2",
      "task_name": "Implement SLA engine tracking",
      "depends_on": "Task#1"
    }
  ],
  "cycle_length": 2,
  "recommendation": "Break cycle by reordering: Have tm_schema define SLA as provisional type first (Task#1 independent), then tm_engine implements (Task#2 depends on Task#1)"
}
```

**3. Resolution: Reorder Tasks**

**Option A: Sequential then Parallel** (Recommended for tight coupling)

```
Original (Impossible):
  tm_schema waits for tm_engine
  tm_engine waits for tm_schema

Reordered (Working):
  PHASE 1 (Sequential):
    tm_schema: Define SLA as long (provisional interface)
    └─ Publish interface contract to mailbox

  PHASE 2 (Parallel):
    tm_engine: Implement SLA tracking (depends on interface from PHASE 1)
    tm_test: Write integration tests (depends on interface from PHASE 1)

  PHASE 3 (Consolidation):
    All teammates validate interface matches, refine as needed
```

**Option B: Contract-First** (For interface-heavy work)

```
Both teammates work in parallel assuming interface:
  // Agreed contract (in mailbox):
  interface WorkflowObject {
    long getScheduledTime();
    void setScheduledTime(long ms);
  }

  tm_schema: Implements schema assuming interface above
  tm_engine: Implements engine assuming interface above

Then in consolidation: Validate both implementations match contract.
If mismatch: Fix discrepancies in post-consolidation.
```

**4. Update Team Manifest**

```json
{
  "team_id": "τ-engine+schema+test-abc123",
  "tasks": [
    {
      "id": "Task#1",
      "name": "Define SLA schema (provisional)",
      "assigned_to": "tm_schema",
      "depends_on": [],  ← BROKEN CYCLE: Now independent
      "sequence": 1      ← ORDERED: Runs first
    },
    {
      "id": "Task#2",
      "name": "Implement SLA engine tracking",
      "assigned_to": "tm_engine",
      "depends_on": ["Task#1"],  ← ORDERED: Depends on Task#1 completion
      "sequence": 2              ← ORDERED: Runs after Task#1
    }
  ]
}
```

**5. Notify Teammates**

```json
{
  "sequence": 1,
  "timestamp": "2026-02-28T14:55:30Z",
  "from": "lead",
  "to": ["tm_schema", "tm_engine"],
  "type": "team_reordered",
  "payload": "Circular dependency detected and resolved. New task order: (1) tm_schema defines SLA schema, (2) tm_engine implements tracking. tm_schema: Start Task#1 immediately. tm_engine: Wait for tm_schema's completion notification, then start Task#2. Both will use SLA interface contract defined in shared mailbox.",
  "task_order": [
    {"sequence": 1, "task": "Task#1-schema", "assigned_to": "tm_schema"},
    {"sequence": 2, "task": "Task#2-engine", "assigned_to": "tm_engine"}
  ]
}
```

### Workflow E: Hook Violation Mid-Task

**Time to Complete**: 5-10 minutes
**Probability**: 12% of team issues
**Outcome**: Teammate fixes, re-runs dx.sh, continues

**Trigger Condition**

```
Lead runs consolidation phase:
  $ bash scripts/dx.sh all
  [yawl-engine] hyper-validate.sh detected violation
  [yawl-engine] Pattern: H_STUB (empty return)
  [yawl-engine] File: src/main/java/.../YWorkItemImpl.java:427
  [yawl-engine] Content: public LocalDateTime getScheduledTime() { return null; }
  [yawl-engine] Commit: 3f5e8a1b2c4d (by tm_impl)
```

**1. Identify Violating Teammate**

```bash
# Get commit author
git log --format=%an 3f5e8a1b2c4d | head -1
# Output: tm_impl

# Get exact violation details
git show 3f5e8a1b2c4d:src/main/java/.../YWorkItemImpl.java | sed -n '425,430p'
# Output:
# 425: public LocalDateTime getScheduledTime() {
# 426:   return null;  ← VIOLATION: Stub return
# 427: }
```

**2. Parse Violation Details**

```json
{
  "violation_id": "H_STUB_20260228_145030",
  "pattern": "H_STUB",
  "severity": "FAIL",
  "file": "src/main/java/org/yawl/engine/YWorkItemImpl.java",
  "line": 426,
  "content": "return null;",
  "context": "public LocalDateTime getScheduledTime() { return null; }",
  "violating_commit": "3f5e8a1b2c4d",
  "violating_teammate": "tm_impl",
  "fix_guidance": "Either implement real logic or throw UnsupportedOperationException",
  "timestamp": "2026-02-28T14:50:30Z"
}
```

**3. Message Teammate with Deadline**

```json
{
  "sequence": 49,
  "timestamp": "2026-02-28T14:50:45Z",
  "from": "lead",
  "to": "tm_impl",
  "priority": "critical",
  "type": "hook_violation_fix_request",
  "payload": "Your commit 3f5e8a1b2c4d has a hook violation: H_STUB in YWorkItemImpl.getScheduledTime() (line 426). You have 10 MINUTES to fix. Option A: Implement real logic for getScheduledTime(). Option B: Replace 'return null;' with 'throw new UnsupportedOperationException(\"...\");'. After fix, run local dx.sh to verify, then re-commit and message FIXED. No fix by 15:00 = task reassignment.",
  "violation_details": "H_STUB_20260228_145030",
  "deadline": "2026-02-28T15:00:45Z",
  "timeout": "10min",
  "required_response": true,
  "escalation": "If no fix by deadline, task will be reassigned and your checkpoint will be preserved"
}
```

**4. Monitor for Fix**

```bash
# Poll for teammate response or new commit
while [ $(date +%s) -lt $(( $(date +%s) + 600 )) ]; do
  # Check if team_impl sent message
  if jq -e '.[] | select(.from == "tm_impl" and .sequence > 49)' \
    .team-state/τ-engine+schema+test-abc123/mailbox.jsonl > /dev/null; then
    echo "tm_impl responded"
    # Extract response status
    jq '.[] | select(.from == "tm_impl" and .sequence > 49) | .payload' \
      .team-state/τ-engine+schema+test-abc123/mailbox.jsonl | tail -1
    break
  fi

  # Check if violation is fixed in latest commits
  git log --oneline -5 | grep "tm_impl"
  if git show HEAD:src/main/java/.../YWorkItemImpl.java | grep -A 2 "getScheduledTime" | grep -q "throw"; then
    echo "Violation appears to be fixed in HEAD"
    break
  fi

  sleep 10
done
```

**5. Verify Fix**

```bash
# After teammate messages FIXED or commits, verify locally
bash scripts/dx.sh all

# Expected: No H_STUB violations
if [ $? -eq 0 ]; then
  echo "Hook validation passed ✓"
  # Update team status
  jq '.[] |= if .id == "tm_impl" then .status = "compliant" else . end' \
    .team-state/τ-engine+schema+test-abc123/teammates.json
else
  echo "Hook validation still failing. Re-message tm_impl."
fi
```

**6. Decision Point**

```
Is fix verified?
  ├─ YES: Continue consolidation, commit changes
  ├─ PARTIAL: Additional violations detected, re-message
  └─ NO + deadline passed: Reassign task, use checkpoint
```

### Workflow F: Consolidation Failure (All Green Teammates)

**Time to Complete**: 15-30 minutes
**Probability**: 20% of team issues
**Outcome**: Fix incompatibility or rollback

**Trigger Condition**

```
Team status: All teammates reporting GREEN
  tm_schema: "Schema complete and tested ✓"
  tm_engine: "Engine integration done ✓"
  tm_test: "Tests passing ✓"

But lead's consolidation fails:
  $ bash scripts/dx.sh all
  [yawl-integration] BUILD FAILURE
  Error: Cannot find symbol: YWorkItem.getScheduledTime()
    at YawnlIntegrationService.java:234
```

**1. Analyze Build Error**

```bash
# Extract exact error from build log
bash scripts/dx.sh all 2>&1 | grep -A 5 "Cannot find symbol"
# Output:
# [ERROR] /home/user/yawl/.../YawnlIntegrationService.java:[234,45] cannot find symbol
# [ERROR]   symbol:   method getScheduledTime()
# [ERROR]   location: interface org.yawl.core.WorkflowObject

# The method is missing from the interface
```

**2. Determine Root Cause**

```bash
# Check if method is in schema
git show tm_schema/commit:schema/WorkflowObject.xsd | grep -i "scheduled"
# Output: (found)

# Check if method is in engine implementation
git show tm_engine/commit:src/main/java/.../YWorkItemImpl.java | grep -i "getScheduledTime"
# Output: public LocalDateTime getScheduledTime() { ... }

# Check if method was exported in interface
git show main:src/main/java/.../WorkflowObject.java | grep -i "getScheduledTime"
# Output: (not found - missing from interface!)
```

**Root Cause**: tm_engine implemented the method in the class but didn't add it to the interface. tm_test wrote tests that depend on the interface. The interface wasn't updated.

**3. Identify Responsible Teammate**

```json
{
  "incompatibility_id": "incomp-20260228-145100",
  "error": "Cannot find symbol: YWorkItem.getScheduledTime()",
  "root_cause": "Method implemented in YWorkItemImpl but not exported in WorkflowObject interface",
  "affected_file": "src/main/java/org/yawl/core/WorkflowObject.java",
  "implementation_file": "src/main/java/org/yawl/engine/YWorkItemImpl.java",
  "responsible_team": ["tm_engine", "tm_schema"],
  "fix_owner": "tm_schema (interface ownership)",
  "secondary_impact": "tm_test (tests depend on interface)"
}
```

**4. Message Responsible Teammate**

```json
{
  "sequence": 50,
  "timestamp": "2026-02-28T14:55:00Z",
  "from": "lead",
  "to": "tm_schema",
  "priority": "critical",
  "type": "consolidation_incompatibility",
  "payload": "Consolidation detected incompatibility: YWorkItemImpl has getScheduledTime() but WorkflowObject interface doesn't export it. This breaks YawnlIntegrationService. Fix: Add 'LocalDateTime getScheduledTime();' to WorkflowObject interface, then run local dx.sh. You have 15 minutes. After fix, message FIXED and I'll re-run consolidation.",
  "missing_api": {
    "method_name": "getScheduledTime",
    "return_type": "LocalDateTime",
    "interface": "WorkflowObject",
    "implementation_found_in": "YWorkItemImpl"
  },
  "deadline": "2026-02-28T15:10:00Z",
  "timeout": "15min"
}
```

**5. Apply Fix and Verify**

```bash
# After teammate messages FIXED, pull latest changes
git pull origin tm_schema

# Verify fix is present
git show HEAD:src/main/java/.../WorkflowObject.java | grep "getScheduledTime"
# Output: LocalDateTime getScheduledTime();

# Re-run consolidation
bash scripts/dx.sh all

# Expected: BUILD SUCCESS
if [ $? -eq 0 ]; then
  echo "Consolidation now GREEN ✓"
else
  echo "Still failing, check next error"
fi
```

**6. Decision Point**

```
Is consolidation now GREEN?
  ├─ YES: Proceed to commit, push, close team
  ├─ NO, new errors: Repeat Workflow F for each error
  └─ UNRESOLVABLE (>2 errors): Consider rollback instead
```

### Decision Tree: Which Workflow?

Use this tree to pick the right recovery procedure:

```
Team shows issue
  │
  ├─ No heartbeat from teammate?
  │  └─ Workflow A: Teammate Unresponsive
  │
  ├─ No ACK to 3+ messages (seq gap)?
  │  └─ Workflow C: Message Delivery Failure
  │
  ├─ Teammate completely unresponsive >30 min?
  │  └─ Workflow B: Teammate Crash
  │
  ├─ Circular task dependencies detected?
  │  └─ Workflow D: Circular Dependency (pre-formation)
  │
  ├─ Hook validation (dx.sh) fails on teammate's code?
  │  └─ Workflow E: Hook Violation Mid-Task
  │
  ├─ All teammates GREEN but dx.sh all fails?
  │  └─ Workflow F: Consolidation Failure
  │
  └─ Unknown/Complex issue?
     └─ Capture state, escalate, run post-mortem
```

---

## Part 3: Recovery Procedures

### Recovery Decision Tree

When a failure is detected, use this tree to decide your action:

```
Failure detected at [timestamp]
  │
  ├─ Blocks ALL teammates (global blocker)?
  │  ├─ YES: HALT team immediately
  │  │  └─ Checkpoint all work
  │  │  └─ Message all teammates: "Team halted, waiting for recovery"
  │  │  └─ Run diagnosis (Part 2 Workflows)
  │  │  └─ Decide: Retry with fix vs Rollback
  │  │
  │  └─ NO: Affects 1-2 teammates?
  │     ├─ Local to 1 teammate: Can continue?
  │     │  ├─ YES: Other teammates continue, affected teammate gets help
  │     │  └─ NO: Must halt
  │     │
  │     └─ Recoverable in <30 min?
  │        ├─ YES: Attempt one-pass recovery (Workflows A-F)
  │        └─ NO: HALT and escalate
```

### One-Pass Recovery Protocol

Execute this exactly once per issue. If it fails, escalate to post-mortem.

**Duration**: 30 minutes maximum
**Participants**: Lead + affected teammate(s)

**Timeline**

```
00:00 - Failure detected
00:01 - Run appropriate Workflow A-F diagnosis (step 1-2)
00:05 - Message teammate(s) with specific fix
00:05-00:25 - Teammate works on fix (20 min window)
00:25 - Verify fix (dx.sh run, tests pass)
00:26-00:30 - If successful: commit and continue
         If failed: Escalate to post-mortem
```

### Abort (Rollback) Procedure

Use this when one-pass recovery fails or issue is unresolvable.

**1. Decision to Abort**

```json
{
  "decision_id": "abort-20260228-145500",
  "timestamp": "2026-02-28T14:55:00Z",
  "reason": "Consolidation failure unresolvable (3+ incompatibilities, >60 min spent)",
  "completed_work": 60,
  "lost_work": 40,
  "recommendation": "Rollback to main, retry with different task decomposition"
}
```

**2. Checkpoint Current State**

```bash
# Create final checkpoint before rollback
git tag -a "abort-tm_engine+schema+test-abc123-202602281455" \
  -m "Team aborted after 60 min, consolidation unresolvable"

# Save team state for post-mortem
cp -r .team-state/τ-engine+schema+test-abc123 \
  .team-state/τ-engine+schema+test-abc123.ABORTED-202602281455
```

**3. Rollback to Main**

```bash
# Reset all branches to main
git checkout main
git reset --hard origin/main
git clean -fd

# Verify clean state
git status
# Output: nothing to commit, working tree clean
```

**4. Notify Teammates**

```json
{
  "sequence": 51,
  "timestamp": "2026-02-28T14:55:30Z",
  "from": "lead",
  "to": ["tm_schema", "tm_engine", "tm_test"],
  "type": "team_aborted",
  "payload": "Team has been aborted due to unresolvable consolidation incompatibilities. All work has been rolled back to main. Checkpoint saved as abort-τ-engine+schema+test-abc123-202602281455. Post-mortem analysis will be conducted. Thank you for your work; this will inform our next attempt.",
  "checkpoint_tag": "abort-τ-engine+schema+test-abc123-202602281455",
  "next_steps": "Lead will conduct post-mortem and plan retry with improved task decomposition"
}
```

### Retry with Fixes Procedure

Use this when one-pass recovery succeeds but requires re-running consolidation.

**1. Apply Fixes**

(This is done during Workflows A-F; here we just verify)

```bash
# Verify all teammate fixes are committed
git log --oneline -10 | grep -E "(tm_schema|tm_engine|tm_test)"
# Expected: Recent commits with "Fixed:" or "Resolved:" prefix
```

**2. Re-run Consolidation**

```bash
# Pull all latest changes
git fetch origin
git merge origin/tm_schema origin/tm_engine origin/tm_test

# Run full validation
bash scripts/dx.sh all

# Check exit code
if [ $? -eq 0 ]; then
  echo "Consolidation GREEN ✓"
else
  echo "Still failing, check error details"
fi
```

**3. If Re-run Succeeds**

```bash
# Commit consolidation changes
git commit -m "Team consolidation complete: schema+engine+test integrated successfully"

# Tag completion
git tag -a "team-complete-τ-engine+schema+test-abc123" \
  -m "Team successful after 1 recovery cycle"

# Message teammates
# (See Part 4, Team Closure)
```

**4. If Re-run Still Fails**

```
Return to Part 2 Workflows, diagnose new error
If >2 failed recovery attempts: ABORT instead of retry
```

---

## Part 4: Post-Mortems and Prevention

### Post-Mortem Workflow

Run this after every team failure (abort or successful recovery).

**Duration**: 30 minutes
**Timeline**: Within 24 hours of failure

**1. Gather Evidence (5 min)**

```bash
# Collect team state
mkdir -p reports/postmortem-20260228-τ-abc123

# State dump
cp .team-state/τ-engine+schema+test-abc123/mailbox.jsonl \
  reports/postmortem-20260228-τ-abc123/mailbox-full.jsonl

cp .team-state/τ-engine+schema+test-abc123/teammates.json \
  reports/postmortem-20260228-τ-abc123/teammates-final-state.json

# Build logs
bash scripts/dx.sh all > reports/postmortem-20260228-τ-abc123/dx-build-log.txt 2>&1

# Git log of changes
git log main..HEAD --format=full > \
  reports/postmortem-20260228-τ-abc123/git-log-changes.txt
```

**2. Timeline Reconstruction (10 min)**

```json
{
  "postmortem_id": "pm-20260228-τ-engine+schema+test-abc123",
  "team_id": "τ-engine+schema+test-abc123",
  "date": "2026-02-28",
  "duration_minutes": 90,
  "status": "aborted",
  "timeline": [
    {
      "time": "14:00:00Z",
      "event": "Team formed",
      "actor": "lead",
      "details": "3 teammates assigned: tm_engine, tm_schema, tm_test"
    },
    {
      "time": "14:25:10Z",
      "event": "Task acknowledged",
      "actor": "tm_engine",
      "details": "Began YNetRunner deadlock investigation"
    },
    {
      "time": "14:45:30Z",
      "event": "Teammate idle",
      "actor": "system",
      "details": "No heartbeat from tm_engine for 20 min"
    },
    {
      "time": "14:48:00Z",
      "event": "Workflow A initiated",
      "actor": "lead",
      "details": "Sent status check message to tm_engine"
    },
    {
      "time": "14:50:00Z",
      "event": "Hook violation detected",
      "actor": "system",
      "details": "H_STUB in tm_impl's commit (YWorkItemImpl.java:426)"
    },
    {
      "time": "15:00:00Z",
      "event": "Hook fix applied",
      "actor": "tm_impl",
      "details": "Replaced 'return null' with 'throw new UnsupportedOperationException'"
    },
    {
      "time": "15:15:00Z",
      "event": "Consolidation attempted",
      "actor": "lead",
      "details": "dx.sh all executed"
    },
    {
      "time": "15:15:30Z",
      "event": "Consolidation failure",
      "actor": "system",
      "details": "Cannot find symbol: YWorkItem.getScheduledTime() in YawnlIntegrationService"
    },
    {
      "time": "15:20:00Z",
      "event": "Incompatibility diagnosed",
      "actor": "lead",
      "details": "Method in YWorkItemImpl but not in WorkflowObject interface"
    },
    {
      "time": "15:30:00Z",
      "event": "Interface fixed",
      "actor": "tm_schema",
      "details": "Added getScheduledTime() to WorkflowObject interface"
    },
    {
      "time": "15:32:00Z",
      "event": "Consolidation re-run",
      "actor": "lead",
      "details": "dx.sh all executed again"
    },
    {
      "time": "15:32:30Z",
      "event": "Third incompatibility",
      "actor": "system",
      "details": "Missing import in YawnlIntegrationService"
    },
    {
      "time": "15:55:00Z",
      "event": "Team aborted",
      "actor": "lead",
      "details": "3 incompatibilities in 30 min, recovery uneconomical"
    }
  ]
}
```

**3. Root Cause Analysis**

Use the 5 Whys technique:

```
Issue: Team aborted after 90 minutes, consolidation failed 3 times

Why #1: Why did consolidation fail?
  Answer: YWorkItem.getScheduledTime() was missing from interface

Why #2: Why wasn't it in the interface?
  Answer: tm_engine implemented it in the class but didn't update interface

Why #3: Why didn't tm_engine update the interface?
  Answer: Task assignment wasn't clear; tm_engine assumed tm_schema would update interface

Why #4: Why wasn't this clarified in task description?
  Answer: No interface contract was documented before teammates started

Why #5: Why wasn't there an interface review checkpoint?
  Answer: Team formation didn't include schema review gate before implementation

ROOT CAUSE: Lack of early interface contract validation between tm_schema and tm_engine
```

**4. Record Findings**

```json
{
  "root_causes": [
    {
      "category": "Process",
      "severity": "HIGH",
      "finding": "No interface contract review between schema and implementation teammates",
      "evidence": "tm_schema and tm_engine worked in parallel without agreeing on WorkflowObject interface",
      "impact": "Led to 3 consolidation failures, 55 min wasted"
    },
    {
      "category": "Communication",
      "severity": "HIGH",
      "finding": "Task dependencies not explicitly stated",
      "evidence": "Task assignment said 'implement SLA' but didn't specify 'interface must match schema'",
      "impact": "Teammates made different assumptions about ownership"
    },
    {
      "category": "Process",
      "severity": "MEDIUM",
      "finding": "No mid-phase checkpoint for interface validation",
      "evidence": "Only validation happened at consolidation (too late)",
      "impact": "Errors caught 60 min into team execution"
    }
  ],
  "contributing_factors": [
    "3 teammates (larger team, harder to coordinate)",
    "Cross-layer work (schema + engine + test interdependencies)",
    "No shared API contract document"
  ]
}
```

**5. Prevention Recommendations**

```json
{
  "recommendations": [
    {
      "title": "Add Interface Contract Gate",
      "category": "Process",
      "timing": "Before teammate assignment",
      "implementation": "Create shared interface (WorkflowObject.java) before delegating schema/engine work. Both teammates review and sign off.",
      "effort": "10 minutes per team",
      "estimated_prevention": "Prevents 80% of similar consolidation failures"
    },
    {
      "title": "Add Mid-Phase Validation Checkpoint",
      "category": "Process",
      "timing": "At 50% completion",
      "implementation": "Lead schedules 15-min checkpoint: teammates review each other's API assumptions, fix mismatches early",
      "effort": "15 minutes per team",
      "estimated_prevention": "Prevents 90% of interface mismatches"
    },
    {
      "title": "Explicit Task Dependencies",
      "category": "Communication",
      "timing": "Task assignment message",
      "implementation": "Message includes: 'Task#1 output becomes input to Task#2. Coordinate interface definition with tm_schema.'",
      "effort": "5 minutes per team",
      "estimated_prevention": "Prevents 60% of assumption mismatches"
    },
    {
      "title": "Schema + Implementation Sequential (For SLA Feature)",
      "category": "Process",
      "timing": "Team formation",
      "implementation": "Have tm_schema define interface first (independent task), then tm_engine implements (dependent task). Use contract-first model.",
      "effort": "5 minutes planning",
      "estimated_prevention": "Prevents 100% of this specific failure"
    }
  ]
}
```

### Lessons Capture

After every post-mortem, update your project's lessons file:

```bash
# Append to tasks/lessons.md
cat >> tasks/lessons.md << 'EOF'

## Lesson: Interface Contracts Prevent Consolidation Failures

**Date**: 2026-02-28
**Team**: τ-engine+schema+test-abc123
**Failure Type**: Consolidation incompatibility (3× failures, 90 min wasted)

### What Happened
tm_engine and tm_schema worked in parallel without agreeing on WorkflowObject interface.
Result: Method getScheduledTime() implemented but not exported, breaking integration.

### What We Learned
1. Cross-layer changes (schema + engine) MUST have shared interface contract BEFORE teammates start
2. Interface review checkpoint at 50% completion catches mismatches early
3. Sequential task ordering (schema first, then engine) prevents this entirely

### What We'll Do Different Next Time
- For schema+engine teams: Add "Interface Contract Review" as Task#0 (before Task#1, Task#2)
- Require teammates to sign off on interface definition before starting parallel work
- Add checkpoint at 50% completion: "Does their interface match yours?"
- Consider sequential ordering for tightly-coupled work instead of parallel

### Prevention Pattern for Similar Teams
This pattern applies to: schema+engine, schema+test, engine+integration combinations.
Pattern: (1) Define interface/contract, (2) Parallel implementation, (3) Consolidation validation.

EOF
```

---

## Part 5: Prevention Strategies for Future Teams

### Pre-Formation Checklist

Before creating a team, use this checklist to prevent common failures:

```
□ Run: bash .claude/hooks/team-recommendation.sh "task description"
  └─ Confirms team is appropriate for the task

□ Verify team size: 2-5 teammates
  └─ >5 = too much coordination overhead

□ Check task dependencies: Can they be parallelized?
  └─ If A→B→A cycle: Reorder tasks before formation

□ Inspect Ψ facts:
  $ bash scripts/observatory/observatory.sh
  └─ Review .facts/shared-src.json for file conflicts

□ Plan interface contracts:
  └─ For schema+engine teams: Define interface (WorkflowObject.java) first

□ Estimate per-task time:
  └─ Each task should be ≥30 min
  └─ If <30 min total: Use single session instead

□ Document assumption handoff:
  └─ Create mailbox message with API contracts before teammates start

□ Schedule checkpoint:
  └─ At 50% completion, teammates review each other's interface assumptions
```

### Task Decomposition Best Practices

**For Schema + Engine Teams**

```
BAD (leads to consolidation failures):
  Task#1 (tm_schema): Define and validate SLA schema
  Task#2 (tm_engine): Implement SLA tracking in YNetRunner
  Problem: No agreed interface, likely mismatch

GOOD (prevents failures):
  Task#0 (lead): Create interface contract document
    └─ Define WorkflowObject.getScheduledTime() signature
    └─ Both teammates review and sign off

  Task#1 (tm_schema): Define SLA schema (assuming interface above)
  Task#2 (tm_engine): Implement SLA tracking (assuming interface above)

  Consolidation Checkpoint: Validate both match interface
  Problem: Caught early if mismatch
```

**For Cross-Layer Teams (Engine + Integration + Test)**

```
SEQUENCING:
  Phase 1: Engine defines API
  Phase 2: Integration + Test implement against API (parallel)
  Phase 3: Consolidation validates API matches all consumers

COMMUNICATION:
  Lead publishes API contract in message #0 (before any work)
  tm_engine: "Here's what I guarantee"
  tm_integration: "Here's what I need"
  tm_test: "Here's how I'll verify it"
  → Agree in 10 minutes before starting
```

### Monitoring Strategy

Monitor your teams to catch failures early:

```bash
# Real-time monitoring script (run in background)
while true; do
  # Check heartbeats
  jq '.[] | select(.heartbeat_age_seconds > 600) | .id' \
    .team-state/$TEAM_ID/teammates.json | while read tm; do
    echo "[ALERT] $tm idle for >10 min at $(date)"
  done

  # Check message gaps
  last_ack=$(jq -s 'map(select(.type == "ack")) | .[-1].timestamp' \
    .team-state/$TEAM_ID/mailbox.jsonl)
  age_sec=$(( $(date +%s) - $(date -d "$last_ack" +%s) ))
  if [ $age_sec -gt 60 ]; then
    echo "[ALERT] No ACKs for >60 sec at $(date)"
  fi

  sleep 30
done
```

### Decision Tree: Single Session vs Team vs Subagents

Use this to avoid forming teams unnecessarily:

```
Do you have N ∈ {2,3,4,5} orthogonal work items?
  ├─ NO: Use SINGLE SESSION (cheapest, fastest)
  │
  └─ YES: Do teammates need to message/iterate?
     ├─ NO: Use SUBAGENTS μ (parallel, report-only)
     │   └─ Cheaper than team, sufficient for independent work
     │
     └─ YES: Do they have tight coupling/dependencies?
        ├─ NO: Use TEAM τ with parallel tasks
        │   └─ Best for loosely-coupled work (review, independent features)
        │
        └─ YES: Can you decompose to reduce coupling?
           ├─ YES: Decompose first, then use TEAM
           │   └─ Move common interface definition to Task#0
           │
           └─ NO: Consider SEQUENTIAL (2-3 phases, cheaper than failed team)
              └─ Prevents consolidation failures from circular dependencies
```

### Real Scenario: Avoid This Formation

```
❌ BAD TEAM FORMATION:
   Team size: 4 teammates
   Tasks: (1) Fix bug A, (2) Fix bug B, (3) Fix bug C, (4) Run tests
   Duration per task: 25 min, 30 min, 20 min, 15 min
   Communication: Little to none (mostly independent fixes)

   Problem: 4 teammates, minimal communication, expensive setup
   Cost: $4C (4× cost)
   Benefit: Parallel bug fixing
   ROI: Break-even; single session might be faster

✓ BETTER APPROACH:
   Use single session: 90 min total for 4 sequential fixes
   Cost: $C
   Trade-off: Linear timeline but cheaper overall

❌ BAD TEAM FORMATION:
   Team size: 3 teammates
   Tasks: (1) Define interface, (2) Implement using interface, (3) Test interface
   Duration per task: 40 min, 50 min, 30 min
   Dependencies: (2) depends on (1), (3) depends on (1) and (2)

   Problem: Task (2) and (3) can't start until (1) is done
   Cost: $3C (parallel setup cost)
   Benefit: Linear execution (same as sequential!)
   ROI: Negative; pure waste

✓ BETTER APPROACH:
   Task#0: Define interface (lead or tm_schema, 40 min)
   Task#1: Implement (tm_engine, 50 min, parallel with testing prep)
   Task#2: Test (tm_test, 30 min, can start when Task#0 done)
   → Still 3-phase but cheaper with 2 teammates starting in phase 2

✓ BEST APPROACH:
   Use single session with clear 3-phase plan
   Phase 1: Define interface
   Phase 2: Implement + write tests (parallel locally)
   Phase 3: Integrate + validate
   Cost: $C
   Time: 120 min (same as team, but cheaper)
```

---

## Part 6: State Examples and Reference

### Complete Team State Dump (JSON)

Reference this to understand team structure during debugging:

```json
{
  "team_id": "τ-engine+schema+test-iDs6b",
  "team_status": "active",
  "created_at": "2026-02-28T14:00:00Z",
  "lead_session_id": "claude_session_6f3d2e1a",
  "teammates": [
    {
      "id": "tm_engine",
      "name": "Engineer A (Concurrency Specialist)",
      "task_id": "Task#1-engine-investigate-deadlock",
      "status": "idle",
      "context_tokens_used": 128000,
      "context_tokens_max": 200000,
      "last_heartbeat": "2026-02-28T14:45:30Z",
      "heartbeat_age_seconds": 1247,
      "task_started_at": "2026-02-28T14:25:10Z",
      "task_age_minutes": 20,
      "completion_percent": 45,
      "messages_pending": 3,
      "last_message_seq": 45
    },
    {
      "id": "tm_schema",
      "name": "Engineer B (Schema Expert)",
      "task_id": "Task#2-schema-define-sla",
      "status": "active",
      "context_tokens_used": 89000,
      "context_tokens_max": 200000,
      "last_heartbeat": "2026-02-28T14:48:15Z",
      "heartbeat_age_seconds": 75,
      "task_started_at": "2026-02-28T14:00:30Z",
      "task_age_minutes": 48,
      "completion_percent": 80,
      "messages_pending": 0,
      "last_message_seq": 46
    },
    {
      "id": "tm_test",
      "name": "Engineer C (Test Architect)",
      "task_id": "Task#3-test-integration",
      "status": "active",
      "context_tokens_used": 76000,
      "context_tokens_max": 200000,
      "last_heartbeat": "2026-02-28T14:49:00Z",
      "heartbeat_age_seconds": 30,
      "task_started_at": "2026-02-28T14:30:00Z",
      "task_age_minutes": 19,
      "completion_percent": 30,
      "messages_pending": 1,
      "last_message_seq": 47
    }
  ],
  "tasks": [
    {
      "task_id": "Task#1-engine-investigate-deadlock",
      "name": "Investigate YNetRunner deadlock in synchronized blocks",
      "assigned_to": "tm_engine",
      "status": "in_progress",
      "depends_on": [],
      "created_at": "2026-02-28T14:00:00Z",
      "started_at": "2026-02-28T14:25:10Z",
      "expected_duration_minutes": 60,
      "completion_percent": 45,
      "blocking_factor": null
    },
    {
      "task_id": "Task#2-schema-define-sla",
      "name": "Define SLA attribute in schema and interface",
      "assigned_to": "tm_schema",
      "status": "in_progress",
      "depends_on": [],
      "created_at": "2026-02-28T14:00:00Z",
      "started_at": "2026-02-28T14:00:30Z",
      "expected_duration_minutes": 60,
      "completion_percent": 80,
      "blocking_factor": null
    },
    {
      "task_id": "Task#3-test-integration",
      "name": "Write integration tests for SLA + deadlock fix",
      "assigned_to": "tm_test",
      "status": "in_progress",
      "depends_on": ["Task#1-engine-investigate-deadlock", "Task#2-schema-define-sla"],
      "created_at": "2026-02-28T14:00:00Z",
      "started_at": "2026-02-28T14:30:00Z",
      "expected_duration_minutes": 45,
      "completion_percent": 30,
      "blocking_factor": "Waiting for Task#1 and Task#2 to stabilize"
    }
  ],
  "mailbox_stats": {
    "total_messages": 47,
    "sent_by_lead": 28,
    "sent_by_teammates": 19,
    "ack_count": 18,
    "unacknowledged": 1,
    "last_message_timestamp": "2026-02-28T14:49:30Z"
  },
  "checkpoints": [
    {
      "checkpoint_id": "ckpt-tm_engine-202602281425",
      "teammate": "tm_engine",
      "timestamp": "2026-02-28T14:25:30Z",
      "completion_percent": 45,
      "files_modified": 2,
      "git_staged": 0
    },
    {
      "checkpoint_id": "ckpt-tm_schema-202602281430",
      "teammate": "tm_schema",
      "timestamp": "2026-02-28T14:30:15Z",
      "completion_percent": 60,
      "files_modified": 1,
      "git_staged": 1
    }
  ]
}
```

### Checkpoint State Example

Example of what's saved when a teammate is interrupted:

```json
{
  "checkpoint_id": "ckpt-tm_engine-202602281425",
  "teammate_id": "tm_engine",
  "timestamp": "2026-02-28T14:25:30Z",
  "task_id": "Task#1-engine-investigate-deadlock",
  "task_name": "Investigate YNetRunner deadlock in synchronized blocks",
  "completion_percent": 45,
  "status": "in_progress",
  "last_action": "git clone yawl && profiled YNetRunner.main loop",
  "current_file": "src/main/java/org/yawl/engine/YNetRunner.java",
  "current_line": 427,
  "analysis_context": "Found 3 potential deadlock scenarios in synchronized blocks. Narrowing down to DataQueue.offer() based on profiler heat map. Next: Implement fix using fair ReadWriteLock instead of synchronized.",
  "blocking_factor": null,
  "estimated_remaining_minutes": 15,
  "files_modified": [
    "src/main/java/org/yawl/engine/YNetRunner.java",
    "src/test/java/org/yawl/engine/YNetRunnerTest.java"
  ],
  "git_staged_files": [],
  "uncommitted_changes": "Modified YNetRunner.java with profiling notes (not yet committed)",
  "notes": "Profiler shows 87% lock contention in synchronized(workQueue). ReadWriteLock should reduce contention to <20%. Testing strategy: Unit test with Thread.sleep() delays + stress test with 100 threads.",
  "dependencies": {
    "blocked_on": null,
    "waiting_for_teammate": null
  },
  "context_at_checkpoint": {
    "tokens_used": 128000,
    "tokens_max": 200000,
    "percent_used": 64
  }
}
```

---

## Conclusion

You now have the tools to debug, recover from, and prevent team failures:

- **Part 1**: Recognize failure signatures (8 types)
- **Part 2**: Diagnose root causes with 6 workflows (A-F)
- **Part 3**: Execute recovery procedures (retry, abort, rollback)
- **Part 4**: Conduct post-mortems and capture lessons
- **Part 5**: Prevent recurrence with checklists and patterns
- **Part 6**: Reference real JSON state examples

### Quick Links for Rapid Diagnosis

- **Team is stuck?** → Check [Workflow A: Teammate Unresponsive](#workflow-a-teammate-unresponsive-no-heartbeat)
- **Build failing?** → Check [Workflow E: Hook Violation](#workflow-e-hook-violation-mid-task) or [Workflow F: Consolidation Failure](#workflow-f-consolidation-failure-all-green-teammates)
- **Not sure what's wrong?** → Use [Decision Tree: Which Workflow?](#decision-tree-which-workflow)
- **Want to prevent this?** → Read [Part 5: Prevention Strategies](#part-5-prevention-strategies-for-future-teams)

### Reference

- Full theory: [.claude/rules/TEAMS-GUIDE.md](./../../../.claude/rules/TEAMS-GUIDE.md)
- Quick reference: [.claude/rules/TEAMS-QUICK-REF.md](./../../../.claude/rules/TEAMS-QUICK-REF.md)
- Decision framework: [.claude/rules/team-decision-framework.md](./../../../.claude/rules/team-decision-framework.md)

---

**Version:** 6.0.0 | **Last Updated:** 2026-02-28 | **Production Status:** Ready
