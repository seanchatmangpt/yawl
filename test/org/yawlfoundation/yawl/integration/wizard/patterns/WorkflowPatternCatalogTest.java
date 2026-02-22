package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the workflow pattern catalog and lookup operations.
 *
 * <p>Verifies catalog operations including byCategory, byCode, suitability
 * filtering, and pattern comparison functionality.
 */
@DisplayName("Workflow Pattern Catalog Tests")
class WorkflowPatternCatalogTest {

    @Test
    @DisplayName("Catalog contains all 20 patterns")
    void testCatalogSize() {
        List<WorkflowPattern> allPatterns = WorkflowPatternCatalog.all();
        assertEquals(20, allPatterns.size(), "Catalog should contain all 20 patterns");
    }

    @Test
    @DisplayName("size() returns 20")
    void testCatalogSizeMethod() {
        assertEquals(20, WorkflowPatternCatalog.size(), "Catalog size should be 20");
    }

    @Test
    @DisplayName("byCategory returns correct count per category")
    void testByCategoryFiltering() {
        assertEquals(5, WorkflowPatternCatalog.byCategory(PatternCategory.BASIC).size(),
            "BASIC category should have 5 patterns");
        assertEquals(4, WorkflowPatternCatalog.byCategory(PatternCategory.ADVANCED_BRANCHING).size(),
            "ADVANCED_BRANCHING category should have 4 patterns");
        assertEquals(2, WorkflowPatternCatalog.byCategory(PatternCategory.STRUCTURAL).size(),
            "STRUCTURAL category should have 2 patterns");
        assertEquals(4, WorkflowPatternCatalog.byCategory(PatternCategory.MULTIPLE_INSTANCES).size(),
            "MULTIPLE_INSTANCES category should have 4 patterns");
        assertEquals(3, WorkflowPatternCatalog.byCategory(PatternCategory.STATE_BASED).size(),
            "STATE_BASED category should have 3 patterns");
        assertEquals(2, WorkflowPatternCatalog.byCategory(PatternCategory.CANCELLATION).size(),
            "CANCELLATION category should have 2 patterns");
    }

    @Test
    @DisplayName("byCategory returns immutable list")
    void testByCategoryReturnsImmutable() {
        List<WorkflowPattern> basic = WorkflowPatternCatalog.byCategory(PatternCategory.BASIC);
        assertThrows(UnsupportedOperationException.class,
            () -> basic.add(WorkflowPattern.SEQUENCE),
            "Returned list should be immutable");
    }

    @Test
    @DisplayName("findByCode finds patterns correctly")
    void testFindByCode() {
        Optional<WorkflowPattern> wp1 = WorkflowPatternCatalog.findByCode("WP-1");
        assertTrue(wp1.isPresent());
        assertEquals(WorkflowPattern.SEQUENCE, wp1.get());

        Optional<WorkflowPattern> wp20 = WorkflowPatternCatalog.findByCode("WP-20");
        assertTrue(wp20.isPresent());
        assertEquals(WorkflowPattern.CANCEL_CASE, wp20.get());

        Optional<WorkflowPattern> invalid = WorkflowPatternCatalog.findByCode("WP-999");
        assertTrue(invalid.isEmpty());
    }

    @Test
    @DisplayName("findByCode is case-insensitive")
    void testFindByCodeCaseInsensitive() {
        Optional<WorkflowPattern> upper = WorkflowPatternCatalog.findByCode("WP-1");
        Optional<WorkflowPattern> lower = WorkflowPatternCatalog.findByCode("wp-1");
        Optional<WorkflowPattern> mixed = WorkflowPatternCatalog.findByCode("Wp-1");

        assertEquals(upper.get(), lower.get());
        assertEquals(upper.get(), mixed.get());
    }

