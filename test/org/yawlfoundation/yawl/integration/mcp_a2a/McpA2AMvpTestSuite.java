/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp_a2a;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive MCP-A2A MVP Test Suite.
 *
 * Aggregates all integration tests for the YAWL MCP-A2A MVP including:
 * - End-to-end workflow tests
 * - Cross-service communication tests
 * - State management tests
 * - Data flow validation tests
 * - Real-world scenario tests
 * - Compatibility tests
 *
 * Run via:
 * - Maven: mvn test -Dtest=McpA2AMvpTestSuite
 * - JUnit Console: java -jar junit-platform-console-standalone.jar
 *       --select-class org.yawlfoundation.yawl.integration.mcp_a2a.McpA2AMvpTestSuite
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Suite
@SuiteDisplayName("MCP-A2A MVP Integration Test Suite")
@SelectClasses({
    McpA2AMvpIntegrationTest.class,
    CrossServiceHandoffTest.class,
    ServiceDiscoveryIntegrationTest.class,
    WorkflowOrchestrationTest.class
})
public class McpA2AMvpTestSuite {
    // JUnit 5 suite uses annotations
}
