/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration;

import java.time.Duration;

/**
 * Shared test constants for integration tests.
 *
 * <p>Centralizes all test-related constants to ensure consistency across
 * test suites and enable easy configuration changes.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class TestConstants {

    private TestConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ============ Database Configuration ============

    /** H2 in-memory database URL for testing. */
    public static final String H2_MEM_URL = "jdbc:h2:mem:yawl_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL";

    /** H2 database driver class. */
    public static final String H2_DRIVER = "org.h2.Driver";

    /** H2 database username for tests. */
    public static final String H2_USERNAME = "sa";

    /** H2 database password for tests. */
    public static final String H2_PASSWORD = "";

    // ============ Engine Configuration ============

    /** Default engine host for testing. */
    public static final String DEFAULT_ENGINE_HOST = "localhost";

    /** Default engine HTTP port for testing. */
    public static final int DEFAULT_ENGINE_HTTP_PORT = 8080;

    /** Default engine HTTPS port for testing. */
    public static final int DEFAULT_ENGINE_HTTPS_PORT = 8443;

    /** Default engine context path. */
    public static final String DEFAULT_ENGINE_CONTEXT = "/yawl";

    /** Default session handle for testing. */
    public static final String DEFAULT_SESSION_HANDLE = "test-session-handle-12345678";

    // ============ Test Data Identifiers ============

    /** Default test specification identifier. */
    public static final String DEFAULT_SPEC_ID = "TestWorkflow";

    /** Default test specification URI. */
    public static final String DEFAULT_SPEC_URI = "http://yawlfoundation.org/test/workflow";

    /** Default test specification version. */
    public static final String DEFAULT_SPEC_VERSION = "1.0.0";

    /** Default test case identifier. */
    public static final String DEFAULT_CASE_ID = "test-case-default";

    /** Default test work item identifier. */
    public static final String DEFAULT_WORK_ITEM_ID = "WI-TEST001";

    /** Default test task identifier. */
    public static final String DEFAULT_TASK_ID = "TestTask";

    /** Default test net identifier. */
    public static final String DEFAULT_NET_ID = "TestNet";

    // ============ A2A Protocol Constants ============

    /** Default source agent identifier. */
    public static final String DEFAULT_SOURCE_AGENT = "agent-source";

    /** Default target agent identifier. */
    public static final String DEFAULT_TARGET_AGENT = "agent-target";

    /** A2A handoff protocol prefix. */
    public static final String A2A_HANDOFF_PREFIX = "YAWL_HANDOFF:";

    /** Default A2A protocol version. */
    public static final String A2A_PROTOCOL_VERSION = "1.0.0";

    /** Default JWT secret for A2A testing (NOT for production use). */
    public static final String TEST_JWT_SECRET = "test-jwt-secret-key-for-integration-tests-32-characters-minimum";

    /** Default handoff TTL duration. */
    public static final Duration DEFAULT_HANDOFF_TTL = Duration.ofMinutes(5);

    // ============ MCP Protocol Constants ============

    /** Default MCP server URI for testing. */
    public static final String DEFAULT_MCP_SERVER_URI = "http://localhost:8943";

    /** MCP protocol version. */
    public static final String MCP_PROTOCOL_VERSION = "2024-11-05";

    /** JSON-RPC version. */
    public static final String JSON_RPC_VERSION = "2.0";

    /** Default MCP tool call timeout. */
    public static final Duration DEFAULT_MCP_TIMEOUT = Duration.ofSeconds(30);

    // ============ Timeout Configuration ============

    /** Default timeout for synchronous operations. */
    public static final Duration DEFAULT_SYNC_TIMEOUT = Duration.ofSeconds(10);

    /** Default timeout for async operations. */
    public static final Duration DEFAULT_ASYNC_TIMEOUT = Duration.ofSeconds(30);

    /** Default timeout for database operations. */
    public static final Duration DEFAULT_DB_TIMEOUT = Duration.ofSeconds(5);

    /** Default timeout for network operations. */
    public static final Duration DEFAULT_NETWORK_TIMEOUT = Duration.ofSeconds(15);

    /** Polling interval for async assertions. */
    public static final Duration POLLING_INTERVAL = Duration.ofMillis(100);

    // ============ Test Data Sizes ============

    /** Number of test cases for load testing. */
    public static final int LOAD_TEST_CASE_COUNT = 100;

    /** Number of test work items for batch operations. */
    public static final int BATCH_WORK_ITEM_COUNT = 50;

    /** Number of concurrent agents for multi-agent tests. */
    public static final int CONCURRENT_AGENT_COUNT = 4;

    /** Maximum payload size for testing (in characters). */
    public static final int MAX_PAYLOAD_SIZE = 10_000;

    // ============ Priority Values ============

    /** Low priority value. */
    public static final String PRIORITY_LOW = "low";

    /** Normal priority value. */
    public static final String PRIORITY_NORMAL = "normal";

    /** High priority value. */
    public static final String PRIORITY_HIGH = "high";

    /** Critical priority value. */
    public static final String PRIORITY_CRITICAL = "critical";

    // ============ Valid ID Patterns ============

    /** Work item ID pattern for validation. */
    public static final String WORK_ITEM_ID_PATTERN = "^WI-[A-Za-z0-9]{1,10}$";

    /** Agent ID pattern for validation. */
    public static final String AGENT_ID_PATTERN = "^[a-zA-Z0-9][a-zA-Z0-9_-]{0,63}$";

    /** Case ID pattern for validation. */
    public static final String CASE_ID_PATTERN = "^[a-zA-Z0-9_-]{8,32}$";

    /** Session handle pattern for validation. */
    public static final String SESSION_HANDLE_PATTERN = "^[a-zA-Z0-9]{8,64}$";

    // ============ JSON Test Templates ============

    /** Minimal valid A2A handoff message template. */
    public static final String MINIMAL_HANDOFF_TEMPLATE = """
        {
          "parts": [
            {
              "type": "text",
              "text": "YAWL_HANDOFF:%s:%s:%s"
            },
            {
              "type": "data",
              "data": {
                "toAgent": "%s",
                "reason": "test"
              }
            }
          ]
        }
        """;

    /** Minimal valid MCP tool call template. */
    public static final String MINIMAL_MCP_TOOL_CALL_TEMPLATE = """
        {
          "jsonrpc": "2.0",
          "id": "%s",
          "method": "%s",
          "params": %s
        }
        """;

    /** Minimal valid agent card template. */
    public static final String MINIMAL_AGENT_CARD_TEMPLATE = """
        {
          "name": "%s",
          "version": "1.0.0",
          "protocols": [
            {
              "name": "a2a",
              "version": "1.0",
              "url": "http://localhost:8080"
            }
          ],
          "skills": [
            {
              "name": "test",
              "description": "Test skill"
            }
          ]
        }
        """;
}
