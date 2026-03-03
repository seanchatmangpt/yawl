/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

/**
 * Integration test for stress test framework components.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
class IntegrationTest {

    @Test
    void testStressTestOrchestratorCreation() {
        StressTestOrchestrator orchestrator = new StressTestOrchestrator();
        assertNotNull(orchestrator);

        List<String> testClasses = orchestrator.getTestClasses();
        assertEquals(4, testClasses.size());
        assertTrue(testClasses.contains("Basic Latency Stress Test"));
        assertTrue(testClasses.contains("Basic Throughput Stress Test"));
        assertTrue(testClasses.contains("Basic Concurrency Stress Test"));
        assertTrue(testClasses.contains("Basic Memory Stress Test"));
    }

    @Test
    void testDataPointsCreation() {
        // Test that data point classes can be instantiated
        ThroughputDataPoint throughputPoint = new ThroughputDataPoint(
            System.currentTimeMillis(), 10, 100.0, 50.0, 0.05);
        assertNotNull(throughputPoint);

        MemoryDataPoint memoryPoint = new MemoryDataPoint(
            System.currentTimeMillis(), 50.5, 0.1, 5, 50, 1000);
        assertNotNull(memoryPoint);

        ConcurrencyDataPoint concurrencyPoint = new ConcurrencyDataPoint(
            System.currentTimeMillis(), 10, 1000, 10, 2, 25.5, 5);
        assertNotNull(concurrencyPoint);
    }

    @Test
    void testReportGeneration() {
        String json = SimpleReportGenerator.generateJSONReport(
            "GREEN",
            List.of("Test1", "Test2"),
            java.time.Duration.ofMinutes(1),
            List.of(),
            List.of("Warning1")
        );

        assertNotNull(json);
        assertTrue(json.contains("overallStatus"));
        assertTrue(json.contains("testClasses"));
        assertTrue(json.contains("GREEN"));
    }
}