/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.validation;

import java.lang.ScopedValue;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * StructuredTaskScope Integration Tester
 *
 * Tests Java 25 StructuredTaskScope integration for coordinated operations
 * in the YAWL actor model, focusing on virtual thread behavior and
 * structured concurrency patterns.
 *
 * <p>Tests:</p>
 * <ul>
 *   <li>StructuredTaskScope.ShutdownOnFailure with virtual threads</li>
 *   <li>StructuredTaskScope.ShutdownOnSuccess with virtual threads</li>
 *   <li>StructuredTaskScope with timeout handling</li>
 *   <li>ScopedValue context propagation across scopes</li>
 *   <li>Automatic cancellation of remaining tasks</li>
 *   <li>Parent-child virtual thread relationships</li>
 *   <li>Performance metrics for structured operations</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class StructuredTaskScopeIntegrationTester {

    private static final Logger _logger = LogManager.getLogger(StructuredTaskScopeIntegrationTester.class);

    // Test configurations
    private static final int[] SCOPE_SIZES = {10, 50, 100, 500, 1000, 5000};
    private static final Duration[] TIMEOUTS = {
        Duration.ofMillis(100),
        Duration.ofSeconds(1),
        Duration.ofSeconds(5),
        Duration.ofSeconds(10)
    };

    // ScopedValues for context propagation
    private static final ScopedValue<String> CASE_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> TASK_ID = ScopedValue.newInstance();
    private static final ScopedValue<String> WORKITEM_ID = ScopedValue.newInstance();
    private static final ScopedValue<Context> TRACE_CONTEXT = ScopedValue.newInstance();

    // Test metrics
    private final AtomicLong totalTasksExecuted = new AtomicLong(0);
    private final AtomicLong successfulTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong cancelledTasks = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong scopeCreationTime = new AtomicLong(0);
    private final AtomicLong taskExecutionTime = new AtomicLong(0);
    private final AtomicInteger activeScopes = new AtomicInteger(0);
    private final AtomicInteger nestedScopes = new AtomicInteger(0);

    private final Tracer tracer;

    public StructuredTaskScopeIntegrationTester(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Run comprehensive StructuredTaskScope integration tests.
     */
    public IntegrationTestResults runIntegrationTests() {
        _logger.info("Starting StructuredTaskScope integration tests");

        IntegrationTestResults results = new IntegrationTestResults();

        // Test different scope sizes
        for (int scopeSize : SCOPE_SIZES) {
            _logger.info("Testing scope size: {}", scopeSize);
            results.addScopeSizeResults(scopeSize, testScopeSizes(scopeSize));
        }

        // Test different timeout configurations
        for (Duration timeout : TIMEOUTS) {
            _logger.info("Testing timeout: {}", timeout);
            results.addTimeoutResults(timeout, testTimeouts(timeout));
        }

        // Test nested scopes
        _logger.info("Testing nested scopes");
        results.setNestedScopeResults(testNestedScopes());

        // Test context propagation
        _logger.info("Testing context propagation");
        results.setContextResults(testContextPropagation());

        // Test cancellation behavior
        _logger.info("Testing cancellation behavior");
        results.setCancellationResults(testCancellationBehavior());

        results.setSummary(generateSummary(results));

        _logger.info("StructuredTaskScope integration tests complete");

        return results;
    }

    /**
     * Test different scope sizes with virtual threads.
     */
    private List<ScopeSizeTestResult> testScopeSizes(int scopeSize) {
        List<ScopeSizeTestResult> results = new ArrayList<>();

        // Test ShutdownOnFailure
        var shutdownOnFailureResult = testWithShutdownOnFailure(scopeSize);
        results.add(shutdownOnFailureResult);

        // Test ShutdownOnSuccess
        var shutdownOnSuccessResult = testWithShutdownOnSuccess(scopeSize);
        results.add(shutdownOnSuccessResult);

        // Test with concurrent limits
        var concurrentResult = testWithConcurrentLimit(scopeSize, Math.min(100, scopeSize));
        results.add(concurrentResult);

        return results;
    }

    /**
     * Test StructuredTaskScope.ShutdownOnFailure with virtual threads.
     */
    private ScopeSizeTestResult testWithShutdownOnFailure(int scopeSize) {
        Instant startTime = Instant.now();
        List<String> results = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

        scopeCreationTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class)) {

            activeScopes.incrementAndGet();

            // Submit tasks to scope
            for (int i = 0; i < scopeSize; i++) {
                final int taskId = i;
                scope.fork(() -> executeTask(
                    "shutdown-failure-" + taskId,
                    () -> {
                        // Simulate work with some failures
                        if (taskId % 10 == 0) {
                            throw new RuntimeException("Simulated task failure " + taskId);
                        }
                        return "result-" + taskId;
                    }
                ));
            }

            // Wait for completion (will shutdown on first failure)
            scope.join();
            taskExecutionTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

            // Process results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                switch (subtask.state()) {
                    case SUCCESS:
                        results.add(subtask.get());
                        successfulTasks.incrementAndGet();
                        break;
                    case FAILED:
                        failures.add(subtask.exception());
                        failedTasks.incrementAndGet();
                        break;
                    case CANCELLED:
                        cancelledTasks.incrementAndGet();
                        break;
                }
            }

        } catch (Exception e) {
            _logger.warn("Scope execution failed: {}", e.getMessage());
            failedTasks.incrementAndGet();
        } finally {
            activeScopes.decrementAndGet();
        }

        return new ScopeSizeTestResult(
            scopeSize,
            "ShutdownOnFailure",
            results.size(),
            failures.size(),
            cancelledTasks.get(),
            Duration.between(startTime, Instant.now()),
            results
        );
    }

    /**
     * Test StructuredTaskScope.ShutdownOnSuccess with virtual threads.
     */
    private ScopeSizeTestResult testWithShutdownOnSuccess(int scopeSize) {
        Instant startTime = Instant.now();
        List<String> results = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

        scopeCreationTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnSuccess.class)) {

            activeScopes.incrementAndGet();

            // Submit tasks to scope (will shutdown on first success)
            for (int i = 0; i < scopeSize; i++) {
                final int taskId = i;
                scope.fork(() -> executeTask(
                    "shutdown-success-" + taskId,
                    () -> {
                        // Simulate work - first few succeed, rest cancelled
                        if (taskId < 3) { // First 3 succeed
                            return "result-" + taskId;
                        }
                        // Rest are cancelled when first succeeds
                        try {
                            Thread.sleep(1000); // Simulate long work
                            return "should-not-reach";
                        } catch (InterruptedException e) {
                            throw new RuntimeException("Task cancelled", e);
                        }
                    }
                ));
            }

            // Wait for completion
            scope.join();
            taskExecutionTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

            // Process results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                switch (subtask.state()) {
                    case SUCCESS:
                        results.add(subtask.get());
                        successfulTasks.incrementAndGet();
                        break;
                    case FAILED:
                        failures.add(subtask.exception());
                        failedTasks.incrementAndGet();
                        break;
                    case CANCELLED:
                        cancelledTasks.incrementAndGet();
                        break;
                }
            }

        } catch (Exception e) {
            _logger.warn("Scope execution failed: {}", e.getMessage());
            failedTasks.incrementAndGet();
        } finally {
            activeScopes.decrementAndGet();
        }

        return new ScopeSizeTestResult(
            scopeSize,
            "ShutdownOnSuccess",
            results.size(),
            failures.size(),
            cancelledTasks.get(),
            Duration.between(startTime, Instant.now()),
            results
        );
    }

    /**
     * Test StructuredTaskScope with concurrency limits.
     */
    private ScopeSizeTestResult testWithConcurrentLimit(int totalTasks, int maxConcurrent) {
        Instant startTime = Instant.now();
        List<String> results = new ArrayList<>();
        List<Exception> failures = new ArrayList<>();

        scopeCreationTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class)) {

            activeScopes.incrementAndGet();
            Semaphore concurrencyLimiter = new Semaphore(maxConcurrent);

            // Submit tasks with concurrency control
            for (int i = 0; i < totalTasks; i++) {
                final int taskId = i;
                scope.fork(() -> {
                    try {
                        concurrencyLimiter.acquire();
                        try {
                            return executeTask(
                                "concurrent-" + taskId,
                                () -> "result-" + taskId
                            );
                        } finally {
                            concurrencyLimiter.release();
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Task cancelled", e);
                    }
                });
            }

            // Wait for completion
            scope.join();
            taskExecutionTime.addAndGet(Duration.between(startTime, Instant.now()).toNanos());

            // Process results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                switch (subtask.state()) {
                    case SUCCESS:
                        results.add(subtask.get());
                        successfulTasks.incrementAndGet();
                        break;
                    case FAILED:
                        failures.add(subtask.exception());
                        failedTasks.incrementAndGet();
                        break;
                    case CANCELLED:
                        cancelledTasks.incrementAndGet();
                        break;
                }
            }

        } catch (Exception e) {
            _logger.warn("Concurrent scope execution failed: {}", e.getMessage());
            failedTasks.incrementAndGet();
        } finally {
            activeScopes.decrementAndGet();
        }

        return new ScopeSizeTestResult(
            totalTasks,
            "ConcurrentLimited",
            results.size(),
            failures.size(),
            cancelledTasks.get(),
            Duration.between(startTime, Instant.now()),
            results
        );
    }

    /**
     * Test timeout behavior of StructuredTaskScope.
     */
    private TimeoutTestResult testTimeouts(Duration timeout) {
        Instant startTime = Instant.now();
        int successCount = 0;
        int timeoutCount = 0;
        int failureCount = 0;

        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class,
                config -> config.withTimeout(timeout))) {

            activeScopes.incrementAndGet();

            // Submit tasks that may timeout
            for (int i = 0; i < 20; i++) {
                final int taskId = i;
                scope.fork(() -> executeTask(
                    "timeout-" + taskId,
                    () -> {
                        // Some tasks complete quickly, some take longer than timeout
                        if (taskId % 3 == 0) {
                            return "quick-result-" + taskId;
                        } else {
                            Thread.sleep(2000); // Longer than most timeouts
                            return "slow-result-" + taskId;
                        }
                    }
                ));
            }

            // Wait for completion with timeout
            try {
                scope.join();
            } catch (StructuredTaskScope.TimeoutException e) {
                _logger.info("Scope timed out as expected: {}", timeout);
            }

            // Process results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                switch (subtask.state()) {
                    case SUCCESS:
                        successCount++;
                        successfulTasks.incrementAndGet();
                        break;
                    case FAILED:
                        failureCount++;
                        failedTasks.incrementAndGet();
                        break;
                    case CANCELLED:
                        timeoutCount++;
                        cancelledTasks.incrementAndGet();
                        break;
                }
            }

        } catch (Exception e) {
            _logger.warn("Timeout test failed: {}", e.getMessage());
            failedTasks.incrementAndGet();
        } finally {
            activeScopes.decrementAndGet();
        }

        return new TimeoutTestResult(
            timeout,
            successCount,
            timeoutCount,
            failureCount,
            Duration.between(startTime, Instant.now())
        );
    }

    /**
     * Test nested StructuredTaskScope scenarios.
     */
    private NestedScopeTestResult testNestedScopes() {
        Instant startTime = Instant.now();
        int parentSuccess = 0;
        int childSuccess = 0;
        int failures = 0;

        try (var parentScope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class)) {

            nestedScopes.incrementAndGet();

            // Parent creates child scopes
            for (int i = 0; i < 5; i++) {
                final int parentId = i;
                parentScope.fork(() -> {
                    try (var childScope = StructuredTaskScope.<String, Void>open(
                            StructuredTaskScope.ShutdownOnFailure.class)) {

                        // Child submits tasks
                        for (int j = 0; j < 10; j++) {
                            final int childId = j;
                            childScope.fork(() -> executeTask(
                                "nested-" + parentId + "-" + childId,
                                () -> "nested-result-" + parentId + "-" + childId
                            ));
                        }

                        // Wait for child completion
                        childScope.join();

                        // Process child results
                        for (StructuredTaskScope.Subtask<String> childTask : childScope.subtasks()) {
                            if (childTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                                childSuccess++;
                            }
                        }

                        return "parent-" + parentId;

                    } catch (Exception e) {
                        throw new RuntimeException("Child scope failed", e);
                    }
                });
            }

            // Wait for parent completion
            parentScope.join();

            // Process parent results
            for (StructuredTaskScope.Subtask<String> parentTask : parentScope.subtasks()) {
                if (parentTask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                    parentSuccess++;
                } else if (parentTask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                    failures++;
                }
            }

        } catch (Exception e) {
            _logger.warn("Nested scope test failed: {}", e.getMessage());
            failures++;
        } finally {
            nestedScopes.decrementAndGet();
        }

        return new NestedScopeTestResult(
            parentSuccess,
            childSuccess,
            failures,
            Duration.between(startTime, Instant.now())
        );
    }

    /**
     * Test ScopedValue context propagation across scopes.
     */
    private ContextPropagationResult testContextPropagation() {
        Instant startTime = Instant.now();
        int contextsPropagated = 0;
        int contextFailures = 0;

        // Set up test context
        Context traceContext = tracer.spanBuilder("context-propagation-test").startSpan().storeInContext();

        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class)) {

            // Submit tasks with context
            for (int i = 0; i < 50; i++) {
                final int taskId = i;
                scope.fork(() -> executeTaskWithContext(
                    "context-" + taskId,
                    () -> {
                        // Verify context propagation
                        String currentCaseId = CASE_ID.getOrDefault("unknown");
                        String currentTaskId = TASK_ID.getOrDefault("unknown");

                        if (!currentCaseId.equals("test-case") || !currentTaskId.equals("test-task")) {
                            throw new RuntimeException("Context not propagated properly");
                        }

                        // Verify trace context
                        Span currentSpan = Span.fromContextOrDefault(Context.current());
                        if (currentSpan == null) {
                            throw new RuntimeException("Trace context not propagated");
                        }

                        contextsPropagated++;
                        return "context-result-" + taskId;
                    },
                    "test-case",
                    "test-task",
                    "workitem-" + taskId,
                    traceContext
                ));
            }

            scope.join();

            // Process results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
                    contextFailures++;
                }
            }

        } catch (Exception e) {
            _logger.warn("Context propagation test failed: {}", e.getMessage());
            contextFailures++;
        }

        return new ContextPropagationResult(
            contextsPropagated,
            contextFailures,
            Duration.between(startTime, Instant.now())
        );
    }

    /**
     * Test cancellation behavior of structured concurrency.
     */
    private CancellationTestResult testCancellationBehavior() {
        Instant startTime = Instant.now();
        int cancelledCount = 0;
        int completedCount = 0;
        int failureCount = 0;

        // Start a scope with some long-running tasks
        try (var scope = StructuredTaskScope.<String, Void>open(
                StructuredTaskScope.ShutdownOnFailure.class)) {

            // Submit tasks with varying completion times
            for (int i = 0; i < 30; i++) {
                final int taskId = i;
                scope.fork(() -> executeTask(
                    "cancellation-" + taskId,
                    () -> {
                        // Tasks 0-9 complete quickly, 10-19 take medium time, 20-29 take longest
                        int delay = (taskId / 10) * 1000 + 100;
                        Thread.sleep(delay);
                        return "result-" + taskId;
                    }
                ));
            }

            // Cancel some tasks after brief delay
            Thread.sleep(200);
            scope.shutdown();

            // Wait for cancellation to complete
            scope.join();

            // Check cancellation results
            for (StructuredTaskScope.Subtask<String> subtask : scope.subtasks()) {
                switch (subtask.state()) {
                    case SUCCESS:
                        completedCount++;
                        break;
                    case CANCELLED:
                        cancelledCount++;
                        break;
                    case FAILED:
                        failureCount++;
                        break;
                }
            }

        } catch (Exception e) {
            _logger.warn("Cancellation test failed: {}", e.getMessage());
            failureCount++;
        }

        return new CancellationTestResult(
            cancelledCount,
            completedCount,
            failureCount,
            Duration.between(startTime, Instant.now())
        );
    }

    /**
     * Execute a task within ScopedValue context.
     */
    private <T> T executeTaskWithContext(String taskName, Callable<T> task,
                                      String caseId, String taskId, String workitemId,
                                      Context traceContext) {
        long startNanos = System.nanoTime();
        totalTasksExecuted.incrementAndGet();

        try (Scope ignored = traceContext.makeCurrent()) {
            return ScopedValue.where(CASE_ID, caseId)
                             .where(TASK_ID, taskId)
                             .where(WORKITEM_ID, workitemId)
                             .where(TRACE_CONTEXT, traceContext)
                             .call(() -> executeTask(taskName, task));
        } finally {
            long duration = System.nanoTime() - startNanos;
            totalLatencyNanos.add(duration);
        }
    }

    /**
     * Execute a task with timing.
     */
    private <T> T executeTask(String taskName, Callable<T> task) {
        long startNanos = System.nanoTime();

        try {
            // Start span for this task
            Span span = tracer.spanBuilder(taskName).startSpan();
            try (Scope ignored = span.makeCurrent()) {
                return task.call();
            } finally {
                span.end();
            }
        } catch (Exception e) {
            // Log or handle exception
            throw new RuntimeException("Task execution failed: " + taskName, e);
        } finally {
            long duration = System.nanoTime() - startNanos;
            totalLatencyNanos.add(duration);
        }
    }

    /**
     * Generate test summary.
     */
    private IntegrationSummary generateSummary(IntegrationTestResults results) {
        long totalTasks = totalTasksExecuted.get();
        double avgLatencyMs = totalTasks > 0 ? totalLatencyNanos.get() / totalTasks / 1_000_000 : 0;

        return new IntegrationSummary(
            totalTasks,
            successfulTasks.get(),
            failedTasks.get(),
            cancelledTasks.get(),
            avgLatencyMs,
            activeScopes.get(),
            nestedScopes.get(),
            calculateThroughput()
        );
    }

    /**
     * Calculate throughput operations per second.
     */
    private double calculateThroughput() {
        long totalTimeSeconds = (taskExecutionTime.get() + scopeCreationTime.get()) / 1_000_000_000;
        return totalTimeSeconds > 0 ? totalTasksExecuted.get() / totalTimeSeconds : 0;
    }

    // Result classes
    public record IntegrationTestResults(
        Map<Integer, List<ScopeSizeTestResult>> scopeSizeResults,
        Map<Duration, TimeoutTestResult> timeoutResults,
        NestedScopeTestResult nestedScopeResults,
        ContextPropagationResult contextResults,
        CancellationTestResult cancellationResults,
        IntegrationSummary summary
    ) {
        public IntegrationTestResults() {
            this(new HashMap<>(), new HashMap<>(), null, null, null, null);
        }

        public void addScopeSizeResults(int size, List<ScopeSizeTestResult> results) {
            scopeSizeResults.put(size, results);
        }

        public void addTimeoutResults(Duration timeout, TimeoutTestResult result) {
            timeoutResults.put(timeout, result);
        }

        public void setNestedScopeResults(NestedScopeTestResult result) {
            this.nestedScopeResults = result;
        }

        public void setContextResults(ContextPropagationResult result) {
            this.contextResults = result;
        }

        public void setCancellationResults(CancellationTestResult result) {
            this.cancellationResults = result;
        }

        public void setSummary(IntegrationSummary summary) {
            this.summary = summary;
        }
    }

    public record ScopeSizeTestResult(
        int scopeSize,
        String scopeType,
        int successfulTasks,
        int failedTasks,
        int cancelledTasks,
        Duration duration,
        List<String> results
    ) {}

    public record TimeoutTestResult(
        Duration timeout,
        int successCount,
        int timeoutCount,
        int failureCount,
        Duration duration
    ) {}

    public record NestedScopeTestResult(
        int parentSuccess,
        int childSuccess,
        int failures,
        Duration duration
    ) {}

    public record ContextPropagationResult(
        int contextsPropagated,
        int contextFailures,
        Duration duration
    ) {}

    public record CancellationTestResult(
        int cancelledCount,
        int completedCount,
        int failureCount,
        Duration duration
    ) {}

    public record IntegrationSummary(
        long totalTasks,
        long successfulTasks,
        long failedTasks,
        long cancelledTasks,
        double avgLatencyMs,
        int activeScopes,
        int nestedScopes,
        double throughput
    ) {
        public double getSuccessRate() {
            return totalTasks > 0 ? (double) successfulTasks / totalTasks * 100 : 0;
        }

        public double getFailureRate() {
            return totalTasks > 0 ? (double) failedTasks / totalTasks * 100 : 0;
        }

        public double getCancellationRate() {
            return totalTasks > 0 ? (double) cancelledTasks / totalTasks * 100 : 0;
        }
    }
}