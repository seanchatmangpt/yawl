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

package org.yawlfoundation.yawl.datamodelling.bridge;

import org.yawlfoundation.yawl.datamodelling.DataModellingException;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/**
 * Internal low-level helper for the DataModelling native bridge.
 *
 * <p>Handles per-call Arena allocation for native strings, DmResult reading,
 * struct layout validation, and JSON argument serialization. This class is
 * not part of the public API — callers use {@link DataModellingL3}.
 *
 * <p>Two call paths mirror the Rust dispatcher's two result variants:
 * <ul>
 *   <li>{@link #call} — for SDK functions returning a String result ({@code dm_ok})</li>
 *   <li>{@link #callVoid} — for SDK functions returning void ({@code dm_void_ok})</li>
 * </ul>
 *
 * <p>DmResult struct layout (x86_64 / aarch64 little-endian, verified by probes):
 * <pre>
 *   struct DmResult {
 *     data:      *mut c_char  // offset 0,  size 8
 *     data_len:  usize        // offset 8,  size 8
 *     error:     *mut c_char  // offset 16, size 8
 *     error_len: usize        // offset 24, size 8
 *   }  // total size = 32 bytes
 * </pre>
 */
final class DataModellingFFI {

    // ── DmResult struct layout constants (expected values) ────────────────────
    // Validated at L2 construction time via dm_sizeof_* and dm_offsetof_* probes.

    static final long STRUCT_SIZE      = 32L;
    static final long OFFSET_DATA      =  0L;
    static final long OFFSET_DATA_LEN  =  8L;
    static final long OFFSET_ERROR     = 16L;
    static final long OFFSET_ERROR_LEN = 24L;

    // ── Layout validation ─────────────────────────────────────────────────────

    /**
     * Validates that the native DmResult struct layout matches our Java constants.
     * Called once at DataModellingL2 construction to fail fast on ABI mismatch.
     */
    static void validateLayout(DataModellingL2 l2) {
        long size    = l2.dmSizeofDmResult();
        long offData = l2.dmOffsetofData();
        long offDLen = l2.dmOffsetofDataLen();
        long offErr  = l2.dmOffsetofError();
        long offELen = l2.dmOffsetofErrorLen();

        if (size != STRUCT_SIZE
                || offData != OFFSET_DATA
                || offDLen != OFFSET_DATA_LEN
                || offErr  != OFFSET_ERROR
                || offELen != OFFSET_ERROR_LEN) {
            throw new IllegalStateException(String.format(
                    "DmResult layout mismatch: expected size=%d offsets=[%d,%d,%d,%d] "
                    + "but native reports size=%d offsets=[%d,%d,%d,%d]. "
                    + "Rebuild rust/data-modelling-ffi/ for this platform.",
                    STRUCT_SIZE, OFFSET_DATA, OFFSET_DATA_LEN, OFFSET_ERROR, OFFSET_ERROR_LEN,
                    size, offData, offDLen, offErr, offELen));
        }
    }

    // ── Call helpers ─────────────────────────────────────────────────────────

    /**
     * Calls a data-modelling-sdk function through dm_call and returns the result string.
     * Use this for SDK functions that return {@code Ok(Some(String))} (i.e. non-void).
     *
     * @param l2   connected L2 transport
     * @param fn   SDK function name (e.g. "parse_odcs_yaml")
     * @param args function arguments as plain strings
     * @return the result data string; never null
     * @throws DataModellingException if the native call reports an error or returns no data
     */
    static String call(DataModellingL2 l2, String fn, String... args) {
        String argsJson = toJsonArray(args);
        try (Arena arena = Arena.ofConfined()) {
            byte[] fnBytes   = fn.getBytes(StandardCharsets.UTF_8);
            byte[] argsBytes = argsJson.getBytes(StandardCharsets.UTF_8);
            MemorySegment fnSeg   = arena.allocateFrom(fn,       StandardCharsets.UTF_8);
            MemorySegment argsSeg = arena.allocateFrom(argsJson, StandardCharsets.UTF_8);

            MemorySegment resultPtr = l2.dmCall(fnSeg, fnBytes.length, argsSeg, argsBytes.length);
            return readData(l2, resultPtr, fn);
        }
    }

