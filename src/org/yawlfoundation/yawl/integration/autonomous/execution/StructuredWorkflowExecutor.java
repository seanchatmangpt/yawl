/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Executes autonomous agent work items using Java 25 {@link StructuredTaskScope}.
 *
 * <p>This executor manages concurrent work item processing across multiple
 * autonomous agents within a structured concurrency scope. If any agent task
 * fails, all remaining tasks are cancelled (ShutdownOnFailure policy).</p>
 *
 * <h2>Java 25 Features</h2>
 * <ul>
 *   <li>{@link StructuredTaskScope} for lifecycle-bound concurrent execution</li>
 *   <li>{@link ScopedValue} for workflow context propagation</li>
 *   <li>Virtual threads for lightweight parallel agent calls</li>
 *   <li>Records and sealed interfaces for result modeling</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class StructuredWorkflowExecutor {

    private static final Logger LOG = LogManager.getLogger(StructuredWorkflowExecutor.class);

    /**
     * Scoped value carrying the current case ID through virtual thread hierarchy.
     */
    public static final ScopedValue<String> CURRENT_CASE_ID = ScopedValue.newInstance();

    /**
     * Scoped value carrying the current session handle.
     */
    public static final ScopedValue<String> SESSION_HANDLE = ScopedValue.newInstance();

    private final AtomicLong totalExecutions = new AtomicLong();
    private final AtomicLong totalSuccesses = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final ReentrantLock metricsLock = new ReentrantLock();

    /**
     * A task descriptor for a work item to be executed by an agent.
     *
     * @param workItemId the work item identifier
     * @param agentId    the target agent identifier
     * @param taskData   key-value data for the task
     */
    public record WorkItemTask(
        String workItemId,
        String agentId,
        Map<String, Object> taskData
    ) {
        public WorkItemTask {
            Objects.requireNonNull(workItemId, "workItemId must not be null");
            Objects.requireNonNull(agentId, "agentId must not be null");
            taskData = taskData != null ? Map.copyOf(taskData) : Map.of();
        }
    }

    /**
     * Result of a work item execution.
     */
    public sealed interface WorkItemResult {

        String workItemId();

        /**
         * Successful execution result.
         */
        record Success(
            String workItemId,
            Map<String, Object> output,
            Duration elapsed
        ) implements WorkItemResult {}

        /**
         * Failed execution result.
         */
        record Failure(
            String workItemId,
            String errorMessage,
            Duration elapsed
        ) implements WorkItemResult {}
    }

    /**
     * Aggregate result of executing a batch of work items.
     *
     * @param results     individual results per work item
     * @param totalTime   total wall-clock time for the batch
     * @param successCount number of successful completions
     * @param failureCount number of failures
     */
    public record BatchResult(
        List<WorkItemResult> results,
        Duration totalTime,
        int successCount,
        int failureCount
    ) {
        public boolean allSucceeded() {
            return failureCount == 0;
        }
    }

    /**
     * Functional interface for executing a single work item task.
     */
    @FunctionalInterface
    public interface TaskExecutor {
        Map<String, Object> execute(WorkItemTask task) throws Exception;
    }

    private final TaskExecutor taskExecutor;

    /**
     * Creates a new executor with the given task execution strategy.
     *
     * @param taskExecutor the strategy for executing individual work items
     */
    public StructuredWorkflowExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = Objects.requireNonNull(taskExecutor, "taskExecutor must not be null");
    }

    /**
     * Executes a batch of work items concurrently using structured concurrency.
     *
     * <p>All tasks run on virtual threads within a {@link StructuredTaskScope}.
     * If any task fails, remaining tasks are cancelled. The method blocks until
     * all tasks complete or the timeout expires.</p>
     *
     * @param caseId        the workflow case ID (propagated via ScopedValue)
     * @param sessionHandle the engine session handle (propagated via ScopedValue)
     * @param tasks         the work item tasks to execute
     * @param timeout       maximum time to wait for all tasks
     * @return aggregate result of the batch execution
     */
    public BatchResult executeWorkItems(String caseId, String sessionHandle,
                                         List<WorkItemTask> tasks, Duration timeout) {
        Objects.requireNonNull(tasks, "tasks must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        if (tasks.isEmpty()) {
            return new BatchResult(List.of(), Duration.ZERO, 0, 0);
        }

        LOG.info("Executing {} work items for case {} with timeout {}",
                 tasks.size(), caseId, timeout);

        Instant start = Instant.now();

        List<WorkItemResult> results = ScopedValue
            .where(CURRENT_CASE_ID, caseId)
            .where(SESSION_HANDLE, sessionHandle)
            .call(() -> executeInScope(tasks, timeout));

        Duration totalTime = Duration.between(start, Instant.now());

        int successes = 0;
        int failures = 0;
        for (WorkItemResult result : results) {
            switch (result) {
                case WorkItemResult.Success _ -> successes++;
                case WorkItemResult.Failure _ -> failures++;
            }
        }

        totalExecutions.addAndGet(tasks.size());
        totalSuccesses.addAndGet(successes);
        totalFailures.addAndGet(failures);
        totalDurationMs.addAndGet(totalTime.toMillis());

        LOG.info("Batch complete: {}/{} succeeded in {}", successes, tasks.size(), totalTime);

        return new BatchResult(Collections.unmodifiableList(results), totalTime, successes, failures);
    }

    private List<WorkItemResult> executeInScope(List<WorkItemTask> tasks, Duration timeout)
            throws Exception {

        List<WorkItemResult> results = new ArrayList<>(tasks.size());

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            List<StructuredTaskScope.Subtask<WorkItemResult>> subtasks = new ArrayList<>(tasks.size());

            for (WorkItemTask task : tasks) {
                StructuredTaskScope.Subtask<WorkItemResult> subtask = scope.fork(() -> executeSingle(task));
                subtasks.add(subtask);
            }

            scope.joinUntil(Instant.now().plus(timeout));
            scope.throwIfFailed();

            for (StructuredTaskScope.Subtask<WorkItemResult> subtask : subtasks) {
                results.add(subtask.get());
            }
        } catch (Exception e) {
            LOG.warn("Structured scope interrupted or failed: {}", e.getMessage());
            for (WorkItemTask task : tasks) {
                boolean alreadyRecorded = results.stream()
                    .anyMatch(r -> r.workItemId().equals(task.workItemId()));
                if (!alreadyRecorded) {
                    results.add(new WorkItemResult.Failure(
                        task.workItemId(), "Scope cancelled: " + e.getMessage(), Duration.ZERO));
                }
            }
        }

        return results;
    }

    private WorkItemResult executeSingle(WorkItemTask task) {
        Instant start = Instant.now();
        try {
            Map<String, Object> output = taskExecutor.execute(task);
            Duration elapsed = Duration.between(start, Instant.now());
            return new WorkItemResult.Success(task.workItemId(), output, elapsed);
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            LOG.error("Work item {} failed: {}", task.workItemId(), e.getMessage(), e);
            return new WorkItemResult.Failure(task.workItemId(), e.getMessage(), elapsed);
        }
    }

    /**
     * Returns cumulative execution metrics.
     *
     * @return map of metric name to value
     */
    public Map<String, Long> getMetrics() {
        metricsLock.lock();
        try {
            return Map.of(
                "totalExecutions", totalExecutions.get(),
                "totalSuccesses", totalSuccesses.get(),
                "totalFailures", totalFailures.get(),
                "totalDurationMs", totalDurationMs.get()
            );
        } finally {
            metricsLock.unlock();
        }
    }
}
