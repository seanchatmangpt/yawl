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

package org.yawlfoundation.yawl.integration.a2a.handoff;

import java.util.concurrent.CompletableFuture;

/**
 * Result of a handoff operation.
 *
 * <p>Provides information about whether the handoff was accepted
 * and any associated message.</p>
 *
 * @since YAWL 6.0
 */
public class HandoffResult {

    private final boolean accepted;
    private final String message;
    private final CompletableFuture<HandoffResult> future;

    /**
     * Creates a new handoff result.
     *
     * @param accepted whether the handoff was accepted
     * @param message associated message
     */
    public HandoffResult(boolean accepted, String message) {
        this(accepted, message, null);
    }

    /**
     * Creates a new handoff result with a future.
     *
     * @param accepted whether the handoff was accepted
     * @param message associated message
     * @param future the future that produced this result
     */
    public HandoffResult(boolean accepted, String message, CompletableFuture<HandoffResult> future) {
        this.accepted = accepted;
        this.message = message;
        this.future = future;
    }

    /**
     * Gets whether the handoff was accepted.
     *
     * @return true if accepted, false otherwise
     */
    public boolean isAccepted() {
        return accepted;
    }

    /**
     * Gets the associated message.
     *
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Gets the future that produced this result.
     *
     * @return the future, or null if not applicable
     */
    public CompletableFuture<HandoffResult> getFuture() {
        return future;
    }
}