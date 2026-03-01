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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Suite for TestDataGenerator Mixed Workload Extension
 *
 * <p>Validates that newRealisticMixedWorkload() generates valid YAWL
 * specifications with realistic task time distributions suitable for
 * 1M case stress testing.</p>
 */
@DisplayName("TestDataGenerator Mixed Workload Test Suite")
class TestDataGeneratorTest {

    private TestDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TestDataGenerator();
    }

    @Test
    @DisplayName("newRealisticMixedWorkload returns immutable map")
    void testNewRealisticMixedWorkloadReturnsUnmodifiableMap() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        assertNotNull(workload, "Should return non-null map");
        assertFalse(workload.isEmpty(), "Should contain specifications");

        // Verify map is unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> workload.put("test", "value"),
                "Map should be unmodifiable");
    }

    @Test
    @DisplayName("newRealisticMixedWorkload contains sequential specifications")
    void testContainsSequentialSpecifications() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        assertTrue(workload.containsKey("sequential_primary"),
                "Should contain sequential_primary");
        assertTrue(workload.containsKey("sequential_2task"),
                "Should contain sequential_2task");
        assertTrue(workload.containsKey("sequential_4task"),
                "Should contain sequential_4task");

        String spec2 = workload.get("sequential_2task");
        String spec4 = workload.get("sequential_4task");

        assertNotNull(spec2, "sequential_2task should not be null");
        assertNotNull(spec4, "sequential_4task should not be null");
        assertFalse(spec2.isBlank(), "sequential_2task should have content");
        assertFalse(spec4.isBlank(), "sequential_4task should have content");
    }

    @Test
    @DisplayName("newRealisticMixedWorkload contains parallel specifications")
    void testContainsParallelSpecifications() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        assertTrue(workload.containsKey("parallel_andsplit"),
                "Should contain parallel_andsplit");

        String parallelSpec = workload.get("parallel_andsplit");
        assertNotNull(parallelSpec, "parallel_andsplit should not be null");
        assertFalse(parallelSpec.isBlank(), "parallel_andsplit should have content");
    }

    @Test
    @DisplayName("newRealisticMixedWorkload contains loop specifications")
    void testContainsLoopSpecifications() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        assertTrue(workload.containsKey("loop_sequential"),
                "Should contain loop_sequential");

        String loopSpec = workload.get("loop_sequential");
        assertNotNull(loopSpec, "loop_sequential should not be null");
        assertFalse(loopSpec.isBlank(), "loop_sequential should have content");
    }

    @Test
    @DisplayName("newRealisticMixedWorkload contains complex specifications")
    void testContainsComplexSpecifications() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        assertTrue(workload.containsKey("complex_multichoice"),
                "Should contain complex_multichoice");
        assertTrue(workload.containsKey("complex_exclusive"),
                "Should contain complex_exclusive");

        String multiChoice = workload.get("complex_multichoice");
        String exclusive = workload.get("complex_exclusive");

        assertNotNull(multiChoice, "complex_multichoice should not be null");
        assertNotNull(exclusive, "complex_exclusive should not be null");
        assertFalse(multiChoice.isBlank(), "complex_multichoice should have content");
        assertFalse(exclusive.isBlank(), "complex_exclusive should have content");
    }

    @Test
    @DisplayName("All specifications are valid XML")
    void testAllSpecificationsAreValidXml() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        for (Map.Entry<String, String> entry : workload.entrySet()) {
            String specName = entry.getKey();
            String specXml = entry.getValue();

            assertTrue(specXml.startsWith("<?xml"),
                    specName + " should start with XML declaration");
            assertTrue(specXml.contains("<specificationSet"),
                    specName + " should contain <specificationSet>");
            assertTrue(specXml.endsWith("</specificationSet>\n"),
                    specName + " should end with </specificationSet>");
        }
    }

    @Test
    @DisplayName("All specifications contain YAWL schema reference")
    void testAllSpecificationsHaveYawlSchema() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        for (Map.Entry<String, String> entry : workload.entrySet()) {
            String specName = entry.getKey();
            String specXml = entry.getValue();

            assertTrue(specXml.contains("http://www.yawlfoundation.org/yawlschema"),
                    specName + " should reference YAWL schema");
        }
    }

    @Test
    @DisplayName("All specifications contain process control elements")
    void testAllSpecificationsHaveProcessControlElements() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        for (Map.Entry<String, String> entry : workload.entrySet()) {
            String specName = entry.getKey();
            String specXml = entry.getValue();

            assertTrue(specXml.contains("<decomposition"),
                    specName + " should contain decomposition");
            assertTrue(specXml.contains("<processControlElements>"),
                    specName + " should contain processControlElements");
        }
    }

    @Test
    @DisplayName("Specifications are consistent across calls")
    void testSpecificationsAreConsistent() {
        Map<String, String> workload1 = generator.newRealisticMixedWorkload(1000, 150);
        Map<String, String> workload2 = generator.newRealisticMixedWorkload(1000, 150);

        // All specs from BenchmarkSpecFactory are constants, so should be identical
        for (String key : workload1.keySet()) {
            assertEquals(workload1.get(key), workload2.get(key),
                    key + " specification should be consistent across calls");
        }
    }

    @Test
    @DisplayName("newRealisticMixedWorkload accepts valid parameters")
    void testNewRealisticMixedWorkloadAcceptsValidParameters() {
        // Should accept various case counts and task rates
        assertDoesNotThrow(() -> generator.newRealisticMixedWorkload(100, 100),
                "Should accept small case count");
        assertDoesNotThrow(() -> generator.newRealisticMixedWorkload(1_000_000, 500),
                "Should accept 1M case count");
        assertDoesNotThrow(() -> generator.newRealisticMixedWorkload(0, 150),
                "Should accept zero case count");
        assertDoesNotThrow(() -> generator.newRealisticMixedWorkload(1000, 10),
                "Should accept small task rate");
    }

    @Test
    @DisplayName("Sequential workflow specifications are distinct")
    void testSequentialWorkflowsAreDistinct() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        String spec2 = workload.get("sequential_2task");
        String spec4 = workload.get("sequential_4task");

        assertNotEquals(spec2, spec4,
                "2-task and 4-task sequential workflows should be different");

        // 4-task should contain more task definitions
        int spec2TaskCount = spec2.split("<task").length;
        int spec4TaskCount = spec4.split("<task").length;

        assertTrue(spec4TaskCount > spec2TaskCount,
                "4-task spec should have more task elements than 2-task spec");
    }

    @Test
    @DisplayName("Workload contains realistic mix of patterns")
    void testWorkloadContainsRealisticPatternMix() {
        Map<String, String> workload = generator.newRealisticMixedWorkload(1000, 150);

        // Expected distribution:
        // 40% Sequential (3 specs: primary, 2task, 4task)
        // 30% Parallel (1 spec)
        // 20% Loop (1 spec)
        // 10% Complex (2 specs)

        assertEquals(3, workload.values().stream()
                        .filter(s -> s.contains("Task 1") && s.contains("Task 2"))
                        .count(),
                "Should have sequential workflow specs");

        assertTrue(workload.containsKey("parallel_andsplit"),
                "Should have parallel workflow spec");

        assertEquals(2, workload.values().stream()
                        .filter(s -> s.contains("Choice") || s.contains("choice"))
                        .count(),
                "Should have complex workflow specs");
    }

    @Test
    @DisplayName("generateWorkflowSpecifications still works (backward compatibility)")
    void testGenerateWorkflowSpecificationsBackwardCompatibility() {
        Map<String, String> specs = generator.generateWorkflowSpecifications();

        assertNotNull(specs, "Should return non-null map");
        assertTrue(specs.containsKey("sequential"), "Should contain sequential");
        assertTrue(specs.containsKey("parallel"), "Should contain parallel");
        assertTrue(specs.containsKey("multiChoice"), "Should contain multiChoice");
        assertTrue(specs.containsKey("exclusiveChoice"), "Should contain exclusiveChoice");
        assertTrue(specs.containsKey("sequential4"), "Should contain sequential4");
    }

    @Test
    @DisplayName("generateWorkItems still works (backward compatibility)")
    void testGenerateWorkItemsBackwardCompatibility() {
        var workItems = generator.generateWorkItems(10);

        assertNotNull(workItems, "Should return non-null list");
        assertEquals(10, workItems.size(), "Should generate requested count");

        for (var item : workItems) {
            assertTrue(item.containsKey("id"), "Work item should have id");
            assertTrue(item.containsKey("caseId"), "Work item should have caseId");
            assertTrue(item.containsKey("taskId"), "Work item should have taskId");
        }
    }

    @Test
    @DisplayName("generateCaseData still works (backward compatibility)")
    void testGenerateCaseDataBackwardCompatibility() {
        var caseData = generator.generateCaseData(5);

        assertNotNull(caseData, "Should return non-null list");
        assertEquals(5, caseData.size(), "Should generate requested count");

        for (var caseItem : caseData) {
            assertTrue(caseItem.containsKey("caseId"), "Case should have caseId");
            assertTrue(caseItem.containsKey("status"), "Case should have status");
        }
    }
}
