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

package org.yawlfoundation.yawl.integration.mcp.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for McpLogger utility class.
 */
class McpLoggerTest {

    @Test
    void logInfo_doesNotThrow() {
        // Test that logging methods don't throw exceptions
        assertDoesNotThrow(() -> McpLogger.logInfo("Test MCP info message"));
        assertDoesNotThrow(() -> McpLogger.logInfo("Test with object", "some object"));
        assertDoesNotThrow(() -> McpLogger.logInfo("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logDebug_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logDebug("Test MCP debug message"));
        assertDoesNotThrow(() -> McpLogger.logDebug("Test with object", "some object"));
        assertDoesNotThrow(() -> McpLogger.logDebug("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logError_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logError("Test MCP error message"));
        assertDoesNotThrow(() -> McpLogger.logError("Test with object", "some object"));
        assertDoesNotThrow(() -> McpLogger.logError("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logWarn_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logWarn("Test MCP warning message"));
        assertDoesNotThrow(() -> McpLogger.logWarn("Test with object", "some object"));
        assertDoesNotThrow(() -> McpLogger.logWarn("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logTrace_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logTrace("Test MCP trace message"));
        assertDoesNotThrow(() -> McpLogger.logTrace("Test with object", "some object"));
        assertDoesNotThrow(() -> McpLogger.logTrace("Test with multiple objects", "obj1", "obj2"));
    }

    @Test
    void logMcpInfo_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Test MCP specific info"));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Info with object", "test object"));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Info with multiple objects", "obj1", 42, true));
    }

    @Test
    void logMcpError_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logMcpError("Test MCP specific error"));
        assertDoesNotThrow(() -> McpLogger.logMcpError("Error with object", "test object"));
        assertDoesNotThrow(() -> McpLogger.logMcpError("Error with multiple objects", "obj1", 42, true));
    }

    @Test
    void logInvocation_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logInvocation("test_method", "param1=value1,param2=value2"));
        assertDoesNotThrow(() -> McpLogger.logInvocation("test_method"));
        assertDoesNotThrow(() -> McpLogger.logInvocation("complex_method", "param1=value1,param2=value2,param3=value3"));
    }

    @Test
    void logSuccess_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logSuccess("test_method", "Operation completed successfully"));
        assertDoesNotThrow(() -> McpLogger.logSuccess("test_method"));
        assertDoesNotThrow(() -> McpLogger.logSuccess("complex_method", "Complex operation completed with result"));
    }

    @Test
    void logError_withParameters_doesNotThrow() {
        assertDoesNotThrow(() -> McpLogger.logError("test_method", "Test error message"));
        assertDoesNotThrow(() -> McpLogger.logError("test_method", "Error with details", new RuntimeException("Test exception")));
        assertDoesNotThrow(() -> McpLogger.logError("complex_method", "Complex operation failed", "Additional error context"));
    }

    @Test
    void logInvocation_withEmptyParams() {
        assertDoesNotThrow(() -> McpLogger.logInvocation("method_with_no_params", ""));
        assertDoesNotThrow(() -> McpLogger.logInvocation("method_with_no_params"));
    }

    @Test
    void logInvocation_withComplexParams() {
        String complexParams = "id=123,name=test,param=value,flag=true,num=3.14";
        assertDoesNotThrow(() -> McpLogger.logInvocation("complex_method", complexParams));
    }

    @Test
    void logSuccess_withResult() {
        assertDoesNotThrow(() -> McpLogger.logSuccess("method", "Result: success_data"));
        assertDoesNotThrow(() -> McpLogger.logSuccess("method", ""));
        assertDoesNotThrow(() -> McpLogger.logSuccess("method"));
    }

    @Test
    void logError_withException() {
        Exception exception = new RuntimeException("Test runtime exception");
        assertDoesNotThrow(() -> McpLogger.logError("method", "Error occurred", exception));
        assertDoesNotThrow(() -> McpLogger.logError("method", "Error occurred", new Throwable("Test throwable")));
    }

    @Test
    void logInfo_withVariousObjects() {
        // Test with different object types
        assertDoesNotThrow(() -> McpLogger.logInfo("String test"));
        assertDoesNotThrow(() -> McpLogger.logInfo("Number test", 42));
        assertDoesNotThrow(() -> McpLogger.logInfo("Boolean test", true));
        assertDoesNotThrow(() -> McpLogger.logInfo("Null test", null));
        assertDoesNotThrow(() -> McpLogger.logInfo("Array test", new int[]{1, 2, 3}));
    }

    @Test
    void logMcpInfo_withVariousObjects() {
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("String test"));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Number test", 3.14));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Boolean test", false));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("Object test", new Object()));
    }

    @Test
    void logAllMcpSpecificMethods() {
        // Test all MCP-specific logging methods
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("MCP info message"));
        assertDoesNotThrow(() -> McpLogger.logMcpError("MCP error message"));
        assertDoesNotThrow(() -> McpLogger.logInvocation("test_method", "param=value"));
        assertDoesNotThrow(() -> McpLogger.logSuccess("test_method", "Success"));
        assertDoesNotThrow(() -> McpLogger.logError("test_method", "Error"));
    }

    @Test
    void logWithMultipleParameters() {
        // Test methods with multiple parameters
        assertDoesNotThrow(() -> McpLogger.logInfo("Message with params", 1, "two", true));
        assertDoesNotThrow(() -> McpLogger.logDebug("Debug with params", new Object(), null, "string"));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("MCP with params", 42, 3.14, 'c'));
    }

    @Test
    void logInvocation_methodNameOnly() {
        // Test with just method name (no parameters)
        assertDoesNotThrow(() -> McpLogger.logInvocation("simple_method"));
    }

    @Test
    void logSuccess_methodNameOnly() {
        // Test with just method name (no success message)
        assertDoesNotThrow(() -> McpLogger.logSuccess("simple_method"));
    }

    @Test
    void logError_noException() {
        // Test logError without exception
        assertDoesNotThrow(() -> McpLogger.logError("method", "Simple error message"));
        assertDoesNotThrow(() -> McpLogger.logError("method"));
    }

    @Test
    void allLogMethods_noExceptions_comprehensive() {
        // Comprehensive test that all logging methods work without exceptions
        assertDoesNotThrow(() -> McpLogger.logInfo("Info test"));
        assertDoesNotThrow(() -> McpLogger.logDebug("Debug test"));
        assertDoesNotThrow(() -> McpLogger.logError("Error test"));
        assertDoesNotThrow(() -> McpLogger.logWarn("Warn test"));
        assertDoesNotThrow(() -> McpLogger.logTrace("Trace test"));
        assertDoesNotThrow(() -> McpLogger.logMcpInfo("MCP Info test"));
        assertDoesNotThrow(() -> McpLogger.logMcpError("MCP Error test"));
        assertDoesNotThrow(() -> McpLogger.logInvocation("test_method"));
        assertDoesNotThrow(() -> McpLogger.logSuccess("test_method"));
    }
}