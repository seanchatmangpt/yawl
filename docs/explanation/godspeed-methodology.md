# GODSPEED: The Five-Phase Quality Circuit

**Quadrant**: Explanation | **Concept**: The production-quality verification circuit for code changes

This document explains GODSPEED, the five-phase quality assurance circuit that every artifact must pass through before being committed to the codebase. GODSPEED is not a suggestion or best practice — it is the mandatory verification circuit that ensures every change meets production standards.

The name comes from the final line of CLAUDE.md: "GODSPEED. ✈️ | Compile ≺ Test ≺ Validate ≺ Deploy | drift(A) → 0"

---

## Executive Summary

The GODSPEED circuit consists of five sequential phases, applied in order:

```
Ψ → Λ → H → Q → Ω
(Observatory → Build → Guards → Invariants → Git)
```

Each phase has a specific purpose, specific entry criteria, and specific exit conditions. The phases **cannot be skipped** — an artifact that passes Ψ and Λ but is blocked at H cannot proceed to Q. The gates are sequential by design.

| Phase | Symbol | Name | What It Does | Why It Matters |
|-------|--------|------|-------------|----------------|
| **Ψ** | Psi | Observatory | Sync facts with codebase | Can't write correct code without knowing what exists |
| **Λ** | Lambda | Build | Compile & link | If it doesn't compile, it doesn't work |
| **H** | H-Guards | Guards | Block anti-patterns | Catch deception patterns before logic runs |
| **Q** | Q-Invariants | Invariants | Verify real implementation | Code must do what it claims or throw loudly |
| **Ω** | Omega | Git | Atomic commit | Changes must be traceable and reversible |

**Total time for a typical change**: 5-30 seconds (Ψ-Λ-H-Q are automated, Ω is manual).

**Cost of skipping a phase**: The error that phase was designed to catch will reach production.

---

## Phase 1: Ψ OBSERVATORY — Observe Before You Change

### Purpose

Before writing or modifying any code, you must sync your understanding with the actual codebase state. The Observatory phase ensures that your observation set O is complete and current.

### What It Does

The Observatory phase gathers **canonical facts** about the codebase:

- **modules.json**: All Maven modules, their dependencies, and test status
- **gates.json**: Which quality gates are applicable to each module
- **deps-conflicts.json**: Transitive dependency conflicts (helps catch silent failures)
- **reactor.json**: Maven reactor order and compilation dependencies
- **shared-src.json**: Files shared between multiple modules (prevents simultaneous edits)
- **tests.json**: Test coverage by module (helps you know where to add tests)
- **dual-family.json**: Stateful vs stateless engine code families (architecture-specific)
- **duplicates.json**: Duplicate code that should be refactored
- **maven-hazards.json**: Maven anti-patterns (shade conflicts, classpath ordering)

### Key Metrics

| Metric | Why It Matters |
|--------|---|
| **Facts compression** | ~50 tokens per fact file vs ~5000 tokens to grep equivalent = **100× faster observation** |
| **Freshness** | Facts older than 2 hours are considered stale; >3 files explored triggers refresh |
| **Completeness** | Observation set O incomplete → artifact quality A drops |

### When to Run Ψ

```
Before starting work:
├─ bash scripts/observatory/observatory.sh
│  (≈5-10 seconds, generates/updates .claude/facts/*.json)
│
After exploring >3 files:
├─ Re-run observatory.sh
│  (facts may have drifted)
│
Before team hand-off:
└─ Verify receipts/observatory.json SHA256
   (ensure facts are not stale)
```

### Exit Criteria (Ψ is GREEN when)

- [ ] modules.json parsed and validated
- [ ] All 9 fact files exist and are current
- [ ] No SHA256 errors (indicates file corruption)
- [ ] Observatory script exits 0

### If Ψ Fails

| Failure | Recovery |
|---------|----------|
| **Missing fact file** | Run observatory.sh again (transient IO error) |
| **SHA256 mismatch** | Files were modified during generation; re-run |
| **Stale facts** | Normal; refresh with observatory.sh |
| **Network error** | If using CLAUDE_CODE_REMOTE=true, check connectivity |

### Practical Example: Editing a Module

You want to add a new method to `YNetRunner`:

1. **Ψ.1: Run observatory**
   ```bash
   bash scripts/observatory/observatory.sh
   ```
   (Generates modules.json, checks that yawl-engine exists, has correct deps)

