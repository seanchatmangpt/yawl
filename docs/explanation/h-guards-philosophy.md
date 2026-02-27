# Why H-Guards Exist

**Quadrant**: Explanation | **Concept**: Zero-tolerance anti-pattern enforcement

This document explains the reasoning behind the HYPER_STANDARDS (H-Guards) system — why 14 patterns are blocked at write time, why there are only two legal outcomes, and why "temporary" placeholders are never acceptable in production code.

---

## The Problem H-Guards Solve

Every codebase accumulates technical debt through one of two paths: conscious trade-offs (we choose to defer X) or unconscious drift (we forget that Y was never implemented). HYPER_STANDARDS exists because the second path — unconscious drift — is the dangerous one.

The patterns H-Guards block are not inherently evil. A `// TODO` comment in a personal project is fine. The problem is the *context*: these patterns, in a production codebase, are lies. They tell the runtime, the user, and the next developer that the code does something it doesn't actually do.

Consider this method:
```java
public String getSpecificationId() { return ""; }
```

This compiles. It type-checks. It passes all tests that don't check the return value. It will be called by downstream code that assumes it returns a real ID. When it fails at runtime, the stack trace will point somewhere 10 frames away from this method. The author meant to implement it later — but "later" is not a runtime concept.

---

## Jidoka: Stop and Fix at the Source

HYPER_STANDARDS is an application of Toyota's **Jidoka** principle: build quality in at the source rather than inspecting for defects at the end. In TPS manufacturing, when a machine detects a defect, it stops immediately and alerts an operator. The line does not continue running defective parts hoping to catch the problem at final inspection.

The H-Guard hook is the equivalent: when a defect pattern is written, the tool stops immediately (exit 2) and alerts the agent. The development line does not continue with a stub or placeholder hoping to catch it in code review.

The alternative — catching these patterns at code review or in CI — is a much longer feedback loop. By the time the reviewer sees the pattern, the author has moved on to other work. The fix requires a context switch back to code that is no longer fresh in memory. Worse, if the pattern reaches CI, it blocks other team members who have nothing to do with the original defect.

---

## Why "Temporary" Is a Category Error

The phrase "I'll do this properly later" assumes two things that are both false:

1. **That "later" is predictable**: Work priorities shift. The person who wrote the TODO may not be the one who encounters the consequence. There is no mechanism that automatically converts "later" into a scheduled task.

2. **That a stub is safe until then**: A stub that returns `""` or `null` does not fail safely — it fails silently. Silent failures are harder to debug than explicit failures. An `UnsupportedOperationException` with a clear message is infinitely more useful than a `NullPointerException` three frames downstream.

"Temporary" code has a well-documented lifecycle: it ships, it stays, it becomes permanent. The H-Guards prevent this lifecycle from starting.

---

## The Two Legal Outcomes

HYPER_STANDARDS forces a binary choice at implementation time:

**Option A — Implement it now**: If the logic is known, write it. Not a placeholder. Not a stub. The real thing.

**Option B — Declare it impossible**: If the logic is not yet known, throw:
```java
throw new UnsupportedOperationException(
    "fetchData: requires DataRepository integration. " +
    "See issue #98 for implementation plan.");
```

This is not surrendering. This is *honest signaling*. The code now says: "I know I don't have this capability yet, and I will tell you loudly the moment you try to use it." An UnsupportedOperationException in a test suite tells you exactly what needs to be implemented. A silent stub that returns `""` tells you nothing until production data is corrupted.

The requirement to include context (issue reference, what's needed) in the exception message ensures that the failure is actionable, not just loud.

---

## Why Only These 14 Patterns

The 14 H-patterns were chosen because they all share a property: **they make code appear to work when it doesn't**. They all represent a form of deception — of the compiler, the test suite, the reviewer, the runtime, or some combination.

Patterns not in the list — complex logic bugs, incorrect algorithms, poor variable names — are not H-Guard violations. They may be bugs, but they are *honest* bugs: code that intends to do one thing and does another, which is debuggable. H-Guard violations are intentional no-ops wearing the mask of real behavior.

The 14 patterns cover:
- **Deferred work** (H1): signaling intent without action
- **Fake identity** (H2, H3): code that pretends to be something it isn't
- **Mode flags** (H4): dual behavior paths that shouldn't exist
- **Silent stubs** (H5, H6, H7): returns that look valid but aren't
- **Fake constants** (H8): values that look real but are test data
- **Silent failure** (H9, H10, H11, H12): execution paths that hide errors
- **False logging** (H13): logging as a substitute for correct behavior
- **Test leakage** (H14): test infrastructure in production code

---

## The Mockito Rule (H14)

H14 deserves special discussion because it's the only pattern that concerns imports rather than code logic. `import org.mockito.*` in production code (`src/main/java`) is a symptom of a design problem: the class being tested was designed around its mocks rather than around its real behavior.

Mockito is a test tool. If it appears in production code, it means one of two things:
1. The class needs mocking infrastructure to be instantiated in production — a sign of missing dependency injection or overly coupled design.
2. The class was developed test-first using mocks, and the mock leaked across the package boundary.

The fix is always the same: move the class or remove the dependency. H14 makes this visible immediately rather than at the code review that notices the import.

---

## Why Not Warnings?

The hook uses exit 2 (blocker), not exit 1 (warning). This is deliberate.

Warnings create a "warning list" that grows until it is ignored. A developer who sees 47 warnings becomes habituated to warnings and misses the 48th. A blocked write operation that must be resolved before continuing creates zero habituation — each block is encountered fresh and must be resolved.

This is the same reason smoke alarms don't warn you gradually as smoke levels rise. The alarm fires or it doesn't.

The cost of a false block (a legitimate `return ""` mistakenly flagged) is lower than the cost of a missed defect reaching production. The hook has a secondary filter for legitimate empty string returns (e.g., error message formatting) to reduce false positives, but the threshold is set conservative.

---

## Relationship to Chicago TDD

H-Guards are compatible with Chicago TDD (real objects, H2 database, no Mockito leakage). The two systems reinforce each other:

- Chicago TDD says: use real objects in tests, not mocks
- H-Guards say: don't write mock implementations in production code

A codebase following both principles has no mocks in `src/main/java` and no Mockito dependencies in production code. Tests use the real implementations against an in-memory H2 database. This means every test exercises the actual code path — there is no gap between "what the test tested" and "what production runs."

---

## See Also

- [HYPER_STANDARDS Pattern Reference](../reference/hyper-standards.md) — all 14 patterns with regexes
- [How-To: Fix H-Guard Violations at Scale](../how-to/build/fix-h-guard-violations.md)
- [Tutorial: Fix Your First Violation](../tutorials/12-fix-hyper-standards-violation.md)
- [Quality Gates Reference](../reference/quality-gates.md) — G_guard gate specification
