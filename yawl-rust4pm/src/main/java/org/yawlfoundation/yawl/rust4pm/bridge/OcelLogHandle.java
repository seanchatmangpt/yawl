package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.OcelEvent;

import java.lang.foreign.*;

/**
 * A handle to a Rust-owned OcelLog.
 *
 * <p>This record wraps a raw pointer into Rust heap memory. The pointer is valid
 * until {@link #close()} is called, which invokes {@code rust4pm_log_free}.
 *
 * <p>Zero-copy contract: {@link #eventCount()} and {@link #events()} read directly
 * from Rust memory via Panama MemorySegment — no Java heap copies.
 *
 * <p>Thread safety: OcelLogHandle is <em>not</em> thread-safe. Do not share
 * a single handle across threads. The parent bridge IS thread-safe.
 */
public record OcelLogHandle(MemorySegment ptr, Rust4pmBridge bridge)
        implements AutoCloseable {

    /**
     * Count of events in this log.
     * Zero-copy — direct native call with isTrivial optimization.
     */
    public int eventCount() {
        return (int) rust4pm_h.rust4pm_log_event_count(ptr);
    }

    /**
     * Get a zero-copy view of all events.
     * The returned OcelEventView borrows from this OcelLogInternal — this handle must remain
     * open while the view is in use. Caller should close the view when done.
     *
     * @return OcelEventView — close it to release the OcelEventsResult
     * @throws ProcessMiningException if Rust returns an error
     */
    public OcelEventView events() throws ProcessMiningException {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment result = rust4pm_h.rust4pm_log_get_events(bridge.arena(), ptr);

            MemorySegment errPtr =
                (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_ERROR.get(result, 0L);
            if (!MemorySegment.NULL.equals(errPtr)) {
                String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0);
                rust4pm_h.rust4pm_error_free(errPtr);
                throw new ProcessMiningException("events() failed: " + msg);
            }

            // size_t count is mapped to ADDRESS-size long in our layout
            MemorySegment countSeg =
                (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_COUNT.get(result, 0L);
            long count = countSeg.address();

            MemorySegment eventsPtr =
                (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_EVENTS.get(result, 0L);

            long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
            // Reinterpret: pointer into Rust's OcelLogInternal — zero-copy, valid while log is alive
            MemorySegment eventsSeg = count == 0
                ? MemorySegment.NULL
                : eventsPtr.reinterpret(count * stride, bridge.arena(), null);

            return new OcelEventView(eventsSeg, (int) count, result, bridge);
        }
    }

    @Override
    public void close() {
        bridge.freeLog(ptr);
    }
}