2. **Ψ.2: Check shared-src.json**
   ```bash
   grep YNetRunner .claude/facts/shared-src.json
   ```
   → If shared, you cannot edit simultaneously with teammates

3. **Ψ.3: Check tests.json**
   ```bash
   grep yawl-engine .claude/facts/tests.json
   ```
   → See existing test count, decide where to add new test

4. **Ψ.4: Read package-info.java**
   ```bash
   cat /home/user/yawl/yawl-engine/src/main/java/org/yawl/engine/package-info.java
   ```
   → Understand module's architectural role before modifying

**Result**: You now have accurate, complete O. You can proceed to Λ.

---

## Phase 2: Λ BUILD — Compile, Link, Verify Buildability

### Purpose

Verify that the artifact compiles and links. Compilation is the first material gate: if code doesn't compile, nothing else matters.

### What It Does

The Build phase runs Maven through three levels of strictness:

```
Level 1: dx.sh compile
└─ Fast compile for modified files only
   (≈5-15s for typical change)
   ├─ Syntax errors caught
   ├─ Type errors caught
   └─ Not re-built: unchanged modules

Level 2: dx.sh -pl <module>
└─ Full rebuild of one module + dependencies
   (≈10-30s)
   ├─ All Λ gates apply to this module
   ├─ But not: downstream modules that depend on it
   └─ Good for: isolated module work

Level 3: dx.sh all
└─ Clean full rebuild of entire reactor
   (≈2-5 minutes)
   ├─ All Λ gates apply to all modules
   ├─ Catches transitive dependency issues
   └─ Mandatory: before commit
```

### Key Concepts

**Compile Order Matters**: Maven builds modules in dependency order (via reactor). If module B depends on A, module A must compile first. The reactor.json fact file tells you this order. If you change module A, you must re-run dx.sh (not just recompile A in isolation) to ensure B still compiles against the new A.

**Test Phase**: Λ includes running tests. A compilation that succeeds but tests fail still fails Λ. This is intentional: your change may have broken downstream code that depends on the old behavior.

### Λ Hierarchy

```
dx.sh compile
    ↓
dx.sh -pl <module>
    ↓
dx.sh all  ← Mandatory pre-commit
```

### Exit Criteria (Λ is GREEN when)

- [ ] All modified modules compile without errors
- [ ] All tests in modified modules pass (no failures, no errors)
- [ ] Reactor order respected (dependencies built first)
- [ ] No warnings in `-Werror` mode (if enabled)
- [ ] `dx.sh` exits 0

### If Λ Fails

| Failure | Recovery |
|---------|----------|
| **Syntax error** | Fix the syntax, re-run `dx.sh compile` |
| **Type error** | Fix the type signature, re-run `dx.sh compile` |
| **Test failure** | Fix the test or the code, re-run `dx.sh -pl <module>` |
| **Transitive issue** | Change may have broken downstream module; run `dx.sh all` |
| **Dependency conflict** | Check maven-hazards.json for known conflicts |

### Practical Example: Modifying YWorkItem

You edited `/home/user/yawl/yawl-elements/src/main/java/org/yawl/elements/YWorkItem.java`:

```bash
# Step 1: Quick compile of changed files
bash scripts/dx.sh compile
# Output: BUILD SUCCESS (5s)
# ✓ YWorkItem.java compiles
# ✓ No syntax/type errors

# Step 2: Full module rebuild + tests
bash scripts/dx.sh -pl yawl-elements
# Output: BUILD SUCCESS (15s)
# ✓ yawl-elements and its dependencies rebuild
# ✓ All yawl-elements tests pass
# ✓ No breakage in yawl-elements itself

# Step 3: Full reactor (required before commit)
bash scripts/dx.sh all
# Output: BUILD SUCCESS (3m)
# ✓ All modules rebuild in order
# ✓ All tests pass everywhere
# ✓ Downstream modules still work with modified YWorkItem
```

**Result**: Λ is GREEN. You can proceed to H.

---

## Phase 3: H GUARDS — Block Anti-Patterns

### Purpose

Detect and block seven forbidden deception patterns before they enter the codebase. H-Guards are **automatic** — they fire on Write|Edit, not by explicit command.

### What It Does

H-Guards checks your code for patterns that violate the principle **"real_impl ∨ throw UnsupportedOperationException"**. These patterns are deceptive: they appear to work but don't.

