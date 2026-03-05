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

package org.yawlfoundation.yawl.integration.autonomous;

/**
 * Lifecycle states for an autonomous agent.
 *
 * <p>Models the state machine: CREATED -> INITIALIZING -> DISCOVERING ->
 * REASONING -> ACTING -> REPORTING -> DISCOVERING (loop) or STOPPING -> STOPPED.</p>
 *
 * <p>State transitions:</p>
 * <pre>
 * CREATED -> INITIALIZING -> DISCOVERING -> REASONING -> ACTING -> REPORTING -> DISCOVERING
 *                                                                                   |
 *                                                     any state -> STOPPING -> STOPPED
 *                                                     any state -> FAILED
 * </pre>
 *
 * @since YAWL 6.0
 */
public enum AgentLifecycle {

    /** Agent has been created but not yet initialized. */
    CREATED,

    /** Agent is initializing: connecting to engine, registering capabilities. */
    INITIALIZING,

    /** Agent is discovering available work items via its DiscoveryStrategy. */
    DISCOVERING,

    /** Agent is reasoning about eligibility and producing output for a work item. */
    REASONING,

    /** Agent is acting: checking out a work item and submitting output. */
    ACTING,

    /** Agent is reporting: logging completion, updating metrics. */
    REPORTING,

    /** Agent is shutting down gracefully. */
    STOPPING,

    /** Agent has stopped and released all resources. */
    STOPPED,

    /** Agent has encountered an unrecoverable error. */
    FAILED;

    /**
     * Checks if this state allows transitioning to the given target state.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(AgentLifecycle target) {
        if (target == STOPPING || target == FAILED) {
            return this != STOPPED;
        }
        return switch (this) {
            case CREATED -> target == INITIALIZING;
            case INITIALIZING -> target == DISCOVERING;
            case DISCOVERING -> target == REASONING;
            case REASONING -> target == ACTING;
            case ACTING -> target == REPORTING;
            case REPORTING -> target == DISCOVERING;
            case STOPPING -> target == STOPPED;
            case STOPPED, FAILED -> false;
        };
    }

    /**
     * Checks if the agent is in an active processing state.
     *
     * @return true if the agent is actively processing work
     */
    public boolean isActive() {
        return this == DISCOVERING || this == REASONING
                || this == ACTING || this == REPORTING;
    }

    /**
     * Checks if the agent is in a terminal state.
     *
     * @return true if the agent has stopped or failed
     */
    public boolean isTerminal() {
        return this == STOPPED || this == FAILED;
    }
}
