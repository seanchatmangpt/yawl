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

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yawlfoundation.yawl.elements.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ConformanceFormulas.
 * 
 * Tests cover:
 * - Fitness calculation with perfect, partial, and zero conformance
 * - Precision calculation with various model structures
 * - Generalization and simplicity calculations
 * - Edge cases (empty nets, missing activities)
 * - Cross-validation against expected mathematical results
 */
@DisplayName("ConformanceFormulas Tests")
class ConformanceFormulasTest {

    private YNet simpleNet;
    private YNet complexNet;
    private YNet emptyNet;
    private YSpecification specification;

    @BeforeEach
    void setUp() {
        specification = new YSpecification("test-spec");

        // Create a simple linear net: A -> B -> C
        simpleNet = createSimpleLinearNet();

        // Create a complex net with splits and joins
        complexNet = createComplexNet();

        // Create empty net
        emptyNet = new YNet("empty", specification);
    }

    // ========================================================================
    // Fitness Calculation Tests
    // ========================================================================

    @Test
    @DisplayName("Perfect Conformance - fitness should be 1.0")
    void testPerfectConformance() {
        String perfectLog = createPerfectLog("A,B,C");
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, perfectLog);

        assertEquals(1.0, metrics.fitness(), 0.001, "Perfect trace should have fitness 1.0");
        assertTrue(metrics.metrics().get("missing") == 0, "No missing tokens");
        assertTrue(metrics.metrics().get("remaining") == 0, "No remaining tokens");
    }

    @Test
    @DisplayName("Zero Conformance - fitness should be 0.0")
    void testZeroConformance() {
        String zeroLog = createPerfectLog("X,Y,Z"); // Activities not in net
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, zeroLog);

        assertEquals(0.0, metrics.fitness(), 0.001, "Unknown activities should have fitness 0.0");
        assertTrue(metrics.metrics().get("missing") > 0, "All activities should be missing");
    }

    @Test
    @DisplayName("Partial Conformance - fitness should be 0.5")
    void testPartialConformance() {
        String partialLog = createPerfectLog("A,C"); // Missing B
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, partialLog);

        // A is consumed (1/3), C is consumed (1/3), B is missing (1/3)
        // Fitness = 0.5*(2/3) + 0.5*(2/3) = 0.666...
        assertEquals(0.667, metrics.fitness(), 0.001, "Partial trace should have fitness 0.667");
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 0, 1.0",
        "10, 10, 0, 0, 1.0",
        "10, 8, 2, 0, 0.9",
        "10, 5, 5, 0, 0.5",
        "10, 0, 10, 0, 0.0",
        "0, 0, 0, 5, 1.0"
    })
    @DisplayName("TokenReplayResult fitness calculation")
    void testTokenReplayFitness(int produced, int consumed, int missing, int remaining, double expected) {
        ConformanceFormulas.TokenReplayResult result = 
            new ConformanceFormulas.TokenReplayResult(
                produced, consumed, missing, remaining, Set.of());

        assertEquals(expected, result.computeFitness(), 0.001, 
            "Fitness calculation for " + produced + "/" + consumed + "/" + missing + "/" + remaining);
    }

    @Test
    @DisplayName("Empty trace should return fitness 1.0")
    void testEmptyTraceFitness() {
        ConformanceFormulas.TokenReplayResult result = 
            new ConformanceFormulas.TokenReplayResult(0, 0, 0, 0, Set.of());

        assertEquals(1.0, result.computeFitness(), 0.001, "Empty trace should have fitness 1.0");
    }

    // ========================================================================
    // Precision Calculation Tests  
    // ========================================================================

    @Test
    @DisplayName("Perfect precision - no escaped edges")
    void testPerfectPrecision() {
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, createPerfectLog("A,B,C"));

        assertEquals(1.0, metrics.precision(), 0.001, "Simple net should have perfect precision");
    }

    @Test
    @DisplayName("Low precision - many escaped edges")
    void testLowPrecision() {
        // Complex net with many optional branches
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(complexNet, createPerfectLog("A,D"));

        // Should have lower precision due to escaped edges from B and C branches
        assertTrue(metrics.precision() < 1.0, "Complex net with partial log should have lower precision");
    }

    @Test
    @DisplayName("Empty net precision should be 1.0")
    void testEmptyNetPrecision() {
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(emptyNet, "");

        assertEquals(1.0, metrics.precision(), 0.001, "Empty net should have perfect precision");
    }

    // ========================================================================
    // Generalization Calculation Tests
    // ========================================================================

    @Test
    @DisplayName("Balanced net should have high generalization")
    void testBalancedGeneralization() {
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, createPerfectLog("A,B,C"));

        double generalization = ConformanceFormulas.computeGeneralization(simpleNet);
        assertTrue(generalization > 0.5, "Balanced linear net should have good generalization");
    }

    @Test
    @DisplayName("Complex net should have lower generalization")
    void testComplexNetGeneralization() {
        double simpleGeneralization = ConformanceFormulas.computeGeneralization(simpleNet);
        double complexGeneralization = ConformanceFormulas.computeGeneralization(complexNet);

        assertTrue(complexGeneralization < simpleGeneralization, 
            "Complex net should have lower generalization than simple net");
    }

    @Test
    @DisplayName("Empty net generalization should be 1.0")
    void testEmptyNetGeneralization() {
        double generalization = ConformanceFormulas.computeGeneralization(emptyNet);
        assertEquals(1.0, generalization, 0.001, "Empty net should be maximally general");
    }

    // ========================================================================
    // Simplicity Calculation Tests
    // ========================================================================

    @Test
    @DisplayName("Simple net should have high simplicity")
    void testSimpleNetSimplicity() {
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, createPerfectLog("A,B,C"));

        double simplicity = ConformanceFormulas.computeSimplicity(simpleNet);
        assertTrue(simplicity > 0.8, "Simple linear net should have high simplicity");
    }

    @Test
    @DisplayName("Complex net should have lower simplicity")
    void testComplexNetSimplicity() {
        double simpleSimplicity = ConformanceFormulas.computeSimplicity(simpleNet);
        double complexSimplicity = ConformanceFormulas.computeSimplicity(complexNet);

        assertTrue(complexSimplicity < simpleSimplicity, 
            "Complex net should have lower simplicity than simple net");
    }

    @Test
    @DisplayName("Empty net simplicity should be 1.0")
    void testEmptyNetSimplicity() {
        double simplicity = ConformanceFormulas.computeSimplicity(emptyNet);
        assertEquals(1.0, simplicity, 0.001, "Empty net should be maximally simple");
    }

    // ========================================================================
    // Edge Case Tests
    // ========================================================================

    @Test
    @DisplayName("Null net should throw IllegalArgumentException")
    void testNullNetThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            ConformanceFormulas.computeConformance(null, createPerfectLog("A,B,C"));
        }, "Null net should throw IllegalArgumentException");
    }

    @Test
    @DisplayName("Empty log should handle gracefully")
    void testEmptyLog() {
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, "");

        assertEquals(1.0, metrics.fitness(), 0.001, "Empty log should have perfect fitness");
        assertEquals(1.0, metrics.precision(), 0.001, "Empty log should have perfect precision");
    }

    @Test
    @DisplayName("Overall score calculation")
    void testOverallScore() {
        String perfectLog = createPerfectLog("A,B,C");
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, perfectLog);

        double overall = metrics.overallScore();
        assertEquals(1.0, overall, 0.001, "Perfect metrics should have overall score 1.0");
    }

    @Test
    @DisplayName("Summary formatting")
    void testSummaryFormatting() {
        String perfectLog = createPerfectLog("A,B,C");
        ConformanceFormulas.ConformanceMetrics metrics = 
            ConformanceFormulas.computeConformance(simpleNet, perfectLog);

        String summary = metrics.summary();
        assertTrue(summary.contains("Fitness: 1.000"), "Summary should contain fitness");
        assertTrue(summary.contains("Precision: 1.000"), "Summary should contain precision");
        assertTrue(summary.contains("Generalization:"), "Summary should contain generalization");
        assertTrue(summary.contains("Simplicity:"), "Summary should contain simplicity");
    }

    // ========================================================================
    // Cross-Validation Tests
    // ========================================================================

    @Test
    @DisplayName("Cross-validate fitness with individual method")
    void testFitnessCrossValidation() {
        String log = createPerfectLog("A,B,C");
        
        double fitnessViaFull = ConformanceFormulas.computeConformance(simpleNet, log).fitness();
        double fitnessViaMethod = ConformanceFormulas.computeFitness(simpleNet, log);

        assertEquals(fitnessViaFull, fitnessViaMethod, 0.001, 
            "Fitness should be same whether computed via full metrics or individual method");
    }

    @Test
    @DisplayName("Cross-validate precision with individual method")
    void testPrecisionCrossValidation() {
        String log = createPerfectLog("A,B,C");
        
        double precisionViaFull = ConformanceFormulas.computeConformance(simpleNet, log).precision();
        double precisionViaMethod = ConformanceFormulas.computePrecision(simpleNet, log);

        assertEquals(precisionViaFull, precisionViaMethod, 0.001, 
            "Precision should be same whether computed via full metrics or individual method");
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Creates a simple linear YAWL net: A -> B -> C
     */
    private YNet createSimpleLinearNet() {
        YNet net = new YNet("linear-net", specification);

        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YAtomicTask taskA = new YAtomicTask("taskA", YTask._XOR, YTask._XOR, net);
        taskA.setName("A");
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._XOR, YTask._XOR, net);
        taskB.setName("B");
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._XOR, YTask._XOR, net);
        taskC.setName("C");

        YCondition c1 = new YCondition("c1", "c1", net);
        YCondition c2 = new YCondition("c2", "c2", net);
        YCondition c3 = new YCondition("c3", "c3", net);

        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);

        new YFlow(input, taskA);
        new YFlow(taskA, c1);
        new YFlow(c1, taskB);
        new YFlow(taskB, c2);
        new YFlow(c2, taskC);
        new YFlow(taskC, c3);
        new YFlow(c3, output);

        return net;
    }

    /**
     * Creates a complex YAWL net with splits and joins
     */
    private YNet createComplexNet() {
        YNet net = new YNet("complex-net", specification);

        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YAtomicTask taskA = new YAtomicTask("taskA", YTask._XOR, YTask._XOR, net);
        taskA.setName("A");
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._XOR, YTask._XOR, net);
        taskB.setName("B");
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._XOR, YTask._XOR, net);
        taskC.setName("C");
        YAtomicTask taskD = new YAtomicTask("taskD", YTask._AND, YTask._XOR, net);
        taskD.setName("D");

        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);

        // A splits to B and C (XOR), B and C join to D (AND)
        new YFlow(input, taskA);
        new YFlow(taskA, taskB);
        new YFlow(taskA, taskC);
        new YFlow(taskB, taskD);
        new YFlow(taskC, taskD);
        new YFlow(taskD, output);

        return net;
    }

    /**
     * Creates XES log XML with specified activities
     */
    private String createPerfectLog(String activities) {
        String[] activityArray = activities.split(",");
        StringBuilder logXml = new StringBuilder();
        logXml.append("<log version=\"1.0\" xmlns=\"http://www.xes.org/\">\n");
        logXml.append("  <trace>\n");
        logXml.append("    <string key=\"concept:name\" value=\"case-001\"/>\n");
        
        for (String activity : activityArray) {
            logXml.append("    <event>\n");
            logXml.append("      <string key=\"concept:name\" value=\"").append(activity).append("\"/>\n");
            logXml.append("    </event>\n");
        }
        
        logXml.append("  </trace>\n");
        logXml.append("</log>");
        
        return logXml.toString();
    }
}
