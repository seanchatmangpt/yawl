# Java 25 Modernization Implementation Guidelines

**Version**: 6.0.0
**Status**: Implementation Guide
**Target**: Development Teams

---

## 1. Core Implementation Principles

### 1.1 Zero Thread-Pin Guarantees

**DO NOT**: Use `synchronized` blocks on virtual threads
**DO**: Use `ReentrantLock` or `StampedLock` for virtual thread synchronization

```java
// ❌ WRONG: Pins virtual thread to carrier
synchronized (this) {
    // critical section
}

// ✅ CORRECT: Virtual thread-safe synchronization
private final ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    // critical section
} finally {
    lock.unlock();
}
```

### 1.2 Context Binding Best Practices

**ALWAYS**: Use `ScopedValue.callWhere()` for context binding
**NEVER**: Use `ThreadLocal` for context propagation

```java
// ✅ CORRECT: ScopedValue context binding
ScopedValue<WorkflowContext> context = ScopedValue.newInstance();

ScopedValue.callWhere(context, new ContextData(...), () -> {
    // Child virtual threads inherit context automatically
    return operation.execute();
});

// ❌ WRONG: ThreadLocal context
ThreadLocal<WorkflowContext> context = new ThreadLocal<>();
context.set(new ContextData(...));
try {
    // No inheritance in child threads
    return operation.execute();
} finally {
    context.remove();
}
```

### 1.3 Structured Concurrency Patterns

**ALWAYS**: Use `StructuredTaskScope` for parallel operations
**NEVER**: Submit tasks to unmanaged executors

```java
// ✅ CORRECT: Structured concurrency
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    // Submit structured tasks
    scope.fork(() -> task1.execute());
    scope.fork(() -> task2.execute());
    scope.join(); // Wait for all or first failure
}

// ❌ WRONG: Unmanaged parallel execution
ExecutorService executor = Executors.newCachedThreadPool();
executor.submit(() -> task1.execute());
executor.submit(() -> task2.execute());
```

---

## 2. Implementation Roadmap

### Phase 1: Core Infrastructure (Weeks 1-2)

#### Task 1: ScopedValue System Implementation
```java
// File: src/org/yawlfoundation/yawl/engine/context/ScopedValueSystem.java
public final class ScopedValueSystem {

    // Root contexts
    public static final ScopedValue<WorkflowContext> WORKFLOW_CONTEXT =
        ScopedValue.newInstance();
    public static final ScopedValue<CaseContext> CASE_CONTEXT =
        ScopedValue.newInstance();
    public static final ScopedValue<WorkflowMetadata> WORKFLOW_METADATA =
        ScopedValue.newInstance();

    // Context execution utilities
    public static <T> T executeWithContext(
        ScopedValue<WorkflowContext> context,
        WorkflowContext data,
        Supplier<T> operation
    ) {
        return ScopedValue.callWhere(context, data, operation::get);
    }

    // Context inheritance
    public static <T> T executeWithInheritedContext(
        String caseID,
        Supplier<T> operation
    ) {
        WorkflowContext context = WorkflowContext.of(caseID, specID, engineInstance);
        return executeWithContext(WORKFLOW_CONTEXT, context, operation);
    }
}
```

#### Task 2: Virtual Thread Pool Implementation
```java
// File: src/org/yawlfoundation/yawl/engine/threads/VirtualThreadPool.java
public final class VirtualThreadPool {

    private final ExecutorService virtualExecutor;
    private final Thread monitorThread;
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final LongAdder totalTasks = new LongAdder();

    public VirtualThreadPool(String name, int maxCarrierThreads) {
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.monitorThread = Thread.ofVirtual()
            .name(name + "-monitor")
            .start(this::monitoringLoop);
    }

    // Submit task with context propagation
    public <T> CompletableFuture<T> submit(
        String caseID,
        Callable<T> task
    ) {
        activeTasks.incrementAndGet();
        totalTasks.increment();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return ScopedValue.callWhere(
                    ScopedValueSystem.CASE_CONTEXT,
                    new CaseContextImpl(caseID, ...),
                    task::call
                );
            } finally {
                activeTasks.decrementAndGet();
            }
        }, virtualExecutor);
    }

    // Monitoring loop
    private void monitoringLoop() {
        while (true) {
            try {
                Thread.sleep(30_000); // 30 second intervals
                logMetrics();
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void logMetrics() {
        int active = activeTasks.get();
        long total = totalTasks.sum();

        if (active > 0) {
            _logger.debug("VirtualThreadPool: {} active tasks, {} total completed",
                        active, total);
        }
    }
}
```

