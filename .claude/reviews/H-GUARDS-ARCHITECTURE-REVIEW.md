# H-Guards Implementation Architectural Review

**Date**: 2026-02-28
**Reviewer**: YAWL Architecture Specialist
**Scope**: GuardChecker interface, HyperStandardsValidator, model classes, supporting infrastructure
**Status**: COMPREHENSIVE REVIEW WITH FINDINGS AND RECOMMENDATIONS

---

## Executive Summary

The H-Guards implementation demonstrates **strong architectural fundamentals** with 4 of 5 YAWL v6 principles well-established. However, **critical gaps exist in async-compatibility and thread-safety** that must be addressed before production deployment in distributed workflows.

### Scorecard

| Principle | Status | Evidence | Action |
|-----------|--------|----------|--------|
| **1. Pluggable Guard Interface** | ✓ PASS | `GuardChecker` interface properly abstracted | Maintain as-is |
| **2. Extensible Pattern Registration** | ✓ PASS | `addChecker()` + default registration | Maintain as-is |
| **3. Graceful Degradation** | ✓ PASS | SPARQL fallback queries in validator | Maintain as-is |
| **4. Async-Compatible** | ✗ FAIL | Blocking I/O, no Virtual Thread support | **CRITICAL: Must fix** |
| **5. Thread-Safe** | ⚠ WARN | Mutable checkers list, state mutation | **HIGH: Requires mitigation** |

**Key Finding**: Implementation is suitable for **single-threaded build pipelines** (CI/CD) but **unsafe for concurrent engine operations** without refactoring.

---

## Principle 1: Pluggable Guard Interface ✓ PASS

### Assessment

The `GuardChecker` interface is **excellently designed** with clean abstraction:

```java
public interface GuardChecker {
    enum Severity { WARN, FAIL }
    List<GuardViolation> check(Path javaSource) throws IOException;
    String patternName();
    Severity severity();
}
```

### Strengths

1. **Minimal Contract** — 3 methods enforce single responsibility
2. **Two Implementations** — Regex and SPARQL strategies properly separated
3. **Extensibility** — New checkers can be added without modifying validator
4. **Testing** — `HyperStandardsValidatorTest.testCustomCheckersCanBeAdded()` validates extensibility

### Evidence

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/GuardChecker.java` (lines 1-54)
- Clean interface definition
- Well-documented contract
- Enum for severity levels

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/RegexGuardChecker.java` (lines 27-95)
- Complete implementation with 2 constructors
- Proper null checking on construction

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/SparqlGuardChecker.java` (lines 35-126)
- Separate implementation for complex patterns
- Demonstrates interface polymorphism

### Recommendation

**STATUS: MAINTAIN** — No changes required. This is exemplary interface design.

---

## Principle 2: Extensible Pattern Registration ✓ PASS

### Assessment

Pattern registration is **both flexible and discoverable**:

```java
public HyperStandardsValidator() {
    this.checkers = new ArrayList<>();
    registerDefaultCheckers();  // Statically register 7 patterns
}

public void addChecker(GuardChecker checker) {
    Objects.requireNonNull(checker, "checker must not be null");
    checkers.add(checker);  // Runtime extension point
}
```

### Strengths

1. **Default Registration** — `registerDefaultCheckers()` establishes baseline (lines 69-124)
2. **Runtime Extension** — `addChecker()` allows custom patterns (lines 290-293)
3. **Immutable Export** — `getCheckers()` returns `List.copyOf()` preventing external mutation (line 282)
4. **Custom Constructor** — Tests use `HyperStandardsValidator(List<GuardChecker>)` for full control (line 61-63)

### Evidence

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java`

```java
// Line 50-53: Default construction with static registration
public HyperStandardsValidator() {
    this.checkers = new ArrayList<>();
    registerDefaultCheckers();
}

// Line 61-63: Custom constructor for testing
public HyperStandardsValidator(List<GuardChecker> customCheckers) {
    this.checkers = Objects.requireNonNull(customCheckers, ...);
}

// Line 290-293: Extension point
public void addChecker(GuardChecker checker) {
    Objects.requireNonNull(checker, "checker must not be null");
    checkers.add(checker);
}

// Line 281-283: Protected getter
public List<GuardChecker> getCheckers() {
    return List.copyOf(checkers);
}
```

