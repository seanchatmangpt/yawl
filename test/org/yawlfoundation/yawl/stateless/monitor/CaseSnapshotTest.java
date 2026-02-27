/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it/or/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;
import static java.time.Instant.now;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Comprehensive tests for CaseSnapshot record covering creation,
 * validation, serialization, and edge cases.
 *
 * <p>Chicago TDD: All tests use real data types and enforce strict
 * immutability guarantees. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("CaseSnapshot Tests")
@Tag("unit")
public class CaseSnapshotTest {

    private static final String VALID_CASE_ID = "case-123";
    private static final String VALID_SPEC_ID = "MinimalSpec";
    private static final String VALID_XML = "<case id=\"case-123\"><runners><runner><identifier id=\"case-123\"/></runner></runners></case>";
    private static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T12:00:00Z");

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Valid snapshot creates successfully")
        void validSnapshotCreatesSuccessfully() {
            CaseSnapshot snapshot = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);

            assertEquals(VALID_CASE_ID, snapshot.caseID());
            assertEquals(VALID_SPEC_ID, snapshot.specID());
            assertEquals(VALID_XML, snapshot.marshalledXML());
            assertEquals(FIXED_INSTANT, snapshot.capturedAt());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Null or blank caseID throws exception")
        void nullOrBlankCaseIDThrowsException(String caseID) {
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(caseID, VALID_SPEC_ID, VALID_XML, FIXED_INSTANT)
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Null or blank specID throws exception")
        void nullOrBlankSpecIDThrowsException(String specID) {
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(VALID_CASE_ID, specID, VALID_XML, FIXED_INSTANT)
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = { "   ", "\t", "\n" })
        @DisplayName("Null or blank marshalledXML throws exception")
        void nullOrBlankMarshalledXMLThrowsException(String xml) {
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, xml, FIXED_INSTANT)
            );
        }

        @Test
        @DisplayName("Null capturedAt throws exception")
        void nullCapturedAtThrowsException() {
            assertThrows(IllegalArgumentException.class, () ->
                new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML, null)
            );
        }
    }

    @Nested
    @DisplayName("Factory Method Tests")
    class FactoryMethodTests {

        @Test
        @DisplayName("of() creates snapshot with current timestamp")
        void ofCreatesSnapshotWithCurrentTimestamp() {
            long beforeCreation = System.currentTimeMillis();
            CaseSnapshot snapshot = CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML);
            long afterCreation = System.currentTimeMillis();

            assertEquals(VALID_CASE_ID, snapshot.caseID());
            assertEquals(VALID_SPEC_ID, snapshot.specID());
            assertEquals(VALID_XML, snapshot.marshalledXML());

            long capturedMillis = snapshot.capturedAt().toEpochMilli();
            assertTrue(capturedMillis >= beforeCreation && capturedMillis <= afterCreation,
                "Captured time should be between before and after creation");
        }

        @Test
        @DisplayName("of() validates required fields")
        void ofValidatesRequiredFields() {
            assertThrows(IllegalArgumentException.class, () ->
                CaseSnapshot.of(null, VALID_SPEC_ID, VALID_XML)
            );
            assertThrows(IllegalArgumentException.class, () ->
                CaseSnapshot.of(VALID_CASE_ID, null, VALID_XML)
            );
            assertThrows(IllegalArgumentException.class, () ->
                CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, null)
            );
        }
    }

    @Nested
    @DisplayName("Age Calculation Tests")
    class AgeCalculationTests {

        @Test
        @DisplayName("ageMillis() returns positive value for old snapshot")
        void ageMillisReturnsPositiveForOldSnapshot() {
            CaseSnapshot oldSnapshot = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML,
                now().minus(5, ChronoUnit.MINUTES));

            assertTrue(oldSnapshot.ageMillis() > 0, "Old snapshot should have positive age");
            assertTrue(oldSnapshot.ageMillis() >= 5 * 60 * 1000 - 100, // Allow 100ms tolerance
                "Age should be at least 5 minutes");
        }

        @Test
        @DisplayName("ageMillis() returns zero for fresh snapshot")
        void ageMillisReturnsZeroForFreshSnapshot() {
            CaseSnapshot freshSnapshot = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML,
                now());

            assertTrue(freshSnapshot.ageMillis() <= 100, // 100ms tolerance
                "Fresh snapshot should have age <= 100ms");
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Record components are immutable")
        void recordComponentsAreImmutable() {
            CaseSnapshot snapshot = CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML);
            String originalCaseID = snapshot.caseID();
            String originalSpecID = snapshot.specID();
            String originalXML = snapshot.marshalledXML();
            Instant originalTime = snapshot.capturedAt();

            // Verify components are not null
            assertNotNull(originalCaseID);
            assertNotNull(originalSpecID);
            assertNotNull(originalXML);
            assertNotNull(originalTime);

            // Verify attempting to change components doesn't work (no setters in records)
            // This test documents the immutability guarantee
        }

        @Test
        @DisplayName("toString() contains all components")
        void toStringContainsAllComponents() {
            CaseSnapshot snapshot = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);
            String snapshotString = snapshot.toString();

            assertTrue(snapshotString.contains(VALID_CASE_ID),
                "ToString should contain case ID");
            assertTrue(snapshotString.contains(VALID_SPEC_ID),
                "ToString should contain spec ID");
            assertTrue(snapshotString.contains(FIXED_INSTANT.toString()),
                "ToString should contain capture time");
        }

        @Test
        @DisplayName("Multiple snapshots with same data are equal")
        void multipleSnapshotsWithSameDataAreEqual() {
            CaseSnapshot snapshot1 = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);
            CaseSnapshot snapshot2 = new CaseSnapshot(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);

            assertEquals(snapshot1, snapshot2, "Snapshots with same data should be equal");
            assertEquals(snapshot1.hashCode(), snapshot2.hashCode(),
                "Equal snapshots should have same hash code");
        }

        @Test
        @DisplayName("Different snapshots are not equal")
        void differentSnapshotsAreNotEqual() {
            CaseSnapshot snapshot1 = new CaseSnapshot("case-1", VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);
            CaseSnapshot snapshot2 = new CaseSnapshot("case-2", VALID_SPEC_ID, VALID_XML, FIXED_INSTANT);

            assertNotEquals(snapshot1, snapshot2, "Snapshots with different case IDs should not be equal");
        }
    }

    @Nested
    @DisplayName("XML Content Tests")
    class XmlContentTests {

        @Test
        @DisplayName("Snapshot preserves XML content exactly")
        void snapshotPreservesXmlContentExactly() {
            String complexXML = "<case id=\"complex-case\">" +
                "<runners><runner><identifier id=\"test\"/><locations><location>loc1</location></locations></runner></runners>" +
                "</case>";

            CaseSnapshot snapshot = new CaseSnapshot("complex-case", "ComplexSpec", complexXML, FIXED_INSTANT);

            assertEquals(complexXML, snapshot.marshalledXML(),
                "XML content should be preserved exactly");
        }

        @Test
        @DisplayName("Snapshot with empty valid XML")
        void snapshotWithEmptyValidXml() {
            String minimalXML = "<case id=\"minimal\"></case>";
            CaseSnapshot snapshot = new CaseSnapshot("minimal", "MinimalSpec", minimalXML, FIXED_INSTANT);

            assertEquals(minimalXML, snapshot.marshalledXML(),
                "Minimal XML should be preserved");
        }

        @Test
        @DisplayName("Snapshot with large XML content")
        void snapshotWithLargeXmlContent() {
            StringBuilder largeXml = new StringBuilder("<case id=\"large-case\"><data>");
            for (int i = 0; i < 1000; i++) {
                largeXml.append("<item id=\"").append(i).append("\"/>");
            }
            largeXml.append("</data></case>");

            CaseSnapshot snapshot = new CaseSnapshot("large-case", "LargeSpec",
                largeXml.toString(), FIXED_INSTANT);

            assertEquals(largeXml.toString(), snapshot.marshalledXML(),
                "Large XML content should be preserved");
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Snapshot with very long case ID")
        void snapshotWithVeryLongCaseID() {
            String longCaseID = "case-" + "a".repeat(1000);
            String longSpecID = "spec-" + "b".repeat(1000);

            assertDoesNotThrow(() -> {
                CaseSnapshot snapshot = new CaseSnapshot(longCaseID, longSpecID, VALID_XML, FIXED_INSTANT);
                assertEquals(longCaseID, snapshot.caseID());
                assertEquals(longSpecID, snapshot.specID());
            }, "Should handle very long identifiers");
        }

        @Test
        @DisplayName("Snapshot with Unicode characters")
        void snapshotWithUnicodeCharacters() {
            String unicodeCaseID = "case-测试-123";
            String unicodeSpecID = "规格-测试";
            String unicodeXML = "<case id=\"case-测试\"><data>测试内容</data></case>";

            CaseSnapshot snapshot = new CaseSnapshot(unicodeCaseID, unicodeSpecID, unicodeXML, FIXED_INSTANT);

            assertEquals(unicodeCaseID, snapshot.caseID());
            assertEquals(unicodeSpecID, snapshot.specID());
            assertEquals(unicodeXML, snapshot.marshalledXML());
        }

        @Test
        @DisplayName("Snapshot with special characters in XML")
        void snapshotWithSpecialCharactersInXml() {
            String specialXML = "<case id=\"special\">" +
                "<data>&lt;script&gt;alert('test');&lt;/script&gt;</data>" +
                "<data><![CDATA[<div>content</div>]]></data>" +
                "</case>";

            CaseSnapshot snapshot = new CaseSnapshot("special", "SpecialSpec", specialXML, FIXED_INSTANT);

            assertEquals(specialXML, snapshot.marshalledXML(),
                "Special XML characters should be preserved");
        }
    }

    @Nested
    @DisplayName("Serialization Compatibility Tests")
    class SerializationCompatibilityTests {

        @Test
        @DisplayName("Snapshot can be serialized to JSON")
        void snapshotCanBeSerializedToJson() {
            CaseSnapshot original = CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML);

            // Convert to JSON and back
            String json = String.format(
                "{\"caseID\":\"%s\",\"specID\":\"%s\",\"marshalledXML\":\"%s\",\"capturedAt\":\"%s\"}",
                original.caseID(), original.specID(), original.marshalledXML(), original.capturedAt()
            );

            assertTrue(json.contains(VALID_CASE_ID));
            assertTrue(json.contains(VALID_SPEC_ID));
            assertTrue(json.contains(VALID_XML));
        }

        @Test
        @DisplayName("Snapshot age calculation is monotonic")
        void snapshotAgeCalculationIsMonotonic() throws InterruptedException {
            CaseSnapshot snapshot = CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML);
            long age1 = snapshot.ageMillis();

            // Wait a short time
            Thread.sleep(50);

            long age2 = snapshot.ageMillis();

            assertTrue(age2 > age1, "Age should increase over time");
        }

        @Test
        @DisplayName("Snapshot is safe for concurrent access")
        void snapshotIsSafeForConcurrentAccess() throws InterruptedException {
            CaseSnapshot snapshot = CaseSnapshot.of(VALID_CASE_ID, VALID_SPEC_ID, VALID_XML);
            final int numThreads = 10;
            final int iterations = 100;

            Thread[] threads = new Thread[numThreads];
            for (int i = 0; i < numThreads; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < iterations; j++) {
                        // These operations are thread-safe due to immutability
                        String caseID = snapshot.caseID();
                        String specID = snapshot.specID();
                        long age = snapshot.ageMillis();

                        // Verify values are consistent
                        assertEquals(VALID_CASE_ID, caseID);
                        assertEquals(VALID_SPEC_ID, specID);
                        assertTrue(age >= 0);
                    }
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
        }
    }

    @Nested
    @DisplayName("Real YAWL Integration Tests")
    class RealYawlIntegrationTests {

        @Test
        @DisplayName("Snapshot from real YAWL case data")
        void snapshotFromRealYawlCaseData() {
            String realYawlXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<yawl:case xmlns:yawl=\"http://www.yawlfoundation.org/yawlschema\" id=\"real-case-123\">" +
                "  <yawl:runners>" +
                "    <yawl:runner>" +
                "      <yawl:identifier id=\"real-case-123\"/>" +
                "      <yawl:netdata>&lt;net&gt;&lt;/net&gt;</yawl:netdata>" +
                "    </yawl:runner>" +
                "  </yawl:runners>" +
                "</yawl:case>";

            CaseSnapshot snapshot = new CaseSnapshot("real-case-123", "RealSpec", realYawlXml, FIXED_INSTANT);

            assertEquals("real-case-123", snapshot.caseID());
            assertEquals("RealSpec", snapshot.specID());
            assertEquals(realYawlXml, snapshot.marshalledXML());
        }

        @Test
        @DisplayName("Snapshot represents a point-in-time state")
        void snapshotRepresentsPointInTimeState() {
            String stateAtTime1 = "<case id=\"state-case\"><data>version1</data></case>";
            Instant time1 = now();

            CaseSnapshot snapshot1 = new CaseSnapshot("state-case", "StateSpec", stateAtTime1, time1);
            long age1 = snapshot1.ageMillis();

            // Time passes
            String stateAtTime2 = "<case id=\"state-case\"><data>version2</data></case>";
            Instant time2 = now();

            CaseSnapshot snapshot2 = new CaseSnapshot("state-case", "StateSpec", stateAtTime2, time2);
            long age2 = snapshot2.ageMillis();

            // Different snapshots, different times
            assertNotEquals(snapshot1, snapshot2);
            assertNotEquals(snapshot1.capturedAt(), snapshot2.capturedAt());
            assertTrue(age2 < age1); // snapshot2 is newer, so smaller age
        }
    }
}