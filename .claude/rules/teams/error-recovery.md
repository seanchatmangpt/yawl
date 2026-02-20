# Team Error Recovery & Timeout Handling — Phase 3 Enforcement

**Problem**: YAWL team system (τ) lacks documented error recovery paths. When timeouts, failures, or circular dependencies occur during execution, behavior is undefined.

**Goal**: Provide comprehensive error recovery guide covering timeout scenarios, dependency deadlocks, async failures, message delivery, and invariant violations. Enable lead to make deterministic decisions when teams encounter problems.

**Scope**: Error handling during team execution (Ψ→Λ→H→Q→Ω circuit), not team formation.

---

## 1. Timeout Handling

### 1.1 Teammate Idle Timeout (30 min no activity)

**Trigger**: Teammate hasn't reported status or progress for 30 minutes.

**Detection**:
```json
{
  "teammate_id": "tm_1",
  "last_heartbeat": "2026-02-20T14:25:12Z",
  "current_time": "2026-02-20T14:55:45Z",
  "idle_duration_min": 30.5
}
```

**Lead Action** (Decision Tree):

```
Is teammate blocked on another teammate's work?
├─ YES → Message blocking teammate: "Engineer C waiting for your Schema fix. ETA?"
│        └─ If no response → Escalate (see 1.3)
│
├─ NO, teammate working independently
│  ├─ LOCAL DX FAILURE? (last compile was RED)
│  │  └─ Action: Message: "Local dx.sh failed. Share error log. I'll help debug."
│  │          Resume teammate on new task (if independent)
│  │          Or provide guidance on dependency issue
│  │
│  ├─ MESSAGE QUEUE FULL? (>10 pending messages from lead)
│  │  └─ Action: Check message buffer. Oldest message may be blocked waiting for reply.
│  │          Resend critical message + timeout.
│  │          If no response in 5 min → teammate context unstable, skip retry
│  │
│  └─ UNKNOWN (no signals, complete silence)
│     └─ Action: Message: "Status check: are you still active?"
│            Wait 5 min for response
│            If no response: Assume crash
│            └─ Recovery path: TEAMMATE_CRASH (see 3.1)
```

**Expected Outcome**:
- If responsive: Resume work with guidance
- If unresponsive: Mark as crashed, delegate task to new teammate
- If message queue stuck: Reduce message volume or split team

**Rollback Cost**: ~30% of original task time (teammate re-onboards new work).

---

### 1.2 Task Timeout (2+ hours, no completion)

**Trigger**: Single team task exceeds 2 hours without completion.

**Detection**:
```json
{
  "task_id": "task_engine_001",
  "description": "Fix YNetRunner deadlock",
  "assigned_to": "tm_2",
  "started_at": "2026-02-20T12:00:00Z",
  "current_time": "2026-02-20T14:15:00Z",
  "duration_min": 135,
  "status": "in_progress"
}
```

**Lead Action** (Decision Tree):

```
Has teammate messaged progress?
├─ YES, still investigating → Task is complex but on track
│  └─ Action: Message: "Great progress. Still on target for completion?"
│          Allow additional time (max +1 hour)
│          Consider splitting task if approaching limit
│
├─ NO progress message in >45 min
│  └─ Is teammate blocked waiting for teammate?
│     ├─ YES → Escalate blocking teammate (see 1.2, recursive)
│     │
│     ├─ NO → Task complexity underestimated
│     │  └─ Action: Message: "Task may be too large. Can we break it into 2 subtasks?"
│     │          If feasible: Spawn second teammate to help
│     │          If infeasible: Extend deadline, but set hard 3-hour limit
│     │
│     └─ UNKNOWN (no communication at all)
│        └─ Action: Declare task stalled
│               Investigate teammate state (health check message)
│               If unresponsive → TEAMMATE_CRASH (see 3.1)
```

**Expected Outcome**:
- If complex but progressing: Allow +1 hour with checkpoint messages
- If blocked: Resolve blocker (escalate)
- If too large: Split into parallel subtasks
- If crashed: Reassign task to new teammate

**Hard Limit**: 3 hours per task (total across all re-attempts). If exceeded, escalate to lead for decision: reduce scope, extend deadline (at cost to team schedule), or declare task infeasible.

---

### 1.3 Message Timeout (no reply > 15 min)

