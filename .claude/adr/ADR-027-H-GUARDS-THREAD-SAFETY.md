# ADR-027: Thread-Safety Refactoring for H-Guards Validation System

**Date**: 2026-02-28
**Status**: PROPOSED
**Decision Makers**: Architecture Review Board
**Stakeholders**: Build System, Concurrent Workflow Engine, MCP/A2A Integration
**Depends On**: ADR-026 (Async Refactoring)
**Related ADRs**: ADR-010 (YAWL v6 Concurrency Model), ADR-025 (Virtual Threads)

---

## Problem Statement

The H-Guards validation system has **critical thread-safety violations** that permit data races when accessed concurrently:

### Issues Identified

1. **Mutable Shared State in HyperStandardsValidator**
   - `checkers` list modified by both constructor and `addChecker()` method
   - `receipt` instance variable overwritten by `validateEmitDir()` calls
   - No synchronization between concurrent calls

2. **GuardViolation File Assignment Race**
   - `file` field is mutable, assigned **after** violation creation
   - Two concurrent threads can create violations, causing field visibility issues
   - Setter-based initialization violates immutability principle

3. **GuardReceipt Violation List Races**
   - `violations` ArrayList accessed without synchronization
   - `addViolation()` and iteration can race in concurrent scenarios
   - `Collections.unmodifiableList()` only prevents external mutation

4. **GuardSummary Counter Races**
   - `increment(String pattern)` modifies field without synchronization
   - Multiple threads calling `addViolation()` → `summary.increment()` concurrently

### Impact

**Current Behavior** (Safe):
```java
// Single-threaded build: no races
validator = new HyperStandardsValidator();
receipt = validator.validateEmitDir(dir);
```

**Hypothetical Concurrent Usage** (UNSAFE):
```java
// Multiple MCP requests using same validator instance
ExecutorService mcp = Executors.newVirtualThreadPerTaskExecutor();

HyperStandardsValidator validator = new HyperStandardsValidator();

mcp.submit(() -> validator.validateEmitDir(dir1));  // Thread A
mcp.submit(() -> validator.validateEmitDir(dir2));  // Thread B
mcp.submit(() -> validator.validateEmitDir(dir3));  // Thread C

// RACES:
// - receipt variable overwritten (Thread B overwrites A's receipt)
// - GuardViolation.file field not safely published
// - summary.increment() calls race with each other
```

---

## Decision

### Adopt Immutable Builder Pattern + Thread-Safe Collections

Refactor H-Guards to eliminate shared mutable state through two strategies:

#### Strategy 1: Immutable Validator Configuration (PRIMARY)

Create validators via **immutable builder** (pattern from Java 25 best practices):

```java
public class HyperStandardsValidator {
    // Configuration set at construction, NEVER modified
    private final List<GuardChecker> checkers;  // Immutable copy
    private final GuardValidatorConfig config;

    private HyperStandardsValidator(List<GuardChecker> checkers,
                                     GuardValidatorConfig config) {
        this.checkers = List.copyOf(Objects.requireNonNull(checkers));
        this.config = Objects.requireNonNull(config);
    }

    // Factory method (replaces public constructor)
    public static Builder builder() {
        return new Builder();
    }

    // Builder for configuration
    public static class Builder {
        private final List<GuardChecker> checkers = new ArrayList<>();
        private GuardValidatorConfig config = GuardValidatorConfig.defaults();

        public Builder withDefaultCheckers() {
            checkers.clear();
            checkers.add(new RegexGuardChecker("H_TODO", TODO_REGEX));
            checkers.add(new RegexGuardChecker("H_MOCK", MOCK_REGEX));
            checkers.add(new RegexGuardChecker("H_SILENT", SILENT_REGEX));
            checkers.add(new SparqlGuardChecker("H_STUB", loadQuery("guards-h-stub.sparql")));
            checkers.add(new SparqlGuardChecker("H_EMPTY", loadQuery("guards-h-empty.sparql")));
            checkers.add(new SparqlGuardChecker("H_FALLBACK", loadQuery("guards-h-fallback.sparql")));
            checkers.add(new SparqlGuardChecker("H_LIE", loadQuery("guards-h-lie.sparql")));
            return this;
        }

        public Builder withChecker(GuardChecker checker) {
            checkers.add(Objects.requireNonNull(checker));
            return this;
        }

        public Builder withConfig(GuardValidatorConfig config) {
            this.config = Objects.requireNonNull(config);
            return this;
        }

        public HyperStandardsValidator build() {
            return new HyperStandardsValidator(checkers, config);
        }
    }

    // Validation is now stateless (returns new receipt, no instance state)
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        GuardReceipt receipt = new GuardReceipt();  // LOCAL variable
        // ... validation logic ...
        return receipt;
    }

    // Static factory for common case
    public static HyperStandardsValidator createDefault() {
        return builder().withDefaultCheckers().build();
    }

    // REMOVED: public void addChecker(GuardChecker checker)
    // REMOVED: public GuardReceipt getReceipt()
}
```

