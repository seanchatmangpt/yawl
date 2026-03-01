package org.yawlfoundation.yawl.rust4pm.bridge;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.OcelObject;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link OcelObjectView}.
 *
 * <p>Tests verify the API contract WITHOUT the native library. Symmetric with
 * {@link OcelEventViewTest}:
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
class OcelObjectViewTest {

    @Test
    void count_returns_correct_value_when_constructed_with_count_zero() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 0);

            assertEquals(0, view.count(), "count() must return 0");
        }
    }

    @Test
    void count_returns_correct_value_when_constructed_with_count_five() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 5);

            assertEquals(5, view.count(), "count() must return 5");
        }
    }

    @Test
    void count_returns_correct_value_when_constructed_with_large_count() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 1_000_000);

            assertEquals(1_000_000, view.count(), "count() must return 1_000_000");
        }
    }

    @Test
    void get_throws_index_out_of_bounds_for_negative_index() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 5);

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
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 5);

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
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 5);

            assertThrows(
                IndexOutOfBoundsException.class,
                () -> view.get(100),
                "get(100) must throw IndexOutOfBoundsException for count=5"
            );
        }
    }

    @Test
    void get_materializes_single_object_from_allocated_segment() {
        Arena sharedArena = Arena.ofShared();

        MemorySegment objectSeg = sharedArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);

        MemorySegment idPtr = sharedArena.allocateFrom("order-001", StandardCharsets.UTF_8);
        MemorySegment typePtr = sharedArena.allocateFrom("Order", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(objectSeg, 0L, idPtr);
        rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(objectSeg, 0L, typePtr);

        OcelObjectView view = new OcelObjectView(objectSeg, 1);

        OcelObject object = view.get(0);

        assertEquals("order-001", object.objectId(), "objectId must match");
        assertEquals("Order", object.objectType(), "objectType must match");

        sharedArena.close();
    }

    @Test
    void get_materializes_object_at_arbitrary_index_within_count() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 3);

        MemorySegment id1 = sharedArena.allocateFrom("obj-100", StandardCharsets.UTF_8);
        MemorySegment type1 = sharedArena.allocateFrom("Item", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(arrayBase, 1 * stride, id1);
        rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(arrayBase, 1 * stride, type1);

        OcelObjectView view = new OcelObjectView(arrayBase, 3);
        OcelObject object = view.get(1);

        assertEquals("obj-100", object.objectId(), "objectId at index 1 must match");
        assertEquals("Item", object.objectType(), "objectType at index 1 must match");

        sharedArena.close();
    }

    @Test
    void get_handles_empty_type_strings() {
        Arena sharedArena = Arena.ofShared();

        MemorySegment objectSeg = sharedArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);

        MemorySegment idPtr = sharedArena.allocateFrom("empty-type-obj", StandardCharsets.UTF_8);
        MemorySegment typePtr = sharedArena.allocateFrom("", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(objectSeg, 0L, idPtr);
        rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(objectSeg, 0L, typePtr);

        OcelObjectView view = new OcelObjectView(objectSeg, 1);

        OcelObject object = view.get(0);

        assertEquals("empty-type-obj", object.objectId(), "objectId must match");
        assertEquals("", object.objectType(), "objectType must be empty string");

        sharedArena.close();
    }

    @Test
    void close_is_idempotent_when_called_once() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 1);

            assertDoesNotThrow(view::close, "First close() must not throw");
        }
    }

    @Test
    void close_is_idempotent_when_called_twice() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 1);

            assertDoesNotThrow(view::close, "First close() must not throw");
            assertDoesNotThrow(view::close, "Second close() must be idempotent");
        }
    }

    @Test
    void close_is_idempotent_when_called_multiple_times() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 1);

            for (int i = 0; i < 5; i++) {
                assertDoesNotThrow(view::close, "close() at iteration " + i + " must not throw");
            }
        }
    }

    @Test
    void stream_returns_empty_stream_when_count_is_zero() {
        try (Arena probeArena = Arena.ofShared()) {
            MemorySegment testSeg = probeArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            OcelObjectView view = new OcelObjectView(testSeg, 0);

            try (Stream<OcelObject> stream = view.stream()) {
                long count = stream.count();
                assertEquals(0, count, "stream().count() must be 0");
            }
        }
    }

    @Test
    void stream_returns_stream_with_correct_count_when_count_is_one() {
        Arena sharedArena = Arena.ofShared();

        MemorySegment testSeg = sharedArena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);

        MemorySegment idPtr = sharedArena.allocateFrom("stream-obj-1", StandardCharsets.UTF_8);
        MemorySegment typePtr = sharedArena.allocateFrom("StreamType", StandardCharsets.UTF_8);

        rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(testSeg, 0L, idPtr);
        rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(testSeg, 0L, typePtr);

        OcelObjectView view = new OcelObjectView(testSeg, 1);

        try (Stream<OcelObject> stream = view.stream()) {
            long count = stream.count();
            assertEquals(1, count, "stream().count() must be 1");
        }

        sharedArena.close();
    }

    @Test
    void stream_returns_stream_with_correct_count_when_count_is_five() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 5);

        String[] types = {"Order", "Item", "Customer", "Invoice", "Payment"};
        for (int i = 0; i < 5; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("obj-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom(types[i], StandardCharsets.UTF_8);

            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(arrayBase, (long) i * stride, typePtr);
        }

        OcelObjectView view = new OcelObjectView(arrayBase, 5);

        try (Stream<OcelObject> stream = view.stream()) {
            List<OcelObject> objects = stream.toList();
            assertEquals(5, objects.size(), "stream().count() must be 5");
        }

        sharedArena.close();
    }

    @Test
    void stream_materializes_elements_lazily_on_demand() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 10);

        for (int i = 0; i < 10; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("lazy-obj-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom("LazyType", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(arrayBase, (long) i * stride, typePtr);
        }

        OcelObjectView view = new OcelObjectView(arrayBase, 10);

        try (Stream<OcelObject> stream = view.stream()) {
            OcelObject firstElement = stream.findFirst().orElseThrow();
            assertEquals("lazy-obj-0", firstElement.objectId(), "First element must be lazy-obj-0");
        }

        sharedArena.close();
    }

    @Test
    void stream_and_get_return_equivalent_values() {
        Arena sharedArena = Arena.ofShared();

        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * 3);

        String[] types = {"Purchase", "Delivery", "Return"};
        for (int i = 0; i < 3; i++) {
            MemorySegment idPtr = sharedArena.allocateFrom("equiv-obj-" + i, StandardCharsets.UTF_8);
            MemorySegment typePtr = sharedArena.allocateFrom(types[i], StandardCharsets.UTF_8);

            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(arrayBase, (long) i * stride, idPtr);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(arrayBase, (long) i * stride, typePtr);
        }

        OcelObjectView view = new OcelObjectView(arrayBase, 3);

        try (Stream<OcelObject> stream = view.stream()) {
            List<OcelObject> streamObjects = stream.toList();

            for (int i = 0; i < 3; i++) {
                OcelObject getObject = view.get(i);
                OcelObject streamObject = streamObjects.get(i);

                assertEquals(getObject.objectId(), streamObject.objectId(), "objectId must match at index " + i);
                assertEquals(getObject.objectType(), streamObject.objectType(), "objectType must match at index " + i);
            }
        }

        sharedArena.close();
    }

    @Test
    void stream_handles_many_objects_correctly() {
        Arena sharedArena = Arena.ofShared();

        int objectCount = 100;
        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        MemorySegment arrayBase = sharedArena.allocate(stride * objectCount);

            for (int i = 0; i < objectCount; i++) {
                MemorySegment idPtr = sharedArena.allocateFrom("many-obj-" + i, StandardCharsets.UTF_8);
                MemorySegment typePtr = sharedArena.allocateFrom("ManyType", StandardCharsets.UTF_8);

                rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(arrayBase, (long) i * stride, idPtr);
                rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(arrayBase, (long) i * stride, typePtr);
        }

        OcelObjectView view = new OcelObjectView(arrayBase, objectCount);

        try (Stream<OcelObject> stream = view.stream()) {
            List<OcelObject> objects = stream.toList();
            assertEquals(objectCount, objects.size(), "Must materialize all " + objectCount + " objects");

            for (int i = 0; i < objectCount; i++) {
                OcelObject obj = objects.get(i);
                assertEquals("many-obj-" + i, obj.objectId(), "objectId at index " + i + " must match");
                assertEquals("ManyType", obj.objectType(), "objectType at index " + i + " must match");
            }
        }

        sharedArena.close();
    }
}
