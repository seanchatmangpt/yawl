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

package org.yawlfoundation.yawl.integration.safe.agent;

import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.stateless.listener.event.YEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all SAFe autonomous agents.
 *
 * <p>Extends {@link GenericPartyAgent} with SAFe-specific capabilities:
 * <ul>
 *   <li>Role assignment (Product Owner, Scrum Master, etc.)</li>
 *   <li>Ceremony participation and facilitation</li>
 *   <li>Decision point handling (architecture review, priority arbitration, etc.)</li>
 *   <li>Escalation protocols for blockers and conflicts</li>
 *   <li>Event emission for all state transitions</li>
 * </ul>
 *
 * <p>All SAFe agents run as autonomous agents with virtual thread-based discovery loops,
 * participate in asynchronous ceremonies via A2A messaging, and maintain internal state
 * machines for their respective domains.
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public sealed abstract class SAFeAgent {

    protected final GenericPartyAgent baseAgent;
    protected final SAFeAgentRole role;
    protected final SAFeAgentLifecycle lifecycle;

    /**
     * Initialize a SAFe agent with base configuration.
     *
     * @param baseConfig agent configuration (engine URL, credentials, capabilities)
     * @param role the SAFe role this agent assumes
     * @throws IOException if engine connection fails
     */
    protected SAFeAgent(AgentConfiguration baseConfig, SAFeAgentRole role) throws IOException {
        if (baseConfig == null) {
            throw new IllegalArgumentException("AgentConfiguration required");
        }
        if (role == null) {
            throw new IllegalArgumentException("SAFeAgentRole required");
        }

        this.baseAgent = new GenericPartyAgent(baseConfig);
        this.role = role;
        this.lifecycle = new SAFeAgentLifecycle(role);
    }

    /**
     * Start the agent: initialize HTTP server, discovery loop, event listeners.
     *
     * @throws IOException if HTTP server or YAWL connection fails
     */
    public final void start() throws IOException {
        lifecycle.notifyStarting();
        baseAgent.start();
        registerEventListeners();
        onAgentStarted();
        lifecycle.notifyStarted();
    }

    /**
     * Stop the agent gracefully: shut down discovery, disconnect from engine.
     */
    public final void stop() {
        lifecycle.notifyStopping();
        unregisterEventListeners();
        onAgentStopping();
        baseAgent.stop();
        lifecycle.notifyStopped();
    }

    /**
     * Get the SAFe role this agent assumes.
     *
     * @return the SAFeAgentRole enum value
     */
    public final SAFeAgentRole getRole() {
        return role;
    }

    /**
     * Get the current lifecycle state.
     *
     * @return the SAFeAgentLifecycle state
     */
    public final SAFeAgentLifecycle getLifecycle() {
        return lifecycle;
    }

    // =========================================================================
    // Ceremony Participation
    // =========================================================================

    /**
     * Participate in a SAFe ceremony (PI Planning, Sprint Planning, Standup, etc.)
     *
     * <p>Subclasses override to provide role-specific behavior.
     * Typically involves:
     * - ACK the ceremony request
     * - Make decisions specific to this agent's role
     * - Emit decision events
     * - Contribute to ceremony outputs
     *
     * @param ceremonyRequest the ceremony request (contains type, context, expected attendees)
     * @return async response indicating participation result
     */
    public abstract CompletableFuture<CeremonyParticipationResult> participateInCeremony(
            SAFeCeremonyRequest ceremonyRequest);

    /**
     * Handle a work item for processing.
     *
     * <p>Subclasses override to implement role-specific work item strategies.
     * Invoked by base agent's discovery loop when eligible work items are found.
     *
     * @param workItem the work item to process
     * @return async result of processing attempt
     */
    public abstract CompletableFuture<WorkItemProcessingResult> handleWorkItem(
            WorkItemRecord workItem);

    // =========================================================================
    // Decision Points (Override in Subclasses)
    // =========================================================================

    /**
     * Make a decision at a critical workflow point.
     *
     * <p>Used when ceremony or workflow requires this agent to make a decision
     * (e.g., ProductOwner accepting a story, Architect approving design, etc.)
     *
     * @param decisionContext context providing all information needed
     * @return the decision (APPROVED, APPROVED_WITH_CONDITIONS, REJECTED, etc.)
     */
    public abstract CompletableFuture<Decision> makeDecision(DecisionContext decisionContext);

    /**
     * Assess if a work item is eligible for this agent to process.
     *
     * @param workItem the work item to evaluate
     * @return true if this agent can process the work item
     */
    public abstract boolean isEligible(WorkItemRecord workItem);

    // =========================================================================
    // Event Emission
    // =========================================================================

    /**
     * Emit an event to the SAFe event bus.
     *
     * <p>All SAFe agents emit events for:
     * - Ceremony participation (started, completed, decision made)
     * - Work item status changes (started, blocked, completed)
     * - Escalations (blocker identified, escalated to RTE/Architect)
     *
     * @param event the event to publish
     */
    protected final void emitEvent(SAFeEvent event) {
        SAFeEventBus.getInstance().publish(event);
    }

    // =========================================================================
    // Handoff Protocol (When Agent Cannot Process Work Item)
    // =========================================================================

    /**
     * Request handoff of a work item to another capable agent.
     *
     * <p>Called when this agent determines it cannot process a work item
     * (skill mismatch, capacity, blocker, etc.) and needs to find an alternate agent.
     *
     * @param workItemId the work item to hand off
     * @param reason why this agent cannot continue
     * @return async result of handoff attempt
     */
    public final CompletableFuture<HandoffResult> requestHandoff(
            String workItemId,
            HandoffReason reason) {
        // Delegate to base agent's handoff mechanism
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException(
                "Handoff not yet implemented; override in subclass if needed"
            )
        );
    }

    // =========================================================================
    // Hook Methods (Override in Subclasses for Custom Behavior)
    // =========================================================================

    /**
     * Called when agent has started and is about to enter discovery loop.
     * Override to initialize role-specific state.
     */
    protected void onAgentStarted() {
        // Default: no-op. Subclasses override as needed.
    }

    /**
     * Called when agent is stopping.
     * Override to clean up role-specific state.
     */
    protected void onAgentStopping() {
        // Default: no-op. Subclasses override as needed.
    }

    /**
     * Register event listeners specific to this agent's role.
     */
    protected abstract void registerEventListeners();

    /**
     * Unregister event listeners.
     */
    protected abstract void unregisterEventListeners();

    // =========================================================================
    // Type Checking for Sealed Hierarchy
    // =========================================================================

    /**
     * All permitted subclasses of SAFeAgent.
     * Enforced at compile time by sealed keyword.
     */
    public static final class PermittedSubclasses {
        // Defined in actual implementation files
    }

    // =========================================================================
    // Inner Classes: Supporting Data Structures
    // =========================================================================

    /**
     * Request to participate in a ceremony.
     */
    public record SAFeCeremonyRequest(
            String ceremonyId,
            String ceremonyType,  // PI_PLANNING, SPRINT_PLANNING, STANDUP, etc.
            String organizerId,
            java.time.Instant scheduledTime,
            int durationMinutes,
            Map<String, String> context,
            java.time.Instant deadline
    ) {}

    /**
     * Result of ceremony participation.
     */
    public record CeremonyParticipationResult(
            String ceremonyId,
            String status,  // ACCEPTED, CONDITIONAL, DECLINED
            String agentId,
            Map<String, Object> contribution,  // Role-specific decision/input
            java.time.Instant respondedAt,
            String notes
    ) {}

    /**
     * Result of work item processing.
     */
    public record WorkItemProcessingResult(
            String workItemId,
            String status,  // COMPLETED, BLOCKED, FAILED, ESCALATED
            String outputData,  // For work item completion
            String blockerDescription,  // If blocked
            java.time.Instant processedAt
    ) {}

    /**
     * Decision context for making role-specific decisions.
     */
    public record DecisionContext(
            String decisionPointId,
            String decisionType,  // ACCEPT_STORY, APPROVE_DESIGN, ESCALATE, etc.
            Map<String, Object> criteria,
            Object subject,  // Story, design, blocker, etc.
            Map<String, String> metadata
    ) {}

    /**
     * Decision result.
     */
    public record Decision(
            String decisionId,
            String verdict,  // APPROVED, APPROVED_WITH_CONDITIONS, REJECTED, ESCALATED
            String rationale,
            Map<String, Object> conditions,  // If conditional approval
            String escalationTarget,  // If escalated (RTE, CTO, etc.)
            java.time.Instant decidedAt
    ) {}

    /**
     * Base class for all SAFe events.
     */
    public sealed static class SAFeEvent permits CeremonyEvent, WorkItemEvent, DependencyEvent, RiskEvent {
        public final String eventId;
        public final String eventType;
        public final java.time.Instant timestamp;
        public final String sourceAgentId;
        public final Map<String, String> context;

        protected SAFeEvent(String type, String sourceAgent, Map<String, String> context) {
            this.eventType = type;
            this.sourceAgentId = sourceAgent;
            this.context = context;
            this.timestamp = java.time.Instant.now();
            this.eventId = java.util.UUID.randomUUID().toString();
        }
    }

    /**
     * Ceremony-related events.
     */
    public static final class CeremonyEvent extends SAFeEvent {
        public final String ceremonyId;

        public CeremonyEvent(String type, String sourceAgent, String ceremonyId,
                             Map<String, String> context) {
            super(type, sourceAgent, context);
            this.ceremonyId = ceremonyId;
        }
    }

    /**
     * Work item status change events.
     */
    public static final class WorkItemEvent extends SAFeEvent {
        public final String workItemId;

        public WorkItemEvent(String type, String sourceAgent, String workItemId,
                             Map<String, String> context) {
            super(type, sourceAgent, context);
            this.workItemId = workItemId;
        }
    }

    /**
     * Dependency-related events.
     */
    public static final class DependencyEvent extends SAFeEvent {
        public final String sourceSoryId;
        public final String targetStoryId;

        public DependencyEvent(String type, String sourceAgent,
                               String sourceStoryId, String targetStoryId,
                               Map<String, String> context) {
            super(type, sourceAgent, context);
            this.sourceSoryId = sourceStoryId;
            this.targetStoryId = targetStoryId;
        }
    }

    /**
     * Risk-related events.
     */
    public static final class RiskEvent extends SAFeEvent {
        public final String riskId;

        public RiskEvent(String type, String sourceAgent, String riskId,
                         Map<String, String> context) {
            super(type, sourceAgent, context);
            this.riskId = riskId;
        }
    }

    /**
     * Handoff-related data structures.
     */
    public enum HandoffReason {
        SKILL_MISMATCH,
        CAPACITY_OVERFLOW,
        DEPENDENCY_BLOCKING,
        BLOCKER_UNRESOLVABLE,
        ESCALATION_REQUIRED
    }

    /**
     * Result of handoff attempt.
     */
    public record HandoffResult(
            String workItemId,
            String sourceAgentId,
            String targetAgentId,
            String status,  // ACCEPTED, DECLINED, FAILED
            String message,
            java.time.Instant handoffTime
    ) {}

    /**
     * Reason for declining a ceremony.
     */
    public record DeclinationReason(
            String ceremonyId,
            String reason,  // CAPACITY, CONFLICT, UNRELATED_ROLE, etc.
            String details
    ) {}
}