**Trigger**: Lead sends message to teammate; no reply within 15 minutes.

**Detection**:
```json
{
  "message_id": "msg_42",
  "from": "lead",
  "to": "tm_3",
  "sent_at": "2026-02-20T14:30:00Z",
  "current_time": "2026-02-20T14:45:30Z",
  "timeout_min": 15.5,
  "status": "no_reply"
}
```

**Lead Action** (Decision Tree):

```
Is this a critical message (blocking) or informational?
├─ CRITICAL (blocking next task): Message timeout is escalation trigger
│  └─ Action: Resend message with [URGENT] prefix
│          Add: "Reply needed within 5 min or I'll assume crash."
│          Wait 5 minutes
│          If no reply → TEAMMATE_CRASH (see 3.1)
│
├─ INFORMATIONAL (non-blocking):
│  └─ Action: Assume high teammate load (context near full?)
│          Check teammate context_window_usage
│          If >70%: Don't send new messages, reduce message load
│          Message can wait until teammate checkpoints (clears context)
│          Re-send after checkpoint or +30 min (whichever sooner)
│
└─ QUESTION (asking teammate opinion):
   └─ Action: Don't block on reply
          Mark as "awaiting opinion" but don't halt work
          Provide deadline: "Reply by [time] or I'll proceed with default option X"
          If reply arrives: Update decision, message affected teammates
          If no reply: Proceed with default, note in mailbox for post-mortem
```

**Expected Outcome**:
- Critical message: Force teammate check-in
- Informational: Batch and resend later
- Questions: Provide default option, don't block

---

## 2. Circular Dependencies & Deadlock Prevention

### 2.1 Circular Dependency Detection

**Definition**: Task A waits for Task B, Task B waits for Task A (or longer chain: A → B → C → A).

**Example Scenario** (Actual Risk):
```
Task: "Fix schema + engine"

Engine Task:
  - YWorkItem.setState() calls validateState()
  - validateState() depends on schema definition
  - Waiting for: Schema team to define valid state transitions
  → BLOCKED

Schema Task:
  - Add state transition constraints to XSD
  - Need to know: What state transitions does engine support?
  - Waiting for: Engine team to document state machine
  → BLOCKED

Circular dependency: Schema ←→ Engine
```

**Detection** (Automatic):

```bash
# In task_list.json:
"dependencies": {
  "task_engine_001": ["task_schema_001"],  # Engine depends on Schema
  "task_schema_001": ["task_engine_001"]   # Schema depends on Engine
}

# Algorithm: DFS cycle detection on task dependency graph
# If cycle found: Flag as CIRCULAR_DEPENDENCY
```

**Prevention Protocol** (Execute BEFORE team spawns):

1. **Pre-Team Validation** (Lead responsibility):
   - Build task dependency DAG
   - Run cycle detection
   - If cycle found: Halt team formation, resolve dependency manually

2. **Resolution Options** (Lead chooses one):

   **Option A: Break Tie via Task Ordering**
   ```
   Assumption: One teammate can make initial decision without waiting.

   Decision: Schema team defines "canonical" state transitions first
             (educated guess: ENABLED → EXECUTING → FIRED)

   Execution order:
   1. Schema team: Define <state> element with 3 values (no validation yet)
   2. Schema team: Message engine team: "I've defined ENABLED, EXECUTING, FIRED"
   3. Engine team: Implement state machine using schema definition
   4. Engine team: Message schema team: "I need one more validation—can you forbid ENABLED→FIRED direct jump?"
   5. Schema team: Add <xs:assertion> forbidding direct jump
   6. Both complete
   ```

   **Cost**: Extra message round (step 4-5), but breaks deadlock.

   **Option B: Extract & Parallelize Independent Work**
   ```
   Decompose circular dependency into independent subtasks.

   Example:
   - Engine Task: Implement state machine with predefined states [ENABLED, EXECUTING, FIRED]
   - Schema Task: Define those states in XSD
   - Engine Task: Add state validation hooks (point to schema)
   - Schema Task: Add constraints (references engine's validation logic)

   Key insight: Both teams can assume interface contracts (state names, method signatures)
                 and implement independently, then verify contracts match.

   Execution: Run both teams in parallel (no wait), validate contracts match in consolidation.
   ```

   **Cost**: Requires explicit interface definition upfront (minimal overhead).

   **Option C: Sequential Phases** (Last Resort)
   ```
   If no way to break dependency:

   Phase 1: Schema team defines schema (solo)
   Phase 2: Engine team implements (depends on Phase 1 output)

   Cost: 2× execution time, loses parallelism benefit of team.
   ```