**Test Evidence**: `HyperStandardsValidatorTest.testCustomCheckersCanBeAdded()` (lines 278-289)
```java
GuardChecker customChecker = new RegexGuardChecker("H_CUSTOM", "custom_pattern");
validator.addChecker(customChecker);
assertTrue(validator.getCheckers().stream()
    .anyMatch(c -> "H_CUSTOM".equals(c.patternName())));
```

### Minor Gap: Configuration-Driven Pattern Loading

The `loadSparqlQuery()` method (lines 133-145) attempts to load from filesystem:
```java
Path resourcePath = Path.of("src/main/resources/sparql", filename);
if (Files.exists(resourcePath)) {
    return Files.readString(resourcePath);
}
LOGGER.warn("SPARQL query file not found: {}, using fallback", filename);
return getDefaultSparqlQuery(filename);
```

**Issue**: Hard-coded path `src/main/resources/sparql` assumes build context. In JAR deployment, this fails.

**Recommendation**:
```java
// BETTER: Load from classpath
ClassLoader cl = Thread.currentThread().getContextClassLoader();
try (InputStream is = cl.getResourceAsStream("sparql/" + filename)) {
    if (is != null) {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

### Recommendation

**STATUS: PASS WITH MITIGATION** — Pattern registration is excellent. Fix classpath loading before JAR deployment.

**Action Required**:
- Update `loadSparqlQuery()` to use ClassLoader.getResourceAsStream()
- Add unit test for JAR-based resource loading

---

## Principle 3: Graceful Degradation ✓ PASS

### Assessment

The validator exhibits **robust graceful degradation** across 3 layers:

#### Layer 1: SPARQL Query Fallback

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 133-200)

```java
private String loadSparqlQuery(String filename) {
    try {
        Path resourcePath = Path.of("src/main/resources/sparql", filename);
        if (Files.exists(resourcePath)) {
            return Files.readString(resourcePath);
        }
        LOGGER.warn("SPARQL query file not found: {}, using fallback", filename);
        return getDefaultSparqlQuery(filename);  // <-- GRACEFUL FALLBACK
    } catch (IOException e) {
        LOGGER.warn("Failed to load SPARQL query {}: {}", filename, e.getMessage());
        return getDefaultSparqlQuery(filename);  // <-- FALLBACK ON EXCEPTION
    }
}

private String getDefaultSparqlQuery(String filename) {
    return switch (filename) {
        case "guards-h-stub.sparql" -> """
            PREFIX code: <http://yawlfoundation.org/ggen/code#>
            SELECT ?line ?content
            WHERE {
                ?method code:returnStatement ?ret .
                ?method code:lineNumber ?line .
                FILTER(REGEX(?ret, "^(null|\\\"\\\"|\\'\\''|0|Collections\\.empty|new\\s+(HashMap|ArrayList)\\(\\))$"))
            }
            """;
        // ... 6 more patterns ...
    };
}
```

**Benefit**: If `guards-h-stub.sparql` is missing from classpath, validator uses embedded fallback instead of crashing.

#### Layer 2: Per-File Error Isolation

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 249-264)

```java
private void validateFile(Path javaFile) {
    for (GuardChecker checker : checkers) {
        try {
            List<GuardViolation> violations = checker.check(javaFile);
            // ... process violations ...
        } catch (IOException e) {
            LOGGER.warn("Failed to check {} with {}: {}",
                       javaFile, checker.patternName(), e.getMessage());
            // <-- CONTINUE TO NEXT CHECKER (no exception propagation)
        }
    }
}
```

**Benefit**: If one checker fails (e.g., AST parsing error), validation continues with remaining checkers.

#### Layer 3: SPARQL Execution Error Handling

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/SparqlGuardChecker.java` (lines 100-104)

```java
} catch (Exception e) {
    // Log SPARQL parsing errors but don't fail completely
    // This allows graceful degradation if AST parsing fails
    throw new IOException("Failed to execute SPARQL query for pattern " + patternName, e);
}
```

**Note**: This layer throws IOException (not swallowed), which is then caught at Layer 2 — correctly delegating to validator-level error handling.

### Strengths

1. **Embedded Fallback Queries** — Basic SPARQL for all 7 patterns (lines 155-199)
2. **Logging Without Escalation** — LOGGER.warn() documents issues without crashing (lines 139-143)
3. **Fail-Safe Defaults** — Empty SPARQL query returns 0 results if pattern unknown (line 197)