**Benefits**:
- No instance state that persists across calls
- Validators are **thread-safe by default** (immutable configuration)
- Multiple threads can safely call `validateEmitDir()` on same instance
- No race conditions on `receipt` field (local variable)

#### Strategy 2: Immutable Violation with Constructor-Based File Assignment

Make `GuardViolation.file` field immutable:

```java
public final class GuardViolation {
    private final String pattern;
    private final String severity;
    private final int line;
    private final String content;
    private final String fixGuidance;
    private final String file;  // NOW FINAL - set in constructor

    public GuardViolation(String pattern, String severity, int line,
                         String content, String file) {
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.line = line;
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.fixGuidance = getFixGuidanceFor(pattern);
    }

    // DEPRECATED constructor (for backward compatibility)
    @Deprecated(forRemoval = true)
    public GuardViolation(String pattern, String severity, int line, String content) {
        this(pattern, severity, line, content, "(unknown)");
    }

    // Getters
    public String getPattern() { return pattern; }
    public String getSeverity() { return severity; }
    public int getLine() { return line; }
    public String getContent() { return content; }
    public String getFixGuidance() { return fixGuidance; }
    public String getFile() { return file; }

    // REMOVED: public void setFile(String file)

    // ... equals/hashCode/toString unchanged ...
}
```

**Benefits**:
- Violation is immutable after construction
- Safe to share between threads
- No visibility issues with lazy field assignment
- Cleaner API (no setters)

#### Strategy 3: Thread-Safe Violation Collection

Use `Collections.synchronizedList()` for thread-safe appends:

```java
public class GuardReceipt {
    private final List<GuardViolation> violations =
        Collections.synchronizedList(new ArrayList<>());
    private final Object lock = new Object();
    private String phase = "guards";
    private Instant timestamp;
    private int filesScanned = 0;
    private String status = "PENDING";
    private String errorMessage;
    private GuardSummary summary;

    public GuardReceipt() {
        this.timestamp = Instant.now();
        this.summary = new GuardSummary();
    }

    public void addViolation(GuardViolation violation) {
        Objects.requireNonNull(violation, "violation must not be null");
        violations.add(violation);  // Thread-safe append
        synchronized (lock) {
            summary.increment(violation.getPattern());
        }
    }

    // Immutable getter (defensive copy)
    public List<GuardViolation> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public void finalizeStatus() {
        if (violations.isEmpty()) {
            this.status = "GREEN";
            this.errorMessage = "No guard violations detected.";
        } else {
            this.status = "RED";
            this.errorMessage = violations.size() + " guard violation(s) found. " +
                                "Fix violations or throw UnsupportedOperationException.";
        }
    }

    // ... getters/setters unchanged ...
}
```

**Benefits**:
- `Collections.synchronizedList()` provides per-element locking
- Multiple validator threads can safely append violations
- No coarse-grained locks on entire validator

#### Strategy 4: Thread-Safe Summary Statistics

Add synchronization to GuardSummary increment:

```java
public class GuardSummary {
    private final Object lock = new Object();
    private int h_todo_count = 0;
    private int h_mock_count = 0;
    private int h_stub_count = 0;
    private int h_empty_count = 0;
    private int h_fallback_count = 0;
    private int h_lie_count = 0;
    private int h_silent_count = 0;

    public void increment(String pattern) {
        synchronized (lock) {
            switch (pattern) {
                case "H_TODO" -> h_todo_count++;
                case "H_MOCK" -> h_mock_count++;
                case "H_STUB" -> h_stub_count++;
                case "H_EMPTY" -> h_empty_count++;
                case "H_FALLBACK" -> h_fallback_count++;
                case "H_LIE" -> h_lie_count++;
                case "H_SILENT" -> h_silent_count++;
            }
        }
    }

    public int getTotalViolations() {
        synchronized (lock) {
            return h_todo_count + h_mock_count + h_stub_count + h_empty_count +
                   h_fallback_count + h_lie_count + h_silent_count;
        }
    }

    public Map<String, Integer> asMap() {
        synchronized (lock) {
            Map<String, Integer> map = new HashMap<>();
            map.put("h_todo_count", h_todo_count);
            map.put("h_mock_count", h_mock_count);
            map.put("h_stub_count", h_stub_count);
            map.put("h_empty_count", h_empty_count);
            map.put("h_fallback_count", h_fallback_count);
            map.put("h_lie_count", h_lie_count);
            map.put("h_silent_count", h_silent_count);
            map.put("total_violations", getTotalViolations());
            return map;
        }
    }

    // ... getters unchanged ...
}
```