**During Execution** (If circular dependency discovered mid-execution):

```
Lead receives message from Engineer A:
  "I'm blocked waiting for Schema to define state transitions.
   Can't proceed without knowing valid states."

Lead's action:
  1. Check task_list for circular dependency
  2. Message Schema team: "We need initial state definition to unblock Engine.
     Provide educated guess: ENABLED, EXECUTING, FIRED?"
  3. Schema team responds with definition (no need to wait for engine validation)
  4. Message Engineer: "Here's the state set. Start implementing."
  5. Both proceed independently
  6. Resolve validation conflicts in consolidation (if any)
```

**Enforcement**: If circular dependency found during team execution and lead fails to break tie within 5 minutes, mark team as DEADLOCKED and roll back (section 3.2).

---

### 2.2 Circular Dependency Example: YNetRunner State Machine Fix

**Real Scenario** (from TEAM-VALIDATION-SCENARIOS.md, Scenario 7):

```
Initial task: "Fix task lifecycle state machine" (single quantum, N=1)
Hook recommendation: Single session (not team)

Execution:
  Engineer discovers: YWorkItem.setState() validates against schema
                      but schema allows invalid transition (ENABLED → FIRED)

Realization: N=2 quantum (engine + schema), needs team!

Circular dependency appears:
  - Engine: "I need schema to forbid ENABLED→FIRED"
  - Schema: "I need engine to tell me what's valid"

Lead breaks tie:
  - Decision: Schema defines constraint directly (via XSD assertion)
  - Engine: Implements state machine validation against schema
  - No wait: Both proceed with their definitions
  - Result: Schema wins this round (is source of truth)
```

**Recovery**: Add to STOP conditions (section 6.1).

---

## 3. Async Failures

### 3.1 Teammate Crash or Unresponsive (Teammate Context Lost)

**Trigger**: Teammate becomes unresponsive (timeouts at 1.1, 1.3 escalated).

**Detection**:
- Teammate hasn't responded to health-check message in 5 minutes
- Teammate's context_window_usage maxed out (100%)
- Teammate session terminated unexpectedly (framework error)

**Lead Action**:

```
1. Assess work done so far
   └─ Query task_list: What files did tm_1 modify?
      git diff <branch>:<previous_commit> -- <files>

2. Save teammate's work
   └─ git add <tm_1_files>
      Create checkpoint commit: "Checkpoint: Teammate tm_1 progress before crash"
      DO NOT PUSH (keep local)

3. Decide: Continue with new teammate, or reassign
   ├─ If work is 80%+ complete
   │  └─ Action: Continue with new teammate (low rework)
   │          New teammate reviews checkpoint, continues from there
   │
   └─ If work is <80% complete
      ├─ If task is in critical path (blocks others)
      │  └─ Action: Spawn new teammate, briefing on checkpoint
      │
      └─ If task is independent
         └─ Action: Decide: New teammate to continue, or
                    reassign to lead to finish alone (faster if nearly done)
```

**New Teammate Onboarding**:

```
Lead message to new teammate:
  "Engineer A crashed mid-task. Here's what they did:

   Task: Fix YNetRunner deadlock
   Progress: 80% (state machine analysis complete, working on sync fix)

   Checkpoint: branch feature/ynetrunner-fix, commit abc1234

   Files modified:
   - yawl/engine/YNetRunner.java (60 lines changed, analysis in comments)
   - yawl/engine/test/YNetRunnerTest.java (new test cases for deadlock)

   TODO:
   - Implement synchronized block in advance() method
   - Run full test suite (100+ tests in YNetRunnerTest)
   - Message Integrator about event publishing changes (pending)

   Can you take it from here? I'm assigning you this task now."
```

**Expected Outcome**:
- Teammate crash is detected and recovered within 10 minutes
- New teammate brought in, work resumes
- No loss of work (checkpoint saved)
- Total delay: ~15-20 minutes (new teammate ramp-up)

---

### 3.2 Lead's Full DX Fails (After all teammates GREEN)

