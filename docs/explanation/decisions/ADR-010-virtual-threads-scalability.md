# ADR-010: Virtual Threads Scalability

## Status

**ACCEPTED**

## Date

2026-02-18

## Context

YAWL v5.x uses platform threads for concurrent case execution and agent discovery loops. This creates scalability limitations:

1. **Memory overhead**: Each platform thread consumes ~2MB of stack space
2. **Thread pool limits**: 1000 concurrent operations requires ~2GB just for thread stacks
3. **Blocking I/O waste**: Threads waiting on HTTP/DB calls hold resources idle
4. **Context switching cost**: OS thread context switches are expensive at scale

Java 21+ provides virtual threads (Project Loom) as a production-ready feature. Virtual threads:
- Are managed by the JVM, not the OS
- Consume ~1KB of heap (not 2MB stack)
- Can be created in millions without memory exhaustion
- Automatically yield on blocking I/O operations

YAWL v6.0.0 already requires Java 25, making virtual threads immediately available.

## Decision

**YAWL v6.0 adopts virtual threads for all concurrent operations: case execution, agent discovery, event notification, and parallel work item processing.**

### Thread-Per-Case Execution Model

Each running workflow case executes on its own virtual thread:

```java
// org.yawlfoundation.yawl.engine.YNetRunner
public void continueIfPossible() {
    Thread.ofVirtual()
        .name("yawl-case-" + caseID)
        .start(() -> {
            try {
                executeNet();
            } catch (Exception e) {
                _log.error("Case execution failed: {}", caseID, e);
                announcer.announceCaseEvent(caseID, YEventType.CASE_COMPLETED);
            }
        });
}
```

Benefits:
- 100,000 concurrent cases use ~100MB of heap (vs 200GB with platform threads)
- No thread pool configuration required
- Natural isolation between cases

### Structured Concurrency for Work Items

When processing multiple work items in parallel, use `StructuredTaskScope`:

```java
// org.yawlfoundation.yawl.engine.YNetRunner
public void processWorkItemsInParallel(List<YWorkItem> items)
        throws InterruptedException, ExecutionException {

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<YWorkItem>> tasks = items.stream()
            .map(item -> scope.fork(() -> processWorkItem(item)))
            .toList();

        scope.join();           // Wait for all tasks
        scope.throwIfFailed();  // Propagate first failure

        // All tasks completed successfully
        tasks.forEach(task -> completeItem(task.resultNow()));
    }
}
```

Benefits:
- Automatic cancellation on failure
- Clear parent-child relationship in thread dumps
- Deterministic completion ordering

### Agent Discovery on Virtual Threads

Autonomous agents use virtual threads for discovery loops:

```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent
public class GenericPartyAgent extends AbstractAutonomousAgent {
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
        // Virtual thread terminates naturally when loop exits
    }

    private void runDiscoveryLoop() {
        while (running.get()) {
            try {
                discoverAndProcessWorkItems();
                Thread.sleep(config.getPollInterval());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
```

Benefits:
- 1000 agents = ~1MB heap (vs 2GB with platform threads)
- No executor service configuration
- Clean shutdown via interrupt

### Event Notification with Virtual Threads

The `MultiThreadEventNotifier` already uses virtual threads:

```java
// org.yawlfoundation.yawl.stateless.listener.event.MultiThreadEventNotifier
private final ExecutorService executor =
    Executors.newVirtualThreadPerTaskExecutor();

public void announce(YWorkflowEvent event) {
    for (YEventListener listener : listeners) {
        executor.submit(() -> {
            listener.eventReceived(event);
            return null;
        });
    }
}
```

### Scoped Values for Context Propagation

Replace ThreadLocal with ScopedValue for workflow context:

```java
// org.yawlfoundation.yawl.engine.context.WorkflowContext
public class WorkflowContext {
    public static final ScopedValue<String> WORKFLOW_ID = ScopedValue.newInstance();
    public static final ScopedValue<SecurityContext> SECURITY = ScopedValue.newInstance();
    public static final ScopedValue<AuditLog> AUDIT = ScopedValue.newInstance();
}

// Usage in case execution
ScopedValue.where(WorkflowContext.WORKFLOW_ID, caseID)
    .where(WorkflowContext.SECURITY, securityContext)
    .where(WorkflowContext.AUDIT, auditLog)
    .run(() -> {
        // All code in this block sees the scoped values
        // Virtual threads forked here inherit the values
        engine.executeCase(caseID);
    });
```

