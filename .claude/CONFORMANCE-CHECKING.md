# Conformance Checking: H-Guards & Q-Invariants Framework

**Framework**: Formal Verification (Trace ≈ Specification)
**Purpose**: Define how H (guards) and Q (invariants) phases verify code quality
**Status**: Complete verification specification
**Last Updated**: 2026-03-06

---

## I. Overview: Trace Conformance

### What is Conformance?

A trace **conforms** to the YAWL specification when:

1. **Event sequence** matches expected process model
2. **No guard violations** detected (H phase)
3. **All invariants satisfied** (Q phase)
4. **Atomic consistency** maintained (no partial commits)

### When Does Conformance Fail?

```
Trace conforms = ✓ All guards pass (H) AND ✓ All invariants pass (Q)
              = ✗ Any guard fails OR ✗ Any invariant fails
```

**Example Non-Conformant Traces**:
```
Trace 1: [Parse] → [Compile] → [Commit]  (skipped Test, Validate)
         ✗ FAILS: Event sequence wrong

Trace 2: [Parse] → [Compile] → [Test] → [Validate]
         Detection: GuardViolationDetected(H_TODO at YNetRunner.java:427)
         ✗ FAILS: H phase failed

Trace 3: All events succeed, but method returns fake data
         Detection: InvariantCheckFailed(real_impl ∨ throw)
         ✗ FAILS: Q phase failed
```

---

## II. H Phase: Guard Violations (Trace Properties)

### Purpose

H guards detect **forbidden patterns** that indicate deceptive, incomplete, or unstable code. These patterns are **automatically rejected** by hyper-validate.sh hook before any commit.

### Seven Guard Patterns

```
┌────────────────────────────────────────────────────────────────────┐
│ Guard Pattern             │ Violation Type    │ Auto-Block? │ Fixable?│
├────────────────────────────────────────────────────────────────────┤
│ H_TODO (deferred work)    │ Code unfinished   │ YES (H)     │ YES     │
│ H_MOCK (fake impl)        │ Deceptive code    │ YES (H)     │ YES     │
│ H_STUB (empty returns)    │ Deceptive code    │ YES (H)     │ YES     │
│ H_EMPTY (void no-op)      │ Incomplete        │ YES (H)     │ YES     │
│ H_FALLBACK (silent catch) │ Error swallowing  │ YES (H)     │ YES     │
│ H_LIE (code ≠ docs)       │ Contradiction     │ YES (H)     │ YES     │
│ H_SILENT (log not throw)  │ Error swallowing  │ YES (H)     │ YES     │
└────────────────────────────────────────────────────────────────────┘
```

### Pattern Definitions & Detection

#### H_TODO: Deferred Work Markers

**Pattern**: Comments indicating unfinished work

```
Regex: //\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)
```

**Examples**:
```java
// TODO: Add deadlock detection
public void detectDeadlock() {
    // Just a stub for now
}

// FIXME: This is a hack
int result = calculateSomething(); // XXX: wrong algorithm

// LATER: optimize this
for (int i = 0; i < 1000000; i++) { }
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_TODO",
  "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
  "line": 427,
  "content": "// TODO: Add deadlock detection",
  "fix_guidance": "Implement real deadlock detection or document why not needed"
}
```

**Fix**:
```java
// Before (VIOLATES)
public void detectDeadlock() {
    // TODO: implement
}

// After (CONFORMS)
public void detectDeadlock() {
    if (isDeadlocked()) {
        throw new DeadlockDetectedException("Cycle detected in YNetRunner");
    }
}
```

#### H_MOCK: Mock Implementations

**Pattern**: Code with mock/stub/fake/demo in name

```
Regex: (mock|stub|fake|demo)[A-Z][a-zA-Z]*  (identifiers)
       ^(Mock|Stub|Fake|Demo)                (class names)
```

