package org.yawlfoundation.yawl.integration.wizard.core;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable audit trail event capturing a step in wizard execution.
 *
 * <p>Events are recorded chronologically in a {@link WizardSession} and form
 * a complete audit trail of wizard decisions and transitions. All fields are
 * immutable; use factory methods to create instances.
 *
 * <p>The data map contains step-specific metadata and is immutable (defensive copy).
 *
 * @param timestamp when this event occurred (UTC)
 * @param phase wizard phase when event was recorded
 * @param stepId unique identifier of the wizard step
 * @param message human-readable description of what occurred
 * @param data immutable map of additional event context
 */
public record WizardEvent(
    Instant timestamp,
    WizardPhase phase,
    String stepId,
    String message,
    Map<String, Object> data
) {
    /**
     * Compact constructor ensures immutability of the data map.
     */
    public WizardEvent {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(phase, "phase cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(data, "data cannot be null");

        // Defensive copy: ensure data is immutable
        data = Collections.unmodifiableMap(Map.copyOf(data));
    }

    /**
     * Factory method: creates event with minimal required fields.
     *
     * @param phase the current wizard phase
     * @param stepId unique identifier of the step
     * @param message human-readable message
     * @return new event with empty data map
     */
    public static WizardEvent of(WizardPhase phase, String stepId, String message) {
        return of(phase, stepId, message, Map.of());
    }

    /**
     * Factory method: creates event with full fields.
     *
     * @param phase the current wizard phase
     * @param stepId unique identifier of the step
     * @param message human-readable message
     * @param data additional context (immutable copy made)
     * @return new event
     */
    public static WizardEvent of(WizardPhase phase, String stepId, String message, Map<String, Object> data) {
        return new WizardEvent(Instant.now(), phase, stepId, message, data);
    }

    /**
     * Retrieves a typed value from the event data map.
     *
     * @param key the data key
     * @param type the expected type
     * @param <T> the type parameter
     * @return the value if present and of correct type
     * @throws ClassCastException if value exists but is not of expected type
     * @throws NullPointerException if type is null
     */
    @SuppressWarnings("unchecked")
    public <T> java.util.Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        Object value = data.get(key);
        if (value == null) {
            return java.util.Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("Event data[%s] is %s but expected %s",
                    key, value.getClass().getName(), type.getName())
            );
        }
        return java.util.Optional.of((T) value);
    }
}
