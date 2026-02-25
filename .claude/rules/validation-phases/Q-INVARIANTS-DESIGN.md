---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/codegen/validation/**"
  - "*/src/test/java/org/yawlfoundation/yawl/codegen/validation/**"
  - ".specify/invariants.ttl"
  - ".claude/hooks/q-phase-invariants.sh"
---

# Q-Invariants Phase Design — Semantic Validation Architecture

**Status**: MVP IMPLEMENTED (Bash hook), Java SHACL pipeline designed
**Mission**: Enforce 4 semantic invariants on generated Java code before git commit

---

## Executive Summary

The Q (Invariants) phase validates that generated code satisfies semantic correctness
rules that cannot be caught by syntactic checks. It runs after H (Guards) passes GREEN
and before Ω (Git) commits the result.

**Key deliverables**:
1. **InvariantValidator interface** — Contract for semantic validation
2. **SHACLValidator** — Apache Jena + Topbraid SHACL engine
3. **CodeToRDF converter** — Java AST → RDF triples (ANTLR4 visitor)
4. **invariants.ttl** — SHACL NodeShape definitions (4 shapes)
5. **InvariantReceipt** — JSON receipt with per-violation detail
6. **q-phase-invariants.sh** — MVP Bash hook (active now)

---

## 1. Core Invariants

### Q1: real_impl ∨ throw

Every non-abstract method must either:
- Contain a real implementation (> 2 statements, non-trivial logic), OR
- Throw `UnsupportedOperationException` with an informative message

```java
// VIOLATION — empty body
public void process() { }

// VIOLATION — one-liner stub
public String getData() { return ""; }

// CORRECT — real implementation
public void process() {
    validateState();
    engine.submitWorkItem(workItem);
    notifyListeners(WorkItemEvent.PROCESSED);
}

// CORRECT — explicit rejection
public void process() {
    throw new UnsupportedOperationException(
        "process() requires integration with YEngine. " +
        "See IMPLEMENTATION_GUIDE.md for setup steps."
    );
}
```

### Q2: ¬mock

No mock, stub, fake, or demo objects in generated output:

```java
// VIOLATION — mock class name
public class MockWorkflowService implements WorkflowService { }

// VIOLATION — stub method name
public void stubValidation() { }

// VIOLATION — demo variable
String demoResult = "example response";
```

### Q3: ¬silent_fallback

Catch blocks must propagate, log meaningfully, or provide a real alternative.
Returning fake data from a catch block is forbidden:

```java
// VIOLATION — silent fallback to fake data
try {
    return engine.fetchCase(caseId);
} catch (Exception e) {
    return Collections.emptyList();  // hides the error
}

// CORRECT — propagate
try {
    return engine.fetchCase(caseId);
} catch (EngineException e) {
    throw new WorkflowServiceException("Failed to fetch case " + caseId, e);
}

// CORRECT — real cached alternative with logging
try {
    return engine.fetchCase(caseId);
} catch (TransientException e) {
    log.warn("Engine unavailable, returning cached state for {}", caseId);
    return caseCache.get(caseId)
        .orElseThrow(() -> new WorkflowServiceException("No cached state", e));
}
```

### Q4: ¬lie

Method behavior must match its documented contract:

```java
// VIOLATION — javadoc says non-null but returns null
/** @return current case state, never null */
public CaseState getState() {
    return null;  // lies
}

// VIOLATION — declares throws but never throws
/** @throws WorkflowException if engine is unavailable */
public void submit() throws WorkflowException {
    engine.submit();  // never propagates or wraps WorkflowException
}

// CORRECT
/** @return current case state, never null */
public CaseState getState() {
    return Objects.requireNonNull(stateMap.get(caseId),
        "State map must contain entry for case " + caseId);
}
```

---

## 2. Architecture

### Detection Pipeline

```
Generated .java files
    ↓
ANTLR4 JavaParser → AST
    ↓
RdfAstConverter   → RDF Model (Turtle)
    ↓
SHACL Engine      → Validation Report
    ↓
ReceiptBuilder    → invariant-receipt.json
    ↓
Exit 0 (GREEN) / Exit 2 (VIOLATIONS)
```

### Component Map

```
yawl-ggen/src/main/java/org/ggen/
├── validation/
│   ├── InvariantValidator.java       [Interface]
│   ├── SHACLValidator.java           [Impl: Topbraid SHACL]
│   ├── InvariantReceiptGenerator.java [CLI entry point]
│   └── InvariantViolationException.java [Checked exception]
├── model/
│   ├── InvariantReceipt.java         [JSON receipt model]
│   └── InvariantViolation.java       [Per-violation record]
└── ast/
    ├── JavaAstParser.java            [ANTLR4 wrapper]
    └── RdfAstConverter.java          [AST → RDF]

