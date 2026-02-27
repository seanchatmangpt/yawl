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

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for null handling in OcedConversionSkill.
 */
public class OcedConversionSkillTest {

    private final OcedConversionSkill skill = new OcedConversionSkill();

    @Test
    void executeWithNullRequest() {
        SkillResult result = skill.execute(null);
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("Request cannot be null", result.getError());
    }

    @Test
    void executeWithValidCsvData() {
        String csvData = "case_id,activity,timestamp\n" +
                         "case1,Task A,2023-01-01T10:00:00\n" +
                         "case1,Task B,2023-01-01T11:00:00";

        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", csvData);

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void executeWithValidJsonData() {
        String jsonData = "[\n" +
                          "  {\"case_id\": \"case1\", \"activity\": \"Task A\", \"timestamp\": \"2023-01-01T10:00:00\"},\n" +
                          "  {\"case_id\": \"case1\", \"activity\": \"Task B\", \"timestamp\": \"2023-01-01T11:00:00\"}\n" +
                          "]";

        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", jsonData);

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void executeWithEmptyEventData() {
        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", "");

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getError().contains("eventData is required"));
    }

    @Test
    void executeWithNullEventData() {
        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", null);

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getError().contains("eventData is required"));
    }

    @Test
    void executeWithExceedingDataSize() {
        // Create a string larger than 10MB
        StringBuilder largeData = new StringBuilder();
        largeData.append("case_id,activity,timestamp\n");
        for (int i = 0; i < 1000000; i++) {
            largeData.append("case").append(i).append(",Task,").append("2023-01-01T").append(i).append(":00:00\n");
        }

        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", largeData.toString());

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertTrue(result.isError());
        assertTrue(result.getError().contains("exceeds maximum size of 10MB"));
    }

    @Test
    void executeWithValidDataAndFormatHint() {
        String csvData = "case_id,activity,timestamp\n" +
                         "case1,Task A,2023-01-01T10:00:00";

        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", csvData);
        request.addParameter("format", "csv");

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertFalse(result.isError());
    }

    @Test
    void executeWithAutoDetectionFormat() {
        String csvData = "case_id,activity,timestamp\n" +
                         "case1,Task A,2023-01-01T10:00:00";

        SkillRequest request = new SkillRequest();
        request.addParameter("eventData", csvData);
        request.addParameter("format", "auto");

        SkillResult result = skill.execute(request);
        assertNotNull(result);
        assertFalse(result.isError());
    }
}