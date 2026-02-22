package org.yawlfoundation.yawl.integration.wizard.core;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Final configuration output of a completed Autonomic A2A/MCP Wizard session.
 *
 * <p>WizardResult is an immutable record capturing the complete outcome of
 * a wizard execution, including success/failure status, generated configurations,
 * validation errors, and the full audit trail. Use factory methods
 * {@link #success(WizardSession)} or {@link #failure(WizardSession, List)}
 * to create instances.
 *
 * <p>A successful result includes:
 * <ul>
 *   <li>MCP configuration (tool bindings, protocol settings)</li>
 *   <li>A2A configuration (agent skills, handoff routes)</li>
 *   <li>Workflow configuration (van der Aalst pattern + task definitions)</li>
 * </ul>
 *
 * <p>A failed result includes error messages and the audit trail up to failure.
 *
 * @param success whether wizard execution completed successfully
 * @param sessionId the unique identifier of the wizard session
 * @param terminalPhase the final phase the wizard reached
 * @param mcpConfiguration immutable map of MCP configuration (empty if failed)
 * @param a2aConfiguration immutable map of A2A configuration (empty if failed)
 * @param workflowConfiguration immutable map of workflow configuration (empty if failed)
 * @param validationErrors list of validation errors (empty if successful)
 * @param auditTrail immutable list of all events in the session
 * @param completedAt timestamp when wizard completed (UTC)
 */
public record WizardResult(
    boolean success,
    String sessionId,
    WizardPhase terminalPhase,
    Map<String, Object> mcpConfiguration,
    Map<String, Object> a2aConfiguration,
    Map<String, Object> workflowConfiguration,
    List<String> validationErrors,
    List<WizardEvent> auditTrail,
    Instant completedAt
) {
    /**
     * Compact constructor ensures immutability of all mutable fields.
     */
    public WizardResult {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(terminalPhase, "terminalPhase cannot be null");
        Objects.requireNonNull(mcpConfiguration, "mcpConfiguration cannot be null");
        Objects.requireNonNull(a2aConfiguration, "a2aConfiguration cannot be null");
        Objects.requireNonNull(workflowConfiguration, "workflowConfiguration cannot be null");
        Objects.requireNonNull(validationErrors, "validationErrors cannot be null");
        Objects.requireNonNull(auditTrail, "auditTrail cannot be null");
        Objects.requireNonNull(completedAt, "completedAt cannot be null");

        // Defensive copies: ensure all maps and lists are unmodifiable
        mcpConfiguration = Collections.unmodifiableMap(Map.copyOf(mcpConfiguration));
        a2aConfiguration = Collections.unmodifiableMap(Map.copyOf(a2aConfiguration));
        workflowConfiguration = Collections.unmodifiableMap(Map.copyOf(workflowConfiguration));
        validationErrors = Collections.unmodifiableList(List.copyOf(validationErrors));
        auditTrail = Collections.unmodifiableList(List.copyOf(auditTrail));
    }

    /**
     * Factory method: creates a successful result from a completed session.
     *
     * <p>Extracts MCP, A2A, and workflow configuration from the session's context
     * using standard key names:
     * <ul>
     *   <li>"mcp_config" → mcpConfiguration</li>
     *   <li>"a2a_config" → a2aConfiguration</li>
     *   <li>"workflow_config" → workflowConfiguration</li>
     * </ul>
     *
     * <p>If any configuration key is missing, an empty map is used. The result
     * terminal phase is set to COMPLETE.
     *
     * @param session the completed wizard session
     * @return successful result with configurations extracted from session context
     * @throws NullPointerException if session is null
     */
    public static WizardResult success(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");

        Map<String, Object> mcp = session.get("mcp_config", Map.class).orElse(Map.of());
        Map<String, Object> a2a = session.get("a2a_config", Map.class).orElse(Map.of());
        Map<String, Object> workflow = session.get("workflow_config", Map.class).orElse(Map.of());

        return new WizardResult(
            true,
            session.sessionId(),
            WizardPhase.COMPLETE,
            mcp,
            a2a,
            workflow,
            List.of(),
            session.events(),
            Instant.now()
        );
    }

    /**
     * Factory method: creates a failed result from a session with error messages.
     *
     * <p>The result terminal phase is set to FAILED. All configuration maps are empty.
     * The audit trail is preserved from the session.
     *
     * @param session the session that failed
     * @param errors list of error messages explaining the failure
     * @return failed result with errors and audit trail
     * @throws NullPointerException if session or errors is null
     */
    public static WizardResult failure(WizardSession session, List<String> errors) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");

        return new WizardResult(
            false,
            session.sessionId(),
            WizardPhase.FAILED,
            Map.of(),
            Map.of(),
            Map.of(),
            errors,
            session.events(),
            Instant.now()
        );
    }

    /**
     * Checks if this result represents success.
     *
     * @return true if wizard completed successfully
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the first error message, if any failure occurred.
     *
     * @return optional containing first error if failed, empty if successful
     */
    public Optional<String> firstError() {
        if (validationErrors.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(validationErrors.get(0));
    }

    /**
     * Gets the total count of errors in this result.
     *
     * @return number of validation errors (0 if successful)
     */
    public int errorCount() {
        return validationErrors.size();
    }

    /**
     * Gets all errors as a single string, one per line.
     *
     * @return formatted error summary (empty string if successful)
     */
    public String errorSummary() {
        return String.join("\n  ", validationErrors);
    }

    /**
     * Gets the count of events in the audit trail.
     *
     * @return number of events recorded
     */
    public int auditTrailSize() {
        return auditTrail.size();
    }

    /**
     * Retrieves a configuration value from MCP configuration.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the type parameter
     * @return optional containing the value if present and correct type
     * @throws ClassCastException if value exists but is not of expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMcpConfig(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Object value = mcpConfiguration.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("MCP config[%s] is %s but expected %s",
                    key, value.getClass().getName(), type.getName())
            );
        }
        return Optional.of((T) value);
    }

    /**
     * Retrieves a configuration value from A2A configuration.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the type parameter
     * @return optional containing the value if present and correct type
     * @throws ClassCastException if value exists but is not of expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getA2AConfig(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Object value = a2aConfiguration.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("A2A config[%s] is %s but expected %s",
                    key, value.getClass().getName(), type.getName())
            );
        }
        return Optional.of((T) value);
    }

    /**
     * Retrieves a configuration value from workflow configuration.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the type parameter
     * @return optional containing the value if present and correct type
     * @throws ClassCastException if value exists but is not of expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getWorkflowConfig(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Object value = workflowConfiguration.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("Workflow config[%s] is %s but expected %s",
                    key, value.getClass().getName(), type.getName())
            );
        }
        return Optional.of((T) value);
    }
}
