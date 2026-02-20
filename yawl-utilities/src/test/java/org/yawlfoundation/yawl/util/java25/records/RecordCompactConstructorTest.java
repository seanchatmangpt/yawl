package org.yawlfoundation.yawl.util.java25.records;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Record compact constructors.
 *
 * Chicago TDD: Validates that compact constructors perform real validation logic.
 * Tests parameter normalization, defensive copying, and exception throwing.
 */
@DisplayName("Record Compact Constructor Validation")
class RecordCompactConstructorTest {

    record CaseIdentifier(String caseId, String taskId) {
        public CaseIdentifier {
            if (caseId == null || caseId.isBlank()) {
                throw new IllegalArgumentException("caseId must not be null or blank");
            }
            if (taskId == null || taskId.isBlank()) {
                throw new IllegalArgumentException("taskId must not be null or blank");
            }
            caseId = caseId.trim().toUpperCase();
            taskId = taskId.trim();
        }
    }

    record WorkStatus(String status, int priority) {
        public WorkStatus {
            if (status == null) {
                throw new IllegalArgumentException("status cannot be null");
            }
            if (!isValidStatus(status)) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            if (priority < 0 || priority > 10) {
                throw new IllegalArgumentException("priority must be 0-10, got " + priority);
            }
        }

        private static boolean isValidStatus(String s) {
            return s.matches("^(pending|active|completed|failed|paused)$");
        }
    }

    record PageSize(int pageNumber, int pageSize) {
        public PageSize {
            if (pageNumber < 1) {
                throw new IllegalArgumentException("pageNumber must be >= 1");
            }
            if (pageSize < 1 || pageSize > 1000) {
                throw new IllegalArgumentException("pageSize must be 1-1000");
            }
        }
    }

    record DefensiveCopyList(String id, List<String> items) {
        public DefensiveCopyList {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            if (items == null) {
                items = Collections.emptyList();
            } else {
                items = new ArrayList<>(items);
            }
        }
    }

    @Test
    @DisplayName("Compact constructor validates required fields")
    void testCompactConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier(null, "task1"));
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier("", "task1"));
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier("  ", "task1"));
    }

    @Test
    @DisplayName("Compact constructor validates both parameters")
    void testCompactConstructorValidatesBothFields() {
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier("case1", null));
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier("case1", ""));
        assertThrows(IllegalArgumentException.class, () -> new CaseIdentifier("case1", "  "));
    }

    @Test
    @DisplayName("Compact constructor normalizes string values")
    void testCompactConstructorNormalization() {
        CaseIdentifier ci = new CaseIdentifier("  case-123  ", "  task-a  ");

        assertEquals("CASE-123", ci.caseId());
        assertEquals("task-a", ci.taskId());
    }

    @Test
    @DisplayName("Compact constructor with pattern validation")
    void testCompactConstructorPatternValidation() {
        WorkStatus valid = new WorkStatus("pending", 5);
        assertEquals("pending", valid.status());

        assertThrows(IllegalArgumentException.class, () -> new WorkStatus("invalid", 5));
        assertThrows(IllegalArgumentException.class, () -> new WorkStatus("PENDING", 5));
    }

    @Test
    @DisplayName("Compact constructor validates numeric ranges")
    void testCompactConstructorNumericValidation() {
        WorkStatus valid = new WorkStatus("active", 0);
        assertEquals(0, valid.priority());

        WorkStatus maxPriority = new WorkStatus("completed", 10);
        assertEquals(10, maxPriority.priority());

        assertThrows(IllegalArgumentException.class, () -> new WorkStatus("active", -1));
        assertThrows(IllegalArgumentException.class, () -> new WorkStatus("active", 11));
    }

    @Test
    @DisplayName("Compact constructor validates pagination parameters")
    void testCompactConstructorPaginationValidation() {
        PageSize valid = new PageSize(1, 1);
        PageSize maxPage = new PageSize(1000000, 1000);

        assertEquals(1, valid.pageNumber());
        assertEquals(1, valid.pageSize());

        assertThrows(IllegalArgumentException.class, () -> new PageSize(0, 50));
        assertThrows(IllegalArgumentException.class, () -> new PageSize(-1, 50));
        assertThrows(IllegalArgumentException.class, () -> new PageSize(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PageSize(1, 1001));
    }

    @Test
    @DisplayName("Compact constructor creates defensive copies of collections")
    void testCompactConstructorDefensiveCopy() {
        List<String> originalList = new ArrayList<>(List.of("a", "b", "c"));
        DefensiveCopyList dcl = new DefensiveCopyList("id1", originalList);

        originalList.add("d");

        assertEquals(3, dcl.items().size());
        assertNotSame(originalList, dcl.items());
    }

    @Test
    @DisplayName("Compact constructor handles null collections gracefully")
    void testCompactConstructorNullCollectionHandling() {
        DefensiveCopyList dcl = new DefensiveCopyList("id2", null);

        assertNotNull(dcl.items());
        assertTrue(dcl.items().isEmpty());
    }

    @Test
    @DisplayName("Compact constructor exception message is descriptive")
    void testCompactConstructorExceptionMessages() {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> new PageSize(1, 2000)
        );

        assertTrue(ex.getMessage().contains("pageSize"));
        assertTrue(ex.getMessage().contains("1-1000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "active", "completed", "failed", "paused"})
    @DisplayName("Compact constructor accepts all valid statuses")
    void testCompactConstructorValidStatuses(String status) {
        WorkStatus ws = new WorkStatus(status, 5);
        assertEquals(status, ws.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PENDING", "Active", "COMPLETED", "unknown"})
    @DisplayName("Compact constructor rejects invalid statuses")
    void testCompactConstructorInvalidStatuses(String status) {
        assertThrows(
            IllegalArgumentException.class,
            () -> new WorkStatus(status, 5)
        );
    }

    @Test
    @DisplayName("Compact constructor runs before field assignment")
    void testCompactConstructorFieldAssignment() {
        CaseIdentifier ci = new CaseIdentifier("lowercase-case", "task");

        assertEquals("LOWERCASE-CASE", ci.caseId());
        assertEquals("task", ci.taskId());
    }

    @Test
    @DisplayName("Multiple compact constructor calls validate independently")
    void testMultipleCompactConstructorInstances() {
        CaseIdentifier c1 = new CaseIdentifier("case1", "task1");
        CaseIdentifier c2 = new CaseIdentifier("case2", "task2");
        CaseIdentifier c3 = new CaseIdentifier("case3", "task3");

        assertNotEquals(c1, c2);
        assertNotEquals(c2, c3);
        assertNotEquals(c1, c3);
    }

    @Test
    @DisplayName("Compact constructor validation prevents invalid state")
    void testCompactConstructorPreventsInvalidState() {
        WorkStatus ws1 = new WorkStatus("pending", 1);
        WorkStatus ws2 = new WorkStatus("active", 5);
        WorkStatus ws3 = new WorkStatus("completed", 10);

        assertTrue(ws1.priority() >= 0 && ws1.priority() <= 10);
        assertTrue(ws2.priority() >= 0 && ws2.priority() <= 10);
        assertTrue(ws3.priority() >= 0 && ws3.priority() <= 10);
    }

    @Test
    @DisplayName("Compact constructor immutability verification")
    void testCompactConstructorImmutability() {
        DefensiveCopyList dcl = new DefensiveCopyList("id", List.of("x", "y"));

        List<String> items = dcl.items();
        assertThrows(UnsupportedOperationException.class, () -> items.add("z"));
    }
}
