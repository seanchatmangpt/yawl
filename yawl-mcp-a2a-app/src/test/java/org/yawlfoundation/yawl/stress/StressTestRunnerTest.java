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
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for StressTestRunner.
 *
 * @author YAWL Stress Testing Framework
 * @version 1.0
 */
class StressTestRunnerTest {

    private StressTestRunner runner;

    @BeforeEach
    void setUp() {
        runner = new StressTestRunner();
        runner.setVerbose(true);
    }

    @Test
    void testRunnerCreation() {
        assertNotNull(runner);
    }

    @Test
    void testRunAllTests() throws StressTestException {
        StressTestReport report = runner.runAllTests();

        assertNotNull(report);
        assertNotNull(report.getMetadata());
        assertNotNull(report.getTestSummary());

        // Verify test count
        assertFalse(report.getTestClasses().isEmpty());

        // Verify metrics collection
        assertFalse(report.getTestMetrics().isEmpty());

        // Verify report status is set
        assertNotNull(report.getOverallStatus());

        // Verify report is saved
        assertTrue(reportFileExists());
    }

    @Test
    void testVerboseMode() {
        StressTestRunner verboseRunner = new StressTestRunner();
        assertFalse(verboseRunner.isVerbose());

        verboseRunner.setVerbose(true);
        assertTrue(verboseRunner.isVerbose());
    }

    /**
     * Checks if the report file was created.
     */
    private boolean reportFileExists() {
        java.io.File reportDir = new java.io.File(".claude", "reports");
        if (!reportDir.exists()) {
            return false;
        }

        java.io.File[] reportFiles = reportDir.listFiles(
            (dir, name) -> name.startsWith("stress-test-report-")
            && name.endsWith(".json")
        );

        return reportFiles != null && reportFiles.length > 0;
    }
}