**Trigger**: All teammates report their local dx.sh GREEN, but lead's `bash scripts/dx.sh all` fails.

**Scenario**:
```
Teammates locally:
  - Engineer A: dx.sh -pl yawl-engine GREEN
  - Engineer B: dx.sh -pl yawl-schema GREEN
  - Integrator: dx.sh -pl yawl-integration GREEN

Lead runs: bash scripts/dx.sh all
Output: RED — compilation failure in yawl-integration

Error: "Cannot find symbol: YWorkItem.newHealthStatusEvent()"
       (Engine teammate added method, schema teammate added element,
        but neither told Integrator about the new method signature)
```

**Lead Action** (Decision Tree):

```
1. Identify failing module
   └─ Error message says: yawl-integration
      Lead runs: mvn -pl yawl-integration compile (verbose)
      Output: Shows missing symbol introduced by schema/engine changes

2. Diagnose root cause
   ├─ Missing interface contract (schema/engine didn't inform integration)
   │  └─ Action: Message schema & engine teammates:
   │          "Integration can't compile. Did you add new methods/fields?"
   │          "If yes, send API contract to Integrator (method signatures, element types)"
   │
   ├─ Incompatible signatures (engine changed method, integrator using old signature)
   │  └─ Action: Decide ownership:
   │          If engine is source of truth: Integrator updates to match engine
   │          If integration API is stable: Engine reverts breaking change
   │
   └─ Transitive dependency mismatch (A→B→C, C changed, now B broken)
      └─ Action: Check module dependency order
             Rebuild affected modules in correct order
             Usually resolves by running full build with correct parallelism

3. Assign fix responsibility
   └─ Lead messages failing teammate:
      "Your module doesn't compile due to incompatibility with Schema/Engine changes.

       Option 1 (Recommended): Update your code to use new API from schema/engine
       Option 2: Request schema/engine to revert their changes

       Local test: cd yawl-integration && bash scripts/dx.sh -pl . (should GREEN)

       Timeline: Fix + retest within 20 minutes?"

4. Re-run local DX
   └─ Teammate applies fix, runs local dx.sh -pl <module> → GREEN
      Teammate messages: "Fixed, local dx.sh GREEN. Re-run full dx.sh."

5. Lead re-runs full build
   └─ bash scripts/dx.sh all → GREEN (or repeat above if new failures)

6. If >2 iterations of failures
   └─ Declare team consolidation FAILED
      Rollback: git reset --hard <last_known_good>
      Analyze root cause (section 4 Post-Mortem)
```

**Expected Outcome**:
- Single incompatibility: Fixed in 1 iteration (~15 min), team continues
- Multiple incompatibilities: 2-3 iterations, team continues
- Structural failure: Rollback + post-mortem required

**Preventive Measure**: In messaging phase (step 5 of team execution), require teammates to share API contracts:

```
Engineer A message to Integrator:
  "I added YWorkItem.newHealthStatusEvent(status, timestamp).
   Returns HealthStatusEvent object with getStatus(), getTimestamp() methods.
   Your InterfaceB can listen to YWorkItem.addEventListener(HealthStatusListener)."

Integrator message to Engineer A:
  "Received API contract. I'm implementing listener. Will test locally."
```

---

### 3.3 Git Push Fails (Branch Conflict or Permission Error)

**Trigger**: Lead attempts `git push -u origin claude/<branch>-<sessionId>` after consolidation, push fails.

**Scenarios**:

**Scenario A: Branch Conflict** (someone else pushed to same branch)

```bash
git push -u origin claude/fix-deadlock-iDs6b
# Error: [rejected] ... (fetch first)

Lead action:
  1. Fetch remote: git fetch origin
  2. Check branch status: git log origin/claude/fix-deadlock-iDs6b
  3. Decide:
     - If remote changes are unrelated: rebase
       git rebase origin/claude/fix-deadlock-iDs6b
       git push -u origin claude/fix-deadlock-iDs6b

     - If remote changes conflict with ours:
       git merge origin/claude/fix-deadlock-iDs6b
       Resolve conflicts manually
       git push -u origin claude/fix-deadlock-iDs6b

     - If remote is different work:
       Use different branch name: claude/fix-deadlock-v2-<sessionId>
```

**Scenario B: Permission Error** (no push rights to origin)

