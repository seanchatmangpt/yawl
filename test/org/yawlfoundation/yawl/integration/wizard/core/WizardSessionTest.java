package org.yawlfoundation.yawl.integration.wizard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link WizardSession}.
 *
 * <p>Tests session creation, immutability, phase transitions,
 * context management, and event recording.
 */
@DisplayName("WizardSession")
class WizardSessionTest {

    private WizardSession session;

    @BeforeEach
    void setUp() {
        session = WizardSession.newSession();
    }

    @Test
    @DisplayName("newSession creates session in INIT phase with empty context")
    void testNewSessionInit() {
        assertNotNull(session.sessionId());
        assertEquals(WizardPhase.INIT, session.currentPhase());
        assertTrue(session.context().isEmpty());
        assertTrue(session.events().isEmpty());
        assertNotNull(session.createdAt());
        assertNotNull(session.lastModifiedAt());
    }

    @Test
    @DisplayName("newSession generates unique sessionIds")
    void testNewSessionUnique() {
        WizardSession session2 = WizardSession.newSession();
        assertNotEquals(session.sessionId(), session2.sessionId());
    }

    @Test
    @DisplayName("newSession with initial context populates context")
    void testNewSessionWithContext() {
        Map<String, Object> initialContext = Map.of(
            "key1", "value1",
            "key2", 42
        );
        WizardSession s = WizardSession.newSession(initialContext);
        assertEquals("value1", s.context().get("key1"));
        assertEquals(42, s.context().get("key2"));
    }

    @Test
    @DisplayName("withPhase transitions phase and records event")
    void testWithPhase() {
        WizardSession transitioned = session.withPhase(
            WizardPhase.DISCOVERY,
            "step1",
            "Starting discovery"
        );

        // Original unchanged (immutability)
        assertEquals(WizardPhase.INIT, session.currentPhase());

        // New session has updated phase
        assertEquals(WizardPhase.DISCOVERY, transitioned.currentPhase());

        // Event recorded
        assertEquals(1, transitioned.events().size());
        WizardEvent event = transitioned.events().get(0);
        assertEquals("step1", event.stepId());
        assertEquals(WizardPhase.DISCOVERY, event.phase());
        assertEquals("Starting discovery", event.message());
    }

    @Test
    @DisplayName("withPhase preserves session ID and creation time")
    void testWithPhasePreservesIds() {
        String originalId = session.sessionId();
        Instant originalCreatedAt = session.createdAt();

        WizardSession transitioned = session.withPhase(
            WizardPhase.DISCOVERY,
            "step1",
            "Test"
        );

        assertEquals(originalId, transitioned.sessionId());
        assertEquals(originalCreatedAt, transitioned.createdAt());
        assertTrue(transitioned.lastModifiedAt().isAfter(originalCreatedAt));
    }

    @Test
    @DisplayName("withContext adds/updates single key-value pair")
    void testWithContext() {
        WizardSession updated = session.withContext("key1", "value1");

        assertEquals("value1", updated.context().get("key1"));
        assertTrue(session.context().isEmpty()); // original unchanged
    }

    @Test
    @DisplayName("withContext can overwrite existing values")
    void testWithContextOverwrite() {
        WizardSession s1 = session.withContext("key", "original");
        WizardSession s2 = s1.withContext("key", "updated");

        assertEquals("original", s1.context().get("key"));
        assertEquals("updated", s2.context().get("key"));
    }

    @Test
    @DisplayName("withContext allows null values")
    void testWithContextNull() {
        WizardSession updated = session.withContext("key", null);
        assertTrue(updated.context().containsKey("key"));
        assertNull(updated.context().get("key"));
    }

    @Test
    @DisplayName("withContextAll merges multiple entries")
    void testWithContextAll() {
        Map<String, Object> entries = Map.of(
            "a", 1,
            "b", "two",
            "c", 3.0
        );
        WizardSession updated = session.withContextAll(entries);

        assertEquals(1, updated.context().get("a"));
        assertEquals("two", updated.context().get("b"));
        assertEquals(3.0, updated.context().get("c"));
    }

