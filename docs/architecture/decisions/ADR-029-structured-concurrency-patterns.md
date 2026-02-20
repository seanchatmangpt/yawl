# ADR-029: Structured Concurrency Patterns

## Status

**ACCEPTED**

## Date

2026-02-20

## Context

Java 25 introduces Project Loom's Structured Concurrency (JEP 505), which provides a way to fork multiple concurrent tasks and wait for them to complete with guaranteed cancellation and cleanup.

YAWL has several scenarios where this is valuable:

1. **Work Item Batch Processing**: Agent discovers 50 work items; processes them in parallel
2. **Multi-Instance Tasks**: Execute multiple task instances concurrently
3. **Parallel Gateway Merges**: Wait for multiple branches to complete before proceeding
4. **Agent Coordination**: Multiple agents working on same task, collecting decisions

Without structured concurrency, developers must manage:
- Manual Future collections
- Timeout handling (which may leave threads running)
- Error propagation (first error doesn't stop others)
- Resource cleanup (easy to forget shutdown())

## Decision

**YAWL v6.0.0 adopts Structured Concurrency (JEP 505) for all parallel task scenarios.**

### Pattern 1: Fan-Out Work Item Batch Processing

**Problem**: Agent discovers 50 work items; processes sequentially (5 seconds × 50 = 4 min 10 sec).

**Solution**: Use StructuredTaskScope to parallelize.

```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent
public void processDiscoveredWorkItems(List<YWorkItem> items) {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<ProcessResult>> tasks = items.stream()
            .map(item -> scope.fork(() -> processSingleItem(item)))
            .toList();

        scope.join();           // Wait for all to complete
        scope.throwIfFailed();  // First exception cancels rest

        // Handle results
        List<ProcessResult> results = tasks.stream()
            .map(Subtask::resultNow)
            .toList();

        recordResults(results);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // All forked tasks are automatically cancelled
    }
}

private ProcessResult processSingleItem(YWorkItem item) throws YStateException {
    // Check eligibility
    if (!canHandle(item)) {
        // Handoff to capable agent
        return new ProcessResult(item.getId(), ResultType.HANDOFF);
    }

    // Reason about decision (expensive operation)
    YDecision decision = reasoner.reason(item, item.getData(), context);

    // Generate output
    Document output = generator.generate(decision, item.getSchema());

    // Complete work item
    engine.completeWorkItem(item.getId(), output, null);

    return new ProcessResult(item.getId(), ResultType.COMPLETED);
}

record ProcessResult(String workItemId, ResultType resultType) {}
enum ResultType { COMPLETED, HANDOFF, FAILED }
```

**Benefits:**
- 50 items: ~5 seconds (parallel) vs 250 seconds (serial)
- If item 1 fails, items 2-50 are automatically cancelled
- No need for explicit Future management or timeout handling
- Parent-child relationships visible in thread dumps

---

### Pattern 2: Merge in Parallel Gateway

**Problem**: Parallel gateway has 3 branches; must wait for all to reach merge.

**Solution**: Use StructuredTaskScope.

```java
// org.yawlfoundation.yawl.engine.YNetRunner
public void executeParallelGateway(YSplitJoin split, YMarkings markings)
        throws YStateException {

    // Fork off each branch as a virtual thread
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<YMarkings>> branches = split.getOutgoingFlows()
            .stream()
            .map(flow -> scope.fork(() -> executeBranch(flow, markings)))
            .toList();

        // Wait for all branches to reach merge point
        scope.join();
        scope.throwIfFailed();

        // Merge markings from all branches
        List<YMarkings> branchMarkings = branches.stream()
            .map(Subtask::resultNow)
            .toList();

        YMarkings mergedMarkings = mergeBranchMarkings(branchMarkings);
        continueFromMerge(split, mergedMarkings);

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        // All branches cancelled
    }
}

private YMarkings executeBranch(YFlow flow, YMarkings markings) throws YStateException {
    YElement target = flow.getTarget();
    return executeNet(target, markings);
}
```

---

### Pattern 3: Multi-Instance Task with Virtual Threads

**Problem**: Multi-instance task with 100 instances; must wait for all to complete or fail threshold.

**Solution**: Use StructuredTaskScope with custom completion policy.

```java
// org.yawlfoundation.yawl.engine.YNetRunner
public void executeMultiInstanceTask(YMultiInstanceTask task, List<Object> instances)
        throws YStateException {

    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<WorkItemResult>()) {
        List<Subtask<WorkItemResult>> tasks = instances.stream()
            .map(instance -> scope.fork(() -> executeInstance(task, instance)))
            .toList();

        // Wait until:
        // - All complete successfully, OR
        // - N fail (fail threshold)
        WorkItemResult result = scope.join();

        if (result.allSucceeded()) {
            continueAfterMultiInstance(task, result.successCount());
        } else if (result.failureCount() > task.getFailureThreshold()) {
            throw new YStateException("Multi-instance failure threshold exceeded");
        }

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}

private WorkItemResult executeInstance(YMultiInstanceTask task, Object instance)
        throws YStateException {
    YWorkItem wi = createWorkItemForInstance(task, instance);
    // ... execute ...
    return new WorkItemResult(wi.getId(), true);
}

record WorkItemResult(
    String workItemId,
    boolean success
) {}
```

---

### Pattern 4: Agent Decision Consensus (Quorum)

**Problem**: 3 agents must approve a sensitive work item before completion; wait for quorum.

**Solution**: StructuredTaskScope with early exit on quorum.

```java
// org.yawlfoundation.yawl.integration.autonomous.DecisionConsensus
public YDecision getConsensusDecision(YWorkItem item, List<String> requiredAgents)
        throws YDataStateException {

    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<AgentDecision>()) {
        List<Subtask<AgentDecision>> decisions = requiredAgents.stream()
            .map(agentID -> scope.fork(() -> getAgentDecision(agentID, item)))
            .toList();

        // Wait for first success (quorum = 1 approval)
        // Or timeout after 30 seconds
        scope.joinUntil(Instant.now().plus(Duration.ofSeconds(30)));

        long approvals = decisions.stream()
            .filter(Subtask::isDone)
            .map(Subtask::resultNow)
            .filter(d -> d.decision() == ApprovalDecision.APPROVE)
            .count();

        if (approvals >= 2) {
            return ApprovedDecision.INSTANCE;
        } else {
            throw new YDataStateException(
                "Insufficient approvals: " + approvals + "/3"
            );
        }

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new YDataStateException("Decision collection interrupted");
    }
}

record AgentDecision(String agentID, ApprovalDecision decision, Instant timestamp) {}
enum ApprovalDecision { APPROVE, REJECT, ABSTAIN }
```

---

### Pattern 5: Timeout Handling with Scope

**Problem**: Process work items with 10-second timeout per item; if one hangs, cancel others.

**Solution**: Use scope with timeout.

```java
// org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent
public void processWorkItemsWithTimeout(List<YWorkItem> items, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        List<Subtask<Void>> tasks = items.stream()
            .map(item -> scope.fork(() -> processItemWithTimeout(item, deadline)))
            .toList();

        // Wait until deadline or all complete
        boolean completed = scope.joinUntil(deadline);

        if (!completed) {
            // Deadline exceeded; scope will cancel remaining tasks
            throw new YDataStateException(
                "Work item processing timeout after " + timeout.toMillis() + "ms"
            );
        }

        scope.throwIfFailed();

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}

private Void processItemWithTimeout(YWorkItem item, Instant deadline) throws Exception {
    while (Instant.now().isBefore(deadline)) {
        // Process item with periodic checks
        if (canCompleteNow(item)) {
            completeWorkItem(item);
            return null;
        }
        Thread.sleep(100);  // Check every 100ms
    }
    throw new TimeoutException("Item processing timeout");
}
```

---

## Core Concepts

### ShutdownOnFailure vs ShutdownOnSuccess

| Policy | Behavior | Use Case |
|--------|----------|----------|
| **ShutdownOnFailure** | Cancel all tasks if any fails | Batch processing; all or nothing |
| **ShutdownOnSuccess** | Cancel remaining once one succeeds | Redundant requests; race condition |
| **ShutdownOnCancel** | Cancel remaining if scope is cancelled | User-initiated cancellation |

### Task Completion Methods

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var task = scope.fork(() -> doWork());

    scope.join();  // Wait for all tasks

    if (task.isDone()) {
        Object result = task.resultNow();  // Throws if failed
    }

    scope.joinUntil(deadline);  // Wait until deadline
}
```

---

## Exception Handling

### First-Failure Stops Others

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var task1 = scope.fork(() -> operation1());
    var task2 = scope.fork(() -> operation2());
    var task3 = scope.fork(() -> operation3());

    scope.join();

    // If task2 throws exception:
    // 1. task3 is cancelled (if not yet complete)
    // 2. scope.throwIfFailed() propagates task2's exception
    scope.throwIfFailed();

} catch (ExecutionException e) {
    // Handle original exception
    throw new YDataStateException("Operation failed", e.getCause());
} catch (InterruptedException e) {
    // Scope was cancelled
    Thread.currentThread().interrupt();
}
```

