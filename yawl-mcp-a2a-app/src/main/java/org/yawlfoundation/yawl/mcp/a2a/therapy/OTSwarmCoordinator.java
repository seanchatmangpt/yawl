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

package org.yawlfoundation.yawl.mcp.a2a.therapy;

import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.mcp.a2a.demo.execution.ExecutionHarness;
import org.yawlfoundation.yawl.mcp.a2a.demo.execution.ExecutionHarness.ExecutionResult;
import org.yawlfoundation.yawl.mcp.a2a.demo.execution.ExecutionHarness.PatternExecutionException;
import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator for the Occupational Therapy Lifestyle Redesign Swarm.
 *
 * <p>Manages the complete execution lifecycle of the OT workflow using YAWL as the
 * process engine. The coordinator:</p>
 * <ol>
 *   <li>Loads the YAML workflow specification from classpath (OTWorkflowSpec)</li>
 *   <li>Converts YAML to YAWL XML via ExtendedYamlConverter</li>
 *   <li>Launches a case in YStatelessEngine</li>
 *   <li>Routes each workflow task to the corresponding OTSwarmAgent via OTAgentRegistry</li>
 *   <li>Feeds agent outputs back into the YAWL workflow data context</li>
 *   <li>Handles the ProgressMonitoring decision (progressScore ≥ 85%?)</li>
 *   <li>Manages the adaptation loop (max cycles enforced)</li>
 *   <li>Collects results and produces OTSwarmResult</li>
 * </ol>
 *
 * <h2>Execution Flow</h2>
 * <p>The coordinator uses ExecutionHarness to run the YAWL workflow with:</p>
 * <ul>
 *   <li>Auto-task completion enabled (configurable via OTSwarmConfig)</li>
 *   <li>A decision provider that evaluates progressScore at ProgressMonitoring</li>
 *   <li>An auto-task handler that delegates to swarm agents</li>
 *   <li>Timeout enforcement per OTSwarmConfig</li>
 * </ul>
 *
 * <h2>Context Map Semantics</h2>
 * <p>A thread-safe ConcurrentHashMap is shared across all agent invocations,
 * enabling downstream agents to read upstream results. Each agent deposits
 * its outputs under phase-specific keys for downstream access.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see OTAgentRegistry
 * @see OTSwarmAgent
 * @see OTSwarmConfig
 * @see OTSwarmResult
 */
public class OTSwarmCoordinator {

    private final OTAgentRegistry registry;
    private final ExtendedYamlConverter yamlConverter;
    private final YStatelessEngine engine;

    /**
     * Constructs a new coordinator with default dependencies.
     *
     * <p>Instantiates a new OTAgentRegistry, ExtendedYamlConverter, and YStatelessEngine.</p>
     */
    public OTSwarmCoordinator() {
        this.registry = new OTAgentRegistry();
        this.yamlConverter = new ExtendedYamlConverter();
        this.engine = new YStatelessEngine();
    }

    /**
     * Constructs a coordinator with injected dependencies (for testing).
     *
     * @param registry the agent registry
     * @param yamlConverter the YAML-to-XML converter
     * @param engine the YAWL stateless engine
     */
    public OTSwarmCoordinator(OTAgentRegistry registry, ExtendedYamlConverter yamlConverter,
                              YStatelessEngine engine) {
        this.registry = Objects.requireNonNull(registry, "Registry cannot be null");
        this.yamlConverter = Objects.requireNonNull(yamlConverter, "YAML converter cannot be null");
        this.engine = Objects.requireNonNull(engine, "Engine cannot be null");
    }

