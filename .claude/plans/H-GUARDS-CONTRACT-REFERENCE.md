# H-Guards Contract Reference — Implementation Guide

**For**: Implementation team (during coding)
**Use Case**: Quick reference while implementing interfaces
**Related**: H-GUARDS-ARCHITECTURE.md (full spec), H-GUARDS-QUICK-START.md (phases)

---

## Interface Contracts

### GuardChecker Interface

```java
public interface GuardChecker {
    List<GuardViolation> check(Path javaSourcePath)
        throws IOException, GuardCheckException;
    String patternName();
    GuardSeverity severity();
    boolean validateConfig(GuardPatternConfig config);
}
```

**Contract Rules**:

| Rule | Requirement | Violation = Exception |
|------|-------------|----------------------|
| **No side effects** | check() MUST NOT modify input file | IOException if read-only violation |
| **Idempotent** | Same input → same output | GuardCheckException if non-deterministic |
| **Fail fast** | throw IOException on I/O errors (transient) | IOException |
| **Fail fast** | throw GuardCheckException on pattern/config errors (fatal) | GuardCheckException |
| **Ordered output** | Violations sorted by line number (ascending) | GuardCheckException if order violated |
| **Empty list on clean** | check() returns empty list if no violations | GuardCheckException if null |
| **Pattern name immutable** | patternName() always returns same value | GuardCheckException if varies |
| **Severity immutable** | severity() always returns same value | GuardCheckException if varies |
| **Config validation** | validateConfig() returns false on invalid config | GuardCheckException if throws |

**Implementation Notes**:

```java
// ✓ CORRECT: Early return on empty
public List<GuardViolation> check(Path path) throws IOException {
    var violations = new ArrayList<GuardViolation>();
    var lines = Files.readAllLines(path);  // May throw IOException

    for (int i = 0; i < lines.size(); i++) {
        if (matches(lines.get(i))) {
            violations.add(/* ... */);
        }
    }

    return violations;  // Empty list if no matches, never null
}

// ✗ WRONG: Null return
public List<GuardViolation> check(Path path) throws IOException {
    if (noViolations) return null;  // FAIL: should return empty list
}

// ✗ WRONG: Silent exception swallow
public List<GuardViolation> check(Path path) throws IOException {
    try {
        return performCheck(path);
    } catch (IOException e) {
        log.warn("Skipping file");  // FAIL: should propagate
        return new ArrayList<>();
    }
}

// ✗ WRONG: Non-deterministic
public List<GuardViolation> check(Path path) throws IOException {
    if (randomBoolean()) {  // FAIL: non-idempotent
        // different result each call
    }
}
```

---

### GuardViolation Record

```java
public record GuardViolation(
    String file,
    int line,
    String pattern,
    String severity,
    String content,
    String fixGuidance
) {}
```

**Field Contracts**:

| Field | Type | Constraint | Example |
|-------|------|-----------|---------|
| **file** | String | Absolute path, non-null | "/home/user/yawl/generated/YWorkItem.java" |
| **line** | int | 1-indexed, line >= 1 | 427 |
| **pattern** | String | One of 7 {H_TODO, H_MOCK, ...}, non-null | "H_TODO" |
| **severity** | String | {FAIL, WARN}, non-null | "FAIL" |
| **content** | String | Source code line(s), non-null, non-empty | "// TODO: Add deadlock detection" |
| **fixGuidance** | String | Human-readable fix, non-null, non-empty | "Implement real logic or throw UnsupportedOperationException" |

**Construction Contract**:

```java
// ✓ CORRECT
new GuardViolation(
    "/path/to/File.java",      // non-null absolute path
    427,                         // line >= 1
    "H_TODO",                    // valid pattern
    "FAIL",                       // valid severity
    "// TODO: implement",        // non-empty content
    "Implement or throw"         // non-empty guidance
);

// ✗ WRONG: line < 1
new GuardViolation("/path", 0, "H_TODO", "FAIL", "content", "guidance");

// ✗ WRONG: invalid pattern
new GuardViolation("/path", 1, "H_INVALID", "FAIL", "content", "guidance");

// ✗ WRONG: null file
new GuardViolation(null, 1, "H_TODO", "FAIL", "content", "guidance");

// ✗ WRONG: empty content
new GuardViolation("/path", 1, "H_TODO", "FAIL", "", "guidance");
```

