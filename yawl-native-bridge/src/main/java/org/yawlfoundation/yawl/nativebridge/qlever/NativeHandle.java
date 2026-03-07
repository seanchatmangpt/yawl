/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.nativebridge.qlever;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import org.yawlfoundation.yawl.nativebridge.qlever.generated.qlever_ffi;

/**
 * Manages native QLever lifecycle and error conversion.
 * Arena-scoped memory management with QLeverStatus → QLeverException mapping.
 *
 * <p>This class provides a typed bridge between the Java API and the
 * native QLever engine. It handles memory segment allocation and
 * error code conversion.</p>
 */
public final class NativeHandle<T extends QLeverEngine> implements AutoCloseable {

    // Shared arena — lives for the bridge's lifetime; thread-safe for concurrent access.
    private final Arena arena = Arena.ofShared();
    private final MemorySegment engineHandle;
    private final QLeverStatus lastStatus;

    /**
     * Creates a new QLever engine instance.
     *
     * @throws QLeverException if engine creation fails
     */
    public static NativeHandle<QLeverEngine> create() throws QLeverException {
        MemorySegment handle = qlever_ffi.qlever_engine_create(arena);
        QLeverStatus status = extractStatus(handle);
        if (status.code() != QLeverStatus.SUCCESS) {
            throw new QLeverException("Engine creation failed: " + status.message(), status);
        }
        return new NativeHandle<>(handle, status);
    }

    private NativeHandle(MemorySegment engineHandle, QLeverStatus lastStatus) {
        this.engineHandle = engineHandle;
        this.lastStatus = lastStatus;
    }

    /**
     * Executes an ASK query through the native engine.
     *
     * @param sparql ASK query string
     * @return boolean result
     * @throws QLeverException on query errors
     */
    public boolean ask(String sparql) throws QLeverException {
        try (Arena queryArena = Arena.ofConfined()) {
            MemorySegment sparqlSeg = queryArena.allocateUtf8String(sparql);

            MemorySegment resultSeg = queryArena.allocate(ValueLayout.JAVA_INT);
            int status = qlever_ffi.qlever_ask(engineHandle, sparqlSeg, resultSeg);

            updateStatus(status);
            if (lastStatus.code() != QLeverStatus.SUCCESS) {
                throw createException();
            }

            int resultValue = resultSeg.get(ValueLayout.JAVA_INT, 0);
            return resultValue != 0;
        }
    }

    /**
     * Executes a SELECT query through the native engine.
     *
     * @param sparql SELECT query string
     * @return list of variable bindings
     * @throws QLeverException on query errors
     */
    public List<Map<String, String>> select(String sparql) throws QLeverException {
        try (Arena queryArena = Arena.ofConfined()) {
            MemorySegment sparqlSeg = queryArena.allocateUtf8String(sparql);

            MemorySegment resultHandle = queryArena.allocate(qlever_ffi.QLEVER_RESULT_HANDLE_LAYOUT);
            int status = qlever_ffi.qlever_select(engineHandle, sparqlSeg, resultHandle);

            updateStatus(status);
            if (lastStatus.code() != QLeverStatus.SUCCESS) {
                throw createException();
            }

            return decodeSelectResult(resultHandle);
        }
    }

    /**
     * Executes a CONSTRUCT query through the native engine.
     *
     * @param sparql CONSTRUCT query string
     * @return list of RDF triples
     * @throws QLeverException on query errors
     */
    public List<Triple> construct(String sparql) throws QLeverException {
        try (Arena queryArena = Arena.ofConfined()) {
            MemorySegment sparqlSeg = queryArena.allocateUtf8String(sparql);

            MemorySegment resultHandle = queryArena.allocate(qlever_ffi.QLEVER_RESULT_HANDLE_LAYOUT);
            int status = qlever_ffi.qlever_construct(engineHandle, sparqlSeg, resultHandle);

            updateStatus(status);
            if (lastStatus.code() != QLeverStatus.SUCCESS) {
                throw createException();
            }

            return decodeConstructResult(resultHandle);
        }
    }

    /**
     * Executes an UPDATE query through the native engine.
     *
     * @param turtle Turtle data for updates
     * @throws QLeverException on update errors
     */
    public void update(String turtle) throws QLeverException {
        try (Arena queryArena = Arena.ofConfined()) {
            MemorySegment turtleSeg = queryArena.allocateUtf8String(turtle);

            int status = qlever_ffi.qlever_update(engineHandle, turtleSeg);

            updateStatus(status);
            if (lastStatus.code() != QLeverStatus.SUCCESS) {
                throw createException();
            }
        }
    }

    /**
     * Gets the native engine handle for direct FFM calls.
     *
     * @return memory segment pointing to the native engine
     */
    public MemorySegment getEngineHandle() {
        return engineHandle;
    }

    /**
     * Gets the shared arena for this bridge handle.
     *
     * @return shared arena
     */
    public Arena getArena() {
        return arena;
    }

    /**
     * Gets the last operation status.
     *
     * @return status of the last operation
     */
    public QLeverStatus getLastStatus() {
        return lastStatus;
    }

    /**
     * Closes the native engine and releases all resources.
     */
    @Override
    public void close() {
        qlever_ffi.qlever_engine_destroy(engineHandle);
        arena.close();
    }

    // Private helper methods

    private void updateStatus(int statusCode) {
        this.lastStatus = new QLeverStatus(statusCode, "Operation completed");
    }

    private QLeverException createException() {
        return switch (lastStatus.code()) {
            case QLeverStatus.PARSE_ERROR ->
                new QLeverParseException(lastStatus.message(), lastStatus);
            case QLeverStatus.SEMANTIC_ERROR ->
                new QLeverSemanticException(lastStatus.message(), lastStatus);
            case QLeverStatus.RUNTIME_ERROR ->
                new QLeverRuntimeException(lastStatus.message(), lastStatus);
            default ->
                new QLeverRuntimeException("Unknown error: " + lastStatus.message(), lastStatus);
        };
    }

    private List<Map<String, String>> decodeSelectResult(MemorySegment resultHandle) {
        // Implementation would decode native result into Java objects
        // This would involve reading the result buffer and parsing the SPARQL results
        return List.of(); // Placeholder
    }

    private List<Triple> decodeConstructResult(MemorySegment resultHandle) {
        // Implementation would decode native result into Java Triple objects
        // This would involve reading the result buffer and parsing RDF triples
        return List.of(); // Placeholder
    }

    private static QLeverStatus extractStatus(MemorySegment handle) {
        int code = handle.get(ValueLayout.JAVA_INT, 0);
        // In a real implementation, this would also extract an error message if present
        return new QLeverStatus(code, "Success");
    }
}