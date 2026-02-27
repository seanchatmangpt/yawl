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

package org.yawlfoundation.yawl.graalwasm;

/**
 * Exception thrown when WASM execution fails within the GraalWasm integration layer.
 *
 * <p>Wraps {@link org.graalvm.polyglot.PolyglotException} and configuration errors
 * from the GraalWasm runtime, providing actionable error messages for YAWL workflow
 * developers.</p>
 *
 * <h2>Common causes</h2>
 * <ul>
 *   <li><strong>GraalWasm not available</strong>: GraalVM JDK 24.1+ required at runtime.
 *       WASM language support not found on the classpath.</li>
 *   <li><strong>WASM module load error</strong>: Invalid WASM binary or classpath resource not found.</li>
 *   <li><strong>WASM instantiation error</strong>: Memory or import requirements could not be satisfied.</li>
 *   <li><strong>WASM function execution error</strong>: Function trap or timeout during execution.</li>
 *   <li><strong>Type conversion error</strong>: WASM value cannot be mapped to a Java type.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WasmException extends RuntimeException {

    /** Categorises the failure source for programmatic handling. */
    public enum ErrorKind {
        /** GraalVM / GraalWasm runtime is not available or not configured. */
        RUNTIME_NOT_AVAILABLE,
        /** WASM module binary could not be loaded or is invalid. */
        MODULE_LOAD_ERROR,
        /** WASM module could not be instantiated or imports are missing. */
        INSTANTIATION_ERROR,
        /** A WASM exported function was not found. */
        FUNCTION_NOT_FOUND,
        /** WASM function execution failed (trap, out of bounds, etc.). */
        EXECUTION_ERROR,
        /** A WASM value could not be marshalled to the requested Java type. */
        TYPE_CONVERSION_ERROR,
        /** WASI system call failed or was blocked by sandbox. */
        WASI_ERROR
    }

    private final ErrorKind errorKind;

    /**
     * Constructs a WasmException with a message and error kind.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     */
    public WasmException(String message, ErrorKind errorKind) {
        super(message);
        this.errorKind = errorKind;
    }

    /**
     * Constructs a WasmException wrapping a cause.
     *
     * @param message  human-readable description of the failure
     * @param errorKind  the failure category
     * @param cause  the underlying exception
     */
    public WasmException(String message, ErrorKind errorKind, Throwable cause) {
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
        return "WasmException[" + errorKind + "]: " + getMessage();
    }
}
