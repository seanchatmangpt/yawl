package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for AdaptivePollingStrategy.
 * Tests exponential backoff algorithm, timeout management, and agent state tracking.
 *
 * Key test scenarios:
 * 1. Backoff progression: 1ms → 2ms → 4ms → 8ms ... → 1000ms
 * 2. Success reset: Backoff immediately resets to 1ms on successful dequeue
 * 3. Per-agent state: Multiple agents have independent backoff states
 * 4. Configuration validation: Rejects invalid timeout values
 *
 * @since Java 21
 */
@DisplayName("AdaptivePollingStrategy Tests")
class AdaptivePollingStrategyTest {

    private AdaptivePollingStrategy strategy;
    private UUID agentId;

    @BeforeEach
    void setUp() {
        strategy = new AdaptivePollingStrategy(1, 1000);
        agentId = UUID.randomUUID();
    }

    // ========== Initialization Tests ==========

    @Test
    @DisplayName("Create strategy with valid timeouts")
    void testCreateWithValidTimeouts() {
        assertNotNull(strategy, "Strategy should be created successfully");
    }

    @Test
    @DisplayName("Reject negative initial timeout")
    void testRejectNegativeInitial() {
        assertThrows(IllegalArgumentException.class, () ->
            new AdaptivePollingStrategy(-1, 1000)
        );
    }

    @Test
    @DisplayName("Reject zero initial timeout")
    void testRejectZeroInitial() {
        assertThrows(IllegalArgumentException.class, () ->
            new AdaptivePollingStrategy(0, 1000)
        );
    }

    @Test
    @DisplayName("Reject maxTimeout < initialTimeout")
    void testRejectInvertedTimeouts() {
        assertThrows(IllegalArgumentException.class, () ->
            new AdaptivePollingStrategy(100, 50)
        );
    }

    // ========== Initial Timeout Tests ==========

    @Nested
    @DisplayName("Initial Timeout Behavior")
    class InitialTimeoutTests {

        @Test
        @DisplayName("New agent starts with initial timeout")
        void testNewAgentInitialTimeout() {
            long timeout = strategy.getTimeout(agentId);
            assertEquals(1, timeout, "New agent should start with 1ms timeout");
        }

        @Test
        @DisplayName("Initial timeout matches configured value")
        void testInitialTimeoutConfigurable() {
            AdaptivePollingStrategy custom = new AdaptivePollingStrategy(10, 5000);
            long timeout = custom.getTimeout(UUID.randomUUID());
            assertEquals(10, timeout, "Should use configured initial timeout");
        }

        @Test
        @DisplayName("getTimeout does not modify state")
        void testGetTimeoutIdempotent() {
            long timeout1 = strategy.getTimeout(agentId);
            long timeout2 = strategy.getTimeout(agentId);
            assertEquals(timeout1, timeout2, "Multiple gets should return same value");
        }
    }

    // ========== Backoff Progression Tests ==========

    @Nested
    @DisplayName("Exponential Backoff Progression")
    class BackoffProgressionTests {

        @Test
        @DisplayName("Empty record doubles timeout")
        void testEmptyDoubleTimeout() {
            long initial = strategy.getTimeout(agentId);
            strategy.recordEmpty(agentId);
            long afterEmpty = strategy.getTimeout(agentId);

            assertEquals(initial * 2, afterEmpty, "Empty should double timeout");
        }

        @Test
        @DisplayName("Multiple empties create exponential backoff")
        void testMultipleEmptyExponential() {
            // Initial: 1ms
            long timeout = strategy.getTimeout(agentId);
            assertEquals(1, timeout);

            // Empty 1: 2ms
            strategy.recordEmpty(agentId);
            timeout = strategy.getTimeout(agentId);
            assertEquals(2, timeout);

            // Empty 2: 4ms
            strategy.recordEmpty(agentId);
            timeout = strategy.getTimeout(agentId);
            assertEquals(4, timeout);

            // Empty 3: 8ms
            strategy.recordEmpty(agentId);
            timeout = strategy.getTimeout(agentId);
            assertEquals(8, timeout);

            // Empty 4: 16ms
            strategy.recordEmpty(agentId);
            timeout = strategy.getTimeout(agentId);
            assertEquals(16, timeout);
        }

