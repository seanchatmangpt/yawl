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
import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OTPatient;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.OccupationalProfile;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.SwarmTaskResult;
import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.TherapySession;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;

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
 * <p>Manages the complete execution lifecycle of the OT workflow. On each call to
 * {@link #execute}, the coordinator:</p>
 * <ol>
 *   <li>Loads the YAML workflow specification and validates it against the YAWL engine.</li>
 *   <li>Runs all 8 OT agents <em>sequentially</em> in the calling thread, sharing a
 *       {@link ConcurrentHashMap} context so each agent can read earlier results.</li>
 *   <li>Applies the adaptation loop programmatically (max {@code maxAdaptationCycles}
 *       times) when progress score is below the 85 % advancement threshold.</li>
 *   <li>Collects agent results and produces an {@link OTSwarmResult}.</li>
 * </ol>
 *
 * <h2>Why sequential rather than YAWL-event-driven?</h2>
 * <p>The {@link org.yawlfoundation.yawl.stateless.YStatelessEngine} fires work-item
 * events asynchronously via a virtual-thread executor. The unsynchronised
 * {@code isEmpty()} check in {@code YNetRunner.isCompleted()} can transiently return
 * {@code true} between task firings, causing the polling loop to exit before all agents
 * have written their results. Running agents directly in the calling thread eliminates
 * the race condition without altering the YAWL core. The YAWL XML spec is still
 * loaded and validated, so the workflow definition remains the authoritative artefact.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see OTAgentRegistry
 * @see OTSwarmAgent
 * @see OTSwarmConfig
 * @see OTSwarmResult
 */
public class OTSwarmCoordinator {

    /** Progress threshold above which the adaptation loop is skipped. */
    private static final double ADVANCEMENT_THRESHOLD = 0.85;

    private final OTAgentRegistry registry;
    private final ExtendedYamlConverter yamlConverter;
    private final YStatelessEngine engine;

    /**
     * Constructs a new coordinator with default dependencies.
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
     * @param engine the YAWL stateless engine (used for spec validation only)
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
     * <p>Loads and validates the YAWL workflow specification, then runs all 8 OT agents
     * in sequence. Returns an {@link OTSwarmResult} describing the execution outcome.</p>
     *
     * @param patient the patient to treat (non-null)
     * @param config  execution configuration (non-null)
     * @return result of swarm execution (never null)
     * @throws IllegalArgumentException if patient or config is null
     * @throws IOException              if YAML resource cannot be loaded
     * @throws YSyntaxException         if YAML-to-XML conversion fails
     */
    public OTSwarmResult execute(OTPatient patient, OTSwarmConfig config)
            throws IOException, YSyntaxException {
        if (patient == null) {
            throw new IllegalArgumentException("Patient cannot be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        // Load and validate YAWL spec (the spec is the authoritative workflow definition)
        String workflowXml = loadAndConvertWorkflow();
        engine.unmarshalSpecification(workflowXml);

        String caseId = "case-" + config.patientId() + "-" + Instant.now().toEpochMilli();
        Map<String, Object> context = new ConcurrentHashMap<>();
        List<SwarmTaskResult> agentResults = new ArrayList<>();

        context.put("patientId", config.patientId());
        context.put("progressScore", 0.0);
        context.put("adaptationCycle", 0);
        context.put("caseStartTime", Instant.now().toString());

        try {
            runAgentSequence(patient, config, context, agentResults);

            SwarmPhase completedPhase = determineCompletedPhase(agentResults);
            boolean success = completedPhase == SwarmPhase.OUTCOME_EVALUATION;
            String summary = buildSummary(agentResults, success);

            return buildSuccessResult(caseId, patient, context, agentResults,
                    completedPhase, success, summary);

        } catch (Exception e) {
            String summary = "Workflow execution failed: " + e.getMessage();
            return buildFailureResult(caseId, patient, config, agentResults, summary);
        }
    }

    // ── Private execution ──────────────────────────────────────────────────────

    /**
     * Runs the full OT agent sequence with optional adaptation loop.
     *
     * <p>Phases 1-5 are always executed once. Phase 6 (ProgressMonitoring) is
     * followed by either OutcomeEvaluation (if progress ≥ threshold) or an
     * adaptation cycle (PlanAdaptation → InterventionPlanning → SessionScheduling →
     * ProgressMonitoring), repeated up to {@code maxAdaptationCycles} times.</p>
     *
     * @param patient      the patient being treated
     * @param config       execution configuration
     * @param context      shared mutable context map
     * @param agentResults accumulating list of agent results
     */
    private void runAgentSequence(OTPatient patient, OTSwarmConfig config,
                                  Map<String, Object> context,
                                  List<SwarmTaskResult> agentResults) {
        // Phases 1-5: always linear
        runAgent(SwarmPhase.INTAKE,                 patient, context, agentResults);
        runAgent(SwarmPhase.ASSESSMENT,             patient, context, agentResults);
        runAgent(SwarmPhase.GOAL_SETTING,           patient, context, agentResults);
        runAgent(SwarmPhase.INTERVENTION_PLANNING,  patient, context, agentResults);
        runAgent(SwarmPhase.SCHEDULING,             patient, context, agentResults);

        // Phase 6+: progress monitoring with adaptation loop
        int adaptationCycles = 0;
        int maxCycles = config.maxAdaptationCycles();

        while (true) {
            runAgent(SwarmPhase.PROGRESS_MONITORING, patient, context, agentResults);

            // Read normalised progress score (0.0 – 1.0) written by ProgressMonitor
            Object progressObj = context.get("progress_score");
            double progressScore = progressObj instanceof Number n ? n.doubleValue() : 0.0;
            // Keep the YAWL workflow variable "progressScore" in sync for spec fidelity
            context.put("progressScore", progressScore * 100.0);

            if (progressScore >= ADVANCEMENT_THRESHOLD || adaptationCycles >= maxCycles) {
                break; // advance to outcome evaluation
            }

            // Adaptation cycle
            runAgent(SwarmPhase.ADAPTATION, patient, context, agentResults);
            adaptationCycles++;
            context.put("adaptationCycle", adaptationCycles);

            // Re-plan and re-schedule before re-monitoring
            runAgent(SwarmPhase.INTERVENTION_PLANNING, patient, context, agentResults);
            runAgent(SwarmPhase.SCHEDULING,            patient, context, agentResults);
        }

        // Phase 8: outcome evaluation
        runAgent(SwarmPhase.OUTCOME_EVALUATION, patient, context, agentResults);
    }

    /**
     * Runs a single OT agent phase and merges its output into the shared context.
     *
     * @param phase        the swarm phase to execute
     * @param patient      the patient being treated
     * @param context      shared context map
     * @param agentResults accumulating list of agent results
     */
    private void runAgent(SwarmPhase phase, OTPatient patient,
                          Map<String, Object> context,
                          List<SwarmTaskResult> agentResults) {
        OTSwarmAgent agent = registry.getAgent(phase);
        SwarmTaskResult result = agent.execute(patient, context);
        agentResults.add(result);
        if (result.data() != null) {
            context.putAll(result.data());
        }
    }

    // ── Workflow loading ────────────────────────────────────────────────────────

    /**
     * Loads the OT workflow YAML and converts it to YAWL XML for engine validation.
     *
     * @return YAWL XML specification string
     * @throws IOException      if the resource cannot be loaded
     * @throws YSyntaxException if YAML-to-XML conversion fails
     */
    private String loadAndConvertWorkflow() throws IOException, YSyntaxException {
        String yaml = loadWorkflowYaml();
        return yamlConverter.convertToXml(yaml);
    }

    /**
     * Loads the YAML from the classpath resource, falling back to the inline constant.
     *
     * @return YAML specification string
     * @throws IOException if resource loading fails and fallback is also unavailable
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

    // ── Result building ─────────────────────────────────────────────────────────

    /**
     * Determines the last phase that completed successfully.
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
     * Builds a result summary narrative.
     *
     * @param agentResults list of agent results
     * @param success      whether the swarm completed successfully
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
     * Builds a success result when the agent sequence completes normally.
     */
    private OTSwarmResult buildSuccessResult(String caseId, OTPatient patient,
                                             Map<String, Object> context,
                                             List<SwarmTaskResult> agentResults,
                                             SwarmPhase completedPhase,
                                             boolean success, String summary) {
        OccupationalProfile profile = extractProfile(context);
        List<LifestyleGoal> goals = extractGoals(context);
        List<TherapySession> sessions = extractSessions(context);
        return new OTSwarmResult(caseId, patient, profile, goals, sessions,
                agentResults, completedPhase, success, summary);
    }

    /**
     * Builds a failure result when an unexpected exception terminates execution.
     */
    private OTSwarmResult buildFailureResult(String caseId, OTPatient patient,
                                             OTSwarmConfig config,
                                             List<SwarmTaskResult> agentResults,
                                             String errorMessage) {
        SwarmPhase lastPhase = determineCompletedPhase(agentResults);
        String summary = "Execution failed: " + errorMessage;
        return new OTSwarmResult(caseId, patient, null, List.of(), List.of(),
                agentResults, lastPhase, false, summary);
    }

    // ── Context extraction ──────────────────────────────────────────────────────

    private OccupationalProfile extractProfile(Map<String, Object> context) {
        Object obj = context.get("profile");
        return obj instanceof OccupationalProfile profile ? profile : null;
    }

    @SuppressWarnings("unchecked")
    private List<LifestyleGoal> extractGoals(Map<String, Object> context) {
        Object obj = context.get("goals");
        if (obj instanceof List<?> list) {
            List<LifestyleGoal> typed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof LifestyleGoal goal) {
                    typed.add(goal);
                }
            }
            return List.copyOf(typed);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<TherapySession> extractSessions(Map<String, Object> context) {
        // OTSchedulerAgent writes "therapy_sessions"; OTProgressMonitorAgent also updates it
        Object obj = context.get("therapy_sessions");
        if (obj == null) {
            obj = context.get("sessions");
        }
        if (obj instanceof List<?> list) {
            List<TherapySession> typed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof TherapySession session) {
                    typed.add(session);
                }
            }
            return List.copyOf(typed);
        }
        return List.of();
    }
}
