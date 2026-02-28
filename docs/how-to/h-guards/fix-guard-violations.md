# How to Fix H-Guards Violations at Scale

A task-oriented guide for developers who encounter H-Guards violations and need to fix them.

**Location**: `docs/how-to/h-guards/fix-guard-violations.md`

## Overview

H-Guards are the **H** phase of GODSPEED (Ψ→Λ→H→Q→Ω), enforcing 7 forbidden patterns that indicate deferred work, mocks, stubs, or silent failures:

| Pattern | Name | Why Forbidden | Detection |
|---------|------|---------------|-----------|
| H_TODO | Deferred work markers | Unfinished debt ships to production | Comments: `// TODO`, `// FIXME`, `// @stub` |
| H_MOCK | Mock implementations | Mocks hide real integration issues | Class/method names: `Mock*`, `*mock()`, `Stub*`, `Fake*` |
| H_STUB | Stub returns | Stub values hide bugs (empty ≠ missing) | Empty string/0/null/emptyList from non-void methods |
| H_EMPTY | No-op methods | Silent no-ops hide unfinished logic | Empty method bodies: `void foo() { }` |
| H_FALLBACK | Silent catch-and-fake | Errors silently continue → cascading failures | Catch blocks returning fake data instead of throwing |
| H_LIE | Code ≠ documentation | Broken contracts surprise callers in production | Javadoc contradicts implementation |
| H_SILENT | Log instead of throw | Errors logged but not surfaced | Log statements: `log.error("Not implemented")` |

## Quick Start: Find and Fix Violations

### Step 1: Identify Violations

Run the guard check (automatically in the build):

```bash
bash scripts/dx.sh compile
```

Or manually:

```bash
# View violation report
cat .claude/receipts/guard-receipt.json | jq '.violations[] | {pattern, file, line, content}'
```

### Step 2: Group by Pattern

```bash
# Count violations by pattern
jq '[.violations[] | .pattern] | group_by(.) | map({pattern: .[0], count: length})' \
  .claude/receipts/guard-receipt.json

# Filter by specific pattern (e.g., H_TODO)
jq '.violations[] | select(.pattern == "H_TODO")' .claude/receipts/guard-receipt.json
```

### Step 3: Apply Fix for Your Pattern

See the pattern-specific sections below. Each has:
- **Why it's forbidden** — Design rationale
- **Fix strategies** — Options ranked by preference
- **Code templates** — Copy-paste starting points
- **Common mistakes** — Pitfalls to avoid

### Step 4: Verify Fix

```bash
# Re-run guards phase
bash scripts/dx.sh compile

# Check violation count (should be 0)
jq '.violations | length' .claude/receipts/guard-receipt.json

# Should output: 0
```

---

## Pattern 1: H_TODO — Deferred Work Markers

**Examples**:
```java
// TODO: implement this
// FIXME: handle null case
// @stub
// LATER: add deadlock detection
```

**Why Forbidden**: Deferred work is unfinished debt that ships to production. TODOs in code are promises never kept. Every TODO is a bug with a known fix but unknown owner.

**Detection**: Comments matching `TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder` (case-insensitive).

### Fix Strategies

#### Strategy A: Implement Real Logic (Preferred)
Finish the work **now**. No shortcuts.

```java
// BEFORE
public void processCase(Case c) {
    // TODO: Add deadlock detection
    this.caseQueue.add(c);
}

// AFTER
public void processCase(Case c) {
    // Check for circular dependencies that indicate deadlock
    if (this.detectDeadlock(c)) {
        log.warn("Deadlock detected in case {}, stopping execution", c.getId());
        this.engine.stopCase(c.getId());
        return;
    }
    this.caseQueue.add(c);
}

private boolean detectDeadlock(Case c) {
    // Check if any task is waiting on output from a downstream task
    // that is waiting on this task's output
    for (Task t : c.getTasks()) {
        if (t.getState() == State.WAITING) {
            for (Task dep : t.getDependencies()) {
                if (dep.isWaitingOn(t)) {
                    return true;  // Circular dependency detected
                }
            }
        }
    }
    return false;
}
```

#### Strategy B: Explicit Failure (If Work Truly Blocked)
If the work is blocked (missing dependency, design not finalized, etc.), throw `UnsupportedOperationException` with a **clear reason** and a **link to design docs**.

