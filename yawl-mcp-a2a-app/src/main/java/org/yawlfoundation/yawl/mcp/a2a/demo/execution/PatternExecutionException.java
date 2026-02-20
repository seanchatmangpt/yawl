/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.execution;

import java.util.Objects;

/**
 * Exception thrown when a workflow pattern execution fails.
 *
 * <p>This exception provides detailed context about pattern execution failures,
 * including the pattern identifier, failure stage, and optional error codes.</p>
 *
 * <h2>Error Codes</h2>
 * <ul>
 *   <li>SYNTAX_ERROR - Specification parsing failed</li>
 *   <li>VALIDATION_ERROR - Specification validation failed</li>
 *   <li>LAUNCH_ERROR - Case launch failed</li>
 *   <li>EXECUTION_ERROR - Runtime execution error</li>
 *   <li>TIMEOUT_ERROR - Execution exceeded time limit</li>
 *   <li>WORKITEM_ERROR - Work item processing error</li>
 *   <li>DECISION_ERROR - Routing decision error</li>
 *   <li>CANCELLED - Execution was cancelled</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try {
 *     ExecutionResult result = harness.execute(specXml);
 * } catch (PatternExecutionException e) {
 *     System.err.println("Pattern failed: " + e.getPatternId());
 *     System.err.println("Error code: " + e.getErrorCode());
 *     System.err.println("Stage: " + e.getStage());
 *     e.printStackTrace();
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PatternExecutionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Error codes for pattern execution failures.
     */
    public enum ErrorCode {
        /** Specification parsing failed */
        SYNTAX_ERROR("Syntax error in specification"),

        /** Specification validation failed */
        VALIDATION_ERROR("Specification validation failed"),

        /** Case launch failed */
        LAUNCH_ERROR("Failed to launch case"),

        /** Runtime execution error */
        EXECUTION_ERROR("Execution error"),

        /** Execution exceeded time limit */
        TIMEOUT_ERROR("Execution timed out"),

        /** Work item processing error */
        WORKITEM_ERROR("Work item processing error"),

        /** Routing decision error */
        DECISION_ERROR("Routing decision error"),

        /** Execution was cancelled */
        CANCELLED("Execution was cancelled"),

        /** Resource not found */
        NOT_FOUND("Resource not found"),

        /** Configuration error */
        CONFIG_ERROR("Configuration error"),

        /** Unknown error */
        UNKNOWN("Unknown error");

        private final String description;

        ErrorCode(String description) {
            this.description = description;
        }

        /**
         * Get the error description.
         *
         * @return the human-readable description
         */
        public String getDescription() {
            return description;
        }
    }

    /**
     * Execution stages where failures can occur.
     */
    public enum Stage {
        /** Specification parsing */
        PARSE,
        /** Specification validation */
        VALIDATE,
        /** Case initialization */
        INIT,
        /** Work item processing */
        WORK_ITEM,
        /** State transition */
        TRANSITION,
        /** Decision evaluation */
        DECISION,
        /** Case completion */
        COMPLETE,
        /** Unknown stage */
        UNKNOWN
    }

    private final String patternId;
    private final ErrorCode errorCode;
    private final Stage stage;
    private final String caseId;

    /**
     * Creates a new pattern execution exception with a message.
     *
     * @param message the error message
     */
    public PatternExecutionException(String message) {
        this(message, null, null, null, null, null);
    }

    /**
     * Creates a new pattern execution exception with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public PatternExecutionException(String message, Throwable cause) {
        this(message, cause, null, null, null, null);
    }

    /**
     * Creates a new pattern execution exception with full context.
     *
     * @param message the error message
     * @param cause the underlying cause
     * @param patternId the pattern identifier that failed
     * @param errorCode the error code
     * @param stage the execution stage
     * @param caseId the case identifier
     */
    public PatternExecutionException(String message, Throwable cause,
                                     String patternId, ErrorCode errorCode,
                                     Stage stage, String caseId) {
        super(message, cause);
        this.patternId = patternId;
        this.errorCode = errorCode != null ? errorCode : ErrorCode.UNKNOWN;
        this.stage = stage != null ? stage : Stage.UNKNOWN;
        this.caseId = caseId;
    }

    /**
     * Creates a builder for constructing PatternExecutionException instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the pattern identifier that failed.
     *
     * @return the pattern ID, or null if not specified
     */
    public String getPatternId() {
        return patternId;
    }

    /**
     * Get the error code.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Get the execution stage where the failure occurred.
     *
     * @return the stage
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * Get the case identifier.
     *
     * @return the case ID, or null if not specified
     */
    public String getCaseId() {
        return caseId;
    }

    /**
     * Check if this exception has a pattern ID.
     *
     * @return true if pattern ID is set
     */
    public boolean hasPatternId() {
        return patternId != null && !patternId.isBlank();
    }

    /**
     * Check if this exception has a case ID.
     *
     * @return true if case ID is set
     */
    public boolean hasCaseId() {
        return caseId != null && !caseId.isBlank();
    }

    /**
     * Check if this exception represents a timeout.
     *
     * @return true if error code is TIMEOUT_ERROR
     */
    public boolean isTimeout() {
        return errorCode == ErrorCode.TIMEOUT_ERROR;
    }

    /**
     * Check if this exception represents a cancellation.
     *
     * @return true if error code is CANCELLED
     */
    public boolean isCancelled() {
        return errorCode == ErrorCode.CANCELLED;
    }

    /**
     * Check if this exception is recoverable.
     *
     * <p>Recoverable errors may be retried with different parameters.</p>
     *
     * @return true if the error may be recoverable
     */
    public boolean isRecoverable() {
        return errorCode == ErrorCode.TIMEOUT_ERROR ||
               errorCode == ErrorCode.WORKITEM_ERROR ||
               errorCode == ErrorCode.DECISION_ERROR;
    }

    /**
     * Get a formatted error summary.
     *
     * @return a human-readable error summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();

        sb.append(errorCode.getDescription());

        if (hasPatternId()) {
            sb.append(" in pattern '").append(patternId).append("'");
        }

        if (hasCaseId()) {
            sb.append(" for case '").append(caseId).append("'");
        }

        sb.append(" at stage ").append(stage);

        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PatternExecutionException{");
        sb.append("errorCode=").append(errorCode);
        sb.append(", stage=").append(stage);

        if (hasPatternId()) {
            sb.append(", patternId='").append(patternId).append("'");
        }

        if (hasCaseId()) {
            sb.append(", caseId='").append(caseId).append("'");
        }

        sb.append(", message='").append(getMessage()).append("'");

        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getClass().getSimpleName());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Builder for constructing PatternExecutionException instances.
     */
    public static class Builder {
        private String message;
        private Throwable cause;
        private String patternId;
        private ErrorCode errorCode;
        private Stage stage;
        private String caseId;

        /**
         * Set the error message.
         *
         * @param message the error message
         * @return this builder
         */
        public Builder message(String message) {
            this.message = message;
            return this;
        }

        /**
         * Set the underlying cause.
         *
         * @param cause the cause
         * @return this builder
         */
        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        /**
         * Set the pattern identifier.
         *
         * @param patternId the pattern ID
         * @return this builder
         */
        public Builder patternId(String patternId) {
            this.patternId = patternId;
            return this;
        }

        /**
         * Set the error code.
         *
         * @param errorCode the error code
         * @return this builder
         */
        public Builder errorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        /**
         * Set the execution stage.
         *
         * @param stage the stage
         * @return this builder
         */
        public Builder stage(Stage stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Set the case identifier.
         *
         * @param caseId the case ID
         * @return this builder
         */
        public Builder caseId(String caseId) {
            this.caseId = caseId;
            return this;
        }

        /**
         * Build the exception.
         *
         * @return a new PatternExecutionException
         */
        public PatternExecutionException build() {
            return new PatternExecutionException(
                Objects.requireNonNullElse(message, "Pattern execution failed"),
                cause,
                patternId,
                errorCode,
                stage,
                caseId
            );
        }
    }

    // ==================== Factory Methods ====================

    /**
     * Create a syntax error exception.
     *
     * @param message the error message
     * @param patternId the pattern ID
     * @param cause the underlying cause
     * @return a new exception
     */
    public static PatternExecutionException syntaxError(String message, String patternId, Throwable cause) {
        return builder()
            .message(message)
            .patternId(patternId)
            .errorCode(ErrorCode.SYNTAX_ERROR)
            .stage(Stage.PARSE)
            .cause(cause)
            .build();
    }

    /**
     * Create a validation error exception.
     *
     * @param message the error message
     * @param patternId the pattern ID
     * @return a new exception
     */
    public static PatternExecutionException validationError(String message, String patternId) {
        return builder()
            .message(message)
            .patternId(patternId)
            .errorCode(ErrorCode.VALIDATION_ERROR)
            .stage(Stage.VALIDATE)
            .build();
    }

    /**
     * Create a timeout exception.
     *
     * @param patternId the pattern ID
     * @param caseId the case ID
     * @param timeoutMs the timeout in milliseconds
     * @return a new exception
     */
    public static PatternExecutionException timeout(String patternId, String caseId, long timeoutMs) {
        return builder()
            .message("Execution timed out after " + timeoutMs + "ms")
            .patternId(patternId)
            .caseId(caseId)
            .errorCode(ErrorCode.TIMEOUT_ERROR)
            .stage(Stage.UNKNOWN)
            .build();
    }

    /**
     * Create a cancellation exception.
     *
     * @param patternId the pattern ID
     * @param caseId the case ID
     * @param reason the cancellation reason
     * @return a new exception
     */
    public static PatternExecutionException cancelled(String patternId, String caseId, String reason) {
        return builder()
            .message(Objects.requireNonNullElse(reason, "Execution was cancelled"))
            .patternId(patternId)
            .caseId(caseId)
            .errorCode(ErrorCode.CANCELLED)
            .stage(Stage.UNKNOWN)
            .build();
    }

    /**
     * Create a work item error exception.
     *
     * @param taskId the task ID
     * @param patternId the pattern ID
     * @param cause the underlying cause
     * @return a new exception
     */
    public static PatternExecutionException workItemError(String taskId, String patternId, Throwable cause) {
        return builder()
            .message("Work item error for task: " + taskId)
            .patternId(patternId)
            .errorCode(ErrorCode.WORKITEM_ERROR)
            .stage(Stage.WORK_ITEM)
            .cause(cause)
            .build();
    }

    /**
     * Create a decision error exception.
     *
     * @param taskId the task ID
     * @param patternId the pattern ID
     * @param reason the decision error reason
     * @return a new exception
     */
    public static PatternExecutionException decisionError(String taskId, String patternId, String reason) {
        return builder()
            .message("Decision error for task " + taskId + ": " + reason)
            .patternId(patternId)
            .errorCode(ErrorCode.DECISION_ERROR)
            .stage(Stage.DECISION)
            .build();
    }

    /**
     * Create a launch error exception.
     *
     * @param patternId the pattern ID
     * @param cause the underlying cause
     * @return a new exception
     */
    public static PatternExecutionException launchError(String patternId, Throwable cause) {
        return builder()
            .message("Failed to launch case for pattern: " + patternId)
            .patternId(patternId)
            .errorCode(ErrorCode.LAUNCH_ERROR)
            .stage(Stage.INIT)
            .cause(cause)
            .build();
    }
}
