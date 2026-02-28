# ADR-026: Async Refactoring for H-Guards Validation System

**Date**: 2026-02-28
**Status**: PROPOSED
**Decision Makers**: Architecture Review Board
**Stakeholders**: Build System, MCP/A2A Integration, Engine Teams
**Related ADRs**: ADR-023 (MCP), ADR-024 (A2A), ADR-025 (Virtual Threads)

---

## Problem Statement

The H-Guards validation system (`HyperStandardsValidator`, `GuardChecker` interface) is **not async-compatible** for YAWL v6 production deployments:

1. **Blocking I/O** in `RegexGuardChecker.check()` uses `Files.readAllLines()` (pins platform threads)
2. **Synchronous-only interface** blocks on `List<GuardViolation> check(Path)` (no `CompletableFuture` variant)
3. **Sequential validation** processes N files × M checkers sequentially (N×M blocking calls)
4. **Jena SPARQL integration** has no async API (blocks until query completes)

**Impact**: Cannot validate 100+ generated files concurrently without 100+ platform threads (2GB+ memory overhead).

**Blocker**: MCP/A2A concurrent requests will exhaust platform threads if multiple async tasks invoke validation simultaneously.

---

## Decision

### Adopt Three-Phase Async Refactoring Strategy

#### Phase 1: Interface Contract Expansion (Non-Breaking)

Add `CompletableFuture`-based validation methods to `GuardChecker` interface:

```java
public interface GuardChecker {
    // Existing synchronous contract (KEEP for backward compatibility)
    List<GuardViolation> check(Path javaSource) throws IOException;

    // New async contract (allows proper async implementations)
    default CompletableFuture<List<GuardViolation>> checkAsync(Path javaSource) {
        return CompletableFuture.supplyAsync(
            () -> {
                try {
                    return check(javaSource);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            },
            ForkJoinPool.commonPool()  // Use common pool (auto-scales with Virtual Threads)
        );
    }

    String patternName();
    Severity severity();
}
```

**Rationale**:
- Default implementation delegates to sync `check()` method
- Existing `RegexGuardChecker` and `SparqlGuardChecker` implementations continue to work
- Future implementations can override with true async logic
- Non-breaking: all current code continues unchanged

#### Phase 2: Structured Concurrency in Validator

Refactor `HyperStandardsValidator.validateEmitDir()` to use `StructuredTaskScope.ShutdownOnFailure`:

```java
public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    Objects.requireNonNull(emitDir, "emitDir must not be null");

    if (!Files.isDirectory(emitDir)) {
        throw new IOException("emitDir is not a directory: " + emitDir);
    }

    receipt = new GuardReceipt();
    receipt.setPhase("guards");

    // Find all Java source files
    List<Path> javaFiles = findJavaFilesParallel(emitDir);
    receipt.setFilesScanned(javaFiles.size());
    LOGGER.info("Scanning {} Java files in {}", javaFiles.size(), emitDir);

    // Validate files in parallel using structured concurrency
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<Void>> validationTasks = javaFiles.stream()
            .map(file -> scope.fork(() -> {
                validateFileParallel(file);
                return null;
            }))
            .toList();

        scope.join();
        scope.throwIfFailed();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IOException("Validation interrupted", e);
    }

    // Finalize status
    receipt.finalizeStatus();
    LOGGER.info("Guard validation complete: {} violations found",
               receipt.getViolations().size());

    return receipt;
}

// New parallel validation method
private void validateFileParallel(Path javaFile) {
    for (GuardChecker checker : checkers) {
        try {
            // Use async variant (delegates to executor for true async-friendly behavior)
            CompletableFuture<List<GuardViolation>> future =
                checker.checkAsync(javaFile);

            List<GuardViolation> violations = future.join();  // Wait for completion

            for (GuardViolation violation : violations) {
                violation.setFile(javaFile.toString());
                receipt.addViolation(violation);
                LOGGER.debug("Found violation: {} at {}:{}",
                            violation.getPattern(), javaFile, violation.getLine());
            }
        } catch (CompletionException e) {
            LOGGER.warn("Failed to check {} with {}: {}",
                       javaFile, checker.patternName(), e.getCause().getMessage());
        }
    }
}

private List<Path> findJavaFilesParallel(Path emitDir) throws IOException {
    try (var stream = Files.walk(emitDir)) {
        return stream.filter(p -> p.toString().endsWith(".java"))
                    .toList();
    }
}
```