```java
// BEFORE
public void processCase(Case c) {
    // TODO: Add deadlock detection
    this.caseQueue.add(c);
}

// AFTER
public void processCase(Case c) {
    if (this.detectDeadlock(c)) {
        throw new UnsupportedOperationException(
            "Deadlock detection requires YNetRunner state inspection. " +
            "See docs/architecture/deadlock-detection.md for design."
        );
    }
    this.caseQueue.add(c);
}
```

#### Strategy C: Conditional Implementation
If the feature is truly optional (e.g., only on Saturdays), use a feature flag:

```java
// BEFORE
public void processCase(Case c) {
    // TODO: Add weekend-only batch processing
    this.caseQueue.add(c);
}

// AFTER
public void processCase(Case c) {
    boolean isWeekend = LocalDate.now().getDayOfWeek().getValue() > 5;
    if (isWeekend && this.isBatchProcessingEnabled()) {
        this.batchProcess(c);
    } else {
        this.caseQueue.add(c);
    }
}
```

### Common Mistakes

```java
// ❌ WRONG: Removing the comment doesn't fix it
public void processCase(Case c) {
    // Still incomplete, just no TODO comment
    this.caseQueue.add(c);
}

// ✓ RIGHT: Actually implement or throw
public void processCase(Case c) {
    this.detectDeadlock(c);  // Real implementation
    this.caseQueue.add(c);
}
```

---

## Pattern 2: H_MOCK — Mock Implementations

**Examples**:
```java
public class MockDataService implements DataService { ... }
public class FakeUserRepository extends UserRepository { ... }
public String mockFetchData() { return "mock data"; }
public void stubInitialize() { }
```

**Why Forbidden**: Mocks hide real integration issues until production. A mock that works doesn't prove the real integration works. Mocks accumulate technical debt and confuse developers ("Is this for testing or production?").

**Detection**: Class/method identifiers matching `mock|stub|fake|demo` (case-insensitive).

### Fix Strategies

#### Strategy A: Delete Mock, Implement Real Service (Preferred)
If this is production code, delete the mock and implement real behavior.

```java
// BEFORE (Production Code)
public class MockDataService implements DataService {
    @Override
    public String fetchData(String key) {
        return "mock data";
    }
}

// AFTER (Real Implementation)
public class PostgresDataService implements DataService {
    private final DataSource dataSource;

    public PostgresDataService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String fetchData(String key) throws DataAccessException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT value FROM data WHERE key = ?")) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("value");
                }
                throw new DataNotFoundException("No data found for key: " + key);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database error fetching data for key: " + key, e);
        }
    }
}
```

Register the real implementation:

```java
@Configuration
public class DataServiceConfiguration {
    @Bean
    public DataService dataService(DataSource dataSource) {
        return new PostgresDataService(dataSource);  // Real impl, not MockDataService
    }
}
```

#### Strategy B: Use Test Containers for Tests (If Test Code)
If this is test code, delete the mock and use **TestContainers** for integration tests:

```java
// BEFORE (Test Code)
public class DataServiceTest {
    private final DataService service = new MockDataService();

    @Test
    public void testFetchData() {
        String result = service.fetchData("key1");
        assertEquals("mock data", result);  // Tests a fake, not real behavior
    }
}

// AFTER (Real Integration Test)
@Testcontainers
public class DataServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private DataService service;

    @BeforeEach
    public void setup() {
        DataSource dataSource = createDataSource(postgres);
        service = new PostgresDataService(dataSource);
    }

    @Test
    public void testFetchData() {
        // Insert real test data
        insertTestData("key1", "real value");

        String result = service.fetchData("key1");
        assertEquals("real value", result);  // Tests real behavior
    }

    private void insertTestData(String key, String value) throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO data (key, value) VALUES (?, ?)")) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }
}
```

#### Strategy C: Use In-Memory Alternative (For Unit Tests)
For fast unit tests that don't need a database, use an in-memory alternative:

```java
// BEFORE
public class DataServiceTest {
    private final DataService service = new MockDataService();
}

// AFTER
public class DataServiceTest {
    private final DataService service = new InMemoryDataService();  // Real (simple) impl
}

// Real implementation (not a mock)
class InMemoryDataService implements DataService {
    private final Map<String, String> data = new ConcurrentHashMap<>();

    @Override
    public String fetchData(String key) {
        String value = data.get(key);
        if (value == null) {
            throw new DataNotFoundException("No data found for key: " + key);
        }
        return value;
    }

    public void setData(String key, String value) {
        data.put(key, value);
    }
}
```

