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

package org.yawlfoundation.yawl.integration.a2a.milestone;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test suite for MilestoneStateMessage and A2A protocol integration.
 */
class MilestoneStateMessageTest {

    private static final String CASE_ID = "case-test-123";
    private static final String MILESTONE_ID = "approval-milestone";
    private static final String MILESTONE_NAME = "Approval Received";
    private static final String SPEC_ID = "OrderProcess:v2.0";

    private MilestoneStateMessage message;

    @BeforeEach
    void setUp() {
        message = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName(MILESTONE_NAME)
            .state("REACHED")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .taskId("task-procure")
            .taskName("Procure Order")
            .enabledByMilestone(true)
            .reachTimeMs(125000)
            .expiryTimeoutMs(3600000)
            .milestoneExpression("case.approval_status == 'approved'")
            .build();
    }

    @Test
    @DisplayName("Should create milestone message with all fields")
    void testCreationWithAllFields() {
        assertNotNull(message);
        assertEquals(CASE_ID, message.caseId());
        assertEquals(MILESTONE_ID, message.milestoneId());
        assertEquals("REACHED", message.state());
        assertTrue(message.enabledByMilestone());
    }

    @Test
    @DisplayName("Should validate required fields")
    void testValidationRequiredFields() {
        assertThrows(NullPointerException.class, () ->
            new MilestoneStateMessage(null, MILESTONE_ID, MILESTONE_NAME, "REACHED",
                "NOT_REACHED", Instant.now(), null, null, false, SPEC_ID, 0, 0, null));
    }

    @Test
    @DisplayName("Should reject negative timing values")
    void testValidationNegativeTiming() {
        assertThrows(IllegalArgumentException.class, () ->
            MilestoneStateMessage.builder()
                .caseId(CASE_ID)
                .milestoneId(MILESTONE_ID)
                .milestoneName(MILESTONE_NAME)
                .state("REACHED")
                .previousState("NOT_REACHED")
                .timestamp(Instant.now())
                .specificationId(SPEC_ID)
                .reachTimeMs(-1)
                .build());
    }

    @Test
    @DisplayName("Should convert to A2A message format")
    void testToA2AMessage() {
        Map<String, Object> a2aMsg = message.toA2AMessage();
        assertNotNull(a2aMsg);
        assertEquals(CASE_ID, a2aMsg.get("caseId"));
        assertEquals(MILESTONE_ID, a2aMsg.get("milestoneId"));
        assertEquals("REACHED", a2aMsg.get("state"));
        assertTrue((Boolean) a2aMsg.get("enabledByMilestone"));
    }

    @Test
    @DisplayName("Should serialize to JSON and deserialize back")
    void testJsonRoundTrip() {
        String json = message.toJson();
        assertNotNull(json);
        assertFalse(json.isEmpty());

        MilestoneStateMessage deserialized = MilestoneStateMessage.fromJson(json);
        assertNotNull(deserialized);
        assertEquals(message.caseId(), deserialized.caseId());
        assertEquals(message.milestoneId(), deserialized.milestoneId());
        assertEquals(message.state(), deserialized.state());
        assertEquals(message.taskId(), deserialized.taskId());
    }

    @Test
    @DisplayName("Should validate milestone message")
    void testValidation() {
        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(message);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should reject invalid milestone state")
    void testValidationInvalidState() {
        var invalidMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName(MILESTONE_NAME)
            .state("INVALID_STATE")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            AIMQMilestoneAdapter.validate(invalidMsg));
    }

    @Test
    @DisplayName("Should detect future timestamps")
    void testValidationFutureTimestamp() {
        var futureMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName(MILESTONE_NAME)
            .state("REACHED")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now().plusSeconds(10))
            .specificationId(SPEC_ID)
            .build();

        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(futureMsg);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Should create message from map")
    void testFromMap() {
        Map<String, Object> data = new HashMap<>();
        data.put("caseId", CASE_ID);
        data.put("milestoneId", MILESTONE_ID);
        data.put("milestoneName", MILESTONE_NAME);
        data.put("state", "REACHED");
        data.put("previousState", "NOT_REACHED");
        data.put("timestamp", Instant.now().toString());
        data.put("specificationId", SPEC_ID);

        var metadata = new HashMap<String, Object>();
        metadata.put("reachTimeMs", 125000L);
        data.put("metadata", metadata);

        MilestoneStateMessage fromMap = MilestoneStateMessage.fromMap(data);
        assertNotNull(fromMap);
        assertEquals(CASE_ID, fromMap.caseId());
    }

    @Test
    @DisplayName("Should handle expired milestone state")
    void testExpiredMilestoneState() {
        var expiredMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName(MILESTONE_NAME)
            .state("EXPIRED")
            .previousState("REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .expiryTimeoutMs(3600000)
            .build();

        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(expiredMsg);
        assertTrue(result.isValid());
        assertEquals("EXPIRED", expiredMsg.state());
    }

    @Test
    @DisplayName("Should validate state transitions")
    void testStateTransitions() {
        // Valid: NOT_REACHED -> REACHED
        var reachedMsg = message;
        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(reachedMsg);
        assertTrue(result.isValid());

        // Valid: REACHED -> EXPIRED
        var expiredMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName(MILESTONE_NAME)
            .state("EXPIRED")
            .previousState("REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .build();
        result = AIMQMilestoneAdapter.validate(expiredMsg);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should create message from builder")
    void testBuilderPattern() {
        var msg = MilestoneStateMessage.builder()
            .caseId("case-999")
            .milestoneId("milestone-xyz")
            .milestoneName("Milestone XYZ")
            .state("REACHED")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .specificationId("ProcessA:v1")
            .reachTimeMs(50000)
            .build();

        assertNotNull(msg);
        assertEquals("case-999", msg.caseId());
        assertEquals("Milestone XYZ", msg.milestoneName());
    }
}