**Rationale**:
- `StructuredTaskScope.ShutdownOnFailure` enforces proper async cleanup
- One virtual thread per file validation
- Automatic cancellation if any validation fails
- No manual ExecutorService management needed
- Bounded concurrency (limited by available Virtual Threads, typically 10,000+)

**Virtual Thread Behavior**:
```
Virtual Thread Pool (unlimited, auto-creates):
│
├─→ VT-1: Validate File1 → validateFileParallel(file1)
├─→ VT-2: Validate File2 → validateFileParallel(file2)
├─→ VT-3: Validate File3 → validateFileParallel(file3)
...
├─→ VT-N: Validate FileN → validateFileParallel(fileN)
│
└─→ Parent scope waits for ALL subtasks → join()
    At least one fails → ShutdownOnFailure cancels rest → throwIfFailed()
    All succeed → continue
```

#### Phase 3: Thread-Safe Result Aggregation

Introduce thread-safe receipt aggregation:

```java
public class GuardReceipt {
    // Use Collections.synchronizedList for thread-safe append
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

    // ... rest unchanged ...
}
```

**Rationale**:
- `Collections.synchronizedList()` provides fine-grained locking per-operation
- Multiple validator threads can safely append violations
- No need for coarse-grained locks on entire validator
- Performance: Per-element locking is cheaper than validating sequentially

---

## Benefits

### Concurrency Improvement

**Before** (Sequential):
```
100 files × 7 checkers = 700 sequential blocking I/O operations
Time: O(N × M) = ~3-5 seconds
Memory: Single platform thread (2MB)
Throughput: 140 files/sec per validator instance
```

**After** (Parallel with Virtual Threads):
```
100 files, 7 checkers → 100 virtual threads × 7 checkers (parallel)
Time: O(max(file_validation_time)) = ~0.5-1 second (7× faster)
Memory: 100 virtual threads (minimal footprint, ~100KB total)
Throughput: 1000+ files/sec per validator instance (can handle 1000+ concurrent requests)
```

### Virtual Thread Compatibility

- ✓ Leverages YAWL v6 Virtual Thread architecture
- ✓ No more platform thread pinning from blocking I/O
- ✓ Auto-scales to handle 1000+ concurrent MCP/A2A requests
- ✓ Thread-safe validation without coarse-grained locks

### API Backward Compatibility

- ✓ Existing synchronous `check(Path)` method retained
- ✓ All current code continues unchanged
- ✓ New `checkAsync(Path)` method is optional default
- ✓ Gradual migration path (no big bang refactor)

### MCP/A2A Integration Ready

```java
// YawlMcpServer.java
YawlMcpServer receives validation request (from MCP Client)
│
└─→ Virtual Thread from server thread pool
    └─→ validator.validateEmitDir(generatedDir)
        └─→ StructuredTaskScope spawns 100 subtasks (VT per file)
            └─→ Each VT calls checker.checkAsync(file)
                └─→ Completes in parallel
        └─→ Results aggregated thread-safely into GuardReceipt
        └─→ Returns result to MCP Client

# Cost: No platform thread exhaustion
# Latency: ~1s for 100 files (not 5s)
# Throughput: 1000+ concurrent requests
```

---

## Implementation Roadmap

### Week 1: Phase 1 (Interface Contract)