### Custom Exception Handling

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var results = items.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();

    scope.join();

    // Collect all failures (not just first)
    List<Throwable> failures = results.stream()
        .filter(t -> t.exception() != null)
        .map(Subtask::exception)
        .toList();

    if (!failures.isEmpty()) {
        throw new YDataStateException("Multiple failures", failures.get(0));
    }

} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

---

## Performance Characteristics

### Speedup with Parallelization

| Scenario | Serial | Parallel | Speedup |
|----------|--------|----------|---------|
| 50 items @ 100ms each | 5.0s | 0.5s | **10x** |
| 3 branches @ 1s each | 3.0s | 1.0s | **3x** |
| 100 instances @ 10ms each | 1.0s | 0.1s | **10x** |

### Virtual Thread Overhead

- Scope fork(): ~1μs (negligible)
- Virtual thread context switch: ~100ns
- GC impact: Minimal (short-lived threads)

---

## Testing Structured Concurrency

```java
@Test
void testStructuredConcurrency() throws Exception {
    List<Integer> results = Collections.synchronizedList(new ArrayList<>());

    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        for (int i = 0; i < 100; i++) {
            int index = i;
            scope.fork(() -> {
                results.add(index);
                return null;
            });
        }

        scope.join();
        scope.throwIfFailed();
    }

    assertEquals(100, results.size());
}

@Test
void testStructuredConcurrencyWithTimeout() throws Exception {
    assertThrows(TimeoutException.class, () -> {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                Thread.sleep(5000);  // 5-second task
                return null;
            });

            // Timeout after 1 second
            scope.joinUntil(Instant.now().plus(Duration.ofSeconds(1)));

            throw new TimeoutException("Scope timeout");
        }
    });
}

@Test
void testStructuredConcurrencyErrorCancellation() throws Exception {
    AtomicBoolean cancelled = new AtomicBoolean(false);

    try {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                throw new YStateException("Intentional failure");
            });

            scope.fork(() -> {
                try {
                    Thread.sleep(10000);  // Would be cancelled
                } catch (InterruptedException e) {
                    cancelled.set(true);
                    throw e;
                }
                return null;
            });

            scope.join();
            scope.throwIfFailed();
        }
    } catch (ExecutionException e) {
        // Expected
    }

    assertTrue(cancelled.get(), "Second task should have been cancelled");
}
```

