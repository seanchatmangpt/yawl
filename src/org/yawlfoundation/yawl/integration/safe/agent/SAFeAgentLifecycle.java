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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle manager for SAFe agents.
 *
 * <p>Tracks agent state transitions: CREATED → INITIALIZING → READY → PROCESSING → STOPPING → STOPPED
 *
 * <p>Also tracks participation in ceremonies:
 * <ul>
 *   <li>IDLE: Ready to participate in ceremonies</li>
 *   <li>IN_CEREMONY: Currently participating in ceremony</li>
 *   <li>PROCESSING_WORK_ITEM: Processing a work item</li>
 *   <li>ESCALATING: Escalating a blocker or conflict</li>
 * </ul>
 *
 * <p>Thread-safe: uses AtomicReference for state transitions.
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
public final class SAFeAgentLifecycle {

    private static final Logger logger = LogManager.getLogger(SAFeAgentLifecycle.class);

    private final SAFeAgentRole role;
    private final AtomicReference<State> state;
    private final AtomicReference<ActivityState> activityState;
    private volatile Instant lastStateChange;
    private volatile Instant lastActivityChange;

    /**
     * Create a lifecycle manager for an agent with the given role.
     *
     * @param role the SAFe role (PRODUCT_OWNER, SCRUM_MASTER, etc.)
     */
    public SAFeAgentLifecycle(SAFeAgentRole role) {
        if (role == null) {
            throw new IllegalArgumentException("SAFeAgentRole required");
        }
        this.role = role;
        this.state = new AtomicReference<>(State.CREATED);
        this.activityState = new AtomicReference<>(ActivityState.IDLE);
        this.lastStateChange = Instant.now();
        this.lastActivityChange = Instant.now();
    }

    // =========================================================================
    // State Machine Transitions
    // =========================================================================

    /**
     * Notify lifecycle that agent is starting.
     */
    public void notifyStarting() {
        transitionState(State.CREATED, State.INITIALIZING);
    }

    /**
     * Notify lifecycle that agent has started successfully.
     */
    public void notifyStarted() {
        transitionState(State.INITIALIZING, State.READY);
        logger.info("SAFe {} [{}] started", role, state.get());
    }

    /**
     * Notify lifecycle that agent is stopping.
     */
    public void notifyStopping() {
        transitionState(null, State.STOPPING);  // Allow from any state
    }

    /**
     * Notify lifecycle that agent has stopped.
     */
    public void notifyStopped() {
        state.set(State.STOPPED);
        lastStateChange = Instant.now();
        logger.info("SAFe {} [{}] stopped", role, state.get());
    }

    /**
     * Transition to PROCESSING state (working on task or ceremony).
     */
    public void notifyProcessingStarted() {
        transitionState(State.READY, State.PROCESSING);
    }

    /**
     * Transition back to READY state (idle, waiting for next work).
     */
    public void notifyProcessingComplete() {
        transitionState(State.PROCESSING, State.READY);
    }

    // =========================================================================
    // Activity State Transitions (More Fine-Grained)
    // =========================================================================

    /**
     * Notify that agent is participating in a ceremony.
     *
     * @param ceremonyType the ceremony type (PI_PLANNING, SPRINT_PLANNING, etc.)
     */
    public void notifyInCeremony(String ceremonyType) {
        activityState.set(ActivityState.IN_CEREMONY);
        lastActivityChange = Instant.now();
        logger.debug("SAFe {} entering ceremony: {}", role, ceremonyType);
    }

    /**
     * Notify that agent has finished ceremony participation.
     */
    public void notifyCeremonyComplete() {
        activityState.set(ActivityState.IDLE);
        lastActivityChange = Instant.now();
    }

    /**
     * Notify that agent is processing a work item.
     *
     * @param workItemId the work item ID
     */
    public void notifyProcessingWorkItem(String workItemId) {
        activityState.set(ActivityState.PROCESSING_WORK_ITEM);
        lastActivityChange = Instant.now();
        logger.debug("SAFe {} processing work item: {}", role, workItemId);
    }

