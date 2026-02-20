# Autonomous Task Parallelization Design

**Status**: Design Phase | **Date**: Feb 2026 | **Benefit**: 80% throughput gain, 20% code

---

## Problem Statement

In YAWL's current agent discovery model, when `PollingDiscoveryStrategy` discovers multiple eligible work items in a single poll cycle, they are processed sequentially:

```
Cycle start
  -> GET /ib/workitems (returns 5 eligible items)
  -> Item 1: checkout + eligibility check + reason + complete (8s)
  -> Item 2: checkout + eligibility check + reason + complete (8s)
  -> Item 3: checkout + eligibility check + reason + complete (8s)
  -> Item 4: checkout + eligibility check + reason + complete (8s)
  -> Item 5: checkout + eligibility check + reason + complete (8s)
Total: 40 seconds
```

Sequential processing is inefficient because:
1. **Single-threaded discovery loop**: Only one work item processed at a time
2. **I/O blocking**: Network delays multiply (5 items × 8s = 40s vs parallel 8s)
3. **Resource underutilization**: CPU idle during network roundtrips
4. **Lost scalability**: Adding more agent threads requires more discovery threads

**Current bottleneck**: `GenericPartyAgent.runDiscoveryLoop()` processes items sequentially (Observable timeout: 40s+ for 5 items)

---

## Solution: Structured Concurrency for Work Item Batches

Use Java 21+ `StructuredTaskScope` to parallelize discovery for independent work items. Each item's processing pipeline (checkout → eligibility check → reasoning → completion) executes in a separate virtual thread.

### Architecture Diagram

```
PollingDiscoveryStrategy.discoverAndProcess()
  |
  ├─ GET /ib/workitems (ENABLED) [shared, blocking]
  |
  └─ Create StructuredTaskScope<WorkItemResult>
       |
       ├─ fork() → scope.fork(this::processWorkItem(item1))
       ├─ fork() → scope.fork(this::processWorkItem(item2))
       ├─ fork() → scope.fork(this::processWorkItem(item3))
       ├─ fork() → scope.fork(this::processWorkItem(item4))
       └─ fork() → scope.fork(this::processWorkItem(item5))
       |
       └─ scope.join()  [wait for all, cancel on first failure]
            |
            └─ results: List<WorkItemResult> [5 results, 8s total]
```

### Concurrency Model

**Thread Pool**: Virtual threads (one per item in batch)
- Cost: ~10 KB per virtual thread vs 2 MB per platform thread
- Scaling: 100 items in parallel = 1 MB memory
- Automatic cleanup: Virtual threads deallocate on completion

**Cancellation Policy**: `ShutdownOnFailure`
- If Item 2 fails (e.g., eligibility check throws), Item 3/4/5 are cancelled
- Graceful degradation: partial results returned
- Observable: failure messages logged with item ID

---

## Pseudocode Design

