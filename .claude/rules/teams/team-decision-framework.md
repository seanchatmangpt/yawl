# Team Decision Framework — When to Use τ (Teams)

**Default behavior: Multi-quantum tasks → Teams (τ)**

This rule file makes teams the default for suitable work. You only override if you have explicit reasons to use single session or subagents.

## Quick Decision Tree

```
Does your task have N ∈ {2,3,4,5} orthogonal quantums?
├─ YES + teammates need to message/iterate?
│  └─ → Use TEAM (τ)  [Default: collaborative investigation]
├─ YES + quantums are purely independent?
│  └─ → Use SUBAGENTS (μ)  [Report-only work]
├─ YES + team size > 5?
│  └─ → SPLIT into 2-3 phases, max 5 per team
├─ NO (single quantum)?
│  └─ → Use SINGLE SESSION  [Fast, cheap]
└─ UNSURE?
   └─ → Run hook: bash .claude/hooks/team-recommendation.sh "your task"
```

## When Teams Are DEFAULT ✓

| Signal | Reason | Example |
|--------|--------|---------|
| **Competing hypotheses** | Investigation converges faster with parallel theories | Deadlock debugging: state-mgmt vs race vs guard logic |
| **Cross-layer changes** | Frontend + backend + tests = 3 independent experts | Schema change affects engine + integration + tests |
| **Code review by concern** | Security + perf + coverage reviewers work in parallel | PR review: security reviewer finds SQL injection, perf reviewer optimizes |
| **Research + implement** | One teammate researches while others implement | Research MCP spec, integrator builds endpoint, tester writes tests |
| **Schema + validation** | Schema team writes spec, impl team uses it | Schema team defines workflow type, engine team implements type-specific logic |
| **Shared discovery phase** | Teammates share findings before consolidating | "My module calls Y.Z()—do yours?" enables smarter refactoring |

**Cost-benefit**: Team tokens ~3-5× higher, but ROI positive when teammates *iterate and message* (not just "report back").

## When Teams Are WRONG ✗

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| **Single quantum** | Team costs 3-5C for work that's $C sequential | Use single session |
| **No inter-team messaging** | Teammates never talk; just parallel isolation | Use subagents (cheaper) |
| **>5 teammates** | Coordination overhead exceeds benefit; leads cost 5× base | Split into 2-3 sequential phases, max 5 per team |
| **<30 min per teammate task** | Team formation overhead (messaging, setup) > task time | Combine into single session or subagent |
| **Same-file edits** | Teammates overwrite each other (no MVCC) | Reduce team size or refactor so each teammate owns files |
| **Tightly coupled logic** | Teammates blocked waiting on each other | Reorder tasks or add dependencies explicitly |

## YAWL-Specific Team Patterns

### Pattern 1: Engine Investigation (3 engineers)

**Scenario**: YNetRunner deadlock—unclear root cause.

```
Lead: "Create team to debug deadlock. Spawn 3 engineers to investigate:"
  - Engineer A: State machine correctness (YWorkItem, task completion)
  - Engineer B: Concurrency (executor.advance(), synchronization)
  - Engineer C: Guard logic (transition guards, false deadlocks)

Teammates:
  - A: Tests YWorkItem state transitions, checks for stuck states
  - B: Profiles executor, checks for race conditions
  - C: Traces guard evaluation, finds false-positive deadlock signals

Message flow:
  - A: "Task completion state is correct. Proceeding to executor."
  - B: "Found race in advance() line 427. Testing with synchronized fix."
  - C: "Guard returns false unexpectedly. Checking evaluation logic."

Lead: Reviews mailbox, synthesizes: "Guard + race combo. C's guard fix + B's sync fix needed."
```

**Cost**: ~$3-4C | **ROI**: Converges 2-3× faster than sequential investigation.

### Pattern 2: Schema + Implementation (2 engineers)

**Scenario**: Add new workflow attribute.

```
Lead: "Modify workflow type to support SLA tracking. 2 engineers:"
  - Engineer A: Schema team (add SLA element, validation)
  - Engineer B: Engine team (engine respects SLA, enforce in execution)

Circuit:
  - A: Adds <sla> element to workflow type XSD, adds minOccurs constraints
     Messages B: "New SLA element ready at line 123, uses isodate format"
  - B: Implements YWorkItem.getSLA(), adds SLA enforcement in state machine
     Runs local test, messages A: "Confirmed SLA works with new schema element"

Lead: Consolidates both changes, runs full DX, commits.
```

**Cost**: ~$2-3C | **ROI**: Avoids back-and-forth iterations.

### Pattern 3: Code Review by Concern (3 reviewers)

**Scenario**: Review PR #142 for security, performance, test coverage.

```
Lead: "Review PR #142 using 3 independent lenses:"
  - Reviewer A: Security (auth, crypto, input validation)
  - Reviewer B: Performance (allocations, loops, I/O)
  - Reviewer C: Testing (coverage, edge cases, assertions)

Each reviewer:
  - Reads code independently
  - Identifies issues in their domain
  - Messages findings before marking task done

Message flow:
  - A: "Found SQL injection risk at line 456. PreparedStatement needed."
  - B: "Loop at line 234 allocates on every iteration. Use StringBuilder."
  - C: "Test missing for edge case: empty workflow. Coverage drops 5%."

Lead: Synthesizes all findings into review summary, categorizes by severity.
```

**Cost**: ~$2-3C | **ROI**: Catches cross-cutting issues that single reviewer misses.

## Avoiding Team Overuse

**Anti-pattern**: Summoning a team for every task.

**Prevention**:
- Teams default only for N ≥ 2 quantums
- Run `bash .claude/hooks/team-recommendation.sh` to validate first
- If hook says "use single session" → follow it
- Monitor STOP conditions for team size violations (>5, <30 min tasks)

**Enforcement**: STOP condition added to CLAUDE.md gates.

## Team Alternatives

| Alternative | When to Use | Cost |
|------------|-----------|------|
| **Single Session (Ψ→Λ→H→Q→Ω)** | Single quantum, tight feedback loop | ~$C |
| **Subagents (μ)** | N independent verification tasks, no messaging | ~$C + summaries |
| **Git Worktrees** | Manual parallel sessions (you manage coordination) | ~$C × N, high overhead |

## Implementation Checklist

Before summoning a team:

- [ ] Run `bash .claude/hooks/team-recommendation.sh "task"` → recommends team?
- [ ] N teammates ∈ {2,3,4,5}?
- [ ] Each teammate scoped ≥30 min?
- [ ] No file conflicts? (`Ψ.facts/shared-src.json` clean)
- [ ] Teammates will message/iterate (not just report)?
- [ ] Facts current? (`bash scripts/observatory/observatory.sh`)
- [ ] Team feature enabled? (`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`)

## Metrics & Tuning

**Track team ROI**:

| Metric | Target | Calculation |
|--------|--------|-------------|
| **Teammate utilization** | >80% | (active_tasks / blocked_tasks) |
| **Message per teammate** | >2 | (total_messages / teammate_count) |
| **Iteration cycles** | 1-2 | (message_rounds before consolidation) |
| **Cost/quality ratio** | >1.5 | (quality_gain / 3C cost) |

**Tune team size**:
- Too many messages? → Reduce to 2-3 teammates, increase task scope
- Teammates idle? → Add more tasks or combine into single session
- All teammates green first try? → Scope was too simple, use single session next time

---

## Reference

- CLAUDE.md: "Teams" section for architecture
- Hook: `.claude/hooks/team-recommendation.sh` for automation
- Quantum selection: CLAUDE.md "GODSPEED Quantum Selection"
