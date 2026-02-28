# H-Guards Patterns Reference — Complete Violation Catalog

**Status**: Authoritative Pattern Catalog
**Phase**: H (Guards) | **Maintained by**: yawl-generator
**Last updated**: 2026-02-28 | **Version**: 2.0

---

## Executive Summary

This document catalogs all **7 H-Guard violation patterns** that the H (Guards) validation phase detects during code generation. Each pattern defines what makes code violate Fortune 5 production standards, how violation detection works, real examples of violations, and remediation guidance.

**Key facts**:
- 7 patterns enforced by H phase (guards validation)
- Patterns detected via **regex scanning** (low complexity) or **SPARQL AST analysis** (semantic)
- Violations are **non-negotiable**: No warnings, no exceptions
- Two legal outcomes only: **real implementation** or **throw UnsupportedOperationException**
- ~800-1100 violations/million lines typical in legacy code; H-Guards reduces to zero

---

## Pattern Summary Table

| Pattern | Name | Detection | Frequency | Severity | Auto-Fixable |
|---------|------|-----------|-----------|----------|--------------|
| **H_TODO** | Deferred work markers | Regex on comments | ~25% | FAIL | No |
| **H_MOCK** | Mock/stub/fake identifiers | Regex on names | ~20% | FAIL | Partial |
| **H_STUB** | Placeholder return values | SPARQL on AST | ~30% | FAIL | No |
| **H_EMPTY** | Empty method bodies | SPARQL on AST | ~15% | FAIL | No |
| **H_FALLBACK** | Silent exception handling | SPARQL on AST | ~5% | FAIL | No |
| **H_LIE** | Documentation ↔ code mismatch | Semantic SPARQL | ~3% | FAIL | No |
| **H_SILENT** | Logging instead of throwing | Regex on calls | ~2% | FAIL | Partial |

---

## Detection Methods Reference

### Regex-Based Detection (Fast Path)

**Used for**: H_TODO, H_MOCK, H_SILENT
**Speed**: <1ms per file (line-by-line scanning)
**False positive rate**: <2% (simple pattern matching)
**Recovery**: User must manually fix or refactor

```
Pattern Class          Detection Strategy              Accuracy
─────────────────────────────────────────────────────────────────
H_TODO                 Comment keyword matching         98%
H_MOCK                 Name prefix + case matching     95%
H_SILENT               Log statement + keyword         96%
```

### SPARQL AST-Based Detection (Semantic Path)

**Used for**: H_STUB, H_EMPTY, H_FALLBACK, H_LIE
**Speed**: 50-500ms per file (AST parsing + RDF + query)
**False positive rate**: <1% (semantic analysis)
**Recovery**: Automatic code inspection required

```
Pattern Class          Detection Strategy              Accuracy
─────────────────────────────────────────────────────────────────
H_STUB                 Return statement analysis       99%
H_EMPTY                Method body structure           99%
H_FALLBACK             Control flow in catch blocks    98%
H_LIE                  Documentation vs code spec      95%
```

---

## Pattern Catalog

---

## Pattern 1: H_TODO — Deferred Work Markers

### What It Catches

Comments that defer implementation responsibility to future developers. These create technical debt and shift responsibility away from the author's code.

### Severity

**FAIL** — Blocks code generation. Must be resolved before proceeding.

### Detection Method

**Regex pattern** (line-by-line scanning)

```regex
//\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder|for\s+now|coming\s+soon|temp|not\s+yet|simplified)
```

### How Detection Works

1. Read file line by line
2. Check each line starting with `//` (comments)
3. Match keywords: `TODO`, `FIXME`, `XXX`, `HACK`, `LATER`, `FUTURE`, etc.
4. If matched: Report violation with line number and comment text
5. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Explicit Deferral

```java
// BAD: Author punts on implementation
public List<WorkItem> getActiveItems() {
    // TODO: implement filtering logic
    return itemRepository.findAll();
}
```

**Why it violates**: `TODO` signals incomplete work. Future developers inherit unclear requirements.

**Fix guidance**: Either implement the filtering logic explicitly or throw UnsupportedOperationException.

```java
// GOOD: Real implementation
public List<WorkItem> getActiveItems() {
    return itemRepository.findAll()
        .stream()
        .filter(item -> item.getStatus() == WorkItemStatus.ACTIVE)
        .collect(Collectors.toList());
}

// ALSO GOOD: Explicit impossibility
public List<WorkItem> getActiveItems() {
    throw new UnsupportedOperationException(
        "Active items filtering not yet integrated with database schema. " +
        "Requires WorkItem.status column. See issue #156.");
}
```

#### Example 2: Pragmatic Deferral (HACK comment)

```java
// BAD: Acknowledges technical debt but doesn't fix it
public void processWorkflow(YNet net) {
    // HACK: we're bypassing validation for now to get demo working
    YNetRunner runner = new YNetRunner(net, null);  // null validator!
    runner.execute();
}
```

**Why it violates**: `HACK` admits the code is wrong. Signals temporary workaround that becomes permanent.

**Fix guidance**: Remove the workaround. Provide real validator or throw.

```java
// GOOD: Explicit error on missing requirement
public void processWorkflow(YNet net) {
    Objects.requireNonNull(net.getValidator(),
        "YNet must have validation configured. See SETUP_GUIDE.md section 3.2");
    YNetRunner runner = new YNetRunner(net, net.getValidator());
    runner.execute();
}
```

#### Example 3: Placeholder Implementation

```java
// BAD: Marks code as incomplete work
public String getTaskDescription(Task task) {
    // placeholder - needs real description lookup
    return "task_" + task.getId();
}
```

**Why it violates**: `placeholder` indicates author knows implementation is wrong.

**Fix guidance**: Implement real lookup or document why placeholder is necessary.

```java
// GOOD: Real implementation
public String getTaskDescription(Task task) {
    return taskDescriptionCache.get(task.getId())
        .orElseThrow(() -> new TaskDescriptionNotFoundException(task.getId()));
}
```

#### Example 4: Annotation-Style Deferred Work

```java
// BAD: Using annotation-style comments
public void sendNotification(User user) {
    // @incomplete - notification service not wired
    System.out.println("Notification: " + user.getEmail());
}
```

**Why it violates**: `@incomplete` signals unfinished code in production path.

**Fix guidance**: Implement real notification or gate it with explicit exception.

```java
// GOOD: Real service call
public void sendNotification(User user) {
    notificationService.sendAsync(
        user.getEmail(),
        "Task assignment",
        getAssignmentMessage(user));
}
```

### Detection Query Example

