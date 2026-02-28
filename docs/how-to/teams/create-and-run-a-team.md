# Create and Run a Team: Step-by-Step Guide

## Introduction

This guide walks you through forming and executing a team in YAWL. Teams enable parallel work across orthogonal domains (quantums) with built-in messaging and consolidation. Each teammate works independently but can exchange findings to iterate together.

**When to use**: Cross-layer changes (schema + implementation), complex debugging (3+ perspectives), or any work requiring 2-5 teammates messaging and iterating.

**Not for**: Single-quantum work (use single session), <30 min tasks (setup cost exceeds benefit), or >5 teammates (too much coordination overhead).

For decision help, run: `bash .claude/hooks/team-recommendation.sh "your task description"`

---

## Part 1: Form a Team (Pre-Flight)

### Step 1: Define Your Quantum Domains

A quantum is an orthogonal work domain. Each teammate owns exactly one quantum.

**Examples**:

- **Engine Investigation** (3 quantums): State machine → Concurrency → Guard logic
- **Schema + Implementation** (2 quantums): Schema design → Engine implementation
- **Code Review** (3 quantums): Security review → Performance review → Test coverage review
- **Observability** (2 quantums): Metrics schema → Engine integration

**Your quantum domains:**
```
Quantum 1: [describe first domain]
Quantum 2: [describe second domain]
Quantum 3: [describe third domain, if applicable]
```

**Validation**: Can teammates work independently on their quantum? Do their results interact? If results never interact → use subagents instead (cheaper).

---

### Step 2: Validate Team Size

Valid team sizes: **2, 3, 4, or 5 teammates**. One teammate per quantum.

| Size | Pattern | Cost | Use If |
|------|---------|------|--------|
| **2** | Schema + Impl, Observability | $2-3C | Two-layer changes, low iteration count |
| **3** | Engine investigation, reviews | $3-4C | Multiple perspectives or parallel deep dives |
| **4** | Extended investigation | $4-5C | 4+ orthogonal axes, high messaging needs |
| **5** | Max parallelism | $4-5C | Complex system-wide refactoring |

If you need >5 teammates:
- **Option A**: Split into 2 sequential phases (Team A finishes, then Team B)
- **Option B**: Use single session (if dependencies are tight)

---

### Step 3: Verify No File Conflicts

Two teammates cannot edit the same file (YAWL has no MVCC). Verify by checking the observatory facts file.

**Run**:
```bash
cat .claude/facts/shared-src.json | jq '.files_with_multiple_owners'
```

Expected output: `[]` (empty array) or files that teammates already own.

**If conflicts exist**:
- Refactor so each teammate owns distinct files
- Example: Teammate A owns `YNetRunner.java`, Teammate B owns `YNetRunnerTest.java` (distinct)

---

### Step 4: Ensure Facts Are Current

Observatory facts tell you module structure, dependencies, and known conflicts. Update before team formation.

**Run**:
```bash
bash scripts/observatory/observatory.sh
```

This takes 30-60 seconds. Output: `.claude/facts/observatory.json` with checksums and timestamps.

**Verify**: Check the file was updated within the last hour.

---

### Step 5: Create Task Descriptions

Each teammate gets **one task**. Each task must be ≥30 minutes of work.

**Task template** (for each teammate):

```markdown
## Task: [Quantum name]

**Quantum**: [Brief domain description]

**Scope**: [What code/files this teammate owns]

**Dependencies**:
- Blocks: [If this task blocks others, list them]
- Blocked by: [If this task waits for others, list them]
- Contracts: [API signatures other teammates depend on]

**Success Criteria**:
- [ ] Local dx.sh passes (./scripts/dx.sh -pl <module>)
- [ ] H-Guards hook passes (no TODO, mock, stub, fake, empty returns, silent fallbacks)
- [ ] All assumptions documented in message to lead
- [ ] Ready for consolidation

**Estimated Time**: 1-2 hours

**Notes**: [Any guidance, gotchas, or assumptions]
```

**Example task** (Schema + Implementation team):

