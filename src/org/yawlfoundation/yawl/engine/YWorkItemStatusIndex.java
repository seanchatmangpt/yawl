/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P3 MEDIUM - Task Scan O(N) Query Optimization: in-memory status index for
 * {@link YWorkItemRepository}.
 *
 * <h2>Problem</h2>
 * <p>{@link YWorkItemRepository#getWorkItems(YWorkItemStatus)} performs a full O(N)
 * linear scan of all work items to find items with a given status.  This is called
 * on every {@code checkOut}, {@code getEnabledWorkItems}, and health-check path.
 * At 10,000 active work items the scan dominates latency.</p>
 *
 * <h2>Solution</h2>
 * <p>A secondary index mapping {@link YWorkItemStatus} &rarr; {@code Set<String>}
 * (item IDs) maintained alongside the primary map.  Status lookups become O(1)
 * set lookups; item retrieval remains O(k) where k is the result set size (not N).</p>
 *
 * <h2>Performance target</h2>
 * <ul>
 *   <li>Before: O(N) scan - 50-200ms for 10,000 items</li>
 *   <li>After: O(1) index lookup + O(k) item retrieval - &lt;10ms p95</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>Each status bucket uses a {@link ConcurrentHashMap}-backed set.  Updates
 * (add/remove/status-change) must be called under the same lock that protects the
 * primary {@code _itemMap} in {@link YWorkItemRepository}.</p>
 *
 * <h2>Database index recommendation</h2>
 * <p>The equivalent database-level optimization for persistent deployments:</p>
 * <pre>
 *   CREATE INDEX IF NOT EXISTS idx_workitem_status ON work_item(status);
 *   ANALYZE work_item;
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YWorkItemStatusIndex {

    private static final Logger _logger = LogManager.getLogger(YWorkItemStatusIndex.class);

    /**
     * Per-status sets of work item ID strings.
     * Using {@link EnumMap} for O(1) status-based bucket lookup with minimal memory.
     */
    private final EnumMap<YWorkItemStatus, Set<String>> _index;

    /** Metrics: total indexed lookups vs linear-scan fallback avoided. */
    private volatile long _indexedLookups = 0;
    private volatile long _addedEntries   = 0;
    private volatile long _removedEntries = 0;

    public YWorkItemStatusIndex() {
        _index = new EnumMap<>(YWorkItemStatus.class);
        for (YWorkItemStatus status : YWorkItemStatus.values()) {
            // ConcurrentHashMap.newKeySet() gives a thread-safe set with O(1) add/remove/contains
            _index.put(status, ConcurrentHashMap.newKeySet());
        }
        _logger.debug("YWorkItemStatusIndex initialized for {} status buckets",
                YWorkItemStatus.values().length);
    }

    /**
     * Registers a work item ID in the index under its current status.
     * Call this when a work item is first added to the repository.
     *
     * @param itemId the work item ID string (never null)
     * @param status the initial status (never null)
     */
    public void add(String itemId, YWorkItemStatus status) {
        if (itemId == null || status == null) return;
        Set<String> bucket = _index.get(status);
        if (bucket != null) {
            bucket.add(itemId);
            _addedEntries++;
        }
    }

    /**
     * Removes a work item ID from all status buckets.
     * Call this when a work item is removed from the repository.
     *
     * @param itemId the work item ID string
     */
    public void remove(String itemId) {
        if (itemId == null) return;
        for (Set<String> bucket : _index.values()) {
            if (bucket.remove(itemId)) {
                _removedEntries++;
                return; // each item appears in exactly one bucket
            }
        }
    }

    /**
     * Updates the index when a work item's status changes.
     * Call this on every status transition (enabled &rarr; fired &rarr; executing &rarr; complete).
     *
     * @param itemId    the work item ID string
     * @param oldStatus the status the item is transitioning FROM
     * @param newStatus the status the item is transitioning TO
     */
    public void updateStatus(String itemId, YWorkItemStatus oldStatus, YWorkItemStatus newStatus) {
        if (itemId == null) return;
        if (oldStatus != null) {
            Set<String> oldBucket = _index.get(oldStatus);
            if (oldBucket != null) oldBucket.remove(itemId);
        }
        if (newStatus != null) {
            Set<String> newBucket = _index.get(newStatus);
            if (newBucket != null) newBucket.add(itemId);
        }
    }

    /**
     * Returns all work item IDs that currently have the given status.
     *
     * <p>This is an O(1) operation; building the result set is O(k) where k is the
     * number of matching items - not O(N) total items.</p>
     *
     * @param status the status to query
     * @return an unmodifiable snapshot of matching item IDs; never null
     */
    public Set<String> getItemIdsWithStatus(YWorkItemStatus status) {
        if (status == null) return Collections.emptySet();
        _indexedLookups++;
        Set<String> bucket = _index.get(status);
        if (bucket == null) return Collections.emptySet();
        // Return a snapshot to avoid ConcurrentModificationException in callers
        return new HashSet<>(bucket);
    }

    /**
     * Returns the number of items currently indexed under the given status.
     * O(1) operation.
     *
     * @param status the status to count
     * @return the count of items with that status
     */
    public int countWithStatus(YWorkItemStatus status) {
        if (status == null) return 0;
        Set<String> bucket = _index.get(status);
        return bucket == null ? 0 : bucket.size();
    }

    /**
     * Clears all entries from the index.  Call when the repository is cleared.
     */
    public void clear() {
        for (Set<String> bucket : _index.values()) {
            bucket.clear();
        }
        _logger.debug("YWorkItemStatusIndex cleared");
    }

    /**
     * Returns a diagnostic summary of index contents.
     *
     * @return a formatted multi-line string with counts per status
     */
    public String diagnostics() {
        StringBuilder sb = new StringBuilder("YWorkItemStatusIndex diagnostics:\n");
        sb.append(String.format("  indexedLookups=%d, added=%d, removed=%d%n",
                _indexedLookups, _addedEntries, _removedEntries));
        for (YWorkItemStatus status : YWorkItemStatus.values()) {
            Set<String> bucket = _index.get(status);
            if (bucket != null && !bucket.isEmpty()) {
                sb.append(String.format("  %-20s: %d items%n", status.name(), bucket.size()));
            }
        }
        return sb.toString();
    }
}