| Pattern | Name | Example | Why It's Blocked |
|---------|------|---------|------------------|
| **H_TODO** | Deferred work marker | `// TODO: implement this` | Signals intent without action |
| **H_MOCK** | Mock name in prod | `class MockDataService` | Code pretends to be something it isn't |
| **H_STUB** | Stub return from non-void | `public String get() { return ""; }` | Looks valid but is empty |
| **H_EMPTY** | Empty method body | `public void init() { }` | No-op masquerading as initialization |
| **H_FALLBACK** | Silent catch-and-fake | `catch(E e) { return Collections.empty...` | Hides error from caller |
| **H_LIE** | Code ≠ documentation | `/** @returns never null */ ... return null;` | Documentation is false |
| **H_SILENT** | Log instead of throw | `log.error("Not implemented")` | Error disappears at runtime |

### Key Principle

H-Guards enforce **Jidoka** (stop-the-line quality): the moment a forbidden pattern is written, the tool blocks further progress. You cannot silence or suppress a guard violation.

### When H Fires

```
You: Edit YWorkItem.java
     ↓
PostToolUse Hook:
     ├─ Scans your changes for H patterns
     │  (regex + AST analysis)
     │
     └─ If violations found:
        ├─ Exit code: 2 (fatal)
        ├─ Message: "H_TODO violation at line 427"
        ├─ Block: You cannot proceed to Q
        └─ Fix: Implement real logic or throw
```

### The Two Legal Outcomes

When you encounter a missing capability, you have **exactly two choices**:

**Choice A: Implement it now**
```java
public String getWorkflowId() {
    // Real implementation
    return this.netInstance.getSpecId();
}
```

**Choice B: Declare it unsupported**
```java
public String getWorkflowId() {
    throw new UnsupportedOperationException(
        "getWorkflowId: requires NetInstance integration. " +
        "See issue #1234 for implementation plan.");
}
```

**Not a choice**: Empty return, stub, fake data, silent fallback. These are violations of the contract that code must honestly signal its capabilities.

### Exit Criteria (H is GREEN when)

- [ ] No H_TODO violations
- [ ] No H_MOCK violations
- [ ] No H_STUB violations
- [ ] No H_EMPTY violations
- [ ] No H_FALLBACK violations
- [ ] No H_LIE violations
- [ ] No H_SILENT violations
- [ ] hyper-validate.sh hook exits 0

### If H Fails

| Violation | Recovery |
|-----------|----------|
| **H_TODO** | Implement real logic or throw UnsupportedOperationException |
| **H_MOCK** | Delete mock class or implement real service |
| **H_STUB** | Implement real return or throw exception |
| **H_EMPTY** | Implement real logic or throw exception |
| **H_FALLBACK** | Propagate exception instead of faking data |
| **H_LIE** | Update code to match documentation |
| **H_SILENT** | Throw exception instead of logging |

### Philosophical Justification

Why does H block and not warn?

**Warning fatigue**: A developer who sees 47 warnings becomes habituated and misses the 48th. A hard block creates zero habituation — each block is fresh.

**Runtime safety**: A stub that returns `""` compiles, passes unit tests, and fails silently in production. An `UnsupportedOperationException` tells you immediately where the gap is.

**Production honesty**: Code should never claim to do something it doesn't. H-Guards enforce this honesty at write time, not at code review or production incident time.

### Practical Example: Adding a Method

You're adding a method to YWorkItem:

```java
public class YWorkItem {
    // ... existing code ...

    public String getTraceId() {
        // TODO: add tracing support
        return null;
    }
}
```

**Result of Ψ-Λ-H run**:
1. ✓ Ψ: Observatory shows tracing module exists
2. ✓ Λ: Code compiles, tests pass
3. ✗ **H: H_TODO violation at line 427**
   - Message: `// TODO: add tracing support`
   - Block: Cannot proceed to Q
   - Action required: Choose A or B

**Option A: Implement now**
```java
public String getTraceId() {
    return this.tracingContext.getTraceId();
}
```
→ Re-run H hook → ✓ GREEN

**Option B: Declare unsupported**
```java
public String getTraceId() {
    throw new UnsupportedOperationException(
        "getTraceId: requires tracing context initialization. " +
        "See ADR-015 for integration plan. " +
        "Enable with: System.setProperty('yawl.trace.enabled', 'true')");
}
```
→ Re-run H hook → ✓ GREEN

Either choice is fine. The forbidden choice is leaving the TODO.

