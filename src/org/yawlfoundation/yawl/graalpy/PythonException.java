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

package org.yawlfoundation.yawl.graalpy;

/**
 * Exception thrown when Python execution fails within the GraalPy integration layer.
 *
 * <p>Wraps {@link org.graalvm.polyglot.PolyglotException} and configuration errors
 * from the GraalPy runtime, providing actionable error messages for YAWL workflow
 * developers.</p>
 *
 * <h2>Common causes</h2>
 * <ul>
 *   <li><strong>GraalPy not available</strong>: GraalVM JDK 24.1+ required at runtime.
 *       Python language support not found on the classpath.</li>
 *   <li><strong>Python syntax error</strong>: Invalid Python source passed to eval().</li>
 *   <li><strong>Python runtime error</strong>: Python exception raised during execution.</li>
 *   <li><strong>Type conversion error</strong>: Python value cannot be mapped to a Java type.</li>
 *   <li><strong>Sandbox violation</strong>: Python code attempted a disallowed operation.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonException extends RuntimeException {

    /** Categorises the failure source for programmatic handling. */
    public enum ErrorKind {
        /** GraalVM / GraalPy runtime is not available or not configured. */
        RUNTIME_NOT_AVAILABLE,
        /** Python source contains a syntax error. */
        SYNTAX_ERROR,
        /** Python code raised an exception during execution. */
        RUNTIME_ERROR,
        /** A Python value could not be marshalled to the requested Java type. */
        TYPE_CONVERSION_ERROR,
        /** Python code attempted a file-system, network, or OS operation blocked by the sandbox. */
        SANDBOX_VIOLATION,
        /** Context pool is exhausted or a context failed to initialise. */
        CONTEXT_ERROR,
        /** Python virtual environment could not be created or package install failed. */
        VENV_ERROR,
        /** Python interface generation from a .pyi stub failed. */
        INTERFACE_GENERATION_ERROR
    }

    private final ErrorKind errorKind;

    /**
     * Constructs a PythonException with a message and error kind.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     */
    public PythonException(String message, ErrorKind errorKind) {
        super(message);
        this.errorKind = errorKind;
    }

    /**
     * Constructs a PythonException wrapping a cause.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     * @param cause  the underlying exception
     */
    public PythonException(String message, ErrorKind errorKind, Throwable cause) {
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
        return "PythonException[" + errorKind + "]: " + getMessage();
    }
}
