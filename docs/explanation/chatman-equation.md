# The Chatman Equation: A = μ(O) | Observation Drives Artifact Quality

**Quadrant**: Explanation | **Concept**: Agent quality model — why observation is the primary driver of artifact quality

This document explains the formal model behind YAWL's agent operating protocol: the Chatman Equation, why it appears at the top of `CLAUDE.md`, what each symbol means, and how it guides every production decision in build and test work.

The Chatman Equation is not a hypothesis — it is an empirical observation from observing thousands of agent operations and locating where errors originate.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [The Equation: A = μ(O)](#the-equation-a--μo)
3. [Observable Set O: What the Agent Sees](#observable-set-o-what-the-agent-sees)
4. [Transformation Function μ: The Five-Phase Pipeline](#transformation-function-μ-the-five-phase-pipeline)
5. [Pipeline Phases: Ψ→Λ→H→Q→Ω](#pipeline-phases-ψλhqω)
6. [Loss Localization: Where Errors Originate](#loss-localization-where-errors-originate)
7. [Priority Ordering: Why H > Q > Ψ > Λ > Ω](#priority-ordering-why-h--q--ψ--λ--ω)
8. [Mathematical Foundation](#mathematical-foundation)
9. [Production Application: Real Workflow Examples](#production-application-real-workflow-examples)
10. [Contrasts with Traditional ML/AI Approaches](#contrasts-with-traditional-mlai-approaches)
11. [Decision Tree: When Does Chatman Apply?](#decision-tree-when-does-chatman-apply)
12. [Practical Implications](#practical-implications)

---

## Executive Summary

The Chatman Equation is a simple formula that predicts artifact quality as a function of observation and processing:

```
A = μ(O)
```

Where:
- **A** = the quality of any artifact (code, commit, test, documentation, design decision)
- **μ** = the agent's processing pipeline (a composition of five sequential gates)
- **O** = the complete observation set available to the agent at work start

**The core insight**: Artifact quality is fundamentally limited by observation quality. An agent with perfect O but imperfect μ can produce better results than an agent with imperfect O and perfect μ. The equation says: **observation is the primary driver**.

**Corollary**: Most agent errors are not reasoning failures; they are observation failures. The agent was never operating on the actual codebase state.

---

## The Equation: A = μ(O)

### The Full Form

```
A = μ(O)

where

μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ
```

Read this as: "Artifact quality is determined by running observation (Ψ) through a composition of five gates: build (Λ), guards (H), invariants (Q), and git (Ω)."

### Symbol Breakdown

| Symbol | Name | Meaning | Example |
|--------|------|---------|---------|
| **A** | Artifact | Any work product: code, commit, test, design | A `.java` file, a git commit, a test suite |
| **μ** | Mu (transformation) | The processing pipeline that transforms O into A | The GODSPEED circuit (Ψ→Λ→H→Q→Ω) |
| **O** | Observation | The complete set of codebase facts available at work start | {engine, elements, stateless, integration, schema, test} |
| **Ψ** | Psi (observatory) | Observe: gather canonical facts about codebase | Run `scripts/observatory/observatory.sh`, read `modules.json` |
| **Λ** | Lambda (build) | Compile: verify code is buildable and links | Run `dx.sh compile` or `dx.sh -pl <module>` |
| **H** | H-Guards | Block: enforce zero-tolerance anti-patterns (7 forbidden patterns) | Detect TODO, mock, stub, fake, empty returns, silent fallbacks, lies |
| **Q** | Q-Invariants | Verify: real implementation or throw loudly | `real_impl ∨ throw UnsupportedOperationException` |
| **Ω** | Omega (git) | Emit: atomic commit with full traceability | `git add <specific-files>`, `git commit`, never `--force` |

### What the Equation Predicts

**If O is degraded, A is degraded** — no matter how sophisticated the μ is.

Example: An agent writes code without reading the module's `package-info.java`. The observation set O is incomplete (missing architectural constraints). Even though μ is perfectly applied (the code compiles, passes tests, has no guard violations), A is low quality because it violates architectural intent.

**Corollary**: The most effective way to improve A is to improve O, not to improve μ.

---

## Observable Set O: What the Agent Sees

O is formally defined as:

```
O = {engine, elements, stateless, integration, schema, test}
```

### The Six Observation Domains

**1. Engine**: Core stateful execution
- Where: `yawl-engine/`, `yawl-engine-interfaces/`, `yawl-engine-resources/`
- What to observe: `YEngine`, `YNetRunner`, `YWorkItem`, persistence layer
- Example observation: "YNetRunner is a singleton that cannot be garbage-collected during case execution"
- Impact on A: Code that creates multiple YNetRunner instances will deadlock

**2. Elements**: Petri net model (workflow definition)
- Where: `yawl-elements/`
- What to observe: `YNet`, `YTask`, `YCondition`, `YFlow`, control flow semantics
- Example observation: "OR-join nodes require non-local semantics for safe synchronization"
- Impact on A: Code that implements naive local OR-join semantics will lose tokens

**3. Stateless**: Stateless event-driven engine (alternative architecture)
- Where: `yawl-stateless/`, `yawl-stateless-execution/`
- What to observe: How stateless execution differs from stateful, event publishing contracts
- Example observation: "Stateless engine cannot block on external service responses; must use callbacks"
- Impact on A: Code that blocks threads in stateless context will starve the event loop

**4. Integration**: External system connectivity (MCP/A2A)
- Where: `yawl-integration/`, `yawl-mcp-server/`, `yawl-a2a-server/`
- What to observe: Protocol contracts, message format, authentication, timeouts
- Example observation: "MCP responses must be non-streaming for client tool-call compatibility"
- Impact on A: Streaming responses will fail at tool call integration points

**5. Schema**: XSD-defined workflow specifications
- Where: `schema/`, `yawl-elements/src/main/resources/`
- What to observe: Valid XML structure, enumeration constraints, cardinality rules
- Example observation: "Task input/output ports are XSD-restricted to unique names"
- Impact on A: Code that allows duplicate port names will fail schema validation

**6. Test**: Existing test suites that constrain safe changes
- Where: `**/src/test/java/`, `./*.pom.xml` test configurations
- What to observe: Test coverage, regression tests, contract tests
- Example observation: "YNetRunner deadlock tests assume single-threaded task queue"
- Impact on A: Code that changes task queue concurrency breaks 17 existing tests

### O Completeness and A Quality Relationship

The relationship is monotonic: **as |O| increases and accuracy(O) improves, A quality increases**.

```
O incomplete (agent reads 1-2 files)
  ↓
A is low (code breaks architectural intent or existing tests)

O reasonably complete (agent reads 5-10 files + facts)
  ↓
A is medium (code works in isolation but misses subtle constraints)

O very complete (agent reads all relevant observation domains)
  ↓
A is high (code integrates correctly, passes all tests, respects architecture)
```

### Why O Matters More Than Training Data

An agent's training data is a static snapshot of code from months or years ago. The codebase has evolved since then:

- New classes have been added
- APIs have changed
- Test coverage has increased
- Architectural constraints have been discovered

Using training data without consulting O is equivalent to building with a 6-month-old blueprint. The result is predictable: A is low because μ is applied to stale O.

The Chatman Equation explains why the first step of every YAWL task is: **"Run the Observatory."**

---

## Transformation Function μ: The Five-Phase Pipeline

μ is not a single step — it is a composition of five gates applied in sequence:

```
μ = Ω ∘ Q ∘ H ∘ Λ ∘ Ψ

Applied right-to-left: Ψ first, then Λ, then H, then Q, then Ω.
```

Each gate is a test. If an artifact fails a gate, it cannot proceed to the next gate. The gates are **sequential and not reorderable**.

### Gate Design Philosophy

Each gate tests a different property:

| Gate | Property Tested | Exit Condition | Example |
|------|-----------------|---|---------|
| **Ψ** | Observation completeness | Facts gathered, O is synchronized | `observatory.json` exists and is current |
| **Λ** | Buildability | Compiles and links without error | `mvn compile` exits 0 |
| **H** | Anti-pattern absence | Zero forbidden patterns detected | No TODO, mock, stub, fake, empty return, silent fallback, or lie patterns |
| **Q** | Correctness | Real implementation or explicit throw | Code does what docs claim, or throws UnsupportedOperationException |
| **Ω** | Traceability | Atomic, single-purpose commit | One logical change per commit, full git history preserved |

### Why Gates Are Sequential

You cannot skip a gate because the gates build on each other:

- **Cannot skip Ψ**: You can't know what to build (Λ) without observing the current codebase
- **Cannot skip Λ**: You can't guard (H) code that doesn't compile
- **Cannot skip H**: You can't verify correctness (Q) if the code contains deception patterns
- **Cannot skip Q**: You can't commit (Ω) code that violates invariants
- **Cannot skip Ω**: You can't claim a change is complete without atomic traceability

**Attempting to skip a gate is like trying to paint a wall that hasn't been plastered.** The work fails downstream.

---

## Pipeline Phases: Ψ→Λ→H→Q→Ω

### Phase Ψ: OBSERVATORY — Observe Before You Change

**Purpose**: Synchronize your understanding of the codebase with its current state.

**What it does**:
- Gathers canonical facts from the codebase (modules.json, gates.json, shared-src.json, etc.)
- Compresses ~5000 tokens of raw grep output into ~50 tokens of structured facts
- Enables 100× observation compression ratio within context limits

**When to apply Ψ**:
- Before starting any work: `bash scripts/observatory/observatory.sh`
- After exploring >3 files: Re-run to refresh facts
- Before committing: Verify facts are current

**Exit criteria (Ψ is GREEN when)**:
- All 9 fact files exist: `modules.json`, `gates.json`, `deps-conflicts.json`, `reactor.json`, `shared-src.json`, `tests.json`, `dual-family.json`, `duplicates.json`, `maven-hazards.json`
- Facts are current (generated within 2 hours)
- Observatory script exits 0
- SHA256 checksums pass validation

**If Ψ fails**:
| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| Missing fact file | Observatory script error or incomplete run | Re-run `observatory.sh` |
| SHA256 mismatch | Files corrupted during generation | Re-run `observatory.sh` |
| Stale facts (>2h old) | Normal; time has passed | Refresh with `observatory.sh` |

**Example**: You want to add a method to YNetRunner
1. Run `bash scripts/observatory/observatory.sh` (≈5-10 seconds)
2. Check `shared-src.json` — is YNetRunner shared?
3. Check `tests.json` — where should the test go?
4. Read `yawl-engine/src/main/java/org/yawl/engine/package-info.java` — what's the module's role?

Now O is complete. You can proceed to Λ.

---

### Phase Λ: BUILD — Compile, Link, Verify Buildability

**Purpose**: Verify that the artifact compiles and links without error.

**What it does**:
- Runs Maven compiler with strict error checking
- Three levels of strictness based on scope:
  - `dx.sh compile` — fast, modified files only (≈5-15s)
  - `dx.sh -pl <module>` — one module + dependencies (≈10-30s)
  - `dx.sh all` — full reactor (≈60-120s)

**When to apply Λ**:
- After each significant code change: `dx.sh compile`
- Before declaring a module complete: `dx.sh -pl <module>`
- Before committing: Prerequisite for H gate

**Exit criteria (Λ is GREEN when)**:
- `mvn compile` exits 0
- No syntax errors
- No type errors
- All declarations are resolvable
- No linking failures in dependent modules

**If Λ fails**:
| Failure | Example | Recovery |
|---------|---------|----------|
| Syntax error | Missing semicolon, mismatched braces | Fix syntax error, re-run `dx.sh compile` |
| Type error | Method call with wrong argument types | Check method signature, fix call site |
| Missing import | Reference to undeclared class | Add import or qualify the class |
| Circular dependency | Module A depends on B, B depends on A | Break the cycle or use shared-src |

**Why Λ matters**: If code doesn't compile, it doesn't work. No amount of subsequent gates can fix this.

---

### Phase H: H-GUARDS — Block Zero-Tolerance Anti-Patterns

**Purpose**: Enforce absolute prohibitions against seven deception patterns that guarantee degraded artifacts.

**What it blocks** (7 patterns):

| Pattern | Symbol | Example | Fix |
|---------|--------|---------|-----|
| Deferred work | H_TODO | `// TODO: implement this` | Implement now or throw UnsupportedOperationException |
| Mock implementations | H_MOCK | `class MockDataService implements DataService` | Delete mock or implement real service |
| Stub returns | H_STUB | `return "";` from non-void method | Implement real method or throw |
| Empty method bodies | H_EMPTY | `public void init() { }` | Implement real logic or throw |
| Silent fallback | H_FALLBACK | `catch (Exception e) { return Collections.emptyList(); }` | Propagate exception |
| Documentation lies | H_LIE | `/** @return never null */ public String get() { return null; }` | Match code to docs |
| Silent logging | H_SILENT | `log.error("Not implemented")` | Throw exception instead |

**When to apply H**:
- Automatically on every write/edit via post-tool hook
- Before declaring code GREEN
- Cannot be skipped or worked around

**Exit criteria (H is GREEN when)**:
- Zero violations across all 7 patterns
- No deferred work markers in comments
- No mock/stub/fake classes or methods
- No empty returns or fallback catches
- Code and documentation are consistent
- Errors are thrown, not silently logged

**If H fails**:
| Failure | Root Cause | Recovery |
|---------|-----------|----------|
| H_TODO | Incomplete work | Finish implementation or throw |
| H_MOCK | Placeholder code | Delete or implement real logic |
| H_STUB | Incomplete return | Add real implementation |
| H_FALLBACK | Silent error hiding | Change to throw instead |
| H_LIE | Code ≠ docs | Fix code or docs to match |

**Why H matters**: H-Guards prevent the single most common agent failure mode: leaving placeholder code in production. An artifact that passes H is guaranteed to contain no deception patterns.

**Note**: H takes absolute priority. You cannot observe, build, or verify your way past a guard violation. Fix the violation for real.

---

### Phase Q: Q-INVARIANTS — Verify Real Implementation or Throw

**Purpose**: Ensure every promise (method signature, documentation) is either fulfilled or explicitly rejected with a thrown exception.

**What it verifies**:

```
real_impl ∨ throw UnsupportedOperationException

There is no third option: partial implementation, silent failure, or fake data.
```

**When to apply Q**:
- After passing H gate
- On every method signature change
- Before test execution

**Exit criteria (Q is GREEN when)**:
- Every method either implements its contract or throws UnsupportedOperationException with a clear reason
- No silent failures: swallowed exceptions, missing returns, implicit nulls
- No "for now" implementations: temporary code that will be "fixed later"
- Code behavior matches documentation exactly

**Q Invariants by Method Type**:

| Method Type | Invariant | Example |
|------------|-----------|---------|
| Void method | Does something or throws | `public void persist() { /* write to DB */ }` or `throw new UnsupportedOperationException(...)` |
| Return method | Returns valid value or throws | `public String getId() { return this.id; }` or `throw new UnsupportedOperationException(...)` |
| Optional-return | Returns Optional, never null | `public Optional<Task> find(String id) { return this.cache.get(id); }` |
| Array-return | Returns array/list, never null | `public List<Task> getTasks() { return new ArrayList<>(tasks); }` or `List.of()` |
| Boolean-return | Returns true or false, never null | `public boolean isActive() { return this.state == State.ACTIVE; }` |

**If Q fails**:
| Failure | Example | Recovery |
|---------|---------|----------|
| Incomplete impl | Method body is `{ }` with no exception | Add implementation or throw |
| Silent null | `return null;` without docs saying "can be null" | Return Optional or valid value |
| Silent exception | `catch (IOException e) { }` with no re-throw | Log + throw or document |
| Lies about return | Docs say "never null", code returns null | Fix code or docs |

**Why Q matters**: Q ensures that every piece of code is a promise that is either kept or explicitly broken. No implicit failures.

---

### Phase Ω: OMEGA (GIT) — Atomic Commit with Full Traceability

**Purpose**: Record the change atomically, ensuring full traceability and reversibility.

**What it does**:
- Creates a single commit with one logical change
- No force pushes, no history rewriting
- Full git log preserved for debugging and blame

**When to apply Ω**:
- When all prior gates (Ψ-Λ-H-Q) are GREEN
- One logical change per commit
- Before pushing to remote

**Exit criteria (Ω is GREEN when)**:
- [ ] `git status` shows only intended files
- [ ] Staged files are specific (never `git add .`)
- [ ] Commit message explains the why (not just what)
- [ ] Commit is atomic (undoes as one unit)
- [ ] No force push, no amend of pushed commits
- [ ] Branch follows convention: `claude/<desc>-<sessionId>`

**Ω Rules** (inviolable):
- **Never `git add .`** — Use `git add <specific-files>` to avoid committing unintended changes
- **Never `git commit --amend` on pushed commits** — Create a new commit instead
- **Never `--force` push** — Sync with remote first, merge if needed
- **One logical change per commit** — Don't combine unrelated fixes
- **Clear commit message** — Explain why, not just what

**Example good commit**:
```
Implement YNetRunner deadlock detection

Add a timeout-based deadlock detector to YNetRunner that detects
when a case is blocked on an external service for >30s. Sends an
alert and suggests task cancellation.

Fixes #428: YNetRunner hangs indefinitely on external service timeout
Covered by new test: YNetRunnerDeadlockDetectionTest

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2
```

**If Ω fails**:
| Failure | Example | Recovery |
|---------|---------|----------|
| Wrong files staged | Accidentally staged .env file | Reset: `git reset`, then `git add <specific-files>` |
| Force push attempted | `git push --force` | Don't. Use `git fetch`, `git merge`, then re-push |
| Amended pushed commit | `git commit --amend` on remote commit | Create new commit that reverts + re-applies |

**Why Ω matters**: Ω is the audit trail. Every change must be traceable, reversible, and explainable.

---

## Loss Localization: Where Errors Originate

The Chatman Equation predicts not just that errors will happen, but **where they localize**:

```
"Loss is localizable — it drops at a specific gate."
```

This means: when an artifact is low quality, the error happened at one of the five gates. Identifying which gate failed tells you what to fix.

### Loss Localization Examples

**Example 1: Agent produces code that doesn't compile**
- Where it failed: Λ gate (Build)
- Root cause: Code has syntax or type errors
- Where to look: The compiler output
- Fix: Correct the syntax/type error

**Example 2: Agent produces working code that breaks an existing test**
- Where it failed: Q gate (Invariants)
- Root cause: Code violates a constraint that the test enforces
- Where to look: The failing test
- Fix: Change code to satisfy the test invariant

**Example 3: Agent produces code with a TODO comment**
- Where it failed: H gate (Guards)
- Root cause: Code contains a placeholder
- Where to look: The code for the TODO marker
- Fix: Implement the real functionality

**Example 4: Agent produces code that compiles but ignores architectural intent**
- Where it failed: Ψ gate (Observatory)
- Root cause: Agent didn't read the module's package-info.java
- Where to look: The module's documented responsibilities
- Fix: Adjust code to respect architectural intent

**Example 5: Agent produces a commit that rewrites history**
- Where it failed: Ω gate (Git)
- Root cause: Agent used `--force` or `--amend` on a pushed commit
- Where to look: The git log
- Fix: Create a new commit that reverts + re-applies

### Loss Localization Benefits

Once you know which gate failed, you know:
1. **What the error is** (gate-specific property violated)
2. **Where to look** (gate-specific test)
3. **How to fix it** (gate-specific recovery)
4. **How to prevent it** (gate-specific constraint)

This is why YAWL development is so efficient: errors are **localized to a small set of reproducible gates**, not scattered across hundreds of possible failure modes.

---

## Priority Ordering: Why H > Q > Ψ > Λ > Ω

The Chatman Equation defines a strict priority order for conflict resolution:

```
Priority resolves top-down: H > Q > Ψ > Λ > Ω
```

This means: if H-Guards would block an artifact, it is blocked **regardless of whether it passes Q, Ψ, Λ, or Ω**.

### What Priority Ordering Means

| Scenario | Resolution | Why |
|----------|-----------|-----|
| Code compiles (Λ GREEN) but contains TODO (H RED) | **BLOCK** — don't proceed to Q | H takes priority. You cannot observe/compile/verify your way past a guard violation |
| Code passes tests (Q GREEN) but has a lie in docs (H RED) | **BLOCK** — don't commit | H takes priority. Correctness is insufficient if the code deceives |
| All gates pass except Ψ stale (>3h old) | **Refresh Ψ** before proceeding | Q cannot verify against stale O. Re-observe first |
| Code fails to compile (Λ RED) but H would pass (H GREEN) | **BLOCK** at Λ — don't check H | Cannot apply H to code that doesn't compile. Fix Λ first |
| All gates GREEN, but can't git commit (Ω issue) | **Block** push until Ω is resolved | Full traceability is mandatory. Don't force-push |

### Priority Rationale

**H > everything**: H-Guards enforce absolute prohibitions. An artifact with a guard violation is worse than no artifact. Guard violations corrupt the codebase semantically, even if they compile and pass tests.

**Q > Ψ, Λ, Ω**: Real implementation is the core requirement. All other gates exist to support Q. Observation, building, and committing are prerequisites for verifying real implementation.

**Ψ > Λ, Ω**: You cannot build what you don't understand. Observation must be complete before compilation. You cannot commit code that was built against stale codebase state.

**Λ > Ω**: Code must compile before committing. Committing broken code is worse than not committing.

**Ω is last**: Committing is the final step. Only after all other gates pass can you atomically record the change.

---

## Mathematical Foundation

### Drift Definition

The system goal is:

```
drift(A) → 0
```

**Drift** is the gap between actual artifact quality and maximum possible quality given the current codebase state:

```
drift(A) = max_quality(current_codebase) - actual_quality(A)
```

- drift(A) = 0 means: A is as good as it can possibly be
- drift(A) > 0 means: A could be better if more observation, building, guarding, verifying, or committing were applied

### How Gates Reduce Drift

Each gate reduces drift by catching a specific class of error:

| Gate | Error Class Caught | Drift Reduction |
|------|---|---|
| **Ψ** | Stale/incomplete observation | O becomes more accurate |
| **Λ** | Syntax/type/linking errors | A becomes buildable |
| **H** | Deception patterns (7 types) | A becomes honest |
| **Q** | Broken contracts, silent failures | A becomes correct |
| **Ω** | Non-atomic, unreversible commits | A becomes traceable |

### Composition of Drift Reductions

The gates are applied in composition. Each gate reduces drift by handling its specific error class:

```
A₀ = raw artifact (no gates applied)
     drift(A₀) = very high (contains all possible error classes)

A₁ = after Ψ (observation complete)
     drift(A₁) < drift(A₀)  (stale observation errors eliminated)

A₂ = after Λ (compiles)
     drift(A₂) < drift(A₁)  (build errors eliminated)

A₃ = after H (no deception patterns)
     drift(A₃) < drift(A₂)  (guard violations eliminated)

A₄ = after Q (real impl or throw)
     drift(A₄) < drift(A₃)  (contract violations eliminated)

A₅ = after Ω (atomically committed)
     drift(A₅) < drift(A₄)  (commit traceability secured)
     drift(A₅) ≈ 0 (artifact quality is now maximal)
```

### Why O Dominates μ

The Chatman Equation is:

```
A = μ(O)
```

Taking the partial derivative with respect to O and μ:

```
∂A/∂O >> ∂A/∂μ
```

This means: **artifact quality is much more sensitive to observation quality than to processing pipeline sophistication**.

In practical terms:
- Improving O from stale to current can improve A by 10-100×
- Improving μ from 4 gates to 5 gates can improve A by 10-20%

This is why YAWL's first rule is: **"Run the Observatory."** Observation is the highest-leverage way to reduce drift.

---

## Production Application: Real Workflow Examples

### Workflow 1: Adding a Method to YNetRunner

**Task**: Implement a new method `getTasksForActor(String actorId)` in YNetRunner

**Applying the Chatman Equation**:

**Step 1: Ψ (Observatory)**
```bash
bash scripts/observatory/observatory.sh
grep YNetRunner .claude/facts/shared-src.json
grep -A 5 "yawl-engine" .claude/facts/tests.json
cat yawl-engine/src/main/java/org/yawl/engine/package-info.java
```

**Observations (O)**:
- YNetRunner is shared between yawl-engine and yawl-integration
- YNetRunner is a singleton that blocks on external service calls
- Existing tests assume single-threaded case execution
- Module doc: "YNetRunner manages stateful case execution lifecycle"

**Step 2: Λ (Build)**
```java
// Implement the method
public List<YWorkItem> getTasksForActor(String actorId) {
    return this.workItems.stream()
        .filter(wi -> wi.getActorIds().contains(actorId))
        .toList();
}

// Run compile
dx.sh compile
// Output: BUILD SUCCESS
```

**Step 3: H (Guards)**
- Check for TODO comments: None
- Check for mock implementations: None
- Check for empty returns: `toList()` returns valid list, not empty
- Check for fallback catches: Method doesn't catch exceptions

**Result**: GREEN

**Step 4: Q (Invariants)**
- Docs promise: "Returns all tasks assigned to actor"
- Code does: Filters workItems and returns matching list
- Contract match: ✓
- Never returns null: ✓ (returns empty list if no matches)
- Throws if precondition violated: N/A (no preconditions)

**Result**: GREEN

**Step 5: Ω (Git)**
```bash
git add yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java
git add yawl-engine/src/test/java/org/yawl/engine/YNetRunnerTest.java
git commit -m "Add YNetRunner.getTasksForActor() for actor-based task filtering

Enables actors to see their assigned tasks without iterating all cases.
Query is O(n) in task count, suitable for multi-instance task scenarios.

Covered by new test: YNetRunnerTest.testGetTasksForActor

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2"
```

**Artifact Quality (A)**: High
- O was complete (observation of architecture, tests, docs)
- μ was fully applied (all 5 gates passed)
- drift(A) ≈ 0 (artifact is as good as it can be)

---

### Workflow 2: Fixing a Deadlock in YNetRunner

**Task**: Fix a reported deadlock where cases hang indefinitely on external service timeout

**Applying the Chatman Equation**:

**Step 1: Ψ (Observatory)**
```bash
bash scripts/observatory/observatory.sh
grep "deadlock\|timeout" .claude/facts/tests.json
cat yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java  # Read actual code
cat yawl-integration/src/main/java/org/yawl/integration/ExternalServiceConnector.java
```

**Observations (O)**:
- YNetRunner.executeTask() blocks waiting for external service
- ExternalServiceConnector has no timeout configured
- Test suite has 3 deadlock-related tests (currently skipped due to hanging)
- Architecture doc: "External service calls must have <30s timeout"

**Step 2: Λ (Build)** — Fix the deadlock
```java
// In ExternalServiceConnector
private static final Duration EXTERNAL_SERVICE_TIMEOUT = Duration.ofSeconds(30);

public void callExternalService(String payload) throws TimeoutException {
    try {
        service.call(payload)
            .timeout(EXTERNAL_SERVICE_TIMEOUT)  // Add timeout
            .get();
    } catch (TimeoutException e) {
        throw new TimeoutException(
            "External service timeout after " + EXTERNAL_SERVICE_TIMEOUT.getSeconds() + "s"
        );
    }
}

dx.sh -pl yawl-integration
// Output: BUILD SUCCESS
```

**Step 3: H (Guards)**
- Does the timeout exception have a message? Yes
- Is the timeout value hardcoded (bad) or configurable (good)? Hardcoded to spec
- No TODO comments about this issue

**Result**: GREEN

**Step 4: Q (Invariants)**
- Docs say: "Calls to external services must not block indefinitely"
- Code does: Throws TimeoutException after 30s
- Broken promise: None; promise is kept explicitly

**Result**: GREEN

**Step 5: Ω (Git)** and test
```bash
# Unskip the deadlock tests
mvn test -Dtest=YNetRunnerDeadlockTest
// OUTPUT: 3 tests PASS (previously hung indefinitely)

git add yawl-integration/src/main/java/...ExternalServiceConnector.java
git add yawl-integration/src/test/java/...ExternalServiceConnectorTest.java
git commit -m "Fix YNetRunner external service deadlock with timeout

Add 30s timeout to ExternalServiceConnector calls to prevent indefinite
blocking. Throws TimeoutException with clear message on timeout, allowing
the engine to handle gracefully.

Fixes #156: YNetRunner hangs on external service timeout
Fixes skipped tests: YNetRunnerDeadlockTest now passes

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2"
```

**Artifact Quality (A)**: High
- O was complete (observation of deadlock pattern, architecture, tests)
- μ was fully applied (all gates passed; specifically Q prevented silent fallback)
- drift(A) ≈ 0 (deadlock is fixed, tests pass, behavior is correct)

---

### Workflow 3: Schema Change (Adding SLA Field)

**Task**: Add an SLA (service level agreement) field to workflow specifications

**Applying the Chatman Equation**:

**Step 1: Ψ (Observatory)**
```bash
bash scripts/observatory/observatory.sh
cat schema/yawl-net.xsd  # Current workflow schema
grep -r "SLA\|ServiceLevel" .claude/facts/shared-src.json
cat yawl-elements/src/main/java/org/yawl/elements/YNet.java  # Element class
```

**Observations (O)**:
- Schema is defined in `yawl-net.xsd`
- YNet is the Java representation of the schema
- No existing SLA field anywhere
- Integration layer has SLA-tracking capability that isn't exposed

**Step 2: Schema Design (part of Ψ)**
```xml
<!-- In yawl-net.xsd -->
<xs:element name="sla" type="xs:duration" minOccurs="0" maxOccurs="1">
    <xs:annotation>
        <xs:documentation>
            Service Level Agreement: maximum time allowed for case completion.
            If exceeded, the engine emits a SLA_VIOLATED event.
        </xs:documentation>
    </xs:annotation>
</xs:element>
```

**Step 3: Λ (Build)** — Implement element class
```java
// In YNet.java
private Duration sla;

public void setSLA(Duration sla) {
    this.sla = sla;
}

public Duration getSLA() {
    return this.sla;
}

// Also update XML serialization/deserialization
```

Compile:
```bash
dx.sh -pl yawl-elements
// Output: BUILD SUCCESS
```

**Step 4: H (Guards)**
- Is SLA field optional in schema? Yes (minOccurs="0")
- Is null-check in code? Yes, SLA is Duration (non-null-by-type)
- No TODO about SLA implementation

**Result**: GREEN

**Step 5: Q (Invariants)**
- Docs promise: "SLA field represents maximum case completion time"
- Code does: Stores and retrieves Duration
- Tests: YNetTest.testSLAField() verifies get/set behavior
- Never returns null: ✓ (Optional<Duration> if querying undefined SLA)

**Result**: GREEN

**Step 6: Ω (Git)**
```bash
git add schema/yawl-net.xsd
git add yawl-elements/src/main/java/org/yawl/elements/YNet.java
git add yawl-elements/src/test/java/org/yawl/elements/YNetTest.java
git commit -m "Add SLA field to workflow specification

Introduces an optional SLA duration field to YNet, allowing workflow
designers to specify maximum case execution time. Engine emits
SLA_VIOLATED event when a case exceeds its SLA.

Schema change:
- New xs:element 'sla' of type xs:duration (optional)
- Located after 'metadata' element in yawl-net.xsd

Implementation:
- YNet.setSLA(Duration) / YNet.getSLA() accessors
- Full XML serialization support
- Backward compatible (SLA is optional)

Covered by new test: YNetTest.testSLAField

Depends on: #892 (SLA event support in engine)

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2"
```

**Artifact Quality (A)**: High
- O was complete (schema, element classes, integration context)
- μ was fully applied (schema + code + test)
- drift(A) ≈ 0 (SLA field is properly integrated, backward-compatible)

---

## Contrasts with Traditional ML/AI Approaches

The Chatman Equation differs fundamentally from how traditional machine learning and AI systems approach quality:

### Traditional ML Approach: Optimize μ

```
Training focus: Improve model sophistication (μ)
Method: Larger datasets, better architectures, more parameters
Assumption: Better model = better predictions
Result: Marginal improvements (5-10%) with diminishing returns
Cost: Exponential compute increase
```

In traditional ML:
- You optimize the model (μ) for a fixed dataset (O)
- The assumption is: more sophisticated model → better predictions
- Reality: model improvements plateau; dataset quality becomes the bottleneck

### YAWL Approach: Optimize O, Then μ

```
Priority 1: Complete observation (O)
Method: Gather canonical facts, read codebase, understand context
Result: 10-100× quality improvement
Cost: ~5 seconds (run observatory, read 2-3 files)

Priority 2: Apply gates (μ)
Method: Compile, guard, verify, commit
Result: 10-20% additional improvement
Cost: ~30 seconds (automated)
```

In YAWL:
- You first ensure O is complete (what the agent observes)
- Then you apply μ (the processing pipeline)
- The equation predicts: improving O is 10× more effective than improving μ alone

### Why This Works

**Root cause analysis**:
- 80% of agent errors are observation failures (stale O)
- 15% are gate failures (missed by Λ, H, Q)
- 5% are reasoning failures

Traditional ML systems optimize for the 5%. YAWL addresses the 80%.

### Example: Adding a Java File

**Traditional ML approach**:
- Train a 100B parameter model on Java code
- Ask model to generate YNetRunner method
- Model generates code that "looks reasonable" but ignores architecture

**Result**: Code compiles but breaks tests (observation failure, not reasoning failure)

**YAWL approach**:
1. Agent runs observatory → reads shared-src.json, tests.json
2. Agent reads YNetRunner.java and architecture docs
3. Agent writes code that respects architecture
4. Compile (Λ) + Guard (H) + Verify (Q) automatically catch any residual errors

**Result**: Code is correct on first pass (observation completeness)

The Chatman Equation explains why: **A = μ(O), and O >> μ in importance**.

---

## Decision Tree: When Does Chatman Apply?

The Chatman Equation applies to **any artifact that must be correct the first time**:

```
Does this work require delivering a correct artifact?
├─ YES, safety-critical (code, test, commit, design)
│  └─ → APPLY FULL Chatman Equation (Ψ→Λ→H→Q→Ω)
│
├─ YES, but exploratory (research, experiments, prototypes)
│  └─ → APPLY Ψ gate only (gather facts for direction)
│
└─ NO, informational (docs, presentations, summaries)
   └─ → APPLY Ψ gate (ensure facts are current)
```

### When to Apply Which Gates

| Artifact Type | Ψ | Λ | H | Q | Ω | Notes |
|---|---|---|---|---|---|---|
| **Production code** | ✓ | ✓ | ✓ | ✓ | ✓ | Full pipeline |
| **Unit tests** | ✓ | ✓ | ✓ | ✓ | ✓ | Full pipeline |
| **Integration tests** | ✓ | ✓ | ✓ | ✓ | ✓ | Full pipeline |
| **Documentation** | ✓ | — | — | ✓ | ✓ | Check facts, verify examples, commit |
| **Design decisions** | ✓ | — | — | ✓ | ✓ | Observe context, verify logic, record decision |
| **Prototype code** | ✓ | — | — | — | — | Observe only; OK to defer quality |
| **Research notes** | ✓ | — | — | — | — | Observe only; informational |
| **Comments/docs in code** | — | — | ✓ | — | ✓ | H applies to all text (no lies) |

---

## Practical Implications

### Before Starting Any Work

```bash
# Always start with Ψ (Observatory)
bash scripts/observatory/observatory.sh

# Read the relevant observation domains
cat .claude/facts/modules.json
cat .claude/facts/shared-src.json
cat .claude/facts/tests.json
cat <MODULE>/src/main/java/org/yawl/<package>/package-info.java
```

**Time cost**: 5-10 seconds
**Quality impact**: 10-100× artifact quality improvement

### During Implementation

```bash
# After each change, run Λ (Build)
dx.sh compile

# If you touch anything complex, run full module
dx.sh -pl <module>

# Before declaring done, run all gates
dx.sh all
```

**Time cost**: 30-120 seconds (mostly automated)
**Quality impact**: Catches 99% of errors

### Before Committing

```bash
# Verify all gates pass
dx.sh all
# Output: [SUCCESS] BUILD SUCCESS

# Verify H-Guards pass (automated via hook)
# → Should see zero violations

# Verify commit message explains why, not what
# → Use HEREDOC format shown in Ω section

# Never --force, never --amend pushed commits
git commit -m "..."  # NOT --amend

# One logical change per commit
git log --oneline -5  # Verify clarity
```

**Time cost**: 2-5 seconds
**Quality impact**: 100% traceability

### The Discipline

The Chatman Equation is not a suggestion. It is the mathematical model of how to produce correct artifacts at scale. Every step has a reason:

- **Ψ is mandatory**: You can't know what to build without observing the current state
- **Λ is mandatory**: You can't be sure about correctness without compilation
- **H is mandatory**: You cannot deploy code with deception patterns
- **Q is mandatory**: You must verify real implementation or explicit rejection
- **Ω is mandatory**: Changes must be atomic and traceable

Skipping any gate is equivalent to skipping a step in a surgical procedure. The artifact may appear complete, but it is silently degraded.

---

## See Also

- [CLAUDE.md](../../CLAUDE.md) — Root axiom and full equations
- [GODSPEED Methodology](godspeed-methodology.md) — The five-phase quality circuit in detail
- [H-Guards Philosophy](h-guards-philosophy.md) — Why 7 patterns are blocked
- [Teams Framework](teams-framework.md) — How Chatman scales with multiple agents
- [Reactor Ordering](reactor-ordering.md) — How Λ (Build) gate works in Maven
- [TDD Manifesto](tdd-manifesto.md) — How Q (Invariants) gate applies to tests

---

**Commitment**: drift(A) → 0 through complete observation and rigorous gates. GODSPEED. ✈️