        @Test
        @DisplayName("Backoff is capped at max timeout")
        void testBackoffCappedAtMax() {
            // Perform many empty records to exceed max
            for (int i = 0; i < 20; i++) {
                strategy.recordEmpty(agentId);
            }

            long timeout = strategy.getTimeout(agentId);
            assertEquals(1000, timeout, "Timeout should cap at 1000ms");
        }

        @Test
        @DisplayName("Backoff remains at cap after multiple empties")
        void testBackoffStaysAtCap() {
            // Reach cap
            for (int i = 0; i < 15; i++) {
                strategy.recordEmpty(agentId);
            }

            long timeout1 = strategy.getTimeout(agentId);
            assertEquals(1000, timeout1);

            // More empties don't increase further
            strategy.recordEmpty(agentId);
            long timeout2 = strategy.getTimeout(agentId);
            assertEquals(1000, timeout2, "Timeout should remain capped");
        }
    }

    // ========== Success Reset Tests ==========

    @Nested
    @DisplayName("Success Reset Behavior")
    class SuccessResetTests {

        @Test
        @DisplayName("Successful dequeue resets to initial timeout")
        void testSuccessResetsToInitial() {
            // Build up backoff
            strategy.recordEmpty(agentId);
            strategy.recordEmpty(agentId);
            strategy.recordEmpty(agentId);

            long backedOff = strategy.getTimeout(agentId);
            assertEquals(8, backedOff, "Should be backed off to 8ms");

            // Success resets
            strategy.recordSuccess(agentId);
            long reset = strategy.getTimeout(agentId);
            assertEquals(1, reset, "Success should reset to initial 1ms");
        }

        @Test
        @DisplayName("Success from max timeout resets to initial")
        void testSuccessFromMaxResets() {
            // Reach max backoff
            for (int i = 0; i < 20; i++) {
                strategy.recordEmpty(agentId);
            }
            long atMax = strategy.getTimeout(agentId);
            assertEquals(1000, atMax);

            // Success immediately resets
            strategy.recordSuccess(agentId);
            long reset = strategy.getTimeout(agentId);
            assertEquals(1, reset, "Should reset from max to initial");
        }

        @Test
        @DisplayName("Immediate success returns initial timeout")
        void testImmediateSuccess() {
            strategy.recordSuccess(agentId);
            long timeout = strategy.getTimeout(agentId);
            assertEquals(1, timeout, "Immediate success returns initial");
        }

        @Test
        @DisplayName("Success resets backoff level to zero")
        void testSuccessResetsBackoffLevel() {
            strategy.recordEmpty(agentId);
            strategy.recordEmpty(agentId);

            int beforeLevel = strategy.getBackoffLevel(agentId);
            assertTrue(beforeLevel > 0);

            strategy.recordSuccess(agentId);

            int afterLevel = strategy.getBackoffLevel(agentId);
            assertEquals(0, afterLevel, "Backoff level should reset to 0");
        }
    }

    // ========== Backoff Level Tests ==========

    @Nested
    @DisplayName("Backoff Level Tracking")
    class BackoffLevelTests {

        @Test
        @DisplayName("Initial backoff level is zero")
        void testInitialBackoffLevelZero() {
            int level = strategy.getBackoffLevel(agentId);
            assertEquals(0, level);
        }

        @Test
        @DisplayName("Backoff level increments on empty")
        void testBackoffLevelIncrement() {
            assertEquals(0, strategy.getBackoffLevel(agentId));

            strategy.recordEmpty(agentId);
            assertEquals(1, strategy.getBackoffLevel(agentId));

            strategy.recordEmpty(agentId);
            assertEquals(2, strategy.getBackoffLevel(agentId));

            strategy.recordEmpty(agentId);
            assertEquals(3, strategy.getBackoffLevel(agentId));
        }

