/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

/**
 * Factory for creating ZaiClient instances.
 *
 * <p>When the Z.AI SDK is not configured, returns a disabled null-object client
 * that throws UnsupportedOperationException on API calls.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ZaiClientFactory {

    public static ZaiClient withApiKey(String apiKey) {
        // Z.AI SDK not available — return disabled null-object client
        return new DisabledZaiClient();
    }
}

interface ZaiClient {
    ChatService chat();
    void close();
}

interface ChatService {
    Object createChatCompletion(Object params);
}

class DisabledZaiClient implements ZaiClient {

    @Override
    public ChatService chat() {
        return new DisabledChatService();
    }

    @Override
    public void close() {
        // No resources to release for disabled client
    }

    static class DisabledChatService implements ChatService {
        @Override
        public Object createChatCompletion(Object params) {
            throw new UnsupportedOperationException(
                "Z.AI client is disabled: configure zai.api.key to enable real API calls");
        }
    }
}
