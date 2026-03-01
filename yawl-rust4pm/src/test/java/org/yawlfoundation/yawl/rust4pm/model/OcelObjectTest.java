package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OcelObject}, including the {@link OcelObject#fromSegment} factory
 * that reads directly from native memory — verifying correct-by-construction field mapping.
 */
class OcelObjectTest {

    @Test
    void record_constructor_preserves_all_fields() {
        OcelObject obj = new OcelObject("order-001", "Order");
        assertEquals("order-001", obj.objectId());
        assertEquals("Order", obj.objectType());
    }

    @Test
    void record_equality_is_structural() {
        OcelObject a = new OcelObject("o1", "Item");
        OcelObject b = new OcelObject("o1", "Item");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void fromSegment_reads_object_id_and_type_from_native_memory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg      = arena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            MemorySegment idSeg    = arena.allocateFrom("item-99",  StandardCharsets.UTF_8);
            MemorySegment typeSeg  = arena.allocateFrom("Item",     StandardCharsets.UTF_8);

            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(seg,   0L, idSeg);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(seg, 0L, typeSeg);

            OcelObject obj = OcelObject.fromSegment(seg);

            assertEquals("item-99", obj.objectId());
            assertEquals("Item",    obj.objectType());
        }
    }

    @Test
    void fromSegment_handles_utf8_multibyte_strings() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg      = arena.allocate(rust4pm_h.OCEL_OBJECT_C_LAYOUT);
            // Multi-byte UTF-8: café (4 chars, 5 bytes in UTF-8)
            MemorySegment idSeg    = arena.allocateFrom("café-42",  StandardCharsets.UTF_8);
            MemorySegment typeSeg  = arena.allocateFrom("Précommande", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_OBJECT_C_OBJECT_ID.set(seg,   0L, idSeg);
            rust4pm_h.OCEL_OBJECT_C_OBJECT_TYPE.set(seg, 0L, typeSeg);

            OcelObject obj = OcelObject.fromSegment(seg);

            assertEquals("café-42",      obj.objectId());
            assertEquals("Précommande",  obj.objectType());
        }
    }

    @Test
    void ocel_object_c_layout_is_16_bytes() {
        // object_id(8) + object_type(8) = 16 bytes
        assertEquals(16L, rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize());
    }

    @Test
    void ocel_objects_result_layout_is_24_bytes() {
        // objects(8) + count(8) + error(8) = 24 bytes
        assertEquals(24L, rust4pm_h.OCEL_OBJECTS_RESULT_LAYOUT.byteSize());
    }
}
