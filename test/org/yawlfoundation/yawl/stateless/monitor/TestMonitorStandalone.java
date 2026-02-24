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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

/**
 * Standalone tests for monitor package that don't depend on external resources.
 * This allows us to test basic functionality without resource file dependencies.
 */
@DisplayName("Monitor Package Standalone Tests")
@Tag("unit")
class TestMonitorStandalone {

    @Nested
    @DisplayName("CaseSnapshot Edge Cases")
    class CaseSnapshotEdgeCases {

        @Test
        @DisplayName("CaseSnapshot with empty XML")
        void caseSnapshotWithEmptyXML() {
            String emptyXML = "";
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot("case1", "spec1", emptyXML, java.time.Instant.now()));
        }

        @Test
        @DisplayName("CaseSnapshot with whitespace only")
        void caseSnapshotWithWhitespace() {
            String whitespaceXML = "   \t\n   ";
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot("case1", "spec1", whitespaceXML, java.time.Instant.now()));
        }

        @Test
        @DisplayName("CaseSnapshot with Unicode IDs")
        void caseSnapshotWithUnicodeIDs() {
            String unicodeCaseId = "测试_123";
            String unicodeSpecId = "日本語_456";
            String xml = "<case><data>안녕하세요</data></case>";

            assertDoesNotThrow(() ->
                new CaseSnapshot(unicodeCaseId, unicodeSpecId, xml, java.time.Instant.now()));
        }
    }

    @Nested
    @DisplayName("YCaseMonitor Basic Operations")
    class YCaseMonitorBasicTests {

        @Test
        @DisplayName("YCaseMonitor with zero timeout")
        void yCaseMonitorWithZeroTimeout() {
            YCaseMonitor monitor = new YCaseMonitor(0);

            assertEquals(0, monitor.getIdleTimeout());
            assertFalse(monitor.hasCase(null)); // Should return false for null ID
        }

        @Test
        @DisplayName("YCaseMonitor with negative timeout")
        void yCaseMonitorWithNegativeTimeout() {
            YCaseMonitor monitor = new YCaseMonitor(-1000);

            assertEquals(-1000, monitor.getIdleTimeout());
            assertFalse(monitor.hasCase(null));
        }

        @Test
        @DisplayName("YCaseMonitor timeout setter")
        void yCaseMonitorTimeoutSetter() {
            YCaseMonitor monitor = new YCaseMonitor(1000);
            monitor.setIdleTimeout(2000);

            assertEquals(2000, monitor.getIdleTimeout());
        }
    }

    @Nested
    @DisplayName("CaseMetrics Inner Classes")
    class CaseMetricsTests {

        @Test
        @DisplayName("YCaseMonitoringService.CaseStatistics")
        void caseStatistics() {
            YCaseMonitoringService.CaseStatistics stats = new YCaseMonitoringService.CaseStatistics();

            // Default values
            assertEquals(0, stats.totalCases);
            assertEquals(0, stats.activeCases);
            assertEquals(0, stats.completedCases);
            assertEquals(0, stats.cancelledCases);
            assertEquals(0.0, stats.avgCompletionTimeMs);
            assertEquals(0, stats.timestamp);

            // Test toString
            String statsString = stats.toString();
            assertTrue(statsString.contains("CaseStatistics"));
            assertTrue(statsString.contains("total=0"));
        }

        @Test
        @DisplayName("YCaseMonitoringService.TaskMetrics")
        void taskMetrics() {
            YCaseMonitoringService.TaskMetrics metrics = new YCaseMonitoringService.TaskMetrics("testTask");

            assertEquals("testTask", metrics.getTaskID());
            assertEquals(0, metrics.getAverageDurationMs());
            assertEquals(0, metrics.getMaxDurationMs());
            assertEquals(0, metrics.getMinDurationMs());
            assertEquals(0, metrics.getExecutionCount());

            // Add some durations
            metrics.addDuration(1000);
            metrics.addDuration(2000);
            metrics.addDuration(3000);
            metrics.incrementCount();
            metrics.incrementCount();

            assertEquals(2, metrics.getExecutionCount());
            assertEquals(2000, metrics.getAverageDurationMs());
            assertEquals(3000, metrics.getMaxDurationMs());
            assertEquals(1000, metrics.getMinDurationMs());

            // Test toString
            String metricsString = metrics.toString();
            assertTrue(metricsString.contains("TaskMetrics"));
            assertTrue(metricsString.contains("task=testTask"));
            assertTrue(metricsString.contains("count=2"));
        }

        @Test
        @DisplayName("YCaseMonitoringService.CaseMetrics")
        void caseMetrics() {
            // We can't directly create YIdentifier, so we'll test the interface
            // This test ensures the inner class can be instantiated and accessed
            Class<?> caseMetricsClass = YCaseMonitoringService.CaseMetrics.class;
            assertEquals("caseID", caseMetricsClass.getFields()[0].getName());
        }
    }

    @Nested
    @DisplayName("ValidationResult Inner Class")
    class ValidationResultTests {

        @Test
        @DisplayName("YCaseImportExportService.ValidationResult")
        void validationResult() {
            YCaseImportExportService.ValidationResult result =
                new YCaseImportExportService.ValidationResult();

            // Default values
            assertNull(result.filename);
            assertFalse(result.isValid);
            assertEquals(0, result.validCaseCount);
            assertTrue(result.errors.isEmpty());

            result.filename = "test.xml";
            result.isValid = true;
            result.validCaseCount = 5;
            result.errors.add("Test error");

            assertEquals("test.xml", result.filename);
            assertTrue(result.isValid);
            assertEquals(5, result.validCaseCount);
            assertEquals(1, result.errors.size());

            // Test toString
            String resultString = result.toString();
            assertTrue(resultString.contains("ValidationResult"));
            assertTrue(resultString.contains("file=test.xml"));
            assertTrue(resultString.contains("errors=1"));
        }
    }
}