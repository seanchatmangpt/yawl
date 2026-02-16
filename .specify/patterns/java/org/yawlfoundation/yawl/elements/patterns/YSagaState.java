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

import org.jdom2.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the execution state of a Saga orchestration.
 *
 * <p>This class maintains the current phase, completed steps,
 * failure information, and step output data for compensation.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YSagaState {

    /**
     * Enumeration of saga execution phases.
     */
    public enum SagaPhase {
        /** Saga not started */
        IDLE,
        /** Executing forward tasks */
        FORWARD,
        /** Executing compensating actions (reverse order) */
        COMPENSATING,
        /** All forward tasks completed successfully */
        COMPLETED,
        /** All compensating actions completed */
        COMPENSATED,
        /** Compensating actions failed (requires manual intervention) */
        FAILED
    }

    /** Unique identifier for this saga instance */
    private final String _sagaId;

    /** Current execution phase */
    private SagaPhase _phase;

    /** IDs of completed steps in execution order */
    private final List<String> _completedStepIds;

    /** Output data from each step, for compensation */
    private final Map<String, Element> _stepOutputData;

    /** ID of the step that failed */
    private String _failedStepId;

    /** The cause of failure */
    private Throwable _failureCause;

    /** Current index in compensation sequence */
    private int _currentCompensationIndex;

    /** Timestamp when saga started */
    private long _startTimestamp;

    /** Timestamp when saga completed (any phase) */
    private long _endTimestamp;

    /**
     * Constructs a new saga state.
     *
     * @param sagaId the saga identifier
     */
    public YSagaState(String sagaId) {
        this._sagaId = sagaId;
        this._phase = SagaPhase.IDLE;
        this._completedStepIds = new ArrayList<>();
        this._stepOutputData = new HashMap<>();
        this._currentCompensationIndex = -1;
    }

    /**
     * Gets the saga identifier.
     *
     * @return the saga ID
     */
    public String getSagaId() {
        return _sagaId;
    }

    /**
     * Gets the current phase.
     *
     * @return the phase
     */
    public SagaPhase getPhase() {
        return _phase;
    }

    /**
     * Sets the current phase.
     *
     * @param phase the phase
     */
    public void setPhase(SagaPhase phase) {
        this._phase = phase;
        if (phase == SagaPhase.FORWARD && _startTimestamp == 0) {
            _startTimestamp = System.currentTimeMillis();
        }
        if (phase == SagaPhase.COMPLETED ||
            phase == SagaPhase.COMPENSATED ||
            phase == SagaPhase.FAILED) {
            _endTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * Gets the list of completed step IDs.
     *
     * @return the completed step IDs
     */
    public List<String> getCompletedStepIds() {
        return new ArrayList<>(_completedStepIds);
    }

    /**
     * Adds a completed step.
     *
     * @param stepId the step ID
     */
    public void addCompletedStep(String stepId) {
        if (!_completedStepIds.contains(stepId)) {
            _completedStepIds.add(stepId);
        }
    }

    /**
     * Gets the list of pending compensation step IDs.
     *
     * @return the pending compensation IDs (in reverse order)
     */
    public List<String> getPendingCompensationIds() {
        List<String> pending = new ArrayList<>();
        for (int i = _currentCompensationIndex; i >= 0; i--) {
            if (i < _completedStepIds.size()) {
                pending.add(_completedStepIds.get(i));
            }
        }
        return pending;
    }

    /**
     * Gets output data for a specific step.
     *
     * @param stepId the step ID
     * @return the output data, or null if not found
     */
    public Element getStepOutputData(String stepId) {
        return _stepOutputData.get(stepId);
    }

    /**
     * Stores output data for a step.
     *
     * @param stepId the step ID
     * @param outputData the output data
     */
    public void setStepOutputData(String stepId, Element outputData) {
        _stepOutputData.put(stepId, outputData);
    }

    /**
     * Gets the failed step ID.
     *
     * @return the failed step ID
     */
    public String getFailedStepId() {
        return _failedStepId;
    }

    /**
     * Sets the failed step ID.
     *
     * @param failedStepId the failed step ID
     */
    public void setFailedStepId(String failedStepId) {
        this._failedStepId = failedStepId;
    }

    /**
     * Gets the failure cause.
     *
     * @return the failure cause
     */
    public Throwable getFailureCause() {
        return _failureCause;
    }

    /**
     * Sets the failure cause.
     *
     * @param failureCause the failure cause
     */
    public void setFailureCause(Throwable failureCause) {
        this._failureCause = failureCause;
    }

    /**
     * Gets the current compensation index.
     *
     * @return the index (-1 if not compensating)
     */
    public int getCurrentCompensationIndex() {
        return _currentCompensationIndex;
    }

    /**
     * Sets the current compensation index.
     *
     * @param index the index
     */
    public void setCurrentCompensationIndex(int index) {
        this._currentCompensationIndex = index;
    }

    /**
     * Gets the start timestamp.
     *
     * @return the start timestamp in milliseconds
     */
    public long getStartTimestamp() {
        return _startTimestamp;
    }

    /**
     * Gets the end timestamp.
     *
     * @return the end timestamp in milliseconds, 0 if not ended
     */
    public long getEndTimestamp() {
        return _endTimestamp;
    }

    /**
     * Gets the duration of the saga.
     *
     * @return the duration in milliseconds, or -1 if not started
     */
    public long getDuration() {
        if (_startTimestamp == 0) {
            return -1;
        }
        if (_endTimestamp > 0) {
            return _endTimestamp - _startTimestamp;
        }
        return System.currentTimeMillis() - _startTimestamp;
    }

    /**
     * Checks if the saga is in a terminal state.
     *
     * @return true if completed, compensated, or failed
     */
    public boolean isTerminal() {
        return _phase == SagaPhase.COMPLETED ||
               _phase == SagaPhase.COMPENSATED ||
               _phase == SagaPhase.FAILED;
    }

    /**
     * Resets the saga state for reuse.
     */
    public void reset() {
        _phase = SagaPhase.IDLE;
        _completedStepIds.clear();
        _stepOutputData.clear();
        _failedStepId = null;
        _failureCause = null;
        _currentCompensationIndex = -1;
        _startTimestamp = 0;
        _endTimestamp = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YSagaState{" +
                "sagaId='" + _sagaId + '\'' +
                ", phase=" + _phase +
                ", completedSteps=" + _completedStepIds.size() +
                ", failedStepId='" + _failedStepId + '\'' +
                ", compensationIndex=" + _currentCompensationIndex +
                '}';
    }
}
