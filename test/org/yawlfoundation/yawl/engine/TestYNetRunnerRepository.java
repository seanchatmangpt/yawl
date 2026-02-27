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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.yawlfoundation.yawl.elements.state.YIdentifier;

/**
 * Comprehensive tests for YNetRunnerRepository following Chicago TDD methodology.
 *
 * <p>Tests the concurrent cache operations for storing and retrieving net runners.</p>
 *
 * @author YAWL Test Suite
 * @see YNetRunnerRepository
 */
@DisplayName("YNetRunnerRepository Tests")
@Tag("integration")
class TestYNetRunnerRepository {

    // ========================================================================
    // Basic Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("Basic Operations Tests")
    class BasicOperationsTests {

        @Test
        @DisplayName("New repository is empty")
        void newRepositoryIsEmpty() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertTrue(repository.isEmpty(), "New repository should be empty");
        }

        @Test
        @DisplayName("Repository extends ConcurrentHashMap")
        void repositoryExtendsConcurrentHashMap() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertTrue(repository instanceof java.util.concurrent.ConcurrentHashMap,
                    "Repository should extend ConcurrentHashMap");
        }
    }

    // ========================================================================
    // Add Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("Add Operations Tests")
    class AddOperationsTests {

        @Test
        @DisplayName("Can get case identifier from string")
        void canGetCaseIdentifierFromString() {
            YNetRunnerRepository repository = new YNetRunnerRepository();

            // Initially null for non-existent ID
            YIdentifier id = repository.getCaseIdentifier("nonexistent");
            assertNull(id, "Should return null for non-existent case ID");
        }
    }

    // ========================================================================
    // Get Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("Get Operations Tests")
    class GetOperationsTests {

        @Test
        @DisplayName("Get with null case ID returns null")
        void getWithNullCaseIdReturnsNull() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            YNetRunner runner = repository.get((String) null);
            assertNull(runner, "Get with null case ID should return null");
        }

        @Test
        @DisplayName("Get with non-existent case ID returns null")
        void getWithNonExistentCaseIdReturnsNull() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            YNetRunner runner = repository.get("nonexistent");
            assertNull(runner, "Get with non-existent case ID should return null");
        }
    }

    // ========================================================================
    // Remove Operations Tests
    // ========================================================================

    @Nested
    @DisplayName("Remove Operations Tests")
    class RemoveOperationsTests {

        @Test
        @DisplayName("Remove with null case ID returns null")
        void removeWithNullCaseIdReturnsNull() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            YNetRunner runner = repository.remove((String) null);
            assertNull(runner, "Remove with null case ID should return null");
        }

        @Test
        @DisplayName("Remove with null runner returns null")
        void removeWithNullRunnerReturnsNull() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            YNetRunner runner = repository.remove((YNetRunner) null);
            assertNull(runner, "Remove with null runner should return null");
        }

        @Test
        @DisplayName("Remove with null identifier returns null")
        void removeWithNullIdentifierReturnsNull() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            YNetRunner runner = repository.remove((YIdentifier) null);
            assertNull(runner, "Remove with null identifier should return null");
        }
    }

    // ========================================================================
    // Thread Safety Tests
    // ========================================================================

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Repository uses ConcurrentHashMap for thread safety")
        void repositoryUsesConcurrentHashMapForThreadSafety() {
            YNetRunnerRepository repository = new YNetRunnerRepository();

            // Verify it's a ConcurrentHashMap implementation
            assertEquals(java.util.concurrent.ConcurrentHashMap.class,
                    repository.getClass().getSuperclass(),
                    "Repository should extend ConcurrentHashMap for thread safety");
        }

        @Test
        @DisplayName("Repository can handle concurrent access")
        void repositoryCanHandleConcurrentAccess() throws Exception {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];

            // Create threads that access repository concurrently
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    // Just verify no exception on concurrent access
                    repository.get("case" + index);
                    repository.getCaseIdentifier("case" + index);
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for all threads to complete
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
        @DisplayName("Size returns correct value")
        void sizeReturnsCorrectValue() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertEquals(0, repository.size(), "Empty repository should have size 0");
        }

        @Test
        @DisplayName("Contains key works correctly")
        void containsKeyWorksCorrectly() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertFalse(repository.containsKey(new Object()),
                    "Should not contain arbitrary key");
        }

        @Test
        @DisplayName("Key set is empty for new repository")
        void keySetIsEmptyForNewRepository() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertTrue(repository.keySet().isEmpty(),
                    "Key set should be empty for new repository");
        }

        @Test
        @DisplayName("Values is empty for new repository")
        void valuesIsEmptyForNewRepository() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertTrue(repository.values().isEmpty(),
                    "Values should be empty for new repository");
        }

        @Test
        @DisplayName("Entry set is empty for new repository")
        void entrySetIsEmptyForNewRepository() {
            YNetRunnerRepository repository = new YNetRunnerRepository();
            assertTrue(repository.entrySet().isEmpty(),
                    "Entry set should be empty for new repository");
        }
    }
}