---

## Phase 4: Q INVARIANTS — Verify Real Implementation

### Purpose

Verify that code implements real behavior, not just mock or stub logic. Q-Invariants is the **semantic validation** phase: it understands what the code claims to do and verifies that it actually does it.

### What It Does

Q-Invariants checks your code for logical correctness:

- **No mock behavior**: If code uses Mockito or returns hardcoded test data, it fails Q
- **Real dependencies**: Code must use real implementations or throw, never fake
- **Correct algorithms**: Code must implement the contract implied by its name and signature
- **Test coverage**: Changes must be tested with real tests (Chicago TDD, not Mockito stubs)

### Key Principle

**"real_impl ∨ throw UnsupportedOperationException"** — a method either implements real logic or it throws loudly. No third option.

### Entry Criteria for Q

The artifact must:
- [ ] Pass Ψ (facts are current)
- [ ] Pass Λ (compiles and tests pass)
- [ ] Pass H (no anti-patterns)

If any of these fail, Q is not run.

### Exit Criteria (Q is GREEN when)

- [ ] `dx.sh all` runs without errors (includes test execution)
- [ ] No mock/stub implementations in production code
- [ ] New code has test coverage (>80% for modified methods)
- [ ] Tests use real objects (Chicago TDD), not mocks
- [ ] SpotBugs/PMD analysis passes (static analysis)

### If Q Fails

| Failure | Recovery |
|---------|----------|
| **Test fails** | Fix the test or the code, re-run tests |
| **Coverage low** | Add tests for new/modified logic |
| **Mock in prod** | Delete Mockito import, use real object |
| **SpotBugs warning** | Fix the issue or suppress with annotation + comment |

### Practical Example: Adding Cache Logic

You want to add caching to YNetRunner:

**Attempt 1: With Mockito (will fail Q)**
```java
public class CachedNetRunner {
    @Mock private NetworkService network;

    public Specification getSpec(String id) {
        // Using mock in production code
        return network.fetch(id);
    }
}
```

**Result**:
- ✓ Ψ: Observatory OK
- ✓ Λ: Compiles
- ✓ H: No TODO/mock/stub patterns... wait, H catches this too
- ✗ **H: H_MOCK violation** (Mockito import in src/main/java)

**Attempt 2: With real objects (will pass Q)**
```java
public class CachedNetRunner {
    private final NetworkService network;  // Injected at runtime
    private final Map<String, Specification> cache = new ConcurrentHashMap<>();

    public CachedNetRunner(NetworkService network) {
        this.network = Objects.requireNonNull(network);
    }

    public Specification getSpec(String id) {
        return cache.computeIfAbsent(id, this::fetchFromNetwork);
    }

    private Specification fetchFromNetwork(String id) {
        return network.fetch(id);  // Real service, not mock
    }
}
```

**Test: Using real objects (Chicago TDD)**
```java
@Test
public void testGetSpecCaches() {
    // Real NetworkService using H2 database
    NetworkService network = new InMemoryNetworkService();
    CachedNetRunner runner = new CachedNetRunner(network);

    Specification spec1 = runner.getSpec("W-1");
    Specification spec2 = runner.getSpec("W-1");  // Should be cached

    // Verify: called network only once
    assertEquals(1, network.getCallCount());
    assertSame(spec1, spec2);  // Same object (cached)
}
```

**Result**:
- ✓ Ψ: Observatory OK
- ✓ Λ: Compiles, tests pass
- ✓ H: No anti-patterns
- ✓ **Q: Real implementation, real tests**

---

## Phase 5: Ω GIT — Atomic Commit with Session ID

### Purpose

Record the change atomically in Git with full traceability. A commit is the final artifact — it must be reversible, auditable, and traceable to the session that produced it.

### What It Does

Ω creates a single logical commit with:
- **Atomic change**: One logical change per commit, not multiple unrelated changes
- **Session ID**: Branch name includes session ID for traceability
- **Specific files**: `git add <file1> <file2>` (never `git add .`)
- **Descriptive message**: Message explains the why, not the what
- **No force push**: Changes are permanent and reversible

### Commit Format

```
Branch: claude/<description>-<sessionId>
        └─ Example: claude/add-trace-context-01SfdxrP7PZC8eiQQws7Rbz2

Commit message:
    One-line summary (imperative, <70 chars)

    Paragraph explaining the why (what problem does this solve?)

    https://claude.ai/code/session_<sessionId>

Files included:
    git add src/main/java/org/yawl/elements/YWorkItem.java
    git add src/test/java/.../YWorkItemTest.java

    (NOT: git add .)
```

