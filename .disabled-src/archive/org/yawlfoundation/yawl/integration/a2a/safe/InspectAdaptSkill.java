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
 * A2A skill for conducting SAFe Inspect & Adapt ceremonies.
 *
 * <p>Runs Inspect & Adapt: analyzes PI telemetry and receipts,
 * produces improvement work packets.
 *
 * <p><b>Inputs:</b>
 * <ul>
 *   <li>{@code piObjectivesDelta} - PI Objectives results (optional)</li>
 * </ul>
 *
 * <p><b>Output:</b> CeremonyReceipt with improvement work packets
 *
 * <p><b>Note:</b> LLM integration (GroqLlmGateway) is wired by Quantum C.
 * This skill uses deterministic placeholder work packets.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class InspectAdaptSkill implements SafeCeremonySkill {

    private static final String SKILL_ID = "inspect_adapt";
    private static final String SKILL_NAME = "SAFe Inspect & Adapt";
    private static final String SKILL_DESCRIPTION =
        "Runs Inspect & Adapt: analyzes PI telemetry and receipts, " +
        "produces improvement work packets";

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
        return List.of("safe", "ceremony", "inspect-adapt", "continuous-improvement");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        try {
            CeremonyRequest ceremonyRequest = buildCeremonyRequest(request, "INSPECT_ADAPT");
            CeremonyReceipt receipt = conductCeremony(ceremonyRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("receipt", receiptToMap(receipt));
            result.put("message", "Inspect & Adapt ceremony completed successfully");

            return SkillResult.success(result);
        } catch (CeremonyException e) {
            return SkillResult.error("Inspect & Adapt ceremony failed: " + e.getMessage());
        }
    }

    @Override
    public CeremonyReceipt conductCeremony(CeremonyRequest request) throws CeremonyException {
        Map<String, Object> inputs = request.inputs();
        String piObjectivesDelta = (String) inputs.getOrDefault("piObjectivesDelta", "");

        Map<String, Object> workPacket1 = new HashMap<>();
        workPacket1.put("type", "story");
        workPacket1.put("title", "Improve deployment frequency");
        workPacket1.put("priority", "HIGH");

        Map<String, Object> workPacket2 = new HashMap<>();
        workPacket2.put("type", "story");
        workPacket2.put("title", "Reduce defect escape rate");
        workPacket2.put("priority", "MEDIUM");

        Map<String, Object> outcomeMap = new HashMap<>();
        outcomeMap.put("workPackets", List.of(workPacket1, workPacket2));
        outcomeMap.put("rootCause", "PI telemetry shows deployment bottleneck");
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