**Examples**:
```java
class MockDataService implements DataService { }  // ✗ H_MOCK
public String getMockData() { return ""; }        // ✗ H_MOCK
public class FakeYNetRunner extends YNetRunner {} // ✗ H_MOCK
DemoWorkflow workflow = new DemoWorkflow();       // ✗ H_MOCK
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_MOCK",
  "file": "yawl-engine/src/test/java/.../MockDataService.java",
  "line": 12,
  "content": "class MockDataService implements DataService {",
  "fix_guidance": "Use real DataService or move to test fixtures with clear naming"
}
```

**Fix**:
```java
// Before (VIOLATES)
class MockDataService implements DataService {
    public String fetch() { return "mock"; }
}

// After (CONFORMS - Option 1: delete if test-only)
// Move to: src/test/java/.../fixtures/TestDataService.java
@TestFixture
class TestDataService implements DataService {
    public String fetch() { return "test-data-123"; }
}

// After (CONFORMS - Option 2: implement for real)
class ProductionDataService implements DataService {
    public String fetch() {
        return remoteApi.fetchData();
    }
}
```

#### H_STUB: Empty or Placeholder Returns

**Pattern**: Non-void methods returning empty, zero, null, or empty collections

```
Return patterns:
  return "";
  return 0;
  return null;
  return Collections.emptyList();
  return Collections.emptyMap();
  return Optional.empty();
  return new ArrayList<>();
```

**Examples**:
```java
public String getData() { return ""; }                    // ✗ H_STUB
public int getCount() { return 0; }                      // ✗ H_STUB
public List<Item> getItems() {
    return Collections.emptyList();                      // ✗ H_STUB
}
public YWorkItem fetch(String id) { return null; }      // ✗ H_STUB
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_STUB",
  "file": "yawl-engine/src/main/java/org/yawl/engine/DataProvider.java",
  "line": 89,
  "content": "public String getData() { return \"\"; }",
  "fix_guidance": "Return real data, throw exception, or mark @Nullable with optional"
}
```

**Fix**:
```java
// Before (VIOLATES)
public String getData() {
    return "";  // Stub: should be real data
}

// After (CONFORMS - Option 1: Real implementation)
public String getData() {
    return dataService.fetch();
}

// After (CONFORMS - Option 2: Explicit exception)
public String getData() {
    throw new UnsupportedOperationException(
        "getData requires initialization. See IMPLEMENTATION_GUIDE.md"
    );
}

// After (CONFORMS - Option 3: Optional/nullable)
public Optional<String> getData() {
    return Optional.of(dataService.fetch());
}
```

#### H_EMPTY: No-op Method Bodies

**Pattern**: Void methods with empty or whitespace-only bodies

```
Regex: ^\s*\{\s*\}$  (within method)
```

**Examples**:
```java
public void initialize() { }                    // ✗ H_EMPTY
void cleanup() {
    // intentionally empty, but guards catch it
}                                               // ✗ H_EMPTY
public void logEvent(String msg) { }           // ✗ H_EMPTY
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_EMPTY",
  "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
  "line": 512,
  "content": "public void initialize() { }",
  "fix_guidance": "Implement real logic, add logging/metrics, or throw exception with reason"
}
```

**Fix**:
```java
// Before (VIOLATES)
public void initialize() { }

// After (CONFORMS - Option 1: Real implementation)
public void initialize() {
    parser = new YAMLParser();
    engine = new WorkflowEngine();
    logger.info("YNetRunner initialized");
}

// After (CONFORMS - Option 2: Explicit exception)
public void initialize() {
    throw new UnsupportedOperationException(
        "initialize() must be called with configuration"
    );
}

// After (CONFORMS - Option 3: Protected/Abstract)
protected void initialize() {
    // Intentionally empty, overridden by subclasses
}
```

#### H_FALLBACK: Silent Catch-and-Continue

**Pattern**: Catch blocks that return fake data instead of propagating exception

```
Catch-and-return patterns:
  catch (Exception e) { return emptyList(); }
  catch (IOException e) { return null; }
  catch (Exception e) { return defaultValue; }
```

