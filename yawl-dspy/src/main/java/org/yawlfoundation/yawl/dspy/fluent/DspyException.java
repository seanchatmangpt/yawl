/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.fluent;

import org.jspecify.annotations.Nullable;

/**
 * Exception thrown by the DSPy fluent API.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyException extends RuntimeException {

    private final @Nullable String signatureId;
    private final @Nullable ErrorKind kind;

    /**
     * Error kind for DSPy operations.
     */
    public enum ErrorKind {
        /**
         * Invalid input provided.
         */
        INVALID_INPUT,

        /**
         * LLM call failed.
         */
        LLM_ERROR,

        /**
         * Output parsing failed.
         */
        PARSE_ERROR,

        /**
         * Optimization/compilation failed.
         */
        OPTIMIZATION_ERROR,

        /**
         * Configuration error.
         */
        CONFIG_ERROR,

        /**
         * General execution error.
         */
        EXECUTION_ERROR
    }

    /**
     * Create a new DSPy exception.
     */
    public DspyException(String message) {
        super(message);
        this.signatureId = null;
        this.kind = null;
    }

    /**
     * Create a new DSPy exception with cause.
     */
    public DspyException(String message, @Nullable Throwable cause) {
        super(message, cause);
        this.signatureId = null;
        this.kind = null;
    }

    /**
     * Create a new DSPy exception with signature context.
     */
    public DspyException(@Nullable String signatureId, ErrorKind kind, String message) {
        super(message);
        this.signatureId = signatureId;
        this.kind = kind;
    }

    /**
     * Create a new DSPy exception with full context.
     */
    public DspyException(@Nullable String signatureId, ErrorKind kind, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.signatureId = signatureId;
        this.kind = kind;
    }

    /**
     * Signature ID where the error occurred (if available).
     */
    public @Nullable String signatureId() {
        return signatureId;
    }

    /**
     * Error kind (if available).
     */
    public @Nullable ErrorKind kind() {
        return kind;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DspyException");
        if (kind != null) {
            sb.append("[").append(kind).append("]");
        }
        if (signatureId != null) {
            sb.append("(").append(signatureId).append(")");
        }
        sb.append(": ").append(getMessage());
        return sb.toString();
    }
}
