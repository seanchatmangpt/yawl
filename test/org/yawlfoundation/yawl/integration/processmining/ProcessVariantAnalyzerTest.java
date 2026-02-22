/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessVariantAnalyzer.
 *
 * Tests cover:
 * - Single trace → 1 variant, 100% coverage
 * - Multiple identical traces → 1 variant
 * - Multiple different traces → multiple variants with correct frequencies
 * - Top-N variant retrieval
 * - Deviating case identification
 * - Empty logs
 * - Single-event traces
 */
@DisplayName("ProcessVariantAnalyzer Tests")
class ProcessVariantAnalyzerTest {

    private final ProcessVariantAnalyzer analyzer = new ProcessVariantAnalyzer();

    @Test
    @DisplayName("Test 1: Single trace → 1 variant, 100% coverage")
    void testSingleTrace() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.totalTraces, "Should have 1 trace");
        assertEquals(1, result.variantCount, "Should have 1 variant");
        assertEquals(1.0, result.topVariantCoverage, 0.001, "Top variant should be 100%");
        assertEquals(1.0, result.top5Coverage, 0.001, "Top-5 coverage should be 100%");

        assertEquals(1, result.variants.size());
        ProcessVariantAnalyzer.Variant variant = result.variants.get(0);
        assertEquals(List.of("A", "B", "C"), variant.activities);
        assertEquals(1, variant.frequency);
        assertEquals(1.0, variant.relativeFrequency, 0.001);
        assertEquals(Set.of("case-001"), variant.caseIds);
    }

    @Test
    @DisplayName("Test 2: Two identical traces → 1 variant, 100% coverage")
    void testTwoIdenticalTraces() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(2, result.totalTraces);
        assertEquals(1, result.variantCount, "Should have 1 variant (both traces identical)");
        assertEquals(1.0, result.topVariantCoverage, 0.001);

        ProcessVariantAnalyzer.Variant variant = result.variants.get(0);
        assertEquals(List.of("A", "B", "C"), variant.activities);
        assertEquals(2, variant.frequency);
        assertEquals(1.0, variant.relativeFrequency, 0.001);
        assertEquals(Set.of("case-001", "case-002"), variant.caseIds);
    }

    @Test
    @DisplayName("Test 3: Two different traces → 2 variants, 50% each")
    void testTwoDifferentTraces() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(2, result.totalTraces);
        assertEquals(2, result.variantCount, "Should have 2 distinct variants");
        assertEquals(0.5, result.topVariantCoverage, 0.001, "Each variant should be 50%");
        assertEquals(1.0, result.top5Coverage, 0.001, "Top-5 should cover all 100%");

        // Both variants should have frequency 1 and relative frequency 0.5
        for (ProcessVariantAnalyzer.Variant v : result.variants) {
            assertEquals(1, v.frequency);
            assertEquals(0.5, v.relativeFrequency, 0.001);
        }
    }

    @Test
    @DisplayName("Test 4: Three traces, two variants (2+1 split)")
    void testThreeTracesWithTwoVariants() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="D"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.totalTraces);
        assertEquals(2, result.variantCount, "Should have 2 distinct variants");

        // Top variant should be (A, B, C) with 2 cases = 66.7%
        assertEquals(2.0 / 3.0, result.topVariantCoverage, 0.001);

        // First variant (most frequent)
        ProcessVariantAnalyzer.Variant variant1 = result.variants.get(0);
        assertEquals(List.of("A", "B", "C"), variant1.activities);
        assertEquals(2, variant1.frequency);
        assertEquals(2.0 / 3.0, variant1.relativeFrequency, 0.001);

        // Second variant (less frequent)
        ProcessVariantAnalyzer.Variant variant2 = result.variants.get(1);
        assertEquals(List.of("A", "D"), variant2.activities);
        assertEquals(1, variant2.frequency);
        assertEquals(1.0 / 3.0, variant2.relativeFrequency, 0.001);
    }

    @Test
    @DisplayName("Test 5: getTopVariants(xes, 2) returns correct ordering")
    void testGetTopVariants() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-004"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="D"/></event>
              </trace>
            </log>
            """;

        List<ProcessVariantAnalyzer.Variant> topVariants = analyzer.getTopVariants(xesXml, 2);

        assertEquals(2, topVariants.size(), "Should return exactly 2 variants");

        // First should be (A, B) with frequency 2
        assertEquals(List.of("A", "B"), topVariants.get(0).activities);
        assertEquals(2, topVariants.get(0).frequency);

        // Second should be (A, C) or (A, D) with frequency 1 each
        // (both have same frequency, so order may vary)
        assertEquals(1, topVariants.get(1).frequency);
    }

    @Test
    @DisplayName("Test 6: getDeviatingCases returns non-conforming cases")
    void testGetDeviatingCases() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="D"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-004"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="E"/></event>
              </trace>
            </log>
            """;

        Set<String> deviatingCases = analyzer.getDeviatingCases(xesXml);

        // (A, B, C) is most frequent with 2 cases
        // (A, D) and (A, E) are deviating with 1 case each
        assertEquals(2, deviatingCases.size(), "Should identify 2 deviating cases");
        assertTrue(deviatingCases.contains("case-003"));
        assertTrue(deviatingCases.contains("case-004"));
        assertFalse(deviatingCases.contains("case-001"));
        assertFalse(deviatingCases.contains("case-002"));
    }

    @Test
    @DisplayName("Test 7: Empty XES log → 0 variants, 0 traces")
    void testEmptyLog() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(0, result.totalTraces);
        assertEquals(0, result.variantCount);
        assertEquals(0, result.variants.size());
        assertEquals(0.0, result.topVariantCoverage);
        assertEquals(0.0, result.top5Coverage);
    }

    @Test
    @DisplayName("Test 8: Null XES log → 0 variants, 0 traces")
    void testNullLog() {
        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(null);

        assertEquals(0, result.totalTraces);
        assertEquals(0, result.variantCount);
        assertEquals(0, result.variants.size());
    }

    @Test
    @DisplayName("Test 9: Single-event traces")
    void testSingleEventTraces() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="B"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.totalTraces);
        assertEquals(2, result.variantCount);

        // (A) should be most frequent with 2 cases
        assertEquals(List.of("A"), result.variants.get(0).activities);
        assertEquals(2, result.variants.get(0).frequency);

        // (B) should be less frequent with 1 case
        assertEquals(List.of("B"), result.variants.get(1).activities);
        assertEquals(1, result.variants.get(1).frequency);
    }

    @Test
    @DisplayName("Test 10: Traces with empty event lists (no activities)")
    void testTracesWithoutEvents() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(2, result.totalTraces);
        assertEquals(1, result.variantCount, "Should have 1 variant (empty sequence)");

        ProcessVariantAnalyzer.Variant variant = result.variants.get(0);
        assertEquals(0, variant.activities.size(), "Variant should have no activities");
        assertEquals(2, variant.frequency);
    }

    @Test
    @DisplayName("Test 11: getTopVariants with n > number of variants")
    void testGetTopVariantsLargeN() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
            </log>
            """;

        List<ProcessVariantAnalyzer.Variant> topVariants = analyzer.getTopVariants(xesXml, 100);

        assertEquals(2, topVariants.size(), "Should return all 2 variants");
    }

    @Test
    @DisplayName("Test 12: getTopVariants with n = 0")
    void testGetTopVariantsZero() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
              </trace>
            </log>
            """;

        List<ProcessVariantAnalyzer.Variant> topVariants = analyzer.getTopVariants(xesXml, 0);

        assertEquals(0, topVariants.size(), "Should return 0 variants");
    }

    @Test
    @DisplayName("Test 13: getDeviatingCases with empty log")
    void testGetDeviatingCasesEmpty() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
            </log>
            """;

        Set<String> deviatingCases = analyzer.getDeviatingCases(xesXml);

        assertEquals(0, deviatingCases.size());
    }

    @Test
    @DisplayName("Test 14: getDeviatingCases when all cases conform")
    void testGetDeviatingCasesAllConform() {
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
            </log>
            """;

        Set<String> deviatingCases = analyzer.getDeviatingCases(xesXml);

        assertEquals(0, deviatingCases.size(), "All cases should conform");
    }

    @Test
    @DisplayName("Test 15: Complex multi-variant scenario with top-5 calculation")
    void testComplexMultiVariantScenario() {
        // Create a scenario with 10 variants to test top-5 coverage calculation
        String xesXml = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-004"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-005"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-006"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="D"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-007"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="E"/></event>
              </trace>
            </log>
            """;

        ProcessVariantAnalyzer.VariantAnalysisResult result = analyzer.analyze(xesXml);

        assertEquals(7, result.totalTraces);
        assertEquals(5, result.variantCount);

        // Top variant (A, B) with 3 cases = 42.9%
        assertEquals(3.0 / 7.0, result.topVariantCoverage, 0.001);

        // Top-5 coverage should be 100% since we only have 5 variants
        assertEquals(1.0, result.top5Coverage, 0.001);

        // Verify sorting by frequency
        assertEquals(3, result.variants.get(0).frequency);
        assertEquals(2, result.variants.get(1).frequency);
        for (int i = 2; i < result.variants.size(); i++) {
            assertEquals(1, result.variants.get(i).frequency);
        }
    }
}
