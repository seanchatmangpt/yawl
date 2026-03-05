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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Registry of all A2A skills exposed by the YAWL engine.
 *
 * <p>Provides lookup and filtering capabilities for the wizard to discover
 * available skills and match them to workflow tasks. Contains the 5 core
 * YAWL A2A skills: launch_workflow, query_workflows, manage_workitems,
 * cancel_workflow, and handoff_workitem.
 *
 * @since YAWL 6.0
 */
public final class A2ASkillRegistry {
    private static final List<A2ASkillDescriptor> ALL_SKILLS = List.of(
        new A2ASkillDescriptor(
            "launch_workflow",
            "Launch Workflow",
            "Launch a new workflow case from a loaded specification. "
                + "Provide the specification identifier and optional case data.",
            List.of("text"),
            List.of("text"),
            A2ASkillCategory.WORKFLOW_EXECUTION,
            true,
            false,
            Map.of(
                "Launch the OrderProcessing workflow",
                "Case OrderProcessing_1234 started successfully",
                "Start a new case of specification 'InvoiceApproval' with order data",
                "Case InvoiceApproval_5678 started with provided data"
            )
        ),
        new A2ASkillDescriptor(
            "query_workflows",
            "Query Workflows",
            "List available workflow specifications, running cases, and their current status.",
            List.of("text"),
            List.of("text"),
            A2ASkillCategory.WORKFLOW_QUERY,
            true,
            false,
            Map.of(
                "List all loaded workflow specifications",
                "OrderProcessing v1.0, InvoiceApproval v2.1, ShipmentTracking v1.5",
                "Show running cases and their status",
                "3 cases running: OrderProcessing_1234 (executing), InvoiceApproval_5678 (waiting)"
            )
        ),
        new A2ASkillDescriptor(
            "manage_workitems",
            "Manage Work Items",
            "Get, check out, and complete work items in running workflow cases.",
            List.of("text"),
            List.of("text"),
            A2ASkillCategory.WORKITEM_MANAGEMENT,
            true,
            false,
            Map.of(
                "Show work items for case 42",
                "Case 42: ReviewOrder (waiting), ApproveOrder (enabled)",
                "Complete work item 42:ReviewOrder with approved status",
                "Work item 42:ReviewOrder completed successfully"
            )
        ),
        new A2ASkillDescriptor(
            "cancel_workflow",
            "Cancel Workflow",
            "Cancel a running workflow case by its case ID.",
            List.of("text"),
            List.of("text"),
            A2ASkillCategory.WORKFLOW_EXECUTION,
            true,
            false,
            Map.of(
                "Cancel case 42",
                "Case 42 cancelled successfully",
                "Stop the running workflow case with ID 15",
                "Case 15 cancelled successfully"
            )
        ),
        new A2ASkillDescriptor(
            "handoff_workitem",
            "Handoff Work Item",
            "Transfer a work item to another agent when the current agent cannot complete it. "
                + "Uses secure JWT-based handoff protocol.",
            List.of("text"),
            List.of("text"),
            A2ASkillCategory.AGENT_COORDINATION,
            true,
            true,
            Map.of(
                "Handoff work item WI-42 to a specialized agent",
                "Work item WI-42 handed off to specialized-agent successfully",
                "Transfer document review task to agent-2",
                "Work item transferred to agent-2, awaiting acceptance"
            )
        )
    );

    /**
     * Private constructor to prevent instantiation.
     */
    private A2ASkillRegistry() {
    }

    /**
     * Gets all 5 YAWL A2A skills.
     *
     * @return unmodifiable list of all available skills
     */
    public static List<A2ASkillDescriptor> allSkills() {
        return ALL_SKILLS;
    }

    /**
     * Builds the local YAWL agent descriptor (what this engine exposes).
     *
     * @param engineUrl base URL of the YAWL engine
     * @return descriptor for the local YAWL A2A server
     * @throws NullPointerException if engineUrl is null
     */
    public static A2AAgentDescriptor localYawlAgent(String engineUrl) {
        Objects.requireNonNull(engineUrl, "engineUrl cannot be null");

        return new A2AAgentDescriptor(
            "yawl-local",
            "YAWL Workflow Engine",
            engineUrl,
            8081,
            "6.0.0",
            ALL_SKILLS,
            List.of("JWT", "API_KEY", "SPIFFE_MTLS"),
            A2AAgentStatus.AVAILABLE,
            Instant.now()
        );
    }

    /**
     * Gets skills in a given category.
     *
     * @param category the skill category to filter by
     * @return list of skills matching the category
     * @throws NullPointerException if category is null
     */
    public static List<A2ASkillDescriptor> byCategory(A2ASkillCategory category) {
        Objects.requireNonNull(category, "category cannot be null");

        return ALL_SKILLS.stream()
            .filter(skill -> skill.category() == category)
            .toList();
    }

    /**
     * Gets skills recommended for a given van der Aalst workflow pattern.
     *
     * <p>Maps workflow pattern codes to applicable skills:
     * <ul>
     *   <li>WP-1 (Sequence): all skills
     *   <li>WP-2 (Parallel Split): all skills
     *   <li>WP-3 (Synchronization): query_workflows, manage_workitems
     *   <li>WP-4 (Exclusive Choice): query_workflows, manage_workitems
     *   <li>WP-5 (Simple Merge): manage_workitems
     *   <li>WP-6 (Multiple Choice): query_workflows, manage_workitems
     *   <li>WP-7 (Synchronizing Merge): query_workflows, manage_workitems
     *   <li>WP-8 (Multiple Merge): manage_workitems
     *   <li>WP-10 (Arbitrary Cycles): all skills
     *   <li>WP-11 (Implicit Termination): all skills
     * </ul>
     *
     * @param patternCode pattern code like "WP-1", "WP-3", etc
     * @return list of recommended skills for the pattern, or all skills if pattern unknown
     * @throws NullPointerException if patternCode is null
     */
    public static List<A2ASkillDescriptor> recommendedForPattern(String patternCode) {
        Objects.requireNonNull(patternCode, "patternCode cannot be null");

        return switch (patternCode.toUpperCase()) {
            case "WP-1", "WP-2", "WP-10", "WP-11" -> ALL_SKILLS;
            case "WP-3", "WP-4", "WP-6", "WP-7" -> List.of(
                findById("query_workflows").orElseThrow(),
                findById("manage_workitems").orElseThrow()
            );
            case "WP-5", "WP-8" -> List.of(
                findById("manage_workitems").orElseThrow()
            );
            default -> ALL_SKILLS;
        };
    }

    /**
     * Finds a skill by its identifier.
     *
     * @param skillId the skill ID
     * @return optional containing the skill if found
     * @throws NullPointerException if skillId is null
     */
    public static Optional<A2ASkillDescriptor> findById(String skillId) {
        Objects.requireNonNull(skillId, "skillId cannot be null");

        return ALL_SKILLS.stream()
            .filter(skill -> skill.skillId().equals(skillId))
            .findFirst();
    }

    /**
     * Gets the count of all available skills.
     *
     * @return total number of skills (always 5 for YAWL)
     */
    public static int skillCount() {
        return ALL_SKILLS.size();
    }
}
