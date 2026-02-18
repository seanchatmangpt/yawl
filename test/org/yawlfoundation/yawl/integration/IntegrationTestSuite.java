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

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.integration.a2a.A2AAuthenticationTest;
import org.yawlfoundation.yawl.integration.a2a.A2AClientTest;
import org.yawlfoundation.yawl.integration.a2a.A2AProtocolTest;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServerTest;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapabilityTest;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfigurationTest;
import org.yawlfoundation.yawl.integration.autonomous.AgentRegistryTest;
import org.yawlfoundation.yawl.integration.autonomous.CircuitBreakerTest;
import org.yawlfoundation.yawl.integration.autonomous.RetryPolicyTest;
import org.yawlfoundation.yawl.integration.autonomous.StaticMappingReasonerTest;
import org.yawlfoundation.yawl.integration.mcp.McpLoggingHandlerTest;
import org.yawlfoundation.yawl.integration.mcp.McpPerformanceTest;
import org.yawlfoundation.yawl.integration.mcp.McpProtocolTest;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServerTest;
import org.yawlfoundation.yawl.integration.V6EndToEndIntegrationTest;

/**
 * Master Integration Test Suite for YAWL V6.
 *
 * Aggregates all integration tests:
 *
 * AUTONOMOUS AGENT TESTS:
 *   - AgentCapability record: construction, validation, equality, environment parsing
 *   - AgentConfiguration builder: required fields, defaults, guard conditions
 *   - StaticMappingReasoner: direct mapping, wildcards, file loading
 *   - AgentRegistry: registration, heartbeat, capability discovery
 *   - CircuitBreaker: state machine (CLOSED/OPEN/HALF_OPEN), concurrency
 *   - RetryPolicy: exponential backoff, transient failure recovery
 *
 * MCP SERVER TESTS:
 *   - YawlMcpServer: constructor validation, lifecycle, failed connection handling
 *   - McpProtocolTest: server capabilities, resource URI formats, logging levels
 *   - McpLoggingHandlerTest: level filtering, null-server handling, helper methods
 *   - McpPerformanceTest: construction latency, throughput, concurrent access
 *
 * A2A SERVER TESTS:
 *   - YawlA2AServer: constructor validation, HTTP lifecycle, agent card endpoint
 *   - A2AAuthenticationTest: AuthenticatedPrincipal, ApiKey, JWT, Composite providers
 *   - A2AProtocolTest: HTTP transport layer, auth enforcement, agent card format
 *   - A2AClientTest: client construction guards, close idempotency
 *
 * SPIFFE IDENTITY TESTS: See SpiffeExceptionTest, SpiffeWorkloadIdentityTest
 *   (package-private JUnit 5 classes, included in ExcludedModulesTestSuite)
 *
 * Target: 80%+ coverage on all V6 integration features.
 * Methodology: Chicago TDD - real objects, real I/O, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Suite
@SuiteDisplayName("YAWL V6 Integration Test Suite")
@SelectClasses({
    // Autonomous agent tests
    AgentCapabilityTest.class,
    AgentConfigurationTest.class,
    StaticMappingReasonerTest.class,
    AgentRegistryTest.class,
    CircuitBreakerTest.class,
    RetryPolicyTest.class,

    // MCP server tests
    YawlMcpServerTest.class,
    McpProtocolTest.class,
    McpLoggingHandlerTest.class,
    McpPerformanceTest.class,

    // A2A server tests
    YawlA2AServerTest.class,
    A2AAuthenticationTest.class,
    A2AProtocolTest.class,
    A2AClientTest.class,

    // V6 end-to-end integration tests
    V6EndToEndIntegrationTest.class
    // Note: SpiffeExceptionTest and SpiffeWorkloadIdentityTest are package-private
    // JUnit 5 classes and are picked up automatically by JUnit platform scanning
})
public class IntegrationTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
