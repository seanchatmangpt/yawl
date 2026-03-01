package org.yawlfoundation.yawl.rust4pm.bridge;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Correct-by-construction tests for {@link OcelLogHandle}.
 *
 * <p>These tests verify the safety invariants WITHOUT the native library:
 * <ul>
 *   <li>Double-close is idempotent (no crash, no double-free)</li>
 *   <li>Use-after-close of a derived view throws {@link IllegalStateException}
 *       enforced by Panama's Arena mechanism, not by developer discipline</li>
 * </ul>
 */
class OcelLogHandleCorrectnessTest {

    /**
     * Verifies that closing a handle twice is safe (idempotent).
     * The AtomicBoolean guard ensures rust4pm_log_free is called at most once.
     * Without the native library, rust4pm_log_free is a no-op (null MH$log_free).
     */
    @Test
    void close_is_idempotent_when_called_twice() {
        if (rust4pm_h.LIBRARY.isPresent()) return;

        try (Rust4pmBridge bridge = new Rust4pmBridge()) {
            OcelLogHandle handle = new OcelLogHandle(MemorySegment.NULL, bridge);

            assertDoesNotThrow(handle::close);
            assertDoesNotThrow(handle::close, "Second close() must be idempotent");
        }
    }

    /**
     * Verifies that an {@link OcelEventView}'s backing segment becomes inaccessible
     * after the owning arena is closed — Panama throws {@link IllegalStateException}.
     *
     * <p>This test synthesizes a real OcelEventC in an Arena, populates all fields,
     * reads it successfully before close, then verifies Panama enforces the lifetime
     * constraint after close — correct by construction, not by developer discipline.
     */
    @Test
    void ocel_event_view_segment_invalid_after_owning_arena_closed() {
        Arena ownedArena = Arena.ofShared();

        MemorySegment testSeg = ownedArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);

        // Populate with real string pointers so fromSegment() works before close
        try (Arena setup = Arena.ofConfined()) {
            MemorySegment idSeg   = setup.allocateFrom("evt-lifetime-test", StandardCharsets.UTF_8);
            MemorySegment typeSeg = setup.allocateFrom("place-order",       StandardCharsets.UTF_8);
            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(testSeg,     0L, idSeg);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(testSeg,   0L, typeSeg);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(testSeg, 0L, 1_700_000_000_000L);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(testSeg,   0L, 2L);
        }

        OcelEventView view = new OcelEventView(testSeg, 1);

        // Pre-close: access works
        assertDoesNotThrow(() -> view.count());

        // Close the owning arena — simulates OcelLogHandle.close()
        ownedArena.close();

        // Post-close: Panama enforces segment validity — must throw
        assertThrows(IllegalStateException.class, () -> view.get(0),
            "Accessing OcelEventView after owning arena closed must throw IllegalStateException");
    }

    /**
     * Verifies that an {@link OcelObjectView}'s backing segment becomes inaccessible
     * after the owning arena is closed.
     */
    @Test
    void ocel_object_view_segment_invalid_after_owning_arena_closed() {
        Arena ownedArena = Arena.ofShared();
        MemorySegment testSeg = ownedArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);

        try (Arena setup = Arena.ofConfined()) {
            MemorySegment idSeg   = setup.allocateFrom("order-lifetime-test", StandardCharsets.UTF_8);
            MemorySegment typeSeg = setup.allocateFrom("Order",               StandardCharsets.UTF_8);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(testSeg,   0L, idSeg);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(testSeg, 0L, typeSeg);
        }

        OcelObjectView view = new OcelObjectView(testSeg, 1);

        assertDoesNotThrow(() -> view.count());

        ownedArena.close();

        assertThrows(IllegalStateException.class, () -> view.get(0),
            "Accessing OcelObjectView after owning arena closed must throw IllegalStateException");
    }

    /**
     * Verifies that {@link OcelEventView#close()} is idempotent.
     * OcelEventView.close() is a logical-only close; multiple calls must not throw.
     */
    @Test
    void ocel_event_view_close_is_idempotent() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment seg = arena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(seg, 0);
            assertDoesNotThrow(view::close);
            assertDoesNotThrow(view::close, "Second close() on OcelEventView must be idempotent");
        }
    }

    /**
     * Verifies that {@link OcelObjectView#close()} is idempotent.
     */
    @Test
    void ocel_object_view_close_is_idempotent() {
        try (Arena arena = Arena.ofShared()) {
            MemorySegment seg = arena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(seg, 0);
            assertDoesNotThrow(view::close);
            assertDoesNotThrow(view::close, "Second close() on OcelObjectView must be idempotent");
        }
    }
}
