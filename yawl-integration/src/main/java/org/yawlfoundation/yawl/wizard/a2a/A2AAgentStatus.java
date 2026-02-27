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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

/**
 * Connection status of a discovered A2A agent.
 *
 * <p>Used during discovery phase to track the operational state of each
 * agent, enabling the wizard to warn if agents are unavailable or only
 * partially operational.
 *
 * @since YAWL 6.0
 */
public enum A2AAgentStatus {
    /**
     * Agent is reachable and all capabilities are available.
     */
    AVAILABLE,

    /**
     * Agent is not reachable (network, DNS, or connection error).
     */
    UNREACHABLE,

    /**
     * Agent is reachable but some skills are unavailable.
     */
    DEGRADED,

    /**
     * Agent is reachable but authentication is still pending.
     */
    AUTHENTICATING
}
