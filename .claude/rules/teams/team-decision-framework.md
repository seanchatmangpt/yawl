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

### Pattern 4: Observability & Monitoring (2 engineers)

**Scenario**: Add logging, metrics, and distributed tracing to YAWL engine execution.

```
Lead: "Instrument YNetRunner for production observability. 2 engineers:"
  - Engineer A: Observability engineer (logging, metrics, tracing)
  - Engineer B: Engine engineer (integration points, instrumentation)

Circuit:
  - A: Designs observability schema (prometheus metrics, log structure, trace context)
     Adds metrics collectors for task queue, execution time, resource usage
     Messages B: "Metrics schema ready: engine_task_duration_ms, queue_depth, executor_threads"
  - B: Integrates metrics into YNetRunner.executeTask(), YWorkItem state transitions
     Adds trace context propagation to async operations
     Runs local benchmarks, messages A: "Verified 2% overhead. Metrics flowing correctly."

Message flow:
  - A: "Defined 8 core metrics. Schema at observability/schemas/engine-metrics.json"
  - B: "Integrated all 8 metrics. Engine fires events correctly in tests."
  - A: "Confirmed Prometheus scrape targets working in integration test."

Lead: Consolidates both changes, runs full DX, validates metrics output in test suite.
```

**Modules**: yawl/observability/**, yawl/engine/**
**Cost**: ~$2-3C | **ROI**: Enables production debugging without code changes.

### Pattern 5: Performance Optimization (2-3 engineers)

**Scenario**: Improve throughput and latency in a critical YAWL subsystem.

```
Lead: "Optimize task queue with lock-free data structures. 3 engineers:"
  - Engineer A: Performance engineer (profiling, benchmarking)
  - Engineer B: Engine engineer (task queue, YWorkItem allocation)
  - Engineer C: Tester (regression tests, concurrency verification)

Circuit:
  - A: Profiles current task queue, identifies contention at queue.take()
     Runs JMH benchmarks, establishes baseline: 50K tasks/sec, 2ms p99 latency
     Messages B & C: "Bottleneck: synchronized queue. Lock-free list/queue candidates identified."
  - B: Replaces java.util.Queue with com.lmax.disruptor.RingBuffer
     Implements YWorkItem pooling to reduce GC pressure
     Runs local tests, messages A & C: "Ready for benchmarking. GC time down 40%."
  - C: Runs full concurrency test suite (100K+ task transitions)
     Validates no lost tasks, no state corruption
     Messages A: "All tests pass. No regression in correctness."

Message flow:
  - A: "Lock-free baseline: 150K tasks/sec, 0.5ms p99. 3× improvement target reached."
  - B: "Object pooling + ring buffer integrated. Memory stable at 500MB."
  - C: "Stress tests (10 threads, 100K tasks each) pass. No deadlocks."

Lead: Consolidates changes, runs full DX + perf validation, commits with benchmark results.
```

**Modules**: yawl/engine/**, yawl/stateless/** (task allocation), src/test/**
**Cost**: ~$3-4C | **ROI**: 3× throughput gain justifies optimization investment.

### Pattern 6: Autonomous Agent Integration (2-3 engineers)

**Scenario**: Add new MCP endpoint or A2A protocol handler for case monitoring.

```
Lead: "Integrate autonomous agents for case monitoring. 3 engineers:"
  - Engineer A: Integrator (MCP/A2A protocol, endpoint design)
  - Engineer B: Engine engineer (YStatelessEngine, case monitoring API)
  - Engineer C: Tester (protocol compliance, integration tests)

Circuit:
  - A: Designs new MCP endpoint: tools/cases/monitor, tools/cases/subscribe
     Defines JSON schema for case state events
     Messages B: "MCP schema ready. Expecting CaseStateUpdate events from engine."
  - B: Extends YStatelessEngine.getCaseMonitor() to emit case state changes
     Implements CaseStateEvent publisher interface
     Integrates with A's MCP endpoint registration
     Messages A & C: "Case monitor events flowing. Ready for protocol binding."
  - C: Writes protocol compliance tests (MCP message format, ordering, error cases)
     Tests autonomous agent can receive and act on case updates
     Messages A: "Protocol tests pass. Agent receives updates within 50ms."

Message flow:
  - A: "MCP schema v1 published. Endpoint /yawl/cases/monitor listening."
  - B: "YStatelessEngine publishes CaseStateUpdate on every state change."
  - C: "Protocol verification: 100 case updates, 0 delivery failures, 0 corruption."
  - A: "Autonomous agent successfully subscribed and responded to case updates."

Lead: Consolidates changes, validates MCP endpoint in standalone test, commits.
```

**Modules**: yawl/integration/autonomous/**, yawl/engine/**, yawl/stateless/**, src/test/**
**Cost**: ~$3-4C | **ROI**: Enables autonomous agents to monitor and react to cases in real time.

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
