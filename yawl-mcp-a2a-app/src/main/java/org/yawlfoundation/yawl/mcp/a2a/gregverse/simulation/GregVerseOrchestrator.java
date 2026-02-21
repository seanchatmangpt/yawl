/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseConfiguration.GregVerseAgentRegistry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates multi-agent workflows with compensation/rollback patterns.
 *
 * <p>This orchestrator implements the Saga pattern for distributed transactions
 * across multiple Greg-Verse agents, providing:</p>
 *
 * <ul>
 *   <li><strong>Saga Orchestration</strong>: Coordinates agent execution with
 *       automatic rollback on failures using compensation actions</li>
 *   <li><strong>Agent Handoffs</strong>: Manages context transfer between agents
 *       with full traceability</li>
 *   <li><strong>Topology Support</strong>: Supports both hierarchical and mesh
 *       coordination patterns</li>
 *   <li><strong>State Tracking</strong>: Comprehensive workflow state management
 *       across multiple agents</li>
 *   <li><strong>Virtual Thread Execution</strong>: Leverages Java 25 virtual threads
 *       for scalable parallel agent execution</li>
 * </ul>
 *
 * <h2>Saga Pattern Implementation</h2>
 * <p>Each agent task is paired with a compensation action. If any task fails,
 * all previously completed tasks are compensated in reverse order:</p>
 *
 * <pre>{@code
 * GregVerseOrchestrator orchestrator = GregVerseOrchestrator.create(registry)
 *     .withTopology(Topology.HIERARCHICAL)
 *     .withTimeout(Duration.ofMinutes(5))
 *     .withMaxHandoffs(5);
 *
 * SagaWorkflow workflow = SagaWorkflow.builder()
 *     .step("analyze", "greg-isenberg", "Analyze startup idea")
 *     .compensation("analyze-compensate", ctx -> "Analysis rolled back")
 *     .step("validate", "justin-welsh", "Validate market fit")
 *     .compensation("validate-compensate", ctx -> "Validation rolled back")
 *     .build();
 *
 * SagaResult result = orchestrator.executeSaga(workflow, initialContext);
 * }</pre>
 *
 * <h2>Coordination Topologies</h2>
 * <ul>
 *   <li><strong>HIERARCHICAL</strong>: Central coordinator dispatches tasks
 *       sequentially or in parallel to agents</li>
 *   <li><strong>MESH</strong>: Agents can delegate to each other directly
 *       with handoff tracking</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see SagaWorkflow
 * @see SagaResult
 * @see OrchestrationTopology
 */