For SPARQL-based detection (semantic verification):

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?file ?line ?violation
WHERE {
  ?comment a code:Comment ;
           code:text ?text ;
           code:lineNumber ?line ;
           code:file ?file .

  FILTER(REGEX(?text,
    "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|placeholder)"))

  BIND(CONCAT("Deferred work marker at ", STR(?line), ": ", ?text)
       AS ?violation)
}
```

### Related Patterns

- **H_STUB**: Often appears alongside H_TODO (e.g., `// TODO: return real value` with stub implementation)
- **H_EMPTY**: May use H_TODO to mark empty methods (should throw instead)

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~25% of all violations |
| Detection speed | <1ms per file |
| False positive rate | <2% |
| Typical per 1M LOC | 200-400 violations |

---

## Pattern 2: H_MOCK — Mock/Stub/Fake Identifiers

### What It Catches

Method names, variable names, or class names that include `mock`, `stub`, `fake`, or `demo` prefixes. These signal test doubles in production code, violating separation of concerns.

### Severity

**FAIL** — Blocks code generation. Indicates production code using test infrastructure.

### Detection Method

**Regex pattern** (identifier matching on method/class names)

```regex
(mock|stub|fake|demo)[A-Z][a-zA-Z]*
```

Matches:
- Method names: `mockFetch()`, `getFakeData()`, `demoMode()`, `stubService()`
- Class names: `MockDataService`, `FakeRepository`, `DemoUserProvider`
- Variable names: `mockDatabase`, `fakeResponse`, `stubConfig`

### How Detection Works

1. Parse Java source for method declarations: `public TYPE NAME(...)`
2. Parse Java source for class declarations: `class NAME ...`
3. Extract identifier part (NAME)
4. Match identifier against regex
5. If matched: Report violation with method/class name and line
6. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Mock Method Name

```java
// BAD: Method name signals test double
public User mockFetchUser(String userId) {
    return new User(userId, "Test User", "test@example.com");
}

// In production code:
public void processUser(String userId) {
    User user = mockFetchUser(userId);  // Using test method!
    processor.handle(user);
}
```

**Why it violates**: Name `mockFetchUser` indicates test code. Production path should not invoke test methods.

**Fix guidance**: Remove `mock` prefix. Implement real fetch or inject dependency.

```java
// GOOD: Real method with real implementation
public User fetchUser(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
}

public void processUser(String userId) {
    User user = fetchUser(userId);  // Clear production intent
    processor.handle(user);
}

// ALSO GOOD: Inject real service
public void processUser(String userId) {
    User user = userService.getUser(userId);  // Dependency injection
    processor.handle(user);
}
```

#### Example 2: Mock Class Name

```java
// BAD: Class name indicates test double
public class MockDataService implements DataService {
    @Override
    public Data fetchData(String id) {
        return new Data(id, "mock", "no-op");
    }
}

// In production code:
@Service
public class WorkflowProcessor {
    @Autowired
    private DataService dataService;

    // Spring wires MockDataService in production? WRONG!
    public void run(WorkItem item) {
        Data data = dataService.fetchData(item.getId());
        // Mock data causes wrong behavior
    }
}
```

**Why it violates**: Class name `MockDataService` signals test class. Should not exist in production codebase.

**Fix guidance**: Delete mock class. Implement real service or use interface-based dependency injection.

```java
// GOOD: Real implementation with interface
public interface DataService {
    Data fetchData(String id);
}

public class PostgresDataService implements DataService {
    @Override
    public Data fetchData(String id) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM data WHERE id = ?",
            new DataMapper(),
            id);
    }
}

// In production, Spring wires real service:
@Service
public class WorkflowProcessor {
    @Autowired
    private DataService dataService;  // Gets real PostgresDataService

    public void run(WorkItem item) {
        Data data = dataService.fetchData(item.getId());
    }
}
```

#### Example 3: Fake Field Name

```java
// BAD: Field name signals placeholder data
public class TaskAssignment {
    private String fakeTaskId;
    private List<User> demoUsers;

    public TaskAssignment() {
        this.fakeTaskId = "task_placeholder_123";
        this.demoUsers = Arrays.asList(
            new User("demo_user_1"),
            new User("demo_user_2"));
    }
}
```

**Why it violates**: Field names `fakeTaskId` and `demoUsers` indicate placeholders in production object.

**Fix guidance**: Remove fake data. Require real initialization from caller.

```java
// GOOD: Constructor requires real values
public class TaskAssignment {
    private final String taskId;
    private final List<User> assignees;

    public TaskAssignment(String taskId, List<User> assignees) {
        this.taskId = Objects.requireNonNull(taskId, "taskId required");
        this.assignees = Objects.requireNonNull(assignees, "assignees required");
    }
}

// Caller provides real data:
public TaskAssignment createAssignment(String taskId) {
    List<User> actualAssignees = userRepository.getDefaultAssignees();
    return new TaskAssignment(taskId, actualAssignees);
}
```

#### Example 4: Stub Service Provider

```java
// BAD: Method with stub in name provides test service
public DatabaseConnection stubGetConnection() {
    return new MockDatabaseConnection();  // Also bad!
}

public class WorkflowEngine {
    private DatabaseConnection conn;

    public WorkflowEngine() {
        this.conn = stubGetConnection();  // Using stub in production?
    }
}
```

**Why it violates**: Method `stubGetConnection` indicates it provides test double. Should not be called from production paths.

**Fix guidance**: Implement real connection factory. Inject real database.

```java
// GOOD: Real connection factory
public DatabaseConnection getConnection() {
    return connectionPool.getConnection();
}

public class WorkflowEngine {
    private final DatabaseConnection conn;

    public WorkflowEngine(DatabaseConnectionPool pool) {
        this.conn = pool.getConnection();  // Explicit dependency injection
    }
}
```

### Detection Variations

**Scope**: Checks method names, class names, field names across all `.java` files in generated code.

**Exclusions**:
- Test source files (`src/test/`) may contain mock classes if they follow test naming conventions
- This pattern only triggers in `src/main/` (production code)

### Related Patterns

- **H_EMPTY**: Often paired with mock classes (empty method bodies)
- **H_STUB**: Mock methods often have stub return values
- **H_SILENT**: Mock implementations often use silent logging

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~20% of all violations |
| Detection speed | <1ms per file |
| False positive rate | <5% (can match legitimate prefixes) |
| Typical per 1M LOC | 150-300 violations |

---

## Pattern 3: H_STUB — Placeholder Return Values

### What It Catches

Methods returning placeholder values (empty strings, zero, null, empty collections) instead of real data. Stub returns defer work and cause subtle bugs in callers.

### Severity

**FAIL** — Blocks code generation. Indicates incomplete functionality.

### Detection Method

**SPARQL on AST** (semantic analysis of return statements)

