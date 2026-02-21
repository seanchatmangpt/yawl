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

package org.yawlfoundation.yawl.engine.interfce.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceMetrics.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("InterfaceMetrics Tests")
class InterfaceMetricsTest {

    private InterfaceMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = InterfaceMetrics.getInstance();
        metrics.reset();
        metrics.setEnabled(true);
    }

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonTests {

        @Test
        @DisplayName("getInstance returns same instance")
        void getInstance_returnsSameInstance() {
            InterfaceMetrics instance1 = InterfaceMetrics.getInstance();
            InterfaceMetrics instance2 = InterfaceMetrics.getInstance();

            assertSame(instance1, instance2, "getInstance should return the same instance");
        }

        @Test
        @DisplayName("getInstance is thread-safe")
        void getInstance_isThreadSafe() throws InterruptedException {
            final InterfaceMetrics[] instances = new InterfaceMetrics[10];
            Thread[] threads = new Thread[10];

            for (int i = 0; i < 10; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    instances[index] = InterfaceMetrics.getInstance();
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            for (int i = 1; i < 10; i++) {
                assertSame(instances[0], instances[i], "All instances should be the same");
            }
        }
    }

    @Nested
    @DisplayName("Interface A Metrics Tests")
    class InterfaceAMetricsTests {

        @Test
        @DisplayName("recordInterfaceARequestStart returns timestamp")
        void recordInterfaceARequestStart_returnsTimestamp() {
            long timestamp = metrics.recordInterfaceARequestStart("upload");

            assertTrue(timestamp > 0, "Should return a valid timestamp");
        }

        @Test
        @DisplayName("recordInterfaceARequestComplete updates counter")
        void recordInterfaceARequestComplete_updatesCounter() throws InterruptedException {
            long start = metrics.recordInterfaceARequestStart("upload");
            Thread.sleep(10);
            metrics.recordInterfaceARequestComplete("upload", start, true);

            assertEquals(1, metrics.getInterfaceATotalRequests(), "Total requests should be 1");
        }

        @Test
        @DisplayName("Interface A tracks multiple operations")
        void interfaceATracks_multipleOperations() {
            long start1 = metrics.recordInterfaceARequestStart("upload");
            long start2 = metrics.recordInterfaceARequestStart("connect");
            long start3 = metrics.recordInterfaceARequestStart("unload");

            metrics.recordInterfaceARequestComplete("upload", start1, true);
            metrics.recordInterfaceARequestComplete("connect", start2, true);
            metrics.recordInterfaceARequestComplete("unload", start3, false);

            assertEquals(3, metrics.getInterfaceATotalRequests(), "Should track 3 requests");
        }

        @Test
        @DisplayName("Interface A metrics disabled when enabled=false")
        void interfaceAMetrics_disabledWhenDisabled() {
            metrics.setEnabled(false);

            long start = metrics.recordInterfaceARequestStart("upload");
            metrics.recordInterfaceARequestComplete("upload", start, true);

            assertEquals(0, metrics.getInterfaceATotalRequests(), "Should not count when disabled");
        }
    }

    @Nested
    @DisplayName("Interface B Metrics Tests")
    class InterfaceBMetricsTests {

        @Test
        @DisplayName("recordInterfaceBRequestStart returns timestamp")
        void recordInterfaceBRequestStart_returnsTimestamp() {
            long timestamp = metrics.recordInterfaceBRequestStart("checkOut");

            assertTrue(timestamp > 0, "Should return a valid timestamp");
        }

        @Test
        @DisplayName("recordInterfaceBRequestComplete updates counter")
        void recordInterfaceBRequestComplete_updatesCounter() throws InterruptedException {
            long start = metrics.recordInterfaceBRequestStart("checkOut");
            Thread.sleep(10);
            metrics.recordInterfaceBRequestComplete("checkOut", start, true);

            assertEquals(1, metrics.getInterfaceBTotalRequests(), "Total requests should be 1");
        }

        @Test
        @DisplayName("recordInterfaceBWorkItemProcessed updates counter")
        void recordInterfaceBWorkItemProcessed_updatesCounter() {
            metrics.recordInterfaceBWorkItemProcessed("item-1", "case-1", "checkOut");
            metrics.recordInterfaceBWorkItemProcessed("item-2", "case-1", "checkIn");

            assertEquals(2, metrics.getInterfaceBTotalWorkItems(), "Should track 2 work items");
        }

        @Test
        @DisplayName("Interface B handles null work item and case IDs")
        void interfaceBHandles_nullIds() {
            metrics.recordInterfaceBWorkItemProcessed(null, null, "checkOut");

            assertEquals(1, metrics.getInterfaceBTotalWorkItems(), "Should handle null IDs gracefully");
        }
    }

    @Nested
    @DisplayName("Interface E Metrics Tests")
    class InterfaceEMetricsTests {

        @Test
        @DisplayName("recordInterfaceEQueryStart returns timestamp")
        void recordInterfaceEQueryStart_returnsTimestamp() {
            long timestamp = metrics.recordInterfaceEQueryStart("getCaseEvents");

            assertTrue(timestamp > 0, "Should return a valid timestamp");
        }

        @Test
        @DisplayName("recordInterfaceEQueryComplete updates counter")
        void recordInterfaceEQueryComplete_updatesCounter() throws InterruptedException {
            long start = metrics.recordInterfaceEQueryStart("getCaseEvents");
            Thread.sleep(10);
            metrics.recordInterfaceEQueryComplete("getCaseEvents", start, true);

            assertEquals(1, metrics.getInterfaceETotalQueries(), "Total queries should be 1");
        }

        @Test
        @DisplayName("Interface E tracks multiple query types")
        void interfaceETracks_multipleQueryTypes() {
            long start1 = metrics.recordInterfaceEQueryStart("getCaseEvents");
            long start2 = metrics.recordInterfaceEQueryStart("getSpecificationStatistics");
            long start3 = metrics.recordInterfaceEQueryStart("getTaskInstancesForCase");

            metrics.recordInterfaceEQueryComplete("getCaseEvents", start1, true);
            metrics.recordInterfaceEQueryComplete("getSpecificationStatistics", start2, true);
            metrics.recordInterfaceEQueryComplete("getTaskInstancesForCase", start3, false);

            assertEquals(3, metrics.getInterfaceETotalQueries(), "Should track 3 queries");
        }
    }

    @Nested
    @DisplayName("Interface X Metrics Tests")
    class InterfaceXMetricsTests {

        @Test
        @DisplayName("recordInterfaceXNotification updates counter")
        void recordInterfaceXNotification_updatesCounter() {
            metrics.recordInterfaceXNotification("workItemAbort", true);
            metrics.recordInterfaceXNotification("timeout", true);
            metrics.recordInterfaceXNotification("constraintViolation", false);

            assertEquals(3, metrics.getInterfaceXTotalNotifications(), "Should track 3 notifications");
        }

        @Test
        @DisplayName("recordInterfaceXRetry updates counter")
        void recordInterfaceXRetry_updatesCounter() {
            metrics.recordInterfaceXRetry("workItemAbort", 1);
            metrics.recordInterfaceXRetry("workItemAbort", 2);
            metrics.recordInterfaceXRetry("timeout", 1);

            assertEquals(3, metrics.getInterfaceXTotalRetries(), "Should track 3 retries");
        }

        @Test
        @DisplayName("recordInterfaceXFailure updates counter")
        void recordInterfaceXFailure_updatesCounter() {
            metrics.recordInterfaceXFailure("workItemAbort", "Connection refused");
            metrics.recordInterfaceXFailure("timeout", "Timeout after 3 retries");

            assertEquals(2, metrics.getInterfaceXTotalFailures(), "Should track 2 failures");
        }

        @Test
        @DisplayName("getInterfaceXRetryRate calculates correct rate")
        void getInterfaceXRetryRate_calculatesCorrectRate() {
            for (int i = 0; i < 10; i++) {
                metrics.recordInterfaceXNotification("event" + i, true);
            }
            metrics.recordInterfaceXRetry("event0", 1);
            metrics.recordInterfaceXRetry("event1", 1);

            assertEquals(20.0, metrics.getInterfaceXRetryRate(), 0.01, "Retry rate should be 20%");
        }

        @Test
        @DisplayName("getInterfaceXFailureRate calculates correct rate")
        void getInterfaceXFailureRate_calculatesCorrectRate() {
            for (int i = 0; i < 20; i++) {
                metrics.recordInterfaceXNotification("event" + i, true);
            }
            metrics.recordInterfaceXFailure("event0", "error1");
            metrics.recordInterfaceXFailure("event1", "error2");
            metrics.recordInterfaceXFailure("event2", "error3");

            assertEquals(15.0, metrics.getInterfaceXFailureRate(), 0.01, "Failure rate should be 15%");
        }

        @Test
        @DisplayName("getInterfaceXRetryRate returns 0 when no notifications")
        void getInterfaceXRetryRate_returnsZeroWhenNoNotifications() {
            assertEquals(0.0, metrics.getInterfaceXRetryRate(), 0.01, "Retry rate should be 0%");
        }

        @Test
        @DisplayName("getInterfaceXFailureRate returns 0 when no notifications")
        void getInterfaceXFailureRate_returnsZeroWhenNoNotifications() {
            assertEquals(0.0, metrics.getInterfaceXFailureRate(), 0.01, "Failure rate should be 0%");
        }
    }

    @Nested
    @DisplayName("Summary and Logging Tests")
    class SummaryTests {

        @Test
        @DisplayName("summary returns formatted string")
        void summary_returnsFormattedString() {
            metrics.recordInterfaceARequestStart("upload");
            metrics.recordInterfaceBRequestStart("checkOut");
            metrics.recordInterfaceEQueryStart("getCaseEvents");
            metrics.recordInterfaceXNotification("workItemAbort", true);

            String summary = metrics.summary();

            assertTrue(summary.contains("A=1"), "Summary should show Interface A count");
            assertTrue(summary.contains("B=1"), "Summary should show Interface B count");
            assertTrue(summary.contains("E=1"), "Summary should show Interface E count");
            assertTrue(summary.contains("notifications=1"), "Summary should show notifications count");
        }

        @Test
        @DisplayName("logMetrics does not throw")
        void logMetrics_doesNotThrow() {
            assertDoesNotThrow(() -> metrics.logMetrics(), "logMetrics should not throw");
        }
    }

    @Nested
    @DisplayName("Enable/Disable Tests")
    class EnableDisableTests {

        @Test
        @DisplayName("setEnabled false prevents counting")
        void setEnabled_falsePreventsCounting() {
            metrics.setEnabled(false);

            metrics.recordInterfaceARequestStart("upload");
            metrics.recordInterfaceBRequestStart("checkOut");
            metrics.recordInterfaceEQueryStart("getCaseEvents");
            metrics.recordInterfaceXNotification("workItemAbort", true);

            assertEquals(0, metrics.getInterfaceATotalRequests(), "Interface A should not count");
            assertEquals(0, metrics.getInterfaceBTotalRequests(), "Interface B should not count");
            assertEquals(0, metrics.getInterfaceETotalQueries(), "Interface E should not count");
            assertEquals(0, metrics.getInterfaceXTotalNotifications(), "Interface X should not count");
        }

        @Test
        @DisplayName("isEnabled returns current state")
        void isEnabled_returnsCurrentState() {
            assertTrue(metrics.isEnabled(), "Should be enabled by default");

            metrics.setEnabled(false);
            assertFalse(metrics.isEnabled(), "Should be disabled after setEnabled(false)");

            metrics.setEnabled(true);
            assertTrue(metrics.isEnabled(), "Should be enabled after setEnabled(true)");
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class ResetTests {

        @Test
        @DisplayName("reset clears all counters")
        void reset_clearsAllCounters() {
            metrics.recordInterfaceARequestStart("upload");
            metrics.recordInterfaceBRequestStart("checkOut");
            metrics.recordInterfaceBWorkItemProcessed("item-1", "case-1", "checkOut");
            metrics.recordInterfaceEQueryStart("getCaseEvents");
            metrics.recordInterfaceXNotification("workItemAbort", true);
            metrics.recordInterfaceXRetry("workItemAbort", 1);
            metrics.recordInterfaceXFailure("workItemAbort", "error");

            metrics.reset();

            assertEquals(0, metrics.getInterfaceATotalRequests(), "Interface A should be reset");
            assertEquals(0, metrics.getInterfaceBTotalRequests(), "Interface B should be reset");
            assertEquals(0, metrics.getInterfaceBTotalWorkItems(), "Interface B work items should be reset");
            assertEquals(0, metrics.getInterfaceETotalQueries(), "Interface E should be reset");
            assertEquals(0, metrics.getInterfaceXTotalNotifications(), "Interface X notifications should be reset");
            assertEquals(0, metrics.getInterfaceXTotalRetries(), "Interface X retries should be reset");
            assertEquals(0, metrics.getInterfaceXTotalFailures(), "Interface X failures should be reset");
        }
    }

    @Nested
    @DisplayName("GetMeter Tests")
    class GetMeterTests {

        @Test
        @DisplayName("getMeter returns non-null")
        void getMeter_returnsNonNull() {
            assertNotNull(metrics.getMeter(), "getMeter should return non-null");
        }
    }
}