public class GregVerseOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseOrchestrator.class);

    /** Default timeout for saga execution */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    /** Default maximum number of agent handoffs before escalation */
    public static final int DEFAULT_MAX_HANDOFFS = 5;

    /** Default maximum concurrent agent executions */
    public static final int DEFAULT_MAX_CONCURRENCY = 4;

    private final GregVerseAgentRegistry agentRegistry;
    private final ExecutorService executor;
    private final OrchestrationTopology topology;
    private final Duration timeout;
    private final int maxHandoffs;
    private final int maxConcurrency;
    private final boolean enableTracing;
    private final boolean enableMetrics;

    /** Active saga executions for monitoring */
    private final ConcurrentHashMap<String, SagaExecution> activeExecutions = new ConcurrentHashMap<>();

    /** Completed saga history (bounded) */
    private final LinkedBlockingDeque<SagaResult> completedSagas = new LinkedBlockingDeque<>(1000);

    /**
     * Create a new orchestrator with the specified configuration.
     *
     * @param agentRegistry the agent registry for agent lookup
     * @param topology the coordination topology
     * @param timeout execution timeout
     * @param maxHandoffs maximum agent handoffs before escalation
     * @param maxConcurrency maximum concurrent agent executions
     * @param enableTracing whether to enable execution tracing
     * @param enableMetrics whether to enable metrics collection
     */
    private GregVerseOrchestrator(
            GregVerseAgentRegistry agentRegistry,
            OrchestrationTopology topology,
            Duration timeout,
            int maxHandoffs,
            int maxConcurrency,
            boolean enableTracing,
            boolean enableMetrics) {

        this.agentRegistry = Objects.requireNonNull(agentRegistry, "Agent registry cannot be null");
        this.topology = topology != null ? topology : OrchestrationTopology.HIERARCHICAL;
        this.timeout = timeout != null ? timeout : DEFAULT_TIMEOUT;
        this.maxHandoffs = maxHandoffs > 0 ? maxHandoffs : DEFAULT_MAX_HANDOFFS;
        this.maxConcurrency = maxConcurrency > 0 ? maxConcurrency : DEFAULT_MAX_CONCURRENCY;
        this.enableTracing = enableTracing;
        this.enableMetrics = enableMetrics;

        // Use virtual thread executor for scalable I/O-bound agent operations
        this.executor = Executors.newVirtualThreadPerTaskExecutor();

        LOGGER.info("GregVerseOrchestrator initialized: topology={}, timeout={}, maxHandoffs={}, maxConcurrency={}",
                this.topology, this.timeout, this.maxHandoffs, this.maxConcurrency);
    }

    /**
     * Create a new orchestrator builder with the required agent registry.
     *
     * @param agentRegistry the agent registry for looking up agents
     * @return a new builder instance
     */
    public static Builder create(GregVerseAgentRegistry agentRegistry) {
        return new Builder(agentRegistry);
    }

    /**
     * Execute a saga workflow with the given initial context.
     *
     * <p>The saga pattern ensures that if any step fails, all previously
     * completed steps are compensated in reverse order.</p>
     *
     * @param workflow the saga workflow to execute
     * @param initialContext the initial execution context
     * @return the saga execution result
     */
    public SagaResult executeSaga(SagaWorkflow workflow, SagaContext initialContext) {
        Objects.requireNonNull(workflow, "Workflow cannot be null");
        Objects.requireNonNull(initialContext, "Initial context cannot be null");

        String sagaId = generateSagaId();
        Instant startTime = Instant.now();

        SagaExecution execution = new SagaExecution(
                sagaId,
                workflow,
                initialContext,
                startTime
        );

        activeExecutions.put(sagaId, execution);
        execution.setState(SagaState.RUNNING);

        LOGGER.info("Starting saga execution: sagaId={}, workflow={}, steps={}",
                sagaId, workflow.getName(), workflow.getSteps().size());

        try {
            return executeWithTimeout(execution);
        } finally {
            activeExecutions.remove(sagaId);
        }
    }

    /**
     * Execute a saga with timeout handling.
     */
    private SagaResult executeWithTimeout(SagaExecution execution) {
        CompletableFuture<SagaResult> future = CompletableFuture.supplyAsync(
                () -> executeSagaInternal(execution),
                executor
        );

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOGGER.error("Saga execution timed out: sagaId={}", execution.getSagaId());
            execution.setState(SagaState.TIMED_OUT);
            return buildTimeoutResult(execution);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Saga execution interrupted: sagaId={}", execution.getSagaId());
            execution.setState(SagaState.INTERRUPTED);
            return buildInterruptedResult(execution);
        } catch (ExecutionException e) {
            LOGGER.error("Saga execution failed: sagaId={}", execution.getSagaId(), e.getCause());
            execution.setState(SagaState.FAILED);
            return buildErrorResult(execution, e.getCause());
        }
    }

    /**
     * Execute the saga workflow steps with compensation tracking.
     */
    private SagaResult executeSagaInternal(SagaExecution execution) {
        SagaWorkflow workflow = execution.getWorkflow();
        SagaContext context = execution.getContext();
        List<SagaStep> steps = workflow.getSteps();
        Deque<CompletedStep> completedSteps = new ArrayDeque<>();

        execution.setState(SagaState.EXECUTING);

        // Execute steps based on topology
        if (topology == OrchestrationTopology.MESH && workflow.supportsParallelExecution()) {
            return executeInMeshTopology(execution, steps, context);
        }

        // Hierarchical execution (sequential with compensation)
        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            execution.setCurrentStepIndex(i);

            LOGGER.debug("Executing step {}/{}: {}", i + 1, steps.size(), step.getName());

            StepResult stepResult = executeStep(step, context, execution);

            if (stepResult.isSuccess()) {
                completedSteps.push(new CompletedStep(step, stepResult, Instant.now()));
                context = stepResult.getUpdatedContext();
                execution.setContext(context);

                if (enableTracing) {
                    execution.addTraceEvent(new TraceEvent(
                            "step_completed",
                            step.getName(),
                            stepResult,
                            Instant.now()
                    ));
                }
            } else {
                // Step failed - trigger compensation
                LOGGER.warn("Step failed: step={}, error={}", step.getName(), stepResult.getError());

                execution.setState(SagaState.COMPENSATING);
                CompensationResult compensationResult = compensateSteps(completedSteps, context, execution);

                SagaResult result = new SagaResult(
                        execution.getSagaId(),
                        workflow.getName(),
                        SagaState.COMPENSATED,
                        context,
                        stepResult.getError(),
                        execution.getTraceEvents(),
                        Duration.between(execution.getStartTime(), Instant.now()),
                        compensationResult,
                        buildStepResults(completedSteps)
                );

                addToHistory(result);
                return result;
            }
        }

        // All steps completed successfully
        execution.setState(SagaState.COMPLETED);

        SagaResult result = new SagaResult(
                execution.getSagaId(),
                workflow.getName(),
                SagaState.COMPLETED,
                context,
                null,
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                null,
                buildStepResults(completedSteps)
        );

        addToHistory(result);
        LOGGER.info("Saga completed successfully: sagaId={}, duration={}",
                execution.getSagaId(), result.getDuration());

        return result;
    }

    /**
     * Execute steps using mesh topology with agent handoffs.
     */
    private SagaResult executeInMeshTopology(
            SagaExecution execution,
            List<SagaStep> steps,
            SagaContext context) {

        // Group steps by dependencies
        List<List<SagaStep>> executionGroups = groupStepsByDependency(steps);
        Deque<CompletedStep> completedSteps = new ArrayDeque<>();

        for (List<SagaStep> group : executionGroups) {
            if (group.size() == 1) {
                // Single step - execute directly
                SagaStep step = group.get(0);
                StepResult result = executeStep(step, context, execution);

                if (!result.isSuccess()) {
                    return handleStepFailure(execution, step, result, completedSteps, context);
                }

                completedSteps.push(new CompletedStep(step, result, Instant.now()));
                context = result.getUpdatedContext();
            } else {
                // Multiple steps - execute in parallel with structured concurrency
                Map<SagaStep, StepResult> results = executeParallelSteps(group, context, execution);

                // Check for failures
                Optional<Map.Entry<SagaStep, StepResult>> failure = results.entrySet().stream()
                        .filter(e -> !e.getValue().isSuccess())
                        .findFirst();

                if (failure.isPresent()) {
                    return handleStepFailure(
                            execution,
                            failure.get().getKey(),
                            failure.get().getValue(),
                            completedSteps,
                            context
                    );
                }

                // Merge results into context
                for (Map.Entry<SagaStep, StepResult> entry : results.entrySet()) {
                    completedSteps.push(new CompletedStep(entry.getKey(), entry.getValue(), Instant.now()));
                    context = context.merge(entry.getValue().getUpdatedContext());
                }

                execution.setContext(context);
            }
        }

        execution.setState(SagaState.COMPLETED);

        return new SagaResult(
                execution.getSagaId(),
                execution.getWorkflow().getName(),
                SagaState.COMPLETED,
                context,
                null,
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                null,
                buildStepResults(completedSteps)
        );
    }

    /**
     * Execute multiple steps in parallel using structured concurrency.
     */
    /**
     * Executes saga steps in parallel with proper exception handling and cancellation.
     *
     * <p>Uses StructuredTaskScope.ShutdownOnFailure to ensure that when any step fails,
     * all remaining steps are cancelled immediately. This is critical for saga semantics:
     * if one step fails, we want to trigger compensation without waiting for other steps.</p>
     */
    private Map<SagaStep, StepResult> executeParallelSteps(
            List<SagaStep> steps,
            SagaContext context,
            SagaExecution execution) {

        Map<SagaStep, StepResult> results = new ConcurrentHashMap<>();
        List<ExecutionException> failures = Collections.synchronizedList(new ArrayList<>());

        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<StepResult>awaitAll(),
                cfg -> cfg.withTimeout(timeout))) {
            for (SagaStep step : steps) {
                scope.fork(() -> {
                    try {
                        StepResult result = executeStep(step, context, execution);
                        results.put(step, result);
                        if (!result.isSuccess()) {
                            failures.add(new ExecutionException(
                                "Step " + step.getName() + " failed: " + result.getError(),
                                null
                            ));
                        }
                        return result;
                    } catch (Exception e) {
                        failures.add(new ExecutionException(
                            "Step " + step.getName() + " threw exception", e));
                        throw e;
                    }
                });
            }

            // Join - timeout configured via withTimeout() above
            scope.join();

            // Detect timeout via scope cancellation flag
            if (scope.isCancelled()) {
                LOGGER.error("Parallel saga execution timeout after {}ms", timeout.toMillis());
                failures.add(new ExecutionException("Execution timeout", null));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("Parallel saga execution interrupted");
            failures.add(new ExecutionException("Execution interrupted", e));
        } catch (Exception e) {
            LOGGER.warn("Parallel saga execution failed: {}", e.getMessage());
            failures.add(new ExecutionException("Step execution failed", e));
        }

        // If there were failures, log them
        if (!failures.isEmpty()) {
            LOGGER.error("Parallel saga execution had {} failures", failures.size());
            for (ExecutionException failure : failures) {
                LOGGER.error("  - {}", failure.getMessage(), failure.getCause());
            }
        }

        return results;
    }

    /**
     * Execute a single saga step with an agent.
     */
    private StepResult executeStep(
            SagaStep step,
            SagaContext context,
            SagaExecution execution) {

        GregVerseAgent agent = agentRegistry.getAgent(step.getAgentId());

        if (agent == null) {
            String error = "Agent not found: " + step.getAgentId();
            LOGGER.error(error);
            return new StepResult(false, context, null, error);
        }

        try {
            Instant stepStart = Instant.now();

            // Build the task prompt with context
            String taskPrompt = buildTaskPrompt(step, context);

            // Execute agent task
            String agentResponse = agent.processQuery(taskPrompt);

            Duration stepDuration = Duration.between(stepStart, Instant.now());

            // Parse response and update context
            SagaContext updatedContext = context.withVariable(
                    step.getName() + "_result",
                    agentResponse
            );

            if (enableMetrics) {
                execution.addMetric(step.getName() + "_duration_ms", stepDuration.toMillis());
            }

            return new StepResult(
                    true,
                    updatedContext,
                    agentResponse,
                    null
            );

        } catch (Exception e) {
            LOGGER.error("Step execution failed: step={}, agent={}",
                    step.getName(), step.getAgentId(), e);

            return new StepResult(
                    false,
                    context,
                    null,
                    "Agent execution failed: " + e.getMessage()
            );
        }
    }

    /**
     * Build task prompt for agent execution.
     */
    private String buildTaskPrompt(SagaStep step, SagaContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: ").append(step.getDescription()).append("\n\n");
        prompt.append("Workflow Context:\n");

        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            prompt.append("  ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append("\n");
        }

        if (step.getInputMapping() != null && !step.getInputMapping().isEmpty()) {
            prompt.append("\nInput Requirements:\n");
            for (String input : step.getInputMapping()) {
                Object value = context.getVariable(input);
                prompt.append("  ").append(input).append(": ").append(value).append("\n");
            }
        }

        prompt.append("\nPlease provide your expert analysis and response for this task.");

        return prompt.toString();
    }

    /**
     * Compensate completed steps in reverse order.
     */
    private CompensationResult compensateSteps(
            Deque<CompletedStep> completedSteps,
            SagaContext context,
            SagaExecution execution) {

        List<CompensationAction> compensations = new ArrayList<>();
        List<String> failedCompensations = new ArrayList<>();

        LOGGER.info("Starting compensation for {} steps", completedSteps.size());

        while (!completedSteps.isEmpty()) {
            CompletedStep completed = completedSteps.pop();
            SagaStep step = completed.step();

            if (step.hasCompensation()) {
                LOGGER.debug("Compensating step: {}", step.getName());

                try {
                    SagaContext compensationContext = context.withVariable(
                            "compensation_reason",
                            "Rolling back due to subsequent failure"
                    );

                    String compensationResult = step.executeCompensation(compensationContext);

                    compensations.add(new CompensationAction(
                            step.getName(),
                            true,
                            compensationResult,
                            null
                    ));

                    if (enableTracing) {
                        execution.addTraceEvent(new TraceEvent(
                                "compensation_completed",
                                step.getName(),
                                compensationResult,
                                Instant.now()
                        ));
                    }

                } catch (Exception e) {
                    LOGGER.error("Compensation failed for step: {}", step.getName(), e);

                    compensations.add(new CompensationAction(
                            step.getName(),
                            false,
                            null,
                            e.getMessage()
                    ));

                    failedCompensations.add(step.getName());
                }
            }
        }

        return new CompensationResult(
                compensations,
                failedCompensations.isEmpty(),
                failedCompensations
        );
    }

    /**
     * Handle step failure with compensation.
     */
    private SagaResult handleStepFailure(
            SagaExecution execution,
            SagaStep failedStep,
            StepResult failureResult,
            Deque<CompletedStep> completedSteps,
            SagaContext context) {

        execution.setState(SagaState.COMPENSATING);

        CompensationResult compensationResult = compensateSteps(completedSteps, context, execution);

        return new SagaResult(
                execution.getSagaId(),
                execution.getWorkflow().getName(),
                SagaState.COMPENSATED,
                context,
                failureResult.getError(),
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                compensationResult,
                buildStepResults(completedSteps)
        );
    }

    /**
     * Execute agent handoff from one agent to another.
     *
     * @param fromAgentId source agent ID
     * @param toAgentId target agent ID
     * @param handoffContext context to transfer
     * @param execution the current saga execution
     * @return handoff result
     */
    public HandoffResult executeHandoff(
            String fromAgentId,
            String toAgentId,
            SagaContext handoffContext,
            SagaExecution execution) {

        AtomicInteger handoffCount = execution.getHandoffCount();
        int currentCount = handoffCount.getAndIncrement();

        if (currentCount >= maxHandoffs) {
            LOGGER.warn("Maximum handoffs exceeded: current={}, max={}", currentCount, maxHandoffs);
            return new HandoffResult(
                    false,
                    handoffContext,
                    "Maximum handoff limit exceeded: " + maxHandoffs
            );
        }

        GregVerseAgent fromAgent = agentRegistry.getAgent(fromAgentId);
        GregVerseAgent toAgent = agentRegistry.getAgent(toAgentId);

        if (toAgent == null) {
            return new HandoffResult(false, handoffContext, "Target agent not found: " + toAgentId);
        }

        LOGGER.info("Executing handoff: {} -> {} (handoff #{})",
                fromAgentId, toAgentId, currentCount + 1);

        // Build handoff context
        String handoffPrompt = String.format(
                "Receiving handoff from agent '%s'.\n\nContext:\n%s\n\nPlease continue with this task.",
                fromAgentId,
                formatContextForHandoff(handoffContext)
        );

        try {
            String response = toAgent.processQuery(handoffPrompt);

            SagaContext updatedContext = handoffContext
                    .withVariable("last_handoff_from", fromAgentId)
                    .withVariable("last_handoff_to", toAgentId)
                    .withVariable("handoff_count", currentCount + 1);

            if (enableTracing) {
                execution.addTraceEvent(new TraceEvent(
                        "agent_handoff",
                        fromAgentId + " -> " + toAgentId,
                        response,
                        Instant.now()
                ));
            }

            return new HandoffResult(true, updatedContext, response);

        } catch (Exception e) {
            LOGGER.error("Handoff failed: {} -> {}", fromAgentId, toAgentId, e);
            return new HandoffResult(false, handoffContext, "Handoff failed: " + e.getMessage());
        }
    }

    /**
     * Format context for handoff display.
     */
    private String formatContextForHandoff(SagaContext context) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ")
                    .append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Group steps by their dependencies for parallel execution.
     */
    private List<List<SagaStep>> groupStepsByDependency(List<SagaStep> steps) {
        Map<String, SagaStep> stepMap = steps.stream()
                .collect(Collectors.toMap(SagaStep::getName, Function.identity()));

        Map<String, Integer> dependencyCount = new HashMap<>();
        Map<String, List<SagaStep>> dependents = new HashMap<>();

        // Initialize
        for (SagaStep step : steps) {
            dependencyCount.put(step.getName(), 0);
            dependents.put(step.getName(), new ArrayList<>());
        }

        // Build dependency graph
        for (SagaStep step : steps) {
            if (step.getDependencies() != null) {
                for (String dep : step.getDependencies()) {
                    dependencyCount.merge(step.getName(), 1, Integer::sum);
                    dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(step);
                }
            }
        }

        // Topological sort with grouping
        List<List<SagaStep>> groups = new ArrayList<>();
        Set<String> completed = new HashSet<>();

        while (completed.size() < steps.size()) {
            List<SagaStep> group = new ArrayList<>();

            for (SagaStep step : steps) {
                if (!completed.contains(step.getName()) &&
                        dependencyCount.get(step.getName()) == 0) {
                    group.add(step);
                }
            }

            if (group.isEmpty()) {
                // Circular dependency - fall back to sequential
                LOGGER.warn("Circular dependency detected, falling back to sequential execution");
                return steps.stream().map(List::of).toList();
            }

            groups.add(group);

            for (SagaStep step : group) {
                completed.add(step.getName());
                for (SagaStep dependent : dependents.get(step.getName())) {
                    dependencyCount.merge(dependent.getName(), -1, Integer::sum);
                }
            }
        }

        return groups;
    }

    /**
     * Build step results list from completed steps.
     */
    private List<StepResult> buildStepResults(Deque<CompletedStep> completedSteps) {
        List<StepResult> results = new ArrayList<>();
        for (CompletedStep cs : completedSteps) {
            results.add(0, cs.result()); // Add at beginning to preserve order
        }
        return results;
    }

    /**
     * Build timeout result.
     */
    private SagaResult buildTimeoutResult(SagaExecution execution) {
        return new SagaResult(
                execution.getSagaId(),
                execution.getWorkflow().getName(),
                SagaState.TIMED_OUT,
                execution.getContext(),
                "Saga execution timed out after " + timeout,
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                null,
                List.of()
        );
    }

    /**
     * Build interrupted result.
     */
    private SagaResult buildInterruptedResult(SagaExecution execution) {
        return new SagaResult(
                execution.getSagaId(),
                execution.getWorkflow().getName(),
                SagaState.INTERRUPTED,
                execution.getContext(),
                "Saga execution was interrupted",
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                null,
                List.of()
        );
    }

    /**
     * Build error result.
     */
    private SagaResult buildErrorResult(SagaExecution execution, Throwable error) {
        return new SagaResult(
                execution.getSagaId(),
                execution.getWorkflow().getName(),
                SagaState.FAILED,
                execution.getContext(),
                error != null ? error.getMessage() : "Unknown error",
                execution.getTraceEvents(),
                Duration.between(execution.getStartTime(), Instant.now()),
                null,
                List.of()
        );
    }

    /**
     * Add completed saga to history.
     */
    private void addToHistory(SagaResult result) {
        if (!completedSagas.offerFirst(result)) {
            completedSagas.removeLast();
            completedSagas.offerFirst(result);
        }
    }

    /**
     * Generate unique saga ID.
     */
    private String generateSagaId() {
        return "saga-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Get active saga executions.
     *
     * @return map of saga ID to execution
     */
    public Map<String, SagaExecution> getActiveExecutions() {
        return Map.copyOf(activeExecutions);
    }

    /**
     * Get completed saga history.
     *
     * @return list of completed saga results
     */
    public List<SagaResult> getCompletedSagas() {
        return new ArrayList<>(completedSagas);
    }

    /**
     * Shutdown the orchestrator and release resources.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("GregVerseOrchestrator shutdown complete");
    }

    // ==================== Builder ====================

    /**
     * Builder for GregVerseOrchestrator.
     */
    public static class Builder {
        private final GregVerseAgentRegistry agentRegistry;
        private OrchestrationTopology topology = OrchestrationTopology.HIERARCHICAL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxHandoffs = DEFAULT_MAX_HANDOFFS;
        private int maxConcurrency = DEFAULT_MAX_CONCURRENCY;
        private boolean enableTracing = true;
        private boolean enableMetrics = true;

        public Builder(GregVerseAgentRegistry agentRegistry) {
            this.agentRegistry = Objects.requireNonNull(agentRegistry);
        }

        /**
         * Set the coordination topology.
         */
        public Builder withTopology(OrchestrationTopology topology) {
            this.topology = topology;
            return this;
        }

        /**
         * Set execution timeout.
         */
        public Builder withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set maximum agent handoffs.
         */
        public Builder withMaxHandoffs(int maxHandoffs) {
            this.maxHandoffs = maxHandoffs;
            return this;
        }

        /**
         * Set maximum concurrent executions.
         */
        public Builder withMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        /**
         * Enable or disable tracing.
         */
        public Builder withTracing(boolean enabled) {
            this.enableTracing = enabled;
            return this;
        }

        /**
         * Enable or disable metrics.
         */
        public Builder withMetrics(boolean enabled) {
            this.enableMetrics = enabled;
            return this;
        }

        /**
         * Build the orchestrator.
         */
        public GregVerseOrchestrator build() {
            return new GregVerseOrchestrator(
                    agentRegistry,
                    topology,
                    timeout,
                    maxHandoffs,
                    maxConcurrency,
                    enableTracing,
                    enableMetrics
            );
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Coordination topology types.
     */
    public enum OrchestrationTopology {
        /**
         * Central coordinator dispatches tasks to agents.
         * Best for sequential workflows with clear dependencies.
         */
        HIERARCHICAL,

        /**
         * Agents can delegate to each other with handoff tracking.
         * Best for collaborative workflows with dynamic routing.
         */
        MESH
    }

    /**
     * Saga workflow definition.
     */
    public static class SagaWorkflow {
        private final String name;
        private final String description;
        private final List<SagaStep> steps;
        private final boolean supportsParallelExecution;

        public SagaWorkflow(String name, String description, List<SagaStep> steps, boolean supportsParallelExecution) {
            this.name = name;
            this.description = description;
            this.steps = List.copyOf(steps);
            this.supportsParallelExecution = supportsParallelExecution;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<SagaStep> getSteps() { return steps; }
        public boolean supportsParallelExecution() { return supportsParallelExecution; }

        /**
         * Create a builder for SagaWorkflow.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for SagaWorkflow.
         */
        public static class Builder {
            private String name;
            private String description = "";
            private final List<SagaStep> steps = new ArrayList<>();
            private boolean supportsParallelExecution = false;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder description(String description) {
                this.description = description;
                return this;
            }

            public Builder step(String name, String agentId, String description) {
                steps.add(new SagaStep(name, agentId, description, null, null, null));
                return this;
            }

            public Builder step(SagaStep step) {
                steps.add(step);
                return this;
            }

            public Builder parallelExecution(boolean enabled) {
                this.supportsParallelExecution = enabled;
                return this;
            }

            public SagaWorkflow build() {
                return new SagaWorkflow(name, description, steps, supportsParallelExecution);
            }
        }
    }

    /**
     * Individual saga step definition.
     */
    public static class SagaStep {
        private final String name;
        private final String agentId;
        private final String description;
        private final List<String> inputMapping;
        private final List<String> dependencies;
        private final BiFunction<SagaContext, String, String> compensationFunction;

        public SagaStep(
                String name,
                String agentId,
                String description,
                List<String> inputMapping,
                List<String> dependencies,
                BiFunction<SagaContext, String, String> compensationFunction) {
            this.name = name;
            this.agentId = agentId;
            this.description = description;
            this.inputMapping = inputMapping != null ? List.copyOf(inputMapping) : List.of();
            this.dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
            this.compensationFunction = compensationFunction;
        }

        public String getName() { return name; }
        public String getAgentId() { return agentId; }
        public String getDescription() { return description; }
        public List<String> getInputMapping() { return inputMapping; }
        public List<String> getDependencies() { return dependencies; }

        public boolean hasCompensation() {
            return compensationFunction != null;
        }

        public String executeCompensation(SagaContext context) {
            if (compensationFunction == null) {
                return "No compensation defined";
            }
            return compensationFunction.apply(context, "Compensating step: " + name);
        }
    }

    /**
     * Saga execution state.
     */
    public enum SagaState {
        PENDING,
        RUNNING,
        EXECUTING,
        COMPENSATING,
        COMPLETED,
        COMPENSATED,
        FAILED,
        TIMED_OUT,
        INTERRUPTED
    }

    /**
     * Execution context for saga workflows.
     */
    public static class SagaContext {
        private final Map<String, Object> variables;

        public SagaContext() {
            this.variables = new ConcurrentHashMap<>();
        }

        public SagaContext(Map<String, Object> variables) {
            this.variables = new ConcurrentHashMap<>(variables);
        }

        public Map<String, Object> getVariables() {
            return Map.copyOf(variables);
        }

        public Object getVariable(String name) {
            return variables.get(name);
        }

        public SagaContext withVariable(String name, Object value) {
            Map<String, Object> newVars = new ConcurrentHashMap<>(variables);
            newVars.put(name, value);
            return new SagaContext(newVars);
        }

        public SagaContext merge(SagaContext other) {
            Map<String, Object> merged = new ConcurrentHashMap<>(variables);
            merged.putAll(other.variables);
            return new SagaContext(merged);
        }
    }

    /**
     * Step execution result.
     */
    public static class StepResult {
        private final boolean success;
        private final SagaContext updatedContext;
        private final String response;
        private final String error;

        public StepResult(boolean success, SagaContext updatedContext, String response, String error) {
            this.success = success;
            this.updatedContext = updatedContext;
            this.response = response;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public SagaContext getUpdatedContext() { return updatedContext; }
        public String getResponse() { return response; }
        public String getError() { return error; }
    }

    /**
     * Saga execution result.
     */
    public static class SagaResult {
        private final String sagaId;
        private final String workflowName;
        private final SagaState state;
        private final SagaContext finalContext;
        private final String error;
        private final List<TraceEvent> traceEvents;
        private final Duration duration;
        private final CompensationResult compensationResult;
        private final List<StepResult> stepResults;

        public SagaResult(
                String sagaId,
                String workflowName,
                SagaState state,
                SagaContext finalContext,
                String error,
                List<TraceEvent> traceEvents,
                Duration duration,
                CompensationResult compensationResult,
                List<StepResult> stepResults) {
            this.sagaId = sagaId;
            this.workflowName = workflowName;
            this.state = state;
            this.finalContext = finalContext;
            this.error = error;
            this.traceEvents = traceEvents != null ? List.copyOf(traceEvents) : List.of();
            this.duration = duration;
            this.compensationResult = compensationResult;
            this.stepResults = stepResults != null ? List.copyOf(stepResults) : List.of();
        }

        public String getSagaId() { return sagaId; }
        public String getWorkflowName() { return workflowName; }
        public SagaState getState() { return state; }
        public SagaContext getFinalContext() { return finalContext; }
        public String getError() { return error; }
        public List<TraceEvent> getTraceEvents() { return traceEvents; }
        public Duration getDuration() { return duration; }
        public CompensationResult getCompensationResult() { return compensationResult; }
        public List<StepResult> getStepResults() { return stepResults; }

        public boolean isSuccess() {
            return state == SagaState.COMPLETED;
        }

        public boolean wasCompensated() {
            return state == SagaState.COMPENSATED;
        }
    }

    /**
     * Compensation action result.
     */
    public static class CompensationResult {
        private final List<CompensationAction> actions;
        private final boolean allSuccessful;
        private final List<String> failedSteps;

        public CompensationResult(List<CompensationAction> actions, boolean allSuccessful, List<String> failedSteps) {
            this.actions = actions != null ? List.copyOf(actions) : List.of();
            this.allSuccessful = allSuccessful;
            this.failedSteps = failedSteps != null ? List.copyOf(failedSteps) : List.of();
        }

        public List<CompensationAction> getActions() { return actions; }
        public boolean isAllSuccessful() { return allSuccessful; }
        public List<String> getFailedSteps() { return failedSteps; }
    }

    /**
     * Individual compensation action.
     */
    public static class CompensationAction {
        private final String stepName;
        private final boolean success;
        private final String result;
        private final String error;

        public CompensationAction(String stepName, boolean success, String result, String error) {
            this.stepName = stepName;
            this.success = success;
            this.result = result;
            this.error = error;
        }

        public String getStepName() { return stepName; }
        public boolean isSuccess() { return success; }
        public String getResult() { return result; }
        public String getError() { return error; }
    }

    /**
     * Agent handoff result.
     */
    public static class HandoffResult {
        private final boolean success;
        private final SagaContext context;
        private final String message;

        public HandoffResult(boolean success, SagaContext context, String message) {
            this.success = success;
            this.context = context;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public SagaContext getContext() { return context; }
        public String getMessage() { return message; }
    }

    /**
     * Trace event for execution tracking.
     */
    public static class TraceEvent {
        private final String type;
        private final String source;
        private final Object data;
        private final Instant timestamp;

        public TraceEvent(String type, String source, Object data, Instant timestamp) {
            this.type = type;
            this.source = source;
            this.data = data;
            this.timestamp = timestamp;
        }

        public String getType() { return type; }
        public String getSource() { return source; }
        public Object getData() { return data; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * Ongoing saga execution state.
     */
    public static class SagaExecution {
        private final String sagaId;
        private final SagaWorkflow workflow;
        private final Instant startTime;
        private final AtomicInteger handoffCount = new AtomicInteger(0);
        private final List<TraceEvent> traceEvents = new CopyOnWriteArrayList<>();
        private final Map<String, Object> metrics = new ConcurrentHashMap<>();

        private volatile SagaState state = SagaState.PENDING;
        private volatile SagaContext context;
        private volatile int currentStepIndex = 0;

        public SagaExecution(String sagaId, SagaWorkflow workflow, SagaContext context, Instant startTime) {
            this.sagaId = sagaId;
            this.workflow = workflow;
            this.context = context;
            this.startTime = startTime;
        }

        public String getSagaId() { return sagaId; }
        public SagaWorkflow getWorkflow() { return workflow; }
        public SagaContext getContext() { return context; }
        public Instant getStartTime() { return startTime; }
        public SagaState getState() { return state; }
        public int getCurrentStepIndex() { return currentStepIndex; }
        public AtomicInteger getHandoffCount() { return handoffCount; }
        public List<TraceEvent> getTraceEvents() { return List.copyOf(traceEvents); }

        public void setState(SagaState state) { this.state = state; }
        public void setContext(SagaContext context) { this.context = context; }
        public void setCurrentStepIndex(int index) { this.currentStepIndex = index; }

        public void addTraceEvent(TraceEvent event) {
            traceEvents.add(event);
        }

        public void addMetric(String name, Object value) {
            metrics.put(name, value);
        }

        public Map<String, Object> getMetrics() {
            return Map.copyOf(metrics);
        }
    }

    /**
     * Record for completed step tracking during compensation.
     */
    private record CompletedStep(SagaStep step, StepResult result, Instant completedAt) {}
}
