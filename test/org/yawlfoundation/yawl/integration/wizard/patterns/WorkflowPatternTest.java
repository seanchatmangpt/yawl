package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for van der Aalst's 20 workflow patterns.
 *
 * <p>Verifies all patterns are correctly defined with proper codes, labels,
 * categories, and suitability scores.
 */
@DisplayName("Workflow Pattern Tests")
class WorkflowPatternTest {

    @Test
    @DisplayName("All 20 patterns exist")
    void testAllPatternsExist() {
        WorkflowPattern[] patterns = WorkflowPattern.values();
        assertEquals(20, patterns.length, "Expected exactly 20 workflow patterns");
    }

    @Test
    @DisplayName("Basic patterns (WP-1 to WP-5) are correct")
    void testBasicPatterns() {
        assertPattern(WorkflowPattern.SEQUENCE, "WP-1", "Sequence", PatternCategory.BASIC);
        assertPattern(WorkflowPattern.PARALLEL_SPLIT, "WP-2", "Parallel Split (AND-split)", PatternCategory.BASIC);
        assertPattern(WorkflowPattern.SYNCHRONIZATION, "WP-3", "Synchronization (AND-join)", PatternCategory.BASIC);
        assertPattern(WorkflowPattern.EXCLUSIVE_CHOICE, "WP-4", "Exclusive Choice (XOR-split)", PatternCategory.BASIC);
        assertPattern(WorkflowPattern.SIMPLE_MERGE, "WP-5", "Simple Merge (XOR-join)", PatternCategory.BASIC);
    }

    @Test
    @DisplayName("Advanced branching patterns (WP-6 to WP-9) are correct")
    void testAdvancedBranchingPatterns() {
        assertPattern(WorkflowPattern.MULTI_CHOICE, "WP-6", "Multi-Choice (OR-split)", PatternCategory.ADVANCED_BRANCHING);
        assertPattern(WorkflowPattern.STRUCTURED_SYNC_MERGE, "WP-7", "Structured Synchronizing Merge", PatternCategory.ADVANCED_BRANCHING);
        assertPattern(WorkflowPattern.MULTI_MERGE, "WP-8", "Multi-Merge", PatternCategory.ADVANCED_BRANCHING);
        assertPattern(WorkflowPattern.STRUCTURED_DISCRIMINATOR, "WP-9", "Structured Discriminator", PatternCategory.ADVANCED_BRANCHING);
    }

    @Test
    @DisplayName("Structural patterns (WP-10 to WP-11) are correct")
    void testStructuralPatterns() {
        assertPattern(WorkflowPattern.ARBITRARY_CYCLES, "WP-10", "Arbitrary Cycles", PatternCategory.STRUCTURAL);
        assertPattern(WorkflowPattern.IMPLICIT_TERMINATION, "WP-11", "Implicit Termination", PatternCategory.STRUCTURAL);
    }

    @Test
    @DisplayName("Multiple instance patterns (WP-12 to WP-15) are correct")
    void testMultipleInstancePatterns() {
        assertPattern(WorkflowPattern.MI_WITHOUT_SYNC, "WP-12", "Multiple Instances Without Synchronization", PatternCategory.MULTIPLE_INSTANCES);
        assertPattern(WorkflowPattern.MI_WITH_APRIORI_DESIGN, "WP-13", "Multiple Instances with A Priori Design-Time Knowledge", PatternCategory.MULTIPLE_INSTANCES);
        assertPattern(WorkflowPattern.MI_WITH_APRIORI_RUNTIME, "WP-14", "Multiple Instances with A Priori Runtime Knowledge", PatternCategory.MULTIPLE_INSTANCES);
        assertPattern(WorkflowPattern.MI_WITHOUT_APRIORI, "WP-15", "Multiple Instances Without A Priori Runtime Knowledge", PatternCategory.MULTIPLE_INSTANCES);
    }

    @Test
    @DisplayName("State-based patterns (WP-16 to WP-18) are correct")
    void testStateBasedPatterns() {
        assertPattern(WorkflowPattern.DEFERRED_CHOICE, "WP-16", "Deferred Choice", PatternCategory.STATE_BASED);
        assertPattern(WorkflowPattern.INTERLEAVED_PARALLEL, "WP-17", "Interleaved Parallel Routing", PatternCategory.STATE_BASED);
        assertPattern(WorkflowPattern.MILESTONE, "WP-18", "Milestone", PatternCategory.STATE_BASED);
    }

    @Test
    @DisplayName("Cancellation patterns (WP-19 to WP-20) are correct")
    void testCancellationPatterns() {
        assertPattern(WorkflowPattern.CANCEL_TASK, "WP-19", "Cancel Task", PatternCategory.CANCELLATION);
        assertPattern(WorkflowPattern.CANCEL_CASE, "WP-20", "Cancel Case", PatternCategory.CANCELLATION);
    }