    @Test
    @DisplayName("findByLabel finds patterns correctly")
    void testFindByLabel() {
        Optional<WorkflowPattern> sequence = WorkflowPatternCatalog.findByLabel("Sequence");
        assertTrue(sequence.isPresent());
        assertEquals(WorkflowPattern.SEQUENCE, sequence.get());

        Optional<WorkflowPattern> parallel = WorkflowPatternCatalog.findByLabel("Parallel Split (AND-split)");
        assertTrue(parallel.isPresent());
        assertEquals(WorkflowPattern.PARALLEL_SPLIT, parallel.get());

        Optional<WorkflowPattern> invalid = WorkflowPatternCatalog.findByLabel("Nonexistent Pattern");
        assertTrue(invalid.isEmpty());
    }

    @Test
    @DisplayName("findByLabel is case-insensitive")
    void testFindByLabelCaseInsensitive() {
        Optional<WorkflowPattern> exact = WorkflowPatternCatalog.findByLabel("Sequence");
        Optional<WorkflowPattern> lower = WorkflowPatternCatalog.findByLabel("sequence");
        Optional<WorkflowPattern> upper = WorkflowPatternCatalog.findByLabel("SEQUENCE");

        assertEquals(exact.get(), lower.get());
        assertEquals(exact.get(), upper.get());
    }

    @Test
    @DisplayName("suitableForMcp filters patterns correctly")
    void testSuitableForMcp() {
        List<WorkflowPattern> suitable = WorkflowPatternCatalog.suitableForMcp();
        assertTrue(suitable.size() > 0, "Should have at least one MCP-suitable pattern");

        for (WorkflowPattern p : suitable) {
            assertTrue(p.getMcpSuitability() >= 7, String.format("%s should have MCP suitability >= 7", p.getCode()));
        }
    }

    @Test
    @DisplayName("suitableForMcp with custom threshold works")
    void testSuitableForMcpCustomThreshold() {
        List<WorkflowPattern> minScore5 = WorkflowPatternCatalog.suitableForMcp(5);
        List<WorkflowPattern> minScore7 = WorkflowPatternCatalog.suitableForMcp(7);
        List<WorkflowPattern> minScore10 = WorkflowPatternCatalog.suitableForMcp(10);

        assertTrue(minScore5.size() >= minScore7.size(), "Lower threshold should have more patterns");
        assertTrue(minScore7.size() >= minScore10.size(), "Higher threshold should have fewer patterns");
    }

    @Test
    @DisplayName("suitableForA2a filters patterns correctly")
    void testSuitableForA2a() {
        List<WorkflowPattern> suitable = WorkflowPatternCatalog.suitableForA2a();
        assertTrue(suitable.size() > 0, "Should have at least one A2A-suitable pattern");

        for (WorkflowPattern p : suitable) {
            assertTrue(p.getA2aSuitability() >= 7, String.format("%s should have A2A suitability >= 7", p.getCode()));
        }
    }

    @Test
    @DisplayName("suitableForA2a with custom threshold works")
    void testSuitableForA2aCustomThreshold() {
        List<WorkflowPattern> minScore5 = WorkflowPatternCatalog.suitableForA2a(5);
        List<WorkflowPattern> minScore7 = WorkflowPatternCatalog.suitableForA2a(7);
        List<WorkflowPattern> minScore10 = WorkflowPatternCatalog.suitableForA2a(10);

        assertTrue(minScore5.size() >= minScore7.size(), "Lower threshold should have more patterns");
        assertTrue(minScore7.size() >= minScore10.size(), "Higher threshold should have fewer patterns");
    }

    @Test
    @DisplayName("compare evaluates MCP suitability correctly")
    void testCompareMcp() {
        WorkflowPattern a = WorkflowPattern.SEQUENCE;        // MCP 9
        WorkflowPattern b = WorkflowPattern.ARBITRARY_CYCLES; // MCP 4

        WorkflowPatternCatalog.PatternComparison comp = WorkflowPatternCatalog.compare(a, b, true);
        assertEquals(a, comp.preferred(), "Higher MCP suitability should be preferred");
        assertTrue(comp.score() > 0);
        assertFalse(comp.isTie());
    }

