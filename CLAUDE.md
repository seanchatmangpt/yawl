# YAWL v6.0.0 | A = μ(O)

O = {engine, elements, stateless, integration, schema, test}
Σ = Java25 + Maven + JUnit + XML/XSD | Λ = compile ≺ test ≺ validate ≺ deploy
**Yet Another Workflow Language** - Enterprise BPM/Workflow system based on rigorous Petri net semantics.

## Quick Commands

```bash
# Agent DX — Fast Build-Test Loop (PREFERRED)
bash scripts/dx.sh                       # Compile + test CHANGED modules only
bash scripts/dx.sh compile               # Compile changed modules (fastest feedback)
bash scripts/dx.sh all                   # Compile + test ALL modules
bash scripts/dx.sh -pl yawl-engine       # Target specific module(s)

# Standard Build
mvn -T 1.5C clean compile               # Parallel compile (~45s)
mvn -T 1.5C clean test                  # Parallel tests
mvn -T 1.5C clean package               # Full build (~90s)

# Before Committing
bash scripts/dx.sh all                   # Fast: agent-dx profile, all modules

# Security & Analysis
mvn clean verify -P analysis             # Static analysis (SpotBugs, PMD)
```

## H (Guards) - ENFORCED BY HOOKS

**H** = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
PostToolUse(Write|Edit) → guard(H) → ⊥ if H ∩ content ≠ ∅

After every Write/Edit, `.claude/hooks/hyper-validate.sh` checks 14 anti-patterns.
Violations are **blocked** (exit 2). See `.claude/HYPER_STANDARDS.md` for full list.

**Your options**: ✅ Real implementation | ✅ Throw UnsupportedOperationException | ❌ NEVER mock/stub/placeholder

## Q (Invariants)

**Q** = {real_impl ∨ throw UnsupportedOperationException, no_mock, no_stub, no_fallback, no_lie}

1. **Real Implementation OR Throw**: Every public method either does real work or throws UnsupportedOperationException
2. **No Mocks/Stubs**: No mock objects, empty implementations, or placeholders
3. **No Silent Fallbacks**: Exceptions must propagate or be explicitly handled
4. **No Lies**: Code does exactly what it claims

## μ(O) → A (Agents)

**μ** = {engineer, validator, architect, integrator, reviewer, tester, prod-val, perf-bench}
Task(prompt, agent) ∈ μ(O) | See `.claude/agents/` for specifications.

## Π (Skills)

**Π** = {/yawl-build, /yawl-test, /yawl-validate, /yawl-deploy, /yawl-review, /yawl-integrate, /yawl-spec, /yawl-pattern}
Invoke with `/skill-name` — see `.claude/skills/` for implementations.

## Ψ (Observatory) — Observe ≺ Act

**Ψ** = `docs/v6/latest/{facts/, diagrams/, receipts/}` | Ψ_refresh = `bash scripts/observatory/observatory.sh`

**AXIOM**: Context window is finite. Codebase is not. **Read facts, don't explore.**
- 1 fact file ≈ 50 tokens. Grepping for same answer ≈ 5000 tokens. **Compression: 100×.**
- If >3 files needed → **build an instrument** (see `.claude/OBSERVATORY.md`)

**Ψ.facts**: `modules.json` `reactor.json` `shared-src.json` `tests.json` `dual-family.json` `duplicates.json` `gates.json` `deps-conflicts.json` `maven-hazards.json`
**Ψ.verify**: `receipts/observatory.json` → SHA256 hashes. Stale? Re-run.

## Γ (Architecture)

**Γ(engine)** = {YEngine, YNetRunner, YWorkItem, YSpecification}
**Γ(interface)** = {A:design, B:client, E:events, X:extended}
**Γ(integration)** = {MCP:zai-mcp, A2A:agent-to-agent}
**Γ(stateless)** = {YStatelessEngine, YCaseMonitor, YCaseImporter/Exporter}

**Entry Points**: `YEngine` (stateful) | `YStatelessEngine` (stateless) | `YSpecification` (definitions) | `YawlMcpServer` (MCP) | `YawlA2AServer` (A2A)
**Docs**: All 89 packages have `package-info.java`. Read these first.

## τ (Coordination)

Task(a₁,...,aₙ) ∈ single_message ∧ max_agents=8
topology = hierarchical(μ) | mesh(integration)
Keep sessions under 70% context via batching and verification checkpoints.

## Ω (Commit Workflow)

Before every commit: `bash scripts/dx.sh all` → `git add <files>` → commit with session URL → `git push -u origin claude/<desc>-<sessionId>`

## Git Protocol — Zero Force Policy

- **NEVER** `--force` or `--force-with-lease` unless explicitly instructed
- **NEVER** amend pushed commits — create new commit instead
- Stage specific files (`git add <files>`, never `git add .`)
- One logical change per commit

## Channels

**emit**: {src/, test/, schema/, .claude/} — modify freely
**⊗**: root, docs/, *.md — ask before modifying

## References

- **Observatory**: `.claude/OBSERVATORY.md` — read FIRST before exploring codebase
- **Standards**: `.claude/HYPER_STANDARDS.md` — detailed guard examples
- **Java 25**: See `.claude/rules/java25/` — features, patterns, conventions
- **Security**: See `.claude/rules/security/` — TLS, crypto, compliance
- **Build**: See `.claude/rules/build/` — profiles, dx workflow
- **Architecture**: `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` — 8 patterns

## Receipt

**A** = μ(O) | O ⊨ Java+BPM+PetriNet | μ∘μ = μ | drift(A) → 0
PostToolUse guards enforce H automatically. Stop hook verifies clean git state.
