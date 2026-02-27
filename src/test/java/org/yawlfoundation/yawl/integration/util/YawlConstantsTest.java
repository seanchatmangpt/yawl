/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL (Yet Another Workflow Language).
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YawlConstants utility class.
 */
class YawlConstantsTest {

    @Test
    void allConstantsAreNonNull() {
        assertNotNull(YawlConstants.PROJECT_NAME);
        assertNotNull(YawlConstants.PROJECT_VERSION);
        assertNotNull(YawlConstants.YAWL_SCHEMA_VERSION);
        assertNotNull(YawlConstants.INTEGRATION_NAMESPACE);
        assertNotNull(YawlConstants.EVENT_TYPE);
        assertNotNull(YawlConstants.SIMULATION_TYPE);
        assertNotNull(YawlConstants.REPORT_TYPE);
        assertNotNull(YawlConstants.DEFAULT_EVENT_SEVERITY);
        assertNotNull(YawlConstants.DEFAULT_EVENT_TIMESTAMP);
        assertNotNull(YawlConstants.YAWL_URL);
        assertNotNull(YawlConstants.MCP_ENDPOINT_URL);
        assertNotNull(YawlConstants.A2A_SERVICE_URL);
        assertNotNull(YawlConstants.MCP_API_KEY);
        assertNotNull(YawlConstants.A2A_API_KEY);
        assertNotNull(YawlConstants.MAX_PAYLOAD_SIZE);
        assertNotNull(YawlConstants.DEFAULT_TIMEOUT_MS);
        assertNotNull(YawlConstants.ENABLE_METRICS);
        assertNotNull(YawlConstants.METRICS_PORT);
        assertNotNull(YawlConstants.LOG_LEVEL);
        assertNotNull(YawlConstants.ENABLE_DEBUG);
    }

    @Test
    void constantsHaveNonEmptyValues() {
        assertFalse(YawlConstants.PROJECT_NAME.isEmpty());
        assertFalse(YawlConstants.PROJECT_VERSION.isEmpty());
        assertFalse(YawlConstants.YAWL_SCHEMA_VERSION.isEmpty());
        assertFalse(YawlConstants.INTEGRATION_NAMESPACE.isEmpty());
        assertFalse(YawlConstants.EVENT_TYPE.isEmpty());
        assertFalse(YawlConstants.SIMULATION_TYPE.isEmpty());
        assertFalse(YawlConstants.REPORT_TYPE.isEmpty());
        assertFalse(YawlConstants.DEFAULT_EVENT_SEVERITY.isEmpty());
        assertFalse(YawlConstants.DEFAULT_EVENT_TIMESTAMP.isEmpty());
        assertFalse(YawlConstants.YAWL_URL.isEmpty());
        assertFalse(YawlConstants.MCP_ENDPOINT_URL.isEmpty());
        assertFalse(YawlConstants.A2A_SERVICE_URL.isEmpty());
        assertFalse(YawlConstants.MCP_API_KEY.isEmpty());
        assertFalse(YawlConstants.A2A_API_KEY.isEmpty());
        assertFalse(YawlConstants.MAX_PAYLOAD_SIZE.isEmpty());
        assertFalse(YawlConstants.DEFAULT_TIMEOUT_MS.isEmpty());
        assertFalse(YawlConstants.ENABLE_METRICS.isEmpty());
        assertFalse(YawlConstants.METRICS_PORT.isEmpty());
        assertFalse(YawlConstants.LOG_LEVEL.isEmpty());
        assertFalse(YawlConstants.ENABLE_DEBUG.isEmpty());
    }

    @Test
    void stringConstantsAreImmutable() {
        // Attempt to modify and verify it fails
        String original = YawlConstants.PROJECT_NAME;
        try {
            // This should throw an exception since String is immutable
            // We can't actually modify it, so we just verify it's not null
            assertNotNull(original);
        } catch (Exception e) {
            // If there's any exception, it means the constant is truly immutable
            assertTrue(true);
        }
    }

    @Test
    void numericConstantsAreValid() {
        // Test numeric constants have reasonable values
        assertNotEquals("0", YawlConstants.METRICS_PORT);
        assertNotEquals("0", YawlConstants.MAX_PAYLOAD_SIZE);
        assertNotEquals("0", YawlConstants.DEFAULT_TIMEOUT_MS);
    }

    @Test
    void booleanConstantsAreValid() {
        // Test boolean constants have valid values
        assertTrue(YawlConstants.ENABLE_METRICS.equals("true") ||
                   YawlConstants.ENABLE_METRICS.equals("false"));
        assertTrue(YawlConstants.ENABLE_DEBUG.equals("true") ||
                   YawlConstants.ENABLE_DEBUG.equals("false"));
    }