---

## Migration from ExecutorService

### Before (ExecutorService)

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

List<Future<WorkItem>> futures = new ArrayList<>();
for (WorkItem item : items) {
    futures.add(executor.submit(() -> processItem(item)));
}

for (Future<WorkItem> future : futures) {
    try {
        WorkItem result = future.get(5, TimeUnit.SECONDS);
        processResult(result);
    } catch (TimeoutException e) {
        future.cancel(true);  // May not stop the task
        // Other futures still running!
    }
}

executor.shutdown();
executor.awaitTermination(30, TimeUnit.SECONDS);
```

### After (StructuredTaskScope)

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<Subtask<WorkItem>> tasks = items.stream()
        .map(item -> scope.fork(() -> processItem(item)))
        .toList();

    boolean completed = scope.joinUntil(
        Instant.now().plus(Duration.ofSeconds(5))
    );

    if (!completed) {
        throw new YDataStateException("Processing timeout");
    }

    scope.throwIfFailed();

    tasks.stream()
        .map(Subtask::resultNow)
        .forEach(this::processResult);
}
// Automatic cleanup; no shutdown() needed
```

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Mixing Structured Scopes

```java
// DON'T: Nested scopes with different exception handling
try (var scope1 = new StructuredTaskScope.ShutdownOnFailure()) {
    scope1.fork(() -> {
        try (var scope2 = new StructuredTaskScope.ShutdownOnSuccess()) {
            // Mixing policies makes code hard to understand
        }
    });
}

// DO: Use single scope with clear policy
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // All tasks follow same exception policy
}
```