### Example Commit

```bash
git add src/main/java/org/yawl/elements/YWorkItem.java \
        src/test/java/org/yawl/elements/YWorkItemTest.java

git commit -m "Add trace context tracking to YWorkItem

YWorkItem instances now carry correlation IDs for distributed tracing.
Enables end-to-end trace visibility in heterogeneous system topologies.

Fixes: ADR-012 (tracing architecture)
See: .claude/facts/requirements.json - trace-context feature

https://claude.ai/code/session_01SfdxrP7PZC8eiQQws7Rbz2"
```

### Exit Criteria (Ω is GREEN when)

- [ ] Changes committed to git
- [ ] Branch name includes session ID
- [ ] Commit message explains the why
- [ ] Only relevant files included (not build artifacts, not `.idea/`, etc.)
- [ ] Commit can be reverted cleanly (`git revert <sha>` works)

### If Ω Fails

| Failure | Recovery |
|---------|----------|
| **Merge conflict** | Fetch latest, rebase or merge, resolve conflicts |
| **Permission denied** | Configure git credentials, retry |
| **Dirty working tree** | Commit or discard uncommitted changes, retry |
| **Branch doesn't exist** | Create it: `git checkout -b claude/<name>-<sessionId>` |

### Channels: What Can Be Committed

**Emit channels** (safe to commit directly):
- `src/` — source code
- `test/` — test code
- `schema/` — XSD schemas
- `.claude/` — session data, receipts

**Non-emit channels** (ask first):
- `docs/` — documentation (may need review)
- `pom.xml` — build configuration (affects all modules)
- `*.md` at root — project-wide docs
- Architecture decisions

### Why Ω Matters

Git is your audit trail. A clean commit history is the difference between "I can bisect to find which commit broke this" and "I have no idea when this broke." Every commit Ω produces is:

- **Reversible**: `git revert <sha>` gets you back
- **Traceable**: `git log` shows why the change was made
- **Auditable**: Session ID links to your work context
- **Bisectable**: `git bisect` can pinpoint the breaking commit

---

## How to Read GODSPEED Output

When you run the Ψ-Λ-H-Q-Ω circuit, here's what you see:

### Success Case (All Phases GREEN)

```
$ bash scripts/dx.sh all
[INFO] --- Observatory Phase (Ψ) ---
[✓] Observatory facts current (2026-02-28T11:07:00Z)
[✓] modules.json: 45 modules, 127 dependencies
[✓] shared-src.json: 0 conflicts with staged changes

[INFO] --- Build Phase (Λ) ---
[✓] Compiling yawl-elements (1/3)
[✓] Compiling yawl-engine (2/3)
[✓] Compiling yawl-integration (3/3)
[✓] Running tests (189 tests)
[INFO] BUILD SUCCESS (2m 34s)

[INFO] --- Guards Phase (H) ---
[✓] Scanning 42 modified files for anti-patterns
[✓] H_TODO: 0 violations
[✓] H_MOCK: 0 violations
[✓] H_STUB: 0 violations
[✓] H_EMPTY: 0 violations
[✓] H_FALLBACK: 0 violations
[✓] H_LIE: 0 violations
[✓] H_SILENT: 0 violations
[✓] hyper-validate.sh: exit 0

[INFO] --- Invariants Phase (Q) ---
[✓] SpotBugs analysis: 0 warnings
[✓] PMD analysis: 0 violations
[✓] Test coverage: 87% (yawl-elements)
[✓] No mock implementations in production code

[INFO] --- Git Phase (Ω) ---
[Branch] claude/add-trace-context-01SfdxrP7PZC8eiQQws7Rbz2
[Ready to commit]

>>> All phases GREEN. Ready for:
    git add src/...
    git commit -m "Add trace context..."
    git push origin claude/add-trace-context-01SfdxrP7PZC8eiQQws7Rbz2
```

### Failure Case (Blocked at H)