    /**
     * Execute the full OT swarm lifecycle for the given patient.
     *
     * <p>Orchestrates the 8-phase workflow, handling adaptation loops and outcome
     * evaluation. Returns an OTSwarmResult describing the execution outcome.</p>
     *
     * @param patient the patient to treat (non-null)
     * @param config execution configuration (non-null)
     * @return result of swarm execution (never null)
     * @throws IllegalArgumentException if patient or config is null
     * @throws IOException if YAML resource cannot be loaded
     * @throws YSyntaxException if YAML-to-XML conversion fails
     */
    public OTSwarmResult execute(OTPatient patient, OTSwarmConfig config)
            throws IOException, YSyntaxException {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        String workflowXml = loadAndConvertWorkflow();
        YSpecification spec = engine.unmarshalSpecification(workflowXml);

        String caseId = "case-" + config.patientId() + "-" + Instant.now().toEpochMilli();
        Map<String, Object> context = new ConcurrentHashMap<>();
        List<SwarmTaskResult> agentResults = new ArrayList<>();

        context.put("patientId", config.patientId());
        context.put("progressScore", 0.0);
        context.put("adaptationCycle", 0);
        context.put("caseStartTime", Instant.now().toString());

        try {
            ExecutionHarness harness = ExecutionHarness.create(engine)
                    .withSpecification(caseId)
                    .withAutoCompletion(config.autoAdvance())
                    .withTimeout(config.timeout())
                    .withDecisionProvider(taskId -> makeDecision(taskId, context))
                    .withAutoTaskHandler(workItem -> handleAutoTask(workItem, patient, context, agentResults));

            ExecutionResult result = harness.execute(spec);

            if (!result.isSuccess()) {
                PatternExecutionException error = result.getError();
                String summary = error != null ? error.getMessage() : "Unknown execution error";
                return buildFailureResult(caseId, patient, config, agentResults, summary);
            }

            SwarmPhase completedPhase = determineCompletedPhase(agentResults);
            boolean success = completedPhase == SwarmPhase.OUTCOME_EVALUATION;
            String summary = buildSummary(agentResults, success);

            return buildSuccessResult(caseId, patient, context, agentResults, completedPhase,
                    success, summary);

        } catch (PatternExecutionException e) {
            String summary = "Workflow execution failed: " + e.getMessage();
            return buildFailureResult(caseId, patient, config, agentResults, summary);
        }
    }

    /**
     * Load the OT workflow YAML specification and convert it to YAWL XML.
     *
     * <p>Attempts to load from classpath resource ({@link OTWorkflowSpec#RESOURCE_PATH}).
     * Falls back to inline YAML ({@link OTWorkflowSpec#THERAPY_WORKFLOW_YAML}) if resource
     * is not available. Converts YAML to YAWL XML using ExtendedYamlConverter.</p>
     *
     * @return YAWL XML specification string
     * @throws IOException if both resource and fallback fail
     * @throws YSyntaxException if YAML-to-XML conversion fails
     */
    private String loadAndConvertWorkflow() throws IOException, YSyntaxException {
        String yaml = loadWorkflowYaml();
        return yamlConverter.convertToXml(yaml);
    }

    /**
     * Load workflow YAML from classpath resource or fallback to inline.
     *
     * @return YAML specification string
     * @throws IOException if resource cannot be loaded and fallback is used
     */
    private String loadWorkflowYaml() throws IOException {
        try (InputStream stream = getClass().getClassLoader()
                .getResourceAsStream(OTWorkflowSpec.RESOURCE_PATH)) {
            if (stream != null) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Fall through to inline YAML
        }
        return OTWorkflowSpec.THERAPY_WORKFLOW_YAML;
    }

    /**
     * Auto-task handler that delegates YAWL tasks to swarm agents.
     *
     * <p>Extracts the task ID from the work item and routes to the corresponding
     * agent via the registry. Collects the agent result and updates context.</p>
     *
     * @param workItem the YAWL work item to execute
     * @param patient the patient being treated
     * @param context shared context map
     * @param agentResults accumulating list of agent results
     */
    private void handleAutoTask(Object workItem, OTPatient patient, Map<String, Object> context,
                               List<SwarmTaskResult> agentResults) {
        String taskId = extractTaskId(workItem);
        SwarmPhase phase = mapTaskIdToPhase(taskId);

        if (phase == null) {
            return;
        }

        OTSwarmAgent agent = registry.getAgent(phase);
        SwarmTaskResult result = agent.execute(patient, context);
        agentResults.add(result);

        if (result.data() != null) {
            context.putAll(result.data());
        }

        if (phase == SwarmPhase.PROGRESS_MONITORING && result.success()) {
            Object progressObj = result.data().get("progressScore");
            if (progressObj instanceof Number num) {
                context.put("progressScore", num.doubleValue());
            }
        }

        if (phase == SwarmPhase.ADAPTATION && result.success()) {
            Object cycleObj = context.get("adaptationCycle");
            int cycle = cycleObj instanceof Number n ? n.intValue() : 0;
            context.put("adaptationCycle", cycle + 1);
        }
    }