### Anti-Pattern 2: Long-Lived Tasks in Scope

```java
// DON'T: Tasks that run for minutes
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    scope.fork(() -> {
        while (true) {  // Unbounded loop!
            doSomething();
        }
    });
    scope.join();  // Never returns
}

// DO: Bounded tasks; scopes for short-lived parallelism
```

### Anti-Pattern 3: Ignoring InterruptedException

```java
// DON'T: Swallow interrupts
try {
    scope.join();
} catch (InterruptedException e) {
    // DON'T DO THIS
}

// DO: Restore interrupt status
try {
    scope.join();
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new YDataStateException("Scope interrupted", e);
}
```

---

## Monitoring

### JFR Events

```bash
jcmd <pid> JFR.dump \
    --events "jdk.VirtualThreadStart,jdk.VirtualThreadEnd" \
    --json recording.jfr
```

### Metrics

```java
@Component
public class StructuredConcurrencyMetrics {
    private final MeterRegistry registry;

    public StructuredConcurrencyMetrics(MeterRegistry registry) {
        this.registry = registry;

        Counter.builder("yawl.scope.forks")
            .description("Total task forks")
            .register(registry);

        Timer.builder("yawl.scope.duration")
            .description("Scope join time")
            .register(registry);
    }
}
```

---

## Consequences

### Positive

1. **Automatic Cancellation**: Cleaner than manual Future management
2. **Implicit Deadline Propagation**: No need for explicit timeout threading
3. **Deterministic Cleanup**: Try-with-resources ensures scope shutdown
4. **Better Visibility**: Thread dumps show parent-child relationships
5. **Observable Semantics**: Clear task outcome (success/failure/cancelled)

### Negative

1. **JDK 21+ Requirement**: New API; less battle-tested than ExecutorService
2. **Learning Curve**: Developers need training
3. **Not for Long-Lived Threads**: Not suitable for servers/daemons

---

## Alternatives Considered

### Alternative 1: ExecutorService with CompletableFuture

**Rejected.** Requires manual Future management; no automatic cancellation.

### Alternative 2: Reactive Streams (WebFlux)

**Rejected.** Virtual threads sufficient; avoid API overhaul.

---

## Related ADRs

- **ADR-028**: Virtual Threads Deployment Strategy
- **ADR-030**: Scoped Values Context Management

---

## References

- JEP 505: Structured Concurrency (finalized Java 21, Java 25)
- Virtual Thread Guide: https://openjdk.org/jeps/444
- "Structured Concurrency": https://openjdk.org/jeps/505

---

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-20
**Implementation Status:** PLANNED (v6.0.0)
**Review Date:** 2026-08-20

---

**Revision History:**
- 2026-02-20: Initial ADR
