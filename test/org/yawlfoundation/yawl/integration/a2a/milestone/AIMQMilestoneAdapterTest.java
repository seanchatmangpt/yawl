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
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

/**
 * Test suite for AIMQ Milestone Adapter (A2A protocol integration).
 */
class AIMQMilestoneAdapterTest {

    private static final String CASE_ID = "case-milestone-456";
    private static final String SPEC_ID = "OrderProcess:v2.0";
    private static final String MILESTONE_ID = "approval-gate";

    private MilestoneStateMessage milestoneMessage;
    private WorkflowEvent workflowEvent;

    @BeforeEach
    void setUp() {
        milestoneMessage = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName("Approval Required")
            .state("REACHED")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .taskId("task-approve")
            .taskName("Approve Order")
            .enabledByMilestone(true)
            .reachTimeMs(100000)
            .expiryTimeoutMs(7200000)
            .milestoneExpression("case.status == 'pending_approval'")
            .build();

        Map<String, String> payload = new HashMap<>();
        payload.put("milestoneId", MILESTONE_ID);
        payload.put("milestoneName", "Approval Required");
        payload.put("state", "REACHED");
        payload.put("previousState", "NOT_REACHED");
        payload.put("taskId", "task-approve");
        payload.put("taskName", "Approve Order");
        payload.put("enabledByMilestone", "true");
        payload.put("reachTimeMs", "100000");
        payload.put("expiryTimeoutMs", "7200000");
        payload.put("milestoneExpression", "case.status == 'pending_approval'");

        workflowEvent = new WorkflowEvent(
            WorkflowEvent.EventType.WORKITEM_ENABLED,
            SPEC_ID,
            CASE_ID,
            "task-approve",
            payload
        );
    }

    @Test
    @DisplayName("Should convert WorkflowEvent to MilestoneStateMessage")
    void testFromWorkflowEvent() {
        MilestoneStateMessage msg = AIMQMilestoneAdapter.fromWorkflowEvent(workflowEvent);
        assertNotNull(msg);
        assertEquals(CASE_ID, msg.caseId());
        assertEquals(SPEC_ID, msg.specificationId());
        assertEquals("REACHED", msg.state());
    }

