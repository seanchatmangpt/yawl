package org.yawlfoundation.yawl.integration.a2a.skills;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable request object for A2A skill execution.
 *
 * <p>Contains the skill ID and parameters needed for execution.
 * Parameters are key-value pairs that vary by skill.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class SkillRequest {

    private final String skillId;
    private final Map<String, String> parameters;
    private final String requestId;
    private final long timestamp;

    /**
     * Create a skill request.
     *
     * @param skillId    the skill to execute
     * @param parameters the parameters for the skill
     */
    public SkillRequest(String skillId, Map<String, String> parameters) {
        this(skillId, parameters, generateRequestId());
    }

    /**
     * Create a skill request with explicit request ID.
     *
     * @param skillId    the skill to execute
     * @param parameters the parameters for the skill
     * @param requestId  unique request identifier for tracing
     */
    public SkillRequest(String skillId, Map<String, String> parameters, String requestId) {
        this.skillId = Objects.requireNonNull(skillId, "skillId is required");
        this.parameters = parameters != null
            ? Collections.unmodifiableMap(new HashMap<>(parameters))
            : Collections.emptyMap();
        this.requestId = requestId != null ? requestId : generateRequestId();
        this.timestamp = System.currentTimeMillis();
    }

    private static String generateRequestId() {
        return "req-" + System.currentTimeMillis() + "-" +
               Integer.toHexString(System.identityHashCode(new Object())).substring(0, 4);
    }

    /**
     * Get the skill ID.
     *
     * @return skill identifier
     */
    public String getSkillId() {
        return skillId;
    }

    /**
     * Get a parameter value by name.
     *
     * @param name parameter name
     * @return parameter value, or null if not present
     */
    public String getParameter(String name) {
        return parameters.get(name);
    }

    /**
     * Get a parameter value with a default.
     *
     * @param name         parameter name
     * @param defaultValue default value if not present
     * @return parameter value or default
     */
    public String getParameter(String name, String defaultValue) {
        return parameters.getOrDefault(name, defaultValue);
    }

    /**
     * Get all parameters.
     *
     * @return immutable map of parameters
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Get the request ID for tracing.
     *
     * @return unique request identifier
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Get the request timestamp.
     *
     * @return epoch millis when request was created
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Create a builder for constructing requests.
     *
     * @param skillId the skill ID
     * @return builder instance
     */
    public static Builder builder(String skillId) {
        return new Builder(skillId);
    }

    @Override
    public String toString() {
        return "SkillRequest{" +
               "skillId='" + skillId + '\'' +
               ", requestId='" + requestId + '\'' +
               ", parameters=" + parameters.keySet() +
               '}';
    }

    /**
     * Builder for SkillRequest.
     */
    public static final class Builder {
        private final String skillId;
        private final Map<String, String> parameters = new HashMap<>();
        private String requestId;

        private Builder(String skillId) {
            this.skillId = skillId;
        }

        public Builder parameter(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        public Builder parameters(Map<String, String> params) {
            parameters.putAll(params);
            return this;
        }

        public Builder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public SkillRequest build() {
            return new SkillRequest(skillId, parameters, requestId);
        }
    }
}
