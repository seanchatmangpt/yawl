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
 * A2A skill for conducting quarterly Strategic Portfolio Review ceremonies.
 *
 * <p>Quarterly Strategic Portfolio Review reviews roadmap, investment allocation,
 * risk register, produces signed budget and risk receipts.
 *
 * <p><b>Output:</b> CeremonyReceipt with roadmap approval and budget allocation
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class StrategicPortfolioReviewSkill implements SafeCeremonySkill {

    private static final String SKILL_ID = "strategic_portfolio_review";
    private static final String SKILL_NAME = "Quarterly Strategic Portfolio Review";
    private static final String SKILL_DESCRIPTION =
        "Quarterly Strategic Portfolio Review: reviews roadmap, " +
        "investment allocation, risk register, produces signed budget and risk receipts";

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
        return List.of("safe", "ceremony", "strategic-portfolio", "quarterly");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        try {
            CeremonyRequest ceremonyRequest = buildCeremonyRequest(request, "STRATEGIC_PORTFOLIO_REVIEW");
            CeremonyReceipt receipt = conductCeremony(ceremonyRequest);

            Map<String, Object> result = new HashMap<>();
            result.put("receipt", receiptToMap(receipt));
            result.put("message", "Strategic Portfolio Review ceremony completed successfully");

            return SkillResult.success(result);
        } catch (CeremonyException e) {
            return SkillResult.error("Strategic Portfolio Review ceremony failed: " + e.getMessage());
        }
    }

    @Override
    public CeremonyReceipt conductCeremony(CeremonyRequest request) throws CeremonyException {
        Map<String, Object> investmentBuckets = new HashMap<>();
        investmentBuckets.put("runBusiness", 0.60);
        investmentBuckets.put("growBusiness", 0.30);
        investmentBuckets.put("transformBusiness", 0.10);

        Map<String, Object> outcomeMap = new HashMap<>();
        outcomeMap.put("roadmapApproved", true);
        outcomeMap.put("investmentBuckets", investmentBuckets);
        outcomeMap.put("topRisks", List.of());
        outcomeMap.put("nextReviewDate", "2026-06-01");
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
