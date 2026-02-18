/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.mcp;

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;

/**
 * Integration tests for YawlMcpServer (V6 feature).
 *
 * Chicago TDD: tests real YawlMcpServer construction and guard conditions
 * without requiring a live YAWL engine.
 *
 * Note: The full start() method requires a real YAWL engine connection, so
 * those tests are separated from construction-only tests.
 *
 * Coverage targets:
 * - Constructor validation (null/empty params)
 * - isRunning() before/after start
 * - Guard conditions on constructor params
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("unit")
public class YawlMcpServerTest extends TestCase {

    public YawlMcpServerTest(String name) {
        super(name);
    }

    // =========================================================================
    // Constructor tests
    // =========================================================================

    public void testConstructorWithValidParameters() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNotNull("Server should be constructed successfully", server);
    }

    public void testConstructorWithNullEngineUrlThrows() {
        try {
            new YawlMcpServer(null, "admin", "YAWL");
            fail("Expected IllegalArgumentException for null engineUrl");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention engine URL",
                    e.getMessage().contains("URL") ||
                    e.getMessage().contains("url") ||
                    e.getMessage().contains("engine"));
        }
    }

    public void testConstructorWithEmptyEngineUrlThrows() {
        try {
            new YawlMcpServer("", "admin", "YAWL");
            fail("Expected IllegalArgumentException for empty engineUrl");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithNullUsernameThrows() {
        try {
            new YawlMcpServer("http://localhost:8080/yawl", null, "YAWL");
            fail("Expected IllegalArgumentException for null username");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention username",
                    e.getMessage().toLowerCase().contains("username") ||
                    e.getMessage().toLowerCase().contains("user"));
        }
    }

    public void testConstructorWithEmptyUsernameThrows() {
        try {
            new YawlMcpServer("http://localhost:8080/yawl", "", "YAWL");
            fail("Expected IllegalArgumentException for empty username");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testConstructorWithNullPasswordThrows() {
        try {
            new YawlMcpServer("http://localhost:8080/yawl", "admin", null);
            fail("Expected IllegalArgumentException for null password");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention password",
                    e.getMessage().toLowerCase().contains("password") ||
                    e.getMessage().toLowerCase().contains("pass"));
        }
    }

    public void testConstructorWithEmptyPasswordThrows() {
        try {
            new YawlMcpServer("http://localhost:8080/yawl", "admin", "");
            fail("Expected IllegalArgumentException for empty password");
        } catch (IllegalArgumentException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // State tests (before start)
    // =========================================================================

    public void testIsNotRunningBeforeStart() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertFalse("Server should not be running before start()", server.isRunning());
    }

    public void testGetMcpServerIsNullBeforeStart() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNull("McpServer should be null before start()", server.getMcpServer());
    }

    public void testGetLoggingHandlerIsNotNull() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNotNull("Logging handler should be initialized in constructor",
                server.getLoggingHandler());
    }

    // =========================================================================
    // stop() behavior before start
    // =========================================================================

    public void testStopBeforeStartIsNoOp() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        // Should not throw - stop on an unstarted server is a no-op
        try {
            server.stop();
        } catch (Exception e) {
            fail("stop() before start() should not throw: " + e.getMessage());
        }
        assertFalse("Server should still not be running after stop()", server.isRunning());
    }

    // =========================================================================
    // start() without live engine - test connection failure handling
    // =========================================================================

    public void testStartFailsWithInvalidEngineUrl() {
        // Use a URL that will immediately fail connection (no server running there)
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:19999/yawl", "admin", "YAWL");

        try {
            server.start();
            // If start succeeds, something unexpected happened
            server.stop();
            fail("Expected IOException for invalid engine URL");
        } catch (java.io.IOException e) {
            assertNotNull("Should have IOException message", e.getMessage());
            assertFalse("Server should not be running after failed start",
                    server.isRunning());
        }
    }

    // =========================================================================
    // Multiple construction with different URLs
    // =========================================================================

    public void testMultipleServerInstancesCanBeConstructed() {
        YawlMcpServer server1 = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        YawlMcpServer server2 = new YawlMcpServer(
            "http://localhost:8081/yawl", "admin2", "pass2");

        assertNotNull(server1);
        assertNotNull(server2);
        assertFalse(server1.isRunning());
        assertFalse(server2.isRunning());
    }

    // =========================================================================
    // Engine URL variations
    // =========================================================================

    public void testConstructorAcceptsUrlWithTrailingSlash() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl/", "admin", "YAWL");
        assertNotNull(server);
    }

    public void testConstructorAcceptsHttpsUrl() {
        YawlMcpServer server = new YawlMcpServer(
            "https://yawl.example.com/yawl", "admin", "YAWL");
        assertNotNull(server);
    }
}
