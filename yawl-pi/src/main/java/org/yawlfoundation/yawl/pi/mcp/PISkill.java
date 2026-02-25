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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;
import org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade;
import org.yawlfoundation.yawl.pi.PIException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A2A Skill for Process Intelligence operations.
 *
 * <p>Provides 4 analysis types:
 * <ul>
 *   <li><b>predict</b> - Predict case outcome (completion probability, risk score)</li>
 *   <li><b>recommend</b> - Recommend prescriptive actions (reroute, escalate, reallocate)</li>
 *   <li><b>ask</b> - Natural language query over process knowledge base</li>
 *   <li><b>prepare-event-data</b> - Convert event data to OCEL2 format</li>
 * </ul>
 *
 * <p><b>Required Permissions:</b> {@code process-intelligence:read}, {@code process-intelligence:write}
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PISkill implements A2ASkill {

    private static final Logger LOGGER = LogManager.getLogger(PISkill.class);
    private static final String SKILL_ID = "process_intelligence";
    private static final String SKILL_NAME = "Process Intelligence";
    private static final String SKILL_DESCRIPTION =
        "Analyze YAWL workflow cases using process intelligence: predict outcomes, " +
        "recommend actions, query knowledge base, and prepare event data for process mining.";

    private final ProcessIntelligenceFacade facade;

    /**
     * Create a Process Intelligence skill.
     *
     * @param facade ProcessIntelligenceFacade for all PI operations (required)
     * @throws IllegalArgumentException if facade is null
     */
    public PISkill(ProcessIntelligenceFacade facade) {
        if (facade == null) {
            throw new IllegalArgumentException("ProcessIntelligenceFacade is required");
        }
        this.facade = facade;
    }

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
        return Set.of("process-intelligence:read", "process-intelligence:write");
    }

    @Override
    public List<String> getTags() {
        return List.of("workflow", "process-intelligence", "predictive", "prescriptive", "optimization", "rag");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        if (request == null) {
            return SkillResult.error("Request cannot be null");
        }

        String analysisType = request.getParameter("analysisType", "predict");

        long startTime = System.currentTimeMillis();

        try {
            SkillResult result = switch (analysisType.toLowerCase()) {
                case "predict" -> executePredictAnalysis(request);
                case "recommend" -> executeRecommendAnalysis(request);
                case "ask" -> executeAskAnalysis(request);
                case "prepare-event-data" -> executePrepareEventDataAnalysis(request);
                default -> SkillResult.error(
                    "Unknown analysis type: " + analysisType +
                    ". Supported: predict, recommend, ask, prepare-event-data");
            };

            long executionTime = System.currentTimeMillis() - startTime;
            LOGGER.info("Process intelligence {} completed in {} ms", analysisType, executionTime);
            return result;
        } catch (Exception e) {
            LOGGER.error("Process intelligence {} failed: {}", analysisType, e.getMessage(), e);
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("exception", e.getClass().getSimpleName());
            errorData.put("message", e.getMessage());
            return SkillResult.error("Analysis failed: " + e.getMessage(), errorData);
        }
    }

    /**
     * Execute predict analysis type.
     *
     * @param request skill request with caseId parameter
     * @return skill result with prediction
     */
    private SkillResult executePredictAnalysis(SkillRequest request) throws PIException {
        String caseId = request.getParameter("caseId");
        if (caseId == null || caseId.isEmpty()) {
            return SkillResult.error("Parameter 'caseId' is required for predict analysis");
        }

        long startTime = System.currentTimeMillis();
        var prediction = facade.predictOutcome(caseId);

        Map<String, Object> data = new HashMap<>();
        data.put("caseId", prediction.caseId());
        data.put("completionProbability", prediction.completionProbability());
        data.put("riskScore", prediction.riskScore());
        data.put("primaryRiskFactor", prediction.primaryRiskFactor());
        data.put("fromOnnxModel", prediction.fromOnnxModel());
        data.put("predictedAt", prediction.predictedAt().toString());

        long executionTime = System.currentTimeMillis() - startTime;
        return SkillResult.success(data, executionTime);
    }

    /**
     * Execute recommend analysis type.
     *
     * @param request skill request with caseId and optional completionProbability/riskScore
     * @return skill result with recommended actions
     */
    private SkillResult executeRecommendAnalysis(SkillRequest request) throws PIException {
        String caseId = request.getParameter("caseId");
        if (caseId == null || caseId.isEmpty()) {
            return SkillResult.error("Parameter 'caseId' is required for recommend analysis");
        }

        double completionProb = parseDouble(request.getParameter("completionProbability"), 0.5);
        double riskScore = parseDouble(request.getParameter("riskScore"), 0.5);

        long startTime = System.currentTimeMillis();

        var prediction = new org.yawlfoundation.yawl.pi.predictive.CaseOutcomePrediction(
            caseId,
            completionProb,
            riskScore,
            "Based on agent input",
            false,
            java.time.Instant.now()
        );

        var actions = facade.recommendActions(caseId, prediction);

        Map<String, Object> data = new HashMap<>();
        data.put("caseId", caseId);
        data.put("actionCount", actions.size());
        data.put("recommendations", actions.stream()
            .map(a -> Map.of(
                "type", a.getClass().getSimpleName(),
                "rationale", a.rationale(),
                "expectedImprovementScore", a.expectedImprovementScore()))
            .toList());

        long executionTime = System.currentTimeMillis() - startTime;
        return SkillResult.success(data, executionTime);
    }

    /**
     * Execute ask analysis type (RAG query).
     *
     * @param request skill request with question and optional specificationId/topK
     * @return skill result with natural language answer
     */
    private SkillResult executeAskAnalysis(SkillRequest request) throws PIException {
        String question = request.getParameter("question");
        if (question == null || question.isEmpty()) {
            return SkillResult.error("Parameter 'question' is required for ask analysis");
        }

        String specId = request.getParameter("specificationId");
        int topK = parseInt(request.getParameter("topK"), 5);

        long startTime = System.currentTimeMillis();

        var nlRequest = specId != null
            ? new org.yawlfoundation.yawl.pi.rag.NlQueryRequest(
                question, specId, topK, request.getRequestId())
            : new org.yawlfoundation.yawl.pi.rag.NlQueryRequest(
                question, null, topK, request.getRequestId());

        var response = facade.ask(nlRequest);

        Map<String, Object> data = new HashMap<>();
        data.put("requestId", response.requestId());
        data.put("answer", response.answer());
        data.put("sourceFacts", response.sourceFacts());
        data.put("groundedInKnowledgeBase", response.groundedInKnowledgeBase());
        data.put("modelUsed", response.modelUsed());
        data.put("latencyMs", response.latencyMs());

        long executionTime = System.currentTimeMillis() - startTime;
        return SkillResult.success(data, executionTime);
    }

    /**
     * Execute prepare-event-data analysis type.
     *
     * @param request skill request with eventData and optional format
     * @return skill result with OCEL2 JSON
     */
    private SkillResult executePrepareEventDataAnalysis(SkillRequest request) throws PIException {
        String eventData = request.getParameter("eventData");
        if (eventData == null || eventData.isEmpty()) {
            return SkillResult.error("Parameter 'eventData' is required for prepare-event-data analysis");
        }

        String format = request.getParameter("format");

        long startTime = System.currentTimeMillis();

        String ocel2Json;
        if (format != null && !format.isEmpty()) {
            ocel2Json = facade.prepareEventData(eventData, format);
        } else {
            ocel2Json = facade.prepareEventData(eventData);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("ocel2Json", ocel2Json);
        data.put("status", "converted to OCEL2");

        long executionTime = System.currentTimeMillis() - startTime;
        return SkillResult.success(data, executionTime);
    }

    /**
     * Parse double from string parameter with default fallback.
     *
     * @param value string value
     * @param defaultValue fallback if parsing fails
     * @return parsed double or default
     */
    private double parseDouble(String value, double defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Could not parse double from '{}', using default {}", value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Parse int from string parameter with default fallback.
     *
     * @param value string value
     * @param defaultValue fallback if parsing fails
     * @return parsed int or default
     */
    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Could not parse int from '{}', using default {}", value, defaultValue);
            return defaultValue;
        }
    }
}
