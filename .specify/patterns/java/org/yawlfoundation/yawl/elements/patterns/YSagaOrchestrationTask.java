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
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.*;

import java.util.List;
import java.util.Map;

/**
 * Implements the Saga Orchestration pattern for distributed transactions
 * with compensating actions.
 *
 * <p>When a sequence of tasks fails, compensating actions are executed
 * in reverse order to undo partial work. This is essential for microservices
 * architectures and distributed workflows.</p>
 *
 * <p>State Machine:</p>
 * <ul>
 *   <li>IDLE: Saga not started</li>
 *   <li>FORWARD: Executing forward tasks</li>
 *   <li>COMPENSATING: Executing compensating actions (reverse order)</li>
 *   <li>COMPLETED: All forward tasks completed successfully</li>
 *   <li>COMPENSATED: All compensating actions completed</li>
 *   <li>FAILED: Compensating actions failed (requires manual intervention)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 5.3
 * @see YSagaStep
 * @see YCompensatingAction
 * @see YSagaState
 */
public class YSagaOrchestrationTask extends YCompositeTask {

    /** Saga configuration: ordered list of forward steps */
    private List<YSagaStep> _forwardSteps;

    /** Map of step ID to compensating action */
    private Map<String, YCompensatingAction> _compensatingActions;

    /** Current saga execution state */
    private YSagaState _sagaState;

    /**
     * Constructs a new Saga Orchestration task.
     *
     * @param id the task identifier
     * @param joinType the task's join type
     * @param splitType the task's split type
     * @param container the containing net
     */
    public YSagaOrchestrationTask(String id, int joinType, int splitType, YNet container) {
        super(id, joinType, splitType, container);
        _sagaState = new YSagaState(id);
    }

    /**
     * Adds a step to the saga's forward execution sequence.
     *
     * @param step the saga step to add
     * @throws NullPointerException if step is null
     */
    public void addSagaStep(YSagaStep step) {
        if (step == null) {
            throw new NullPointerException("Saga step cannot be null");
        }
        _forwardSteps.add(step);
    }

    /**
     * Registers a compensating action for a specific step.
     *
     * @param stepId the ID of the step this action compensates
     * @param action the compensating action
     * @throws NullPointerException if action is null
     */
    public void addCompensatingAction(String stepId, YCompensatingAction action) {
        if (action == null) {
            throw new NullPointerException("Compensating action cannot be null");
        }
        _compensatingActions.put(stepId, action);
    }

    /**
     * Starts the saga execution in forward direction.
     *
     * @param pmgr the persistence manager
     * @throws YStateException if saga cannot be started
     * @throws YPersistenceException if persistence fails
     */
    public void startSaga(YPersistenceManager pmgr)
            throws YStateException, YPersistenceException {

        if (_sagaState.getPhase() != YSagaState.SagaPhase.IDLE) {
            throw new YStateException("Saga is not in IDLE state");
        }

        _sagaState.setPhase(YSagaState.SagaPhase.FORWARD);

        if (pmgr != null) {
            pmgr.storeObjectFromExternal(_sagaState);
        }

        // Execute first step
        if (!_forwardSteps.isEmpty()) {
            executeNextForwardStep(pmgr);
        }
    }

    /**
     * Called when a saga step completes successfully.
     *
     * @param pmgr the persistence manager
     * @param stepId the ID of the completed step
     * @throws YStateException if step completion is invalid
     * @throws YPersistenceException if persistence fails
     */
    public void onStepComplete(YPersistenceManager pmgr, String stepId)
            throws YStateException, YPersistenceException {

        if (_sagaState.getPhase() != YSagaState.SagaPhase.FORWARD) {
            throw new YStateException("Saga is not in FORWARD phase");
        }

        _sagaState.addCompletedStep(stepId);

        if (pmgr != null) {
            pmgr.updateObjectExternal(_sagaState);
        }

        // Check if all steps complete
        if (_sagaState.getCompletedStepIds().size() == _forwardSteps.size()) {
            _sagaState.setPhase(YSagaState.SagaPhase.COMPLETED);
            if (pmgr != null) {
                pmgr.updateObjectExternal(_sagaState);
            }
        } else {
            // Execute next step
            executeNextForwardStep(pmgr);
        }
    }