    @Test
    @DisplayName("compare evaluates A2A suitability correctly")
    void testCompareA2a() {
        WorkflowPattern a = WorkflowPattern.DEFERRED_CHOICE; // A2A 8
        WorkflowPattern b = WorkflowPattern.ARBITRARY_CYCLES; // A2A 4

        WorkflowPatternCatalog.PatternComparison comp = WorkflowPatternCatalog.compare(a, b, false);
        assertEquals(a, comp.preferred(), "Higher A2A suitability should be preferred");
        assertTrue(comp.score() > 0);
        assertFalse(comp.isTie());
    }

    @Test
    @DisplayName("compare detects ties")
    void testCompareTie() {
        WorkflowPattern a = WorkflowPattern.SEQUENCE;           // Both 9
        WorkflowPattern b = WorkflowPattern.PARALLEL_SPLIT;     // Both 8
        WorkflowPattern c = WorkflowPattern.SYNCHRONIZATION;    // Both 8

        WorkflowPatternCatalog.PatternComparison comp = WorkflowPatternCatalog.compare(b, c, true);
        assertTrue(comp.isTie(), "Should detect tie");
        assertEquals(0, comp.score());
    }

    @Test
    @DisplayName("bestForMcp finds pattern with highest MCP suitability")
    void testBestForMcp() {
        List<WorkflowPattern> patterns = WorkflowPatternCatalog.byCategory(PatternCategory.BASIC);
        WorkflowPattern best = WorkflowPatternCatalog.bestForMcp(patterns);

        for (WorkflowPattern p : patterns) {
            assertTrue(best.getMcpSuitability() >= p.getMcpSuitability(),
                String.format("bestForMcp should return highest or equal MCP score, got %d < %d",
                    best.getMcpSuitability(), p.getMcpSuitability()));
        }
    }

    @Test
    @DisplayName("bestForMcp throws on empty list")
    void testBestForMcpEmpty() {
        assertThrows(IllegalArgumentException.class,
            () -> WorkflowPatternCatalog.bestForMcp(List.of()),
            "Should throw on empty list");
    }

    @Test
    @DisplayName("bestForA2a finds pattern with highest A2A suitability")
    void testBestForA2a() {
        List<WorkflowPattern> patterns = WorkflowPatternCatalog.byCategory(PatternCategory.BASIC);
        WorkflowPattern best = WorkflowPatternCatalog.bestForA2a(patterns);

        for (WorkflowPattern p : patterns) {
            assertTrue(best.getA2aSuitability() >= p.getA2aSuitability(),
                String.format("bestForA2a should return highest or equal A2A score, got %d < %d",
                    best.getA2aSuitability(), p.getA2aSuitability()));
        }
    }

    @Test
    @DisplayName("bestForA2a throws on empty list")
    void testBestForA2aEmpty() {
        assertThrows(IllegalArgumentException.class,
            () -> WorkflowPatternCatalog.bestForA2a(List.of()),
            "Should throw on empty list");
    }

    @Test
    @DisplayName("printCatalog returns non-empty string")
    void testPrintCatalog() {
        String catalog = WorkflowPatternCatalog.printCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.length() > 0);
        assertTrue(catalog.contains("WP-1"));
        assertTrue(catalog.contains("WP-20"));
        assertTrue(catalog.contains("BASIC"));
        assertTrue(catalog.contains("CANCELLATION"));
    }

    @Test
    @DisplayName("PatternComparison record validates fields")
    void testPatternComparisonValidation() {
        assertThrows(NullPointerException.class,
            () -> new WorkflowPatternCatalog.PatternComparison(null, "reason", 0),
            "Should reject null preferred");

        assertThrows(NullPointerException.class,
            () -> new WorkflowPatternCatalog.PatternComparison(WorkflowPattern.SEQUENCE, null, 0),
            "Should reject null reason");
    }

    @Test
    @DisplayName("All methods throw on null arguments")
    void testNullSafety() {
        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.byCategory(null));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.suitableForMcp(-1));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.findByCode(null));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.findByLabel(null));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.compare(null, WorkflowPattern.SEQUENCE, true));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.bestForMcp(null));

        assertThrows(NullPointerException.class,
            () -> WorkflowPatternCatalog.bestForA2a(null));
    }
}
