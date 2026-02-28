package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the 43-pattern workflow pattern catalog.
 *
 * <p>Verifies that all 43 workflow patterns (WP-1 to WP-20, WCP-21 to WCP-43)
 * are present, correctly categorized, and accessible via the catalog.
 */
@DisplayName("WorkflowPattern 43-Pattern Catalog Tests")
class WorkflowPatternCatalog43Test {

    @Test
    @DisplayName("Enum has exactly 43 patterns")
    void testEnumSize() {
        assertEquals(43, WorkflowPattern.values().length,
            "WorkflowPattern enum must contain exactly 43 patterns");
    }

    @Test
    @DisplayName("Catalog.all() returns 43 patterns")
    void testCatalogSize() {
        assertEquals(43, WorkflowPatternCatalog.all().size(),
            "WorkflowPatternCatalog.all() must return 43 patterns");
    }

    @Test
    @DisplayName("Catalog.size() returns 43")
    void testCatalogSizeMethod() {
        assertEquals(43, WorkflowPatternCatalog.size(),
            "WorkflowPatternCatalog.size() must return 43");
    }

    @Test
    @DisplayName("All patterns have non-null code")
    void testAllPatternsCodes() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getCode(), "Pattern code cannot be null: " + pattern);
            assertFalse(pattern.getCode().isBlank(), "Pattern code cannot be blank: " + pattern);
        }
    }

    @Test
    @DisplayName("All patterns have non-null label")
    void testAllPatternsLabels() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getLabel(), "Pattern label cannot be null: " + pattern);
            assertFalse(pattern.getLabel().isBlank(), "Pattern label cannot be blank: " + pattern);
        }
    }

    @Test
    @DisplayName("All patterns have non-null category")
    void testAllPatternsCategories() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            assertNotNull(pattern.getCategory(), "Pattern category cannot be null: " + pattern);
        }
    }

    @Test
    @DisplayName("All patterns have valid MCP suitability (0-10)")
    void testAllPatternsMcpSuitability() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            int suitability = pattern.getMcpSuitability();
            assertTrue(suitability >= 0 && suitability <= 10,
                "MCP suitability must be 0-10 for " + pattern + ", got " + suitability);
        }
    }

    @Test
    @DisplayName("All patterns have valid A2A suitability (0-10)")
    void testAllPatternsA2aSuitability() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            int suitability = pattern.getA2aSuitability();
            assertTrue(suitability >= 0 && suitability <= 10,
                "A2A suitability must be 0-10 for " + pattern + ", got " + suitability);
        }
    }

    @Test
    @DisplayName("Original 20 patterns (WP-1 to WP-20) are present")
    void testOriginal20Patterns() {
        String[] originalCodes = {
            "WP-1", "WP-2", "WP-3", "WP-4", "WP-5",
            "WP-6", "WP-7", "WP-8", "WP-9", "WP-10",
            "WP-11", "WP-12", "WP-13", "WP-14", "WP-15",
            "WP-16", "WP-17", "WP-18", "WP-19", "WP-20"
        };

        for (String code : originalCodes) {
            Optional<WorkflowPattern> pattern = WorkflowPatternCatalog.findByCode(code);
            assertTrue(pattern.isPresent(), "Pattern " + code + " must be present");
        }
    }

    @Test
    @DisplayName("Extended 23 patterns (WCP-21 to WCP-43) are present")
    void testExtended23Patterns() {
        String[] extendedCodes = {
            "WCP-21", "WCP-22", "WCP-23", "WCP-24", "WCP-25",
            "WCP-26", "WCP-27", "WCP-28", "WCP-29", "WCP-30",
            "WCP-31", "WCP-32", "WCP-33", "WCP-34", "WCP-35",
            "WCP-36", "WCP-37", "WCP-38", "WCP-39", "WCP-40",
            "WCP-41", "WCP-42", "WCP-43"
        };

        for (String code : extendedCodes) {
            Optional<WorkflowPattern> pattern = WorkflowPatternCatalog.findByCode(code);
            assertTrue(pattern.isPresent(), "Pattern " + code + " must be present");
        }
    }

    @Test
    @DisplayName("New categories are present")
    void testNewCategories() {
        assertNotNull(PatternCategory.ITERATION, "PatternCategory.ITERATION must exist");
        assertNotNull(PatternCategory.TRIGGER, "PatternCategory.TRIGGER must exist");
        assertNotNull(PatternCategory.SYNCHRONISATION, "PatternCategory.SYNCHRONISATION must exist");
    }

    @Test
    @DisplayName("ITERATION category contains structured loop and recursion")
    void testIterationCategory() {
        List<WorkflowPattern> iterationPatterns = WorkflowPatternCatalog.byCategory(PatternCategory.ITERATION);
        assertTrue(iterationPatterns.size() >= 2, "ITERATION category should contain at least 2 patterns");

        boolean hasStructuredLoop = iterationPatterns.stream()
            .anyMatch(p -> "Structured Loop".equals(p.getLabel()));
        boolean hasRecursion = iterationPatterns.stream()
            .anyMatch(p -> "Recursion".equals(p.getLabel()));

        assertTrue(hasStructuredLoop, "ITERATION category should contain Structured Loop");
        assertTrue(hasRecursion, "ITERATION category should contain Recursion");
    }

    @Test
    @DisplayName("TRIGGER category contains trigger patterns")
    void testTriggerCategory() {
        List<WorkflowPattern> triggerPatterns = WorkflowPatternCatalog.byCategory(PatternCategory.TRIGGER);
        assertTrue(triggerPatterns.size() >= 2, "TRIGGER category should contain at least 2 patterns");

        boolean hasTransientTrigger = triggerPatterns.stream()
            .anyMatch(p -> "Transient Trigger".equals(p.getLabel()));
        boolean hasPersistentTrigger = triggerPatterns.stream()
            .anyMatch(p -> "Persistent Trigger".equals(p.getLabel()));

        assertTrue(hasTransientTrigger, "TRIGGER category should contain Transient Trigger");
        assertTrue(hasPersistentTrigger, "TRIGGER category should contain Persistent Trigger");
    }

    @Test
    @DisplayName("SYNCHRONISATION category contains generalised AND-join")
    void testSynchronisationCategory() {
        List<WorkflowPattern> syncPatterns = WorkflowPatternCatalog.byCategory(PatternCategory.SYNCHRONISATION);
        assertTrue(syncPatterns.size() >= 1, "SYNCHRONISATION category should contain at least 1 pattern");

        boolean hasGeneralisedAndJoin = syncPatterns.stream()
            .anyMatch(p -> "Generalised AND-Join".equals(p.getLabel()));

        assertTrue(hasGeneralisedAndJoin, "SYNCHRONISATION category should contain Generalised AND-Join");
    }

    @Test
    @DisplayName("Specific patterns have correct labels")
    void testSpecificPatternLabels() {
        WorkflowPattern structuredLoop = WorkflowPattern.valueOf("STRUCTURED_LOOP");
        assertEquals("Structured Loop", structuredLoop.getLabel());

        WorkflowPattern recursion = WorkflowPattern.valueOf("RECURSION");
        assertEquals("Recursion", recursion.getLabel());

        WorkflowPattern transientTrigger = WorkflowPattern.valueOf("TRANSIENT_TRIGGER");
        assertEquals("Transient Trigger", transientTrigger.getLabel());

        WorkflowPattern persistentTrigger = WorkflowPattern.valueOf("PERSISTENT_TRIGGER");
        assertEquals("Persistent Trigger", persistentTrigger.getLabel());

        WorkflowPattern explicitTermination = WorkflowPattern.valueOf("EXPLICIT_TERMINATION");
        assertEquals("Explicit Termination", explicitTermination.getLabel());
    }

    @Test
    @DisplayName("Pattern codes match expected format")
    void testPatternCodeFormat() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            String code = pattern.getCode();
            assertTrue(code.matches("(WP|WCP)-\\d+"),
                "Pattern code must match (WP|WCP)-\\d+ format: " + code);
        }
    }

    @Test
    @DisplayName("Extended patterns metadata is accessible")
    void testExtendedPatternMetadata() {
        List<ExtendedPatternMetadata.PatternRef> catalog = ExtendedPatternMetadata.getExtendedCatalog();
        assertEquals(23, catalog.size(), "Extended metadata catalog should contain 23 patterns");
    }

    @Test
    @DisplayName("Extended metadata find by code works")
    void testExtendedMetadataFindByCode() {
        Optional<ExtendedPatternMetadata.PatternRef> wcp21 = ExtendedPatternMetadata.findByCode("WCP-21");
        assertTrue(wcp21.isPresent(), "WCP-21 should be found in extended metadata");
        assertEquals("Structured Loop", wcp21.get().name());

        Optional<ExtendedPatternMetadata.PatternRef> wcp43 = ExtendedPatternMetadata.findByCode("WCP-43");
        assertTrue(wcp43.isPresent(), "WCP-43 should be found in extended metadata");
    }

    @Test
    @DisplayName("Extended metadata support levels are valid")
    void testExtendedMetadataSupportLevels() {
        for (ExtendedPatternMetadata.PatternRef ref : ExtendedPatternMetadata.getExtendedCatalog()) {
            assertTrue(ref.yawlSupport().matches("(FULL|PARTIAL|NONE)"),
                "Support level must be FULL, PARTIAL, or NONE: " + ref.wcpCode());
        }
    }

    @Test
    @DisplayName("Fully supported patterns are retrievable")
    void testFullySupportedPatterns() {
        List<ExtendedPatternMetadata.PatternRef> fullSupport = ExtendedPatternMetadata.getFullySupported();
        assertFalse(fullSupport.isEmpty(), "There should be some fully supported patterns");

        // Verify they're all marked as FULL
        for (ExtendedPatternMetadata.PatternRef ref : fullSupport) {
            assertEquals("FULL", ref.yawlSupport());
        }
    }

    @Test
    @DisplayName("Pattern advisor handles new categories")
    void testPatternAdvisorNewCategories() {
        // Test that advisor can score patterns in new categories
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            2, 2, List.of("loop", "iteration")
        );
        assertTrue(recommendations.size() > 0, "Advisor should return recommendations");

        // Verify at least one is from ITERATION category
        boolean hasIterationPattern = recommendations.stream()
            .anyMatch(p -> p.getCategory() == PatternCategory.ITERATION);
        assertTrue(hasIterationPattern, "Should recommend ITERATION patterns for 'iteration' requirement");
    }

    @Test
    @DisplayName("All patterns are findable by code")
    void testAllPatternsFindableByCode() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            Optional<WorkflowPattern> found = WorkflowPatternCatalog.findByCode(pattern.getCode());
            assertTrue(found.isPresent(), "Pattern " + pattern.getCode() + " should be findable");
            assertEquals(pattern, found.get(), "Found pattern should match original");
        }
    }

    @Test
    @DisplayName("All patterns are findable by label")
    void testAllPatternsFindableByLabel() {
        for (WorkflowPattern pattern : WorkflowPattern.values()) {
            Optional<WorkflowPattern> found = WorkflowPatternCatalog.findByLabel(pattern.getLabel());
            assertTrue(found.isPresent(), "Pattern " + pattern.getLabel() + " should be findable");
            assertEquals(pattern, found.get(), "Found pattern should match original");
        }
    }

    @Test
    @DisplayName("Pattern codes are unique")
    void testPatternCodesUnique() {
        List<String> codes = WorkflowPatternCatalog.all().stream()
            .map(WorkflowPattern::getCode)
            .toList();
        assertEquals(codes.size(), codes.stream().distinct().count(),
            "All pattern codes must be unique");
    }

    @Test
    @DisplayName("Pattern labels are unique")
    void testPatternLabelsUnique() {
        List<String> labels = WorkflowPatternCatalog.all().stream()
            .map(WorkflowPattern::getLabel)
            .toList();
        assertEquals(labels.size(), labels.stream().distinct().count(),
            "All pattern labels must be unique");
    }

    @Test
    @DisplayName("Catalog prints all patterns")
    void testCatalogPrint() {
        String output = WorkflowPatternCatalog.printCatalog();
        assertNotNull(output);
        assertFalse(output.isEmpty());
        assertTrue(output.contains("Van der Aalst"), "Output should contain header");
        assertTrue(output.contains("WP-1") || output.contains("Sequence"), "Output should contain pattern info");
    }
}
