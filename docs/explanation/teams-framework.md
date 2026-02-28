# The Teams (τ) Framework: Multi-Agent Collaboration for Orthogonal Work

**Quadrant**: Explanation | **Concept**: Multi-agent collaboration patterns — why and when to use teams

This document explains the Teams (τ) framework: when teams make economic sense, how team patterns map to YAWL domains, what the lifecycle looks like, and why some work is better suited to single agents than teams.

---

## The Core Economic Question

**One agent for 2 hours, or 3 agents for 45 minutes each?**

Teams introduce coordination overhead. The answer depends on how much parallelizable work exists and how much overlap you can eliminate through asynchronous messaging.

Teams are never about going faster per clock hour. Teams are about enabling **convergence in wall-clock time** when:

1. **Competing hypotheses** must be explored in parallel (e.g., three different root causes for a deadlock)
2. **Cross-layer changes** require different expertise (schema owner, engine implementer, test engineer)
3. **Research + implementation** can happen concurrently (spec writer and engine developer)
4. **Code review** needs multiple independent perspectives (security, performance, coverage)

Teams cost 3-5× as much as a single session (3-4 agents × 200K context windows). The ROI must be at least 2-3× faster wall-clock convergence to justify the cost.

---

## When Teams Make Economic Sense

### Signal 1: Competing Hypotheses

**Scenario**: YNetRunner deadlock investigation
- One theory: State machine race condition
- Another theory: Guard logic deadlock
- Third theory: Lock acquisition order issue

**Single agent approach**: Investigate each hypothesis sequentially. ~2-3 hours.

**Team approach**: 3 engineers investigate in parallel, report findings, synthesize. ~45-60 minutes wall-clock + 15 min consolidation.

**Why teams win**: The hypotheses don't block each other. Each investigator can work independently on their theory. Asynchronous messaging ("My thread dump shows X, do yours?") allows cross-fertilization without synchronous handoffs.

**ROI**: 2-3× faster wall-clock time justifies ~4× cost in context.

---

### Signal 2: Cross-Layer Changes

**Scenario**: Add SLA tracking to YAWL workflow attribute

**Layers involved**:
1. **Schema**: Define new `<sla>` element in XSD
2. **Engine**: Implement SLA evaluation logic in YNetRunner
3. **Test**: Write integration tests for SLA enforcement
4. **Integration**: Export SLA status via MCP/A2A

**Single agent approach**:
- Write schema → implement engine → write test → integrate
- Sequential, 4-6 hours total
- Back-and-forth: "The test needs X from engine" → fix → re-run test

**Team approach**:
- Parallel: Schema owner drafts XSD, engine dev waits for contract, test engineer preps fixtures
- Async messaging: "Schema defines SlaState enum (ACTIVE, BREACHED, COMPLETE)", engine dev implements
- Consolidation: All pieces integrate with fewer surprises

**Why teams win**: Dependencies are clear but non-blocking. Team members can work in parallel on separate layers, synchronizing via API contracts rather than code review.

**ROI**: 2-3× faster, fewer iteration loops.

---

### Signal 3: Code Review by Concern

**Scenario**: Large refactoring PR with security, performance, and test coverage implications

**Single reviewer**: One person reads all code, context-switches between concerns. Misses cross-cutting issues.

**Team approach** (3 reviewers):
1. Security reviewer: TLS, auth, injection attacks
2. Performance reviewer: GC, lock contention, throughput
3. Test reviewer: Coverage, edge cases, flakiness

Each reviewer runs deep on their domain. Results synthesized in lead consolidation.

**ROI**: Catch more bugs, higher quality feedback, 2× quality gain justifies 3× cost.

---

### Signal 4: Research + Implementation Concurrency

**Scenario**: Add MCP endpoint for case monitoring

**Team approach**:
- Engineer A: Research MCP spec, understand protocol state machine
- Engineer B: Design YAWL case schema, implement persistence
- Both: Share findings async ("MCP requires this sequence..."), converge on interface

**Why teams win**: Research doesn't block implementation. Both start simultaneously.

**ROI**: 1.5-2× faster wall-clock, research knowledge transferred asynchronously.

---

## The Six Core Team Patterns

Every successful team in YAWL falls into one of six patterns. Understanding which pattern you need determines team composition, messaging frequency, and consolidation strategy.

---

### Pattern 1: Engine Investigation (3 engineers)