```markdown
## Task 1: Workflow Attribute Schema

**Quantum**: XSD schema design for SLA tracking

**Scope**:
- yawl/schema/YAWLSchema.xsd (new attribute definition)
- Test fixture: schema-validation-sla.xsd

**Dependencies**:
- Blocks: Task 2 (implementation depends on schema)
- Contracts:
  - New attribute: `sla_minutes` (integer, required on workflow)
  - Validation: Must be positive, ≥1

**Success Criteria**:
- [ ] Schema valid per XSD spec (xmllint passes)
- [ ] H-Guards hook GREEN
- [ ] Documented contracts sent to Task 2 lead
```

```markdown
## Task 2: Engine Implementation (SLA)

**Quantum**: YNetRunner SLA enforcement

**Scope**:
- yawl/engine/YNetRunner.java (add sla_minutes handling)
- Tests in yawl/engine/test/YNetRunnerSlaTest.java

**Dependencies**:
- Blocked by: Task 1 (waiting for schema)
- Contracts expected:
  - Read schema's sla_minutes attribute
  - Enforce timeout on task execution

**Success Criteria**:
- [ ] All tests GREEN
- [ ] H-Guards hook GREEN
- [ ] Local dx.sh passes
```

---

### Step 6: Assign Teammates and Verify Checklist

Before summoning the team, complete this checklist:

- [ ] Task 1 description written (≥30 min)
- [ ] Task 2 description written (≥30 min)
- [ ] (If 3+ teammates): Task 3+ descriptions written
- [ ] No file ownership conflicts confirmed
- [ ] Observatory facts are current
- [ ] Team size is 2-5
- [ ] Each quantum is orthogonal (minimal blocking)
- [ ] All teammates have accepted their quantum/task
- [ ] Expected duration is 1-3 hours per teammate

**Once checklist GREEN**: Proceed to Part 2 (Execute).

---

## Part 2: Execute Team Work

### Step 7: Each Teammate Starts Independent Work

Each teammate works on their task in parallel. **No coordination yet**—just get to a working state.

**Per teammate**:

1. **Read your task description** fully
2. **Set up your environment** (branch, modules, dependencies)
3. **Implement your quantum**:
   - Write real code (not TODO/mock/stub/fake/empty returns)
   - Throw `UnsupportedOperationException` if you can't implement something
4. **Run local validation**:
   ```bash
   ./scripts/dx.sh -pl <your-module>  # Compile + test your module only
   ```
   Expected: BUILD SUCCESS
5. **Run H-Guards hook** (validates no deferred work):
   ```bash
   bash .claude/hooks/hyper-validate.sh
   ```
   Expected: All checks GREEN