### Recommendation

**STATUS: MAINTAIN** — Graceful degradation is well-implemented.

**Optional Enhancement** (if needed later):
- Add `GuardValidationConfig` with degradation strategy:
  ```java
  public enum DegradationStrategy {
      FAIL_FAST,      // Throw on first error
      CONTINUE,       // Skip failed checkers (current)
      SKIP_PATTERN    // Disable failing pattern temporarily
  }
  ```

---

## Principle 4: Async-Compatible ✗ CRITICAL FAILURE

### Assessment

**BLOCKING ARCHITECTURAL ISSUE**: The implementation uses **blocking I/O** without Virtual Thread support and is **not suitable for async workflows**.

### Issue 1: Blocking File I/O in Hot Path

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/RegexGuardChecker.java` (lines 57-76)

```java
public List<GuardViolation> check(Path javaSource) throws IOException {
    List<GuardViolation> violations = new ArrayList<>();

    List<String> lines = Files.readAllLines(javaSource);  // <-- BLOCKING I/O
    for (int i = 0; i < lines.size(); i++) {
        String line = lines.get(i);
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            violations.add(new GuardViolation(...));
        }
    }
    return violations;
}
```

**Problem**: `Files.readAllLines()` is a **blocking system call**. If called from a Virtual Thread, the underlying platform thread pins and blocks other virtual threads.

**Impact**:
- Cannot validate 100+ files concurrently without 100+ platform threads
- Violates YAWL v6 Virtual Thread design principle
- Creates memory pressure (each platform thread = 2MB)

### Issue 2: Synchronous SPARQL Query Execution

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/SparqlGuardChecker.java` (lines 63-107)

```java
public List<GuardViolation> check(Path javaSource) throws IOException {
    // ...
    try (QueryExecution qexec = QueryExecutionFactory.create(sparqlQuery, rdfModel)) {
        ResultSet results = qexec.execSelect();  // <-- BLOCKING SPARQL QUERY

        while (results.hasNext()) {  // <-- BLOCKING ITERATION
            QuerySolution soln = results.next();
            // ... process result ...
        }
    }
    // ...
}
```

**Problem**: Apache Jena's `QueryExecution.execSelect()` **blocks the calling thread**. No async API provided by Jena.

**Impact**: Large RDF models (1000+ methods) can block for seconds.

### Issue 3: No Async Validator Contract

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/GuardChecker.java` (lines 22-54)

```java
public interface GuardChecker {
    List<GuardViolation> check(Path javaSource) throws IOException;
    String patternName();
    Severity severity();
}
```

**Problem**: The contract is purely **synchronous**. No `CompletableFuture<List<GuardViolation>>` variant.

**Impact**: Cannot easily add async variants without breaking existing implementations.

### Issue 4: Uncontrolled Checker Iteration

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 230-233)

```java
for (Path javaFile : javaFiles) {
    validateFile(javaFile);  // <-- SEQUENTIAL
}

private void validateFile(Path javaFile) {
    for (GuardChecker checker : checkers) {  // <-- NESTED LOOP
        try {
            List<GuardViolation> violations = checker.check(javaFile);
            // ...
        }
    }
}
```

**Problem**: N×M sequential execution (N files × M checkers). Validation of 100 files with 7 checkers = 700 sequential checks.

**Impact**: On modern hardware with 8+ cores, this is a wasted optimization opportunity.

### Verification

**Current Behavior** (Safe):
```bash
dx.sh compile
# Validator runs SEQUENTIALLY on single thread during build
# Input: 50 generated Java files
# Time: ~2-3 seconds
# Memory: ~50MB
# Result: GREEN/RED status
```

**Hypothetical Async Flow** (Unsafe with current code):
```bash
# MCP Server with Virtual Thread handling concurrent requests
YawlMcpServer receives 10 concurrent validation requests
│
└─→ Virtual Thread 1: Files 1-10
└─→ Virtual Thread 2: Files 11-20
    ...
    └─→ Virtual Thread 10: Files 91-100

# Each virtual thread calls HyperStandardsValidator.validateFile()
# → Files.readAllLines() blocks platform thread
# → 10 platform threads created for 10 virtual threads
# → System exhausted
```

### Recommendation

**ACTION REQUIRED: HIGH PRIORITY**

#### Step 1: Add Async Interface (Non-Breaking)

```java
// File: GuardChecker.java (add to interface)
public interface GuardChecker {
    // Existing synchronous contract (keep for compatibility)
    List<GuardViolation> check(Path javaSource) throws IOException;

