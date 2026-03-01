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
package org.yawlfoundation.yawl.erlang.bridge;

import org.yawlfoundation.yawl.erlang.generated.ei_h;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;

/**
 * Immutable record carrying a message received from the Erlang distribution.
 *
 * <p>The {@code type} field indicates whether this is a real message ({@link #ERL_MSG})
 * or a distribution tick/keepalive ({@link #ERL_TICK}). The {@code payload} contains
 * the decoded term, and {@code from} identifies the sender PID.</p>
 *
 * <p>Use {@link #fromMsgType(int, ErlTerm)} factory to construct messages,
 * which validates the message type.</p>
 */
public record ErlMessage(int type, ErlTerm payload) {

    /** Message type constant: a real message arrived (type=1). */
    public static final int ERL_MSG = ei_h.ERL_MSG;

    /** Message type constant: a distribution tick/keepalive (type=2). */
    public static final int ERL_TICK = ei_h.ERL_TICK;

    /**
     * Constructs an ErlMessage with validated message type.
     *
     * @param type    the message type: either {@link #ERL_MSG} (1) or {@link #ERL_TICK} (2)
     * @param payload the decoded term (ignored for ERL_TICK messages)
     * @return an ErlMessage record
     * @throws IllegalArgumentException if type is neither ERL_MSG nor ERL_TICK
     */
    public static ErlMessage fromMsgType(int type, ErlTerm payload) {
        if (type != ERL_MSG && type != ERL_TICK) {
            throw new IllegalArgumentException(
                "Invalid message type: " + type + " (expected " + ERL_MSG + " or " + ERL_TICK + ")");
        }
        return new ErlMessage(type, payload);
    }

    /**
     * Checks if this message is a real message (type == ERL_MSG).
     *
     * @return true if this is a real message, false if it's a tick
     */
    public boolean isRealMessage() {
        return type == ERL_MSG;
    }

    /**
     * Checks if this message is a distribution tick (type == ERL_TICK).
     *
     * @return true if this is a tick, false if it's a real message
     */
    public boolean isTick() {
        return type == ERL_TICK;
    }
}
