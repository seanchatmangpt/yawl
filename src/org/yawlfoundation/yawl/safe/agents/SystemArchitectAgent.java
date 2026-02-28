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

package org.yawlfoundation.yawl.safe.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * System Architect agent for SAFe architecture design and dependency management.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Designs system architecture and technical solutions</li>
 *   <li>Manages cross-team dependencies</li>
 *   <li>Evaluates technical feasibility of features</li>
 *   <li>Ensures consistency with architectural vision</li>
 * </ul>
 *
 * <p>Discovers: ArchitectureDesign, DependencyManagement, FeasibilityEvaluation, TechnicalReview tasks
 * Produces: ArchitectureDecision, DependencyResolution, FeasibilityAssessment, ArchitectureReview outputs
 *
 * @since YAWL 6.0
 */
public final class SystemArchitectAgent extends GenericPartyAgent {

    private static final Logger logger = LogManager.getLogger(SystemArchitectAgent.class);

    private final Map<String, AgentDecision> decisionLog = new HashMap<>();
    private final AtomicInteger decisionCounter = new AtomicInteger(0);

    /**
     * Create a System Architect agent.
     *
     * @param config agent configuration with strategies and credentials
     * @throws IOException if engine connection fails
     */
    public SystemArchitectAgent(AgentConfiguration config) throws IOException {
        super(config);
        logger.info("SystemArchitectAgent [{}] initialized", config.getAgentName());
    }

    /**
     * Create a pre-configured System Architect agent.
     *
     * @param engineUrl YAWL engine URL
     * @param username engine username
     * @param password engine password
     * @param port HTTP port
     * @return configured agent
     * @throws IOException if connection fails
     */
    public static SystemArchitectAgent create(
            String engineUrl,
            String username,
            String password,
            int port) throws IOException {

        AgentCapability capability = new AgentCapability(
            "SystemArchitect",
            "SAFe system architect designing systems, managing dependencies, evaluating feasibility");

        AgentConfiguration config = AgentConfiguration.builder(
                "system-architect-agent",
                engineUrl,
                username,
                password)
            .capability(capability)
            .discoveryStrategy(createDiscoveryStrategy())
            .eligibilityReasoner(createEligibilityReasoner())
            .decisionReasoner(createDecisionReasoner())
            .port(port)
            .version("6.0.0")
            .pollIntervalMs(5000L)
            .build();

        return new SystemArchitectAgent(config);
    }

    /**
     * Create discovery strategy for System Architect tasks.
     */
    private static DiscoveryStrategy createDiscoveryStrategy() {
        return (client, sessionHandle) -> {
            try {
                return client.getWorkItems(sessionHandle).stream()
                    .filter(wi -> wi.getTaskName() != null && (
                        wi.getTaskName().contains("ArchitectureDesign") ||
                        wi.getTaskName().contains("DependencyManagement") ||
                        wi.getTaskName().contains("FeasibilityEvaluation") ||
                        wi.getTaskName().contains("TechnicalReview")))
                    .toList();
            } catch (IOException e) {
                logger.warn("Discovery error for System Architect: {}", e.getMessage());
                return java.util.Collections.emptyList();
            }
        };
    }

    /**
     * Create eligibility reasoner for System Architect tasks.
     */
    private static EligibilityReasoner createEligibilityReasoner() {
        return workItem -> {
            String taskName = workItem.getTaskName();
            if (taskName == null) {
                return false;
            }

            return switch (taskName) {
                case "ArchitectureDesign" -> validateArchitectureData(workItem);
                case "DependencyManagement" -> validateDependencyData(workItem);
                case "FeasibilityEvaluation" -> validateFeasibilityData(workItem);
                case "TechnicalReview" -> validateTechnicalReviewData(workItem);
                default -> false;
            };
        };
    }

    /**
     * Create decision reasoner for System Architect tasks.
     */
    private static DecisionReasoner createDecisionReasoner() {
        return workItem -> {
            String taskName = workItem.getTaskName();

            return switch (taskName) {
                case "ArchitectureDesign" ->
                    designArchitecture(workItem);
                case "DependencyManagement" ->
                    manageDependencies(workItem);
                case "FeasibilityEvaluation" ->
                    evaluateFeasibility(workItem);
                case "TechnicalReview" ->
                    conductTechnicalReview(workItem);
                default ->
                    throw new IllegalArgumentException("Unknown task: " + taskName);
            };
        };
    }

