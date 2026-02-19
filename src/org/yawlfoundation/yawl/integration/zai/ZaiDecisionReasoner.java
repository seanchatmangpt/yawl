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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI-powered decision reasoner for YAWL workflows using Z.AI.
 *
 * <p>This class provides intelligent decision-making capabilities for workflow routing,
 * task assignment, data validation, and process optimization. It integrates with the
 * Z.AI API to leverage large language models for complex decision logic.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Workflow routing decisions based on context and business rules</li>
 *   <li>Dynamic task assignment with skill and workload consideration</li>
 *   <li>Data quality assessment and anomaly detection</li>
 *   <li>Process bottleneck identification and recommendations</li>
 *   <li>Confidence scoring and decision audit trails</li>
 * </ul>
 *
 * <h2>Supported Models:</h2>
 * <ul>
 *   <li>GLM-4.7-Flash - Fast responses for real-time decisions</li>
 *   <li>glm-4.6 - Balanced performance for complex reasoning</li>
 *   <li>glm-4.5 - Standard model for general decisions</li>
 *   <li>glm-5 - Advanced reasoning for critical decisions</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * ZaiService zaiService = new ZaiService(apiKey);
 * ZaiDecisionReasoner reasoner = new ZaiDecisionReasoner(zaiService);
 *
 * // Make a routing decision
 * WorkflowContext context = new WorkflowContext("OrderProcessing", "ReviewOrder", caseData);
 * DecisionResult result = reasoner.makeRoutingDecision(context, routingOptions);
 * if (result.isConfident()) {
 *     executeRoute(result.getSelectedOption());
 * }
 * }</pre>
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 * @since 5.2
 */
public class ZaiDecisionReasoner {

    private static final Logger logger = LogManager.getLogger(ZaiDecisionReasoner.class);

    private static final String DEFAULT_MODEL = "GLM-4.7-Flash";
    private static final String ADVANCED_MODEL = "glm-5";
    private static final double CONFIDENCE_THRESHOLD = 0.7;

    private final ZaiService zaiService;
    private final ObjectMapper objectMapper;
    private final String defaultModel;
    private final Map<String, DecisionCacheEntry> decisionCache;
    private final List<DecisionAuditEntry> auditLog;
    private final int maxCacheSize;
    private final long cacheExpirationMs;

    /**
     * Creates a new ZaiDecisionReasoner with the specified Z.AI service.
     *
     * @param zaiService the Z.AI service for API calls (must not be null)
     * @throws IllegalArgumentException if zaiService is null
     */
    public ZaiDecisionReasoner(ZaiService zaiService) {
        this(zaiService, DEFAULT_MODEL, 1000, 3600000);
    }

    /**
     * Creates a new ZaiDecisionReasoner with custom configuration.
     *
     * @param zaiService the Z.AI service for API calls
     * @param defaultModel the default model to use for decisions
     * @param maxCacheSize maximum number of decisions to cache
     * @param cacheExpirationMs cache entry expiration time in milliseconds
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ZaiDecisionReasoner(ZaiService zaiService, String defaultModel,
                                int maxCacheSize, long cacheExpirationMs) {
        if (zaiService == null) {
            throw new IllegalArgumentException("ZaiService cannot be null");
        }
        if (defaultModel == null || defaultModel.isEmpty()) {
            throw new IllegalArgumentException("Default model cannot be null or empty");
        }
        if (maxCacheSize < 0) {
            throw new IllegalArgumentException("Max cache size must be non-negative");
        }
        if (cacheExpirationMs < 0) {
            throw new IllegalArgumentException("Cache expiration must be non-negative");
        }

        this.zaiService = zaiService;
        this.objectMapper = new ObjectMapper();
        this.defaultModel = defaultModel;
        this.maxCacheSize = maxCacheSize;
        this.cacheExpirationMs = cacheExpirationMs;
        this.decisionCache = new ConcurrentHashMap<>(maxCacheSize);
        this.auditLog = Collections.synchronizedList(new ArrayList<>());

        logger.info("ZaiDecisionReasoner initialized with model={}, cacheSize={}, cacheExpiry={}ms",
                defaultModel, maxCacheSize, cacheExpirationMs);
    }

    /**
     * Makes a routing decision based on workflow context and available options.
     *
     * <p>This method analyzes the current workflow state, input data, and business
     * rules to determine the optimal routing path. It uses AI reasoning to handle
     * complex conditions that cannot be expressed in standard workflow predicates.</p>
     *
     * @param context the workflow context containing case data and current state
     * @param options the list of possible routing options
     * @return a decision result with the selected option and confidence score
     * @throws DecisionException if the decision cannot be made
     */
    public DecisionResult makeRoutingDecision(WorkflowContext context, List<RoutingOption> options)
            throws DecisionException {
        return makeRoutingDecision(context, options, DecisionCriteria.DEFAULT);
    }

