/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for OCEL 2.0 exporter.
 * Tests conversion of XES event logs to OCEL 2.0 format.
 *
 * @author YAWL Foundation
 */
@DisplayName("OCEL 2.0 Exporter Tests")
class OcelExporterTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Single trace with two events produces valid OCEL JSON")
    void testSimpleSingleTraceXes() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="start"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:31:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        assertNotNull(ocel);
        assertFalse(ocel.isEmpty());

        JsonNode root = mapper.readTree(ocel);
        assertTrue(root.has("objectTypes"));
        assertTrue(root.has("eventTypes"));
        assertTrue(root.has("objects"));
        assertTrue(root.has("events"));

        // Should have 3 object types: case, task, resource
        JsonNode objectTypes = root.get("objectTypes");
        assertEquals(3, objectTypes.size());
        Set<String> typeNames = new HashSet<>();
        for (JsonNode type : objectTypes) {
            typeNames.add(type.get("name").asText());
        }
        assertTrue(typeNames.contains("case"));
        assertTrue(typeNames.contains("task"));
        assertTrue(typeNames.contains("resource"));

        // Should have 1 case object, 1 task object, 1 resource object
        JsonNode objects = root.get("objects");
        assertEquals(3, objects.size());

        // Should have 2 events
        JsonNode events = root.get("events");
        assertEquals(2, events.size());

        // First event should have type workflow_event
        JsonNode firstEvent = events.get(0);
        assertEquals("workflow_event", firstEvent.get("type").asText());
        assertTrue(firstEvent.has("time"));
        assertTrue(firstEvent.has("attributes"));
        assertTrue(firstEvent.has("relationships"));

        // Event should have 3 relationships (case, task, resource)
        JsonNode relationships = firstEvent.get("relationships");
        assertEquals(3, relationships.size());
    }

    @Test
    @DisplayName("Multiple traces with different cases create separate case objects")
    void testMultipleTraces() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                    </event>
                  </trace>
                  <trace>
                    <string key="concept:name" value="case-002"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_2"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:31:00.000Z"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        JsonNode objects = root.get("objects");
        // Should have: 2 cases, 2 tasks (per case)
        assertEquals(4, objects.size());

        // Count case objects
        int caseCount = 0;
        for (JsonNode obj : objects) {
            if ("case".equals(obj.get("type").asText())) {
                caseCount++;
            }
        }
        assertEquals(2, caseCount);
    }

    @Test
    @DisplayName("Event with org:resource creates resource object")
    void testEventWithResourceCreatesResourceObject() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="alice"/>
                    </event>
                    <event>
                      <string key="concept:name" value="TaskB"/>
                      <string key="concept:instance" value="taskB_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:31:00.000Z"/>
                      <string key="org:resource" value="bob"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        JsonNode objects = root.get("objects");
        // Should have: 1 case, 2 tasks, 2 resources
        assertEquals(5, objects.size());

        // Count resource objects
        int resourceCount = 0;
        for (JsonNode obj : objects) {
            if ("resource".equals(obj.get("type").asText())) {
                resourceCount++;
            }
        }
        assertEquals(2, resourceCount);
    }

    @Test
    @DisplayName("Event without resource still exports correctly")
    void testEventWithoutResourceExportsCorrectly() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        assertNotNull(ocel);
        assertFalse(ocel.isEmpty());

        JsonNode root = mapper.readTree(ocel);
        JsonNode events = root.get("events");
        assertEquals(1, events.size());

        // Event should have 2 relationships (case and task, no resource)
        JsonNode event = events.get(0);
        JsonNode relationships = event.get("relationships");
        assertEquals(2, relationships.size());
    }

    @Test
    @DisplayName("Output is valid parseable JSON")
    void testOutputIsValidJson() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="start"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);

        // Should parse without exception
        JsonNode root = mapper.readTree(ocel);
        assertNotNull(root);
    }

    @Test
    @DisplayName("OCEL has correct objectTypes (case, task, resource)")
    void testOcelHasCorrectObjectTypes() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        JsonNode objectTypes = root.get("objectTypes");
        assertNotNull(objectTypes);

        Set<String> typeNames = new HashSet<>();
        for (JsonNode type : objectTypes) {
            typeNames.add(type.get("name").asText());
            assertTrue(type.has("attributes"), "Object type should have attributes");
        }

        assertTrue(typeNames.contains("case"));
        assertTrue(typeNames.contains("task"));
        assertTrue(typeNames.contains("resource"));
    }

    @Test
    @DisplayName("Event relationships reference existing object IDs")
    void testEventRelationshipsReferenceExistingObjects() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        // Get all object IDs
        Set<String> objectIds = new HashSet<>();
        for (JsonNode obj : root.get("objects")) {
            objectIds.add(obj.get("id").asText());
        }

        // Check that event relationships reference existing objects
        for (JsonNode event : root.get("events")) {
            for (JsonNode rel : event.get("relationships")) {
                String referencedId = rel.get("objectId").asText();
                assertTrue(objectIds.contains(referencedId),
                        "Event relationship references non-existent object: " + referencedId);
            }
        }
    }

    @Test
    @DisplayName("Empty log produces valid OCEL structure")
    void testEmptyLogProducesValidOcel() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        assertTrue(root.has("objectTypes"));
        assertTrue(root.has("eventTypes"));
        assertTrue(root.has("objects"));
        assertTrue(root.has("events"));

        assertEquals(0, root.get("objects").size());
        assertEquals(0, root.get("events").size());
    }

    @Test
    @DisplayName("Null XES input produces valid empty OCEL")
    void testNullXesInputProducesEmptyOcel() throws Exception {
        String ocel = OcelExporter.xesToOcel(null);
        assertNotNull(ocel);

        JsonNode root = mapper.readTree(ocel);
        assertEquals(0, root.get("objects").size());
        assertEquals(0, root.get("events").size());
    }

    @Test
    @DisplayName("XES with special characters in names handles escaping")
    void testSpecialCharactersInNames() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="Task &quot;A&quot;"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="john@domain"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        assertNotNull(ocel);

        // Should parse without exception
        JsonNode root = mapper.readTree(ocel);
        assertNotNull(root);
        assertEquals(1, root.get("events").size());
    }

    @Test
    @DisplayName("Multiple events for same task instance link to same task object")
    void testMultipleEventsForSameTaskInstance() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="start"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:31:00.000Z"/>
                      <string key="org:resource" value="john"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        // Should have exactly 3 objects (1 case, 1 task, 1 resource)
        JsonNode objects = root.get("objects");
        assertEquals(3, objects.size());

        // Both events should reference the same task object
        JsonNode events = root.get("events");
        String taskId1 = null;
        String taskId2 = null;

        for (JsonNode event : events) {
            for (JsonNode rel : event.get("relationships")) {
                if ("task".equals(rel.get("qualifier").asText())) {
                    if (taskId1 == null) {
                        taskId1 = rel.get("objectId").asText();
                    } else {
                        taskId2 = rel.get("objectId").asText();
                    }
                }
            }
        }

        assertEquals(taskId1, taskId2, "Both events should reference the same task object");
    }

    @Test
    @DisplayName("Event attributes contain lifecycle and engineInstanceId")
    void testEventAttributesContainExpectedFields() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="case-001"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        JsonNode event = root.get("events").get(0);
        JsonNode attributes = event.get("attributes");

        Set<String> attrNames = new HashSet<>();
        for (JsonNode attr : attributes) {
            attrNames.add(attr.get("name").asText());
        }

        assertTrue(attrNames.contains("lifecycle"), "Event should have lifecycle attribute");
        assertTrue(attrNames.contains("engineInstanceId"), "Event should have engineInstanceId attribute");
    }

    @Test
    @DisplayName("Case ID is preserved in case object attributes")
    void testCaseIdPreservedInObject() throws Exception {
        String xes = """
                <?xml version="1.0" encoding="UTF-8"?>
                <log>
                  <trace>
                    <string key="concept:name" value="my-important-case"/>
                    <event>
                      <string key="concept:name" value="TaskA"/>
                      <string key="concept:instance" value="taskA_1"/>
                      <string key="lifecycle:transition" value="complete"/>
                      <string key="time:timestamp" value="2026-02-20T14:30:00.000Z"/>
                    </event>
                  </trace>
                </log>
                """;

        String ocel = OcelExporter.xesToOcel(xes);
        JsonNode root = mapper.readTree(ocel);

        // Find case object
        JsonNode caseObj = null;
        for (JsonNode obj : root.get("objects")) {
            if ("case".equals(obj.get("type").asText())) {
                caseObj = obj;
                break;
            }
        }

        assertNotNull(caseObj);
        assertTrue(caseObj.has("attributes"));
        assertTrue(caseObj.get("attributes").size() > 0);
    }
}
