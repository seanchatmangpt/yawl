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
2. Λ context: [ ] Which rule file activates? | [ ] DX loop ready?
3. H filter:  [ ] Will hook block this? (search H = {TODO,mock,stub,fake,empty,lie})
4. Q check:   [ ] Real impl ∨ throw? | [ ] No silent fallback?
5. Ω guard:   [ ] emit channel? | [ ] Session ID set? | [ ] Specific files, not git add .
```

**Decision Tree**:
- Ψ facts stale? → `bash scripts/observatory/observatory.sh` + re-read fact file
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

## μ(O) → A (Agents)

**μ** = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O) | See `.claude/agents/` for specifications.
Task(a₁,...,aₙ) ∈ single_message ∧ max_agents=8 | Keep sessions under 70% context.

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
- PostToolUse (hook validation) — enforces H at write time
- Stop conditions — re-anchor if uncertain
- Rules (path-scoped) — context-aware governance
- Facts (observable only) — 100× token compression

**Result**: Zero configuration drift. Compile ≺ Test ≺ Validate ≺ Deploy. ✈️⚡