.specify/
└── invariants.ttl                   [SHACL shapes (4 NodeShapes)]

.claude/hooks/
└── q-phase-invariants.sh            [MVP Bash hook (active)]
```

---

## 3. Interface Design

```java
/**
 * Validates generated Java code against Q-phase invariants:
 * Q1: real_impl ∨ throw
 * Q2: ¬mock
 * Q3: ¬silent_fallback
 * Q4: ¬lie (code matches docs)
 */
public interface InvariantValidator {

    /**
     * Validate all Java files under emitDir.
     * @return receipt with GREEN or VIOLATIONS status
     */
    InvariantReceipt validateEmitDir(Path emitDir) throws IOException;

    /**
     * Validate a single Java file.
     */
    List<InvariantViolation> validateFile(Path javaFile) throws IOException;

    /** Invariant name (Q1, Q2, Q3, Q4). */
    String invariantName();
}
```

---

## 4. SHACL Shape Summary

Defined in `.specify/invariants.ttl`:

| Shape | Invariant | Detection Method |
|-------|-----------|-----------------|
| `RealImplShape` | Q1: real_impl ∨ throw | SPARQL: empty bodies without throw |
| `NoMockShape` | Q2: ¬mock | SPARQL: class/method name patterns |
| `NoSilentFallbackShape` | Q3: ¬silent_fallback | SPARQL: catch blocks with fake returns |
| `NoLieShape` | Q4: ¬lie | SPARQL: javadoc vs. implementation diff |

Each shape uses `sh:sparql` constraint components for precise detection.

---

## 5. Receipt Format

```json
{
  "phase": "invariants",
  "timestamp": "2026-02-21T14:30:00Z",
  "status": "RED",
  "methods_checked": 45,
  "classes_checked": 12,
  "passing_rate": "95.6%",
  "violations": [
    {
      "invariant": "Q1_real_impl_or_throw",
      "severity": "FAIL",
      "file": "generated/YWorkItem.java",
      "line": 234,
      "content": "public void validateState() { }",
      "remediation": "Implement validation or throw UnsupportedOperationException"
    },
    {
      "invariant": "Q3_no_silent_fallback",
      "severity": "FAIL",
      "file": "generated/WorkflowClient.java",
      "line": 156,
      "content": "} catch (Exception e) { return Collections.emptyList(); }",
      "remediation": "Re-throw exception or provide logged cached alternative"
    }
  ],
  "shaclReportHash": "sha256:abc123...",
  "files_processed": ["YWorkItem.java", "WorkflowClient.java"]
}
```

---

## 6. MVP Hook (Active Now)

The Bash hook at `.claude/hooks/q-phase-invariants.sh` provides regex-based
detection as an executable MVP:

```bash
# Run Q phase on a directory
.claude/hooks/q-phase-invariants.sh generated/
# → receipts/invariant-receipt.json
# → Exit 0: GREEN | Exit 2: violations found
```

**Coverage**: Detects Q1 (empty bodies), Q2 (mock names), Q3 (catch return patterns).
Q4 (¬lie semantic) requires the full SHACL pipeline.

---

## 7. Integration with ggen.toml

```toml
[phases.Q]
name = "Invariants"
enabled = true
after_phase = "H"
command = "./.claude/hooks/q-phase-invariants.sh"
receipt = "receipts/invariant-receipt.json"
exit_codes = { green = 0, violations = 2 }
```

---

## 8. Test Coverage Targets

| Test | Invariant | Expected |
|------|-----------|----------|
| Empty method body | Q1 | VIOLATION |
| Method with throw | Q1 | GREEN |
| `class MockService` | Q2 | VIOLATION |
| Real service class | Q2 | GREEN |
| Catch returning `emptyList()` | Q3 | VIOLATION |
| Catch re-throwing | Q3 | GREEN |
| Javadoc `@return never null`, returns null | Q4 | VIOLATION |
| Javadoc matches implementation | Q4 | GREEN |

**Targets**: 100% true positive rate, 0% false positive rate, <30s for 1000+ methods.

---

## 9. GODSPEED Pipeline Position

```
Ψ (Observatory) ──→ facts current
Λ (Build)       ──→ dx.sh all green
H (Guards)      ──→ no TODO/mock/stub/empty_return
Q (Invariants)  ──→ real_impl∨throw, ¬mock, ¬silent_fallback, ¬lie  ← HERE
Ω (Git)         ──→ atomic commit
```

**Next**: See `Q-INVARIANTS-PHASE.md` in `.claude/phases/` for architecture detail.
**Deep ref**: `.specify/invariants.ttl` for SHACL shapes. `.claude/hooks/q-phase-invariants.sh` for the MVP hook.
