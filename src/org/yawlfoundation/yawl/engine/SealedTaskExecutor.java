/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This software is published under the terms of the LGPL licence.
 *
 * YAWL Sealed Task Executor - Java 25 Patterns
 * Pattern: Sealed class hierarchy for task execution
 *
 * Purpose:
 * Ensure compiler-verified exhaustiveness when handling all task types.
 * Replace instanceof checks with pattern matching.
 *
 * Benefits:
 * - Type safety: Compiler checks all task types handled
 * - No unchecked casts
 * - Refactoring safety: Adding new task type requires code updates
 */

package org.yawlfoundation.yawl.engine;

import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YWorkItem;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceBClient;
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Sealed interface defining all possible task executor types.
 *
 * Sealed hierarchy ensures:
 * 1. Compiler enforces all subtypes handled in switch
 * 2. Pattern matching is exhaustive
 * 3. New task types require code updates (safe refactoring)
 */
public sealed interface TaskExecutor
    permits HumanTaskExecutor,
            AutomatedTaskExecutor,
            CompositeTaskExecutor,
            MultiInstanceTaskExecutor {

    /**
     * Execute the task's work item.
     *
     * @param workItem the work item to execute
     * @throws InterruptedException if execution is interrupted
     */
    void execute(YWorkItem workItem) throws InterruptedException;

    /**
     * Get the task being executed.
     */
    YTask getTask();
}

/**
 * Human task executor - requires manual intervention.
 *
 * Pattern: Allocate work item to participant based on allocation rules.
 * Execution: Participant performs task manually, then completes work item.
 */
public final class HumanTaskExecutor implements TaskExecutor {
    private final YTask task;
    private final AllocationRules allocationRules;
    private final InterfaceBClient workItemManager;

    public HumanTaskExecutor(YTask task, AllocationRules rules, InterfaceBClient manager) {
        this.task = task;
        this.allocationRules = rules;
        this.workItemManager = manager;
    }

    @Override
    public void execute(YWorkItem workItem) {
        // Determine participant based on allocation rules
        String participant = allocationRules.selectParticipant(workItem);

        // Allocate to human participant
        workItemManager.allocateWorkItem(workItem, participant);

        // Human completes via separate interface call
        // No further action needed here
    }

    @Override
    public YTask getTask() {
        return task;
    }
}

/**
 * Automated task executor - executed by service/agent on virtual thread.
 *
 * Pattern: Virtual Thread Per Work Item
 * - Creates dedicated virtual thread for each work item
 * - Minimal memory overhead (1KB vs 2MB platform thread)
 * - Thousands of concurrent tasks without pool exhaustion
 * - Automatic OS scheduling
 */
public final class AutomatedTaskExecutor implements TaskExecutor {
    private final YTask task;
    private final WebServiceDecomposition service;
    private final YNetRunner netRunner;
    private final EventAnnouncer announcer;

    public AutomatedTaskExecutor(
            YTask task,
            WebServiceDecomposition service,
            YNetRunner netRunner,
            EventAnnouncer announcer) {
        this.task = task;
        this.service = service;
        this.netRunner = netRunner;
        this.announcer = announcer;
    }

    @Override
    public void execute(YWorkItem workItem) throws InterruptedException {
        // Execute on virtual thread (one thread per work item)
        Thread.ofVirtual()
            .name("task-" + task.getID() + "-" + workItem.getID())
            .start(() -> executeOnVirtualThread(workItem));
    }

    private void executeOnVirtualThread(YWorkItem workItem) {
        try {
            // Call external service (cloud deployment, etc.)
            String result = service.execute(workItem.getDataList());

            // Mark work item complete
            workItem.setData(result);
            netRunner.completeWorkItem(workItem);

            announcer.announceWorkItemCompletion(workItem.getID());
        } catch (Exception e) {
            // Refusal with error code
            String refusalCode = deriveRefusalCode(e);
            workItem.setRefusalCode(refusalCode);
            netRunner.refuseWorkItem(workItem, refusalCode, e.getMessage());

            announcer.announceWorkItemRefusal(workItem.getID(), refusalCode);
        }
    }

    private String deriveRefusalCode(Exception e) {
        if (e instanceof TimeoutException) {
            return "TASK_TIMEOUT_EXCEEDED";
        } else if (e instanceof IllegalArgumentException) {
            return "TASK_INVALID_INPUT";
        } else {
            return "TASK_EXECUTION_FAILED";
        }
    }

