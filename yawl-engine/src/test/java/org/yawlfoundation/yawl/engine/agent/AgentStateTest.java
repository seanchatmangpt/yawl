package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for AgentState immutable record.
 * Tests lifecycle management, heartbeat renewal, and health monitoring.
 *
 * @since Java 21
 */
@DisplayName("AgentState Lifecycle Tests")
class AgentStateTest {

    private UUID agentId;

    @BeforeEach
    void setUp() {
        agentId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionTests {

        @Test
        @DisplayName("Create agent with RUNNING status")
        void testCreateRunningAgent() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);

            assertNotNull(state);
            assertEquals(agentId, state.agentId());
            assertTrue(state.status() instanceof AgentStatus.Running);
            assertFalse(state.isExpired());
            assertTrue(state.isHealthy());
        }

        @Test
        @DisplayName("Create agent with IDLE status")
        void testCreateIdleAgent() {
            AgentState state = AgentState.create(agentId, AgentStatus.idle(), 60);

            assertNotNull(state);
            assertEquals(agentId, state.agentId());
            assertTrue(state.status() instanceof AgentStatus.Idle);
            assertTrue(state.isHealthy());
        }

        @Test
        @DisplayName("Reject null agent ID")
        void testNullAgentId() {
            assertThrows(NullPointerException.class, () ->
                    AgentState.create(null, AgentStatus.running(), 60)
            );
        }

        @Test
        @DisplayName("Reject null status")
        void testNullStatus() {
            assertThrows(NullPointerException.class, () ->
                    new AgentState(agentId, null, Instant.now(), Instant.now(), Instant.now())
            );
        }

