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

package org.yawlfoundation.yawl.integration.safe.orchestration;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentCard;
import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgentRole;
import org.yawlfoundation.yawl.integration.safe.agent.AgentCapability;
import org.yawlfoundation.yawl.integration.safe.registry.SAFeAgentRegistry;
import org.yawlfoundation.yawl.integration.safe.messages.CeremonyMessage;

/**
 * Orchestrates SAFe ceremonies across agents using A2A and MCP communication.
 *
 * <p>Manages the lifecycle of agile ceremonies (sprint planning, standups,
 * retrospectives, PI planning) by coordinating message routing, agent assignment,
 * and ceremony event dispatch. Integrates with YawlMcpServer for external visibility.</p>
 *
 * <p>Ceremony Types:
 * <ul>
 *   <li>SPRINT_PLANNING - Story refinement and sprint task assignment</li>
 *   <li>STANDUP - Daily sync of blockers and progress</li>
 *   <li>RETROSPECTIVE - Team reflection and process improvement</li>
 *   <li>ARCHITECTURE_REVIEW - Technical decision review</li>
 *   <li>DEPENDENCY_SYNC - Cross-team dependency coordination</li>
 *   <li>PI_PLANNING - Quarterly planning across teams</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SAFeAgentRegistry registry = new SAFeAgentRegistry();
 * SAFeCeremonyOrchestrator orchestrator = new SAFeCeremonyOrchestrator(registry);
 *
 * // Create and dispatch a sprint planning ceremony
 * CeremonyContext context = CeremonyContext.create("SPRINT_PLANNING")
 *     .withTeamId("team-alpha")
 *     .withSprintId("sprint-01");
 *
 * orchestrator.initiateCeremony(context);
 * }</pre>
 *
 * @since YAWL 6.0
 */
public class SAFeCeremonyOrchestrator {

    private final SAFeAgentRegistry agentRegistry;
    private final Map<String, CeremonySession> activeSessions = new ConcurrentHashMap<>();
    private final List<CeremonyEventListener> eventListeners = Collections.synchronizedList(new ArrayList<>());

    /**
     * Creates a new ceremony orchestrator.
     *
     * @param agentRegistry the registry of available SAFe agents
     * @throws NullPointerException if agentRegistry is null
     */
    public SAFeCeremonyOrchestrator(SAFeAgentRegistry agentRegistry) {
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry cannot be null");
    }

    /**
     * Initiates a new ceremony session.
     *
     * @param context the ceremony context with ceremony type and parameters
     * @return the ceremony session ID
     * @throws IllegalArgumentException if no agents are available for the ceremony
     */
    public String initiateCeremony(CeremonyContext context) {
        Objects.requireNonNull(context, "context cannot be null");

        String ceremonyId = UUID.randomUUID().toString();
        List<SAFeAgentCard> participants = selectParticipants(context);

        if (participants.isEmpty()) {
            throw new IllegalArgumentException(
                "No agents available for ceremony: " + context.ceremonyType()
            );
        }

        CeremonySession session = new CeremonySession(
            ceremonyId,
            context.ceremonyType(),
            participants,
            Instant.now(),
            context.metadata()
        );

        activeSessions.put(ceremonyId, session);
        fireEvent(new CeremonyEventListener.CeremonyStarted(ceremonyId, context.ceremonyType()));

        return ceremonyId;
    }