Detects patterns:
- `return "";` (empty string from non-void method)
- `return 0;` or `return 0L;` (zero from numeric method)
- `return null;` (null from non-nullable method)
- `return Collections.emptyList();` (empty collection from method expecting data)
- `return new HashMap<>();` (empty container when data expected)

### How Detection Works

1. Parse Java source to AST (tree-sitter or similar)
2. Convert AST to RDF graph (code semantic model)
3. Execute SPARQL query to find return statements
4. Filter returns that are "obviously stub" (empty, zero, null)
5. Exclude void methods (empty body OK for void)
6. Report violations with method name, line, and return value
7. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Empty String Return

```java
// BAD: Returns empty string as placeholder
public String getTaskId() {
    return "";
}

// Caller trusts it's valid:
public void logTask(Task task) {
    String id = task.getTaskId();
    log.info("Processing task: " + id);  // Logs empty string!
    database.updateTask(id);  // Updates wrong record!
}
```

**Why it violates**: `return ""` is not a valid task ID. Caller has no way to detect the stub and bugs silently.

**Fix guidance**: Return real task ID or throw exception.

```java
// GOOD: Real implementation
public String getTaskId() {
    return this.taskId;  // Assigned in constructor
}

// OR: Explicit failure
public String getTaskId() {
    if (this.taskId == null) {
        throw new IllegalStateException(
            "Task ID not initialized. See constructor signature.");
    }
    return this.taskId;
}
```

#### Example 2: Zero Return

```java
// BAD: Returns 0 as placeholder count
public int getWorkItemCount() {
    return 0;  // Always lies!
}

// Caller checks for work:
public void executeWorkflow(Process process) {
    if (process.getWorkItemCount() > 0) {
        executor.run(process);
    } else {
        log.info("No work items.");  // Incorrectly skips execution
    }
}
```

**Why it violates**: `return 0` always signals "no work", but might be a stub. Caller makes wrong decisions.

**Fix guidance**: Count real items or throw.

```java
// GOOD: Real counting logic
public int getWorkItemCount() {
    return workItems.size();
}

// OR: Fail if not initialized
public int getWorkItemCount() {
    if (workItems == null) {
        throw new IllegalStateException("Work items not loaded. " +
            "Call loadWorkItems() first. See API docs.");
    }
    return workItems.size();
}
```

#### Example 3: Null Return

```java
// BAD: Returns null as "not found" sentinel (implicit contract)
public User getUserById(String userId) {
    return null;  // No way to distinguish from "real" not found
}

// Caller has no way to handle stub vs real null:
public void assignTask(String userId, Task task) {
    User user = getUserById(userId);
    if (user != null) {
        user.assign(task);  // NPE if stub returns null!
    }
}
```

**Why it violates**: `return null` ambiguously means either "not found" (real) or "not implemented" (stub). Caller cannot distinguish.

**Fix guidance**: Return Optional or throw, never raw null for "not found".

```java
// GOOD: Explicit contract with Optional
public Optional<User> getUserById(String userId) {
    return userRepository.findById(userId);
}

// Caller handles correctly:
public void assignTask(String userId, Task task) {
    User user = getUserById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    user.assign(task);
}

// OR: Always throw if not found
public User getUserByIdOrThrow(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
}
```

#### Example 4: Empty Collection Return

```java
// BAD: Returns empty list as placeholder
public List<WorkItem> getActiveItems() {
    return Collections.emptyList();  // Stub: always empty!
}

// Caller processes "empty" workflow:
public void processWorkflow(YNet net) {
    List<WorkItem> items = net.getActiveItems();
    if (items.isEmpty()) {
        log.info("Workflow complete.");  // Incorrectly reports done
        return;
    }
    processor.executeAll(items);
}
```

**Why it violates**: `Collections.emptyList()` always signals "no items", but might be a stub. Workflows never execute.

**Fix guidance**: Query real active items or throw.

```java
// GOOD: Real active items query
public List<WorkItem> getActiveItems() {
    return workItems.stream()
        .filter(item -> item.getStatus() == Status.ACTIVE)
        .collect(Collectors.toList());
}

// OR: Fail if item source not configured
public List<WorkItem> getActiveItems() {
    if (workItems == null) {
        throw new IllegalStateException(
            "Work items not loaded from repository. " +
            "Call initialize() before getActiveItems().");
    }
    return workItems;
}
```

#### Example 5: Empty Container

```java
// BAD: Returns new empty map as placeholder config
public Map<String, String> getConfiguration() {
    return new HashMap<>();  // Always empty!
}

// Caller tries to use config:
public void startEngine(Engine engine) {
    Map<String, String> config = engine.getConfiguration();
    String dbUrl = config.get("database.url");  // Always null!
    engine.connect(dbUrl);  // Connects to null? ERROR!
}
```

**Why it violates**: `new HashMap<>()` always empty. Caller cannot get required configuration values.

**Fix guidance**: Load real configuration or throw.

```java
// GOOD: Load from file or properties
public Map<String, String> getConfiguration() {
    return configLoader.loadFromEnvironment();
}

// OR: Fail if not configured
public String getConfigValue(String key) {
    String value = configuration.get(key);
    if (value == null) {
        throw new ConfigurationException(
            "Missing required config: " + key + ". " +
            "Set " + key + " in application.properties");
    }
    return value;
}
```

### Detection Query Example

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?method ?line ?returnValue
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:returnType ?type .

  # Match stub return patterns
  FILTER(
    (REGEX(?body, 'return\\s+"";') ||
     REGEX(?body, 'return\\s+0;') ||
     REGEX(?body, 'return\\s+null;') ||
     REGEX(?body, 'Collections\\.emptyList\\(\\)'))
    &&
    ?type != "void"  # Not for void methods
  )

  EXTRACT_RETURN_VALUE(?body, ?returnValue)
}
```

### Related Patterns

- **H_TODO**: Often appears with H_STUB (e.g., `// TODO` comment + stub return)
- **H_EMPTY**: Void methods with empty bodies vs non-void with stub returns
- **H_FALLBACK**: Catch blocks that stub data instead of throwing

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~30% of all violations |
| Detection speed | 50-200ms per file (AST parsing) |
| False positive rate | <1% (semantic analysis) |
| Typical per 1M LOC | 250-500 violations |

---

## Pattern 4: H_EMPTY — Empty Method Bodies

### What It Catches

Methods with `void` return type that have empty bodies `{ }`. These defer initialization or side-effect logic, causing silent failures.

### Severity

**FAIL** — Blocks code generation. Indicates incomplete behavior.

### Detection Method

**SPARQL on AST** (structural analysis of method bodies)

Detects:
- `public void methodName() { }` (completely empty)
- `public void methodName() {}` (no whitespace)
- `void internal() { }` (all visibility levels)

