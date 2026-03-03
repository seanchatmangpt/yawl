/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.mcp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive MCP server integration test suite with metrics collection.
 *
 * <p>This test validates the full MCP server functionality including:
 * - Server startup and shutdown
 * - Transport layer (STDIO/HTTP)
 * - YAWL engine connectivity
 * - Tool execution metrics
 * - Performance benchmarks
 *
 * <p><strong>NOTE:</strong> Disabled until MCP SDK 1.0.0-RC3 dependencies are resolved.
 * The YawlMcpHttpServer class is excluded from compilation due to dependency issues.
 * See pom.xml compiler excludes for details.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Disabled("MCP SDK 1.0.0-RC3: YawlMcpHttpServer depends on excluded YawlToolSpecifications. " +
          "See pom.xml compiler excludes: **/mcp/a2a/mcp/*.java")
class McpServerIntegrationTest {

    @Test
    @Disabled("Placeholder - YawlMcpHttpServer not available in classpath")
    void placeholder() {
        // This test is disabled because YawlMcpHttpServer is excluded from compilation.
        // The actual MCP server integration tests will be enabled once the MCP SDK
        // dependencies are resolved and the class is included in the build.
        //
        // Original test coverage:
        // - Transport Layer Validation (STDIO/HTTP)
        // - MCP Tool Testing (YAML conversion, soundness verification)
        // - Performance Benchmarking (throughput, memory usage)
        // - Error Handling and Resilience
        // - Metrics Collection
    }
}