Key difference: **InMemoryDataService is a real implementation** (it actually stores data), not a fake (which just returns hardcoded values).

### Common Mistakes

```java
// ❌ WRONG: Renaming the mock doesn't fix it
public class DataService implements DataService {
    public String fetchData(String key) {
        return "mock data";  // Still returns fake data
    }
}

// ✓ RIGHT: Delete the mock, use TestContainers for tests
@Testcontainers
public class DataServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    private final DataService service = new PostgresDataService(createDataSource());
}
```

---

## Pattern 3: H_STUB — Stub Return Values

**Examples**:
```java
public String getName() { return ""; }
public int getCount() { return 0; }
public List<Item> getItems() { return Collections.emptyList(); }
public User getUser() { return null; }
```

**Why Forbidden**: Stub values hide bugs. An empty string is not the same as a missing value. Zero is not the same as unknown. `null` is not the same as not-found. Stub returns cause cascading silent failures:

1. Service returns `""` (empty string).
2. Caller checks `if (result != null)` — passes silently.
3. Caller tries to parse it as a number — crash in production.

**Detection**: Non-void methods returning empty strings, 0, null, or empty collections.

### Fix Strategies

#### Strategy A: Return Real Computed Value (Preferred)
Calculate the actual value instead of stubbing.

```java
// BEFORE
public String getName() {
    return "";  // Stub
}

// AFTER
public String getName() {
    return this.case.getMetadata().getTaskName();
}
```

```java
// BEFORE
public int getCount() {
    return 0;  // Stub
}

// AFTER
public int getCount() {
    return this.tasks.stream()
        .filter(Task::isActive)
        .mapToInt(Task::getWorkItemCount)
        .sum();
}
```

```java
// BEFORE
public List<Item> getItems() {
    return Collections.emptyList();  // Stub
}

// AFTER
public List<Item> getItems() {
    return this.inventory.getAllItems()
        .stream()
        .filter(Item::isAvailable)
        .collect(Collectors.toList());
}
```

#### Strategy B: Use Optional<T> to Represent Absence
If absence is a valid state, use `Optional<T>` to make it explicit:

```java
// BEFORE
public String getName() {
    return null;  // Ambiguous: not found? Not set? Error?
}

// AFTER
public Optional<String> getName() {
    String name = this.case.getMetadata().getTaskName();
    return name != null && !name.isEmpty()
        ? Optional.of(name)
        : Optional.empty();
}

// Caller must explicitly handle absence
String name = this.getName()
    .orElse("Unknown Task");
```

#### Strategy C: Fail Fast with Clear Context
If absence indicates a bug, throw an exception with context:

```java
// BEFORE
public String getName() {
    return "";  // Silently returns empty
}

// AFTER (Option 1: Checked Exception)
public String getName() throws CaseMetadataException {
    String name = this.case.getMetadata().getTaskName();
    if (name == null || name.isEmpty()) {
        throw new CaseMetadataException(
            "Case has no task name. " +
            "See design/case-lifecycle.md#metadata-requirements"
        );
    }
    return name;
}

// AFTER (Option 2: Runtime Exception)
public String getName() {
    String name = this.case.getMetadata().getTaskName();
    if (name == null || name.isEmpty()) {
        throw new IllegalStateException(
            "Cannot get task name: case has no metadata. " +
            "Case must be initialized before calling getName()."
        );
    }
    return name;
}
```

```java
// BEFORE
public List<Item> getItems() {
    return Collections.emptyList();  // Silently returns empty
}

// AFTER
public List<Item> getItems() {
    if (!this.hasItems()) {
        throw new IllegalStateException(
            "No items available. " +
            "Ensure inventory is loaded via loadInventory() before calling getItems()."
        );
    }
    return this.inventory.getAllItems();
}
```

#### Strategy D: Return Sentinel Object
If you need a non-null object to avoid null checks, create a sentinel:

