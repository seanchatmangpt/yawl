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
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Comprehensive tests for CaseSnapshot immutable record.
 * Verifies immutability, validation, age calculation, and factory methods.
 */
@DisplayName("CaseSnapshot Tests")
@Tag("monitor")
class TestCaseSnapshot {

    private static final String TEST_CASE_ID = "case123";
    private static final String TEST_SPEC_ID = "spec456";
    private static final String TEST_XML = "<case><status>running</status></case>";
    private static final Instant TEST_TIME = Instant.parse("2023-01-01T00:00:00Z");

    private CaseSnapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_XML, TEST_TIME);
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Create valid snapshot with all parameters")
        void createValidSnapshot() {
            assertEquals(TEST_CASE_ID, snapshot.caseID());
            assertEquals(TEST_SPEC_ID, snapshot.specID());
            assertEquals(TEST_XML, snapshot.marshalledXML());
            assertEquals(TEST_TIME, snapshot.capturedAt());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Reject null or blank caseID")
        void rejectNullOrBlankCaseID(String caseID) {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot(caseID, TEST_SPEC_ID, TEST_XML, TEST_TIME));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Reject null or blank specID")
        void rejectNullOrBlankSpecID(String specID) {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot(TEST_CASE_ID, specID, TEST_XML, TEST_TIME));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Reject null or blank marshalledXML")
        void rejectNullOrBlankMarshalledXML(String xml) {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, xml, TEST_TIME));
        }

        @Test
        @DisplayName("Reject null capturedAt")
        void rejectNullCapturedAt() {
            assertThrows(IllegalArgumentException.class,
                () -> new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_XML, null));
        }

        @Test
        @DisplayName("Accept edge case IDs and XML")
        void acceptEdgeCases() {
            String edgeCaseId = "case:123!special";
            String edgeSpecId = "spec:with!special#chars";
            String edgeXml = "<case><data></data></case>";

            assertDoesNotThrow(() ->
                new CaseSnapshot(edgeCaseId, edgeSpecId, edgeXml, TEST_TIME));

            CaseSnapshot edgeSnapshot = new CaseSnapshot(edgeCaseId, edgeSpecId, edgeXml, TEST_TIME);
            assertEquals(edgeCaseId, edgeSnapshot.caseID());
            assertEquals(edgeSpecId, edgeSnapshot.specID());
            assertEquals(edgeXml, edgeSnapshot.marshalledXML());
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("Create snapshot with current time using factory")
        void createWithCurrentTime() {
            String nowXml = "<current></current>";
            CaseSnapshot nowSnapshot = CaseSnapshot.of(TEST_CASE_ID, TEST_SPEC_ID, nowXml);

            assertEquals(TEST_CASE_ID, nowSnapshot.caseID());
            assertEquals(TEST_SPEC_ID, nowSnapshot.specID());
            assertEquals(nowXml, nowSnapshot.marshalledXML());

            // Should be created very recently (within 1 second)
            long age = nowSnapshot.ageMillis();
            assertTrue(age >= 0 && age <= 1000,
                "Age should be between 0 and 1000ms, but was: " + age);
        }

        @Test
        @DisplayName("Reject null parameters in factory method")
        void factoryRejectsNulls() {
            assertThrows(IllegalArgumentException.class,
                () -> CaseSnapshot.of(null, TEST_SPEC_ID, TEST_XML));
            assertThrows(IllegalArgumentException.class,
                () -> CaseSnapshot.of(TEST_CASE_ID, null, TEST_XML));
            assertThrows(IllegalArgumentException.class,
                () -> CaseSnapshot.of(TEST_CASE_ID, TEST_SPEC_ID, null));
        }
    }

    @Nested
    @DisplayName("Age Calculation")
    class AgeCalculationTests {

        @Test
        @DisplayName("Calculate age for recent snapshot")
        void calculateRecentAge() {
            Instant creationTime = Instant.now();
            CaseSnapshot recent = new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_XML, creationTime);

            // Age should be very small (within 100ms)
            long age = recent.ageMillis();
            assertTrue(age >= 0 && age < 100,
                "Recent snapshot age should be <100ms, but was: " + age);
        }

        @Test
        @DisplayName("Calculate age for old snapshot")
        void calculateOldAge() {
            Instant oldTime = Instant.now().minusSeconds(3600); // 1 hour ago
            CaseSnapshot old = new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, TEST_XML, oldTime);

            long age = old.ageMillis();
            assertTrue(age > 3500000, // at least ~59 minutes
                "Old snapshot age should be >59min, but was: " + age);
        }

        @Test
        @DisplayName("Age calculation is non-negative")
        void ageIsNonNegative() {
            long age = snapshot.ageMillis();
            assertTrue(age >= 0, "Age should never be negative");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Record is immutable - fields are final")
        void fieldsAreFinal() {
            // Records in Java automatically generate final fields
            // Test that we cannot modify field references
            assertDoesNotThrow(() -> {
                // These should compile but not modify the original
                String caseID = snapshot.caseID();
                String specID = snapshot.specID();
                String xml = snapshot.marshalledXML();
                Instant time = snapshot.capturedAt();

                // Modifying the references doesn't affect the original
                caseID = "modified";
                specID = "modified";
                xml = "modified";
                time = Instant.EPOCH;

                // Original should remain unchanged
                assertEquals(TEST_CASE_ID, snapshot.caseID());
                assertEquals(TEST_SPEC_ID, snapshot.specID());
                assertEquals(TEST_XML, snapshot.marshalledXML());
                assertEquals(TEST_TIME, snapshot.capturedAt());
            });
        }

        @Test
        @DisplayName("toString is consistent")
        void toStringConsistency() {
            String expected = "CaseSnapshot[case=" + TEST_CASE_ID +
                             ", spec=" + TEST_SPEC_ID +
                             ", capturedAt=" + TEST_TIME + "]";
            assertEquals(expected, snapshot.toString());

            // Should return same result
            assertEquals(snapshot.toString(), snapshot.toString());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handle large XML content")
        void handleLargeXML() {
            StringBuilder largeXML = new StringBuilder("<case>");
            for (int i = 0; i < 1000; i++) {
                largeXML.append("<data>").append(i).append("</data>");
            }
            largeXML.append("</case>");

            assertDoesNotThrow(() ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, largeXML.toString(), TEST_TIME));

            CaseSnapshot largeSnapshot = new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID,
                largeXML.toString(), TEST_TIME);
            assertTrue(largeSnapshot.marshalledXML().length() > 50000);
        }

        @Test
        @DisplayName("Handle special characters in XML")
        void handleSpecialCharacters() {
            String xmlWithSpecialChars = "<case><message>&lt;test&gt; &quot;quote&quot; 'apostrophe'</message></case>";
            assertDoesNotThrow(() ->
                new CaseSnapshot(TEST_CASE_ID, TEST_SPEC_ID, xmlWithSpecialChars, TEST_TIME));
        }

        @Test
        @DisplayName("Handle Unicode characters")
        void handleUnicodeCharacters() {
            String unicodeCaseId = "case_测试_123";
            String unicodeSpecId = "spec_日本語_456";
            String unicodeXml = "<case><title>안녕하세요</title></case>";

            assertDoesNotThrow(() ->
                new CaseSnapshot(unicodeCaseId, unicodeSpecId, unicodeXml, TEST_TIME));

            CaseSnapshot unicodeSnapshot = new CaseSnapshot(unicodeCaseId, unicodeSpecId,
                unicodeXml, TEST_TIME);
            assertEquals(unicodeCaseId, unicodeSnapshot.caseID());
            assertEquals(unicodeSpecId, unicodeSnapshot.specID());
        }
    }

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("Snapshot creation is fast")
        void creationIsFast() {
            long startTime = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                new CaseSnapshot("case" + i, "spec" + i, "<test/>", Instant.now());
            }
            long duration = System.nanoTime() - startTime;

            // Should complete in under 100ms (0.1ms per snapshot)
            assertTrue(duration < 100_000_000,
                "1000 snapshots should take <100ms, took: " + (duration / 1_000_000.0) + "ms");
        }

        @Test
        @DisplayName("Age calculation is efficient")
        void ageCalculationIsEfficient() {
            long startTime = System.nanoTime();
            for (int i = 0; i < 10000; i++) {
                snapshot.ageMillis();
            }
            long duration = System.nanoTime() - startTime;

            // Should complete in under 50ms (0.005ms per calculation)
            assertTrue(duration < 50_000_000,
                "10000 age calculations should take <50ms, took: " + (duration / 1_000_000.0) + "ms");
        }
    }

    @Nested
    @DisplayName("Threading")
    class ThreadingTests {

        @Test
        @DisplayName("Snapshot is thread-safe for reading")
        void threadSafeForReading() throws InterruptedException {
            final int threadCount = 10;
            final int iterations = 1000;
            final AtomicInteger counter = new AtomicInteger();

            Runnable reader = () -> {
                for (int i = 0; i < iterations; i++) {
                    // These reads should be thread-safe
                    String caseID = snapshot.caseID();
                    String specID = snapshot.specID();
                    String xml = snapshot.marshalledXML();
                    Instant time = snapshot.capturedAt();

                    assertEquals(TEST_CASE_ID, caseID);
                    assertEquals(TEST_SPEC_ID, specID);
                    assertEquals(TEST_XML, xml);
                    assertEquals(TEST_TIME, time);

                    counter.incrementAndGet();
                }
            };

            Thread[] threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                threads[i] = new Thread(reader);
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(threadCount * iterations, counter.get());
        }
    }
}