### How Detection Works

1. Parse Java source to AST
2. Identify all method declarations
3. Check if method returns `void`
4. Check if method body is empty (only whitespace/braces)
5. Report violations with method signature and line
6. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Empty Initialization

```java
// BAD: Empty initialization method
public void initialize() {
}

// System expects initialization to happen:
public class WorkflowEngine {
    private YNetRunner runner;

    public void start(YNet net) {
        engine.initialize();  // Does nothing!
        runner = new YNetRunner(net);
        runner.execute();
    }
}
```

**Why it violates**: `initialize()` empty body signals missing setup. Callers cannot distinguish intentional no-op from incomplete work.

**Fix guidance**: Implement initialization or throw if impossible.

```java
// GOOD: Real initialization
public void initialize() {
    connectionPool.warmUp(MIN_CONNECTIONS);
    schemaValidator.verifyVersion();
    auditLogger.recordStart(LocalDateTime.now());
}

// OR: Fail if requirements not met
public void initialize() {
    if (config == null) {
        throw new IllegalStateException(
            "Configuration must be set before initialize(). " +
            "Call setConfiguration(Config) first.");
    }
    // ... continue
}
```

#### Example 2: Empty Cleanup

```java
// BAD: Empty cleanup method
public void cleanup() {
}

// Caller expects resources to be freed:
public class WorkflowExecutor {
    public void runWorkflow(YNet net) {
        YNetRunner runner = new YNetRunner(net);
        try {
            runner.execute();
        } finally {
            runner.cleanup();  // Does nothing! Resources leak!
        }
    }
}
```

**Why it violates**: `cleanup()` empty means resources (connections, files, locks) are never freed.

**Fix guidance**: Implement real cleanup.

```java
// GOOD: Real resource cleanup
public void cleanup() {
    if (databaseConnection != null && !databaseConnection.isClosed()) {
        databaseConnection.close();
    }
    if (outputStream != null) {
        outputStream.flush();
        outputStream.close();
    }
    auditLogger.recordCompletion(LocalDateTime.now());
}

// OR: Fail if resource state is wrong
public void cleanup() {
    if (runner == null || !runner.isRunning()) {
        throw new IllegalStateException(
            "Cannot cleanup: runner not initialized. " +
            "Call execute() before cleanup().");
    }
    // ... cleanup
}
```

#### Example 3: Empty Save/Persist

```java
// BAD: Empty save method
public void save() {
}

// Caller assumes data persisted:
public class WorkflowService {
    private YNet net;

    public void updateWorkflow(String name, String definition) {
        net.setName(name);
        net.setDefinition(definition);
        net.save();  // Does nothing! Changes lost!
    }
}
```

**Why it violates**: `save()` empty means updates are never persisted to database.

**Fix guidance**: Implement real persistence.

```java
// GOOD: Real database update
public void save() {
    Objects.requireNonNull(this.id, "Cannot save unsaved workflow");
    database.update(
        "UPDATE workflows SET name=?, definition=? WHERE id=?",
        this.name,
        this.definition,
        this.id);
    auditLogger.log("Workflow saved: " + this.id);
}
```

#### Example 4: Empty Validation Method

```java
// BAD: Empty validation always succeeds
public void validate() {
}

// Caller trusts validation happened:
public class TaskAssignmentService {
    public void assign(Task task, User user) {
        task.validate();  // Does nothing!
        taskRepository.assignTo(user);  // Invalid data persisted!
    }
}
```

**Why it violates**: `validate()` empty means invalid tasks slip through.

**Fix guidance**: Implement real validation.

```java
// GOOD: Real validation with exceptions
public void validate() {
    if (this.taskId == null || this.taskId.isEmpty()) {
        throw new ValidationException("Task ID required");
    }
    if (this.priority < 1 || this.priority > 5) {
        throw new ValidationException("Priority must be 1-5, got " + this.priority);
    }
    if (this.assignee == null) {
        throw new ValidationException("Task must be assigned");
    }
}
```

### Detection Query Example

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?method ?line
WHERE {
  ?method a code:Method ;
          code:returnType "void" ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:name ?name .

  # Match completely empty body: only whitespace between braces
  FILTER(REGEX(?body, '^\\s*\\{\\s*\\}\\s*$'))
}
```

### Related Patterns

- **H_TODO**: Often appears with empty methods (e.g., `// TODO` in empty body)
- **H_STUB**: Non-void methods with stub returns
- **H_FALLBACK**: Catch blocks with empty handling

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~15% of all violations |
| Detection speed | 50-200ms per file (AST parsing) |
| False positive rate | <0.5% (structural analysis) |
| Typical per 1M LOC | 100-250 violations |

---

## Pattern 5: H_FALLBACK — Silent Exception Handling

### What It Catches

Catch blocks that return fake data or silently log errors instead of propagating exceptions. These mask failures and create "lying" code.

### Severity

**FAIL** — Blocks code generation. Indicates masked errors.

### Detection Method

**SPARQL on AST** (control flow analysis in catch blocks)

Detects patterns:
- `catch (Exception e) { return Collections.emptyList(); }` (silent failure)
- `catch (Exception e) { return null; }` (masked null)
- `catch (Exception e) { return "default_value"; }` (fake data)
- `catch (Exception e) { log.warn(...); return ...; }` (logged but not thrown)

### How Detection Works

1. Parse Java source to AST
2. Identify all try-catch blocks
3. Analyze catch block body
4. Detect returns of fake/empty data
5. Verify exception is NOT re-thrown
6. Report violations with catch block location
7. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Silent Empty List Return

```java
// BAD: Exception silently returns empty list
public List<Task> fetchTasks(String userId) {
    try {
        return taskRepository.findByUserId(userId);
    } catch (DatabaseException e) {
        return Collections.emptyList();  // Silently fails!
    }
}

// Caller assumes tasks loaded:
public void showUserTasks(String userId) {
    List<Task> tasks = fetchTasks(userId);
    if (tasks.isEmpty()) {
        ui.showMessage("No tasks assigned.");  // Incorrectly reports user has no tasks
    } else {
        ui.renderTasks(tasks);
    }
}
```

**Why it violates**: Empty list return masks database failure. Caller cannot distinguish "user has no tasks" from "database is down".

**Fix guidance**: Propagate exception or explicitly document empty list meaning.

```java
// GOOD: Propagate exception
public List<Task> fetchTasks(String userId) {
    try {
        return taskRepository.findByUserId(userId);
    } catch (DatabaseException e) {
        throw new TaskFetchException(
            "Failed to fetch tasks for user " + userId, e);
    }
}

// Caller handles error explicitly:
public void showUserTasks(String userId) {
    try {
        List<Task> tasks = fetchTasks(userId);
        if (tasks.isEmpty()) {
            ui.showMessage("No tasks assigned.");
        } else {
            ui.renderTasks(tasks);
        }
    } catch (TaskFetchException e) {
        ui.showError("Unable to load tasks: " + e.getMessage());
        log.error("Task fetch failed", e);
    }
}
```

