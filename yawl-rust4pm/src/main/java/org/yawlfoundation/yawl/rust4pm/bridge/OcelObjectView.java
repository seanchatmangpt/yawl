package org.yawlfoundation.yawl.rust4pm.bridge;

import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.OcelObject;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A zero-copy view over a Rust-owned {@code OcelObjectC} array.
 *
 * <p>Symmetric with {@link OcelEventView}. The backing memory is borrowed from
 * the parent {@link OcelLogHandle}'s {@code OcelLogInternal} and associated with
 * the handle's {@code ownedArena}.
 *
 * <p><b>Correct-by-construction lifetime</b>: accessing {@link #get(int)} or
 * {@link #stream()} after the parent {@link OcelLogHandle} is closed throws
 * {@link IllegalStateException} — enforced by Panama, not by developer discipline.
 *
 * <p><b>Memory ownership</b>: the objects array is BORROWED from Rust.
 * {@link #close()} does not free any native memory. Native memory is freed when
 * the parent {@link OcelLogHandle} is closed.
 *
 * <p><b>Idempotent close</b>: safe to call multiple times.
 */
public final class OcelObjectView implements AutoCloseable {

    private final MemorySegment  segment;   // borrowed from OcelLogInternal, scoped to handle's arena
    private final int            count;
    private final AtomicBoolean  closed = new AtomicBoolean(false);

    OcelObjectView(MemorySegment segment, int count) {
        this.segment = segment;
        this.count   = count;
    }

    /** Number of objects in this view. */
    public int count() { return count; }

    /**
     * Random access — O(1) offset arithmetic into Rust-owned array.
     * Materializes one {@link OcelObject} record by reading fields from native memory.
     *
     * @param index 0-based object index
     * @return materialized OcelObject record
     * @throws IndexOutOfBoundsException if index out of range
     * @throws IllegalStateException if parent OcelLogHandle has been closed
     */
    public OcelObject get(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for count " + count);
        }
        long stride = rust4pm_h.OCEL_OBJECT_C_LAYOUT.byteSize();
        // segment.asSlice() checks arena liveness → throws IllegalStateException if handle closed
        MemorySegment slice = segment.asSlice((long) index * stride, stride);
        return OcelObject.fromSegment(slice);
    }

    /**
     * Lazy stream — materializes {@link OcelObject} records from Rust memory on demand.
     *
     * @throws IllegalStateException if parent OcelLogHandle has been closed (on terminal operation)
     */
    public Stream<OcelObject> stream() {
        return IntStream.range(0, count).mapToObj(this::get);
    }

    /**
     * Logical close only. Does not free native memory. Idempotent.
     */
    @Override
    public void close() {
        closed.compareAndSet(false, true);
    }
}
