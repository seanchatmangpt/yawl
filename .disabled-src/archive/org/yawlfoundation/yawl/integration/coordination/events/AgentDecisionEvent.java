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

package org.yawlfoundation.yawl.integration.coordination.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Event for tracking agent decisions and their context in coordination scenarios.
 *
 * <p>This event captures the decision-making process of agents in coordination
 * scenarios, including the decision context, options considered, factors influencing
 * the decision, and the final choice made. It provides traceability for audit
 * and debugging purposes.
 *
 * <h2>Decision Types</h2>
 * <ul>
 *   <li><b>Resource Allocation</b> - Which agent gets which resources</li>
 *   <li><b>Task Assignment</b> - Which agent handles which tasks</li>
 *   <li><b>Priority Ordering</b> - Order of task execution</li>
 *   <li><b>Conflict Resolution</b> - How to resolve conflicts</li>
 *   <li><b>Exception Handling</b> - How to handle workflow exceptions</li>
 * </ul>
 *
 * <h2>JSON Schema</h2>
 * <pre>
 * {
 *   "decisionId": "550e8400-e29b-41d4-a716-446655440004",
 *   "agentId": "agent-1",
 *   "caseId": "case-42",
 *   "workItemId": "wi-123",
 *   "decisionType": "RESOURCE_ALLOCATION",
 *   "context": {
 *     "availableResources": ["server-1", "server-2"],
 *     "resourceRequirements": {"server-1": 2, "server-2": 1},
 *     "currentLoad": {"agent-1": 0.3, "agent-2": 0.8}
 *   },
 *   "decisionTimestamp": "2026-02-17T10:00:00Z",
 *   "decisionDeadline": "2026-02-17T10:05:00Z",
 *   "optionsConsidered": [
 *     {"optionId": "opt1", "agent": "agent-1", "score": 0.9},
 *     {"optionId": "opt2", "agent": "agent-2", "score": 0.7}
 *   ],
 *   "decisionFactors": [
 *     {"factor": "load_balancing", "weight": 0.6, "value": 0.8},
 *     {"factor": "affinity", "weight": 0.4, "value": 0.5}
 *   ],
 *   "finalDecision": {
 *     "chosenOption": "opt1",
 *     "chosenAgent": "agent-1",
 *     "confidence": 0.9,
 *     "reasoning": "Lower current load and higher affinity score"
 *   },
 *   "executionPlan": {
 *     "immediateActions": ["allocate_resource", "update_agent_status"],
 *     "futureActions": ["schedule_next_task", "monitor_performance"],
 *     "expectedOutcome": {"completionTime": "2026-02-17T10:30:00Z"}
 *   },
 *   "metadata": {
 *     "decisionMaker": "agent-1",
 *     "reviewRequired": false,
 *     "wasTimeout": false
 *   }
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AgentDecisionEvent {

    public enum DecisionType {
        RESOURCE_ALLOCATION,    // Allocate resources to agents
        TASK_ASSIGNMENT,       // Assign tasks to agents
        PRIORITY_ORDERING,     // Set execution priorities
        CONFLICT_RESOLUTION,   // Resolve agent conflicts
        EXCEPTION_HANDLING,    // Handle workflow exceptions
        WORKFLOW_SELECTION,    // Select workflow path
        LOAD_BALANCING,       // Distribute workload
        RESOURCE_NEGOTIATION,  // Negotiate resource sharing
        AUTHORITY_DELEGATION,  // Delegate authority/responsibility
        POLICY_APPLICATION     // Apply business policies
    }

    private final String decisionId;
    private final String agentId;
    private final String caseId;
    private final String workItemId;
    private final DecisionType decisionType;
    private final Map<String, String> context;
    private final Instant decisionTimestamp;
    private final Instant decisionDeadline;
    private final DecisionOption[] optionsConsidered;
    private final DecisionFactor[] decisionFactors;
    private final Decision finalDecision;
    private final ExecutionPlan executionPlan;
    private final Map<String, Object> metadata;

    /**
     * Create an agent decision event with the required fields.
     *
     * @param decisionId unique identifier for this decision
     * @param agentId the agent making the decision (must not be blank)
     * @param caseId the workflow case context (must not be blank)
     * @param workItemId the work item context (null for case-level decisions)
     * @param decisionType the type of decision being made (must not be null)
     * @param context decision context and constraints (may be empty)
     * @param decisionTimestamp when the decision was made (must not be null)
     * @param decisionDeadline when the decision expires (null if no deadline)
     * @param optionsConsidered options evaluated during decision process (may be empty)
     * @param decisionFactors factors influencing the decision (may be empty)
     * @param finalDecision the final choice made (must not be null)
     * @param executionPlan how the decision will be executed (must not be null)
     * @param metadata additional decision metadata (may be empty)
     */
    public AgentDecisionEvent(String decisionId, String agentId, String caseId,
                             String workItemId, DecisionType decisionType,
                             Map<String, String> context, Instant decisionTimestamp,
                             Instant decisionDeadline, DecisionOption[] optionsConsidered,
                             DecisionFactor[] decisionFactors, Decision finalDecision,
                             ExecutionPlan executionPlan, Map<String, Object> metadata) {
        this.decisionId = Objects.requireNonNull(decisionId, "decisionId");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.workItemId = workItemId;
        this.decisionType = Objects.requireNonNull(decisionType, "decisionType");
        this.context = context != null ? Map.copyOf(context) : Map.of();
        this.decisionTimestamp = Objects.requireNonNull(decisionTimestamp, "decisionTimestamp");
        this.decisionDeadline = decisionDeadline;
        this.optionsConsidered = optionsConsidered != null ? optionsConsidered.clone() : new DecisionOption[0];
        this.decisionFactors = decisionFactors != null ? decisionFactors.clone() : new DecisionFactor[0];
        this.finalDecision = Objects.requireNonNull(finalDecision, "finalDecision");
        this.executionPlan = Objects.requireNonNull(executionPlan, "executionPlan");
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Generate a new agent decision event.
     */
    public static AgentDecisionEvent made(String agentId, String caseId, String workItemId,
                                        DecisionType decisionType, Map<String, String> context,
                                        Instant timestamp, Decision finalDecision,
                                        ExecutionPlan executionPlan) {
        String decisionId = java.util.UUID.randomUUID().toString();
        return new AgentDecisionEvent(decisionId, agentId, caseId, workItemId,
                                   decisionType, context, timestamp, null,
                                   new DecisionOption[0], new DecisionFactor[0],
                                   finalDecision, executionPlan, Map.of());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique identifier for this decision event. */
    public String getDecisionId() { return decisionId; }

    /** The agent making the decision. */
    public String getAgentId() { return agentId; }

    /** The workflow case context. */
    public String getCaseId() { return caseId; }

    /** The work item context (null for case-level decisions). */
    public String getWorkItemId() { return workItemId; }

    /** Type of decision being made. */
    public DecisionType getDecisionType() { return decisionType; }

    /** Decision context and constraints. */
    public Map<String, String> getContext() { return context; }

    /** When the decision was made. */
    public Instant getDecisionTimestamp() { return decisionTimestamp; }

    /** When the decision expires (null if no deadline). */
    public Instant getDecisionDeadline() { return decisionDeadline; }

    /** Options evaluated during decision process. */
    public DecisionOption[] getOptionsConsidered() { return optionsConsidered.clone(); }

    /** Factors influencing the decision. */
    public DecisionFactor[] getDecisionFactors() { return decisionFactors.clone(); }

    /** The final choice made. */
    public Decision getFinalDecision() { return finalDecision; }

    /** How the decision will be executed. */
    public ExecutionPlan getExecutionPlan() { return executionPlan; }

    /** Additional decision metadata. */
    public Map<String, Object> getMetadata() { return metadata; }

    /** Returns true if the decision has expired. */
    public boolean isExpired() {
        return decisionDeadline != null && decisionDeadline.isBefore(Instant.now());
    }

    // -------------------------------------------------------------------------
    // Serialization Support
    // -------------------------------------------------------------------------

    /**
     * Convert this event to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("decisionId", decisionId);
        map.put("agentId", agentId);
        map.put("caseId", caseId);
        map.put("workItemId", workItemId);
        map.put("decisionType", decisionType.name());
        map.put("context", context);
        map.put("decisionTimestamp", decisionTimestamp.toString());
        map.put("decisionDeadline", decisionDeadline != null ? decisionDeadline.toString() : null);
        map.put("optionsConsidered", List.of(optionsConsidered));
        map.put("decisionFactors", List.of(decisionFactors));
        map.put("finalDecision", finalDecision.toMap());
        map.put("executionPlan", executionPlan.toMap());
        map.put("metadata", metadata);
        return map;
    }

    /**
     * Create an AgentDecisionEvent from a map (deserialization).
     */
    @SuppressWarnings("unchecked")
    public static AgentDecisionEvent fromMap(Map<String, Object> map) {
        String decisionId = (String) map.get("decisionId");
        String agentId = (String) map.get("agentId");
        String caseId = (String) map.get("caseId");
        String workItemId = (String) map.get("workItemId");
        DecisionType decisionType = DecisionType.valueOf((String) map.get("decisionType"));

        Map<String, String> context = (Map<String, String>) map.get("context");

        String timestampStr = (String) map.get("decisionTimestamp");
        Instant decisionTimestamp = Instant.parse(timestampStr);

        String deadlineStr = (String) map.get("decisionDeadline");
        Instant decisionDeadline = deadlineStr != null ? Instant.parse(deadlineStr) : null;

        DecisionOption[] optionsConsidered = ((List<DecisionOption>) map.get("optionsConsidered")).toArray(new DecisionOption[0]);
        DecisionFactor[] decisionFactors = ((List<DecisionFactor>) map.get("decisionFactors")).toArray(new DecisionFactor[0]);

        Map<String, Object> finalDecisionMap = (Map<String, Object>) map.get("finalDecision");
        Decision finalDecision = Decision.fromMap(finalDecisionMap);

        Map<String, Object> executionPlanMap = (Map<String, Object>) map.get("executionPlan");
        ExecutionPlan executionPlan = ExecutionPlan.fromMap(executionPlanMap);

        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");

        return new AgentDecisionEvent(decisionId, agentId, caseId, workItemId,
                                   decisionType, context, decisionTimestamp,
                                   decisionDeadline, optionsConsidered, decisionFactors,
                                   finalDecision, executionPlan, metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgentDecisionEvent)) return false;
        AgentDecisionEvent that = (AgentDecisionEvent) o;
        return Objects.equals(decisionId, that.decisionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decisionId);
    }

    @Override
    public String toString() {
        return "AgentDecisionEvent{decisionId='" + decisionId + "', agentId='" + agentId +
               "', type=" + decisionType + ", timestamp=" + decisionTimestamp + "}";
    }

    /**
     * Nested class for decision options.
     */
    public static final class DecisionOption {
        private final String optionId;
        private final String agent;
        private final double score;
        private final String description;

        public DecisionOption(String optionId, String agent, double score, String description) {
            this.optionId = optionId;
            this.agent = agent;
            this.score = score;
            this.description = description;
        }

        public String getOptionId() { return optionId; }
        public String getAgent() { return agent; }
        public double getScore() { return score; }
        public String getDescription() { return description; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("optionId", optionId);
            map.put("agent", agent);
            map.put("score", score);
            map.put("description", description);
            return map;
        }

        public static DecisionOption fromMap(Map<String, Object> map) {
            return new DecisionOption((String) map.get("optionId"), (String) map.get("agent"),
                                   ((Number) map.get("score")).doubleValue(), (String) map.get("description"));
        }
    }

    /**
     * Nested class for decision factors.
     */
    public static final class DecisionFactor {
        private final String factor;
        private final double weight;
        private final double value;

        public DecisionFactor(String factor, double weight, double value) {
            this.factor = factor;
            this.weight = weight;
            this.value = value;
        }

        public String getFactor() { return factor; }
        public double getWeight() { return weight; }
        public double getValue() { return value; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("factor", factor);
            map.put("weight", weight);
            map.put("value", value);
            return map;
        }

        public static DecisionFactor fromMap(Map<String, Object> map) {
            return new DecisionFactor((String) map.get("factor"), ((Number) map.get("weight")).doubleValue(),
                                   ((Number) map.get("value")).doubleValue());
        }
    }

    /**
     * Nested class for final decision details.
     */
    public static final class Decision {
        private final String chosenOption;
        private final String chosenAgent;
        private final double confidence;
        private final String reasoning;

        public Decision(String chosenOption, String chosenAgent, double confidence, String reasoning) {
            this.chosenOption = chosenOption;
            this.chosenAgent = chosenAgent;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }

        public String getChosenOption() { return chosenOption; }
        public String getChosenAgent() { return chosenAgent; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("chosenOption", chosenOption);
            map.put("chosenAgent", chosenAgent);
            map.put("confidence", confidence);
            map.put("reasoning", reasoning);
            return map;
        }

        public static Decision fromMap(Map<String, Object> map) {
            return new Decision((String) map.get("chosenOption"), (String) map.get("chosenAgent"),
                             ((Number) map.get("confidence")).doubleValue(), (String) map.get("reasoning"));
        }
    }

    /**
     * Nested class for execution plans.
     */
    public static final class ExecutionPlan {
        private final String[] immediateActions;
        private final String[] futureActions;
        private final Map<String, Object> expectedOutcome;

        public ExecutionPlan(String[] immediateActions, String[] futureActions,
                           Map<String, Object> expectedOutcome) {
            this.immediateActions = immediateActions != null ? immediateActions.clone() : new String[0];
            this.futureActions = futureActions != null ? futureActions.clone() : new String[0];
            this.expectedOutcome = expectedOutcome != null ? Map.copyOf(expectedOutcome) : Map.of();
        }

        public String[] getImmediateActions() { return immediateActions.clone(); }
        public String[] getFutureActions() { return futureActions.clone(); }
        public Map<String, Object> getExpectedOutcome() { return expectedOutcome; }

        public Map<String, Object> toMap() {
            var map = new java.util.HashMap<String, Object>();
            map.put("immediateActions", immediateActions);
            map.put("futureActions", futureActions);
            map.put("expectedOutcome", expectedOutcome);
            return map;
        }

        public static ExecutionPlan fromMap(Map<String, Object> map) {
            String[] immediate = ((List<String>) map.get("immediateActions")).toArray(new String[0]);
            String[] future = ((List<String>) map.get("futureActions")).toArray(new String[0]);
            Map<String, Object> outcome = (Map<String, Object>) map.get("expectedOutcome");

            return new ExecutionPlan(immediate, future, outcome);
        }
    }
}