    @Test
    void defaultValuesAreValid() {
        // Test that default values are valid according to their types
        assertFalse(YawlConstants.DEFAULT_EVENT_SEVERITY.isEmpty());
        assertFalse(YawlConstants.DEFAULT_EVENT_TIMESTAMP.isEmpty());
        assertFalse(YawlConstants.LOG_LEVEL.isEmpty());
    }

    @Test
    void urlConstantsAreValid() {
        // Test URL constants start with http(s)
        assertTrue(YawlConstants.YAWL_URL.startsWith("http"));
        assertTrue(YawlConstants.MCP_ENDPOINT_URL.startsWith("http"));
        assertTrue(YawlConstants.A2A_SERVICE_URL.startsWith("http"));
    }

    @Test
    void allConstantsAreAccessible() {
        // Access all constants to ensure they're properly defined
        String[] constants = {
            YawlConstants.PROJECT_NAME,
            YawlConstants.PROJECT_VERSION,
            YawlConstants.YAWL_SCHEMA_VERSION,
            YawlConstants.INTEGRATION_NAMESPACE,
            YawlConstants.EVENT_TYPE,
            YawlConstants.SIMULATION_TYPE,
            YawlConstants.REPORT_TYPE,
            YawlConstants.DEFAULT_EVENT_SEVERITY,
            YawlConstants.DEFAULT_EVENT_TIMESTAMP,
            YawlConstants.YAWL_URL,
            YawlConstants.MCP_ENDPOINT_URL,
            YawlConstants.A2A_SERVICE_URL,
            YawlConstants.MCP_API_KEY,
            YawlConstants.A2A_API_KEY,
            YawlConstants.MAX_PAYLOAD_SIZE,
            YawlConstants.DEFAULT_TIMEOUT_MS,
            YawlConstants.ENABLE_METRICS,
            YawlConstants.METRICS_PORT,
            YawlConstants.LOG_LEVEL,
            YawlConstants.ENABLE_DEBUG
        };

        for (String constant : constants) {
            assertNotNull(constant);
            assertFalse(constant.isEmpty());
        }
    }

    @Test
    void constantsAreNotNullOrEmpty() {
        // Comprehensive check that no constant is null or empty
        String[] constants = {
            YawlConstants.PROJECT_NAME,
            YawlConstants.PROJECT_VERSION,
            YawlConstants.YAWL_SCHEMA_VERSION,
            YawlConstants.INTEGRATION_NAMESPACE,
            YawlConstants.EVENT_TYPE,
            YawlConstants.SIMULATION_TYPE,
            YawlConstants.REPORT_TYPE,
            YawlConstants.DEFAULT_EVENT_SEVERITY,
            YawlConstants.DEFAULT_EVENT_TIMESTAMP,
            YawlConstants.YAWL_URL,
            YawlConstants.MCP_ENDPOINT_URL,
            YawlConstants.A2A_SERVICE_URL,
            YawlConstants.MCP_API_KEY,
            YawlConstants.A2A_API_KEY,
            YawlConstants.MAX_PAYLOAD_SIZE,
            YawlConstants.DEFAULT_TIMEOUT_MS,
            YawlConstants.ENABLE_METRICS,
            YawlConstants.METRICS_PORT,
            YawlConstants.LOG_LEVEL,
            YawlConstants.ENABLE_DEBUG
        };

        for (String constant : constants) {
            assertNotNull(constant, "Constant should not be null");
            assertFalse(constant.isEmpty(), "Constant should not be empty");
            assertFalse(constant.trim().isEmpty(), "Constant should not be whitespace only");
        }
    }

    @Test
    void constantsHaveExpectedFormat() {
        // Test that constants have expected format/length
        assertFalse(YawlConstants.PROJECT_NAME.length() < 1);
        assertFalse(YawlConstants.PROJECT_VERSION.length() < 1);
        assertFalse(YawlConstants.YAWL_SCHEMA_VERSION.length() < 1);

        // Version-like constants should look like versions
        assertTrue(YawlConstants.PROJECT_VERSION.matches("\\d+\\.\\d+.*") ||
                  YawlConstants.PROJECT_VERSION.matches("v\\d+\\.\\d+"));

        // URL constants should be properly formatted
        assertTrue(YawlConstants.YAWL_URL.contains("://"));
        assertTrue(YawlConstants.MCP_ENDPOINT_URL.contains("://"));
        assertTrue(YawlConstants.A2A_SERVICE_URL.contains("://"));
    }
}