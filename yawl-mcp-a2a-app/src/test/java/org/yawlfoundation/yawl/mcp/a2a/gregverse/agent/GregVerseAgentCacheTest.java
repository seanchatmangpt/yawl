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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.GregVerseAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.GregIsenbergAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.JamesAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.NicolasColeAgent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GregVerseAgentCache.
 *
 * <p>Tests verify cache hit/miss behavior, thread safety, and memory efficiency.
 * Uses real agent implementations from Greg-Verse ecosystem.</p>
 */
@DisplayName("GregVerse Agent Cache Tests")
class GregVerseAgentCacheTest {

    private GregVerseAgentCache cache;
    private AtomicInteger creationCount;

    @BeforeEach
    void setUp() {
        cache = new GregVerseAgentCache();
        creationCount = new AtomicInteger(0);
    }

    @Test
    @DisplayName("should cache agent after first creation")
    void testCacheHit() {
        GregVerseAgent agent1 = cache.getOrCreate("greg-isenberg", unused -> {
            creationCount.incrementAndGet();
            return new GregIsenbergAgent();
        });

        GregVerseAgent agent2 = cache.getOrCreate("greg-isenberg", unused -> {
            creationCount.incrementAndGet();
            return new GregIsenbergAgent();
        });

        assertEquals(1, creationCount.get(), "Agent should only be created once");
        assertSame(agent1, agent2, "Cached instance should be returned");
    }

    @Test
    @DisplayName("should create separate instances for different agents")
    void testSeparateAgents() {
        GregVerseAgent agent1 = cache.getOrCreate("greg-isenberg", unused -> {
            creationCount.incrementAndGet();
            return new GregIsenbergAgent();
        });

        GregVerseAgent agent2 = cache.getOrCreate("james", unused -> {
            creationCount.incrementAndGet();
            return new JamesAgent();
        });

        assertEquals(2, creationCount.get(), "Two agents should be created");
        assertNotSame(agent1, agent2, "Different agents should be different instances");
        assertEquals("greg-isenberg", agent1.getAgentId());
        assertEquals("james", agent2.getAgentId());
    }

    @Test
    @DisplayName("should handle concurrent cache requests")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();  // Wait for all threads to be ready
                    cache.getOrCreate("greg-isenberg", unused -> {
                        creationCount.incrementAndGet();
                        return new GregIsenbergAgent();
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();  // Start all threads
        endLatch.await();  // Wait for all threads to finish

        assertEquals(1, creationCount.get(), "Agent should only be created once despite concurrent access");
    }

    @Test
    @DisplayName("should return cached agent without factory call")
    void testCachedLookup() {
        cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());

        // Second request should use cached instance without calling factory
        GregVerseAgent agent = cache.getOrCreate("greg-isenberg", unused -> {
            throw new RuntimeException("Factory should not be called for cached agent");
        });

        assertNotNull(agent);
        assertEquals("greg-isenberg", agent.getAgentId());
    }

    @Test
    @DisplayName("should track cache size")
    void testCacheSize() {
        assertEquals(0, cache.size());

        cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());
        assertEquals(1, cache.size());

        cache.getOrCreate("james", unused -> new JamesAgent());
        assertEquals(2, cache.size());

        cache.getOrCreate("nicolas-cole", unused -> new NicolasColeAgent());
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("should clear all cached agents")
    void testClear() {
        cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());
        cache.getOrCreate("james", unused -> new JamesAgent());
        assertEquals(2, cache.size());

        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.isEmpty());

        // Subsequent getOrCreate should fail (cache is in cleared state)
        assertThrows(IllegalStateException.class, () ->
            cache.getOrCreate("nicolas-cole", unused -> new NicolasColeAgent()));
    }

    @Test
    @DisplayName("should reset cache for new lifecycle")
    void testReset() {
        cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());
        cache.clear();

        // After reset, cache should accept new agents
        cache.reset();
        GregVerseAgent agent = cache.getOrCreate("james", unused -> new JamesAgent());
        assertNotNull(agent);
        assertEquals(1, cache.size());
        assertEquals("james", agent.getAgentId());
    }

    @Test
    @DisplayName("should reject null agent ID")
    void testNullAgentId() {
        assertThrows(IllegalArgumentException.class, () ->
            cache.getOrCreate(null, unused -> new GregIsenbergAgent()));

        assertThrows(IllegalArgumentException.class, () ->
            cache.getOrCreate("", unused -> new GregIsenbergAgent()));
    }

    @Test
    @DisplayName("should reject null factory")
    void testNullFactory() {
        assertThrows(NullPointerException.class, () ->
            cache.getOrCreate("greg-isenberg", null));
    }

    @Test
    @DisplayName("should get cached agent without factory")
    void testGetWithoutFactory() {
        cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());

        GregVerseAgent agent = cache.get("greg-isenberg");
        assertNotNull(agent);
        assertEquals("greg-isenberg", agent.getAgentId());

        GregVerseAgent missing = cache.get("nonexistent");
        assertNull(missing);
    }

    @Test
    @DisplayName("should propagate factory exceptions")
    void testFactoryException() {
        assertThrows(RuntimeException.class, () ->
            cache.getOrCreate("bad-agent", unused -> {
                throw new RuntimeException("Factory failed");
            }));
    }

    @Test
    @DisplayName("should cache multiple real agents efficiently")
    void testMultipleRealAgents() {
        GregVerseAgent greg = cache.getOrCreate("greg-isenberg", unused -> new GregIsenbergAgent());
        GregVerseAgent james = cache.getOrCreate("james", unused -> new JamesAgent());
        GregVerseAgent nicolas = cache.getOrCreate("nicolas-cole", unused -> new NicolasColeAgent());

        assertEquals(3, cache.size());
        assertSame(greg, cache.get("greg-isenberg"));
        assertSame(james, cache.get("james"));
        assertSame(nicolas, cache.get("nicolas-cole"));

        // Verify agent functionality
        assertNotNull(greg.processQuery("test query"));
        assertNotNull(james.processQuery("test query"));
        assertNotNull(nicolas.processQuery("test query"));
    }
}