    /**
     * Calls a void data-modelling-sdk function through dm_call.
     * Use this for SDK functions that return {@code Ok(None)} (i.e. void return).
     * Checks only the error field; the data field is ignored.
     *
     * @param l2   connected L2 transport
     * @param fn   SDK function name (e.g. "validate_odps")
     * @param args function arguments as plain strings
     * @throws DataModellingException if the native call reports an error
     */
    static void callVoid(DataModellingL2 l2, String fn, String... args) {
        String argsJson = toJsonArray(args);
        try (Arena arena = Arena.ofConfined()) {
            byte[] fnBytes   = fn.getBytes(StandardCharsets.UTF_8);
            byte[] argsBytes = argsJson.getBytes(StandardCharsets.UTF_8);
            MemorySegment fnSeg   = arena.allocateFrom(fn,       StandardCharsets.UTF_8);
            MemorySegment argsSeg = arena.allocateFrom(argsJson, StandardCharsets.UTF_8);

            MemorySegment resultPtr = l2.dmCall(fnSeg, fnBytes.length, argsSeg, argsBytes.length);
            checkError(l2, resultPtr, fn);
        }
    }

    // ── Result reading ───────────────────────────────────────────────────────

    /**
     * Reads the data string from a DmResult. Throws on error or missing data.
     * Frees the DmResult in all cases.
     */
    private static String readData(DataModellingL2 l2, MemorySegment resultPtr, String fn) {
        if (MemorySegment.NULL.equals(resultPtr)) {
            throw new DataModellingException(
                    "dm_call returned null pointer for: " + fn,
                    DataModellingException.ErrorKind.EXECUTION_ERROR);
        }
        try {
            MemorySegment r        = resultPtr.reinterpret(STRUCT_SIZE);
            MemorySegment errorPtr = r.get(ValueLayout.ADDRESS,   OFFSET_ERROR);
            long          errorLen = r.get(ValueLayout.JAVA_LONG, OFFSET_ERROR_LEN);

            if (!MemorySegment.NULL.equals(errorPtr) && errorLen > 0) {
                String msg = errorPtr.reinterpret(errorLen)
                                     .getString(0, StandardCharsets.UTF_8);
                throw new DataModellingException(
                        fn + " failed: " + msg,
                        DataModellingException.ErrorKind.EXECUTION_ERROR);
            }

            MemorySegment dataPtr = r.get(ValueLayout.ADDRESS,   OFFSET_DATA);
            long          dataLen = r.get(ValueLayout.JAVA_LONG, OFFSET_DATA_LEN);

            if (!MemorySegment.NULL.equals(dataPtr) && dataLen > 0) {
                return dataPtr.reinterpret(dataLen).getString(0, StandardCharsets.UTF_8);
            }

            // data is null/zero with no error — SDK returned dm_void_ok() for a non-void call.
            throw new DataModellingException(
                    fn + " returned no data — call callVoid() for void SDK functions",
                    DataModellingException.ErrorKind.EXECUTION_ERROR);
        } finally {
            l2.dmResultFree(resultPtr);
        }
    }

    /**
     * Checks the error field of a DmResult for void operations. Frees the DmResult in all cases.
     */
    private static void checkError(DataModellingL2 l2, MemorySegment resultPtr, String fn) {
        if (MemorySegment.NULL.equals(resultPtr)) {
            throw new DataModellingException(
                    "dm_call returned null pointer for: " + fn,
                    DataModellingException.ErrorKind.EXECUTION_ERROR);
        }
        try {
            MemorySegment r        = resultPtr.reinterpret(STRUCT_SIZE);
            MemorySegment errorPtr = r.get(ValueLayout.ADDRESS,   OFFSET_ERROR);
            long          errorLen = r.get(ValueLayout.JAVA_LONG, OFFSET_ERROR_LEN);

            if (!MemorySegment.NULL.equals(errorPtr) && errorLen > 0) {
                String msg = errorPtr.reinterpret(errorLen)
                                     .getString(0, StandardCharsets.UTF_8);
                throw new DataModellingException(
                        fn + " failed: " + msg,
                        DataModellingException.ErrorKind.EXECUTION_ERROR);
            }
        } finally {
            l2.dmResultFree(resultPtr);
        }
    }

    // ── JSON serialization ────────────────────────────────────────────────────

    /**
     * Serializes a String[] to a compact JSON array for the dm_call args_json parameter.
     * Escapes backslash, double-quote, newline, carriage-return, and tab.
     */
    static String toJsonArray(String[] args) {
        StringJoiner sj = new StringJoiner(",", "[", "]");
        for (String arg : args) {
            if (arg == null) {
                sj.add("\"\"");
            } else {
                sj.add("\"" + arg.replace("\\", "\\\\")
                                  .replace("\"", "\\\"")
                                  .replace("\n", "\\n")
                                  .replace("\r", "\\r")
                                  .replace("\t", "\\t") + "\"");
            }
        }
        return sj.toString();
    }

    private DataModellingFFI() {}
}
