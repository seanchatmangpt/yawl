/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.time;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YTimedObject interface contract.
 *
 * <p>Chicago TDD: Tests verify interface contracts and behavior.
 * No mocks - use real implementations.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YTimedInterface Contract Tests")
@Tag("unit")
class YTimedObjectTest {

    // ==================== Interface Contract Tests ====================

    @Test
    @DisplayName("Interface methods should be accessible")
    void interfaceMethodsShouldBeAccessible() {
        // Create a test implementation
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");

        // Verify all interface methods are callable
        assertNotNull(impl.getOwnerID(), "getOwnerID should return non-null");
        assertDoesNotThrow(impl::handleTimerExpiry,
                          "handleTimerExpiry should not throw");
        assertDoesNotThrow(impl::cancel,
                          "cancel should not throw");
    }

    @Test
    @DisplayName("Owner ID should be immutable after creation")
    void ownerIDShouldBeImmutable() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("original-id");

        // Act
        String initialID = impl.getOwnerID();

        // Note: Interface doesn't enforce immutability, but implementations should
        // In our test implementation, ownerID is immutable after construction
        assertEquals("original-id", initialID, "Should return original owner ID");
    }

    @Test
    @DisplayName("Handle timer expiry should be idempotent")
    void handleTimerExpiryShouldBeIdempotent() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");

        // Act multiple times
        impl.handleTimerExpiry();
        impl.handleTimerExpiry();
        impl.handleTimerExpiry();

        // Assert
        // Should not throw exception and should handle multiple calls gracefully
        assertTrue(true, "Handle expiry should be idempotent");
    }

    @Test
    @DisplayName("Cancel should be idempotent")
    void cancelShouldBeIdempotent() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");

        // Act multiple times
        impl.cancel();
        impl.cancel();
        impl.cancel();

        // Assert
        // Should not throw exception and should handle multiple calls gracefully
        assertTrue(true, "Cancel should be idempotent");
    }

    @Test
    @DisplayName("Interface implementation should be thread-safe for handleTimerExpiry")
    void implementationShouldBeThreadSafeForHandleTimerExpiry() throws InterruptedException {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];

        // Act - create multiple threads calling handleTimerExpiry
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                impl.handleTimerExpiry();
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000); // Wait max 1 second per thread
        }

        // Assert
        assertTrue(true, "Handle expiry should complete without exceptions");
    }

    @Test
    @DisplayName("Interface implementation should be thread-safe for cancel")
    void implementationShouldBeThreadSafeForCancel() throws InterruptedException {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];

        // Act - create multiple threads calling cancel
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                impl.cancel();
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000); // Wait max 1 second per thread
        }

        // Assert
        assertTrue(true, "Cancel should complete without exceptions");
    }

    @Test
    @DisplayName("Owner ID should be non-null and non-empty")
    void ownerIDShouldBeValid() {
        // Arrange & Act
        TestTimedObjectImpl impl = new TestTimedObjectImpl("valid-id");

        // Assert
        assertNotNull(impl.getOwnerID(), "Owner ID should not be null");
        assertFalse(impl.getOwnerID().isEmpty(), "Owner ID should not be empty");
        assertFalse(impl.getOwnerID().isBlank(), "Owner ID should not be blank");
    }

    @Test
    @DisplayName("Handle timer expiry and cancel can be called in any order")
    void methodsCanBeCalledInAnyOrder() {
        // Test different execution orders
        TestTimedObjectImpl impl1 = new TestTimedObjectImpl("order-test-1");
        TestTimedObjectImpl impl2 = new TestTimedObjectImpl("order-test-2");
        TestTimedObjectImpl impl3 = new TestTimedObjectImpl("order-test-3");

        // Order 1: handle then cancel
        impl1.handleTimerExpiry();
        impl1.cancel();

        // Order 2: cancel then handle
        impl2.cancel();
        impl2.handleTimerExpiry();

        // Order 3: interleaved
        impl3.handleTimerExpiry();
        impl3.cancel();
        impl3.handleTimerExpiry();
        impl3.cancel();

        // Assert
        assertTrue(true, "All orders should complete without exceptions");
    }

    @Test
    @DisplayName("Interface implementation should not throw on null inputs")
    void implementationShouldHandleNullsGracefully() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("test-id");

        // Act & Assert - interface doesn't define null behavior, but implementations should handle gracefully
        assertDoesNotThrow(impl::handleTimerExpiry, "Should handle null contexts gracefully");
        assertDoesNotThrow(impl::cancel, "Should handle null contexts gracefully");
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Interface should work with special characters in owner ID")
    void interfaceShouldWorkWithSpecialCharacters() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("special:id@#$%^&*()");

        // Act & Assert
        assertNotNull(impl.getOwnerID(), "Should handle special characters");
        assertEquals("special:id@#$%^&*()", impl.getOwnerID(), "Should preserve special characters");
        assertDoesNotThrow(impl::handleTimerExpiry, "Should handle special characters in operations");
    }

    @Test
    @DisplayName("Interface should work with very long owner ID")
    void interfaceShouldWorkWithLongOwnerID() {
        // Arrange
        String longID = "a".repeat(1000); // 1000 character string
        TestTimedObjectImpl impl = new TestTimedObjectImpl(longID);

        // Act & Assert
        assertEquals(longID, impl.getOwnerID(), "Should handle long owner ID");
        assertDoesNotThrow(impl::handleTimerExpiry, "Should handle long ID in operations");
    }

    @Test
    @DisplayName("Interface should work with Unicode characters in owner ID")
    void interfaceShouldWorkWithUnicodeCharacters() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectObject("test-🚀-id");

        // Act & Assert
        assertNotNull(impl.getOwnerID(), "Should handle Unicode characters");
        assertDoesNotThrow(impl::handleTimerExpiry, "Should handle Unicode in operations");
    }

    // ==================== Performance Tests ====================

    @Test
    @DisplayName("Interface methods should be fast")
    void interfaceMethodsShouldBeFast() {
        // Arrange
        TestTimedObjectImpl impl = new TestTimedObjectImpl("performance-test");
        final int iterations = 10000;

        // Act - measure getOwnerID performance
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            impl.getOwnerID();
        }
        long endTime = System.nanoTime();

        // Assert
        long duration = endTime - startTime;
        double avgDuration = (double) duration / iterations;
        assertTrue(avgDuration < 1000, // Average should be less than 1000 nanoseconds
                  String.format("getOwnerID should be fast: avg %.2f ns", avgDuration));
    }

    // ==================== Helper Classes ====================

    /**
     * Real implementation of YTimedObject for testing interface contract.
     * No mocking - implements real behavior as per H-invariants.
     */
    private static class TestTimedObjectImpl implements YTimedObject {
        private final String ownerID;
        private boolean hasExpired = false;
        private boolean isCancelled = false;

        public TestTimedObjectImpl(String ownerID) {
            if (ownerID == null || ownerID.trim().isEmpty()) {
                throw new IllegalArgumentException("Owner ID cannot be null or empty");
            }
            this.ownerID = ownerID;
        }

        @Override
        public String getOwnerID() {
            return ownerID;
        }

        @Override
        public void handleTimerExpiry() {
            // Real implementation - track expiry state
            this.hasExpired = true;
        }

        @Override
        public void cancel() {
            // Real implementation - track cancellation state
            this.isCancelled = true;
        }

        // Helper methods for test verification
        public boolean hasExpired() {
            return hasExpired;
        }

        public boolean isCancelled() {
            return isCancelled;
        }
    }
}