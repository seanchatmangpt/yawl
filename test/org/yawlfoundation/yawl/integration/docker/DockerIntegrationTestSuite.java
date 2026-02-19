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

package org.yawlfoundation.yawl.integration.docker;

import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive Docker Integration Test Suite for YAWL v6.0.0.
 *
 * Tests all Docker-deployed components:
 * - Spring Boot application health and endpoints
 * - MCP tool availability and functionality
 * - A2A agent capabilities
 * - Handoff protocol implementation
 * - Authentication and authorization
 * - Performance of endpoints
 * - Error handling verification
 *
 * Run via:
 * - Maven: mvn test -Dtest=DockerIntegrationTestSuite -Dgroups="docker"
 * - JUnit Console: java -jar junit-platform-console-standalone.jar
 *       --select-class org.yawlfoundation.yawl.integration.docker.DockerIntegrationTestSuite
 *
 * Prerequisites:
 * - Docker container running with YAWL Spring Boot application
 * - MCP server available on STDIO or HTTP transport
 * - A2A server running on configured port
 * - Test database (H2 in-memory) for isolation
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 */
@Suite
@SuiteDisplayName("Docker Integration Test Suite")
@IncludeTags({"integration", "docker"})
@SelectClasses({
    SpringBootHealthEndpointTest.class,
    McpToolIntegrationTest.class,
    A2AAgentCapabilityTest.class,
    HandoffProtocolIntegrationTest.class,
    AuthenticationAuthorizationTest.class,
    EndpointPerformanceTest.class,
    ErrorHandlingVerificationTest.class
})
public class DockerIntegrationTestSuite {
    // JUnit 5 suite uses annotations
}
