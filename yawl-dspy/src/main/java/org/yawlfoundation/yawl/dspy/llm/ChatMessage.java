/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.llm;

/**
 * Chat message in a conversation.
 *
 * @param role    the message role (system, user, assistant)
 * @param content the message content
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record ChatMessage(Role role, String content) {

    public ChatMessage {
        if (role == null) throw new IllegalArgumentException("role is required");
        if (content == null) content = "";
    }

    /**
     * Create a system message.
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    /**
     * Create a user message.
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    /**
     * Create an assistant message.
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }

    /**
     * Message role in a conversation.
     */
    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        FUNCTION,
        TOOL
    }
}