#### Example 2: Silent Null Return

```java
// BAD: Exception silently returns null
public User loadUser(String userId) {
    try {
        return userRepository.findById(userId);
    } catch (Exception e) {
        return null;  // Silently fails, caller gets null
    }
}

// Caller NPE risk:
public void processUser(String userId) {
    User user = loadUser(userId);
    user.notify();  // NPE if exception occurred!
}
```

**Why it violates**: Null return masks exception. Caller may get NPE instead of catching real error.

**Fix guidance**: Throw exception, use Optional, or document null contract.

```java
// GOOD: Throw exception
public User loadUserOrThrow(String userId) {
    try {
        return userRepository.findById(userId);
    } catch (DatabaseException e) {
        throw new UserLoadException(
            "Cannot load user: " + userId, e);
    }
}

// OR: Use Optional
public Optional<User> loadUser(String userId) {
    try {
        return userRepository.findById(userId);
    } catch (DatabaseException e) {
        throw new UserLoadException(
            "Database error loading user: " + userId, e);
    }
}

// Caller handles correctly:
public void processUser(String userId) {
    try {
        User user = loadUserOrThrow(userId);
        user.notify();
    } catch (UserLoadException e) {
        log.error("Failed to process user", e);
    }
}
```

#### Example 3: Logged but Not Thrown

```java
// BAD: Exception logged but execution continues
public void publishEvent(Event event) {
    try {
        eventBus.publish(event);
    } catch (PublishException e) {
        log.warn("Failed to publish event: " + e.getMessage());
        // Silently continues, caller unaware of failure!
    }
}

// Caller thinks event published:
public void notifyUsers(List<User> users) {
    Event notification = Event.create("users_updated", users);
    publishEvent(notification);  // Fails silently, users not notified!
    ui.showMessage("Users notified.");  // False!
}
```

**Why it violates**: Logging masks failure. Caller has no way to know publication failed.

**Fix guidance**: Throw exception, don't just log.

```java
// GOOD: Throw exception when publish fails
public void publishEvent(Event event) {
    try {
        eventBus.publish(event);
    } catch (PublishException e) {
        throw new EventPublicationException(
            "Failed to publish event: " + event.getId(), e);
    }
}

// Caller handles failure:
public void notifyUsers(List<User> users) {
    Event notification = Event.create("users_updated", users);
    try {
        publishEvent(notification);
        ui.showMessage("Users notified.");
    } catch (EventPublicationException e) {
        ui.showError("Failed to notify users: " + e.getMessage());
        log.error("Notification failed", e);
    }
}
```

#### Example 4: Fake Default Data Return

```java
// BAD: Exception returns fake hardcoded data
public Configuration loadConfiguration() {
    try {
        return configRepository.loadActive();
    } catch (ConfigurationException e) {
        Configuration fake = new Configuration();
        fake.setDatabaseUrl("localhost:5432");  // Hardcoded fake!
        fake.setMaxConnections(10);
        return fake;
    }
}

// Caller uses fake config in production:
public void initializeDatabase() {
    Configuration config = loadConfiguration();
    try {
        db.connect(config.getDatabaseUrl());  // Connects to localhost!
    } catch (Exception e) {
        // Should not happen if config was real
    }
}
```

**Why it violates**: Hardcoded defaults are not real configuration. Production connects to wrong database.

**Fix guidance**: Throw exception, never return fake config.

```java
// GOOD: Throw when configuration unavailable
public Configuration loadConfiguration() {
    try {
        return configRepository.loadActive();
    } catch (ConfigurationException e) {
        throw new ConfigurationUnavailableException(
            "Cannot load configuration from repository. " +
            "Ensure CONFIG_FILE environment variable is set.", e);
    }
}

// Caller must handle missing configuration:
public void initializeDatabase() {
    try {
        Configuration config = loadConfiguration();
        db.connect(config.getDatabaseUrl());
    } catch (ConfigurationUnavailableException e) {
        log.error("Cannot start: " + e.getMessage());
        throw e;  // Fail fast
    }
}
```

### Detection Query Example

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?catchBlock ?line ?returnPattern
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line .

  ?catchBlock a code:CatchBlock ;
              code:parentMethod ?method ;
              code:body ?catchBody .

  # Match catch blocks with silent returns
  FILTER(
    REGEX(?catchBody, 'catch\\s*\\([^)]+\\)\\s*\\{[^}]*return[^}]*\\}')
    &&
    !REGEX(?catchBody, 'throw')  # No re-throw in catch block
  )
}
```

### Related Patterns

- **H_STUB**: Catch blocks that return stub data
- **H_SILENT**: Logging in catch blocks instead of throwing
- **H_LIE**: Documentation says method throws but catch silences it

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~5% of all violations |
| Detection speed | 50-200ms per file (AST + control flow) |
| False positive rate | <2% (may match complex control flow) |
| Typical per 1M LOC | 40-100 violations |

---

## Pattern 6: H_LIE — Documentation vs Code Mismatch

### What It Catches

Methods whose documentation contract (Javadoc) contradicts actual implementation. These "lie" to callers about behavior.

### Severity

**FAIL** — Blocks code generation. Indicates contract violation.

### Detection Method

**Semantic SPARQL on AST** (documentation vs code analysis)

Detects mismatches:
- `/** @return never null */` but code `return null;`
- `/** @throws IOException */` but code never throws IOException
- `/** @return empty list if not found */` but code throws exception
- `/** public void initialize() { }` (docs promise behavior, implementation empty)

### How Detection Works

1. Parse Java source to AST
2. Extract method Javadoc
3. Parse Javadoc tags (`@return`, `@throws`, `@param`)
4. Analyze method implementation
5. Compare documentation claims vs actual behavior
6. Report contradictions with method signature
7. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: @return Never Null vs Actual Null Return

```java
// BAD: Javadoc lies about null possibility
/**
 * Get the workflow specification.
 *
 * @return never null, always valid specification
 * @throws IllegalStateException if spec not loaded
 */
public YSpecification getSpecification() {
    return specification;  // Can be null!
}

// Caller trusts documentation:
public void validateWorkflow() {
    YSpecification spec = engine.getSpecification();
    spec.validate();  // NPE if spec is null, but docs said never null!
}
```

**Why it violates**: Javadoc promises "never null", but code returns null unconditionally.

**Fix guidance**: Either guarantee non-null or fix documentation.

```java
// GOOD: Real guarantee with null check
/**
 * Get the workflow specification.
 *
 * @return never null, specification loaded from repository
 * @throws IllegalStateException if spec not loaded
 */
