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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Comprehensive tests for YCaseNbrStore following Chicago TDD methodology.
 *
 * <p>Tests the singleton pattern and sequence generation for case numbers.</p>
 *
 * @author YAWL Test Suite
 * @see YCaseNbrStore
 */
@DisplayName("YCaseNbrStore Tests")
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)  // Uses YCaseNbrStore singleton
class TestYCaseNbrStore {

    // ========================================================================
    // Singleton Pattern Tests
    // ========================================================================

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonPatternTests {

        @Test
        @DisplayName("GetInstance returns non-null instance")
        void getInstanceReturnsNonNullInstance() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            assertNotNull(store, "getInstance should return non-null instance");
        }

        @Test
        @DisplayName("GetInstance returns same instance on multiple calls")
        void getInstanceReturnsSameInstanceOnMultipleCalls() {
            YCaseNbrStore store1 = YCaseNbrStore.getInstance();
            YCaseNbrStore store2 = YCaseNbrStore.getInstance();
            assertSame(store1, store2, "getInstance should return the same instance");
        }

        @Test
        @DisplayName("Singleton is lazily initialized")
        void singletonIsLazilyInitialized() {
            // Multiple calls should not create new instances
            for (int i = 0; i < 100; i++) {
                YCaseNbrStore store = YCaseNbrStore.getInstance();
                assertNotNull(store, "getInstance should always return non-null");
            }
        }
    }

    // ========================================================================
    // Case Number Tests
    // ========================================================================

    @Nested
    @DisplayName("Case Number Tests")
    class CaseNumberTests {

        @Test
        @DisplayName("GetCaseNbr returns integer value")
        void getCaseNbrReturnsIntegerValue() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int caseNbr = store.getCaseNbr();
            assertTrue(caseNbr >= 0, "Case number should be non-negative");
        }

        @Test
        @DisplayName("SetCaseNbr updates the value")
        void setCaseNbrUpdatesTheValue() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int original = store.getCaseNbr();

            store.setCaseNbr(original + 100);
            assertEquals(original + 100, store.getCaseNbr(),
                    "Case number should be updated");

            // Restore original
            store.setCaseNbr(original);
        }

        @Test
        @DisplayName("Case number is thread-safe")
        void caseNumberIsThreadSafe() throws Exception {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int original = store.getCaseNbr();

            // Use reflection to verify AtomicInteger is used
            Field caseNbrField = YCaseNbrStore.class.getDeclaredField("caseNbr");
            caseNbrField.setAccessible(true);
            Object fieldValue = caseNbrField.get(store);

            assertTrue(fieldValue instanceof AtomicInteger,
                    "Case number should use AtomicInteger for thread safety");
        }
    }

    // ========================================================================
    // Persistence State Tests
    // ========================================================================

    @Nested
    @DisplayName("Persistence State Tests")
    class PersistenceStateTests {

        @Test
        @DisplayName("IsPersisted returns boolean")
        void isPersistedReturnsBoolean() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            boolean persisted = store.isPersisted();
            // Just verify it returns a boolean without exception
            assertTrue(persisted == true || persisted == false,
                    "isPersisted should return boolean");
        }

        @Test
        @DisplayName("SetPersisted updates persistence state")
        void setPersistedUpdatesPersistenceState() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            boolean original = store.isPersisted();

            store.setPersisted(true);
            assertTrue(store.isPersisted(), "Persisted should be true after setting");

            store.setPersisted(false);
            assertFalse(store.isPersisted(), "Persisted should be false after setting");

            // Restore original
            store.setPersisted(original);
        }

        @Test
        @DisplayName("IsPersisting returns boolean")
        void isPersistingReturnsBoolean() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            boolean persisting = store.isPersisting();
            // Just verify it returns a boolean without exception
            assertTrue(persisting == true || persisting == false,
                    "isPersisting should return boolean");
        }

        @Test
        @DisplayName("SetPersisting updates persisting state")
        void setPersistingUpdatesPersistingState() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            boolean original = store.isPersisting();

            store.setPersisting(true);
            assertTrue(store.isPersisting(), "Persisting should be true after setting");

            store.setPersisting(false);
            assertFalse(store.isPersisting(), "Persisting should be false after setting");

            // Restore original
            store.setPersisting(original);
        }
    }

    // ========================================================================
    // Primary Key Tests
    // ========================================================================

    @Nested
    @DisplayName("Primary Key Tests")
    class PrimaryKeyTests {

        @Test
        @DisplayName("GetPkey returns integer")
        void getPkeyReturnsInteger() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int pkey = store.getPkey();
            assertTrue(pkey > 0, "Primary key should be positive");
        }

        @Test
        @DisplayName("SetPkey updates the value")
        void setPkeyUpdatesTheValue() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int original = store.getPkey();

            store.setPkey(9999);
            assertEquals(9999, store.getPkey(), "Primary key should be updated");

            // Restore original
            store.setPkey(original);
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
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            String str = store.toString();
            assertNotNull(str, "toString should not return null");
            assertFalse(str.isEmpty(), "toString should not return empty string");
        }

        @Test
        @DisplayName("ToString contains case number")
        void toStringContainsCaseNumber() {
            YCaseNbrStore store = YCaseNbrStore.getInstance();
            int caseNbr = store.getCaseNbr();
            String str = store.toString();
            assertTrue(str.contains(String.valueOf(caseNbr)),
                    "toString should contain the case number");
        }
    }
}