```java
// BEFORE
public User getUser(String id) {
    User user = this.userService.findById(id);
    if (user == null) {
        return null;  // Stub
    }
    return user;
}

// AFTER
public User getUser(String id) {
    User user = this.userService.findById(id);
    if (user == null) {
        return User.NONE;  // Sentinel, not stub
    }
    return user;
}

// Define sentinel
public static final User NONE = new User(-1L, "NONE", true) {
    @Override
    public void update(String name) {
        throw new IllegalStateException(
            "Cannot update NONE user. User must be fetched first."
        );
    }
};
```

### Common Mistakes

```java
// ❌ WRONG: Returning different type of empty doesn't fix it
public String getName() {
    return null;  // Still a stub (was "")
}

// ✓ RIGHT: Compute real value or use Optional/fail-fast
public String getName() {
    return this.case.getMetadata().getTaskName();  // Real value
}

public Optional<String> getName() {  // Explicit absence
    return Optional.ofNullable(this.case.getMetadata().getTaskName());
}

public String getNameOrThrow() {  // Fail fast
    String name = this.case.getMetadata().getTaskName();
    if (name == null) throw new IllegalStateException("No name");
    return name;
}
```

---

## Pattern 4: H_EMPTY — No-Op Methods

**Examples**:
```java
public void initialize() { }
public void configure(Config cfg) { }
@Override
public void close() { }
```

**Why Forbidden**: Empty method bodies hide unfinished logic. A caller expects `initialize()` to initialize something. An empty implementation that does nothing is broken.

**Detection**: Methods with empty bodies (void or non-void).

### Fix Strategies

#### Strategy A: Implement Real Logic (Preferred)
Finish the method implementation.

```java
// BEFORE
public void initialize() { }

// AFTER
public void initialize() {
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.cache = new ConcurrentHashMap<>();
    this.listener = new DefaultYNetListener(this);
    log.info("YNetRunner initialized with virtual threads");
}
```

```java
// BEFORE
public void configure(Config cfg) { }

// AFTER
public void configure(Config cfg) {
    this.maxCaseCount = cfg.getInt("max.cases", 1000);
    this.taskTimeout = Duration.ofSeconds(cfg.getLong("task.timeout.seconds", 300));
    this.enablePersistence = cfg.getBoolean("persistence.enabled", true);

    if (this.enablePersistence) {
        this.persistence = new JpaEventStore(cfg.getDataSource());
    }

    log.info("YNetRunner configured with max_cases={}, timeout={}s, persistence={}",
        this.maxCaseCount, this.taskTimeout.getSeconds(), this.enablePersistence);
}
```