```bash
git push -u origin claude/fix-deadlock-iDs6b
# Error: fatal: 'origin' does not appear to be a git repository

Lead action:
  1. Check git remote: git remote -v
  2. Configure origin if missing:
     git remote add origin <repo_url>
  3. Authenticate (if needed):
     git config user.name "Claude Code"
     git config user.email "claude@anthropic.com"
  4. Retry push
```

**Scenario C: Remote Rejected (branch protection rule)**

```bash
git push -u origin claude/fix-deadlock-iDs6b
# Error: [rejected] ... (protected branch, required status checks failed)

Lead action:
  1. Don't force push (violates Ω gate, never do this without explicit user request)
  2. Diagnose why status checks failed:
     git log <base>..HEAD (what commits are we pushing?)
     These should all be from team consolidation

  3. If checks really failed:
     Run local validation: bash scripts/dx.sh all
     If GREEN locally but rejected remotely: contact DevOps
     If RED locally: we have a bigger problem (section 3.2)
```

**Expected Outcome**:
- Branch conflict: Resolve via rebase/merge, push succeeds
- Permission error: Fix auth, push succeeds
- Status check failure: Unlikely (we ran dx.sh all), contact DevOps
- Core outcome: Final commit is atomically pushed to remote

---

## 4. Message Delivery Guarantees

### 4.1 Message Ordering (FIFO per Teammate)

**Guarantee**: Messages TO the same teammate are delivered in order (FIFO).

**Mechanism**:
```json
{
  "from": "lead",
  "to": "tm_1",
  "sequence_number": 42,
  "timestamp": "2026-02-20T14:25:45Z",
  "payload": "Schema team: Add <priority> element with 4 levels"
}
```

**Teammate receives** (in order):
```
msg 40: "Starting priority feature work"
msg 41: "Schema team: Define element first"
msg 42: "Schema team: Add <priority> element with 4 levels"
msg 43: "Ready when you are"
```

**Enforcement**: Teammate acknowledges each message receipt before processing next. If gap in sequence_number, teammate pauses and messages lead: "Missed messages? Received 40, 42 (missing 41). Resend?"

**Expected Outcome**: No out-of-order message delivery. If message lost, detection via sequence gap.

---

### 4.2 Lost Message Recovery (If Message Not Delivered)

**Trigger**: Message sent by lead, but teammate never receives it (network failure, lead crash, etc.).

**Detection**:

```
Lead sent message (seq 42) at 14:25:45
Teammate should ACK within 30 seconds
Current time: 14:26:20
No ACK received → Message may be lost
```

**Recovery Protocol**:

```
Lead action:
  1. Resend message with note:
     "Resending msg 42 (original time 14:25:45). If you already received and acted,
      please ACK this copy. If not, please ACK this time."

  2. Wait 30 seconds for ACK

  3. If ACK received:
     Lead: "Confirmed msg 42 received. Proceeding."
     Teammate: Acknowledges

  4. If no ACK:
     Lead: Escalate to health check (section 1.1)
            Assume teammate unresponsive
```

**Idempotency**: Messages that trigger state changes (e.g., "Add schema element") should be idempotent:

```
Message 1: "Add <priority> element"
Teammate: Adds element, ACKs

Message 1 (resend): "Add <priority> element"
Teammate: Element already exists, skips, ACKs (no duplicate added)
```

**Expected Outcome**: Lost messages are re-transmitted and acknowledged, no silent failures.

---

### 4.3 Duplicate Messages (Message Delivered Twice)

**Trigger**: Message sent once, received twice (network retry, lead crashed and restarted with stale queue).

**Detection**:
```
Teammate receives message (seq 42, timestamp 14:25:45)
Teammate receives same message again (seq 42, timestamp 14:25:45)
Duplicate detected (same sequence_number + timestamp)
```

**Handler**:

```
Teammate action:
  1. Check: Have I already processed seq 42?
     ├─ YES: Message is duplicate
     │  └─ Action: Send ACK WITHOUT re-executing
     │          Message lead: "Already processed msg 42. Duplicate detected & discarded."
     │
     └─ NO: Process message normally, ACK
```

**Expected Outcome**: Duplicates are detected and ignored, no state corruption.

---

## 5. Invariant Violations During Execution

### 5.1 Hook Detects Violation Mid-Task (Q Violation)