    /**
     * Notify that agent is escalating an issue.
     */
    public void notifyEscalating() {
        activityState.set(ActivityState.ESCALATING);
        lastActivityChange = Instant.now();
    }

    // =========================================================================
    // State Query
    // =========================================================================

    /**
     * Get the current lifecycle state.
     *
     * @return the current State enum value
     */
    public State getState() {
        return state.get();
    }

    /**
     * Get the current activity state.
     *
     * @return the current ActivityState enum value
     */
    public ActivityState getActivityState() {
        return activityState.get();
    }

    /**
     * Get the SAFe role.
     *
     * @return the SAFeAgentRole
     */
    public SAFeAgentRole getRole() {
        return role;
    }

    /**
     * Get when the last state change occurred.
     *
     * @return Instant of last state change
     */
    public Instant getLastStateChange() {
        return lastStateChange;
    }

    /**
     * Get when the last activity change occurred.
     *
     * @return Instant of last activity change
     */
    public Instant getLastActivityChange() {
        return lastActivityChange;
    }

    /**
     * Check if agent is ready to participate in ceremonies.
     *
     * @return true if in READY state and IDLE activity
     */
    public boolean isReadyForCeremony() {
        return state.get() == State.READY && activityState.get() == ActivityState.IDLE;
    }

    /**
     * Check if agent is currently idle (not in a ceremony or processing work).
     *
     * @return true if activity state is IDLE
     */
    public boolean isIdle() {
        return activityState.get() == ActivityState.IDLE;
    }

    /**
     * Check if agent has stopped or is stopping.
     *
     * @return true if state is STOPPED or STOPPING
     */
    public boolean isStopped() {
        State current = state.get();
        return current == State.STOPPED || current == State.STOPPING;
    }

    // =========================================================================
    // Private: State Transitions
    // =========================================================================

    private void transitionState(State expected, State newState) {
        State current = state.get();

        if (expected != null && current != expected) {
            logger.warn("Illegal state transition: expected {} but was {} → {}",
                expected, current, newState);
            return;
        }

        if (state.compareAndSet(current, newState)) {
            lastStateChange = Instant.now();
            logger.trace("SAFe {} [{}] → [{}]", role, current, newState);
        } else {
            logger.warn("State transition conflict: {} → {} (concurrent update)", current, newState);
        }
    }

    // =========================================================================
    // Enums
    // =========================================================================

    /**
     * Agent lifecycle states (gross lifecycle).
     */
    public enum State {
        /**
         * Agent has been created but not yet initialized.
         */
        CREATED,

        /**
         * Agent is initializing (starting HTTP server, connecting to engine).
         */
        INITIALIZING,

        /**
         * Agent is ready and waiting for work or ceremonies.
         */
        READY,

        /**
         * Agent is processing work (ceremony or work item).
         */
        PROCESSING,

        /**
         * Agent is shutting down.
         */
        STOPPING,

        /**
         * Agent has stopped.
         */
        STOPPED
    }

    /**
     * Agent activity states (fine-grained what is the agent doing right now).
     */
    public enum ActivityState {
        /**
         * Agent is idle, ready to participate in ceremonies or process work.
         */
        IDLE,

        /**
         * Agent is currently participating in a ceremony (PI Planning, Standup, etc.)
         */
        IN_CEREMONY,

        /**
         * Agent is processing a work item.
         */
        PROCESSING_WORK_ITEM,

        /**
         * Agent is escalating an issue to another agent or management.
         */
        ESCALATING,

        /**
         * Agent is waiting for another agent's response (blocked).
         */
        WAITING
    }

    @Override
    public String toString() {
        return "SAFeAgentLifecycle{" +
                "role=" + role +
                ", state=" + state.get() +
                ", activity=" + activityState.get() +
                ", lastStateChange=" + lastStateChange +
                ", lastActivityChange=" + lastActivityChange +
                '}';
    }
}