#### Task 3: Structured Task Executor
```java
// File: src/org/yawlfoundation/yawl/engine/execution/StructuredTaskExecutor.java
public final class StructuredTaskExecutor {

    public <T> List<T> executeInParallel(
        String operationName,
        List<Callable<T>> tasks
    ) throws ExecutionException, InterruptedException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<CompletableFuture<T>> futures = new ArrayList<>();

            // Submit all tasks to structured scope
            for (Callable<T> task : tasks) {
                CompletableFuture<T> future = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return task.call();
                        } catch (Exception e) {
                            throw new CompletionException(e);
                        }
                    },
                    scope.fork()
                );
                futures.add(future);
            }

            // Wait for completion or failure
            scope.join();

            // Collect results
            List<T> results = new ArrayList<>();
            for (CompletableFuture<T> future : futures) {
                try {
                    results.add(future.join());
                } catch (CompletionException e) {
                    throw new ExecutionException("Task failed in " + operationName, e.getCause());
                }
            }
            return results;
        }
    }

    // Execute with timeout
    public <T> T executeWithTimeout(
        String operationName,
        Supplier<T> operation,
        Duration timeout
    ) throws TimeoutException, InterruptedException {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(
                operation::get,
                scope.fork()
            );

            // Wait with timeout
            if (!scope.joinUntil(Instant.now().plus(timeout))) {
                throw new TimeoutException("Operation timed out: " + operationName);
            }

            return future.join();
        }
    }
}
```

### Phase 2: Core Modernization (Weeks 3-4)

#### Task 4: Modernized YNetRunner
```java
// File: src/org/yawlfoundation/yawl/engine/YNetRunnerModernized.java
public class YNetRunnerModernized {

    private final VirtualThreadPool caseThreadPool;
    private final StructuredTaskExecutor taskExecutor;
    private final ScopedValueSystem contextSystem;

    public YNetRunnerModernized(VirtualThreadPool caseThreadPool) {
        this.caseThreadPool = caseThreadPool;
        this.taskExecutor = new StructuredTaskExecutor();
        this.contextSystem = new ScopedValueSystem();
    }

    // Modernized case execution
    public void executeCaseModernized(String caseID) {
        ScopedValue<WorkflowContext> context = ScopedValue.newInstance();

        ScopedValue.callWhere(context,
            WorkflowContext.of(caseID, getSpecificationID(), getEngineInstance()),
            () -> {

                try {
                    // Get enabled tasks
                    List<YWorkItem> enabledTasks = getEnabledTasks(caseID);

                    // Execute in parallel using structured concurrency
                    List<Callable<Void>> tasks = enabledTasks.stream()
                        .map(task -> (Callable<Void>) () -> {
                            executeWorkItemModernized(task);
                            return null;
                        })
                        .collect(Collectors.toList());

                    List<Void> results = taskExecutor.executeInParallel(
                        "execute-case-" + caseID,
                        tasks
                    );

                    // Process results
                    processTaskResults(results);

                } catch (ExecutionException e) {
                    handleTaskFailure(caseID, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Case execution interrupted", e);
                }

                return null;
            });
    }

    // Modernized work item execution
    private void executeWorkItemModernized(YWorkItem workItem) {
        // Create scoped context for work item
        ScopedValue<OperationContext> operationContext = ScopedValue.newInstance();

        ScopedValue.callWhere(operationContext,
            new OperationContextImpl(
                workItem.getTaskID(),
                workItem.getCaseID(),
                UUID.randomUUID().toString(),
                Map.of("workItem", workItem)
            ),
            () -> {

                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

                    // Execute work item in structured scope
                    CompletableFuture<Void> result = CompletableFuture.runAsync(
                        () -> executeSingleWorkItem(workItem),
                        scope.fork()
                    );

                    // Wait for completion
                    scope.join();
                    result.join();

                    // Handle completion
                    workItem.complete();

                } catch (StructuredTaskScope.SubtaskFailureException e) {
                    handleWorkItemFailure(workItem, e.exception());
                }

                return null;
            });
    }
}
```

