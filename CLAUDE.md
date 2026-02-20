# YAWL v6.0.0 | A = μ(O)

O = {engine, elements, stateless, integration, schema, test}
Σ = Java25 + Maven + JUnit + XML/XSD | Λ = compile ≺ test ≺ validate ≺ deploy
**Yet Another Workflow Language** — Enterprise BPM/Workflow on rigorous Petri net semantics.

## ⊤ (Priority Stack) — RESOLVE CONFLICTS TOP-DOWN

1. **H (Guards)** — blocked patterns are non-negotiable
2. **Q (Invariants)** — real code or UnsupportedOperationException
3. **Ψ (Observatory)** — read facts before exploring
4. **Λ (Build)** — compile ≺ test ≺ validate before commit
5. **Ω (Git)** — zero force, specific files, one logical change

If uncertain which rule applies → **stop and re-read this stack**.

---

## ⚡ GODSPEED!!! — Maximum Forward Velocity (Zero Invariant Breakage)

**Flow**: Ψ → Λ → H → Q → Ω

### PreToolUse GODSPEED ✈️

Before ANY tool call (Bash|Task|Read|Glob|Grep|Write|Edit):

```
1. Ψ gate:    [ ] Stale facts? Run observatory.sh | [ ] Pick 1 quantum (module + path)?
2. Fanout:    [ ] N independent quantums? (N ≤ 8) | [ ] Zero file overlap? (check shared-src.json)
3. Λ context: [ ] Which rule file activates? | [ ] DX loop ready?
4. H filter:  [ ] Will hook block this? (search H = {TODO,mock,stub,fake,empty,lie})
5. Q check:   [ ] Real impl ∨ throw? | [ ] No silent fallback?
6. Ω guard:   [ ] emit channel? | [ ] Session ID set? | [ ] Specific files, not git add .
```

**Decision Tree**:
- Ψ facts stale? → `bash scripts/observatory/observatory.sh` + re-read fact file
- Multiple orthogonal quantums? → Spawn agents (Fanout) instead of sequential work
- Cannot pick 1 quantum? → STOP. Read `Ψ.facts/modules.json` + `gates.json`
- H blocks? → Fix violation for real (don't work around hook)
- Ω uncertain? → Ask user before Write/Edit outside emit
- **Otherwise**: Proceed. Hook is autopilot. 🎯

**Information Density Rule**: Link to facts, don't repeat. 1 fact file ≈ 50 tokens. Grep ≈ 5000 tokens. **100× compression.**

### PostToolUse GODSPEED ✈️

After Write|Edit:
- Hook ran? Green → commit
- Hook blocked? Read error. Fix code for real. Re-stage. New commit (never amend).

---

## GODSPEED Quantum Selection — Pick ONE Axis

**Multi-axis changes = drift.** Choose exactly one:

| Quantum | Example | Facts Check |
|---------|---------|-------------|
| **Toolchain** (Java25/Maven/JUnit) | Upgrade JDK version | `maven-hazards.json` |
| **Dependency** (one family) | Add com.google.* | `deps-conflicts.json` |
| **Schema** (XSD path) | Modify workflow type | `gates.json` |
| **Engine semantic** (one pattern) | Fix task completion | `modules.json` → find module |
| **MCP/A2A** (one endpoint) | Add event handler | `modules.json` → find module |
| **Resourcing** (allocation logic) | Change workqueue | `modules.json` → find module |

**Flow**: Pick quantum → read 1 fact file → read rule file → DX loop → commit.

---

## Λ (Build) — ALWAYS USE DX

**One command per context**:

```bash
bash scripts/dx.sh compile               # Fastest (compile only, changed modules)
bash scripts/dx.sh -pl yawl-engine       # One module (after quantum picked)
bash scripts/dx.sh all                   # Pre-commit gate (all modules)
mvn clean verify -P analysis             # Static analysis (SpotBugs, PMD)
```

**Rule**: No commit until `dx.sh all` is green. See `.claude/rules/build/dx-workflow.md`.

## H (Guards) — ENFORCED BY HOOKS

**H** = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}

