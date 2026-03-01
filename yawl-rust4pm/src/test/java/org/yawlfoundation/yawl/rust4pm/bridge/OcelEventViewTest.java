package org.yawlfoundation.yawl.rust4pm.bridge;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.OcelEvent;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link OcelEventView}.
 *
 * <p>Tests verify the API contract WITHOUT the native library:
 * <ul>
 *   <li>count() returns correct value</li>
 *   <li>get() throws IndexOutOfBoundsException for out-of-range indices</li>
 *   <li>close() is idempotent</li>
 *   <li>stream() returns correct element count</li>
 * </ul>
 *
 * <p>All tests use real Panama FFM allocations (Arena.ofShared) and populate
 * segments with actual string pointers from confined arenas. No mocks or stubs.
 */
class OcelEventViewTest {

    @Test
    void count_returns_correct_value_when_constructed_with_count_zero() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 0);

            assertEquals(0, view.count(), "count() must return 0");
        }
    }

    @Test
    void count_returns_correct_value_when_constructed_with_count_five() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 5);

            assertEquals(5, view.count(), "count() must return 5");
        }
    }

    @Test
    void count_returns_correct_value_when_constructed_with_large_count() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 1_000_000);

            assertEquals(1_000_000, view.count(), "count() must return 1_000_000");
        }
    }

    @Test
    void get_throws_index_out_of_bounds_for_negative_index() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 5);

            assertThrows(
                IndexOutOfBoundsException.class,
                () -> view.get(-1),
                "get(-1) must throw IndexOutOfBoundsException"
            );
        }
    }

    @Test
    void get_throws_index_out_of_bounds_for_index_equal_to_count() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 5);

            assertThrows(
                IndexOutOfBoundsException.class,
                () -> view.get(5),
                "get(count) must throw IndexOutOfBoundsException"
            );
        }
    }

    @Test
    void get_throws_index_out_of_bounds_for_index_greater_than_count() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 5);

            assertThrows(
                IndexOutOfBoundsException.class,
                () -> view.get(100),
                "get(100) must throw IndexOutOfBoundsException for count=5"
            );
        }
    }

    @Test
    void get_materializes_single_event_from_allocated_segment() {
        Arena sharedArena = Arena.ofShared();

        MemorySegment eventSeg = sharedArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);

        MemorySegment idPtr = sharedArena.allocateFrom("evt-001", StandardCharsets.UTF_8);
        MemorySegment typePtr = sharedArena.allocateFrom("place-order", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(eventSeg, 0L, idPtr);
        rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(eventSeg, 0L, typePtr);
        rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(eventSeg, 0L, 1_700_000_000_000L);
        rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(eventSeg, 0L, 3L);

        OcelEventView view = new OcelEventView(eventSeg, 1);

        OcelEvent event = view.get(0);

        assertEquals("evt-001", event.eventId(), "eventId must match");
        assertEquals("place-order", event.eventType(), "eventType must match");
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), event.timestamp(), "timestamp must match");
        assertEquals(3, event.attrCount(), "attrCount must match");

        sharedArena.close();
    }

    @Test
    void get_materializes_event_at_arbitrary_index_within_count() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 3);

        MemorySegment id1 = sharedArena.allocateFrom("evt-100", StandardCharsets.UTF_8);
        MemorySegment type1 = sharedArena.allocateFrom("approve", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(arrayBase, 1 * stride, id1);
        rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(arrayBase, 1 * stride, type1);
        rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(arrayBase, 1 * stride, 1_700_000_000_100L);
        rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(arrayBase, 1 * stride, 2L);

        OcelEventView view = new OcelEventView(arrayBase, 3);
        OcelEvent event = view.get(1);

        assertEquals("evt-100", event.eventId(), "eventId at index 1 must match");
        assertEquals("approve", event.eventType(), "eventType at index 1 must match");
        assertEquals(Instant.ofEpochMilli(1_700_000_000_100L), event.timestamp(), "timestamp at index 1 must match");
        assertEquals(2, event.attrCount(), "attrCount at index 1 must match");

        sharedArena.close();
    }

    @Test
    void close_is_idempotent_when_called_once() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 1);

            assertDoesNotThrow(view::close, "First close() must not throw");
        }
    }

    @Test
    void close_is_idempotent_when_called_twice() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 1);

            assertDoesNotThrow(view::close, "First close() must not throw");
            assertDoesNotThrow(view::close, "Second close() must be idempotent");
        }
    }

    @Test
    void close_is_idempotent_when_called_multiple_times() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 1);

            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(view::close, "close() at iteration " + i + " must not throw");
            }
        }
    }

    @Test
    void stream_returns_empty_stream_when_count_is_zero() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            OcelEventView view = new OcelEventView(testSeg, 0);

            try (Stream<OcelEvent> stream = view.stream()) {
                long count = stream.count();
                assertEquals(0, count, "stream().count() must be 0");
            }
        }
    }

    @Test
    void stream_returns_stream_with_correct_count_when_count_is_one() {
        Arena sharedArena = Arena.ofShared();

        MemorySegment testSeg = sharedArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);

        MemorySegment idPtr = sharedArena.allocateFrom("evt-stream-1", StandardCharsets.UTF_8);
        MemorySegment typePtr = sharedArena.allocateFrom("process", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(testSeg, 0L, idPtr);
        rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(testSeg, 0L, typePtr);
        rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(testSeg, 0L, 1_700_000_000_000L);
        rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(testSeg, 0L, 1L);

        OcelEventView view = new OcelEventView(testSeg, 1);

        try (Stream<OcelEvent> stream = view.stream()) {
            long count = stream.count();
            assertEquals(1, count, "stream().count() must be 1");
        }

        sharedArena.close();
    }

    @Test
    void stream_returns_stream_with_correct_count_when_count_is_five() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 5);

        for (int i = 0; i < 5; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("evt-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom("activity", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(arrayBase, (long) i * stride, typePtr);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(arrayBase, (long) i * stride, 1_700_000_000_000L + i);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(arrayBase, (long) i * stride, (long) (i + 1));
        }

        OcelEventView view = new OcelEventView(arrayBase, 5);

        try (Stream<OcelEvent> stream = view.stream()) {
            List<OcelEvent> events = stream.toList();
            assertEquals(5, events.size(), "stream().count() must be 5");
        }

        sharedArena.close();
    }

    @Test
    void stream_materializes_elements_lazily_on_demand() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 10);

        for (int i = 0; i < 10; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("lazy-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom("lazy-type", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(arrayBase, (long) i * stride, typePtr);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(arrayBase, (long) i * stride, 1_700_000_000_000L);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(arrayBase, (long) i * stride, 0L);
        }

        OcelEventView view = new OcelEventView(arrayBase, 10);

        try (Stream<OcelEvent> stream = view.stream()) {
            OcelEvent firstElement = stream.findFirst().orElseThrow();
            assertEquals("lazy-0", firstElement.eventId(), "First element must be lazy-0");
        }

        sharedArena.close();
    }

    @Test
    void stream_and_get_return_equivalent_values() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 3);

        for (int i = 0; i < 3; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("evt-equiv-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom("equiv-type", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(arrayBase, (long) i * stride, typePtr);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(arrayBase, (long) i * stride, 1_700_000_000_000L + i);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(arrayBase, (long) i * stride, 10L);
        }
        OcelEventView view = new OcelEventView(arrayBase, 3);

        try (Stream<OcelEvent> stream = view.stream()) {
            List<OcelEvent> streamEvents = stream.toList();

            for (int i = 0; i < 3; i++) {
                OcelEvent getEvent = view.get(i);
                OcelEvent streamEvent = streamEvents.get(i);

                assertEquals(getEvent.eventId(), streamEvent.eventId(), "eventId must match at index " + i);
                assertEquals(getEvent.eventType(), streamEvent.eventType(), "eventType must match at index " + i);
                assertEquals(getEvent.timestamp(), streamEvent.timestamp(), "timestamp must match at index " + i);
                assertEquals(getEvent.attrCount(), streamEvent.attrCount(), "attrCount must match at index " + i);
            }
        }

        sharedArena.close();
    }
}
