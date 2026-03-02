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

/**
 * SAFe artifact MCP resources and governance tools.
 *
 * Exposes SAFe Agile Release Train (ART) backlogs, Program Increment (PI)
 * objectives, Non-Functional Requirements (NFR) catalog, AI model registry,
 * Responsible AI policy, and telemetry metrics as MCP resources and tools.
 *
 * <h2>Resources (6 total)</h2>
 * <ul>
 *   <li>{@code safe://backlog/{artId}} - ART backlog with features and enablers</li>
 *   <li>{@code safe://pi-objectives/{artId}/{pi}} - PI planning objectives</li>
 *   <li>{@code safe://nfrs} - Responsible AI NFR catalog policies</li>
 *   <li>{@code safe://model-registry/{modelId}} - AI model artifact registry</li>
 *   <li>{@code safe://policy/responsible-ai} - SAFe Responsible AI policy (13 attributes)</li>
 *   <li>{@code safe://telemetry} - Flow metrics and incident signals</li>
 * </ul>
 *
 * <h2>Tools (6 total)</h2>
 * <ul>
 *   <li>{@code safe_validate_nfrs} - Validate source file against NFR policies</li>
 *   <li>{@code safe_approve_model_promotion} - Promote model with ResponsibleAiReceipt proof</li>
 *   <li>{@code safe_check_guardrails} - Verify budget and utilization thresholds</li>
 *   <li>{@code safe_create_receipt} - Create governance receipt with SHA256 hash</li>
 *   <li>{@code safe_register_model} - Register new model version as candidate</li>
 *   <li>{@code safe_promote_model} - Alias to safe_approve_model_promotion</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.ModelRegistry}
 *       - Thread-safe in-memory registry for AI model versions</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.ModelRegistryEntry}
 *       - Immutable record of a versioned model with promotion status</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.NfrCatalog}
 *       - Versioned catalog of 6 Responsible AI NFR policies</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.NfrViolation}
 *       - Single NFR policy violation detected in source code</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.SafeMcpResourceProvider}
 *       - MCP resource provider for SAFe artifacts</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.safe.SafeMcpToolRegistry}
 *       - MCP tool provider for SAFe governance</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 *
 * <p>In {@code YawlMcpServer.start()}, after existing tool registration:</p>
 * <pre>{@code
 * // SAFe MCP resources and governance tools
 * ModelRegistry modelRegistry = new ModelRegistry();
 * NfrCatalog nfrCatalog = new NfrCatalog();
 * SafeMcpResourceProvider safeResources = new SafeMcpResourceProvider(modelRegistry);
 * SafeMcpToolRegistry safeTools = new SafeMcpToolRegistry(nfrCatalog, modelRegistry);
 *
 * // Register resources and tools using existing MCP server patterns
 * allResources.addAll(safeResources.createAll());
 * allTools.addAll(safeTools.createAll());
 * }</pre>
 *
 * @since YAWL 6.0.0
 * @author YAWL Foundation
 */
package org.yawlfoundation.yawl.integration.mcp.safe;
