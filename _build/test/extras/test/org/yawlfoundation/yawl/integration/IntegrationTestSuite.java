/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration;

import org.junit.platform.suite.api.IncludeTags;
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
import org.yawlfoundation.yawl.integration.e2e.EndToEndWorkflowTest;
import org.yawlfoundation.yawl.integration.e2e.FullEngineLifecycleTest;
import org.yawlfoundation.yawl.integration.e2e.UserJourneyTest;
import org.yawlfoundation.yawl.integration.external.ExternalServiceIntegrationTest;
import org.yawlfoundation.yawl.integration.performance.LoadIntegrationTest;
import org.yawlfoundation.yawl.integration.persistence.DatabaseIntegrationTest;
import org.yawlfoundation.yawl.integration.persistence.SchemaMigrationTest;
import org.yawlfoundation.yawl.integration.failure.FailureScenarioTest;
import org.yawlfoundation.yawl.integration.failure.NetworkPartitionTest;
import org.yawlfoundation.yawl.integration.api.ApiContractTest;
import org.yawlfoundation.yawl.integration.mcp.McpLoggingHandlerTest;
import org.yawlfoundation.yawl.integration.mcp.McpPerformanceTest;
import org.yawlfoundation.yawl.integration.mcp.McpProtocolTest;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServerTest;

/**
 * Master Integration Test Suite for YAWL V6.
 *
 * Aggregates all integration tests across the following categories:
 *
 * END-TO-END WORKFLOW TESTS:
 *   - EndToEndWorkflowTest: Complete workflow execution from spec load to completion
 *   - FullEngineLifecycleTest: Engine initialization, case execution, shutdown
 *   - UserJourneyTest: Real-world user scenarios (admin, participant, observer)
 *
 * API CONTRACT TESTS:
 *   - ApiContractTest: Interface B client API validation, request/response schemas
 *
 * DATABASE INTEGRATION TESTS:
 *   - DatabaseIntegrationTest: H2/PostgreSQL/MySQL CRUD, transactions, constraints
 *   - SchemaMigrationTest: Schema version migrations, backward compatibility
 *
 * EXTERNAL SERVICE INTEGRATION TESTS:
 *   - ExternalServiceIntegrationTest: HTTP client, timeout handling, retry logic
 *
 * PERFORMANCE UNDER LOAD TESTS:
 *   - LoadIntegrationTest: Concurrent cases, throughput benchmarks, latency SLAs
 *
 * FAILURE SCENARIO TESTS:
 *   - FailureScenarioTest: Exception handling, graceful degradation
 *   - NetworkPartitionTest: Network failure simulation, recovery behavior
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
 * Target: 80%+ coverage on all V6 integration features.
 * Methodology: Chicago TDD - real objects, real I/O, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Suite
@SuiteDisplayName("YAWL V6 Complete Integration Test Suite")
@IncludeTags("integration")
@SelectClasses({
    // End-to-end workflow tests
    EndToEndWorkflowTest.class,
    FullEngineLifecycleTest.class,
    UserJourneyTest.class,

    // API contract tests
    ApiContractTest.class,

    // Database integration tests
    DatabaseIntegrationTest.class,
    SchemaMigrationTest.class,

    // External service integration tests
    ExternalServiceIntegrationTest.class,

    // Performance under load tests
    LoadIntegrationTest.class,

    // Failure scenario tests
    FailureScenarioTest.class,
    NetworkPartitionTest.class,

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
})
public class IntegrationTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
