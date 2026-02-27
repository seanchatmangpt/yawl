/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.elements.state.YIdentifier;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Off-heap implementation of {@link RunnerEvictionStore} using the Java 21+
 * Foreign Memory API ({@code Arena} + {@code MemorySegment}).
 *
 * <h3>Why off-heap?</h3>
 * <p>At 1M cases × 30 KB average snapshot size = 30 GB of runner data. Storing this
 * on the Java heap would trigger continuous GC pauses and ultimately OOM. Off-heap
 * memory is not managed by the GC — it can hold 30 GB without affecting GC pause times
 * (target p99 &lt; 5ms with ZGC).</p>
 *
 * <h3>Memory model</h3>
 * <p>A single {@link Arena#ofShared() shared Arena} is used, which means off-heap
 * memory persists until {@link #close()} is called. Each evicted runner is stored as a
 * contiguous {@link MemorySegment}; its address and length are recorded in a
 * {@link ConcurrentHashMap} index ({@code caseId → [address, length]}).</p>
 *
 * <p><b>Note</b>: The current implementation allocates a new segment per eviction.
 * Evicted-then-removed segments are freed via the Arena's deallocation mechanism.
 * Future versions may use a slab allocator to reduce Arena fragmentation.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class OffHeapRunnerStore implements RunnerEvictionStore {

    /** Shared arena — off-heap memory managed outside GC. */
    private final Arena _arena = Arena.ofShared();

    /**
     * Index: caseId.toString() → [address, length].
     * Capacity hint: 50K hot-set with 50% load factor → ~100K pre-sized.
     */
    private final ConcurrentHashMap<String, long[]> _index = new ConcurrentHashMap<>(100_000);

    private final AtomicLong _snapshotCount = new AtomicLong(0);

    /**
     * Evicts a runner snapshot to off-heap memory.
     *
     * @param caseId   the case identifier
     * @param snapshot the serialised runner state; must not be empty
     * @throws IllegalArgumentException if snapshot is empty
     * @throws IllegalStateException    if this store has been closed
     */
    @Override
    public void evict(YIdentifier caseId, byte[] snapshot) {
        if (snapshot == null || snapshot.length == 0) {
            throw new IllegalArgumentException("snapshot must not be null or empty for case: " + caseId);
        }
        // Allocate off-heap segment and copy snapshot bytes
        MemorySegment seg = _arena.allocate(snapshot.length);
        MemorySegment.copy(snapshot, 0, seg, ValueLayout.JAVA_BYTE, 0, snapshot.length);
        long[] entry = {seg.address(), snapshot.length};

        // If there was a previous snapshot for this case, record it (future: free old segment)
        _index.put(caseId.toString(), entry);
        _snapshotCount.incrementAndGet();
    }

    /**
     * Restores a runner snapshot from off-heap memory.
     *
     * @param caseId the case identifier
     * @return the serialised runner bytes
     * @throws CaseNotFoundInStoreException if no snapshot exists for the case
     */
    @Override
    public byte[] restore(YIdentifier caseId) {
        long[] entry = _index.get(caseId.toString());
        if (entry == null) {
            throw new CaseNotFoundInStoreException(caseId);
        }
        long address = entry[0];
        int length = (int) entry[1];
        // Reinterpret the raw address as a MemorySegment for safe reading
        MemorySegment seg = MemorySegment.ofAddress(address)
                .reinterpret(length, _arena, null);
        byte[] snapshot = new byte[length];
        MemorySegment.copy(seg, ValueLayout.JAVA_BYTE, 0, snapshot, 0, length);
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(YIdentifier caseId) {
        return _index.containsKey(caseId.toString());
    }

    /**
     * Removes the snapshot for a completed/cancelled case.
     * The off-heap memory is freed when the Arena is closed; individual segment
     * deallocation is not supported by {@code Arena.ofShared()} without an allocator.
     *
     * @param caseId the case identifier
     */
    @Override
    public void remove(YIdentifier caseId) {
        long[] removed = _index.remove(caseId.toString());
        if (removed != null) {
            _snapshotCount.decrementAndGet();
            // Note: individual segment memory is reclaimed on Arena.close().
            // For long-running servers, consider periodic arena rotation.
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long size() {
        return _snapshotCount.get();
    }

    /**
     * Closes the shared Arena, releasing ALL off-heap memory allocated by this store.
     * After this call the store must not be used.
     */
    @Override
    public void close() {
        _index.clear();
        _arena.close();
    }
}