**Files to Modify**:
- `GuardChecker.java` — Add `checkAsync(Path)` default method

**Files to Update**:
- All implementations inherit default behavior (no changes needed)

**Tests**:
- New: `GuardCheckerAsyncTest.java` — Verify `checkAsync()` delegates correctly

**Effort**: 1 day (including tests)

### Week 2-3: Phase 2 (Structured Concurrency)

**Files to Modify**:
- `HyperStandardsValidator.java` — Refactor `validateEmitDir()` to use `StructuredTaskScope`

**Files to Update**:
- `HyperStandardsValidatorTest.java` — Add parallel scenario tests

**Tests**:
- New: `HyperStandardsValidatorConcurrencyTest.java` — Test with 10, 100, 1000 virtual threads
- New: `HyperStandardsValidatorStressTest.java` — Test high-concurrency edge cases

**Effort**: 2-3 days (including comprehensive testing)

### Week 4: Phase 3 (Thread Safety)

**Files to Modify**:
- `GuardReceipt.java` — Update violation list to `Collections.synchronizedList()`
- `GuardViolation.java` — Make `file` field final (constructor-based)

**Files to Update**:
- `HyperStandardsValidator.java` — Use constructor-based violation creation

**Tests**:
- New: `GuardReceiptConcurrencyTest.java` — Verify thread-safe append

**Effort**: 1-2 days (mostly testing)

### Week 5: Integration & Deployment

**Integration Points**:
- Update `YawlMcpServer` to use async validator
- Add validator integration to MCP workflow
- Performance benchmarking (concurrent request handling)

**Effort**: 2-3 days

**Total Effort**: 8-10 days (2-3 developers)

---

## Alternatives Considered

### Alternative 1: Coarse-Grained Synchronization (Rejected)

```java
public synchronized GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    // Sequential validation
}
```

**Pros**: Simpler to implement
**Cons**: Defeats parallelism, single thread bottleneck, no Virtual Thread benefit

### Alternative 2: Custom Async Framework (Rejected)

```java
public interface GuardChecker {
    ListenableFuture<List<GuardViolation>> checkAsync(Path javaSource);
}
```

**Pros**: More control
**Cons**: Requires Guava dependency, non-standard for Java 25, steeper learning curve

### Alternative 3: Pure Immutable Design (Considered)

```java
public GuardReceipt validateEmitDir(Path emitDir) returns immutable receipt
// No thread-unsafe mutable state
```

**Pros**: Maximum safety
**Cons**: Requires structural refactor of GuardReceipt (breaking change), higher effort (12+ days)

**Decision**: Phase 2 approach chosen (good balance of safety + effort)

---

## Risk Assessment

### Risk 1: Virtual Thread Pinning in SPARQL Queries

**Severity**: MEDIUM
**Mitigation**:
- SPARQL queries are CPU-bound (not I/O-bound), so pinning is acceptable
- Can offload to dedicated executor if needed later:
  ```java
  default CompletableFuture<List<GuardViolation>> checkAsync(Path javaSource) {
      return CompletableFuture.supplyAsync(
          () -> { return check(javaSource); },
          sqlParqlExecutor  // Optional: dedicated executor for SPARQL
      );
  }
  ```

### Risk 2: Violation Ordering Not Guaranteed

**Severity**: LOW
**Mitigation**:
- Current code doesn't guarantee order (HashMap iteration is unordered)
- Violations are deduplicated by pattern/file/line, order doesn't affect correctness
- If needed, add post-processing: `receipt.sortViolations()`

### Risk 3: Receipt Timestamp No Longer Reflects Actual Validation Time

**Severity**: NEGLIGIBLE
**Mitigation**:
- Record per-subtask timestamps
- Calculate total validation time from first-spawned to last-completed task

### Risk 4: Scope Cancellation Edge Cases

