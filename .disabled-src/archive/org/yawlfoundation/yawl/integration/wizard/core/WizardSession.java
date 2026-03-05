package org.yawlfoundation.yawl.integration.wizard.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Session state carrier for the Autonomic A2A/MCP Wizard.
 *
 * <p>WizardSession is immutable and represents the complete state of a wizard
 * execution at a point in time. All state changes are made by creating new
 * session instances via transition methods like {@link #withPhase(WizardPhase, String, String)}.
 *
 * <p>Sessions maintain:
 * <ul>
 *   <li>sessionId: unique identifier for audit trail</li>
 *   <li>currentPhase: the phase the wizard is in</li>
 *   <li>context: key-value store for step-specific data (unmodifiable)</li>
 *   <li>events: chronological audit trail of transitions (unmodifiable)</li>
 *   <li>timestamps: creation and last modification times</li>
 * </ul>
 *
 * <p>Use {@link #newSession()} to create a fresh session, then use methods like
 * {@link #withPhase(WizardPhase, String, String)} to create derived sessions with
 * updated state. This functional style ensures auditability and enables easy
 * session rollback/replay.
 *
 * @param sessionId unique identifier for this wizard session (UUID)
 * @param currentPhase current phase of the wizard
 * @param context immutable map of context data (step outputs, configuration)
 * @param events immutable list of audit trail events
 * @param createdAt timestamp when session was created (UTC)
 * @param lastModifiedAt timestamp of most recent change (UTC)
 */
public record WizardSession(
    String sessionId,
    WizardPhase currentPhase,
    Map<String, Object> context,
    List<WizardEvent> events,
    Instant createdAt,
    Instant lastModifiedAt
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public WizardSession {
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(currentPhase, "currentPhase cannot be null");
        Objects.requireNonNull(context, "context cannot be null");
        Objects.requireNonNull(events, "events cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(lastModifiedAt, "lastModifiedAt cannot be null");

        // Defensive copies: ensure all mutable collections are unmodifiable
        context = Collections.unmodifiableMap(Map.copyOf(context));
        events = Collections.unmodifiableList(List.copyOf(events));
    }

    /**
     * Factory method: creates a fresh wizard session.
     *
     * <p>The new session is in INIT phase with empty context and no events.
     * A unique sessionId is generated automatically.
     *
     * @return new session ready to begin wizard execution
     */
    public static WizardSession newSession() {
        Instant now = Instant.now();
        return new WizardSession(
            UUID.randomUUID().toString(),
            WizardPhase.INIT,
            Map.of(),
            List.of(),
            now,
            now
        );
    }

    /**
     * Factory method: creates a fresh wizard session with initial context.
     *
     * <p>The new session is in INIT phase with the provided context data
     * and no events. Useful for resuming a wizard with pre-populated context.
     *
     * @param initialContext map of initial context (immutable copy made)
     * @return new session with pre-populated context
     * @throws NullPointerException if initialContext is null
     */
    public static WizardSession newSession(Map<String, Object> initialContext) {
        Objects.requireNonNull(initialContext, "initialContext cannot be null");
        Instant now = Instant.now();
        return new WizardSession(
            UUID.randomUUID().toString(),
            WizardPhase.INIT,
            Map.copyOf(initialContext),
            List.of(),
            now,
            now
        );
    }

    /**
     * Creates a new session with phase transition and event recorded.
     *
     * <p>Returns a new WizardSession that is identical except:
     * <ul>
     *   <li>currentPhase is updated to the new phase</li>
     *   <li>A new WizardEvent is appended to the events list</li>
     *   <li>lastModifiedAt is updated to now</li>
     * </ul>
     *
     * <p>This is the primary mechanism for advancing the wizard through phases.
     * The event provides an audit trail of the transition.
     *
     * @param nextPhase the phase to transition to
     * @param stepId identifier of the step causing the transition
     * @param message human-readable transition description
     * @return new session with updated phase and event
     * @throws NullPointerException if any parameter is null
     */
    public WizardSession withPhase(WizardPhase nextPhase, String stepId, String message) {
        Objects.requireNonNull(nextPhase, "nextPhase cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        WizardEvent event = WizardEvent.of(nextPhase, stepId, message);
        List<WizardEvent> newEvents = new ArrayList<>(this.events);
        newEvents.add(event);

        return new WizardSession(
            this.sessionId,
            nextPhase,
            this.context,
            newEvents,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * Creates a new session with a single context entry added.
     *
     * <p>Returns a new WizardSession with the given key-value pair added to context.
     * If the key already existed, its value is replaced.
     *
     * @param key the context key
     * @param value the value to associate (may be null)
     * @return new session with updated context
     * @throws NullPointerException if key is null
     */
    public WizardSession withContext(String key, Object value) {
        Objects.requireNonNull(key, "key cannot be null");

        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.put(key, value);

        return new WizardSession(
            this.sessionId,
            this.currentPhase,
            newContext,
            this.events,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * Creates a new session with multiple context entries added.
     *
     * <p>Returns a new WizardSession with all entries from the provided map
     * merged into the existing context. Existing keys are overwritten.
     *
     * @param entries map of entries to add to context (immutable copy made)
     * @return new session with updated context
     * @throws NullPointerException if entries is null
     */
    public WizardSession withContextAll(Map<String, Object> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");

        Map<String, Object> newContext = new HashMap<>(this.context);
        newContext.putAll(entries);

        return new WizardSession(
            this.sessionId,
            this.currentPhase,
            newContext,
            this.events,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * Retrieves a typed value from session context.
     *
     * <p>Performs a type-safe lookup in the context map. Returns empty if the key
     * does not exist or throws ClassCastException if the value is not of the expected type.
     *
     * @param key the context key
     * @param type the expected type of the value
     * @param <T> the type parameter
     * @return optional containing the value if present and of correct type
     * @throws NullPointerException if key or type is null
     * @throws ClassCastException if value exists but is not of expected type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(type, "type cannot be null");

        Object value = context.get(key);
        if (value == null) {
            return Optional.empty();
        }
        if (!type.isInstance(value)) {
            throw new ClassCastException(
                String.format("Context[%s] is %s but expected %s",
                    key, value.getClass().getName(), type.getName())
            );
        }
        return Optional.of((T) value);
    }

    /**
     * Checks if a context key exists.
     *
     * @param key the context key to check
     * @return true if the key is present in context
     * @throws NullPointerException if key is null
     */
    public boolean has(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return context.containsKey(key);
    }

    /**
     * Creates a new session with an event recorded (phase unchanged).
     *
     * <p>Appends an event to the audit trail without changing the phase.
     * Useful for recording actions that occur within a phase.
     *
     * @param event the event to record
     * @return new session with event appended
     * @throws NullPointerException if event is null
     */
    public WizardSession recordEvent(WizardEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        List<WizardEvent> newEvents = new ArrayList<>(this.events);
        newEvents.add(event);

        return new WizardSession(
            this.sessionId,
            this.currentPhase,
            this.context,
            newEvents,
            this.createdAt,
            Instant.now()
        );
    }

    /**
     * Gets the number of events in the audit trail.
     *
     * @return count of events recorded in this session
     */
    public int eventCount() {
        return events.size();
    }

    /**
     * Gets the most recent event, if any.
     *
     * @return optional containing last event if any exist
     */
    public Optional<WizardEvent> lastEvent() {
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(events.get(events.size() - 1));
    }

    /**
     * Gets the elapsed time since session creation (in seconds).
     *
     * @return duration in seconds
     */
    public long elapsedSeconds() {
        return java.time.temporal.ChronoUnit.SECONDS.between(createdAt, Instant.now());
    }
}
