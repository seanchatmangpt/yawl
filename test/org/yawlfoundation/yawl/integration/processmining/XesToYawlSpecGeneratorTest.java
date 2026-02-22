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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link XesToYawlSpecGenerator}.
 *
 * Tests real behavior of the XES → YAWL specification generation pipeline.
 * No mocks — uses real XES XML and verifies real YAWL XML output structure.
 */
class XesToYawlSpecGeneratorTest {

    private XesToYawlSpecGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new XesToYawlSpecGenerator(1);
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void constructor_rejectsZeroMinFrequency() {
        assertThrows(IllegalArgumentException.class,
            () -> new XesToYawlSpecGenerator(0));
    }

    @Test
    void constructor_rejectsNegativeMinFrequency() {
        assertThrows(IllegalArgumentException.class,
            () -> new XesToYawlSpecGenerator(-5));
    }

    @Test
    void constructor_acceptsMinFrequencyOfOne() {
        XesToYawlSpecGenerator gen = new XesToYawlSpecGenerator(1);
        assertEquals(1, gen.getMinFrequency());
    }

    @Test
    void constructor_storesMinFrequency() {
        XesToYawlSpecGenerator gen = new XesToYawlSpecGenerator(3);
        assertEquals(3, gen.getMinFrequency());
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Test
    void generate_rejectsNullXes() {
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate(null, "MyProcess"));
    }

