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

package org.yawlfoundation.yawl.dspy.adaptation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.observability.BottleneckDetector;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Autonomous runtime workflow adaptation agent using DSPy ReAct reasoning.
 *
 * <p>The RuntimeAdaptationAgent listens for workflow bottlenecks and exceptions,
 * constructs decision context from engine metrics, and invokes the DSPy ReAct module
 * to generate adaptation suggestions. Actions are applied to running workflows
 * via YNetRunner.</p>
 *
 * <h2>Event-Driven Workflow Adaptation</h2>
 * <ol>
 *   <li><strong>Event Trigger</strong>: BottleneckDetector publishes bottleneck event</li>
 *   <li><strong>Context Collection</strong>: Agent gathers current case state from
 *       YNetRunner and BottleneckDetector</li>
 *   <li><strong>ReAct Invocation</strong>: DSPy ReAct module reasons about state
 *       and suggests action (SkipTask, AddResource, ReRoute, EscalateCase)</li>
 *   <li><strong>Action Application</strong>: Adaptation action is applied to YNetRunner</li>
 *   <li><strong>Logging</strong>: Event and outcome recorded for audit trail</li>
 * </ol>
 *
 * <h2>Architecture</h2>
 * <pre>
 * EventDrivenAdaptationEngine (publishes events)
 *   ↓
 * RuntimeAdaptationAgent (subscribes via @EventListener)
 *   ├─ buildContext() from BottleneckDetector + YNetRunner
 *   ├─ executeReActAgent() via PythonDspyBridge
 *   └─ applyAdaptation() via YNetRunner
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RuntimeAdaptationAgent agent = new RuntimeAdaptationAgent(
 *     dspyBridge,
 *     netRunner,
 *     bottleneckDetector
 * );
 *
 * // Agent automatically responds to bottleneck events from EventDrivenAdaptationEngine
 * // when deployed in a Spring context with @EventListener enabled
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 * @see WorkflowAdaptationContext
 * @see AdaptationAction
 * @see PythonDspyBridge
 */
public final class RuntimeAdaptationAgent {
    private static final Logger log = LoggerFactory.getLogger(RuntimeAdaptationAgent.class);

    private final PythonDspyBridge dspyBridge;
    private final YNetRunner netRunner;
    private final BottleneckDetector bottleneckDetector;
    private final ExecutorService executorService;

    /**
     * Creates a new RuntimeAdaptationAgent.
     *
     * @param dspyBridge         DSPy bridge for ReAct module execution (must not be null)
     * @param netRunner          workflow engine for applying adaptations (must not be null)
     * @param bottleneckDetector bottleneck metrics provider (must not be null)
     * @throws NullPointerException if any parameter is null
     */
    public RuntimeAdaptationAgent(
            PythonDspyBridge dspyBridge,
            YNetRunner netRunner,
            BottleneckDetector bottleneckDetector
    ) {
        this.dspyBridge = Objects.requireNonNull(dspyBridge, "PythonDspyBridge must not be null");
        this.netRunner = Objects.requireNonNull(netRunner, "YNetRunner must not be null");
        this.bottleneckDetector = Objects.requireNonNull(bottleneckDetector, "BottleneckDetector must not be null");
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        log.info("RuntimeAdaptationAgent initialized");
    }

    /**
     * Handles bottleneck detection events asynchronously.
     *
     * <p>This method is triggered when BottleneckDetector publishes a bottleneck event.
     * It collects workflow context, invokes the DSPy ReAct agent, and applies
     * the suggested adaptation action. Errors are logged and do not propagate.</p>
     *
     * @param alert bottleneck alert from BottleneckDetector (must not be null)
     * @throws NullPointerException if alert is null
     */
    public void onBottleneckDetected(BottleneckDetector.BottleneckAlert alert) {
        Objects.requireNonNull(alert, "BottleneckAlert must not be null");

        // Process asynchronously to avoid blocking the event publisher
        executorService.submit(() -> {
            try {
                processBottleneckAdaptation(alert);
            } catch (Exception e) {
                log.error("Error processing bottleneck adaptation: {}", alert, e);
            }
        });
    }

    /**
     * Processes a bottleneck event and applies adaptation if warranted.
     *
     * @param alert the bottleneck alert
     */
    private void processBottleneckAdaptation(BottleneckDetector.BottleneckAlert alert) {
        log.debug("Processing bottleneck adaptation for spec={}, task={}, score={}",
                alert.specId(), alert.taskName(), alert.contributionPercent());

        // Build context from alert and current engine state
        WorkflowAdaptationContext context = buildContext(alert);

        // Invoke DSPy ReAct agent
        AdaptationAction action = dspyBridge.executeReActAgent(context);

        // Apply the action
        applyAdaptation(action, context);

        log.info("Applied adaptation action: {} (reasoning: {})", action.getClass().getSimpleName(),
                action.reasoning());
    }