Hook `.claude/hooks/hyper-validate.sh` checks 14 anti-patterns on Write|Edit → **exit 2 if H ∩ content ≠ ∅**

**ONLY**: ✅ Real impl | ✅ UnsupportedOperationException | ❌ TODO/mock/stub/fallback

See `.claude/HYPER_STANDARDS.md` for detection regex + enforcement protocol.

## Q (Invariants) — NO NEGOTIATION

**Q** = {real ∨ throw, ¬mock, ¬stub, ¬fallback, ¬lie}

| Invariant | Check | Fix |
|-----------|-------|-----|
| real_impl ∨ throw | Does method do real work or throw? | Implement real logic or `throw new UnsupportedOperationException()` |
| ¬mock | Empty/mock objects in code? | Delete. If needed for tests, use JUnit @Mock. |
| ¬silent_fallback | Exceptions caught without propagation? | Let exception flow or catch + throw real logic. |
| ¬lie | Does code match docs + signature? | Update code or docs to align. |

## Ψ (Observatory) — Observe ≺ Act

**AXIOM**: Context finite. Codebase infinite. **Facts only, not vibes.**

### Fact Files (Information Density Table)

| Fact | Tokens | Use Case |
|------|--------|----------|
| `modules.json` | ~50 | "Which module does X belong to?" |
| `gates.json` | ~50 | "What test gates exist for module X?" |
| `deps-conflicts.json` | ~50 | "Can I add dependency Y?" |
| `reactor.json` | ~50 | "Build order? Parallel safe?" |
| `shared-src.json` | ~50 | "Is code shared across modules?" |
| `tests.json` | ~50 | "Coverage? Test count per module?" |
| `dual-family.json` | ~50 | "Type family aliasing?" |
| `duplicates.json` | ~50 | "Duplicate code patterns?" |
| `maven-hazards.json` | ~50 | "Plugin conflicts? Version skew?" |

**vs. Grep alternative**: ~5000 tokens for same answer. **100× worse.**

### Refresh When Uncertain

```bash
bash scripts/observatory/observatory.sh  # Sync facts with codebase
```

**Ψ.verify**: `receipts/observatory.json` → SHA256 hashes. Mismatch? Stale facts. Re-run.

**If >3 files needed → build instrument** (`.claude/OBSERVATORY.md`).

## Γ (Architecture)

| Domain | Key Types |
|--------|-----------|
| Engine | YEngine, YNetRunner, YWorkItem, YSpecification |
| Interface | A:design, B:client, E:events, X:extended |
| Integration | MCP:zai-mcp, A2A:agent-to-agent |
| Stateless | YStatelessEngine, YCaseMonitor, YCaseImporter/Exporter |

**Entry Points**: `YEngine` (stateful) · `YStatelessEngine` (stateless) · `YSpecification` (defs) · `YawlMcpServer` (MCP) · `YawlA2AServer` (A2A)
**Docs**: All 89 packages have `package-info.java` — read these first.

## μ(O) → A (Subagents) vs τ (Teams)

**μ** = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O) | See `.claude/agents/` for specifications.

**Subagents (within single session)**:
- Task(a₁,...,aₙ) ∈ single_message ∧ max=5 agents
- Each subagent: isolated Task execution, results summarized back to lead
- Best for: quick verification, code review, report-only tasks
- Cost: ~$C + summaries

**Teams (τ, separate sessions, experimental)**:
- τ(tm₁,...,tm_N) ∈ {2..5} teammates ∧ CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1
- Each teammate: own context window, direct messaging, shared task list
- Best for: collaborative investigation, cross-layer changes, competing hypotheses
- Cost: ~$3-5C
- See "Teams" section above for full architecture