**Examples**:
```java
public List<YWorkItem> fetchItems() {
    try {
        return remoteApi.get();
    } catch (IOException e) {
        return Collections.emptyList();  // ✗ H_FALLBACK: silent fake
    }
}

public String getData() {
    try {
        return database.query();
    } catch (SQLException e) {
        return ""; // ✗ H_FALLBACK: caller doesn't know it failed
    }
}
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_FALLBACK",
  "file": "yawl-engine/src/main/java/org/yawl/integration/RemoteDataSource.java",
  "line": 78,
  "content": "catch (IOException e) { return Collections.emptyList(); }",
  "fix_guidance": "Propagate exception, use Optional<T>, or retry logic - never silent fallback"
}
```

**Fix**:
```java
// Before (VIOLATES - silent fallback)
public List<YWorkItem> fetchItems() {
    try {
        return remoteApi.get();
    } catch (IOException e) {
        return Collections.emptyList();  // Caller has no idea it failed!
    }
}

// After (CONFORMS - Option 1: Propagate)
public List<YWorkItem> fetchItems() throws IOException {
    return remoteApi.get();
}

// After (CONFORMS - Option 2: Use Optional)
public Optional<List<YWorkItem>> fetchItems() {
    try {
        return Optional.of(remoteApi.get());
    } catch (IOException e) {
        logger.warn("Failed to fetch items", e);
        return Optional.empty();  // Explicit, not silent
    }
}

// After (CONFORMS - Option 3: Retry + then throw)
public List<YWorkItem> fetchItems() throws IOException {
    int retries = 0;
    while (retries < 3) {
        try {
            return remoteApi.get();
        } catch (IOException e) {
            retries++;
            if (retries >= 3) throw e;  // Finally propagate
            Thread.sleep(2000);
        }
    }
}
```

#### H_LIE: Code ≠ Documentation

**Pattern**: Javadoc/documentation contradicts actual code

```
Mismatches:
  @return never null           ↔ code returns null
  @throws IOException          ↔ code doesn't throw
  @param required              ↔ code accepts null
  @deprecated                  ↔ code is actively used
```

**Examples**:
```java
/**
 * Returns the value, never null.
 */
public String getValue() {
    return null;  // ✗ H_LIE: docs say never null
}

/**
 * @throws IOException on read error
 */
public void readFile(String path) {
    // ✗ H_LIE: doesn't actually throw IOException
    try {
        Files.readString(Paths.get(path));
    } catch (IOException e) {
        logger.error("Error", e);  // Silently caught!
    }
}

/**
 * @deprecated Use newMethod() instead
 */
public void oldMethod() {
    // ✗ H_LIE: if actually deprecated, shouldn't be in main code
}
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_LIE",
  "file": "yawl-engine/src/main/java/org/yawl/engine/YDataProvider.java",
  "line": 45,
  "content": "/** @return never null */ public String getValue() { return null; }",
  "fix_guidance": "Update code to match docs, or update docs to match code (not both wrong)"
}
```

**Fix**:
```java
// Before (VIOLATES - docs lie)
/**
 * Returns the value, never null.
 */
public String getValue() {
    return null;
}

// After (CONFORMS - Option 1: Fix code)
/**
 * Returns the value, never null.
 */
public String getValue() {
    return dataStore.getOrDefault("key", "");
}

// After (CONFORMS - Option 2: Fix docs)
/**
 * Returns the value, or null if not found.
 */
public String getValue() {
    return null;
}

// After (CONFORMS - Option 3: Use Optional)
/**
 * Returns the value if present.
 */
public Optional<String> getValue() {
    return Optional.ofNullable(null);
}
```

#### H_SILENT: Logging Instead of Throwing

**Pattern**: Error conditions logged instead of propagated

```
Patterns:
  log.error("not implemented");
  log.warn("operation failed");
  logger.error("X not supported");
```

