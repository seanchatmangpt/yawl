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
 * Unit tests for SystemDemoSkill.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class SystemDemoSkillTest {

    private SystemDemoSkill skill;

    @BeforeEach
    void setUp() {
        skill = new SystemDemoSkill();
    }

    @Test
    void skillId_is_system_demo() {
        assertEquals("system_demo", skill.getId());
    }

    @Test
    void skillName_is_set() {
        assertEquals("SAFe System Demo", skill.getName());
    }

    @Test
    void conductCeremony_missingEvaluationReceipt_throwsCeremonyException() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("responsibleAiEvidence", "evidence");

        CeremonyRequest request = new CeremonyRequest(
            "SYSTEM_DEMO",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            inputs,
            java.time.Instant.now()
        );

        assertThrows(CeremonyException.class,
            () -> skill.conductCeremony(request));
    }

    @Test
    void conductCeremony_missingResponsibleAiEvidence_throwsCeremonyException() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluationReceipt", "receipt");

        CeremonyRequest request = new CeremonyRequest(
            "SYSTEM_DEMO",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            inputs,
            java.time.Instant.now()
        );

        assertThrows(CeremonyException.class,
            () -> skill.conductCeremony(request));
    }

    @Test
    void conductCeremony_withAllEvidence_returnsReceipt() throws CeremonyException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluationReceipt", "receipt-data");
        inputs.put("responsibleAiEvidence", "ai-evidence-data");

        CeremonyRequest request = new CeremonyRequest(
            "SYSTEM_DEMO",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            inputs,
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertNotNull(receipt);
        assertEquals("SYSTEM_DEMO", receipt.ceremonyType());
    }

    @Test
    void conductCeremony_outcomeContainsAccepted() throws CeremonyException {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("evaluationReceipt", "receipt");
        inputs.put("responsibleAiEvidence", "evidence");

        CeremonyRequest request = new CeremonyRequest(
            "SYSTEM_DEMO",
            "ART-001",
            "session-123",
            List.of("agent-1"),
            inputs,
            java.time.Instant.now()
        );

        CeremonyReceipt receipt = skill.conductCeremony(request);

        assertTrue(receipt.outcome().contains("accepted"));
    }

    @Test
    void execute_withMissingInputs_returnsError() {
        Map<String, String> params = new HashMap<>();
        params.put("artId", "ART-001");

        SkillRequest skillRequest = new SkillRequest("system_demo", params);
        SkillResult result = skill.execute(skillRequest);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("failed"));
    }

    @Test
    void execute_withAllEvidence_returnsSuccess() {
        Map<String, String> params = new HashMap<>();
        params.put("artId", "ART-001");
        params.put("evaluationReceipt", "receipt-data");
        params.put("responsibleAiEvidence", "ai-evidence-data");

        SkillRequest skillRequest = new SkillRequest("system_demo", params);
        SkillResult result = skill.execute(skillRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.get("receipt"));
    }
}