#### Task 5: Context Propagation
```java
// File: src/org/yawlfoundation/yawl/engine/context/ContextPropagation.java
public final class ContextPropagation {

    // Propagate context to external operations
    public <T> T executeExternalOperation(
        String caseID,
        String operationName,
        Supplier<T> operation
    ) {
        // Bind case context
        ScopedValue<CaseContext> caseContext = ScopedValue.newInstance();

        ScopedValue.callWhere(caseContext,
            new CaseContextImpl(caseID, getCurrentCaseState(caseID)),
            () -> {

                // Execute with tracing
                return TracingUtils.traceVirtualThread(
                    "external-operation-" + operationName,
                    caseID,
                    operation::get
                );
            });
    }

    // Database operations with context
    public <T> T executeDatabaseTransaction(
        String caseID,
        Supplier<T> operation
    ) throws SQLException {

        // Structured database transaction with context
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            CompletableFuture<T> result = CompletableFuture.supplyAsync(
                () -> {
                    try (Connection conn = dataSource.getConnection()) {
                        conn.setAutoCommit(false);

                        // Context-bound operation
                        T resultValue = ScopedValue.callWhere(
                            ScopedValueSystem.CASE_CONTEXT,
                            new CaseContextImpl(caseID, ...),
                            () -> operation.get()
                        );

                        conn.commit();
                        return resultValue;

                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                },
                scope.fork()
            );

            try {
                scope.join();
                return result.join();
            } catch (StructuredTaskScope.SubtaskFailureException e) {
                throw new SQLException("Transaction failed", e.exception());
            }
        }
    }
}
```

### Phase 3: Integration and Optimization (Weeks 5-6)

#### Task 6: Integration with Existing Components
```java
// File: src/org/yawlfoundation/yawl/engine/integration/ModernEngineGateway.java
public class ModernEngineGateway {

    private final YNetRunnerModernized modernRunner;
    private final VirtualThreadPool virtualPool;

    public ModernEngineGateway() {
        this.virtualPool = new VirtualThreadPool("engine-gateway", 100);
        this.modernRunner = new YNetRunnerModernized(virtualPool);
    }

    // Bridge between old and new execution models
    public void startCaseModernized(String caseID, String specID) {
        // Use modern executor but maintain compatibility
        virtualPool.submit(caseID, () -> {
            modernRunner.executeCaseModernized(caseID);
            return null;
        }).whenComplete((result, error) -> {
            if (error != null) {
                handleCaseFailure(caseID, error);
            }
        });
    }

    // Handle both old and new work item completion
    public void completeWorkItem(YWorkItem workItem) {
        // Check if work item uses modern execution
        if (workItem.isModernExecution()) {
            completeWorkItemModernized(workItem);
        } else {
            completeWorkItemLegacy(workItem);
        }
    }
}
```

#### Task 7: Monitoring and Metrics
```java
// File: src/org/yawlfoundation/yawl/engine/monitoring/VirtualThreadMetrics.java
public final class VirtualThreadMetrics {

    private final MeterRegistry meterRegistry;
    private final VirtualThreadPool pool;

    public VirtualThreadMetrics(MeterRegistry meterRegistry, VirtualThreadPool pool) {
        this.meterRegistry = meterRegistry;
        this.pool = pool;
        registerMetrics();
    }

    private void registerMetrics() {
        // Virtual thread pool metrics
        Gauge.builder("virtual.thread.pool.active", pool, p -> p.getActiveTasks())
            .register(meterRegistry);

        Gauge.builder("virtual.thread.pool.completed", pool, p -> p.getCompletedTasks())
            .register(meterRegistry);

        // Case-specific metrics
        Gauge.builder("cases.active", YNetRunner::getActiveCaseCount)
            .register(meterRegistry);

        // Performance metrics
        DistributionSummary.builder("virtual.thread.latency")
            .description("Virtual thread task execution latency")
            .register(meterRegistry);

        // Error metrics
        Counter.builder("virtual.thread.errors")
            .description("Virtual thread execution errors")
            .register(meterRegistry);
    }

    // Health check for virtual thread pool
    public Health checkPoolHealth() {
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        boolean healthy = metrics.carrierUtilizationPercent() < 80.0;

        return healthy ?
            Health.up().withDetail("utilization", metrics.carrierUtilizationPercent()).build() :
            Health.down().withDetail("utilization", metrics.carrierUtilizationPercent()).build();
    }
}
```

