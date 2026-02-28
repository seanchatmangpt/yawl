# YAWL Validation Gates Upgrade Guide

## 1. What Changed

YAWL now enforces Fortune 5 production standards through two new validation phases integrated into the `dx.sh` build pipeline:

- **H (Guards Phase)**: Scans for forbidden patterns like TODO markers, mocks, stubs, empty implementations, silent fallbacks, documentation mismatches, and logging instead of throwing
- **Q (Invariants Phase)**: Ensures code quality invariants — that every implementation either works correctly or explicitly throws `UnsupportedOperationException`

These phases integrate into YAWL's GODSPEED flow as: **Λ (Compile) → H (Guards) → Q (Invariants) → Ω (Deploy)**

Validation runs automatically on full builds only (`dx.sh all`), not on incremental development builds (`dx.sh` or `dx.sh -pl`). This keeps fast feedback loops fast while preventing broken code from reaching production.

**New CLI parameters**:
- `dx.sh all` — Full build + validation (new default)
- `dx.sh all --skip-validate` — Full build without validation (backward compatibility)
- `dx.sh --validate-only` — Run only H + Q phases (for debugging)

Receipt files documenting all violations are written to `.claude/receipts/guard-receipt.json` and `.claude/receipts/invariant-receipt.json`.

---

## 2. Backward Compatibility

**No action required.** All existing scripts continue working unchanged:

- `dx.sh` — Changed modules, no validation (development)
- `dx.sh compile` — Compile only, no validation
- `dx.sh test` — Test only, no validation
- `dx.sh -pl mod1,mod2` — Explicit modules, no validation
- `dx.sh compile all` — Compile all modules, no validation
- `dx.sh test all` — Test all modules, no validation

The **only new behavior** is `dx.sh all`, which now adds validation gates. If you need the old behavior, use:

```bash
# Full build without validation (legacy CI pipelines)
bash scripts/dx.sh all --skip-validate
```

All existing CI pipelines continue working without changes. Teams that don't run `dx.sh all` are unaffected. The `--skip-validate` flag provides an escape hatch for legacy systems while the build system supports both patterns simultaneously.

---

## 3. Fixing Guard Violations

When `dx.sh all` detects violations, it exits with code 2 and writes detailed diagnostics to `.claude/receipts/guard-receipt.json`. Each violation includes the exact line, pattern, and fix guidance. Here are all 7 patterns with examples and fixes:

### H_TODO — Deferred Work Markers

**Pattern**: Comments containing `//TODO`, `//FIXME`, `//XXX`, `//HACK`, `//LATER`, `//FUTURE`, `@incomplete`, or `@stub`

**Example that fails**:
```java
public void addDeadlockDetection() {
    // TODO: Add deadlock detection for circular dependencies
    this.deadlockDetectorEnabled = false;
}
```

**How to fix**: Either implement the real logic or throw `UnsupportedOperationException`:

```java
public void addDeadlockDetection() {
    throw new UnsupportedOperationException(
        "Deadlock detection not yet implemented. " +
        "See IMPLEMENTATION_GUIDE.md for design details."
    );
}
```

Or implement it completely:
```java
public void addDeadlockDetection() {
    this.deadlockDetector = new CycleDetector(this.graph);
    this.deadlockDetectorEnabled = true;
}
```

### H_MOCK — Mock Implementations

**Pattern**: Class or method names starting with `mock`, `stub`, `fake`, or `demo` (case-insensitive)

**Example that fails**:
```java
public class MockDataService implements DataService {
    @Override
    public String fetchData(String id) {
        return "mock data";
    }
}
```

**How to fix**: Delete the mock or implement the real service:

```java
public class DataService implements IDataService {
    @Override
    public String fetchData(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ID cannot be empty");
        }
        return repository.fetch(id).orElseThrow(
            () -> new DataNotFoundException("Data not found: " + id)
        );
    }
}
```