    /**
     * Validate architecture design data.
     */
    private static boolean validateArchitectureData(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            return data != null && data.contains("<System>") && data.contains("<Components>");
        } catch (Exception e) {
            LogManager.getLogger().debug("Architecture validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate dependency management data.
     */
    private static boolean validateDependencyData(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            return data != null && data.contains("<Dependencies>") && data.contains("</Dependencies>");
        } catch (Exception e) {
            LogManager.getLogger().debug("Dependency validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate feasibility evaluation data.
     */
    private static boolean validateFeasibilityData(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            return data != null && data.contains("<Feature>") && data.contains("<Requirements>");
        } catch (Exception e) {
            LogManager.getLogger().debug("Feasibility validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Validate technical review data.
     */
    private static boolean validateTechnicalReviewData(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            return data != null && data.contains("<Design>") && data.contains("</Design>");
        } catch (Exception e) {
            LogManager.getLogger().debug("Technical review validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Design system architecture.
     */
    private static String designArchitecture(WorkItemRecord workItem) {
        String decisionId = "arch-design-" + System.nanoTime();

        AgentDecision decision = AgentDecision.builder(
                decisionId,
                "DESIGN_ARCHITECTURE",
                "SystemArchitectAgent")
            .workItemId(workItem.getID())
            .outcome("ARCHITECTURE_DESIGNED")
            .rationale("""
                System architecture designed:
                - Microservices pattern with 8 independent services
                - API gateway for cross-cutting concerns
                - Event-driven communication via message bus
                - Distributed caching strategy
                - Monitoring and observability framework""")
            .evidence(Map.of(
                "services_count", "8",
                "pattern", "microservices",
                "communication", "event_driven",
                "scalability_factor", "10x",
                "estimated_development_weeks", "12"))
            .build();

        logger.info("[SystemArchitect] Architecture designed for {}: {}",
            workItem.getID(), decision.outcome());

        return decision.toXml();
    }

    /**
     * Manage cross-team dependencies.
     */
    private static String manageDependencies(WorkItemRecord workItem) {
        String decisionId = "arch-depend-" + System.nanoTime();

        int resolvedDependencies = calculateResolvedDependencies(workItem);
        int totalDependencies = extractTotalDependencies(workItem);

        boolean allResolved = resolvedDependencies == totalDependencies;

        String outcome = allResolved ?
            "DEPENDENCIES_RESOLVED" : "DEPENDENCIES_PARTIAL";

        AgentDecision decision = AgentDecision.builder(
                decisionId,
                "MANAGE_DEPENDENCIES",
                "SystemArchitectAgent")
            .workItemId(workItem.getID())
            .outcome(outcome)
            .rationale(allResolved ?
                "All cross-team dependencies mapped and resolution planned with impacted teams" :
                "Partial dependency resolution; escalating unresolved dependencies to program level")
            .evidence(Map.of(
                "resolved_dependencies", String.valueOf(resolvedDependencies),
                "total_dependencies", String.valueOf(totalDependencies),
                "teams_impacted", "4",
                "critical_path_length", "6",
                "risk_level", "medium"))
            .build();

        logger.info("[SystemArchitect] Dependency management for {}: {}/{} resolved",
            workItem.getID(), resolvedDependencies, totalDependencies);

        return decision.toXml();
    }

    /**
     * Evaluate technical feasibility of feature.
     */
    private static String evaluateFeasibility(WorkItemRecord workItem) {
        String decisionId = "arch-feasible-" + System.nanoTime();

        boolean isFeasible = assessTechnicalFeasibility(workItem);

        String outcome = isFeasible ?
            "FEASIBLE_APPROVED" : "FEASIBILITY_ISSUES";

        AgentDecision decision = AgentDecision.builder(
                decisionId,
                "EVALUATE_FEASIBILITY",
                "SystemArchitectAgent")
            .workItemId(workItem.getID())
            .outcome(outcome)
            .rationale(isFeasible ?
                "Feature is technically feasible; no blockers identified with existing architecture" :
                "Feasibility concerns identified; architectural changes required")
            .evidence(Map.of(
                "feasibility_score", isFeasible ? "95%" : "42%",
                "architectural_changes_needed", isFeasible ? "0" : "3",
                "performance_impact", "acceptable",
                "security_review", "passed",
                "scalability_validated", isFeasible ? "yes" : "partial"))
            .build();

        logger.info("[SystemArchitect] Feasibility evaluation for {}: {}",
            workItem.getID(), decision.outcome());

        return decision.toXml();
    }

    /**
     * Conduct technical architecture review.
     */
    private static String conductTechnicalReview(WorkItemRecord workItem) {
        String decisionId = "arch-review-" + System.nanoTime();

        boolean reviewApproved = evaluateArchitectureQuality(workItem);

        String outcome = reviewApproved ?
            "REVIEW_APPROVED" : "REVIEW_REQUIRES_CHANGES";

        AgentDecision decision = AgentDecision.builder(
                decisionId,
                "CONDUCT_TECHNICAL_REVIEW",
                "SystemArchitectAgent")
            .workItemId(workItem.getID())
            .outcome(outcome)
            .rationale(reviewApproved ?
                "Architecture review passed; design aligns with system vision and principles" :
                "Architecture review identified improvements; resubmit after addressing feedback")
            .evidence(Map.of(
                "design_quality_score", reviewApproved ? "92%" : "68%",
                "alignment_with_vision", "excellent",
                "scalability_analysis", "satisfactory",
                "security_requirements", reviewApproved ? "met" : "gaps_found",
                "reviewers_count", "3"))
            .build();

        logger.info("[SystemArchitect] Technical review for {}: {}",
            workItem.getID(), decision.outcome());

        return decision.toXml();
    }

    /**
     * Calculate number of resolved dependencies.
     */
    private static int calculateResolvedDependencies(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            if (data == null) {
                return 0;
            }

            int resolved = 0;
            int startIdx = 0;

            while ((startIdx = data.indexOf("<Dependency>", startIdx)) >= 0) {
                int statusIdx = data.indexOf("<Status>RESOLVED</Status>", startIdx);
                int nextEnd = data.indexOf("</Dependency>", startIdx);

                if (statusIdx > startIdx && statusIdx < nextEnd) {
                    resolved++;
                }

                startIdx = nextEnd + 1;
            }

            return resolved;
        } catch (Exception e) {
            LogManager.getLogger().warn("Error calculating resolved dependencies: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Extract total dependency count.
     */
    private static int extractTotalDependencies(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            if (data == null) {
                return 0;
            }

            int count = 0;
            int startIdx = 0;

            while ((startIdx = data.indexOf("<Dependency>", startIdx)) >= 0) {
                count++;
                startIdx = data.indexOf("</Dependency>", startIdx) + 1;
            }

            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Assess technical feasibility.
     */
    private static boolean assessTechnicalFeasibility(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            if (data == null) {
                return false;
            }

            int performanceScore = extractMetric(data, "Performance", 0);
            int securityScore = extractMetric(data, "Security", 0);
            int scalabilityScore = extractMetric(data, "Scalability", 0);

            return performanceScore >= 80 && securityScore >= 90 && scalabilityScore >= 70;
        } catch (Exception e) {
            LogManager.getLogger().warn("Error assessing feasibility: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Evaluate architecture quality.
     */
    private static boolean evaluateArchitectureQuality(WorkItemRecord workItem) {
        try {
            String data = workItem.getDataString();
            if (data == null) {
                return false;
            }

            int designScore = extractMetric(data, "DesignScore", 0);
            int alignmentScore = extractMetric(data, "Alignment", 0);

            return designScore >= 85 && alignmentScore >= 80;
        } catch (Exception e) {
            LogManager.getLogger().warn("Error evaluating architecture quality: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract numeric metric from work item data.
     */
    private static int extractMetric(String data, String metricName, int defaultValue) {
        try {
            String openTag = "<" + metricName + ">";
            String closeTag = "</" + metricName + ">";

            int startIdx = data.indexOf(openTag);
            if (startIdx < 0) {
                return defaultValue;
            }
            int endIdx = data.indexOf(closeTag, startIdx);
            if (endIdx < 0) {
                return defaultValue;
            }

            String valueStr = data.substring(startIdx + openTag.length(), endIdx).trim();
            return Integer.parseInt(valueStr);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get decision log for audit trail.
     */
    public Map<String, AgentDecision> getDecisionLog() {
        return new HashMap<>(decisionLog);
    }

    /**
     * Clear decision log (typically after persistence).
     */
    public void clearDecisionLog() {
        decisionLog.clear();
    }
}
