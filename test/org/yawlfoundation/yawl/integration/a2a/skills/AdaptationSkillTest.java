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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for AdaptationSkill.
 * Uses real EventDrivenAdaptationEngine — no mocks.
 *
 * @since YAWL 6.0
 */
class AdaptationSkillTest {

    private AdaptationSkill skill;

    @BeforeEach
    void setUp() {
        skill = new AdaptationSkill();
    }

    // =========================================================================
    // Metadata Tests
    // =========================================================================

    @Test
    void testSkillId() {
        assertEquals("adapt_to_event", skill.getId());
    }

    @Test
    void testSkillName() {
        assertNotNull(skill.getName());
        assertFalse(skill.getName().isBlank());
    }

    @Test
    void testSkillDescription() {
        assertNotNull(skill.getDescription());
        String desc = skill.getDescription();
        assertTrue(desc.contains("adapt") || desc.contains("event") || desc.contains("rule"),
            "Description must mention adaptation, events, or rules");
    }

    @Test
    void testRequiredPermissions() {
        Set<String> perms = skill.getRequiredPermissions();
        assertTrue(perms.contains("workflow:adapt"),
            "Must require workflow:adapt permission");
    }

    @Test
    void testTags() {
        List<String> tags = skill.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("no-llm"), "Must be tagged no-llm");
        assertTrue(tags.contains("adaptation") || tags.contains("events") || tags.contains("rules"),
            "Must have domain tags");
    }

    @Test
    void testCanExecuteWithPermission() {
        assertTrue(skill.canExecute(Set.of("workflow:adapt")));
    }

    @Test
    void testCannotExecuteWithoutPermission() {
        assertFalse(skill.canExecute(Set.of("workflow:read")));
    }

    // =========================================================================
    // Parameter Validation
    // =========================================================================

    @Test
    void testMissingEventTypeReturnsError() {
        SkillRequest request = SkillRequest.builder("adapt_to_event").build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Missing eventType must return error");
        assertTrue(result.getError().contains("eventType"),
            "Error must mention eventType parameter");
    }

    @Test
    void testBlankEventTypeReturnsError() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "   ")
            .build();
        SkillResult result = skill.execute(request);
        assertTrue(result.isError(), "Blank eventType must return error");
    }

    // =========================================================================
    // Rule Matching Tests
    // =========================================================================

    @Test
    void testDeadlineExceededEscalates() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "DEADLINE_EXCEEDED")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertTrue((boolean) result.get("adapted"), "DEADLINE_EXCEEDED must trigger adaptation");
        assertEquals("ESCALATE_TO_MANUAL", result.get("action"),
            "DEADLINE_EXCEEDED must ESCALATE_TO_MANUAL");
    }

    @Test
    void testResourceUnavailablePausesCase() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "RESOURCE_UNAVAILABLE")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertTrue((boolean) result.get("adapted"), "RESOURCE_UNAVAILABLE must trigger adaptation");
        assertEquals("PAUSE_CASE", result.get("action"),
            "RESOURCE_UNAVAILABLE must PAUSE_CASE");
    }

    @Test
    void testFraudAlertWithHighRiskRejectsImmediately() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "FRAUD_ALERT")
            .parameter("payload", "risk_score=0.95")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertEquals("REJECT_IMMEDIATELY", result.get("action"),
            "FRAUD_ALERT with risk_score=0.95 must REJECT_IMMEDIATELY");
    }

    @Test
    void testFraudAlertWithLowRiskNoHighRiskReject() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "FRAUD_ALERT")
            .parameter("payload", "risk_score=0.3")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        // risk_score=0.3 < 0.8 threshold — high-risk fraud rule should NOT trigger REJECT_IMMEDIATELY
        assertNotEquals("REJECT_IMMEDIATELY", result.get("action"),
            "FRAUD_ALERT with risk_score=0.3 must NOT REJECT_IMMEDIATELY");
    }

    @Test
    void testUnknownEventTypeReturnsNoMatch() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "TOTALLY_UNKNOWN_XYZ")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Expected success but got: " + result.getError());
        assertFalse((boolean) result.get("adapted"), "Unknown event type must not adapt");
        assertEquals("NO_MATCH", result.get("action"), "Unknown event must return NO_MATCH action");
    }

    // =========================================================================
    // Result Structure Tests
    // =========================================================================

    @Test
    void testResultContainsRequiredKeys() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "DEADLINE_EXCEEDED")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("adapted"), "adapted must be present");
        assertNotNull(result.get("action"), "action must be present");
        assertNotNull(result.get("actionDescription"), "actionDescription must be present");
        assertNotNull(result.get("matchedRuleCount"), "matchedRuleCount must be present");
        assertNotNull(result.get("explanation"), "explanation must be present");
        assertNotNull(result.get("eventType"), "eventType must be present");
        assertNotNull(result.get("severity"), "severity must be present");
        assertNotNull(result.get("elapsed_ms"), "elapsed_ms must be present");
    }

    @Test
    void testElapsedMsIsNonNegative() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "DEADLINE_EXCEEDED")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        long elapsed = (long) result.get("elapsed_ms");
        assertTrue(elapsed >= 0, "elapsed_ms must be non-negative");
    }

    @Test
    void testSeverityParsedCorrectly() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "DEADLINE_EXCEEDED")
            .parameter("severity", "HIGH")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertEquals("HIGH", result.get("severity"),
            "Severity HIGH must be reflected in result");
    }

    @Test
    void testDefaultSeverityIsMedium() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "DEADLINE_EXCEEDED")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess());
        assertEquals("MEDIUM", result.get("severity"),
            "Default severity must be MEDIUM");
    }

    @Test
    void testCaseInsensitiveEventType() {
        SkillRequest request = SkillRequest.builder("adapt_to_event")
            .parameter("eventType", "deadline_exceeded")
            .build();
        SkillResult result = skill.execute(request);

        assertTrue(result.isSuccess(), "Case-insensitive eventType must be accepted");
        assertTrue((boolean) result.get("adapted"),
            "Lowercase DEADLINE_EXCEEDED must still match");
    }
}