### H_STUB — Empty or Placeholder Returns

**Pattern**: Methods returning empty strings (`""`), zero (`0`), `null`, or empty collections (`Collections.emptyList()`, `new HashMap()`, etc.)

**Example that fails**:
```java
public String getWorkflowId() {
    return "";  // Stub return
}

public List<Task> getTasks() {
    return Collections.emptyList();
}
```

**How to fix**: Implement the real method or throw an exception:

```java
public String getWorkflowId() {
    return this.workflow != null 
        ? this.workflow.getId()
        : throw new IllegalStateException("Workflow not initialized");
}

public List<Task> getTasks() {
    if (this.tasks == null) {
        throw new UnsupportedOperationException(
            "Task retrieval requires real task manager implementation"
        );
    }
    return Collections.unmodifiableList(this.tasks);
}
```

### H_EMPTY — No-Op Method Bodies

**Pattern**: Void methods with empty bodies `{ }`

**Example that fails**:
```java
public void initialize() {
}

public void shutdown() {
}
```

**How to fix**: Implement the actual logic or throw an exception:

```java
public void initialize() {
    this.scheduler = Executors.newScheduledThreadPool(threadCount);
    this.taskQueue = new ConcurrentLinkedQueue<>();
    this.isRunning = true;
}

public void shutdown() {
    if (!this.isRunning) {
        return;  // Already stopped
    }
    this.scheduler.shutdown();
    if (!this.scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
        this.scheduler.shutdownNow();
    }
    this.isRunning = false;
}
```

### H_FALLBACK — Silent Degradation in Catch Blocks

**Pattern**: Catch blocks that return fake data instead of propagating the exception

**Example that fails**:
```java
public List<WorkItem> getWorkItems(String caseId) {
    try {
        return service.fetchWorkItems(caseId);
    } catch (IOException e) {
        return Collections.emptyList();  // Silent fallback
    }
}
```

**How to fix**: Propagate the exception up the stack:

```java
public List<WorkItem> getWorkItems(String caseId) throws IOException {
    return service.fetchWorkItems(caseId);
}

// Or wrap in a domain exception:
public List<WorkItem> getWorkItems(String caseId) {
    try {
        return service.fetchWorkItems(caseId);
    } catch (IOException e) {
        throw new WorkflowDataException(
            "Failed to retrieve work items for case: " + caseId,
            e
        );
    }
}
```

### H_LIE — Code Doesn't Match Documentation

**Pattern**: Javadoc promises behavior that the code doesn't implement, or vice versa

**Example that fails**:
```java
/**
 * Gets the workflow name.
 * @return the workflow name, never null
 */
public String getWorkflowName() {
    return this.name;  // Can be null!
}

/**
 * Executes the workflow.
 * @throws WorkflowException if execution fails
 */
public void execute() {
    // Never throws, just logs
    try {
        workflow.run();
    } catch (Exception e) {
        logger.error("Execution failed", e);
    }
}
```

**How to fix**: Either update the code to match docs or update docs to match code:

```java
/**
 * Gets the workflow name.
 * @return the workflow name, never null
 * @throws IllegalStateException if workflow is not initialized
 */
public String getWorkflowName() {
    if (this.name == null) {
        throw new IllegalStateException("Workflow not initialized");
    }
    return this.name;
}

/**
 * Executes the workflow.
 * @throws WorkflowException if execution fails
 */
public void execute() throws WorkflowException {
    try {
        workflow.run();
    } catch (Exception e) {
        throw new WorkflowException("Execution failed: " + e.getMessage(), e);
    }
}
```

### H_SILENT — Logging Instead of Throwing

**Pattern**: Logging errors about unimplemented features instead of throwing exceptions

**Example that fails**:
```java
public String processWorkflow() {
    try {
        return engine.execute();
    } catch (Exception e) {
        log.error("Workflow processing not implemented yet");  // Silent failure
        return null;
    }
}
```

