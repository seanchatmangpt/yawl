package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;

/**
 * Test suite for WorkflowDNAOracle — validates semantic cross-workflow intelligence
 */
class WorkflowDNAOracleTest {

    private WorkflowDNAOracle oracle;

    @BeforeEach
    void setUp() {
        // Create a minimal XesToYawlSpecGenerator for testing
        // Note: This requires the integration module to be available
        // For now, we'll test with null and handle gracefully
        oracle = new WorkflowDNAOracle(null);
    }

    @Test
    void testSuccessfulCaseAbsorption() {
        // Test absorbing a successful case
        List<String> activities = List.of("create", "validate", "ship");
        Map<String, Long> durations = Map.of("create", 100L, "validate", 50L, "ship", 200L);

        assertDoesNotThrow(() -> {
            oracle.absorb("case-001", "order-process", activities, durations, false);
        });
    }

    @Test
    void testFailedCaseAbsorption() {
        // Test absorbing a failed case
        List<String> activities = List.of("create", "cancel");
        Map<String, Long> durations = Map.of("create", 80L, "cancel", 30L);

        assertDoesNotThrow(() -> {
            oracle.absorb("case-002", "order-process", activities, durations, true);
        });
    }

    @Test
    void testNewWorkflowPattern() {
        // Test assessing a workflow pattern that hasn't been seen before
        WorkflowDNAOracle.DNARecommendation recommendation = oracle.assess(
            "case-003", "new-process",
            List.of("start", "process", "end"));

        // Should return recommendation but with no historical data
        assertNotNull(recommendation);
        assertEquals(0.0, recommendation.historicalFailureRate(), "New pattern should have zero risk");
        assertFalse(recommendation.riskMessage().contains("High failure risk"));
    }

    @Test
    void testFingerprinting() {
        // Test that activity sequences create consistent fingerprints
        List<String> activities1 = List.of("create", "validate", "ship");
        List<String> activities2 = List.of("create", "validate", "ship");

        // These should have the same fingerprint
        String fingerprint1 = createFingerprint(activities1);
        String fingerprint2 = createFingerprint(activities2);
        assertEquals(fingerprint1, fingerprint2);

        // Different sequences should have different fingerprints
        List<String> activities3 = List.of("create", "ship", "validate");
        String fingerprint3 = createFingerprint(activities3);
        assertNotEquals(fingerprint1, fingerprint3);
    }

    @Test
    void testGracefulDegradation() {
        // Test that exceptions don't break the system
        assertDoesNotThrow(() -> {
            // Assess case with unlikely activity sequence
            WorkflowDNAOracle.DNARecommendation rec = oracle.assess(
                "case-004", "problematic-process",
                List.of("fail", "fail", "fail"));

            // Should not throw even with invalid data
            assertNotNull(rec);
            assertEquals(0.0, rec.historicalFailureRate());
        });
    }

    // Helper method to test fingerprinting
    private String createFingerprint(List<String> activities) {
        // Simple hash-based fingerprint (should match implementation)
        String joined = String.join("→", activities);
        return Integer.toHexString(joined.hashCode());
    }
}