**Immutability Contract**:
- All fields are final (record enforces)
- No setters
- Cannot be modified after construction

---

### GuardReceipt Record

```java
public record GuardReceipt(
    String phase,
    String status,
    Instant timestamp,
    int filesScanned,
    List<GuardViolation> violations,
    GuardSummary summary,
    String errorMessage,
    long durationMs
) {}
```

**Field Contracts**:

| Field | Type | Constraint | Example |
|-------|------|-----------|---------|
| **phase** | String | Always "guards", non-null | "guards" |
| **status** | String | {GREEN, RED}, non-null | "RED" |
| **timestamp** | Instant | UTC ISO-8601, non-null | 2026-02-28T14:32:15.123Z |
| **filesScanned** | int | >= 0 | 42 |
| **violations** | List | Possibly empty, non-null | List of GuardViolation |
| **summary** | GuardSummary | Non-null, counts match violations | GuardSummary(...) |
| **errorMessage** | String | Non-null, non-empty IFF status=RED | "3 violations found" |
| **durationMs** | long | >= 0 | 2847 |

**Invariant Contract** (MUST validate in record constructor):

```java
public GuardReceipt {
    Objects.requireNonNull(phase);
    Objects.requireNonNull(status);
    Objects.requireNonNull(timestamp);
    Objects.requireNonNull(violations);
    Objects.requireNonNull(summary);

    // Invariant: status RED ⟺ !violations.isEmpty()
    boolean isRed = status.equals("RED");
    boolean hasViolations = !violations.isEmpty();

    if (isRed != hasViolations) {
        throw new IllegalArgumentException(
            "Invariant violated: status=RED iff violations non-empty"
        );
    }

    // Invariant: RED status REQUIRES non-empty error message
    if (isRed && (errorMessage == null || errorMessage.isBlank())) {
        throw new IllegalArgumentException(
            "RED status requires non-empty errorMessage"
        );
    }

    // Invariant: GREEN status requires empty error message
    if (!isRed && errorMessage != null && !errorMessage.isBlank()) {
        throw new IllegalArgumentException(
            "GREEN status requires empty errorMessage"
        );
    }

    // Invariant: summary counts MUST match violations
    GuardSummary expected = GuardSummary.from(violations);
    if (!summary.equals(expected)) {
        throw new IllegalArgumentException(
            "Summary counts don't match violation list"
        );
    }

    if (filesScanned < 0) {
        throw new IllegalArgumentException("filesScanned >= 0");
    }

    if (durationMs < 0) {
        throw new IllegalArgumentException("durationMs >= 0");
    }
}
```

**Exit Code Mapping**:

```java
public int getExitCode() {
    return status.equals("GREEN") ? 0 : 2;
}
```

---

### HyperStandardsValidator Contract

```java
public GuardReceipt validateEmitDir(Path emitDir, Path receiptPath)
    throws IOException, GuardConfigException;
```

**Precondition**:
- `emitDir` must be readable directory
- `emitDir` must contain .java files to validate
- `receiptPath` must be writable

**Postcondition**:
- GuardReceipt returned with all violations
- Receipt written to receiptPath as JSON
- Violations sorted by (file, line number)
- Summary computed from violations

**Exception Contract**:
- `IOException`: File I/O error (transient, retry safe)
- `GuardConfigException`: Configuration error (fatal, fix required)
- No exceptions swallowed silently

**Exit Code Contract**:
- Return 0 (GREEN) if no violations
- Return 2 (RED) if violations found
- Never return 1 (transient errors propagate via exception)

---

## Pattern Specifications

### H_TODO: Deferred Work Markers

**Pattern Regex**:
```regex
//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)
```

