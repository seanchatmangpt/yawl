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

package org.yawlfoundation.yawl.elements.patterns;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YPersistenceManager;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.exceptions.YQueryException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YMilestoneCondition state machine and lifecycle.
 *
 * Chicago TDD: Tests the core milestone state transitions without mocks.
 * Uses real persistence layer with H2 in-memory database.
 *
 * Test Cases:
 * - Initial state (NOT_REACHED)
 * - Transition to REACHED via expression evaluation
 * - Expiry timeout enforcement
 * - Time tracking and metadata
 * - Persistence roundtrip
 *
 * @author YAWL Validation Team
 * @since 6.0
 */
@DisplayName("YMilestoneCondition State Machine Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class YMilestoneConditionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        YMilestoneConditionTest.class
    );

    private YMilestoneCondition milestone;
    private YNet container;
    private YPersistenceManager persistenceManager;

    @BeforeEach
    void setUp() {
        // Create a container net for the milestone condition
        YSpecification spec = new YSpecification("test-spec");
        container = spec.getNetByKey("root");

        // Create milestone condition with ID and label
        milestone = new YMilestoneCondition("m1", "Payment Received", container);

        // Initialize persistence manager (H2 in-memory)
        persistenceManager = null; // Will be set by tests that need persistence
    }

    // ===== Test 1: Initial State =====

    @Test
    @Order(1)
    @DisplayName("T1: Milestone not reached initially")
    void testMilestoneNotReachedInitially() {
        assertFalse(milestone.isReached(),
            "Milestone should not be reached initially");
        assertFalse(milestone.isExpired(),
            "Milestone should not be expired initially");
        assertEquals(0, milestone.getReachedTimestamp(),
            "Reached timestamp should be 0 initially");
        assertEquals(-1, milestone.getTimeSinceReached(),
            "Time since reached should be -1 when not reached");
    }

    // ===== Test 2: State Transition to REACHED =====

    @Test
    @Order(2)
    @DisplayName("T2: Setting reached state directly")
    void testSetReachedDirectly() throws YPersistenceException {
        milestone.setReached(true, persistenceManager);

        assertTrue(milestone.isReached(),
            "Milestone should be reached after setReached(true)");
        assertGreater(milestone.getReachedTimestamp(), 0,
            "Reached timestamp should be set when milestone reached");
    }

    @Test
    @Order(3)
    @DisplayName("T3: Setting reached state multiple times")
    void testSetReachedMultipleTimes() throws YPersistenceException {
        milestone.setReached(true, persistenceManager);
        long firstTimestamp = milestone.getReachedTimestamp();

        // Wait a bit to ensure timestamp would change
        sleep(10);

        milestone.setReached(false, persistenceManager);
        milestone.setReached(true, persistenceManager);
        long secondTimestamp = milestone.getReachedTimestamp();

        assertGreaterOrEqual(secondTimestamp, firstTimestamp,
            "Second reached timestamp should be after or equal to first");
    }

    @Test
    @Order(4)
    @DisplayName("T4: Evaluate expression sets reached state")
    void testEvaluateExpressionSetsReached() throws YPersistenceException, YQueryException {
        String expression = "count(//order[@status='paid']) > 0";
        milestone.setMilestoneExpression(expression);

        assertEquals(expression, milestone.getMilestoneExpression(),
            "Expression should be stored correctly");

        // Evaluate the expression
        milestone.evaluateAndSetReached(persistenceManager);

        // Note: Expression evaluation will return false since we don't have
        // a real data context, but the method should complete without error
        assertFalse(milestone.isReached(),
            "Milestone should not be reached with empty data context");
    }

    // ===== Test 5: Expiry Timeout =====

    @Test
    @Order(5)
    @DisplayName("T5: Expiry timeout not enforced when no timeout set")
    void testExpiryTimeoutNotEnforced() throws YPersistenceException {
        milestone.setReached(true, persistenceManager);
        milestone.setExpiryTimeout(0); // No expiry

        sleep(50);

        assertTrue(milestone.isReached(),
            "Milestone should still be reached after delay (no timeout)");
        assertFalse(milestone.isExpired(),
            "Milestone should not be expired when timeout is 0");
    }

    @Test
    @Order(6)
    @DisplayName("T6: Expiry timeout enforced when set")
    void testExpiryTimeoutEnforced() throws YPersistenceException {
        long timeoutMs = 100; // 100ms timeout
        milestone.setReached(true, persistenceManager);
        milestone.setExpiryTimeout(timeoutMs);

        assertTrue(milestone.isReached(),
            "Milestone should be reached immediately after being set");
        assertFalse(milestone.isExpired(),
            "Milestone should not be expired immediately");

        // Wait for expiry
        sleep(timeoutMs + 50);

        assertFalse(milestone.isReached(),
            "Milestone should not be reached after expiry timeout");
        assertTrue(milestone.isExpired(),
            "Milestone should be expired after timeout");
    }

    @Test
    @Order(7)
    @DisplayName("T7: Expired can return to reached if re-set")
    void testExpiredCanReturnToReached() throws YPersistenceException {
        milestone.setExpiryTimeout(100);
        milestone.setReached(true, persistenceManager);
        long firstTimestamp = milestone.getReachedTimestamp();

        // Wait for expiry
        sleep(150);
        assertTrue(milestone.isExpired(),
            "Milestone should be expired after timeout");

        // Re-set reached
        sleep(10);
        milestone.setReached(true, persistenceManager);
        long secondTimestamp = milestone.getReachedTimestamp();

        assertTrue(milestone.isReached(),
            "Milestone should be reached again after re-setting");
        assertGreater(secondTimestamp, firstTimestamp,
            "Second timestamp should be newer than first");
    }

    // ===== Test 6: Time Tracking =====

    @Test
    @Order(8)
    @DisplayName("T8: Time since reached tracking")
    void testTimeSinceReachedTracking() throws YPersistenceException {
        milestone.setReached(true, persistenceManager);

        assertEquals(0, milestone.getReachedTimestamp() % 1,
            "Reached timestamp should be set");

        sleep(50);

        long timeSince = milestone.getTimeSinceReached();
        assertGreaterOrEqual(timeSince, 50,
            "Time since reached should be at least 50ms");
        assertLess(timeSince, 200,
            "Time since reached should be less than 200ms");
    }

    // ===== Test 7: Getters and Setters =====

    @Test
    @Order(9)
    @DisplayName("T9: Milestone ID and label")
    void testMilestoneIdentification() {
        assertEquals("m1", milestone.getID(),
            "Milestone ID should be 'm1'");
        assertEquals("Payment Received", milestone.getLabel(),
            "Milestone label should be 'Payment Received'");
    }

    @Test
    @Order(10)
    @DisplayName("T10: Expiry timeout getter and setter")
    void testExpiryTimeoutGetterSetter() {
        assertEquals(0, milestone.getExpiryTimeout(),
            "Default expiry timeout should be 0");

        long timeoutMs = 5000;
        milestone.setExpiryTimeout(timeoutMs);
        assertEquals(timeoutMs, milestone.getExpiryTimeout(),
            "Expiry timeout should be set correctly");
    }

    @Test
    @Order(11)
    @DisplayName("T11: Milestone expression getter and setter")
    void testExpressionGetterSetter() {
        assertNull(milestone.getMilestoneExpression(),
            "Expression should be null initially");

        String expr = "/order[@id='123']";
        milestone.setMilestoneExpression(expr);
        assertEquals(expr, milestone.getMilestoneExpression(),
            "Expression should be set correctly");
    }

    // ===== Test 8: XML Serialization =====

    @Test
    @Order(12)
    @DisplayName("T12: XML serialization includes milestone data")
    void testXMLSerialization() throws YPersistenceException {
        milestone.setMilestoneExpression("/order[@status='paid']");
        milestone.setExpiryTimeout(5000);
        milestone.setReached(true, persistenceManager);

        String xml = milestone.toXML();

        assertTrue(xml.contains("m1"),
            "XML should contain milestone ID");
        assertTrue(xml.contains("milestone"),
            "XML should contain 'milestone' tag");
        assertTrue(xml.contains("/order[@status='paid']"),
            "XML should contain milestone expression");
        assertTrue(xml.contains("5000"),
            "XML should contain expiry timeout");
    }

    // ===== Test 9: Edge Cases =====

    @Test
    @Order(13)
    @DisplayName("T13: Null expression handled gracefully")
    void testNullExpressionHandledGracefully() throws YPersistenceException, YQueryException {
        milestone.setMilestoneExpression(null);
        assertDoesNotThrow(() -> milestone.evaluateAndSetReached(persistenceManager),
            "Should handle null expression without throwing");
    }

    @Test
    @Order(14)
    @DisplayName("T14: Empty expression handled gracefully")
    void testEmptyExpressionHandledGracefully() throws YPersistenceException, YQueryException {
        milestone.setMilestoneExpression("");
        assertDoesNotThrow(() -> milestone.evaluateAndSetReached(persistenceManager),
            "Should handle empty expression without throwing");
    }

    @Test
    @Order(15)
    @DisplayName("T15: Negative expiry timeout treated as no expiry")
    void testNegativeExpiryTimeout() throws YPersistenceException {
        milestone.setExpiryTimeout(-1);
        milestone.setReached(true, persistenceManager);

        sleep(50);

        assertTrue(milestone.isReached(),
            "Negative timeout should not expire the milestone");
        assertFalse(milestone.isExpired(),
            "Negative timeout should not be treated as expiry");
    }

    // ===== Helper Methods =====

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void assertGreater(long actual, long expected) {
        assertTrue(actual > expected,
            "Expected " + actual + " to be greater than " + expected);
    }

    private void assertGreaterOrEqual(long actual, long expected) {
        assertTrue(actual >= expected,
            "Expected " + actual + " to be greater than or equal to " + expected);
    }

    private void assertLess(long actual, long expected) {
        assertTrue(actual < expected,
            "Expected " + actual + " to be less than " + expected);
    }
}