---

## 3. Implementation Patterns

### 3.1 Context-Bound Operations

```java
// Pattern 1: Case execution with context
public class CaseExecutionPattern {

    public void executeCaseWithContext(String caseID) {
        // Create context and bind it
        WorkflowContext context = WorkflowContext.of(caseID, getSpecID(), getEngineNbr());

        ScopedValue.callWhere(ScopedValueSystem.WORKFLOW_CONTEXT, context, () -> {
            // All operations inherit context
            initializeCase(caseID);
            executeTasks(caseID);
            finalizeCase(caseID);
            return null;
        });
    }

    // Nested context operations
    private void executeTasks(String caseID) {
        List<Callable<Void>> tasks = getEnabledTasks(caseID);

        tasks.forEach(task -> ScopedValue.callWhere(
            ScopedValueSystem.OPERATION_CONTEXT,
            new OperationContextImpl("execute-tasks", caseID, UUID.randomUUID().toString()),
            () -> {
                task.call();
                return null;
            }
        ));
    }
}
```

### 3.2 Error Handling Patterns

```java
// Pattern 2: Structured error handling
public class ErrorHandlingPattern {

    public <T> T executeWithRetry(
        String operationName,
        Supplier<T> operation,
        int maxRetries
    ) {
        int attempt = 0;
        Exception lastError = null;

        while (attempt <= maxRetries) {
            try {
                return StructuredUtils.executeWithTimeout(
                    operationName,
                    operation,
                    Duration.ofSeconds(30)
                );
            } catch (TimeoutException e) {
                lastError = e;
                attempt++;
                if (attempt <= maxRetries) {
                    waitBeforeRetry(attempt);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted", e);
            }
        }

        throw new RuntimeException("Operation failed after " + maxRetries + " attempts", lastError);
    }

    private void waitBeforeRetry(int attempt) {
        try {
            long waitTime = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
            Thread.sleep(Math.min(waitTime, 30000)); // Cap at 30 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

### 3.3 Resource Management Patterns

```java
// Pattern 3: Resource management with structured concurrency
public class ResourceManagementPattern {

    public void executeWithResources(String caseID) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Bind case context
            ScopedValue<CaseContext> context = ScopedValue.newInstance();
            ScopedValue.callWhere(context,
                new CaseContextImpl(caseID, ...),
                () -> {

                    // Execute multiple operations with shared resources
                    CompletableFuture<CaseResult> result1 = CompletableFuture.supplyAsync(
                        () -> executeOperation1(caseID),
                        scope.fork()
                    );

                    CompletableFuture<CaseResult> result2 = CompletableFuture.supplyAsync(
                        () -> executeOperation2(caseID),
                        scope.fork()
                    );

                    // Wait for all operations
                    scope.join();

                    // Combine results
                    return combineResults(result1.join(), result2.join());
                });
        }
    }
}
```

---

## 4. Testing Strategy

### 4.1 Unit Testing

```java
// Test class for ScopedValue system
class ScopedValueSystemTest {

    @Test
    void testContextBinding() {
        ScopedValue<WorkflowContext> context = ScopedValue.newInstance();

        String caseID = "test-case-123";
        WorkflowContext data = WorkflowContext.of(caseID, "test-spec", 1);

        // Test context binding
        String result = ScopedValue.callWhere(context, data, () -> {
            // Verify context is available
            assertEquals(caseID, ScopedValueSystem.getCurrentCaseID());
            return "success";
        });

        assertEquals("success", result);
    }

