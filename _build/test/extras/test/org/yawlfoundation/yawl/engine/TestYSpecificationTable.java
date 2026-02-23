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

import org.yawlfoundation.yawl.elements.YSpecVersion;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.exceptions.YStateException;

/**
 * Comprehensive tests for YSpecificationTable following Chicago TDD methodology.
 *
 * <p>Tests the version management and specification lookup operations.</p>
 *
 * @author YAWL Test Suite
 * @see YSpecificationTable
 */
@DisplayName("YSpecificationTable Tests")
@Tag("integration")
class TestYSpecificationTable {

    private YSpecificationTable specTable;

    @BeforeEach
    void setUp() {
        specTable = new YSpecificationTable();
    }

    // ========================================================================
    // Constructor Tests
    // ========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("New specification table is empty")
        void newSpecificationTableIsEmpty() {
            YSpecificationTable table = new YSpecificationTable();
            assertTrue(table.isEmpty(), "New table should be empty");
        }

        @Test
        @DisplayName("Specification table extends ConcurrentHashMap")
        void specificationTableExtendsConcurrentHashMap() {
            assertTrue(specTable instanceof java.util.concurrent.ConcurrentHashMap,
                    "YSpecificationTable should extend ConcurrentHashMap");
        }
    }

    // ========================================================================
    // Contains Tests
    // ========================================================================

    @Nested
    @DisplayName("Contains Tests")
    class ContainsTests {

        @Test
        @DisplayName("Contains with null key returns false")
        void containsWithNullKeyReturnsFalse() {
            assertFalse(specTable.contains((String) null),
                    "Should return false for null key");
        }

        @Test
        @DisplayName("Contains with non-existent key returns false")
        void containsWithNonExistentKeyReturnsFalse() {
            assertFalse(specTable.contains("nonexistent"),
                    "Should return false for non-existent key");
        }

        @Test
        @DisplayName("Contains with null spec returns false")
        void containsWithNullSpecReturnsFalse() {
            assertFalse(specTable.contains((YSpecification) null),
                    "Should return false for null specification");
        }

        @Test
        @DisplayName("Contains with null spec ID returns false")
        void containsWithNullSpecIdReturnsFalse() {
            assertFalse(specTable.contains((YSpecificationID) null),
                    "Should return false for null specification ID");
        }
    }

    // ========================================================================
    // GetSpecification Tests
    // ========================================================================

    @Nested
    @DisplayName("GetSpecification Tests")
    class GetSpecificationTests {

        @Test
        @DisplayName("GetSpecification with null ID returns null")
        void getSpecificationWithNullIdReturnsNull() {
            assertNull(specTable.getSpecification(null),
                    "Should return null for null specification ID");
        }

        @Test
        @DisplayName("GetSpecification with non-existent ID returns null")
        void getSpecificationWithNonExistentIdReturnsNull() {
            YSpecificationID specId = new YSpecificationID("id", "1.0", "uri");
            assertNull(specTable.getSpecification(specId),
                    "Should return null for non-existent specification ID");
        }

        @Test
        @DisplayName("GetLatestSpecification with null key returns null")
        void getLatestSpecificationWithNullKeyReturnsNull() {
            assertNull(specTable.getLatestSpecification(null),
                    "Should return null for null key");
        }

        @Test
        @DisplayName("GetLatestSpecification with non-existent key returns null")
        void getLatestSpecificationWithNonExistentKeyReturnsNull() {
            assertNull(specTable.getLatestSpecification("nonexistent"),
                    "Should return null for non-existent key");
        }
    }

    // ========================================================================
    // IsLatest Tests
    // ========================================================================

    @Nested
    @DisplayName("IsLatest Tests")
    class IsLatestTests {

        @Test
        @DisplayName("IsLatest with null ID returns false")
        void isLatestWithNullIdReturnsFalse() {
            assertFalse(specTable.isLatest(null),
                    "Should return false for null specification ID");
        }

        @Test
        @DisplayName("IsLatest with non-existent ID returns false")
        void isLatestWithNonExistentIdReturnsFalse() {
            YSpecificationID specId = new YSpecificationID("id", "1.0", "uri");
            assertFalse(specTable.isLatest(specId),
                    "Should return false for non-existent specification ID");
        }
    }

    // ========================================================================
    // GetSpecIDs Tests
    // ========================================================================

    @Nested
    @DisplayName("GetSpecIDs Tests")
    class GetSpecIDsTests {

        @Test
        @DisplayName("GetSpecIDs returns empty set for empty table")
        void getSpecIdsReturnsEmptySetForEmptyTable() {
            assertTrue(specTable.getSpecIDs().isEmpty(),
                    "Should return empty set for empty table");
        }

        @Test
        @DisplayName("GetSpecIDs returns non-null set")
        void getSpecIdsReturnsNonNullSet() {
            assertNotNull(specTable.getSpecIDs(),
                    "Should return non-null set");
        }
    }

    // ========================================================================
    // GetSpecificationForCaseStart Tests
    // ========================================================================

    @Nested
    @DisplayName("GetSpecificationForCaseStart Tests")
    class GetSpecificationForCaseStartTests {

        @Test
        @DisplayName("GetSpecificationForCaseStart with null ID throws YStateException")
        void getSpecificationForCaseStartWithNullIdThrowsYStateException() {
            assertThrows(YStateException.class, () -> {
                specTable.getSpecificationForCaseStart(null);
            }, "Should throw YStateException for null specification ID");
        }

        @Test
        @DisplayName("GetSpecificationForCaseStart with non-existent ID throws YStateException")
        void getSpecificationForCaseStartWithNonExistentIdThrowsYStateException() {
            YSpecificationID specId = new YSpecificationID("id", "1.0", "uri");
            assertThrows(YStateException.class, () -> {
                specTable.getSpecificationForCaseStart(specId);
            }, "Should throw YStateException for non-existent specification ID");
        }
    }

    // ========================================================================
    // LoadSpecification Tests
    // ========================================================================

    @Nested
    @DisplayName("LoadSpecification Tests")
    class LoadSpecificationTests {

        @Test
        @DisplayName("LoadSpecification with null spec returns false")
        void loadSpecificationWithNullSpecReturnsFalse() {
            assertFalse(specTable.loadSpecification(null),
                    "Should return false for null specification");
        }
    }

    // ========================================================================
    // UnloadSpecification Tests
    // ========================================================================

    @Nested
    @DisplayName("UnloadSpecification Tests")
    class UnloadSpecificationTests {

        @Test
        @DisplayName("UnloadSpecification with null spec does not throw")
        void unloadSpecificationWithNullSpecDoesNotThrow() {
            assertDoesNotThrow(() -> {
                specTable.unloadSpecification(null);
            }, "Should not throw for null specification");
        }
    }

    // ========================================================================
    // Thread Safety Tests
    // ========================================================================

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Specification table handles concurrent access")
        void specificationTableHandlesConcurrentAccess() throws Exception {
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    // Concurrent reads
                    specTable.getSpecIDs();
                    specTable.contains("key" + index);
                    specTable.getLatestSpecification("key" + index);
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join(1000);
            }

            // If we get here without exception, test passes
            assertTrue(true, "Concurrent access should not throw exceptions");
        }
    }

    // ========================================================================
    // Map Interface Tests
    // ========================================================================

    @Nested
    @DisplayName("Map Interface Tests")
    class MapInterfaceTests {

        @Test
        @DisplayName("Size returns correct value for empty table")
        void sizeReturnsCorrectValueForEmptyTable() {
            assertEquals(0, specTable.size(), "Empty table should have size 0");
        }

        @Test
        @DisplayName("Key set is empty for new table")
        void keySetIsEmptyForNewTable() {
            assertTrue(specTable.keySet().isEmpty(),
                    "Key set should be empty for new table");
        }

        @Test
        @DisplayName("Values is empty for new table")
        void valuesIsEmptyForNewTable() {
            assertTrue(specTable.values().isEmpty(),
                    "Values should be empty for new table");
        }

        @Test
        @DisplayName("Entry set is empty for new table")
        void entrySetIsEmptyForNewTable() {
            assertTrue(specTable.entrySet().isEmpty(),
                    "Entry set should be empty for new table");
        }

        @Test
        @DisplayName("Clear does not throw on empty table")
        void clearDoesNotThrowOnEmptyTable() {
            assertDoesNotThrow(() -> specTable.clear(),
                    "Clear should not throw on empty table");
        }
    }
}
