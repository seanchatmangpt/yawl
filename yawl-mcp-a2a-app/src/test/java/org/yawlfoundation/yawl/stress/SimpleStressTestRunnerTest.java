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

/**
 * Simple test to verify stress test runner functionality.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
class SimpleStressTestRunnerTest {

    @Test
    void testStressRunnerInitialization() {
        StressTestRunner runner = new StressTestRunner();
        assertNotNull(runner);
        assertEquals(4, runner.getClass().getDeclaredFields().length); // Basic structure check
    }

    @Test
    void testStressTestInterfaces() {
        // Verify that our test classes implement the required interface
        assertTrue(StressTest.class.isAssignableFrom(BasicLatencyStressTest.class));
        assertTrue(StressTest.class.isAssignableFrom(BasicThroughputStressTest.class));
        assertTrue(StressTest.class.isAssignableFrom(BasicConcurrencyStressTest.class));
        assertTrue(StressTest.class.isAssignableFrom(BasicMemoryStressTest.class));
    }

    @Test
    void testDataPointClasses() {
        // Verify data point classes can be instantiated
        ThroughputDataPoint throughputPoint = new ThroughputDataPoint(0, 0, 0, 0, 0);
        assertNotNull(throughputPoint);

        MemoryDataPoint memoryPoint = new MemoryDataPoint(0, 0, 0, 0, 0, 0);
        assertNotNull(memoryPoint);

        ConcurrencyDataPoint concurrencyPoint = new ConcurrencyDataPoint(0, 0, 0, 0, 0, 0);
        assertNotNull(concurrencyPoint);
    }
}