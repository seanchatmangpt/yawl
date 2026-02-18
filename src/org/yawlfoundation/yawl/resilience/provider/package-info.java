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
 * Resilience provider for simplified YAWL operations.
 *
 * <p>This package provides a unified API for applying resilience patterns to
 * YAWL operations without requiring boilerplate code.</p>
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider} -
 *       Simple, consistent API for resilient operations</li>
 * </ul>
 *
 * <p>Usage patterns:</p>
 * <ul>
 *   <li>executeEngineCall() - Engine service calls with circuit breaker and retry</li>
 *   <li>executeExternalCall() - External service calls with full resilience stack</li>
 *   <li>executeMcpCall() - MCP integration with custom patterns</li>
 *   <li>executeMultiAgentFanout() - Multi-agent operations with rate limiting</li>
 * </ul>
 */
package org.yawlfoundation.yawl.resilience.provider;