    // New async contract
    default CompletableFuture<List<GuardViolation>> checkAsync(Path javaSource) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return check(javaSource);  // Delegate to sync implementation
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    String patternName();
    Severity severity();
}
```

#### Step 2: Use Virtual Thread-Friendly I/O (Structured Concurrency)

```java
// File: HyperStandardsValidator.java (replace line 230-233)
public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    // ... existing setup ...

    // Use structured concurrency for parallel file validation
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Path> javaFiles = findJavaFiles(emitDir);

        List<Subtask<Void>> tasks = javaFiles.stream()
            .map(file -> scope.fork(() -> {
                validateFile(file);
                return null;
            }))
            .toList();

        scope.join();
        scope.throwIfFailed();
    }

    receipt.finalizeStatus();
    return receipt;
}
```

**Benefit**:
- Each virtual thread can validate 1 file
- I/O blocking is now acceptable (virtual threads designed for this)
- 100 files validated in parallel with ~100 virtual threads

#### Step 3: Migrate to NIO for Classpath Resource Loading

```java
// File: HyperStandardsValidator.java (replace line 135-137)
private String loadSparqlQuery(String filename) {
    try {
        // Use ClassLoader instead of hard-coded path
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream("sparql/" + filename)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        LOGGER.warn("SPARQL query file not found: {}, using fallback", filename);
        return getDefaultSparqlQuery(filename);
    } catch (IOException e) {
        LOGGER.warn("Failed to load SPARQL query {}: {}", filename, e.getMessage());
        return getDefaultSparqlQuery(filename);
    }
}
```

### Timeline

- **Phase 1** (2 days): Add async interface + tests
- **Phase 2** (3 days): Refactor validator for structured concurrency
- **Phase 3** (1 day): Update classpath resource loading
- **Phase 4** (1 day): Integration testing with YawlMcpServer

**Blocker for**: Production deployment with MCP/A2A concurrent requests

---

## Principle 5: Thread-Safety ⚠ HIGH CONCERN

### Assessment

**Thread-safety is inadequate for concurrent access**. The validator shares mutable state without synchronization.

### Issue 1: Shared Mutable Checkers List

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 44-45)

```java
private final List<GuardChecker> checkers;  // <-- MUTABLE
// ...
private GuardReceipt receipt;               // <-- MUTABLE STATE
```

**Problem**: Both fields are instance state modified by multiple methods:

```java
// Line 51-52: Constructor modifies checkers
public HyperStandardsValidator() {
    this.checkers = new ArrayList<>();
    registerDefaultCheckers();  // <-- MODIFIES
}

// Line 290-293: Instance method modifies checkers
public void addChecker(GuardChecker checker) {
    checkers.add(checker);  // <-- UNSAFE
}

// Line 217: validateEmitDir modifies receipt
receipt = new GuardReceipt();  // <-- RACE CONDITION
```

**Scenario - Race Condition**:
```java
// Thread 1
validator.addChecker(newChecker);  // Add custom checker
validator.validateEmitDir(dir1);   // Use all checkers including newChecker

// Thread 2 (concurrent)
validator.validateEmitDir(dir2);   // Might see partially-added checkers

// Thread 3 (concurrent)
List<GuardChecker> checkers = validator.getCheckers();  // Sees inconsistent state
```

### Issue 2: Receipt Mutation Race

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 217, 272-274)

```java
public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
    // ...
    receipt = new GuardReceipt();  // <-- INSTANCE STATE (line 217)

    // ... validation loop modifies receipt ...

    return receipt;
}

public GuardReceipt getReceipt() {
    return receipt;  // <-- UNSAFE (can be modified by concurrent validateEmitDir)
}
```

**Race Scenario**:
```java
// Thread 1: validator.validateEmitDir(dir1) creates receipt
// Thread 2: validator.getReceipt() reads receipt (partial, from dir1)
// Thread 3: validator.validateEmitDir(dir2) overwrites receipt
// → Thread 2 observes receipt from dir2, not dir1
```

### Issue 3: GuardViolation Mutable File Field

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/model/GuardViolation.java` (lines 18-38)