    @Test
    @DisplayName("All patterns have valid suitability scores")
    void testSuitabilityScores() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertTrue(pattern.getMcpSuitability() >= 0 && pattern.getMcpSuitability() <= 10,
                String.format("%s MCP suitability out of range: %d", pattern.getCode(), pattern.getMcpSuitability()));
            assertTrue(pattern.getA2aSuitability() >= 0 && pattern.getA2aSuitability() <= 10,
                String.format("%s A2A suitability out of range: %d", pattern.getCode(), pattern.getA2aSuitability()));
        }
    }

    @Test
    @DisplayName("All patterns have descriptions")
    void testDescriptions() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getDescription(), String.format("%s has no description", pattern.getCode()));
            assertTrue(pattern.getDescription().length() > 0, String.format("%s has empty description", pattern.getCode()));
        }
    }

    @Test
    @DisplayName("All patterns have Petri net notation")
    void testPetriNotation() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getPetriNotation(), String.format("%s has no Petri notation", pattern.getCode()));
            assertTrue(pattern.getPetriNotation().length() > 0, String.format("%s has empty Petri notation", pattern.getCode()));
        }
    }

    @Test
    @DisplayName("Pattern code lookup works")
    void testPatternCodeLookup() {
        Optional<WorkflowPattern> pattern = WorkflowPattern.forCode("WP-1");
        assertTrue(pattern.isPresent());
        assertEquals(WorkflowPattern.SEQUENCE, pattern.get());
    }

    @Test
    @DisplayName("Pattern code lookup is case-insensitive")
    void testPatternCodeLookupCaseInsensitive() {
        Optional<WorkflowPattern> pattern1 = WorkflowPattern.forCode("wp-1");
        Optional<WorkflowPattern> pattern2 = WorkflowPattern.forCode("WP-1");
        Optional<WorkflowPattern> pattern3 = WorkflowPattern.forCode("Wp-1");

        assertTrue(pattern1.isPresent());
        assertTrue(pattern2.isPresent());
        assertTrue(pattern3.isPresent());
        assertEquals(pattern1.get(), pattern2.get());
        assertEquals(pattern2.get(), pattern3.get());
    }

    @Test
    @DisplayName("Invalid pattern code returns empty")
    void testInvalidPatternCode() {
        Optional<WorkflowPattern> pattern = WorkflowPattern.forCode("WP-99");
        assertTrue(pattern.isEmpty());
    }

    @Test
    @DisplayName("Code map contains all 20 patterns")
    void testCodeMap() {
        var codeMap = WorkflowPattern.codeMap();
        assertEquals(20, codeMap.size());

        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertTrue(codeMap.containsKey(pattern.getCode()));
            assertEquals(pattern, codeMap.get(pattern.getCode()));
        }
    }

    @Test
    @DisplayName("MCP-suitable patterns identified correctly")
    void testMcpSuitability() {
        assertTrue(WorkflowPattern.SEQUENCE.isSuitableForMcp());
        assertTrue(WorkflowPattern.PARALLEL_SPLIT.isSuitableForMcp());
        assertTrue(WorkflowPattern.SYNCHRONIZATION.isSuitableForMcp());
        assertTrue(WorkflowPattern.EXCLUSIVE_CHOICE.isSuitableForMcp());
        assertTrue(WorkflowPattern.SIMPLE_MERGE.isSuitableForMcp());

        assertFalse(WorkflowPattern.ARBITRARY_CYCLES.isSuitableForMcp());
        assertFalse(WorkflowPattern.MI_WITHOUT_APRIORI.isSuitableForMcp());
    }

    @Test
    @DisplayName("A2A-suitable patterns identified correctly")
    void testA2aSuitability() {
        assertTrue(WorkflowPattern.SEQUENCE.isSuitableForA2a());
        assertTrue(WorkflowPattern.PARALLEL_SPLIT.isSuitableForA2a());
        assertTrue(WorkflowPattern.DEFERRED_CHOICE.isSuitableForA2a());
        assertTrue(WorkflowPattern.CANCEL_TASK.isSuitableForA2a());

        assertFalse(WorkflowPattern.ARBITRARY_CYCLES.isSuitableForA2a());
        assertFalse(WorkflowPattern.MI_WITHOUT_APRIORI.isSuitableForA2a());
    }

    @Test
    @DisplayName("All pattern categories are used")
    void testAllCategoriesUsed() {
        List<WorkflowPattern> basic = WorkflowPatternCatalog.byCategory(PatternCategory.BASIC);
        List<WorkflowPattern> advancedBranching = WorkflowPatternCatalog.byCategory(PatternCategory.ADVANCED_BRANCHING);
        List<WorkflowPattern> structural = WorkflowPatternCatalog.byCategory(PatternCategory.STRUCTURAL);
        List<WorkflowPattern> multipleInstances = WorkflowPatternCatalog.byCategory(PatternCategory.MULTIPLE_INSTANCES);
        List<WorkflowPattern> stateBased = WorkflowPatternCatalog.byCategory(PatternCategory.STATE_BASED);
        List<WorkflowPattern> cancellation = WorkflowPatternCatalog.byCategory(PatternCategory.CANCELLATION);

        assertEquals(5, basic.size());
        assertEquals(4, advancedBranching.size());
        assertEquals(2, structural.size());
        assertEquals(4, multipleInstances.size());
        assertEquals(3, stateBased.size());
        assertEquals(2, cancellation.size());
    }

    @Test
    @DisplayName("Pattern codes are unique")
    void testUniqueCodes() {
        var codes = WorkflowPattern.codeMap();
        assertEquals(20, codes.size(), "All pattern codes should be unique");
    }

    @Test
    @DisplayName("Pattern labels are descriptive")
    void testLabels() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getLabel());
            assertTrue(pattern.getLabel().length() > 0);
            assertFalse(pattern.getLabel().contains("WP-"));  // Label should be human-readable, not code
        }
    }

    /**
     * Helper method to assert pattern properties.
     */
    private void assertPattern(
        WorkflowPattern pattern,
        String expectedCode,
        String expectedLabel,
        PatternCategory expectedCategory
    ) {
        assertEquals(expectedCode, pattern.getCode());
        assertEquals(expectedLabel, pattern.getLabel());
        assertEquals(expectedCategory, pattern.getCategory());
        assertNotNull(pattern.getDescription());
        assertTrue(pattern.getDescription().length() > 0);
    }
}