    /**
     * Called when a saga step fails. Triggers compensation.
     *
     * @param pmgr the persistence manager
     * @param stepId the ID of the failed step
     * @param cause the failure cause
     * @throws YStateException if compensation cannot be started
     * @throws YPersistenceException if persistence fails
     */
    public void onStepFailure(YPersistenceManager pmgr, String stepId, Throwable cause)
            throws YStateException, YPersistenceException {

        if (_sagaState.getPhase() != YSagaState.SagaPhase.FORWARD) {
            throw new YStateException("Saga is not in FORWARD phase");
        }

        _sagaState.setFailedStepId(stepId);
        _sagaState.setFailureCause(cause);
        _sagaState.setPhase(YSagaState.SagaPhase.COMPENSATING);
        _sagaState.setCurrentCompensationIndex(_sagaState.getCompletedStepIds().size() - 1);

        if (pmgr != null) {
            pmgr.updateObjectExternal(_sagaState);
        }

        // Start compensation
        compensate(pmgr);
    }

    /**
     * Executes compensating actions in reverse order.
     *
     * @param pmgr the persistence manager
     * @throws YStateException if compensation is invalid
     * @throws YPersistenceException if persistence fails
     */
    public void compensate(YPersistenceManager pmgr)
            throws YStateException, YPersistenceException {

        if (_sagaState.getPhase() != YSagaState.SagaPhase.COMPENSATING) {
            throw new YStateException("Saga is not in COMPENSATING phase");
        }

        int index = _sagaState.getCurrentCompensationIndex();

        if (index < 0) {
            // All compensations complete
            _sagaState.setPhase(YSagaState.SagaPhase.COMPENSATED);
            if (pmgr != null) {
                pmgr.updateObjectExternal(_sagaState);
            }
            return;
        }

        String stepId = _sagaState.getCompletedStepIds().get(index);
        YCompensatingAction action = _compensatingActions.get(stepId);

        if (action != null) {
            try {
                Element stepOutputData = _sagaState.getStepOutputData(stepId);
                action.execute(pmgr, stepOutputData);
            } catch (YQueryException e) {
                _sagaState.setPhase(YSagaState.SagaPhase.FAILED);
                if (pmgr != null) {
                    pmgr.updateObjectExternal(_sagaState);
                }
                throw new YStateException("Compensation failed for step: " + stepId, e);
            }
        }

        _sagaState.setCurrentCompensationIndex(index - 1);

        if (pmgr != null) {
            pmgr.updateObjectExternal(_sagaState);
        }

        // Continue with next compensation
        compensate(pmgr);
    }

    /**
     * Gets the current saga state.
     *
     * @return the saga state
     */
    public YSagaState getSagaState() {
        return _sagaState;
    }

    /**
     * Gets the list of completed step IDs.
     *
     * @return list of completed step IDs
     */
    public List<String> getCompletedSteps() {
        return _sagaState.getCompletedStepIds();
    }

    /**
     * Gets the list of pending compensation step IDs.
     *
     * @return list of pending compensation step IDs
     */
    public List<String> getPendingCompensations() {
        return _sagaState.getPendingCompensationIds();
    }

    /**
     * Checks if the saga is currently compensating.
     *
     * @return true if in COMPENSATING phase
     */
    public boolean isCompensating() {
        return _sagaState.getPhase() == YSagaState.SagaPhase.COMPENSATING;
    }

    /**
     * Executes the next forward step in the saga.
     *
     * @param pmgr the persistence manager
     */
    private void executeNextForwardStep(YPersistenceManager pmgr) {
        int nextIndex = _sagaState.getCompletedStepIds().size();
        if (nextIndex < _forwardSteps.size()) {
            YSagaStep step = _forwardSteps.get(nextIndex);
            // Step execution logic delegated to task execution
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void startOne(YPersistenceManager pmgr, YIdentifier id)
            throws YPersistenceException {
        // Implementation for atomic task behavior within saga
        this._mi_entered.removeOne(pmgr, id);
        this._mi_executing.add(pmgr, id);
    }
}