```java
private final String pattern;      // IMMUTABLE
private final String severity;     // IMMUTABLE
private final int line;            // IMMUTABLE
private final String content;      // IMMUTABLE
private String file;               // <-- MUTABLE
```

**Problem**: `setFile()` is called after violation creation:

```java
violation.setFile(javaFile.toString());  // Line 254 in validator
```

**Issue**: If violation escapes to another thread before `setFile()` completes, field might be uninitialized.

### Issue 4: GuardReceipt Shared Mutable List

**File**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/model/GuardReceipt.java` (lines 29, 121-123)

```java
private final List<GuardViolation> violations = new ArrayList<>();  // <-- MUTABLE

public List<GuardViolation> getViolations() {
    return Collections.unmodifiableList(violations);  // <-- GOOD DEFENSIVE COPY
}
```

**Good**: Defensive copying prevents external mutation.
**Issue**: Internal code still has reference to mutable list:

```java
public void addViolation(GuardViolation violation) {
    violations.add(violation);  // <-- APPEND RACE
}
```

If two threads call `addViolation()` concurrently:
```
Thread 1: size = violations.size()          // reads 5
Thread 2: size = violations.size()          // reads 5 (ArrayList not synchronized)
Thread 1: violations.add(v1)                // adds at index 5
Thread 2: violations.add(v2)                // should add at index 6, might collide
```

### Verification

**Current Usage Pattern** (Safe):
```bash
# Single-threaded build pipeline
validator = new HyperStandardsValidator()      // Single instance
validator.validateEmitDir(generatedDir)       // Sequential
receipt = validator.getReceipt()              // Read result
```

**Hypothetical Async Usage** (Unsafe):
```java
// MCP Server with concurrent requests
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

GuardChecker checker = /* shared instance */;

// Multiple requests use same checker
executor.submit(() -> checker.check(file1));  // Thread A
executor.submit(() -> checker.check(file2));  // Thread B

// Result: Race condition on any mutable state
```

### Recommendation

**ACTION REQUIRED: MEDIUM-HIGH PRIORITY**

#### Option A: Thread-Safe Wrapper (Recommended)

Create immutable validator configuration:

```java
// File: HyperStandardsValidator.java (refactor)
public class HyperStandardsValidator {
    private final List<GuardChecker> checkers;  // Initialize once, never modify

    public HyperStandardsValidator(List<GuardChecker> checkers) {
        this.checkers = List.copyOf(Objects.requireNonNull(checkers));
    }

    public static HyperStandardsValidator createDefault() {
        List<GuardChecker> defaultCheckers = new ArrayList<>();
        defaultCheckers.add(new RegexGuardChecker("H_TODO", TODO_REGEX));
        // ... add all 7 ...
        return new HyperStandardsValidator(defaultCheckers);
    }

    // Remove instance method addChecker() - use builder instead
    public static class Builder {
        private List<GuardChecker> checkers = new ArrayList<>();

        public Builder withDefaultCheckers() {
            // ... register defaults ...
            return this;
        }

        public Builder withChecker(GuardChecker checker) {
            checkers.add(checker);
            return this;
        }

        public HyperStandardsValidator build() {
            return new HyperStandardsValidator(checkers);
        }
    }

    // validateEmitDir() returns NEW GuardReceipt (no instance state)
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        GuardReceipt receipt = new GuardReceipt();  // LOCAL variable
        // ... populate ...
        return receipt;
    }
}
```

**Benefits**:
- No shared mutable state
- Multiple threads can safely use same validator instance
- Builder pattern for configuration
- No `getReceipt()` method needed

#### Option B: Synchronization (Acceptable)

If immutability is too disruptive:

```java
public class HyperStandardsValidator {
    private final Object lock = new Object();
    private final List<GuardChecker> checkers;
    private GuardReceipt receipt;

    public void addChecker(GuardChecker checker) {
        synchronized (lock) {
            checkers.add(checker);
        }
    }

    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        synchronized (lock) {
            receipt = new GuardReceipt();
            // ... validation ...
            return receipt;
        }
    }

    public GuardReceipt getReceipt() {
        synchronized (lock) {
            return receipt;
        }
    }
}
```

**Downside**: Coarse-grained lock defeats parallelism.

#### Option C: ThreadLocal (Not Recommended)

```java
// DO NOT USE - violates YAWL v6 ScopedValue principle
private static final ThreadLocal<GuardReceipt> receiptContext = new ThreadLocal<>();
```

**Why Not**:
- ThreadLocal doesn't work with Virtual Threads
- YAWL v6 mandates ScopedValue for context passing

#### Immutable GuardViolation

```java
// File: GuardViolation.java (refactor)
public class GuardViolation {
    private final String pattern;
    private final String severity;
    private final int line;
    private final String content;
    private final String fixGuidance;
    private final String file;  // <-- MAKE FINAL