**Choose subagents if**: work is parallelizable but doesn't need inter-task communication.
**Choose teams if**: teammates need to share findings, iterate, challenge each other.
**Choose single session if**: work is inherently sequential or scope < 30 min.

Keep single sessions under 70% context usage. Teams manage context per teammate (200K each).

---

## ⚡ GODSPEED!!! Teams — Collaborative Agent Coherence

**τ (Team)** = lead session + N teammates ∈ {2..5} | Each teammate: Ψ→Λ→H→Q→Ω on ONE quantum. Coordinate via task list + direct messaging. **Experimental feature** (requires `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`).

**When to team**: Parallel exploration adds value. **Best for**: research + review, competing hypotheses, cross-layer coordination (frontend + backend + schema). **Not for**: sequential tasks, same-file edits, small scopes (N=1). Subagents are better for "report-only" work; teams are for *collaborative* investigation.

**Information Density**: τ costs ~3-5× single session tokens. Use only when team ROI > cost. See facts `modules.json` + `reactor.json` to validate team-ability.

### PreTeam Checklist

Before summoning a team:

```
[ ] Facts fresh? Run bash scripts/observatory/observatory.sh
[ ] Verify Ψ.facts/reactor.json: build order + parallel safety?
[ ] Pick N quantums (N ∈ {2,3,4,5}, each orthogonal)
[ ] Zero file conflicts? Check Ψ.facts/shared-src.json (no teammate overlap)
[ ] Each quantum is self-contained ≥ 30 min scope? (avoid 5-min tasks)
[ ] Can teammates message about findings + iterate? (not pure isolation)
```

**Team Axiom**: Teammate coherence = shared task list + direct messaging. If teammates never communicate, use subagents instead. If all work is identical per-file, use single session.

### Team Structure — Lead + Teammates

```
τ = {
  lead:       main session (orchestration + synthesis)
  teammates:  [tm_1, tm_2, ..., tm_N] (each own context window)
  shared:     task_list + mailbox + facts (read-only)
  state:      tasks{pending, in_progress, completed} + dependencies
}
```

**Lead role**:
- Creates team, spawns teammates
- Defines initial tasks + dependencies
- Synthesizes findings from teammates
- Approves plans if required
- Never implements; delegates via task list

**Teammate role**:
- Claims or is assigned a task from shared list
- Executes Ψ→Λ→H→Q→Ω independently
- Messages other teammates to share findings
- Marks task complete when done
- Auto-idles when no more blocked tasks

**Messaging protocol**:
- `message {teammate_name}`: direct message to one
- `broadcast {message}`: all teammates (⚠️ scales linearly with team size)
- Auto-delivery: lead doesn't poll; messages arrive asynchronously
- Each message is logged in shared mailbox for lead review

### Teammate-to-Quantum Mapping