        @Test
        @DisplayName("Backoff level continues incrementing at max timeout")
        void testBackoffLevelContinuesAtMax() {
            // Reach max
            for (int i = 0; i < 15; i++) {
                strategy.recordEmpty(agentId);
            }

            int levelAtMax = strategy.getBackoffLevel(agentId);

            // More empties continue incrementing level (but timeout stays at max)
            strategy.recordEmpty(agentId);
            int levelAfter = strategy.getBackoffLevel(agentId);

            assertTrue(levelAfter > levelAtMax, "Level continues incrementing");
            assertEquals(1000, strategy.getTimeout(agentId), "Timeout stays at max");
        }

        @Test
        @DisplayName("Reset sets backoff level to zero")
        void testResetBackoffLevel() {
            strategy.recordEmpty(agentId);
            strategy.recordEmpty(agentId);

            int beforeReset = strategy.getBackoffLevel(agentId);
            assertTrue(beforeReset > 0);

            strategy.reset(agentId);

            int afterReset = strategy.getBackoffLevel(agentId);
            assertEquals(0, afterReset, "Reset should clear backoff level");
        }
    }

    // ========== Multi-Agent Tests ==========

    @Nested
    @DisplayName("Multi-Agent Independent State")
    class MultiAgentTests {

        @Test
        @DisplayName("Different agents have independent backoff states")
        void testIndependentAgentStates() {
            UUID agent1 = UUID.randomUUID();
            UUID agent2 = UUID.randomUUID();

            // Agent 1: build backoff
            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent1);
            long timeout1 = strategy.getTimeout(agent1);

            // Agent 2: stays at initial
            long timeout2 = strategy.getTimeout(agent2);

            assertTrue(timeout1 > timeout2, "Agents should have independent backoff");
        }

        @Test
        @DisplayName("Success on one agent does not affect others")
        void testSuccessIndependent() {
            UUID agent1 = UUID.randomUUID();
            UUID agent2 = UUID.randomUUID();

            // Both build backoff
            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent2);
            strategy.recordEmpty(agent2);

            long timeout1Before = strategy.getTimeout(agent1);
            long timeout2Before = strategy.getTimeout(agent2);
            assertEquals(4, timeout1Before);
            assertEquals(4, timeout2Before);

            // Agent 1 success
            strategy.recordSuccess(agent1);

