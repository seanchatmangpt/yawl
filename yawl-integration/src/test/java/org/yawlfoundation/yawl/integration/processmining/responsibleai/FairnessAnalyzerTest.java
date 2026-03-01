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
 */

package org.yawlfoundation.yawl.integration.processmining.responsibleai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for fairness analysis in process mining.
 *
 * <p>Chicago TDD: Tests use real data, not mocks.</p>
 */
public class FairnessAnalyzerTest {

    /**
     * Test fair scenario: equal decision rates across groups.
     *
     * Scenario:
     * - Group A: 10 cases, 8 approved (80%)
     * - Group B: 10 cases, 8 approved (80%)
     * - Disparate impact = 80/80 = 1.0 ≥ 0.8 → FAIR
     */
    @Test
    public void testFairDecisionRates() {
        List<Map<String, String>> caseAttrs = new ArrayList<>();
        List<Map<String, String>> decisions = new ArrayList<>();

        // Group A: 80% approval rate
        for (int i = 0; i < 10; i++) {
            caseAttrs.add(Map.of("department", "A"));
            decisions.add(Map.of("decision", i < 8 ? "approved" : "rejected"));
        }

        // Group B: 80% approval rate
        for (int i = 0; i < 10; i++) {
            caseAttrs.add(Map.of("department", "B"));
            decisions.add(Map.of("decision", i < 8 ? "approved" : "rejected"));
        }

        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            caseAttrs, decisions, "department", "approved"
        );

