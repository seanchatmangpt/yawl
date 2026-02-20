/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/**
 * Thread-safe cache for Greg-Verse agent instances.
 *
 * <p>Caches initialized agent instances to avoid expensive re-instantiation
 * across multiple simulation runs. Each agent is lazily initialized on first
 * request and reused for subsequent requests within the same lifecycle.</p>
 *
 * <h2>Performance Benefits</h2>
 * <ul>
 *   <li>Avoids agent reconstruction on every simulation run</li>
 *   <li>Amortizes initialization costs across multiple executions</li>
 *   <li>Reduces memory pressure from repeated allocation/GC cycles</li>
 *   <li>Thread-safe with minimal contention (read-heavy workload)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * GregVerseAgentCache cache = new GregVerseAgentCache();
 * GregVerseAgent agent = cache.getOrCreate("greg-isenberg", () -> new GregIsenbergAgent());
 * // Subsequent calls with same ID return cached instance
 * GregVerseAgent cached = cache.getOrCreate("greg-isenberg", () -> new GregIsenbergAgent());
 * assert agent == cached;
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GregVerseAgentCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseAgentCache.class);

    private final Map<String, GregVerseAgent> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock lifecycle = new ReentrantReadWriteLock();
    private volatile boolean cleared = false;

    /**
     * Gets a cached agent or creates one using the provided factory.
     *
     * <p>This method is thread-safe and uses optimistic locking. If the agent
     * is already cached, it returns the cached instance immediately. If not,
     * it calls the factory to create the agent and caches it.</p>
     *
     * @param agentId the unique agent identifier
     * @param factory the function to create the agent if not cached
     * @return the agent instance (newly created or cached)
     * @throws IllegalArgumentException if agentId is null or empty
     * @throws Exception if factory throws an exception
     */
    public GregVerseAgent getOrCreate(String agentId, Function<Void, GregVerseAgent> factory) {
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        Objects.requireNonNull(factory, "factory cannot be null");

        lifecycle.readLock().lock();
        try {
            if (cleared) {
                throw new IllegalStateException("Cache has been cleared");
            }

            // Fast path: agent already cached
            GregVerseAgent cached = cache.get(agentId);
            if (cached != null) {
                LOGGER.debug("Cache hit for agent: {}", agentId);
                return cached;
            }
        } finally {
            lifecycle.readLock().unlock();
        }

        // Slow path: create agent and cache it
        lifecycle.writeLock().lock();
        try {
            // Double-check pattern: another thread may have created it
            GregVerseAgent cached = cache.get(agentId);
            if (cached != null) {
                return cached;
            }

            if (cleared) {
                throw new IllegalStateException("Cache has been cleared");
            }

            LOGGER.info("Creating and caching agent: {}", agentId);
            GregVerseAgent agent = factory.apply(null);
            Objects.requireNonNull(agent, "factory returned null");

            cache.put(agentId, agent);
            LOGGER.debug("Cached agent: {} (total cached: {})", agentId, cache.size());

            return agent;
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    /**
     * Gets a cached agent without creating one.
     *
     * @param agentId the agent identifier
     * @return the cached agent, or null if not present
     */
    public GregVerseAgent get(String agentId) {
        lifecycle.readLock().lock();
        try {
            return cache.get(agentId);
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /**
     * Clears the cache and invalidates all cached agents.
     *
     * <p>After clearing, getOrCreate() calls will fail with IllegalStateException.
     * This is used to enforce cache lifecycle boundaries.</p>
     */
    public void clear() {
        lifecycle.writeLock().lock();
        try {
            int size = cache.size();
            cache.clear();
            cleared = true;
            LOGGER.info("Cleared agent cache ({} agents)", size);
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    /**
     * Resets the cache for a new lifecycle.
     *
     * <p>Clears all cached agents and allows new agents to be cached.</p>
     */
    public void reset() {
        lifecycle.writeLock().lock();
        try {
            cache.clear();
            cleared = false;
            LOGGER.info("Reset agent cache");
        } finally {
            lifecycle.writeLock().unlock();
        }
    }

    /**
     * Gets the current number of cached agents.
     *
     * @return the cache size
     */
    public int size() {
        lifecycle.readLock().lock();
        try {
            return cache.size();
        } finally {
            lifecycle.readLock().unlock();
        }
    }

    /**
     * Checks if the cache is empty.
     *
     * @return true if no agents are cached
     */
    public boolean isEmpty() {
        lifecycle.readLock().lock();
        try {
            return cache.isEmpty();
        } finally {
            lifecycle.readLock().unlock();
        }
    }
}