**Examples**:
```java
public void processWorkflow(YWorkflow w) {
    if (w == null) {
        log.error("Workflow is null, skipping");  // ✗ H_SILENT: should throw
        return;
    }
}

public int calculate() {
    if (input < 0) {
        log.warn("Invalid input, using default");  // ✗ H_SILENT
        return 0;
    }
}
```

**Event**:
```json
{
  "event_type": "GuardViolationDetected",
  "pattern": "H_SILENT",
  "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
  "line": 234,
  "content": "log.error(\"not implemented\");",
  "fix_guidance": "Throw IllegalArgumentException or IllegalStateException instead of logging"
}
```

**Fix**:
```java
// Before (VIOLATES - silent logging)
public void processWorkflow(YWorkflow w) {
    if (w == null) {
        log.error("Workflow is null");  // Caller doesn't know it failed!
        return;
    }
}

// After (CONFORMS - explicit exception)
public void processWorkflow(YWorkflow w) {
    if (w == null) {
        throw new IllegalArgumentException("Workflow cannot be null");
    }
    // ... real processing ...
}
```

---

## III. H Phase Execution: hyper-validate.sh

### Hook Mechanism

The hyper-validate.sh hook runs **before every git commit**:

```bash
git add .claude/FIRST-PRINCIPLES.md
↓
git commit -m "message"
↓
[Pre-commit hook triggered: hyper-validate.sh]
  ├─ Scan all staged files for H_TODO
  ├─ Scan all staged files for H_MOCK
  ├─ Scan all staged files for H_STUB
  ├─ ... (all 7 patterns)
  ├─ Aggregate violations
  └─ if violations > 0:
      exit 2 (BLOCK COMMIT)
     else:
      exit 0 (allow commit)
↓
If exit 0: commit created
If exit 2: commit blocked, engineer must fix
```

### Receipt Output

After H phase completes, emit receipt:

```json
{
  "phase": "H_guards",
  "timestamp": "2026-03-06T01:31:12.573Z",
  "files_scanned": 487,
  "violations": [
    {
      "pattern": "H_TODO",
      "file": "yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Implement real logic or throw UnsupportedOperationException"
    },
    {
      "pattern": "H_MOCK",
      "file": "yawl-engine/src/test/java/fixtures/MockDataService.java",
      "line": 12,
      "content": "class MockDataService implements DataService {",
      "fix_guidance": "Delete or move to test fixtures"
    }
  ],
  "summary": {
    "h_todo_violations": 1,
    "h_mock_violations": 1,
    "h_stub_violations": 0,
    "h_empty_violations": 0,
    "h_fallback_violations": 0,
    "h_lie_violations": 0,
    "h_silent_violations": 0,
    "total_violations": 2
  },
  "status": "FAILED",
  "action": "Fix 2 violations and re-run: git commit"
}
```

---

## IV. Q Phase: Invariant Violations (State Reachability)

### Purpose

Q invariants enforce **correctness constraints** at the code level. Unlike guards (syntax patterns), invariants verify **semantic reachability**—that code can reach valid states.

### Three Core Invariants

#### Invariant 1: real_impl ∨ throw

**Definition**: Every method must either implement real logic OR throw UnsupportedOperationException.

```
∀ method M in code:
  (real_implementation(M) ∨ throws(UnsupportedOperationException))
```

**Why**: Code must not silently do nothing or pretend to work.

**Examples**:
```java
// ✗ VIOLATES Invariant 1 (neither real nor throws)
public String getData() {
    return "";  // Fake, silent
}

// ✓ SATISFIES Invariant 1 (real implementation)
public String getData() {
    return cache.get("data");
}

// ✓ SATISFIES Invariant 1 (explicit exception)
public String getData() {
    throw new UnsupportedOperationException(
        "getData requires cache initialization"
    );
}
```

**Verification**:
```
For each method M:
  1. Parse method body B
  2. Check: does B have real logic? (>1 line, not stub)
  3. Check: does B throw UnsupportedOperationException?
  4. If (real ∨ throw): PASS
     Else: FAIL with violation
```

