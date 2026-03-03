/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.resilience.observability.RetryObservability;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RetryObservability component.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class RetryObservabilityTest {

    private RetryObservability retryObservability;

    @BeforeEach
    void setUp() {
        retryObservability = RetryObservability.getInstance();
    }

    @Test
    void testGetInstance() {
        assertNotNull(retryObservability, "RetryObservability instance should not be null");
        assertSame(retryObservability, RetryObservability.getInstance(),
                  "Should return the same instance");
    }

    @Test
    void testRecordSuccess() {
        assertDoesNotThrow(() -> retryObservability.recordSuccess());
    }

    @Test
    void testRecordFailure() {
        assertDoesNotThrow(() -> retryObservability.recordFailure(new RuntimeException("Test error")));
    }

    @Test
    void testRecordAttempt() {
        assertDoesNotThrow(() -> retryObservability.recordAttempt());
    }

    @Test
    void testRecordDuration() {
        assertDoesNotThrow(() -> retryObservability.recordDuration(Duration.ofMillis(100)));
    }

    @Test
    void testRecordCircuitBreakerState() {
        assertDoesNotThrow(() -> {
            retryObservability.recordCircuitBreakerState(0); // Closed
            retryObservability.recordCircuitBreakerState(1); // Half-open
            retryObservability.recordCircuitBreakerState(2); // Open
        });
    }

    @Test
    void testGetCircuitBreakerStateCount() {
        // Clear any previous counts
        retryObservability.recordCircuitBreakerState(0);

        long closedCount = retryObservability.getCircuitBreakerStateCount(0);
        assertTrue(closedCount >= 0, "Closed count should be non-negative");

        long halfOpenCount = retryObservability.getCircuitBreakerStateCount(1);
        assertTrue(halfOpenCount >= 0, "Half-open count should be non-negative");

        long openCount = retryObservability.getCircuitBreakerStateCount(2);
        assertTrue(openCount >= 0, "Open count should be non-negative");

        // Test invalid state
        long invalidCount = retryObservability.getCircuitBreakerStateCount(999);
        assertEquals(0, invalidCount, "Invalid state should return 0");
    }
}