```
$ bash scripts/dx.sh all
[INFO] --- Observatory Phase (Ψ) ---
[✓] Observatory facts current

[INFO] --- Build Phase (Λ) ---
[✓] Compiling (3/3)
[✓] Running tests (189 tests pass)
[INFO] BUILD SUCCESS (2m 34s)

[ERROR] --- Guards Phase (H) ---
[✗] H_TODO violation detected
    File: src/main/java/org/yawl/engine/YNetRunner.java
    Line: 427
    Content: // TODO: Add deadlock detection

[Fix guidance]
Either:
  1. Implement the logic now (recommended)
  2. Replace with: throw new UnsupportedOperationException(...)

[Cannot proceed to Q until H violations are fixed]

>>> BLOCKED at H. Fix the violations and re-run.
```

### Failure Case (Failed at Λ)

```
$ bash scripts/dx.sh all
[INFO] --- Observatory Phase (Ψ) ---
[✓] Observatory facts current

[ERROR] --- Build Phase (Λ) ---
[✗] Compilation error in yawl-engine
    File: src/main/java/org/yawl/engine/YNetRunner.java
    Line: 312
    Error: Cannot find symbol 'getSpecId'

Downstream modules depend on this compilation:
  - yawl-integration (waiting for yawl-engine)
  - yawl-stateless-engine (waiting on yawl-engine)

[Fix guidance]
1. Check that method signature matches the contract
2. Verify shared-src.json (method may be in shared source)
3. Re-run: bash scripts/dx.sh compile

>>> BLOCKED at Λ. Fix the compilation error and re-run.
```

---

## Decision Tree: Which Phase Applies When?

```
You are about to make a change.

[1. Which phase?]
    ├─ "I've never looked at this module"
    │   → Run Ψ (Observatory)
    │   └─ bash scripts/observatory/observatory.sh
    │
    ├─ "I've modified files, haven't compiled yet"
    │   → Run Ψ then Λ (Build)
    │   └─ bash scripts/dx.sh compile
    │
    ├─ "I've compiled, but got H warnings"
    │   → Fix H violations
    │   └─ Delete TODO, implement real logic, or throw
    │
    ├─ "I've fixed H violations, tests are failing"
    │   → Tests are part of Λ (Build), fix test failures
    │   └─ bash scripts/dx.sh -pl <module>
    │
    ├─ "Everything compiles, tests pass, I'm ready to commit"
    │   → Run full circuit Ψ-Λ-H-Q-Ω
    │   └─ bash scripts/dx.sh all
    │
    └─ "I'm committing my change"
        → Run Ω (Git)
        └─ git add <specific files>
           git commit -m "..."
           git push ...

[2. Can I skip a phase?]
    No. Every phase must pass. The order is:
    Ψ (observe) ≺ Λ (build) ≺ H (guard) ≺ Q (invariants) ≺ Ω (commit)

    Skipping any phase means its gate's error class reaches production.

[3. How often to re-run?]
    Ψ: After exploring >3 files or after significant time passes
    Λ: After every edit (use dx.sh compile for speed)
    H: Automatic on Write|Edit (don't manually run)
    Q: Before committing (part of dx.sh all)
    Ω: Once per logical change
```

---

## Loss Localization: Where Did It Fail?

GODSPEED's superpower is **loss localization** — when something goes wrong, you know exactly which phase failed and why.

```
Problem: "My code works locally but fails in production"

Phase Analysis:
├─ Ψ failed?
│   → You didn't observe the actual codebase
│      (stale facts, didn't read package-info.java)
│
├─ Λ failed?
│   → You changed something that breaks downstream
│      (didn't run dx.sh all before shipping)
│
├─ H failed?
│   → You shipped a deception pattern
│      (TODO, mock, stub, silent fallback)
│      → Fix: remove the pattern
│
├─ Q failed?
│   → You shipped untested code
│      (tests pass locally but fail in integration)
│      → Fix: add real tests with real objects
│
└─ Ω failed?
    → You have a commit history mess
       (force pushed, amended commits, lost context)
       → Fix: use clean commits with session IDs
```

Each phase drops at a specific gate. If you passed all five, the error is not in GODSPEED — the error is in human reasoning about what the code should do.

---

## Common Patterns & Questions

### Q: How long does GODSPEED take?

**A**: 5-30 seconds for Ψ-Λ-H-Q, plus the time for you to write code.

- Ψ (Observatory): 5-10 seconds
- Λ (Build): 5-15 seconds (incremental), 2-5 minutes (full)
- H (Guards): <1 second (automatic hook)
- Q (Invariants): <1 second (hook, if no violations)
- Ω (Git): 10 seconds (your typing time)

