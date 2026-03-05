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

package org.yawlfoundation.yawl.elements;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates YAWL specifications and provides detailed error reports.
 * Checks specification structure, semantics, and workflow soundness.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class YSpecificationValidator {

    private static final Logger logger = LoggerFactory.getLogger(YSpecificationValidator.class);
    private final YSpecification _spec;
    private final List<ValidationError> _errors = new ArrayList<>();

    /**
     * Creates a new validator for the given specification.
     * @param spec the specification to validate
     */
    public YSpecificationValidator(YSpecification spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }
        _spec = spec;
    }

    /**
     * Validates specification structure and semantics.
     * @return true if valid, false if errors were found
     */
    public boolean validate() {
        _errors.clear();

        validateSpecificationID();
        validateRootNet();
        validateDecompositions();
        validateNetStructures();

        boolean isValid = _errors.isEmpty();
        if (isValid) {
            logger.info("Specification validation passed: {}", getSpecID());
        } else {
            logger.warn("Specification validation failed with {} errors: {}",
                       _errors.size(), getSpecID());
        }

        return isValid;
    }

    /**
     * Validates that the specification has a valid ID.
     */
    private void validateSpecificationID() {
        String uri = _spec.getURI();
        if (uri == null || uri.trim().isEmpty()) {
            addError("Specification URI is required", "spec-id", null);
        }
    }

    /**
     * Validates that the specification has a root net.
     */
    private void validateRootNet() {
        YNet rootNet = _spec.getRootNet();
        if (rootNet == null) {
            addError("Specification must have a root net", "root-net", null);
            return;
        }

        if (rootNet.getID() == null || rootNet.getID().trim().isEmpty()) {
            addError("Root net must have an ID", "root-net-id", rootNet.getID());
        }
    }

    /**
     * Validates all decompositions in the specification.
     */
    private void validateDecompositions() {
        Set<YDecomposition> decomps = _spec.getDecompositions();

        if (decomps == null || decomps.isEmpty()) {
            addError("Specification must have at least one decomposition",
                    "decompositions", null);
            return;
        }

        for (YDecomposition decomp : decomps) {
            if (decomp.getID() == null || decomp.getID().trim().isEmpty()) {
                addError("Decomposition must have an ID", "decomp-id", null);
            }
        }
    }

    /**
     * Validates the structure of all nets in the specification.
     */
    private void validateNetStructures() {
        Set<YDecomposition> decomps = _spec.getDecompositions();
        if (decomps == null) {
            return;
        }

        for (YDecomposition decomp : decomps) {
            if (decomp instanceof YNet) {
                validateNet((YNet) decomp);
            }
        }
    }

    /**
     * Validates an individual net.
     * @param net the net to validate
     */
    private void validateNet(YNet net) {
        String netID = net.getID();

        YInputCondition inputCondition = net.getInputCondition();
        if (inputCondition == null) {
            addError("Net '" + netID + "' must have an input condition",
                    "input-condition", netID);
        }

        YOutputCondition outputCondition = net.getOutputCondition();
        if (outputCondition == null) {
            addError("Net '" + netID + "' must have an output condition",
                    "output-condition", netID);
        }

        Map<String, YExternalNetElement> elementsMap = net.getNetElements();
        if (elementsMap == null || elementsMap.size() < 2) {
            addError("Net '" + netID + "' must have at least input and output conditions",
                    "net-elements", netID);
        }

        validateNetConnectivity(net);
    }

    /**
     * Validates that all net elements are properly connected.
     * @param net the net to validate
     */
    private void validateNetConnectivity(YNet net) {
        Map<String, YExternalNetElement> elementsMap = net.getNetElements();
        if (elementsMap == null) {
            return;
        }

        for (YExternalNetElement element : elementsMap.values()) {
            if (element instanceof YTask) {
                validateTaskConnectivity((YTask) element, net.getID());
            }
        }
    }

    /**
     * Validates that a task has proper connections.
     * @param task the task to validate
     * @param netID the containing net's ID
     */
    private void validateTaskConnectivity(YTask task, String netID) {
        String taskID = task.getID();

        if (task.getPresetElements().isEmpty() && task.getPostsetElements().isEmpty()) {
            addError("Task '" + taskID + "' in net '" + netID + "' has no connections",
                    "task-isolation", taskID);
        }
    }

    /**
     * Adds a validation error.
     * @param message the error message
     * @param errorCode the error code
     * @param elementID the ID of the element with the error (may be null)
     */
    private void addError(String message, String errorCode, String elementID) {
        ValidationError error = new ValidationError(message, errorCode, elementID);
        _errors.add(error);
        logger.warn("Validation error: {} [code={}, element={}]", message, errorCode, elementID);
    }

    /**
     * Gets all validation errors found.
     * @return an immutable list of validation errors
     */
    public List<ValidationError> getErrors() {
        return List.copyOf(_errors);
    }

    /**
     * Gets a human-readable error report.
     * @return formatted error report
     */
    public String getErrorReport() {
        if (_errors.isEmpty()) {
            return "No validation errors found.";
        }

        StringBuilder report = new StringBuilder();
        report.append("Validation Errors (").append(_errors.size()).append("):\n");
        report.append("Specification: ").append(getSpecID()).append("\n\n");

        for (int i = 0; i < _errors.size(); i++) {
            report.append(i + 1).append(". ").append(_errors.get(i)).append("\n");
        }

        return report.toString();
    }

    /**
     * Gets the specification ID for error messages.
     * @return the specification ID or "unknown"
     */
    private String getSpecID() {
        String uri = _spec.getURI();
        return (uri != null && !uri.isEmpty()) ? uri : "unknown";
    }

    /**
     * Represents a validation error with context.
     */
    public static class ValidationError {
        private final String _message;
        private final String _errorCode;
        private final String _elementID;
        private final long _timestamp;

        /**
         * Creates a new validation error.
         * @param message the error message
         * @param errorCode the error code
         * @param elementID the element ID (may be null)
         */
        public ValidationError(String message, String errorCode, String elementID) {
            _message = message;
            _errorCode = errorCode;
            _elementID = elementID;
            _timestamp = System.currentTimeMillis();
        }

        /**
         * Gets the error message.
         * @return the error message
         */
        public String getMessage() {
            return _message;
        }

        /**
         * Gets the error code.
         * @return the error code
         */
        public String getErrorCode() {
            return _errorCode;
        }

        /**
         * Gets the ID of the element with the error.
         * @return the element ID, or null if not applicable
         */
        public String getElementID() {
            return _elementID;
        }

        /**
         * Gets the timestamp when this error was created.
         * @return the timestamp in milliseconds
         */
        public long getTimestamp() {
            return _timestamp;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(_message);
            sb.append(" [code=").append(_errorCode);
            if (_elementID != null) {
                sb.append(", element=").append(_elementID);
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
