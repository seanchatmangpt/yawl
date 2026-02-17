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
 * Configuration loading for autonomous agents.
 *
 * <p>This package provides utilities for loading agent configurations from
 * properties files and environment variables, following the 12-factor app
 * pattern for configuration management.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.autonomous.config.AgentConfigLoader} -
 *       Loads agent capabilities, task mappings, and engine connection settings</li>
 * </ul>
 *
 * <p>Configuration sources (in order of precedence):</p>
 * <ol>
 *   <li>Environment variables</li>
 *   <li>System properties</li>
 *   <li>Properties files (agent-config.properties, task-mappings.properties)</li>
 * </ol>
 */
package org.yawlfoundation.yawl.integration.autonomous.config;
