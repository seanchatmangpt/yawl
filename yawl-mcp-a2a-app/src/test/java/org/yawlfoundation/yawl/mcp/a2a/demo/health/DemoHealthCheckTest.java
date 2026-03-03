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

package org.yawlfoundation.yawl.mcp.a2a.demo.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.demo.health.DemoHealthCheck.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DemoHealthCheck.
 */
class DemoHealthCheckTest {

    private DemoHealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new DemoHealthCheck();
    }

    @Test
    void testInitialization() {
        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(Status.STARTING, status.status());
        assertFalse(status.isHealthy());
        assertEquals("INITIALIZING", status.details().get("status"));
    }

    @Test
    void testInitializationComplete() {
        healthCheck.markInitialized();
        healthCheck.setTotalPatterns(5);

        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(Status.HEALTHY, status.status());
        assertTrue(status.isHealthy());
        assertEquals(5, status.details().get("patterns_total"));
        assertEquals("HEALTHY", status.details().get("status"));
    }

    @Test
    void testPatternCompletion() {
        healthCheck.markInitialized();
        healthCheck.setTotalPatterns(10);
        healthCheck.incrementCompleted();
        healthCheck.incrementCompleted();
        healthCheck.incrementFailed();

        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(10, status.details().get("patterns_total"));
        assertEquals(2, status.details().get("patterns_completed"));
        assertEquals(1, status.details().get("patterns_failed"));
        assertEquals(20.0, status.details().get("progress_percent"));

        // Should still be healthy with only 10% failure rate
        assertEquals(Status.HEALTHY, status.status());
        assertTrue(status.isHealthy());
    }

    @Test
    void testHighFailureRate() {
        healthCheck.markInitialized();
        healthCheck.setTotalPatterns(10);
        healthCheck.incrementFailed();
        healthCheck.incrementFailed(); // 20% failure rate

        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(Status.DEGRADED, status.status());
        assertFalse(status.isHealthy());
        assertTrue(status.details().containsKey("warning"));
        assertTrue(((String) status.details().get("warning")).contains("High failure rate"));
    }

    @Test
    void testShutdownRequested() {
        healthCheck.markInitialized();
        healthCheck.setTotalPatterns(5);
        healthCheck.incrementCompleted();
        healthCheck.markShutdownRequested();

        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(Status.STOPPING, status.status());
        assertEquals("SHUTTING_DOWN", status.details().get("status"));
        assertEquals(1, status.details().get("patterns_completed"));
        assertEquals(5, status.details().get("patterns_total"));
    }

    @Test
    void testZeroPatterns() {
        healthCheck.markInitialized();
        healthCheck.setTotalPatterns(0);

        DemoHealthCheck.HealthStatus status = healthCheck.check();

        assertEquals(0, status.details().get("patterns_total"));
        assertEquals(0, status.details().get("patterns_completed"));
        assertEquals(0.0, status.details().get("progress_percent"));
        assertEquals(Status.HEALTHY, status.status());
    }
}