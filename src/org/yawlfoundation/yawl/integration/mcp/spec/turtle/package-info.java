/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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
 * MCP tool specifications for Turtle RDF format import/export operations.
 *
 * <p>This package provides Model Context Protocol (MCP) tool specifications for
 * importing YAWL workflow specifications from Turtle RDF format and exporting
 * existing specifications to Turtle. Tools enable MCP clients (like Claude) to
 * work with semantic workflow representations.</p>
 *
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.spec.turtle.YawlTurtleToolSpecifications}
 *       - Factory for creating Turtle import/export MCP tool specifications</li>
 * </ul>
 *
 * <p><b>Tool Capabilities:</b>
 * <ul>
 *   <li>Import specifications from Turtle RDF format with optional validation</li>
 *   <li>Export loaded specifications to Turtle RDF for semantic web processing</li>
 *   <li>Validate Turtle content for YAWL specification compliance</li>
 *   <li>List available workflow patterns for Turtle-based design</li>
 * </ul>
 *
 * <p><b>RDF/Turtle Integration:</b>
 * <p>Turtle is a Terse RDF Triple Language that provides a human-readable syntax
 * for RDF data. YAWL specifications can be represented as RDF using the YAWL ontology
 * namespace (http://www.yawlfoundation.org/yawlschema#) with Dublin Core metadata
 * support. This enables semantic workflow processing, knowledge graph integration,
 * and interoperability with other RDF-based systems.</p>
 *
 * <p><b>YAWL Ontology Namespace:</b>
 * <pre>http://www.yawlfoundation.org/yawlschema#</pre>
 *
 * <p><b>Supported Ontology Classes:</b>
 * <ul>
 *   <li>Specification - Workflow specification container</li>
 *   <li>WorkflowNet - Workflow net decomposition</li>
 *   <li>Task - Task elements (atomic tasks)</li>
 *   <li>Condition - Condition nodes (places)</li>
 *   <li>InputCondition - Net entry point</li>
 *   <li>OutputCondition - Net exit point</li>
 *   <li>FlowInto - Flow connections between elements</li>
 * </ul>
 *
 * <p><b>Usage Example:</b>
 * <pre>
 * // Create turtle tool specifications
 * List&lt;McpServerFeatures.SyncToolSpecification&gt; turtleTools =
 *     YawlTurtleToolSpecifications.createAll(
 *         interfaceAClient, interfaceBClient, sessionHandle);
 *
 * // Register with MCP server
 * mcpServer.tools(turtleTools);
 *
 * // Client can now use tools:
 * // - import_turtle_specification: Import Turtle RDF as YAWL spec
 * // - export_specification_to_turtle: Export spec to Turtle RDF
 * // - validate_turtle_specification: Validate Turtle content
 * // - list_turtle_patterns: Show available workflow patterns
 * </pre>
 *
 * @since 6.0.0
 * @author YAWL Foundation
 * @see org.yawlfoundation.yawl.unmarshal.turtle.YTurtleImporter
 * @see org.yawlfoundation.yawl.schema.turtle.YTurtleExporter
 */
package org.yawlfoundation.yawl.integration.mcp.spec.turtle;