    @Override
    public YTask getTask() {
        return task;
    }
}

/**
 * Composite task executor - creates sub-case from nested net (subnet).
 *
 * Pattern: Nested Workflows
 * - Composite task references a subnet (YNet)
 * - Spawns sub-case(s) for nested execution
 * - Monitors sub-case completion to complete parent task
 */
public final class CompositeTaskExecutor implements TaskExecutor {
    private final YTask task;
    private final YNet subnet;
    private final YEngine engine;
    private final EventAnnouncer announcer;

    public CompositeTaskExecutor(YTask task, YNet subnet, YEngine engine, EventAnnouncer announcer) {
        this.task = task;
        this.subnet = subnet;
        this.engine = engine;
        this.announcer = announcer;
    }

    @Override
    public void execute(YWorkItem workItem) throws InterruptedException {
        // Create sub-case from the subnet decomposition
        YIdentifier subCaseId = engine.createSubCase(
            subnet,
            task.getID(),
            workItem.getDataList()
        );

        // Register monitor to track sub-case completion
        SubCaseMonitor monitor = new SubCaseMonitor() {
            @Override
            public void onSubCaseComplete(YIdentifier subId, Map<String, Object> outputData) {
                workItem.setData(outputData);
                engine.completeWorkItem(workItem);
                announcer.announceWorkItemCompletion(workItem.getID());
            }

            @Override
            public void onSubCaseFailed(YIdentifier subId, String reason) {
                workItem.setRefusalCode("SUBCASE_FAILED");
                engine.refuseWorkItem(workItem, "SUBCASE_FAILED", reason);
                announcer.announceWorkItemRefusal(workItem.getID(), "SUBCASE_FAILED");
            }
        };

        engine.registerSubcaseMonitor(workItem.getID(), subCaseId, monitor);
    }

    @Override
    public YTask getTask() {
        return task;
    }
}

/**
 * Multi-instance task executor - repeats for each item in collection.
 *
 * Pattern: Multiple Instantiation
 * - Splits single work item into multiple instances
 * - One instance per collection item
 * - Parallel or sequential execution
 * - Joins results when all instances complete
 */
public final class MultiInstanceTaskExecutor implements TaskExecutor {
    private final YTask task;
    private final MultiInstanceType miType;
    private final YNetRunner netRunner;
    private final EventAnnouncer announcer;

    public MultiInstanceTaskExecutor(
            YTask task,
            MultiInstanceType miType,
            YNetRunner netRunner,
            EventAnnouncer announcer) {
        this.task = task;
        this.miType = miType;
        this.netRunner = netRunner;
        this.announcer = announcer;
    }

    @Override
    public void execute(YWorkItem workItem) throws InterruptedException {
        // Get collection to iterate over
        java.util.Collection<?> items = getCollectionFromWorkItem(workItem);

        if (miType.isParallel()) {
            executeParallel(workItem, items);
        } else {
            executeSequential(workItem, items);
        }
    }

    private void executeParallel(YWorkItem workItem, java.util.Collection<?> items)
            throws InterruptedException {
        // Use structured concurrency for parallel execution
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            var tasks = items.stream()
                .map(item -> scope.fork(() -> executeInstance(workItem, item)))
                .toList();

            scope.join();
            scope.throwIfFailed();

            // Aggregate results
            Map<String, Object> results = tasks.stream()
                .collect(java.util.stream.Collectors.toMap(
                    t -> t.toString(),
                    java.util.concurrent.Callable::call
                ));

            workItem.setData(results);
            netRunner.completeWorkItem(workItem);
        }
    }

    private void executeSequential(YWorkItem workItem, java.util.Collection<?> items)
            throws InterruptedException {
        Map<String, Object> results = new java.util.LinkedHashMap<>();

        for (Object item : items) {
            Object result = executeInstance(workItem, item);
            results.put(item.toString(), result);
        }

        workItem.setData(results);
        netRunner.completeWorkItem(workItem);
    }

    private Object executeInstance(YWorkItem workItem, Object item) {
        // Execute task for single collection item
        return netRunner.executeTaskForItem(task, workItem, item);
    }

    private java.util.Collection<?> getCollectionFromWorkItem(YWorkItem workItem) {
        return (java.util.Collection<?>) workItem.getData().get("items");
    }

    @Override
    public YTask getTask() {
        return task;
    }
}

