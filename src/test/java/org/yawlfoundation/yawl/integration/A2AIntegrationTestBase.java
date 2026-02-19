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

package org.yawlfoundation.yawl.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.yawlfoundation.yawl.integration.TestConstants.*;

/**
 * Base class for A2A (Agent-to-Agent) integration tests.
 *
 * <p>Provides A2A-specific test infrastructure including:</p>
 * <ul>
 *   <li>JWT authentication provider setup</li>
 *   <li>Handoff protocol utilities</li>
 *   <li>Agent registration and discovery mocks</li>
 *   <li>A2A message validation helpers</li>
 *   <li>Test agent implementations</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * class MyA2ATest extends A2AIntegrationTestBase {
 *     @Test
 *     void testHandoffFlow() throws Exception {
 *         HandoffScenario scenario = createHandoffScenario();
 *         HandoffResult result = executeHandoff(scenario);
 *         assertSuccessful(result);
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class A2AIntegrationTestBase extends IntegrationTestBase {

    protected SecretKey jwtSigningKey;
    protected TestAgentRegistry agentRegistry;
    protected TestHandoffRouter handoffRouter;
    protected ObjectMapper objectMapper;

    // ============ Sealed A2A Result Types ============

    /**
     * Sealed interface for A2A operation results.
     */
    public sealed interface A2AResult permits
        A2AResult.HandoffSuccess,
        A2AResult.HandoffFailed,
        A2AResult.DiscoverySuccess,
        A2AResult.ValidationFailed {

        /**
         * Successful handoff result.
         *
         * @param handoffId unique handoff identifier
         * @param workItemId work item that was handed off
         * @param fromAgent source agent
         * @param toAgent target agent
         * @param timestamp completion timestamp
         * @param duration operation duration
         */
        record HandoffSuccess(
            String handoffId,
            String workItemId,
            String fromAgent,
            String toAgent,
            Instant timestamp,
            Duration duration
        ) implements A2AResult {}

        /**
         * Failed handoff result.
         *
         * @param handoffId unique handoff identifier
         * @param workItemId work item that failed handoff
         * @param errorCode error code
         * @param errorMessage error message
         * @param recoverable whether the failure is recoverable
         */
        record HandoffFailed(
            String handoffId,
            String workItemId,
            String errorCode,
            String errorMessage,
            boolean recoverable
        ) implements A2AResult {}

        /**
         * Successful agent discovery result.
         *
         * @param discoveredAgents list of discovered agents
         * @param discoveryTime time taken for discovery
         */
        record DiscoverySuccess(
            List<AgentInfo> discoveredAgents,
            Duration discoveryTime
        ) implements A2AResult {}

        /**
         * Validation failure result.
         *
         * @param field field that failed validation
         * @param value invalid value
         * @param constraint violated constraint
         */
        record ValidationFailed(
            String field,
            Object value,
            String constraint
        ) implements A2AResult {}
    }

    /**
     * Agent information record.
     *
     * @param agentId unique agent identifier
     * @param agentName display name
     * @param capabilities list of capabilities
     * @param protocols supported protocols
     * @param endpoint agent endpoint URL
     * @param registeredAt registration timestamp
     * @param status current status
     */
    public record AgentInfo(
        String agentId,
        String agentName,
        List<String> capabilities,
        List<String> protocols,
        String endpoint,
        Instant registeredAt,
        AgentStatus status
    ) {
        /**
         * Checks if agent is available for work.
         *
         * @return true if available
         */
        public boolean isAvailable() {
            return status == AgentStatus.ACTIVE || status == AgentStatus.IDLE;
        }
    }

    /**
     * Agent status enumeration.
     */
    public enum AgentStatus {
        ACTIVE, IDLE, BUSY, OFFLINE, ERROR
    }

    /**
     * Handoff message record for testing.
     *
     * @param messageId unique message identifier
     * @param workItemId work item being handed off
     * @param fromAgent source agent
     * @param toAgent target agent
     * @param sessionHandle engine session handle
     * @param reason handoff reason
     * @param priority priority level
     * @param expiresAt expiration timestamp
     * @param createdAt creation timestamp
     * @param additionalData extra data
     */
    public record HandoffMessage(
        String messageId,
        String workItemId,
        String fromAgent,
        String toAgent,
        String sessionHandle,
        String reason,
        String priority,
        Instant expiresAt,
        Instant createdAt,
        Map<String, Object> additionalData
    ) {
        /**
         * Creates a handoff message from a scenario.
         *
         * @param scenario handoff scenario
         * @return handoff message
         */
        public static HandoffMessage fromScenario(TestDataGenerator.HandoffScenario scenario) {
            return new HandoffMessage(
                UUID.randomUUID().toString(),
                scenario.workItemId(),
                scenario.fromAgent(),
                scenario.toAgent(),
                scenario.sessionHandle(),
                scenario.reason(),
                scenario.priority(),
                Instant.now().plus(scenario.ttl()),
                Instant.now(),
                Map.of()
            );
        }

        /**
         * Checks if the handoff message has expired.
         *
         * @return true if expired
         */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        /**
         * Converts to A2A protocol JSON format.
         *
         * @param mapper object mapper to use
         * @return JSON string
         */
        public String toJson(ObjectMapper mapper) {
            return TestDataGenerator.createHandoffMessage(
                workItemId, fromAgent, toAgent, additionalData
            );
        }
    }

    // ============ Lifecycle ============

    @Override
    protected void onSetUp() throws Exception {
        objectMapper = new ObjectMapper();

        // Initialize JWT signing key
        jwtSigningKey = Keys.hmacShaKeyFor(
            TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)
        );

        // Initialize agent registry
        agentRegistry = new TestAgentRegistry();

        // Initialize handoff router
        handoffRouter = new TestHandoffRouter(agentRegistry);

        // Register default test agents
        registerDefaultAgents();

        logger.info("A2A test infrastructure initialized");
    }

    @Override
    protected void onTearDown() throws Exception {
        if (handoffRouter != null) {
            handoffRouter.shutdown();
        }
        if (agentRegistry != null) {
            agentRegistry.clear();
        }
        logger.info("A2A test infrastructure shutdown");
    }

    // ============ Agent Management ============

    /**
     * Registers a test agent.
     *
     * @param agentId agent identifier
     * @param capabilities list of capabilities
     * @return registered agent info
     */
    protected AgentInfo registerAgent(String agentId, List<String> capabilities) {
        AgentInfo agent = new AgentInfo(
            agentId,
            agentId,
            capabilities,
            List.of("a2a"),
            "http://localhost:8080/agents/" + agentId,
            Instant.now(),
            AgentStatus.ACTIVE
        );
        agentRegistry.register(agent);
        return agent;
    }

    /**
     * Creates a test agent with default capabilities.
     *
     * @param agentId agent identifier
     * @return registered agent info
     */
    protected AgentInfo createTestAgent(String agentId) {
        return registerAgent(agentId, List.of("general", "handoff"));
    }

    /**
     * Discovers available agents for a capability.
     *
     * @param capability required capability
     * @return list of matching agents
     */
    protected List<AgentInfo> discoverAgents(String capability) {
        return agentRegistry.findByCapability(capability);
    }

    // ============ Handoff Operations ============

    /**
     * Creates a handoff scenario with default values.
     *
     * @return handoff scenario
     */
    protected TestDataGenerator.HandoffScenario createHandoffScenario() {
        return TestDataGenerator.HandoffScenario.defaultScenario();
    }

    /**
     * Creates a handoff scenario with custom agents.
     *
     * @param from source agent
     * @param to target agent
     * @return handoff scenario
     */
    protected TestDataGenerator.HandoffScenario createHandoffScenario(String from, String to) {
        return TestDataGenerator.HandoffScenario.withAgents(from, to);
    }

    /**
     * Executes a handoff scenario.
     *
     * @param scenario handoff scenario
     * @return handoff result
     */
    protected A2AResult executeHandoff(TestDataGenerator.HandoffScenario scenario) {
        Instant start = Instant.now();
        String handoffId = UUID.randomUUID().toString();

        try {
            // Validate scenario
            A2AResult validation = validateHandoffScenario(scenario);
            if (validation instanceof A2AResult.ValidationFailed failed) {
                return failed;
            }

            // Create and route message
            HandoffMessage message = HandoffMessage.fromScenario(scenario);
            handoffRouter.route(message);

            // Return success
            return new A2AResult.HandoffSuccess(
                handoffId,
                scenario.workItemId(),
                scenario.fromAgent(),
                scenario.toAgent(),
                Instant.now(),
                Duration.between(start, Instant.now())
            );
        } catch (Exception e) {
            return new A2AResult.HandoffFailed(
                handoffId,
                scenario.workItemId(),
                "HANDOFF_ERROR",
                e.getMessage(),
                true
            );
        }
    }

    /**
     * Validates a handoff scenario.
     *
     * @param scenario scenario to validate
     * @return validation result
     */
    protected A2AResult validateHandoffScenario(TestDataGenerator.HandoffScenario scenario) {
        // Check work item ID format
        if (!scenario.workItemId().matches(WORK_ITEM_ID_PATTERN)) {
            return new A2AResult.ValidationFailed(
                "workItemId",
                scenario.workItemId(),
                "Must match pattern: " + WORK_ITEM_ID_PATTERN
            );
        }

        // Check agent IDs
        if (!scenario.fromAgent().matches(AGENT_ID_PATTERN)) {
            return new A2AResult.ValidationFailed(
                "fromAgent",
                scenario.fromAgent(),
                "Must match pattern: " + AGENT_ID_PATTERN
            );
        }

        if (!scenario.toAgent().matches(AGENT_ID_PATTERN)) {
            return new A2AResult.ValidationFailed(
                "toAgent",
                scenario.toAgent(),
                "Must match pattern: " + AGENT_ID_PATTERN
            );
        }

        // Check source != target
        if (scenario.fromAgent().equals(scenario.toAgent())) {
            return new A2AResult.ValidationFailed(
                "toAgent",
                scenario.toAgent(),
                "Source and target agents must be different"
            );
        }

        return new A2AResult.HandoffSuccess(
            "", scenario.workItemId(), scenario.fromAgent(), scenario.toAgent(),
            Instant.now(), Duration.ZERO
        );
    }

    // ============ JWT Utilities ============

    /**
     * Generates a test JWT token.
     *
     * @param agentId agent identifier
     * @param expirationMinutes minutes until expiration
     * @return JWT token string
     */
    protected String generateJwtToken(String agentId, long expirationMinutes) {
        return Jwts.builder()
            .subject(agentId)
            .claim("agent_id", agentId)
            .claim("type", "handoff")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMinutes * 60 * 1000))
            .signWith(jwtSigningKey)
            .compact();
    }

    /**
     * Validates a JWT token.
     *
     * @param token token to validate
     * @return agent ID if valid
     */
    protected String validateJwtToken(String token) {
        return Jwts.parser()
            .verifyWith(jwtSigningKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    // ============ Assertions ============

    /**
     * Asserts that a handoff was successful.
     *
     * @param result handoff result
     */
    protected void assertSuccessful(A2AResult result) {
        assertTrue(result instanceof A2AResult.HandoffSuccess,
            "Expected successful handoff but got: " + result.getClass().getSimpleName());
    }

    /**
     * Asserts that a handoff failed.
     *
     * @param result handoff result
     */
    protected void assertFailed(A2AResult result) {
        assertTrue(result instanceof A2AResult.HandoffFailed,
            "Expected failed handoff but got: " + result.getClass().getSimpleName());
    }

    /**
     * Asserts that an agent is registered.
     *
     * @param agentId agent identifier
     */
    protected void assertAgentRegistered(String agentId) {
        assertTrue(agentRegistry.isRegistered(agentId),
            "Agent should be registered: " + agentId);
    }

    /**
     * Asserts that an agent has a specific capability.
     *
     * @param agentId agent identifier
     * @param capability required capability
     */
    protected void assertAgentHasCapability(String agentId, String capability) {
        AgentInfo agent = agentRegistry.get(agentId);
        assertNotNull(agent, "Agent should exist: " + agentId);
        assertTrue(agent.capabilities().contains(capability),
            "Agent should have capability: " + capability);
    }

    // ============ Private Methods ============

    private void registerDefaultAgents() {
        createTestAgent(DEFAULT_SOURCE_AGENT);
        createTestAgent(DEFAULT_TARGET_AGENT);
    }

    // ============ Test Infrastructure Classes ============

    /**
     * In-memory agent registry for testing.
     */
    protected static class TestAgentRegistry {
        private final Map<String, AgentInfo> agents = new ConcurrentHashMap<>();
        private final Map<String, List<String>> capabilityIndex = new ConcurrentHashMap<>();

        public void register(AgentInfo agent) {
            agents.put(agent.agentId(), agent);
            for (String capability : agent.capabilities()) {
                capabilityIndex.computeIfAbsent(capability, _ -> new CopyOnWriteArrayList<>())
                    .add(agent.agentId());
            }
        }

        public void unregister(String agentId) {
            AgentInfo agent = agents.remove(agentId);
            if (agent != null) {
                for (String capability : agent.capabilities()) {
                    capabilityIndex.getOrDefault(capability, List.of()).remove(agentId);
                }
            }
        }

        public AgentInfo get(String agentId) {
            return agents.get(agentId);
        }

        public boolean isRegistered(String agentId) {
            return agents.containsKey(agentId);
        }

        public List<AgentInfo> findByCapability(String capability) {
            List<String> agentIds = capabilityIndex.getOrDefault(capability, List.of());
            return agentIds.stream()
                .map(agents::get)
                .filter(Objects::nonNull)
                .filter(AgentInfo::isAvailable)
                .toList();
        }

        public List<AgentInfo> getAll() {
            return new ArrayList<>(agents.values());
        }

        public void clear() {
            agents.clear();
            capabilityIndex.clear();
        }

        public void updateStatus(String agentId, AgentStatus status) {
            AgentInfo existing = agents.get(agentId);
            if (existing != null) {
                AgentInfo updated = new AgentInfo(
                    existing.agentId(),
                    existing.agentName(),
                    existing.capabilities(),
                    existing.protocols(),
                    existing.endpoint(),
                    existing.registeredAt(),
                    status
                );
                agents.put(agentId, updated);
            }
        }
    }

    /**
     * Test handoff router for message routing.
     */
    protected static class TestHandoffRouter {
        private final TestAgentRegistry registry;
        private final Map<String, BlockingQueue<HandoffMessage>> agentQueues = new ConcurrentHashMap<>();
        private volatile boolean running = true;

        public TestHandoffRouter(TestAgentRegistry registry) {
            this.registry = registry;
        }

        public void route(HandoffMessage message) {
            if (!running) {
                throw new IllegalStateException("Router is shut down");
            }

            if (!registry.isRegistered(message.toAgent())) {
                throw new IllegalArgumentException("Target agent not registered: " + message.toAgent());
            }

            BlockingQueue<HandoffMessage> queue = agentQueues.computeIfAbsent(
                message.toAgent(),
                _ -> new LinkedBlockingQueue<>()
            );
            queue.offer(message);
        }

        public HandoffMessage receive(String agentId, Duration timeout) throws InterruptedException {
            BlockingQueue<HandoffMessage> queue = agentQueues.get(agentId);
            if (queue == null) {
                return null;
            }
            return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        public List<HandoffMessage> drainAll(String agentId) {
            BlockingQueue<HandoffMessage> queue = agentQueues.get(agentId);
            if (queue == null) {
                return List.of();
            }
            List<HandoffMessage> messages = new ArrayList<>();
            queue.drainTo(messages);
            return messages;
        }

        public void shutdown() {
            running = false;
            agentQueues.clear();
        }
    }
}
