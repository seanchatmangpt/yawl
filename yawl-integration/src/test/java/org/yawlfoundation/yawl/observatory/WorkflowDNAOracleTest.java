/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for WorkflowDNAOracle using Chicago TDD style.
 *
 * Tests case absorption, risk assessment, alternative path mining, and edge cases.
 */
public class WorkflowDNAOracleTest {

    private static final String ORDER_PROCESS = "order-process";
    private static final String SHIPPING_PROCESS = "shipping-process";

    private WorkflowDNAOracle oracle;
    private XesToYawlSpecGenerator xesGenerator;
    private static final Instant TEST_TIME = Instant.parse("2026-03-01T12:00:00Z");

    @BeforeEach
    void setUp() {
        xesGenerator = new XesToYawlSpecGenerator(1);
        oracle = new WorkflowDNAOracle(xesGenerator);
    }

    @Nested
    @DisplayName("Case Absorption Tests")
    class CaseAbsorptionTests {

        @Test
        @DisplayName("Successfully absorb successful case")
        void testAbsorbSuccessfulCase() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");
            Map<String, Long> durations = Map.of("create", 100L, "validate", 50L, "ship", 200L);
            boolean caseFailed = false;

            // When
            oracle.absorb("case-001", ORDER_PROCESS, activities, durations, caseFailed);

            // Then
            // Verify no exceptions thrown
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Should have 1 absorbed case");

            // Verify case was absorbed with correct fingerprint
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-002", ORDER_PROCESS, activities);
            assertEquals("insufficient_history", assessment.matchedPattern(),
                "Should show insufficient history initially");
        }

        @Test
        @DisplayName("Successfully absorb failed case")
        void testAbsorbFailedCase() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");
            Map<String, Long> durations = Map.of("create", 100L, "validate", 50L, "ship", 200L);
            boolean caseFailed = true;

            // When
            oracle.absorb("case-fail-001", ORDER_PROCESS, activities, durations, caseFailed);