    /**
     * Decision provider for ProgressMonitoring conditional split.
     *
     * <p>Evaluates progressScore from context:
     * If ≥ 85.0, returns "OutcomeEvaluation".
     * Otherwise, returns "PlanAdaptation".</p>
     *
     * @param taskId the YAWL task identifier
     * @param context the shared context map
     * @return the chosen flow identifier
     */
    private String makeDecision(String taskId, Map<String, Object> context) {
        if (!isProgressMonitoringTask(taskId)) {
            return "default";
        }

        Object progressObj = context.get("progressScore");
        double progressScore = progressObj instanceof Number n ? n.doubleValue() : 0.0;

        return progressScore >= 85.0 ? "OutcomeEvaluation" : "PlanAdaptation";
    }

    /**
     * Extracts the task ID from a YAWL work item.
     *
     * <p>Invokes the work item's {@code get_thisID()} method via reflection.
     * If the method is not available or invocation fails, returns the string
     * representation of the work item.</p>
     *
     * @param workItem the work item object
     * @return the task identifier string (never null, may be empty if work item is null)
     * @throws IllegalArgumentException if work item is null
     */
    private String extractTaskId(Object workItem) {
        if (workItem == null) {
            throw new IllegalArgumentException("Work item cannot be null in task extraction");
        }
        try {
            var method = workItem.getClass().getMethod("get_thisID");
            Object result = method.invoke(workItem);
            return result != null ? result.toString() : workItem.toString();
        } catch (NoSuchMethodException e) {
            return workItem.toString();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Failed to extract task ID from work item: " + e.getMessage(), e);
        }
    }

    /**
     * Map a workflow task ID to its corresponding SwarmPhase.
     *
     * <p>Uses simple string matching against known task IDs from OTWorkflowSpec.</p>
     *
     * @param taskId the task identifier
     * @return the corresponding SwarmPhase, or null if not recognized
     */
    private SwarmPhase mapTaskIdToPhase(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return switch (taskId) {
            case "PatientIntake" -> SwarmPhase.INTAKE;
            case "OccupationalAssessment" -> SwarmPhase.ASSESSMENT;
            case "GoalSetting" -> SwarmPhase.GOAL_SETTING;
            case "InterventionPlanning" -> SwarmPhase.INTERVENTION_PLANNING;
            case "SessionScheduling" -> SwarmPhase.SCHEDULING;
            case "ProgressMonitoring" -> SwarmPhase.PROGRESS_MONITORING;
            case "PlanAdaptation" -> SwarmPhase.ADAPTATION;
            case "OutcomeEvaluation" -> SwarmPhase.OUTCOME_EVALUATION;
            default -> null;
        };
    }

    /**
     * Check if the given task ID represents ProgressMonitoring.
     *
     * @param taskId the task identifier
     * @return true if this is the ProgressMonitoring decision point
     */
    private boolean isProgressMonitoringTask(String taskId) {
        return "ProgressMonitoring".equals(taskId);
    }

    /**
     * Determine the last phase that completed successfully.
     *
     * @param agentResults list of agent execution results
     * @return the latest completed phase, or INTAKE if empty
     */
    private SwarmPhase determineCompletedPhase(List<SwarmTaskResult> agentResults) {
        if (agentResults.isEmpty()) {
            return SwarmPhase.INTAKE;
        }
        SwarmPhase latest = SwarmPhase.INTAKE;
        for (SwarmTaskResult result : agentResults) {
            if (result.success()) {
                latest = result.phase();
            }
        }
        return latest;
    }