    @Test
    void testContextInheritance() {
        ScopedValue<WorkflowContext> parentContext = ScopedValue.newInstance();

        String parentCaseID = "parent-case";
        WorkflowContext parentData = WorkflowContext.of(parentCaseID, "parent-spec", 1);

        // Test child thread inherits context
        String result = ScopedValue.callWhere(parentContext, parentData, () -> {
            return Thread.ofVirtual()
                .name("child-thread")
                .start(() -> {
                    // Child should inherit parent context
                    assertEquals(parentCaseID, ScopedValueSystem.getCurrentCaseID());
                    return "child-success";
                }).get();
        });

        assertEquals("child-success", result);
    }
}
```

### 4.2 Integration Testing

```java
// Integration test for complete workflow
class WorkflowIntegrationTest {

    @Test
    void testCompleteWorkflowExecution() throws Exception {
        // Setup
        VirtualThreadPool pool = new VirtualThreadPool("test-pool", 10);
        YNetRunnerModernized runner = new YNetRunnerModernized(pool);

        // Execute workflow
        String caseID = UUID.randomUUID().toString();

        try {
            runner.executeCaseModernized(caseID);

            // Verify case completion
            assertTrue(isCaseCompleted(caseID));

        } finally {
            pool.shutdown();
        }
    }

    @Test
    void testConcurrentWorkflows() throws Exception {
        // Setup
        VirtualThreadPool pool = new VirtualThreadPool("concurrent-pool", 50);
        YNetRunnerModernized runner = new YNetRunnerModernized(pool);

        // Execute multiple cases concurrently
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int caseCount = 100;

        for (int i = 0; i < caseCount; i++) {
            String caseID = "case-" + i;
            futures.add(pool.submit(caseID, () -> {
                runner.executeCaseModernized(caseID);
                return null;
            }));
        }

        // Wait for all cases to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify all cases completed
        for (int i = 0; i < caseCount; i++) {
            String caseID = "case-" + i;
            assertTrue(isCaseCompleted(caseID));
        }
    }
}
```

### 4.3 Performance Testing

```java
// Performance test for virtual threads
class PerformanceTest {

    @Test
    void testVirtualThreadPerformance() {
        // Test setup
        VirtualThreadPool pool = new VirtualThreadPool("performance-pool", 100);
        int taskCount = 10_000;
        List<CompletableFuture<String>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // Submit many tasks
        for (int i = 0; i < taskCount; i++) {
            String taskID = "task-" + i;
            futures.add(pool.submit("test-case", () -> {
                // Simulate work
                Thread.sleep(1);
                return "completed-" + taskID;
            }));
        }

        // Wait for all tasks
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (double) taskCount / duration * 1000;

        System.out.printf("Executed %d tasks in %d ms (%.1f tasks/sec)%n",
            taskCount, duration, throughput);

        // Verify all tasks completed
        assertEquals(taskCount, futures.size());

        pool.shutdown();
    }
}
```

---

## 5. Quality Assurance

### 5.1 Code Review Checklist

- [ ] No `synchronized` blocks on virtual threads
- [ ] All context propagation uses `ScopedValue`
- [ ] Structured concurrency is used for parallel operations
- [ ] Proper error handling and cleanup
- [ ] Comprehensive unit tests
- [ ] Performance tests included
- [ ] Memory usage monitored

### 5.2 Performance Metrics to Monitor

1. **Virtual Thread Metrics**
   - Active virtual thread count
   - Carrier thread utilization
   - Task latency distribution
   - Memory usage patterns

2. **Case Execution Metrics**
   - Case completion time
   - Task execution parallelism
   - Error rates
   - Resource contention

3. **System Metrics**
   - CPU utilization
   - Memory usage
   - Garbage collection patterns
   - Network I/O

### 5.3 Troubleshooting Common Issues

```java
// Common issue: Virtual thread pinning
public class VirtualThreadPinning {

    // Issue 1: Blocking I/O in virtual thread
    public void avoidBlockingIO() {
        // ❌ WRONG: Blocks virtual thread
        String result = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString())
            .body();

