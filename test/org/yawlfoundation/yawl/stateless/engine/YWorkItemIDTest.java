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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.engine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAttributeMap;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

/**
 * Comprehensive tests for YWorkItemID class.
 *
 * <p>Chicago TDD: Tests use real YWorkItemID instances and real workflow context.
 * No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YWorkItemID Tests")
@Tag("unit")
class YWorkItemIDTest {

    private YWorkItemID workItemID;
    private static final String TEST_CASE_ID = "test-case-123";
    private static final String TEST_TASK_ID = "task-456";

    @BeforeEach
    void setUp() {
        workItemID = new YWorkItemID(TEST_CASE_ID, TEST_TASK_ID);
    }

    // ==================== Basic Properties Tests ====================

    @Test
    @DisplayName("Get case ID")
    void getCaseID() {
        // Assert
        assertEquals(TEST_CASE_ID, workItemID.getCaseID());
    }

    @Test
    @DisplayName("Get task ID")
    void getTaskID() {
        // Assert
        assertEquals(TEST_TASK_ID, workItemID.getTaskID());
    }

    @Test
    @DisplayName("Get local ID")
    void getLocalID() {
        // Assert
        assertNotNull(workItemID.getLocalID());
        assertFalse(workItemID.getLocalID().isEmpty());
    }

    // ==================== Creation Tests ====================

    @Test
    @DisplayName("Create work item ID - valid parameters")
    void createWorkItemId_valid() {
        // Assert
        assertNotNull(workItemID);
        assertEquals(TEST_CASE_ID, workItemID.getCaseID());
        assertEquals(TEST_TASK_ID, workItemID.getTaskID());
    }

    @Test
    @DisplayName("Create work item ID - null case ID")
    void createWorkItemId_nullCaseId() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new YWorkItemID(null, TEST_TASK_ID);
        });
    }

    @Test
    @DisplayName("Create work item ID - empty case ID")
    void createWorkItemId_emptyCaseId() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new YWorkItemID("", TEST_TASK_ID);
        });
    }

    @Test
    @DisplayName("Create work item ID - null task ID")
    void createWorkItemId_nullTaskId() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new YWorkItemID(TEST_CASE_ID, null);
        });
    }

    @Test
    @DisplayName("Create work item ID - empty task ID")
    void createWorkItemId_emptyTaskId() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            new YWorkItemID(TEST_CASE_ID, "");
        });
    }

    // ==================== From Identifier Tests ====================

    @Test
    @DisplayName("From identifier - valid identifier")
    void fromIdentifier_valid() {
        // Arrange
        YIdentifier identifier = new YIdentifier(TEST_CASE_ID + ":" + TEST_TASK_ID);

        // Act
        YWorkItemID fromIdentifier = YWorkItemID.fromIdentifier(identifier);

        // Assert
        assertNotNull(fromIdentifier);
        assertEquals(TEST_CASE_ID, fromIdentifier.getCaseID());
        assertEquals(TEST_TASK_ID, fromIdentifier.getTaskID());
    }

    @Test
    @DisplayName("From identifier - null identifier")
    void fromIdentifier_null() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            YWorkItemID.fromIdentifier(null);
        });
    }

    @Test
    @DisplayName("From identifier - invalid format")
    void fromIdentifier_invalidFormat() {
        // Arrange
        YIdentifier identifier = new YIdentifier("invalid-format");

        // Act
        YWorkItemID fromIdentifier = YWorkItemID.fromIdentifier(identifier);

        // Assert
        // Should handle invalid format gracefully
        assertNotNull(fromIdentifier);
        // Depending on implementation, it might have default values
    }

    // ==================== To Identifier Tests ====================

    @Test
    @DisplayName("To identifier")
    void toIdentifier() {
        // Act
        YIdentifier identifier = workItemID.toIdentifier();

        // Assert
        assertNotNull(identifier);
        assertEquals(TEST_CASE_ID + ":" + TEST_TASK_ID, identifier.getID());
    }

    // ==================== Equals Tests ====================

    @Test
    @DisplayName("Equals - same object")
    void equals_sameObject() {
        // Assert
        assertEquals(workItemID, workItemID);
        assertEquals(workItemID.hashCode(), workItemID.hashCode());
    }

    @Test
    @DisplayName("Equals - equal objects")
    void equals_equalObjects() {
        // Arrange
        YWorkItemID other = new YWorkItemID(TEST_CASE_ID, TEST_TASK_ID);

        // Assert
        assertEquals(workItemID, other);
        assertEquals(workItemID.hashCode(), other.hashCode());
    }

    @Test
    @DisplayName("Equals - different case ID")
    void equals_differentCaseId() {
        // Arrange
        YWorkItemID other = new YWorkItemID("different-case", TEST_TASK_ID);

        // Assert
        assertNotEquals(workItemID, other);
    }

    @Test
    @DisplayName("Equals - different task ID")
    void equals_differentTaskId() {
        // Arrange
        YWorkItemID other = new YWorkItemID(TEST_CASE_ID, "different-task");

        // Assert
        assertNotEquals(workItemID, other);
    }

    @Test
    @DisplayName("Equals - null")
    void equals_null() {
        // Assert
        assertNotEquals(workItemID, null);
    }

    @Test
    @DisplayName("Equals - different type")
    void equals_differentType() {
        // Assert
        assertNotEquals(workItemID, "string");
    }

    // ==================== Comparison Tests ====================

    @Test
    @DisplayName("Compare to - same object")
    void compareTo_sameObject() {
        // Act
        int result = workItemID.compareTo(workItemID);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Compare to - equal object")
    void compareTo_equalObject() {
        // Arrange
        YWorkItemID other = new YWorkItemID(TEST_CASE_ID, TEST_TASK_ID);

        // Act
        int result = workItemID.compareTo(other);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("Compare to - different case ID (lexicographic)")
    void compareTo_differentCaseId() {
        // Arrange
        YWorkItemID other = new YWorkItemID("case-after", TEST_TASK_ID);

        // Act
        int result = workItemID.compareTo(other);

        // Assert
        assertTrue(result < 0); // "test-case-123" < "case-after"
    }

    @Test
    @DisplayName("Compare to - different task ID (lexicographic)")
    void compareTo_differentTaskId() {
        // Arrange
        YWorkItemID other = new YWorkItemID(TEST_CASE_ID, "task-after");

        // Act
        int result = workItemID.compareTo(other);

        // Assert
        assertTrue(result < 0); // "task-456" < "task-after"
    }

    // ==================== String Representation Tests ====================

    @Test
    @DisplayName("To string")
    void toStringTest() {
        // Act
        String str = workItemID.toString();

        // Assert
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains(TEST_CASE_ID));
        assertTrue(str.contains(TEST_TASK_ID));
    }

    @Test
    @DisplayName("To string - null values")
    void toString_nullValues() {
        // Arrange
        YWorkItemID idWithNulls = new YWorkItemID(null, null);

        // Act
        String str = idWithNulls.toString();

        // Assert
        assertNotNull(str);
        // Should handle null values gracefully
    }

    @Test
    @DisplayName("To string - empty values")
    void toString_emptyValues() {
        // Arrange
        YWorkItemID idWithEmpty = new YWorkItemID("", "");

        // Act
        String str = idWithEmpty.toString();

        // Assert
        assertNotNull(str);
        // Should handle empty values gracefully
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Validate - valid ID")
    void validate_valid() {
        // Act & Assert
        assertDoesNotThrow(() -> workItemID.validate());
    }

    @Test
    @DisplayName("Validate - null case ID")
    void validate_nullCaseId() {
        // Arrange
        YWorkItemID invalid = new YWorkItemID(null, TEST_TASK_ID);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - empty case ID")
    void validate_emptyCaseId() {
        // Arrange
        YWorkItemID invalid = new YWorkItemID("", TEST_TASK_ID);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - null task ID")
    void validate_nullTaskId() {
        // Arrange
        YWorkItemID invalid = new YWorkItemID(TEST_CASE_ID, null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    @Test
    @DisplayName("Validate - empty task ID")
    void validate_emptyTaskId() {
        // Arrange
        YWorkItemID invalid = new YWorkItemID(TEST_CASE_ID, "");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> invalid.validate());
    }

    // ==================== Serialization Tests ====================

    @Test
    @DisplayName("Serialize and deserialize")
    void serializeAndDeserialize() {
        // Act
        String serialized = workItemID.serialize();
        YWorkItemID deserialized = YWorkItemID.deserialize(serialized);

        // Assert
        assertNotNull(serialized);
        assertNotNull(deserialized);
        assertEquals(workItemID, deserialized);
        assertEquals(workItemID.hashCode(), deserialized.hashCode());
    }

    @Test
    @DisplayName("Serialize - null values")
    void serialize_nullValues() {
        // Arrange
        YWorkItemID idWithNulls = new YWorkItemID(null, null);

        // Act
        String serialized = idWithNulls.serialize();

        // Assert
        assertNotNull(serialized);
        // Should handle null values in serialization
    }

    @Test
    @DisplayName("Deserialize - null string")
    void deserialize_nullString() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.deserialize(null);
        });
    }

    @Test
    @DisplayName("Deserialize - empty string")
    void deserialize_emptyString() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.deserialize("");
        });
    }

    @Test
    @DisplayName("Deserialize - invalid format")
    void deserialize_invalidFormat() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.deserialize("invalid-format");
        });
    }

    // ==================== Factory Method Tests ====================

    @Test
    @DisplayName("Create - valid parameters")
    void create_valid() {
        // Act
        YWorkItemID created = YWorkItemID.create(TEST_CASE_ID, TEST_TASK_ID);

        // Assert
        assertNotNull(created);
        assertEquals(TEST_CASE_ID, created.getCaseID());
        assertEquals(TEST_TASK_ID, created.getTaskID());
    }

    @Test
    @DisplayName("Create - null case ID")
    void create_nullCaseId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.create(null, TEST_TASK_ID);
        });
    }

    @Test
    @DisplayName("Create - empty case ID")
    void create_emptyCaseId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.create("", TEST_TASK_ID);
        });
    }

    @Test
    @DisplayName("Create - null task ID")
    void create_nullTaskId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.create(TEST_CASE_ID, null);
        });
    }

    @Test
    @DisplayName("Create - empty task ID")
    void create_emptyTaskId() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.create(TEST_CASE_ID, "");
        });
    }

    // ==================== Attribute Map Tests ====================

    @Test
    @DisplayName("To attribute map")
    void toAttributeMap() {
        // Act
        YAttributeMap attrMap = workItemID.toAttributeMap();

        // Assert
        assertNotNull(attrMap);
        assertEquals(TEST_CASE_ID, attrMap.get("caseID"));
        assertEquals(TEST_TASK_ID, attrMap.get("taskID"));
    }

    @Test
    @DisplayName("From attribute map")
    void fromAttributeMap() {
        // Arrange
        YAttributeMap attrMap = new YAttributeMap();
        attrMap.put("caseID", TEST_CASE_ID);
        attrMap.put("taskID", TEST_TASK_ID);

        // Act
        YWorkItemID fromMap = YWorkItemID.fromAttributeMap(attrMap);

        // Assert
        assertNotNull(fromMap);
        assertEquals(TEST_CASE_ID, fromMap.getCaseID());
        assertEquals(TEST_TASK_ID, fromMap.getTaskID());
    }

    @Test
    @DisplayName("From attribute map - null map")
    void fromAttributeMap_nullMap() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.fromAttributeMap(null);
        });
    }

    @Test
    @DisplayName("From attribute map - missing case ID")
    void fromAttributeMap_missingCaseId() {
        // Arrange
        YAttributeMap attrMap = new YAttributeMap();
        attrMap.put("taskID", TEST_TASK_ID);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.fromAttributeMap(attrMap);
        });
    }

    @Test
    @DisplayName("From attribute map - missing task ID")
    void fromAttributeMap_missingTaskId() {
        // Arrange
        YAttributeMap attrMap = new YAttributeMap();
        attrMap.put("caseID", TEST_CASE_ID);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            YWorkItemID.fromAttributeMap(attrMap);
        });
    }
}