        @Test
        @DisplayName("Create agent with full constructor")
        void testFullConstructor() {
            Instant now = Instant.now();
            Instant expires = now.plusSeconds(60);
            AgentState state = new AgentState(agentId, AgentStatus.running(), now, now, expires);

            assertEquals(agentId, state.agentId());
            assertEquals(now, state.registeredAt());
            assertEquals(now, state.lastHeartbeat());
            assertEquals(expires, state.ttlExpires());
        }
    }

    @Nested
    @DisplayName("Health Monitoring")
    class HealthMonitoringTests {

        @Test
        @DisplayName("Healthy agent shows positive TTL")
        void testHealthyAgentPositiveTtl() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);

            assertTrue(state.isHealthy());
            assertTrue(state.getRemainingTtlMillis() > 0);
        }

        @Test
        @DisplayName("Expired agent shows zero TTL")
        void testExpiredAgentZeroTtl() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 0);

            // Wait a bit to ensure expiration
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertTrue(state.isExpired());
            assertEquals(0, state.getRemainingTtlMillis());
        }

        @Test
        @DisplayName("Failed agent is never healthy")
        void testFailedAgentNeverHealthy() {
            AgentStatus failed = AgentStatus.failed("Database connection lost");
            AgentState state = AgentState.create(agentId, failed, 60);

            assertFalse(state.isHealthy());
            assertTrue(state.status() instanceof AgentStatus.Failed);
        }

        @Test
        @DisplayName("Calculate remaining TTL correctly")
        void testRemainingTtlCalculation() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);
            long ttl = state.getRemainingTtlMillis();

            // Should be approximately 60 seconds (allow 1 second margin for execution time)
            assertTrue(ttl > 58_000, "TTL should be > 58 seconds");
            assertTrue(ttl <= 60_000, "TTL should be <= 60 seconds");
        }
    }

    @Nested
    @DisplayName("Heartbeat Renewal")
    class HeartbeatRenewalTests {

        @Test
        @DisplayName("Renew heartbeat extends expiration")
        void testRenewHeartbeatExtends() {
            AgentState initial = AgentState.create(agentId, AgentStatus.running(), 30);
            long initialTtl = initial.getRemainingTtlMillis();

            // Wait a bit
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            AgentState renewed = initial.renewHeartbeat(60);
            long renewedTtl = renewed.getRemainingTtlMillis();

            // Renewed TTL should be longer than initial TTL
            assertTrue(renewedTtl > initialTtl, "Renewed TTL should extend expiration");
            assertEquals(initial.agentId(), renewed.agentId());
        }

        @Test
        @DisplayName("Renew updates lastHeartbeat timestamp")
        void testRenewUpdatesHeartbeat() {
            AgentState initial = AgentState.create(agentId, AgentStatus.running(), 30);
            Instant originalHeartbeat = initial.lastHeartbeat();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            AgentState renewed = initial.renewHeartbeat(60);
            Instant newHeartbeat = renewed.lastHeartbeat();

            assertTrue(newHeartbeat.isAfter(originalHeartbeat),
                    "lastHeartbeat should be updated");
        }

        @Test
        @DisplayName("Repeated heartbeats keep agent alive")
        void testRepeatedHeartbeatsKeepAlive() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 10);

            for (int i = 0; i < 5; i++) {
                assertTrue(state.isHealthy(), "Agent should be healthy after renewal " + i);
                state = state.renewHeartbeat(10);
            }

            assertTrue(state.isHealthy(), "Agent should remain healthy after repeated renewals");
        }

        @Test
        @DisplayName("Heartbeat renewal preserves registration time")
        void testRenewalPreservesRegistration() {
            AgentState initial = AgentState.create(agentId, AgentStatus.running(), 30);
            Instant registeredAt = initial.registeredAt();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            AgentState renewed = initial.renewHeartbeat(60);

            assertEquals(registeredAt, renewed.registeredAt(),
                    "Registration time should not change on renewal");
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    class StatusTransitionTests {

        @Test
        @DisplayName("Transition from RUNNING to IDLE")
        void testTransitionToIdle() {
            AgentState running = AgentState.create(agentId, AgentStatus.running(), 60);
            AgentState idle = running.withStatus(AgentStatus.idle());

            assertTrue(running.status() instanceof AgentStatus.Running);
            assertTrue(idle.status() instanceof AgentStatus.Idle);
            assertEquals(running.agentId(), idle.agentId());
            assertTrue(idle.isHealthy());
        }

        @Test
        @DisplayName("Transition to FAILED status")
        void testTransitionToFailed() {
            AgentState initial = AgentState.create(agentId, AgentStatus.running(), 60);
            AgentStatus failed = AgentStatus.failed("Network unreachable");
            AgentState failedState = initial.withStatus(failed);

            assertFalse(failedState.isHealthy());
            assertTrue(failedState.status() instanceof AgentStatus.Failed);
            AgentStatus.Failed failedStatus = (AgentStatus.Failed) failedState.status();
            assertEquals("Network unreachable", failedStatus.reason());
        }

        @Test
        @DisplayName("Status transition preserves other fields")
        void testStatusTransitionPreservesFields() {
            AgentState initial = AgentState.create(agentId, AgentStatus.running(), 60);
            Instant originalRegistration = initial.registeredAt();
            Instant originalHeartbeat = initial.lastHeartbeat();

            AgentState transitioned = initial.withStatus(AgentStatus.idle());

            assertEquals(originalRegistration, transitioned.registeredAt());
            assertEquals(originalHeartbeat, transitioned.lastHeartbeat());
            assertEquals(initial.ttlExpires(), transitioned.ttlExpires());
        }
    }

    @Nested
    @DisplayName("Uptime Tracking")
    class UptimeTrackingTests {

        @Test
        @DisplayName("Uptime increases after creation")
        void testUptimeIncreases() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);
            long uptimeInitial = state.getUptimeMillis();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long uptimeAfterWait = state.getUptimeMillis();

            assertTrue(uptimeAfterWait > uptimeInitial,
                    "Uptime should increase over time");
        }

        @Test
        @DisplayName("Uptime reflects time since registration")
        void testUptimeAccuracy() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);
            long uptime = state.getUptimeMillis();

            // Uptime should be close to 0 (within 100ms)
            assertTrue(uptime >= 0);
            assertTrue(uptime < 100, "Uptime should be minimal right after creation");
        }
    }

    @Nested
    @DisplayName("Immutability and Equality")
    class ImmutabilityTests {

        @Test
        @DisplayName("Record provides value-based equality")
        void testValueEquality() {
            Instant now = Instant.now();
            Instant expires = now.plusSeconds(60);

            AgentState state1 = new AgentState(agentId, AgentStatus.running(), now, now, expires);
            AgentState state2 = new AgentState(agentId, AgentStatus.running(), now, now, expires);

            assertEquals(state1, state2);
            assertEquals(state1.hashCode(), state2.hashCode());
        }

        @Test
        @DisplayName("Different agents are not equal")
        void testDifferentAgentsNotEqual() {
            UUID otherAgentId = UUID.randomUUID();
            AgentState state1 = AgentState.create(agentId, AgentStatus.running(), 60);
            AgentState state2 = AgentState.create(otherAgentId, AgentStatus.running(), 60);

            assertNotEquals(state1, state2);
        }

        @Test
        @DisplayName("Record methods provide consistent toString")
        void testToString() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);
            String str = state.toString();

            assertNotNull(str);
            assertTrue(str.contains("AgentState"));
            assertTrue(str.contains(agentId.toString()));
            assertTrue(str.contains("RUNNING"));
        }
    }

    @Nested
    @DisplayName("Stress and Edge Cases")
    class StressTests {

        @Test
        @DisplayName("Handle rapid heartbeat renewals")
        void testRapidHeartbeatRenewals() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 60);

            for (int i = 0; i < 100; i++) {
                state = state.renewHeartbeat(60);
                assertTrue(state.isHealthy());
            }
        }

        @Test
        @DisplayName("Handle status transitions with different TTLs")
        void testStatusTransitionsVaryingTtl() {
            AgentState state = AgentState.create(agentId, AgentStatus.running(), 10);

            state = state.withStatus(AgentStatus.idle()).renewHeartbeat(60);
            state = state.withStatus(AgentStatus.running()).renewHeartbeat(30);
            state = state.withStatus(AgentStatus.idle()).renewHeartbeat(120);

            assertTrue(state.isHealthy());
        }
    }

    @Nested
    @DisplayName("Failure Scenario Tests")
    class FailureScenarioTests {

        @Test
        @DisplayName("Detect failures with detailed reason")
        void testFailureWithReason() {
            String reason = "Database connection timeout after 5000ms";
            AgentStatus failed = AgentStatus.failed(reason);
            AgentState state = AgentState.create(agentId, failed, 60);

            assertFalse(state.isHealthy());
            AgentStatus.Failed failureStatus = (AgentStatus.Failed) state.status();
            assertEquals(reason, failureStatus.reason());
        }

        @Test
        @DisplayName("Failed status persists through heartbeat renewal")
        void testFailurePersistsThroughRenewal() {
            AgentState initial = AgentState.create(agentId, AgentStatus.failed("Error"), 60);
            AgentState renewed = initial.renewHeartbeat(60);

            assertFalse(renewed.isHealthy());
            assertTrue(renewed.status() instanceof AgentStatus.Failed);
        }

        @Test
        @DisplayName("Can recover from FAILED to IDLE")
        void testRecoveryFromFailure() {
            AgentState failed = AgentState.create(agentId, AgentStatus.failed("Temporary error"), 60);
            assertFalse(failed.isHealthy());

            AgentState recovered = failed
                    .withStatus(AgentStatus.idle())
                    .renewHeartbeat(60);

            assertTrue(recovered.isHealthy());
            assertTrue(recovered.status() instanceof AgentStatus.Idle);
        }
    }
}