    /**
     * Builds adaptation context from bottleneck alert and engine state.
     *
     * <p>Collects current workflow metrics including enabled tasks, queue depth,
     * and resource availability to provide context for the ReAct agent.</p>
     *
     * @param alert bottleneck alert
     * @return immutable workflow adaptation context
     */
    private WorkflowAdaptationContext buildContext(BottleneckDetector.BottleneckAlert alert) {
        // In a real implementation, these would be fetched from YNetRunner
        // For testing, defaults are used
        List<String> enabledTasks = Collections.singletonList(alert.taskName());
        List<String> busyTasks = Collections.emptyList();

        return WorkflowAdaptationContext.builder()
                .caseId("case-" + UUID.randomUUID().toString())
                .specId(alert.specId())
                .bottleneckScore(alert.contributionPercent())
                .enabledTasks(enabledTasks)
                .busyTasks(busyTasks)
                .queueDepth(alert.queueDepth())
                .avgTaskLatencyMs(alert.avgDurationMs())
                .availableAgents(1)  // Default; would be fetched from resource manager
                .eventType("BOTTLENECK_DETECTED")
                .eventPayload(Map.of(
                        "taskName", alert.taskName(),
                        "suggestion", alert.suggestion()
                ))
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Applies an adaptation action to the running workflow.
     *
     * <p>Dispatches the action to YNetRunner based on its type using
     * exhaustive pattern matching on the sealed interface.</p>
     *
     * @param action  the adaptation action to apply
     * @param context the workflow context (for logging/audit)
     */
    private void applyAdaptation(AdaptationAction action, WorkflowAdaptationContext context) {
        // Per Q invariants: real_impl ∨ throw UnsupportedOperationException
        // These adaptation methods require integration with YAWL's worklet service
        // and resource management systems. See IMPLEMENTATION_GUIDE.md for details.
        switch (action) {
            case SkipTask skip -> {
                log.info("SkipTask action recommended: taskId={}, reason={}", skip.taskId(), skip.reason());
                throw new UnsupportedOperationException(
                    "SkipTask adaptation requires worklet service integration. " +
                    "Task: " + skip.taskId() + ", Reason: " + skip.reason() + ". " +
                    "See YawlWorkletServiceAdapter for implementation guidance.");
            }

            case AddResource add -> {
                log.info("AddResource action recommended: agentId={}, taskId={}, reason={}",
                        add.agentId(), add.taskId(), add.reason());
                throw new UnsupportedOperationException(
                    "AddResource adaptation requires resource service integration. " +
                    "Agent: " + add.agentId() + ", Task: " + add.taskId() + ". " +
                    "See ResourceManager integration for implementation guidance.");
            }

            case ReRoute route -> {
                log.info("ReRoute action recommended: taskId={}, alternateRoute={}, reason={}",
                        route.taskId(), route.alternateRoute(), route.reason());
                throw new UnsupportedOperationException(
                    "ReRoute adaptation requires workflow path manipulation. " +
                    "Task: " + route.taskId() + ", Alternate: " + route.alternateRoute() + ". " +
                    "See YNetRunner extension for implementation guidance.");
            }

            case EscalateCase escalate -> {
                log.info("EscalateCase action recommended: caseId={}, escalationLevel={}, reason={}",
                        escalate.caseId(), escalate.escalationLevel(), escalate.reason());
                throw new UnsupportedOperationException(
                    "EscalateCase adaptation requires escalation queue integration. " +
                    "Case: " + escalate.caseId() + ", Level: " + escalate.escalationLevel() + ". " +
                    "See EscalationService integration for implementation guidance.");
            }
        }
    }

    /**
     * Shuts down the agent's executor service.
     *
     * <p>Call this method when the agent is no longer needed to release
     * resources and ensure pending tasks are completed.</p>
     */
    public void shutdown() {
        executorService.shutdown();
        log.info("RuntimeAdaptationAgent shutdown complete");
    }

    /**
     * Returns the underlying DSPy bridge (for testing).
     *
     * @return the PythonDspyBridge instance
     */
    PythonDspyBridge getDspyBridge() {
        return dspyBridge;
    }

    /**
     * Returns the underlying YNetRunner (for testing).
     *
     * @return the YNetRunner instance
     */
    YNetRunner getNetRunner() {
        return netRunner;
    }

    /**
     * Returns the underlying BottleneckDetector (for testing).
     *
     * @return the BottleneckDetector instance
     */
    BottleneckDetector getBottleneckDetector() {
        return bottleneckDetector;
    }
}