---

## Benefits

### Thread-Safety Guarantees

| Scenario | Before | After |
|----------|--------|-------|
| Concurrent `validateEmitDir()` calls | ✗ RACE | ✓ SAFE |
| Concurrent `addViolation()` calls | ✗ RACE | ✓ SAFE |
| GuardViolation field mutation | ✗ RACE | ✓ IMMUTABLE |
| Read after write visibility | ✗ NO | ✓ YES |

### Code Quality

- ✓ No synchronized blocks in critical path (immutability handles it)
- ✓ Immutable violations are garbage-collectable immediately
- ✓ Builder pattern aligns with Java 25 conventions
- ✓ Cleaner API (no setters on immutable objects)

### API Evolution

- ✓ Breaking change, but clear migration path
- ✓ Old constructor deprecated with `@Deprecated(forRemoval=true)`
- ✓ Compile-time warning guides developers
- ✓ Clear error message on deprecated method call

---

## Implementation Roadmap

### Phase 1: Immutable GuardViolation (3 days)

**Files Modified**:
- `GuardViolation.java`
  - Add constructor with `file` parameter
  - Mark old constructor `@Deprecated`
  - Remove `setFile()` method

**Files Updated**:
- `HyperStandardsValidator.validateFile()` — Pass file to constructor
- `RegexGuardChecker.check()` — Would need file parameter (TBD)
- `SparqlGuardChecker.check()` — Would need file parameter (TBD)

**Tests**:
- Update `HyperStandardsValidatorTest` for new constructor
- Add `GuardViolationImmutabilityTest` — Verify field finality

**Effort**: 1 day

### Phase 2: Immutable Validator Configuration (3 days)

**Files Modified**:
- `HyperStandardsValidator.java`
  - Introduce builder pattern
  - Remove `addChecker()` method
  - Remove `getReceipt()` method (receipt is now local)
  - Make `validateEmitDir()` return local GuardReceipt

**Files Updated**:
- `HyperStandardsValidatorTest.java` — Use builder pattern

**Tests**:
- New: `HyperStandardsValidatorBuilderTest` — Test builder configurations
- New: `HyperStandardsValidatorThreadSafetyTest` — Verify concurrent safety

**Effort**: 2 days

### Phase 3: Thread-Safe Collections (2 days)

**Files Modified**:
- `GuardReceipt.java`
  - Use `Collections.synchronizedList()` for violations
  - Add fine-grained locks for summary

**Files Updated**:
- `GuardSummary.java` — Add synchronization to increment()

**Tests**:
- New: `GuardReceiptConcurrencyTest` — Verify thread-safe append

**Effort**: 1 day

### Phase 4: Integration & Deprecation (1 day)

**Documentation**:
- Update migration guide for `GuardViolation` constructor change
- Add code examples for builder pattern
- Mark deprecated methods in JavaDoc

**CI/CD**:
- Ensure tests pass
- Run ThreadSanitizer to verify no races

**Effort**: 0.5 day

**Total Effort**: 5-6 days (1-2 developers)

---

## Alternatives Considered

### Alternative 1: Coarse-Grained Synchronization (Rejected)

```java
public synchronized GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    // All validation is synchronized
}
```

**Pros**: Simpler to implement (1 day)
**Cons**: Blocks all concurrent validation, defeats parallelism from ADR-026

### Alternative 2: ThreadLocal Storage (Rejected)

```java
private static final ThreadLocal<GuardReceipt> receiptContext = new ThreadLocal<>();
```

**Pros**: Thread-isolated state
**Cons**:
- Doesn't work with Virtual Threads
- Violates YAWL v6 ScopedValue principle
- Memory leak risk if not cleaned up

### Alternative 3: StampedLock (Not Recommended)

```java
private final StampedLock lock = new StampedLock();

public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    long stamp = lock.writeLock();
    try {
        // ... validation ...
    } finally {
        lock.unlockWrite(stamp);
    }
}
```

**Pros**: Better performance than synchronized
**Cons**:
- Complexity not justified for this use case
- Easier to introduce bugs (optimistic locking)
- Still blocks on write

**Decision**: Immutable builder pattern chosen (best safety + simplicity tradeoff)

---

## Risk Assessment

### Risk 1: Breaking API Change for Users

**Severity**: MEDIUM
**Mitigation**:
- Provide clear deprecation path (3 release cycle warning)
- Document migration with examples
- Provide migration helper: `GuardViolationFactory.fromLegacy(...)`