```java
// org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy

public class PollingDiscoveryStrategy implements DiscoveryStrategy {
    private final InterfaceBQueries engine;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;
    private final OutputGenerator outputGenerator;
    private final AgentContext context;

    // Discover and process work items in parallel
    public List<WorkItemResult> discoverAndProcess()
            throws InterruptedException, ExecutionException {

        // Step 1: Fetch available work items (single blocking call)
        List<YWorkItem> discoveredItems = engine.getAvailableWorkItems()
            .stream()
            .filter(this::isEligible)
            .toList();

        if (discoveredItems.isEmpty()) {
            return List.of();
        }

        // Step 2: Create scope for parallel processing
        try (var scope = new StructuredTaskScope.ShutdownOnFailure<WorkItemResult>()) {

            // Step 3: Fork each item as independent task
            List<Subtask<WorkItemResult>> tasks = discoveredItems.stream()
                .map(item -> scope.fork(() -> processWorkItem(item)))
                .toList();

            // Step 4: Wait for all tasks; cancel others on first failure
            scope.join();           // Block until all complete or first fails
            scope.throwIfFailed();  // Propagate first exception

            // Step 5: Collect successful results
            return tasks.stream()
                .map(Subtask::resultNow)
                .collect(Collectors.toList());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DiscoveryException("Discovery interrupted", e);
        }
    }

    // Each work item is processed independently
    private WorkItemResult processWorkItem(YWorkItem item)
            throws CheckoutException, ReasoningException {

        String workItemId = item.getID();
        String logPrefix = "[" + workItemId + "]";

        try {
            // Step 1: Attempt checkout
            logger.info("{} Checking out work item", logPrefix);
            YWorkItem checkedOutItem = engine.startWorkItem(item, getClientContext());

            // Step 2: Check eligibility
            logger.info("{} Checking eligibility", logPrefix);
            if (!eligibilityReasoner.canHandle(checkedOutItem, context.getCapabilities())) {
                logger.info("{} Agent ineligible, initiating handoff", logPrefix);
                initiateHandoff(checkedOutItem);
                return WorkItemResult.handedOff(workItemId);
            }

            // Step 3: Reason about work item
            logger.info("{} Starting decision reasoning", logPrefix);
            WorkflowDecision decision = decisionReasoner.reason(
                checkedOutItem,
                context.getDomainKnowledge()
            );

            // Step 4: Generate output
            logger.info("{} Generating output", logPrefix);
            String outputData = outputGenerator.generate(decision, checkedOutItem.getTaskID());

            // Step 5: Complete work item
            logger.info("{} Completing work item", logPrefix);
            engine.completeWorkItem(checkedOutItem, outputData, getClientContext());

            logger.info("{} Successfully completed", logPrefix);
            return WorkItemResult.completed(workItemId, decision);

        } catch (CheckoutConflictException e) {
            // Item already taken by another agent
            logger.debug("{} Checkout conflict (item taken): {}", logPrefix, e.getMessage());
            return WorkItemResult.skipped(workItemId, "already_taken");

        } catch (EligibilityException e) {
            // Eligibility check failed
            logger.warn("{} Eligibility check failed: {}", logPrefix, e.getMessage());
            rollbackWorkItem(item);
            return WorkItemResult.failed(workItemId, "ineligible");

        } catch (ReasoningException e) {
            // Decision reasoning failed (recoverable or non-recoverable)
            logger.error("{} Reasoning failed: {}", logPrefix, e.getMessage());
            rollbackWorkItem(item);
            return WorkItemResult.failed(workItemId, "reasoning_error");

        } catch (Exception e) {
            // Unexpected error
            logger.error("{} Unexpected error: {}", logPrefix, e.getMessage(), e);
            try {
                rollbackWorkItem(item);
            } catch (Exception rollbackError) {
                logger.error("{} Rollback also failed: {}", logPrefix, rollbackError.getMessage());
            }
            throw new DiscoveryException("Unexpected error processing " + workItemId, e);
        }
    }

    private void rollbackWorkItem(YWorkItem item) throws CheckoutException {
        try {
            // Interface B: rollback operation (undo checkout)
            engine.rollbackWorkItem(item.getID());
        } catch (Exception e) {
            logger.error("Rollback failed for {}: {}", item.getID(), e.getMessage());
            throw new CheckoutException("Rollback failed", e);
        }
    }

    private void initiateHandoff(YWorkItem item) {
        // Delegate to HandoffRequestService (ADR-025 Pattern 10)
        // Details in AUTONOMOUS-HANDOFF-PROTOCOL.md
    }

    private boolean isEligible(YWorkItem item) {
        // Pre-filter: skip items this agent can never handle
        return !item.isHasWaitingData() &&
               context.getCapabilities().contains(item.getTaskID());
    }
}
```

### Result Record Type

