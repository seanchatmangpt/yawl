/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.conflict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for conflict resolution implementations.
 *
 * Tests all conflict resolution strategies defined in ADR-025:
 * - Majority Vote Conflict Resolver
 * - Escalating Conflict Resolver
 * - Human Fallback Conflict Resolver
 * - Hybrid Strategy
 *
 * Coverage targets:
 * - All resolver implementations
 * - Conflict context validation
 * - Decision aggregation and majority calculation
 * - Escalation to arbiter agents
 * - Human fallback mechanisms
 * - Edge cases and failure scenarios
 */
class ConflictResolverTest {

    private static final String TEST_WORKFLOW_ID = "WF-42";
    private static final String TEST_TASK_ID = "ReviewDocument";
    private static final String TEST_ARBITER_AGENT = "supervisor-agent";
    private static final double CONFIDENCE_THRESHOLD = 0.8;

    @Nested
    @DisplayName("Majority Vote Conflict Resolver")
    class MajorityVoteConflictResolverTests {

        @Test
        @DisplayName("Resolve conflict with clear majority")
        void resolveConflictWithClearMajority() throws ConflictResolutionException {
            // Given three conflicting decisions
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Document looks good"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.7), 0.7, "Suspicious patterns found"),
                new AgentDecision("agent-3", "APPROVE", Map.of("confidence", 0.85), 0.85, "Meets all criteria")
            );

            ConflictContext context = new ConflictContext(
                "conflict-123",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("documentType", "contract", "amount", 50000)
            );

            // When resolving with majority vote
            ConflictResolver resolver = new MajorityVoteConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should be APPROVE (2 out of 3)
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
            assertEquals("MAJORITY_VOTE", resolution.getResolutionStrategy());
            assertEquals(List.of("agent-1", "agent-2", "agent-3"), resolution.getParticipatingAgents());
            assertEquals(ConflictResolver.Severity.LOW, resolution.getSeverity());
        }

        @Test
        @DisplayName("Resolve conflict with tie (even number)")
        void resolveConflictWithTie() throws ConflictResolutionException {
            // Given four conflicting decisions with a tie
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good document"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.8), 0.8, "Found issues"),
                new AgentDecision("agent-3", "REJECT", Map.of("confidence", 0.85), 0.85, "Suspicious content"),
                new AgentDecision("agent-4", "APPROVE", Map.of("confidence", 0.75), 0.75, "Looks valid")
            );

            ConflictContext context = new ConflictContext(
                "conflict-456",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("stakeholder", "high-importance")
            );

