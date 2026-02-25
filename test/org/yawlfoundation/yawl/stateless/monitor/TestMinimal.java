/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
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

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Minimal tests that don't depend on external resources.
 */
@DisplayName("Minimal Monitor Tests")
@Tag("unit")
class TestMinimal {

    @Nested
    @DisplayName("Basic CaseSnapshot Tests")
    class CaseSnapshotTests {

        @Test
        @DisplayName("Create CaseSnapshot with valid data")
        void createValidCaseSnapshot() {
            String caseId = "test-case-123";
            String specId = "test-spec-456";
            String xml = "<test>data</test>";

            java.time.Instant now = java.time.Instant.now();
            CaseSnapshot snapshot = new CaseSnapshot(caseId, specId, xml, now);

            assertEquals(caseId, snapshot.caseID());
            assertEquals(specId, snapshot.specID());
            assertEquals(xml, snapshot.marshalledXML());
            assertEquals(now, snapshot.capturedAt());

            // Age should be calculated
            assertTrue(snapshot.ageMillis() >= 0);
        }

        @Test
        @DisplayName("Create CaseSnapshot with factory method")
        void createCaseSnapshotWithFactory() {
            String caseId = "factory-case";
            String specId = "factory-spec";
            String xml = "<factory>data</factory>";

            CaseSnapshot snapshot = CaseSnapshot.of(caseId, specId, xml);

            assertEquals(caseId, snapshot.caseID());
            assertEquals(specId, snapshot.specID());
            assertEquals(xml, snapshot.marshalledXML());

            // Age should be small since we just created it
            assertTrue(snapshot.ageMillis() < 1000);
        }
    }

    @Nested
    @DisplayName("Basic YCase Tests")
    class YCaseTests {

        @Test
        @DisplayName("Create YCase with null runner")
        void createYCaseWithNullRunner() {
            // This should work - no runtime validation in constructor
            YCase yCase = new YCase(null);
            assertNull(yCase.getRunner());
        }

        @Test
        @DisplayName("Create YCase with timeout")
        void createYCaseWithTimeout() {
            // We can't test this fully without a real YNetRunner, but we can test the constructor
            // This is a minimal test that doesn't require dependencies
            assertDoesNotThrow(() -> new YCase(null, 1000));
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Reject null caseID")
        void rejectNullCaseID() {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot(null, "spec", "xml", java.time.Instant.now()));
        }

        @Test
        @DisplayName("Reject blank caseID")
        void rejectBlankCaseID() {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot("", "spec", "xml", java.time.Instant.now()));
        }

        @Test
        @DisplayName("Reject null marshalledXML")
        void rejectNullMarshalledXML() {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot("case", "spec", null, java.time.Instant.now()));
        }

        @Test
        @DisplayName("Reject null capturedAt")
        void rejectNullCapturedAt() {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot("case", "spec", "xml", null));
        }
    }
}