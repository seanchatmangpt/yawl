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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InspectAdaptSkill.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class InspectAdaptSkillTest {

    private InspectAdaptSkill skill;

    @BeforeEach
    void setUp() {
        skill = new InspectAdaptSkill();
    }

    @Test
    void skillId_is_inspect_adapt() {
        assertEquals("inspect_adapt", skill.getId());
    }

    @Test
    void skillName_is_set() {
        assertEquals("SAFe Inspect & Adapt", skill.getName());
    }

    @Test
    void skillDescription_contains_keyword() {
        assertTrue(skill.getDescription().contains("Inspect"));
    }

    @Test
    void conductCeremony_validRequest_returnsWorkPackets() throws CeremonyException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("piObjectivesDelta", "");

        CeremonyRequest request = new CeremonyRequest(
            "INSPECT_ADAPT",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            inputs,
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertNotNull(receipt);
        assertTrue(receipt.outcome().contains("workPackets"));
    }

    @Test
    void conductCeremony_receiptHasCorrectCeremonyType() throws CeremonyException {
        CeremonyRequest request = new CeremonyRequest(
            "INSPECT_ADAPT",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            new HashMap<>(),
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertEquals("INSPECT_ADAPT", receipt.ceremonyType());
    }

    @Test
    void execute_withValidRequest_returnsSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("artId", "ART-001");

        SkillRequest skillRequest = new SkillRequest("inspect_adapt", params);
        SkillResult result = skill.execute(skillRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("receipt"));
        assertNotNull(result.get("message"));
    }

    @Test
    void conductCeremony_outcomeContainsRootCause() throws CeremonyException {
        CeremonyRequest request = new CeremonyRequest(
            "INSPECT_ADAPT",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            new HashMap<>(),
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertTrue(receipt.outcome().contains("rootCause"));
    }
}