    @Test
    @DisplayName("withContextAll overwrites existing keys")
    void testWithContextAllOverwrite() {
        WizardSession s1 = session.withContext("a", "original");
        WizardSession s2 = s1.withContextAll(Map.of("a", "updated", "b", "new"));

        assertEquals("original", s1.context().get("a"));
        assertEquals("updated", s2.context().get("a"));
        assertEquals("new", s2.context().get("b"));
    }

    @Test
    @DisplayName("get retrieves typed value from context")
    void testGetTyped() {
        WizardSession s = session
            .withContext("string", "hello")
            .withContext("number", 42)
            .withContext("double", 3.14);

        Optional<String> str = s.get("string", String.class);
        assertTrue(str.isPresent());
        assertEquals("hello", str.get());

        Optional<Integer> num = s.get("number", Integer.class);
        assertTrue(num.isPresent());
        assertEquals(42, num.get());

        Optional<Double> dbl = s.get("double", Double.class);
        assertTrue(dbl.isPresent());
        assertEquals(3.14, dbl.get());
    }

    @Test
    @DisplayName("get returns empty for missing key")
    void testGetMissing() {
        Optional<String> result = session.get("missing", String.class);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("get throws on type mismatch")
    void testGetTypeMismatch() {
        WizardSession s = session.withContext("key", "string value");
        assertThrows(ClassCastException.class, () -> s.get("key", Integer.class));
    }

    @Test
    @DisplayName("has checks key presence")
    void testHas() {
        WizardSession s = session.withContext("key", "value");
        assertTrue(s.has("key"));
        assertFalse(s.has("missing"));
    }

    @Test
    @DisplayName("recordEvent appends event to audit trail")
    void testRecordEvent() {
        WizardEvent event = WizardEvent.of(
            WizardPhase.DISCOVERY,
            "step1",
            "Test event"
        );
        WizardSession updated = session.recordEvent(event);

        assertEquals(1, updated.events().size());
        assertEquals(event, updated.events().get(0));
        assertEquals(WizardPhase.INIT, updated.currentPhase()); // phase unchanged
    }

    @Test
    @DisplayName("recordEvent preserves prior events")
    void testRecordEventMultiple() {
        WizardEvent event1 = WizardEvent.of(WizardPhase.INIT, "step1", "Event 1");
        WizardEvent event2 = WizardEvent.of(WizardPhase.INIT, "step2", "Event 2");

        WizardSession s1 = session.recordEvent(event1);
        WizardSession s2 = s1.recordEvent(event2);

        assertEquals(2, s2.events().size());
        assertEquals("Event 1", s2.events().get(0).message());
        assertEquals("Event 2", s2.events().get(1).message());
    }

    @Test
    @DisplayName("eventCount returns number of events")
    void testEventCount() {
        assertEquals(0, session.eventCount());

        WizardSession s1 = session.recordEvent(
            WizardEvent.of(WizardPhase.INIT, "step1", "Event")
        );
        assertEquals(1, s1.eventCount());

        WizardSession s2 = s1.recordEvent(
            WizardEvent.of(WizardPhase.INIT, "step2", "Event")
        );
        assertEquals(2, s2.eventCount());
    }

    @Test
    @DisplayName("lastEvent returns most recent event")
    void testLastEvent() {
        WizardEvent event1 = WizardEvent.of(WizardPhase.INIT, "step1", "Event 1");
        WizardEvent event2 = WizardEvent.of(WizardPhase.INIT, "step2", "Event 2");

        WizardSession s1 = session.recordEvent(event1);
        assertTrue(s1.lastEvent().isPresent());
        assertEquals("Event 1", s1.lastEvent().get().message());

        WizardSession s2 = s1.recordEvent(event2);
        assertTrue(s2.lastEvent().isPresent());
        assertEquals("Event 2", s2.lastEvent().get().message());
    }

    @Test
    @DisplayName("lastEvent returns empty when no events")
    void testLastEventEmpty() {
        assertTrue(session.lastEvent().isEmpty());
    }

    @Test
    @DisplayName("elapsedSeconds returns positive duration")
    void testElapsedSeconds() {
        long elapsed = session.elapsedSeconds();
        assertTrue(elapsed >= 0);
    }

    @Test
    @DisplayName("context is immutable")
    void testContextImmutable() {
        WizardSession s = session.withContext("key", "value");
        assertThrows(UnsupportedOperationException.class, () -> s.context().put("new", "value"));
    }

    @Test
    @DisplayName("events list is immutable")
    void testEventsImmutable() {
        WizardSession s = session.recordEvent(
            WizardEvent.of(WizardPhase.INIT, "step1", "Event")
        );
        assertThrows(UnsupportedOperationException.class, () -> s.events().add(
            WizardEvent.of(WizardPhase.INIT, "step2", "Event")
        ));
    }

    @Test
    @DisplayName("complex workflow: multiple transitions and context accumulation")
    void testComplexWorkflow() {
        // Start in INIT
        assertEquals(WizardPhase.INIT, session.currentPhase());

        // Add context
        WizardSession s1 = session
            .withContext("discovered_tools", Map.of("tool1", "config1"))
            .withPhase(WizardPhase.DISCOVERY, "discover", "Discovered tools");

        assertEquals(WizardPhase.DISCOVERY, s1.currentPhase());
        assertEquals(1, s1.eventCount());
        assertTrue(s1.has("discovered_tools"));

        // Continue
        WizardSession s2 = s1
            .withContext("selected_pattern", "Sequence")
            .withPhase(WizardPhase.PATTERN_SELECTION, "select", "Selected pattern");

        assertEquals(WizardPhase.PATTERN_SELECTION, s2.currentPhase());
        assertEquals(2, s2.eventCount());
        assertEquals("Sequence", s2.get("selected_pattern", String.class).get());

        // Original unchanged
        assertEquals(WizardPhase.INIT, session.currentPhase());
        assertFalse(session.has("discovered_tools"));
    }

    @Test
    @DisplayName("null sessionId throws in constructor")
    void testNullSessionId() {
        assertThrows(NullPointerException.class, () ->
            new WizardSession(null, WizardPhase.INIT, Map.of(), List.of(),
                Instant.now(), Instant.now())
        );
    }

    @Test
    @DisplayName("null phase throws in constructor")
    void testNullPhase() {
        assertThrows(NullPointerException.class, () ->
            new WizardSession("id", null, Map.of(), List.of(),
                Instant.now(), Instant.now())
        );
    }

    @Test
    @DisplayName("null context throws in constructor")
    void testNullContext() {
        assertThrows(NullPointerException.class, () ->
            new WizardSession("id", WizardPhase.INIT, null, List.of(),
                Instant.now(), Instant.now())
        );
    }

    @Test
    @DisplayName("withPhase with null phase throws")
    void testWithPhaseNull() {
        assertThrows(NullPointerException.class, () ->
            session.withPhase(null, "step", "message")
        );
    }

    @Test
    @DisplayName("withContext with null key throws")
    void testWithContextNullKey() {
        assertThrows(NullPointerException.class, () ->
            session.withContext(null, "value")
        );
    }

    @Test
    @DisplayName("get with null key throws")
    void testGetNullKey() {
        assertThrows(NullPointerException.class, () ->
            session.get(null, String.class)
        );
    }

    @Test
    @DisplayName("get with null type throws")
    void testGetNullType() {
        session = session.withContext("key", "value");
        assertThrows(NullPointerException.class, () ->
            session.get("key", null)
        );
    }
}