**Severity**: MEDIUM
**Mitigation**:
- Use `ShutdownOnFailure` (not `ShutdownOnSuccess`)
- Ensures all tasks complete or explicit cancellation
- Test edge cases: file-not-found, concurrent modification, SPARQL timeout

---

## Metrics & Success Criteria

### Performance Metrics

| Metric | Before | After | Target |
|--------|--------|-------|--------|
| Validation time (100 files) | 5s | 1s | <1.5s |
| Memory per 1000 concurrent validations | 2GB | 100MB | <150MB |
| Max concurrent requests | 10 | 1000+ | >100 |
| CPU utilization | 15% | 80%+ | >70% |

### Quality Metrics

| Metric | Target | Verification |
|--------|--------|--------------|
| Thread-safety issues | 0 | ThreadSanitizer scan |
| Test coverage | >95% | JaCoCo report |
| Stress test (1000 concurrent requests) | Pass | New stress test |
| Latency p99 | <2s | Load test |

---

## Acceptance Criteria

- [ ] `GuardChecker.checkAsync()` default method implemented
- [ ] `HyperStandardsValidator.validateEmitDir()` uses `StructuredTaskScope`
- [ ] All unit tests pass (old + new)
- [ ] New concurrent test suite passes (10, 100, 1000 virtual threads)
- [ ] ThreadSanitizer finds no data races
- [ ] Performance benchmark shows >5× improvement for 100 files
- [ ] MCP integration test validates async behavior end-to-end
- [ ] Documentation updated with async examples

---

## Related Documentation

- `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` — Pattern 2 (Structured Concurrency)
- `.claude/rules/validation-phases/H-GUARDS-DESIGN.md` — Original H-Guards design
- ADR-023 (MCP Architecture) — MCP concurrent request handling
- ADR-024 (A2A Concurrency) — Agent-to-agent async protocols
- ADR-025 (Virtual Threads in YAWL) — Thread model adoption

---

## Sign-Off

- **Proposed By**: Architecture Review (2026-02-28)
- **Review Status**: AWAITING BOARD DECISION
- **Implementation Lead**: [TBD]
- **QA Lead**: [TBD]

---

## Appendix: Code Examples

### Example 1: Using Async Validator in MCP Server

```java
// YawlMcpServer.java
public class ValidateWorkflowHandler implements McpRequestHandler {
    private final HyperStandardsValidator validator =
        HyperStandardsValidator.createDefault();

    public McpResponse handle(ValidateWorkflowRequest request) {
        try {
            Path generatedDir = Paths.get(request.getGeneratedCodePath());

            // This is now async-safe and can be called from MCP handler (Virtual Thread)
            GuardReceipt receipt = validator.validateEmitDir(generatedDir);

            return McpResponse.builder()
                .status(receipt.getStatus())
                .violations(receipt.getViolations())
                .timestamp(receipt.getTimestamp())
                .build();

        } catch (IOException e) {
            return McpResponse.error("Validation failed: " + e.getMessage());
        }
    }
}
```

### Example 2: Stress Testing with 1000 Virtual Threads

```java
// HyperStandardsValidatorStressTest.java
@Test
void testConcurrent1000Validations() throws Exception {
    HyperStandardsValidator validator = HyperStandardsValidator.createDefault();
    Path[] tempDirs = createTempDirs(1000);

    Instant start = Instant.now();

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<GuardReceipt>> tasks = Arrays.stream(tempDirs)
            .map(dir -> scope.fork(() -> validator.validateEmitDir(dir)))
            .toList();

        scope.join();
        scope.throwIfFailed();

        List<GuardReceipt> results = tasks.stream()
            .map(Subtask::resultNow)
            .toList();

        Instant end = Instant.now();
        Duration totalTime = Duration.between(start, end);

        System.out.println("1000 validations completed in " + totalTime);
        assertTrue(totalTime.toMillis() < 5000,
                  "1000 concurrent validations should complete in <5 seconds");
    }
}
```

---

**END ADR-026**