**When to use**: Deadlock, race condition, or behavioral bug in YNetRunner or stateless engine

**Why 3 engineers**: Complex failures often have multiple independent root causes

**Work breakdown**:
- **Engineer A** (State Machine): Debug state transitions, order of operations
- **Engineer B** (Concurrency): Lock acquisition, thread safety, race windows
- **Engineer C** (Guard Logic): If-then-else chains, missing conditions, specification mismatches

**Collaboration model**:
- Initial async: Each engineer investigates independently with thread dumps, logs, traces
- Checkpoint: "My hypothesis ruled out X. Do your dumps show it differently?"
- Consolidation: Lead synthesizes root cause from three independent threads of investigation

**Messaging frequency**: Low (async) except during consolidation

**Cost**: ~$3-4C | **ROI**: 2-3× faster convergence

**Example**: YAWL-2025-deadlock investigation
- Single engineer: Try X, fails; try Y, fails; try Z, works. (2-3 hours)
- Team: X (ruled out in 20 min), Y (ruled out in 20 min), Z (confirmed in 20 min). Consolidate answer. (1 hour wall-clock)

---

### Pattern 2: Schema + Implementation (2 engineers)

**When to use**: Adding new workflow attributes, extending the Petri net model, or revising element types

**Why 2 engineers**: Tight coupling between spec and implementation, but non-blocking

**Work breakdown**:
- **Engineer A** (Schema Owner): Define XSD changes, enum types, cardinality, constraints
- **Engineer B** (Engine Developer): Implement logic on top of schema, persist to DB, handle edge cases

**Collaboration model**:
1. Schema owner drafts XSD and publishes interface: "SLA element has (deadline: ISO8601, escalation: enum(NONE|NOTIFY|FAIL))"
2. Engine developer starts implementation with interface only, mocks if needed
3. Async validation: "I need a third state: MONITORING. Does that break your logic?" → Schema owner updates XSD
4. Consolidation: All pieces fit together cleanly

**Messaging frequency**: Medium (5-7 async messages during investigation)

**Cost**: ~$2-3C | **ROI**: 2-3 iterations avoided by asynchronous contract negotiation

**Example**: SLA tracking feature
- Sequential: Schema → Engine → Test → Integration. 4-6 hours, 2-3 surprises at integration stage.
- Team: Schema (1h) + Engine (1h parallel) + Test (1h parallel) + Integration (30min consolidation). 1.5 hours wall-clock, 0 surprises.

---

### Pattern 3: Code Review by Concern (3 reviewers)

**When to use**: Large refactoring, security hardening, or performance optimization PR

**Why 3 reviewers**: Cross-cutting concerns require different expertise

**Work breakdown**:
- **Reviewer A** (Security): Crypto, TLS, auth, injection, deserialization
- **Reviewer B** (Performance): GC, locks, throughput, memory, cache behavior
- **Reviewer C** (Testing): Coverage, edge cases, flakiness, test isolation

**Collaboration model**:
1. All reviewers read code in parallel
2. Async: Each publishes findings on their domain
3. Consolidation: Lead synthesizes findings, identifies cross-cutting issues ("Security fix introduces lock contention")
4. Developer fixes, reviewers re-validate key findings

**Messaging frequency**: Low-to-medium

**Cost**: ~$2-3C | **ROI**: 2-3× better quality, catches issues single reviewer misses

**Example**: Stateless engine refactoring
- Single reviewer: Catches security and performance issues. Misses test coverage gap.
- Team: Each reviewer finds issues on their domain independently. Cross-cutting issue (security fix affects locking) caught at consolidation.

---

### Pattern 4: Observability & Monitoring (2 engineers)

**When to use**: Adding metrics, tracing, or dashboards to production engine

**Why 2 engineers**: Design and integration are distinct

**Work breakdown**:
- **Engineer A** (Metrics Designer): Define metric schema (counters, histograms, summaries), storage, aggregation
- **Engineer B** (Integration Engineer): Instrument YNetRunner, emit metrics at key points, wire to observability stack

**Collaboration model**:
1. Designer defines schema: "Task execution time: histogram(0.1s, 1s, 10s, 60s buckets)"
2. Integrator instruments code with counters/timers in parallel
3. Async: "I need a breakdown by task type. Add dimension?" → Designer extends schema
4. Consolidation: All metrics flow through the pipeline

**Messaging frequency**: Medium

**Cost**: ~$2-3C | **ROI**: Production debugging capability gained