**Trigger**: Teammate's code triggers hyper-validate.sh hook block.

**Scenario**:
```
Teammate Engineer A is implementing YWorkItem state validation.
Locally, dx.sh -pl yawl-engine is GREEN (teammate ran it).

But then Engineer A commits locally with code containing TODO comment:

  public void setState(TaskState newState) {
    // TODO: Add deadlock detection here
    this.state = newState;
  }

Hook blocks: TODO detected (H violation).
```

**Lead Detection**:

```
Lead runs: bash scripts/dx.sh all
Output contains: Hook error from yawl-engine
               "TODO: Add deadlock detection here" (H violation)
```

**Lead Action**:

```
1. Identify which teammate added the violation
   └─ git log --oneline (last 3 commits show Engineer A's work)

2. Message teammate:
   "Hook blocked TODO in YWorkItem.setState() line 42.
    Either implement the deadlock detection or throw UnsupportedOperationException.

    Option 1 (Preferred): Implement real deadlock check
    Option 2 (Fallback): throw UnsupportedOperationException

    Fix + re-run local dx.sh within 10 minutes."

3. Teammate responds:
   "Implementing real deadlock detection (2-minute estimate)."
   OR
   "Not needed for MVP. Throwing UnsupportedOperationException."

4. Teammate runs local dx.sh -pl yawl-engine → GREEN (hook passes)

5. Lead re-runs full dx.sh all → GREEN
```

**Expected Outcome**:
- Hook violation detected
- Teammate fixes locally
- Violation cleared in next build
- Total delay: ~10-15 minutes

**Prevention**: Teammates should run local hook before declaring local GREEN:
```bash
bash scripts/dx.sh -pl yawl-engine  # → GREEN
bash .claude/hooks/hyper-validate.sh yawl/engine/src/  # → GREEN
# Both green = safe to say "local dx GREEN"
```

---

### 5.2 Invariant Violation in Consolidation (Teammate Made Assumption About Other Module)

**Trigger**: Lead consolidates work, discovers engineer A's code assumes behavior that engineer B didn't implement.

**Scenario**:
```
Engineer A (Engine): Calls YSpecification.validateState()
Engineer B (Schema): Supposed to add schema validation

But Engineer B finished early and didn't add <xs:assertion> yet
(thought it was optional).

Now Engine code calls validateState() which throws because schema has no constraint.

Full dx.sh all: RED — integration tests fail
```

**Lead Action**:

```
1. Identify missing piece
   └─ Error: "validateState() expects schema constraint <xs:assert> at line 234"
      But schema doesn't have it

2. Message Schema engineer B:
   "Engine code expects <xs:assert> forbidding ENABLED→FIRED.
    I need you to add that constraint now.

    Local test: yawl-schema should re-validate and pass all schema tests."

3. Engineer B adds constraint, runs dx.sh -pl yawl-schema → GREEN

4. Lead re-runs dx.sh all → GREEN

5. If multiple missing pieces:
   └─ Identify all, batch fixes, re-run
```

**Expected Outcome**:
- Assumption mismatch discovered in consolidation
- Lead identifies which teammate must fix
- Fix is applied, build re-run
- Atomic commit includes all fixes

**Prevention**: Teammates should message about assumptions during execution (section 6.1, messaging phase).

---

## 6. Failure Recovery Workflows

### 6.1 Decision Tree: When to Halt vs Continue

**General Rule**:
```
Failure occurs in execution

├─ Is failure blocking ALL teammates?
│  ├─ YES (e.g., lead crashed)
│  │  └─ Action: HALT TEAM, enter session resumption (see session-resumption.md)
│  │
│  └─ NO → failure is local to 1-2 teammates
│     └─ Continue (section 6.2)

Is failure recoverable in <30 minutes?
├─ YES → Attempt recovery (one pass only)
│  ├─ If recovery succeeds → continue team
│  └─ If recovery fails → Decide: assign to lead solo, or re-assign to new teammate
│
└─ NO (fundamental architectural issue)
   └─ HALT TEAM → post-mortem (section 6.3)
```

---

### 6.2 One-Pass Recovery Protocol

**When Lead Attempts Recovery** (for single-teammate failures):

