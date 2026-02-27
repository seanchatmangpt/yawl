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

import java.lang.reflect.Method;
import java.util.List;

/**
 * Simple validation suite for TemporalForkBenchmark without external dependencies
 */
public class TemporalForkBenchmarkValidationSimple {

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("TemporalForkBenchmark Validation");
        System.out.println("==========================================");

        try {
            validateBenchmark();
            System.out.println("\n✓ All validations passed!");
        } catch (Exception e) {
            System.err.println("\n✗ Validation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("==========================================");
    }

    private static void validateBenchmark() throws Exception {
        TemporalForkBenchmark benchmark = new TemporalForkBenchmark();

        System.out.println("1. Testing benchmark initialization...");
        assertNotNull(benchmark, "Benchmark should not be null");
        System.out.println("   ✓ Benchmark initialized successfully");

        System.out.println("\n2. Testing XML generation...");
        testXmlGeneration(benchmark);
        System.out.println("   ✓ XML generation works correctly");

        System.out.println("\n3. Testing task name generation...");
        testTaskNameGeneration(benchmark);
        System.out.println("   ✓ Task name generation works correctly");

        System.out.println("\n4. Testing benchmark methods...");
        testBenchmarkMethodsExist(benchmark);
        System.out.println("   ✓ All required methods exist");

        System.out.println("\n5. Testing main method...");
        testMainMethodExists();
        System.out.println("   ✓ Main method exists for standalone execution");

        System.out.println("\n6. Testing edge cases...");
        testEdgeCases(benchmark);
        System.out.println("   ✓ Edge cases handled correctly");
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void testXmlGeneration(TemporalForkBenchmark benchmark) {
        List<String> taskNames = List.of("ReviewApplication", "ApproveApplication", "RejectApplication");
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

    private static void testTaskNameGeneration(TemporalForkBenchmark benchmark) {
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

    private static void testBenchmarkMethodsExist(TemporalForkBenchmark benchmark) {
        Method[] methods = TemporalForkBenchmark.class.getDeclaredMethods();

        boolean has10Forks = false;
        boolean has100Forks = false;
        boolean has1000Forks = false;
        boolean hasXmlSerialization = false;
        boolean hasMemoryUsage = false;

        for (Method method : methods) {
            switch (method.getName()) {
                case "benchmarkForkExecution_10Forks":
                    has10Forks = true;
                    break;
                case "benchmarkForkExecution_100Forks":
                    has100Forks = true;
                    break;
                case "benchmarkForkExecution_1000Forks":
                    has1000Forks = true;
                    break;
                case "benchmarkXmlSerialization":
                    hasXmlSerialization = true;
                    break;
                case "benchmarkMemoryUsage":
                    hasMemoryUsage = true;
                    break;
            }
        }

        assertTrue(has10Forks, "Should have benchmarkForkExecution_10Forks method");
        assertTrue(has100Forks, "Should have benchmarkForkExecution_100Forks method");
        assertTrue(has1000Forks, "Should have benchmarkForkExecution_1000Forks method");
        assertTrue(hasXmlSerialization, "Should have benchmarkXmlSerialization method");
        assertTrue(hasMemoryUsage, "Should have benchmarkMemoryUsage method");
    }

    private static void testMainMethodExists() {
        try {
            TemporalForkBenchmark.class.getMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("Benchmark should have a main method for standalone execution");
        }
    }

    private static void testEdgeCases(TemporalForkBenchmark benchmark) {
        // Test empty task list
        var emptyXml = benchmark.buildSyntheticCaseXml(List.of());
        assertNotNull(emptyXml, "Should handle empty task list");
        assertTrue(emptyXml.contains("<tasks></tasks>"),
                   "Empty task list should generate empty tasks element");

        // Test null handling (should throw NPE)
        try {
            benchmark.buildSyntheticCaseXml(null);
            throw new AssertionError("Should throw NPE for null task names");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected '" + expected + "', got '" + actual + "'");
        }
    }
}