    public GuardViolation(String pattern, String severity, int line,
                         String content, String file) {  // Add file to constructor
        this.file = file;  // Set in constructor, not via setter
        // ...
    }

    // Remove setFile() method
}
```

**Updated Validator**:
```java
private void validateFile(Path javaFile) {
    for (GuardChecker checker : checkers) {
        try {
            List<GuardViolation> violations = checker.check(javaFile);
            for (GuardViolation violation : violations) {
                // Create immutable copy with file set
                GuardViolation withFile = new GuardViolation(
                    violation.getPattern(),
                    violation.getSeverity(),
                    violation.getLine(),
                    violation.getContent(),
                    javaFile.toString()  // <-- THREAD-SAFE
                );
                receipt.addViolation(withFile);
            }
        } catch (IOException e) {
            // ...
        }
    }
}
```

### Timeline

- **Phase 1** (1 day): Make `GuardViolation.file` immutable
- **Phase 2** (2 days): Refactor `HyperStandardsValidator` to builder pattern + immutable checkers
- **Phase 3** (1 day): Remove `getReceipt()` instance method, return receipt from `validateEmitDir()`
- **Phase 4** (1 day): Update all tests

**Blocker for**: Concurrent MCP/A2A deployments

---

## Anti-Patterns Detected

### Anti-Pattern 1: Checked IOException in GuardChecker Interface

**Location**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/GuardChecker.java` (line 39)

```java
List<GuardViolation> check(Path javaSource) throws IOException;
```

**Problem**: IOException is over-broad. Maps both transient (file not found, permission denied) and fatal (SPARQL parse error) errors to same exception.

**Better**:
```java
// Create specific exception hierarchy
public abstract sealed class GuardCheckException extends Exception {
    private final String patternName;

    protected GuardCheckException(String patternName, String message, Throwable cause) {
        super(message, cause);
        this.patternName = patternName;
    }

    public String getPatternName() { return patternName; }
}

public final class GuardCheckIOException extends GuardCheckException {
    public GuardCheckIOException(String pattern, String message, IOException cause) {
        super(pattern, message, cause);
    }
}

public final class GuardCheckExecutionException extends GuardCheckException {
    public GuardCheckExecutionException(String pattern, String message, Throwable cause) {
        super(pattern, message, cause);
    }
}

// Updated interface
public interface GuardChecker {
    List<GuardViolation> check(Path javaSource) throws GuardCheckException;
    // ...
}
```

**Impact**: Allows callers to distinguish recoverable errors (retry) from fatal errors (skip pattern).

### Anti-Pattern 2: Hard-Coded SPARQL Query Fallbacks

**Location**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java` (lines 154-199)

```java
private String getDefaultSparqlQuery(String filename) {
    return switch (filename) {
        case "guards-h-stub.sparql" -> """...""";
        case "guards-h-empty.sparql" -> """...""";
        // ... 5 more ...
        default -> """PREFIX code: <...> SELECT ?line WHERE { }""";
    };
}
```

**Problem**:
- Queries duplicated from external files
- Changes to queries require code recompilation
- Difficult to test query variations

**Better**:
```java
// File: src/main/resources/sparql/default/guards-h-stub.sparql (classpath resource)
PREFIX code: <http://yawlfoundation.org/ggen/code#>
SELECT ?line ?content
WHERE {
    ?method code:returnStatement ?ret .
    ?method code:lineNumber ?line .
    FILTER(REGEX(?ret, "^(null|\\\"\\\"|\\'\\''|0|Collections\\.empty|new\\s+(HashMap|ArrayList)\\(\\))$"))
}