```
Failure occurs
  ↓
Lead determines: Recoverable in <30 min? (yes/no)
  ├─ YES
  │  └─ Action: Message teammate with specific fix
  │          "Here's the issue: ... Fix by [deadline] or I'll reassign."
  │          Deadline = now + 20 minutes
  │          ↓
  │          Teammate responds within 20 min?
  │          ├─ YES: Implement fix, re-run dx.sh -pl <module>
  │          │  └─ Green? Continue. Red? FAIL below.
  │          │
  │          └─ NO: Assume unresponsive, REASSIGN (see 6.2.1)
  │
  └─ NO → HALT (go to 6.3)
```

#### 6.2.1 Reassignment (Crashing Teammate Replaced)

```
Lead decides: Current teammate can't recover, reassign task.

Saved state:
  - Checkpoint commit with teammate's work
  - Task description
  - Dependency info

New teammate onboarding:
  1. Lead: "Engineer A crashed/stalled. I've checkpointed their work at commit abc1234.
            Can you take over? Task: Fix YNetRunner deadlock
            Status: 70% done (state machine analysis + test cases added).
            Next: Implement synchronized block in advance() method.

            You have 45 min to complete."

  2. New teammate:
     - git checkout abc1234
     - Read Engineer A's comments in code
     - Implement missing piece
     - Run local dx.sh -pl yawl-engine → GREEN
     - Message lead: "Completed, local dx GREEN."

  3. Lead: Continues consolidation with new teammate's work
```

**Cost**: ~20-30 min delay, but task completes.

---

### 6.3 Post-Mortem (After Team Failure)

**When to Trigger**: Team halted due to unrecoverable failure.

**Process**:

```
1. Preserve state
   └─ Checkpoint all work (git add + commit)
      Create analysis commit: "TEAM FAILURE CHECKPOINT: [reason]"
      DO NOT PUSH (keep local for analysis)

2. Diagnose root cause
   └─ Lead + failed teammate(s) analyze:
      - What went wrong?
      - When did it become unrecoverable?
      - Could it have been prevented?

3. Document findings
   └─ Create analysis document:
      .claude/reports/team-failure-<sessionId>.md

      Include:
      - Timeline of events
      - Decision points and options considered
      - Root cause analysis
      - What could be improved
      - Recommendation: Try again with fixes? Or revert to single session?

4. Decide: Retry or Rollback?
   ├─ RETRY (with fixes)
   │  └─ Apply lessons learned
   │          Re-run team with same quantums but different approach
   │          (e.g., different task ordering, additional inter-teammate syncs)
   │
   └─ ROLLBACK
      └─ git reset --hard <base_branch>
         Revert all team work
         Single-session engineer implements sequentially
         (slower but guaranteed to work)
```

**Expected Outcome**: Failure is analyzed, documented, and team can retry with fixes or rollback gracefully.

---

## 7. STOP Conditions (Add to CLAUDE.md)

When ANY of these occur during team execution, **HALT immediately** and escalate to lead for decision:

| Condition | Action | Resolution |
|-----------|--------|-----------|
| **Circular dependency** detected | Halt team formation | Lead breaks tie via task ordering (2.1) |
| **Teammate timeout** (30 min idle) | Message teammate, await response (5 min) | If no response: CRASH (3.1) |
| **Task timeout** (2+ hours, no message) | Message teammate for status | If no response: Reassign (6.2.1) |
| **Message timeout** (15 min, critical) | Resend with [URGENT] prefix | If still no response: CRASH (3.1) |
| **Lead DX fails** (after all teammates GREEN) | Halt consolidation | Identify and fix incompatibility (3.2) |
| **Hook detects Q violation** | Halt build | Teammate fixes locally (5.1) |
| **Teammate crash** (unresponsive >5 min) | Halt task | Reassign (6.2.1) or save work (3.1) |
| **Message delivery unconfirmed** (dup/loss) | Detect via sequence gap | Resend + ACK (4.2, 4.3) |
| **Unrecoverable failure** (>30 min to fix) | Halt team | Post-mortem + retry/rollback (6.3) |

**Enforcement**: Lead uses decision tree (6.1) for each STOP condition. If uncertain → call post-mortem.

---

## 8. Reference Architecture

### 8.1 Error State Machine