    /**
     * Build a result summary narrative.
     *
     * @param agentResults list of agent results
     * @param success whether the swarm completed successfully
     * @return narrative summary string
     */
    private String buildSummary(List<SwarmTaskResult> agentResults, boolean success) {
        if (agentResults.isEmpty()) {
            return success ? "Swarm completed (no agent output)" : "Swarm terminated with no results";
        }

        long successCount = agentResults.stream().filter(SwarmTaskResult::success).count();
        long failureCount = agentResults.size() - successCount;

        String summary = String.format("Swarm lifecycle: %d agents succeeded, %d failed. ",
                successCount, failureCount);

        if (success) {
            summary += "Patient therapy plan completed successfully and discharged.";
        } else {
            summary += "Patient therapy plan requires further iteration or was interrupted.";
        }

        return summary;
    }

    /**
     * Build a success result when workflow completes normally.
     *
     * @param caseId YAWL case identifier
     * @param patient the patient treated
     * @param context execution context containing shared data
     * @param agentResults list of agent results
     * @param completedPhase the final completed phase
     * @param success whether outcome was successful
     * @param summary narrative summary
     * @return OTSwarmResult
     */
    private OTSwarmResult buildSuccessResult(String caseId, OTPatient patient,
                                            Map<String, Object> context,
                                            List<SwarmTaskResult> agentResults,
                                            SwarmPhase completedPhase,
                                            boolean success, String summary) {
        OccupationalProfile profile = extractProfile(context);
        List<LifestyleGoal> goals = extractGoals(context);
        List<TherapySession> sessions = extractSessions(context);

        return new OTSwarmResult(
                caseId, patient, profile, goals, sessions, agentResults, completedPhase, success, summary
        );
    }

    /**
     * Build a failure result when workflow execution fails.
     *
     * @param caseId YAWL case identifier
     * @param patient the patient treated
     * @param config execution configuration
     * @param agentResults partial list of agent results (if any)
     * @param errorMessage the error message
     * @return OTSwarmResult with failure flag and error message
     */
    private OTSwarmResult buildFailureResult(String caseId, OTPatient patient, OTSwarmConfig config,
                                            List<SwarmTaskResult> agentResults, String errorMessage) {
        SwarmPhase lastPhase = determineCompletedPhase(agentResults);
        String summary = "Execution failed: " + errorMessage;

        return new OTSwarmResult(
                caseId, patient, null, List.of(), List.of(),
                agentResults, lastPhase, false, summary
        );
    }

    /**
     * Extract occupational profile from context (if available).
     *
     * @param context the execution context
     * @return OccupationalProfile or null if not present
     */
    private OccupationalProfile extractProfile(Map<String, Object> context) {
        Object profileObj = context.get("profile");
        if (profileObj instanceof OccupationalProfile profile) {
            return profile;
        }
        return null;
    }

    /**
     * Extract lifestyle goals from context (if available).
     *
     * @param context the execution context
     * @return unmodifiable list of goals
     */
    @SuppressWarnings("unchecked")
    private List<LifestyleGoal> extractGoals(Map<String, Object> context) {
        Object goalsObj = context.get("goals");
        if (goalsObj instanceof List<?> list) {
            List<LifestyleGoal> typedList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof LifestyleGoal goal) {
                    typedList.add(goal);
                }
            }
            return List.copyOf(typedList);
        }
        return List.of();
    }

    /**
     * Extract therapy sessions from context (if available).
     *
     * @param context the execution context
     * @return unmodifiable list of sessions
     */
    @SuppressWarnings("unchecked")
    private List<TherapySession> extractSessions(Map<String, Object> context) {
        Object sessionsObj = context.get("sessions");
        if (sessionsObj instanceof List<?> list) {
            List<TherapySession> typedList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof TherapySession session) {
                    typedList.add(session);
                }
            }
            return List.copyOf(typedList);
        }
        return List.of();
    }
}