**Must Detect**:
- `// TODO: implement this`
- `// FIXME: broken logic`
- `// XXX: edge case`
- `// HACK: temporary workaround`
- `// LATER: optimization`
- `// FUTURE: feature request`
- `// @incomplete`
- `// @stub`
- `// placeholder`

**Must NOT Detect**:
- Comments containing pattern in string: `// See TODO.md file`
- JavaDoc `@TODO` (not deferred marker)

**Fix Guidance**:
```text
Implement real logic or throw UnsupportedOperationException
```

---

### H_MOCK: Mock/Stub/Fake/Demo Names

**Pattern Regex** (for identifiers):
```regex
(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]
```

**Also detects** (class level):
```regex
^(Mock|Stub|Fake|Demo)[A-Za-z]*
```

**Must Detect**:
- `class MockDataService`
- `public MockService service;`
- `MockService mockService = new MockService();`
- `public String mockFetch() { }`
- `private StubData getData() { }`
- `FakeDatabase db = new FakeDatabase();`
- `DemoController demo = new DemoController();`

**Must NOT Detect**:
- Suffix patterns: `MyMockery`, `AbstractMock` (not starting with mock prefix)
- `getMockData()` (valid use in testing context)
- Class fields named `isMock` (boolean, not implementation)

**Fix Guidance**:
```text
Delete mock class or implement real service
```

---

### H_STUB: Empty/Placeholder Returns

**SPARQL Pattern**:
- Detect `return "";` in non-void methods
- Detect `return 0;` in non-void methods
- Detect `return null;` in non-void methods
- Detect `return Collections.empty*();` in non-void methods

**Must Detect**:
```java
public String getData() { return ""; }
public int getCount() { return 0; }
public String getName() { return null; }  // stub, should throw
public List<User> getUsers() { return Collections.emptyList(); }
public Map<String, Object> getConfig() { return new HashMap<>(); }
```

**Must NOT Detect**:
```java
public void initialize() { }  // void method, no return needed
public String getData() { return getRealData(); }  // real implementation
public Optional<String> get() { return Optional.empty(); }  // valid use
```

**Fix Guidance**:
```text
Implement real method or throw exception
```

---

### H_EMPTY: No-op Method Bodies

**SPARQL Pattern**:
- Detect `{ }` (empty braces) in void methods

**Must Detect**:
```java
public void initialize() { }
public void close() { }
protected void cleanup() { }
```

**Must NOT Detect**:
```java
public void initialize() { init(); }  // has content
public void close() { /* cleanup */ }  // has comment
public void noop() { return; }  // has return
```

**Fix Guidance**:
```text
Implement real logic or throw exception
```

---

### H_FALLBACK: Silent Catch-and-Fake

**SPARQL Pattern**:
- Detect `catch` blocks that return/assign fake data instead of re-throwing

**Must Detect**:
```java
try {
    return fetchRealData();
} catch (IOException e) {
    return Collections.emptyList();  // fake return
}

try {
    processEvent(event);
} catch (Exception e) {
    data = new HashMap<>();  // fake assignment
}
```

**Must NOT Detect**:
```java
try {
    return fetchRealData();
} catch (IOException e) {
    throw new RuntimeException("Failed to fetch", e);  // re-throw
}

try {
    return fetchRealData();
} catch (NotFoundException e) {
    log.warn("Not found", e);
    throw e;  // propagate
}
```

**Fix Guidance**:
```text
Propagate exception instead of faking data
```

---

### H_LIE: Code ≠ Documentation Mismatch

**SPARQL Pattern**:
- Detect methods where JavaDoc says one thing but code does another
- `@return never null` but code has `return null;`
- `@throws IOException` but no `throw IOException` in body
- `@return SomeObject` but code returns `null` or empty

**Must Detect**:
```java
/** @return never null */
public String getName() {
    return null;  // LIE: claims never null but returns null
}

/** @throws IOException if file not found */
public void readFile(String path) {
    // never throws IOException
    File f = new File(path);
}

/** Always returns a valid object */
public User getUser(String id) {
    return userCache.get(id);  // may return null
}
```