```
Team Created
    ↓
Ψ (Discovery) ──[detect circular dep]──→ HALT (break tie)
    ↓
Λ (Local DX) ──[compile fails]──→ FIX (teammate re-run) ──[success]──→ Λ
    ↓           ↓
    ├[timeout]─→ TIMEOUT_CHECK ──[responsive]──→ FIX
    │           └[unresponsive]──→ CRASH ──[reassign]──→ ONBOARD_NEW
    ↓
H (Guards) ──[Q violation]──→ HALT ──[teammate fixes]──→ Λ
    ↓
Q (Invariants) ──[real impl check]──→ PASS
    ↓
Message Phase ──[circular dep]──→ ESCALATE (lead breaks tie)
    ↓
Ω (Consolidation) ──[lead dx fails]──→ IDENTIFY_INCOMPATIBILITY ──[fix]──→ Ω
    ↓
Push ──[branch conflict]──→ RESOLVE ──[success]──→ SUCCESS
    ↓
COMPLETE
```

### 8.2 Message Protocol

```
Lead → Teammate:
  { sequence: 42, timestamp, from, to, payload, timeout: 15min }

Teammate → Lead (ACK):
  { ack_sequence: 42, status: received | in_progress | complete }

Duplicate Detection:
  { sequence_number + timestamp } = key for dedup
  If already processed: silent ACK (no re-execution)

Lost Message Recovery:
  Lead: No ACK within 30 sec → Resend with note: "resend of msg 42"
  Teammate: Process once, ACK all duplicates
```

---

## 9. Testing Error Recovery

### 9.1 Failure Injection Tests

```bash
# Test 1: Simulate teammate timeout
.claude/tests/test-scenarios/error-timeout.sh
└─ Freeze teammate for 30 min, measure lead's detection time

# Test 2: Circular dependency detection
.claude/tests/test-scenarios/error-circular-dep.sh
└─ Create task_list with A→B, B→A, measure DFS detection

# Test 3: Lead DX failure
.claude/tests/test-scenarios/error-lead-build-fail.sh
└─ Introduce compilation error in module C, measure detective work time

# Test 4: Message loss simulation
.claude/tests/test-scenarios/error-message-loss.sh
└─ Drop 20% of messages, measure detection & recovery time

# Test 5: Teammate crash simulation
.claude/tests/test-scenarios/error-teammate-crash.sh
└─ Kill teammate process, measure checkpoint + reassignment time
```

### 9.2 Success Criteria

```
Timeout Detection: <5 minutes after timeout occurs ✓
Circular Dependency Detection: Before team formation ✓
Incompatibility Fix: <20 minutes from build fail ✓
Message Loss Recovery: <2 min (detected in next ACK cycle) ✓
Teammate Crash Recovery: <15 min (new teammate onboarded) ✓
Post-Mortem Documentation: Complete and actionable ✓
```

---

## Summary Table

| Failure Mode | Detection Time | Recovery Time | Total Impact | Prevention |
|--------------|---|---|---|---|
| Teammate idle (30 min) | 30 min | 5-10 min | 35-40 min | Send status checks |
| Task timeout (2+ hours) | 120 min | 10-20 min | 130-140 min | Reduce scope, parallel |
| Message timeout (15 min) | 15 min | 5 min | 20 min | Resend + ACK |
| Circular dependency | Before spawn | 10 min (tie-break) | 10 min | Pre-validate DAG |
| Lead DX fail | During consol. | 15-30 min | 15-30 min | Share API contracts |
| Push fail | Post-consol. | 5-10 min | 5-10 min | Check branch status |
| Hook violation | During consol. | 10-15 min | 10-15 min | Local hook check |
| Teammate crash | 5-10 min (timeout) | 20-30 min (reassign) | 25-40 min | Health checks |
| Message loss | 30 sec (ACK timeout) | 2 min (resend) | 2-3 min | Sequence ACK |
| Message dup | <1 sec (seq check) | 0 min (dedup) | <1 sec | Idempotency |

---

## References

- CLAUDE.md: Teams section, GODSPEED flow
- team-decision-framework.md: When to use teams, team patterns
- session-resumption.md: Durable team state persistence
- TEAM-VALIDATION-SCENARIOS.md: 7 real execution scenarios
- team-recommendation.sh: Hook logic for N quantum detection

---

**Document Generated**: 2026-02-20
**Author**: Test Specialist (Validation Team)
**Status**: Ready for Production Use
**Next Step**: Integrate failure injection tests into CI/CD pipeline
