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

## Λ (Build) — ALWAYS USE DX

```bash
bash scripts/dx.sh                       # Compile + test CHANGED modules
bash scripts/dx.sh compile               # Compile only (fastest feedback)
bash scripts/dx.sh all                   # ALL modules (pre-commit gate)
bash scripts/dx.sh -pl yawl-engine       # Target specific module(s)
mvn clean verify -P analysis             # Static analysis (SpotBugs, PMD)
```

## H (Guards) — ENFORCED BY HOOKS

**H** = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
PostToolUse(Write|Edit) → guard(H) → ⊥ if H ∩ content ≠ ∅

Hook `.claude/hooks/hyper-validate.sh` checks 14 anti-patterns on every Write/Edit.
Violations are **blocked** (exit 2). See `.claude/HYPER_STANDARDS.md`.

**ONLY**: ✅ Real implementation | ✅ Throw UnsupportedOperationException | ❌ NEVER mock/stub/placeholder

## Q (Invariants)

**Q** = {real_impl ∨ throw, no_mock, no_stub, no_fallback, no_lie}

1. **Real OR Throw** — every public method does real work or throws UnsupportedOperationException
2. **No Mocks/Stubs** — no mock objects, empty implementations, or placeholders
3. **No Silent Fallbacks** — exceptions propagate or are explicitly handled with real logic
4. **No Lies** — code does exactly what its name, signature, and docs claim

## Ψ (Observatory) — Observe ≺ Act

**AXIOM**: Context window is finite. Codebase is not. **Read facts, don't explore.**

```bash
bash scripts/observatory/observatory.sh  # Refresh all facts
```

**Ψ.facts** in `docs/v6/latest/facts/`: `modules.json` `reactor.json` `shared-src.json` `tests.json` `dual-family.json` `duplicates.json` `gates.json` `deps-conflicts.json` `maven-hazards.json`
**Ψ.verify**: `receipts/observatory.json` → SHA256 hashes. Stale? Re-run.

1 fact file ≈ 50 tokens. Grepping for same answer ≈ 5000 tokens. **100× compression.**
If >3 files needed → build an instrument (`.claude/OBSERVATORY.md`).

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

## R (Rules) — PATH-SCOPED, LOADED ON DEMAND

17 rule files in `.claude/rules/` — each activates only when touching matching paths:

| Category | Rule File | Scope |
|----------|-----------|-------|
| Build | `build/dx-workflow.md` | pom.xml, scripts/, .mvn/ |
| Build | `build/maven-modules.md` | pom.xml, .mvn/ |
| Config | `config/static-analysis.md` | checkstyle.xml, pmd, spotbugs, .github/ |
| Docker | `docker/container-conventions.md` | Dockerfile*, docker-compose*, kubernetes/ |
| Elements | `elements/domain-model.md` | yawl/elements/** |
| Engine | `engine/interfaces.md` | yawl/engine/interfac*/** |
| Engine | `engine/workflow-patterns.md` | yawl/engine/**, yawl/stateless/** |
| Engine | `engine/worklet-service.md` | yawl/worklet/** |
| Integration | `integration/autonomous-agents.md` | yawl/integration/autonomous/** |
| Integration | `integration/mcp-a2a-conventions.md` | yawl/integration/** |
| Java 25 | `java25/modern-java.md` | **/*.java |
| Monitoring | `observability/monitoring-patterns.md` | yawl/observability/**, scripts/observatory/ |
| Resourcing | `resourcing/resource-allocation.md` | yawl/resourcing/** |
| Schema | `schema/xsd-validation.md` | schema/**, exampleSpecs/**, *.xsd |
| Scripts | `scripts/shell-conventions.md` | scripts/**, .claude/hooks/**, *.sh |
| Security | `security/crypto-and-tls.md` | yawl/authentication/**, Dockerfile* |
| Testing | `testing/chicago-tdd.md` | **/src/test/**, test/** |

**Do NOT duplicate rule content here.** Rules are the source of truth for their scope.

## Deep References

- `.claude/HYPER_STANDARDS.md` — guard patterns, detection regex, enforcement protocol
- `.claude/OBSERVATORY.md` — instrument-building guide, fact schema
- `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` — 8 architecture patterns

## STOP Conditions — HALT AND RE-ANCHOR

**STOP** if any of these occur:
- You are about to write code and cannot state which module it belongs to → read `Ψ.facts/modules.json`
- You are exploring >3 files to answer a question → use Observatory instead
- A hook blocks your Write/Edit → fix the violation, do not work around the hook
- You are unsure whether a file is in **emit** or **⊗** channel → ask the user
- Context usage exceeds 70% → checkpoint, summarize, batch remaining work
- You feel tempted to add "for now" / "later" / "simplified" → throw UnsupportedOperationException instead

## Receipt

**A** = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0
PostToolUse guards enforce H. Stop conditions enforce re-anchoring. Rules enforce scope. Drift → 0.
