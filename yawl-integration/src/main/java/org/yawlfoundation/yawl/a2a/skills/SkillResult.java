package org.yawlfoundation.yawl.integration.a2a.skills;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result object from A2A skill execution.
 *
 * <p>Contains success/error status, result data, and execution metadata.
 * Results are serializable for transmission over A2A protocol.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class SkillResult {

    private final boolean success;
    private final String error;
    private final Map<String, Object> data;
    private final long executionTimeMs;
    private final Instant timestamp;

    private SkillResult(boolean success, String error, Map<String, Object> data, long executionTimeMs) {
        this.success = success;
        this.error = error;
        this.data = data != null
            ? Collections.unmodifiableMap(new HashMap<>(data))
            : Collections.emptyMap();
        this.executionTimeMs = executionTimeMs;
        this.timestamp = Instant.now();
    }

    /**
     * Create a successful result.
     *
     * @param data the result data
     * @return successful result
     */
    public static SkillResult success(Map<String, Object> data) {
        return new SkillResult(true, null, data, 0);
    }

    /**
     * Create a successful result with execution time.
     *
     * @param data            the result data
     * @param executionTimeMs execution time in milliseconds
     * @return successful result
     */
    public static SkillResult success(Map<String, Object> data, long executionTimeMs) {
        return new SkillResult(true, null, data, executionTimeMs);
    }

    /**
     * Create a successful result with a single message.
     *
     * @param message success message
     * @return successful result
     */
    public static SkillResult success(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return new SkillResult(true, null, data, 0);
    }

    /**
     * Create an error result.
     *
     * @param error error message
     * @return error result
     */
    public static SkillResult error(String error) {
        return new SkillResult(false, error, null, 0);
    }

    /**
     * Create an error result with additional data.
     *
     * @param error error message
     * @param data  additional error context
     * @return error result
     */
    public static SkillResult error(String error, Map<String, Object> data) {
        return new SkillResult(false, error, data, 0);
    }

    /**
     * Check if the result indicates success.
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Check if the result indicates failure.
     *
     * @return true if failed
     */
    public boolean isError() {
        return !success;
    }

    /**
     * Get the error message (only present if failed).
     *
     * @return error message or null
     */
    public String getError() {
        return error;
    }

    /**
     * Get the result data.
     *
     * @return immutable map of result data
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Get a specific data value.
     *
     * @param key data key
     * @return data value or null
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Get a specific data value with type.
     *
     * @param key  data key
     * @param type expected type
     * @param <T>  type parameter
     * @return typed data value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * Get the execution time in milliseconds.
     *
     * @return execution time or 0 if not tracked
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Get the timestamp when this result was created.
     *
     * @return result timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Convert to a map for JSON serialization.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        if (error != null) {
            map.put("error", error);
        }
        if (!data.isEmpty()) {
            map.put("data", data);
        }
        map.put("timestamp", timestamp.toString());
        if (executionTimeMs > 0) {
            map.put("executionTimeMs", executionTimeMs);
        }
        return map;
    }

    @Override
    public String toString() {
        return "SkillResult{" +
               "success=" + success +
               ", error='" + error + '\'' +
               ", dataKeys=" + data.keySet() +
               ", executionTimeMs=" + executionTimeMs +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillResult that)) return false;
        return success == that.success &&
               Objects.equals(error, that.error) &&
               Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, error, data);
    }
}
