package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A handle to a Rust-owned OcelLog.
 *
 * <p>Wraps a raw pointer into Rust heap memory. The pointer is valid until
 * {@link #close()} is called, which invokes {@code rust4pm_log_free} and
 * closes the per-handle {@link Arena}, invalidating all derived segments.
 *
 * <p><b>Correct-by-construction lifetime</b>: {@link #events()} and
 * {@link #objects()} reinterpret borrowed Rust memory with this handle's
 * {@code ownedArena}. When {@link #close()} calls {@code ownedArena.close()},
 * any subsequent access to a surviving {@link OcelEventView} or
 * {@link OcelObjectView} throws {@link IllegalStateException} — enforced by
 * Panama, not by developer discipline.
 *
 * <p><b>Idempotent close</b>: {@link #close()} is safe to call multiple times.
 * {@code rust4pm_log_free} is invoked exactly once regardless of call count.
 *
 * <p>Thread safety: not thread-safe. Do not share a single handle across threads.
 * The parent {@link Rust4pmBridge} IS thread-safe.
 */
public final class OcelLogHandle implements AutoCloseable {

    private final MemorySegment rawPtr;
    private final Arena          ownedArena;   // per-handle; closed on close()
    private final Rust4pmBridge  bridge;
    private final AtomicBoolean  closed = new AtomicBoolean(false);

    OcelLogHandle(MemorySegment rawPtr, Rust4pmBridge bridge) {
        this.rawPtr     = rawPtr;
        this.ownedArena = Arena.ofShared();
        this.bridge     = bridge;
    }

    /** Raw pointer — bridge-layer internal API. Public for cross-package access from ProcessMiningEngine. */
    public MemorySegment ptr() { return rawPtr; }

    /**
     * Count of events in this log. Zero-copy — direct native call.
     */
    public int eventCount() {
        return (int) rust4pm_h.rust4pm_log_event_count(rawPtr);
    }

    /**
     * Count of objects in this log. Zero-copy — direct native call.
     */
    public int objectCount() {
        return (int) rust4pm_h.rust4pm_log_object_count(rawPtr);
    }

    /**
     * Zero-copy view of all events.
     *
     * <p>The returned view's backing memory is borrowed from Rust's OcelLogInternal
     * and reinterpreted with this handle's {@code ownedArena}. Accessing the view
     * after {@link #close()} throws {@link IllegalStateException}.
     *
     * @throws ProcessMiningException if Rust returns an error
     */
    public OcelEventView events() throws ProcessMiningException {
        MemorySegment result = rust4pm_h.rust4pm_log_get_events(ownedArena, rawPtr);

        MemorySegment errPtr = (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_ERROR.get(result, 0L);
        if (!MemorySegment.NULL.equals(errPtr)) {
            String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0, java.nio.charset.StandardCharsets.UTF_8);
            rust4pm_h.rust4pm_error_free(errPtr);
            throw new ProcessMiningException("events() failed: " + msg);
        }

        // JAVA_LONG varHandle returns long (was incorrectly cast to MemorySegment before)
        long count = (long) rust4pm_h.OCEL_EVENTS_RESULT_COUNT.get(result, 0L);
        MemorySegment eventsPtr = (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_EVENTS.get(result, 0L);

        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        // Reinterpret: pointer into Rust's OcelLogInternal, scoped to ownedArena.
        // After close(), ownedArena.close() makes this segment invalid → IllegalStateException on access.
        MemorySegment eventsSeg = count == 0
            ? MemorySegment.NULL
            : eventsPtr.reinterpret(count * stride, ownedArena, null);

        return new OcelEventView(eventsSeg, (int) count);
    }

    /**
     * Zero-copy view of all objects.
     *
     * <p>The returned view's backing memory is borrowed from Rust's OcelLogInternal
     * and reinterpreted with this handle's {@code ownedArena}. Accessing the view
     * after {@link #close()} throws {@link IllegalStateException}.
     *
     * @throws ProcessMiningException if Rust returns an error
     */
    public OcelObjectView objects() throws ProcessMiningException {
        MemorySegment result = rust4pm_h.rust4pm_log_get_objects(ownedArena, rawPtr);

        MemorySegment errPtr = (MemorySegment) rust4pm_h.OCEL_OBJECTS_RESULT_ERROR.get(result, 0L);
        if (!MemorySegment.NULL.equals(errPtr)) {
            String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0, java.nio.charset.StandardCharsets.UTF_8);
            rust4pm_h.rust4pm_error_free(errPtr);
            throw new ProcessMiningException("objects() failed: " + msg);
        }

        long count = (long) rust4pm_h.OCEL_OBJECTS_RESULT_COUNT.get(result, 0L);
        MemorySegment objectsPtr = (MemorySegment) rust4pm_h.OCEL_OBJECTS_RESULT_OBJECTS.get(result, 0L);

        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment objectsSeg = count == 0
            ? MemorySegment.NULL
            : objectsPtr.reinterpret(count * stride, ownedArena, null);

        return new OcelObjectView(objectsSeg, (int) count);
    }

    /**
     * Idempotent close. Safe to call multiple times.
     *
     * <p>First call: invokes {@code rust4pm_log_free} then closes {@code ownedArena},
     * invalidating all derived event/object view segments.
     * Subsequent calls: no-op.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            rust4pm_h.rust4pm_log_free(rawPtr);
            ownedArena.close();
        }
    }
}