6. **Commit locally** (don't push yet):
   ```bash
   git add src/... test/...
   git commit -m "Implement [quantum]: [brief summary]"
   ```
7. **Mark as READY** in messaging to lead

---

### Step 8: Share Findings and Contracts

Once your local work is GREEN, message the lead with:

1. **API contracts** (methods, signatures, behaviors you've defined/discovered):
   ```
   Task 1 → Lead:
   "Schema defines new attribute `sla_minutes` (int, positive, required).
    Signature: @XmlAttribute(name="sla_minutes") int slaMins.
    Validation in schema enforces: xs:positiveInteger"
   ```

2. **Assumptions about other quantums**:
   ```
   Task 2 → Lead:
   "I assume Task 1 will provide schema attribute `sla_minutes`.
    If it's different (e.g., `sla_timeout`), my implementation breaks.
    Waiting for Task 1's contract message."
   ```

3. **Blocked or blocking status**:
   ```
   Lead tracks: Task 2 is BLOCKED_WAITING_FOR_TASK_1
   Once Task 1 shares contracts → Task 2 becomes READY_TO_INTEGRATE
   ```

4. **Any discoveries that affect other teammates**:
   ```
   Task 3 → Lead:
   "Found that YNetRunner.execute() is called from multiple threads.
    Task 1 and Task 2: ensure your SLA logic is thread-safe."
   ```

---

### Step 9: Lead Coordinates Messaging Phase

The **messaging phase** is where teammates share API contracts and assumptions before full consolidation.

**Lead's job**:
- Receive findings from all teammates
- Identify contract mismatches early
- Message teammates if: "Task 1 and Task 2 have incompatible contracts"
- Document assumptions for consolidation later

**Example messaging sequence**:

```
09:00 - Task 1 (Schema) declares READY:
  "Schema GREEN. Attribute: sla_minutes (int, positive)"

09:15 - Task 2 (Engine) declares READY_FOR_CONTRACTS:
  "Engine skeleton GREEN, waiting for schema contracts.
   I expect: attribute name sla_minutes with type integer."

09:20 - Lead confirms contract match:
  "Task 1 & 2 contracts align. Both GREEN. Task 3, status?"

09:25 - Task 3 (Tests) declares READY:
  "Tests GREEN. Uses schema attribute sla_minutes.
   Assumes types match Task 2's implementation."

09:30 - Lead: "All contracts confirmed. Proceed to consolidation."
```

**No changes during messaging phase**—just verification and documentation.

---

### Step 10: Monitor Teammate Health

**Every 30 seconds**, lead checks teammate heartbeats.

**Heartbeat status tiers**:

| Elapsed Time | Status | Action |
|--------------|--------|--------|
| <10 min | ✓ ONLINE | Normal—can message anytime |
| 10-30 min | ⚠ IDLE | Send wake-up message: "Status update?" |
| 30-60 min | ❌ STALE | Teammate likely crashed; message for recovery |
| >60 min | 🔴 DEAD | Assume unresponsive; reassign task |

**If teammate idle >30 min**:
1. Send message: "[Urgent] Status? If no response in 5 min, will reassign."
2. Wait 5 minutes
3. If no response: Reassign task to new teammate (with checkpoint recovery)

---

### Step 11: Handle Dependencies & Timeouts

**Circular dependency detected** (Task A needs Task B, Task B needs Task A):

1. Lead identifies the cycle in task descriptions
2. Lead decides task ordering: "Task A starts with mock contract, Task B implements assuming mock, then Task A validates"
3. Message both teammates with revised order

**Task timeout** (one teammate's work exceeds 2 hours):

1. Message: "Task [X] has been in progress for 2 hours. Can you split into subtasks or push to done?"
2. Options:
   - If implementable in <1 hour more: allow extension
   - If stuck: split into subtask + reassign remaining work
   - If context exhausted: reassign with checkpoint recovery

---

## Part 3: Consolidation (Lead's Phase)

### Step 12: Declare All Teammates GREEN

Before consolidation, all teammates must have:

- [ ] Local `dx.sh -pl <module>` passes
- [ ] H-Guards hook passes (no violations)
- [ ] Sent findings/contracts to lead
- [ ] Committed locally (not pushed)
- [ ] Marked READY in message

---

### Step 13: Pull All Changes and Run Full Build

Lead runs the **consolidation phase**:

1. **Fetch all teammate changes** (via git):
   ```bash
   git fetch origin
   git log --oneline -10  # Verify all teammate commits visible
   ```

2. **Run full validation** (compile ≺ test ≺ validate):
   ```bash
   ./scripts/dx.sh all
   ```

   Expected: `BUILD SUCCESS`

3. **If build fails**:
   - Identify which module(s) failed
   - Diagnose root cause:
     - Missing API contract? Message responsible teammate
     - Incompatible signatures? Decide who fixes it
     - Transitive dependency? Rebuild modules in order
   - Message teammate: "dx.sh all failed. See [error details]. Fix and message when ready for retry."
   - Re-run once fixed

---

### Step 14: Verify Hook Compliance

Run the H-Guards hook across all generated code:

```bash
bash .claude/hooks/hyper-validate.sh
```

Expected: All patterns pass (no TODO, mock, stub, fake, empty returns, silent fallbacks, or lies).

If violations: Message responsible teammate to fix within 10 minutes.

---

### Step 15: Atomic Commit

Once `dx.sh all` and hooks are GREEN:

```bash
git add src/ test/ schema/
git commit -m "$(cat <<'EOF'
Consolidated team work: [quantums involved]

- Teammate A: [brief summary of quantum 1]
- Teammate B: [brief summary of quantum 2]
- [Additional teammates]

All local dx.sh + hooks GREEN. Ready for consolidation.

https://claude.ai/code/session_[your-session-id]
EOF
)"
```

---

### Step 16: Push to Feature Branch

```bash
git push -u origin claude/[feature-name]-[session-id]
```

Create a pull request with the consolidation commit.

---

## Part 4: Troubleshooting

### "My teammate went offline (no heartbeat for 30+ min)"

**What happened**: Teammate's context exhausted, crashed, or stuck.

**Recovery**:
1. Lead checks last commit timestamp: `git log --oneline --all | head -5`
2. If task is 80%+ done: new teammate resumes from checkpoint
3. If task is <80% and independent: lead finishes alone
4. If task is <80% and critical: reorder work, reassign

**Example**:
```
Lead's action:
git log --oneline task-2-implementation  # Last commit: 2 hours ago
→ Task 80% done, last change was 2 hours ago
→ New teammate resumes from that commit + checkpoint file
```

---

### "Tasks ended up depending on each other (circular)"

**What happened**: Circular dependency emerged during execution (wasn't caught in pre-flight).

**Recovery**:
1. Lead identifies the cycle: "Task A reads field X, Task B defines field X, they're waiting for each other"
2. Lead decides task order: "Task B defines X first (with placeholder if needed), Task A reads it, then Task B validates"
3. Message both teammates with revised order and timeline

---

### "dx.sh all failed but all teammates are GREEN locally"

**What happened**: Incompatible assumptions between teammates' code.

**Diagnosis**:
```bash
./scripts/dx.sh all 2>&1 | grep -A 5 "ERROR\|FAIL"
# Output: YNetRunner.java:427: cannot find symbol: method validateSla(int)
```

This means Task 2 (implementation) calls a method Task 1 (schema) didn't export.

**Recovery**:
1. Identify which teammate owns the missing piece
2. Message them: "dx.sh all failed. Missing method: validateSla(int). Can you add or adjust call site?"
3. Teammate fixes locally, runs `dx.sh -pl <module>`, commits
4. Lead re-runs `dx.sh all`

---

### "Got stuck in circular dependency but can't break it"

**What happened**: Both tasks truly depend on each other; can't order them.

**Recovery options**:
1. **Use interface contracts**: Task A defines interface, Task B implements, Task A uses interface (no cycle)
2. **Split one task**: Reduce scope so one task can go first independently
3. **Sequential phases**: Run Task A to completion, then Task B (loses parallelism)

---

### "One teammate's task is taking way longer than estimated"

**What happened**: Scope underestimated, stuck on problem, or context exhaustion.

**Recovery**:
1. Check: Is this still <2 hours total? If yes: allow extension
2. If >2 hours: message teammate to split task
   - "Task [X] is taking longer than expected. Can you break it into subtask A (done now) and subtask B (reassign to new teammate)?"
3. Reassign subtask B with checkpoint file

---

### "H-Guards hook failed (found TODO/mock/stub)"

**What happened**: Teammate's code has deferred work, mocks, or empty returns.

**Recovery**:
1. Lead messages teammate with specific violation:
   ```
   "H-Guards found violations:
    - YNetRunner.java:427: TODO comment (H_TODO)
    - MockDataService.java:12: Mock class name (H_MOCK)

    Fix by: Implement real logic or throw UnsupportedOperationException.
    Deadline: 10 minutes."
   ```
2. Teammate fixes locally, re-runs H-Guards hook
3. Teammate commits fix and messages lead
4. Lead re-runs `dx.sh all + hyper-validate.sh`

---

## Part 5: Templates & Checklists

### Pre-Formation Checklist

Use this before summoning the team.

```markdown
## Team Pre-Formation Checklist

### Task Definition
- [ ] Quantum 1: [name] — Teammate [A]
- [ ] Quantum 2: [name] — Teammate [B]
- [ ] (If applicable) Quantum 3: [name] — Teammate [C]

### Team Composition
- [ ] Total teammates: 2-5? ✓
- [ ] Each teammate assigned exactly 1 quantum? ✓
- [ ] Each task estimated ≥30 min? ✓
- [ ] All teammates confirmed available? ✓

### Conflict Check
- [ ] Ran observatory: bash scripts/observatory/observatory.sh ✓
- [ ] Checked shared-src.json for conflicts? ✓
- [ ] No file owned by 2+ teammates? ✓

### Dependency Analysis
- [ ] Identified task dependencies (A blocks B, etc.)? ✓
- [ ] No circular dependencies? ✓
- [ ] Blocking tasks ≤2? (If >2: reconsider team structure) ✓

### Ready to Form
- [ ] All checkboxes above GREEN? ✓
- [ ] Lead will monitor heartbeats every 30s? ✓
- [ ] All teammates briefed on messaging protocol? ✓
- [ ] Estimated consolidation time: [X hours] ✓

**Approved by**: [lead name] | **Date**: [today]
```

---

### Task Template

Use this to describe work for each teammate.

```markdown
# Task: [Quantum Name]

## Overview
- **Quantum**: [Domain description]
- **Owner**: [Teammate name]
- **Estimated Time**: 1-2 hours
- **Status**: [PENDING | IN_PROGRESS | READY | GREEN]

## Scope
- **Files**: [src/main/java/org/yawl/... | schema/...]
- **Modules**: [yawl-engine | yawl-schema | ...]
- **Responsibilities**:
  - [ ] Implement [feature/change]
  - [ ] Add/update tests
  - [ ] Document assumptions

## Dependencies
- **Blocks**: [Task B, Task C] (what this task enables)
- **Blocked by**: [Task A] (what must finish first)
- **Contracts Provided**:
  - Method: `public Type methodName(params)`
  - Validation: [rules]
  - Behavior: [what it does]
- **Contracts Expected**:
  - From [Task X]: [API signature, input/output]

## Success Criteria
- [ ] Code compiles: `./scripts/dx.sh -pl <module>`
- [ ] Tests pass (including new tests for feature)
- [ ] H-Guards hook passes (no TODO/mock/stub/empty returns)
- [ ] Assumptions documented and messaged to lead
- [ ] Ready for consolidation (local work committed)

## Notes
- [Any gotchas, edge cases, or guidance]
- [Links to related docs, examples, patterns]

---

**Teammate to read this fully before starting.**
```

---

### Message Template

Use this when sharing findings with the lead.

```markdown
## Message to Lead: Task [X] Update

**Status**: [READY | BLOCKED | WAITING_FOR_CONTRACT | IN_PROGRESS]

**Summary**:
[One sentence: what you accomplished or what's blocking]

**API Contracts Defined**:
```java
// If applicable: method signatures, classes, attributes you've defined
public class YNetRunner {
    private int slaMins;  // New field for SLA minutes

    public void validateSla() throws SlaTimeoutException {
        // Behavior: throws if task exceeds sla_mins
    }
}
```

**Assumptions About Other Tasks**:
- Task [A] must provide: [API/behavior]
- Task [B] must NOT [conflicting design]
- If above assumptions broken: [impact on my code]

**Thread Safety / Performance Notes** (if relevant):
- Assumes single-threaded: [details]
- Performance impacts: [what's slow, why]

**Blockers** (if any):
- [Issue]: [description]
- Waiting for: [Task X contract] | [Clarification on design]

**Next Steps**:
- If contracts confirmed: ready to integrate
- If assumptions broken: need to refactor [area]

---

**Teammate**: [name] | **Time sent**: [timestamp]
```

---

### Consolidation Checklist (Lead)

Use this before running `dx.sh all`.

```markdown
## Consolidation Checklist

### Pre-Flight
- [ ] All teammates sent READY message?
- [ ] All contracts and assumptions documented?
- [ ] No contradictions in teammate messages?
- [ ] All teammates' local commits visible (git log)?

### Build Phase
- [ ] Running: ./scripts/dx.sh all
- [ ] Expected: BUILD SUCCESS
- [ ] If failure:
  - [ ] Identified module(s) that failed
  - [ ] Root cause diagnosed (missing API, signature mismatch, etc.)
  - [ ] Responsible teammate messaged with fix request
  - [ ] Re-run after fix applied

### Hook Phase
- [ ] Running: bash .claude/hooks/hyper-validate.sh
- [ ] Expected: All checks GREEN
- [ ] If failure:
  - [ ] Identified violations (TODO, mock, stub, fake, etc.)
  - [ ] Responsible teammate messaged: 10-minute fix deadline
  - [ ] Re-run after fix applied

### Commit Phase
- [ ] git status shows all changes staged
- [ ] Commit message includes:
  - [ ] Feature description
  - [ ] List of quantums/teammates
  - [ ] Session URL
- [ ] Commit created: git commit -m "..."

### Push Phase
- [ ] git push -u origin claude/[feature]-[session-id]
- [ ] PR created (if applicable)
- [ ] All CI checks passing on remote

---

**Lead**: [name] | **Consolidation complete**: [timestamp]
```

---

## Quick Reference: Key Timeouts

| Timeout | Duration | Trigger | Action |
|---------|----------|---------|--------|
| **Teammate heartbeat online** | <10 min | Normal operation | Lead can message anytime |
| **Teammate heartbeat idle** | 10-30 min | No status for 10+ min | Lead sends wake-up message |
| **Teammate heartbeat stale** | 30-60 min | No status for 30+ min | Consider reassignment |
| **Teammate offline** | >60 min | No heartbeat for 60+ min | Reassign task to new teammate |
| **Critical message** | 15 min | Message sent, no ACK | Resend with [URGENT] tag |
| **Critical message retry** | 5 min after [URGENT] | Still no response | Escalate or reassign |
| **Task completion** | 2 hours | Task started but not done | Check if stuck; allow 1 more hour or split |
| **Task hard limit** | 3 hours | Task ongoing >3 hours | Escalate to lead; consider reassignment |
| **ZOMBIE mode** (lead offline) | >5 min | Lead unresponsive | Teammates auto-checkpoint; continue locally |
| **Lead recovery window** | 30 min | Lead comes back | Load checkpoints and resume team |

---

## Key STOP Conditions (HALT Immediately)

| Condition | Status | Lead Action |
|-----------|--------|-------------|
| **Circular dependency detected** | Team formation fails | Break tie via task ordering |
| **Teammate offline >30 min** | Task stalled | Reassign to new teammate with checkpoint |
| **dx.sh all fails** | Consolidation blocked | Diagnose incompatibility, message responsible teammate |
| **H-Guards violations found** | Hook blocks merge | Teammate fixes within 10 min |
| **Task >2 hours with no progress** | Potential blocker | Message teammate for status; consider split |
| **Critical message >15 min no ACK** | Communication loss | Resend with [URGENT]; escalate if still no response |
| **Teammate crash (>5 min unresponsive)** | Context lost | Load checkpoint, reassign task |

---

## Glossary

- **Quantum**: An orthogonal work domain (e.g., schema design, engine implementation, test design)
- **Teammate**: A Claude agent assigned to one quantum
- **Task**: Atomic work unit for one teammate (≥30 min, ≤2 hours)
- **Heartbeat**: Periodic status signal from teammate (every 60s)
- **Message**: Async communication between lead and teammate (includes contracts, blockers, status)
- **Contract**: API signature, validation rule, or behavior agreement between quantums
- **Consolidation**: Final phase where lead runs full build and commits all work
- **Checkpoint**: Snapshot of team state (task progress, teammate status, messages) for recovery
- **ZOMBIE mode**: Teammates continue work locally if lead goes offline >5 min
- **H-Guards**: Hook that validates no deferred work (TODO, mock, stub, fake, empty returns)

---

## What's Next?

- **For decision help**: Run `bash .claude/hooks/team-recommendation.sh "your task description"`
- **For error recovery details**: See `.claude/rules/TEAMS-GUIDE.md` (error recovery section)
- **For session resumption**: See `.claude/rules/TEAMS-GUIDE.md` (session resumption section)
- **For team patterns**: See `.claude/rules/TEAMS-QUICK-REF.md` (6 common patterns)