**How to fix**: Throw an exception instead:

```java
public String processWorkflow() throws WorkflowException {
    try {
        return engine.execute();
    } catch (Exception e) {
        throw new WorkflowException(
            "Failed to execute workflow: " + e.getMessage(),
            e
        );
    }
}
```

---

## 4. Fixing Invariant Violations

The Q (Invariants) phase enforces four code quality rules:

**Q1: Real Implementation or Exception**  
Every public method must either work correctly or throw `UnsupportedOperationException`.

```java
// FAILS Q1
public void doWork() {
    System.out.println("Not implemented");
}

// PASSES Q1
public void doWork() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("doWork requires real implementation");
}

// Or implements it completely
public void doWork() {
    this.engine.execute();
    this.metrics.recordCompletion();
}
```

**Q2: No Mock Classes in Production**  
Classes with mock/stub/fake/demo in their names are forbidden in src/main.

```java
// FAILS Q2
public class MockTaskService {}

// PASSES Q2
public class TaskService {}
public class StubTaskService {}  // OK only in src/test
```

**Q3: No Silent Exception Fallbacks**  
Catch blocks cannot return fake data; they must propagate or wrap exceptions.

```java
// FAILS Q3
try {
    work.execute();
} catch (Exception e) {
    return null;
}

// PASSES Q3
try {
    work.execute();
} catch (Exception e) {
    throw new WorkflowException("Execution failed", e);
}
```

**Q4: Documentation Must Match Code**  
Javadoc contracts must be honored by the implementation.

---

## 5. Troubleshooting

**Q: Validation is slow**  
A: Typical validation runs in 2-3 seconds per 100 Java files. Full codebases complete in <10 seconds. This runs only on `dx.sh all`, not in rapid development cycles.

**Q: How do I skip validation?**  
A: Use `dx.sh all --skip-validate` to run full builds without H+Q gates. This is supported for legacy CI pipelines.

**Q: Can I run validation alone?**  
A: Yes, use `dx.sh --validate-only` to run only H + Q phases without compiling. Useful for debugging violations without recompiling.

**Q: Receipt file not generated?**  
A: Receipt files are written to `.claude/receipts/guard-receipt.json` and `.claude/receipts/invariant-receipt.json`. Check that the directory exists and is writable.

**Q: CI pipeline failing on validation?**  
A: Check `.claude/receipts/` for violation details. If legacy systems can't adapt, use `--skip-validate` as a temporary bridge while teams fix violations in parallel. For new violations, reference the pattern documentation above and fix violations or throw exceptions.

---

## 6. Performance & Impact

- **H-phase runtime**: 2-3 seconds per 100 Java files (regex-based scanning)
- **Q-phase runtime**: 3-4 seconds per 100 files (semantic invariant checks)
- **Total impact**: <10 seconds for typical codebases
- **Development builds** (`dx.sh`): 0 seconds (validation skipped)
- **Full builds** (`dx.sh all`): +5-10 seconds
- **Future**: Phases can be parallelized for further speedup

The validation overhead only affects full builds (`dx.sh all`), keeping development loops fast for incremental work. Teams using rapid iteration with `dx.sh` or `dx.sh -pl` are unaffected.

---

## Summary

| Command | Validation | Use Case |
|---------|-----------|----------|
| `dx.sh` | ✗ No | Rapid development (changed modules only) |
| `dx.sh -pl mod1,mod2` | ✗ No | Testing specific modules |
| `dx.sh compile all` | ✗ No | Full compile without tests |
| `dx.sh all` | ✓ **Yes** | Production builds (new) |
| `dx.sh all --skip-validate` | ✗ No | Legacy CI pipelines |
| `dx.sh --validate-only` | ✓ **Yes** | Debug violations only |

**No action required for existing workflows.** New violations appear only on `dx.sh all`. Use the pattern fixes documented above or `--skip-validate` while teams remediate. Full backward compatibility is maintained.
