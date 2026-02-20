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

## Λ.net (Network) — Maven Proxy in Claude Code Web

**AUTO-DETECTION**: SessionStart hook detects egress proxy and activates Maven proxy workaround.

### Web Environment (Claude Code Remote)

When `CLAUDE_CODE_REMOTE=true`, SessionStart:
1. Detects egress proxy via `https_proxy` environment variable
2. Starts local Maven proxy bridge (`python3 maven-proxy-v2.py`)
3. Configures Maven settings.xml to use local proxy
4. Reports network status in environment summary

**Network Bridge**:
```
Maven Client (on 127.0.0.1)
  ↓
Local Maven Proxy (127.0.0.1:3128)
  ↓
Egress Proxy (21.0.0.111:15004, JWT auth)
  ↓
Maven Central (repo.maven.apache.org)
```

**Proxy Scripts** (auto-selected):
- `maven-proxy-v2.py` (preferred: improved error handling)
- `maven-proxy.py` (fallback: simple CONNECT tunnel)

**Result**: Transparent HTTPS CONNECT tunneling with automatic auth injection.

### Local Environment

No special setup required - Maven uses system DNS and direct access.

### Manual Override

Force proxy setup:
```bash
export https_proxy="http://user:token@proxy:port"
python3 maven-proxy-v2.py &
echo '[proxy]' >> ~/.m2/settings.xml
```

See `JAVA25-SETUP.md` for troubleshooting proxy issues.

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

## μ(O) → A (Agents)

**μ** = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O) | See `.claude/agents/` for specifications.
Task(a₁,...,aₙ) ∈ single_message ∧ max_agents=8 | Keep sessions under 70% context.

---

## ⚡ GODSPEED!!! Fanout — Horizontal Parallelization

**Fanout** = spawn n agents in parallel, each running Ψ→Λ→H→Q→Ω with ONE quantum per agent. Max 8 agents. Coordinate via facts, not messages.

### PreFanout Checklist

Before spawning agents:

```
[ ] Facts fresh? Run bash scripts/observatory/observatory.sh
[ ] Pick N quantums (N ≤ 8, each orthogonal)
[ ] Map each quantum → 1 agent (no overlapping files)
[ ] No multi-axis changes per agent (1 axis = 1 quantum per agent)
[ ] Verify zero file conflicts in Ψ.facts/shared-src.json
```

**Fanout Axiom**: Agent independence = zero coordination overhead. If agents need to negotiate, fanout is too wide.

### Agent-to-Quantum Mapping

| Quantum Axis | Best Agent | Rationale |
|--------------|-----------|-----------|
| **Toolchain** (Java25/Maven/JUnit) | validator | Verify compile + test gates |
| **Dependency** (one family) | architect | Check deps-conflicts.json |
| **Schema** (XSD path) | engineer | Edit + validate schema path |
| **Engine semantic** (one pattern) | engineer | Core logic fix in yawl/engine/** |
| **MCP/A2A** (one endpoint) | integrator | Endpoint contract + tests |
| **Resourcing** (allocation logic) | engineer | Workqueue/resource allocation |
| **Test coverage** (one module) | tester | Add tests, run coverage gates |
| **Observability** (one metric) | prod-val | Add monitoring/observability |

### Fanout Circuit (Agent i executes this)

```
Agent_i picks quantum_i

Ψ: read Ψ.facts/modules.json + gates.json (shared read-only)
   ↓
Λ: bash scripts/dx.sh -pl <module_i>  (isolated compile)
   ↓ red?     → fix code_i
   ↓ green?   → proceed
   ↓
H: hook guard (same H ∩ content = ∅ rule)
   ↓
Q: real_impl ∨ throw ∧ ¬mock ∧ ¬lie
   ↓
Ω_i: emit { <specific files for quantum_i> }  (stage only YOUR files)
     (do NOT commit yet — wait for consolidation)
```

**Key**: Agent_i only touches `files_i`. Verify no overlaps in `.claude/facts/shared-src.json` pre-spawn.

### PostFanout Consolidation (Main Session)

After all agents complete:

```
1. Collect emit{file_i} from all agents
2. Verify ∩(emit{i}) = ∅  (no file overlaps)
3. Verify ∧(result_i = green)  (all agents passed Λ)
4. bash scripts/dx.sh all  (final full compile gate)
   ↓ red? → identify failing agent_i, resume with fix
   ↓ green? → proceed
5. git add <all emit files>  (atomic stage)
6. git commit -m "..."  (one logical change across agents)
7. git push -u origin claude/<fanout>-<sessionId>
```

**Atomicity**: Fanout only commits if ALL agents green + full DX passes.

### Fanout Patterns

| Pattern | Example | Agents | Constraint |
|---------|---------|--------|-----------|
| **Module parallel** | Fix 3 modules | 3 | Each agent fixes 1 module |
| **Quantum parallel** | Schema + Engine + MCP | 3 | Each axis independent |
| **Test parallel** | Unit + Integration + E2E | 3 | Each test module separate |
| **Multi-module schema** | Fix XSD in 2 modules | 2 | Schema changes don't conflict |

**Constraint**: Zero shared-src overlap. Use `Ψ.facts/shared-src.json` to verify.

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
- Fanout (n agents, each 1 quantum, coordinate via facts) — horizontal scaling without coordination overhead
- PostToolUse (hook validation) — enforces H at write time
- Stop conditions — re-anchor if uncertain
- Rules (path-scoped) — context-aware governance
- Facts (observable only) — 100× token compression

**Result**: Zero configuration drift. Single-session and multi-agent scaling. Compile ≺ Test ≺ Validate ≺ Deploy. ✈️⚡