    @Test
    @DisplayName("Should convert MilestoneStateMessage to WorkflowEvent")
    void testToWorkflowEvent() {
        WorkflowEvent event = AIMQMilestoneAdapter.toWorkflowEvent(milestoneMessage);
        assertNotNull(event);
        assertEquals(CASE_ID, event.getCaseId());
        assertEquals(SPEC_ID, event.getSpecId());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("Should roundtrip conversion without data loss")
    void testRoundTripConversion() {
        // Workflow event -> milestone message -> workflow event
        MilestoneStateMessage msg = AIMQMilestoneAdapter.fromWorkflowEvent(workflowEvent);
        WorkflowEvent converted = AIMQMilestoneAdapter.toWorkflowEvent(msg);

        assertEquals(workflowEvent.getCaseId(), converted.getCaseId());
        assertEquals(workflowEvent.getSpecId(), converted.getSpecId());
        assertEquals(workflowEvent.getWorkItemId(), converted.getWorkItemId());
    }

    @Test
    @DisplayName("Should validate milestone messages")
    void testValidateMessage() {
        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(milestoneMessage);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should detect validation errors")
    void testValidationErrors() {
        var invalidMsg = MilestoneStateMessage.builder()
            .caseId("")  // Empty case ID
            .milestoneId(MILESTONE_ID)
            .milestoneName("Test")
            .state("REACHED")
            .previousState("NOT_REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            AIMQMilestoneAdapter.validate(invalidMsg));
    }

    @Test
    @DisplayName("Should create message from case data")
    void testCreateMessage() {
        MilestoneStateMessage msg = AIMQMilestoneAdapter.createMessage(
            CASE_ID, SPEC_ID, MILESTONE_ID, "Approval Gate", "REACHED");

        assertNotNull(msg);
        assertEquals(CASE_ID, msg.caseId());
        assertEquals(MILESTONE_ID, msg.milestoneId());
        assertEquals("REACHED", msg.state());
    }

    @Test
    @DisplayName("Should handle null workflow event gracefully")
    void testNullWorkflowEvent() {
        assertThrows(IllegalArgumentException.class, () ->
            AIMQMilestoneAdapter.fromWorkflowEvent(null));
    }

    @Test
    @DisplayName("Should handle null milestone message gracefully")
    void testNullMilestoneMessage() {
        assertThrows(IllegalArgumentException.class, () ->
            AIMQMilestoneAdapter.toWorkflowEvent(null));
    }

    @Test
    @DisplayName("Should support custom publisher function")
    void testPublisherCallback() throws Exception {
        boolean[] publisherCalled = {false};

        AIMQMilestoneAdapter.MilestonePublisher publisher = msg -> {
            publisherCalled[0] = true;
            assertNotNull(msg);
        };

        AIMQMilestoneAdapter.publishViaA2A(milestoneMessage, publisher);
        assertTrue(publisherCalled[0]);
    }

    @Test
    @DisplayName("Should retry publishing with exponential backoff")
    void testPublishWithRetry() throws Exception {
        int[] attemptCount = {0};

        AIMQMilestoneAdapter.MilestonePublisher publisher = msg -> {
            attemptCount[0]++;
            if (attemptCount[0] < 3) {
                throw new RuntimeException("Transient failure");
            }
        };

        AIMQMilestoneAdapter.publishWithRetry(milestoneMessage, publisher);
        assertEquals(3, attemptCount[0]);
    }

    @Test
    @DisplayName("Should fail after 3 retry attempts")
    void testPublishRetryExhaustion() {
        AIMQMilestoneAdapter.MilestonePublisher publisher = msg -> {
            throw new RuntimeException("Persistent failure");
        };

        assertThrows(RuntimeException.class, () ->
            AIMQMilestoneAdapter.publishWithRetry(milestoneMessage, publisher));
    }

    @Test
    @DisplayName("Should handle milestone expired state")
    void testExpiredMilestoneHandling() {
        var expiredMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName("Approval Required")
            .state("EXPIRED")
            .previousState("REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .expiryTimeoutMs(7200000)
            .build();

        WorkflowEvent event = AIMQMilestoneAdapter.toWorkflowEvent(expiredMsg);
        assertNotNull(event);
        assertEquals(CASE_ID, event.getCaseId());
    }

    @Test
    @DisplayName("Should validate state transitions")
    void testStateTransitionValidation() {
        // NOT_REACHED -> REACHED
        AIMQMilestoneAdapter.ValidationResult result =
            AIMQMilestoneAdapter.validate(milestoneMessage);
        assertTrue(result.isValid());

        // REACHED -> EXPIRED
        var expiredMsg = MilestoneStateMessage.builder()
            .caseId(CASE_ID)
            .milestoneId(MILESTONE_ID)
            .milestoneName("Approval Required")
            .state("EXPIRED")
            .previousState("REACHED")
            .timestamp(Instant.now())
            .specificationId(SPEC_ID)
            .build();

        result = AIMQMilestoneAdapter.validate(expiredMsg);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Should serialize milestone to A2A JSON")
    void testA2ASerialization() {
        String json = milestoneMessage.toJson();
        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains(CASE_ID));
        assertTrue(json.contains(MILESTONE_ID));
    }

    @Test
    @DisplayName("Should deserialize A2A JSON to milestone")
    void testA2ADeserialization() {
        String json = milestoneMessage.toJson();
        MilestoneStateMessage deserialized = MilestoneStateMessage.fromJson(json);

        assertEquals(milestoneMessage.caseId(), deserialized.caseId());
        assertEquals(milestoneMessage.milestoneId(), deserialized.milestoneId());
        assertEquals(milestoneMessage.state(), deserialized.state());
    }
}