**Event**:
```json
{
  "event_type": "InvariantCheckFailed",
  "invariant": "real_impl ∨ throw",
  "violation": {
    "file": "yawl-engine/src/main/java/org/yawl/engine/DataProvider.java",
    "method": "getData()",
    "line": 89,
    "reason": "returns empty string (neither real_impl nor throws)"
  }
}
```

#### Invariant 2: ¬mock ∧ ¬stub ∧ ¬silent_fallback ∧ ¬lie

**Definition**: Code must have no deceptive patterns (combines H phase with semantic checks)

```
∀ code C:
  ¬mock(C) ∧ ¬stub(C) ∧ ¬silent_fallback(C) ∧ ¬lie(C)
```

**Why**: Q phase is defense-in-depth; H guards catch syntax, Q ensures semantics.

**Examples**:
```java
// ✗ VIOLATES (mock class exists)
class MockDataService { }

// ✗ VIOLATES (silent fallback)
try { ... } catch (Exception e) { return emptyList(); }

// ✗ VIOLATES (docs lie)
/** @return never null */ public String get() { return null; }

// ✓ SATISFIES (all deceptive patterns absent)
public String get() {
    return database.fetch();
}
```

**Verification**:
```
Combine H phase results + semantic analysis:
  1. H_MOCK violations? → Q fails
  2. H_STUB violations? → Q fails
  3. H_FALLBACK violations? → Q fails
  4. H_LIE violations? → Q fails
  5. All absent? → Q passes
```

#### Invariant 3: code ≈ documentation

**Definition**: Javadoc accurately describes actual code behavior

```
∀ method M with Javadoc D:
  signature(D) ≈ signature(M)
  return_type(D) = return_type(M)
  throws(D) ≈ throws(M)
  preconditions(D) ≈ preconditions(M)
```

**Why**: Documentation is a contract; code must honor it.

**Examples**:
```java
// ✗ VIOLATES (javadoc says never null, code returns null)
/**
 * @return never null
 */
public String getValue() {
    return null;
}

// ✓ SATISFIES (docs match code)
/**
 * @return value if present, null if not found
 */
public String getValue() {
    return database.query();
}

// ✓ SATISFIES (docs accurate about exceptions)
/**
 * @throws IOException on read error
 * @throws FileNotFoundException if file doesn't exist
 */
public String readFile(String path) throws IOException {
    return Files.readString(Paths.get(path));
}
```

**Verification**:
```
For each method M with Javadoc D:
  1. Extract @return type from D
  2. Extract actual return type from M
  3. Extract @throws from D
  4. Extract throw statements from M
  5. Compare:
     - Type mismatch? → FAIL
     - Missing throw? → FAIL
     - Extra throw not documented? → FAIL
     - All consistent? → PASS
```

---

## V. Q Phase Execution

### Invariant Checker Algorithm

```
Input: Compiled code (target/classes) or source code
Output: invariant-receipt.json (violations or pass)

for each invariant I:
  violations[I] = []

  for each method M in code:
    if not I.check(M):
      violations[I].append({method: M, reason: I.failure_reason})

if any violations:
  emit InvariantCheckFailed event
  exit_code = 2
else:
  emit InvariantCheckPassed event
  exit_code = 0
```

### Receipt Output

```json
{
  "phase": "Q_invariants",
  "timestamp": "2026-03-06T01:31:15.773Z",
  "invariants_checked": 3,
  "invariants_satisfied": 2,
  "invariants_violated": 1,
  "violations": [
    {
      "invariant": "real_impl ∨ throw",
      "violated_in": [
        {
          "file": "yawl-engine/src/main/java/org/yawl/engine/DataProvider.java",
          "method": "getData()",
          "line": 89,
          "reason": "returns empty string without throwing",
          "fix": "Implement real logic OR throw UnsupportedOperationException"
        }
      ]
    }
  ],
  "status": "FAILED",
  "action": "Fix violations in code and re-run: dx.sh all"
}
```

