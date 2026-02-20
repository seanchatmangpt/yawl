# Java 25 Developer Guide for YAWL v6.0.0

**Version:** 1.0 | **Date:** February 2026 | **Target Audience:** YAWL Developers & Extension Authors

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Records in Depth](#records-in-depth)
3. [Sealed Classes & Pattern Matching](#sealed-classes--pattern-matching)
4. [Virtual Threads Best Practices](#virtual-threads-best-practices)
5. [Structured Concurrency Patterns](#structured-concurrency-patterns)
6. [ScopedValue for Context](#scopedvalue-for-context)
7. [Common Pitfalls](#common-pitfalls)
8. [Performance Tips](#performance-tips)

---

## Quick Start

### Enable Compact Object Headers (5 minutes)

```bash
# In application.yml or Dockerfile
JAVA_OPTS="-XX:+UseCompactObjectHeaders"
```

**Result**: +5-10% throughput, no code changes.

---

### Use Records for Data Classes (Today)

**Before:**
```java
public class YCaseEvent {
    private final Instant timestamp;
    private final String caseID;
    private final String type;

    public YCaseEvent(Instant timestamp, String caseID, String type) {
        this.timestamp = timestamp;
        this.caseID = caseID;
        this.type = type;
    }

    @Override public boolean equals(Object o) { /* ... */ }
    @Override public int hashCode() { /* ... */ }
    @Override public String toString() { /* ... */ }

    // Getters
}
```

**After:**
```java
public record YCaseEvent(
    Instant timestamp,
    String caseID,
    String type
) {}
```

**Benefit**: 80% less boilerplate; immutable by default.

---

### Fork Virtual Threads (Quick Change)

**Before:**
```java
new Thread(() -> discoverWorkItems()).start();  // 2MB each
```

**After:**
```java
Thread.ofVirtual()
    .name("yawl-discovery")
    .start(this::discoverWorkItems);  // few KB each
```

**Benefit**: 1000 agents = 2GB → ~1MB memory.

---

## Records in Depth

### 1. Basic Records

```java
// Syntax
public record WorkItem(
    String id,
    String taskID,
    Instant enabledTime,
    @Nullable Instant startTime
) {}

// Automatically generated:
// - Constructor: WorkItem(id, taskID, enabledTime, startTime)
// - Accessors: id(), taskID(), enabledTime(), startTime()
// - equals(): Field-by-field comparison
// - hashCode(): Based on all fields
// - toString(): "WorkItem[id=..., taskID=..., ...]"
```

### 2. Compact Constructor (Validation)

```java
public record WorkItem(
    String id,
    String taskID,
    Instant enabledTime,
    @Nullable Instant startTime,
    @Nullable Instant completionTime
) {
    // Compact constructor: validates before field assignment
    public WorkItem {
        Objects.requireNonNull(id);
        Objects.requireNonNull(taskID);
        Objects.requireNonNull(enabledTime);

        // Temporal validation
        if (startTime != null && startTime.isBefore(enabledTime)) {
            throw new IllegalArgumentException("startTime after enabledTime");
        }
        if (completionTime != null && completionTime.isBefore(enabledTime)) {
            throw new IllegalArgumentException("completionTime after enabledTime");
        }
        if (startTime != null && completionTime != null &&
            completionTime.isBefore(startTime)) {
            throw new IllegalArgumentException("completionTime after startTime");
        }
    }
}
```

**Usage:**
```java
// Valid
new WorkItem("1", "task1", Instant.now(), null, null);

// Invalid - throws IllegalArgumentException
new WorkItem("1", "task1", Instant.now(), Instant.now().minusSeconds(10), null);
```

### 3. Custom Accessor Methods

```java
public record WorkItem(
    String id,
    String taskID,
    Instant enabledTime,
    @Nullable Instant startTime
) {
    // Custom method beyond auto-generated accessors
    public long getAgeSeconds() {
        return ChronoUnit.SECONDS.between(enabledTime, Instant.now());
    }

    public boolean isOverdue() {
        return getAgeSeconds() > 3600;  // > 1 hour
    }

    // Derived data (not stored)
    public String getStatus() {
        return startTime == null ? "Enabled" : "Executing";
    }
}
```

### 4. Records with Sealed Interfaces

```java
// Sealed interface (all events)
public sealed interface YWorkflowEvent
    permits YCaseEvent, YWorkItemEvent, YTimerEvent {}

// Concrete implementations
public record YCaseEvent(
    Instant timestamp,
    String caseID,
    String eventType
) implements YWorkflowEvent {}

public record YWorkItemEvent(
    Instant timestamp,
    String caseID,
    String workItemID,
    String eventType
) implements YWorkflowEvent {}

public record YTimerEvent(
    Instant timestamp,
    String taskID,
    String caseID
) implements YWorkflowEvent {}

// Pattern matching
String description = switch (event) {
    case YCaseEvent e -> "Case: " + e.caseID();
    case YWorkItemEvent e -> "WorkItem: " + e.workItemID();
    case YTimerEvent e -> "Timer: " + e.taskID();
};
```

### 5. Records with Nesting

```java
public record Case(
    String id,
    YSpecificationID specID,
    List<WorkItem> items,
    CaseMetadata metadata
) {}

public record CaseMetadata(
    LocalDateTime launchedAt,
    String launchedBy,
    @Nullable String parentCaseID
) {}

// Usage
Case c = new Case(
    "case-1",
    new YSpecificationID("Process", "1.0"),
    List.of(/* ... */),
    new CaseMetadata(LocalDateTime.now(), "user123", null)
);

String launchedBy = c.metadata().launchedBy();
```

---

## Sealed Classes & Pattern Matching

### 1. Sealed Interfaces

```java
// Define domain hierarchy
public sealed interface WorkItemState
    permits EnabledState, FiredState, ExecutingState,
            SuspendedState, CompleteState, FailedState {}

// Implementations (can be records)
public record EnabledState(Instant enabledAt) implements WorkItemState {}
public record ExecutingState(Instant startedAt, String participant) implements WorkItemState {}
public record CompleteState(Instant completedAt) implements WorkItemState {}
public record FailedState(Instant failedAt, String reason) implements WorkItemState {}
```

### 2. Exhaustive Pattern Matching

```java
// Compiler verifies all cases covered
String status = workItem.getState() switch {
    EnabledState _ -> "Waiting",
    ExecutingState e -> "Executing: " + e.participant(),
    CompleteState _ -> "Done",
    FailedState f -> "Failed: " + f.reason(),
    // Missing a case? COMPILER ERROR!
};

// With record destructuring
String detail = event switch {
    case YWorkItemEvent(_, var caseID, var itemID, "COMPLETED") ->
        "Item " + itemID + " completed in case " + caseID,
    case YWorkItemEvent(_, var caseID, _, _) ->
        "Item event in case " + caseID,
    default -> "Unknown event"
};
```

### 3. Sealed Hierarchy with Subtypes

```java
// Base: sealed interface
public sealed interface YElement
    permits YTask, YFlow, YCondition, YDecomposition {}

// Intermediate: can be sealed or non-sealed
public sealed interface YNetElement extends YElement
    permits YTask, YCondition {}

// Leaf: non-sealed (no more extensions)
public non-sealed class YTask implements YNetElement {
    private final String id;
    private final String name;
    // ...
}

// Another leaf
public non-sealed class YFlow implements YElement {
    private final YElement source;
    private final YElement target;
    // ...
}
```

### 4. Pattern Matching with Guards

```java
String description = event switch {
    case YWorkItemEvent(_, var caseID, _, "COMPLETED") when isUrgent(caseID) ->
        "URGENT: Item completed",
    case YWorkItemEvent(_, var caseID, _, "COMPLETED") ->
        "Item completed in case: " + caseID,
    case YWorkItemEvent(_, var caseID, _, "FAILED") ->
        "Item failed in case: " + caseID,
    default -> "Event: " + event
};
```

---

## Virtual Threads Best Practices

### 1. Naming Threads for Debugging

```java
// Good: Descriptive name
Thread.ofVirtual()
    .name("yawl-case-" + caseID)
    .start(() -> executeCase(caseID));

// Better: Includes context
Thread.ofVirtual()
    .name("yawl-agent-" + agentID + "-discovery")
    .start(this::runDiscoveryLoop);

// In thread dumps:
// "yawl-case-case-123" #1234 virtual
// "yawl-agent-agent-1-discovery" #1235 virtual
```

### 2. Avoiding Synchronized Blocks

**Problem: Pins virtual threads**
```java
// DON'T: synchronized blocks with I/O
public synchronized void saveWorkItem(YWorkItem item) {
    // If DB call blocks, carrier thread is blocked!
    db.save(item);
}

// DO: Use ReentrantLock, keep critical section short
private final ReentrantLock lock = new ReentrantLock();

public void saveWorkItem(YWorkItem item) {
    lock.lock();
    try {
        // Quick: validate, update in-memory state
        item.validate();
        registry.put(item.getId(), item);
    } finally {
        lock.unlock();
    }
    // I/O outside lock
    db.save(item);  // Carrier can handle other threads
}
```

### 3. Database Connection Pooling

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # Pool sized for CPU cores, NOT threads
      maximum-pool-size: 10      # Typically 2-3x CPU cores
      minimum-idle: 5
      connection-timeout: 30s
      idle-timeout: 10m
      max-lifetime: 30m

# Why? Virtual threads wait on connection efficiently
# 10,000 concurrent virtual threads share 10 connections
# When a virtual thread waits for a connection, it yields the carrier
# Carrier continues running other virtual threads
```

### 4. Thread Sleep and I/O

```java
// Virtual threads efficiently handle blocking I/O
private void discoveryLoop() {
    while (running.get()) {
        try {
            // Long I/O: No problem
            Set<YWorkItem> items = engine.getAvailableWorkItems();  // Network call

            // Process them...

            // Sleep: No problem
            Thread.sleep(5000);  // 5 second poll interval
            // Carrier thread goes to other tasks while this virtual thread sleeps
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
    }
}
```

### 5. Exception Handling in Threads

```java
// Virtual thread uncaught exceptions are logged
Thread.ofVirtual()
    .name("yawl-task")
    .uncaughtExceptionHandler((t, e) -> {
        log.error("Thread {} failed: {}", t.getName(), e.getMessage(), e);
        // Notify monitoring system
        metrics.recordThreadFailure(t.getName());
    })
    .start(() -> {
        try {
            doWork();
        } catch (Exception e) {
            // Handle or re-throw
            throw new YDataStateException("Work failed", e);
        }
    });
```

---

## Structured Concurrency Patterns

### 1. Fan-Out with ShutdownOnFailure

```java
// Process 50 items in parallel; if one fails, cancel rest
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var tasks = items.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();

    scope.join();           // Wait for all
    scope.throwIfFailed();  // If any failed, throw first exception

    return tasks.stream()
        .map(Subtask::resultNow)
        .toList();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // scope automatically cancels remaining tasks
}
```

### 2. Timeout Handling

```java
// Wait for all tasks, but timeout if too long
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var tasks = items.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();

    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    boolean completed = scope.joinUntil(deadline);

    if (!completed) {
        // Deadline exceeded; scope cancels remaining tasks
        throw new TimeoutException("Processing took > 30s");
    }

    scope.throwIfFailed();
    return tasks.stream().map(Subtask::resultNow).toList();
}
```

### 3. Error Collection

```java
// Collect all errors, not just first
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var tasks = items.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();

    scope.join();

    // Check for errors
    List<Throwable> errors = tasks.stream()
        .filter(t -> !t.isDone())
        .map(Subtask::exception)
        .filter(Objects::nonNull)
        .toList();

    if (!errors.isEmpty()) {
        throw new CompositeException("Multiple failures", errors);
    }

    return tasks.stream().map(Subtask::resultNow).toList();
}
```

---

## ScopedValue for Context

### 1. Define Context

```java
public class WorkflowContext {
    public static final ScopedValue<String> CASE_ID = ScopedValue.newInstance();
    public static final ScopedValue<SecurityContext> SECURITY = ScopedValue.newInstance();
    public static final ScopedValue<AuditLog> AUDIT = ScopedValue.newInstance();

    private WorkflowContext() {}
}
```

### 2. Set Context

```java
public void launchCase(String caseID, SecurityContext sec) {
    ScopedValue.where(WorkflowContext.CASE_ID, caseID)
        .where(WorkflowContext.SECURITY, sec)
        .where(WorkflowContext.AUDIT, new AuditLog(caseID))
        .run(() -> {
            // caseID, sec, and audit are accessible here
            engine.executeCase(caseID);
        });
}
```

### 3. Access Context

```java
public void processWorkItem(YWorkItem item) {
    String caseID = WorkflowContext.CASE_ID.get();
    SecurityContext sec = WorkflowContext.SECURITY.get();
    AuditLog audit = WorkflowContext.AUDIT.get();

    // Do work...

    audit.log("Item processed: " + item.getId());
}
```

### 4. Virtual Thread Inheritance

```java
ScopedValue.where(WorkflowContext.CASE_ID, "case-1")
    .run(() -> {
        // Fork virtual threads
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var tasks = items.stream()
                .map(item -> scope.fork(() -> {
                    // caseID is automatically available here!
                    String caseID = WorkflowContext.CASE_ID.get();
                    processItem(item);
                }))
                .toList();
            scope.join();
        }
    });
```

---

## Common Pitfalls

### Pitfall 1: Forgetting Null Safety on Records

```java
// DON'T: Assume record component is never null
public record YEvent(Instant timestamp, @Nullable String caseID) {}

YEvent event = new YEvent(Instant.now(), null);
String caseID = event.caseID();
if (caseID.isEmpty()) { /* ... */ }  // NullPointerException!

// DO: Use @Nullable annotation and check
public record YEvent(Instant timestamp, @Nullable String caseID) {}

YEvent event = new YEvent(Instant.now(), null);
if (event.caseID() != null && event.caseID().isEmpty()) {
    // Safe
}
```

### Pitfall 2: Mutable Record Components

```java
// DON'T: Store mutable objects in records
public record Case(
    String id,
    List<YWorkItem> items  // Mutable!
) {}

Case c1 = new Case("1", new ArrayList<>());
c1.items().add(item);  // Mutated!

Case c2 = new Case("1", c1.items());
c2.items().add(other);  // Also mutates c1!

// DO: Use immutable collections
public record Case(
    String id,
    List<YWorkItem> items  // Use List.copyOf()
) {
    public Case {
        items = List.copyOf(items);  // Immutable copy
    }
}
```

### Pitfall 3: Synchronized Blocks with I/O

```java
// DON'T: Long operations under lock
public synchronized void saveAndNotify(YWorkItem item) {
    db.save(item);       // 100ms network call
    notify();            // Sends event
    // Carrier thread blocked for 100+ ms!
}

// DO: Minimize critical section
private final ReentrantLock lock = new ReentrantLock();

public void saveAndNotify(YWorkItem item) {
    lock.lock();
    try {
        item.validate();
        registry.put(item.getId(), item);
    } finally {
        lock.unlock();
    }
    // I/O outside lock
    db.save(item);
    notify();
}
```

### Pitfall 4: ThreadLocal with Virtual Threads

```java
// DON'T: ThreadLocal doesn't work with virtual threads
static final ThreadLocal<String> caseID = new ThreadLocal<>();

caseID.set("case-1");
Thread.ofVirtual().start(() -> {
    String id = caseID.get();  // null! Virtual thread doesn't inherit
});

// DO: Use ScopedValue
static final ScopedValue<String> caseID = ScopedValue.newInstance();

ScopedValue.where(caseID, "case-1").run(() -> {
    Thread.ofVirtual().start(() -> {
        String id = caseID.get();  // "case-1" - inherited!
    });
});
```

### Pitfall 5: Pattern Matching Without Exhaustiveness

```java
// DON'T: Switch might miss cases
public String describe(YElement elem) {
    return switch (elem) {
        case YTask t -> "Task";
        case YFlow f -> "Flow";
        // Missing YCondition? Compiler doesn't know if sealed!
    };
}

// DO: Seal the interface
public sealed interface YElement permits YTask, YFlow, YCondition {}

public String describe(YElement elem) {
    return switch (elem) {
        case YTask t -> "Task";
        case YFlow f -> "Flow";
        case YCondition c -> "Condition";
        // Compiler error if missing a case!
    };
}
```

---

## Performance Tips

### 1. Use Parallel Builds

```bash
# In .mvn/maven.config
-T 1.5C
```

**Result**: Build time -50% (180s → 90s)

### 2. Enable Compact Object Headers

```bash
JAVA_OPTS="-XX:+UseCompactObjectHeaders"
```

**Result**: +5-10% throughput, no code change

### 3. Connection Pooling for Virtual Threads

```yaml
hikari:
  maximum-pool-size: 10    # CPU cores × 2-3, not thread count
  minimum-idle: 5
  connection-timeout: 30s
```

### 4. GC Selection

| Heap Size | GC | Flags |
|-----------|----|----|
| < 4GB | G1 (default) | (none needed) |
| 4-64GB | Shenandoah | `-XX:+UseShenandoahGC` |
| > 64GB | ZGC | `-XX:+UseZGC` |

### 5. Monitor Virtual Threads

```bash
# Detect pinning
-Djdk.tracePinnedThreads=short

# JFR recording
jcmd <pid> JFR.start filename=recording.jfr
```

---

## Testing Guide

### Unit Test with Records

```java
@Test
void testRecordConstruction() {
    YCaseEvent event = new YCaseEvent(
        Instant.now(),
        "case-1",
        YEventType.CASE_STARTED
    );

    assertEquals("case-1", event.caseID());
    assertNotNull(event.timestamp());
}

@Test
void testRecordEquality() {
    YCaseEvent event1 = new YCaseEvent(Instant.parse("2026-02-20T10:00:00Z"), "case-1", YEventType.CASE_STARTED);
    YCaseEvent event2 = new YCaseEvent(Instant.parse("2026-02-20T10:00:00Z"), "case-1", YEventType.CASE_STARTED);

    assertEquals(event1, event2);
    assertEquals(event1.hashCode(), event2.hashCode());
}
```

### Virtual Thread Testing

```java
@Test
void testVirtualThreadCreation() throws InterruptedException {
    AtomicBoolean executed = new AtomicBoolean(false);

    Thread vt = Thread.ofVirtual()
        .name("test-vt")
        .start(() -> executed.set(true));

    vt.join();
    assertTrue(executed.get());
}

@Test
void testStructuredConcurrency() throws InterruptedException, ExecutionException {
    List<String> results = Collections.synchronizedList(new ArrayList<>());

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        for (int i = 0; i < 10; i++) {
            int index = i;
            scope.fork(() -> results.add("task-" + index));
        }

        scope.join();
        scope.throwIfFailed();
    }

    assertEquals(10, results.size());
}
```

---

## References

- **Java 25 Upgrade Guide**: `JAVA25_UPGRADE_GUIDE.md`
- **ADRs 026-030**: Sealed classes, records, virtual threads, structured concurrency, scoped values
- **Official JEPs**:
  - JEP 440: Records
  - JEP 409: Sealed Classes
  - JEP 444: Virtual Threads
  - JEP 505: Structured Concurrency
  - JEP 506: Scoped Values

---

**Questions?** See related ADRs or reach out to YAWL team.