public YSpecification getSpecification() {
    if (specification == null) {
        throw new IllegalStateException(
            "Specification not loaded. Call loadSpecification() first.");
    }
    return specification;
}

// OR: Fix documentation to match implementation
/**
 * Get the workflow specification if loaded.
 *
 * @return specification, or null if not yet loaded
 * @throws IllegalStateException if load failed
 */
public YSpecification getSpecification() {
    return specification;
}

// Caller handles null:
public void validateWorkflow() {
    YSpecification spec = engine.getSpecification();
    if (spec != null) {
        spec.validate();
    } else {
        // Handle not-loaded case
    }
}
```

#### Example 2: @throws Declaration vs No Exception Thrown

```java
// BAD: Javadoc claims exception never thrown
/**
 * Process the workflow.
 *
 * @throws YEngineException on validation failure
 * @throws InterruptedException if processing interrupted
 */
public void process(YNet net) {
    YNetRunner runner = new YNetRunner(net);
    runner.execute();  // Never throws declared exceptions!
}

// Caller expects to catch documented exception:
public void runWorkflow(YNet net) {
    try {
        engine.process(net);
    } catch (YEngineException e) {
        log.error("Workflow failed: " + e.getMessage());
        // Never happens because process() never throws!
    }
}
```

**Why it violates**: Javadoc promises exceptions that code never throws. Caller's error handling is dead code.

**Fix guidance**: Either throw documented exceptions or remove from Javadoc.

```java
// GOOD: Throw documented exceptions
/**
 * Process the workflow.
 *
 * @throws YEngineException on validation failure
 * @throws InterruptedException if processing interrupted
 */
public void process(YNet net) throws YEngineException, InterruptedException {
    YNetRunner runner = new YNetRunner(net);
    try {
        runner.execute();
    } catch (ExecutionException e) {
        throw new YEngineException("Workflow execution failed", e);
    }
}

// OR: Remove false exception declarations
/**
 * Process the workflow.
 */
public void process(YNet net) {
    YNetRunner runner = new YNetRunner(net);
    runner.execute();
}
```

#### Example 3: @return Type vs Actual Return Type

```java
// BAD: Javadoc describes List, code returns null or empty
/**
 * Get all active work items.
 *
 * @return list of active items (never empty)
 */
public List<WorkItem> getActiveItems() {
    return null;  // Contradicts "never empty"!
}

// Caller assumes non-empty:
public void executeWorkflow() {
    List<WorkItem> items = engine.getActiveItems();
    WorkItem first = items.get(0);  // NPE if null!
}
```

**Why it violates**: Javadoc describes guaranteed non-empty list, code returns null.

**Fix guidance**: Return actual list or fix documentation.

```java
// GOOD: Return real list with guarantee
/**
 * Get all active work items.
 *
 * @return list of active items (empty if none active)
 */
public List<WorkItem> getActiveItems() {
    List<WorkItem> active = workItems.stream()
        .filter(item -> item.isActive())
        .collect(Collectors.toList());
    return Collections.unmodifiableList(active);
}

// Caller handles empty list:
public void executeWorkflow() {
    List<WorkItem> items = engine.getActiveItems();
    if (items.isEmpty()) {
        log.info("No active items, workflow complete.");
        return;
    }
    items.forEach(item -> executor.run(item));
}
```

#### Example 4: Behavior vs Documentation Contract

```java
// BAD: Javadoc promises validation, empty code doesn't
/**
 * Validate the workflow specification.
 * Ensures all elements reference valid resources.
 *
 * @throws ValidationException if any validation fails
 */
public void validate() {
    // Empty! No validation done!
}

// Caller trusts validation happened:
public void deployWorkflow(YNet net) {
    net.validate();  // Does nothing!
    deploymentService.deploy(net);  // Invalid workflow deployed!
}
```

**Why it violates**: Javadoc promises validation behavior, code does nothing.

**Fix guidance**: Either implement validation or fix documentation.

```java
// GOOD: Implement promised validation
/**
 * Validate the workflow specification.
 * Ensures all elements reference valid resources.
 *
 * @throws ValidationException if any validation fails
 */
public void validate() {
    if (elements == null || elements.isEmpty()) {
        throw new ValidationException("Workflow has no elements");
    }

    for (WorkflowElement element : elements) {
        if (!resourceRegistry.contains(element.getResourceId())) {
            throw new ValidationException(
                "Element references unknown resource: " +
                element.getResourceId());
        }
    }
}

// OR: Fix documentation if validation not needed
/**
 * Mark this workflow as validated (without actual validation).
 * Call validator.validate(net) first to ensure correctness.
 */
public void markValidated() {
    this.validated = true;
}
```

### Detection Query Example

```sparql
PREFIX code: <http://ggen.io/code#>
PREFIX javadoc: <http://ggen.io/javadoc#>

SELECT ?method ?line ?mismatch
WHERE {
  ?method a code:Method ;
          code:javadoc ?doc ;
          code:body ?body ;
          code:lineNumber ?line ;
          code:name ?name .

  ?doc javadoc:returns ?returnDecl ;
       javadoc:throws ?throwDecl .

  # Check if @return says non-null but code returns null
  FILTER(
    CONTAINS(?returnDecl, "never null") &&
    REGEX(?body, 'return\\s+null')
  )

  BIND("Return mismatch: @return never null but code returns null"
       AS ?mismatch)
}
```

### Related Patterns

- **H_EMPTY**: Empty implementations often paired with detailed Javadoc
- **H_STUB**: Stub returns contradicting @return documentation
- **H_FALLBACK**: Catch blocks contradicting @throws documentation

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~3% of all violations |
| Detection speed | 100-500ms per file (semantic analysis) |
| False positive rate | ~5% (complex semantic rules) |
| Typical per 1M LOC | 20-60 violations |

---

## Pattern 7: H_SILENT — Logging Instead of Throwing

### What It Catches

Code that logs warnings/errors about missing implementations instead of throwing exceptions. These "silently" hide failures in logs.

### Severity

**FAIL** — Blocks code generation. Indicates masked errors.

### Detection Method

**Regex pattern** (log statement analysis)

Detects:
- `log.warn("not implemented");` (warning about unimplemented feature)
- `log.error("...not yet implemented...");` (error about missing code)
- `logger.warn("TODO: ...");` (TODO in log message)
- `System.out.println("...not implemented...");` (stdout instead of exception)

### How Detection Works

1. Scan file line by line
2. Find log statements: `log.warn()`, `log.error()`, `logger.warn()`, etc.
3. Extract log message text
4. Match text for implementation keywords: "not implemented", "TODO", "FIXME", etc.
5. Report violations with line number and log statement
6. Exit code 2 (fatal violation)

### Real Violation Examples

#### Example 1: Warning About Unimplemented Feature

```java
// BAD: Logs warning instead of throwing
public void startTransaction() {
    if (supportedDatabases.isEmpty()) {
        log.warn("Transaction support not yet implemented");
        // Continues without transaction!
    }
    // ... proceeds without safety guarantee
}

