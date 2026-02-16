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
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;

import java.util.Map;

/**
 * Represents a compensating action in a Saga orchestration.
 *
 * <p>Compensating actions are executed in reverse order when a saga
 * fails, to undo the effects of completed steps.</p>
 *
 * @author YAWL Foundation
 * @since 5.3
 */
public class YCompensatingAction {

    /** Unique identifier for this action */
    private final String _actionId;

    /** The decomposition to execute for compensation */
    private final YDecomposition _decomposition;

    /** Input mappings from original task output to compensation input */
    private Map<String, String> _inputMappings;

    /** XQuery expression for dynamic compensation logic */
    private String _xqueryExpression;

    /** Whether this action is optional (failure tolerated) */
    private boolean _isOptional;

    /** Maximum retry attempts for this action */
    private int _maxRetries;

    /**
     * Constructs a new compensating action.
     *
     * @param actionId unique identifier
     * @param decomposition the decomposition to execute
     */
    public YCompensatingAction(String actionId, YDecomposition decomposition) {
        this._actionId = actionId;
        this._decomposition = decomposition;
        this._isOptional = false;
        this._maxRetries = 0;
    }

    /**
     * Executes the compensating action with the provided input data.
     *
     * @param pmgr the persistence manager
     * @param inputData the input data from the original task output
     * @throws YStateException if execution fails
     * @throws YQueryException if query evaluation fails
     */
    public void execute(YPersistenceManager pmgr, Element inputData)
            throws YStateException, YQueryException {

        if (_decomposition == null) {
            if (_isOptional) {
                return; // Skip optional actions without decomposition
            }
            throw new YStateException("No decomposition for compensating action: " + _actionId);
        }

        Element transformedInput = transformInput(inputData);

        // Execute the compensation decomposition
        // Actual execution delegated to engine
        executeDecomposition(pmgr, transformedInput);
    }

    /**
     * Gets the action identifier.
     *
     * @return the action ID
     */
    public String getActionId() {
        return _actionId;
    }

    /**
     * Gets the decomposition.
     *
     * @return the decomposition
     */
    public YDecomposition getDecomposition() {
        return _decomposition;
    }

    /**
     * Gets the input mappings.
     *
     * @return map of parameter name to XQuery expression
     */
    public Map<String, String> getInputMappings() {
        return _inputMappings;
    }

    /**
     * Sets the input mappings.
     *
     * @param inputMappings map of parameter name to XQuery expression
     */
    public void setInputMappings(Map<String, String> inputMappings) {
        this._inputMappings = inputMappings;
    }

    /**
     * Gets the XQuery expression for dynamic logic.
     *
     * @return the XQuery expression
     */
    public String getXqueryExpression() {
        return _xqueryExpression;
    }

    /**
     * Sets the XQuery expression for dynamic logic.
     *
     * @param xqueryExpression the XQuery expression
     */
    public void setXqueryExpression(String xqueryExpression) {
        this._xqueryExpression = xqueryExpression;
    }

    /**
     * Checks if this action is optional.
     *
     * @return true if optional
     */
    public boolean isOptional() {
        return _isOptional;
    }

    /**
     * Sets whether this action is optional.
     *
     * @param optional true if optional
     */
    public void setOptional(boolean optional) {
        _isOptional = optional;
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
     * Transforms input data according to mappings.
     *
     * @param inputData the original input data
     * @return transformed input
     */
    private Element transformInput(Element inputData) {
        if (_inputMappings == null || _inputMappings.isEmpty()) {
            return inputData;
        }
        // Apply XQuery transformations based on mappings
        // Implementation would use SaxonUtil for query evaluation
        return inputData;
    }

    /**
     * Executes the decomposition.
     *
     * @param pmgr the persistence manager
     * @param inputData the input data
     */
    private void executeDecomposition(YPersistenceManager pmgr, Element inputData) {
        // Delegated to engine for actual execution
        // This is a placeholder for the execution logic
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "YCompensatingAction{" +
                "actionId='" + _actionId + '\'' +
                ", decomposition=" + (_decomposition != null ? _decomposition.getID() : "null") +
                ", optional=" + _isOptional +
                '}';
    }
}
