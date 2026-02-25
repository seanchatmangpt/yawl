# GODSPEED!!! Protocol — Formal Thesis
## Maximum Forward Velocity with Zero Invariant Breakage

**Version**: 1.0 | **Date**: 2026-02-20 | **Domain**: YAWL v6.0.0 Enterprise Workflow Engine

---

## Executive Summary

GODSPEED!!! is a deterministic software engineering protocol that achieves **maximum forward velocity** (development speed) while enforcing **zero invariant breakage** (no silent failures, mocks, stubs, or false implementations). It is a five-gate control flow applied **before and after every tool invocation** in the development cycle.

**Core Axiom**: *Compile ≺ Test ≺ Validate ≺ Deploy* — each gate must be green before proceeding.

**Core Insight**: Drift accumulates in parallel dimensions. GODSPEED!!! forces **orthogonal quantums** (single-axis changes), **fact-driven decision-making** (Ψ), and **automated enforcement** (H = hooks).

---

## I. Foundation — The Five Gates (⊤)

GODSPEED!!! enforces a **priority stack** that resolves all conflicts top-down. If uncertain which rule applies: **stop and re-read this stack**.

| Gate | Symbol | Role | Decision Rule |
|------|--------|------|---------------|
| **Observatory** | Ψ | Observe before acting | Are facts fresh? Have I picked ONE quantum? |
| **Build** | Λ | Compile, test, validate | Is `dx.sh all` green? |
| **Guards** | H | Enforce anti-patterns | Does code contain {TODO, mock, stub, fake, lie}? |
| **Invariants** | Q | Real impl or throw | Does method do real work or throw? |
| **Git** | Ω | Atomic commits | Specific files, one logical change, push to session branch |

**Ordering**: Ψ → Λ → H → Q → Ω

**Enforcement**: Automated. PreToolUse checklist before invoking any Bash/Task/Read/Glob/Grep/Write/Edit. Hook validation (`hyper-validate.sh`) on every Write|Edit.

---

## II. Ψ (Observatory) — Observe ≺ Act

### Axiom
*Context is finite. Codebase is infinite.* Facts only, not vibes.

### Principle
Before modifying code, establish **observable facts** about the codebase. The Observatory produces nine fact files, each ~50 tokens, providing 100× compression over code exploration (Grep ≈ 5000 tokens per answer).

### Fact Files — Information Density

| Fact File | Tokens | Answer |
|-----------|--------|--------|
| `modules.json` | ~50 | Which module does X belong to? |
| `gates.json` | ~50 | What test gates/rules exist for module X? |
| `deps-conflicts.json` | ~50 | Can I safely add dependency Y? |
| `reactor.json` | ~50 | Build order? Parallel-safe? |
| `shared-src.json` | ~50 | Is code shared across modules? (Fanout safety check) |
| `tests.json` | ~50 | Coverage? Test count? What gates block this module? |
| `dual-family.json` | ~50 | Type family aliasing? (Compatibility checks) |
| `duplicates.json` | ~50 | Duplicate code patterns? |
| `maven-hazards.json` | ~50 | Plugin conflicts? Version skew? |

### Ψ Refresh Protocol

Facts become stale when:
- New files added to codebase
- Major dependency changes
- Test suite modified
- Build configuration updated

**Check freshness**: Compare SHA256 hashes in `receipts/observatory.json` against current codebase.

**Refresh**: `bash scripts/observatory/observatory.sh` (authoritative source)

### Ψ STOP Condition
If exploring >3 files for one answer → STOP. Facts are stale or missing. Run `bash scripts/observatory/observatory.sh` and re-read fact file.

---

## III. Quantum Selection — Pick ONE Axis

**Multi-axis changes = drift ↑.** GODSPEED!!! forces orthogonal changes. Choose exactly one quantum.

### Definition
A **quantum** is a logically atomic change along a single axis (dimension). Examples:
- Toolchain axis: Upgrade Java version
- Dependency axis: Add one dependency family
- Schema axis: Modify one XSD definition
- Engine semantic axis: Fix one workflow pattern
- MCP/A2A axis: Add one integration endpoint
- Resourcing axis: Change allocation logic
- Test axis: Add test coverage for one module

### Quantum → Rule Mapping

