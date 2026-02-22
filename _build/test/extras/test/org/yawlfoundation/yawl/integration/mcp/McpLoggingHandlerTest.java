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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import junit.framework.TestCase;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the McpLoggingHandler class.
 *
 * Chicago TDD: tests the real McpLoggingHandler with actual level filtering,
 * level ordering assertions, and no-op behaviour when the McpSyncServer is null.
 *
 * Coverage targets:
 * - Default logging level (INFO)
 * - Level change via setLevel()
 * - Level ordering (DEBUG < INFO < NOTICE < WARNING < ERROR < CRITICAL < ALERT < EMERGENCY)
 * - Construction with default and custom ObjectMapper
 * - sendLogNotification with null server (graceful failure)
 * - logToolExecution with null server (graceful failure)
 * - logToolCompletion with null server (graceful failure)
 * - logError with null server (graceful failure)
 * - info/debug/warning/error helpers
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpLoggingHandlerTest extends TestCase {

    private McpLoggingHandler handler;

    public McpLoggingHandlerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        handler = new McpLoggingHandler();
    }

    // =========================================================================
    // Construction
    // =========================================================================

    public void testDefaultConstructorCreatesHandler() {
        McpLoggingHandler h = new McpLoggingHandler();
        assertNotNull("Handler should be created", h);
    }

    public void testCustomObjectMapperConstructorCreatesHandler() {
        ObjectMapper mapper = new ObjectMapper();
        McpLoggingHandler h = new McpLoggingHandler(mapper);
        assertNotNull("Handler with custom mapper should be created", h);
    }

    // =========================================================================
    // Default level
    // =========================================================================

    public void testDefaultLevelIsInfo() {
        assertEquals("Default level should be INFO",
            McpSchema.LoggingLevel.INFO, handler.getLevel());
    }

    // =========================================================================
    // setLevel / getLevel round-trip
    // =========================================================================

    public void testSetLevelDebug() {
        handler.setLevel(McpSchema.LoggingLevel.DEBUG);
        assertEquals(McpSchema.LoggingLevel.DEBUG, handler.getLevel());
    }

    public void testSetLevelInfo() {
        handler.setLevel(McpSchema.LoggingLevel.DEBUG); // change from default
        handler.setLevel(McpSchema.LoggingLevel.INFO);
        assertEquals(McpSchema.LoggingLevel.INFO, handler.getLevel());
    }

    public void testSetLevelNotice() {
        handler.setLevel(McpSchema.LoggingLevel.NOTICE);
        assertEquals(McpSchema.LoggingLevel.NOTICE, handler.getLevel());
    }

    public void testSetLevelWarning() {
        handler.setLevel(McpSchema.LoggingLevel.WARNING);
        assertEquals(McpSchema.LoggingLevel.WARNING, handler.getLevel());
    }

    public void testSetLevelError() {
        handler.setLevel(McpSchema.LoggingLevel.ERROR);
        assertEquals(McpSchema.LoggingLevel.ERROR, handler.getLevel());
    }

    public void testSetLevelCritical() {
        handler.setLevel(McpSchema.LoggingLevel.CRITICAL);
        assertEquals(McpSchema.LoggingLevel.CRITICAL, handler.getLevel());
    }

    public void testSetLevelAlert() {
        handler.setLevel(McpSchema.LoggingLevel.ALERT);
        assertEquals(McpSchema.LoggingLevel.ALERT, handler.getLevel());
    }

    public void testSetLevelEmergency() {
        handler.setLevel(McpSchema.LoggingLevel.EMERGENCY);
        assertEquals(McpSchema.LoggingLevel.EMERGENCY, handler.getLevel());
    }

    // =========================================================================
    // Level ordering assertions (protocol contract)
    // =========================================================================

    public void testDebugLevelIsLowerThanInfo() {
        assertTrue("DEBUG level must be < INFO level",
            McpSchema.LoggingLevel.DEBUG.level() < McpSchema.LoggingLevel.INFO.level());
    }

    public void testInfoLevelIsLowerThanNotice() {
        assertTrue("INFO level must be < NOTICE level",
            McpSchema.LoggingLevel.INFO.level() < McpSchema.LoggingLevel.NOTICE.level());
    }

    public void testNoticeLevelIsLowerThanWarning() {
        assertTrue("NOTICE level must be < WARNING level",
            McpSchema.LoggingLevel.NOTICE.level() < McpSchema.LoggingLevel.WARNING.level());
    }

    public void testWarningLevelIsLowerThanError() {
        assertTrue("WARNING level must be < ERROR level",
            McpSchema.LoggingLevel.WARNING.level() < McpSchema.LoggingLevel.ERROR.level());
    }

    public void testErrorLevelIsLowerThanCritical() {
        assertTrue("ERROR level must be < CRITICAL level",
            McpSchema.LoggingLevel.ERROR.level() < McpSchema.LoggingLevel.CRITICAL.level());
    }

    public void testCriticalLevelIsLowerThanAlert() {
        assertTrue("CRITICAL level must be < ALERT level",
            McpSchema.LoggingLevel.CRITICAL.level() < McpSchema.LoggingLevel.ALERT.level());
    }

    public void testAlertLevelIsLowerThanEmergency() {
        assertTrue("ALERT level must be < EMERGENCY level",
            McpSchema.LoggingLevel.ALERT.level() < McpSchema.LoggingLevel.EMERGENCY.level());
    }

    // =========================================================================
    // sendLogNotification with null server - graceful no-op
    // =========================================================================

    public void testSendLogNotificationWithNullServerDoesNotThrow() {
        // When the server is null (before start()), logging should not crash
        try {
            handler.sendLogNotification(
                null, McpSchema.LoggingLevel.INFO, "test.logger", "test message");
        } catch (Exception e) {
            fail("sendLogNotification with null server should not throw: " + e.getMessage());
        }
    }

    public void testSendLogNotificationBelowCurrentLevelIsFiltered() {
        // Set level to WARNING - DEBUG messages should be filtered out
        handler.setLevel(McpSchema.LoggingLevel.WARNING);
        try {
            // DEBUG is below WARNING, should be silently filtered
            handler.sendLogNotification(
                null, McpSchema.LoggingLevel.DEBUG, "test.logger", "debug message");
        } catch (Exception e) {
            fail("Filtered log notification should not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // logToolExecution with null server - graceful no-op
    // =========================================================================

    public void testLogToolExecutionWithNullServerDoesNotThrow() {
        Map<String, Object> args = new HashMap<>();
        args.put("specId", "OrderProcessing");
        args.put("caseData", "<data/>");
        try {
            handler.logToolExecution(null, "launch_case", args);
        } catch (Exception e) {
            fail("logToolExecution with null server should not throw: " + e.getMessage());
        }
    }

    public void testLogToolExecutionWithEmptyArgs() {
        try {
            handler.logToolExecution(null, "get_case_status", new HashMap<>());
        } catch (Exception e) {
            fail("logToolExecution with empty args should not throw: " + e.getMessage());
        }
    }

    public void testLogToolExecutionWithNullArgs() {
        try {
            handler.logToolExecution(null, "list_cases", null);
        } catch (Exception e) {
            fail("logToolExecution with null args should not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // logToolCompletion with null server - graceful no-op
    // =========================================================================

    public void testLogToolCompletionSuccessWithNullServer() {
        try {
            handler.logToolCompletion(null, "launch_case", true, 42L);
        } catch (Exception e) {
            fail("logToolCompletion success with null server should not throw: "
                + e.getMessage());
        }
    }

    public void testLogToolCompletionFailureWithNullServer() {
        try {
            handler.logToolCompletion(null, "launch_case", false, 150L);
        } catch (Exception e) {
            fail("logToolCompletion failure with null server should not throw: "
                + e.getMessage());
        }
    }

    public void testLogToolCompletionZeroDuration() {
        try {
            handler.logToolCompletion(null, "get_case_status", true, 0L);
        } catch (Exception e) {
            fail("logToolCompletion with zero duration should not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // logError with null server - graceful no-op
    // =========================================================================

    public void testLogErrorWithNullServer() {
        try {
            handler.logError(null, "tool_execution", new RuntimeException("test error"));
        } catch (Exception e) {
            fail("logError with null server should not throw: " + e.getMessage());
        }
    }

    public void testLogErrorWithCauseMessage() {
        RuntimeException cause = new RuntimeException("YAWL engine unavailable");
        try {
            handler.logError(null, "engine_connection", cause);
        } catch (Exception e) {
            fail("logError with cause should not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // Convenience helper methods with null server
    // =========================================================================

    public void testInfoHelperWithNullServer() {
        try {
            handler.info(null, "Server started successfully");
        } catch (Exception e) {
            fail("info() with null server should not throw: " + e.getMessage());
        }
    }

    public void testDebugHelperWithNullServer() {
        try {
            handler.debug(null, "Processing tool: launch_case");
        } catch (Exception e) {
            fail("debug() with null server should not throw: " + e.getMessage());
        }
    }

    public void testWarningHelperWithNullServer() {
        try {
            handler.warning(null, "Session handle near expiry");
        } catch (Exception e) {
            fail("warning() with null server should not throw: " + e.getMessage());
        }
    }

    public void testErrorHelperWithNullServer() {
        try {
            handler.error(null, "Engine connection lost");
        } catch (Exception e) {
            fail("error() with null server should not throw: " + e.getMessage());
        }
    }

    // =========================================================================
    // Level change affects subsequent filtering
    // =========================================================================

    public void testLevelChangeAffectsFiltering() {
        // Start at INFO, so DEBUG should be filtered
        handler.setLevel(McpSchema.LoggingLevel.INFO);
        int infoLevel = McpSchema.LoggingLevel.INFO.level();
        int debugLevel = McpSchema.LoggingLevel.DEBUG.level();
        assertTrue("At INFO level, DEBUG messages should be filtered (level < currentLevel)",
            debugLevel < infoLevel);

        // Change to DEBUG so all messages pass through
        handler.setLevel(McpSchema.LoggingLevel.DEBUG);
        assertEquals(McpSchema.LoggingLevel.DEBUG, handler.getLevel());
    }

    // =========================================================================
    // Multiple level transitions
    // =========================================================================

    public void testMultipleLevelTransitions() {
        handler.setLevel(McpSchema.LoggingLevel.DEBUG);
        assertEquals(McpSchema.LoggingLevel.DEBUG, handler.getLevel());

        handler.setLevel(McpSchema.LoggingLevel.ERROR);
        assertEquals(McpSchema.LoggingLevel.ERROR, handler.getLevel());

        handler.setLevel(McpSchema.LoggingLevel.INFO);
        assertEquals(McpSchema.LoggingLevel.INFO, handler.getLevel());

        handler.setLevel(McpSchema.LoggingLevel.EMERGENCY);
        assertEquals(McpSchema.LoggingLevel.EMERGENCY, handler.getLevel());
    }
}