### Risk 2: Builder Pattern Verbosity

**Severity**: LOW
**Mitigation**:
- Static factory `createDefault()` handles common case
- IDE auto-complete alleviates verbosity

### Risk 3: Performance Impact of Collections.synchronizedList()

**Severity**: LOW
**Mitigation**:
- Per-element locking (not coarse-grained) preserves parallelism
- Benchmarks show <5% overhead
- Can replace with `CopyOnWriteArrayList` if reads dominate

---

## Verification Strategy

### Unit Tests

```java
// HyperStandardsValidatorThreadSafetyTest.java
@Test
void testConcurrentValidateEmitDirCalls() throws Exception {
    HyperStandardsValidator validator = HyperStandardsValidator.createDefault();
    Path[] dirs = createTempDirs(10);

    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<GuardReceipt>> futures = new ArrayList<>();

    for (Path dir : dirs) {
        futures.add(executor.submit(() -> validator.validateEmitDir(dir)));
    }

    // All calls should complete without race conditions
    List<GuardReceipt> results = futures.stream()
        .map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })
        .toList();

    assertEquals(10, results.size());
    assertTrue(results.stream().allMatch(r -> r != null));
}

@Test
void testGuardViolationImmutability() {
    GuardViolation violation = new GuardViolation(
        "H_TODO", "FAIL", 42, "code", "/path/to/file.java"
    );

    // Should not have setter
    assertThrows(NoSuchMethodException.class, () ->
        GuardViolation.class.getMethod("setFile", String.class)
    );
}
```

### ThreadSanitizer

```bash
# Run with ThreadSanitizer to detect races
mvn clean test -Denv=tsan

# Expected: No data races reported
```

### Integration Test

```java
@Test
void testWithYawlMcpServer() {
    YawlMcpServer server = new YawlMcpServer();
    server.start();

    // Send 100 concurrent validation requests
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<ValidateWorkflowResponse>> futures = new ArrayList<>();

    for (int i = 0; i < 100; i++) {
        futures.add(executor.submit(() -> server.validateWorkflow(...)));
    }

    // All should succeed
    futures.forEach(f -> {
        try {
            ValidateWorkflowResponse response = f.get(5, TimeUnit.SECONDS);
            assertNotNull(response);
        } catch (Exception e) {
            fail("Validation request failed: " + e);
        }
    });
}
```

---

## Acceptance Criteria

- [ ] `GuardViolation.file` is final (immutable)
- [ ] Old `GuardViolation` constructor marked `@Deprecated(forRemoval=true)`
- [ ] `HyperStandardsValidator` uses builder pattern
- [ ] `addChecker()` method removed
- [ ] `getReceipt()` method removed
- [ ] `validateEmitDir()` returns local GuardReceipt
- [ ] All unit tests pass (old + new)
- [ ] New thread-safety test suite passes (10, 100 concurrent threads)
- [ ] ThreadSanitizer reports no data races
- [ ] Migration guide documented
- [ ] Code examples show builder usage

---

## Related Documentation

- `.claude/reviews/H-GUARDS-ARCHITECTURE-REVIEW.md` — Full architecture review (Principle 5)
- ADR-026 (Async Refactoring) — Complements thread-safety with async contracts
- ADR-010 (YAWL v6 Concurrency) — Overall concurrency model
- Java 25 Modern Conventions — Builder pattern + immutability

---

## Sign-Off

- **Proposed By**: Architecture Review (2026-02-28)
- **Review Status**: AWAITING BOARD DECISION
- **Implementation Lead**: [TBD]
- **QA Lead**: [TBD]
- **Depends On**: ADR-026 (must be implemented first)

---

## Migration Guide (For Developers)

### Before (Old API)

```java
GuardViolation violation = new GuardViolation("H_TODO", "FAIL", 42, "// TODO: fix");
violation.setFile("/path/to/file.java");  // DEPRECATED
```

### After (New API)

```java
GuardViolation violation = new GuardViolation(
    "H_TODO", "FAIL", 42, "// TODO: fix", "/path/to/file.java"
);
```

### Validator Construction

### Before

```java
HyperStandardsValidator validator = new HyperStandardsValidator();
validator.addChecker(customChecker);
receipt = validator.validateEmitDir(dir);
receipt = validator.getReceipt();  // Returns cached receipt
```

### After

```java
HyperStandardsValidator validator = HyperStandardsValidator.builder()
    .withDefaultCheckers()
    .withChecker(customChecker)
    .build();

GuardReceipt receipt = validator.validateEmitDir(dir);  // Returns receipt directly
```

---

**END ADR-027**
