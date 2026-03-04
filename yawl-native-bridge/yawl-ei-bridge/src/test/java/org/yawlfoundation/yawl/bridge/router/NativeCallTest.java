/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.bridge.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NativeCall record.
 */
class NativeCallTest {

    @Test
    @DisplayName("Create NativeCall with auto-generated correlation ID")
    void testNativeCallOf() {
        NativeCall call = NativeCall.of(
            "http://example.org/subject1",
            "http://example.org/predicate1",
            "http://example.org/object1",
            CallPattern.JVM
        );

        assertEquals("http://example.org/subject1", call.subject());
        assertEquals("http://example.org/predicate1", call.predicate());
        assertEquals("http://example.org/object1", call.object());
        assertEquals(CallPattern.JVM, call.callPattern());
        assertNotNull(call.correlationId());
    }

    @Test
    @DisplayName("Create NativeCall with specified correlation ID")
    void testNativeCallWithId() {
        UUID correlationId = UUID.randomUUID();
        NativeCall call = NativeCall.withId(
            "http://example.org/subject2",
            "http://example.org/predicate2",
            "http://example.org/object2",
            CallPattern.BEAM,
            correlationId
        );

        assertEquals(correlationId, call.correlationId());
    }

    @Test
    @DisplayName("N-triple format is correct")
    void testToNtriple() {
        NativeCall call = NativeCall.of(
            "s1",
            "p1",
            "o1",
            CallPattern.JVM
        );

        String ntriple = call.toNtriple();
        assertEquals("s1 p1 o1 .", ntriple);
    }

    @Test
    @DisplayName("Literal object is detected correctly")
    void testIsLiteralObject() {
        NativeCall uriCall = NativeCall.of(
            "s1", "p1", "http://example.org/object1", CallPattern.JVM
        );
        assertFalse(uriCall.isLiteralObject());

        NativeCall literalCall = NativeCall.of(
            "s1", "p1", "\"literal value\"", CallPattern.JVM
        );
        assertTrue(literalCall.isLiteralObject());

        NativeCall blankNodeCall = NativeCall.of(
            "s1", "p1", "_:blank1", CallPattern.JVM
        );
        assertFalse(blankNodeCall.isLiteralObject());
    }

    @Test
    @DisplayName("Execution domain description is informative")
    void testGetExecutionDomain() {
        NativeCall jvmCall = NativeCall.of("s1", "p1", "o1", CallPattern.JVM);
        assertTrue(jvmCall.getExecutionDomain().contains("JVM"));

        NativeCall beamCall = NativeCall.of("s1", "p1", "o1", CallPattern.BEAM);
        assertTrue(beamCall.getExecutionDomain().contains("BEAM"));

        NativeCall directCall = NativeCall.of("s1", "p1", "o1", CallPattern.DIRECT);
        assertTrue(directCall.getExecutionDomain().contains("blocked"));
    }

    @Test
    @DisplayName("Can execute in domain check works correctly")
    void testCanExecuteIn() {
        NativeCall jvmCall = NativeCall.of("s1", "p1", "o1", CallPattern.JVM);
        assertTrue(jvmCall.canExecuteIn(CallPattern.JVM));
        assertFalse(jvmCall.canExecuteIn(CallPattern.BEAM));

        NativeCall directCall = NativeCall.of("s1", "p1", "o1", CallPattern.DIRECT);
        assertFalse(directCall.canExecuteIn(CallPattern.DIRECT));
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Null values throw exceptions")
        void testNullValues() {
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall(null, "p1", "o1", CallPattern.JVM, null)
            );
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", null, "o1", CallPattern.JVM, null)
            );
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", "p1", null, CallPattern.JVM, null)
            );
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", "p1", "o1", null, null)
            );
        }

        @Test
        @DisplayName("Blank values throw exceptions")
        void testBlankValues() {
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("", "p1", "o1", CallPattern.JVM, null)
            );
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", "", "o1", CallPattern.JVM, null)
            );
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", "p1", "", CallPattern.JVM, null)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "subject", "simple"})
        @DisplayName("Invalid subject throws exception")
        void testInvalidSubject(String invalidSubject) {
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall(invalidSubject, "p1", "o1", CallPattern.JVM, null)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "predicate", "simple"})
        @DisplayName("Invalid predicate throws exception")
        void testInvalidPredicate(String invalidPredicate) {
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", invalidPredicate, "o1", CallPattern.JVM, null)
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {"invalid", "object", "simple"})
        @DisplayName("Invalid object throws exception")
        void testInvalidObject(String invalidObject) {
            assertThrows(IllegalArgumentException.class, () ->
                new NativeCall("s1", "p1", invalidObject, CallPattern.JVM, null)
            );
        }

        @Test
        @DisplayName("Valid subjects are accepted")
        void testValidSubjects() {
            assertDoesNotThrow(() -> new NativeCall("http://example.org/s", "p", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("urn:example:s", "p", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("_:blank", "p", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("example#s", "p", "o", CallPattern.JVM, null));
        }

        @Test
        @DisplayName("Valid predicates are accepted")
        void testValidPredicates() {
            assertDoesNotThrow(() -> new NativeCall("s", "http://example.org/p", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "urn:example:p", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "rdf:type", "o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "example#p", "o", CallPattern.JVM, null));
        }

        @Test
        @DisplayName("Valid objects are accepted")
        void testValidObjects() {
            assertDoesNotThrow(() -> new NativeCall("s", "p", "http://example.org/o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "p", "urn:example:o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "p", "_:blank", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "p", "example#o", CallPattern.JVM, null));
            assertDoesNotThrow(() -> new NativeCall("s", "p", "\"literal\"", CallPattern.JVM, null));
        }
    }
}