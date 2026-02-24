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

package org.yawlfoundation.yawl.integration.concierge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.observability.AutoRemediationLog;
import org.yawlfoundation.yawl.observability.SLAMonitor;
import org.yawlfoundation.yawl.engine.observability.AndonAlert;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.autonomous.AgentLifecycle;
import org.yawlfoundation.yawl.integration.autonomous.PartitionConfig;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Autonomous case concierge agent implementing YCaseEventListener.
 *
 * <p>Drives the AgentLifecycle state machine on every CASE_IDLE_TIMEOUT event,
 * making real-time health decisions for cases trending toward SLA breach.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 * DISCOVERING --[CASE_IDLE_TIMEOUT event]--> REASONING
 *     ↓                                              ↓
 *     └──────────────────────────────────────-> ACTING
 *                                                  ↓
 *                                             REPORTING
 *                                                  ↓
 *                                          DISCOVERING (loop)
 * </pre>
 *
 * @since YAWL 6.0
 */
public final class CaseConciergeAgent implements YCaseEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaseConciergeAgent.class);

    private final SLAMonitor slaMonitor;
    private final HandoffProtocol handoffProtocol;
    private final AutoRemediationLog remediationLog;
    private final AndonAlert andonAlert;
    private final PartitionConfig partition;
    private final long defaultSlaThresholdMs;

    private final ConcurrentHashMap<String, AtomicReference<AgentLifecycle>> lifecycleMap;
    private final ReentrantLock lifecycleLock;
    private final ConcurrentHashMap<String, Long> slaThresholdsMs;

    /**
     * Constructs a CaseConciergeAgent with all dependencies.
     *
     * @param slaMonitor the SLA monitoring service
     * @param handoffProtocol the handoff protocol for agent-to-agent transfers
     * @param remediationLog the auto-remediation logging service
     * @param andonAlert the Andon alert system
     * @param partition the partition configuration for distributed processing
     * @param defaultSlaThresholdMs the default SLA threshold in milliseconds
     */
    public CaseConciergeAgent(
            SLAMonitor slaMonitor,
            HandoffProtocol handoffProtocol,
            AutoRemediationLog remediationLog,
            AndonAlert andonAlert,
            PartitionConfig partition,
            long defaultSlaThresholdMs
    ) {
        this.slaMonitor = Objects.requireNonNull(slaMonitor, "slaMonitor cannot be null");
        this.handoffProtocol = Objects.requireNonNull(handoffProtocol, "handoffProtocol cannot be null");
        this.remediationLog = Objects.requireNonNull(remediationLog, "remediationLog cannot be null");
        this.andonAlert = Objects.requireNonNull(andonAlert, "andonAlert cannot be null");
        this.partition = Objects.requireNonNull(partition, "partition cannot be null");

        if (defaultSlaThresholdMs <= 0) {
            throw new IllegalArgumentException("defaultSlaThresholdMs must be positive");
        }
        this.defaultSlaThresholdMs = defaultSlaThresholdMs;

        this.lifecycleMap = new ConcurrentHashMap<>();
        this.lifecycleLock = new ReentrantLock();
        this.slaThresholdsMs = new ConcurrentHashMap<>();
    }

    /**
     * Sealed interface for health decisions with permitted record subtypes.
     */
    public sealed interface HealthDecision {
        record Healthy(String caseId) implements HealthDecision {}
        record Handoff(String caseId, String targetAgentId, String reason) implements HealthDecision {}
        record Remediate(String caseId, String remediationAction) implements HealthDecision {}
        record Escalate(String caseId, int escalationLevel) implements HealthDecision {}
        record CriticalAlert(String caseId, String alertMessage) implements HealthDecision {}
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        String caseId = event.getCaseID().toString();

        // Partition check: only process if this case belongs to this agent
        if (!partition.shouldProcess(caseId)) {
            return;
        }

        switch (event.getEventType()) {
            case CASE_STARTED -> onCaseStarted(caseId, event);
            case CASE_IDLE_TIMEOUT -> onIdleTimeout(caseId, event);
            case CASE_COMPLETED, CASE_CANCELLED -> onCaseTerminated(caseId);
            default -> {
                // Ignore other event types
            }
        }
    }

    /**
     * Handles case started event: initializes lifecycle and SLA tracking.
     */
    private void onCaseStarted(String caseId, YCaseEvent event) {
        LOGGER.debug("Case started: {}", caseId);

        // Initialize lifecycle in DISCOVERING state
        lifecycleMap.put(caseId, new AtomicReference<>(AgentLifecycle.DISCOVERING));

        // Define SLA for this case
        slaMonitor.defineSLA(caseId, defaultSlaThresholdMs, "Case SLA for " + caseId);
        slaThresholdsMs.put(caseId, defaultSlaThresholdMs);

        // Start tracking
        String specUri = event.getSpecID() != null ? event.getSpecID().getSpecUri() : "unknown";
        slaMonitor.startTracking(caseId, caseId, Map.of("specUri", specUri));

        LOGGER.info("Case lifecycle initialized: caseId={}, slaThresholdMs={}", caseId, defaultSlaThresholdMs);
    }

    /**
     * Handles case idle timeout event: drives AgentLifecycle through REASONING -> ACTING -> REPORTING.
     */
    private void onIdleTimeout(String caseId, YCaseEvent event) {
        LOGGER.debug("Case idle timeout: {}", caseId);

        try {
            // Transition to REASONING
            transition(caseId, AgentLifecycle.REASONING);

            // Diagnose health
            HealthDecision decision = diagnose(caseId);

            // Transition to ACTING
            transition(caseId, AgentLifecycle.ACTING);

            // Act on decision
            act(decision);

            // Transition to REPORTING
            transition(caseId, AgentLifecycle.REPORTING);

            // Report (log at INFO level)
            report(decision);

            // Transition back to DISCOVERING for next cycle
            transition(caseId, AgentLifecycle.DISCOVERING);

        } catch (Exception e) {
            LOGGER.error("Error handling idle timeout for case {}: {}", caseId, e.getMessage(), e);
        }
    }

    /**
     * Diagnoses case health based on SLA status and remediation history.
     */
    private HealthDecision diagnose(String caseId) {
        boolean critical = slaMonitor.isBreached(caseId);
        boolean atRisk = slaMonitor.isPredictedBreach(caseId);

        if (critical) {
            return new HealthDecision.CriticalAlert(caseId, "SLA breached for " + caseId);
        } else if (atRisk && remediationLog.hasKnownPattern(caseId)) {
            return new HealthDecision.Remediate(caseId, remediationLog.getSuggestedAction(caseId));
        } else if (atRisk) {
            return new HealthDecision.Handoff(caseId, selectTargetAgent(), "SLA breach predicted");
        } else {
            return new HealthDecision.Healthy(caseId);
        }
    }

    /**
     * Acts on the health decision.
     */
    private void act(HealthDecision decision) {
        switch (decision) {
            case HealthDecision.Handoff h -> {
                try {
                    LOGGER.info("Initiating handoff for case {} to agent {}", h.caseId(), h.targetAgentId());
                    handoffProtocol.generateHandoffToken(
                            h.caseId(),
                            "concierge",
                            h.targetAgentId(),
                            "session-" + h.caseId()
                    );
                } catch (HandoffException e) {
                    LOGGER.warn("Handoff generation failed for case {}: {}", h.caseId(), e.getMessage());
                }
            }
            case HealthDecision.Remediate r -> {
                LOGGER.info("Applying remediation for case {}: {}", r.caseId(), r.remediationAction());
                remediationLog.logTimeoutRecovery(r.caseId(), 0L, r.remediationAction(), true);
            }
            case HealthDecision.CriticalAlert a -> {
                LOGGER.error("CRITICAL: SLA breach detected for case {}: {}", a.caseId(), a.alertMessage());
                andonAlert.fire();
            }
            case HealthDecision.Escalate e -> {
                LOGGER.warn("Escalating case {} at level {}", e.caseId(), e.escalationLevel());
                andonAlert.fire();
            }
            case HealthDecision.Healthy _ -> {
                // No action needed
            }
        }
    }

    /**
     * Reports the decision (logs at INFO level).
     */
    private void report(HealthDecision decision) {
        String reportMessage = switch (decision) {
            case HealthDecision.Healthy h -> "Case " + h.caseId() + " is healthy";
            case HealthDecision.Handoff h -> "Case " + h.caseId() + " handed off to " + h.targetAgentId();
            case HealthDecision.Remediate r -> "Case " + r.caseId() + " remediated with " + r.remediationAction();
            case HealthDecision.Escalate e -> "Case " + e.caseId() + " escalated to level " + e.escalationLevel();
            case HealthDecision.CriticalAlert a -> "CRITICAL ALERT: " + a.alertMessage();
        };
        LOGGER.info("Health report: {}", reportMessage);
    }

    /**
     * Transitions the case lifecycle to the target state if valid.
     */
    private void transition(String caseId, AgentLifecycle target) {
        lifecycleLock.lock();
        try {
            AtomicReference<AgentLifecycle> lifecycleRef = lifecycleMap.get(caseId);
            if (lifecycleRef == null) {
                LOGGER.warn("No lifecycle found for case {}", caseId);
                return;
            }

            AgentLifecycle current = lifecycleRef.get();
            if (current.canTransitionTo(target)) {
                lifecycleRef.set(target);
                LOGGER.debug("Lifecycle transition for case {}: {} -> {}", caseId, current, target);
            } else {
                LOGGER.warn("Invalid transition for case {}: {} -> {}", caseId, current, target);
            }
        } finally {
            lifecycleLock.unlock();
        }
    }

    /**
     * Terminates case tracking.
     */
    private void onCaseTerminated(String caseId) {
        LOGGER.debug("Case terminated: {}", caseId);

        lifecycleMap.remove(caseId);
        slaThresholdsMs.remove(caseId);
        slaMonitor.completeTracking(caseId, caseId);

        LOGGER.info("Case lifecycle terminated: {}", caseId);
    }

    /**
     * Gets the current lifecycle state for a case, or null if not tracked.
     */
    public AgentLifecycle getLifecycle(String caseId) {
        AtomicReference<AgentLifecycle> ref = lifecycleMap.get(caseId);
        return ref != null ? ref.get() : null;
    }

    /**
     * Gets the number of managed cases.
     */
    public int getManagedCaseCount() {
        return lifecycleMap.size();
    }

    /**
     * Selects a target agent for handoff (simple default implementation).
     */
    private String selectTargetAgent() {
        return "concierge-peer-agent";
    }
}