Benefits:
- Context automatically inherited by child virtual threads
- No memory leaks from ThreadLocal cleanup issues
- Type-safe context access

## Consequences

### Positive

1. **Massive scalability**: Millions of concurrent operations possible
2. **Memory efficiency**: ~1000x reduction in thread-related memory
3. **Simpler code**: No thread pool tuning, no executor configuration
4. **Better observability**: Virtual thread stack traces include full call history
5. **Natural blocking I/O**: Database and HTTP calls don't block OS threads

### Negative

1. **Pinned thread risk**: Synchronized blocks can pin virtual threads to carriers
2. **JVM compatibility**: Requires Java 21+ (already required for YAWL v6.0.0)
3. **Debugging differences**: Thread dumps show virtual threads differently
4. **Monitoring changes**: Traditional thread pool metrics don't apply

### Performance Characteristics

| Metric | Platform Threads | Virtual Threads |
|--------|-----------------|-----------------|
| Memory per thread | ~2MB | ~1KB |
| 10,000 concurrent ops | ~20GB | ~10MB |
| Thread creation | ~1ms | ~1us |
| Context switch | ~10us (OS) | ~100ns (JVM) |
| Max concurrent threads | ~10,000 | ~1,000,000+ |

### Risks and Mitigations

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Thread pinning in synchronized blocks | MEDIUM | HIGH | Replace synchronized with ReentrantLock |
| Database connection pool exhaustion | MEDIUM | HIGH | Use connection pool sized for CPU cores, not threads |
| Excessive virtual thread creation | LOW | MEDIUM | Rate limiting on case launch |
| Carrier thread starvation | LOW | MEDIUM | Monitor carrier thread pool via JFR |

## Implementation Notes

### Pinning Avoidance

Virtual threads can be "pinned" to carrier threads when executing synchronized blocks:

```java
// BAD: Pins virtual thread
public synchronized void processWorkItem(YWorkItem item) {
    // Long I/O operation while holding lock
    db.save(item);
}

// GOOD: Uses ReentrantLock
private final ReentrantLock lock = new ReentrantLock();

public void processWorkItem(YWorkItem item) {
    lock.lock();
    try {
        db.save(item);
    } finally {
        lock.unlock();
    }
}
```

### Database Connection Pooling

Virtual threads don't change database connection pool requirements:

```properties
# Connection pool sized for CPU cores, not concurrent threads
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

# Virtual threads wait efficiently for connections
# No need to increase pool size for concurrency
```

### Thread Dump Analysis

Virtual thread dumps include carrier thread mapping:

```
"yawl-case-12345" #1000 virtual
  java.lang.Thread.State: TIMED_WAITING
  at java.lang.VirtualThread.sleep(java.base/VirtualThread.java)
  at org.yawlfoundation.yawl.engine.YNetRunner.executeNet(YNetRunner.java:234)
  Carrier thread: "worker-3" #15
```

### JVM Flags for Production

```bash
# Enable virtual thread scheduling optimizations (Java 25 defaults)
-XX:+UseVirtualThreads

# Debug pinning events (development only)
-Djdk.tracePinnedThreads=full

# Carrier thread pool size (default = CPU cores)
-Djdk.virtualThreadScheduler.parallelism=8
```

## Alternatives Considered

### Reactive / Non-Blocking I/O (WebFlux)

**Rejected.** Requires complete rewrite of the engine. Virtual threads provide similar benefits without API changes.

### Actor Model (Akka/Pekko)

**Rejected for v6.0.** Actor-per-case would be architecturally elegant but requires significant refactoring. Consider for v7.0.

### Coroutine Library (Kotlin)

**Rejected.** YAWL is Java-native. Virtual threads provide similar benefits within pure Java.

### Fixed Thread Pool with Async

**Rejected.** Thread pool tuning is complex and doesn't scale to millions of operations.

## Related ADRs

- ADR-004: Spring Boot 3.4 + Java 25 (enables virtual threads)
- ADR-009: Multi-Cloud Strategy (virtual threads for cloud scale)
- ADR-014: Clustering and Horizontal Scaling (thread-per-case per node)
- ADR-025: Agent Coordination Protocol (virtual threads for agent loops)

## Approval

**Approved by:** YAWL Architecture Team, Performance Team
**Date:** 2026-02-18
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-18

---

**Revision History:**
- 2026-02-18: Initial ADR establishing virtual threads adoption
