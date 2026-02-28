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

package org.yawlfoundation.yawl.dspy;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe LRU cache for compiled DSPy programs.
 *
 * <p>Stores compiled DSPy program modules by cache key (program name + source hash).
 * When the cache reaches max capacity, the least-recently-used entry is evicted.
 * All operations are thread-safe using {@link ReadWriteLock}.</p>
 *
 * <h2>Cache Key</h2>
 * <p>The cache key is derived from {@link DspyProgram#cacheKey()}, which combines
 * the program name and SHA-256 source hash. This ensures that two programs with
 * the same name but different source are treated as separate cache entries.</p>
 *
 * <h2>LRU Eviction</h2>
 * <p>When the cache is full and a new entry is inserted, the least-recently-used
 * entry is automatically evicted. Both get and put operations update the access order.</p>
 *
 * <h2>Configuration</h2>
 * <p>Maximum cache size is fixed at 100 entries. This is configurable via constructor,
 * but the default (100) is suitable for most workflows.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. Multiple threads may concurrently call
 * get(), put(), contains(), and clear() without external synchronization.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * DspyProgramCache cache = new DspyProgramCache();
 *
 * // First compilation (cache miss)
 * if (!cache.contains(cacheKey)) {
 *     String compiledVar = compileProgram();
 *     cache.put(cacheKey, compiledVar);
 * }
 *
 * // Retrieve compiled module (cache hit)
 * String compiledVar = cache.get(cacheKey);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyProgramCache {

    private static final Logger log = LoggerFactory.getLogger(DspyProgramCache.class);

    private static final int DEFAULT_MAX_SIZE = 100;

    private final int maxSize;
    private final Map<String, String> lruCache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Creates a new cache with default maximum size (100 entries).
     */
    public DspyProgramCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a new cache with a custom maximum size.
     *
     * @param maxSize the maximum number of entries; must be positive
     * @throws IllegalArgumentException if maxSize <= 0
     */
    public DspyProgramCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive, got: " + maxSize);
        }
        this.maxSize = maxSize;
        // LinkedHashMap with access-order (LRU) enabled
        this.lruCache = new LinkedHashMap<String, String>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                boolean shouldRemove = size() > DspyProgramCache.this.maxSize;
                if (shouldRemove) {
                    log.debug("Evicting LRU entry from DSPy cache: {}", eldest.getKey());
                }
                return shouldRemove;
            }
        };
        log.info("DspyProgramCache initialized: maxSize={}", maxSize);
    }

    /**
     * Retrieves a compiled DSPy program from the cache.
     *
     * <p>Accessing an entry updates its position in the LRU order, making it
     * the most recently used entry.</p>
     *
     * @param cacheKey the cache key (program name + source hash); must not be null
     * @return the cached variable name of the compiled module, or null if not found
     */
    public @Nullable String get(String cacheKey) {
        lock.readLock().lock();
        try {
            String value = lruCache.get(cacheKey);
            if (value != null) {
                log.debug("DSPy cache hit: {}", cacheKey);
            }
            return value;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Stores a compiled DSPy program in the cache.
     *
     * <p>If the cache is at max capacity, the least-recently-used entry is evicted.</p>
     *
     * @param cacheKey      the cache key (program name + source hash); must not be null
     * @param compiledModule the variable name of the compiled module; must not be null
     * @throws NullPointerException if either parameter is null
     */
    public void put(String cacheKey, String compiledModule) {
        if (cacheKey == null || compiledModule == null) {
            throw new NullPointerException("cacheKey and compiledModule must not be null");
        }
        lock.writeLock().lock();
        try {
            int sizeBefore = lruCache.size();
            lruCache.put(cacheKey, compiledModule);
            int sizeAfter = lruCache.size();
            log.debug("DSPy cache put: key={}, size={} -> {}", cacheKey, sizeBefore, sizeAfter);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Checks if a cache entry exists.
     *
     * <p>This is a non-mutating operation that doesn't update LRU order.</p>
     *
     * @param cacheKey the cache key; must not be null
     * @return true if the key exists in the cache, false otherwise
     */
    public boolean contains(String cacheKey) {
        lock.readLock().lock();
        try {
            return lruCache.containsKey(cacheKey);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the number of entries currently in the cache.
     *
     * @return the current cache size
     */
    public int size() {
        lock.readLock().lock();
        try {
            return lruCache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the maximum cache size.
     *
     * @return the max size configured at initialization
     */
    public int maxSize() {
        return maxSize;
    }

    /**
     * Clears all entries from the cache.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            int sizeBefore = lruCache.size();
            lruCache.clear();
            log.info("DSPy program cache cleared: {} entries removed", sizeBefore);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns a snapshot of the current cache contents.
     *
     * <p>The returned map is a copy and reflects the cache state at the time
     * of the call. Modifications to the returned map do not affect the cache.</p>
     *
     * @return a copy of the cache as a map; never null
     */
    public Map<String, String> snapshot() {
        lock.readLock().lock();
        try {
            return new LinkedHashMap<>(lruCache);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns cache statistics as a summary string.
     *
     * @return a human-readable summary of cache state
     */
    public String stats() {
        lock.readLock().lock();
        try {
            return String.format("DSPyCache[size=%d, maxSize=%d, utilization=%.1f%%]",
                    lruCache.size(), maxSize, (100.0 * lruCache.size() / maxSize));
        } finally {
            lock.readLock().unlock();
        }
    }
}
