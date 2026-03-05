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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

/**
 * Context for an autonomous agent — Java 25 record edition.
 *
 * <p>Converted from a plain class to a Java 25 record:
 * <ul>
 *   <li>Immutable by construction (all components are final)</li>
 *   <li>Auto-generated equals, hashCode, and toString</li>
 *   <li>Eliminated 40+ lines of boilerplate getters and constructor</li>
 *   <li>Canonical constructor validates all required fields</li>
 * </ul>
 *
 * <p>The {@link #CURRENT} scoped value carries the active context across
 * virtual threads without {@code ThreadLocal}. Bind it with
 * {@code ScopedValue.callWhere(AgentContext.CURRENT, ctx, () -> ...)}
 * and read it with {@code AgentContext.CURRENT.get()} from any
 * forked virtual thread.
 *
 * @param agentId       unique identifier for this agent
 * @param agentName     display name of this agent
 * @param engineUrl     URL of the YAWL engine (e.g. http://localhost:8080/yawl)
 * @param sessionHandle current session handle for engine communication
 *
 * @since YAWL 6.0
 */
public record AgentContext(
        String agentId,
        String agentName,
        String engineUrl,
        String sessionHandle) {

    /**
     * Scoped value for propagating agent context across virtual threads.
     *
     * <p>Use instead of {@code ThreadLocal}. Automatically inherited by all
     * virtual threads forked inside a {@link java.util.concurrent.StructuredTaskScope}.
     */
    public static final ScopedValue<AgentContext> CURRENT = ScopedValue.newInstance();

    /**
     * Canonical constructor with validation.
     */
    public AgentContext {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("AgentContext agentId is required");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("AgentContext agentName is required");
        }
        if (engineUrl == null || engineUrl.isBlank()) {
            throw new IllegalArgumentException("AgentContext engineUrl is required");
        }
        if (sessionHandle == null) {
            throw new IllegalArgumentException("AgentContext sessionHandle must not be null");
        }
    }

    /**
     * Create an agent context from environment variables.
     *
     * <p>Variables read:
     * <ul>
     *   <li>{@code AGENT_ID} — defaults to {@code "agent-unknown"}</li>
     *   <li>{@code AGENT_NAME} — defaults to {@code "Unknown Agent"}</li>
     *   <li>{@code YAWL_ENGINE_URL} — defaults to {@code "http://localhost:8080/yawl"}</li>
     *   <li>{@code YAWL_SESSION_HANDLE} — defaults to empty string</li>
     * </ul>
     *
     * @return the agent context from environment
     */
    public static AgentContext fromEnvironment() {
        String agentId = System.getenv().getOrDefault("AGENT_ID", "agent-unknown");
        String agentName = System.getenv().getOrDefault("AGENT_NAME", "Unknown Agent");
        String engineUrl = System.getenv().getOrDefault(
            "YAWL_ENGINE_URL", "http://localhost:8080/yawl");
        String sessionHandle = System.getenv().getOrDefault("YAWL_SESSION_HANDLE", "");
        return new AgentContext(agentId, agentName, engineUrl, sessionHandle);
    }

    /**
     * Return a copy of this context with an updated session handle.
     *
     * <p>Records are immutable, so updates create a new instance.
     *
     * @param newSessionHandle the new session handle
     * @return updated context record
     */
    public AgentContext withSessionHandle(String newSessionHandle) {
        return new AgentContext(agentId, agentName, engineUrl, newSessionHandle);
    }

    /**
     * Check whether this context has a valid (non-empty) session handle.
     *
     * @return true if a session is established
     */
    public boolean hasSession() {
        return !sessionHandle.isBlank();
    }
}
