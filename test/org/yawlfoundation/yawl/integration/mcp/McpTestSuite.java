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

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * MCP test suite aggregating all Model Context Protocol tests.
 *
 * Tests SDK v1 (0.18.0+) compatibility implementing MCP 2025-11-25 specification.
 *
 * Includes:
 * - YawlMcpServerTest: constructor validation, lifecycle, failed connection
 * - McpProtocolTest: server capabilities, resource URIs, logging levels, error handling
 * - McpLoggingHandlerTest: logging level filtering, notifications, helper methods
 * - McpPerformanceTest: construction latency, throughput, concurrent access
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class McpTestSuite {

    private McpTestSuite() {
        throw new UnsupportedOperationException("McpTestSuite is a suite runner, not instantiable");
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL MCP Protocol Tests");
        suite.addTestSuite(YawlMcpServerTest.class);
        suite.addTestSuite(McpProtocolTest.class);
        suite.addTestSuite(McpLoggingHandlerTest.class);
        suite.addTestSuite(McpPerformanceTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
