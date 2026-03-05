/**
 * Unit tests for WorkflowDNAOracle class
 *
 * Tests workflow DNA extraction and pattern matching using Chicago TDD principles:
 * - Real YAWL objects (XesToYawlSpecGenerator)
 * - Proper exception handling
 * - Test boundary conditions
 * - Verify semantic patterns
 */
package org.yawlfoundation.yawl.observatory;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle.DNARecommendation;
import org.yawlfoundation.yawl.observatory.rdf.WorkflowDNAOracle.DNASignature;
import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for WorkflowDNAOracle functionality
 *
 * Tests cover:
 * 1. DNA extraction with valid workflow
 * 2. Exception handling for null inputs
 * 3. Pattern matching with valid DNA
 * 4. High failure rate detection
 * 5. Alternative path mining
 */
class WorkflowDNAOracleTest {

    private WorkflowDNAOracle oracle;
    private MockXesGenerator mockXesGenerator;

    @BeforeEach
    void setUp() {
        // Initialize with a real XesToYawlSpecGenerator
        XesToYawlSpecGenerator testGenerator = new XesToYawlSpecGenerator();
        oracle = new WorkflowDNAOracle(testGenerator);
    }

    @Test
    @DisplayName("testExtractDNA_WithValidWorkflow_ReturnsDNA")
    void testExtractDNA_WithValidWorkflow_ReturnsDNA() {
        // Given: Valid workflow execution data
        String caseId = "test-case-001";
        String specId = "order-processing";
        List<String> activities = List.of("create", "validate", "ship");
        Map<String, Long> durations = Map.of("create", 100L, "validate", 200L, "ship", 300L);
        boolean caseFailed = false;

        // When: Absorb workflow execution
        assertDoesNotThrow(() -> {
            oracle.absorb(caseId, specId, activities, durations, caseFailed);
        }, "absorb should not throw with valid input");

        // Then: Verify case was absorbed
        assertEquals(1, oracle.getAbsorbedCaseCount(), "One case should be absorbed");

        // And: Pattern registry should be updated
        // Note: In a real test, we'd verify the pattern registry content
        // For now, we ensure no exceptions are thrown
    }

    @Test
    @DisplayName("testExtractDNA_WithNullWorkflow_ThrowsException")
    void testExtractDNA_WithNullWorkflow_ThrowsException() {
        // Given: Various null inputs
        Map<String, Long> durations = Map.of("create", 100L);

        // When & Then: Null parameters should throw NullPointerException
        assertThrows(
            NullPointerException.class,
            () -> oracle.absorb(null, "spec", List.of("task"), durations, false),
            "absorb should reject null caseId"
        );

        assertThrows(
            NullPointerException.class,
            () -> oracle.absorb("case", null, List.of("task"), durations, false),
            "absorb should reject null specId"
        );

        assertThrows(
            NullPointerException.class,
            () -> oracle.absorb("case", "spec", null, durations, false),
            "absorb should reject null activitySequence"
        );
    }

