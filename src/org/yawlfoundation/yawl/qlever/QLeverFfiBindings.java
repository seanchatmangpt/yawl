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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Panama FFM (Foreign Function & Memory) bindings for QLever native library.
 *
 * <p>This class provides the Java-to-C bridge using the Hourglass pattern:
 * QLever C++ → C façade (extern "C") → Java FFM. All native functions are
 * declared with C linkage to avoid name mangling issues.</p>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 * @see QLeverEmbeddedSparqlEngine
 */
public final class QLeverFfiBindings implements AutoCloseable {

    /** Native library name (without platform-specific prefix/suffix) */
    private static final String LIBRARY_NAME = "qlever_ffi";

    /** Maximum line length for safe string reinterpreting (64KB) */
    private static final long MAX_LINE_LENGTH = 64 * 1024;

    /** Maximum error message length for safe reinterpreting (16KB) */
    private static final long MAX_ERROR_LENGTH = 16 * 1024;

    // Static initialization loads native library once
    static {
        try {
            System.loadLibrary(LIBRARY_NAME);
        } catch (UnsatisfiedLinkError e) {
            throw QLeverFfiException.libraryLoadFailed(LIBRARY_NAME, e);
        }
    }

    /** Native linker for FFM downcalls */
    private static final Linker LINKER = Linker.nativeLinker();

    /** Symbol lookup from loaded library */
    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    /** Shared arena for long-lived native resources */
    private final Arena arena;

    // ========================================================================
    // Function Descriptors (define C function signatures)
    // ========================================================================

    private static final FunctionDescriptor DESC_INDEX_CREATE = FunctionDescriptor.of(
        ValueLayout.ADDRESS,    // returns QLeverIndex*
        ValueLayout.ADDRESS     // const char* index_path
    );

    private static final FunctionDescriptor DESC_INDEX_DESTROY = FunctionDescriptor.ofVoid(
        ValueLayout.ADDRESS     // QLeverIndex* index
    );

    private static final FunctionDescriptor DESC_INDEX_IS_LOADED = FunctionDescriptor.of(
        ValueLayout.JAVA_BOOLEAN,  // returns bool
        ValueLayout.ADDRESS        // const QLeverIndex* index
    );

    private static final FunctionDescriptor DESC_INDEX_TRIPLE_COUNT = FunctionDescriptor.of(
        ValueLayout.JAVA_LONG,  // returns size_t
        ValueLayout.ADDRESS     // const QLeverIndex* index
    );

    private static final FunctionDescriptor DESC_QUERY_EXEC = FunctionDescriptor.of(
        ValueLayout.ADDRESS,    // returns QLeverResult*
        ValueLayout.ADDRESS,    // QLeverIndex* index
        ValueLayout.ADDRESS,    // const char* sparql_query
        ValueLayout.ADDRESS     // const char* accept_header
    );

    private static final FunctionDescriptor DESC_RESULT_HAS_NEXT = FunctionDescriptor.of(
        ValueLayout.JAVA_BOOLEAN,  // returns bool
        ValueLayout.ADDRESS        // const QLeverResult* result
    );

    private static final FunctionDescriptor DESC_RESULT_NEXT = FunctionDescriptor.of(
        ValueLayout.ADDRESS,   // returns const char*
        ValueLayout.ADDRESS    // QLeverResult* result
    );

    private static final FunctionDescriptor DESC_RESULT_DESTROY = FunctionDescriptor.ofVoid(
        ValueLayout.ADDRESS    // QLeverResult* result
    );

    private static final FunctionDescriptor DESC_RESULT_ERROR = FunctionDescriptor.of(
        ValueLayout.ADDRESS,   // returns const char*
        ValueLayout.ADDRESS    // const QLeverResult* result
    );

    private static final FunctionDescriptor DESC_RESULT_STATUS = FunctionDescriptor.of(
        ValueLayout.JAVA_INT,  // returns int (HTTP status code)
        ValueLayout.ADDRESS    // const QLeverResult* result
    );

    // ========================================================================
    // Method Handles (downcall handles for native functions)
    // ========================================================================

    private final MethodHandle mhIndexCreate;
    private final MethodHandle mhIndexDestroy;
    private final MethodHandle mhIndexIsLoaded;
    private final MethodHandle mhIndexTripleCount;
    private final MethodHandle mhQueryExec;
    private final MethodHandle mhResultHasNext;
    private final MethodHandle mhResultNext;
    private final MethodHandle mhResultDestroy;
    private final MethodHandle mhResultError;
    private final MethodHandle mhResultStatus;

