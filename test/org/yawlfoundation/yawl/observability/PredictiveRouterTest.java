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

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Tests PredictiveRouter with real agent metrics tracking.
 * No mocks, real concurrent data structures and timing.
 */
@DisplayName("PredictiveRouter: Autonomous Agent Learning")
class PredictiveRouterTest {

    private PredictiveRouter router;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        router = new PredictiveRouter(meterRegistry);
    }

    @Test
    @DisplayName("Should register agents successfully")
    void testRegisterAgent() {
        router.registerAgent("agent-1");
        router.registerAgent("agent-2");

        Map<String, Map<String, Object>> stats = router.getRoutingStats();
        assertEquals(2, stats.size());
        assertTrue(stats.containsKey("agent-1"));
        assertTrue(stats.containsKey("agent-2"));
    }

    @Test
    @DisplayName("Should route via fallback when no learning data")
    void testFallbackRouting_NoData() {
        router.registerAgent("agent-1");
        router.registerAgent("agent-2");

        String routed = router.predictBestAgent("task-1");
        assertNotNull(routed);
        assertTrue(routed.equals("agent-1") || routed.equals("agent-2"));
    }

    @Test
    @DisplayName("Should learn and route to fastest agent")
    void testPredictiveRouting_FastestAgent() {
        router.registerAgent("slow-agent");
        router.registerAgent("fast-agent");

        // Train slow-agent: 500ms average
        for (int i = 0; i < 10; i++) {
            router.recordTaskCompletion("slow-agent", "task-1", 500);
        }

        // Train fast-agent: 100ms average
        for (int i = 0; i < 10; i++) {
            router.recordTaskCompletion("fast-agent", "task-1", 100);
        }

        // Should predict fast-agent
        String predicted = router.predictBestAgent("task-1");
        assertEquals("fast-agent", predicted);
    }

    @Test
    @DisplayName("Should track task failures")
    void testFailureTracking() {
        router.registerAgent("agent-1");

        router.recordTaskCompletion("agent-1", "task-1", 100);
        router.recordTaskCompletion("agent-1", "task-1", 100);
        router.recordTaskFailure("agent-1", "task-1");

        Map<String, Map<String, Object>> stats = router.getRoutingStats();
        Map<String, Object> agentStats = stats.get("agent-1");

        double successRate = (double) agentStats.get("successRate");
        assertTrue(successRate < 1.0 && successRate > 0.5);
    }

    @Test
    @DisplayName("Should calculate EWMA correctly")
    void testEWMACalculation() {
        router.registerAgent("agent-1");

        // Record consistent 100ms completions
        for (int i = 0; i < 20; i++) {
            router.recordTaskCompletion("agent-1", "task-1", 100);
        }

        Map<String, Map<String, Object>> stats = router.getRoutingStats();
        Map<String, Object> agentStats = stats.get("agent-1");
        double ewma = (double) agentStats.get("ewmaCompletionMs");

        // Should be very close to 100
        assertTrue(ewma >= 95 && ewma <= 105);
    }

    @Test
    @DisplayName("Should route to different agents via fallback")
    void testRoundRobinFallback() {
        router.registerAgent("agent-1");
        router.registerAgent("agent-2");

        // Without training data, should distribute evenly
        Set<String> routed = new java.util.HashSet<>();
        for (int i = 0; i < 10; i++) {
            routed.add(router.predictBestAgent("task-" + i));
        }

        assertEquals(2, routed.size());
    }

    @Test
    @DisplayName("Should handle unknown agents gracefully")
    void testUnknownAgentHandling() {
        router.registerAgent("agent-1");

        // Try to record completion for unknown agent
        router.recordTaskCompletion("unknown-agent", "task-1", 100);

        Map<String, Map<String, Object>> stats = router.getRoutingStats();
        assertFalse(stats.containsKey("unknown-agent"));
    }

    @Test
    @DisplayName("Should require agents to be registered")
    void testNoAgentsThrowsException() {
        assertThrows(IllegalStateException.class, () ->
                router.predictBestAgent("task-1"));
    }

    @Test
    @DisplayName("Should track min/max completion times")
    void testMinMaxTracking() {
        router.registerAgent("agent-1");

        router.recordTaskCompletion("agent-1", "task-1", 50);
        router.recordTaskCompletion("agent-1", "task-1", 200);
        router.recordTaskCompletion("agent-1", "task-1", 150);

        Map<String, Map<String, Object>> stats = router.getRoutingStats();
        Map<String, Object> agentStats = stats.get("agent-1");

        assertEquals(50L, agentStats.get("minMs"));
        assertEquals(200L, agentStats.get("maxMs"));
    }

    @Test
    @DisplayName("Should update routing as performance data arrives")
    void testDynamicRouting() {
        router.registerAgent("agent-1");
        router.registerAgent("agent-2");

        // Initial: agent-1 is fast
        for (int i = 0; i < 10; i++) {
            router.recordTaskCompletion("agent-1", "task-1", 100);
            router.recordTaskCompletion("agent-2", "task-1", 500);
        }

        assertEquals("agent-1", router.predictBestAgent("task-1"));

        // Agent-2 becomes faster
        for (int i = 0; i < 10; i++) {
            router.recordTaskCompletion("agent-2", "task-1", 50);
        }

        assertEquals("agent-2", router.predictBestAgent("task-1"));
    }

    @Test
    @DisplayName("Should handle concurrent task routing")
    void testConcurrentRouting() throws InterruptedException {
        router.registerAgent("agent-1");
        router.registerAgent("agent-2");

        for (int i = 0; i < 10; i++) {
            router.recordTaskCompletion("agent-1", "task-1", 100);
        }

        Thread[] threads = new Thread[5];
        String[] results = new String[5];

        for (int i = 0; i < 5; i++) {
            final int idx = i;
            threads[i] = new Thread(() ->
                    results[idx] = router.predictBestAgent("concurrent-task"));
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        for (String result : results) {
            assertNotNull(result);
            assertTrue(result.equals("agent-1") || result.equals("agent-2"));
        }
    }
}
