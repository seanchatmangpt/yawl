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

package org.yawlfoundation.yawl.integration;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.integration.a2a.A2AAuthenticationTest;
import org.yawlfoundation.yawl.integration.a2a.A2AClientTest;
import org.yawlfoundation.yawl.integration.a2a.A2AProtocolTest;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServerTest;
import org.yawlfoundation.yawl.integration.mcp.McpLoggingHandlerTest;
import org.yawlfoundation.yawl.integration.mcp.McpPerformanceTest;
import org.yawlfoundation.yawl.integration.mcp.McpProtocolTest;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServerTest;

/**
 * Master CI/CD integration test suite for MCP and A2A protocols.
 *
 * This suite aggregates all MCP and A2A tests for execution in CI/CD pipelines.
 * It uses real server instances, real HTTP connections, and real authentication
 * providers. No mocks. Chicago TDD methodology throughout.
 *
 * Test categories:
 *
 * MCP PROTOCOL TESTS:
 *   - YawlMcpServerTest: constructor validation, lifecycle, failed connection handling
 *   - McpProtocolTest: server capabilities, resource URI formats, logging levels
 *   - McpLoggingHandlerTest: level filtering, notifications, helper methods
 *   - McpPerformanceTest: construction latency, concurrent access, throughput
 *
 * A2A PROTOCOL TESTS:
 *   - YawlA2AServerTest: constructor validation, HTTP lifecycle, agent card endpoint
 *   - A2AAuthenticationTest: AuthenticatedPrincipal, API key, JWT, composite providers
 *   - A2AProtocolTest: full HTTP transport with real server - agent card, auth, skills
 *   - A2AClientTest: client construction guards, close idempotency
 *
 * CI/CD execution:
 *   mvn -T 1.5C clean test -Dtest=McpA2AIntegrationSuite
 *
 *   Or via JUnit runner:
 *   java -cp classes:lib/* junit.textui.TestRunner \
 *     org.yawlfoundation.yawl.integration.McpA2AIntegrationSuite
 *
 * Coverage targets: 80%+ line coverage on all MCP and A2A integration code.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpA2AIntegrationSuite {

    private McpA2AIntegrationSuite() {
        throw new UnsupportedOperationException(
            "McpA2AIntegrationSuite is a test runner, not instantiable");
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL MCP and A2A CI/CD Integration Tests");

        // ----- MCP protocol tests -----
        suite.addTestSuite(YawlMcpServerTest.class);
        suite.addTestSuite(McpProtocolTest.class);
        suite.addTestSuite(McpLoggingHandlerTest.class);
        suite.addTestSuite(McpPerformanceTest.class);

        // ----- A2A protocol tests -----
        suite.addTestSuite(YawlA2AServerTest.class);
        suite.addTestSuite(A2AAuthenticationTest.class);
        suite.addTestSuite(A2AProtocolTest.class);
        suite.addTestSuite(A2AClientTest.class);

        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
