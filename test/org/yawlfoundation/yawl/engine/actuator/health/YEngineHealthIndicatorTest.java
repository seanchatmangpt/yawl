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

package org.yawlfoundation.yawl.engine.actuator.health;

import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.yawlfoundation.yawl.engine.YEngine;

import static org.junit.Assert.*;

/**
 * Test suite for YEngineHealthIndicator.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YEngineHealthIndicatorTest {

    private YEngineHealthIndicator healthIndicator;
    private YEngine engine;

    @Before
    public void setUp() throws Exception {
        engine = YEngine.getInstance();
        healthIndicator = new YEngineHealthIndicator();
    }

    @Test
    public void testHealthCheckWhenEngineRunning() {
        Health health = healthIndicator.health();

        assertNotNull("Health result should not be null", health);
        assertEquals("Engine should be UP when running",
            Status.UP, health.getStatus());

        assertNotNull("Health details should include status",
            health.getDetails().get("status"));
        assertEquals("Ready flag should be true",
            true, health.getDetails().get("ready"));
    }

    @Test
    public void testHealthDetailsIncludeMetrics() {
        Health health = healthIndicator.health();

        assertTrue("Health details should include activeCases",
            health.getDetails().containsKey("activeCases"));
        assertTrue("Health details should include maxActiveCases",
            health.getDetails().containsKey("maxActiveCases"));
        assertTrue("Health details should include workItems",
            health.getDetails().containsKey("workItems"));
        assertTrue("Health details should include loadedSpecifications",
            health.getDetails().containsKey("loadedSpecifications"));
        assertTrue("Health details should include overallLoad",
            health.getDetails().containsKey("overallLoad"));
    }

    @Test
    public void testHealthCheckReportsActiveCases() {
        Health health = healthIndicator.health();

        Object activeCases = health.getDetails().get("activeCases");
        assertNotNull("Active cases should be reported", activeCases);
        assertTrue("Active cases should be a number",
            activeCases instanceof Integer);

        int caseCount = (Integer) activeCases;
        assertTrue("Active cases should be non-negative",
            caseCount >= 0);
    }

    @Test
    public void testHealthCheckReportsLoadMetrics() {
        Health health = healthIndicator.health();

        Object caseLoad = health.getDetails().get("caseLoad");
        Object workItemLoad = health.getDetails().get("workItemLoad");
        Object overallLoad = health.getDetails().get("overallLoad");

        assertNotNull("Case load should be reported", caseLoad);
        assertNotNull("Work item load should be reported", workItemLoad);
        assertNotNull("Overall load should be reported", overallLoad);

        assertTrue("Load metrics should be strings",
            caseLoad instanceof String);
        assertTrue("Load string should contain percentage",
            ((String) caseLoad).contains("%"));
    }

    @Test
    public void testHealthCheckHandlesErrors() {
        Health health = healthIndicator.health();

        assertNotNull("Health check should handle errors gracefully", health);

        if (health.getStatus().equals(Status.DOWN)) {
            assertTrue("Error details should be present on failure",
                health.getDetails().containsKey("error") ||
                health.getDetails().containsKey("reason"));
        }
    }
}