Total overhead: ~30-60 seconds per change.

### Q: What if I'm in a hurry and want to skip H?

**A**: You can't. H is a hard block. But if your change is simple, you can skip expensive parts:

- Use `dx.sh compile` instead of `dx.sh -pl` to skip re-testing unchanged modules
- Push to a branch and let CI run the full suite while you work on the next task

You still cannot skip H — the hook will block you.

### Q: What if I have a legitimate empty method?

**A**: You have two choices:

1. **Implement it now** (recommended):
   ```java
   public void initialize() {
       // No-op is intentional: subclasses override this
       // See subclass implementations in yawl-integration/
   }
   ```

2. **Throw if called** (safest):
   ```java
   public void initialize() {
       throw new UnsupportedOperationException(
           "initialize: must be overridden by subclass. " +
           "See AbstractWorkflowEngine for pattern.");
   }
   ```

The hook treats empty methods as violations because they are almost always mistakes. If you have a legitimate one, document it in code.

### Q: My team is working on the same file. How does GODSPEED handle that?

**A**: Check `shared-src.json` before you start. If the file is shared:

- Run `shared-src.json` to see who else might edit it
- Consider refactoring to reduce sharing
- If unavoidable, coordinate commits (one person at a time)

Ψ will alert you to conflicts before they happen.

### Q: Can I work around an H violation?

**A**: No. H is a hard block. The Post-Tool hook will prevent your Write|Edit from completing if there's an H violation.

If you think the violation is a false positive:
1. Document why it's legitimate in code comments
2. Ask a teammate to review the suppression
3. Add it to the (rare) exception list in hyper-validate config

This requires explicit approval, not silence.

---

## Relationship to Other Systems

### How GODSPEED Relates to the Chatman Equation

GODSPEED is the concrete implementation of the Chatman Equation's processing pipeline:

```
A = μ(O)
  = (Ω ∘ Q ∘ H ∘ Λ ∘ Ψ)(O)

Where:
  A = artifact quality (your commit)
  μ = processing pipeline (the five phases)
  O = observation set (codebase facts)
```

GODSPEED ensures that μ is applied fully and correctly. Skipping a phase means μ is not fully applied, which degrades A.

### How GODSPEED Relates to Teams

When teams work on orthogonal quantums, each teammate runs GODSPEED independently on their module. When the lead consolidates:

1. Each teammate: Ψ-Λ-H-Q on their module (isolated)
2. Lead: Ψ-Λ-H-Q on combined changes (full system)
3. If lead's Λ fails: identify which teammate's code broke it, message them
4. Once lead's full circuit is green: Ω (one atomic commit with all changes)

### How GODSPEED Relates to the Build System

The build system (`dx.sh`) is the execution framework for Λ. Ψ (Observatory facts) are inputs to the decision logic. H (Guards) and Q (Invariants) are gates enforced by hooks.

---

## Appendix: Gate Reference

| Gate | Command | Time | Exit Code | Purpose |
|------|---------|------|-----------|---------|
| **Ψ.observe** | `bash scripts/observatory/observatory.sh` | 5-10s | 0=current, 1=stale | Sync facts |
| **Λ.compile** | `bash scripts/dx.sh compile` | 5-15s | 0=green, 1=error | Compile only |
| **Λ.module** | `bash scripts/dx.sh -pl MODULE` | 10-30s | 0=green, 1=error | Module + tests |
| **Λ.all** | `bash scripts/dx.sh all` | 2-5m | 0=green, 1=error | Full reactor |
| **H.guards** | `hyper-validate.sh` (hook) | <1s | 0=green, 2=violation | Block patterns |
| **Q.test** | `mvn test` (part of Λ) | 30-60s | 0=green, 1=fail | Run tests |
| **Q.analyze** | `mvn verify -P analysis` | 30s | 0=green, 1=warning | SpotBugs/PMD |
| **Ω.commit** | `git commit` | 10s | 0=success, 1=error | Record change |

---

## See Also

- [CLAUDE.md](../../CLAUDE.md) — Full definition of GODSPEED and the equation
- [Chatman Equation](chatman-equation.md) — Why the phases are in this order
- [Why H-Guards Exist](h-guards-philosophy.md) — Deep dive into the H phase
- [Quality Gates Reference](../reference/quality-gates.md) — All gates with specs
- [dx.sh Documentation](../reference/dx-workflow.md) — Detailed build system reference
