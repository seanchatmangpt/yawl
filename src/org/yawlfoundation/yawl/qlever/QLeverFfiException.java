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

package org.yawlfoundation.yawl.qlever;

/**
 * Runtime exception thrown when QLever FFI (Foreign Function Interface) operations fail.
 *
 * <p>This exception wraps native errors from the QLever C++ engine, including:</p>
 * <ul>
 *   <li>Native library loading failures</li>
 *   <li>Memory allocation errors in native code</li>
 *   <li>Index loading failures</li>
 *   <li>Query execution errors in the native layer</li>
 *   <li>Invalid arguments passed to native functions</li>
 *   <li>Runtime errors in native code</li>
 *   <li>Out of memory errors in the native heap</li>
 * </ul>
 *
 * <p><strong>Error Code System:</strong></p>
 * <ul>
 *   <li><code>1</code> - Invalid argument error</li>
 *   <li><code>2</code> - Runtime error in native code</li>
 *   <li><code>3</code> - Out of memory error</li>
 *   <li><code>101</code> - Index loading failure</li>
 *   <li><code>102</code> - Native library loading failure</li>
 *   <li><code>103</code> - Query execution failure</li>
 *   <li><code>104</code> - FFI initialization failure</li>
 *   <li><code>105</code> - Memory allocation failure</li>
 *   <li><code>99-100</code> - Unknown errors</li>
 * </ul>
 *
 * <p>For SPARQL-level errors (malformed queries, semantic errors), use
 * {@link org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException}
 * instead.</p>
 *
 * <p><strong>Factory Methods:</strong></p>
 * <p>Use factory methods for common error types:</p>
 * <ul>
 *   <li>{@link #libraryLoadFailed(String, Throwable)} - For native library loading failures</li>
 *   <li>{@link #indexLoadFailed(String, Throwable)} - For index loading failures</li>
 *   <li>{@link #queryFailed(String, String)} - For query execution failures</li>
 *   <li>{@link #invalidArgument(String, Throwable)} - For invalid argument errors</li>
 *   <li>{@link #runtimeError(String, Throwable)} - For runtime errors</li>
 *   <li>{@link #outOfMemory(String, Throwable)} - For out of memory errors</li>
 *   <li>{@link #unknownError(String, Throwable)} - For unknown errors</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class QLeverFfiException extends RuntimeException {

    private final int errorCode;
    private final String errorMessage;

    /**
     * Creates a new FFI exception with the specified message.
     *
     * @param message the error message describing the FFI failure
     */
    public QLeverFfiException(String message) {
        this(0, message, null, message);
    }

    /**
     * Creates a new FFI exception with the specified message and cause.
     *
     * @param message the error message describing the FFI failure
     * @param cause   the underlying cause (typically from native code or Linker)
     */
    public QLeverFfiException(String message, Throwable cause) {
        this(0, message, cause, message);
    }

    /**
     * Creates a new FFI exception with the specified error code and message.
     *
     * @param errorCode the numeric error code from native code
     * @param message the error message describing the FFI failure
     */
    public QLeverFfiException(int errorCode, String message) {
        this(errorCode, message, null, message);
    }

    /**
     * Creates a new FFI exception with the specified error code, message, and cause.
     *
     * @param errorCode the numeric error code from native code
     * @param message the error message describing the FFI failure
     * @param cause   the underlying cause (typically from native code or Linker)
     */
    private QLeverFfiException(int errorCode, String message, Throwable cause, String fullMessage) {
        super(fullMessage, cause);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }

    /**
     * Creates a new FFI exception from a status code and message.
     *
     * @param code the numeric error code
     * @param message the error message describing the FFI failure
     * @return a new QLeverFfiException with the specified status and message
     */
    public static QLeverFfiException fromStatus(int code, String message) {
        return switch (code) {
            case 1 -> invalidArgument(message);
            case 2 -> runtimeError(message);
            case 3 -> outOfMemory(message);
            case 101 -> indexLoadFailed("unknown", new Throwable(message));
            case 102 -> libraryLoadFailed("unknown", new Throwable(message));
            case 103 -> queryFailed("unknown", message);
            case 104 -> initializationFailed(new Throwable(message));
            case 105 -> memoryAllocationFailed(-1, new Throwable(message));
            case 99, 100 -> unknownError(message);
            default -> unknownError(message);
        };
    }

    /**
     * Creates a new FFI exception for invalid argument errors.
     *
     * @param message the error message describing the invalid argument
     * @return a new QLeverFfiException for invalid argument errors
     */
    public static QLeverFfiException invalidArgument(String message) {
        return new QLeverFfiException(1, message, null, "QLever FFI invalid argument: " + message);
    }

    /**
     * Creates a new FFI exception for invalid argument errors with cause.
     *
     * @param message the error message describing the invalid argument
     * @param cause   the underlying cause of the invalid argument
     * @return a new QLeverFfiException for invalid argument errors
     */
    public static QLeverFfiException invalidArgument(String message, Throwable cause) {
        return new QLeverFfiException(1, message, cause, "QLever FFI invalid argument: " + message);
    }

    /**
     * Creates a new FFI exception for runtime errors.
     *
     * @param message the error message describing the runtime error
     * @return a new QLeverFfiException for runtime errors
     */
    public static QLeverFfiException runtimeError(String message) {
        return new QLeverFfiException(2, message, null, "QLever FFI runtime error: " + message);
    }

    /**
     * Creates a new FFI exception for runtime errors with cause.
     *
     * @param message the error message describing the runtime error
     * @param cause   the underlying cause of the runtime error
     * @return a new QLeverFfiException for runtime errors
     */
    public static QLeverFfiException runtimeError(String message, Throwable cause) {
        return new QLeverFfiException(2, message, cause, "QLever FFI runtime error: " + message);
    }

    /**
     * Creates a new FFI exception for out of memory errors.
     *
     * @param message the error message describing the out of memory error
     * @return a new QLeverFfiException for out of memory errors
     */
    public static QLeverFfiException outOfMemory(String message) {
        return new QLeverFfiException(3, message, null, "QLever FFI out of memory: " + message);
    }

    /**
     * Creates a new FFI exception for out of memory errors with cause.
     *
     * @param message the error message describing the out of memory error
     * @param cause   the underlying cause of the out of memory error
     * @return a new QLeverFfiException for out of memory errors
     */
    public static QLeverFfiException outOfMemory(String message, Throwable cause) {
        return new QLeverFfiException(3, message, cause, "QLever FFI out of memory: " + message);
    }

    /**
     * Creates a new FFI exception for unknown errors.
     *
     * @param message the error message describing the unknown error
     * @return a new QLeverFfiException for unknown errors
     */
    public static QLeverFfiException unknownError(String message) {
        return new QLeverFfiException(99, message, null, "QLever FFI unknown error: " + message);
    }

    /**
     * Creates a new FFI exception for unknown errors with cause.
     *
     * @param message the error message describing the unknown error
     * @param cause   the underlying cause of the unknown error
     * @return a new QLeverFfiException for unknown errors
     */
    public static QLeverFfiException unknownError(String message, Throwable cause) {
        return new QLeverFfiException(99, message, cause, "QLever FFI unknown error: " + message);
    }

    /**
     * Creates a new FFI exception for index loading failures.
     *
     * @param indexPath the path to the index that failed to load
     * @param cause     the underlying cause
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException indexLoadFailed(String indexPath, Throwable cause) {
        String message = "Failed to load QLever index from: " + indexPath;
        return new QLeverFfiException(101, message, cause, "QLever FFI index load failed: " + message);
    }

    /**
     * Creates a new FFI exception for native library loading failures.
     *
     * @param libraryName the name of the library that failed to load
     * @param cause       the underlying UnsatisfiedLinkError
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException libraryLoadFailed(String libraryName, Throwable cause) {
        String message = "Failed to load native library '" + libraryName + "'. " +
                         "Ensure libqlever_ffi.so/dylib/dll is in java.library.path";
        return new QLeverFfiException(102, message, cause, "QLever FFI library load failed: " + message);
    }

    /**
     * Creates a new FFI exception for query execution failures.
     *
     * @param query     the SPARQL query that failed (truncated if too long)
     * @param nativeError the error from the native layer
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException queryFailed(String query, String nativeError) {
        String truncatedQuery = query.length() > 100
            ? query.substring(0, 100) + "..."
            : query;
        String message = "QLever native query execution failed for query [" + truncatedQuery + "]: " + nativeError;
        return new QLeverFfiException(103, message, null, "QLever FFI query execution failed: " + message);
    }

    /**
     * Creates a new FFI exception for query execution failures with cause.
     *
     * @param query     the SPARQL query that failed (truncated if too long)
     * @param nativeError the error from the native layer
     * @param cause     the underlying cause of the query failure
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException queryFailed(String query, String nativeError, Throwable cause) {
        String truncatedQuery = query.length() > 100
            ? query.substring(0, 100) + "..."
            : query;
        String message = "QLever native query execution failed for query [" + truncatedQuery + "]: " + nativeError;
        return new QLeverFfiException(103, message, cause, "QLever FFI query execution failed: " + message);
    }

    /**
     * Creates a new FFI exception for FFI initialization failures.
     *
     * @param cause the underlying cause during FFI initialization
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException initializationFailed(Throwable cause) {
        String message = "QLever FFI initialization failed";
        return new QLeverFfiException(104, message, cause, "QLever FFI initialization failed");
    }

    /**
     * Creates a new FFI exception for memory allocation failures.
     *
     * @param allocationSize the requested allocation size (if known)
     * @param cause           the underlying cause of the allocation failure
     * @return a new QLeverFfiException with context-appropriate message
     */
    public static QLeverFfiException memoryAllocationFailed(long allocationSize, Throwable cause) {
        String message = allocationSize > 0
            ? "Failed to allocate " + allocationSize + " bytes in native memory"
            : "Failed to allocate memory in native code";
        return new QLeverFfiException(105, message, cause, "QLever FFI memory allocation failed: " + message);
    }

    /**
     * Returns the numeric error code from the native code.
     *
     * @return the error code, or 0 if not applicable
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the original error message from the native code.
     *
     * @return the error message, or null if not applicable
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        if (errorCode != 0) {
            return super.toString() + " [error code: " + errorCode + "]";
        }
        return super.toString();
    }
}