---

### Pattern 5: Performance Optimization (2-3 engineers)

**When to use**: Throughput bottleneck, GC spike, or lock contention in critical path

**Why 2-3 engineers**: Profile, hypothesis, implement, validate often require different skills

**Work breakdown**:
- **Engineer A** (Profiler): Run JFR, flame graphs, lock profilers; identify bottleneck
- **Engineer B** (Optimizer): Design fix, implement optimization, verify no behavior change
- **Engineer C** (Validator, optional): Benchmark, stress test, monitor for regression

**Collaboration model**:
1. Profiler identifies bottleneck: "67% of time in YNetRunner.acquireWriteLock()"
2. Optimizer designs fix: "Use ReentrantReadWriteLock instead of Mutex"
3. Validator benchmarks: "10× throughput improvement, no regression on latency p99"

**Messaging frequency**: Low (checkpoint-based)

**Cost**: ~$3-4C | **ROI**: 3× throughput gain in common case

---

### Pattern 6: Autonomous Agent Integration (2-3 engineers)

**When to use**: Adding MCP/A2A endpoints, autonomous tool invocation, or agent-driven case orchestration

**Why 2-3 engineers**: Protocol design, YAWL integration, and test harness are separate concerns

**Work breakdown**:
- **Engineer A** (Protocol Designer): MCP/A2A spec, message contracts, state machines
- **Engineer B** (YAWL Integrator): Wire agent calls to YNetRunner, case context, persistence
- **Engineer C** (Test Engineer, optional): Protocol harness, integration tests, compliance

**Collaboration model**:
1. Designer defines protocol: "MCP requires request ID, timeout, correlation ID"
2. Integrator wires YAWL context and case data to requests
3. Test engineer validates protocol compliance and end-to-end behavior
4. Consolidation: Agent can invoke case actions reliably

**Messaging frequency**: Medium

**Cost**: ~$3-4C | **ROI**: Real-time case monitoring, autonomous orchestration

---

## Team Lifecycle: Formation → Execution → Consolidation

### Phase 1: Formation (Pre-Team Checkpoint)

**Checklist before summoning a team**:

```
□ Run team-recommendation.sh hook?
□ Task has 2-5 orthogonal quantums?
□ Each quantum is ≥30 minutes?
□ No file conflicts? (check shared-src.json)
□ Teammates will message/iterate?
□ Facts are current? (run observatory.sh if >3 files explored)
□ Team feature enabled? (CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1)
```

**Formation exit criteria**:
- Each teammate has clear, independent task
- Async messaging points identified ("Designer publishes schema, implementer waits")
- Consolidation strategy decided ("Lead runs dx.sh all, atomic commit")
- Estimated ROI > 2× (wall-clock speedup justifies cost)

If any checkbox fails, reconsider:
- **No orthogonal quantums?** → Use single session
- **<30 min per task?** → Combine tasks, extend scope
- **File conflicts?** → Refactor ownership or reduce team size
- **No async messaging?** → Use subagents (independent, report-only)

---

### Phase 2: Execution (Parallel Work)

Each teammate works independently on their quantum. **No synchronization required until async messaging points.**

**Teammate responsibilities**:

1. **Report status** (every 60 minutes or at task boundaries)
   - Example: "Engine investigation: ruled out race condition, pursuing guard logic hypothesis"

2. **Emit heartbeat** (every 60 seconds automatically)
   - System detects offline teammates (>30 min idle → reassign)

3. **Message at decision points**
   - Example: "Schema now includes <sla> element with states ACTIVE|BREACHED|COMPLETE. Ready for engine implementation?"

4. **Run local dx.sh before declaring GREEN**
   - Compile + test locally
   - Ensures handoff quality

**Lead responsibilities**:

1. **Monitor heartbeats** (teammate online/idle/offline status)
2. **Facilitate async messaging** (route discoveries across team)
3. **Detect blockers** (if Engineer A waits on Engineer B, message the dependency)
4. **Checkpoint team state** (auto-save every 30 seconds)

**Execution exit criteria**:
- All teammates report COMPLETE
- All code passes local dx.sh
- All async messages resolved

---

### Phase 3: Consolidation (Lead Final Integration)

Lead pulls all changes together, runs full build pipeline, and commits atomically.

**Consolidation steps**:

1. **Load checkpoint state** (all teammate code)
2. **Merge changes** (ideally no conflicts if team was well-formed)
3. **Run dx.sh all** (full: compile + test + static analysis + release)
4. **If GREEN**: Atomic git commit with team context
5. **If RED**: Identify failing teammate, message for fix, re-run (max 2 iterations)

**Consolidation exit criteria**:
- `dx.sh all` exits 0 (all gates green)
- Single commit with all team changes
- Lineage preserved (show which teammates contributed)

---

## Anti-Patterns: When NOT to Use Teams

### Anti-Pattern 1: Single Quantum Work

**Problem**: One person, one task. Teams add coordination overhead with zero parallelism benefit.

**Cost**: 3-5× higher

**Example**: "Add logging to YWorkItem"
- Team approach: 1 engineer × 200K context × 3-5× cost
- Single session approach: 1 engineer × 200K context × 1× cost

**Solution**: Use single session. Save the cost.

---

### Anti-Pattern 2: No Async Messaging

**Problem**: Teammates work independently but never message each other. This is just "run them in parallel," not teams.

**Cost**: All team cost, zero collaboration benefit

**Example**: Engineer A researches XSD, Engineer B researches XML parsing. Neither messages the other. Both could have done it alone.

**Solution**: Use subagents (independent verification, cheaper) or combine into single task.

---

### Anti-Pattern 3: >5 Teammates

**Problem**: Coordination overhead exceeds parallelism benefit

**Messaging matrix**: N teammates = ~N² potential messages. At N=6, that's 36 message pairs. Too much.

**Solution**: Split into 2-3 teams, 2-5 teammates each, across sequential phases.

---

### Anti-Pattern 4: <30 Minutes Per Task

**Problem**: Team setup, context load, first async message all take ~15-20 minutes. If task is <30 min, overhead dominates.

**Cost**: ~$0.5-1C per team, task is ~10 minutes of actual work

**Solution**: Combine tasks into single session.

---

### Anti-Pattern 5: Same-File Edits

**Problem**: No MVCC (Multi-Version Concurrency Control). Teammates editing the same file → conflicts, overwrites, lost work.

**Cost**: Hours of merge debugging

**Example**: Both teammates edit YNetRunner.java
- Teammate A edits lines 100-120 (state machine)
- Teammate B edits lines 150-170 (locking)
- Merge conflict: Git doesn't know which change to keep

