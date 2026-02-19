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

/**
 * Comprehensive integration tests for the YAWL MCP-A2A MVP.
 *
 * This package contains integration tests that validate the complete
 * Model Context Protocol (MCP) and Agent-to-Agent (A2A) integration
 * with the YAWL workflow engine.
 *
 * <h2>Test Categories</h2>
 *
 * <h3>End-to-End Workflow Tests</h3>
 * Complete workflow execution from MCP client through YAWL engine to A2A server:
 * <ul>
 *   <li>Complete workflow: MCP client to YAWL engine to A2A server to work item completion</li>
 *   <li>Complex workflow with multiple services and handoffs</li>
 *   <li>Error propagation and recovery across services</li>
 *   <li>Load testing with realistic workloads</li>
 * </ul>
 *
 * <h3>Cross-Service Communication Tests</h3>
 * Service discovery, load balancing, and resilience:
 * <ul>
 *   <li>Service discovery and registration</li>
 *   <li>Load balancing effectiveness</li>
 *   <li>Circuit breaker isolation</li>
 *   <li>Timeout handling and retry logic</li>
 * </ul>
 *
 * <h3>State Management Tests</h3>
 * Consistency and recovery of state across services:
 * <ul>
 *   <li>Session state consistency across services</li>
 *   <li>Cache invalidation and synchronization</li>
 *   <li>State recovery after service restarts</li>
 *   <li>Concurrent state modifications</li>
 * </ul>
 *
 * <h3>Data Flow Validation Tests</h3>
 * Message integrity and schema compliance:
 * <ul>
 *   <li>Message integrity across services</li>
 *   <li>Data transformation validation</li>
 *   <li>Schema compliance</li>
 *   <li>Large payload handling</li>
 * </ul>
 *
 * <h3>Real-World Scenarios Tests</h3>
 * Production-like traffic patterns:
 * <ul>
 *   <li>Production-like traffic patterns</li>
 *   <li>Peak load testing</li>
 *   <li>Failover scenarios</li>
 *   <li>Graceful degradation</li>
 * </ul>
 *
 * <h3>Compatibility Tests</h3>
 * Protocol version and cross-orchestrator compatibility:
 * <ul>
 *   <li>Multiple A2A protocol versions</li>
 *   <li>Cross-orchestrator workflows</li>
 *   <li>Schema evolution compatibility</li>
 * </ul>
 *
 * <h2>Chicago TDD Methodology</h2>
 * All tests follow Chicago/Detroit TDD principles:
 * <ul>
 *   <li>Real YAWL engine objects (YSpecification, YNetRunner, YWorkItem)</li>
 *   <li>Real HTTP servers (YawlA2AServer, AgentRegistry)</li>
 *   <li>Real database connections (H2 in-memory for tests)</li>
 *   <li>Real JWT tokens and authentication</li>
 *   <li>No mocks, stubs, or fake implementations</li>
 * </ul>
 *
 * <h2>Running Tests</h2>
 *
 * <h3>Run all MCP-A2A MVP tests:</h3>
 * <pre>
 * mvn test -Dtest=McpA2AMvpTestSuite
 * </pre>
 *
 * <h3>Run specific test class:</h3>
 * <pre>
 * mvn test -Dtest=McpA2AMvpIntegrationTest
 * mvn test -Dtest=CrossServiceHandoffTest
 * mvn test -Dtest=ServiceDiscoveryIntegrationTest
 * mvn test -Dtest=WorkflowOrchestrationTest
 * </pre>
 *
 * <h3>Run by tags:</h3>
 * <pre>
 * mvn test -Dgroups=integration
 * mvn test -Dgroups=mvp
 * mvn test -Dgroups=handoff
 * mvn test -Dgroups=discovery
 * mvn test -Dgroups=orchestration
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-19
 * @see org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
 * @see org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
 * @see org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry
 */
package org.yawlfoundation.yawl.integration.mcp_a2a;