| Quantum Axis | Ideal Teammate | Task Example | YAWL Module |
|--------------|---|---|---|
| **Engine semantic** (1 pattern) | Engineer A | Fix task-completion state machine | yawl/engine/** |
| **Schema** (XSD path) | Engineer B | Modify workflow type definition | schema/** + exampleSpecs/** |
| **Integration** (MCP/A2A endpoint) | Integrator | Add event publisher endpoint | yawl/integration/** |
| **Resourcing** (allocation logic) | Engineer C | Implement resource pool draining | yawl/resourcing/** |
| **Security** (auth + crypto) | Reviewer | Add JWT validation hooks | yawl/authentication/** |
| **Test coverage** (1 module) | Tester | Write integration tests for engine | yawl/engine/**/src/test/** |
| **Stateless* (monitor/export) | Engineer D | Build case snapshot API | yawl/stateless/** |

**Constraint**: No two teammates touch same file. Use `Ψ.facts/shared-src.json` to verify. If overlap exists → reduce team size or split quantum differently.

### Team Execution Circuit (Teammate tm_i)

```
Ψ (Discovery): Read shared task_list
              find(task where status=pending ∧ all_dependencies_complete)
              claim task_i OR await lead assignment
              ↓
Λ (Local DX):  bash scripts/dx.sh -pl <module_i>  (isolated compile, no blocking)
              ↓ red?  → fix locally, re-run until green
              ↓ green?→ proceed
              ↓
H (Guard):    hook check (H ∩ content = ∅)
              ↓ blocked? → fix real violation, re-check
              ↓ clear?  → proceed
              ↓
Q (Invariant): real_impl ∨ throw ∧ ¬mock ∧ ¬lie
              ↓ failed? → fix invariant, reverify
              ↓ passed? → proceed
              ↓
Message (Info): tm_i broadcasts/messages key findings to teammates
               (before marking task complete)
               "Found deadlock in net-runner line 423. Proposing fix XYZ."
               ↓ teammates may reply with competing theory
               ↓ reconcile via direct message
               ↓
Ω (Commit):   Mark task complete in shared task_list
              (don't git-commit yet; lead will consolidate)
              ↓
Idle:         Wait for next unblocked task OR lead message
```

**Key governance**:
- Each teammate's Λ run is isolated (no blocking on other teammates)
- Messaging happens *before* task completion (collaboration point)
- No individual git commits; all files stay in emit channel
- Lead monitors via task_list + mailbox (async, no polling)

### PostTeam Consolidation (Lead Session)

After all teammates report tasks complete:

```
1. Ψ: Review shared mailbox + task_list
   ↓ Read all teammate findings + reconciliations
   ↓ Identify conflicts (if two teammates propose different fixes)

2. Q: Verify ∧(tm_i completed ∨ blocked_on_external)
   ↓ All ready? Proceed. Some blocked? Wait + nudge.

3. Λ: bash scripts/dx.sh all  (full compile gate, all modules)
   ↓ red?  → identify failing module_i
   ↓        → message tm_i: "DX failed in your module. Fix and re-run local Λ."
   ↓        → tm_i re-runs, confirms green, marks task re-complete
   ↓ green? → proceed

4. H: Final hook run on all teammate edits combined
   ↓ Hook blocks? → identify teammate + pattern → message fix request
   ↓ Clear? → proceed

5. Ω (Atomic Commit):
   git add <all emit files from all teammates>  (no overlaps verified in step 1)
   git commit -m "..."  (one logical change spanning N quantums)
   git push -u origin claude/team-<quantum-names>-<sessionId>
```

**Atomicity guarantee**: All teammates green Λ + all hook clear + lead Λ green = atomic push. Any red = rollback message to failing teammate.

### Team Patterns & Use Cases

| Pattern | Quantums | Teammates | YAWL Example |
|---------|----------|-----------|---|
| **Engine investigation** | Engine semantic (3 sub-patterns) | 3 engineers | Fix net-runner deadlock (hyp1: state mgt, hyp2: race in executor, hyp3: transition guard logic) |
| **Schema + impl** | Schema def + Engine use of schema | 2 engineers | Modify workflow type (schema in yawl/elements, usage in yawl/engine) |
| **Cross-layer** | API + Engine + Tests | 3 (engineer + integrator + tester) | Add case monitoring endpoint (API: yawl/integration, Engine: yawl/stateless, Tests: test/**) |
| **Security audit** | Auth layer + Crypto + Integration | 3 (reviewer + engineer + integrator) | Add TLS cert validation (auth/**, crypto/**, MCP/A2A/**) |
| **Code review** | Review by concern (security + perf + coverage) | 3 reviewers | PR #142: each reviews different lens, message findings |

**Rule**: Each teammate owns ≥1 file, ≤2 modules. Overlap = sequential fallback.

### Team Communication Patterns

**When teammate finds issue**:
```
Teammate A (engine): "Found null dereference in YNetRunner.checkGuards() line 427"
Teammate B (schema): "Our schema allows missing guards. Should we forbid at schema level?"
Teammate A: "Yes—let's add minOccurs=1 to guard element in schema"
Teammate B: (adds constraint, runs local Λ, messages back)
Lead: (reads mailbox, approves trade-off, messages both: "proceed")
```

**When teammates disagree**:
```
Teammate A: "Root cause is race in executor.advance()"
Teammate C: "I traced 50 runs; always fails in state-persist layer"
Teammate A: "Let me run under synchronized block + report back"
(A modifies, re-runs local tests, messages: "My hypothesis confirmed by synchronized test")
(C validates in their module, agrees, both mark tasks complete)
Lead: synthesizes both findings into commit message
```

**Message types**:
- Info: "Found X at line Y" (sharing observation)
- Question: "Should we constraint schema?" (asking teammate opinion)
- Challenge: "I think your fix is incomplete because..." (hypothetical clash)
- Resolution: "Confirmed—marking task done" (collaboration resolved)

### Team Lifecycle Hooks

| Hook | Trigger | Lead Action |
|------|---------|---|
| `TeammateIdle` | Teammate finished a task, no more pending blocked tasks | Lead can assign new task OR shut down teammate |
| `TaskCompleted` | Task being marked complete | Lead can reject + send feedback + force rework |
| `TeammateShutdown` | Teammate asks permission to exit | Lead approves OR rejects with more work |

Use hooks to enforce "wait for teammates to finish" or "verify findings before closing task".

### Team vs Subagents vs Single Session

| Dimension | Single Session | Subagents | Teams (τ) |
|-----------|---|---|---|
| **Parallelism** | None | Limited (within session context) | Full (separate contexts) |
| **Communication** | N/A | Report-only to lead | Direct teammate-to-teammate |
| **Context isolation** | N/A | Own context, auto-summarized | Own context, full history in mailbox |
| **Cost** | ~$C | ~$C + summaries | ~$3-5C per team |
| **Best for** | Single quantum, tight loop | Quick verification tasks | Investigation + review + cross-layer |
| **Coordination overhead** | None | Low (report back) | Medium (messaging) |

**Decision tree**:
- Single quantum, fast feedback → single session
- N independent verification tasks → subagents (report-only)
- N collaborative investigations, finding interaction → teams (messaging)

---

## Π (Skills)

**Π** = {/yawl-build, /yawl-test, /yawl-validate, /yawl-deploy, /yawl-review, /yawl-integrate, /yawl-spec, /yawl-pattern}
Invoke with `/skill-name` — see `.claude/skills/`.

## Ω (Git) — Zero Force Policy

```
bash scripts/dx.sh all → git add <files> → commit with session URL → git push -u origin claude/<desc>-<sessionId>
```

- **NEVER** `--force` or `--force-with-lease` unless explicitly instructed
- **NEVER** amend pushed commits — create new commit instead
- Stage specific files (`git add <files>`, never `git add .`)
- One logical change per commit

## Channels

**emit**: {src/, test/, schema/, .claude/} — modify freely
**⊗**: {root, docs/, *.md} — ask before modifying

## R (Rules) — AUTO-ACTIVATE BY PATH

17 rule files in `.claude/rules/` — load on first file touch in scope. **Never duplicate here.**

### Quantum → Rule Mapping (Quick Reference)

| Quantum | Rule File | Path Pattern |
|---------|-----------|--------------|
| **Toolchain** | `build/dx-workflow.md` | pom.xml, scripts/, .mvn/ |
| **Toolchain** | `build/maven-modules.md` | pom.xml, .mvn/ |
| **Dependency** | `config/static-analysis.md` | checkstyle.xml, pmd, spotbugs, .github/ |
| **Schema** | `schema/xsd-validation.md` | schema/**, exampleSpecs/**, *.xsd |
| **Engine semantic** | `engine/workflow-patterns.md` | yawl/engine/**, yawl/stateless/** |
| **Engine semantic** | `engine/interfaces.md` | yawl/engine/interfac*/** |
| **Engine semantic** | `engine/worklet-service.md` | yawl/worklet/** |
| **MCP/A2A** | `integration/mcp-a2a-conventions.md` | yawl/integration/** |
| **MCP/A2A** | `integration/autonomous-agents.md` | yawl/integration/autonomous/** |
| **Resourcing** | `resourcing/resource-allocation.md` | yawl/resourcing/** |
| **Any** | `java25/modern-java.md` | **/*.java |
| **Any** | `elements/domain-model.md` | yawl/elements/** |
| **Any** | `observability/monitoring-patterns.md` | yawl/observability/**, scripts/observatory/ |
| **Any** | `scripts/shell-conventions.md` | scripts/**, .claude/hooks/**, *.sh |
| **Any** | `security/crypto-and-tls.md` | yawl/authentication/**, Dockerfile* |
| **Any** | `testing/chicago-tdd.md` | **/src/test/**, test/** |
| **Any** | `docker/container-conventions.md` | Dockerfile*, docker-compose*, kubernetes/ |

**Procedure**: Quantum picked → identify files → load matching rule file(s) → proceed.

---

## GODSPEED!!! FLOW — Complete Circuit

```
Ψ: facts fresh? → pick 1 quantum (module + axis)
   ↓ stale?     → bash scripts/observatory/observatory.sh
   ↓ uncertain? → read Ψ.facts/modules.json + gates.json

Λ: bash scripts/dx.sh compile    (fastest feedback loop)
   ↓ red?       → fix, bash scripts/dx.sh -pl <module>
   ↓ green?     → proceed to H

H: hook will block?  (search H ∩ content)
   ↓ yes?       → implement real logic or throw UnsupportedOperationException
   ↓ no?        → proceed to Q

Q: real_impl ∨ throw ∧ ¬mock ∧ ¬lie?
   ↓ no?        → fix invariant violation
   ↓ yes?       → proceed to Ω

Ω: git add <specific-files>      (never git add .)
   git commit -m "..."            (one logical change)
   git push -u origin claude/<desc>-<sessionId>

Σ: drift(A) → 0 | Compile green ≺ Test green ≺ Validate green ≺ Deploy
```

**Key**: Each phase is a gate. No skips. Hook is autopilot. ✈️

---

## Deep References

- `.claude/HYPER_STANDARDS.md` — guard patterns, detection regex, enforcement protocol
- `.claude/OBSERVATORY.md` — instrument-building guide, fact schema
- `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` — 8 architecture patterns

## STOP Conditions — HALT AND RE-ANCHOR

**STOP** iff (if and only if) any of these:

| Condition | Action | Gate |
|-----------|--------|------|
| Cannot state which module → code belongs? | Read `Ψ.facts/modules.json` | Ψ |
| Exploring >3 files for 1 answer? | Run `bash scripts/observatory/observatory.sh` | Ψ |
| Hook blocked Write\|Edit? | Fix violation for real. Don't work around. | H |
| Unsure file in emit vs ⊗? | Ask user before touching. | Ω |
| Context usage >70%? | Checkpoint + summarize. Batch remaining. | Meta |
| Tempted "for now" / "later"? | Throw UnsupportedOperationException. | Q |

**Breaking any STOP condition = drift ↑. GODSPEED requires all gates green.**

## Receipt — GODSPEED Enforcement

**A** = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0

**Automation**:
- PreToolUse (Ψ→Λ→H→Q→Ω checklist) — keeps session aligned
- Teams (τ with N∈{2..5} teammates, coordinate via task list + messaging) — collaborative scaling with shared investigation
- PostToolUse (hook validation) — enforces H at write time
- Stop conditions — re-anchor if uncertain
- Rules (path-scoped) — context-aware governance
- Facts (observable only) — 100× token compression

**Result**: Zero configuration drift. Single-session, subagent, and team scaling. Compile ≺ Test ≺ Validate ≺ Deploy. ✈️⚡