| Quantum | Rule File | Files Touched |
|---------|-----------|---------------|
| **Toolchain** (Java25/Maven/JUnit) | `build/dx-workflow.md` | pom.xml, scripts/, .mvn/ |
| **Dependency** (one family: com.google.*, org.apache.*, etc.) | `config/static-analysis.md` | pom.xml |
| **Schema** (one XSD path) | `schema/xsd-validation.md` | schema/**, *.xsd |
| **Engine semantic** (one pattern) | `engine/workflow-patterns.md` | yawl/engine/**, yawl/stateless/** |
| **MCP/A2A** (one endpoint) | `integration/mcp-a2a-conventions.md` | yawl/integration/** |
| **Resourcing** (allocation logic) | `resourcing/resource-allocation.md` | yawl/resourcing/** |
| **Any code change** | `java25/modern-java.md` | **/*.java |
| **Any test** | `testing/chicago-tdd.md` | **/src/test/** |

### Flow
1. Pick ONE axis (e.g., "Fix task completion in engine")
2. Identify files touched
3. Load rule file(s) for that axis
4. Proceed through gates Ψ → Λ → H → Q → Ω

---

## IV. Λ (Build) — The DX Command

GODSPEED!!! uses a single **DX (Developer Experience)** command for all build operations:

```bash
bash scripts/dx.sh compile          # Fast: compile only, changed modules
bash scripts/dx.sh -pl <module>     # Module-scoped compile
bash scripts/dx.sh all              # Full: all modules (pre-commit gate)
mvn clean verify -P analysis        # Static analysis (SpotBugs, PMD)
```

### Λ Rule
**No commit until `bash scripts/dx.sh all` is green.**

### Build Gate Logic
```
Λ invokes compile
  ↓ red?   → fix code, re-stage, run `dx.sh -pl <module>` → if green, proceed
  ↓ green? → proceed to H
```

### Why DX?
- **Single source of truth** for build semantics
- **Fast feedback**: Changed modules only (compile mode)
- **Module-scoped isolation**: Parallel-safe for agent fanout
- **Pre-commit validation**: Full build gate (`dx.sh all`)

---

## V. H (Guards) — Anti-Pattern Enforcement

**H** = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}

### Axiom
*Blocked patterns are non-negotiable.* The hook `hyper-validate.sh` runs on every Write|Edit and exits with code 2 if H ∩ content ≠ ∅.

### Guard Definitions

| Guard | Pattern | Why Forbidden |
|-------|---------|--------------|
| **TODO** | `// TODO`, `/* TODO */` | Defers work. No explicit contract. |
| **FIXME** | `// FIXME`, `/* FIXME */` | Indicates known bug. Unsustainable. |
| **mock** | Class/method named `Mock*`, `*Mock()` | Fake objects hide missing logic. |
| **stub** | Method named `stub*`, `*Stub()` | Incomplete implementation. |
| **fake** | Method named `*Fake()`, class `Fake*` | Simulation instead of real logic. |
| **empty_return** | `return;`, `return null;`, `return {};` without semantics | Silent failure. |
| **silent_fallback** | `try{...}catch(Exception e){}` (no rethrow/real logic) | Swallows errors. |
| **lie** | Code doesn't match docstring or method signature | Breaks contract. |

### Hook Enforcement Protocol
On Write|Edit:
1. `hyper-validate.sh` scans file for H ∩ content
2. If match found → exit 2 (hook blocks commit)
3. Developer reads error → fixes code for real
4. Re-stages file → creates NEW commit (never amend)
5. Hook passes → commit succeeds

### H → Q Bridge
If you encounter a guard pattern you *think* is necessary:
- **Case 1**: Can you throw `UnsupportedOperationException`? → Throw. (Q gate passes)
- **Case 2**: Real logic possible? → Implement. (H passes)
- **Case 3**: Neither? → STOP. Reassess design.

---

## VI. Q (Invariants) — The Guarantee

**Q** = {real ∨ throw, ¬mock, ¬stub, ¬fallback, ¬lie}

### Four Invariants

| Invariant | Check | Fix |
|-----------|-------|-----|
| **real_impl ∨ throw** | Does method do real work OR throw? | Implement real logic or `throw new UnsupportedOperationException()` |
| **¬mock** | Empty/mock objects in code? | Delete. Use JUnit `@Mock` for tests only. |
| **¬silent_fallback** | Exceptions caught without propagation? | Let exception flow or catch + rethrow + log. |
| **¬lie** | Does code match docstring + signature? | Update code OR update docs to match. |

