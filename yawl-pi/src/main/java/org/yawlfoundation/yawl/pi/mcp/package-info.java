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
 * MCP and A2A integration for Process Intelligence.
 *
 * <p>Exposes PI capabilities through two agent communication protocols:
 * <ul>
 *   <li><b>MCP (Model Context Protocol)</b>: 4 tools for autonomous agents via {@link org.yawlfoundation.yawl.pi.mcp.PIToolProvider}</li>
 *   <li><b>A2A (Agent-to-Agent)</b>: 4 analysis types via {@link org.yawlfoundation.yawl.pi.mcp.PISkill}</li>
 * </ul>
 *
 * <p>Tools and Skills:
 * <ul>
 *   <li>{@code yawl_pi_predict_risk} / {@code predict} - Predict case outcome and risk</li>
 *   <li>{@code yawl_pi_recommend_action} / {@code recommend} - Recommend prescriptive actions</li>
 *   <li>{@code yawl_pi_ask} / {@code ask} - RAG-based natural language query</li>
 *   <li>{@code yawl_pi_prepare_event_data} / {@code prepare-event-data} - Convert event data to OCEL2</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.pi.mcp;
