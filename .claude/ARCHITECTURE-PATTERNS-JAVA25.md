# Java 25 Architectural Patterns for YAWL v5.2

**Date**: Feb 2026 | **Status**: Research + Implementation Guide

This document details 8 architectural patterns for Java 25 adoption in YAWL, each with specific file references to the codebase.

---

## 🧭 Navigation

**Related Documentation**:
- **[JAVA-25-FEATURES.md](JAVA-25-FEATURES.md)** - Feature overview and adoption roadmap
- **[BUILD-PERFORMANCE.md](BUILD-PERFORMANCE.md)** - Build system optimization
- **[SECURITY-CHECKLIST-JAVA25.md](SECURITY-CHECKLIST-JAVA25.md)** - Security requirements
- **[BEST-PRACTICES-2026.md](BEST-PRACTICES-2026.md)** - 12 best practices sections
- **[../CLAUDE.md](../CLAUDE.md)** - Project specification (architecture section Γ)

**In This Document**:
- 📊 [Current Architecture Assessment](#current-architecture-assessment) - YAWL v5.2 state
- 🎯 [8 Patterns](#pattern-1-virtual-threads-for-workflow-concurrency) - Each with code examples
- 📋 [Priority Matrix](#implementation-priority) - Effort vs benefit
- ✅ [Files to Update](#implementation-priority) - Specific locations in codebase

---

## Pattern 1: Virtual Threads for Workflow Concurrency

### Problem
`GenericPartyAgent.discoveryThread` uses platform threads (2MB each). With 1000 agents, memory usage explodes to 2GB+.

**Current Code**:
```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent (line 52)
private Thread discoveryThread;
private final AtomicBoolean running = new AtomicBoolean(false);
```

### Solution
Replace with named virtual threads:

```java
// Recommended replacement
private Thread discoveryThread;

@Override
public void start() {
    running.set(true);
    discoveryThread = Thread.ofVirtual()
        .name("yawl-agent-discovery-" + config.getAgentId())
        .start(this::runDiscoveryLoop);
}

@Override
public void stop() {
    running.set(false);
    // Virtual thread automatically deallocates
}
```

### Benefits
- 1000 agents: 2GB → ~1MB memory
- Unlimited horizontal scaling
- Automatic cleanup on completion

### Files to Update
- `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`
- `org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy`

### Effort: 1 day

---

## Pattern 2: Structured Concurrency for Work Item Batches

### Problem
When `DiscoveryStrategy.discoverWorkItems()` finds multiple eligible items, they're processed sequentially. If one external call hangs, all others block.

### Solution
Use `StructuredTaskScope` for fan-out with automatic cancellation:

```java
public List<WorkItem> processDiscoveredItems(List<WorkItem> discovered)
        throws InterruptedException, ExecutionException {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<WorkItem>> tasks = discovered.stream()
            .map(item -> scope.fork(() -> processWorkItem(item)))
            .toList();

        // Wait for all tasks; cancel others on first failure
        scope.join();
        scope.throwIfFailed();

        return tasks.stream()
            .map(Subtask::resultNow)
            .toList();
    }
}
```

### Benefits
- Parallel processing: 10 items in 1s instead of 10s
- Automatic cancellation: One failure stops all
- Observable: Thread dumps show parent-child relationships

### Files to Update
- `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`
- All `DiscoveryStrategy` implementations

### Effort: 2 days

---

## Pattern 3: Domain-Driven Design Alignment

### Problem
`YWorkItemStatus` is a flat 13-value enum. State transitions are distributed across `YNetRunner`, `YWorkItem`, and `YEngine`. No central state validator.

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.YWorkItemStatus
public enum YWorkItemStatus {
    statusEnabled,      // 0
    statusFired,        // 1
    statusExecuting,    // 2
    statusComplete,     // 3
    // ... 9 more ...
}
```

### Solution: Sealed State Machine

```java
// Immutable, exhaustive state hierarchy
public sealed interface WorkItemState
    permits EnabledState, FiredState, ExecutingState, SuspendedState, TerminalState {}

public record EnabledState(Instant enabledAt) implements WorkItemState {}
public record FiredState(Instant enabledAt, Instant firedAt) implements WorkItemState {}
public record ExecutingState(
    Instant enabledAt,
    Instant firedAt,
    Instant startedAt,
    String executingParticipant
) implements WorkItemState {}

public record SuspendedState(WorkItemState suspendedFrom) implements WorkItemState {}

public sealed interface TerminalState extends WorkItemState
    permits CompletedState, FailedState, CancelledState, DeadlockedState {}

public record CompletedState(Instant completedAt, WorkItemCompletion completionType)
    implements TerminalState {}
public record FailedState(Instant failedAt, String reason) implements TerminalState {}
public record CancelledState(Instant cancelledAt, CancellationReason reason)
    implements TerminalState {}
```

### Compiler-Verified Exhaustiveness

```java
// In YCaseMonitor.handleCaseEvent() (line ~500)
String status = workItem switch {
    EnabledState _ -> "Waiting",
    FiredState _ -> "Fired",
    ExecutingState e -> "Executing: " + e.executingParticipant(),
    SuspendedState s -> "Suspended",
    CompletedState _ -> "Completed",
    FailedState f -> "Failed: " + f.reason(),
    CancelledState c -> "Cancelled: " + c.reason(),
    DeadlockedState _ -> "Deadlocked",
    // Compiler error if any case missing!
};
```

### Benefits
- Compiler catches missed state transitions
- `_prevStatus` field becomes unnecessary (embedded in `SuspendedState`)
- Type-safe state queries

### Files to Update
- `org.yawlfoundation.yawl.engine.YWorkItem` (line 72: `_status` field)
- `org.yawlfoundation.yawl.engine.YWorkItemStatus` (replace enum)
- `org.yawlfoundation.yawl.engine.YNetRunner` (all state transitions)

### Effort: 3 days

---

## Pattern 4: CQRS for Interface B (Commands + Queries)

### Problem
`InterfaceBClient` mixes commands (mutations) with queries (reads). A service that only polls work items must depend on the same interface that handles case launch and item completion.

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient
public interface InterfaceBClient {
    // Commands (mutations)
    String launchCase(...);
    YWorkItem startWorkItem(...);
    void completeWorkItem(...);
    void rollbackWorkItem(...);

    // Queries (reads)
    Set<YWorkItem> getAvailableWorkItems();
    Set<YWorkItem> getAllWorkItems();
    YWorkItem getWorkItem(...);
    String getCaseData(...);
}
```

### Solution: CQRS Split

```java
// Command interface (write side)
public interface InterfaceBCommands {
    String launchCase(YSpecificationID specID, String caseParams, ...)
        throws YStateException, YDataStateException, ...;
    YWorkItem startWorkItem(YWorkItem workItem, YClient client)
        throws YStateException, YDataStateException, ...;
    void completeWorkItem(YWorkItem workItem, String data, ...)
        throws YStateException, YDataStateException, ...;
    void rollbackWorkItem(String workItemID)
        throws YStateException, YPersistenceException, ...;
}

// Query interface (read side)
public interface InterfaceBQueries {
    Set<YWorkItem> getAvailableWorkItems();
    Set<YWorkItem> getAllWorkItems();
    YWorkItem getWorkItem(String workItemID);
    String getCaseData(String caseID) throws YStateException;
    YTask getTaskDefinition(YSpecificationID specID, String taskID);
}

// Backward-compatible composite
public interface InterfaceBClient extends InterfaceBCommands, InterfaceBQueries {
    // ... legacy methods for compatibility ...
}
```

### Usage in Agent

```java
// Discovery strategy now only depends on queries
public class PollingDiscoveryStrategy implements DiscoveryStrategy {
    private InterfaceBQueries engine;  // Immutable reference to read side

    @Override
    public List<WorkItem> discoverWorkItems() {
        return engine.getAvailableWorkItems().stream()
            .filter(this::isEligible)
            .toList();
    }
}
```

### Benefits
- Separation of concerns (CQRS pattern)
- Independent scaling of read and write paths
- Clearer API contracts

### Files to Update
- `org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient` (split interface)
- `org.yawlfoundation.yawl.engine.YEngine` (implements both)
- All implementations of `DiscoveryStrategy`

### Effort: 2 days

---

## Pattern 5: Records for Event Hierarchy

### Problem
`YEvent` is mutable abstract class with post-construction setters. Multi-threaded event dispatch in `MultiThreadEventNotifier` cannot guarantee immutability.

**Current Code**:
```java
// org.yawlfoundation.yawl.stateless.listener.event.YEvent
public abstract class YEvent {
    private final Instant _timeStamp;
    private YSpecificationID _specID;      // Mutable, set after
    private YWorkItem _item;               // Mutable, set after
    private Document _dataDoc;             // Mutable, set after
    private int _engineNbr;                // Mutable, set after

    public void setSpecID(YSpecificationID specID) { this._specID = specID; }
    public void setWorkItem(YWorkItem item) { this._item = item; }
    // ... more setters ...
}
```

### Solution: Sealed Record Hierarchy

```java
// Sealed interface for all events
public sealed interface YWorkflowEvent
    permits YCaseLifecycleEvent, YWorkItemLifecycleEvent,
            YTimerEvent, YConstraintEvent, YExceptionEvent,
            YAgentServiceEvent {}

// Case events
public record YCaseLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    int engineNbr
) implements YWorkflowEvent {}

// Work item events
public record YWorkItemLifecycleEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    YWorkItem workItem,
    Document data,
    int engineNbr
) implements YWorkflowEvent {}

// Timer events
public record YTimerEvent(
    Instant timestamp,
    YEventType type,
    YIdentifier caseID,
    YSpecificationID specID,
    String taskID,
    int engineNbr
) implements YWorkflowEvent {}
```

### Thread-Safe Event Dispatch

```java
// In MultiThreadEventNotifier (already using virtual threads)
// Exhaustive pattern matching
for (YWorkflowEvent event : eventQueue) {
    switch (event) {
        case YCaseLifecycleEvent e ->
            handleCaseEvent(e.caseID(), e.type());
        case YWorkItemLifecycleEvent e ->
            handleWorkItemEvent(e.workItem(), e.type());
        case YTimerEvent e ->
            handleTimerEvent(e.taskID(), e.timestamp());
        // Compiler error if missing case
    }
}
```

### Testing Benefit: No Builders

```java
// Test setup without mock infrastructure
@Test
void testCaseStartEvent() {
    YCaseLifecycleEvent event = new YCaseLifecycleEvent(
        Instant.now(),
        YEventType.CASE_STARTED,
        new YIdentifier("test-case"),
        new YSpecificationID("Process", "1.0", "uri"),
        1
    );

    engine.announceEvent(event);

    assertTrue(monitor.getCaseState(event.caseID()).isRunning());
}
```

### Files to Update
- `org.yawlfoundation.yawl.stateless.listener.event.YEvent` (seal + records)
- `org.yawlfoundation.yawl.engine.announcement.YEngineEvent` (seal + records)
- All event listener implementations

### Effort: 3 days

---

## Pattern 6: Module System for Boundaries

### Problem
Both `org.yawlfoundation.yawl.engine.YNetRunner` and `org.yawlfoundation.yawl.stateless.engine.YNetRunner` exist with near-identical logic. The Singleton `YEngine` pattern prevents code reuse.

**Current**:
- Monolithic Maven project, no module-info.java
- Package boundaries not enforced by compiler
- Duplication across engine and stateless.engine packages

### Solution: Java 9+ Module System

```java
// module-info.java for stateless execution core
module yawl.execution.core {
    exports org.yawlfoundation.yawl.stateless;
    exports org.yawlfoundation.yawl.stateless.engine;
    exports org.yawlfoundation.yawl.stateless.elements;
    exports org.yawlfoundation.yawl.stateless.listener;
    exports org.yawlfoundation.yawl.stateless.monitor;

    requires org.jdom2;
    requires net.sf.saxon;
    requires org.apache.logging.log4j;
    requires java.persistence;
    requires org.hibernate.orm.core;
}

// module-info.java for stateful engine (depends on core)
module yawl.engine {
    requires yawl.execution.core;
    requires java.servlet;
    requires jakarta.xml.bind;

    exports org.yawlfoundation.yawl.engine;
    exports org.yawlfoundation.yawl.engine.interfce;
    exports org.yawlfoundation.yawl.engine.interfce.interfaceA;
    exports org.yawlfoundation.yawl.engine.interfce.interfaceB;

    // Prevents reflection access to internals
    // does not export org.yawlfoundation.yawl.engine.internal;
}

// module-info.java for agent framework
module yawl.agents {
    requires yawl.execution.core;
    requires yawl.engine;

    exports org.yawlfoundation.yawl.integration.autonomous;
    exports org.yawlfoundation.yawl.integration.autonomous.strategies;
}
```

### Bounded Contexts

```
Bounded Context: WorkflowDefinition
    → org.yawlfoundation.yawl.specification module
    → YSpecification, YNet, YTask, YFlow, YDecomposition

Bounded Context: CaseExecution (Core)
    → yawl.execution.core module
    → YNetRunner, YMarkings, YIdentifier, YCaseMonitor

Bounded Context: Stateful Engine
    → yawl.engine module
    → YEngine, Interfaces A/B, Persistence

Bounded Context: AgentCoordination
    → yawl.agents module
    → AutonomousAgent, DiscoveryStrategy, DecisionReasoner
```

### Benefits
- Explicit dependency graph
- Compile-time enforcement of boundaries
- `-XX:+UnlockDiagnosticVMOptions -XX:+PrintModuleNativeLookup` for runtime analysis

### Files to Create/Update
- Create `src/org/yawlfoundation/yawl/stateless/module-info.java`
- Create `src/org/yawlfoundation/yawl/engine/module-info.java`
- Create `src/org/yawlfoundation/yawl/integration/module-info.java`

### Effort: 1 week

---

## Pattern 7: Reactive Event Pipeline with Predicates

### Problem
`YAnnouncer` dispatches all events to all listeners. Listeners that filter events (e.g., "only case start events") receive all events and discard them, wasting CPU.

### Solution: Event Predicate Filtering

```java
// Event filter interface
public interface YEventFilter<E extends YWorkflowEvent> {
    boolean test(E event);

    default YEventFilter<E> and(YEventFilter<E> other) {
        return event -> this.test(event) && other.test(event);
    }

    default YEventFilter<E> or(YEventFilter<E> other) {
        return event -> this.test(event) || other.test(event);
    }
}

// Built-in filters
public class YEventFilters {
    public static YEventFilter<YCaseLifecycleEvent> caseType(YEventType type) {
        return e -> e.type() == type;
    }

    public static YEventFilter<YCaseLifecycleEvent> caseId(YIdentifier id) {
        return e -> e.caseID().equals(id);
    }

    public static YEventFilter<YWorkItemLifecycleEvent> taskId(String taskId) {
        return e -> e.workItem().getTaskID().equals(taskId);
    }
}

// Announcer accepts filters
public void addWorkItemEventListener(
        YWorkItemEventListener listener,
        YEventFilter<YWorkItemLifecycleEvent> filter) {
    _workItemListeners.put(listener, filter);
}
```

### Usage

```java
// Only receive completion events for specific case
announcer.addWorkItemEventListener(
    listener,
    YEventFilters.caseId(caseID)
        .and(YEventFilters.taskId("review"))
        .and(e -> e.type() == YEventType.ITEM_COMPLETED)
);
```

### Benefit: Reduced Event Processing

- Without filters: 1000 events/sec → all processed
- With filters: 1000 events/sec → 50 match predicate → only 50 processed
- 95% reduction in listener callback overhead

### Files to Update
- `org.yawlfoundation.yawl.stateless.listener.YAnnouncer`
- All listener registrations

### Effort: 2 days

---

## Pattern 8: Constructor Injection (Dependency Inversion)

### Problem
`YEngine.getInstance()` uses Singleton anti-pattern with static field. `YNetRunner` calls `YEngine.getInstance()` in its default constructor (circular dependency).

**Current Code**:
```java
// org.yawlfoundation.yawl.engine.YEngine (line 81)
protected static YEngine _thisInstance;

public static YEngine getInstance(boolean persisting) throws YPersistenceException {
    if (_thisInstance == null) {
        _thisInstance = new YEngine();
        initialise(null, persisting, false, false);
    }
    return _thisInstance;
}

// org.yawlfoundation.yawl.engine.YNetRunner (line 96)
private YEngine _engine = YEngine.getInstance();  // Hidden dependency
```

### Solution: Constructor Injection

```java
// YEngine without static singleton
public class YEngine implements InterfaceAManagement, InterfaceBClient {
    private final YPersistenceManager persistenceManager;
    private final YAnnouncer announcer;
    private final YWorkItemRepository workItemRepository;

    public YEngine(YPersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.announcer = new YAnnouncer(this);
        this.workItemRepository = new YWorkItemRepository();
    }
}

// YNetRunner with injected engine
public class YNetRunner {
    private final YEngine engine;
    private final YAnnouncer announcer;

    public YNetRunner(YEngine engine, YAnnouncer announcer) {
        this.engine = engine;
        this.announcer = announcer;
    }
}

// Spring Boot context (singleton lifetime managed by Spring)
@Configuration
public class YawlEngineConfiguration {
    @Bean
    @Singleton
    public YPersistenceManager persistenceManager() {
        return new YPersistenceManager();
    }

    @Bean
    @Singleton
    public YEngine yawlEngine(YPersistenceManager persistenceManager) {
        return new YEngine(persistenceManager);
    }

    @Bean
    public YNetRunner yNetRunner(YEngine engine, YAnnouncer announcer) {
        return new YNetRunner(engine, announcer);
    }
}
```

### Benefits
- Testable: Mock engine in tests
- Observable: Dependency graph explicit
- Thread-safe: Spring manages singleton lifetime

### Files to Update
- `org.yawlfoundation.yawl.engine.YEngine` (remove getInstance)
- `org.yawlfoundation.yawl.engine.YNetRunner` (inject engine)
- All places calling `YEngine.getInstance()`

### Effort: 1 week (high-risk refactoring)

---

## Implementation Priority

| Pattern | Phase | Effort | Risk | Benefit |
|---------|-------|--------|------|---------|
| Virtual Threads (1) | 1 | 1 day | Low | High |
| Sealed Records (5) | 1 | 3 days | Low | High |
| Sealed State Machine (3) | 1 | 3 days | Medium | High |
| Structured Concurrency (2) | 1 | 2 days | Low | High |
| CQRS (4) | 2 | 2 days | Low | Medium |
| Reactive Events (7) | 2 | 2 days | Low | Medium |
| Module System (6) | 3 | 1 week | High | High |
| Constructor Injection (8) | 4 | 1 week | High | Medium |

**Recommended start**: Patterns 1, 3, 5 in parallel (weeks 1-2)

---

## References

- Pattern 1-2: Project Loom / Virtual Threads - https://openjdk.org/jeps/444
- Pattern 3: Sealed Classes - https://openjdk.org/jeps/409
- Pattern 4: CQRS Pattern - https://martinfowler.com/bliki/CQRS.html
- Pattern 5: Records - https://openjdk.org/jeps/440
- Pattern 6: Java Modules - https://openjdk.org/projects/jigsaw/
- Pattern 7: Reactive Streams - https://www.reactive-streams.org/
- Pattern 8: Dependency Inversion - https://en.wikipedia.org/wiki/Dependency_inversion_principle