### Enforcement
These are not hooks; they are code-level **contracts**. Detect by code review:
- Is a method returning without doing work? → Violates `real ∨ throw`
- Is a field initialized to null? → Violates `real_impl` (unless nullable type)
- Is an exception caught and silently ignored? → Violates `¬silent_fallback`
- Is a docstring wrong? → Violates `¬lie`

### Q STOP Condition
If you write code that doesn't satisfy Q, **stop and rewrite**. Q violations propagate as bugs.

---

## VII. Ω (Git) — Atomic Commits with Zero Force

### Rules
1. **No --force, ever** (`--force`, `--force-with-lease` prohibited unless explicitly instructed)
2. **No amending pushed commits** — create new commit instead
3. **Stage specific files only** — `git add <files>`, never `git add .`
4. **One logical change per commit** — e.g., "Fix task completion + add tests", not "Fix task + refactor utils + update docs"
5. **Branch naming**: `claude/<desc>-<sessionId>` (enforced by CI)

### Workflow
```bash
bash scripts/dx.sh all                           # Green gate

git add <file1> <file2> ...                      # Specific files only

git commit -m "$(cat <<'EOF'                      # Descriptive message
Brief one-liner describing logical change.

Detailed explanation (if needed).

https://claude.ai/code/session_<ID>              # Session link (proof of work)
EOF
)"

git push -u origin claude/<desc>-<sessionId>     # Push to session branch
```

### Emit vs. ⊗ Channels

| Channel | Files | Permission |
|---------|-------|-----------|
| **emit** | src/, test/, schema/, .claude/ | Modify freely (within quantum) |
| **⊗** | root, docs/, *.md | Ask user before modifying |

---

## VIII. Fanout — Horizontal Parallelization

### Axiom
**Fanout = spawn n agents in parallel, each running Ψ→Λ→H→Q→Ω with ONE quantum per agent. Max 8 agents.**

### When to Fanout
Multiple **orthogonal quantums** (independent axes, zero file overlap):
- Fix 3 different modules
- Schema change + Engine semantic + MCP integration
- Unit tests + Integration tests + E2E tests

### PreFanout Checklist
```
[ ] Facts fresh? Run bash scripts/observatory/observatory.sh
[ ] Pick N quantums (N ≤ 8, each orthogonal)
[ ] Map each quantum → 1 agent (no overlapping files)
[ ] No multi-axis changes per agent
[ ] Verify zero file conflicts in Ψ.facts/shared-src.json
```

### Agent-to-Quantum Mapping

| Quantum Axis | Best Agent | Why |
|--------------|-----------|-----|
| **Toolchain** (Java/Maven/JUnit) | validator | Verify compile + gates |
| **Dependency** (one family) | architect | Check deps-conflicts.json |
| **Schema** (one XSD) | engineer | Edit + validate |
| **Engine semantic** (one pattern) | engineer | Core logic fix |
| **MCP/A2A** (one endpoint) | integrator | Contract + tests |
| **Resourcing** (allocation) | engineer | Workqueue logic |
| **Test coverage** (one module) | tester | Add tests, verify gates |
| **Observability** (one metric) | prod-val | Add monitoring |

### Fanout Circuit (Each Agent)
```
Agent_i picks quantum_i

Ψ: read modules.json + gates.json (shared read-only, fresh facts)
   ↓
Λ: bash scripts/dx.sh -pl <module_i>  (isolated compile)
   ↓ red?    → fix code
   ↓ green?  → proceed
   ↓
H: hook guard (H ∩ content = ∅)
   ↓
Q: real_impl ∨ throw ∧ ¬mock ∧ ¬lie
   ↓
Ω_i: emit { <specific files for quantum_i> }  (no commit yet)
     (verify zero overlaps with other agents in shared-src.json)
```

### PostFanout Consolidation (Main Session)
```
1. Collect emit{file_i} from all agents
2. Verify ∩(emit{i}) = ∅  (no file overlaps)
3. Verify ∧(result_i = green)  (all agents green)
4. bash scripts/dx.sh all  (final full compile gate)
   ↓ red?   → identify failing agent_i, resume with fix
   ↓ green? → proceed
5. git add <all emit files>  (atomic stage)
6. git commit -m "..."  (one logical change across agents)
7. git push -u origin claude/<fanout>-<sessionId>
```

**Key**: Fanout only commits if ALL agents green + full DX passes. Atomicity guaranteed.

---

## IX. Complete Circuit — Σ (Sigma)

### GODSPEED!!! Flow (Step-by-Step)

