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

package org.yawlfoundation.yawl.integration.mcp.util;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Test class for McpResponseBuilder utility.
 */
class McpResponseBuilderTest {

    @Test
    void testSuccessResponse() {
        McpSchema.CallToolResult result = McpResponseBuilder.success("Test content");

        assertFalse(result.isError());
        assertEquals("Test content", result.contents().getFirst().text());
    }

    @Test
    void testSuccessWithTiming() {
        String operationName = "test-operation";
        long elapsedMs = 123;
        McpSchema.CallToolResult result = McpResponseBuilder.successWithTiming(
            "Operation completed", operationName, elapsedMs);

        assertFalse(result.isError());
        String content = result.contents().getFirst().text();
        assertTrue(content.contains("Operation completed"));
        assertTrue(content.contains("test-operation completed in 123ms"));
    }

    @Test
    void testSuccessWithData() {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 42);

        long elapsedMs = 456;
        McpSchema.CallToolResult result = McpResponseBuilder.successWithData(data, elapsedMs);

        assertFalse(result.isError());
        String content = result.contents().getFirst().text();
        assertTrue(content.contains("key1: value1"));
        assertTrue(content.contains("key2: 42"));
        assertTrue(content.contains("elapsed_ms: 456"));
    }

    @Test
    void testErrorResponse() {
        McpSchema.CallToolResult result = McpResponseBuilder.error("Test error");

        assertTrue(result.isError());
        assertTrue(result.contents().getFirst().text().contains("ERROR: Test error"));
    }

    @Test
    void testErrorWithException() {
        RuntimeException e = new RuntimeException("Test exception message");
        McpSchema.CallToolResult result = McpResponseBuilder.error("test-operation", e);

        assertTrue(result.isError());
        String content = result.contents().getFirst().text();
        assertTrue(content.contains("ERROR in test-operation: RuntimeException - Test exception message"));
    }

    @Test
    void testFormatKeyValue() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");
        data.put("count", 5);
        data.put("active", true);

        String formatted = McpResponseBuilder.formatKeyValue(data);

        assertTrue(formatted.contains("name: test"));
        assertTrue(formatted.contains("count: 5"));
        assertTrue(formatted.contains("active: true"));
    }

    @Test
    void testHeader() {
        String header = McpResponseBuilder.header("Test Operation");

        assertEquals("=== Test Operation ===\n", header);
    }
}