```java
// Immutable result per work item
public record WorkItemResult(
    String workItemId,
    Status status,           // COMPLETED, HANDED_OFF, SKIPPED, FAILED
    WorkflowDecision decision,  // null if not completed
    String failureReason
) {
    public enum Status { COMPLETED, HANDED_OFF, SKIPPED, FAILED }

    public static WorkItemResult completed(String id, WorkflowDecision decision) {
        return new WorkItemResult(id, Status.COMPLETED, decision, null);
    }

    public static WorkItemResult handedOff(String id) {
        return new WorkItemResult(id, Status.HANDED_OFF, null, "handed_off");
    }

    public static WorkItemResult skipped(String id, String reason) {
        return new WorkItemResult(id, Status.SKIPPED, null, reason);
    }

    public static WorkItemResult failed(String id, String reason) {
        return new WorkItemResult(id, Status.FAILED, null, reason);
    }
}
```

---

## Integration Points

### Interface B (Read Side)

```
GET /ib/workitems?status=ENABLED
  -> Returns List<YWorkItem>
  -> Immutable snapshot for parallel processing
```

### InterfaceBQueries (Pattern 4: CQRS)

```java
// New interface method (backward-compatible extension)
public interface InterfaceBQueries {
    Set<YWorkItem> getAvailableWorkItems();
    // ... other queries ...
}
```

### Virtual Thread Pool

Replace platform threads with virtual thread executor in `GenericPartyAgent`:

```java
public class GenericPartyAgent {
    private final ExecutorService discoveryExecutor =
        Executors.newVirtualThreadPerTaskExecutor();

    private Thread discoveryThread;

    @Override
    public void start() {
        running.set(true);
        // Launch discovery loop on virtual thread
        discoveryThread = Thread.ofVirtual()
            .name("yawl-agent-discovery-" + config.getAgentId())
            .start(this::runDiscoveryLoop);
    }
}
```

---

## Performance Model

### Without Parallelization (Sequential)

```
5 items × 8s per item = 40s per cycle
100 items = 800s (13 minutes) per cycle
```

Assumptions:
- Checkout: 200ms
- Eligibility check: 1s
- Reasoning: 4s
- Completion: 2.8s

### With Parallelization (Parallel via StructuredTaskScope)

```
5 items: max(8s) = 8s per cycle (80% improvement)
100 items: max(8s) = 8s per cycle (100× improvement)
```

### Memory Cost

- Platform threads: 2 MB × 100 items = 200 MB
- Virtual threads: 10 KB × 100 items = 1 MB
- Savings: 99% memory reduction

### Latency Impact

```
Discovery cycle time: 40s → 8s (5× faster)
Agent throughput: 5 items/cycle → 5 items/cycle (same)
Utilization: 1/5 → 5/5 (5× better resource usage)
```

---

## Failure Modes & Recovery

### Mode 1: Item Checkout Fails (409 Conflict)

**Cause**: Another agent already checked out the item

**Recovery**:
```
Catch CheckoutConflictException
  -> Log as "skipped"
  -> Continue with remaining items
  -> Item returned to Enabled state
  -> Next discovery cycle picks it up
```

### Mode 2: Eligibility Check Fails

**Cause**: Agent lacks domain knowledge or credentials

**Recovery**:
```
Catch EligibilityException
  -> Rollback checkout
  -> Initiate handoff via HandoffRequestService
  -> Result: HANDED_OFF
  -> Item returned to Enabled state for other agents
```

### Mode 3: Reasoning Fails (Non-Recoverable)

**Cause**: Decision reasoner cannot produce valid decision

**Recovery**:
```
Catch ReasoningException (non-recoverable)
  -> Rollback checkout
  -> Log error with item context
  -> Result: FAILED
  -> Escalate to human if configured
```

### Mode 4: First Item Fails -> Cascade Cancel

**Cause**: Item 2 throws exception

**Behavior**:
```
scope.join() + scope.throwIfFailed()
  -> Cancels Items 3, 4, 5 (via CancellationException)
  -> Returns partial results (Items 1 success, Item 2 failure)
  -> Next cycle retries Items 3, 4, 5
```

---

## Configuration

