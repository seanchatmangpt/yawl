# HYPER_STANDARDS Pattern Reference

**Quadrant**: Reference | **Source of truth**: `.claude/hooks/hyper-validate.sh`

All 14 forbidden patterns checked on every `Write` or `Edit` to a Java source file. The hook exits 2 and blocks the operation if any pattern matches. There is no warning-only mode — every match is a blocker.

---

## Pattern Table

| ID | Name | Severity | Detection Method |
|----|------|----------|-----------------|
| H1 | Deferred work markers | BLOCKER | Regex on comments |
| H2 | Mock/stub method names | BLOCKER | Regex on identifiers |
| H3 | Mock/stub class names | BLOCKER | Regex on class declarations |
| H4 | Mock mode flags | BLOCKER | Regex on field assignments |
| H5 | Empty string return | BLOCKER | Regex on return statements |
| H6 | Null return with stub comment | BLOCKER | Regex on return + comment |
| H7 | Empty method body | BLOCKER | Regex on `void` method declarations |
| H8 | Placeholder constants | BLOCKER | Regex on constant names |
| H9 | Silent fallback catch | BLOCKER | Regex on catch blocks |
| H10 | Conditional mock behavior | BLOCKER | Regex on conditional returns |
| H11 | Fake getOrDefault value | BLOCKER | Regex on `.getOrDefault(` |
| H12 | Early return skips logic | BLOCKER | Regex on `if (true) return` |
| H13 | Log instead of throw | BLOCKER | Regex on `log.warn/error` |
| H14 | Mockito import in `src/` | BLOCKER | Regex on import statements |

---

## H1 — Deferred Work Markers

**What it catches**: Comments that defer implementation to the future.

**Regex**:
```
//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|NOTE:.*implement|REVIEW:.*implement|TEMPORARY|@incomplete|@unimplemented|@stub|@mock|@fake|not\s+implemented\s+yet|coming\s+soon|placeholder|for\s+demo|simplified\s+version|basic\s+implementation)
```

**Forbidden examples**:
```java
// TODO: implement deadlock detection
// FIXME: broken logic here
// HACK: temporary workaround
// @stub
// placeholder for now
```

**Required fix**:
```java
// Option A — implement it:
if (hasCircularDependency(token)) {
    throw new YEngineException("Deadlock detected in net " + netId);
}

// Option B — declare impossible:
throw new UnsupportedOperationException(
    "Deadlock detection not implemented. See issue #42.");
```

---

## H2 — Mock/Stub Method Names

**What it catches**: Methods or variables named with `mock`, `stub`, `fake`, or `demo` prefix.

**Regex**:
```
(mock|stub|fake|demo)[A-Z][a-zA-Z]*\s*[=(]
```

**Forbidden examples**:
```java
public String mockFetch() { return "fake"; }
Data testData = new Data();
String fakeResponse = "";
```

**Required fix**: Rename to describe real behavior. `mockFetch` → `fetchFromApi`. Delete mock implementations; use real objects with H2 in-memory DB in tests.

---

## H3 — Mock/Stub Class Names

**What it catches**: Class or interface declarations named with `Mock`, `Stub`, `Fake`, or `Demo` prefix.

**Regex**:
```
(class|interface)\s+(Mock|Stub|Fake|Demo)[A-Za-z]*\s+(implements|extends|\{)
```

**Forbidden examples**:
```java
public class MockDataService implements DataService { }
public class FakeRepository extends Repository { }
```

**Required fix**: Delete the mock class. In `src/`, only real implementations are allowed. In `test/`, mocks may exist if the class name starts with `Test` (not `Mock`/`Fake`).

---

## H4 — Mock Mode Flags

**What it catches**: Boolean fields that switch mock behavior on or off.

**Regex**:
```
(is|use|enable|allow)(Mock|Fake|Demo|Stub)(Mode|Data|ing)\s*=
```

**Forbidden examples**:
```java
private boolean useMockData = true;
private static final boolean MOCK_MODE = true;
private boolean isFakeMode = false;
```

**Required fix**: Remove the flag entirely. Implement the real path. If the real infrastructure isn't available, `throw UnsupportedOperationException`.

---

## H5 — Empty String Return

**What it catches**: Methods that return `""` as a placeholder.

**Regex**:
```
return\s+""\s*;
```

**Exception**: Returns within string formatting logic (error messages, config defaults with context) are excluded by a secondary filter.

**Forbidden example**:
```java
public String getSpecificationId() { return ""; }
```

**Required fix**:
```java
public String getSpecificationId() {
    return Objects.requireNonNull(specId,
        "getSpecificationId called before specification was loaded");
}
```

---

## H6 — Null Return with Stub Comment

**What it catches**: `return null;` annotated with stub-indicating comments.

**Regex**:
```
return\s+null\s*;.*//\s*(stub|todo|placeholder|not\s+implemented|temporary)
```

**Forbidden example**:
```java
public User findUser(String id) { return null; // stub }
```

**Required fix**:
```java
public User findUser(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
}
```

---

## H7 — Empty Method Body

**What it catches**: `void` methods with empty bodies `{ }`.

**Regex**:
```
public\s+void\s+\w+\([^)]*\)\s*\{\s*\}
```

