/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
 * Z.AI function service integration for YAWL MCP.
 *
 * <p>This package provides integration between YAWL's Model Context Protocol (MCP)
 * server and Z.AI's function calling capability for natural language workflow operations.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.zai.ZaiFunctionService} -
 *       Bridge between MCP tools and Z.AI function calling</li>
 * </ul>
 *
 * <p>Requirements:</p>
 * <ul>
 *   <li>Z.AI SDK dependency in yawl-integration/pom.xml</li>
 *   <li>ZAI_API_KEY environment variable</li>
 * </ul>
 */
package org.yawlfoundation.yawl.integration.mcp.zai;
