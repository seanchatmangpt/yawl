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

import org.yawlfoundation.yawl.elements.YTask;

/**
 * Represents a single step in a Saga orchestration.
 *
 * <p>Each step has an associated task and an optional compensating action
 * that will be executed if the saga needs to rollback.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YSagaStep {

    /** Unique identifier for this step */
    private final String _stepId;

    /** The task to execute for this step */
    private final YTask _task;

    /** Reference to the compensating action for this step */
    private String _compensatingActionRef;

    /** Execution order (0-based) */
    private final int _order;

    /** Whether failure of this step immediately triggers compensation */
    private boolean _isCritical;

    /** Maximum retry attempts for this step */
    private int _maxRetries;

    /** Timeout in milliseconds (0 = no timeout) */
    private long _timeoutMs;

    /**
     * Constructs a new saga step.
     *
     * @param stepId unique identifier for this step
     * @param task the task to execute
     * @param order the execution order
     */
    public YSagaStep(String stepId, YTask task, int order) {
        this._stepId = stepId;
        this._task = task;
        this._order = order;
        this._isCritical = true;
        this._maxRetries = 0;
        this._timeoutMs = 0;
    }

    /**
     * Gets the step identifier.
     *
     * @return the step ID
     */
    public String getStepId() {
        return _stepId;
    }

    /**
     * Gets the task for this step.
     *
     * @return the task
     */
    public YTask getTask() {
        return _task;
    }

    /**
     * Gets the reference to the compensating action.
     *
     * @return the compensating action reference, may be null
     */
    public String getCompensatingActionRef() {
        return _compensatingActionRef;
    }

    /**
     * Sets the reference to the compensating action.
     *
     * @param compensatingActionRef the compensating action reference
     */
    public void setCompensatingActionRef(String compensatingActionRef) {
        this._compensatingActionRef = compensatingActionRef;
    }

    /**
     * Gets the execution order.
     *
     * @return the order (0-based)
     */
    public int getOrder() {
        return _order;
    }

    /**
     * Checks if this step is critical.
     *
     * <p>Critical steps trigger immediate compensation on failure.
     * Non-critical steps may allow the saga to continue.</p>
     *
     * @return true if critical, false otherwise
     */
    public boolean isCritical() {
        return _isCritical;
    }

    /**
     * Sets whether this step is critical.
     *
     * @param critical true if critical
     */
    public void setCritical(boolean critical) {
        _isCritical = critical;
    }

    /**
     * Gets the maximum retry attempts.
     *
     * @return the max retries
     */
    public int getMaxRetries() {
        return _maxRetries;
    }

    /**
     * Sets the maximum retry attempts.
     *
     * @param maxRetries the max retries
     */
    public void setMaxRetries(int maxRetries) {
        this._maxRetries = maxRetries;
    }

    /**
     * Gets the timeout in milliseconds.
     *
     * @return the timeout, 0 means no timeout
     */
    public long getTimeoutMs() {
        return _timeoutMs;
    }

    /**
     * Sets the timeout in milliseconds.
     *
     * @param timeoutMs the timeout
     */
    public void setTimeoutMs(long timeoutMs) {
        this._timeoutMs = timeoutMs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YSagaStep{" +
                "stepId='" + _stepId + '\'' +
                ", order=" + _order +
                ", critical=" + _isCritical +
                '}';
    }
}
