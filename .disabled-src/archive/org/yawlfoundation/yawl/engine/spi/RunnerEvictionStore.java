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

/**
 * SPI for cold-storage of serialised {@code YNetRunner} snapshots.
 *
 * <p>When the in-memory hot-set LRU cache in {@code YNetRunnerRepository} evicts
 * a runner, it calls {@link #evict} to persist the serialised snapshot. On a cache
 * miss, {@link #restore} is called to reload the runner from cold storage.</p>
 *
 * <p><b>Default implementation</b>: {@link OffHeapRunnerStore} uses the Java 21+
 * Foreign Memory API ({@code Arena + MemorySegment}) to store snapshots off-heap.
 * Zero GC pressure: the GC never scans evicted runner data.</p>
 *
 * <p><b>Optional adapters</b>:
 * {@code PostgreSQLRunnerStore} stores snapshots as {@code BYTEA} in a
 * {@code runner_snapshots} table (in the {@code yawl-postgres-adapter} module).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see OffHeapRunnerStore
 */
public interface RunnerEvictionStore extends AutoCloseable {

    /**
     * Persists a serialised runner snapshot to cold storage.
     *
     * @param caseId   the case identifier; must not be {@code null}
     * @param snapshot the serialised runner state; must not be {@code null} or empty
     * @throws IllegalArgumentException if snapshot is empty
     */
    void evict(YIdentifier caseId, byte[] snapshot);

    /**
     * Retrieves a previously evicted runner snapshot.
     *
     * @param caseId the case identifier; must not be {@code null}
     * @return the serialised runner state
     * @throws CaseNotFoundInStoreException if no snapshot exists for the given case ID
     */
    byte[] restore(YIdentifier caseId);

    /**
     * Returns {@code true} if the store holds a snapshot for the given case.
     *
     * @param caseId the case identifier; must not be {@code null}
     * @return {@code true} if a snapshot is present
     */
    boolean contains(YIdentifier caseId);

    /**
     * Removes the snapshot for the given case from cold storage (case completed/cancelled).
     *
     * @param caseId the case identifier; must not be {@code null}
     */
    void remove(YIdentifier caseId);

    /**
     * Returns the number of snapshots currently held in this store.
     *
     * @return snapshot count
     */
    long size();

    /**
     * Releases all resources held by this store.
     */
    @Override
    void close();
}
