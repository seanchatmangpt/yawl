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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation suite for TemporalForkBenchmark
 */
public class TemporalForkBenchmarkValidation {

    private TemporalForkBenchmark benchmark;

    @BeforeEach
    void setUp() {
        benchmark = new TemporalForkBenchmark();
    }

    @Test
    @DisplayName("Benchmark should compile and initialize")
    void testBenchmarkInitialization() {
        assertNotNull(benchmark, "Benchmark should not be null");
    }

    @Test
    @DisplayName("XML generation should produce valid XML structure")
    void testXmlGeneration() {
        var taskNames = List.of("ReviewApplication", "ApproveApplication", "RejectApplication");
        String xml = benchmark.buildSyntheticCaseXml(taskNames);

        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("<case"), "XML should contain case element");
        assertTrue(xml.contains("</case>"), "XML should contain closing case element");
        assertTrue(xml.contains("<task>ReviewApplication</task>"),
                   "XML should contain ReviewApplication task");
        assertTrue(xml.contains("<task>ApproveApplication</task>"),
                   "XML should contain ApproveApplication task");
        assertTrue(xml.contains("<task>RejectApplication</task>"),
                   "XML should contain RejectApplication task");
    }

    @Test
    @DisplayName("Task name generation should create correct number of tasks")
    void testTaskNameGeneration() {
        // Test with different counts
        assertEquals(5, benchmark.generateTaskNames(5).size());
        assertEquals(10, benchmark.generateTaskNames(10).size());
        assertEquals(0, benchmark.generateTaskNames(0).size());

        // Test naming pattern
        var tasks = benchmark.generateTaskNames(3);
        assertEquals("Task-1", tasks.get(0));
        assertEquals("Task-2", tasks.get(1));
        assertEquals("Task-3", tasks.get(2));
    }

    @Test
    @DisplayName("Task name generation should handle edge cases")
    void testTaskNameGenerationEdgeCases() {
        // Test with single task
        var singleTask = benchmark.generateTaskNames(1);
        assertEquals(1, singleTask.size());
        assertEquals("Task-1", singleTask.get(0));

        // Test with large number
        var manyTasks = benchmark.generateTaskNames(1000);
        assertEquals(1000, manyTasks.size());
        assertEquals("Task-1", manyTasks.get(0));
        assertEquals("Task-1000", manyTasks.get(999));
    }

    @Test
    @DisplayName("Benchmark methods should have correct annotations")
    void testBenchmarkAnnotations() {
        // This test validates that the benchmark has the required JMH annotations
        // We can't directly check annotations at runtime without reflection,
        // but we can verify the class structure

        assertTrue(TemporalForkBenchmark.class.isAnnotationPresent(
            org.openjdk.jmh.annotations.State.class
        ), "Benchmark should have @State annotation");
    }

    @Test
    @DisplayName("Benchmark should have main method for standalone execution")
    void testMainMethod() {
        try {
            // Test that main method exists and can be called
            TemporalForkBenchmark.class.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            fail("Benchmark should have a main method for standalone execution");
        }
    }

    @Test
    @DisplayName("Benchmark should have correct benchmark methods")
    void testBenchmarkMethodStructure() {
        // Test that all required benchmark methods exist
        try {
            // Check for benchmark methods using reflection
            var methods = TemporalForkBenchmark.class.getDeclaredMethods();

            boolean has10Forks = false;
            boolean has100Forks = false;
            boolean has1000Forks = false;
            boolean hasXmlSerialization = false;
            boolean hasMemoryUsage = false;

            for (var method : methods) {
                if (method.getName().equals("benchmarkForkExecution_10Forks")) {
                    has10Forks = true;
                } else if (method.getName().equals("benchmarkForkExecution_100Forks")) {
                    has100Forks = true;
                } else if (method.getName().equals("benchmarkForkExecution_1000Forks")) {
                    has1000Forks = true;
                } else if (method.getName().equals("benchmarkXmlSerialization")) {
                    hasXmlSerialization = true;
                } else if (method.getName().equals("benchmarkMemoryUsage")) {
                    hasMemoryUsage = true;
                }
            }

            assertTrue(has10Forks, "Should have benchmarkForkExecution_10Forks method");
            assertTrue(has100Forks, "Should have benchmarkForkExecution_100Forks method");
            assertTrue(has1000Forks, "Should have benchmarkForkExecution_1000Forks method");
            assertTrue(hasXmlSerialization, "Should have benchmarkXmlSerialization method");
            assertTrue(hasMemoryUsage, "Should have benchmarkMemoryUsage method");

        } catch (Exception e) {
            fail("Benchmark method validation failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Benchmark validation should catch common issues")
    void testCommonIssueDetection() {
        // Test for null handling
        assertThrows(NullPointerException.class, () -> {
            benchmark.buildSyntheticCaseXml(null);
        }, "Should throw NPE for null task names");
    }

    @Test
    @DisplayName("Benchmark should handle empty task lists")
    void testEmptyTaskListHandling() {
        var emptyXml = benchmark.buildSyntheticCaseXml(List.of());
        assertNotNull(emptyXml, "Should handle empty task list");
        assertTrue(emptyXml.contains("<tasks></tasks>"),
                   "Empty task list should generate empty tasks element");
    }
}