**Forbidden example**:
```java
public void initialize() {}
public void save(Data data) {}
```

**Required fix**:
```java
public void initialize() {
    schemaValidator.ensureSchemaVersion(SCHEMA_VERSION);
    connectionPool.warmUp(MIN_CONNECTIONS);
}

// OR if logic is not yet known:
public void initialize() {
    throw new UnsupportedOperationException(
        "initialize: schema bootstrapping not yet wired up. See #88.");
}
```

---

## H8 — Placeholder Constants

**What it catches**: Named constants with `DUMMY_`, `PLACEHOLDER_`, `MOCK_`, or `FAKE_` prefix.

**Regex**:
```
(DUMMY|PLACEHOLDER|MOCK|FAKE)_[A-Z_]+\s*=
```

**Forbidden examples**:
```java
private static final String PLACEHOLDER_URL = "http://localhost";
private static final User DUMMY_USER = new User("test", "test@example.com");
```

**Required fix**: Replace with real config values from environment, configuration files, or throw if the value is genuinely unknown.

---

## H9 — Silent Fallback Catch

**What it catches**: Catch blocks that return fake data instead of propagating the exception.

**Regex**:
```
catch\s*\([^)]+\)\s*\{[^}]*(return\s+(new|mock|fake|test|"[^"]*"|null)|log\.(warn|error).*not\s+implemented)
```

**Forbidden example**:
```java
try {
    return externalService.fetch();
} catch (Exception e) {
    return Collections.emptyList();  // silent fallback
}
```

**Required fix**:
```java
try {
    return externalService.fetch();
} catch (IOException e) {
    throw new YEngineException("Failed to fetch from external service", e);
}
```

---

## H10 — Conditional Mock Behavior

**What it catches**: Conditional returns that invoke mock/fake methods based on a test flag.

**Regex**:
```
if\s*\([^)]*\)\s*return\s+(mock|fake|test|sample|demo)[A-Z][a-zA-Z]*\(\)
```

**Forbidden example**:
```java
if (isTestMode) return mockData();
if (sdk == null) return fakeResponse();
```

**Required fix**: Remove the condition and the mock path. The real path must work in all contexts, or throw.

---

## H11 — Fake getOrDefault Value

**What it catches**: `.getOrDefault()` calls with obviously fake default strings.

**Regex**:
```
\.getOrDefault\([^,]+,\s*"(test|mock|fake|default|sample|placeholder)
```

**Forbidden example**:
```java
String apiKey = config.getOrDefault("api.key", "test_key_placeholder");
```

**Required fix**:
```java
String apiKey = Objects.requireNonNull(config.get("api.key"),
    "api.key must be set in application.properties");
```

---

## H12 — Early Return Skips Logic

**What it catches**: `if (true) return;` guard that bypasses all subsequent code.

**Regex**:
```
if\s*\(true\)\s*return\s*;
```

**Forbidden example**:
```java
public void process(Data data) {
    if (true) return;
    // Real logic never runs
    validator.validate(data);
}
```

**Required fix**: Delete the guard. Implement the logic or throw.

---

## H13 — Log Instead of Throw

**What it catches**: `log.warn` or `log.error` calls that report "not implemented" instead of throwing.

**Regex**:
```
log\.(warn|error)\([^)]*"[^"]*not\s+implemented[^"]*"
```

**Forbidden example**:
```java
log.warn("method not yet implemented");
return null;
```

**Required fix**:
```java
throw new UnsupportedOperationException(
    "method not yet implemented — see issue #78");
```

---

## H14 — Mockito Import in `src/`

**What it catches**: `import org.mockito.*` statements in production source files (not in `test/`).

**Regex**:
```
import\s+org\.mockito\.
```

**Scope**: Only triggers when the file path contains `/src/main/` (not `/src/test/`).

**Forbidden example**:
```java
// In src/main/java/org/yawl/MyService.java
import org.mockito.Mockito;
```

**Required fix**: Move the class to `src/test/java/`. Production code must not depend on Mockito.

---

## Hook Behavior

The hook runs on every `Write` and `Edit` tool use:

```
File written → hyper-validate.sh reads file path from hook JSON
  → Only checks *.java files under src/ or test/
  → Runs all 14 patterns in sequence
  → If any match: prints violation + line content, exits 2
  → If no match: exits 0 silently
```

Batch mode scans directories:
```bash
bash .claude/hooks/hyper-validate.sh yawl-engine/src/main/java/
# Scans all *.java files, exits 2 if any violations found
```

---

## Two Legal Outcomes

| Situation | Correct code |
|-----------|-------------|
| Logic is known | Implement it fully |
| Logic is not yet known | `throw new UnsupportedOperationException("reason + issue ref")` |

There is no third option. `return null`, `return ""`, empty bodies, TODO comments, and silent fallbacks are all illegal in production code.

---

## See Also

- [Tutorial: Fix Your First Violation](../tutorials/12-fix-hyper-standards-violation.md) — step-by-step walkthrough
- [How-To: Fix H-Guard Violations at Scale](../how-to/build/fix-h-guard-violations.md) — batch remediation
- [Explanation: Why H-Guards Exist](../explanation/h-guards-philosophy.md) — the philosophy
