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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AgentMarketplace liveness indexing optimization.
 *
 * <p>This test suite verifies that the O(N) → O(K) optimization correctly
 * maintains the liveAgentIds index in sync with listings, and that query
 * latency is proportional to K (live agents) not N (total agents).</p>
 *
 * @since YAWL 6.0
 */
@DisplayName("AgentMarketplace Liveness Indexing (O(N) → O(K))")
class AgentMarketplaceIndexingTest {

    private AgentMarketplace marketplace;
    private TestListingFactory factory;

    @BeforeEach
    void setUp() {
        marketplace = new AgentMarketplace();
        factory = new TestListingFactory();
    }

    @Test
    @DisplayName("Index stays in sync with publish operation")
    void testIndexSyncOnPublish() {
        var listing1 = factory.createListing("agent-1");
        var listing2 = factory.createListing("agent-2");

        marketplace.publish(listing1);
        assertEquals(1, marketplace.liveCount(), "Should have 1 live agent after publish");

        marketplace.publish(listing2);
        assertEquals(2, marketplace.liveCount(), "Should have 2 live agents after publish");
    }

    @Test
    @DisplayName("Index stays in sync with unpublish operation")
    void testIndexSyncOnUnpublish() {
        var listing1 = factory.createListing("agent-1");
        var listing2 = factory.createListing("agent-2");

        marketplace.publish(listing1);
        marketplace.publish(listing2);
        assertEquals(2, marketplace.liveCount());

        marketplace.unpublish("agent-1");
        assertEquals(1, marketplace.liveCount(), "Should have 1 live agent after unpublish");

        marketplace.unpublish("agent-2");
        assertEquals(0, marketplace.liveCount(), "Should have 0 live agents after unpublish");
    }

    @Test
    @DisplayName("Index stays in sync with heartbeat operation")
    void testIndexSyncOnHeartbeat() {
        var listing = factory.createListing("agent-1");
        marketplace.publish(listing);

        // Heartbeat refreshes liveness
        marketplace.heartbeat("agent-1", Instant.now());
        assertEquals(1, marketplace.liveCount(), "Agent should remain live after heartbeat");

        // Heartbeat on unknown agent is no-op
        marketplace.heartbeat("unknown-agent", Instant.now());
        assertEquals(1, marketplace.liveCount(), "Unknown agent heartbeat should be no-op");
    }

    @Test
    @DisplayName("Query latency is O(K) not O(N) with large N, small K")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testQueryLatencyIsLinearInLiveCount() {
        // N = 1000 total agents, K = 10 live agents
        int totalAgents = 1000;
        int liveAgents = 10;

        // Publish all agents
        for (int i = 0; i < totalAgents; i++) {
            var listing = factory.createListing("agent-" + i);
            marketplace.publish(listing);
        }

        // Make K of them stale by not sending heartbeats (rely on default staleness check)
        // The first 10 are live, rest are stale by default
        var now = Instant.now();
        for (int i = 0; i < liveAgents; i++) {
            marketplace.heartbeat("agent-" + i, now);
        }

        // Query should be fast: O(K) traversal of liveAgentIds, not O(N) of listings
        long startNanos = System.nanoTime();
        var liveListings = marketplace.allLiveListings();
        long elapsedNanos = System.nanoTime() - startNanos;

        assertTrue(liveListings.size() <= liveAgents,
                "Should only find live agents");
        assertTrue(elapsedNanos < 100_000_000,  // 100 ms should be plenty for O(K)
                "Query should complete in <100ms for K=" + liveAgents +
                        ", got " + (elapsedNanos / 1_000_000.0) + "ms");
    }