```
START

Ψ: Are facts fresh? Can I pick ONE quantum?
   ↓ stale?      → bash scripts/observatory/observatory.sh
   ↓ uncertain?  → read modules.json + gates.json
   ↓ ready?      → pick quantum + identify files → proceed

Λ: bash scripts/dx.sh compile  (or -pl <module> or all)
   ↓ red?        → read error, fix code
   ↓ green?      → proceed

H: Does code contain {TODO, mock, stub, fake, lie, silent_fallback}?
   ↓ yes?        → implement real logic OR throw UnsupportedOperationException
   ↓ no?         → proceed

Q: Does method do real work ∨ throw? ¬mock? ¬lie?
   ↓ no?         → fix invariant
   ↓ yes?        → proceed

Ω: git add <specific-files>  (never git add .)
   git commit -m "..."  (one logical change)
   git push -u origin claude/<desc>-<sessionId>

Σ: Compile ≺ Test ≺ Validate ≺ Deploy
   drift(A) → 0

SUCCESS
```

### STOP Conditions — Halt and Re-Anchor

| Condition | Action | Gate |
|-----------|--------|------|
| Cannot state which module X belongs to? | Read `modules.json` | Ψ |
| Exploring >3 files for one answer? | Run `bash scripts/observatory/observatory.sh` | Ψ |
| Hook blocked Write\|Edit? | Fix violation for real. Don't work around. | H |
| Unsure if file in emit vs. ⊗? | Ask user before touching. | Ω |
| Context usage >70%? | Checkpoint + summarize. Batch remaining. | Meta |
| Tempted "for now" / "later" / "TODO"? | Throw UnsupportedOperationException. | Q |

---

## X. Information Density Principle

### The 100× Rule
- **Fact file answer**: ~50 tokens
- **Grep exploration answer**: ~5000 tokens
- **Compression ratio**: 100×

### Why This Matters
With finite context (200k tokens), exploration overhead compounds. A 20-file investigation (100k tokens) leaves little for implementation. Facts (1k tokens) leave 199k for work.

### Practice
**Always check facts before exploring code.**

```bash
# Fast (fact-driven)
grep "module_X" .claude/facts/modules.json

# Slow (exploration-driven)
find . -name "*.java" | xargs grep "class.*X" | xargs cat
```

---

## XI. Dual-Use Concepts

### Real ∨ Throw (Q Gate Refinement)
Methods must **either** do real work **or** throw. Examples:

**Real work**:
```java
public void handleWorkItem(YWorkItem item) {
    queue.enqueue(item);  // Real logic
}
```

**Throw (when impossible)**:
```java
public void handleRemoteWorkItem(YWorkItem item) {
    throw new UnsupportedOperationException(
        "Remote work items not supported in stateless mode"
    );
}
```

**Wrong** (silent fallback):
```java
public void handleRemoteWorkItem(YWorkItem item) {
    // Does nothing — silent failure
}
```

### Schema as Code (Quantum Example)
If modifying XSD schema:
1. Quantum: "Add `priority` field to task element"
2. Files: `schema/TaskDefinition.xsd`, tests
3. Rule: `schema/xsd-validation.md`
4. Λ: `bash scripts/dx.sh compile` validates XSD syntax
5. Q: XSD must be valid and all uses updated

---

## XII. Rules Activation — Path-Scoped Governance

Seventeen rule files in `.claude/rules/` auto-activate based on files touched:

When you modify `pom.xml` → `.claude/rules/build/dx-workflow.md` and `build/maven-modules.md` activate automatically.

When you modify `**/*.java` → `.claude/rules/java25/modern-java.md` activates.

**No manual loading required.** Rules are context-aware.

---

## XIII. Receipt — Automation & Enforcement

### PreToolUse (Before Bash/Task/Read/Glob/Grep/Write/Edit)
```
1. Ψ gate:    [ ] Stale facts? Run observatory.sh | [ ] Pick 1 quantum?
2. Fanout:    [ ] N independent quantums? | [ ] Zero file overlap?
3. Λ context: [ ] Which rule file activates?
4. H filter:  [ ] Will hook block this?
5. Q check:   [ ] Real impl ∨ throw?
6. Ω guard:   [ ] emit channel? | [ ] Session ID set?
```