            // Agent 1 reset, Agent 2 unchanged
            assertEquals(1, strategy.getTimeout(agent1));
            assertEquals(4, strategy.getTimeout(agent2));
        }

        @Test
        @DisplayName("Reset one agent does not affect others")
        void testResetIndependent() {
            UUID agent1 = UUID.randomUUID();
            UUID agent2 = UUID.randomUUID();

            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent2);

            strategy.reset(agent1);

            assertEquals(1, strategy.getTimeout(agent1), "Agent 1 should reset");
            assertEquals(2, strategy.getTimeout(agent2), "Agent 2 unchanged");
        }

        @Test
        @DisplayName("Track agent count increases with new agents")
        void testTrackedAgentCount() {
            strategy.resetAll(); // Clear state from other tests

            assertEquals(0, strategy.getTrackedAgentCount());

            strategy.getTimeout(UUID.randomUUID());
            assertEquals(1, strategy.getTrackedAgentCount());

            strategy.getTimeout(UUID.randomUUID());
            assertEquals(2, strategy.getTrackedAgentCount());

            strategy.getTimeout(UUID.randomUUID());
            assertEquals(3, strategy.getTrackedAgentCount());
        }
    }

    // ========== Reset Tests ==========

    @Nested
    @DisplayName("Reset Operations")
    class ResetTests {

        @Test
        @DisplayName("reset() resets single agent")
        void testResetSingleAgent() {
            strategy.recordEmpty(agentId);
            strategy.recordEmpty(agentId);

            strategy.reset(agentId);

            assertEquals(1, strategy.getTimeout(agentId));
            assertEquals(0, strategy.getBackoffLevel(agentId));
        }

        @Test
        @DisplayName("resetAll() clears all agent state")
        void testResetAllClearsState() {
            UUID agent1 = UUID.randomUUID();
            UUID agent2 = UUID.randomUUID();
            UUID agent3 = UUID.randomUUID();

            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent1);
            strategy.recordEmpty(agent2);

            strategy.resetAll();

            // All agents start fresh
            assertEquals(1, strategy.getTimeout(agent1));
            assertEquals(1, strategy.getTimeout(agent2));
            assertEquals(1, strategy.getTimeout(agent3));

            assertEquals(0, strategy.getBackoffLevel(agent1));
            assertEquals(0, strategy.getBackoffLevel(agent2));
            assertEquals(0, strategy.getBackoffLevel(agent3));
        }

        @Test
        @DisplayName("resetAll() clears agent tracking count")
        void testResetAllClearsTracking() {
            strategy.getTimeout(UUID.randomUUID());
            strategy.getTimeout(UUID.randomUUID());

            int before = strategy.getTrackedAgentCount();
            assertTrue(before > 0);

            strategy.resetAll();

            assertEquals(0, strategy.getTrackedAgentCount());
        }
    }

    // ========== Null Handling Tests ==========

    @Nested
    @DisplayName("Null Parameter Handling")
    class NullHandlingTests {

        @Test
        @DisplayName("getTimeout rejects null agent")
        void testGetTimeoutNullAgent() {
            assertThrows(NullPointerException.class, () ->
                strategy.getTimeout(null)
            );
        }

        @Test
        @DisplayName("recordSuccess rejects null agent")
        void testRecordSuccessNullAgent() {
            assertThrows(NullPointerException.class, () ->
                strategy.recordSuccess(null)
            );
        }

        @Test
        @DisplayName("recordEmpty rejects null agent")
        void testRecordEmptyNullAgent() {
            assertThrows(NullPointerException.class, () ->
                strategy.recordEmpty(null)
            );
        }

        @Test
        @DisplayName("getBackoffLevel rejects null agent")
        void testGetBackoffLevelNullAgent() {
            assertThrows(NullPointerException.class, () ->
                strategy.getBackoffLevel(null)
            );
        }

        @Test
        @DisplayName("reset rejects null agent")
        void testResetNullAgent() {
            assertThrows(NullPointerException.class, () ->
                strategy.reset(null)
            );
        }
    }

    // ========== Configuration Tests ==========

    @Nested
    @DisplayName("Configuration Scenarios")
    class ConfigurationTests {

        @Test
        @DisplayName("Very small timeout range (1ms - 10ms)")
        void testSmallTimeoutRange() {
            AdaptivePollingStrategy small = new AdaptivePollingStrategy(1, 10);

            UUID agent = UUID.randomUUID();
            small.recordEmpty(agent);
            small.recordEmpty(agent);
            small.recordEmpty(agent);
            small.recordEmpty(agent);

            long timeout = small.getTimeout(agent);
            assertEquals(10, timeout, "Should cap at max 10ms");
        }

        @Test
        @DisplayName("Large timeout range (100ms - 60000ms)")
        void testLargeTimeoutRange() {
            AdaptivePollingStrategy large = new AdaptivePollingStrategy(100, 60000);

            UUID agent = UUID.randomUUID();
            long timeout = large.getTimeout(agent);
            assertEquals(100, timeout);
        }

        @Test
        @DisplayName("Equal initial and max timeouts (no backoff)")
        void testNoBackoffConfig() {
            AdaptivePollingStrategy noBackoff = new AdaptivePollingStrategy(1, 1);

            UUID agent = UUID.randomUUID();
            noBackoff.recordEmpty(agent);
            noBackoff.recordEmpty(agent);

            long timeout = noBackoff.getTimeout(agent);
            assertEquals(1, timeout, "Max backoff should stay at initial");
        }
    }

    // ========== String Representation Tests ==========

    @Test
    @DisplayName("toString provides diagnostic information")
    void testToString() {
        String repr = strategy.toString();

        assertNotNull(repr);
        assertTrue(repr.contains("AdaptivePollingStrategy"));
        assertTrue(repr.contains("initialMs"));
        assertTrue(repr.contains("maxMs"));
    }

    @Test
    @DisplayName("toString includes tracked agent count")
    void testToStringIncludesAgentCount() {
        strategy.getTimeout(UUID.randomUUID());
        strategy.getTimeout(UUID.randomUUID());

        String repr = strategy.toString();
        assertTrue(repr.contains("trackedAgents"), "Should include agent count");
    }
}