/**
 * Task executor factory with pattern matching dispatcher.
 *
 * Usage:
 * ```
 * TaskExecutor executor = TaskExecutorFactory.create(task);
 * executor.execute(workItem);
 * ```
 *
 * The factory automatically determines task type and creates appropriate executor.
 * Pattern matching in dispatch ensures exhaustiveness:
 * ```
 * switch (executor) {
 *     case HumanTaskExecutor h -> handleHuman(h);
 *     case AutomatedTaskExecutor a -> handleAutomated(a);
 *     case CompositeTaskExecutor c -> handleComposite(c);
 *     case MultiInstanceTaskExecutor m -> handleMultiInstance(m);
 * }
 * ```
 */
public class TaskExecutorFactory {
    private final YEngine engine;
    private final InterfaceBClient workItemManager;
    private final EventAnnouncer announcer;

    public TaskExecutorFactory(
            YEngine engine,
            InterfaceBClient workItemManager,
            EventAnnouncer announcer) {
        this.engine = engine;
        this.workItemManager = workItemManager;
        this.announcer = announcer;
    }

    /**
     * Create appropriate executor for task type.
     *
     * @param task the task to create executor for
     * @return executor matching task type (sealed hierarchy)
     */
    public TaskExecutor create(YTask task) {
        if (task.isHumanTask()) {
            return new HumanTaskExecutor(
                task,
                AllocationRules.forTask(task),
                workItemManager
            );
        }

        if (task.isCompositeTask()) {
            YNet subnet = task.getDecomposition();
            return new CompositeTaskExecutor(task, subnet, engine, announcer);
        }

        if (task.isMultiInstance()) {
            MultiInstanceType miType = task.getMultiInstanceType();
            return new MultiInstanceTaskExecutor(task, miType, engine.getNetRunner(task.getParentNet().getID()), announcer);
        }

        // Default: automated task
        WebServiceDecomposition service = task.getDecomposition();
        return new AutomatedTaskExecutor(
            task,
            service,
            engine.getNetRunner(task.getParentNet().getID()),
            announcer
        );
    }

    /**
     * Dispatch executor with pattern matching.
     *
     * Example usage:
     * ```
     * TaskExecutor executor = factory.create(task);
     * factory.dispatch(executor, workItem);
     * ```
     */
    public void dispatch(TaskExecutor executor, YWorkItem workItem) throws InterruptedException {
        switch (executor) {
            case HumanTaskExecutor human -> {
                announcer.announceWorkItemAllocation(workItem.getID(), "human");
                human.execute(workItem);
            }

            case AutomatedTaskExecutor automated -> {
                announcer.announceWorkItemAllocation(workItem.getID(), "automated");
                announcer.announceWorkItemStart(workItem.getID());
                automated.execute(workItem);
            }

            case CompositeTaskExecutor composite -> {
                announcer.announceWorkItemAllocation(workItem.getID(), "composite");
                announcer.announceWorkItemStart(workItem.getID());
                composite.execute(workItem);
            }

            case MultiInstanceTaskExecutor multiInstance -> {
                announcer.announceWorkItemAllocation(workItem.getID(), "multi-instance");
                announcer.announceWorkItemStart(workItem.getID());
                multiInstance.execute(workItem);
            }
        }
    }
}

// Supporting interfaces

interface AllocationRules {
    static AllocationRules forTask(YTask task) {
        return new AllocationRulesImpl(task);
    }

    String selectParticipant(YWorkItem workItem);

    class AllocationRulesImpl implements AllocationRules {
        private final YTask task;

        public AllocationRulesImpl(YTask task) {
            this.task = task;
        }

        @Override
        public String selectParticipant(YWorkItem workItem) {
            // Role-based allocation logic
            String role = task.getAllocationRolesByName().stream()
                .findFirst()
                .orElse("default");
            return role;
        }
    }
}

interface WebServiceDecomposition {
    String execute(java.util.Map<String, Object> inputData) throws Exception;
}

interface MultiInstanceType {
    boolean isParallel();
    String getIterationVariable();
}

interface SubCaseMonitor {
    void onSubCaseComplete(YIdentifier subId, java.util.Map<String, Object> outputData);
    void onSubCaseFailed(YIdentifier subId, String reason);
}

interface EventAnnouncer {
    void announceWorkItemCompletion(String workItemId);
    void announceWorkItemRefusal(String workItemId, String refusalCode);
    void announceWorkItemAllocation(String workItemId, String type);
    void announceWorkItemStart(String workItemId);
}

class YIdentifier {
    // Placeholder
}

class TimeoutException extends Exception {}