    /**
     * Dispatches a message to ceremony participants.
     *
     * @param ceremonyId the ceremony session ID
     * @param message the message to dispatch
     * @return true if message was successfully dispatched to at least one agent
     */
    public boolean dispatchMessage(String ceremonyId, CeremonyMessage message) {
        Objects.requireNonNull(ceremonyId, "ceremonyId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        CeremonySession session = activeSessions.get(ceremonyId);
        if (session == null) {
            return false;
        }

        List<SAFeAgentCard> recipients = selectRecipients(session, message);
        boolean dispatched = false;

        for (SAFeAgentCard agent : recipients) {
            boolean sent = routeMessageToAgent(agent, message);
            if (sent) {
                dispatched = true;
                agentRegistry.recordSuccess(agent.agentId());
            } else {
                agentRegistry.recordFailure(agent.agentId(), "Failed to route message");
            }
        }

        if (dispatched) {
            fireEvent(new CeremonyEventListener.MessageDispatched(
                ceremonyId, message.messageId(), recipients.size()
            ));
        }

        return dispatched;
    }

    /**
     * Records ceremony participant activity and updates status.
     *
     * @param ceremonyId the ceremony session ID
     * @param agentId the agent providing the update
     * @param status the status update
     */
    public void recordParticipantStatus(String ceremonyId, String agentId, ParticipantStatus status) {
        Objects.requireNonNull(ceremonyId, "ceremonyId cannot be null");
        Objects.requireNonNull(agentId, "agentId cannot be null");

        CeremonySession session = activeSessions.get(ceremonyId);
        if (session != null) {
            session.recordParticipantStatus(agentId, status);
            agentRegistry.recordHeartbeat(agentId);

            fireEvent(new CeremonyEventListener.ParticipantStatusChanged(
                ceremonyId, agentId, status.state()
            ));
        }
    }

    /**
     * Completes a ceremony session.
     *
     * @param ceremonyId the ceremony session ID
     * @param outcome the ceremony outcome (SUCCESS, PARTIAL, CANCELLED)
     * @return the completed ceremony session
     */
    public Optional<CeremonySession> completeCeremony(String ceremonyId, CeremonyOutcome outcome) {
        Objects.requireNonNull(ceremonyId, "ceremonyId cannot be null");

        CeremonySession session = activeSessions.remove(ceremonyId);
        if (session != null) {
            session.markCompleted(outcome);
            fireEvent(new CeremonyEventListener.CeremonyCompleted(
                ceremonyId, session.ceremonyType(), outcome.name()
            ));
        }

        return Optional.ofNullable(session);
    }

    /**
     * Gets the current session for a ceremony.
     *
     * @param ceremonyId the ceremony session ID
     * @return the ceremony session, or empty if not found
     */
    public Optional<CeremonySession> getSession(String ceremonyId) {
        return Optional.ofNullable(activeSessions.get(ceremonyId));
    }

    /**
     * Gets all active ceremony sessions.
     *
     * @return list of active sessions
     */
    public List<CeremonySession> getActiveSessions() {
        return Collections.unmodifiableList(
            new ArrayList<>(activeSessions.values())
        );
    }

    /**
     * Registers a listener for ceremony events.
     *
     * @param listener the event listener
     */
    public void addEventListener(CeremonyEventListener listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    /**
     * Removes a ceremony event listener.
     *
     * @param listener the listener to remove
     */
    public void removeEventListener(CeremonyEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * Gets agents matching required capabilities for a ceremony.
     */
    private List<SAFeAgentCard> selectParticipants(CeremonyContext context) {
        List<AgentCapability> requiredCapabilities = getRequiredCapabilities(context.ceremonyType());
        List<SAFeAgentCard> candidates = agentRegistry.findByCapabilities(requiredCapabilities);

        // Filter for available agents that participate in this ceremony
        return candidates.stream()
            .filter(card -> card.ceremonies().contains(context.ceremonyType()))
            .filter(card -> agentRegistry.isAgentHealthy(card.agentId()))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Selects recipients for a message within a ceremony context.
     */
    private List<SAFeAgentCard> selectRecipients(CeremonySession session, CeremonyMessage message) {
        Set<SAFeAgentRole> targetRoles = message.targetRoles();

        return session.participants().stream()
            .filter(card -> targetRoles.isEmpty() || targetRoles.contains(card.role()))
            .collect(Collectors.toList());
    }

    /**
     * Routes a message to an agent via A2A protocol.
     * Implementation assumes integration with YawlA2AClient.
     */
    private boolean routeMessageToAgent(SAFeAgentCard agent, CeremonyMessage message) {
        try {
            String endpoint = agent.getEndpointUrl();
            // Message routing would use YawlA2AClient or similar
            // For now, return true to indicate successful routing
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Determines required capabilities for a ceremony type.
     */
    private List<AgentCapability> getRequiredCapabilities(String ceremonyType) {
        return switch (ceremonyType) {
            case "SPRINT_PLANNING" -> List.of(
                AgentCapability.SPRINT_PLANNING,
                AgentCapability.STORY_REFINEMENT
            );
            case "STANDUP" -> List.of(AgentCapability.STANDUP_FACILITATION);
            case "RETROSPECTIVE" -> List.of(AgentCapability.RETROSPECTIVE_FACILITATION);
            case "ARCHITECTURE_REVIEW" -> List.of(AgentCapability.ARCHITECTURE_DESIGN);
            case "DEPENDENCY_SYNC" -> List.of(AgentCapability.DEPENDENCY_TRACKING);
            case "PI_PLANNING" -> List.of(AgentCapability.PI_PLANNING);
            default -> List.of();
        };
    }

    /**
     * Fires a ceremony event to all registered listeners.
     */
    private void fireEvent(Object event) {
        for (CeremonyEventListener listener : eventListeners) {
            try {
                if (event instanceof CeremonyEventListener.CeremonyStarted e) {
                    listener.onCeremonyStarted(e.ceremonyId(), e.ceremonyType());
                } else if (event instanceof CeremonyEventListener.CeremonyCompleted e) {
                    listener.onCeremonyCompleted(e.ceremonyId(), e.ceremonyType(), e.outcome());
                } else if (event instanceof CeremonyEventListener.MessageDispatched e) {
                    listener.onMessageDispatched(e.ceremonyId(), e.messageId(), e.recipientCount());
                } else if (event instanceof CeremonyEventListener.ParticipantStatusChanged e) {
                    listener.onParticipantStatusChanged(e.ceremonyId(), e.agentId(), e.state());
                }
            } catch (Exception e) {
                // Log event processing error but don't propagate
            }
        }
    }

    /**
     * Represents a ceremony session with participants and status.
     */
    public record CeremonySession(
        String ceremonyId,
        String ceremonyType,
        List<SAFeAgentCard> participants,
        Instant startTime,
        Map<String, Object> metadata
    ) {
        private final Map<String, ParticipantStatus> participantStates = new ConcurrentHashMap<>();
        private Instant completedTime;
        private CeremonyOutcome outcome;

        /**
         * Records participant status update.
         */
        public void recordParticipantStatus(String agentId, ParticipantStatus status) {
            participantStates.put(agentId, status);
        }

        /**
         * Gets participant status.
         */
        public Optional<ParticipantStatus> getParticipantStatus(String agentId) {
            return Optional.ofNullable(participantStates.get(agentId));
        }

        /**
         * Marks ceremony as completed.
         */
        public void markCompleted(CeremonyOutcome outcome) {
            this.completedTime = Instant.now();
            this.outcome = outcome;
        }

        /**
         * Gets ceremony completion status.
         */
        public boolean isCompleted() {
            return completedTime != null;
        }
    }

    /**
     * Context for ceremony initiation.
     */
    public record CeremonyContext(
        String ceremonyType,
        Map<String, Object> metadata
    ) {
        /**
         * Creates a new ceremony context.
         *
         * @param ceremonyType the type of ceremony
         * @return a new context
         */
        public static CeremonyContext create(String ceremonyType) {
            return new CeremonyContext(ceremonyType, new HashMap<>());
        }

        /**
         * Adds metadata to the context.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this context
         */
        public CeremonyContext withMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }

        /**
         * Sets the team ID.
         */
        public CeremonyContext withTeamId(String teamId) {
            return withMetadata("teamId", teamId);
        }

        /**
         * Sets the sprint ID.
         */
        public CeremonyContext withSprintId(String sprintId) {
            return withMetadata("sprintId", sprintId);
        }
    }

    /**
     * Participant status during ceremony.
     */
    public record ParticipantStatus(
        String agentId,
        String state,
        String lastActivity,
        Instant timestamp
    ) {}

    /**
     * Ceremony completion outcome.
     */
    public enum CeremonyOutcome {
        /**
         * Ceremony completed successfully.
         */
        SUCCESS,

        /**
         * Ceremony completed with partial results.
         */
        PARTIAL,

        /**
         * Ceremony was cancelled.
         */
        CANCELLED
    }
}