**Must NOT Detect**:
```java
/** @return user if found, null otherwise */
public User getUser(String id) {
    return userCache.get(id);  // doc says null is possible
}

/** @throws IOException if I/O fails */
public void readFile(String path) throws IOException {
    if (Files.exists(path)) {
        Files.readString(path);  // may throw IOException
    }
}
```

**Fix Guidance**:
```text
Update code to match documentation or update documentation to match code
```

---

### H_SILENT: Silent Logging Instead of Exception

**Pattern Regex**:
```regex
log\.(warn|error)\([^)]*"[^"]*not\s+implemented[^"]*"[^)]*\)
```

**Must Detect**:
```java
log.error("Not implemented yet");
log.warn("Feature not implemented");
logger.error("This method is not implemented");
log.error(String.format("Operation %s not implemented", op));
```

**Must NOT Detect**:
```java
throw new UnsupportedOperationException("Not implemented");
log.info("Processing");  // normal logging
log.error("Error occurred: " + e.getMessage());  // not about "not implemented"
```

**Fix Guidance**:
```text
Throw exception instead of logging
```

---

## Exit Code Contract

| Code | When | Action | Example |
|------|------|--------|---------|
| **0** | No violations found | Proceed to next phase | `ggen validate --phase invariants` |
| **1** | Transient error | Retry is safe | File I/O error, timeout |
| **2** | Violations found or fatal error | Fix and re-run | Guard violations detected |

**CLI Usage**:
```bash
ggen validate --phase guards --emit /path/to/generated
status=$?

if [ $status -eq 0 ]; then
    echo "Green: no violations"
elif [ $status -eq 1 ]; then
    echo "Transient error, retrying..."
    # retry
else
    echo "Red: violations found (status=$status)"
    exit 2
fi
```

---

## Data Flow Contracts

### From File to GuardViolation

```
File.java (input)
    ↓ [parse with tree-sitter]
AST (tree structure)
    ↓ [walk tree, extract features]
MethodInfo[] + CommentInfo[] (features)
    ↓ [convert to RDF]
Model (Jena RDF graph)
    ↓ [execute SPARQL query]
ResultSet (SPARQL results)
    ↓ [map to objects]
GuardViolation[] (violations)
    ↓ [sort by line number]
GuardViolation[] (sorted violations)
```

**Key Invariants**:
1. Each GuardViolation references a valid line in original file
2. GuardViolation.file = absolute path to File.java
3. GuardViolation.line = 1-indexed line number in file
4. GuardViolation.content = exact line text from file
5. Violations are sorted by (file, line) order

---

## JSON Receipt Contract

**Sample Receipt** (status = RED):
```json
{
  "phase": "guards",
  "status": "RED",
  "timestamp": "2026-02-28T14:32:15.123Z",
  "filesScanned": 42,
  "violations": [
    {
      "file": "/home/user/yawl/generated/YWorkItem.java",
      "line": 427,
      "pattern": "H_TODO",
      "severity": "FAIL",
      "content": "// TODO: Add deadlock detection",
      "fixGuidance": "Implement real logic or throw UnsupportedOperationException"
    }
  ],
  "summary": {
    "h_todo_count": 5,
    "h_mock_count": 2,
    "h_stub_count": 0,
    "h_empty_count": 3,
    "h_fallback_count": 1,
    "h_lie_count": 0,
    "h_silent_count": 0
  },
  "errorMessage": "5 guard violations found. Fix violations or throw UnsupportedOperationException.",
  "durationMs": 2847
}
```

**JSON Invariants**:
- `phase` always = "guards"
- `status` ∈ {GREEN, RED}
- `timestamp` format: ISO-8601 UTC (YYYY-MM-DDTHH:MM:SS.sssZ)
- `filesScanned` >= 0
- `violations` array sorted by (file ASC, line ASC)
- `summary.h_todo_count + ... + summary.h_silent_count = violations.length`
- `status = RED ⟺ violations.length > 0`
- `status = RED ⟺ errorMessage non-empty`
- `status = GREEN ⟺ violations.length = 0`
- `status = GREEN ⟺ errorMessage empty or null`