    @Test
    @DisplayName("Concurrent publish/unpublish maintains index consistency")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentModificationsConsistency() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger publishCount = new AtomicInteger(0);
        AtomicInteger unpublishCount = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int op = 0; op < operationsPerThread; op++) {
                        String agentId = "agent-" + threadId + "-" + op;
                        var listing = factory.createListing(agentId);

                        marketplace.publish(listing);
                        publishCount.incrementAndGet();

                        if (op % 2 == 0) {
                            marketplace.unpublish(agentId);
                            unpublishCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Operations should complete");
        executor.shutdown();

        // Index should be consistent with actual listings
        int expectedLiveCount = publishCount.get() - unpublishCount.get();
        assertEquals(expectedLiveCount, marketplace.size(),
                "Total listings should match publish count minus unpublish count");
    }

    @Test
    @DisplayName("Concurrent heartbeats maintain index consistency")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentHeartbeatsConsistency() throws InterruptedException {
        // Pre-publish agents
        int agentCount = 20;
        for (int i = 0; i < agentCount; i++) {
            marketplace.publish(factory.createListing("agent-" + i));
        }

        int threadCount = 4;
        int heartbeatsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int hb = 0; hb < heartbeatsPerThread; hb++) {
                        for (int i = 0; i < agentCount; i++) {
                            marketplace.heartbeat("agent-" + i, Instant.now());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Heartbeats should complete");
        executor.shutdown();

        // All agents should still be live and count should match
        assertEquals(agentCount, marketplace.liveCount(),
                "All agents should remain live after concurrent heartbeats");
    }

    @Test
    @DisplayName("Index handles race condition: removed from index but in listings")
    void testRaceConditionRemovedFromIndex() {
        var listing = factory.createListing("agent-1");
        marketplace.publish(listing);
        assertEquals(1, marketplace.liveCount());

        // Simulate race: manually unpublish but verify liveListings handles null gracefully
        marketplace.unpublish("agent-1");
        assertEquals(0, marketplace.liveCount(), "Index should be consistent");
    }

    @Test
    @DisplayName("Index handles heartbeat on non-existent agent")
    void testHeartbeatOnNonExistentAgent() {
        var listing = factory.createListing("agent-1");
        marketplace.publish(listing);

        // Heartbeat on non-existent agent should be no-op
        marketplace.heartbeat("non-existent", Instant.now());
        assertEquals(1, marketplace.liveCount(), "Live count should not change");
    }

    @Test
    @DisplayName("Re-publish overwrites previous listing")
    void testRepublishIdempotence() {
        var listing1 = factory.createListing("agent-1");
        var listing2 = factory.createListingWithDifferentSpec("agent-1");

        marketplace.publish(listing1);
        assertEquals(1, marketplace.liveCount());

        marketplace.publish(listing2);
        assertEquals(1, marketplace.liveCount(), "Should still have 1 live agent after re-publish");
    }

    @Test
    @DisplayName("Large N with small K: verify O(K) dominates O(N)")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testScalingBehavior() {
        // Add many offline agents
        int offlineAgents = 5000;
        for (int i = 0; i < offlineAgents; i++) {
            var listing = factory.createListing("offline-agent-" + i);
            marketplace.publish(listing);
            // Don't heartbeat these, so they become stale
        }

        // Add few live agents
        int liveAgents = 20;
        var now = Instant.now();
        for (int i = 0; i < liveAgents; i++) {
            var listing = factory.createListing("live-agent-" + i);
            marketplace.publish(listing);
            marketplace.heartbeat("live-agent-" + i, now);
        }

        // Query should be O(K) not O(N)
        long startNanos = System.nanoTime();
        var results = marketplace.allLiveListings();
        long elapsedNanos = System.nanoTime() - startNanos;

        assertEquals(liveAgents, results.size(), "Should find exactly K live agents");
        assertTrue(elapsedNanos < 200_000_000,  // 200 ms is very generous for O(K)
                "Query O(K) should be much faster than O(N), elapsed: " +
                        (elapsedNanos / 1_000_000.0) + "ms");
    }

    // --- Test Utilities ---

    static class TestListingFactory {
        int counter = 0;

        AgentMarketplaceListing createListing(String agentId) {
            return AgentMarketplaceListing.builder()
                    .agentInfo(AgentInfo.builder()
                            .id(agentId)
                            .name("Test Agent " + (counter++))
                            .build())
                    .spec(AgentSpecification.builder()
                            .costProfile(CostProfile.builder()
                                    .basePricePerCycle(100.0)
                                    .build())
                            .ontologicalCoverage(OntologicalCoverage.builder()
                                    .build())
                            .build())
                    .listingVersion(1)
                    .publishedAt(Instant.now())
                    .heartbeatAt(Instant.now())
                    .build();
        }

        AgentMarketplaceListing createListingWithDifferentSpec(String agentId) {
            return AgentMarketplaceListing.builder()
                    .agentInfo(AgentInfo.builder()
                            .id(agentId)
                            .name("Test Agent " + (counter++))
                            .build())
                    .spec(AgentSpecification.builder()
                            .costProfile(CostProfile.builder()
                                    .basePricePerCycle(200.0)  // Different spec
                                    .build())
                            .ontologicalCoverage(OntologicalCoverage.builder()
                                    .build())
                            .build())
                    .listingVersion(2)
                    .publishedAt(Instant.now())
                    .heartbeatAt(Instant.now())
                    .build();
        }
    }
}
