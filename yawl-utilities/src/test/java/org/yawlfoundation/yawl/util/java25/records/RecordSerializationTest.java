package org.yawlfoundation.yawl.util.java25.records;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive serialization/deserialization tests for Records.
 *
 * Chicago TDD: Tests real record serialization via Java object streams.
 * Validates that records properly implement Serializable contract.
 */
@DisplayName("Record Serialization and Deserialization")
class RecordSerializationTest {

    record WorkItem(
        String caseId,
        String taskId,
        String status,
        Instant createdAt,
        Map<String, Object> data
    ) implements Serializable {
    }

    record WorkflowEvent(
        String eventId,
        String eventType,
        long timestamp,
        String sourceTask,
        List<String> affectedItems
    ) implements Serializable {
    }

    record PagedResult<T>(
        List<T> items,
        int totalPages,
        int currentPage,
        long totalCount
    ) implements Serializable {
    }

    private ByteArrayOutputStream outputStream;
    private ObjectOutputStream objectOutput;

    @BeforeEach
    void setUp() throws IOException {
        outputStream = new ByteArrayOutputStream();
        objectOutput = new ObjectOutputStream(outputStream);
    }

    @Test
    @DisplayName("Serialize and deserialize simple record")
    void testSimpleRecordSerialization() throws IOException, ClassNotFoundException {
        Instant now = Instant.now();
        WorkItem original = new WorkItem("case-001", "task-A", "active", now, Map.of("key", "value"));

        objectOutput.writeObject(original);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkItem restored = (WorkItem) objectInput.readObject();

        assertEquals(original.caseId(), restored.caseId());
        assertEquals(original.taskId(), restored.taskId());
        assertEquals(original.status(), restored.status());
        assertEquals(original.createdAt(), restored.createdAt());
        assertEquals(original.data(), restored.data());
    }

    @Test
    @DisplayName("Record serialization preserves object equality")
    void testSerializationPreservesEquality() throws IOException, ClassNotFoundException {
        Instant now = Instant.now();
        WorkItem original = new WorkItem("case-002", "task-B", "pending", now, Map.of());

        objectOutput.writeObject(original);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkItem restored = (WorkItem) objectInput.readObject();

        assertEquals(original, restored);
        assertEquals(original.hashCode(), restored.hashCode());
    }

    @Test
    @DisplayName("Serialize record with complex nested structures")
    void testComplexRecordSerialization() throws IOException, ClassNotFoundException {
        List<String> items = List.of("item1", "item2", "item3");
        WorkflowEvent event = new WorkflowEvent(
            "evt-001",
            "taskCompleted",
            System.currentTimeMillis(),
            "ProcessTask",
            new ArrayList<>(items)
        );

        objectOutput.writeObject(event);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkflowEvent restored = (WorkflowEvent) objectInput.readObject();

        assertEquals(event, restored);
        assertEquals(event.affectedItems(), restored.affectedItems());
        assertEquals(3, restored.affectedItems().size());
    }

    @Test
    @DisplayName("Serialize generic record (PagedResult)")
    void testGenericRecordSerialization() throws IOException, ClassNotFoundException {
        List<String> items = List.of("a", "b", "c");
        PagedResult<String> original = new PagedResult<>(items, 5, 1, 25L);

        objectOutput.writeObject(original);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);

        @SuppressWarnings("unchecked")
        PagedResult<String> restored = (PagedResult<String>) objectInput.readObject();

        assertEquals(original.items(), restored.items());
        assertEquals(original.totalPages(), restored.totalPages());
        assertEquals(original.currentPage(), restored.currentPage());
        assertEquals(original.totalCount(), restored.totalCount());
    }

    @Test
    @DisplayName("Multiple record instances serialize independently")
    void testMultipleRecordInstances() throws IOException, ClassNotFoundException {
        WorkItem item1 = new WorkItem("case-1", "task-1", "active", Instant.now(), Map.of());
        WorkItem item2 = new WorkItem("case-2", "task-2", "pending", Instant.now(), Map.of());
        WorkItem item3 = new WorkItem("case-3", "task-3", "complete", Instant.now(), Map.of());

        objectOutput.writeObject(item1);
        objectOutput.writeObject(item2);
        objectOutput.writeObject(item3);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);

        WorkItem restored1 = (WorkItem) objectInput.readObject();
        WorkItem restored2 = (WorkItem) objectInput.readObject();
        WorkItem restored3 = (WorkItem) objectInput.readObject();

        assertEquals(item1, restored1);
        assertEquals(item2, restored2);
        assertEquals(item3, restored3);
    }

    @Test
    @DisplayName("Null values in record fields serialize correctly")
    void testRecordWithNullFields() throws IOException, ClassNotFoundException {
        WorkItem withNulls = new WorkItem("case-null", null, "pending", null, null);

        objectOutput.writeObject(withNulls);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkItem restored = (WorkItem) objectInput.readObject();

        assertEquals("case-null", restored.caseId());
        assertNull(restored.taskId());
        assertEquals("pending", restored.status());
        assertNull(restored.createdAt());
        assertNull(restored.data());
    }

    @Test
    @DisplayName("Record deserialization creates new instance")
    void testDeserializationCreatesNewInstance() throws IOException, ClassNotFoundException {
        WorkItem original = new WorkItem("case-inst", "task-inst", "active", Instant.now(), Map.of("k", "v"));

        objectOutput.writeObject(original);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkItem restored = (WorkItem) objectInput.readObject();

        assertNotSame(original, restored);
        assertEquals(original, restored);
    }

    @Test
    @DisplayName("Empty collections in records serialize correctly")
    void testEmptyCollectionsInRecords() throws IOException, ClassNotFoundException {
        WorkflowEvent emptyEvent = new WorkflowEvent("evt-empty", "noOp", System.currentTimeMillis(), "task", Collections.emptyList());

        objectOutput.writeObject(emptyEvent);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkflowEvent restored = (WorkflowEvent) objectInput.readObject();

        assertTrue(restored.affectedItems().isEmpty());
        assertEquals(0, restored.affectedItems().size());
    }

    @Test
    @DisplayName("Large collections in records maintain integrity")
    void testLargeCollectionsInRecords() throws IOException, ClassNotFoundException {
        List<String> largeList = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            largeList.add("item-" + i);
        }
        WorkflowEvent largeEvent = new WorkflowEvent("evt-large", "bulk", System.currentTimeMillis(), "bulk-task", largeList);

        objectOutput.writeObject(largeEvent);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkflowEvent restored = (WorkflowEvent) objectInput.readObject();

        assertEquals(10000, restored.affectedItems().size());
        assertEquals("item-0", restored.affectedItems().get(0));
        assertEquals("item-9999", restored.affectedItems().get(9999));
    }

    @Test
    @DisplayName("Record toString generates from serialized data")
    void testRecordToStringFromSerialized() throws IOException, ClassNotFoundException {
        WorkItem original = new WorkItem("case-str", "task-str", "final", Instant.EPOCH, Map.of("x", 1));

        objectOutput.writeObject(original);
        objectOutput.close();

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ObjectInputStream objectInput = new ObjectInputStream(inputStream);
        WorkItem restored = (WorkItem) objectInput.readObject();

        String toString = restored.toString();
        assertTrue(toString.contains("case-str"));
        assertTrue(toString.contains("task-str"));
        assertTrue(toString.contains("final"));
    }
}
