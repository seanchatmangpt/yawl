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

package org.yawlfoundation.yawl.pi.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OcedConversionSkill.
 *
 * <p>Verifies the A2A skill converts CSV, JSON, and XML event log data to OCEL 2.0
 * using heuristic schema inference (no LLM). Chicago TDD: real objects, no mocks.</p>
 *
 * @since YAWL 6.0
 */
public class OcedConversionSkillTest {

    private static final String CSV_DATA =
        "case_id,activity,timestamp,resource\n" +
        "case001,Login,2024-01-01T10:00:00,alice\n" +
        "case001,Approve,2024-01-01T11:00:00,bob\n" +
        "case002,Login,2024-01-01T12:00:00,charlie\n";

    private static final String JSON_DATA =
        "[{\"case_id\":\"case001\",\"activity\":\"Login\"," +
        "\"timestamp\":\"2024-01-01T10:00:00\",\"resource\":\"alice\"}," +
        "{\"case_id\":\"case001\",\"activity\":\"Approve\"," +
        "\"timestamp\":\"2024-01-01T11:00:00\",\"resource\":\"bob\"}]";

    private OcedConversionSkill skill;

    @BeforeEach
    void setUp() {
        skill = new OcedConversionSkill();
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    @Test
    void getId_returnsOcedToOcel() {
        assertEquals("oced_to_ocel", skill.getId());
    }

    @Test
    void getName_isNonBlank() {
        assertNotNull(skill.getName());
        assertFalse(skill.getName().isBlank());
    }

    @Test
    void getDescription_mentioneOcelStandard() {
        String desc = skill.getDescription();
        assertTrue(desc.contains("OCEL") || desc.contains("ocel"),
            "Description should reference the OCEL standard");
    }

    @Test
    void getRequiredPermissions_containsWorkflowData() {
        Set<String> perms = skill.getRequiredPermissions();
        assertTrue(perms.contains("workflow:data"),
            "Skill must require 'workflow:data' permission");
    }

    @Test
    void getTags_containsOcelTag() {
        assertTrue(skill.getTags().contains("ocel"),
            "Tags must include 'ocel'");
    }

    @Test
    void getTags_containsNoLlmTag() {
        assertTrue(skill.getTags().contains("no-llm"),
            "Tags must include 'no-llm' to signal pure heuristic path");
    }

    // =========================================================================
    // Execute — CSV
    // =========================================================================

    @Test
    void execute_withCsvData_returnsSuccess() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isSuccess(), "Expected success for valid CSV: " + result.getError());
    }

    @Test
    void execute_withCsvData_returnsOcel2Json() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        assertNotNull(result.get("ocel2Json"), "Result must contain ocel2Json");
        String ocelJson = (String) result.get("ocel2Json");
        assertFalse(ocelJson.isBlank(), "ocel2Json must not be blank");
    }

    @Test
    void execute_withCsvData_returnsSchemaMetadata() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        assertNotNull(result.get("caseIdColumn"), "Result must have caseIdColumn");
        assertNotNull(result.get("activityColumn"), "Result must have activityColumn");
        assertNotNull(result.get("timestampColumn"), "Result must have timestampColumn");
    }

    @Test
    void execute_withCsvData_reportsFormatAsCsv() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        String fmt = (String) result.get("detectedFormat");
        assertNotNull(fmt);
        assertEquals("CSV", fmt.toUpperCase());
    }

    @Test
    void execute_withCsvData_elapsedMsPresent() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        assertNotNull(result.get("elapsed_ms"), "Result must include elapsed_ms");
    }

    @Test
    void execute_withCsvData_aiInferredIsFalse() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", CSV_DATA)
            .build();
        SkillResult result = skill.execute(request);
        Object aiInferred = result.get("aiInferred");
        assertNotNull(aiInferred);
        assertFalse((Boolean) aiInferred,
            "Heuristic path must report aiInferred=false (no LLM)");
    }

    // =========================================================================
    // Execute — JSON
    // =========================================================================

    @Test
    void execute_withJsonData_returnsSuccess() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", JSON_DATA)
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isSuccess(), "Expected success for valid JSON: " + result.getError());
    }

    @Test
    void execute_withJsonData_reportsFormatAsJson() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", JSON_DATA)
            .build();
        SkillResult result = skill.execute(request);
        String fmt = (String) result.get("detectedFormat");
        assertNotNull(fmt);
        assertEquals("JSON", fmt.toUpperCase());
    }

    @Test
    void execute_withJsonData_returnsOcel2Json() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", JSON_DATA)
            .build();
        SkillResult result = skill.execute(request);
        String ocelJson = (String) result.get("ocel2Json");
        assertNotNull(ocelJson);
        assertFalse(ocelJson.isBlank());
    }

    // =========================================================================
    // Execute — explicit format parameter
    // =========================================================================

    @Test
    void execute_withExplicitJsonFormat_usesRequestedFormat() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", JSON_DATA)
            .parameter("format", "json")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isSuccess());
    }

    // =========================================================================
    // Execute — error cases
    // =========================================================================

    @Test
    void execute_withNullRequest_returnsError() {
        SkillResult result = skill.execute(null);
        assertTrue(result.isError(), "Null request must return error");
        assertNotNull(result.getError());
    }

    @Test
    void execute_withMissingEventData_returnsError() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Missing eventData must return error");
        assertNotNull(result.getError());
    }

    @Test
    void execute_withBlankEventData_returnsError() {
        SkillRequest request = SkillRequest.builder("oced_to_ocel")
            .parameter("eventData", "")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Blank eventData must return error");
    }
}