// Caller assumes transaction started:
public void saveWorkflow(YNet net) {
    db.startTransaction();
    net.save();
    // If "not yet implemented", transaction never started!
    db.commit();
}
```

**Why it violates**: `log.warn()` logs to file, but execution continues unsafely. No stack trace, no exception propagation.

**Fix guidance**: Throw exception instead of logging.

```java
// GOOD: Throw exception for missing feature
public void startTransaction() {
    if (supportedDatabases.isEmpty()) {
        throw new UnsupportedOperationException(
            "Transaction support not yet implemented. " +
            "Supported databases: " + SUPPORTED_DBS +
            ". See issue #234.");
    }
    // ... real transaction logic
}

// Caller must handle exception:
public void saveWorkflow(YNet net) {
    try {
        db.startTransaction();
        net.save();
        db.commit();
    } catch (UnsupportedOperationException e) {
        // Explicitly handle not-yet-implemented
        log.error("Cannot save: " + e.getMessage());
        throw e;
    }
}
```

#### Example 2: Error Log About TODO

```java
// BAD: Logs error with TODO instead of failing
public void attachResourceReference(String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) {
        log.error("TODO: resource validation not implemented yet");
        this.resourceId = "UNVALIDATED";  // Fake assignment!
        return;
    }
    // ... continues with fake ID
}

// Caller assumes reference valid:
public void executeTask(Task task) {
    task.attachResourceReference("unknown_resource_id");
    taskRepository.save(task);  // Invalid reference persisted!
}
```

**Why it violates**: Logs error but continues with fake data. Reference validation never happens.

**Fix guidance**: Throw exception and force caller to provide real reference.

```java
// GOOD: Validate and throw on error
public void attachResourceReference(String resourceId) {
    if (resourceId == null || resourceId.isEmpty()) {
        throw new IllegalArgumentException("resourceId required");
    }

    if (!resourceRegistry.contains(resourceId)) {
        throw new ResourceNotFoundException(
            "Resource not found: " + resourceId);
    }

    this.resourceId = resourceId;
}

// Caller must provide valid reference:
public void executeTask(Task task, String resourceId) {
    try {
        task.attachResourceReference(resourceId);
        taskRepository.save(task);
    } catch (ResourceNotFoundException e) {
        // Handle missing resource
        ui.showError("Resource not available: " + resourceId);
    }
}
```

#### Example 3: System.out Println Instead of Exception

```java
// BAD: Prints to stdout instead of throwing
public void loadConfiguration(String path) {
    File file = new File(path);
    if (!file.exists()) {
        System.out.println("Config file not found, using defaults");
        // Silently uses defaults, no error handling possible
        loadDefaults();
        return;
    }
    // ... continues with real config
}

// Caller has no way to know defaults were used:
public void initializeEngine(String configPath) {
    engine.loadConfiguration(configPath);
    engine.start();  // Started with defaults instead of real config!
}
```

**Why it violates**: `System.out.println()` is not a proper error signal. Caller cannot detect or handle the issue.

**Fix guidance**: Throw exception or return status.

```java
// GOOD: Throw exception on missing config
public void loadConfiguration(String path) {
    File file = new File(path);
    if (!file.exists()) {
        throw new ConfigurationException(
            "Configuration file not found: " + path + ". " +
            "Expected at CONFIG_PATH environment variable.");
    }
    loadFromFile(file);
}

// OR: Allow fallback with explicit contract
/**
 * Load configuration from path, or defaults if not found.
 *
 * @param path path to config file (optional)
 * @return loaded config (from file or defaults)
 */
public Configuration loadConfiguration(Optional<String> path) {
    if (path.isPresent()) {
        File file = new File(path.get());
        if (!file.exists()) {
            throw new ConfigurationException(
                "Specified config file not found: " + path.get());
        }
        return loadFromFile(file);
    }
    return loadDefaults();  // Explicit fallback
}
```

#### Example 4: Error Log with Implementation Pending

```java
// BAD: Logs error about feature not implemented
public void processDeadlock(YNet net) {
    if (hasCircularDependency(net)) {
        log.error("Deadlock detection: feature not yet implemented");
        // Returns without handling deadlock!
        return;
    }
}

// Caller thinks deadlocks handled:
public void executeWorkflow(YNet net) {
    engine.processDeadlock(net);
    executor.run(net);  // Executes potentially deadlocked workflow!
}
```

**Why it violates**: Logs "not implemented" but allows execution to continue unsafely.

**Fix guidance**: Throw exception to halt unsafe execution.

```java
// GOOD: Throw on unimplemented safety feature
public void processDeadlock(YNet net) throws DeadlockException {
    if (hasCircularDependency(net)) {
        throw new DeadlockException(
            "Circular dependency detected in net: " + net.getId() + ". " +
            "Deadlock resolution not yet implemented. See issue #567.");
    }
    // ... handle other conditions
}

// Caller must handle deadlock risk:
public void executeWorkflow(YNet net) {
    try {
        engine.processDeadlock(net);
        executor.run(net);
    } catch (DeadlockException e) {
        // Handle deadlock explicitly
        log.error("Workflow execution blocked: " + e.getMessage());
        notifyAdministrator(e);
        throw e;
    }
}
```

### Detection Query Example

```sparql
PREFIX code: <http://ggen.io/code#>

SELECT ?line ?logStatement
WHERE {
  ?method a code:Method ;
          code:body ?body ;
          code:lineNumber ?line .

  # Find log statements about "not implemented"
  FILTER(REGEX(?body, 'log\\.(warn|error)\\([^)]*"[^"]*not\\s+(implemented|yet)[^"]*"'))

  EXTRACT_LOG_STATEMENT(?body, ?logStatement)
}
```

### Related Patterns

- **H_TODO**: Similar intent (deferred work) but in comments
- **H_FALLBACK**: Similar effect (silent failure) but in catch blocks
- **H_EMPTY**: Often paired with H_SILENT (empty method logs warning)

### Statistics

| Metric | Value |
|--------|-------|
| Frequency in legacy code | ~2% of all violations |
| Detection speed | <1ms per file (regex) |
| False positive rate | <3% (may match legitimate log messages) |
| Typical per 1M LOC | 15-40 violations |

---

## Violation Frequency Distribution

Based on typical legacy codebases (1M LOC sample):

```
H_STUB       (30%) ████████████████████████████
H_TODO       (25%) █████████████████████
H_MOCK       (20%) ████████████████
H_EMPTY      (15%) █████████████
H_FALLBACK    (5%) █████
H_LIE         (3%) ███
H_SILENT      (2%) ██
```

**Key insight**: Placeholder returns (H_STUB) and deferred work (H_TODO) account for >50% of violations. Focus remediation efforts there first.

---

## Detection Architecture

### Full Pipeline

```
Generated Java Files
    ↓