            // When resolving with majority vote
            ConflictResolver resolver = new MajorityVoteConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should handle tie (typically default to APPROVE or reject)
            assertNotNull(resolution);
            // In case of tie, we'll default to APPROVE for conservative approach
            assertTrue(List.of("APPROVE", "REJECT").contains(resolution.getResolvedValue()));
            assertEquals("MAJORITY_VOTE", resolution.getResolutionStrategy());
        }

        @Test
        @DisplayName("Resolve conflict with unanimous decision")
        void resolveConflictWithUnanimousDecision() throws ConflictResolutionException {
            // Given three identical decisions
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.95), 0.95, "Excellent"),
                new AgentDecision("agent-2", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                new AgentDecision("agent-3", "APPROVE", Map.of("confidence", 0.92), 0.92, "Valid")
            );

            ConflictContext context = new ConflictContext(
                "conflict-789",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                Map.of()
            );

            // When resolving with majority vote
            ConflictResolver resolver = new MajorityVoteConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should be unanimous APPROVE
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
            assertEquals(ConflictResolver.Severity.LOW, resolution.getSeverity());
            assertEquals(3, resolution.getParticipatingAgents().size());
        }

        @Test
        @DisplayName("Resolve conflict with confidence-weighted voting")
        void resolveConflictWithConfidenceWeightedVoting() throws ConflictResolutionException {
            // Create a resolver with confidence-weighted configuration
            MajorityVoteConflictResolver resolver = new MajorityVoteConflictResolver();
            Map<String, Object> config = Map.of(
                "useConfidenceWeighting", true,
                "confidenceThreshold", 0.8,
                "defaultOnTie", "APPROVE"
            );
            resolver.updateConfiguration(config);

            // Given decisions with varying confidence levels
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "High confidence"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.6), 0.6, "Low confidence"),
                new AgentDecision("agent-3", "APPROVE", Map.of("confidence", 0.95), 0.95, "Very high confidence")
            );

            ConflictContext context = new ConflictContext(
                "conflict-101",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("weightedVoting", true)
            );

            // When resolving with confidence weighting
            Decision resolution = resolver.resolveConflict(context);

            // Then high-confidence APPROVE votes should win
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
        }

        @Test
        @DisplayName("Resolve conflict with single decision")
        void resolveConflictWithSingleDecision() throws ConflictResolutionException {
            // Given only one decision
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Single decision")
            );

            ConflictContext context = new ConflictContext(
                "conflict-202",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                Map.of()
            );

            // When resolving with majority vote
            ConflictResolver resolver = new MajorityVoteConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should be the single decision
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
            assertEquals(1, resolution.getParticipatingAgents().size());
        }

        @Test
        @DisplayName("Reject invalid conflict context")
        void rejectInvalidConflictContext() {
            // Given invalid context (null decisions)
            ConflictContext invalidContext = new ConflictContext(
                null,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                null,
                Map.of()
            );

            // When resolving with majority vote
            ConflictResolver resolver = new MajorityVoteConflictResolver();

            // Then it should throw exception
            assertThrows(IllegalArgumentException.class, () -> {
                resolver.resolveConflict(invalidContext);
            }, "Invalid context should throw exception");
        }

        @Test
        @DisplayName("Check if resolver can handle conflict")
        void canHandleConflict() {
            // Given different conflict contexts
            ConflictContext mediumContext = new ConflictContext(
                "conflict-303",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                List.of(createTestDecision()),
                Map.of()
            );

            ConflictContext criticalContext = new ConflictContext(
                "conflict-404",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.CRITICAL,
                List.of(createTestDecision()),
                Map.of()
            );

            // When checking resolver capability
            ConflictResolver resolver = new MajorityVoteConflictResolver();

            // Then it should handle all severity levels
            assertTrue(resolver.canResolve(mediumContext), "Should handle medium severity");
            assertTrue(resolver.canResolve(criticalContext), "Should handle critical severity");
        }
    }

    @Nested
    @DisplayName("Escalating Conflict Resolver")
    class EscalatingConflictResolverTests {

        @Test
        @DisplayName("Escalate conflict to arbiter agent")
        void escalateConflictToArbiter() throws ConflictResolutionException {
            // Given conflicting decisions with low agreement
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Document is valid"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.8), 0.8, "Found suspicious patterns"),
                new AgentDecision("agent-3", "REJECT", Map.of("confidence", 0.7), 0.7, "Requires review")
            );

            ConflictContext context = new ConflictContext(
                "conflict-505",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                Map.of("escalateTo", TEST_ARBITER_AGENT)
            );

            // When escalating to arbiter
            ConflictResolver resolver = new EscalatingConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should indicate escalation
            assertNotNull(resolution);
            assertEquals("ESCALATED", resolution.getResolvedValue());
            assertEquals("ESCALATING", resolution.getResolutionStrategy());
            assertEquals(TEST_ARBITER_AGENT, resolution.getResolutionMetadata().get("escalatedTo"));
            assertEquals(ConflictResolver.Severity.HIGH, resolution.getSeverity());
        }

        @Test
        @DisplayName("Escalate with agreement threshold")
        void escalateWithAgreementThreshold() throws ConflictResolutionException {
            // Create resolver with 60% agreement threshold
            EscalatingConflictResolver resolver = new EscalatingConflictResolver();
            Map<String, Object> config = Map.of(
                "agreementThreshold", 0.6,
                "arbiterAgent", TEST_ARBITER_AGENT
            );
            resolver.updateConfiguration(config);

            // Given decisions below threshold (33% agree)
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.8), 0.8, "Bad"),
                new AgentDecision("agent-3", "REJECT", Map.of("confidence", 0.7), 0.7, "Ugly")
            );

            ConflictContext context = new ConflictContext(
                "conflict-606",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("agreementCheck", true)
            );

            // When checking agreement
            Decision resolution = resolver.resolveConflict(context);

            // Then it should escalate due to low agreement
            assertNotNull(resolution);
            assertEquals("ESCALATED", resolution.getResolvedValue());
            assertTrue((double) resolution.getResolutionMetadata().get("agreementPercentage") < 0.6);
        }

        @Test
        @DisplayName("Do not escalate when agreement threshold is met")
        void doNotEscalateWhenAgreementMet() throws ConflictResolutionException {
            // Create resolver with 60% agreement threshold
            EscalatingConflictResolver resolver = new EscalatingConflictResolver();
            Map<String, Object> config = Map.of(
                "agreementThreshold", 0.6,
                "arbiterAgent", TEST_ARBITER_AGENT,
                "fallbackToMajority", true
            );
            resolver.updateConfiguration(config);

            // Given decisions above threshold (67% agree on APPROVE)
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                new AgentDecision("agent-2", "APPROVE", Map.of("confidence", 0.8), 0.8, "Good"),
                new AgentDecision("agent-3", "REJECT", Map.of("confidence", 0.7), 0.7, "Bad")
            );

            ConflictContext context = new ConflictContext(
                "conflict-707",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("agreementCheck", true)
            );

            // When checking agreement
            Decision resolution = resolver.resolveConflict(context);

            // Then it should not escalate but use majority vote
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
            assertEquals("MAJORITY_VOTE", resolution.getResolutionStrategy());
        }

        @Test
        @DisplayName("Handle arbiter agent failure")
        void handleArbiterAgentFailure() {
            // Given resolver with failing arbiter
            EscalatingConflictResolver resolver = new EscalatingConflictResolver();
            Map<String, Object> config = Map.of(
                "arbiterAgent", "non-existent-agent",
                "fallbackToHuman", true
            );
            resolver.updateConfiguration(config);

            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.8), 0.8, "Bad")
            );

            ConflictContext context = new ConflictContext(
                "conflict-808",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.CRITICAL,
                decisions,
                Map.of()
            );

            // When escalating with failing arbiter
            assertThrows(ConflictResolutionException.class, () -> {
                resolver.resolveConflict(context);
            }, "Should throw exception when arbiter fails");
        }

        @Test
        @DisplayName("Check if escalating resolver can handle conflict")
        void canHandleEscalation() {
            // Given different severity contexts
            ConflictContext highContext = new ConflictContext(
                "conflict-909",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                List.of(createTestDecision()),
                Map.of("escalateTo", TEST_ARBITER_AGENT)
            );

            ConflictContext lowContext = new ConflictContext(
                "conflict-1010",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(createTestDecision()),
                Map.of()
            );

            // When checking resolver capability
            ConflictResolver resolver = new EscalatingConflictResolver();

            // Then it should handle high severity but not low
            assertTrue(resolver.canResolve(highContext), "Should handle high severity");
            assertFalse(resolver.canResolve(lowContext), "Should not handle low severity");
        }
    }

    @Nested
    @DisplayName("Human Fallback Conflict Resolver")
    class HumanFallbackConflictResolverTests {

        @Test
        @DisplayName("Fallback to human when no resolution possible")
        void fallbackToHuman() throws ConflictResolutionException {
            // Given conflicting decisions with no clear resolution
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Document is valid"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.9), 0.9, "Document is fraudulent")
            );

            ConflictContext context = new ConflictContext(
                "conflict-1111",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.CRITICAL,
                decisions,
                Map.of("fallbackToHuman", true)
            );

            // When using human fallback
            ConflictResolver resolver = new HumanFallbackConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then the resolution should indicate human fallback
            assertNotNull(resolution);
            assertEquals("HUMAN_REVIEW_REQUIRED", resolution.getResolvedValue());
            assertEquals("HUMAN_FALLBACK", resolution.getResolutionStrategy());
            assertEquals(ConflictResolver.Severity.CRITICAL, resolution.getSeverity());
            assertTrue((boolean) resolution.getResolutionMetadata().get("escalatedToHuman"));
        }

        @Test
        @DisplayName("Fallback includes all decision details")
        void fallbackIncludesDecisionDetails() throws ConflictResolutionException {
            // Given detailed conflicting decisions
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.95), 0.95, "Document meets all criteria", "Detailed rationale 1"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.88), 0.88, "Document has legal issues", "Detailed rationale 2")
            );

            ConflictContext context = new ConflictContext(
                "conflict-1212",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                Map.of("fallbackToHuman", true)
            );

            // When using human fallback
            ConflictResolver resolver = new HumanFallbackConflictResolver();
            Decision resolution = resolver.resolveConflict(context);

            // Then all decision details should be included in metadata
            assertNotNull(resolution);
            Map<String, Object> metadata = resolution.getResolutionMetadata();
            assertEquals(2, ((List<?>) metadata.get("agentDecisions")).size());
            assertTrue(metadata.containsKey("escalatedAt"));
        }

        @Test
        @DisplayName("Do not fallback when not configured")
        void doNotFallbackWhenNotConfigured() {
            // Given context without human fallback configured
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good")
            );

            ConflictContext context = new ConflictContext(
                "conflict-1313",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                decisions,
                Map.of("fallbackToHuman", false)
            );

            // When using human resolver without fallback
            ConflictResolver resolver = new HumanFallbackConflictResolver();

            // Then it should throw exception
            assertThrows(ConflictResolutionException.class, () -> {
                resolver.resolveConflict(context);
            }, "Should throw exception when not configured for fallback");
        }

        @Test
        @DisplayName("Human fallback resolver health check")
        void humanFallbackHealthCheck() {
            // Given human fallback resolver
            ConflictResolver resolver = new HumanFallbackConflictResolver();

            // When checking health
            boolean isHealthy = resolver.isHealthy();

            // Then it should be healthy (assuming human reviewers are available)
            assertTrue(isHealthy, "Human fallback resolver should be healthy");
        }
    }

    @Nested
    @DisplayName("Hybrid Conflict Resolver")
    class HybridConflictResolverTests {

        @Test
        @DisplayName("Use hybrid strategy with multiple resolvers")
        void useHybridStrategy() throws ConflictResolutionException {
            // Create hybrid resolver combining majority vote and escalation
            HybridConflictResolver resolver = new HybridConflictResolver();
            Map<String, Object> config = Map.of(
                "primaryStrategy", "MAJORITY_VOTE",
                "secondaryStrategy", "ESCALATING",
                "escalationThreshold", 0.5,
                "arbiterAgent", TEST_ARBITER_AGENT
            );
            resolver.updateConfiguration(config);

            // Given conflicting decisions
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.8), 0.8, "Bad")
            );

            ConflictContext context = new ConflictContext(
                "conflict-1414",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                Map.of()
            );

            // When using hybrid strategy
            Decision resolution = resolver.resolveConflict(context);

            // Then it should try majority vote first, then escalate
            assertNotNull(resolution);
            assertTrue(List.of("MAJORITY_VOTE", "ESCALATING").contains(resolution.getResolutionStrategy()));
        }

        @Test
        @DisplayName("Configure hybrid resolver thresholds")
        void configureHybridThresholds() throws ConflictResolutionException {
            // Create hybrid resolver with specific thresholds
            HybridConflictResolver resolver = new HybridConflictResolver();
            Map<String, Object> config = Map.of(
                "primaryStrategy", "MAJORITY_VOTE",
                "secondaryStrategy", "HUMAN_FALLBACK",
                "confidenceThreshold", 0.7,
                "useConfidenceWeighting", true
            );
            resolver.updateConfiguration(config);

            // Given high-confidence decisions
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.95), 0.95, "Very confident"),
                new AgentDecision("agent-2", "APPROVE", Map.of("confidence", 0.92), 0.92, "Also confident")
            );

            ConflictContext context = new ConflictContext(
                "conflict-1515",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                Map.of()
            );

            // When resolving with high confidence
            Decision resolution = resolver.resolveConflict(context);

            // Then it should use majority vote due to high confidence
            assertNotNull(resolution);
            assertEquals("APPROVE", resolution.getResolvedValue());
            assertEquals("MAJORITY_VOTE", resolution.getResolutionStrategy());
        }

        @Test
        @DisplayName("Hybrid resolver strategy selection")
        void hybridResolverStrategySelection() {
            // Create hybrid resolver
            HybridConflictResolver resolver = new HybridConflictResolver();

            // Test different conflict scenarios
            ConflictContext clearMajorityContext = new ConflictContext(
                "conflict-1616",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(
                    new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                    new AgentDecision("agent-2", "APPROVE", Map.of("confidence", 0.8), 0.8, "Good"),
                    new AgentDecision("agent-3", "REJECT", Map.of("confidence", 0.7), 0.7, "Bad")
                ),
                Map.of()
            );

            ConflictContext tieContext = new ConflictContext(
                "conflict-1717",
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                List.of(
                    new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Good"),
                    new AgentDecision("agent-2", "REJECT", Map.of("confidence", 0.9), 0.9, "Bad")
                ),
                Map.of()
            );

            // When checking which strategies to use
            assertTrue(resolver.canResolve(clearMajorityContext), "Should handle clear majority");
            assertTrue(resolver.canResolve(tieContext), "Should handle tie scenarios");
        }
    }

    @Nested
    @DisplayName("General Resolver Tests")
    class GeneralResolverTests {

        @Test
        @DisplayName("Get resolver strategy type")
        void getResolverStrategyType() {
            // Given different resolvers
            ConflictResolver majorityResolver = new MajorityVoteConflictResolver();
            ConflictResolver escalatingResolver = new EscalatingConflictResolver();
            ConflictResolver humanResolver = new HumanFallbackConflictResolver();
            ConflictResolver hybridResolver = new HybridConflictResolver();

            // When getting strategy types
            assertEquals(ConflictResolver.Strategy.MAJORITY_VOTE, majorityResolver.getStrategy());
            assertEquals(ConflictResolver.Strategy.ESCALATING, escalatingResolver.getStrategy());
            assertEquals(ConflictResolver.Strategy.HUMAN_FALLBACK, humanResolver.getStrategy());
            assertEquals(ConflictResolver.Strategy.HYBRID, hybridResolver.getStrategy());
        }

        @Test
        @DisplayName("Update resolver configuration")
        void updateResolverConfiguration() {
            // Given majority vote resolver
            MajorityVoteConflictResolver resolver = new MajorityVoteConflictResolver();

            // When updating configuration
            Map<String, Object> newConfig = Map.of(
                "useConfidenceWeighting", true,
                "confidenceThreshold", 0.75,
                "defaultOnTie", "REJECT"
            );

            assertDoesNotThrow(() -> {
                resolver.updateConfiguration(newConfig);
            }, "Configuration update should not throw exception");

            // Then configuration should be updated
            assertEquals(newConfig, resolver.getConfiguration());
        }

        @Test
        @DisplayName("Handle invalid configuration update")
        void handleInvalidConfigurationUpdate() {
            // Given resolver
            MajorityVoteConflictResolver resolver = new MajorityVoteConflictResolver();

            // When updating with invalid configuration
            Map<String, Object> invalidConfig = Map.of(
                "confidenceThreshold", 1.5, // Invalid threshold
                "invalidKey", "invalidValue"
            );

            // Then it should throw exception
            assertThrows(IllegalArgumentException.class, () -> {
                resolver.updateConfiguration(invalidConfig);
            }, "Invalid configuration should throw exception");
        }

        @Test
        @DisplayName("Resolver equality and hash code")
        void resolverEqualityAndHashCode() {
            // Given two identical resolvers
            MajorityVoteConflictResolver resolver1 = new MajorityVoteConflictResolver();
            MajorityVoteConflictResolver resolver2 = new MajorityVoteConflictResolver();

            // When checking equality
            assertEquals(resolver1, resolver2, "Identical resolvers should be equal");
            assertEquals(resolver1.hashCode(), resolver2.hashCode(), "Equal resolvers should have same hash code");
        }

        @Test
        @DisplayName("Concurrent conflict resolution")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void concurrentConflictResolution() throws ConflictResolutionException, InterruptedException {
            // Given multiple conflicts to resolve
            int threadCount = 5;
            Thread[] threads = new Thread[threadCount];
            Decision[] resolutions = new Decision[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[index] = new Thread(() -> {
                    try {
                        List<AgentDecision> decisions = List.of(
                            new AgentDecision("agent-" + index, "DECISION_" + index, Map.of("confidence", 0.9), 0.9, "Reason " + index)
                        );

                        ConflictContext context = new ConflictContext(
                            "conflict-concurrent-" + index,
                            TEST_WORKFLOW_ID,
                            TEST_TASK_ID,
                            ConflictResolver.Severity.MEDIUM,
                            decisions,
                            Map.of()
                        );

                        ConflictResolver resolver = new MajorityVoteConflictResolver();
                        resolutions[index] = resolver.resolveConflict(context);
                    } catch (ConflictResolutionException e) {
                        fail("Thread " + index + " failed: " + e.getMessage());
                    }
                });
                threads[index].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then all resolutions should be successful
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(resolutions[i], "Resolution " + i + " should not be null");
                assertEquals("DECISION_" + i, resolutions[i].getResolvedValue());
            }
        }

        @Test
        @DisplayName("Conflict resolution performance")
        void conflictResolutionPerformance() throws ConflictResolutionException {
            // Given resolver
            ConflictResolver resolver = new MajorityVoteConflictResolver();

            // When resolving many conflicts
            int iterations = 100;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                List<AgentDecision> decisions = List.of(
                    new AgentDecision("agent-" + i, "APPROVE", Map.of("confidence", 0.9), 0.9, "Reason " + i)
                );

                ConflictContext context = new ConflictContext(
                    "conflict-perf-" + i,
                    TEST_WORKFLOW_ID,
                    TEST_TASK_ID,
                    ConflictResolver.Severity.LOW,
                    decisions,
                    Map.of()
                );

                Decision resolution = resolver.resolveConflict(context);
                assertNotNull(resolution, "Resolution " + i + " should not be null");
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average resolution time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 5, "Average resolution time should be < 5ms, was " + avgTimeMs + "ms");
            System.out.println("Average conflict resolution time: " + avgTimeMs + "ms");
        }
    }

    // Helper method to create a test decision
    private AgentDecision createTestDecision() {
        return new AgentDecision("agent-1", "APPROVE", Map.of("confidence", 0.9), 0.9, "Test decision");
    }
}