**Solution**:
1. Refactor ownership (split file into two: YNetRunnerStateMachine, YNetRunnerLocks)
2. Reduce team size (let one person own the file)
3. Use sequential phases (A's changes merge, B starts fresh)

---

### Anti-Pattern 6: Tightly Coupled Logic

**Problem**: Teammates block each other because changes have hidden dependencies

**Example**:
- Engineer A: Refactor guard evaluation logic
- Engineer B: Add new guard syntax

Engineer B can't start until A's changes are done. Not parallelizable.

**Solution**:
- Reorder tasks (new syntax first, then refactor)
- Or use sequential phases
- Or combine into single session

---

## Team Timeouts & Error Recovery

### Timeout Hierarchy

| Timeout | Duration | Meaning | Action |
|---------|----------|---------|--------|
| **Heartbeat fresh** | <10 min | Teammate online, can message | Continue |
| **Heartbeat stale** | 10-30 min | Teammate idle, wake up | Send message, wait 30s |
| **Heartbeat dead** | >30 min | Teammate offline, assume crash | Reassign task |
| **ZOMBIE mode** (lead) | >5 min | Lead offline, continue locally | Auto-checkpoint every 30s |
| **Critical message** | 15 min no reply | Urgent decision needed | Resend [URGENT], wait 5 min → crash |
| **Task timeout** | 2+ hours | Task taking too long | Extend +1h or split & reassign |

### Circular Dependency Handling

**Problem**: Task A depends on B, B depends on A. Can't start either.

**Example**:
- Engineer A (Schema): Needs to know if engine supports <sla> nesting
- Engineer B (Engine): Needs schema to know structure of <sla>

**Solution**: Lead breaks tie via ordering
1. Schema owner makes initial decision: "<sla> is NOT nestable (cardinality 1)"
2. Engine developer implements assuming non-nesting
3. If assumption wrong, go back. But 80% of time this works.

**Why teams lose on circular deps**: Recovery time is linear in team size. Chains of >3 teams → escalate, don't attempt recovery.

---

### Lead's Consolidation Fails

**Scenario**: All teammates GREEN locally, but `dx.sh all` fails at lead's consolidation.

**Cause**: Missing contract, incompatible signatures, transitive mismatch

**Recovery** (one attempt only):

1. Identify failing module
2. Diagnose root cause:
   - Missing API? → Message responsible teammate: "Add X method"
   - Type mismatch? → Message responsible teammate: "Change return type to Y"
3. Teammate fixes locally (10-min deadline)
4. Lead re-runs dx.sh all

**If still fails**: Rollback to last good commit, post-mortem, retry with different approach.

---

### Teammate Crash

**Detection**: No heartbeat >5 min, context maxed

**Recovery options**:

| Completion % | Action |
|---|---|
| >80% | New teammate loads checkpoint, continues |
| 50-80% | New teammate inherits checkpoint, re-runs from milestone |
| <50% | New teammate or lead finishes from scratch |

---

## When to Choose Alternatives

### Single Session (Cheapest)
- One quantum, one person
- Cost: ~$1C
- Use when: Task is focused, <2 hours, no competing hypotheses

### Subagents (Independent Verification)
- Same team size as teams (2-5 agents), but **no messaging**
- Each agent produces independent artifact, lead synthesizes
- Cost: ~$1C + summaries
- Use when: You need multiple perspectives but they don't interact (e.g., 3 architects independently design module X, then lead picks best design)

### Sequential Phases (Lowest Risk)
- Team 1 completes, commits, Team 2 starts
- Cost: ~$1C per phase (cheaper than parallel)
- Use when: Circular dependencies exist or coordination is too tight

### Git Worktrees (Maximum Parallelism, Manual)
- Engineers manually create parallel branches with git worktree
- Merge manually
- Cost: ~$1C per branch + merge overhead
- Use when: Team feature unavailable or repo doesn't support teams

---

## Cost-Benefit Decision Tree

```
Is this a single quantum?
├─ YES → Use SINGLE SESSION ($C)
└─ NO → Do you have 2-5 orthogonal quantums?
    ├─ NO → Combine into single task or sequential phases
    └─ YES → Will teammates message/iterate?
        ├─ NO → Use SUBAGENTS ($C + summaries)
        └─ YES → Will each task take ≥30 min?
            ├─ NO → Combine tasks into single session
            └─ YES → Are there file conflicts?
                ├─ YES → Refactor ownership or reduce team size
                └─ NO → Use TEAM (τ) — 3-5× cost for 2-3× speed
```

---

## Practical Tips for Team Leads

### 1. Start Async Early
Send first message at 15-minute mark. Don't wait until end of task.

### 2. Be Specific in Messages
Bad: "Schema done, implement it"
Good: "Schema defines SlaState(ACTIVE, BREACHED, COMPLETE). Engine should evaluate deadline every 60s. Throw SlaBreachedException if deadline exceeded."

### 3. Monitor Heartbeats
If a teammate hasn't reported in 30 min, message them. Catch problems early.

### 4. Plan Consolidation Upfront
Decide before team starts: Will there be conflicts? Who owns which files? Where does lead merge?

### 5. Checkpoint Aggressively
Team state persists. If lead goes offline, new lead can resume. But only if state is current (auto-saved every 30s).

### 6. Give One Iteration for Recovery
If consolidation fails, give responsible teammate one 10-minute window to fix. If still fails, rollback.

---

## Metrics & Tuning

| Metric | Good | Bad | Tuning |
|--------|------|-----|--------|
| **Team utilization** | >80% | <50% | Reduce team size or expand scope |
| **Messages per teammate** | 3-5 | >10 or <1 | 3-5 means good async rhythm; <1 means no collaboration |
| **Time to first message** | <15 min | >30 min | Start async earlier |
| **Iteration rounds** | 1-2 | >3 | Scope too tight or dependencies underestimated |
| **Consolidation time** | <30 min | >1 hour | File conflicts or missing contracts |

---

## See Also

- [TEAMS-GUIDE.md](../../.claude/rules/TEAMS-GUIDE.md) — Detailed protocols (error recovery, session resumption, messaging)
- [Team Decision Framework](../../.claude/rules/team-decision-framework.md) — Quick reference for formation checklist
- [TEAMS-QUICK-REF.md](../../.claude/rules/TEAMS-QUICK-REF.md) — Timeout values, message templates
- [Chatman Equation](chatman-equation.md) — Agent quality model that justifies teams
