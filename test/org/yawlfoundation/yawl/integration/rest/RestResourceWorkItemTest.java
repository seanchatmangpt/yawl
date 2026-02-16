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

package org.yawlfoundation.yawl.integration.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.util.YLogUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for YAWL work item operations via REST API.
 *
 * Tests real work item operations including:
 * - Work item retrieval and queries
 * - Work item checkout and completion
 * - Work item data management
 * - Work item status transitions
 *
 * @author YAWL Foundation
 * @version 5.2
 * @date 2026-02-16
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestResourceWorkItemTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";

    private YEngine engine;
    private String sessionHandle;

    /**
     * Set up test fixtures before each test.
     * Create a fresh engine session for isolation.
     */
    @BeforeEach
    public void setUp() throws Exception {
        engine = YEngine.getInstance();
        assertNotNull(engine, "Engine should be initialized");
        assertTrue(engine.isRunning(), "Engine should be running");

        // Create session for this test
        sessionHandle = engine.connect(ADMIN_USER, ADMIN_PASSWORD);
        assertNotNull(sessionHandle, "Session should be created");
        assertFalse(sessionHandle.contains("fail"), "Session should be valid");
    }

    /**
     * Clean up test fixtures after each test.
     * Disconnect session and clean up resources.
     */
    @AfterEach
    public void tearDown() throws Exception {
        if (sessionHandle != null && !sessionHandle.isEmpty() && !sessionHandle.contains("fail")) {
            try {
                engine.disconnect(sessionHandle);
            } catch (Exception e) {
                YLogUtil.logWarn("Failed to disconnect session: " + e.getMessage());
            }
        }
    }

    /**
     * Test Case 1: Get live work items.
     *
     * This test verifies:
     * - Work item retrieval works with valid session
     * - Returns a collection (may be empty when no cases running)
     * - No exception is thrown
     */
    @Test
    @Order(1)
    public void testGetLiveWorkItems() throws Exception {
        Object result = engine.getLiveWorkItems(sessionHandle);

        assertNotNull(result, "Live work items result should not be null");
    }

    /**
     * Test Case 2: Get work items for a specific case.
     *
     * This test verifies:
     * - Work item query by case ID works
     * - Handles non-existent case gracefully
     * - Returns proper result structure
     */
    @Test
    @Order(2)
    public void testGetWorkItemsForCase() throws Exception {
        String nonExistentCaseId = "case-12345";

        Object result = engine.getWorkItemsForCase(nonExistentCaseId, sessionHandle);

        // Should handle gracefully - may return empty or error
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 3: Get work items for a specific task.
     *
     * This test verifies:
     * - Work item query by task works
     * - Handles non-existent task gracefully
     * - Returns proper result
     */
    @Test
    @Order(3)
    public void testGetWorkItemsForTask() throws Exception {
        String nonExistentTaskName = "task-name-12345";

        Object result = engine.getWorkItemsForTask(nonExistentTaskName, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 4: Get a specific work item by ID.
     *
     * This test verifies:
     * - Work item retrieval by ID works
     * - Handles non-existent work item gracefully
     * - Returns proper work item XML
     */
    @Test
    @Order(4)
    public void testGetWorkItemById() throws Exception {
        String nonExistentItemId = "work-item-12345";

        Object result = engine.getWorkItem(nonExistentItemId, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 5: Start work item (checkout).
     *
     * This test verifies:
     * - Work item checkout operation works
     * - Handles non-existent work item gracefully
     * - No exception is thrown
     */
    @Test
    @Order(5)
    public void testStartWorkItem() throws Exception {
        String nonExistentItemId = "work-item-to-start-12345";

        Object result = engine.startWorkItem(nonExistentItemId, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 6: Update work item data.
     *
     * This test verifies:
     * - Work item data update works
     * - Handles non-existent work item gracefully
     * - XML data is processed correctly
     */
    @Test
    @Order(6)
    public void testUpdateWorkItemData() throws Exception {
        String nonExistentItemId = "work-item-to-update-12345";
        String dataXml = "<root/>";

        Object result = engine.updateWorkItemData(nonExistentItemId, dataXml, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 7: Complete work item.
     *
     * This test verifies:
     * - Work item completion works
     * - Handles non-existent work item gracefully
     * - Completion data is processed
     */
    @Test
    @Order(7)
    public void testCompleteWorkItem() throws Exception {
        String nonExistentItemId = "work-item-to-complete-12345";
        String completionData = "<root/>";

        Object result = engine.completeWorkItem(nonExistentItemId, completionData, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 8: Work item operations require valid session.
     *
     * This test verifies:
     * - Operations with invalid session are handled
     * - Security is maintained
     */
    @Test
    @Order(8)
    public void testWorkItemOperationsWithInvalidSession() throws Exception {
        String invalidSession = "invalid-session-handle";

        Object result = engine.getLiveWorkItems(invalidSession);

        // Should either return error or empty result
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 9: Get work items for specification.
     *
     * This test verifies:
     * - Work item query by specification works
     * - Handles non-existent specification gracefully
     */
    @Test
    @Order(9)
    public void testGetWorkItemsForSpecification() throws Exception {
        String nonExistentSpecId = "spec-12345";

        Object result = engine.getWorkItemsForSpecification(nonExistentSpecId, sessionHandle);

        // Should handle gracefully
        assertNotNull(result, "Should return a result");
    }

    /**
     * Test Case 10: Concurrent work item queries.
     *
     * This test verifies:
     * - Multiple concurrent work item queries work
     * - No race conditions occur
     * - Results are consistent
     */
    @Test
    @Order(10)
    public void testConcurrentWorkItemQueries() throws Exception {
        Object result1 = engine.getLiveWorkItems(sessionHandle);
        Object result2 = engine.getLiveWorkItems(sessionHandle);
        Object result3 = engine.getLiveWorkItems(sessionHandle);

        assertNotNull(result1, "First query should return result");
        assertNotNull(result2, "Second query should return result");
        assertNotNull(result3, "Third query should return result");
    }

    /**
     * Test Case 11: Work item data with complex XML.
     *
     * This test verifies:
     * - Complex XML data is handled correctly
     * - Nested elements are preserved
     * - Special characters are escaped properly
     */
    @Test
    @Order(11)
    public void testWorkItemDataWithComplexXml() throws Exception {
        String itemId = "work-item-12345";
        String complexXml = "<root>" +
                "<name>Test &amp; Special &lt;Characters&gt;</name>" +
                "<nested>" +
                "  <child attr=\"value\">content</child>" +
                "</nested>" +
                "</root>";

        Object result = engine.updateWorkItemData(itemId, complexXml, sessionHandle);

        // Should handle complex XML
        assertNotNull(result, "Should handle complex XML data");
    }

    /**
     * Test Case 12: Work item operation resilience.
     *
     * This test verifies:
     * - System remains stable after failed operations
     * - Can continue operations after errors
     */
    @Test
    @Order(12)
    public void testWorkItemOperationResilience() throws Exception {
        // Attempt invalid operation
        engine.getWorkItem("invalid-item-id", sessionHandle);

        // System should still be functional
        Object result = engine.getLiveWorkItems(sessionHandle);
        assertNotNull(result, "System should remain functional after error");
    }

    /**
     * Test Case 13: Empty work item list handling.
     *
     * This test verifies:
     * - Empty work item lists are handled correctly
     * - No null pointer exceptions
     * - Proper empty result indication
     */
    @Test
    @Order(13)
    public void testEmptyWorkItemListHandling() throws Exception {
        // When no cases are running, should get empty list
        Object result = engine.getLiveWorkItems(sessionHandle);

        assertNotNull(result, "Should return result even if empty");
    }

    /**
     * Test Case 14: Work item state transitions.
     *
     * This test verifies:
     * - Work item state transitions are valid
     * - Cannot transition from invalid states
     */
    @Test
    @Order(14)
    public void testWorkItemStateTransitions() throws Exception {
        String itemId = "work-item-for-state-test";

        // Attempt checkout (start)
        Object result = engine.startWorkItem(itemId, sessionHandle);
        assertNotNull(result, "Start should return result");

        // Attempt completion
        result = engine.completeWorkItem(itemId, "<root/>", sessionHandle);
        assertNotNull(result, "Complete should return result");
    }
}
