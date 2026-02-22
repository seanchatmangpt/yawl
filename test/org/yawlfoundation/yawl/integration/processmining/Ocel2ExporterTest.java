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
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD OCEL2 exporter tests.
 * Tests real Ocel2Exporter behavior with WorkflowEventRecord collections.
 *
 * @author Test Specialist
 */
class Ocel2ExporterTest {

    /**
     * Test: empty event list produces valid OCEL2 structure with no events.
     */
    @Test
    void exportEmpty_returnsValidOcel2Structure() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        List<Ocel2Exporter.WorkflowEventRecord> events = new ArrayList<>();

        String json = exporter.exportWorkflowEvents(events);

        assertNotNull(json);
        assertFalse(json.isEmpty());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertEquals("2.0", root.get("ocel:version").asText());
        assertEquals("timestamp", root.get("ocel:ordering").asText());
        assertTrue(root.has("ocel:attribute-names"));
        assertTrue(root.has("ocel:object-types"));
        assertTrue(root.has("ocel:events"));
        assertTrue(root.has("ocel:objects"));
    }

    /**
     * Test: single event is exported and appears in ocel:events.
     */
    @Test
    void exportSingleEvent_containsActivity() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            "case-1",
            null,
            "ProcessOrder",
            "user@example.com",
            now,
            "ActivityEvent"
        );

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(event);

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode eventsNode = root.get("ocel:events");

        assertTrue(eventsNode.has("evt-001"));
        JsonNode evt = eventsNode.get("evt-001");
        assertEquals("ProcessOrder", evt.get("ocel:activity").asText());
    }

    /**
     * Test: multiple events are all included in export.
     * Three events with different activities.
     */
    @Test
    void exportMultipleEvents_allIncluded() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant base = Instant.now();

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-001", "case-1", null, "Receive", null, base, "TaskCompleted"),
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-002", "case-1", null, "Validate", null, base.plusSeconds(60), "TaskCompleted"),
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-003", "case-1", null, "Ship", null, base.plusSeconds(120), "TaskCompleted")
        );

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode eventsNode = root.get("ocel:events");

        assertEquals(3, eventsNode.size());
        assertTrue(eventsNode.has("evt-001"));
        assertTrue(eventsNode.has("evt-002"));
        assertTrue(eventsNode.has("evt-003"));
    }

    /**
     * Test: fromXesEvent factory method generates non-empty eventId and correct activity.
     */
    @Test
    void fromXesEvent_generatesValidRecord() {
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord record =
            Ocel2Exporter.fromXesEvent("case-123", "ApproveOrder", now);

        assertNotNull(record);
        assertNotNull(record.eventId());
        assertFalse(record.eventId().isEmpty());
        assertEquals("case-123", record.caseId());
        assertEquals("ApproveOrder", record.activity());
        assertEquals(now, record.timestamp());
        assertEquals("ActivityEvent", record.eventType());
        assertNull(record.workItemId());
        assertNull(record.resource());
    }

    /**
     * Test: fromXesEvent generates unique eventIds.
     */
    @Test
    void fromXesEvent_generatesUniqueIds() {
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord record1 =
            Ocel2Exporter.fromXesEvent("case-1", "Act", now);
        Ocel2Exporter.WorkflowEventRecord record2 =
            Ocel2Exporter.fromXesEvent("case-1", "Act", now);

        assertNotEquals(record1.eventId(), record2.eventId());
    }

    /**
     * Test: exported JSON has valid ocel:vmap (value map) with resource and case:id.
     */
    @Test
    void exportEvent_includesValueMap() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            "case-ABC",
            null,
            "Task",
            "alice@example.com",
            now,
            "ActivityEvent"
        );

        String json = exporter.exportWorkflowEvents(List.of(event));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode evt = root.get("ocel:events").get("evt-001");

        JsonNode vmap = evt.get("ocel:vmap");
        assertNotNull(vmap);
        assertEquals("alice@example.com", vmap.get("org:resource").asText());
        assertEquals("case-ABC", vmap.get("case:id").asText());
    }

    /**
     * Test: exported JSON has valid ocel:omap (object map) with case reference.
     */
    @Test
    void exportEvent_includesObjectMap() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            "case-123",
            "wi-456",
            "Task",
            null,
            now,
            "ActivityEvent"
        );

        String json = exporter.exportWorkflowEvents(List.of(event));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode evt = root.get("ocel:events").get("evt-001");

        JsonNode omap = evt.get("ocel:omap");
        assertNotNull(omap);
        assertTrue(omap.has("Case"));
        assertTrue(omap.has("WorkItem"));

        JsonNode caseArray = omap.get("Case");
        assertTrue(caseArray.isArray());
        assertEquals("case-123", caseArray.get(0).asText());

        JsonNode wiArray = omap.get("WorkItem");
        assertTrue(wiArray.isArray());
        assertEquals("wi-456", wiArray.get(0).asText());
    }

    /**
     * Test: ocel:objects section contains Case and WorkItem objects.
     */
    @Test
    void exportEvent_includesObjectsSection() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-001", "case-1", "wi-1", "Task", null, now, "ActivityEvent")
        );

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode objects = root.get("ocel:objects");

        assertTrue(objects.has("case-1"));
        assertTrue(objects.has("wi-1"));

        JsonNode caseObj = objects.get("case-1");
        assertEquals("Case", caseObj.get("ocel:type").asText());

        JsonNode wiObj = objects.get("wi-1");
        assertEquals("WorkItem", wiObj.get("ocel:type").asText());
    }

    /**
     * Test: timestamp is included in exported event.
     */
    @Test
    void exportEvent_includesTimestamp() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant testTime = Instant.parse("2026-02-20T14:30:00Z");

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            "case-1",
            null,
            "Task",
            null,
            testTime,
            "ActivityEvent"
        );

        String json = exporter.exportWorkflowEvents(List.of(event));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode evt = root.get("ocel:events").get("evt-001");

        String timestamp = evt.get("ocel:timestamp").asText();
        assertTrue(timestamp.contains("2026-02-20"));
    }

    /**
     * Test: ocel:attribute-names includes standard attributes.
     */
    @Test
    void exportEvent_includesAttributeNames() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();

        String json = exporter.exportWorkflowEvents(new ArrayList<>());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode attrNames = root.get("ocel:attribute-names");

        assertTrue(attrNames.isArray());
        assertTrue(attrNames.toString().contains("org:resource"));
        assertTrue(attrNames.toString().contains("case:id"));
    }

    /**
     * Test: ocel:object-types includes Case and WorkItem.
     */
    @Test
    void exportEvent_includesObjectTypes() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();

        String json = exporter.exportWorkflowEvents(new ArrayList<>());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode objTypes = root.get("ocel:object-types");

        assertTrue(objTypes.isArray());
        assertTrue(objTypes.toString().contains("Case"));
        assertTrue(objTypes.toString().contains("WorkItem"));
    }

    /**
     * Test: event with null caseId is handled gracefully.
     */
    @Test
    void exportEvent_nullCaseId_handled() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            null,
            "wi-1",
            "Task",
            null,
            now,
            "ActivityEvent"
        );

        String json = exporter.exportWorkflowEvents(List.of(event));

        assertNotNull(json);
        assertFalse(json.isEmpty());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        assertNotNull(root.get("ocel:events"));
    }

    /**
     * Test: event with null workItemId is handled gracefully.
     */
    @Test
    void exportEvent_nullWorkItemId_handled() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord event = new Ocel2Exporter.WorkflowEventRecord(
            "evt-001",
            "case-1",
            null,
            "Task",
            null,
            now,
            "ActivityEvent"
        );

        String json = exporter.exportWorkflowEvents(List.of(event));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode omap = root.get("ocel:events").get("evt-001").get("ocel:omap");

        assertTrue(omap.has("Case"));
        assertFalse(omap.has("WorkItem") && omap.get("WorkItem") != null);
    }

    /**
     * Test: exported JSON is valid and parseable.
     */
    @Test
    void exportEvent_producesValidJson() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant now = Instant.now();

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-1", "c1", "w1", "A", "res", now, "Type1"),
            new Ocel2Exporter.WorkflowEventRecord(
                "evt-2", "c1", "w2", "B", "res", now.plusSeconds(60), "Type2")
        );

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        assertDoesNotThrow(() -> mapper.readTree(json));
    }

    /**
     * Test: multiple events with same case ID share the same Case object.
     */
    @Test
    void exportMultipleEvents_shareCaseObject() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant base = Instant.now();

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(
            new Ocel2Exporter.WorkflowEventRecord("evt-1", "case-1", "wi-1", "A", null, base, "Type"),
            new Ocel2Exporter.WorkflowEventRecord("evt-2", "case-1", "wi-2", "B", null, base.plusSeconds(60), "Type")
        );

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode objects = root.get("ocel:objects");

        // Should have exactly one Case object for "case-1"
        assertTrue(objects.has("case-1"));
        assertEquals("Case", objects.get("case-1").get("ocel:type").asText());
    }

    /**
     * Test: complex workflow with multiple cases and work items.
     */
    @Test
    void exportComplexWorkflow_allDataIncluded() throws IOException {
        Ocel2Exporter exporter = new Ocel2Exporter();
        Instant base = Instant.now();

        List<Ocel2Exporter.WorkflowEventRecord> events = List.of(
            new Ocel2Exporter.WorkflowEventRecord("e1", "c1", "w1", "Start", "alice", base, "TaskStart"),
            new Ocel2Exporter.WorkflowEventRecord("e2", "c1", "w1", "Complete", "alice", base.plusSeconds(100), "TaskComplete"),
            new Ocel2Exporter.WorkflowEventRecord("e3", "c2", "w2", "Start", "bob", base.plusSeconds(200), "TaskStart"),
            new Ocel2Exporter.WorkflowEventRecord("e4", "c2", "w2", "Complete", "bob", base.plusSeconds(300), "TaskComplete")
        );

        String json = exporter.exportWorkflowEvents(events);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertEquals(4, root.get("ocel:events").size());
        assertTrue(root.get("ocel:objects").has("c1"));
        assertTrue(root.get("ocel:objects").has("c2"));
        assertTrue(root.get("ocel:objects").has("w1"));
        assertTrue(root.get("ocel:objects").has("w2"));
    }

    /**
     * Test: WorkflowEventRecord is immutable (record semantics).
     */
    @Test
    void workflowEventRecord_immutable() {
        Instant now = Instant.now();

        Ocel2Exporter.WorkflowEventRecord record =
            new Ocel2Exporter.WorkflowEventRecord("e1", "c1", "w1", "Act", "res", now, "Type");

        assertEquals("e1", record.eventId());
        assertEquals("c1", record.caseId());
        assertEquals("w1", record.workItemId());
        assertEquals("Act", record.activity());
        assertEquals("res", record.resource());
        assertEquals(now, record.timestamp());
        assertEquals("Type", record.eventType());
    }
}
