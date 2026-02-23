/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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

package org.yawlfoundation.yawl.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for YNetData following Chicago TDD methodology.
 *
 * <p>Tests the data holder operations for net-level data in workflow cases.</p>
 *
 * @author YAWL Test Suite
 * @see YNetData
 */
@DisplayName("YNetData Tests")
@Tag("unit")
class TestYNetData {

    private YNetData netData;
    private String testCaseId;

    @BeforeEach
    void setUp() {
        testCaseId = "testCase123";
        netData = new YNetData(testCaseId);
    }

    // ========================================================================
    // Constructor Tests
    // ========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Default constructor creates instance")
        void defaultConstructorCreatesInstance() {
            YNetData data = new YNetData();
            assertNotNull(data, "Default constructor should create instance");
        }

        @Test
        @DisplayName("Constructor with case ID sets ID")
        void constructorWithCaseIdSetsId() {
            YNetData data = new YNetData("case456");
            assertEquals("case456", data.getId(), "Case ID should be set");
        }

        @Test
        @DisplayName("Constructor with null case ID is allowed")
        void constructorWithNullCaseIdIsAllowed() {
            YNetData data = new YNetData(null);
            assertNull(data.getId(), "ID should be null when null is passed");
        }
    }

    // ========================================================================
    // ID Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("ID Operations Tests")
    class IdOperationsTests {

        @Test
        @DisplayName("GetId returns the case ID")
        void getIdReturnsTheCaseId() {
            assertEquals(testCaseId, netData.getId(), "getId should return the case ID");
        }

        @Test
        @DisplayName("SetId updates the case ID")
        void setIdUpdatesTheCaseId() {
            netData.setId("newCase789");
            assertEquals("newCase789", netData.getId(), "setId should update the case ID");
        }

        @Test
        @DisplayName("SetId can set null")
        void setIdCanSetNull() {
            netData.setId(null);
            assertNull(netData.getId(), "setId should allow null");
        }

        @Test
        @DisplayName("SetId can set empty string")
        void setIdCanSetEmptyString() {
            netData.setId("");
            assertEquals("", netData.getId(), "setId should allow empty string");
        }
    }

    // ========================================================================
    // Data Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("Data Operations Tests")
    class DataOperationsTests {

        @Test
        @DisplayName("GetData returns null when no data set")
        void getDataReturnsNullWhenNoDataSet() {
            YNetData data = new YNetData();
            assertNull(data.getData(), "getData should return null when no data set");
        }

        @Test
        @DisplayName("SetData updates the data")
        void setDataUpdatesTheData() {
            String testData = "<data><item>value</item></data>";
            netData.setData(testData);
            assertEquals(testData, netData.getData(), "setData should update the data");
        }

        @Test
        @DisplayName("SetData can set null")
        void setDataCanSetNull() {
            netData.setData("some data");
            netData.setData(null);
            assertNull(netData.getData(), "setData should allow null");
        }

        @Test
        @DisplayName("SetData can set empty string")
        void setDataCanSetEmptyString() {
            netData.setData("");
            assertEquals("", netData.getData(), "setData should allow empty string");
        }

        @Test
        @DisplayName("SetData can set complex XML data")
        void setDataCanSetComplexXmlData() {
            String complexData = """
                <netData>
                    <variables>
                        <var name="x" type="integer">42</var>
                        <var name="y" type="string">hello</var>
                    </variables>
                </netData>
                """;
            netData.setData(complexData);
            assertEquals(complexData, netData.getData(),
                    "setData should handle complex XML data");
        }
    }

    // ========================================================================
    // Equality Tests
    // ========================================================================

    @Nested
    @DisplayName("Equality Tests")
    class EqualityTests {

        @Test
        @DisplayName("Equals returns true for same ID")
        void equalsReturnsTrueForSameId() {
            YNetData data1 = new YNetData("sameId");
            YNetData data2 = new YNetData("sameId");

            assertEquals(data1, data2, "YNetData with same ID should be equal");
        }

        @Test
        @DisplayName("Equals returns false for different ID")
        void equalsReturnsFalseForDifferentId() {
            YNetData data1 = new YNetData("id1");
            YNetData data2 = new YNetData("id2");

            assertNotEquals(data1, data2, "YNetData with different ID should not be equal");
        }

        @Test
        @DisplayName("Equals is consistent with hashCode")
        void equalsIsConsistentWithHashCode() {
            YNetData data1 = new YNetData("sameId");
            YNetData data2 = new YNetData("sameId");

            assertEquals(data1.hashCode(), data2.hashCode(),
                    "Equal objects should have equal hash codes");
        }

        @Test
        @DisplayName("Equals returns true for same instance")
        void equalsReturnsTrueForSameInstance() {
            assertEquals(netData, netData, "YNetData should equal itself");
        }

        @Test
        @DisplayName("Equals returns false for null")
        void equalsReturnsFalseForNull() {
            assertNotEquals(netData, null, "YNetData should not equal null");
        }

        @Test
        @DisplayName("Equals returns false for different type")
        void equalsReturnsFalseForDifferentType() {
            assertNotEquals(netData, "not a YNetData",
                    "YNetData should not equal different type");
        }
    }

    // ========================================================================
    // HashCode Tests
    // ========================================================================

    @Nested
    @DisplayName("HashCode Tests")
    class HashCodeTests {

        @Test
        @DisplayName("HashCode is consistent across calls")
        void hashCodeIsConsistentAcrossCalls() {
            int hash1 = netData.hashCode();
            int hash2 = netData.hashCode();
            assertEquals(hash1, hash2, "hashCode should be consistent");
        }

        @Test
        @DisplayName("HashCode for null ID uses super implementation")
        void hashCodeForNullIdUsesSuperImplementation() {
            YNetData dataWithNullId = new YNetData(null);
            // Should not throw and should return a valid hash
            assertDoesNotThrow(() -> dataWithNullId.hashCode(),
                    "hashCode with null ID should not throw");
        }
    }

    // ========================================================================
    // ToString Tests
    // ========================================================================

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("ToString returns non-null")
        void toStringReturnsNonNull() {
            assertNotNull(netData.toString(), "toString should not return null");
        }

        @Test
        @DisplayName("ToString contains ID")
        void toStringContainsId() {
            String str = netData.toString();
            assertTrue(str.contains(testCaseId), "toString should contain the ID");
        }

        @Test
        @DisplayName("ToString contains data when set")
        void toStringContainsDataWhenSet() {
            netData.setData("testData123");
            String str = netData.toString();
            assertTrue(str.contains("testData123"), "toString should contain the data");
        }

        @Test
        @DisplayName("ToString format is correct")
        void toStringFormatIsCorrect() {
            netData.setData("<xml/>");
            String str = netData.toString();
            assertTrue(str.startsWith("ID: "), "toString should start with 'ID: '");
            assertTrue(str.contains("; DATA: "), "toString should contain '; DATA: '");
        }
    }
}