---

## VI. Combined H + Q Conformance

### Full Conformance Check

```
BUILD SUCCESSFUL
  ↓
[H Phase: Guard Checks]
  ├─ Scan for 7 patterns
  ├─ Violations found? → ConformanceCheckFailed (exit 2)
  └─ No violations? → ConformanceCheckPassed (continue)
  ↓
[Q Phase: Invariant Checks]
  ├─ Check real_impl ∨ throw
  ├─ Check ¬deception (H results + semantics)
  ├─ Check code ≈ docs
  ├─ Any failed? → InvariantCheckFailed (exit 2)
  └─ All passed? → InvariantCheckPassed (continue)
  ↓
[Emit Consolidated Receipt]
  ├─ guard-receipt.json (H results)
  ├─ invariant-receipt.json (Q results)
  └─ validation-summary.json (combined: PASS or FAIL)
  ↓
If validation_summary.status == "PASSED":
  exit 0 (ready for commit)
Else:
  exit 2 (fix violations, re-run)
```

### Example: Full Conformance Failure

```
Trace Execution:
  Parse: ✓
  Compile: ✓
  Test: ✓
  Validate:
    H Phase:
      GuardViolationDetected(H_TODO, YNetRunner.java:427)
      GuardViolationDetected(H_STUB, DataProvider.java:89)
      Status: FAILED
    ↓
    [Exit H Phase with violations, don't proceed to Q]
    ↓
    ConformanceCheckFailed event

Final Status: TRACE DOES NOT CONFORM
Exit Code: 2
Action: Engineer fixes violations, re-runs dx.sh all
```

---

## VII. Conformance Metrics & Reporting

### Trace Conformance Score

```
Score = (phases_passed ÷ phases_attempted) × 100
        - (violations_found ÷ max_violations) × 50

Example:
  Phases: 5 attempted, 4 passed (1 validation failure)
  Violations: 2 H violations

  Score = (4/5) × 100 - (2/7) × 50
        = 80 - 14.3
        = 65.7%

  Verdict: TRACE PARTIALLY CONFORMS (fix violations, retry)
```

### Conformance Report

```json
{
  "trace_id": "tr-2026-03-06-001",
  "overall_conformance": "FAILED",
  "conformance_score": "65.7%",
  "phase_results": {
    "parse": { "status": "PASS", "duration_ms": 450 },
    "compile": { "status": "PASS", "duration_ms": 4300 },
    "test": { "status": "PASS", "duration_ms": 18500 },
    "validate": {
      "status": "FAILED",
      "h_phase": {
        "status": "FAILED",
        "violations": 2,
        "violations_detail": [
          { "pattern": "H_TODO", "count": 1 },
          { "pattern": "H_STUB", "count": 1 }
        ]
      },
      "q_phase": {
        "status": "SKIPPED (blocked by H phase)"
      }
    }
  },
  "remediation": {
    "step_1": "Fix H_TODO at YNetRunner.java:427: implement real logic",
    "step_2": "Fix H_STUB at DataProvider.java:89: return real data or throw",
    "step_3": "Re-run: dx.sh all",
    "expected_result": "All violations fixed → conformance = PASS"
  }
}
```

---

## VIII. Reference: Decision Tree

```
Does this trace conform to YAWL spec?

trace.phases all executed?
├─ NO → Non-conformant (missing phases)
└─ YES: proceed
   ↓
   H Phase: violations > 0?
   ├─ YES → Non-conformant (guard violations)
   │        emit ConformanceCheckFailed, exit 2
   └─ NO: proceed
      ↓
      Q Phase: invariants satisfied?
      ├─ NO → Non-conformant (invariant violations)
      │       emit InvariantCheckFailed, exit 2
      └─ YES: CONFORMANT
              emit InvariantCheckPassed, exit 0
              Ready for commit
```

---

## GODSPEED. ✈️

*Conformance is not about perfection; it's about honest code that does what it promises.*
