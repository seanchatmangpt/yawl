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

package org.yawlfoundation.yawl.integration.a2a.safe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PIPlannningSkill.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class PIPlannningSkillTest {

    private PIPlannningSkill skill;

    @BeforeEach
    void setUp() {
        skill = new PIPlannningSkill();
    }

    @Test
    void skillId_is_pi_planning() {
        assertEquals("pi_planning", skill.getId());
    }

    @Test
    void skillName_is_set() {
        assertEquals("SAFe PI Planning", skill.getName());
    }

    @Test
    void skillDescription_is_set() {
        assertTrue(skill.getDescription().contains("PI Planning"));
    }

    @Test
    void getRequiredPermissions_returns_set() {
        Set<String> perms = skill.getRequiredPermissions();
        assertNotNull(perms);
        assertTrue(perms.contains("workflow:read"));
        assertTrue(perms.contains("workflow:write"));
        assertTrue(perms.contains("ceremony:execute"));
    }

    @Test
    void getTags_returns_list() {
        List<String> tags = skill.getTags();
        assertNotNull(tags);
        assertTrue(tags.contains("safe"));
        assertTrue(tags.contains("ceremony"));
    }

    @Test
    void conductCeremony_validRequest_returnsReceipt() throws CeremonyException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("key", "value");

        CeremonyRequest request = new CeremonyRequest(
            "PI_PLANNING",
            "ART-001",
            "session-123",
            List.of("agent-1", "agent-2"),
            inputs,
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertNotNull(receipt);
        assertEquals("PI_PLANNING", receipt.ceremonyType());
        assertEquals("ART-001", receipt.artId());
    }

    @Test
    void conductCeremony_missingArtId_throwsCeremonyException() {
        CeremonyRequest request = new CeremonyRequest(
            "PI_PLANNING",
            null,
            "session-123",
            List.of("agent-1"),
            new HashMap<>(),
            java.time.Instant.now()
        );

        assertThrows(CeremonyException.class,
            () -> skill.conductCeremony(request));
    }

    @Test
    void conductCeremony_emptyParticipants_throwsCeremonyException() {
        assertThrows(IllegalArgumentException.class,
            () -> new CeremonyRequest(
                "PI_PLANNING",
                "ART-001",
                "session-123",
                List.of(),
                new HashMap<>(),
                java.time.Instant.now()
            ));
    }

    @Test
    void conductCeremony_outcomeContainsPiObjectives() throws CeremonyException {
        CeremonyRequest request = new CeremonyRequest(
            "PI_PLANNING",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            new HashMap<>(),
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertTrue(receipt.outcome().contains("piObjectives"));
    }

    @Test
    void execute_withValidRequest_returnsSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("artId", "ART-002");
        params.put("sessionId", "sess-456");
        params.put("participantAgentIds", "agent-1,agent-2");

        SkillRequest skillRequest = new SkillRequest("pi_planning", params);
        SkillResult result = skill.execute(skillRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("receipt"));
        assertNotNull(result.get("message"));
    }

    @Test
    void execute_withMissingArtId_returnsError() {
        Map<String, String> params = new HashMap<>();
        params.put("sessionId", "sess-456");

        SkillRequest skillRequest = new SkillRequest("pi_planning", params);
        SkillResult result = skill.execute(skillRequest);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("failed"));
    }
}