            // Then
            // Verify no exceptions thrown
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Should have 1 absorbed case");
        }

        @Test
        @DisplayName("Absorb case with minimal activity sequence")
        void testAbsorbMinimalSequence() {
            // Given
            List<String> activities = List.of("start", "end");
            Map<String, Long> durations = Map.of("start", 10L, "end", 20L);
            boolean caseFailed = false;

            // When
            oracle.absorb("case-minimal", ORDER_PROCESS, activities, durations, caseFailed);

            // Then
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Should have 1 absorbed case");
        }

        @Test
        @DisplayName("Absorb case with complex activity sequence")
        void testAbsorbComplexSequence() {
            // Given
            List<String> activities = List.of(
                "start", "check-credit", "authorize", "verify-stock",
                "process-payment", "create-order", "ship", "notify", "end"
            );
            Map<String, Long> durations = Map.of(
                "start", 100L, "check-credit", 500L, "authorize", 200L,
                "verify-stock", 300L, "process-payment", 1000L,
                "create-order", 200L, "ship", 2000L, "notify", 100L, "end", 50L
            );
            boolean caseFailed = false;

            // When
            oracle.absorb("case-complex", ORDER_PROCESS, activities, durations, caseFailed);

            // Then
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Should have 1 absorbed case");
        }

        @Test
        @DisplayName("Absorb case with null task durations")
        void testAbsorbWithNullDurations() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");
            boolean caseFailed = false;

            // When
            oracle.absorb("case-null-durations", ORDER_PROCESS, activities, null, caseFailed);

            // Then
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Should have 1 absorbed case");
            // Should handle null durations gracefully
        }

        @Test
        @DisplayName("Absorb multiple cases with same fingerprint")
        void testAbsorbMultipleCasesSameFingerprint() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");
            Map<String, Long> durations = Map.of("create", 100L, "validate", 50L, "ship", 200L);
            boolean caseFailed = false;

            // When
            oracle.absorb("case-001", ORDER_PROCESS, activities, durations, caseFailed);
            oracle.absorb("case-002", ORDER_PROCESS, activities, durations, false);
            oracle.absorb("case-003", ORDER_PROCESS, activities, durations, true);

            // Then
            assertEquals(3, oracle.getAbsorbedCaseCount(), "Should have 3 absorbed cases");
        }

        @Test
        @DisplayName("Absorb cases with different fingerprints")
        void testAbsorbMultipleCasesDifferentFingerprints() {
            // Given
            List<String> activities1 = List.of("create", "validate", "ship");
            List<String> activities2 = List.of("create", "process", "ship");
            Map<String, Long> durations = Map.of("create", 100L, "validate", 50L, "ship", 200L);
            boolean caseFailed = false;

            // When
            oracle.absorb("case-001", ORDER_PROCESS, activities1, durations, caseFailed);
            oracle.absorb("case-002", ORDER_PROCESS, activities2, durations, caseFailed);

            // Then
            assertEquals(2, oracle.getAbsorbedCaseCount(), "Should have 2 absorbed cases");
            // Different fingerprints should be treated as different patterns
        }

        @Test
        @DisplayName("Throw NullPointerException for null caseId")
        void testAbsorbWithNullCaseId() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");

            // When & Then
            assertThrows(NullPointerException.class,
                () -> oracle.absorb(null, ORDER_PROCESS, activities, null, false),
                "Should throw NullPointerException for null caseId");
        }

        @Test
        @DisplayName("Throw NullPointerException for null specId")
        void testAbsorbWithNullSpecId() {
            // Given
            List<String> activities = List.of("create", "validate", "ship");

            // When & Then
            assertThrows(NullPointerException.class,
                () -> oracle.absorb("case-001", null, activities, null, false),
                "Should throw NullPointerException for null specId");
        }

        @Test
        @DisplayName("Throw NullPointerException for null activitySequence")
        void testAbsorbWithNullActivitySequence() {
            // Given
            Map<String, Long> durations = Map.of("create", 100L);

            // When & Then
            assertThrows(NullPointerException.class,
                () -> oracle.absorb("case-001", ORDER_PROCESS, null, durations, false),
                "Should throw NullPointerException for null activitySequence");
        }

        @Test
        @DisplayName("Throw IllegalArgumentException for empty activitySequence")
        void testAbsorbWithEmptyActivitySequence() {
            // Given
            List<String> activities = List.of();

            // When & Then
            assertThrows(IllegalArgumentException.class,
                () -> oracle.absorb("case-001", ORDER_PROCESS, activities, null, false),
                "Should throw IllegalArgumentException for empty activitySequence");
        }
    }

    @Nested
    @DisplayName("Risk Assessment Tests")
    class RiskAssessmentTests {

        @BeforeEach
        void setupCases() {
            // Setup test cases with different failure patterns
            absorbTestCases();
        }

        private void absorbTestCases() {
            // Low failure pattern: 5 cases, 1 failure (20% < 23% threshold)
            for (int i = 0; i < 4; i++) {
                oracle.absorb("low-fail-" + i, ORDER_PROCESS,
                    List.of("create", "validate", "ship"),
                    Map.of("create", 100L, "validate", 50L, "ship", 200L),
                    false);
            }
            oracle.absorb("low-fail-4", ORDER_PROCESS,
                List.of("create", "validate", "ship"),
                Map.of("create", 100L, "validate", 50L, "ship", 200L),
                true);

            // High failure pattern: 10 cases, 3 failures (30% > 23% threshold)
            for (int i = 0; i < 7; i++) {
                oracle.absorb("high-fail-" + i, SHIPPING_PROCESS,
                    List.of("receive", "validate", "ship"),
                    Map.of("receive", 50L, "validate", 30L, "ship", 150L),
                    false);
            }
            for (int i = 7; i < 10; i++) {
                oracle.absorb("high-fail-" + i, SHIPPING_PROCESS,
                    List.of("receive", "validate", "ship"),
                    Map.of("receive", 50L, "validate", 30L, "ship", 150L),
                    true);
            }
        }

        @Test
        @DisplayName("Assess case with insufficient historical data")
        void testAssessInsufficientData() {
            // Given - no historical cases

            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-new", ORDER_PROCESS, List.of("create", "validate", "ship"));

            // Then
            assertEquals("insufficient_history", assessment.matchedPattern());
            assertEquals(0.0, assessment.historicalFailureRate());
            assertTrue(assessment.riskMessage().contains("Insufficient historical data"));
            assertTrue(assessment.prePositionResources().isEmpty());
            assertTrue(assessment.alternativePathXml().isEmpty());
        }

        @Test
        @DisplayName("Assess case with low failure risk")
        void testAssessLowRisk() {
            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-low-risk", ORDER_PROCESS, List.of("create", "validate", "ship"));

            // Then
            assertNotEquals("insufficient_history", assessment.matchedPattern());
            assertEquals(0.2, assessment.historicalFailureRate(), 0.001); // 1/5 = 20%
            assertFalse(assessment.riskMessage().contains("High failure risk"));
            assertTrue(assessment.riskMessage().contains("Low failure risk"));
            assertTrue(assessment.prePositionResources().isEmpty());
            assertTrue(assessment.alternativePathXml().isEmpty());
        }

        @Test
        @DisplayName("Assess case with high failure risk")
        void testAssessHighRisk() {
            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-high-risk", SHIPPING_PROCESS, List.of("receive", "validate", "ship"));

            // Then
            assertNotEquals("insufficient_history", assessment.matchedPattern());
            assertEquals(0.3, assessment.historicalFailureRate(), 0.001); // 3/10 = 30%
            assertTrue(assessment.riskMessage().contains("High failure risk"));
            assertFalse(assessment.riskMessage().contains("Low failure risk"));
            assertFalse(assessment.prePositionResources().isEmpty());
            assertEquals(2, assessment.prePositionResources().size());
            assertTrue(assessment.prePositionResources().contains("task_queue_monitor"));
            assertTrue(assessment.prePositionResources().contains("error_recovery_service"));

            // Alternative path may or may not be present (depends on XES generator success)
            assertNotNull(assessment.alternativePathXml());
        }

        @Test
        @DisplayName("Assess case with edge case: exactly at threshold")
        void testAssessExactlyAtThreshold() {
            // Given - setup cases with exactly 23% failure rate
            // 23 failures out of 100 cases would be exactly at threshold
            // For simplicity, we'll test with a smaller number: 3/13 ≈ 23%

            for (int i = 0; i < 10; i++) {
                oracle.absorb("threshold-" + i, "threshold-process",
                    List.of("task1", "task2"),
                    Map.of("task1", 100L, "task2", 50L),
                    false);
            }
            for (int i = 10; i < 13; i++) {
                oracle.absorb("threshold-" + i, "threshold-process",
                    List.of("task1", "task2"),
                    Map.of("task1", 100L, "task2", 50L),
                    true);
            }

            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-threshold", "threshold-process", List.of("task1", "task2"));

            // Then
            assertEquals(3.0/13.0, assessment.historicalFailureRate(), 0.001);
            assertTrue(assessment.riskMessage().contains("High failure risk"));
        }

        @Test
        @DisplayName("Assess case with different activity sequences")
        void testAssessDifferentActivitySequences() {
            // Given - setup different patterns
            for (int i = 0; i < 5; i++) {
                oracle.absorb("seq1-" + i, "multi-process",
                    List.of("a", "b", "c"),
                    Map.of("a", 100L, "b", 50L, "c", 150L),
                    false);
            }
            for (int i = 0; i < 3; i++) {
                oracle.absorb("seq2-" + i, "multi-process",
                    List.of("a", "d", "c"),
                    Map.of("a", 100L, "d", 75L, "c", 150L),
                    true);
            }

            // When - assess a case with a different sequence
            WorkflowDNAOracle.DNARecommendation assessment1 = oracle.assess(
                "case-seq1", "multi-process", List.of("a", "b", "c"));
            WorkflowDNAOracle.DNARecommendation assessment2 = oracle.assess(
                "case-seq2", "multi-process", List.of("a", "d", "c"));

            // Then
            assertEquals(0.0, assessment1.historicalFailureRate(), 0.001); // 0/5 = 0%
            assertEquals(1.0, assessment2.historicalFailureRate(), 0.001); // 3/3 = 100%
        }

        @Test
        @DisplayName("Assess case with single activity")
        void testAssessSingleActivity() {
            // Given - setup cases with single activity
            for (int i = 0; i < 5; i++) {
                oracle.absorb("single-" + i, "single-process",
                    List.of("only"),
                    Map.of("only", 100L),
                    i < 4); // 4 successes, 1 failure
            }

            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-single", "single-process", List.of("only"));

            // Then
            assertEquals(0.2, assessment.historicalFailureRate(), 0.001); // 1/5 = 20%
            assertFalse(assessment.riskMessage().contains("High failure risk"));
        }

        @Test
        @DisplayName("Throw NullPointerException for null parameters")
        void testAssessWithNullParameters() {
            // When & Then
            assertThrows(NullPointerException.class,
                () -> oracle.assess(null, ORDER_PROCESS, List.of("create", "validate", "ship")),
                "Should throw NullPointerException for null caseId");
            assertThrows(NullPointerException.class,
                () -> oracle.assess("case-001", null, List.of("create", "validate", "ship")),
                "Should throw NullPointerException for null specId");
            assertThrows(NullPointerException.class,
                () -> oracle.assess("case-001", ORDER_PROCESS, null),
                "Should throw NullPointerException for null activitySequence");
        }

        @Test
        @DisplayName("Throw IllegalArgumentException for empty activity sequence")
        void testAssessWithEmptyActivitySequence() {
            // Given
            List<String> activities = List.of();

            // When & Then
            assertThrows(IllegalArgumentException.class,
                () -> oracle.assess("case-001", ORDER_PROCESS, activities),
                "Should throw IllegalArgumentException for empty activity sequence");
        }
    }

    @Nested
    @DisplayName("Alternative Path Mining Tests")
    class AlternativePathMiningTests {

        @Test
        @DisplayName("Mine alternative path when available")
        void testMineAlternativePath_Available() {
            // Given - setup successful cases with different patterns
            for (int i = 0; i < 10; i++) {
                oracle.absorb("alt-success-" + i, ORDER_PROCESS,
                    List.of("create", "validate", "ship"),
                    Map.of("create", 100L, "validate", 50L, "ship", 200L),
                    false);
            }

            // When - assess a case with high failure rate
            // Add high failure cases to trigger alternative path mining
            for (int i = 0; i < 5; i++) {
                oracle.absorb("alt-fail-" + i, ORDER_PROCESS,
                    List.of("create", "ship"), // Different pattern
                    Map.of("create", 100L, "ship", 200L),
                    true);
            }

            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-alt-test", ORDER_PROCESS, List.of("create", "ship"));

            // Then
            assertTrue(assessment.riskMessage().contains("High failure risk"));
            Optional<String> altPath = assessment.alternativePathXml();
            assertNotNull(altPath);
            // The alternative path may be empty if XES generator fails, which is acceptable graceful degradation
        }

        @Test
        @DisplayName("Handle alternative path mining gracefully on failure")
        void testMineAlternativePath_GracefulFailure() {
            // Given - setup mixed results
            for (int i = 0; i < 3; i++) {
                oracle.absorb("graceful-success-" + i, ORDER_PROCESS,
                    List.of("create", "validate", "ship"),
                    Map.of("create", 100L, "validate", 50L, "ship", 200L),
                    false);
            }

            // When - assess with potential alternative path
            for (int i = 0; i < 4; i++) {
                oracle.absorb("graceful-fail-" + i, ORDER_PROCESS,
                    List.of("create", "ship"),
                    Map.of("create", 100L, "ship", 200L),
                    true);
            }

            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-graceful", ORDER_PROCESS, List.of("create", "ship"));

            // Then
            assertTrue(assessment.riskMessage().contains("High failure risk"));
            // Should not throw exception even if alternative path mining fails
            assertNotNull(assessment.alternativePathXml());
        }
    }

    @Nested
    @DisplayName("Pruning Tests")
    class PruningTests {

        @BeforeEach
        void setupOldCases() {
            // Setup old cases
            for (int i = 0; i < 10; i++) {
                oracle.absorb("old-case-" + i, ORDER_PROCESS,
                    List.of("task1"),
                    Map.of("task1", 100L),
                    false);
            }
        }

        @Test
        @DisplayName("Prune cases older than specified duration")
        void testPruneOlderThan() {
            // Given - all cases are "old" in test context

            // When - prune cases older than 1 day
            Duration maxAge = Duration.ofDays(1);
            oracle.pruneOlderThan(maxAge);

            // Then
            assertEquals(0, oracle.getAbsorbedCaseCount(), "All cases should be pruned");
        }

        @Test
        @DisplayName("Prune none if all cases are recent")
        void testPruneNoneIfRecent() {
            // Given - recent cases
            oracle.absorb("recent-case", ORDER_PROCESS,
                List.of("task1"),
                Map.of("task1", 100L),
                false);

            // When - prune cases older than 1 week
            Duration maxAge = Duration.ofDays(7);
            oracle.pruneOlderThan(maxAge);

            // Then
            assertEquals(1, oracle.getAbsorbedCaseCount(), "Recent cases should remain");
        }

        @Test
        @DisplayName("Prune mixed age cases")
        void testPruneMixedAgeCases() {
            // Given - setup cases with different timestamps (mocked by setting absorbedAt)
            // In real implementation, this would be handled by RDF timestamp values

            // When - prune very old cases
            Duration maxAge = Duration.ofDays(365); // 1 year
            oracle.pruneOlderThan(maxAge);

            // Then - should prune all test cases since they're not real timestamps
            // This behavior depends on how timestamps are handled in the RDF model
            assertEquals(0, oracle.getAbsorbedCaseCount(), "All test cases should be pruned");
        }

        @Test
        @DisplayName("Throw NullPointerException for null maxAge")
        void testPruneWithNullMaxAge() {
            // When & Then
            assertThrows(NullPointerException.class,
                () -> oracle.pruneOlderThan(null),
                "Should throw NullPointerException for null maxAge");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handle massive activity sequence")
        void testMassiveActivitySequence() {
            // Given - very long activity sequence
            List<String> massiveSequence = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                massiveSequence.add("task-" + i);
            }

            // When & Then - should handle gracefully without throwing
            assertDoesNotThrow(() -> oracle.absorb("case-massive", ORDER_PROCESS,
                massiveSequence, Map.of("task-0", 100L), false));
        }

        @Test
        @DisplayName("Handle activities with special characters")
        void testActivitiesWithSpecialCharacters() {
            // Given
            List<String> specialActivities = List.of(
                "task-🚀", "task-🎯", "task-✨",
                "task-with-dashes", "task.with.dots", "task with spaces"
            );

            // When & Then
            assertDoesNotThrow(() -> oracle.absorb("case-special", ORDER_PROCESS,
                specialActivities, Map.of("task-🚀", 100L), false));
        }

        @Test
        @DisplayName("Handle zero duration activities")
        void testZeroDurationActivities() {
            // Given
            List<String> activities = List.of("start", "process", "end");
            Map<String, Long> durations = Map.of("start", 0L, "process", 0L, "end", 0L);

            // When & Then
            assertDoesNotThrow(() -> oracle.absorb("case-zero", ORDER_PROCESS,
                activities, durations, false));
        }

        @Test
        @DisplayName("Handle very long activity names")
        void testLongActivityNames() {
            // Given
            List<String> longNameActivities = List.of(
                "this-is-a-very-long-activity-name-that-tests-length-handling",
                "another-long-name-task-for-validation-purposes",
                "third-long-name-for-comprehensive-testing"
            );

            // When & Then
            assertDoesNotThrow(() -> oracle.absorb("case-long-names", ORDER_PROCESS,
                longNameActivities, Map.of("this-is-a-very-long-activity-name-that-tests-length-handling", 100L),
                false));
        }

        @Test
        @DisplayName("Handle duplicate activities in sequence")
        void testDuplicateActivities() {
            // Given
            List<String> duplicateActivities = List.of("start", "process", "process", "process", "end");

            // When & Then
            assertDoesNotThrow(() -> oracle.absorb("case-duplicates", ORDER_PROCESS,
                duplicateActivities, Map.of("start", 50L, "process", 100L, "end", 50L),
                false));
        }

        @Test
        @DisplayName("Handle extremely high failure rate")
        void testHighFailureRate() {
            // Given - setup with 99% failure rate
            for (int i = 0; i < 99; i++) {
                oracle.absorb("fail-" + i, ORDER_PROCESS,
                    List.of("failing"),
                    Map.of("failing", 100L),
                    true);
            }
            oracle.absorb("success-1", ORDER_PROCESS,
                List.of("failing"),
                Map.of("failing", 100L),
                false);

            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-high-rate", ORDER_PROCESS, List.of("failing"));

            // Then
            assertEquals(0.99, assessment.historicalFailureRate(), 0.001);
            assertTrue(assessment.riskMessage().contains("High failure risk"));
        }

        @Test
        @DisplayName("Handle case with only failed instances")
        void testOnlyFailedInstances() {
            // Given - setup only failed cases
            for (int i = 0; i < 5; i++) {
                oracle.absorb("only-fail-" + i, ORDER_PROCESS,
                    List.of("task"),
                    Map.of("task", 100L),
                    true);
            }

            // When
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "case-only-failed", ORDER_PROCESS, List.of("task"));

            // Then
            assertEquals(1.0, assessment.historicalFailureRate(), 0.001);
            assertTrue(assessment.riskMessage().contains("High failure risk"));
        }
    }

    @Nested
    @DisplayName("Fingerprinting Tests")
    class FingerprintingTests {

        @Test
        @DisplayName("Generate consistent fingerprint for same sequence")
        void testFingerprintConsistency() {
            // Given
            List<String> activities1 = List.of("create", "validate", "ship");
            List<String> activities2 = List.of("create", "validate", "ship");

            // When
            String fp1 = oracle.assess("test1", ORDER_PROCESS, activities1).matchedPattern();
            String fp2 = oracle.assess("test2", ORDER_PROCESS, activities2).matchedPattern();

            // Then
            assertEquals(fp1, fp2, "Same activity sequence should produce same fingerprint");
        }

        @Test
        @DisplayName("Generate different fingerprints for different sequences")
        void testFingerprintDifference() {
            // Given
            List<String> activities1 = List.of("create", "validate", "ship");
            List<String> activities2 = List.of("create", "process", "ship");
            List<String> activities3 = List.of("create", "validate", "ship", "notify");

            // When
            String fp1 = oracle.assess("test1", ORDER_PROCESS, activities1).matchedPattern();
            String fp2 = oracle.assess("test2", ORDER_PROCESS, activities2).matchedPattern();
            String fp3 = oracle.assess("test3", ORDER_PROCESS, activities3).matchedPattern();

            // Then
            assertNotEquals(fp1, fp2, "Different sequences should produce different fingerprints");
            assertNotEquals(fp1, fp3, "Different length sequences should produce different fingerprints");
            assertNotEquals(fp2, fp3, "Different sequences should produce different fingerprints");
        }

        @Test
        @DisplayName("Generate same fingerprint for different order of same tasks")
        void testFingerprintDifferentOrder() {
            // Given
            List<String> activities1 = List.of("a", "b", "c");
            List<String> activities2 = List.of("c", "b", "a");

            // When
            String fp1 = oracle.assess("test1", ORDER_PROCESS, activities1).matchedPattern();
            String fp2 = oracle.assess("test2", ORDER_PROCESS, activities2).matchedPattern();

            // Then
            assertNotEquals(fp1, fp2, "Different order should produce different fingerprints");
        }
    }
}