**Decision Tree**:
- Ψ facts stale? → Run `bash scripts/observatory/observatory.sh`
- Multiple orthogonal quantums? → Spawn agents
- Cannot pick 1 quantum? → Read `modules.json`
- H blocks? → Fix violation (don't work around)
- Otherwise? → Proceed. Hook is autopilot.

### PostToolUse (After Write|Edit)
- Hook runs automatically
- If green → proceed to next operation
- If blocked (exit 2) → read error, fix code for real, re-stage, create NEW commit

### Automation Layers
1. **PreToolUse checklist** — keeps session aligned
2. **Fanout (n agents, 1 quantum each)** — horizontal scaling without coordination
3. **PostToolUse hook validation** — enforces H at write time
4. **Facts (observable only)** — 100× token compression
5. **Rules (path-scoped)** — context-aware governance

---

## XIV. Architecture Integration (Γ)

GODSPEED!!! works with YAWL's four-domain architecture:

| Domain | Key Types | Rule(s) |
|--------|-----------|---------|
| **Engine** | YEngine, YNetRunner, YWorkItem | `engine/workflow-patterns.md` |
| **Interface** | A:design, B:client, E:events | `engine/interfaces.md` |
| **Integration** | MCP, A2A, Autonomous Agents | `integration/mcp-a2a-conventions.md` |
| **Stateless** | YStatelessEngine, YCaseMonitor | `engine/workflow-patterns.md` |

Each domain has its own rule file. GODSPEED!!! enforces them orthogonally.

---

## XV. Theorem (Drift Elimination)

### Statement
*If each commit satisfies Ψ→Λ→H→Q→Ω, then drift(A) → 0 over time.*

Where:
- **drift(A)** = architectural entropy (quality degradation, silent failures, technical debt)
- **A** = codebase state
- **Ψ→Λ→H→Q→Ω** = GODSPEED gate sequence

### Proof Sketch
1. Ψ (facts) prevents assumption-driven bugs
2. Λ (build) enforces compilation + tests green
3. H (guards) prevents silent failures
4. Q (invariants) enforces contracts
5. Ω (git) ensures atomic, traceable changes

→ Each commit is guaranteed correct by construction
→ Accumulated commits form a chain of correctness
→ drift(A) cannot increase

---

## XVI. Closing — The Philosophy

GODSPEED!!! is not a workflow; it is a **covenant**. It says:

> *I will not ship silent failures. I will not write TODOs. I will not mock what I don't understand. I will let exceptions flow, build green, and commit atomically.*

**Cost**: Discipline. Every tool invocation asks: "Are my facts fresh? Is this one quantum? Is my code real?"

**Benefit**: Velocity + correctness. No regressions. No surprises at deploy time. One logical change per commit. Compile ≺ Test ≺ Validate ≺ Deploy. ✈️⚡

---

## References & Appendices

### Key Files
- `.claude/CLAUDE.md` — embedded protocol (this document's source)
- `.claude/HYPER_STANDARDS.md` — guard detection regex + enforcement
- `.claude/OBSERVATORY.md` — instrument-building guide
- `.claude/facts/*.json` — observable facts (output of observatory.sh)
- `.claude/rules/` — 17 path-scoped rule files
- `.claude/hooks/hyper-validate.sh` — H enforcement hook
- `scripts/dx.sh` — DX command implementation
- `scripts/observatory/observatory.sh` — Ψ fact generation

### Glossary

| Term | Definition |
|------|-----------|
| **Quantum** | Single-axis change (one module, one feature, one dependency family) |
| **Fanout** | Parallel execution of N agents, each with 1 quantum |
| **Drift** | Architectural entropy; quality degradation; technical debt |
| **Guard (H)** | Anti-pattern; forbidden in code (TODO, mock, stub, etc.) |
| **Invariant (Q)** | Code property that must hold (real ∨ throw, ¬mock, ¬lie) |
| **Fact (Ψ)** | Observable property of codebase (modules, tests, deps) |
| **Emit** | Files allowed to be modified freely in this quantum |
| **⊗** | Files (root, docs/) that require user permission |
| **DX** | Developer Experience; single unified build command |

### Citation
```
@thesis{godspeed2026,
  title={GODSPEED!!! Protocol: Maximum Forward Velocity with Zero Invariant Breakage},
  author={Anthropic Claude Code},
  year={2026},
  institution={YAWL Project},
  url={https://github.com/yawlfoundation/yawl/blob/master/.claude/GODSPEED-THESIS.md}
}
```

---

**End of Thesis**

✈️⚡ — *Compile ≺ Test ≺ Validate ≺ Deploy*
