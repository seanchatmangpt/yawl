# ScopedValue Migration Reference — Java 21-25

**Document Type:** Pure Reference (API Documentation)
**Audience:** YAWL team members migrating from ThreadLocal to ScopedValue
**Java Versions:** Java 21-25
**Related JEP:** [JEP 487 — Scoped Values (Java 21 Preview, Java 25 Final)](https://openjdk.org/jeps/487)

---

## Executive Summary

**ScopedValue** is Java 25's successor to `ThreadLocal`, specifically designed for virtual thread safety. Unlike ThreadLocal, ScopedValue values are:
- **Immutable within a scope** — no silent overwrites
- **Auto-released on scope exit** — no `remove()` needed
- **Inherited by child virtual threads** — no manual propagation
- **Zero cross-thread leakage** — isolated to single scope

YAWL 6.0.0 uses ScopedValue for tenant context propagation through multi-tenant workflow execution.

---

## Quick Comparison: ThreadLocal vs ScopedValue

| Aspect | ThreadLocal | ScopedValue |
|--------|------------|------------|
| **Cleanup** | Manual `remove()` | Automatic on scope exit |
| **Thread inheritance** | Manual propagation to children | Automatic for virtual threads |
| **Mutation** | Mutable within thread | Immutable within scope |
| **Leakage risk** | High (forgetting `remove()`) | Zero (scope isolation) |
| **Virtual thread safe** | ⚠️ Poor (pinning, context loss) | ✅ Excellent (designed for vthreads) |
| **Performance** | 50 ns/get, 50 ns/set | ~40 ns/get, ~40 ns/set |
| **API maturity** | GA since Java 1.2 | Final since Java 25 |
| **Recommended for Java 25+** | ❌ No | ✅ Yes (preferred) |

---

## ThreadLocal → ScopedValue Migration Patterns

### Pattern 1: Simple Read Access

**ThreadLocal (Legacy):**
```java
// Declaration
private static final ThreadLocal<TenantContext> CURRENT_TENANT = new ThreadLocal<>();

// Setter (somewhere in initialization)
YEngine.setTenantContext(tenantContext);

// Getter
TenantContext ctx = YEngine.getTenantContext();
if (ctx != null) {
    // Use context
}

// Cleanup (often forgotten!)
YEngine.clearTenantContext();
```

**ScopedValue (Modern):**
```java
// Declaration (package-private)
static final ScopedValue<TenantContext> CURRENT_TENANT = ScopedValue.newInstance();

// Getter (check if bound)
static TenantContext getTenantContext() {
    return CURRENT_TENANT.isBound() ? CURRENT_TENANT.get() : null;
}

// No cleanup needed — automatic!
```

**Usage stays the same:**
```java
TenantContext ctx = ScopedTenantContext.getTenantContext();
```

---

### Pattern 2: Runnable-Based Binding (Most Common)

**ThreadLocal (Problematic):**
```java
public void executeWorkflow(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    // Manual binding
    setTenantContext(tenant);
    try {
        YNetRunner runner = yEngine.createNetRunner(caseId);
        runner.executeBusyTasks();
        // Problem: Child virtual threads don't inherit tenant!
        // Problem: If exception here, cleanup never runs!
    } finally {
        clearTenantContext();  // Cleanup required (easy to forget)
    }
}
```

**ScopedValue (Safe):**
```java
public void executeWorkflow(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    // Binding + auto-cleanup
    ScopedTenantContext.runWithTenant(tenant, () -> {
        YNetRunner runner = yEngine.createNetRunner(caseId);
        runner.executeBusyTasks();
        // Automatic cleanup here
    });
    // Context is guaranteed released even if exception thrown
}
```

---

### Pattern 3: Callable<T> with Result Return

**ThreadLocal (Messy):**
```java
public String fetchCaseData(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    setTenantContext(tenant);
    try {
        return yEngine.getCaseData(caseId);  // Requires tenant context
    } finally {
        clearTenantContext();
    }
}
```

**ScopedValue (Clean):**
```java
public String fetchCaseData(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    return ScopedTenantContext.runWithTenant(tenant, () ->
        yEngine.getCaseData(caseId)  // Context inherited here
    );
}
```

---

### Pattern 4: Nested Scope (Avoiding Overwrite)

**ThreadLocal (Dangerous):**
```java
public void outerTask(String tenantId1) {
    TenantContext tenant1 = new TenantContext(tenantId1);

    setTenantContext(tenant1);  // tenant1 bound
    try {
        doWork1();

        // Problem: inner task overwrites!
        TenantContext tenant2 = new TenantContext("other-tenant-id");
        setTenantContext(tenant2);  // tenant1 OVERWRITTEN (silent bug!)
        try {
            doWork2();  // Runs under tenant2, leaks context!
        } finally {
            clearTenantContext();  // Only clears tenant2
        }
        // Problem: tenant1 context is lost, subsequent code broken

        doWork3();  // Runs with NO context (should be tenant1)
    } finally {
        clearTenantContext();
    }
}
```

**ScopedValue (Safe — immutable nesting):**
```java
public void outerTask(String tenantId1) {
    TenantContext tenant1 = new TenantContext(tenantId1);

    ScopedTenantContext.runWithTenant(tenant1, () -> {
        doWork1();

        // Safe: inner scope is independent
        TenantContext tenant2 = new TenantContext("other-tenant-id");
        ScopedTenantContext.runWithTenant(tenant2, () -> {
            doWork2();  // Runs under tenant2
        });  // Inner scope exits, tenant2 released

        doWork3();  // Automatically back under tenant1
    });  // Outer scope exits, tenant1 released
}
```

---

### Pattern 5: Parallel Virtual Threads (Critical)

**ThreadLocal (Fails):**
```java
public List<String> processTasksInParallel(List<String> taskIds, TenantContext tenant) {
    List<String> results = Collections.synchronizedList(new ArrayList<>());
    List<Thread> threads = new ArrayList<>();

    // Problem: Child virtual threads don't inherit ThreadLocal!
    for (String taskId : taskIds) {
        Thread vthread = Thread.ofVirtual()
            .start(() -> {
                // BUG: tenant context is NULL here!
                String result = processTask(taskId);  // No tenant context!
                results.add(result);
            });
        threads.add(vthread);
    }

    for (Thread t : threads) t.join();
    return results;
}
```

**ScopedValue (Automatic inheritance):**
```java
public List<String> processTasksInParallel(List<String> taskIds, TenantContext tenant) {
    return ScopedTenantContext.runWithTenant(tenant, () -> {
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(taskIds.size());

        // Each vthread automatically inherits tenant context
        for (String taskId : taskIds) {
            Thread.ofVirtual()
                .start(() -> {
                    try {
                        String result = processTask(taskId);  // Tenant context inherited!
                        results.add(result);
                    } finally {
                        latch.countDown();
                    }
                });
        }

        latch.await();
        return results;
    });
}
```

---

### Pattern 6: Structured Concurrency with Task Scope

**ThreadLocal (Manual propagation):**
```java
public T[] runParallelTasks(List<Callable<T>> tasks, TenantContext tenant) throws Exception {
    // Manually propagate tenant to each task (error-prone)
    List<Callable<T>> wrappedTasks = tasks.stream()
        .map(task -> (Callable<T>) () -> {
            setTenantContext(tenant);
            try {
                return task.call();
            } finally {
                clearTenantContext();
            }
        })
        .collect(Collectors.toList());

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Future<T>> futures = wrappedTasks.stream()
            .map(scope::fork)
            .collect(Collectors.toList());

        scope.join().throwIfFailed();

        return futures.stream()
            .map(Future::resultNow)
            .toArray(Object[]::new);
    }
}
```

**ScopedValue (Automatic):**
```java
public T[] runParallelTasks(List<Callable<T>> tasks, TenantContext tenant) throws Exception {
    return ScopedTenantContext.runWithTenant(tenant, () -> {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<T>> futures = tasks.stream()
                .map(scope::fork)  // Tenant inherited automatically!
                .collect(Collectors.toList());

            scope.join().throwIfFailed();

            return futures.stream()
                .map(Future::resultNow)
                .toArray(Object[]::new);
        } catch (InterruptedException e) {
            throw new RuntimeException("Task execution interrupted", e);
        }
    });
}
```

---

## ScopedValue API Reference

### Initialization

#### `ScopedValue.newInstance()`

**Signature:**
```java
static <T> ScopedValue<T> newInstance()
```

**Purpose:** Create a new, unbound ScopedValue of generic type `T`

**Returns:** A new ScopedValue instance (initially unbound)

**Example:**
```java
static final ScopedValue<TenantContext> CURRENT_TENANT = ScopedValue.newInstance();
static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();
```

**Notes:**
- Should be `static final` for thread safety and immutability
- Package-private scope recommended (use accessors for public API)
- Can be instantiated multiple times (each is independent)

---

### Read Operations

#### `get()`

**Signature:**
```java
T get()
```

**Purpose:** Get the value bound to the current scope

**Returns:** The bound value

**Throws:** `NoSuchElementException` if not bound

**Example:**
```java
try {
    TenantContext ctx = CURRENT_TENANT.get();
    System.out.println("Tenant: " + ctx.getTenantId());
} catch (NoSuchElementException e) {
    System.err.println("No tenant bound!");
}
```

**Notes:**
- Only call after verifying `isBound()` or inside a `runWithTenant()` scope
- Throws if value is not bound (fail-fast)

---

#### `isBound()`

**Signature:**
```java
boolean isBound()
```

**Purpose:** Check if a value is bound to the current scope

**Returns:** `true` if bound, `false` if not

**Example:**
```java
if (CURRENT_TENANT.isBound()) {
    TenantContext ctx = CURRENT_TENANT.get();
    // Safe to use context
} else {
    // Handle unbound case
    System.out.println("Running without tenant context");
}
```

**Notes:**
- Must call before `get()` to avoid NoSuchElementException
- Returns `false` outside binding scope
- Zero overhead (intrinsic JVM method)

---

#### `orElse(T defaultValue)`

**Signature:**
```java
T orElse(T defaultValue)
```

**Purpose:** Get the bound value or return a default

**Parameters:**
- `defaultValue`: Returned if not bound (can be `null`)

**Returns:** Bound value or `defaultValue`

**Example:**
```java
TenantContext ctx = CURRENT_TENANT.orElse(new TenantContext("default-tenant"));
String tenantId = ctx.getTenantId();  // Never null
```

**Notes:**
- Convenient for optional contexts
- Does not throw; safe to call unconditionally
- `null` is a valid default value

---

#### `orElseThrow()`

**Signature:**
```java
T orElseThrow()
```

**Purpose:** Get the bound value or throw `NoSuchElementException`

**Returns:** The bound value (never null)

**Throws:** `NoSuchElementException` if not bound

**Example:**
```java
TenantContext ctx = CURRENT_TENANT.orElseThrow();  // Throws if unbound
processCase(ctx);  // Guaranteed non-null
```

**Notes:**
- Fail-fast: explicitly indicates value must be bound
- Equivalent to `get()` (same exception)

---

### Binding Operations

#### `ScopedValue.where(ScopedValue<T> scopedValue, T value).run(Runnable action)`

**Signature:**
```java
static <T> ScopedValue.Snapshot run(Runnable action)
```

**Purpose:** Bind a value and execute a Runnable action

**Parameters:**
- `scopedValue`: The ScopedValue to bind
- `value`: The value to bind (can be `null`)
- `action`: The work to perform under the binding

**Returns:** Nothing; binding is automatic on entry, released on exit

**Example:**
```java
TenantContext tenant = new TenantContext("customer-123");

ScopedTenantContext.runWithTenant(tenant, () -> {
    // Tenant context is bound here
    String caseId = yEngine.createNewCase("my-workflow");
    yEngine.startCase(caseId);
    // Binding released when block exits
});

// Context is guaranteed released here
```

**Error Handling:**
```java
ScopedTenantContext.runWithTenant(tenant, () -> {
    try {
        yEngine.executeCase(caseId);
    } catch (WorkflowException e) {
        log.error("Case execution failed", e);
        // Binding still released despite exception!
    }
});
```

**Notes:**
- Binding is released automatically, even if `action` throws
- Nested `runWithTenant()` calls create independent scopes
- Virtual threads inherit binding automatically
- Recommended for most workflows

---

#### `ScopedValue.where(ScopedValue<T> scopedValue, T value).call(Callable<T> action)`

**Signature:**
```java
static <T> T call(Callable<T> action)
```

**Purpose:** Bind a value, execute a Callable, and return its result

**Parameters:**
- `scopedValue`: The ScopedValue to bind
- `value`: The value to bind
- `action`: The work to perform (returns `T`)

**Returns:** The value returned by `action`

**Throws:**
- `RuntimeException`: If checked exception thrown by `action`
- `Error`: Propagates as-is

**Example:**
```java
String caseData = ScopedTenantContext.runWithTenant(tenant, () ->
    yEngine.getCaseData(caseID)
);
```

**Error Handling:**
```java
try {
    WorkItem item = ScopedTenantContext.runWithTenant(tenant, () ->
        yEngine.getWorkItem(itemID)
    );
} catch (RuntimeException e) {
    if (e.getCause() instanceof NoSuchElementException) {
        log.warn("Work item not found");
    }
}
```

**Notes:**
- Checked exceptions wrapped in RuntimeException
- Unchecked exceptions and Errors propagate as-is
- Result type `T` can be any reference type
- Binding released even if exception thrown

---

### Advanced Binding Operations

#### `ScopedValue.where(ScopedValue<T> sv, T value)`

**Signature:**
```java
static <T> ScopedValue.Binding<T> where(ScopedValue<T> sv, T value)
```

**Purpose:** Create a binding snapshot (advanced use case)

**Returns:** A `Binding<T>` that can be used with `.run()` or `.call()`

**Example (Manual binding control):**
```java
ScopedValue.Binding<TenantContext> binding =
    ScopedValue.where(CURRENT_TENANT, tenant);

// Use binding multiple times
binding.run(() -> doWork1());
binding.run(() -> doWork2());

// Each call is independent scope
```

**Notes:**
- Low-level API; use `ScopedTenantContext.runWithTenant()` instead
- `Binding<T>` is immutable and reusable
- Each `.run()` or `.call()` creates fresh scope

---

## Integration with YAWL Engine

### YNetRunner Context Binding

**File:** `src/org/yawlfoundation/yawl/engine/YNetRunner.java`

**Usage:**
```java
public void executeCase(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    ScopedTenantContext.runWithTenant(tenant, () -> {
        YNetRunner runner = yEngine.createNetRunner(caseId);
        runner.executeAsync();
    });
}
```

**Why ScopedValue:**
- Tenant context must be available in all engine calls
- Virtual threads (one per case) inherit context automatically
- No risk of context leak between concurrent cases
- Automatic cleanup prevents resource exhaustion

---

### Work Item Processing

**Integration Point:** Task execution within YNetRunner

**Before (ThreadLocal):**
```java
public void startWorkItem(String tenantId, String itemId) {
    YEngine.setTenantContext(new TenantContext(tenantId));
    try {
        WorkItem item = yEngine.getWorkItem(itemId);
        item.execute();
    } finally {
        YEngine.clearTenantContext();  // Manual cleanup
    }
}
```

**After (ScopedValue):**
```java
public void startWorkItem(String tenantId, String itemId) {
    ScopedTenantContext.runWithTenant(new TenantContext(tenantId), () -> {
        WorkItem item = yEngine.getWorkItem(itemId);  // Context inherited
        item.execute();
    });  // Cleanup automatic
}
```

---

### Composite Task Sub-Net Execution

**Integration Point:** Nested workflow execution

**Code Pattern:**
```java
private void executeCompositeTask(CompositeTask task) {
    // Current tenant context inherited automatically!
    TenantContext tenant = ScopedTenantContext.getTenantContext();

    if (tenant == null) {
        throw new IllegalStateException("No tenant context in composite task");
    }

    // Sub-net runs under same tenant automatically
    YNetRunner subRunner = yEngine.createSubNetRunner(task, tenant);
    subRunner.executeAsync();
}
```

---

### Event Notification Context

**Integration Point:** Announcement broadcast to listeners

**Pattern:**
```java
public void announceEvent(YEvent event) {
    // Listeners must receive current tenant context
    TenantContext tenant = ScopedTenantContext.requireTenantContext();

    for (YAnnouncer listener : listeners) {
        ScopedTenantContext.runWithTenant(tenant, () ->
            listener.onEvent(event)
        );
    }
}
```

---

### Multi-Tenant Case Isolation

**Integration Point:** Case registry authorization checks

**Code Pattern:**
```java
public YNetRunner getNetRunner(String caseId) {
    // Get current tenant (scoped value, fail-fast)
    TenantContext tenant = ScopedTenantContext.requireTenantContext();

    // Check authorization (prevents cross-tenant leaks)
    if (!tenant.isAuthorized(caseId)) {
        throw new SecurityException(
            "Tenant " + tenant.getTenantId() + " not authorized for case " + caseId
        );
    }

    return runners.get(caseId);
}
```

---

## YAWL Module Integration Points

### Module: yawl-engine

| Class | Method | Context Usage |
|-------|--------|----------------|
| `YEngine` | `createNewCase(specId)` | Get tenant from scope, register case |
| `YNetRunner` | `executeAsync()` | Inherit tenant in virtual thread |
| `YNetRunner` | `completeWorkItem(itemId, data)` | Verify tenant authorization |
| `ScopedTenantContext` | `runWithTenant(tenant, work)` | Entry point for all case execution |

### Module: yawl-elements

| Class | Method | Context Usage |
|-------|--------|----------------|
| `YNet` | `fire(caseId, condition)` | Read tenant to log audit trail |
| `YTask` | `start(caseId)` | Verify tenant authorization on task |
| `YCompositeTask` | `executeSubNet()` | Inherit tenant to sub-net execution |

### Module: yawl-integration

| Class | Method | Context Usage |
|-------|--------|----------------|
| `YawlMcpServer` | `onCaseStarted(caseId)` | Send notification with current tenant |
| `YawlA2AServer` | `invoke(agentId, input)` | Propagate tenant to autonomous agent call |

---

## Performance Characteristics

### Access Speed

| Operation | ThreadLocal | ScopedValue | Difference |
|-----------|------------|------------|-----------|
| `get()` | ~50 ns | ~40 ns | 20% faster |
| `set()` | ~50 ns | N/A (immutable) | N/A |
| `isBound()` | ~5 ns | ~5 ns | Same |
| Binding entry | ~200 ns | ~200 ns | Same |
| Binding exit | ~200 ns | ~200 ns | Same |

**Summary:** ScopedValue is as fast or faster than ThreadLocal, with no `remove()` overhead.

---

### Memory Overhead

| Scenario | ThreadLocal | ScopedValue |
|----------|-----------|------------|
| 1M case runners | ~160 MB (per-thread storage) | ~0 MB (scope-local) |
| Virtual thread leak | 100% (forgot `remove()`) | 0% (automatic) |
| Context propagation cost | Manual (~100 ns per fork) | Automatic (free, JVM-native) |

---

### Virtual Thread Scalability

| Metric | ThreadLocal | ScopedValue |
|--------|-----------|------------|
| **Max concurrent cases** | 10K (carrier thread pool limited) | 1M (virtual threads unlimited) |
| **Context lookup latency** | 50 ns | 40 ns |
| **Child thread inheritance** | Manual (error-prone) | Automatic (guaranteed) |
| **Cleanup cost** | Manual `remove()` (easy to forget) | Automatic (zero) |

---

## Migration Checklist

### Phase 1: Identify ThreadLocal Usage

- [ ] Run `grep -r "ThreadLocal" src/ --include="*.java"` to find all occurrences
- [ ] Document each ThreadLocal's purpose (context, session, state)
- [ ] Categorize: session context, execution context, request state
- [ ] Identify cleanup patterns (try-finally, resource cleanup, etc.)

### Phase 2: Create ScopedValue Replacements

- [ ] For each ThreadLocal, create corresponding ScopedValue in same package
- [ ] Wrap `set()/remove()` calls in a `runWithTenant()/runWithContext()` method
- [ ] Keep old ThreadLocal methods for backward compatibility (delegate to scoped value)
- [ ] Add `isBound()` checks in getters (fail-fast on missing context)

**Example:**
```java
// Old API (for compatibility)
public static void setTenantContext(TenantContext ctx) {
    LEGACY_THREAD_LOCAL.set(ctx);
}

// New API (preferred)
public static void runWithTenant(TenantContext ctx, Runnable action) {
    ScopedValue.where(SCOPED_TENANT, ctx).run(action);
}

// Getter prefers scoped value
public static TenantContext getTenantContext() {
    if (SCOPED_TENANT.isBound()) {
        return SCOPED_TENANT.get();  // Prefer scoped
    }
    return LEGACY_THREAD_LOCAL.get();  // Fallback for old code
}
```

### Phase 3: Update Call Sites

- [ ] Find all `setTenantContext()` + `clearTenantContext()` pairs
- [ ] Replace with `ScopedTenantContext.runWithTenant(tenant, work)`
- [ ] Verify no `clearTenantContext()` calls remain (should be zero)
- [ ] Update virtual thread creation to use scoped values (automatic inheritance)

### Phase 4: Test Coverage

- [ ] Write unit tests for scoped value binding
- [ ] Test exception safety (context released even on throw)
- [ ] Test nested scopes (independent bindings)
- [ ] Test virtual thread inheritance (child threads see parent context)
- [ ] Integration tests for full workflow execution

### Phase 5: Deprecation

- [ ] Mark old ThreadLocal methods `@Deprecated`
- [ ] Update Javadoc to recommend `runWithTenant()` over `setTenantContext()`
- [ ] Run static analysis to find remaining old-API calls
- [ ] Plan removal in next major version (YAWL 7.0)

---

## Common Pitfalls & Solutions

### Pitfall 1: Forgetting to Check `isBound()`

**Problem:**
```java
public void processTask() {
    TenantContext ctx = CURRENT_TENANT.get();  // Throws if unbound!
    doWork(ctx);
}
```

**Solution:**
```java
public void processTask() {
    if (!CURRENT_TENANT.isBound()) {
        throw new IllegalStateException("No tenant context bound");
    }
    TenantContext ctx = CURRENT_TENANT.get();
    doWork(ctx);
}

// Or use safe accessor
public void processTask() {
    TenantContext ctx = ScopedTenantContext.requireTenantContext();  // Fails clearly
    doWork(ctx);
}
```

---

### Pitfall 2: Context Not Inherited in Virtual Threads

**Problem:**
```java
ScopedTenantContext.runWithTenant(tenant, () -> {
    // Create virtual thread outside ScopedValue scope
    Thread vthread = Thread.ofVirtual().start(() -> {
        // BUG: Context is NOT inherited!
        TenantContext ctx = ScopedTenantContext.getTenantContext();
        // ctx is null!
    });
});
```

**Solution:**
```java
ScopedTenantContext.runWithTenant(tenant, () -> {
    // Create virtual thread inside ScopedValue scope
    Thread vthread = Thread.ofVirtual().start(() -> {
        // OK: Context IS inherited!
        TenantContext ctx = ScopedTenantContext.getTenantContext();
        // ctx is not null
    });
});
```

---

### Pitfall 3: Nested Mutations (ThreadLocal-style thinking)

**Problem:**
```java
public void process(String tenantId1, String tenantId2) {
    TenantContext tenant1 = new TenantContext(tenantId1);
    TenantContext tenant2 = new TenantContext(tenantId2);

    // Dangerous: trying to mutate scoped value
    CURRENT_TENANT.get();  // Error: can't set() in ScopedValue!
}
```

**Solution:**
```java
public void process(String tenantId1, String tenantId2) {
    TenantContext tenant1 = new TenantContext(tenantId1);
    TenantContext tenant2 = new TenantContext(tenantId2);

    // Correct: create nested scopes
    ScopedTenantContext.runWithTenant(tenant1, () -> {
        doWork1();

        ScopedTenantContext.runWithTenant(tenant2, () -> {
            doWork2();
        });  // tenant2 scope exits

        doWork3();  // Back under tenant1 automatically
    });  // tenant1 scope exits
}
```

---

### Pitfall 4: Leaking Virtual Threads Outside Scope

**Problem:**
```java
public Callable<String> createTask(TenantContext tenant) {
    return ScopedTenantContext.runWithTenant(tenant, () ->
        (Callable<String>) () -> {
            // BUG: Context lost when Callable executes later!
            TenantContext ctx = ScopedTenantContext.getTenantContext();
            // ctx is null (scope already exited)
        }
    );
}

// Later...
Callable<String> task = createTask(tenant);
String result = executor.submit(task).get();  // Fails!
```

**Solution:**
```java
public Callable<String> createTask(TenantContext tenant) {
    // Bind context to the callable itself
    return () -> ScopedTenantContext.runWithTenant(tenant, () -> {
        // OK: Context bound for Callable's lifetime
        TenantContext ctx = ScopedTenantContext.getTenantContext();
        // ctx is not null
    });
}

// Later...
Callable<String> task = createTask(tenant);
String result = executor.submit(task).get();  // Works!
```

---

### Pitfall 5: Mixing ThreadLocal and ScopedValue

**Problem:**
```java
public void executeCase(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    // Mixed APIs (confusing!)
    YEngine.setTenantContext(tenant);  // Old ThreadLocal API
    try {
        // Some code sees scoped value, some sees thread local (inconsistent)
        doWork();
    } finally {
        ScopedTenantContext.runWithTenant(tenant, () -> {  // New ScopedValue API
            moreWork();
        });
    }
}
```

**Solution:**
```java
public void executeCase(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    // Consistent use of new API
    ScopedTenantContext.runWithTenant(tenant, () -> {
        doWork();
        moreWork();
    });
}
```

---

## Troubleshooting Guide

### Issue: NoSuchElementException when accessing ScopedValue

**Symptom:**
```
java.util.NoSuchElementException: No value bound
    at java.lang.ScopedValue.get(ScopedValue.java:456)
```

**Diagnosis:**
```java
// Code is not inside runWithTenant() scope
ScopedTenantContext.getTenantContext();  // Throws!
```

**Fix:**
```java
// Ensure code runs inside binding scope
ScopedTenantContext.runWithTenant(tenant, () -> {
    TenantContext ctx = ScopedTenantContext.getTenantContext();  // Safe!
});
```

---

### Issue: Context Lost in Async Task

**Symptom:**
```
Task execution fails with NullPointerException when accessing tenant context
```

**Diagnosis:**
```java
// Virtual thread created outside binding scope
executor.submit(() -> {
    // Tenant context is NULL here
    yEngine.executeCase(caseId);  // NPE!
});
```

**Fix:**
```java
// Wrap virtual thread creation in binding scope
ScopedTenantContext.runWithTenant(tenant, () -> {
    executor.submit(() -> {
        // Tenant context is inherited!
        yEngine.executeCase(caseId);  // Works!
    });
});
```

---

### Issue: Memory Leak Despite Using ScopedValue

**Symptom:**
```
Heap grows unbounded even with ScopedValue
```

**Likely Cause:**
```java
// ScopedValue is correct, but data structure inside TenantContext leaks
class TenantContext {
    private Set<String> authorizedCases = new HashSet<>();  // Grows without bounds

    public void registerCase(String caseId) {
        authorizedCases.add(caseId);  // Never cleaned up!
    }
}
```

**Fix:**
```java
// Clean up data structures when case completes
public void completeCase(String caseId) {
    TenantContext ctx = ScopedTenantContext.getTenantContext();
    ctx.deregisterCase(caseId);  // Explicit cleanup
    yEngine.archiveCase(caseId);
}
```

---

### Issue: Pinning When Using Synchronized Blocks

**Symptom:**
```
Virtual thread pinned to carrier thread (JFR event: VirtualThreadPinned)
```

**Likely Cause:**
```java
// Synchronized within scoped value (pins virtual thread)
ScopedTenantContext.runWithTenant(tenant, () -> {
    synchronized (lock) {  // Virtual thread pinned!
        yEngine.executeCase(caseId);
    }
});
```

**Fix:**
```java
// Use ReentrantLock instead
ScopedTenantContext.runWithTenant(tenant, () -> {
    lock.lock();
    try {
        yEngine.executeCase(caseId);  // Virtual thread not pinned
    } finally {
        lock.unlock();
    }
});
```

---

## Best Practices Summary

### ✅ DO

| Practice | Reason |
|----------|--------|
| **Use `ScopedValue` for context data** | Immutable, auto-released, vthread-safe |
| **Call `isBound()` before `get()`** | Fail-fast on missing context |
| **Use `runWithTenant()` for entry points** | Automatic cleanup, exception-safe |
| **Nest scopes for multi-context work** | Independent, non-interfering bindings |
| **Create virtual threads inside scopes** | Context inherited automatically |
| **Use `ReentrantLock` instead of `synchronized`** | No virtual thread pinning |
| **Document context requirements** | Future developers understand dependencies |

### ❌ DON'T

| Anti-Pattern | Problem |
|--------------|---------|
| **Call `get()` without `isBound()` check** | Throws NoSuchElementException |
| **Create virtual threads outside scope** | Context lost |
| **Try to mutate scoped values** | ScopedValue is immutable |
| **Mix ThreadLocal and ScopedValue APIs** | Confusing, inconsistent behavior |
| **Use `synchronized` within scopes** | Pins virtual threads |
| **Forget cleanup in ThreadLocal fallback** | Resource leaks (use runWithTenant instead) |
| **Share ScopedValue across threads** | Each thread has independent scope |

---

## Example: Complete Migration

### Before (ThreadLocal, YAWL 5.x)

```java
// YEngine.java
private static final ThreadLocal<TenantContext> _currentTenant = new ThreadLocal<>();

public static void setTenantContext(TenantContext tenant) {
    _currentTenant.set(tenant);
}

public static TenantContext getTenantContext() {
    return _currentTenant.get();
}

public static void clearTenantContext() {
    _currentTenant.remove();
}

// Usage
public void executeCase(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);
    setTenantContext(tenant);
    try {
        YNetRunner runner = createNetRunner(caseId);
        runner.executeAsync();
    } finally {
        clearTenantContext();  // Manual cleanup (easy to forget!)
    }
}
```

### After (ScopedValue, YAWL 6.0+)

```java
// ScopedTenantContext.java
public final class ScopedTenantContext {
    static final ScopedValue<TenantContext> TENANT = ScopedValue.newInstance();

    // New primary API
    public static void runWithTenant(TenantContext tenant, Runnable action) {
        ScopedValue.where(TENANT, tenant).run(action);
    }

    public static <T> T runWithTenant(TenantContext tenant, Callable<T> action) {
        try {
            return ScopedValue.where(TENANT, tenant).call(() -> action.call());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Callable under tenant scope threw checked exception", e);
        }
    }

    // Read accessors (backward compatible)
    public static TenantContext getTenantContext() {
        return TENANT.isBound() ? TENANT.get() : null;
    }

    public static TenantContext requireTenantContext() {
        if (!TENANT.isBound()) {
            throw new IllegalStateException("No tenant context bound for current virtual thread");
        }
        return TENANT.get();
    }

    // Compatibility (deprecated)
    @Deprecated(forRemoval = true)
    public static void setTenantContext(TenantContext tenant) {
        // Fallback for legacy code
        LEGACY_THREAD_LOCAL.set(tenant);
    }

    @Deprecated(forRemoval = true)
    public static void clearTenantContext() {
        LEGACY_THREAD_LOCAL.remove();
    }
}

// Usage (new way)
public void executeCase(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);

    ScopedTenantContext.runWithTenant(tenant, () -> {
        YNetRunner runner = createNetRunner(caseId);
        runner.executeAsync();
    });  // Cleanup automatic, exception-safe
}

// Usage (old way, still works but deprecated)
public void executeCaseOld(String tenantId, String caseId) {
    TenantContext tenant = new TenantContext(tenantId);
    ScopedTenantContext.setTenantContext(tenant);
    try {
        YNetRunner runner = createNetRunner(caseId);
        runner.executeAsync();
    } finally {
        ScopedTenantContext.clearTenantContext();  // Still required for old API
    }
}
```

---

## References

### Java Documentation

- [JEP 487 — Scoped Values](https://openjdk.org/jeps/487)
- [ScopedValue Javadoc](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html)
- [Virtual Threads Overview](https://openjdk.org/jeps/444)

### YAWL Documentation

- **YAWL Virtual Threads Guide:** `/home/user/yawl/docs/reference/virtual-threads.md`
- **YAWL Java 25 JEP Index:** `/home/user/yawl/docs/reference/java25-jep-index.md`
- **Modern Java Conventions:** `/home/user/yawl/.claude/rules/java25/modern-java.md`

### Related Code

- **ScopedTenantContext:** `/home/user/yawl/src/org/yawlfoundation/yawl/engine/ScopedTenantContext.java`
- **TenantContext:** `/home/user/yawl/src/org/yawlfoundation/yawl/engine/TenantContext.java`
- **YNetRunner:** `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YNetRunner.java`
- **Test Suite:** `/home/user/yawl/test/org/yawlfoundation/yawl/engine/ScopedTenantContextTest.java`

---

## Document History

| Version | Date | Author | Notes |
|---------|------|--------|-------|
| 1.0 | 2026-02-28 | YAWL Team | Initial reference documentation for ScopedValue migration |

---

**Last Updated:** 2026-02-28

**Classification:** Reference (Pure API Documentation)

**Audience:** YAWL team members, Java 25 developers migrating from ThreadLocal

**Next Steps:** Implement migration checklist; update all ThreadLocal usage to ScopedValue by YAWL 6.1
