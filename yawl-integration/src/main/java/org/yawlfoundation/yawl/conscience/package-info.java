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
 * Agent Conscience Graph: persistent, queryable storage of autonomous agent decisions.
 *
 * <p>The conscience graph enables agents to:</p>
 * <ul>
 *   <li>Record decisions (routing, selection, rejection) with rationale and confidence</li>
 *   <li>Recall similar past decisions for learning-informed future choices</li>
 *   <li>Explain their behavior via auditable decision logs</li>
 *   <li>Query decision patterns using SPARQL 1.1 CONSTRUCT queries</li>
 * </ul>
 *
 * <p><b>Key Classes:</b></p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.conscience.AgentDecision} —
 *       immutable record of a single agent decision with timestamp, confidence, and context</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conscience.DecisionGraph} —
 *       persistent graph backed by Oxigraph SPARQL store; records and queries decisions</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conscience.DecisionRdfSerializer} —
 *       converts AgentDecision records to valid Turtle RDF format</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.conscience.ConscienceQueryLibrary} —
 *       library of SPARQL CONSTRUCT query templates for common decision queries</li>
 * </ul>
 *
 * <p><b>Design Patterns:</b></p>
 * <ul>
 *   <li><b>Records:</b> AgentDecision uses Java 14+ record syntax for immutability</li>
 *   <li><b>RDF Serialization:</b> Pure Java Turtle generation, no external RDF libraries</li>
 *   <li><b>Graceful Degradation:</b> Operations succeed silently if SPARQL engine unavailable</li>
 *   <li><b>Query Templates:</b> SPARQL queries are parameterized string constants</li>
 * </ul>
 *
 * <p><b>Integration:</b> The conscience graph is exposed to autonomous agents via MCP tools
 * ({@link org.yawlfoundation.yawl.integration.mcp.spec.YawlConscienceToolSpecifications})
 * and can be queried remotely via SPARQL endpoints (Oxigraph default port 8083).</p>
 *
 * @since YAWL 6.0
 */
package org.yawlfoundation.yawl.integration.conscience;