    /**
     * Creates new FFI bindings and resolves all native method handles.
     *
     * @throws QLeverFfiException if any native symbol cannot be resolved
     */
    public QLeverFfiBindings() {
        this.arena = Arena.ofShared();

        try {
            this.mhIndexCreate = lookupDowncall("qlever_index_create", DESC_INDEX_CREATE);
            this.mhIndexDestroy = lookupDowncall("qlever_index_destroy", DESC_INDEX_DESTROY);
            this.mhIndexIsLoaded = lookupDowncall("qlever_index_is_loaded", DESC_INDEX_IS_LOADED);
            this.mhIndexTripleCount = lookupDowncall("qlever_index_triple_count", DESC_INDEX_TRIPLE_COUNT);
            this.mhQueryExec = lookupDowncall("qlever_query_exec", DESC_QUERY_EXEC);
            this.mhResultHasNext = lookupDowncall("qlever_result_has_next", DESC_RESULT_HAS_NEXT);
            this.mhResultNext = lookupDowncall("qlever_result_next", DESC_RESULT_NEXT);
            this.mhResultDestroy = lookupDowncall("qlever_result_destroy", DESC_RESULT_DESTROY);
            this.mhResultError = lookupDowncall("qlever_result_error", DESC_RESULT_ERROR);
            this.mhResultStatus = lookupDowncall("qlever_result_status", DESC_RESULT_STATUS);
        } catch (QLeverFfiException e) {
            close();  // Clean up arena on failure
            throw e;
        }
    }

    /**
     * Looks up a native symbol and creates a downcall method handle.
     */
    private MethodHandle lookupDowncall(String name, FunctionDescriptor descriptor) {
        return LOOKUP.find(name)
            .map(addr -> LINKER.downcallHandle(addr, descriptor))
            .orElseThrow(() -> new QLeverFfiException(
                "Native symbol '" + name + "' not found in lib" + LIBRARY_NAME
            ));
    }