        assertTrue(report.isFair(), "Equal rates should be fair");
        assertEquals(1.0, report.disparateImpact(), 0.01, "Disparate impact should be 1.0");
        assertEquals(1.0, report.demographicParity(), 0.01, "Demographic parity should be 1.0");
        assertEquals(0, report.violations().size(), "Should have no violations");
    }

    /**
     * Test biased scenario: one group has much lower approval rate.
     *
     * Scenario:
     * - Group A: 20 cases, 18 approved (90%)
     * - Group B: 20 cases, 9 approved (45%)
     * - Disparate impact = min(90/45, 45/90) = min(2.0, 0.5) = 0.5 < 0.8 → UNFAIR
     */
    @Test
    public void testBiasedDecisionRates() {
        List<Map<String, String>> caseAttrs = new ArrayList<>();
        List<Map<String, String>> decisions = new ArrayList<>();

        // Group A: 90% approval
        for (int i = 0; i < 20; i++) {
            caseAttrs.add(Map.of("resource", "A"));
            decisions.add(Map.of("decision", i < 18 ? "approved" : "rejected"));
        }

        // Group B: 45% approval
        for (int i = 0; i < 20; i++) {
            caseAttrs.add(Map.of("resource", "B"));
            decisions.add(Map.of("decision", i < 9 ? "approved" : "rejected"));
        }

        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            caseAttrs, decisions, "resource", "approved"
        );

        assertFalse(report.isFair(), "Biased rates should be unfair");
        assertTrue(report.disparateImpact() < 0.8, "Disparate impact should be < 0.8");
        assertFalse(report.violations().isEmpty(), "Should have fairness violations");
        assertTrue(
            report.violations().get(0).contains("Disparate impact violation"),
            "Violation should mention disparate impact"
        );
    }

    /**
     * Test borderline case: exactly at four-fifths rule threshold.
     *
     * Scenario:
     * - Group A: 80% approval (0.80)
     * - Group B: 100% approval (1.00)
     * - Disparate impact = min(0.80/1.00, 1.00/0.80) = min(0.80, 1.25) = 0.80 = threshold → FAIR
     */
    @Test
    public void testBorderlineAtThreshold() {
        List<Map<String, String>> caseAttrs = new ArrayList<>();
        List<Map<String, String>> decisions = new ArrayList<>();

        // Group A: 80% (4 out of 5)
        caseAttrs.add(Map.of("dept", "A"));
        decisions.add(Map.of("decision", "approved"));
        caseAttrs.add(Map.of("dept", "A"));
        decisions.add(Map.of("decision", "approved"));
        caseAttrs.add(Map.of("dept", "A"));
        decisions.add(Map.of("decision", "approved"));
        caseAttrs.add(Map.of("dept", "A"));
        decisions.add(Map.of("decision", "approved"));
        caseAttrs.add(Map.of("dept", "A"));
        decisions.add(Map.of("decision", "rejected"));

        // Group B: 100% (5 out of 5)
        for (int i = 0; i < 5; i++) {
            caseAttrs.add(Map.of("dept", "B"));
            decisions.add(Map.of("decision", "approved"));
        }

        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            caseAttrs, decisions, "dept", "approved"
        );

        assertTrue(report.isFair(), "Exactly at threshold (0.80) should be fair");
        assertEquals(0.8, report.disparateImpact(), 0.01);
    }

    /**
     * Test decision rate calculation per group.
     */
    @Test
    public void testDecisionRateCalculation() {
        List<Map<String, String>> caseAttrs = new ArrayList<>();
        List<Map<String, String>> decisions = new ArrayList<>();

        // Group X: 3 approved, 1 rejected = 75%
        for (int i = 0; i < 4; i++) {
            caseAttrs.add(Map.of("team", "X"));
            decisions.add(Map.of("decision", i < 3 ? "approved" : "rejected"));
        }

        // Group Y: 1 approved, 1 rejected = 50%
        caseAttrs.add(Map.of("team", "Y"));
        decisions.add(Map.of("decision", "approved"));
        caseAttrs.add(Map.of("team", "Y"));
        decisions.add(Map.of("decision", "rejected"));

        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            caseAttrs, decisions, "team", "approved"
        );

        assertEquals(0.75, report.decisionRateByGroup().get("X"), 0.01);
        assertEquals(0.50, report.decisionRateByGroup().get("Y"), 0.01);
    }

    /**
     * Test empty input handling.
     */
    @Test
    public void testEmptyInput() {
        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            new ArrayList<>(), new ArrayList<>(), "attr", "positive"
        );

        assertTrue(report.isFair(), "Empty input should be considered fair");
        assertEquals(1.0, report.disparateImpact(), 0.01);
    }

    /**
     * Test demographic parity calculation.
     *
     * Scenario:
     * - Perfect parity: all groups have 100% approval → parity = 1.0
     * - No parity: one group 100%, another 0% → parity = 0.0
     */
    @Test
    public void testDemographicParity() {
        // Test 1: Perfect parity
        List<Map<String, String>> perfectParity = new ArrayList<>();
        List<Map<String, String>> perfectDecisions = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            perfectParity.add(Map.of("group", "A"));
            perfectDecisions.add(Map.of("d", "yes"));
            perfectParity.add(Map.of("group", "B"));
            perfectDecisions.add(Map.of("d", "yes"));
        }

        FairnessAnalyzer.FairnessReport perfect = FairnessAnalyzer.analyze(
            perfectParity, perfectDecisions, "group", "yes"
        );
        assertEquals(1.0, perfect.demographicParity(), 0.01);

        // Test 2: No parity
        List<Map<String, String>> noParity = new ArrayList<>();
        List<Map<String, String>> noParityDecisions = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            noParity.add(Map.of("group", "A"));
            noParityDecisions.add(Map.of("d", "yes"));
        }
        for (int i = 0; i < 5; i++) {
            noParity.add(Map.of("group", "B"));
            noParityDecisions.add(Map.of("d", "no"));
        }

        FairnessAnalyzer.FairnessReport noPar = FairnessAnalyzer.analyze(
            noParity, noParityDecisions, "group", "yes"
        );
        assertEquals(0.0, noPar.demographicParity(), 0.01);
    }

    /**
     * Test NullPointerException on null input.
     */
    @Test
    public void testNullInputValidation() {
        List<Map<String, String>> empty = new ArrayList<>();

        assertThrows(NullPointerException.class, () ->
            FairnessAnalyzer.analyze(null, empty, "attr", "positive")
        );
        assertThrows(NullPointerException.class, () ->
            FairnessAnalyzer.analyze(empty, null, "attr", "positive")
        );
    }

    /**
     * Test IllegalArgumentException on size mismatch.
     */
    @Test
    public void testSizeMismatchValidation() {
        List<Map<String, String>> attrs = List.of(Map.of("x", "1"));
        List<Map<String, String>> decisions = new ArrayList<>();

        assertThrows(IllegalArgumentException.class, () ->
            FairnessAnalyzer.analyze(attrs, decisions, "x", "y")
        );
    }
}
