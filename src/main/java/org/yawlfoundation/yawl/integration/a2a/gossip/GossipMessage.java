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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A message exchanged via the gossip protocol.
 *
 * The message format includes:
 * - Unique identifier for deduplication
 * - Timestamp for ordering
 * - Topic for routing
 * - Payload data
 * - Metadata for extensibility
 */
public final class GossipMessage {

    private final String messageId;
    private final Instant timestamp;
    private final String topic;
    private final Object payload;
    private final Map<String, String> metadata;
    private int sequenceNumber;

    private GossipMessage(Builder builder) {
        this.messageId = builder.messageId;
        this.timestamp = builder.timestamp;
        this.topic = builder.topic;
        this.payload = builder.payload;
        this.metadata = builder.metadata;
        this.sequenceNumber = builder.sequenceNumber;
    }

    public String getMessageId() {
        return messageId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTopic() {
        return topic;
    }

    public Object getPayload() {
        return payload;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    /**
     * Sets the sequence number.
     * Package-private for internal use only.
     */
    void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GossipMessage that = (GossipMessage) o;
        return Objects.equals(messageId, that.messageId) &&
               Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, topic);
    }

    @Override
    public String toString() {
        return String.format("GossipMessage{id=%s, topic=%s, seq=%d, timestamp=%s}",
                           messageId, topic, sequenceNumber, timestamp);
    }

    /**
     * Builder for creating GossipMessage instances.
     */
    public static final class Builder {
        private String messageId;
        private Instant timestamp;
        private String topic;
        private Object payload;
        private Map<String, String> metadata;
        private int sequenceNumber;

        public Builder withTopic(String topic) {
            this.topic = Objects.requireNonNull(topic, "Topic cannot be null");
            return this;
        }

        public Builder withPayload(Object payload) {
            this.payload = payload;
            return this;
        }

        public Builder withMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder withSequenceNumber(int sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public GossipMessage build() {
            this.messageId = UUID.randomUUID().toString();
            this.timestamp = Instant.now();
            this.sequenceNumber = 0;

            if (topic == null) {
                throw new IllegalStateException("Topic is required");
            }

            return new GossipMessage(this);
        }
    }
}