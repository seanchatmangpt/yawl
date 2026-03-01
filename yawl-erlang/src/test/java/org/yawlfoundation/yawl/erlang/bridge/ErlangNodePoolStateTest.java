/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErlangNodePool} state management.
 * Tests pool lifecycle, closure, and error cases — no OTP connections.
 */
@Tag("unit")
@DisplayName("ErlangNodePool state tests")
class ErlangNodePoolStateTest {

    private static final String LOCAL_NODE = "yawl@localhost";
    private static final String TARGET_NODE = "erl@localhost";
    private static final String COOKIE = "secret";

    @Test
    @DisplayName("Constructor accepts valid parameters")
    void constructor_valid_parameters() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        assertNotNull(pool);
    }

    @Test
    @DisplayName("Constructor rejects blank localName")
    void constructor_blank_localName_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool("", TARGET_NODE, COOKIE, 1, 2)
        );
        assertTrue(ex.getMessage().contains("localName"));
    }

    @Test
    @DisplayName("Constructor rejects null localName")
    void constructor_null_localName_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(null, TARGET_NODE, COOKIE, 1, 2)
        );
        assertTrue(ex.getMessage().contains("localName"));
    }

    @Test
    @DisplayName("Constructor rejects blank targetNode")
    void constructor_blank_targetNode_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, "", COOKIE, 1, 2)
        );
        assertTrue(ex.getMessage().contains("targetNode"));
    }

    @Test
    @DisplayName("Constructor rejects null targetNode")
    void constructor_null_targetNode_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, null, COOKIE, 1, 2)
        );
        assertTrue(ex.getMessage().contains("targetNode"));
    }

    @Test
    @DisplayName("Constructor rejects blank cookie")
    void constructor_blank_cookie_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, "", 1, 2)
        );
        assertTrue(ex.getMessage().contains("cookie"));
    }

    @Test
    @DisplayName("Constructor rejects null cookie")
    void constructor_null_cookie_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, null, 1, 2)
        );
        assertTrue(ex.getMessage().contains("cookie"));
    }

    @Test
    @DisplayName("Constructor rejects minSize < 1")
    void constructor_minSize_zero_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 0, 2)
        );
        assertTrue(ex.getMessage().contains("minSize"));
    }

    @Test
    @DisplayName("Constructor rejects minSize negative")
    void constructor_minSize_negative_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, -1, 2)
        );
        assertTrue(ex.getMessage().contains("minSize"));
    }

    @Test
    @DisplayName("Constructor rejects maxSize < minSize")
    void constructor_maxSize_less_than_minSize_throwsException() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 5, 3)
        );
        assertTrue(ex.getMessage().contains("maxSize"));
    }

    @Test
    @DisplayName("close() does not throw exception on uninitialized pool")
    void close_uninitialized_pool_noException() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        assertDoesNotThrow(pool::close);
    }

    @Test
    @DisplayName("close() is idempotent")
    void close_twice_idempotent() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        assertDoesNotThrow(pool::close);
        assertDoesNotThrow(pool::close);
    }

    @Test
    @DisplayName("acquire() after close() throws IllegalStateException")
    void acquire_closedPool_throwsIllegalStateException() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        pool.close();

        assertThrows(
            IllegalStateException.class,
            () -> pool.acquire()
        );
    }

    @Test
    @DisplayName("release() after close() throws IllegalStateException")
    void release_closedPool_throwsIllegalStateException() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        pool.close();

        assertThrows(
            IllegalStateException.class,
            () -> pool.release(null)
        );
    }

    @Test
    @DisplayName("getAvailableCount() returns 0 before initialization")
    void getAvailableCount_uninitialized_pool_is_zero() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 3, 5);
        assertEquals(0, pool.getAvailableCount());
    }

    @Test
    @DisplayName("getTotalCount() returns 0 before initialization")
    void getTotalCount_uninitialized_pool_is_zero() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 3, 5);
        assertEquals(0, pool.getTotalCount());
    }

    @Test
    @DisplayName("Pool handles null node on release gracefully")
    void release_null_node_is_safe() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 2);
        // release(null) should be safe per implementation: checks if (node != null)
        assertDoesNotThrow(() -> pool.release(null));
    }

    @Test
    @DisplayName("Pool constructor with minSize == maxSize is valid")
    void constructor_minSize_equals_maxSize() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 5, 5);
        assertEquals(0, pool.getAvailableCount());
        assertEquals(0, pool.getTotalCount());
    }

    @Test
    @DisplayName("Pool constructor with minSize == 1 and maxSize == 1")
    void constructor_single_node_pool() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 1);
        assertNotNull(pool);
    }

    @Test
    @DisplayName("Pool enforces maxSize constraint in constructor")
    void constructor_maxSize_must_be_greater_or_equal_minSize() {
        assertDoesNotThrow(
            () -> new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 2, 10)
        );
    }

    @Test
    @DisplayName("Pool allows large maxSize values")
    void constructor_large_maxSize() {
        ErlangNodePool pool = new ErlangNodePool(LOCAL_NODE, TARGET_NODE, COOKIE, 1, 1000);
        assertNotNull(pool);
    }
}
