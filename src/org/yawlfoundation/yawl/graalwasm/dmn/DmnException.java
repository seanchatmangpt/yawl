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

package org.yawlfoundation.yawl.graalwasm.dmn;

/**
 * Runtime exception for DMN model parsing and evaluation failures.
 *
 * <p>Categorises failures into eight {@link ErrorKind} values to support
 * structured error handling without catch-and-guess patterns.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try {
 *     DmnDecisionResult result = bridge.evaluateDecision(model, "eligibility", ctx);
 * } catch (DmnException e) {
 *     switch (e.getErrorKind()) {
 *         case DECISION_NOT_FOUND  -> log.warn("No decision named: {}", decisionId);
 *         case HIT_POLICY_VIOLATION -> log.error("UNIQUE hit policy violated");
 *         default -> throw e;
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see DmnWasmBridge
 */
public final class DmnException extends RuntimeException {

    /**
     * Categorises the failure reason.
     */
    public enum ErrorKind {

        /**
         * The DMN XML could not be parsed (malformed XML, unknown namespace, etc.).
         */
        MODEL_PARSE_ERROR("DMN XML model could not be parsed"),

        /**
         * A requested decision ID was not found in the model.
         */
        DECISION_NOT_FOUND("Requested decision not found in model"),

        /**
         * A requested decision table ID was not found in the model.
         */
        TABLE_NOT_FOUND("Requested decision table not found in model"),

        /**
         * General evaluation failure (unexpected state during rule matching).
         */
        EVALUATION_ERROR("Decision evaluation failed"),

        /**
         * An input value is null where a non-null value is required, or
         * does not conform to the declared input type.
         */
        INVALID_INPUT("Input value is invalid or null"),

        /**
         * An input or output value cannot be coerced to the declared type.
         */
        TYPE_MISMATCH("Input or output type does not match declared typeRef"),

        /**
         * A FEEL expression could not be parsed or evaluated.
         */
        FEEL_EXPRESSION_ERROR("FEEL expression evaluation failed"),

        /**
         * The UNIQUE hit policy was violated (multiple rules matched).
         */
        HIT_POLICY_VIOLATION("Hit policy violation: UNIQUE policy with multiple matching rules"),

        /**
         * The FEEL engine WASM binary could not be loaded or instantiated.
         */
        WASM_LOAD_ERROR("FEEL engine WASM could not be loaded");

        private final String description;

        ErrorKind(String description) {
            this.description = description;
        }

        /**
         * Returns the human-readable description of this error kind.
         *
         * @return the description; never null
         */
        public String getDescription() {
            return description;
        }
    }

    private final ErrorKind errorKind;

    /**
     * Constructs a DmnException with a message and error kind.
     *
     * @param message    the detail message; must not be null
     * @param errorKind  the error category; must not be null
     */
    public DmnException(String message, ErrorKind errorKind) {
        super(message);
        this.errorKind = errorKind;
    }

    /**
     * Constructs a DmnException with a message, error kind, and cause.
     *
     * @param message    the detail message; must not be null
     * @param errorKind  the error category; must not be null
     * @param cause      the underlying cause; may be null
     */
    public DmnException(String message, ErrorKind errorKind, Throwable cause) {
        super(message, cause);
        this.errorKind = errorKind;
    }

    /**
     * Returns the error category.
     *
     * @return the error kind; never null
     */
    public ErrorKind getErrorKind() {
        return errorKind;
    }
}
