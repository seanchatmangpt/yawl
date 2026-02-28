package org.yawlfoundation.yawl.rust4pm.model;

import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;

import java.lang.foreign.MemorySegment;
import java.time.Instant;

/**
 * An OCEL2 event materialized from Rust-owned memory.
 *
 * <p>{@link #fromSegment(MemorySegment)} reads directly from a {@code OcelEventC}
 * struct in native memory — no intermediate copies for string pointer and long fields.
 *
 * @param eventId    unique event identifier
 * @param eventType  activity/event type name
 * @param timestamp  event occurrence time
 * @param attrCount  number of attributes (attributes fetched on demand)
 */
public record OcelEvent(String eventId, String eventType, Instant timestamp, int attrCount) {

    /**
     * Read one OcelEventC struct from native memory. Zero-copy field reads.
     *
     * @param s a MemorySegment slice over exactly one OcelEventC struct
     * @return materialized OcelEvent record
     */
    public static OcelEvent fromSegment(MemorySegment s) {
        MemorySegment idPtr    = (MemorySegment) rust4pm_h.OCEL_EVENT_C_EVENT_ID.get(s, 0L);
        MemorySegment typePtr  = (MemorySegment) rust4pm_h.OCEL_EVENT_C_EVENT_TYPE.get(s, 0L);
        long          tsMs     = (long)          rust4pm_h.OCEL_EVENT_C_TIMESTAMP_MS.get(s, 0L);
        long          attrLong = (long)          rust4pm_h.OCEL_EVENT_C_ATTR_COUNT.get(s, 0L);

        return new OcelEvent(
            idPtr.reinterpret(Long.MAX_VALUE).getString(0),
            typePtr.reinterpret(Long.MAX_VALUE).getString(0),
            Instant.ofEpochMilli(tsMs),
            (int) attrLong
        );
    }
}
