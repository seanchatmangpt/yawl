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
 * <p>This creates a mock ZaiClient for testing purposes to avoid network calls.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ZaiClientFactory {

    public static ZaiClient withApiKey(String apiKey) {
        // Create a mock client that doesn't make real API calls
        return new MockZaiClient();
    }
}

interface ZaiClient {
    ChatService chat();
    void close();
}

interface ChatService {
    Object createChatCompletion(Object params);
}

class MockZaiClient implements ZaiClient {
    
    @Override
    public ChatService chat() {
        return new MockChatService();
    }
    
    @Override
    public void close() {
        // No-op
    }
    
    public static class MockChatService implements ChatService {
        @Override
        public Object createChatCompletion(Object params) {
            throw new UnsupportedOperationException("Mock client - no real API calls");
        }
    }
}
