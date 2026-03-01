package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OcelEventTest {

    @Test
    void record_constructor_preserves_all_fields() {
        Instant ts = Instant.ofEpochMilli(1_700_000_000_000L);
        OcelEvent event = new OcelEvent("evt-1", "place-order", ts, 3);
        assertEquals("evt-1", event.eventId());
        assertEquals("place-order", event.eventType());
        assertEquals(ts, event.timestamp());
        assertEquals(3, event.attrCount());
    }

    @Test
    void record_equality_is_structural() {
        Instant ts = Instant.ofEpochMilli(0L);
        OcelEvent a = new OcelEvent("e1", "pick", ts, 0);
        OcelEvent b = new OcelEvent("e1", "pick", ts, 0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void fromSegment_reads_fields_from_native_memory() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg     = arena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            MemorySegment idSeg   = arena.allocateFrom("e-42",       StandardCharsets.UTF_8);
            MemorySegment typeSeg = arena.allocateFrom("ship-order", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(seg,     0L, idSeg);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(seg,   0L, typeSeg);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(seg, 0L, 1_000_000L);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(seg,   0L, 5L);

            OcelEvent event = OcelEvent.fromSegment(seg);

            assertEquals("e-42", event.eventId());
            assertEquals("ship-order", event.eventType());
            assertEquals(Instant.ofEpochMilli(1_000_000L), event.timestamp());
            assertEquals(5, event.attrCount());
        }
    }

    @Test
    void fromSegment_handles_zero_timestamp() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg     = arena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);
            MemorySegment idSeg   = arena.allocateFrom("e0",    StandardCharsets.UTF_8);
            MemorySegment typeSeg = arena.allocateFrom("start", StandardCharsets.UTF_8);

            rust4pm_h.OCEL_EVENT_C_EVENT_ID.set(seg,     0L, idSeg);
            rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.set(seg,   0L, typeSeg);
            rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.set(seg, 0L, 0L);
            rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.set(seg,   0L, 0L);

            OcelEvent event = OcelEvent.fromSegment(seg);
            assertEquals(Instant.EPOCH, event.timestamp());
            assertEquals(0, event.attrCount());
        }
    }
}