    // ========================================================================
    // Index Lifecycle Operations
    // ========================================================================

    
    /**
     * Creates a new QLever index handle and returns status.
     *
     * @param indexPath path to the QLever index directory
     * @return QLeverStatus containing index handle or error information
     * @throws QLeverFfiException if FFI call fails
     * @throws NullPointerException if indexPath is null
     */
    public QLeverStatus indexCreate(String indexPath) {
        Objects.requireNonNull(indexPath, "indexPath must not be null");

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment pathSeg = callArena.allocateFrom(indexPath, StandardCharsets.UTF_8);
            MemorySegment indexHandle = (MemorySegment) mhIndexCreate.invokeExact(pathSeg);

            return new QLeverStatus(indexHandle);
        } catch (Throwable t) {
            return new QLeverStatus("Failed to create QLever index: " + indexPath, 500);
        }
    }

    /**
     * Destroys a QLever index handle and releases native resources.
     *
     * @param index the index handle to destroy (safe to pass null or NULL)
     */
    public void indexDestroy(MemorySegment index) {
        if (index != null && !index.equals(MemorySegment.NULL)) {
            try {
                mhIndexDestroy.invokeExact(index);
            } catch (Throwable t) {
                throw new QLeverFfiException("Failed to destroy QLever index", t);
            }
        }
    }

    /**
     * Checks if the index is successfully loaded and ready for queries.
     *
     * @param index the index handle
     * @return true if index is operational, false otherwise
     */
    public boolean indexIsLoaded(MemorySegment index) {
        if (index == null || index.equals(MemorySegment.NULL)) {
            return false;
        }
        try {
            return (boolean) mhIndexIsLoaded.invokeExact(index);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Returns the number of triples in the index.
     *
     * @param index the index handle
     * @return triple count, or 0 if index is invalid
     */
    public long indexTripleCount(MemorySegment index) {
        if (index == null || index.equals(MemorySegment.NULL)) {
            return 0;
        }
        try {
            return (long) mhIndexTripleCount.invokeExact(index);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ========================================================================
    // Query Execution Operations
    // ========================================================================

    
    /**
     * Executes a SPARQL query and returns a status object containing the result.
     *
     * <p>This method returns a QLeverStatus object that contains either the result handle
     * for successful operations or error information for failed operations.</p>
     *
     * @param index the index handle
     * @param sparqlQuery the SPARQL query to execute
     * @param mediaType the media type for the result format
     * @return QLeverStatus containing result handle or error information
     * @throws QLeverFfiException if the FFI call fails
     * @throws NullPointerException if any argument is null
     */
    public QLeverStatus queryExec(MemorySegment index, String sparqlQuery, QLeverMediaType mediaType) {
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(sparqlQuery, "sparqlQuery must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment querySeg = callArena.allocateFrom(sparqlQuery, StandardCharsets.UTF_8);
            MemorySegment acceptSeg = callArena.allocateFrom(mediaType.headerValue(), StandardCharsets.UTF_8);
            MemorySegment resultHandle = (MemorySegment) mhQueryExec.invokeExact(index, querySeg, acceptSeg);

            return new QLeverStatus(resultHandle);
        } catch (Throwable t) {
            // Extract error message from throwable
            String errorMessage = t.getMessage();
            if (errorMessage == null) {
                errorMessage = "QLever query execution failed";
            }
            return new QLeverStatus(errorMessage, 500); // Internal server error
        }
    }

    // ========================================================================
    // Result Iteration Operations
    // ========================================================================

    /**
     * Checks if the result has more lines to consume.
     *
     * @param result the result handle
     * @return true if {@link #resultNext(MemorySegment)} will return another line
     */
    public boolean resultHasNext(MemorySegment result) {
        if (result == null || result.equals(MemorySegment.NULL)) {
            return false;
        }
        try {
            return (boolean) mhResultHasNext.invokeExact(result);
        } catch (Throwable t) {
            throw new QLeverFfiException("Failed to check result has next", t);
        }
    }

    /**
     * Returns the next line of the result.
     *
     * @param result the result handle
     * @return next line as string, or null if no more lines
     * @throws QLeverFfiException if FFI call fails
     */
    public String resultNext(MemorySegment result) {
        if (result == null || result.equals(MemorySegment.NULL)) {
            return null;
        }
        try {
            MemorySegment linePtr = (MemorySegment) mhResultNext.invokeExact(result);
            if (linePtr == null || linePtr.equals(MemorySegment.NULL)) {
                return null;
            }
            // Safely reinterpret the C string with a reasonable max length
            MemorySegment lineSeg = linePtr.reinterpret(MAX_LINE_LENGTH, arena, null);
            return lineSeg.getString(0, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            throw new QLeverFfiException("Failed to get next result line", t);
        }
    }

    /**
     * Destroys a result handle and releases native resources.
     *
     * @param result the result handle to destroy (safe to pass null or NULL)
     */
    public void resultDestroy(MemorySegment result) {
        if (result != null && !result.equals(MemorySegment.NULL)) {
            try {
                mhResultDestroy.invokeExact(result);
            } catch (Throwable t) {
                throw new QLeverFfiException("Failed to destroy result", t);
            }
        }
    }

    // ========================================================================
    // Error Handling Operations
    // ========================================================================

    /**
     * Returns the error message if query execution failed.
     *
     * @param result the result handle
     * @return error message string, or null if no error
     */
    public String resultError(MemorySegment result) {
        if (result == null || result.equals(MemorySegment.NULL)) {
            return "Result handle is null";
        }
        try {
            MemorySegment errorPtr = (MemorySegment) mhResultError.invokeExact(result);
            if (errorPtr == null || errorPtr.equals(MemorySegment.NULL)) {
                return null;
            }
            return errorPtr.reinterpret(MAX_ERROR_LENGTH, arena, null)
                          .getString(0, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "Failed to retrieve error: " + t.getMessage();
        }
    }

    /**
     * Returns the HTTP status code for the query result.
     *
     * @param result the result handle
     * @return HTTP status code (200=success, 400=bad query, 500=server error)
     */
    public int resultStatus(MemorySegment result) {
        if (result == null || result.equals(MemorySegment.NULL)) {
            return 0;
        }
        try {
            return (int) mhResultStatus.invokeExact(result);
        } catch (Throwable t) {
            return 0;
        }
    }

    // ========================================================================
    // Resource Management
    // ========================================================================

    /**
     * Closes the shared arena, releasing all associated native resources.
     *
     * <p>After calling this method, the FFI bindings must not be used.</p>
     */
    @Override
    public void close() {
        arena.close();
    }
}