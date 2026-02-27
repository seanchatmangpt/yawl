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

package org.yawlfoundation.yawl.graaljs;

/**
 * Exception thrown when JavaScript execution fails within the GraalJS integration layer.
 *
 * <p>Wraps {@link org.graalvm.polyglot.PolyglotException} and configuration errors
 * from the GraalVM JavaScript runtime, providing actionable error messages for YAWL
 * workflow developers.</p>
 *
 * <h2>Common causes</h2>
 * <ul>
 *   <li><strong>GraalJS not available</strong>: GraalVM JDK 24.1+ required at runtime.
 *       JavaScript language support not found on the classpath.</li>
 *   <li><strong>JavaScript syntax error</strong>: Invalid JavaScript source passed to eval().</li>
 *   <li><strong>JavaScript runtime error</strong>: JavaScript exception raised during execution.</li>
 *   <li><strong>Type conversion error</strong>: JavaScript value cannot be mapped to a Java type.</li>
 *   <li><strong>Sandbox violation</strong>: JavaScript code attempted a disallowed operation.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class JavaScriptException extends RuntimeException {

    /** Categorises the failure source for programmatic handling. */
    public enum ErrorKind {
        /** GraalVM / GraalJS runtime is not available or not configured. */
        RUNTIME_NOT_AVAILABLE,
        /** JavaScript source contains a syntax error. */
        SYNTAX_ERROR,
        /** JavaScript code raised an exception during execution. */
        RUNTIME_ERROR,
        /** A JavaScript value could not be marshalled to the requested Java type. */
        TYPE_CONVERSION_ERROR,
        /** JavaScript code attempted a file-system, network, or OS operation blocked by the sandbox. */
        SANDBOX_VIOLATION,
        /** Context pool is exhausted or a context failed to initialise. */
        CONTEXT_ERROR,
        /** JavaScript module could not be loaded or imported. */
        MODULE_LOAD_ERROR
    }

    private final ErrorKind errorKind;

    /**
     * Constructs a JavaScriptException with a message and error kind.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     */
    public JavaScriptException(String message, ErrorKind errorKind) {
        super(message);
        this.errorKind = errorKind;
    }

    /**
     * Constructs a JavaScriptException wrapping a cause.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     * @param cause  the underlying exception
     */
    public JavaScriptException(String message, ErrorKind errorKind, Throwable cause) {
        super(message, cause);
        this.errorKind = errorKind;
    }

    /**
     * Returns the failure category.
     *
     * @return the error kind; never null
     */
    public ErrorKind getErrorKind() {
        return errorKind;
    }

    @Override
    public String toString() {
        return "JavaScriptException[" + errorKind + "]: " + getMessage();
    }
}
