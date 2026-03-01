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
package org.yawlfoundation.yawl.erlang.lifecycle;

import java.time.Instant;

/**
 * Emitted by OtpNodeLifecycleManager when a monitored OTP node is restarted
 * by the watchdog. Consumers can react to restarts (e.g., reconnect bridges,
 * reset circuit breakers).
 *
 * <p>This is an immutable event record capturing the essential information
 * about a node restart: when it occurred, how many times the node has been
 * restarted, and why the restart was triggered.</p>
 */
public record NodeRestartEvent(
    String nodeName,
    Instant timestamp,
    int restartCount,
    String reason
) {
    /**
     * Creates a node restart event.
     *
     * @param nodeName     Erlang node name that was restarted
     * @param timestamp    wall-clock time of the restart
     * @param restartCount cumulative restart count since lifecycle manager was created
     * @param reason       human-readable reason for the restart
     * @throws IllegalArgumentException if nodeName is blank or timestamp is null
     */
    public NodeRestartEvent {
        if (nodeName == null || nodeName.isBlank()) {
            throw new IllegalArgumentException("nodeName must be non-blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must be non-null");
        }
        if (reason == null) {
            reason = "unknown";
        }
    }
}
