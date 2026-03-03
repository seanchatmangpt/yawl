/*
 * Copyright 2024 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.integration.a2a.gossip;

import java.util.Objects;

/**
 * A subscriber that receives messages from the gossip bus.
 *
 * Subscribers implement this interface to receive messages
 * for specific topics. Each subscriber runs in its own virtual thread
 * to ensure non-blocking execution.
 */
@FunctionalInterface
public interface GossipSubscriber {

    /**
     * Called when a message is received for the subscribed topic.
     *
     * This method is executed in a virtual thread and should be
     * designed to return quickly. For long-running operations,
     * consider offloading to a separate thread pool.
     *
     * @param message the received message
     */
    void onMessage(GossipMessage message);

    /**
     * Returns the identifier for this subscriber.
     *
     * @return subscriber identifier
     */
    default String getId() {
        return Integer.toHexString(Objects.hashCode(this));
    }

    /**
     * Called when the subscriber is unsubscribed from all topics.
     *
     * This allows for any cleanup operations to be performed.
     */
    default void onUnsubscribe() {
        // Default implementation does nothing
    }
}