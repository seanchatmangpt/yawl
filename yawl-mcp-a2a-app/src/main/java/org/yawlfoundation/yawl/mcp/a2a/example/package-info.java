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

/**
 * A2A (Agent-to-Agent) protocol examples demonstrating client and server usage.
 *
 * <h2>Overview</h2>
 * <p>This package contains example implementations showing how to use the A2A Java SDK
 * for agent-to-agent communication. The examples demonstrate both client and server
 * patterns following the official A2A protocol specification.</p>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.example.A2ALlmAgent} - LLM-powered agent
 *       using Z.AI (Zhipu) for intelligent responses</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.example.A2AServerExample} - Basic server
 *       showing AgentCard creation and AgentExecutor implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.example.A2AClientExample} - Client-side
 *       example showing how to create messages and queries</li>
 * </ul>
 *
 * <h2>LLM Integration</h2>
 * <p>The {@link org.yawlfoundation.yawl.mcp.a2a.example.A2ALlmAgent} demonstrates
 * integration with Z.AI (Zhipu GLM-4.7-Flash) for:</p>
 * <ul>
 *   <li>Natural language understanding and conversation</li>
 *   <li>Workflow context analysis and recommendations</li>
 *   <li>Intelligent decision-making for routing</li>
 *   <li>Data transformation using natural language rules</li>
 * </ul>
 *
 * <h2>Environment Setup</h2>
 * <pre>{@code
 * export ZAI_API_KEY="your-zhipu-api-key"
 * export ZAI_MODEL="GLM-4.7-Flash"  # optional, defaults to GLM-4.7-Flash
 * }</pre>
 *
 * <h2>Three Transports</h2>
 * <p>The A2A SDK supports three transport protocols:</p>
 * <ol>
 *   <li><strong>JSON-RPC 2.0</strong> - Primary protocol for A2A communication</li>
 *   <li><strong>HTTP+JSON/REST</strong> - RESTful alternative for simple integrations</li>
 *   <li><strong>gRPC</strong> - High-performance binary protocol for production use</li>
 * </ol>
 *
 * <h2>Quick Start</h2>
 * <h3>Running the LLM Agent</h3>
 * <pre>{@code
 * export ZAI_API_KEY="your-key"
 * java -cp yawl-mcp-a2a-app/target/classes \
 *   org.yawlfoundation.yawl.mcp.a2a.example.A2ALlmAgent
 * }</pre>
 *
 * <h3>Running the Basic Server</h3>
 * <pre>{@code
 * java -cp yawl-mcp-a2a-app/target/classes \
 *   org.yawlfoundation.yawl.mcp.a2a.example.A2AServerExample
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see <a href="https://a2a-protocol.org/">A2A Protocol Specification</a>
 * @see <a href="https://github.com/a2aproject/a2a-java">A2A Java SDK</a>
 * @see <a href="https://open.bigmodel.cn/">Z.AI (Zhipu) API</a>
 */
package org.yawlfoundation.yawl.mcp.a2a.example;
