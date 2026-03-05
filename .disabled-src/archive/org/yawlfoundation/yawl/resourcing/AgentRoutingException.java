/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.resourcing;

/**
 * Thrown when an agent-route dispatch fails after the routing decision committed to
 * an agent.
 *
 * <p>This exception enforces the no-silent-fallback invariant: once a
 * {@link RoutingDecision.AgentRoute} is returned by {@link CapabilityMatcher}, the
 * system must attempt to dispatch the work item to the selected agent. If that dispatch
 * fails (network error, agent rejection, timeout), this exception is thrown rather than
 * quietly falling back to human routing.</p>
 *
 * <p>Callers that want graceful human-fallback should check
 * {@link CapabilityMatcher#match(org.yawlfoundation.yawl.stateless.engine.YWorkItem)}
 * before dispatching — if no suitable agent exists, a {@link RoutingDecision.HumanRoute}
 * is returned and no dispatch is attempted.</p>
 *
 * @since YAWL 6.0
 */
public class AgentRoutingException extends RuntimeException {

    /**
     * Constructs an exception with the given message.
     *
     * @param message description of the dispatch failure
     */
    public AgentRoutingException(String message) {
        super(message);
    }

    /**
     * Constructs an exception with the given message and root cause.
     *
     * @param message description of the dispatch failure
     * @param cause   the underlying exception that caused the dispatch to fail
     */
    public AgentRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