    /**
     * Makes a routing decision with custom criteria.
     *
     * @param context the workflow context
     * @param options the routing options
     * @param criteria additional decision criteria
     * @return the decision result
     * @throws DecisionException if decision fails
     */
    public DecisionResult makeRoutingDecision(WorkflowContext context, List<RoutingOption> options,
                                               DecisionCriteria criteria) throws DecisionException {
        if (context == null) {
            throw new IllegalArgumentException("Workflow context cannot be null");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Options list cannot be null or empty");
        }

        String cacheKey = buildCacheKey("routing", context, options);
        DecisionResult cachedResult = getCachedDecision(cacheKey);
        if (cachedResult != null) {
            logger.debug("Returning cached routing decision for case {}", context.getCaseId());
            return cachedResult;
        }

        long startTime = System.currentTimeMillis();
        String selectedModel = selectModelForDecision(criteria);

        try {
            String prompt = buildRoutingPrompt(context, options, criteria);
            String response = zaiService.chat(prompt, selectedModel);

            DecisionResult result = parseRoutingResponse(response, options);

            long duration = System.currentTimeMillis() - startTime;
            logDecision("ROUTING", context.getCaseId(), result, duration, selectedModel);

            cacheDecision(cacheKey, result);

            logger.info("Routing decision for case {}: selected {} with confidence {} ({}ms)",
                    context.getCaseId(), result.getSelectedOption(), result.getConfidence(), duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logDecisionError("ROUTING", context.getCaseId(), e.getMessage(), duration);
            throw new DecisionException("Failed to make routing decision: " + e.getMessage(), e);
        }
    }

    /**
     * Analyzes workflow data quality and returns assessment results.
     *
     * @param data the workflow data to analyze
     * @param dataSchema the expected schema or validation rules
     * @return data quality assessment result
     * @throws DecisionException if analysis fails
     */
    public DataQualityResult assessDataQuality(String data, String dataSchema) throws DecisionException {
        if (data == null || data.isEmpty()) {
            return new DataQualityResult(0.0, "Empty data", Collections.emptyList());
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildDataQualityPrompt(data, dataSchema);
            String response = zaiService.chat(prompt, defaultModel);

            DataQualityResult result = parseDataQualityResponse(response);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Data quality assessment completed: score {} ({}ms)", result.getScore(), duration);

            return result;
        } catch (Exception e) {
            throw new DecisionException("Data quality assessment failed: " + e.getMessage(), e);
        }
    }

    /**
     * Identifies potential bottlenecks in workflow execution.
     *
     * @param executionHistory historical execution data as JSON
     * @param workflowSpec the workflow specification
     * @return bottleneck analysis result with recommendations
     * @throws DecisionException if analysis fails
     */
    public BottleneckAnalysis analyzeBottlenecks(String executionHistory, String workflowSpec)
            throws DecisionException {
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildBottleneckPrompt(executionHistory, workflowSpec);
            String response = zaiService.chat(prompt, ADVANCED_MODEL);

            BottleneckAnalysis analysis = parseBottleneckResponse(response);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Bottleneck analysis completed: found {} bottlenecks ({}ms)",
                    analysis.getBottlenecks().size(), duration);

            return analysis;
        } catch (Exception e) {
            throw new DecisionException("Bottleneck analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates task assignment recommendation based on skills and workload.
     *
     * @param taskDescription the task to assign
     * @param availableWorkers list of available workers with their skills
     * @param workloadData current workload data for each worker
     * @return assignment recommendation with reasoning
     * @throws DecisionException if recommendation fails
     */
    public AssignmentRecommendation recommendTaskAssignment(String taskDescription,
                                                              List<WorkerProfile> availableWorkers,
                                                              Map<String, Integer> workloadData)
            throws DecisionException {
        if (taskDescription == null || taskDescription.isEmpty()) {
            throw new IllegalArgumentException("Task description cannot be null or empty");
        }
        if (availableWorkers == null || availableWorkers.isEmpty()) {
            throw new IllegalArgumentException("Available workers list cannot be null or empty");
        }

        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildAssignmentPrompt(taskDescription, availableWorkers, workloadData);
            String response = zaiService.chat(prompt, defaultModel);

            AssignmentRecommendation recommendation = parseAssignmentResponse(response, availableWorkers);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Task assignment recommendation: {} for task ({}ms)",
                    recommendation.getRecommendedWorkerId(), duration);

            return recommendation;
        } catch (Exception e) {
            throw new DecisionException("Task assignment recommendation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Predicts workflow completion time based on current state.
     *
     * @param context the current workflow context
     * @param historicalData historical execution times
     * @return completion time prediction
     * @throws DecisionException if prediction fails
     */
    public CompletionPrediction predictCompletionTime(WorkflowContext context, String historicalData)
            throws DecisionException {
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildCompletionPrompt(context, historicalData);
            String response = zaiService.chat(prompt, defaultModel);

            CompletionPrediction prediction = parseCompletionResponse(response);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Completion prediction for case {}: {} minutes ({}ms)",
                    context.getCaseId(), prediction.getEstimatedMinutes(), duration);

            return prediction;
        } catch (Exception e) {
            throw new DecisionException("Completion prediction failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates workflow transition based on business rules.
     *
     * @param fromState current state
     * @param toState proposed next state
     * @param context workflow context with case data
     * @param businessRules applicable business rules
     * @return validation result with approval or rejection reason
     * @throws DecisionException if validation fails
     */
    public TransitionValidation validateTransition(String fromState, String toState,
                                                     WorkflowContext context, String businessRules)
            throws DecisionException {
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildTransitionPrompt(fromState, toState, context, businessRules);
            String response = zaiService.chat(prompt, defaultModel);

            TransitionValidation validation = parseTransitionResponse(response);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Transition validation {}->{}: {} ({}ms)",
                    fromState, toState, validation.isValid() ? "APPROVED" : "REJECTED", duration);

            return validation;
        } catch (Exception e) {
            throw new DecisionException("Transition validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates optimization suggestions for a workflow.
     *
     * @param workflowSpec the workflow specification
     * @param performanceMetrics current performance metrics
     * @return list of optimization suggestions
     * @throws DecisionException if generation fails
     */
    public List<OptimizationSuggestion> generateOptimizations(String workflowSpec, String performanceMetrics)
            throws DecisionException {
        long startTime = System.currentTimeMillis();

        try {
            String prompt = buildOptimizationPrompt(workflowSpec, performanceMetrics);
            String response = zaiService.chat(prompt, ADVANCED_MODEL);

            List<OptimizationSuggestion> suggestions = parseOptimizationResponse(response);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Generated {} optimization suggestions ({}ms)", suggestions.size(), duration);

            return suggestions;
        } catch (Exception e) {
            throw new DecisionException("Optimization generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the audit log of all decisions made.
     *
     * @return unmodifiable list of audit entries
     */
    public List<DecisionAuditEntry> getAuditLog() {
        return Collections.unmodifiableList(new ArrayList<>(auditLog));
    }

    /**
     * Clears the decision cache.
     */
    public void clearCache() {
        decisionCache.clear();
        logger.info("Decision cache cleared");
    }

    /**
     * Gets the current cache size.
     *
     * @return number of cached decisions
     */
    public int getCacheSize() {
        return decisionCache.size();
    }

    /**
     * Exports decision audit log as JSON.
     *
     * @return JSON string of audit entries
     * @throws IOException if serialization fails
     */
    public String exportAuditLog() throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.putPOJO("entries", new ArrayList<>(auditLog));
        root.put("exportedAt", System.currentTimeMillis());
        root.put("totalEntries", auditLog.size());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    // Private helper methods

    private String selectModelForDecision(DecisionCriteria criteria) {
        if (criteria != null && criteria.isHighStakes()) {
            return ADVANCED_MODEL;
        }
        return defaultModel;
    }

    private String buildCacheKey(String decisionType, WorkflowContext context, List<?> options) {
        int optionsHash = options != null ? options.hashCode() : 0;
        return String.format("%s:%s:%s:%d", decisionType, context.getCaseId(),
                context.getCurrentTask(), optionsHash);
    }

    private DecisionResult getCachedDecision(String cacheKey) {
        DecisionCacheEntry entry = decisionCache.get(cacheKey);
        if (entry != null && !entry.isExpired(cacheExpirationMs)) {
            return entry.getResult();
        }
        if (entry != null) {
            decisionCache.remove(cacheKey);
        }
        return null;
    }

    private void cacheDecision(String cacheKey, DecisionResult result) {
        if (decisionCache.size() >= maxCacheSize) {
            evictOldestEntries(maxCacheSize / 10);
        }
        decisionCache.put(cacheKey, new DecisionCacheEntry(result, System.currentTimeMillis()));
    }

    private void evictOldestEntries(int count) {
        decisionCache.entrySet().stream()
                .sorted(Comparator.comparingLong(e -> e.getValue().getTimestamp()))
                .limit(count)
                .map(Map.Entry::getKey)
                .forEach(decisionCache::remove);
    }

    private String buildRoutingPrompt(WorkflowContext context, List<RoutingOption> options,
                                       DecisionCriteria criteria) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a YAWL workflow routing decision engine. Analyze the context and select the best route.\n\n");
        prompt.append("## Workflow Context\n");
        prompt.append("- Case ID: ").append(context.getCaseId()).append("\n");
        prompt.append("- Workflow: ").append(context.getWorkflowName()).append("\n");
        prompt.append("- Current Task: ").append(context.getCurrentTask()).append("\n");
        prompt.append("- Case Data: ").append(context.getCaseData()).append("\n\n");

        if (criteria != null && criteria.getAdditionalContext() != null) {
            prompt.append("## Additional Criteria\n");
            prompt.append(criteria.getAdditionalContext()).append("\n\n");
        }

        prompt.append("## Available Routes\n");
        for (int i = 0; i < options.size(); i++) {
            RoutingOption option = options.get(i);
            prompt.append(String.format("%d. %s - %s (Priority: %s)\n",
                    i + 1, option.getId(), option.getDescription(), option.getPriority()));
        }

        prompt.append("\n## Response Format\n");
        prompt.append("Respond in this exact JSON format:\n");
        prompt.append("{\"selected_index\": <number>, \"confidence\": <0.0-1.0>, \"reasoning\": \"<explanation>\"}\n");
        prompt.append("Select the most appropriate route based on workflow data and business logic.");

        return prompt.toString();
    }

    private DecisionResult parseRoutingResponse(String response, List<RoutingOption> options)
            throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            int selectedIndex = root.path("selected_index").asInt(1) - 1;
            if (selectedIndex < 0 || selectedIndex >= options.size()) {
                selectedIndex = 0;
            }

            double confidence = root.path("confidence").asDouble(0.5);
            String reasoning = root.path("reasoning").asText("No reasoning provided");

            RoutingOption selected = options.get(selectedIndex);
            return new DecisionResult(selected.getId(), confidence, reasoning, Map.of(
                    "option_index", selectedIndex,
                    "option_count", options.size()
            ));
        } catch (Exception e) {
            throw new DecisionException("Failed to parse routing response: " + e.getMessage(), e);
        }
    }

    private String buildDataQualityPrompt(String data, String schema) {
        return String.format("""
            Analyze the following workflow data for quality issues.

            ## Data
            %s

            ## Expected Schema/Rules
            %s

            ## Response Format
            Respond in this JSON format:
            {"score": <0.0-1.0>, "summary": "<brief assessment>", "issues": ["<issue1>", "<issue2>...]}

            Evaluate: completeness, accuracy, consistency, and conformance to schema.
            """, data, schema != null ? schema : "No specific schema provided");
    }

    private DataQualityResult parseDataQualityResponse(String response) throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            double score = root.path("score").asDouble(0.0);
            String summary = root.path("summary").asText("No summary provided");

            List<String> issues = new ArrayList<>();
            JsonNode issuesNode = root.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode issue : issuesNode) {
                    issues.add(issue.asText());
                }
            }

            return new DataQualityResult(score, summary, issues);
        } catch (Exception e) {
            throw new DecisionException("Failed to parse data quality response: " + e.getMessage(), e);
        }
    }

    private String buildBottleneckPrompt(String executionHistory, String workflowSpec) {
        return String.format("""
            Analyze the following workflow execution data to identify bottlenecks.

            ## Workflow Specification
            %s

            ## Execution History
            %s

            ## Response Format
            Respond in this JSON format:
            {
              "bottlenecks": [
                {"task_id": "<id>", "type": "<wait_time|resource|complexity>", "severity": "<high|medium|low>", "description": "<reason>"}
              ],
              "recommendations": ["<rec1>", "<rec2>..."]
            }

            Identify tasks with unusual wait times, resource contention, or design issues.
            """, workflowSpec, executionHistory);
    }

    private BottleneckAnalysis parseBottleneckResponse(String response) throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            List<Bottleneck> bottlenecks = new ArrayList<>();
            JsonNode bottlenecksNode = root.path("bottlenecks");
            if (bottlenecksNode.isArray()) {
                for (JsonNode node : bottlenecksNode) {
                    bottlenecks.add(new Bottleneck(
                            node.path("task_id").asText(),
                            node.path("type").asText(),
                            node.path("severity").asText(),
                            node.path("description").asText()
                    ));
                }
            }

            List<String> recommendations = new ArrayList<>();
            JsonNode recsNode = root.path("recommendations");
            if (recsNode.isArray()) {
                for (JsonNode rec : recsNode) {
                    recommendations.add(rec.asText());
                }
            }

            return new BottleneckAnalysis(bottlenecks, recommendations);
        } catch (Exception e) {
            throw new DecisionException("Failed to parse bottleneck response: " + e.getMessage(), e);
        }
    }

    private String buildAssignmentPrompt(String task, List<WorkerProfile> workers,
                                          Map<String, Integer> workload) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Recommend the best worker for task assignment.\n\n");
        prompt.append("## Task Description\n").append(task).append("\n\n");
        prompt.append("## Available Workers\n");

        for (WorkerProfile worker : workers) {
            int currentLoad = workload.getOrDefault(worker.getId(), 0);
            prompt.append(String.format("- %s: Skills=%s, Load=%d, Level=%s\n",
                    worker.getId(), worker.getSkills(), currentLoad, worker.getLevel()));
        }

        prompt.append("\n## Response Format\n");
        prompt.append("{\"worker_id\": \"<id>\", \"confidence\": <0.0-1.0>, \"reasoning\": \"<explanation>\"}\n");

        return prompt.toString();
    }

    private AssignmentRecommendation parseAssignmentResponse(String response, List<WorkerProfile> workers)
            throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            String workerId = root.path("worker_id").asText();
            double confidence = root.path("confidence").asDouble(0.5);
            String reasoning = root.path("reasoning").asText("No reasoning provided");

            WorkerProfile assigned = workers.stream()
                    .filter(w -> w.getId().equals(workerId))
                    .findFirst()
                    .orElse(workers.get(0));

            return new AssignmentRecommendation(assigned.getId(), confidence, reasoning);
        } catch (Exception e) {
            throw new DecisionException("Failed to parse assignment response: " + e.getMessage(), e);
        }
    }

    private String buildCompletionPrompt(WorkflowContext context, String historicalData) {
        return String.format("""
            Predict workflow completion time.

            ## Current Context
            Case ID: %s
            Workflow: %s
            Current Task: %s
            Data: %s

            ## Historical Data
            %s

            ## Response Format
            {"estimated_minutes": <number>, "confidence": <0.0-1.0>, "factors": ["<factor1>", "<factor2>...]}
            """, context.getCaseId(), context.getWorkflowName(), context.getCurrentTask(),
                context.getCaseData(), historicalData != null ? historicalData : "No historical data");
    }

    private CompletionPrediction parseCompletionResponse(String response) throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            int estimatedMinutes = root.path("estimated_minutes").asInt(60);
            double confidence = root.path("confidence").asDouble(0.5);

            List<String> factors = new ArrayList<>();
            JsonNode factorsNode = root.path("factors");
            if (factorsNode.isArray()) {
                for (JsonNode factor : factorsNode) {
                    factors.add(factor.asText());
                }
            }

            return new CompletionPrediction(estimatedMinutes, confidence, factors);
        } catch (Exception e) {
            throw new DecisionException("Failed to parse completion response: " + e.getMessage(), e);
        }
    }

    private String buildTransitionPrompt(String fromState, String toState,
                                          WorkflowContext context, String rules) {
        return String.format("""
            Validate workflow state transition.

            ## Transition
            From: %s
            To: %s

            ## Context
            Case ID: %s
            Data: %s

            ## Business Rules
            %s

            ## Response Format
            {"valid": <true|false>, "reason": "<explanation>", "warnings": ["<warning>"...]}
            """, fromState, toState, context.getCaseId(), context.getCaseData(),
                rules != null ? rules : "No specific rules");
    }

    private TransitionValidation parseTransitionResponse(String response) throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            boolean valid = root.path("valid").asBoolean(false);
            String reason = root.path("reason").asText("No reason provided");

            List<String> warnings = new ArrayList<>();
            JsonNode warningsNode = root.path("warnings");
            if (warningsNode.isArray()) {
                for (JsonNode warning : warningsNode) {
                    warnings.add(warning.asText());
                }
            }

            return new TransitionValidation(valid, reason, warnings);
        } catch (Exception e) {
            throw new DecisionException("Failed to parse transition response: " + e.getMessage(), e);
        }
    }

    private String buildOptimizationPrompt(String spec, String metrics) {
        return String.format("""
            Analyze workflow and suggest optimizations.

            ## Workflow Specification
            %s

            ## Performance Metrics
            %s

            ## Response Format
            {
              "suggestions": [
                {"area": "<parallelism|data_flow|task_design>", "suggestion": "<text>", "impact": "<high|medium|low>"}
              ]
            }
            """, spec, metrics != null ? metrics : "No metrics available");
    }

    private List<OptimizationSuggestion> parseOptimizationResponse(String response) throws DecisionException {
        try {
            JsonNode root = objectMapper.readTree(extractJsonFromResponse(response));

            List<OptimizationSuggestion> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode node : suggestionsNode) {
                    suggestions.add(new OptimizationSuggestion(
                            node.path("area").asText(),
                            node.path("suggestion").asText(),
                            node.path("impact").asText()
                    ));
                }
            }

            return suggestions;
        } catch (Exception e) {
            throw new DecisionException("Failed to parse optimization response: " + e.getMessage(), e);
        }
    }

    private String extractJsonFromResponse(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private void logDecision(String type, String caseId, DecisionResult result,
                              long durationMs, String model) {
        auditLog.add(new DecisionAuditEntry(
                System.currentTimeMillis(),
                type,
                caseId,
                result.getSelectedOption(),
                result.getConfidence(),
                result.getReasoning(),
                durationMs,
                model,
                true,
                null
        ));
    }

    private void logDecisionError(String type, String caseId, String error, long durationMs) {
        auditLog.add(new DecisionAuditEntry(
                System.currentTimeMillis(),
                type,
                caseId,
                null,
                0.0,
                null,
                durationMs,
                defaultModel,
                false,
                error
        ));
    }

    // Inner classes for data structures

    /**
     * Represents workflow context for decision making.
     */
    public static class WorkflowContext {
        private final String caseId;
        private final String workflowName;
        private final String currentTask;
        private final String caseData;
        private final Map<String, Object> metadata;

        public WorkflowContext(String caseId, String workflowName, String currentTask, String caseData) {
            this.caseId = caseId;
            this.workflowName = workflowName;
            this.currentTask = currentTask;
            this.caseData = caseData;
            this.metadata = new HashMap<>();
        }

        public String getCaseId() { return caseId; }
        public String getWorkflowName() { return workflowName; }
        public String getCurrentTask() { return currentTask; }
        public String getCaseData() { return caseData; }
        public Map<String, Object> getMetadata() { return metadata; }

        public WorkflowContext withMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }
    }

    /**
     * Represents a routing option in a decision.
     */
    public static class RoutingOption {
        private final String id;
        private final String description;
        private final String priority;
        private final String condition;

        public RoutingOption(String id, String description, String priority) {
            this(id, description, priority, null);
        }

        public RoutingOption(String id, String description, String priority, String condition) {
            this.id = id;
            this.description = description;
            this.priority = priority;
            this.condition = condition;
        }

        public String getId() { return id; }
        public String getDescription() { return description; }
        public String getPriority() { return priority; }
        public String getCondition() { return condition; }
    }

    /**
     * Criteria for decision making.
     */
    public static class DecisionCriteria {
        private final boolean highStakes;
        private final String additionalContext;
        private final Map<String, Object> constraints;

        public static final DecisionCriteria DEFAULT = new DecisionCriteria(false, null, Map.of());

        public DecisionCriteria(boolean highStakes, String additionalContext, Map<String, Object> constraints) {
            this.highStakes = highStakes;
            this.additionalContext = additionalContext;
            this.constraints = constraints != null ? constraints : Map.of();
        }

        public boolean isHighStakes() { return highStakes; }
        public String getAdditionalContext() { return additionalContext; }
        public Map<String, Object> getConstraints() { return constraints; }
    }

    /**
     * Result of a decision.
     */
    public static class DecisionResult {
        private final String selectedOption;
        private final double confidence;
        private final String reasoning;
        private final Map<String, Object> metadata;

        public DecisionResult(String selectedOption, double confidence, String reasoning,
                               Map<String, Object> metadata) {
            this.selectedOption = selectedOption;
            this.confidence = Math.max(0.0, Math.min(1.0, confidence));
            this.reasoning = reasoning;
            this.metadata = metadata != null ? metadata : Map.of();
        }

        public String getSelectedOption() { return selectedOption; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public Map<String, Object> getMetadata() { return metadata; }
        public boolean isConfident() { return confidence >= CONFIDENCE_THRESHOLD; }
    }

    /**
     * Data quality assessment result.
     */
    public static class DataQualityResult {
        private final double score;
        private final String summary;
        private final List<String> issues;

        public DataQualityResult(double score, String summary, List<String> issues) {
            this.score = Math.max(0.0, Math.min(1.0, score));
            this.summary = summary;
            this.issues = issues != null ? issues : Collections.emptyList();
        }

        public double getScore() { return score; }
        public String getSummary() { return summary; }
        public List<String> getIssues() { return issues; }
        public boolean isAcceptable() { return score >= 0.7; }
    }

    /**
     * Bottleneck information.
     */
    public static class Bottleneck {
        private final String taskId;
        private final String type;
        private final String severity;
        private final String description;

        public Bottleneck(String taskId, String type, String severity, String description) {
            this.taskId = taskId;
            this.type = type;
            this.severity = severity;
            this.description = description;
        }

        public String getTaskId() { return taskId; }
        public String getType() { return type; }
        public String getSeverity() { return severity; }
        public String getDescription() { return description; }
    }

    /**
     * Bottleneck analysis result.
     */
    public static class BottleneckAnalysis {
        private final List<Bottleneck> bottlenecks;
        private final List<String> recommendations;

        public BottleneckAnalysis(List<Bottleneck> bottlenecks, List<String> recommendations) {
            this.bottlenecks = bottlenecks != null ? bottlenecks : Collections.emptyList();
            this.recommendations = recommendations != null ? recommendations : Collections.emptyList();
        }

        public List<Bottleneck> getBottlenecks() { return bottlenecks; }
        public List<String> getRecommendations() { return recommendations; }
        public boolean hasBottlenecks() { return !bottlenecks.isEmpty(); }
    }

    /**
     * Worker profile for task assignment.
     */
    public static class WorkerProfile {
        private final String id;
        private final List<String> skills;
        private final String level;

        public WorkerProfile(String id, List<String> skills, String level) {
            this.id = id;
            this.skills = skills != null ? skills : Collections.emptyList();
            this.level = level != null ? level : "standard";
        }

        public String getId() { return id; }
        public List<String> getSkills() { return skills; }
        public String getLevel() { return level; }
    }

    /**
     * Task assignment recommendation.
     */
    public static class AssignmentRecommendation {
        private final String recommendedWorkerId;
        private final double confidence;
        private final String reasoning;

        public AssignmentRecommendation(String recommendedWorkerId, double confidence, String reasoning) {
            this.recommendedWorkerId = recommendedWorkerId;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }

        public String getRecommendedWorkerId() { return recommendedWorkerId; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
    }

    /**
     * Completion time prediction.
     */
    public static class CompletionPrediction {
        private final int estimatedMinutes;
        private final double confidence;
        private final List<String> factors;

        public CompletionPrediction(int estimatedMinutes, double confidence, List<String> factors) {
            this.estimatedMinutes = Math.max(0, estimatedMinutes);
            this.confidence = confidence;
            this.factors = factors != null ? factors : Collections.emptyList();
        }

        public int getEstimatedMinutes() { return estimatedMinutes; }
        public double getConfidence() { return confidence; }
        public List<String> getFactors() { return factors; }
    }

    /**
     * Transition validation result.
     */
    public static class TransitionValidation {
        private final boolean valid;
        private final String reason;
        private final List<String> warnings;

        public TransitionValidation(boolean valid, String reason, List<String> warnings) {
            this.valid = valid;
            this.reason = reason;
            this.warnings = warnings != null ? warnings : Collections.emptyList();
        }

        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Optimization suggestion.
     */
    public static class OptimizationSuggestion {
        private final String area;
        private final String suggestion;
        private final String impact;

        public OptimizationSuggestion(String area, String suggestion, String impact) {
            this.area = area;
            this.suggestion = suggestion;
            this.impact = impact;
        }

        public String getArea() { return area; }
        public String getSuggestion() { return suggestion; }
        public String getImpact() { return impact; }
    }

    /**
     * Cache entry for decision results.
     */
    private static class DecisionCacheEntry {
        private final DecisionResult result;
        private final long timestamp;

        DecisionCacheEntry(DecisionResult result, long timestamp) {
            this.result = result;
            this.timestamp = timestamp;
        }

        DecisionResult getResult() { return result; }
        long getTimestamp() { return timestamp; }

        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - timestamp > expirationMs;
        }
    }

    /**
     * Audit entry for decision logging.
     */
    public static class DecisionAuditEntry {
        private final long timestamp;
        private final String decisionType;
        private final String caseId;
        private final String selectedOption;
        private final double confidence;
        private final String reasoning;
        private final long durationMs;
        private final String model;
        private final boolean success;
        private final String errorMessage;

        public DecisionAuditEntry(long timestamp, String decisionType, String caseId,
                                   String selectedOption, double confidence, String reasoning,
                                   long durationMs, String model, boolean success, String errorMessage) {
            this.timestamp = timestamp;
            this.decisionType = decisionType;
            this.caseId = caseId;
            this.selectedOption = selectedOption;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.durationMs = durationMs;
            this.model = model;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public long getTimestamp() { return timestamp; }
        public String getDecisionType() { return decisionType; }
        public String getCaseId() { return caseId; }
        public String getSelectedOption() { return selectedOption; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public long getDurationMs() { return durationMs; }
        public String getModel() { return model; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Exception for decision-related errors.
     */
    public static class DecisionException extends Exception {
        public DecisionException(String message) {
            super(message);
        }

        public DecisionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