```yaml
agent:
  discovery:
    parallelism:
      enabled: true
      batch-size: 50              # Max items per discovery cycle
      timeout-seconds: 60         # Max time for entire batch
      failure-policy: SHUTDOWN_ON_FAILURE  # or SUPPRESS_EXCEPTIONS

  executor:
    type: VIRTUAL_THREAD          # or PLATFORM_THREAD for fallback
    core-threads: 1               # Ignored for virtual threads
    queue-size: 1000
```

---

## Metrics & Observability

```java
// Counters
agent.discovery.items.total       // Total items discovered
agent.discovery.items.completed   // Successfully completed
agent.discovery.items.handed_off  // Handed off to other agents
agent.discovery.items.failed      // Failed processing
agent.discovery.items.skipped     // Already taken by others

// Histograms
agent.discovery.batch.size        // Items per batch
agent.discovery.item.duration.ms  // Time per item (parallel)
agent.discovery.cycle.duration.ms // Time for entire batch

// Flags
agent.discovery.parallel.enabled  // Configuration status
```

---

## Implementation Roadmap

1. **Phase 1 (Days 1-2)**: Create `PollingDiscoveryStrategy` with `StructuredTaskScope`
2. **Phase 2 (Days 3-4)**: Add `WorkItemResult` record and result aggregation
3. **Phase 3 (Days 5-6)**: Integrate with `GenericPartyAgent` discovery loop
4. **Phase 4 (Days 7)**: Add metrics, update agent configuration
5. **Validation (Days 8)**: Performance test with 100-item batch

---

## Files to Create/Update

- **Create**: `org/yawlfoundation/yawl/integration/autonomous/strategies/PollingDiscoveryStrategy.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/model/WorkItemResult.java`
- **Update**: `org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java` (use virtual threads)
- **Update**: `org/yawlfoundation/yawl/integration/autonomous/discovery/DiscoveryStrategy.java` (interface)
- **Update**: `yawl-integration/pom.xml` (add Java 21 source/target if not present)

---

## Related Designs

- **Pattern 2 (YAWL v6)**: Structured Concurrency for Work Item Batches (reference implementation)
- **ADR-025**: Agent Coordination Protocol (handoff mechanism)
- **AUTONOMOUS-HANDOFF-PROTOCOL.md**: Handoff request service integration

---

## Testing Strategy

### Unit Test: Parallel Processing

```java
@Test
void testParallelProcessingOf5Items() throws InterruptedException, ExecutionException {
    // Arrange: 5 work items, all eligible
    List<YWorkItem> items = createTestItems(5);

    // Act: Process in parallel
    Instant start = Instant.now();
    List<WorkItemResult> results = strategy.discoverAndProcess();
    Instant end = Instant.now();

    // Assert
    assertEquals(5, results.size());
    assertEquals(5, results.stream()
        .filter(r -> r.status() == Status.COMPLETED)
        .count());

    // Parallel: ~8s, Sequential would be ~40s
    assertTrue(Duration.between(start, end).toMillis() < 15000,
        "Should complete in <15s (parallel), not 40s (sequential)");
}
```

### Integration Test: Failure Cascade

```java
@Test
void testFailureCausesOtherItemsToBeCancelled() {
    // Arrange: Item 2 will fail eligibility check
    mockEligibilityCheckToFail(items.get(1));

    // Act
    try {
        strategy.discoverAndProcess();
    } catch (DiscoveryException e) {
        // Expected
    }

    // Assert: Items 3-5 were cancelled
    verify(engine, times(2)).startWorkItem(any(), any());  // Only 1 & 2 attempted
}
```

---

## Backward Compatibility

- **Interface A (Design)**: No changes
- **Interface B (Runtime)**: New InterfaceBQueries interface; existing methods unchanged
- **Existing Code**: `SequentialDiscoveryStrategy` remains as fallback for legacy deployments
- **Configuration**: Default `parallelism.enabled: false` for safe rollout

---

## References

- JEP 444 (Virtual Threads): https://openjdk.org/jeps/444
- JEP 462 (StructuredTaskScope): https://openjdk.org/jeps/462
- Martin Fowler - Parallel Testing: https://martinfowler.com/articles/nonDeterminism.html