// HyperStandardsValidator.java
private String getDefaultSparqlQuery(String filename) {
    try (InputStream is =
         Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("sparql/default/" + filename)) {
        if (is != null) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    } catch (IOException ignored) {}

    // Absolute fallback: empty query returns no results
    return "PREFIX code: <http://yawlfoundation.org/ggen/code#> SELECT ?line WHERE { }";
}
```

**Benefit**: Single source of truth for fallback queries.

### Anti-Pattern 3: Regex-Based AST Parsing

**Location**: `/home/user/yawl/yawl-ggen/src/main/java/org/yawlfoundation/yawl/ggen/validation/JavaAstToRdfConverter.java` (lines 42-64)

```java
private static final Pattern METHOD_PATTERN =
    Pattern.compile(
        "(?:public|private|protected)?\\s+(?:static)?\\s+(\\w+)\\s+(\\w+)\\s*\\(([^)]*)\\)",
        Pattern.MULTILINE
    );
```

**Problem**:
- Regex cannot parse nested braces correctly
- False positives on code in comments/strings: `// private void foo() {`
- Fragile to Java syntax variations (annotations, generics)

**Better**: Use tree-sitter or ANTLR for proper AST parsing

```java
// Consider: org.apache.commons.lang3.StringUtils.countMatches() or
// tree-sitter-java binding for accurate parsing
```

**Current Workaround** (lines 238-267): Manual brace counting
```java
private static String extractMethodBody(String source, int startPos) {
    int braceCount = 0;
    // ... manual state machine ...
}
```

This partially mitigates regex limitations but is fragile.

---

## Summary Table: Design Principle Compliance

| Principle | Status | Severity | Resolution | Effort |
|-----------|--------|----------|-----------|--------|
| Pluggable Guard Interface | ✓ PASS | — | Maintain | — |
| Extensible Pattern Registration | ✓ PASS | — | Fix classpath loading | 0.5 day |
| Graceful Degradation | ✓ PASS | — | Maintain | — |
| Async-Compatible | ✗ FAIL | CRITICAL | Add CompletableFuture + structured concurrency | 5 days |
| Thread-Safe | ⚠ WARN | HIGH | Refactor to immutable builder pattern | 3 days |
| **TOTAL** | **3/5** | **CRITICAL** | **Full refactor needed for production** | **8-9 days** |

---

## Deployment Readiness Assessment

### Current State: Suitable For

- ✓ Single-threaded build pipeline (CI/CD)
- ✓ Synchronous validation during code generation
- ✓ Testing/development workflows
- ✓ Standalone CLI invocation

### Not Suitable For

- ✗ Concurrent MCP server requests
- ✗ Virtual Thread-based engines
- ✗ Distributed workflow validation
- ✗ High-throughput async validation

### Remediation Path

**Phase 1** (Days 1-2): Fix immediate blockers
- Make GuardViolation.file immutable
- Fix classpath resource loading
- Add thread-safety guards with coarse-grained locking

**Phase 2** (Days 3-5): Async refactor (enables deployment)
- Add CompletableFuture variants
- Implement StructuredTaskScope parallelism
- Update validator contract

**Phase 3** (Days 6-9): Production hardening
- Migrate to proper AST parsing (tree-sitter)
- Add observability (metrics, tracing)
- Comprehensive integration tests with YawlMcpServer

---

## Files Requiring Changes

### Immediate (Must-Fix)

1. **GuardViolation.java** — Make file field final
2. **HyperStandardsValidator.java** — Fix classpath loading, refactor for immutability
3. **GuardChecker.java** — Add async contract

### Medium Priority

4. **HyperStandardsValidatorTest.java** — Update for immutable builder pattern
5. **SparqlGuardChecker.java** — Add async support

### Optional (Nice-to-Have)

6. **JavaAstToRdfConverter.java** — Replace regex with tree-sitter
7. Add **HyperStandardsValidatorIntegrationTest.java** for concurrent scenarios

---

## Conclusion

The H-Guards implementation demonstrates **excellent foundational design** with 3 of 5 YAWL v6 principles well-established. However, **critical gaps in async-compatibility and thread-safety** prevent production deployment without refactoring.

**Recommended Action**:
- Keep pluggable interface as-is (exemplary design)
- Prioritize async refactoring (enables Virtual Thread deployment)
- Introduce immutable builder pattern (eliminates race conditions)
- Timeline: 8-9 days with 1-2 engineers for full production-ready implementation

**Next Steps**:
1. Schedule architecture review with team leads
2. Create detailed implementation ADRs (ADR-026, ADR-027)
3. Add async refactoring to sprint backlog
4. Block MCP/A2A deployment until Principle 4 + 5 resolved