---

## Testing Contract

### Test Categories

**Positive Tests** (violations should be detected):
```
✓ violation-h-todo.java → detects 5+ H_TODO violations
✓ violation-h-mock.java → detects 3+ H_MOCK violations
✓ violation-h-stub.java → detects 4+ H_STUB violations
✓ violation-h-empty.java → detects 2+ H_EMPTY violations
✓ violation-h-fallback.java → detects 1+ H_FALLBACK violations
✓ violation-h-lie.java → detects 2+ H_LIE violations
✓ violation-h-silent.java → detects 1+ H_SILENT violations
```

**Negative Tests** (clean code should NOT be flagged):
```
✓ clean-code.java → 0 violations
✓ Comments in strings → not detected
✓ Legitimate mock usage (testing) → not detected
✓ Valid empty catch blocks → not detected
✓ Matching code + docs → not detected
```

**Edge Case Tests**:
```
✓ Multi-line comments
✓ Method names with "mock" as suffix (MyMockery)
✓ Return statements split across lines
✓ Nested try-catch blocks
✓ Anonymous classes
✓ Lambda expressions
```

---

## Performance Contract

| Metric | Target | Example |
|--------|--------|---------|
| **Per-file processing** | <5 seconds | Median 0.5s, max 4.9s |
| **Per-pattern latency** | <1 second | Regex <100ms, SPARQL <800ms |
| **SPARQL query timeout** | 30 seconds | Kill query if exceeds 30s |
| **Memory per file** | <100 MB | RDF model for typical Java file |
| **Total validation** | Linear O(n) | Time ∝ number of files |

**SLA Calculation**:
```
Total time = n files × (parsing + extraction + conversion + queries + reporting)
           ≤ n files × 5 seconds
           = 5n seconds

For 100 files: ≤ 500 seconds (8.3 minutes)
For 1000 files: ≤ 5000 seconds (83 minutes) — might need optimization
```

**If Performance Degrades**:
1. Check SPARQL query complexity (may need LIMIT clause)
2. Enable RDF caching (optional, disabled by default)
3. Process files in batches
4. Profile with JFR to identify bottleneck

---

## Exception Handling Contract

**IOException** (transient, retry safe):
- File not found
- Permission denied
- File size exceeds limit
- Read timeout

**GuardCheckException** (fatal, fix required):
- Invalid regex pattern
- SPARQL query error
- Configuration missing
- RDF conversion failure

**GuardConfigException** (fatal, fix required):
- guard-config.toml missing
- Invalid pattern name
- Regex fails to compile
- SPARQL file not found

**Principle**: Never swallow exceptions silently. Always propagate with context.

```java
// ✓ CORRECT: Propagate with context
try {
    return Files.readAllLines(path);
} catch (IOException e) {
    throw new IOException("Failed to read " + path, e);
}

// ✗ WRONG: Silent swallow
try {
    return Files.readAllLines(path);
} catch (IOException e) {
    log.warn("Skipping file");  // FAIL: data loss
    return new ArrayList<>();
}

// ✗ WRONG: Wrong exception type
try {
    return Files.readAllLines(path);
} catch (IOException e) {
    throw new GuardCheckException(e);  // FAIL: should be IOException (transient)
}
```

---

## Key Takeaways for Implementation

1. **GuardChecker** must be deterministic, idempotent, side-effect free
2. **GuardViolation** is immutable record with strict validation
3. **GuardReceipt** has invariants that MUST be validated in constructor
4. **Exit codes** are 0 (GREEN), 1 (transient), 2 (RED) — NOT variations
5. **Exceptions** are never swallowed — always propagate with context
6. **Performance** target is <5s per file (non-negotiable SLA)
7. **Accuracy** is 100% TP, 0% FP (both mandatory, not trade-offs)
8. **JSON receipts** must be valid and round-trip parseable
9. **Pattern specs** are complete and must be implemented exactly
10. **Tests** must cover positive + negative + edge cases for all 7 patterns

**When in doubt**: Refer to H-GUARDS-ARCHITECTURE.md for full specifications.
