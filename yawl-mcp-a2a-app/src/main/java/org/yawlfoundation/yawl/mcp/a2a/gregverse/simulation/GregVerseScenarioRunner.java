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

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseConfiguration.GregVerseAgentRegistry;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport.AgentInteraction.InteractionType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Executes predefined Greg-Verse business scenarios.
 *
 * <p>This runner orchestrates multi-agent simulations defined in YAML files,
 * executing scenario steps in dependency order with support for compensation
 * rollback on failures.</p>
 *
 * <h2>Scenario File Format</h2>
 * <pre>{@code
 * id: gvs-1-startup-idea
 * name: Startup Idea Validation
 * description: Validate a startup idea using multiple Greg-Verse agents
 * timeout: 300
 * steps:
 *   - id: market-analysis
 *     agent: greg-isenberg
 *     skill: market-research
 *     input: "${idea}"
 *     dependsOn: []
 *   - id: positioning
 *     agent: james
 *     skill: positioning
 *     input: "${market-analysis.output}"
 *     dependsOn: [market-analysis]
 * compensations:
 *   - stepId: market-analysis
 *     action: cleanup-market-data
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class GregVerseScenarioRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseScenarioRunner.class);

    private final GregVerseAgentRegistry registry;
    private final YAMLMapper yamlMapper;
    private final long defaultTimeoutMs;
    private final Map<String, ScenarioExecution> activeExecutions;
    private final List<Consumer<ScenarioEvent>> eventListeners;

    /**
     * Creates a new scenario runner with the specified registry.
     *
     * @param registry the agent registry for looking up agents
     * @param defaultTimeoutMs default timeout for scenario execution in milliseconds
     * @throws IllegalArgumentException if registry is null
     */
    public GregVerseScenarioRunner(GregVerseAgentRegistry registry, long defaultTimeoutMs) {
        if (registry == null) {
            throw new IllegalArgumentException("Registry cannot be null");
        }
        this.registry = registry;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.yamlMapper = new YAMLMapper();
        this.activeExecutions = new ConcurrentHashMap<>();
        this.eventListeners = new ArrayList<>();
        LOGGER.info("GregVerseScenarioRunner initialized with default timeout: {}ms", defaultTimeoutMs);
    }

    /**
     * Loads a scenario from a YAML file.
     *
     * @param yamlPath path to the YAML scenario file
     * @return the loaded scenario definition
     * @throws ScenarioLoadException if the file cannot be read or parsed
     */
    public Scenario loadScenario(Path yamlPath) throws ScenarioLoadException {
        LOGGER.debug("Loading scenario from: {}", yamlPath);
        try {
            String yamlContent = Files.readString(yamlPath);
            return loadScenarioFromString(yamlContent);
        } catch (IOException e) {
            throw new ScenarioLoadException("Failed to read scenario file: " + yamlPath, e);
        }
    }

    /**
     * Loads a scenario from a YAML string.
     *
     * @param yamlContent the YAML content
     * @return the loaded scenario definition
     * @throws ScenarioLoadException if the content cannot be parsed
     */
    public Scenario loadScenarioFromString(String yamlContent) throws ScenarioLoadException {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> yamlMap = yamlMapper.readValue(yamlContent, Map.class);
            return parseScenario(yamlMap);
        } catch (IOException e) {
            throw new ScenarioLoadException("Failed to parse YAML content", e);
        }
    }

    /**
     * Executes a scenario with default configuration.
     *
     * @param scenario the scenario to execute
     * @return the execution result
     * @throws ScenarioExecutionException if execution fails
     */
    public ScenarioResult execute(Scenario scenario) throws ScenarioExecutionException {
        return execute(scenario, GregVerseConfig.defaults());
    }

    /**
     * Executes a scenario with the specified configuration.
     *
     * @param scenario the scenario to execute
     * @param config execution configuration
     * @return the execution result
     * @throws ScenarioExecutionException if execution fails
     */
    public ScenarioResult execute(Scenario scenario, GregVerseConfig config) throws ScenarioExecutionException {
        String executionId = UUID.randomUUID().toString().substring(0, 8);
        Instant startTime = Instant.now();

        LOGGER.info("Starting scenario execution: {} (executionId={})", scenario.id(), executionId);
        emitEvent(ScenarioEvent.started(executionId, scenario.id()));

        ScenarioExecution execution = new ScenarioExecution(executionId, scenario, config);
        activeExecutions.put(executionId, execution);

        try {
            List<StepResult> stepResults = executeStepsWithDependencies(execution);

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);

            ScenarioResult result = new ScenarioResult(
                executionId,
                scenario.id(),
                true,
                stepResults,
                Collections.emptyList(),
                duration,
                startTime,
                endTime,
                null
            );

            LOGGER.info("Scenario completed successfully: {} (duration={})", scenario.id(), duration);
            emitEvent(ScenarioEvent.completed(executionId, scenario.id(), duration));

            return result;

        } catch (ScenarioExecutionException e) {
            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);

            LOGGER.error("Scenario execution failed: {} - {}", scenario.id(), e.getMessage());

            // Run compensations
            List<CompensationResult> compensationResults = executeCompensations(execution, e);

            ScenarioResult result = new ScenarioResult(
                executionId,
                scenario.id(),
                false,
                execution.getCompletedSteps(),
                compensationResults,
                duration,
                startTime,
                endTime,
                e.getMessage()
            );

            emitEvent(ScenarioEvent.failed(executionId, scenario.id(), e.getMessage()));
            throw new ScenarioExecutionException("Scenario execution failed: " + e.getMessage(), e, result);

        } finally {
            activeExecutions.remove(executionId);
        }
    }

    /**
     * Executes a scenario asynchronously.
     *
     * @param scenario the scenario to execute
     * @param config execution configuration
     * @return a future containing the execution result
     */
    public CompletableFuture<ScenarioResult> executeAsync(Scenario scenario, GregVerseConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(scenario, config);
            } catch (ScenarioExecutionException e) {
                throw new CompletionExceptionWrapper(e);
            }
        });
    }

    /**
     * Cancels an active scenario execution.
     *
     * @param executionId the execution ID to cancel
     * @return true if the execution was cancelled
     */
    public boolean cancel(String executionId) {
        ScenarioExecution execution = activeExecutions.get(executionId);
        if (execution != null) {
            LOGGER.info("Cancelling scenario execution: {}", executionId);
            execution.cancel();
            emitEvent(ScenarioEvent.cancelled(executionId, execution.getScenario().id()));
            return true;
        }
        return false;
    }

    /**
     * Adds an event listener for scenario execution events.
     *
     * @param listener the listener to add
     */
    public void addEventListener(Consumer<ScenarioEvent> listener) {
        eventListeners.add(listener);
    }

    /**
     * Removes an event listener.
     *
     * @param listener the listener to remove
     */
    public void removeEventListener(Consumer<ScenarioEvent> listener) {
        eventListeners.remove(listener);
    }

    /**
     * Gets all active scenario executions.
     *
     * @return map of execution ID to execution state
     */
    public Map<String, ScenarioExecution> getActiveExecutions() {
        return Map.copyOf(activeExecutions);
    }

    /**
     * Gets the progress of an active execution.
     *
     * @param executionId the execution ID
     * @return optional containing progress percentage (0-100), or empty if not found
     */
    public Optional<Integer> getProgress(String executionId) {
        ScenarioExecution execution = activeExecutions.get(executionId);
        if (execution != null) {
            return Optional.of(execution.getProgress());
        }
        return Optional.empty();
    }

    // --- Private implementation methods ---

    private void emitEvent(ScenarioEvent event) {
        for (Consumer<ScenarioEvent> listener : eventListeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOGGER.warn("Event listener threw exception: {}", e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Scenario parseScenario(Map<String, Object> yamlMap) throws ScenarioLoadException {
        String id = getString(yamlMap, "id", null);
        String name = getString(yamlMap, "name", id);
        String description = getString(yamlMap, "description", "");
        int timeoutSeconds = getInt(yamlMap, "timeout", (int) (defaultTimeoutMs / 1000));

        if (id == null || id.isBlank()) {
            throw new ScenarioLoadException("Scenario must have an 'id' field");
        }

        List<ScenarioStep> steps = new ArrayList<>();
        Object stepsObj = yamlMap.get("steps");
        if (stepsObj instanceof List) {
            for (Object stepObj : (List<?>) stepsObj) {
                if (stepObj instanceof Map) {
                    steps.add(parseStep((Map<String, Object>) stepObj));
                }
            }
        }

        if (steps.isEmpty()) {
            throw new ScenarioLoadException("Scenario must have at least one step");
        }

        List<Compensation> compensations = new ArrayList<>();
        Object compsObj = yamlMap.get("compensations");
        if (compsObj instanceof List) {
            for (Object compObj : (List<?>) compsObj) {
                if (compObj instanceof Map) {
                    compensations.add(parseCompensation((Map<String, Object>) compObj));
                }
            }
        }

        Map<String, Object> variables = new HashMap<>();
        Object varsObj = yamlMap.get("variables");
        if (varsObj instanceof Map) {
            variables.putAll((Map<String, Object>) varsObj);
        }

        return new Scenario(id, name, description, Duration.ofSeconds(timeoutSeconds), steps, compensations, variables);
    }

    @SuppressWarnings("unchecked")
    private ScenarioStep parseStep(Map<String, Object> stepMap) throws ScenarioLoadException {
        String stepId = getString(stepMap, "id", null);
        if (stepId == null || stepId.isBlank()) {
            throw new ScenarioLoadException("Step must have an 'id' field");
        }

        String agentId = getString(stepMap, "agent", null);
        String skillId = getString(stepMap, "skill", null);
        String input = getString(stepMap, "input", "");
        String description = getString(stepMap, "description", "");
        int timeoutSeconds = getInt(stepMap, "timeout", (int) (defaultTimeoutMs / 1000));

        List<String> dependsOn = new ArrayList<>();
        Object dependsOnObj = stepMap.get("dependsOn");
        if (dependsOnObj instanceof List) {
            for (Object dep : (List<?>) dependsOnObj) {
                if (dep != null) {
                    dependsOn.add(dep.toString());
                }
            }
        }

        Map<String, Object> stepConfig = new HashMap<>();
        Object configObj = stepMap.get("config");
        if (configObj instanceof Map) {
            stepConfig.putAll((Map<String, Object>) configObj);
        }

        return new ScenarioStep(stepId, agentId, skillId, input, description,
            Duration.ofSeconds(timeoutSeconds), dependsOn, stepConfig);
    }

    @SuppressWarnings("unchecked")
    private Compensation parseCompensation(Map<String, Object> compMap) {
        String stepId = getString(compMap, "stepId", "");
        String action = getString(compMap, "action", "");
        String agentId = getString(compMap, "agent", null);

        Map<String, Object> params = new HashMap<>();
        Object paramsObj = compMap.get("params");
        if (paramsObj instanceof Map) {
            params.putAll((Map<String, Object>) paramsObj);
        }

        return new Compensation(stepId, action, agentId, params);
    }

    private List<StepResult> executeStepsWithDependencies(ScenarioExecution execution)
            throws ScenarioExecutionException {

        Scenario scenario = execution.getScenario();
        List<ScenarioStep> steps = scenario.steps();
        Map<String, StepResult> completedResults = new LinkedHashMap<>();

        // Build dependency graph
        Map<String, Set<String>> dependencies = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();

        for (ScenarioStep step : steps) {
            dependencies.put(step.id(), new HashSet<>(step.dependsOn()));
            dependents.computeIfAbsent(step.id(), k -> new HashSet<>());
            for (String dep : step.dependsOn()) {
                dependents.computeIfAbsent(dep, k -> new HashSet<>()).add(step.id());
            }
        }

        // Find steps with no dependencies (ready to run)
        List<ScenarioStep> readySteps = steps.stream()
            .filter(s -> dependencies.get(s.id()).isEmpty())
            .collect(Collectors.toList());

        if (readySteps.isEmpty()) {
            throw new ScenarioExecutionException("No steps ready to run - circular dependency detected");
        }

        // Execute in dependency order
        Set<String> completed = new HashSet<>();
        Set<String> failed = new HashSet<>();

        while (!readySteps.isEmpty() && !execution.isCancelled()) {
            // Group steps that can run in parallel (same dependency level)
            List<ScenarioStep> parallelBatch = new ArrayList<>(readySteps);
            readySteps.clear();

            if (execution.getConfig().parallelExecution()) {
                // Execute batch in parallel using virtual threads
                List<StepResult> batchResults = executeBatchParallel(execution, parallelBatch, completedResults);
                for (StepResult result : batchResults) {
                    completedResults.put(result.stepId(), result);
                    if (result.success()) {
                        completed.add(result.stepId());
                        updateReadySteps(dependencies, dependents, completed, failed, result.stepId(), steps, readySteps);
                    } else {
                        failed.add(result.stepId());
                        // Check if any dependent steps can still run
                        updateReadyStepsForFailure(dependencies, dependents, completed, failed, result.stepId(), steps, readySteps);
                    }
                }
            } else {
                // Execute sequentially
                for (ScenarioStep step : parallelBatch) {
                    if (execution.isCancelled()) {
                        throw new ScenarioExecutionException("Scenario cancelled");
                    }

                    StepResult result = executeStep(execution, step, completedResults);
                    completedResults.put(step.id(), result);
                    execution.addCompletedStep(result);

                    if (result.success()) {
                        completed.add(step.id());
                        updateReadySteps(dependencies, dependents, completed, failed, step.id(), steps, readySteps);
                    } else {
                        failed.add(step.id());
                        updateReadyStepsForFailure(dependencies, dependents, completed, failed, step.id(), steps, readySteps);
                    }
                }
            }
        }

        // Check for failures
        List<StepResult> failures = completedResults.values().stream()
            .filter(r -> !r.success())
            .toList();

        if (!failures.isEmpty()) {
            String failureMessages = failures.stream()
                .map(r -> r.stepId() + ": " + r.error())
                .collect(Collectors.joining(", "));
            throw new ScenarioExecutionException("Steps failed: " + failureMessages);
        }

        if (execution.isCancelled()) {
            throw new ScenarioExecutionException("Scenario cancelled");
        }

        return new ArrayList<>(completedResults.values());
    }

    private List<StepResult> executeBatchParallel(ScenarioExecution execution,
            List<ScenarioStep> batch, Map<String, StepResult> completedResults) {

        List<StepResult> results = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<StepResult>> futures = batch.stream()
                .map(step -> CompletableFuture.supplyAsync(
                    () -> executeStep(execution, step, completedResults),
                    executor))
                .toList();

            // Wait for all to complete with overall timeout
            Duration scenarioTimeout = execution.getScenario().timeout();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(scenarioTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

            for (CompletableFuture<StepResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    results.add(new StepResult(
                        "unknown", false, null, null, null,
                        Instant.now(), Instant.now(),
                        cause != null ? cause.getMessage() : e.getMessage()
                    ));
                }
            }

        } catch (TimeoutException e) {
            LOGGER.error("Parallel batch execution timed out");
            for (ScenarioStep step : batch) {
                if (results.stream().noneMatch(r -> r.stepId().equals(step.id()))) {
                    results.add(new StepResult(
                        step.id(), false, null, null, null,
                        Instant.now(), Instant.now(), "Execution timed out"
                    ));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            for (ScenarioStep step : batch) {
                results.add(new StepResult(
                    step.id(), false, null, null, null,
                    Instant.now(), Instant.now(), "Execution interrupted"
                ));
            }
        } catch (ExecutionException e) {
            LOGGER.error("Parallel batch execution failed", e);
        }

        return results;
    }

    private StepResult executeStep(ScenarioExecution execution, ScenarioStep step,
            Map<String, StepResult> completedResults) {

        Instant startTime = Instant.now();
        String executionId = execution.getExecutionId();

        LOGGER.debug("[{}] Executing step: {} (agent={}, skill={})",
            executionId, step.id(), step.agentId(), step.skillId());
        emitEvent(ScenarioEvent.stepStarted(executionId, execution.getScenario().id(), step.id()));

        try {
            // Resolve agent
            GregVerseAgent agent = registry.getAgent(step.agentId());
            if (agent == null) {
                throw new ScenarioExecutionException("Agent not found: " + step.agentId());
            }

            // Resolve input variables
            String resolvedInput = resolveVariables(step.input(), completedResults, execution.getScenario().variables());

            // Execute with timeout
            String output = executeWithTimeout(agent, step.skillId(), resolvedInput, step.timeout());

            Instant endTime = Instant.now();

            StepResult result = new StepResult(
                step.id(),
                true,
                step.agentId(),
                step.skillId(),
                output,
                startTime,
                endTime,
                null
            );

            LOGGER.debug("[{}] Step completed: {} (duration={})",
                executionId, step.id(), Duration.between(startTime, endTime));
            emitEvent(ScenarioEvent.stepCompleted(executionId, execution.getScenario().id(), step.id(),
                Duration.between(startTime, endTime)));

            return result;

        } catch (TimeoutException e) {
            Instant endTime = Instant.now();
            LOGGER.warn("[{}] Step timed out: {}", executionId, step.id());
            emitEvent(ScenarioEvent.stepFailed(executionId, execution.getScenario().id(), step.id(), "Timeout"));

            return new StepResult(
                step.id(), false, step.agentId(), step.skillId(), null,
                startTime, endTime, "Step execution timed out after " + step.timeout()
            );

        } catch (Exception e) {
            Instant endTime = Instant.now();
            LOGGER.error("[{}] Step failed: {} - {}", executionId, step.id(), e.getMessage());
            emitEvent(ScenarioEvent.stepFailed(executionId, execution.getScenario().id(), step.id(), e.getMessage()));

            return new StepResult(
                step.id(), false, step.agentId(), step.skillId(), null,
                startTime, endTime, e.getMessage()
            );
        }
    }

    private String executeWithTimeout(GregVerseAgent agent, String skillId, String input, Duration timeout)
            throws TimeoutException {

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            if (skillId != null && !skillId.isBlank()) {
                return agent.processSkillQuery(skillId, input);
            } else {
                return agent.processQuery(input);
            }
        });

        try {
            return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Step execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("Step execution failed: " +
                (cause != null ? cause.getMessage() : e.getMessage()), e);
        }
    }

    private String resolveVariables(String input, Map<String, StepResult> completedResults,
            Map<String, Object> scenarioVariables) {

        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input cannot be null or blank for variable resolution");
        }

        String resolved = input;

        // Resolve scenario variables: ${varName}
        for (Map.Entry<String, Object> entry : scenarioVariables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (resolved.contains(placeholder)) {
                resolved = resolved.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }

        // Resolve step outputs: ${stepId.output}
        for (Map.Entry<String, StepResult> entry : completedResults.entrySet()) {
            String outputPlaceholder = "${" + entry.getKey() + ".output}";
            if (resolved.contains(outputPlaceholder) && entry.getValue().output() != null) {
                resolved = resolved.replace(outputPlaceholder, entry.getValue().output());
            }
        }

        return resolved;
    }

    private void updateReadySteps(Map<String, Set<String>> dependencies,
            Map<String, Set<String>> dependents, Set<String> completed, Set<String> failed,
            String completedStep, List<ScenarioStep> allSteps, List<ScenarioStep> readySteps) {

        Set<String> newlyReady = dependents.getOrDefault(completedStep, Collections.emptySet());

        for (String dependentId : newlyReady) {
            if (completed.contains(dependentId) || failed.contains(dependentId)) {
                continue;
            }

            Set<String> deps = dependencies.get(dependentId);
            boolean allDepsComplete = completed.containsAll(deps);

            if (allDepsComplete) {
                allSteps.stream()
                    .filter(s -> s.id().equals(dependentId))
                    .findFirst()
                    .ifPresent(readySteps::add);
            }
        }
    }

    private void updateReadyStepsForFailure(Map<String, Set<String>> dependencies,
            Map<String, Set<String>> dependents, Set<String> completed, Set<String> failed,
            String failedStep, List<ScenarioStep> allSteps, List<ScenarioStep> readySteps) {

        // Mark all dependents as failed too (cascading failure)
        Set<String> toFail = new HashSet<>();
        collectDependents(failedStep, dependents, toFail, failed);

        for (String stepId : toFail) {
            if (!failed.contains(stepId) && !completed.contains(stepId)) {
                failed.add(stepId);
            }
        }
    }

    private void collectDependents(String stepId, Map<String, Set<String>> dependents,
            Set<String> collected, Set<String> alreadyFailed) {

        Set<String> directDependents = dependents.getOrDefault(stepId, Collections.emptySet());
        for (String dependent : directDependents) {
            if (!collected.contains(dependent) && !alreadyFailed.contains(dependent)) {
                collected.add(dependent);
                collectDependents(dependent, dependents, collected, alreadyFailed);
            }
        }
    }

    private List<CompensationResult> executeCompensations(ScenarioExecution execution,
            ScenarioExecutionException failure) {

        Scenario scenario = execution.getScenario();
        List<Compensation> compensations = scenario.compensations();

        if (compensations.isEmpty()) {
            return Collections.emptyList();
        }

        LOGGER.info("[{}] Executing {} compensations", execution.getExecutionId(), compensations.size());
        List<CompensationResult> results = new ArrayList<>();

        // Execute compensations in reverse order of completed steps
        List<String> completedStepIds = execution.getCompletedSteps().stream()
            .map(StepResult::stepId)
            .collect(Collectors.toList());
        Collections.reverse(completedStepIds);

        Set<String> compensatedSteps = new HashSet<>();

        for (String stepId : completedStepIds) {
            for (Compensation comp : compensations) {
                if (comp.stepId().equals(stepId) && !compensatedSteps.contains(stepId)) {
                    CompensationResult result = executeCompensation(execution, comp);
                    results.add(result);
                    compensatedSteps.add(stepId);
                }
            }
        }

        return results;
    }

    private CompensationResult executeCompensation(ScenarioExecution execution, Compensation comp) {
        Instant startTime = Instant.now();
        String executionId = execution.getExecutionId();

        LOGGER.debug("[{}] Executing compensation: {} for step: {}",
            executionId, comp.action(), comp.stepId());
        emitEvent(ScenarioEvent.compensationStarted(executionId, execution.getScenario().id(), comp.stepId()));

        try {
            String agentId = comp.agentId();
            if (agentId != null) {
                GregVerseAgent agent = registry.getAgent(agentId);
                if (agent != null) {
                    String input = String.format("Execute compensation action: %s for step: %s. Parameters: %s",
                        comp.action(), comp.stepId(), comp.params());
                    agent.processQuery(input);
                }
            }

            Instant endTime = Instant.now();
            emitEvent(ScenarioEvent.compensationCompleted(executionId, execution.getScenario().id(), comp.stepId()));

            return new CompensationResult(
                comp.stepId(), comp.action(), true,
                startTime, endTime, null
            );

        } catch (Exception e) {
            Instant endTime = Instant.now();
            LOGGER.error("[{}] Compensation failed: {} - {}", executionId, comp.stepId(), e.getMessage());

            return new CompensationResult(
                comp.stepId(), comp.action(), false,
                startTime, endTime, e.getMessage()
            );
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    // --- Inner classes for scenario model and results ---

    /**
     * Represents a complete scenario definition.
     *
     * @param id unique scenario identifier
     * @param name human-readable name
     * @param description scenario description
     * @param timeout overall execution timeout
     * @param steps ordered list of steps to execute
     * @param compensations rollback actions for failures
     * @param variables scenario-level variables
     */
    public record Scenario(
        String id,
        String name,
        String description,
        Duration timeout,
        List<ScenarioStep> steps,
        List<Compensation> compensations,
        Map<String, Object> variables
    ) {}

    /**
     * Represents a single step in a scenario.
     *
     * @param id unique step identifier
     * @param agentId agent to invoke
     * @param skillId skill to invoke (optional)
     * @param input input data with variable interpolation
     * @param description step description
     * @param timeout step execution timeout
     * @param dependsOn list of step IDs that must complete first
     * @param config additional step configuration
     */
    public record ScenarioStep(
        String id,
        String agentId,
        String skillId,
        String input,
        String description,
        Duration timeout,
        List<String> dependsOn,
        Map<String, Object> config
    ) {}

    /**
     * Represents a compensation action for rollback.
     *
     * @param stepId the step this compensation applies to
     * @param action the compensation action to perform
     * @param agentId optional agent to invoke for compensation
     * @param params additional parameters for compensation
     */
    public record Compensation(
        String stepId,
        String action,
        String agentId,
        Map<String, Object> params
    ) {}

    /**
     * Result of executing a single step.
     *
     * @param stepId the step identifier
     * @param success whether execution succeeded
     * @param agentId the agent that was invoked
     * @param skillId the skill that was invoked
     * @param output the output from the step
     * @param startTime execution start time
     * @param endTime execution end time
     * @param error error message if failed
     */
    public record StepResult(
        String stepId,
        boolean success,
        String agentId,
        String skillId,
        String output,
        Instant startTime,
        Instant endTime,
        String error
    ) {
        /**
         * Returns the execution duration.
         *
         * @return duration between start and end time
         */
        public Duration duration() {
            return Duration.between(startTime, endTime);
        }
    }

    /**
     * Result of executing a compensation action.
     *
     * @param stepId the step that was compensated
     * @param action the compensation action performed
     * @param success whether compensation succeeded
     * @param startTime compensation start time
     * @param endTime compensation end time
     * @param error error message if failed
     */
    public record CompensationResult(
        String stepId,
        String action,
        boolean success,
        Instant startTime,
        Instant endTime,
        String error
    ) {}

    /**
     * Result of executing a complete scenario.
     *
     * @param executionId unique execution identifier
     * @param scenarioId scenario that was executed
     * @param success whether execution succeeded
     * @param stepResults results from all steps
     * @param compensationResults results from compensations (if any)
     * @param duration total execution duration
     * @param startTime execution start time
     * @param endTime execution end time
     * @param error error message if failed
     */
    public record ScenarioResult(
        String executionId,
        String scenarioId,
        boolean success,
        List<StepResult> stepResults,
        List<CompensationResult> compensationResults,
        Duration duration,
        Instant startTime,
        Instant endTime,
        String error
    ) {
        /**
         * Returns the number of successful steps.
         *
         * @return count of successful steps
         */
        public int getSuccessfulSteps() {
            return (int) stepResults.stream().filter(StepResult::success).count();
        }

        /**
         * Returns the number of failed steps.
         *
         * @return count of failed steps
         */
        public int getFailedSteps() {
            return (int) stepResults.stream().filter(r -> !r.success()).count();
        }

        /**
         * Converts this result to a GregVerseReport.
         *
         * @return the report representation
         */
        public GregVerseReport toReport() {
            GregVerseReport.Builder builder = GregVerseReport.builder()
                .scenarioId(scenarioId)
                .generatedAt(startTime)
                .totalDuration(duration);

            for (StepResult stepResult : stepResults) {
                builder.addAgentResult(new GregVerseReport.AgentResult(
                    stepResult.agentId() != null ? stepResult.agentId() : "unknown",
                    stepResult.agentId() != null ? stepResult.agentId() : "unknown",
                    stepResult.success(),
                    stepResult.startTime(),
                    stepResult.endTime(),
                    Collections.emptyList(),
                    stepResult.output(),
                    stepResult.error()
                ));
            }

            return builder.build();
        }
    }

    /**
     * Event emitted during scenario execution.
     *
     * @param type event type
     * @param executionId execution identifier
     * @param scenarioId scenario identifier
     * @param stepId step identifier (for step events)
     * @param timestamp event timestamp
     * @param message event message
     * @param duration duration (for completion events)
     */
    public record ScenarioEvent(
        EventType type,
        String executionId,
        String scenarioId,
        String stepId,
        Instant timestamp,
        String message,
        Duration duration
    ) {
        /** Event types for scenario execution */
        public enum EventType {
            SCENARIO_STARTED,
            SCENARIO_COMPLETED,
            SCENARIO_FAILED,
            SCENARIO_CANCELLED,
            STEP_STARTED,
            STEP_COMPLETED,
            STEP_FAILED,
            COMPENSATION_STARTED,
            COMPENSATION_COMPLETED
        }

        static ScenarioEvent started(String executionId, String scenarioId) {
            return new ScenarioEvent(EventType.SCENARIO_STARTED, executionId, scenarioId,
                null, Instant.now(), "Scenario started", null);
        }

        static ScenarioEvent completed(String executionId, String scenarioId, Duration duration) {
            return new ScenarioEvent(EventType.SCENARIO_COMPLETED, executionId, scenarioId,
                null, Instant.now(), "Scenario completed", duration);
        }

        static ScenarioEvent failed(String executionId, String scenarioId, String message) {
            return new ScenarioEvent(EventType.SCENARIO_FAILED, executionId, scenarioId,
                null, Instant.now(), message, null);
        }

        static ScenarioEvent cancelled(String executionId, String scenarioId) {
            return new ScenarioEvent(EventType.SCENARIO_CANCELLED, executionId, scenarioId,
                null, Instant.now(), "Scenario cancelled", null);
        }

        static ScenarioEvent stepStarted(String executionId, String scenarioId, String stepId) {
            return new ScenarioEvent(EventType.STEP_STARTED, executionId, scenarioId,
                stepId, Instant.now(), "Step started", null);
        }

        static ScenarioEvent stepCompleted(String executionId, String scenarioId, String stepId, Duration duration) {
            return new ScenarioEvent(EventType.STEP_COMPLETED, executionId, scenarioId,
                stepId, Instant.now(), "Step completed", duration);
        }

        static ScenarioEvent stepFailed(String executionId, String scenarioId, String stepId, String message) {
            return new ScenarioEvent(EventType.STEP_FAILED, executionId, scenarioId,
                stepId, Instant.now(), message, null);
        }

        static ScenarioEvent compensationStarted(String executionId, String scenarioId, String stepId) {
            return new ScenarioEvent(EventType.COMPENSATION_STARTED, executionId, scenarioId,
                stepId, Instant.now(), "Compensation started", null);
        }

        static ScenarioEvent compensationCompleted(String executionId, String scenarioId, String stepId) {
            return new ScenarioEvent(EventType.COMPENSATION_COMPLETED, executionId, scenarioId,
                stepId, Instant.now(), "Compensation completed", null);
        }
    }

    /**
     * Runtime state for a scenario execution.
     */
    public static class ScenarioExecution {
        private final String executionId;
        private final Scenario scenario;
        private final GregVerseConfig config;
        private final List<StepResult> completedSteps;
        private volatile boolean cancelled;

        ScenarioExecution(String executionId, Scenario scenario, GregVerseConfig config) {
            this.executionId = executionId;
            this.scenario = scenario;
            this.config = config;
            this.completedSteps = new ArrayList<>();
            this.cancelled = false;
        }

        public String getExecutionId() {
            return executionId;
        }

        public Scenario getScenario() {
            return scenario;
        }

        public GregVerseConfig getConfig() {
            return config;
        }

        public List<StepResult> getCompletedSteps() {
            return List.copyOf(completedSteps);
        }

        public void addCompletedStep(StepResult result) {
            completedSteps.add(result);
        }

        public void cancel() {
            this.cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public int getProgress() {
            if (scenario.steps().isEmpty()) {
                return 0;
            }
            return (completedSteps.size() * 100) / scenario.steps().size();
        }
    }

    /**
     * Exception thrown when scenario loading fails.
     */
    public static class ScenarioLoadException extends Exception {
        public ScenarioLoadException(String message) {
            super(message);
        }

        public ScenarioLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when scenario execution fails.
     */
    public static class ScenarioExecutionException extends Exception {
        private final ScenarioResult partialResult;

        public ScenarioExecutionException(String message) {
            super(message);
            this.partialResult = null;
        }

        public ScenarioExecutionException(String message, Throwable cause) {
            super(message, cause);
            this.partialResult = null;
        }

        public ScenarioExecutionException(String message, Throwable cause, ScenarioResult partialResult) {
            super(message, cause);
            this.partialResult = partialResult;
        }

        public Optional<ScenarioResult> getPartialResult() {
            return Optional.ofNullable(partialResult);
        }
    }

    /**
     * Wrapper for completion exceptions to preserve checked exception semantics.
     */
    private static class CompletionExceptionWrapper extends RuntimeException {
        public CompletionExceptionWrapper(ScenarioExecutionException cause) {
            super(cause);
        }

        @Override
        public synchronized ScenarioExecutionException getCause() {
            return (ScenarioExecutionException) super.getCause();
        }
    }
}
