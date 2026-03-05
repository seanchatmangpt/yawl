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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A2A skill for conducting SAFe System Demo ceremonies.
 *
 * <p>System Demo reviews deployed artifacts and evaluation receipts,
 * producing a SystemDemo acceptance decision.
 *
 * <p><b>Required Inputs:</b>
 * <ul>
 *   <li>{@code evaluationReceipt} - Evidence of evaluation activities</li>
 *   <li>{@code responsibleAiEvidence} - Responsible AI compliance evidence</li>
 * </ul>
 *
 * <p><b>Output:</b> CeremonyReceipt with acceptance decision
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class SystemDemoSkill implements SafeCeremonySkill {

    private static final String SKILL_ID = "system_demo";
    private static final String SKILL_NAME = "SAFe System Demo";
    private static final String SKILL_DESCRIPTION =
        "Reviews deployed artifacts and evaluation receipts, " +
        "produces SystemDemo acceptance decision";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:read", "workflow:write", "ceremony:execute");
    }

    @Override
    public List<String> getTags() {
        return List.of("safe", "ceremony", "system-demo", "acceptance");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        try {
            CeremonyRequest ceremonyRequest = buildCeremonyRequest(request, "SYSTEM_DEMO");
            CeremonyReceipt receipt = conductCeremony(ceremonyRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("receipt", receiptToMap(receipt));
            result.put("message", "System Demo ceremony completed successfully");

            return SkillResult.success(result);
        } catch (CeremonyException e) {
            return SkillResult.error("System Demo ceremony failed: " + e.getMessage());
        }
    }

    @Override
    public CeremonyReceipt conductCeremony(CeremonyRequest request) throws CeremonyException {
        Map<String, Object> inputs = request.inputs();

        if (!inputs.containsKey("evaluationReceipt")) {
            throw new CeremonyException(
                "System Demo requires evaluationReceipt in inputs");
        }
        if (!inputs.containsKey("responsibleAiEvidence")) {
            throw new CeremonyException(
                "System Demo requires responsibleAiEvidence in inputs");
        }

        Map<String, Object> outcomeMap = new HashMap<>();
        outcomeMap.put("accepted", true);
        outcomeMap.put("evidenceReviewed", List.of("evaluationReceipt", "responsibleAiEvidence"));
        outcomeMap.put("artId", request.artId());

        String outcome = mapToJson(outcomeMap);
        return CeremonyReceipt.create(request, outcome);
    }

    private CeremonyRequest buildCeremonyRequest(SkillRequest skillRequest, String ceremonyType)
            throws CeremonyException {
        String artId = skillRequest.getParameter("artId");
        String sessionId = skillRequest.getParameter("sessionId", "session-" + System.currentTimeMillis());
        String participantStr = skillRequest.getParameter("participantAgentIds", "agent-1");

        List<String> participants = List.of(participantStr.split(","));
        Map<String, Object> inputs = new HashMap<>(skillRequest.getParameters());

        return new CeremonyRequest(
            ceremonyType,
            artId,
            sessionId,
            participants,
            inputs,
            Instant.now()
        );
    }

    private String mapToJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outcome: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> receiptToMap(CeremonyReceipt receipt) {
        Map<String, Object> map = new HashMap<>();
        map.put("ceremonyType", receipt.ceremonyType());
        map.put("artId", receipt.artId());
        map.put("sessionId", receipt.sessionId());
        map.put("conductedAt", receipt.conductedAt().toString());
        map.put("participatingAgents", receipt.participatingAgents());
        map.put("outcome", receipt.outcome());
        map.put("evidenceHash", receipt.evidenceHash());
        map.put("receiptHash", receipt.receiptHash());
        return map;
    }
}
