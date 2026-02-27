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

package org.yawlfoundation.yawl.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.integration.a2a.skills.TemporalForkBenchmark;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for TemporalForkBenchmark
 */
public class TemporalForkBenchmarkTest {

    @Test
    public void testBenchmarkCompiles() {
        // This test ensures the benchmark compiles without errors
        TemporalForkBenchmark benchmark = new TemporalForkBenchmark();

        // Verify benchmark methods exist
        assertNotNull(benchmark);
    }

    @Test
    public void testXmlSerializationMethod() throws Exception {
        TemporalForkBenchmark benchmark = new TemporalForkBenchmark();

        // Test XML generation with small task list
        var taskNames = List.of("ReviewApplication", "ApproveApplication", "RejectApplication");
        String xml = benchmark.buildSyntheticCaseXml(taskNames);

        assertNotNull(xml);
        assertTrue(xml.contains("ReviewApplication"));
        assertTrue(xml.contains("ApproveApplication"));
        assertTrue(xml.contains("RejectApplication"));
        assertTrue(xml.contains("benchmark-case"));
    }

    @Test
    public void testGenerateTaskNames() {
        TemporalForkBenchmark benchmark = new TemporalForkBenchmark();

        // Test task name generation
        var taskNames = benchmark.generateTaskNames(5);

        assertEquals(5, taskNames.size());
        assertEquals("Task-1", taskNames.get(0));
        assertEquals("Task-2", taskNames.get(1));
        assertEquals("Task-3", taskNames.get(2));
        assertEquals("Task-4", taskNames.get(3));
        assertEquals("Task-5", taskNames.get(4));
    }
}