#### Strategy B: Throw Exception (If Method is Optional or Deprecated)
If the method is optional (e.g., override of an interface that doesn't apply to this class), throw an exception:

```java
// BEFORE
public void close() { }  // Implementation doesn't need cleanup

// AFTER
public void close() {
    throw new UnsupportedOperationException(
        "close() is not supported by InMemoryYNetRunner. " +
        "Only JpaYNetRunner requires cleanup."
    );
}
```

Or mark as no-op if it's intentionally a no-op:

```java
// BEFORE
public void close() { }

// AFTER
@Override
public void close() {
    // No cleanup needed for in-memory engine.
    // All state is garbage-collected with the instance.
}
```

#### Strategy C: Remove Method (If Unused)
If the method is not called anywhere, delete it:

```java
// BEFORE
public class YNetRunner {
    public void initialize() { }  // Never called
}

// AFTER (Delete the method entirely)
// If it's from an interface, mark as deprecated instead:

@Override
@Deprecated(since = "6.1", forRemoval = true)
public void initialize() {
    throw new UnsupportedOperationException(
        "initialize() is deprecated. Use configure(Config) instead."
    );
}
```

### Common Mistakes

```java
// ❌ WRONG: Adding a comment doesn't fix it
public void initialize() {
    // This method initializes the engine
}

// ✓ RIGHT: Actually do the initialization
public void initialize() {
    this.executor = Executors.newVirtualThreadPerTaskExecutor();
    this.cache = new ConcurrentHashMap<>();
}
```

---

## Pattern 5: H_FALLBACK — Silent Catch-and-Fake

**Examples**:
```java
try {
    caseService.launch(caseId);
} catch (Exception e) {
    return Collections.emptyList();  // Silent fallback
}

try {
    workItem.complete();
} catch (Exception e) {
    log.warn("Failed to complete work item");  // Silent fallback (logged)
}
```

**Why Forbidden**: Silently swallowing errors hides systemic issues. A failed task launch that returns an empty list looks like success. The caller continues as if everything worked, leading to cascading failures downstream.

**Detection**: Catch blocks that return fake/mock data, return empty collections, return null, or return default values without rethrowing.

### Fix Strategies

#### Strategy A: Propagate Exception (Most Common)
Let the exception bubble up to the caller. The caller is responsible for recovery strategy.

```java
// BEFORE
try {
    caseService.launch(caseId);
    return getActiveCases();
} catch (Exception e) {
    return Collections.emptyList();  // Silent fallback
}

// AFTER
try {
    caseService.launch(caseId);
    return getActiveCases();
} catch (CaseException e) {
    throw new RuntimeException(
        "Failed to launch case: " + caseId + ". " +
        "Verify case specification exists and is valid.",
        e
    );
}
```

#### Strategy B: Explicit Retry (For Transient Errors)
If the error is transient (network timeout, temporary lock), retry:

```java
// BEFORE
try {
    workItem.complete();
    return Status.SUCCESS;
} catch (Exception e) {
    return Status.FAILED;  // Silent fallback
}

// AFTER
try {
    workItem.complete();
    return Status.SUCCESS;
} catch (LockTimeoutException e) {
    // Transient error, retry once
    Thread.sleep(100);
    try {
        workItem.complete();
        return Status.SUCCESS;
    } catch (Exception retry) {
        throw new RuntimeException(
            "Failed to complete work item after retry: " + workItem.getId(),
            retry
        );
    }
}
```

#### Strategy C: Conditional Fallback (For Expected Failures)
If failure is expected and recoverable, handle it explicitly:

```java
// BEFORE
try {
    caseService.launch(caseId);
} catch (Exception e) {
    return false;  // Silent fallback
}

// AFTER
try {
    caseService.launch(caseId);
    return true;
} catch (CaseNotFoundException e) {
    log.info("Case not found (expected): {}", caseId);
    return false;  // Expected case, explicit fallback
} catch (CaseException e) {
    throw new RuntimeException(
        "Unexpected error launching case: " + caseId,
        e
    );
}
```

#### Strategy D: Log + Rethrow (For Debugging)
If you need to log before failing, log and rethrow:

```java
// BEFORE
try {
    caseService.launch(caseId);
} catch (Exception e) {
    log.error("Failed to launch case");  // Silent fallback
    return null;
}

// AFTER
try {
    caseService.launch(caseId);
} catch (CaseException e) {
    log.error("Failed to launch case: {} - {}",
        caseId, e.getMessage(), e);
    throw new RuntimeException(
        "Cannot launch case: " + caseId,
        e
    );
}
```

### Common Mistakes

```java
// ❌ WRONG: Logging doesn't count as handling
try {
    caseService.launch(caseId);
} catch (Exception e) {
    log.error("Launch failed");
    return Collections.emptyList();  // Still a silent fallback
}

// ✓ RIGHT: Either propagate or handle explicitly
try {
    caseService.launch(caseId);
} catch (CaseException e) {
    throw e;  // Propagate
}

try {
    caseService.launch(caseId);
} catch (NotFoundException e) {
    return Collections.emptyList();  // Explicit: expected case
}
```

---

## Pattern 6: H_LIE — Code ≠ Documentation

**Examples**:
```java
/**
 * @return never null
 */
public String getValue() {
    return null;  // Violates contract
}

/**
 * @throws CaseException if case not found
 */
public Case getCase(String id) {
    return caseService.findById(id);  // Doesn't throw
}

/**
 * Initializes the engine immediately.
 */
public void initialize() {
    this.executor = Executors.newFixedThreadPool(10);
    // Creates only 10 threads, but docs say "immediately"
    // which implies all cores are used
}
```

**Why Forbidden**: Broken contracts surprise callers in production. Javadoc is a contract. Code is the implementation. They must match, or callers will write code that crashes.

**Detection**: Semantic analysis comparing Javadoc (`@return`, `@throws`) with code behavior.

### Fix Strategies

#### Strategy A: Update Code to Match Documentation (Preferred)
Documentation is the specification. Update implementation to match.

```java
// BEFORE
/**
 * @return never null
 * @throws CaseNotFoundException if case not found
 */
public Case getCase(String id) {
    return caseService.findById(id);  // Returns null, doesn't throw
}

// AFTER (Update code to match docs)
/**
 * @return never null
 * @throws CaseNotFoundException if case not found
 */
public Case getCase(String id) throws CaseNotFoundException {
    Case c = caseService.findById(id);
    if (c == null) {
        throw new CaseNotFoundException("Case not found: " + id);
    }
    return c;
}
```

```java
// BEFORE
/**
 * Initializes the engine with all available processor cores.
 */
public void initialize() {
    this.executor = Executors.newFixedThreadPool(10);
}

// AFTER (Update code to match docs)
/**
 * Initializes the engine with all available processor cores.
 */
public void initialize() {
    int coreCount = Runtime.getRuntime().availableProcessors();
    this.executor = Executors.newFixedThreadPool(coreCount);
}
```

#### Strategy B: Update Documentation to Match Code (If Code is Correct)
If the implementation is correct but documentation is wrong, fix the docs:

```java
// BEFORE (docs are wrong)
/**
 * @return never null
 */
public Case getCase(String id) {
    return caseService.findById(id);  // Can return null
}

// AFTER (update docs)
/**
 * @return case if found, null if not found
 */
public Case getCase(String id) {
    return caseService.findById(id);
}

// Or use Optional to make absence explicit:
/**
 * @return the case, or empty if not found
 */
public Optional<Case> getCase(String id) {
    return Optional.ofNullable(caseService.findById(id));
}
```

### Common Mistakes

```java
// ❌ WRONG: Deleting the contract doesn't fix the lie
public Case getCase(String id) {
    return caseService.findById(id);  // Still broken, just undocumented now
}

// ✓ RIGHT: Align code and docs
/**
 * @return case if found, null if not found
 */
public Case getCase(String id) {
    return caseService.findById(id);
}

/**
 * @return never null
 * @throws CaseNotFoundException if not found
 */
public Case getCaseOrThrow(String id) throws CaseNotFoundException {
    Case c = caseService.findById(id);
    if (c == null) throw new CaseNotFoundException("Case not found: " + id);
    return c;
}
```

---

## Pattern 7: H_SILENT — Log Instead of Throw

**Examples**:
```java
if (!item.isReady()) {
    log.error("Work item not ready");
    return;  // Silent failure
}

try {
    process();
} catch (Exception e) {
    log.error("Processing failed");  // No rethrow
}
```

**Why Forbidden**: Errors logged but not surfaced silently continue. The application looks fine (logs are written) but execution is broken. Callers don't know the operation failed.

**Detection**: Log statements in error/warn level without corresponding exception throw or rethrow.

### Fix Strategies

#### Strategy A: Throw Exception (Most Common)
Replace logging with throwing:

```java
// BEFORE
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        log.error("Work item not ready: " + item.getId());
        return;  // Silent failure
    }
    // Process...
}

// AFTER
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        throw new IllegalStateException(
            "Work item must be in READY state: " + item.getId()
        );
    }
    // Process...
}
```

```java
// BEFORE
public Result process() {
    try {
        return doProcess();
    } catch (Exception e) {
        log.error("Processing failed: " + e.getMessage());
        return Result.FAILED;  // Silent failure
    }
}

// AFTER
public Result process() {
    try {
        return doProcess();
    } catch (ProcessException e) {
        throw new RuntimeException(
            "Processing failed unexpectedly",
            e
        );
    }
}
```

#### Strategy B: Log + Throw (If Logging is Important)
If you need logging context before failing, log and throw:

```java
// BEFORE
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        log.error("Work item not ready: {}", item.getId());
        return;  // Silent failure
    }
    // Process...
}

// AFTER
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        log.error("Work item not ready for processing: {}", item.getId());
        throw new IllegalStateException(
            "Work item must be in READY state: " + item.getId()
        );
    }
    // Process...
}
```

#### Strategy C: Return Failure Indicator + Ensure Caller Checks
If returning failure instead of throwing, document it clearly and ensure caller checks:

```java
// BEFORE
public boolean launch(Case c) {
    try {
        doLaunch(c);
        return true;
    } catch (Exception e) {
        log.error("Launch failed for case: {}", c.getId());
        return false;  // Silent failure
    }
}

// AFTER (Document that caller MUST check return value)
/**
 * Launches the case.
 *
 * @param c the case to launch
 * @return true if launched successfully, false if launch failed
 * @throws IllegalStateException if case specification is invalid (caller must fix configuration)
 *
 * IMPORTANT: Caller MUST check return value and handle false case:
 * <pre>
 *   if (!engine.launch(case)) {
 *       log.warn("Launch failed, retrying...");
 *       // Implement retry logic
 *   }
 * </pre>
 */
public boolean launch(Case c) {
    try {
        doLaunch(c);
        return true;
    } catch (SpecificationException e) {
        throw new IllegalStateException(
            "Case specification is invalid: " + e.getMessage(),
            e
        );
    } catch (TemporaryException e) {
        log.warn("Temporary failure launching case {}, will retry on next attempt", c.getId());
        return false;
    }
}
```

### Common Mistakes

```java
// ❌ WRONG: Changing log level doesn't fix it
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        log.warn("Work item not ready: " + item.getId());  // Still silent
        return;
    }
}

// ✓ RIGHT: Throw exception
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        throw new IllegalStateException(
            "Work item must be in READY state: " + item.getId()
        );
    }
}
```

---

## Batch Remediation Workflow

For large codebases with many violations, use this structured approach:

### Step 1: Inventory Violations

```bash
# Save violation report to file
jq '.violations' .claude/receipts/guard-receipt.json > violations.json

# Count by pattern
jq 'group_by(.pattern) | map({pattern: .[0].pattern, count: length})' \
  violations.json

# Count by file
jq 'group_by(.file) | map({file: .[0].file, count: length})' \
  violations.json
```

### Step 2: Prioritize

**Priority 1 (Fix First)**: H_FALLBACK, H_LIE (these cause cascading failures)
**Priority 2**: H_TODO, H_MOCK (these hide unfinished work)
**Priority 3**: H_STUB, H_EMPTY, H_SILENT (these hide silent errors)

### Step 3: Assign by Pattern

Distribute fixes by pattern:

| Pattern | Engineer | Est. Time |
|---------|----------|-----------|
| H_TODO (5 violations) | Alice | 2-3 hours |
| H_MOCK (8 violations) | Bob | 3-4 hours |
| H_FALLBACK (3 violations) | Carol | 2 hours |
| H_STUB (12 violations) | David | 4-5 hours |

### Step 4: Fix in Batches

Fix 2-3 violations per commit to keep history clean:

```bash
# Work on violations for file X
vim src/org/yawlfoundation/yawl/engine/YWorkItem.java

# Test locally
bash scripts/dx.sh -pl yawl-engine

# Verify guards pass
bash scripts/dx.sh compile && jq '.violations | length' .claude/receipts/guard-receipt.json

# Commit when clean
git add src/org/yawlfoundation/yawl/engine/YWorkItem.java
git commit -m "Fix H_TODO violations in YWorkItem

Replaced 3 TODO comments with real deadlock detection implementation.
See docs/architecture/deadlock-detection.md for design."
```

### Step 5: Verify Complete Fix

```bash
# Full build
bash scripts/dx.sh all

# Check final violation count
jq '.violations | length' .claude/receipts/guard-receipt.json

# Should be 0
```

---

## Common Mistakes to Avoid

### Mistake 1: Removing Comments Instead of Implementing

```java
// ❌ WRONG
public void processCase(Case c) {
    this.caseQueue.add(c);  // Removed the TODO, but still incomplete
}

// ✓ RIGHT
public void processCase(Case c) {
    if (this.detectDeadlock(c)) {
        throw new IllegalStateException("Deadlock detected");
    }
    this.caseQueue.add(c);
}
```

### Mistake 2: Renaming Mocks Instead of Replacing

```java
// ❌ WRONG
public class DataService implements DataService {  // Was MockDataService
    public String fetchData() { return "mock data"; }  // Still returns mock
}

// ✓ RIGHT
public class PostgresDataService implements DataService {
    private final DataSource ds;
    public String fetchData() {
        // Real SQL query
    }
}
```

### Mistake 3: Catching and Logging Without Throwing

```java
// ❌ WRONG
try {
    caseService.launch(caseId);
} catch (Exception e) {
    log.error("Launch failed");  // Still silent failure
    return null;
}

// ✓ RIGHT
try {
    caseService.launch(caseId);
} catch (CaseException e) {
    log.error("Launch failed for case {}", caseId, e);
    throw e;
}
```

### Mistake 4: Wrapping Exception Without Context

```java
// ❌ WRONG
try {
    caseService.launch(caseId);
} catch (Exception e) {
    throw new RuntimeException(e);  // Loses context
}

// ✓ RIGHT
try {
    caseService.launch(caseId);
} catch (CaseException e) {
    throw new RuntimeException(
        "Failed to launch case: " + caseId + ". " +
        "Verify case specification exists and is valid.",
        e
    );
}
```

### Mistake 5: Documenting a Lie

```java
// ❌ WRONG
/**
 * Returns the user or null if not found.
 * @return never null
 */
public User getUser(String id) {
    return userService.findById(id);  // Can return null
}

// ✓ RIGHT
/**
 * Returns the user or null if not found.
 */
public User getUser(String id) {
    return userService.findById(id);
}

// OR use Optional to be explicit
/**
 * Returns the user if found.
 * @return the user, or empty if not found
 */
public Optional<User> getUser(String id) {
    return Optional.ofNullable(userService.findById(id));
}
```

---

## Pro Tips

### Tip 1: Use UnsupportedOperationException for Design-Time Blockers

When the work is truly blocked (missing design, dependency unavailable), use `UnsupportedOperationException`:

```java
public void processDeadlock(Case c) {
    throw new UnsupportedOperationException(
        "Deadlock detection requires YNetRunner state inspection. " +
        "See docs/architecture/deadlock-detection.md for design."
    );
}
```

### Tip 2: Use IllegalStateException for Runtime Contract Violations

When the application is in an invalid state, use `IllegalStateException`:

```java
public void processWorkItem(WorkItem item) {
    if (!item.isReady()) {
        throw new IllegalStateException(
            "Work item must be in READY state: " + item.getId()
        );
    }
    // Process...
}
```

### Tip 3: Use IllegalArgumentException for Invalid Input

When the caller passes invalid input, use `IllegalArgumentException`:

```java
public void launch(String caseId) {
    if (caseId == null || caseId.isEmpty()) {
        throw new IllegalArgumentException("Case ID cannot be null or empty");
    }
    // Launch...
}
```

### Tip 4: Always Include Context in Exception Messages

Never throw bare exceptions:

```java
// ❌ WRONG
throw new RuntimeException("Failed");

// ✓ RIGHT
throw new RuntimeException(
    "Failed to launch case: " + caseId + ". " +
    "Verify case specification exists and is valid. " +
    "See docs/troubleshooting.md#case-launch-failures"
);
```

### Tip 5: Link to Documentation When Explaining WHY

When explaining design decisions or requirements, link to docs:

```java
throw new UnsupportedOperationException(
    "Deadlock detection requires YNetRunner state inspection. " +
    "See docs/architecture/deadlock-detection.md for design."
);
```

---

## Verification Checklist

Before declaring a violation fixed:

- [ ] H_TODO: Is real logic implemented or explicit failure thrown?
- [ ] H_MOCK: Is real implementation used (not mock)?
- [ ] H_STUB: Is real value computed or Optional/fail-fast used?
- [ ] H_EMPTY: Is real logic implemented or exception thrown?
- [ ] H_FALLBACK: Does code propagate or explicitly handle exceptions?
- [ ] H_LIE: Do docs and code match?
- [ ] H_SILENT: Does code throw (not just log)?

---

## Need Help?

If you're stuck on a violation:

1. **Understand the pattern** — Read the "Why Forbidden" section above
2. **Review code templates** — Copy a template matching your situation
3. **Test locally** — `bash scripts/dx.sh -pl module` to verify
4. **Run full build** — `bash scripts/dx.sh all` before commit
5. **Check receipt** — `jq '.violations | length' .claude/receipts/guard-receipt.json`

If a violation is truly blockers:

```bash
# Throw explicit exception with context
throw new UnsupportedOperationException(
    "Feature blocked by: [dependency/design/timeline]. " +
    "See: [link-to-design-doc]"
);
```

---

**Target Audience**: Developers encountering guard violations, QA engineers running bulk audits, CI/CD engineers validating gates.

**Related Guides**:
- [H-Guards Design](../../../.claude/rules/validation-phases/H-GUARDS-DESIGN.md) — Technical architecture
- [H-Guards Queries](../../../.claude/rules/validation-phases/H-GUARDS-QUERIES.md) — SPARQL query reference
- [H-Guards Philosophy](../explanation/h-guards-philosophy.md) — Design rationale
- [Contributing Guide](../contributing.md) — Developer standards
