/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.elements.patterns;

/**
 * Tracks the state of an interleaved parallel router.
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YInterleavedState {

    /**
     * Enumeration of router phases.
     */
    public enum RouterPhase {
        /** No tasks enabled */
        IDLE,
        /** Tasks being enabled, waiting for mutex */
        ENABLING,
        /** One task executing, others waiting */
        EXECUTING_ONE,
        /** All enabled tasks completed */
        ALL_COMPLETE
    }

    /** Router identifier */
    private final String _routerId;

    /** Current phase */
    private RouterPhase _phase;

    /** Currently executing task ID */
    private String _executingTaskId;

    /** Cycle count (for round-robin) */
    private int _cycleCount;

    /** Timestamp when current phase started */
    private long _phaseStartTimestamp;

    /**
     * Constructs a new interleaved state.
     *
     * @param routerId the router identifier
     */
    public YInterleavedState(String routerId) {
        this._routerId = routerId;
        this._phase = RouterPhase.IDLE;
        this._executingTaskId = null;
        this._cycleCount = 0;
        this._phaseStartTimestamp = 0;
    }

    /**
     * Gets the router identifier.
     *
     * @return the router ID
     */
    public String getRouterId() {
        return _routerId;
    }

    /**
     * Gets the current phase.
     *
     * @return the phase
     */
    public RouterPhase getPhase() {
        return _phase;
    }

    /**
     * Sets the current phase.
     *
     * @param phase the phase
     */
    public void setPhase(RouterPhase phase) {
        this._phase = phase;
        this._phaseStartTimestamp = System.currentTimeMillis();
        if (phase == RouterPhase.EXECUTING_ONE) {
            _cycleCount++;
        }
    }

    /**
     * Gets the executing task ID.
     *
     * @return the task ID, or null
     */
    public String getExecutingTaskId() {
        return _executingTaskId;
    }

    /**
     * Sets the executing task ID.
     *
     * @param taskId the task ID
     */
    public void setExecutingTaskId(String taskId) {
        this._executingTaskId = taskId;
    }

    /**
     * Gets the cycle count.
     *
     * @return the cycle count
     */
    public int getCycleCount() {
        return _cycleCount;
    }

    /**
     * Gets the phase duration.
     *
     * @return the duration in milliseconds
     */
    public long getPhaseDuration() {
        if (_phaseStartTimestamp == 0) {
            return 0;
        }
        return System.currentTimeMillis() - _phaseStartTimestamp;
    }

    /**
     * Checks if the router is in a terminal state.
     *
     * @return true if all complete
     */
    public boolean isTerminal() {
        return _phase == RouterPhase.ALL_COMPLETE;
    }

    /**
     * Resets the state.
     */
    public void reset() {
        _phase = RouterPhase.IDLE;
        _executingTaskId = null;
        _phaseStartTimestamp = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YInterleavedState{" +
                "routerId='" + _routerId + '\'' +
                ", phase=" + _phase +
                ", executingTaskId='" + _executingTaskId + '\'' +
                ", cycleCount=" + _cycleCount +
                '}';
    }
}
