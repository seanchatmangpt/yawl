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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.qlever;

import java.lang.foreign.MemorySegment;

/**
 * A wrapper for QLever query results with automatic resource management.
 *
 * <p>This class provides a convenient way to iterate over query results
 * while ensuring proper cleanup of native resources. It implements
 * AutoCloseable to ensure the result handle is properly destroyed.</p>
 *
 * <h2>Usage Example</h2>
 * {@snippet :
 * try (QLeverResult result = new QLeverResult(ffi, queryStatus.result())) {
 *     while (result.hasNext()) {
 *         String line = result.next();
 *         System.out.println(line);
 *     }
 * }
 * }
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class QLeverResult implements AutoCloseable {

    private final QLeverFfiBindings ffi;
    private final MemorySegment resultHandle;
    private final String accumulatedData;
    private final int accumulatedRows;

    /**
     * Creates a new QLeverResult wrapper for streaming results.
     *
     * @param ffi the FFI bindings instance
     * @param resultHandle the native result handle
     */
    public QLeverResult(QLeverFfiBindings ffi, MemorySegment resultHandle) {
        this.ffi = ffi;
        this.resultHandle = resultHandle;
        this.accumulatedData = null;
        this.accumulatedRows = 0;
    }

    /**
     * Creates a new QLeverResult wrapper for eager (accumulated) results.
     * Internal use only; call {@link #ofEager(String, int)} instead.
     *
     * @param data the accumulated result data
     * @param rowCount the number of rows
     */
    private QLeverResult(String data, int rowCount) {
        this.ffi = null;
        this.resultHandle = null;
        this.accumulatedData = data;
        this.accumulatedRows = rowCount;
    }

    /**
     * Creates an eager QLeverResult with accumulated data.
     * Suitable for test fixtures and scenarios where data is already collected.
     *
     * @param data the accumulated result data as a string
     * @param rowCount the number of result rows
     * @return a new QLeverResult backed by eager data
     */
    public static QLeverResult ofEager(String data, int rowCount) {
        return new QLeverResult(data, rowCount);
    }

    /**
     * Checks if there are more results to consume.
     *
     * @return true if {@link #next()} will return another result line
     */
    public boolean hasNext() {
        return ffi.resultHasNext(resultHandle);
    }

    /**
     * Returns the next line of the result.
     *
     * @return next line as string, or null if no more lines
     */
    public String next() {
        return ffi.resultNext(resultHandle);
    }

    /**
     * Returns the error message if query execution failed.
     *
     * @return error message string, or null if no error
     */
    public String error() {
        return ffi.resultError(resultHandle);
    }

    /**
     * Returns the HTTP status code for the query result.
     *
     * @return HTTP status code (200=success, 400=bad query, 500=server error)
     */
    public int status() {
        return ffi.resultStatus(resultHandle);
    }

    /**
     * Returns the accumulated result data.
     * Only available for eager results created via {@link #ofEager(String, int)}.
     *
     * @return the accumulated data string
     * @throws IllegalStateException if this is a streaming result
     */
    public String data() {
        if (accumulatedData == null) {
            throw new IllegalStateException("data() not available for streaming results");
        }
        return accumulatedData;
    }

    /**
     * Returns the number of accumulated result rows.
     * Only available for eager results created via {@link #ofEager(String, int)}.
     *
     * @return the row count
     * @throws IllegalStateException if this is a streaming result
     */
    public int rowCount() {
        if (accumulatedData == null) {
            throw new IllegalStateException("rowCount() not available for streaming results");
        }
        return accumulatedRows;
    }

    /**
     * Destroys the result handle and releases native resources.
     *
     * <p>This method is called automatically when using try-with-resources.</p>
     */
    @Override
    public void close() {
        if (ffi != null) {
            ffi.resultDestroy(resultHandle);
        }
    }
}