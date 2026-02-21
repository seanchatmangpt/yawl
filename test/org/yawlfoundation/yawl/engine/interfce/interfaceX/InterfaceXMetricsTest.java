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

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.metrics.InterfaceMetrics;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceXMetrics.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class InterfaceXMetricsTest {

    @BeforeEach
    void setUp() {
        resetMetricsSingletons();
    }

    @AfterEach
    void tearDown() {
        resetMetricsSingletons();
    }

    private void resetMetricsSingletons() {
        try {
            var field = InterfaceXMetrics.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
            if (InterfaceXMetrics.isInitialized()) {
                throw new RuntimeException("Failed to reset InterfaceXMetrics.instance via reflection");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset InterfaceXMetrics singleton", e);
        }
        try {
            var field = InterfaceMetrics.class.getDeclaredField("_instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset InterfaceMetrics singleton", e);
        }
    }

    @Test
    @DisplayName("Should initialize singleton instance")
    void shouldInitializeSingleton() {
        assertFalse(InterfaceXMetrics.isInitialized());

        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertTrue(InterfaceXMetrics.isInitialized());
        assertNotNull(metrics);
        assertSame(metrics, InterfaceXMetrics.getInstance());
    }

    @Test
    @DisplayName("Should throw when getting uninitialized instance")
    void shouldThrowWhenUninitialized() {
        assertFalse(InterfaceXMetrics.isInitialized());

        assertThrows(IllegalStateException.class, InterfaceXMetrics::getInstance);
    }

    @Test
    @DisplayName("Should record notification attempts")
    void shouldRecordNotificationAttempts() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertEquals(0, metrics.getNotificationsTotalCount());

        metrics.recordNotificationAttempt("NOTIFY_TIMEOUT");
        metrics.recordNotificationAttempt("NOTIFY_TIMEOUT");

        assertEquals(2, metrics.getNotificationsTotalCount());
    }

    @Test
    @DisplayName("Should record successes")
    void shouldRecordSuccesses() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertEquals(0, metrics.getNotificationsSuccessCount());

        metrics.recordSuccess("NOTIFY_TIMEOUT");
        metrics.recordSuccess("NOTIFY_TIMEOUT");
        metrics.recordSuccess("NOTIFY_WORKITEM_ABORT");

        assertEquals(3, metrics.getNotificationsSuccessCount());
    }

    @Test
    @DisplayName("Should record retries")
    void shouldRecordRetries() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertEquals(0, metrics.getRetriesTotalCount());

        metrics.recordRetry("NOTIFY_TIMEOUT", 1);
        metrics.recordRetry("NOTIFY_TIMEOUT", 2);

        assertEquals(2, metrics.getRetriesTotalCount());
    }

    @Test
    @DisplayName("Should record failures")
    void shouldRecordFailures() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertEquals(0, metrics.getFailuresTotalCount());

        metrics.recordFailure("NOTIFY_TIMEOUT");
        metrics.recordFailure("NOTIFY_CANCELLED_CASE");

        assertEquals(2, metrics.getFailuresTotalCount());
    }

    @Test
    @DisplayName("Should record dead letters")
    void shouldRecordDeadLetters() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertEquals(0, metrics.getDeadLettersTotalCount());

        metrics.recordDeadLetter("NOTIFY_TIMEOUT");
        metrics.recordDeadLetter("NOTIFY_TIMEOUT");
        metrics.recordDeadLetter("NOTIFY_TIMEOUT");

        assertEquals(3, metrics.getDeadLettersTotalCount());
    }

    @Test
    @DisplayName("Should record duration without Micrometer")
    void shouldRecordDurationWithoutMicrometer() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertDoesNotThrow(() -> metrics.recordDuration(100));
        assertDoesNotThrow(() -> metrics.recordDuration(500));
    }

    @Test
    @DisplayName("Should handle timer without Micrometer")
    void shouldHandleTimerWithoutMicrometer() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertNull(metrics.startTimer());
        assertDoesNotThrow(() -> metrics.stopTimer(null));
    }

    @Test
    @DisplayName("Should return null meter registry when not provided")
    void shouldReturnNullMeterRegistry() {
        InterfaceXMetrics metrics = InterfaceXMetrics.initialize(null);

        assertNull(metrics.getMeterRegistry());
    }
}
