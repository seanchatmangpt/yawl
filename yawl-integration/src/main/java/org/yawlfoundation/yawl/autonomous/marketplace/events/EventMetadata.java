package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.util.Map;
import java.util.Optional;

/**
 * Optional metadata for marketplace events.
 *
 * <p>Includes cross-cutting concerns like distributed tracing, request correlation,
 * and audit fields.
 *
 * @param traceId OpenTelemetry trace ID for distributed tracing
 * @param correlationId business correlation ID (e.g. customer request ID)
 * @param userId authenticated user making the request
 * @param tags key-value pairs for filtering/searching events
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record EventMetadata(
    String traceId,
    String correlationId,
    String userId,
    Map<String, String> tags
) {

    /**
     * Return a builder for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EventMetadata with fluent API.
     */
    public static class Builder {
        private String traceId;
        private String correlationId;
        private String userId;
        private Map<String, String> tags = Map.of();

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            this.tags = tags != null ? tags : Map.of();
            return this;
        }

        public EventMetadata build() {
            return new EventMetadata(traceId, correlationId, userId, tags);
        }
    }

    /**
     * Get a tag value by key, or Optional.empty if not present.
     */
    public Optional<String> getTag(String key) {
        return tags != null && tags.containsKey(key)
            ? Optional.of(tags.get(key))
            : Optional.empty();
    }
}