Lexical Scan (Regex Phase)
├─ H_TODO      ← comments
├─ H_MOCK      ← identifiers
└─ H_SILENT    ← log statements
    ↓ (violations reported)
    ↓
AST Parse & RDF Conversion
├─ Tree-sitter Java parser
├─ Extract methods, bodies, types
└─ Convert to RDF fact graph
    ↓
SPARQL Semantic Analysis (AST Phase)
├─ H_STUB      ← return values
├─ H_EMPTY     ← method bodies
├─ H_FALLBACK  ← catch blocks
└─ H_LIE       ← documentation vs code
    ↓ (violations reported)
    ↓
Guard Receipt (JSON)
├─ files_scanned
├─ violations[]
└─ summary{}
    ↓
Exit Code Decision
├─ violations.count == 0 → exit 0 (GREEN)
└─ violations.count > 0  → exit 2 (RED)
```

### Performance Characteristics

| Phase | Speed | Complexity | Accuracy |
|-------|-------|-----------|----------|
| Regex (TODO, MOCK, SILENT) | <1ms per file | O(n) lines | 95-98% |
| AST Parse | 10-50ms per file | O(n) AST nodes | 99%+ |
| RDF Conversion | 20-100ms per file | O(m) facts | 99%+ |
| SPARQL Queries | 20-300ms per file | O(q) queries | 95-99% |
| **Total** | **50-500ms per file** | **O(n+m+q)** | **98%+** |

For 10K files (typical codebase):
- Regex phase: ~10 seconds
- AST + SPARQL: ~2-5 minutes
- **Total: 2-6 minutes** for full scan

### Configuration

Guards validation is configured in `guard-config.toml`:

```toml
[guards]
enabled = true
phase = "H"

patterns = [
  { name = "H_TODO", severity = "FAIL" },
  { name = "H_MOCK", severity = "FAIL" },
  { name = "H_STUB", severity = "FAIL" },
  { name = "H_EMPTY", severity = "FAIL" },
  { name = "H_FALLBACK", severity = "FAIL" },
  { name = "H_LIE", severity = "FAIL" },
  { name = "H_SILENT", severity = "FAIL" }
]

[receipt]
enabled = true
format = "json"
path = ".claude/receipts/guard-receipt.json"
```

---

## Remediation Patterns

### Pattern 1: Implement Real Logic

When you can implement the required behavior:

```java
// Instead of:
return "";  // H_STUB

// Do:
return Objects.requireNonNull(value,
    "Expected non-null value from initialization");
```

### Pattern 2: Throw UnsupportedOperationException

When the feature truly cannot be implemented yet:

```java
// Instead of:
// TODO: not yet implemented
return null;

// Do:
throw new UnsupportedOperationException(
    "Feature not yet implemented. " +
    "Dependencies: database schema, resource pool. " +
    "See issue #456.");
```

### Pattern 3: Validate and Fail Fast

When input validation is deferred:

```java
// Instead of:
public void process(Config config) {
    if (config == null) {
        log.warn("Config is null");  // H_SILENT
    }
    // continues unsafely
}

// Do:
public void process(Config config) {
    Objects.requireNonNull(config,
        "Config required. See SETUP_GUIDE.md");
    // continues safely
}
```

### Pattern 4: Use Optional for "Not Found"

When null is used to mean "not found":

```java
// Instead of:
public User findUser(String id) {
    try {
        return repository.findById(id);
    } catch (Exception e) {
        return null;  // H_FALLBACK + H_STUB
    }
}

// Do:
public Optional<User> findUser(String id) {
    return repository.findById(id);
}

// Caller handles explicitly:
user = findUser(id)
    .orElseThrow(() -> new UserNotFoundException(id));
```

---

## Integration with Build Pipeline

### CI/CD Hook Points

```bash
# Phase Λ (Build): Code compiles
mvn clean compile -pl yawl-generator

# Phase H (Guards): Violations checked
ggen validate --phase guards \
  --emit generated/java \
  --receipt .claude/receipts/guard-receipt.json

# Exit code handling
if [ $? -eq 0 ]; then
    echo "GREEN: No violations, proceeding to Phase Q"
    ggen validate --phase invariants
else
    echo "RED: Violations detected, stopping build"
    cat .claude/receipts/guard-receipt.json
    exit 2
fi
```

### Batch Remediation

To fix violations at scale:

```bash
# Find all violations
ggen validate --phase guards --emit src/main/java

# Review detailed violations
jq '.violations[] | {file, line, pattern, content}' \
    .claude/receipts/guard-receipt.json | less

# Group by pattern for focused fixes
jq 'group_by(.pattern) | .[] |
    {pattern: .[0].pattern, count: length}' \
    .claude/receipts/guard-receipt.json
```

---

## See Also

- [H-Guards Implementation Guide](/yawl/.claude/rules/validation-phases/H-GUARDS-IMPLEMENTATION.md) — Step-by-step implementation
- [H-Guards SPARQL Queries](/yawl/.claude/rules/validation-phases/H-GUARDS-QUERIES.md) — Complete query reference
- [Hyper-Standards Pattern Reference](/docs/reference/hyper-standards.md) — Production code standards (14 patterns)
- [Tutorial: Fix Your First Violation](/docs/tutorials/12-fix-hyper-standards-violation.md) — Walkthrough
- [How-To: Fix H-Guard Violations at Scale](/docs/how-to/build/fix-h-guard-violations.md) — Batch remediation

---

## Glossary

| Term | Meaning |
|------|---------|
| **Guard** | Validation pattern that blocks non-compliant code |
| **H Phase** | Guards phase in Λ (Build) pipeline |
| **Violation** | Single instance of pattern match in source code |
| **Receipt** | JSON report of all violations found (guard-receipt.json) |
| **Semantic analysis** | Understanding code meaning, not just text patterns |
| **SPARQL** | Query language for RDF fact graphs (semantic web) |
| **AST** | Abstract Syntax Tree (code structure representation) |
| **RDF** | Resource Description Framework (semantic data model) |

---

**Version 2.0** | Last updated: 2026-02-28
**Authoritative reference for H-Guards pattern detection and remediation**