    @Test
    @DisplayName("testExtractDNA_WithEmptyActivities_ThrowsException")
    void testExtractDNA_WithEmptyActivities_ThrowsException() {
        // Given: Empty activity sequence
        Map<String, Long> durations = Map.of("create", 100L);

        // When & Then: Empty activities should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> oracle.absorb("case", "spec", List.of(), durations, false),
            "absorb should reject empty activitySequence"
        );
    }

    @Test
    @DisplayName("testQueryPattern_WithValidDNA_ReturnsMatches")
    void testQueryPattern_WithValidDNA_ReturnsMatches() {
        // Given: Absorb multiple successful cases
        absorbTestCases();

        // When: Query pattern for known activities
        DNARecommendation recommendation = oracle.assess(
            "new-case-001",
            "order-processing",
            List.of("create", "validate", "ship")
        );

        // Then: Should return valid recommendation
        assertNotNull(recommendation, "Recommendation should not be null");
        assertEquals("new-case-001", recommendation.caseId());
        assertEquals(0.0, recommendation.historicalFailureRate(), 0.001, "Low failure rate expected");
        assertFalse(recommendation.alternativePathXml().isPresent(), "No alternative path for low risk");
        assertTrue(recommendation.riskMessage().contains("Low failure risk"), "Risk message should indicate low risk");
    }

    @Test
    @DisplayName("testHighFailureDetection_ReturnsAlternativePath")
    void testHighFailureDetection_ReturnsAlternativePath() {
        // Given: Absorb cases with high failure rate
        absorbFailureCases();

        // When: Query pattern with high failure activities
        DNARecommendation recommendation = oracle.assess(
            "risky-case-001",
            "risky-process",
            List.of("create", "validate", "ship", "retry")
        );

        // Then: Should return high-risk recommendation
        assertNotNull(recommendation, "Recommendation should not be null");
        assertTrue(recommendation.historicalFailureRate() >= 0.23, "Should detect high failure rate");
        assertTrue(recommendation.riskMessage().contains("High failure risk"), "Risk message should indicate high risk");
        assertFalse(recommendation.alternativePathXml().isEmpty(), "Should offer alternative path for high risk");
    }

    @Test
    @DisplayName("testInsufficientData_ReturnsMonitoringMessage")
    void testInsufficientData_ReturnsMonitoringMessage() {
        // Given: Only a few cases absorbed (below threshold)
        oracle.absorb("case-001", "rare-process", List.of("task1"), Map.of(), false);

        // When: Query pattern with insufficient data
        DNARecommendation recommendation = oracle.assess(
            "new-case-001",
            "rare-process",
            List.of("task1")
        );

        // Then: Should return insufficient data recommendation
        assertNotNull(recommendation, "Recommendation should not be null");
        assertEquals("insufficient_history", recommendation.matchedPattern());
        assertTrue(recommendation.riskMessage().contains("Insufficient historical data"), "Should note insufficient data");
        assertTrue(recommendation.prePositionResources().isEmpty(), "No resource suggestions for insufficient data");
    }

    @Test
    @DisplayName("testAssess_WithNullInput_ThrowsException")
    void testAssess_WithNullInput_ThrowsException() {
        // Given: Fresh oracle

        // When & Then: Null parameters should throw NullPointerException
        assertThrows(
            NullPointerException.class,
            () -> oracle.assess(null, "spec", List.of("task")),
            "assess should reject null newCaseId"
        );

        assertThrows(
            NullPointerException.class,
            () -> oracle.assess("case", null, List.of("task")),
            "assess should reject null specId"
        );

        assertThrows(
            NullPointerException.class,
            () -> oracle.assess("case", "spec", null),
            "assess should reject null expectedActivities"
        );
    }

    @Test
    @DisplayName("testAssess_WithEmptyActivities_ThrowsException")
    void testAssess_WithEmptyActivities_ThrowsException() {
        // Given: Fresh oracle

        // When & Then: Empty activities should throw IllegalArgumentException
        assertThrows(
            IllegalArgumentException.class,
            () -> oracle.assess("case", "spec", List.of()),
            "assess should reject empty expectedActivities"
        );
    }

    @Test
    @DisplayName("testPruneOlderThan_WithValidDuration_PrunesCases")
    void testPruneOlderThan_WithValidDuration_PrunesCases() {
        // Given: Absorb cases with known timestamps
        oracle.absorb("old-case", "test-spec", List.of("task1"), Map.of(), false);
        int initialCount = oracle.getAbsorbedCaseCount();
        assertEquals(1, initialCount, "Should have one case");

        // When: Prune cases older than 0 seconds (should remove all)
        assertDoesNotThrow(() -> {
            oracle.pruneOlderThan(Duration.ofSeconds(0));
        }, "pruneOlderThan should not throw with valid duration");

        // Then: All cases should be pruned
        assertEquals(0, oracle.getAbsorbedCaseCount(), "All cases should be pruned");
    }

    @Test
    @DisplayName("testPruneOlderThan_WithNullDuration_ThrowsException")
    void testPruneOlderThan_WithNullDuration_ThrowsException() {
        // When & Then: Null duration should throw NullPointerException
        assertThrows(
            NullPointerException.class,
            () -> oracle.pruneOlderThan(null),
            "pruneOlderThan should reject null duration"
        );
    }

    @Test
    @DisplayName("testFingerprintDeterminism_SameActivitiesSameHash")
    void testFingerprintDeterminism_SameActivitiesSameHash() {
        // Given: Same activity sequences
        List<String> activities1 = List.of("create", "validate", "ship");
        List<String> activities2 = List.of("create", "validate", "ship");

        // When: Compute fingerprints
        String fp1 = oracle.getFingerprintForTest(activities1);
        String fp2 = oracle.getFingerprintForTest(activities2);

        // Then: Fingerprints should be identical
        assertEquals(fp1, fp2, "Same activity sequences should have identical fingerprints");
    }

    @Test
    @DisplayName("testFingerprintUniqueness_DifferentActivitiesDifferentHashes")
    void testFingerprintUniqueness_DifferentActivitiesDifferentHashes() {
        // Given: Different activity sequences
        List<String> activities1 = List.of("create", "validate", "ship");
        List<String> activities2 = List.of("create", "ship");
        List<String> activities3 = List.of("create", "validate", "ship", "retry");

        // When: Compute fingerprints
        String fp1 = oracle.getFingerprintForTest(activities1);
        String fp2 = oracle.getFingerprintForTest(activities2);
        String fp3 = oracle.getFingerprintForTest(activities3);

        // Then: Fingerprints should be different
        assertNotEquals(fp1, fp2, "Different activity sequences should have different fingerprints");
        assertNotEquals(fp1, fp3, "Different activity sequences should have different fingerprints");
        assertNotEquals(fp2, fp3, "Different activity sequences should have different fingerprints");
    }

    // Helper method to absorb test cases (successful)
    private void absorbTestCases() {
        // Create 7 successful cases to reach threshold
        for (int i = 0; i < 7; i++) {
            oracle.absorb(
                "success-case-" + i,
                "order-processing",
                List.of("create", "validate", "ship"),
                Map.of("create", 100L, "validate", 200L, "ship", 300L),
                false
            );
        }
    }

    // Helper method to absorb test cases with failures
    private void absorbFailureCases() {
        // Create 10 cases with 3 failures to reach 30% failure rate
        for (int i = 0; i < 7; i++) {
            oracle.absorb(
                "success-case-" + i,
                "risky-process",
                List.of("create", "validate"),
                Map.of("create", 100L, "validate", 200L),
                false
            );
        }

        // Add 3 failed cases
        for (int i = 7; i < 10; i++) {
            oracle.absorb(
                "fail-case-" + i,
                "risky-process",
                List.of("create", "validate", "ship", "retry"),
                Map.of("create", 100L, "validate", 200L, "ship", 300L, "retry", 50L),
                true
            );
        }
    }

    // Package-private method for testing fingerprint functionality
    String getFingerprintForTest(List<String> activities) {
        // This would be a private method, but we need access for testing
        // In production, this would use reflection or be part of a test-specific subclass
        try {
            var fingerprintMethod = WorkflowDNAOracle.class.getDeclaredMethod("fingerprint", List.class);
            fingerprintMethod.setAccessible(true);
            return (String) fingerprintMethod.invoke(oracle, activities);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke fingerprint method", e);
        }
    }

    }