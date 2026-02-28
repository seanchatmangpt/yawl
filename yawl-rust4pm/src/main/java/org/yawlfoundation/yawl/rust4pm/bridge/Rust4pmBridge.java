package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.error.ParseException;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * Layer 2 bridge to librust4pm.so.
 *
 * <p>Thread-safe: uses {@link Arena#ofShared()} for the bridge lifetime arena.
 * Multiple threads may call any method concurrently.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * try (Rust4pmBridge bridge = new Rust4pmBridge()) {
 *     try (OcelLogHandle log = bridge.parseOcel2Json(json)) {
 *         int count = log.eventCount();
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>If the native library is not loaded, all methods throw
 * {@link UnsupportedOperationException} with actionable guidance.
 */
public final class Rust4pmBridge implements AutoCloseable {

    // Shared arena — lives for the bridge's lifetime; thread-safe for concurrent access
    private final Arena arena = Arena.ofShared();

    /**
     * Parse OCEL2 JSON string into a native OcelLog.
     *
     * @param json OCEL2 JSON (UTF-8)
     * @return OcelLogHandle — caller MUST close it (try-with-resources recommended)
     * @throws ParseException if Rust reports a JSON parse error
     * @throws UnsupportedOperationException if native library not loaded
     */
    public OcelLogHandle parseOcel2Json(String json) throws ParseException {
        try (Arena call = Arena.ofConfined()) {
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            MemorySegment jsonSeg = call.allocateFrom(json, StandardCharsets.UTF_8);

            MemorySegment result = rust4pm_h.rust4pm_parse_ocel2_json(arena, jsonSeg, jsonBytes.length);

            MemorySegment errorPtr = (MemorySegment) rust4pm_h.PARSE_RESULT_ERROR.get(result, 0L);
            if (!MemorySegment.NULL.equals(errorPtr)) {
                String msg = errorPtr.reinterpret(Long.MAX_VALUE).getString(0);
                rust4pm_h.rust4pm_error_free(errorPtr);
                throw new ParseException(msg);
            }

            MemorySegment handlePtr = (MemorySegment) rust4pm_h.PARSE_RESULT_HANDLE_PTR.get(result, 0L);
            return new OcelLogHandle(handlePtr, this);
        }
    }

    // ── Package-private: called by OcelLogHandle and OcelEventView ──────────

    /** Free a log pointer. Called by OcelLogHandle.close(). */
    void freeLog(MemorySegment ptr) {
        rust4pm_h.rust4pm_log_free(ptr);
    }

    /** Free an events result segment. Called by OcelEventView.close(). */
    void freeEvents(MemorySegment resultSeg) {
        rust4pm_h.rust4pm_events_free(resultSeg);
    }

    /** Bridge's shared arena — used by OcelLogHandle for sub-allocations. */
    Arena arena() {
        return arena;
    }

    @Override
    public void close() {
        arena.close();
    }
}