    @Test
    void generate_rejectsEmptyXes() {
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate("", "MyProcess"));
    }

    @Test
    void generate_rejectsBlankXes() {
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate("   ", "MyProcess"));
    }

    @Test
    void generate_rejectsNullProcessName() {
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate(singleTraceXes("A", "B"), null));
    }

    @Test
    void generate_rejectsBlankProcessName() {
        assertThrows(IllegalArgumentException.class,
            () -> generator.generate(singleTraceXes("A", "B"), "   "));
    }

    @Test
    void generate_rejectsXesWithNoTraces() {
        String emptyLog = "<log xes.version=\"1.0\"></log>";
        assertThrows(IllegalStateException.class,
            () -> generator.generate(emptyLog, "EmptyProcess"));
    }

    // -------------------------------------------------------------------------
    // YAWL XML structure
    // -------------------------------------------------------------------------

    @Test
    void generate_producesXmlDeclaration() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "P1");
        assertTrue(result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
            "Output should start with XML declaration");
    }

    @Test
    void generate_containsSpecificationSetElement() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "LoanProcess");
        assertTrue(result.contains("<specificationSet"), "Must contain specificationSet root");
    }

    @Test
    void generate_containsYawlNamespace() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "LoanProcess");
        assertTrue(result.contains("http://www.yawlfoundation.org/yawlschema"),
            "Must reference YAWL schema namespace");
    }

    @Test
    void generate_containsSpecificationWithProcessNameUri() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "LoanProcess");
        assertTrue(result.contains("uri=\"LoanProcess\""),
            "specification element must have uri matching processName");
    }

    @Test
    void generate_containsInputCondition() {
        String result = generator.generate(singleTraceXes("Apply", "Review", "Approve"), "Loan");
        assertTrue(result.contains("id=\"InputCondition\""),
            "Must contain InputCondition");
    }

    @Test
    void generate_containsOutputCondition() {
        String result = generator.generate(singleTraceXes("Apply", "Review", "Approve"), "Loan");
        assertTrue(result.contains("id=\"OutputCondition\""),
            "Must contain OutputCondition");
    }

    @Test
    void generate_containsAtomicTaskForEachActivity() {
        String result = generator.generate(singleTraceXes("Submit", "Review", "Approve"), "HR");
        assertTrue(result.contains("id=\"Submit\""), "Must have task for Submit");
        assertTrue(result.contains("id=\"Review\""), "Must have task for Review");
        assertTrue(result.contains("id=\"Approve\""), "Must have task for Approve");
    }

    @Test
    void generate_containsDecompositionWithRootNet() {
        String result = generator.generate(singleTraceXes("Step1", "Step2"), "Workflow");
        assertTrue(result.contains("isRootNet=\"true\""),
            "Decomposition must be marked as root net");
    }

    @Test
    void generate_containsVersion4Schema() {
        String result = generator.generate(singleTraceXes("A", "B"), "P");
        assertTrue(result.contains("version=\"4.0\""),
            "Must reference YAWL schema version 4.0");
    }

    @Test
    void generate_tasksHaveJoinAndSplitCodes() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "Proc");
        assertTrue(result.contains("<join"), "Must contain join element");
        assertTrue(result.contains("<split"), "Must contain split element");
    }

    @Test
    void generate_tasksHaveResourcingBlock() {
        String result = generator.generate(singleTraceXes("TaskA", "TaskB"), "Proc");
        assertTrue(result.contains("<resourcing>"), "Must contain resourcing element");
    }

    @Test
    void generate_taskNameMatchesActivityLabel() {
        String result = generator.generate(singleTraceXes("Approve_Payment"), "Finance");
        assertTrue(result.contains("<name>Approve_Payment</name>"),
            "Task name must match original activity label");
    }

    // -------------------------------------------------------------------------
    // DFG building and model construction
    // -------------------------------------------------------------------------

    @Test
    void buildModel_singleTrace_identifiesStartAndEnd() {
        List<List<String>> traces = List.of(List.of("A", "B", "C"));
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);
        assertTrue(model.startActivities().contains("A"), "A must be start");
        assertTrue(model.endActivities().contains("C"), "C must be end");
        assertFalse(model.startActivities().contains("C"), "C must not be start");
        assertFalse(model.endActivities().contains("A"), "A must not be end");
    }

    @Test
    void buildModel_singleTrace_buildsDfgEdges() {
        List<List<String>> traces = List.of(List.of("Submit", "Review", "Approve"));
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);
        assertTrue(model.dfg().get("Submit").contains("Review"),
            "DFG must have Submit→Review edge");
        assertTrue(model.dfg().get("Review").contains("Approve"),
            "DFG must have Review→Approve edge");
    }

    @Test
    void buildModel_multipleTraces_aggregatesStartActivities() {
        List<List<String>> traces = List.of(
            List.of("A", "B"),
            List.of("C", "B")
        );
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);
        assertTrue(model.startActivities().contains("A"), "A is start in trace 1");
        assertTrue(model.startActivities().contains("C"), "C is start in trace 2");
    }

    @Test
    void buildModel_frequencyFilter_prunesRareEdges() {
        XesToYawlSpecGenerator gen = new XesToYawlSpecGenerator(2);
        // A→B appears 2 times, A→C appears 1 time
        List<List<String>> traces = List.of(
            List.of("A", "B"),
            List.of("A", "B"),
            List.of("A", "C")
        );
        XesToYawlSpecGenerator.DiscoveredModel model = gen.buildModel(traces);
        assertTrue(model.dfg().get("A").contains("B"),
            "A→B (freq=2) must survive filter");
        assertFalse(model.dfg().get("A").contains("C"),
            "A→C (freq=1) must be pruned by minFrequency=2");
    }

    @Test
    void buildModel_allActivitiesPresent() {
        List<List<String>> traces = List.of(List.of("X", "Y", "Z"));
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);
        assertTrue(model.activities().containsAll(Set.of("X", "Y", "Z")),
            "All activities must appear in model");
    }

    // -------------------------------------------------------------------------
    // sanitizeId
    // -------------------------------------------------------------------------

    @Test
    void sanitizeId_replacesSpacesWithUnderscore() {
        assertEquals("Approve_Payment", XesToYawlSpecGenerator.sanitizeId("Approve Payment"));
    }

    @Test
    void sanitizeId_preservesAlphanumericAndUnderscore() {
        assertEquals("Task_1", XesToYawlSpecGenerator.sanitizeId("Task_1"));
    }

    @Test
    void sanitizeId_nullReturnsDefaultId() {
        assertEquals("_task", XesToYawlSpecGenerator.sanitizeId(null));
    }

    @Test
    void sanitizeId_blankReturnsDefaultId() {
        assertEquals("_task", XesToYawlSpecGenerator.sanitizeId("   "));
    }

    @Test
    void sanitizeId_specialCharactersReplaced() {
        String result = XesToYawlSpecGenerator.sanitizeId("Review & Approve (Loan)");
        assertFalse(result.contains(" "), "No spaces in sanitized ID");
        assertFalse(result.contains("&"), "No & in sanitized ID");
        assertFalse(result.contains("("), "No ( in sanitized ID");
    }

    // -------------------------------------------------------------------------
    // Integration: full XES → YAWL pipeline
    // -------------------------------------------------------------------------

    @Test
    void generate_loanProcessPipeline_producesValidStructure() {
        String xes = multiTraceXes(
            List.of("Submit_Application", "Credit_Check", "Approve_Loan", "Disburse_Funds"),
            List.of("Submit_Application", "Credit_Check", "Reject_Loan")
        );
        String spec = generator.generate(xes, "LoanApproval");

        assertTrue(spec.contains("uri=\"LoanApproval\""));
        assertTrue(spec.contains("id=\"Submit_Application\""));
        assertTrue(spec.contains("id=\"Credit_Check\""));
        assertTrue(spec.contains("id=\"InputCondition\""));
        assertTrue(spec.contains("id=\"OutputCondition\""));
    }

    // -------------------------------------------------------------------------
    // XES helpers
    // -------------------------------------------------------------------------

    /** Creates a minimal XES log with a single trace containing the given activities. */
    private String singleTraceXes(String... activities) {
        return multiTraceXes(List.of(activities));
    }

    @SafeVarargs
    private String multiTraceXes(List<String>... traceLists) {
        StringBuilder xes = new StringBuilder();
        xes.append("<log xes.version=\"1.0\" xes.features=\"nested-attributes\" ")
           .append("xmlns:xes=\"http://www.xes-standard.org/\" ")
           .append("xmlns:concept=\"http://www.xes-standard.org/concept.xesext\">\n");
        for (int i = 0; i < traceLists.length; i++) {
            xes.append("  <trace>\n");
            xes.append("    <string key=\"concept:name\" value=\"Case_").append(i + 1).append("\"/>\n");
            for (String activity : traceLists[i]) {
                xes.append("    <event>\n");
                xes.append("      <string key=\"concept:name\" value=\"")
                   .append(activity).append("\"/>\n");
                xes.append("    </event>\n");
            }
            xes.append("  </trace>\n");
        }
        xes.append("</log>");
        return xes.toString();
    }
}
