package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.OcelEvent;

import java.lang.foreign.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A zero-copy view over Rust-owned OcelEventC array.
 *
 * <p>The underlying memory is borrowed from {@link OcelLogHandle}'s OcelLogInternal.
 * This view is valid as long as the parent OcelLogHandle is open.
 *
 * <p>{@link #get(int)} performs O(1) pointer arithmetic — no copies.
 * {@link #stream()} materializes {@link OcelEvent} records lazily.
 *
 * <p>Close this view when done to free the OcelEventsResult struct.
 */
public record OcelEventView(
    MemorySegment segment,        // array of OcelEventC, borrowed from Rust
    int           count,
    MemorySegment resultSegment,  // OcelEventsResult — freed on close
    Rust4pmBridge bridge
) implements AutoCloseable {

    /**
     * Random access — O(1) offset arithmetic into Rust-owned array.
     * Materializes one OcelEvent record by reading fields from native memory.
     *
     * @param index 0-based event index
     * @return materialized OcelEvent record
     * @throws IndexOutOfBoundsException if index out of range
     */
    public OcelEvent get(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(
                "index " + index + " out of bounds for count " + count);
        }
        long stride = rust4pm_h.OCEL_EVENT_C_LAYOUT.byteSize();
        MemorySegment slice = segment.asSlice((long) index * stride, stride);
        return OcelEvent.fromSegment(slice);
    }

    /**
     * Lazy stream — materializes OcelEvent records from Rust memory on demand.
     * Does not copy the entire array; each element is materialized only when consumed.
     */
    public Stream<OcelEvent> stream() {
        return IntStream.range(0, count).mapToObj(this::get);
    }

    @Override
    public void close() {
        bridge.freeEvents(resultSegment);
    }
}