        // ✅ CORRECT: Use non-blocking or async I/O
        CompletableFuture<String> future = HttpClient.newHttpClient()
            .sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body);
    }

    // Issue 2: Long-running computations
    public void avoidCPUIntensiveTasks() {
        // ❌ WRONG: CPU-intensive in virtual thread
        for (int i = 0; i < 1000000; i++) {
            computeIntensiveTask();
        }

        // ✅ CORRECT: Offload to separate thread pool
        ExecutorService cpuPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
        );
        cpuPool.submit(() -> {
            for (int i = 0; i < 1000000; i++) {
                computeIntensiveTask();
            }
        });
    }
}
```

---

## 6. Deployment and Operations

### 6.1 Configuration Properties

```properties
# Virtual thread configuration
virtual.thread.pool.name=workflow-engine
virtual.thread.pool.max-carriers=100
virtual.thread.pool.min-carriers=10
virtual.thread.pool.sampling-interval=30
virtual.thread.pool.carrier-threshold=0.8

# Memory optimization
compact.object.headers=true
gc.heap.min.free=10m
gc.heap.max.free=20m
gc.heap.init.size=256m

# Monitoring
metrics.enabled=true
metrics.export.prometheus=true
metrics.export.interval=30
```

### 6.2 Monitoring Dashboard

```java
// Dashboard metrics collector
public class DashboardMetrics {

    private final MeterRegistry meterRegistry;
    private final VirtualThreadPool pool;

    public DashboardMetrics(MeterRegistry meterRegistry, VirtualThreadPool pool) {
        this.meterRegistry = meterRegistry;
        this.pool = pool;
    }

    public void registerDashboardMetrics() {
        // Virtual thread pool status
        Gauge.builder("virtual.thread.pool.status", pool, this::getPoolStatus)
            .register(meterRegistry);

        // Case distribution
        DistributionSummary.builder("case.execution.time")
            .description("Case execution time distribution")
            .register(meterRegistry);

        // Task success/failure rates
        Counter.builder("task.success")
            .register(meterRegistry);

        Counter.builder("task.failure")
            .register(meterRegistry);
    }

    private String getPoolStatus(VirtualThreadPool pool) {
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();
        if (metrics.carrierUtilizationPercent() > 80) {
            return "OVERLOADED";
        } else if (metrics.carrierUtilizationPercent() > 60) {
            return "HIGH";
        } else if (metrics.carrierUtilizationPercent() > 40) {
            return "MODERATE";
        } else {
            return "LOW";
        }
    }
}
```

### 6.3 Scaling Guidelines

```java
// Scaling recommendations
public class ScalingGuidelines {

    public static class ScalingConfig {
        private int maxCarrierThreads;
        private double targetUtilization;
        private long monitoringInterval;

        // Constructor, getters, setters
    }

    public ScalingConfig recommendScaling(VirtualThreadPool pool) {
        VirtualThreadPool.CostMetrics metrics = pool.getCostMetrics();

        // Calculate recommended scaling
        double currentUtilization = metrics.carrierUtilizationPercent();
        long currentThroughput = metrics.throughputPerSecond();

        ScalingConfig config = new ScalingConfig();

        if (currentUtilization > 80) {
            // Scale up significantly
            config.setMaxCarrierThreads((int) (maxCarrierThreads * 1.5));
            config.setTargetUtilization(0.7);
        } else if (currentUtilization > 60) {
            // Scale up moderately
            config.setMaxCarrierThreads((int) (maxCarrierThreads * 1.2));
            config.setTargetUtilization(0.6);
        } else if (currentUtilization < 30) {
            // Scale down
            config.setMaxCarrierThreads((int) (maxCarrierThreads * 0.8));
            config.setTargetUtilization(0.5);
        } else {
            // No change
            config.setMaxCarrierThreads(maxCarrierThreads);
            config.setTargetUtilization(0.6);
        }

        return config;
    }
}
```

---

## 7. Conclusion

The implementation guidelines provide a comprehensive roadmap for modernizing the YAWL engine to Java 25. Key success factors include:

1. **Follow the phased approach** - Build infrastructure before migrating core logic
2. **Use the established patterns** - Context binding, structured concurrency, proper error handling
3. **Implement comprehensive testing** - Unit, integration, and performance tests
4. **Monitor continuously** - Track virtual thread metrics and performance
5. **Scale appropriately** - Adjust based on actual usage patterns

By following these guidelines, the YAWL engine will be successfully modernized to leverage Java 25's advanced concurrency features while maintaining compatibility and performance.