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

package org.yawlfoundation.yawl.datamodelling;

/**
 * Exception thrown by {@link DataModellingBridge} when a WASM operation fails.
 *
 * <p>Errors may originate from:</p>
 * <ul>
 *   <li>WASM module load failures (missing resources, SIGSEGV on unsupported kernels)</li>
 *   <li>WASM function execution failures (invalid input, unsupported format)</li>
 *   <li>JSON serialization failures (malformed WASM output)</li>
 * </ul>
 *
 * <p>The {@link ErrorKind} enum classifies each error for programmatic handling.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DataModellingException extends RuntimeException {

    private final ErrorKind kind;

    /**
     * Categories of errors raised by DataModellingBridge.
     */
    public enum ErrorKind {
        /** WASM binary or JS glue resource not found on classpath. */
        MODULE_LOAD_ERROR,
        /** WASM function execution trapped (invalid input, unsupported format). */
        EXECUTION_ERROR,
        /** Bridge is closed; operation cannot proceed. */
        CLOSED_ERROR,
        /** JSON parsing/deserialization failure. */
        JSON_PARSE_ERROR,
        /** JSON serialization/encoding failure. */
        JSON_SERIALIZE_ERROR
    }

    /**
     * Constructs an exception with the given message and kind.
     *
     * @param message  the error message; must not be null
     * @param kind     the error kind; must not be null
     */
    public DataModellingException(String message, ErrorKind kind) {
        super(message);
        this.kind = kind;
    }

    /**
     * Constructs an exception with the given message, kind, and cause.
     *
     * @param message  the error message; must not be null
     * @param kind     the error kind; must not be null
     * @param cause    the underlying cause; may be null
     */
    public DataModellingException(String message, ErrorKind kind, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * Returns the error kind for programmatic handling.
     *
     * @return the error kind; never null
     */
    public ErrorKind getKind() {
        return kind;
    }
}
