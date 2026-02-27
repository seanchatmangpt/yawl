# The Chatman Equation: A = μ(O)

**Quadrant**: Explanation | **Concept**: Agent quality model — how observation drives artifact quality

This document explains the formal model behind YAWL's agent operating protocol, why "A = μ(O)" appears at the top of `CLAUDE.md`, and what each symbol means in practice for build and test work.

---

## The Equation

```
A = μ(O)
```

Where:
- **A** — the quality of any artifact produced (code, commit, test, documentation)
- **μ** — the agent's processing pipeline (a composition of 5 gates)
- **O** — the set of codebase observations available to the agent

The equation says: artifact quality is a function of what the agent observes. An agent with accurate, complete observations produces accurate, complete artifacts. An agent operating on stale, incomplete, or incorrect observations produces stale, incomplete, or incorrect artifacts — regardless of how sophisticated the processing pipeline is.

This has a concrete implication: **the primary cause of agent error is not reasoning failure, it is observation failure**.

---

## The Processing Pipeline (μ)

μ is a composition of five gates, applied in this order:

```
μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ
```

Applied right-to-left: first Ψ, then Λ, then H, then Q, then Ω.

| Gate | Symbol | Name | What it does |
|------|--------|------|-------------|
| Ψ | Psi | Observatory | Observe: gather codebase facts |
| Λ | Lambda | Build | Compile: verify code is buildable |
| H | H-Guards | Guards | Block: enforce zero-tolerance anti-patterns |
| Q | Q-Invariants | Invariants | Verify: real implementation or throw |
| Ω | Omega | Git | Emit: commit and push the artifact |

Priority resolves top-down: **H > Q > Ψ > Λ > Ω**. This means H-Guards (hard blocks) take priority over everything, including the observatory. You cannot observe your way past a guard violation.

Flow per action: **Ψ → Λ → H → Q → Ω**. No gate skipping.

---

## O: The Observation Set

O is defined as:
```
O = {engine, elements, stateless, integration, schema, test}
```

In plain terms: the relevant portions of the codebase that the agent must observe before producing any artifact. For build and test work:

- **engine**: How `YEngine`, `YNetRunner`, and the persistence layer work
- **elements**: The Petri net model (`YNet`, `YTask`, `YCondition`)
- **stateless**: How the stateless event-driven engine differs from the stateful one
- **integration**: MCP/A2A connectors, external system interfaces
- **schema**: XSD schemas that define valid workflow specifications
- **test**: Existing tests that constrain what changes are safe

An agent that produces code without reading the relevant observation set is operating from its training data (stale, generic) rather than from the actual codebase (current, specific). The equation predicts the result: A (artifact quality) will be low because μ(O) received a degraded O.

---

## Why O Matters More Than μ

The Chatman Equation is a statement about **where errors localize**. If an agent makes a mistake, the mistake happened at a specific gate: "Loss is localizable — it drops at a specific gate."

In practice, most agent errors are not reasoning failures at H or Q — they are observation failures at Ψ:
- The agent didn't read the module's `package-info.java` before adding a class
- The agent didn't check `shared-src.json` before editing a shared source file
- The agent didn't read the existing test class before adding a test that duplicates it

The Observatory (Ψ) gate exists to make observation cheap. The fact files in `docs/v6/diagrams/facts/` represent ~50 tokens each versus ~5000 tokens to grep the equivalent raw source. A 100× compression ratio means the agent can observe more with the same context budget, which means O is more complete, which means A is higher quality.

---

## The Build System as μ

The 7 quality gates (G_compile through G_release) are the concrete implementation of μ for the build and test domain:

| CLAUDE.md gate | Quality gate | Role in μ |
|---------------|-------------|----------|
| Ψ (observe) | Observatory facts | Pre-work observation |
| Λ (build) | G_compile | Compile verification |
| H (guards) | G_guard | Anti-pattern blocking |
| Q (invariants) | G_test, G_analysis | Correctness verification |
| Ω (emit) | G_release | Artifact publication |

Every action in the build system is either observation (reading facts, running observatory) or a gate application (compile, test, guard, analyze, release). The Chatman Equation predicts that skipping any gate degrades A.

This is why the DoD says "No gate skipping" — not as a bureaucratic requirement, but as a consequence of the model. Skipping G_test means Q was not applied to the artifact. The equation predicts that the artifact quality will be degraded by exactly the amount that Q would have caught.

---

## drift(A) → 0

The system goal is:
```
drift(A) → 0
```

Where drift(A) is the gap between the actual artifact quality and the maximum possible quality given the current codebase state. As agents observe more accurately (Ψ improves), build more rigorously (Λ gates pass), enforce more strictly (H blocks), verify more completely (Q runs), and emit more carefully (Ω controls commits), drift approaches zero.

This is not a one-time achievement but a continuous process. Each correction teaches the system: "This class of error has zero pattern; apply the correction at its gate." When a lesson is documented in `tasks/lessons.md`, it reduces the probability of the same error in future actions, which reduces drift over time.

---

## Practical Implications for Build Work

**Before starting any build task**:
- Run `scripts/observatory/observatory.sh` if facts are stale (>3 files explored)
- Read `reactor.json` before touching `pom.xml` or `dx.sh` (Ψ gate for FM7)
- Read `shared-src.json` before editing any file in the shared source modules (Ψ gate for FM1)

**During implementation**:
- Let H-Guards fire and fix them immediately — do not defer to "fix later" (H gate)
- Run `dx.sh compile` after each significant change before moving to the next task (Λ gate)

**Before committing**:
- Run `dx.sh all` and verify it exits 0 (full μ application)
- Do not commit until all gates are green (Ω gate)

These are not arbitrary process steps — they are the direct application of μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ to build and test work.

---

## The Chatman Principle

The last line of the equation's context in `CLAUDE.md` is:

> "Today's best practices are tomorrow's antipatterns. Tomorrow is always less than 24 hours away."

This is the time-derivative of the equation. O changes continuously as the codebase evolves. μ must adapt to match. An agent that applies yesterday's best practices to today's O produces drift. The correction is always the same: observe more frequently (Ψ), and update the processing pipeline (μ) when corrections arrive.

The build system's poka-yoke mechanisms (PY-1 through PY-6) are the equivalent of frequent re-observation: they force the agent to verify that O still matches A before declaring the artifact complete.

---

## See Also

- [CLAUDE.md](../../CLAUDE.md) — root axiom and full equation
- [Quality Gates Reference](../reference/quality-gates.md) — μ applied to build/test
- [FMEA Risk Table](../reference/fmea-risk-table.md) — what happens when gates are skipped
- [Why H-Guards Exist](h-guards-philosophy.md) — the H gate in depth
- [Why Reactor Ordering Matters](reactor-ordering.md) — the Λ gate in depth
